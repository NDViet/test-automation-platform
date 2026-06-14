package com.platform.integration.azureboards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.integration.port.IssueRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AzureBoardsIssueMapperTest {

    private final ObjectMapper om = new ObjectMapper();
    private final AzureBoardsIssueMapper mapper = new AzureBoardsIssueMapper(om);

    private IssueRequest req() {
        return new IssueRequest("Test failure: login", "Something broke\nsecond line",
                "Bug", "High", "MyProject",
                List.of("com.example.Test#login", "platform-auto"),
                "com.example.Test#login", "team-a");
    }

    @Test
    void createPatch_hasTitleDescriptionTagsAndPriority() throws Exception {
        JsonNode patch = om.readTree(mapper.toCreatePatch(req(), "MyProject\\Area"));

        assertThat(patch.isArray()).isTrue();
        assertThat(fieldValue(patch, "/fields/System.Title")).isEqualTo("Test failure: login");
        assertThat(fieldValue(patch, "/fields/System.Description")).contains("<br/>"); // newline -> br
        assertThat(fieldValue(patch, "/fields/System.Tags"))
                .contains("platform:testId:com.example.Test#login")
                .contains("platform-auto");
        assertThat(fieldValue(patch, "/fields/System.AreaPath")).isEqualTo("MyProject\\Area");
        assertThat(fieldValue(patch, "/fields/Microsoft.VSTS.Common.Priority")).isEqualTo("2"); // High
    }

    @Test
    void statePatch_setsState() throws Exception {
        JsonNode patch = om.readTree(mapper.toStatePatch("Closed"));
        assertThat(fieldValue(patch, "/fields/System.State")).isEqualTo("Closed");
    }

    @Test
    void testIdTag_isSanitized() {
        assertThat(mapper.testIdTag("a b/c")).isEqualTo("platform:testId:a_b_c");
    }

    private static String fieldValue(JsonNode patch, String path) {
        for (JsonNode op : patch) {
            if (path.equals(op.path("path").asText())) {
                return op.path("value").asText();
            }
        }
        return null;
    }
}
