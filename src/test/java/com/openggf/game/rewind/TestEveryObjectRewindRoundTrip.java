package com.openggf.game.rewind;

import com.openggf.game.GameId;
import com.openggf.game.rewind.coverage.ObjectClasspathScan;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Per-object production capture/restore checks with explicit prerequisite skips. */
public class TestEveryObjectRewindRoundTrip {

    // =========================================================================
    // Setup / teardown
    // =========================================================================

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    // =========================================================================
    // Step 1 — Keystone test: DEZ boss-child round-trips exactly through harness
    // =========================================================================

    /**
     * DEZ robot children must round-trip through the harness with an unchanged
     * COUNT and exact, non-init scalar state. This is the keystone test that
     * validates the harness drives the real ObjectManager + RewindRegistry path.
     *
     * <p>It PASSES once the harness correctly wires placed-spawn materialisation,
     * capture, and restore — including the boss-child codec and the suppression
     * of double-spawn during restore reconstruction.
     */
    @Test
    void dezRobotChildrenRoundTripExactlyThroughHarness() {
        // Build harness with the DEZ boss as a placed/active object.
        // buildPlaced materialises via om.reset(0) and registers the RewindRegistry.
        RewindRoundTripHarness h = RewindRoundTripHarness.buildPlaced(
                GameId.S2, Sonic2ObjectIds.DEATH_EGG_ROBOT);

        // Record the count and a non-init scalar before round-trip.
        Map<String, Integer> before = h.countByType();
        int capturedX = h.firstArticulatedChildX();

        // Round-trip through the real ObjectManager + RewindRegistry.
        h.roundTrip();

        // (a) Count must not change: no double-spawn, no drop.
        assertEquals(before, h.countByType(),
                "object counts must be unchanged after rewind round-trip "
                        + "(no double-spawn, no drop): before=" + before
                        + " after=" + h.countByType());

        // (b) Scalar state must be exact — zero re-simulation.
        // ArticulatedChild currentX is set at construction from the boss's X position.
        // It is not the default -1 from the harness helper (no child found).
        assertTrue(capturedX != -1,
                "precondition: harness must find at least one ArticulatedChild after materialisation");
        assertEquals(capturedX, h.firstArticulatedChildX(),
                "ArticulatedChild currentX must be restored EXACTLY (target==keyframe, "
                        + "zero re-sim); capturedX=" + capturedX
                        + " restoredX=" + h.firstArticulatedChildX());
    }

    // =========================================================================
    // Steps 3b-4 — Parametrized sweep over all spawnable object classes
    // =========================================================================

    static Stream<String> allSpawnableObjectClassNames() throws IOException {
        Path source = ObjectClasspathScan.findSourceRoot();
        org.junit.jupiter.api.Assertions.assertNotNull(source, "object sweep requires a source checkout");
        return ObjectClasspathScan.findConcreteObjectInstances(source).stream()
                .map(ObjectClasspathScan.SourceClass::fqn);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allSpawnableObjectClassNames")
    void everySpawnableObjectRoundTrips(String fqn) {
        var result = RewindRoundTripHarness.probeClass(fqn);
        var outcome = RewindRoundTripHarnessCoverage.assertResult(fqn, result);
        if (outcome == RewindRoundTripHarnessCoverage.Outcome.PREREQUISITE_UNAVAILABLE) {
            org.junit.jupiter.api.Assumptions.abort(fqn + ": " + RewindRoundTripHarness.MISSING_ROM_REASON);
        }
    }

    @Test
    void everySpawnableClassHasAnExplicitCoverageDisposition() throws IOException {
        Path source = ObjectClasspathScan.findSourceRoot();
        org.junit.jupiter.api.Assertions.assertNotNull(source, "coverage contract requires a source checkout");
        RewindRoundTripHarnessCoverage.assertDeclaredClasses(ObjectClasspathScan.findConcreteObjectInstances(source));
    }
}
