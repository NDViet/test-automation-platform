package com.platform.agent.hub;

import com.platform.common.agent.NodeCapabilities;
import com.platform.common.agent.NodeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hub component that tracks registered Node instances and their capabilities. Nodes register on
 * startup via POST /hub/nodes/register and heartbeat every 30s.
 */
public interface NodeRegistry {

  /** Register or refresh a node's capabilities. */
  void register(NodeCapabilities capabilities);

  /** Remove a node (called on graceful shutdown or missed heartbeats). */
  void deregister(UUID nodeId);

  /** Return the live capabilities for a specific node. */
  Optional<NodeCapabilities> get(UUID nodeId);

  /** Return all nodes of a given type that are currently healthy. */
  List<NodeCapabilities> healthy(NodeType nodeType);

  /** Return all currently registered nodes. */
  List<NodeCapabilities> all();
}
