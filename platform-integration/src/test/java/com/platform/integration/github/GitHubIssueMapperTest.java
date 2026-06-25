package com.platform.integration.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.integration.port.IssueRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitHubIssueMapperTest {

  private final ObjectMapper om = new ObjectMapper();
  private final GitHubIssueMapper mapper = new GitHubIssueMapper(om);

  @Test
  void createBody_hasTitleBodyAndLabels() throws Exception {
    IssueRequest req =
        new IssueRequest(
            "Test failure: login",
            "stack trace",
            "Bug",
            "High",
            null,
            List.of("extra-label"),
            "com.example.Test#login",
            "team-a");

    JsonNode body = om.readTree(mapper.toCreateBody(req));

    assertThat(body.path("title").asText()).isEqualTo("Test failure: login");
    assertThat(body.path("body").asText()).isEqualTo("stack trace");
    List<String> labels =
        om.convertValue(
            body.path("labels"),
            om.getTypeFactory().constructCollectionType(List.class, String.class));
    assertThat(labels).contains("testId:com.example.Test#login", "platform-auto", "extra-label");
  }

  @Test
  void testIdLabel_truncatedTo50() {
    String longId = "com.example.very.long.package.name.Test#aReallyLongMethodNameHere";
    assertThat(mapper.testIdLabel(longId)).hasSizeLessThanOrEqualTo(50);
    assertThat(mapper.testIdLabel("a#b")).isEqualTo("testId:a#b");
  }
}
