package com.platform.integration.azureboards;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.platform.integration.port.IssueRequest;
import org.springframework.stereotype.Component;

/**
 * Maps platform {@link IssueRequest}s to Azure DevOps json-patch documents.
 *
 * <p>A {@code platform:testId:{testId}} tag is always added so open work items can be located later
 * via WIQL (the dedup key).
 */
@Component
public class AzureBoardsIssueMapper {

  public static final String TEST_ID_TAG_PREFIX = "platform:testId:";

  private final ObjectMapper mapper;

  public AzureBoardsIssueMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** Builds the json-patch array used to create a work item. */
  public String toCreatePatch(IssueRequest req, String areaPath) {
    ArrayNode patch = mapper.createArrayNode();
    addField(patch, "/fields/System.Title", req.title());
    addField(patch, "/fields/System.Description", htmlify(req.description()));
    addField(patch, "/fields/System.Tags", buildTags(req));
    if (areaPath != null && !areaPath.isBlank()) {
      addField(patch, "/fields/System.AreaPath", areaPath);
    }
    // Azure priority is numeric 1..4; map High→2, Medium→3, Low→4.
    addField(patch, "/fields/Microsoft.VSTS.Common.Priority", priority(req.priority()));
    return patch.toString();
  }

  /** Builds the json-patch array to set a work item's state. */
  public String toStatePatch(String state) {
    ArrayNode patch = mapper.createArrayNode();
    addField(patch, "/fields/System.State", state);
    return patch.toString();
  }

  /** The dedup tag value for a test id. */
  public String testIdTag(String testId) {
    return TEST_ID_TAG_PREFIX + sanitize(testId);
  }

  private String buildTags(IssueRequest req) {
    StringBuilder sb = new StringBuilder();
    sb.append(testIdTag(req.testId())).append("; platform-auto");
    if (req.labels() != null) {
      for (String l : req.labels()) {
        if (l != null && !l.isBlank()) sb.append("; ").append(l);
      }
    }
    return sb.toString();
  }

  private void addField(ArrayNode patch, String path, Object value) {
    ObjectNode op = mapper.createObjectNode();
    op.put("op", "add");
    op.put("path", path);
    op.putPOJO("value", value);
    patch.add(op);
  }

  private int priority(String p) {
    if (p == null) return 3;
    return switch (p.toLowerCase()) {
      case "critical" -> 1;
      case "high" -> 2;
      case "low" -> 4;
      default -> 3;
    };
  }

  /** Azure renders System.Description as HTML; convert minimal markdown line breaks. */
  private String htmlify(String text) {
    if (text == null) return "";
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\n", "<br/>");
  }

  private String sanitize(String s) {
    return s == null ? "" : s.replaceAll("[^a-zA-Z0-9._#-]", "_");
  }
}
