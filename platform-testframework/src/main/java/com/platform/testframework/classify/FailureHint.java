package com.platform.testframework.classify;

/**
 * Lightweight failure classification produced by {@link FailureClassifier}.
 *
 * <p>Attached to each test result as environment entries so the platform AI
 * classifier and dashboard can surface it without re-analyzing the exception.</p>
 *
 * @param category   coarse-grained failure category
 * @param confidence 0.0–1.0 classifier confidence; below 0.5 = educated guess
 * @param message    human-readable explanation and suggested fix
 */
public record FailureHint(
        FailureCategory category,
        double confidence,
        String message
) {
    public static FailureHint of(FailureCategory category, double confidence, String message) {
        return new FailureHint(category, confidence, message);
    }

    public static FailureHint unknown() {
        return new FailureHint(FailureCategory.UNKNOWN, 0.0,
                "Unclassified failure — see stack trace and step log for details.");
    }

    /** True when confidence is high enough to surface as a definitive label. */
    public boolean isHighConfidence() {
        return confidence >= 0.75;
    }
}
