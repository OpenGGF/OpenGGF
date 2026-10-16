package com.openggf.game.rewind;

import com.openggf.game.rewind.coverage.ObjectClasspathScan;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** The unexpected fixture tail stays empty; real sweep results are checked by class identity. */
class TestRemainingRewindTailInventory {
    @Test
    void remainingRoundTripTailMatchesInventory() throws IOException {
        for (String resource : new String[] {
                "/rewind/round-trip-tail-inventory.txt",
                "/rewind/parent-dependent-graph-coverage-baseline.txt"}) {
            var stream = getClass().getResourceAsStream(resource);
            assertNotNull(stream, "missing " + resource);
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                assertTrue(reader.lines().allMatch(line -> line.isBlank() || line.stripLeading().startsWith("#")),
                        "unexpected rewind exemptions must remain empty: " + resource);
            }
        }
        var source = ObjectClasspathScan.findSourceRoot();
        assertNotNull(source, "tail inventory requires a source checkout");
        RewindRoundTripHarnessCoverage.assertDeclaredClasses(ObjectClasspathScan.findConcreteObjectInstances(source));
        // TestRewindHarnessCoverageRatchet and everySpawnableObjectRoundTrips
        // execute those identities and distinguish actual round-trips from the
        // three named optional ROM prerequisites. No aggregate allowance remains.
    }
}
