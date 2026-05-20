package com.platform.common.integration;

import com.platform.common.model.RequirementRecord;

import java.util.List;

/**
 * Pluggable adapter contract for one external system at one platform tier.
 * <p>
 * Type parameters:
 * <ul>
 *   <li>{@code R} — the platform canonical record type (e.g. RequirementRecord)</li>
 *   <li>{@code C} — the outbound command/payload type used for push operations</li>
 * </ul>
 * Each implementation is bound to a specific {@link IntegrationType}.
 * Implementations live in {@code platform-integration-config}.
 */
public interface IntegrationAdapter<R, C> {

    /** The external system this adapter handles. */
    IntegrationType type();

    /**
     * Pull records from the external system using the provided config and cursor.
     * Returns a SyncResult containing the records fetched and the next cursor.
     */
    PagedRecords<R> fetchPage(SourceIntegrationConfig config, SyncCursor cursor);

    /**
     * Push a command (create/update/transition) to the external system.
     * Returns the external ID assigned or updated.
     */
    String push(SourceIntegrationConfig config, C command);

    /**
     * Health-check the connection for the given config.
     */
    IntegrationHealth healthCheck(SourceIntegrationConfig config);

    /**
     * Map a raw webhook event payload to zero or more canonical records.
     * Return empty list if this event type is not relevant to this adapter.
     */
    List<R> fromWebhook(WebhookEvent event, SourceIntegrationConfig config);

    /**
     * A page of canonical records with a cursor for the next fetch.
     */
    record PagedRecords<R>(List<R> records, SyncCursor nextCursor) {
        public PagedRecords {
            records = records == null ? List.of() : List.copyOf(records);
        }

        public boolean isEmpty() { return records.isEmpty(); }

        public static <R> PagedRecords<R> empty(SyncCursor cursor) {
            return new PagedRecords<>(List.of(), cursor);
        }
    }
}
