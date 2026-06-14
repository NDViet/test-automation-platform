package com.platform.integrationconfig.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.integration.IntegrationType;
import com.platform.common.integration.WebhookEvent;
import com.platform.common.model.AutomatedTestRef;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubAdapterTest {

    private final ObjectMapper om = new ObjectMapper();
    private final GitHubAdapter adapter = new GitHubAdapter(RestClient.builder(), om);

    @Test
    void type_isGitHub() {
        assertThat(adapter.type()).isEqualTo(IntegrationType.GITHUB);
    }

    @Test
    void fromWebhook_mapsAddedAndModifiedTestFiles() throws Exception {
        String payload = """
            {"commits":[
              {"added":["src/test/java/com/example/LoginTest.java","README.md"],
               "modified":["e2e/checkout.spec.ts"]}
            ]}
            """;
        Map<String, Object> raw = om.readValue(payload, new TypeReference<>() {});
        WebhookEvent event = new WebhookEvent(UUID.randomUUID(), IntegrationType.GITHUB,
                WebhookEvent.EventAction.UPDATED, "push", "abc", raw, Instant.now());

        List<AutomatedTestRef> refs = adapter.fromWebhook(event, null);

        assertThat(refs).hasSize(2); // README.md filtered out
        assertThat(refs).extracting(AutomatedTestRef::filePath)
                .containsExactlyInAnyOrder(
                        "src/test/java/com/example/LoginTest.java",
                        "e2e/checkout.spec.ts");
        assertThat(refs).extracting(AutomatedTestRef::framework)
                .containsExactlyInAnyOrder("JUNIT5", "PLAYWRIGHT");
    }
}
