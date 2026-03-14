package com.platform.integration.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.integration.port.IssueRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JiraIssueMapperTest {

    JiraIssueMapper mapper;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mapper = new JiraIssueMapper(objectMapper);
    }

    @Test
    void createBodyContainsRequiredFields() throws Exception {
        IssueRequest req = new IssueRequest(
                "Test failure: loginTest", "Test failed 3 times", "Bug", "High",
                "PROJ", List.of("platform-auto"), "com.example.LoginTest#login", "team-a");

        JsonNode body = objectMapper.readTree(mapper.toCreateBody(req));

        assertThat(body.path("fields").path("project").path("key").asText()).isEqualTo("PROJ");
        assertThat(body.path("fields").path("summary").asText()).isEqualTo("Test failure: loginTest");
        assertThat(body.path("fields").path("issuetype").path("name").asText()).isEqualTo("Bug");
        assertThat(body.path("fields").path("priority").path("name").asText()).isEqualTo("High");
    }

    @Test
    void createBodyHasAdfDescription() throws Exception {
        IssueRequest req = new IssueRequest(
                "Title", "Some description text", "Bug", "Medium",
                "PROJ", List.of(), "test-id", "team");

        JsonNode body = objectMapper.readTree(mapper.toCreateBody(req));
        JsonNode desc = body.path("fields").path("description");

        assertThat(desc.path("version").asInt()).isEqualTo(1);
        assertThat(desc.path("type").asText()).isEqualTo("doc");
        assertThat(desc.path("content").isArray()).isTrue();
    }

    @Test
    void createBodyHasLabels() throws Exception {
        IssueRequest req = new IssueRequest(
                "Title", "Desc", "Bug", null,
                "PROJ", List.of("label1", "label2"), "test", "team");

        JsonNode body = objectMapper.readTree(mapper.toCreateBody(req));
        JsonNode labels = body.path("fields").path("labels");

        assertThat(labels.isArray()).isTrue();
        assertThat(labels.size()).isEqualTo(2);
    }

    @Test
    void labelIsSanitized() throws Exception {
        IssueRequest req = new IssueRequest(
                "Title", "Desc", "Bug", null,
                "PROJ", List.of("com.example.Test#method"), "t", "team");

        JsonNode body = objectMapper.readTree(mapper.toCreateBody(req));
        String label = body.path("fields").path("labels").get(0).asText();

        assertThat(label).doesNotContain("#");
        assertThat(label).matches("[a-zA-Z0-9._-]+");
    }

    @Test
    void commentBodyIsAdf() throws Exception {
        JsonNode body = objectMapper.readTree(mapper.toCommentBody("Test is still failing"));

        assertThat(body.path("body").path("type").asText()).isEqualTo("doc");
        assertThat(body.path("body").path("content").isArray()).isTrue();
    }

    @Test
    void adfDocSplitsOnBlankLines() throws Exception {
        String text = "First paragraph.\n\nSecond paragraph.";
        JsonNode doc = mapper.buildAdfDoc(text);

        assertThat(doc.path("content").size()).isEqualTo(2);
    }

    @Test
    void adfDocHandlesNullText() {
        JsonNode doc = mapper.buildAdfDoc(null);
        assertThat(doc.path("type").asText()).isEqualTo("doc");
    }

    @Test
    void noPriorityFieldWhenNull() throws Exception {
        IssueRequest req = new IssueRequest(
                "Title", "Desc", "Task", null, "PROJ", List.of(), "test", "team");

        JsonNode body = objectMapper.readTree(mapper.toCreateBody(req));

        assertThat(body.path("fields").has("priority")).isFalse();
    }
}
