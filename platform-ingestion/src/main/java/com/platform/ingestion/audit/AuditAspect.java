package com.platform.ingestion.audit;

import com.platform.core.domain.ApiKey;
import com.platform.core.domain.AuditEvent;
import com.platform.core.repository.AuditEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * AOP aspect that intercepts {@link AuditLog}-annotated methods and persists
 * an {@link AuditEvent} for each invocation.
 *
 * <p>Runs around the method: records SUCCESS on normal return, FAILURE on exception.</p>
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditEventRepository auditRepo;

    public AuditAspect(AuditEventRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    @Around("@annotation(auditLog)")
    public Object audit(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        String outcome = "SUCCESS";
        Object result = null;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            outcome = "FAILURE";
            throw t;
        } finally {
            try {
                persist(auditLog, outcome);
            } catch (Exception e) {
                log.warn("[Audit] Failed to persist audit event (non-fatal): {}", e.getMessage());
            }
        }
        return result;
    }

    private void persist(AuditLog auditLog, String outcome) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        UUID actorKeyId    = null;
        String actorPrefix = null;
        UUID teamId        = null;

        if (auth != null && auth.getDetails() instanceof ApiKey key) {
            actorKeyId   = key.getId();
            actorPrefix  = key.getKeyPrefix();
            teamId       = key.getTeamId();
        }

        String clientIp = resolveClientIp();

        AuditEvent event = AuditEvent.builder()
                .eventType(auditLog.eventType())
                .actorKeyId(actorKeyId)
                .actorKeyPrefix(actorPrefix)
                .teamId(teamId)
                .resourceType(auditLog.resourceType())
                .clientIp(clientIp)
                .outcome(outcome)
                .build();

        auditRepo.save(event);
    }

    private String resolveClientIp() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest req = sra.getRequest();
                String forwarded = req.getHeader("X-Forwarded-For");
                return (forwarded != null && !forwarded.isBlank())
                        ? forwarded.split(",")[0].trim()
                        : req.getRemoteAddr();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
