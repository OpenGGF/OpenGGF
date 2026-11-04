package com.openggf.trace.timing;

import java.util.Objects;
import java.util.regex.Pattern;

/** One ordered loading-phase observation; contains no gameplay counter values. */
public record InitializationTimingEvent(
        int rawFrame, int sequence, long loadOrdinal, int phaseOrdinal,
        Phase phase, String submissionFingerprint, Operation operation) {
    private static final Pattern FINGERPRINT = Pattern.compile("sha256:[0-9a-f]{64}");

    public InitializationTimingEvent {
        if (rawFrame < 0 || sequence < 0 || loadOrdinal <= 0 || phaseOrdinal <= 0) {
            throw new IllegalArgumentException("Invalid initialization timing frame, sequence or ordinal");
        }
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(operation, "operation");
        if (submissionFingerprint == null || !FINGERPRINT.matcher(submissionFingerprint).matches()) {
            throw new IllegalArgumentException("Invalid submission_fingerprint");
        }
    }

    public enum Phase {
        DISPLAY_CLEAR("display_clear"), LEVEL_DATA_DECODE("level_data_decode"),
        LEVEL_SETUP("level_setup"), INITIAL_PLACEMENT("initial_placement"), INITIAL_OBJECTS("initial_objects"),
        INITIAL_PRESENTATION("initial_presentation");
        private final String wireName;
        Phase(String wireName) { this.wireName = wireName; }
        public String wireName() { return wireName; }
        static Phase fromWireName(String name) {
            for (Phase phase : values()) if (phase.wireName.equals(name)) return phase;
            throw new IllegalArgumentException("Invalid phase");
        }
    }

    public enum Operation {
        HOLD("hold"), RESUME("resume"), VINT_0("vint_0");
        private final String wireName;
        Operation(String wireName) { this.wireName = wireName; }
        public String wireName() { return wireName; }
        static Operation fromWireName(String name) {
            for (Operation operation : values()) if (operation.wireName.equals(name)) return operation;
            throw new IllegalArgumentException("Invalid operation");
        }
    }
}
