package com.openggf.game.rewind;

import com.openggf.game.rewind.RewindRoundTripHarness.RoundTripSweepResult;
import com.openggf.game.rewind.coverage.ObjectClasspathScan;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/** Expected class identities, independent of a run's aggregate pass count. */
final class RewindRoundTripHarnessCoverage {
    private static final String ISOLATED_RESOURCE = "/rewind/isolated-round-trip-classes.txt";

    enum Outcome { ISOLATED, GRAPH, PREREQUISITE_UNAVAILABLE }

    private RewindRoundTripHarnessCoverage() { }

    static Set<String> isolatedClasses() {
        return DeclaredIdentities.ISOLATED;
    }

    private static final class DeclaredIdentities {
        private static final Set<String> ISOLATED = loadIsolatedClasses();
    }

    private static Set<String> loadIsolatedClasses() {
        var stream = RewindRoundTripHarnessCoverage.class.getResourceAsStream(ISOLATED_RESOURCE);
        assertNotNull(stream, "missing expected coverage contract " + ISOLATED_RESOURCE);
        Set<String> classes = new TreeSet<>();
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                String name = line.trim();
                if (name.isEmpty() || name.startsWith("#")) continue;
                assertTrue(classes.add(name), "duplicate isolated identity: " + name);
            }
        } catch (IOException error) {
            throw new AssertionError("cannot read " + ISOLATED_RESOURCE, error);
        }
        assertFalse(classes.isEmpty(), "isolated coverage contract must not be empty");
        assertTrue(classes.containsAll(RewindRoundTripHarness.romPrerequisiteClasses()),
                "each declared ROM prerequisite must name an expected isolated class");
        return Set.copyOf(classes);
    }

    static void assertDeclaredClasses(Collection<ObjectClasspathScan.SourceClass> discovered) {
        Set<String> expected = new TreeSet<>(isolatedClasses());
        for (String graph : RewindRoundTripHarness.declaredGraphCoverage().keySet()) {
            assertTrue(expected.add(graph), "class has both isolated and graph dispositions: " + graph);
        }
        Set<String> actual = new TreeSet<>();
        for (var source : discovered) {
            assertTrue(actual.add(source.fqn()), "duplicate discovered object identity: " + source.fqn());
        }
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        Set<String> undeclared = new TreeSet<>(actual);
        undeclared.removeAll(expected);
        assertTrue(missing.isEmpty() && undeclared.isEmpty(),
                "Object coverage identities changed; missing=" + missing + "; undeclared=" + undeclared);
    }

    static Outcome assertResult(String name, RoundTripSweepResult result) {
        String graph = RewindRoundTripHarness.declaredGraphCoverage().get(name);
        if (graph != null) {
            var covered = assertInstanceOf(RoundTripSweepResult.GraphCovered.class, result,
                    "expected named graph evidence for " + name);
            assertEquals(graph, covered.evidence(), "graph evidence changed for " + name);
            assertFalse(graph.isBlank(), "graph evidence cannot be blank: " + name);
            return Outcome.GRAPH;
        }
        assertTrue(isolatedClasses().contains(name), "undeclared isolated object: " + name);
        if (RewindRoundTripHarness.requiredRomUnavailable(name)) {
            var unavailable = assertInstanceOf(RoundTripSweepResult.Unprobed.class, result,
                    "an unavailable prerequisite must not count as an executed round-trip: " + name);
            assertEquals(RewindRoundTripHarness.MISSING_ROM_REASON, unavailable.reason(),
                    "only the declared missing prerequisite may exempt " + name);
            return Outcome.PREREQUISITE_UNAVAILABLE;
        }
        if (result instanceof RoundTripSweepResult.Passed) return Outcome.ISOLATED;
        if (result instanceof RoundTripSweepResult.CountMismatch mismatch) {
            assertEquals(mismatch.beforeCounts(), mismatch.afterCounts(),
                    "object count changed across rewind for " + name);
        }
        if (result instanceof RoundTripSweepResult.ScalarMismatch mismatch) {
            assertTrue(mismatch.diffs().isEmpty(), "scalar rewind mismatch for " + name + ": " + mismatch.diffs());
        }
        return fail("expected an executed isolated round-trip for " + name + "; got " + result);
    }
}
