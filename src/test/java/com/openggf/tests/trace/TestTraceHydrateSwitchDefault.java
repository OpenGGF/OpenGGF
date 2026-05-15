package com.openggf.tests.trace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * CI guard: the {@code oggf.trace.hydrate} diagnostic system property must
 * default to {@code false} so trace replay tests run as comparison-only
 * (engine state is never written from recorded ROM frame-0 snapshots).
 *
 * <p>When this property is set to {@code true}, the replay loop snaps engine
 * player history, sidekick CPU state, and pre-trace SST snapshots to the
 * recorded values. Such runs are explicitly diagnostic and must NEVER be used
 * to claim a green replay — the test asserts the property is unset on CI to
 * prevent accidental hydration from masking real engine divergences.
 *
 * <p>This guard pairs with the {@code HYDRATE_PRE_TRACE} gate in
 * {@link AbstractTraceReplayTest}: both read the same property name.
 */
class TestTraceHydrateSwitchDefault {

    @Test
    void hydrateSwitchDefaultsOff() {
        assertFalse(Boolean.getBoolean("oggf.trace.hydrate"),
                "oggf.trace.hydrate must remain unset in CI — hydration runs are "
                        + "diagnostic only and never count as green");
    }
}
