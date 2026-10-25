package com.openggf.game.rewind;

import com.openggf.game.rewind.RewindRoundTripHarness.RoundTripSweepResult;
import com.openggf.game.rewind.coverage.ObjectClasspathScan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.TreeSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Discriminates identity substitution and arbitrary fixture exemptions from real coverage. */
class TestRewindRoundTripHarnessCoverage {
    private static final String FORCE_ROMLESS = "openggf.rewind.harness.forceRomless";

    @AfterEach
    void resetProperty() {
        System.clearProperty(FORCE_ROMLESS);
    }

    @Test
    void sameCountCannotSubstituteADifferentCoveredObject() {
        var names = new TreeSet<>(RewindRoundTripHarnessCoverage.isolatedClasses());
        names.addAll(RewindRoundTripHarness.declaredGraphCoverage().keySet());
        names.remove(names.first());
        names.add("example.UnreviewedObject");
        var discovered = names.stream().map(name -> new ObjectClasspathScan.SourceClass(name, "test")).toList();
        assertThrows(AssertionError.class, () -> RewindRoundTripHarnessCoverage.assertDeclaredClasses(discovered));
    }

    @Test
    void unexpectedUnprobedResultCannotBecomeACoverageExemption() {
        String name = RewindRoundTripHarnessCoverage.isolatedClasses().stream()
                .filter(type -> !RewindRoundTripHarness.romPrerequisiteClasses().contains(type)).findFirst().orElseThrow();
        assertThrows(AssertionError.class, () -> RewindRoundTripHarnessCoverage.assertResult(name,
                new RoundTripSweepResult.Unprobed("capture failed for another reason")));
    }

    @Test
    void absentRomCannotAcceptASuccessfulClassification() {
        System.setProperty(FORCE_ROMLESS, "true");
        String name = RewindRoundTripHarness.romPrerequisiteClasses().iterator().next();
        assertThrows(AssertionError.class, () -> RewindRoundTripHarnessCoverage.assertResult(name,
                new RoundTripSweepResult.Passed()));
    }

    @Test
    void onlyTheDeclaredPrerequisiteReasonIsExempt() {
        System.setProperty(FORCE_ROMLESS, "true");
        String name = RewindRoundTripHarness.romPrerequisiteClasses().iterator().next();
        assertEquals(RewindRoundTripHarnessCoverage.Outcome.PREREQUISITE_UNAVAILABLE,
                RewindRoundTripHarnessCoverage.assertResult(name,
                        new RoundTripSweepResult.Unprobed(RewindRoundTripHarness.MISSING_ROM_REASON)));
        assertThrows(AssertionError.class, () -> RewindRoundTripHarnessCoverage.assertResult(name,
                new RoundTripSweepResult.Unprobed("restore crashed")));
    }

    @Test
    void nativeS1BossComponentsRetainTheirExactCoverageDispositions() {
        String prefix = "com.openggf.game.sonic1.objects.bosses.";
        Map<String, String> graphs = Map.of(
                "GHZBossWreckingBall$NativeComponent", "TestS1GhzBossGraphRewind",
                "FZBossVisualComponent", "TestS1FzBossGraphRewind",
                "S1EggmanVisualComponent",
                "TestS1EggmanNativeComponents#midScriptRewindRecreatesIdentityAndProducesTheSameForwardFramesWithoutRespawn");
        for (var graph : graphs.entrySet()) {
            String name = prefix + graph.getKey();
            assertEquals(graph.getValue(), RewindRoundTripHarness.declaredGraphCoverage().get(name));
            assertEquals(RewindRoundTripHarnessCoverage.Outcome.GRAPH,
                    RewindRoundTripHarnessCoverage.assertResult(name, RewindRoundTripHarness.probeClass(name)));
            assertFalse(RewindRoundTripHarnessCoverage.isolatedClasses().contains(name));
        }
        String explosion = prefix + "S1BossExplosion";
        assertTrue(RewindRoundTripHarnessCoverage.isolatedClasses().contains(explosion));
        assertFalse(RewindRoundTripHarness.declaredGraphCoverage().containsKey(explosion));
        assertEquals(RewindRoundTripHarnessCoverage.Outcome.ISOLATED,
                RewindRoundTripHarnessCoverage.assertResult(explosion, RewindRoundTripHarness.probeClass(explosion)));
    }

    @Test
    void graphDispositionRetainsItsNamedEvidenceInsteadOfAnIsolatedPass() {
        var graph = RewindRoundTripHarness.declaredGraphCoverage().entrySet().iterator().next();
        assertEquals(RewindRoundTripHarnessCoverage.Outcome.GRAPH,
                RewindRoundTripHarnessCoverage.assertResult(graph.getKey(),
                        new RoundTripSweepResult.GraphCovered(graph.getValue())));
        assertThrows(AssertionError.class, () -> RewindRoundTripHarnessCoverage.assertResult(graph.getKey(),
                new RoundTripSweepResult.Passed()));
    }
}
