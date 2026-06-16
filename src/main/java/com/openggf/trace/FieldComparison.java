package com.openggf.trace;

/**
 * Comparison result for a single field on a single frame.
 */
public record FieldComparison(
    String fieldName,
    String expected,
    String actual,
    Severity severity,
    int delta,
    boolean observedMismatch
) {
    public FieldComparison(String fieldName, String expected, String actual, Severity severity, int delta) {
        this(fieldName, expected, actual, severity, delta, false);
    }

    public boolean isDivergent() {
        return severity != Severity.MATCH;
    }

    public boolean isContextRelevant() {
        return isDivergent() || observedMismatch;
    }
}
