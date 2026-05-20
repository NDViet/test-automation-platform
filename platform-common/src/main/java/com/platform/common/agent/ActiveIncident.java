package com.platform.common.agent;

import com.platform.common.model.MonitorRecord;

import java.time.Instant;
import java.util.UUID;

/**
 * A production incident that is currently open and linked to a monitor.
 * Carried in MonitorContext so nodes can correlate failures with live incidents.
 */
public record ActiveIncident(
        UUID incidentId,
        String externalIncidentId,   // PagerDuty/Opsgenie incident key
        String title,
        MonitorRecord.MonitorStatus severity,
        String monitorRef,           // platform monitor ID or name
        Instant startedAt,
        String runbookUrl
) {}
