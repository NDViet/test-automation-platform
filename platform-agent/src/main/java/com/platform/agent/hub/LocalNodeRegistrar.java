package com.platform.agent.hub;

import com.platform.common.agent.LlmTier;
import com.platform.common.agent.NodeCapabilities;
import com.platform.agent.node.AgentNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Registers local AgentNode beans into NodeRegistry on startup so CapabilityTaskRouter
 * can route tasks to them without requiring a remote HTTP registration call.
 * Each local node gets a stable deterministic UUID derived from its task type name.
 */
@Configuration
public class LocalNodeRegistrar {

    private static final Logger log = LoggerFactory.getLogger(LocalNodeRegistrar.class);

    @Bean
    public ApplicationRunner registerLocalNodes(List<AgentNode> nodes, NodeRegistry registry) {
        return args -> {
            for (AgentNode node : nodes) {
                UUID nodeId = UUID.nameUUIDFromBytes(node.taskType().name().getBytes());
                NodeCapabilities caps = new NodeCapabilities(
                        nodeId,
                        node.nodeType(),
                        Set.of(node.taskType()),
                        Set.of(),
                        LlmTier.STANDARD,
                        4,
                        "local",
                        "embedded"
                );
                registry.register(caps);
                log.info("registered local node: {} task={}", node.nodeType(), node.taskType());
            }
        };
    }
}
