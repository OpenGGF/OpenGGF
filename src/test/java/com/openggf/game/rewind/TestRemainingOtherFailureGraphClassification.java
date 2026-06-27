package com.openggf.game.rewind;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.openggf.graphics.GraphicsManager;

class TestRemainingOtherFailureGraphClassification {
    private static final String GUMBALL_SPRING =
            "com.openggf.game.sonic3k.objects.GumballMachineObjectInstance$GumballSpringChild";
    private static final String ICZ_SEGMENT =
            "com.openggf.game.sonic3k.objects.IczSegmentColumnObjectInstance$Segment";

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void graphCoveredOtherFailuresAreParentDependentInIsolatedSweep() {
        for (String className : List.of(GUMBALL_SPRING, ICZ_SEGMENT)) {
            RewindRoundTripHarness.RoundTripSweepResult result = RewindRoundTripHarness.probeClass(className);
            RewindRoundTripHarness.RoundTripSweepResult.Unprobed unprobed =
                    assertInstanceOf(RewindRoundTripHarness.RoundTripSweepResult.Unprobed.class, result, className);
            assertTrue(unprobed.reason().startsWith("parent-dependent"),
                    className + " must stay in graph-only inventory; reason was " + unprobed.reason());
        }
    }
}
