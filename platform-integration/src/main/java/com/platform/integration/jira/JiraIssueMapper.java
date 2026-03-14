package com.platform.integration.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.platform.integration.port.IssueRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps platform {@link IssueRequest} objects to JIRA REST API v3 JSON payloads.
 *
 * <p>JIRA v3 uses Atlassian Document Format (ADF) for rich-text fields.</p>
 */
@Component
public class JiraIssueMapper {

    private final ObjectMapper mapper;

    public JiraIssueMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Builds the JSON body for {@code POST /rest/api/3/issue}.
     */
    public String toCreateBody(IssueRequest req) {
        ObjectNode root   = mapper.createObjectNode();
        ObjectNode fields = root.putObject("fields");

        // Project
        fields.putObject("project").put("key", req.projectKey());

        // Summary
        fields.put("summary", req.title());

        // Issue type
        fields.putObject("issuetype").put("name", req.issueType());

        // Priority
        if (req.priority() != null) {
            fields.putObject("priority").put("name", req.priority());
        }

        // Description (ADF)
        fields.set("description", buildAdfDoc(req.description()));

        // Labels — JIRA labels are plain strings (no spaces)
        if (req.labels() != null && !req.labels().isEmpty()) {
            ArrayNode labels = fields.putArray("labels");
            for (String label : req.labels()) {
                labels.add(sanitizeLabel(label));
            }
        }

        return root.toString();
    }

    /**
     * Builds the JSON body for {@code POST /rest/api/3/issue/{key}/comment}.
     */
    public String toCommentBody(String commentText) {
        ObjectNode root = mapper.createObjectNode();
        root.set("body", buildAdfDoc(commentText));
        return root.toString();
    }

    // ── ADF helpers ───────────────────────────────────────────────────────────

    /**
     * Wraps plain text in a minimal ADF document (paragraphs split on blank lines).
     */
    ObjectNode buildAdfDoc(String text) {
        ObjectNode doc = mapper.createObjectNode();
        doc.put("version", 1);
        doc.put("type", "doc");
        ArrayNode content = doc.putArray("content");

        if (text == null || text.isBlank()) {
            addParagraph(content, "");
            return doc;
        }

        // Split on double newlines into paragraphs
        String[] paragraphs = text.split("\\n\\n+");
        for (String para : paragraphs) {
            String trimmed = para.strip();
            if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
                // Code block
                String code = trimmed.substring(3, trimmed.length() - 3).strip();
                ObjectNode codeBlock = content.addObject();
                codeBlock.put("type", "codeBlock");
                codeBlock.putObject("attrs").put("language", "text");
                codeBlock.putArray("content").addObject()
                        .put("type", "text").put("text", code);
            } else {
                addParagraph(content, trimmed);
            }
        }
        return doc;
    }

    private void addParagraph(ArrayNode content, String text) {
        ObjectNode para = content.addObject();
        para.put("type", "paragraph");
        para.putArray("content").addObject()
                .put("type", "text")
                .put("text", text);
    }

    private String sanitizeLabel(String label) {
        return label.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
