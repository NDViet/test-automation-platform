package com.platform.ingestion.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for automatic audit logging.
 * Applied to service or controller methods that perform security-sensitive actions.
 *
 * <p>The {@link AuditAspect} intercepts calls and persists an {@code AuditEvent}
 * with the outcome (SUCCESS or FAILURE) after the method returns.</p>
 *
 * <pre>{@code
 * @AuditLog(eventType = "INGEST", resourceType = "TEST_RUN")
 * public IngestResponse ingest(IngestRequest request) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** Audit event type — e.g. "INGEST", "API_KEY_CREATED". */
    String eventType();

    /** The type of resource being acted on — e.g. "TEST_RUN", "API_KEY". */
    String resourceType() default "";
}
