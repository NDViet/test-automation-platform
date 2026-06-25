package com.platform.integration.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.platform.integration.port.IssueRequest;
import org.springframework.stereotype.Component;

/**
 * Maps platform {@link IssueRequest}s to GitHub Issues create payloads.
 *
 * <p>A {@code testId:{testId}} label is always added so open issues can be located later via search
 * (the dedup key).
 */
@Component
public class GitHubIssueMapper {

  public static final String TEST_ID_LABEL_PREFIX = "testId:";

  private final ObjectMapper mapper;

  public GitHubIssueMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public String toCreateBody(IssueRequest req) {
    ObjectNode node = mapper.createObjectNode();
    node.put("title", req.title());
    node.put("body", req.description() == null ? "" : req.description());

    ArrayNode labels = mapper.createArrayNode();
    labels.add(testIdLabel(req.testId()));
    labels.add("platform-auto");
    if (req.labels() != null) {
      for (String l : req.labels()) {
        if (l != null && !l.isBlank()) labels.add(l);
      }
    }
    node.set("labels", labels);
    return node.toString();
  }

  public String testIdLabel(String testId) {
    // GitHub labels allow most chars but keep them tidy and < 50 chars.
    String s = testId == null ? "" : testId.replaceAll("\\s+", "_");
    String label = TEST_ID_LABEL_PREFIX + s;
    return label.length() > 50 ? label.substring(0, 50) : label;
  }
}
