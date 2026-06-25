package com.platform.common.agent;

import com.platform.common.model.MonitorRecord;
import java.util.List;

/** Monitor and incident context for the Hub→Node contract. */
public record MonitorContext(
    List<MonitorRecord> monitors,
    List<ActiveIncident> activeIncidents,
    boolean hasUnlinkedMonitors // monitors not yet traced back to a requirement
    ) {
  public MonitorContext {
    monitors = monitors == null ? List.of() : List.copyOf(monitors);
    activeIncidents = activeIncidents == null ? List.of() : List.copyOf(activeIncidents);
  }

  public boolean hasActiveIncidents() {
    return !activeIncidents.isEmpty();
  }

  public boolean isEmpty() {
    return monitors.isEmpty() && activeIncidents.isEmpty();
  }
}
