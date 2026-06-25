package com.platform.agent.api;

import com.platform.agent.hub.impl.InMemoryNodeRegistry;
import com.platform.common.agent.NodeCapabilities;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/hub/nodes")
public class NodeRegistrationController {

  private final InMemoryNodeRegistry registry;

  public NodeRegistrationController(InMemoryNodeRegistry registry) {
    this.registry = registry;
  }

  @PostMapping("/register")
  public ResponseEntity<Void> register(@RequestBody NodeCapabilities capabilities) {
    registry.register(capabilities);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/{nodeId}/heartbeat")
  public ResponseEntity<Void> heartbeat(@PathVariable UUID nodeId) {
    registry.heartbeat(nodeId);
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/{nodeId}")
  public ResponseEntity<Void> deregister(@PathVariable UUID nodeId) {
    registry.deregister(nodeId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping
  public ResponseEntity<List<NodeCapabilities>> listAll() {
    return ResponseEntity.ok(registry.all());
  }
}
