package com.openggf.game.rewind;

import com.openggf.game.rewind.coverage.ObjectClasspathScan;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Class identities cannot be substituted by unrelated new passing classes. */
public class TestRewindHarnessCoverageRatchet {
    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void everyDeclaredClassRetainsItsCoverage() throws IOException {
        var source = ObjectClasspathScan.findSourceRoot();
        assertNotNull(source, "coverage contract requires a source checkout");
        var classes = ObjectClasspathScan.findConcreteObjectInstances(source);
        RewindRoundTripHarnessCoverage.assertDeclaredClasses(classes);
        int isolated = 0;
        int graph = 0;
        int prerequisiteUnavailable = 0;
        for (var type : classes) {
            var result = RewindRoundTripHarness.probeClass(type.fqn());
            switch (RewindRoundTripHarnessCoverage.assertResult(type.fqn(), result)) {
                case ISOLATED -> isolated++;
                case GRAPH -> graph++;
                case PREREQUISITE_UNAVAILABLE -> prerequisiteUnavailable++;
            }
        }
        System.out.printf("[rewind-identities] isolated-executed=%d graph-declared=%d prerequisite-unavailable=%d%n",
                isolated, graph, prerequisiteUnavailable);
    }
}
