package com.platform.agent.hub.impl;

import com.platform.common.agent.NodeCapabilities;
import com.platform.common.agent.NodeType;
import com.platform.agent.hub.NodeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory node registry.
 * Nodes heartbeat every 30s; entries missing 2 consecutive heartbeats are evicted.
 */
@Component
public class InMemoryNodeRegistry implements NodeRegistry {

    private static final Logger log = LoggerFactory.getLogger(InMemoryNodeRegistry.class);
    private static final long HEARTBEAT_TIMEOUT_MS = 70_000; // 70s = 2 missed 30s heartbeats

    private record Entry(NodeCapabilities caps, Instant lastSeen) {}

    private final ConcurrentHashMap<UUID, Entry> nodes = new ConcurrentHashMap<>();

    @Override
    public void register(NodeCapabilities capabilities) {
        nodes.put(capabilities.nodeId(), new Entry(capabilities, Instant.now()));
        log.info("node registered: {} type={} tasks={}", capabilities.nodeId(),
                capabilities.nodeType(), capabilities.supportedTaskTypes());
    }

    @Override
    public void deregister(UUID nodeId) {
        nodes.remove(nodeId);
        log.info("node deregistered: {}", nodeId);
    }

    @Override
    public Optional<NodeCapabilities> get(UUID nodeId) {
        Entry e = nodes.get(nodeId);
        return e == null ? Optional.empty() : Optional.of(e.caps());
    }

    @Override
    public List<NodeCapabilities> healthy(NodeType nodeType) {
        return nodes.values().stream()
                .filter(e -> e.caps().nodeType() == nodeType)
                .map(Entry::caps)
                .collect(Collectors.toList());
    }

    @Override
    public List<NodeCapabilities> all() {
        return nodes.values().stream().map(Entry::caps).collect(Collectors.toList());
    }

    /** Called by nodes to refresh their heartbeat timestamp. */
    public void heartbeat(UUID nodeId) {
        nodes.computeIfPresent(nodeId, (id, e) -> new Entry(e.caps(), Instant.now()));
    }

    @Scheduled(fixedDelay = 30_000)
    public void evictStaleNodes() {
        Instant cutoff = Instant.now().minusMillis(HEARTBEAT_TIMEOUT_MS);
        nodes.entrySet().removeIf(entry -> {
            // Local (embedded) nodes never heartbeat — skip eviction
            if ("local".equals(entry.getValue().caps().endpoint())) return false;
            boolean stale = entry.getValue().lastSeen().isBefore(cutoff);
            if (stale) log.warn("evicting stale node: {}", entry.getKey());
            return stale;
        });
    }
}
