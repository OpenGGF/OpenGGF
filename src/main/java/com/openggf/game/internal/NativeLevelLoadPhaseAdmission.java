package com.openggf.game.internal;

import java.util.Objects;

/** Scheduling of an already prepared, independently selected production Level operation. */
@FunctionalInterface
public interface NativeLevelLoadPhaseAdmission {
    NativeLevelLoadPhaseAdmission IMMEDIATE = phase -> Decision.RESUME;

    enum Decision { HOLD, VINT_0, RESUME }

    enum Phase {
        DISPLAY_CLEAR("display_clear"), LEVEL_DATA_DECODE("level_data_decode"),
        LEVEL_SETUP("level_setup"), INITIAL_PLACEMENT("initial_placement"), INITIAL_OBJECTS("initial_objects"),
        INITIAL_PRESENTATION("initial_presentation");
        private final String wireName;
        Phase(String wireName) { this.wireName = wireName; }
        public String wireName() { return wireName; }
    }

    record PreparedPhase(long loadOrdinal, int phaseOrdinal, Phase phase, NativeLevelLoadInputs inputs) {
        public PreparedPhase {
            if (loadOrdinal <= 0 || phaseOrdinal <= 0) throw new IllegalArgumentException("nonpositive load identity");
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(inputs, "inputs");
        }
        public String fingerprint() { return inputs.fingerprint(phase.wireName()); }
    }

    Decision next(PreparedPhase phase);
}
