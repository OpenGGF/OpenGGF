package com.openggf.mods;

import com.openggf.mods.code.ModApiSignatureSurface;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestModApiPinPolicy {
    private static final Path POLICY = Path.of("mod-api-release-policy.properties");
    private static final Path PIN_DIRECTORY = Path.of("src/test/resources/mods");

    @Test
    void actualPinFilesExactlyMatchDescriptorAndCandidateSurface() throws IOException {
        ModApiReleasePolicy policy = ModApiReleasePolicy.read(POLICY);
        List<String> actualPins;
        try (var paths = Files.list(PIN_DIRECTORY)) {
            actualPins = paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("mod-api-signatures-") && name.endsWith(".txt"))
                    .sorted()
                    .toList();
        }
        assertEquals(policy.expectedPins().keySet().stream().sorted().toList(), actualPins);

        Set<String> currentSurface = ModApiSignatureSurface.snapshotLines();
        for (Map.Entry<String, SemanticVersion> pin : policy.expectedPins().entrySet()) {
            List<String> lines = Files.readAllLines(PIN_DIRECTORY.resolve(pin.getKey()));
            assertEquals(new ArrayList<>(new TreeSet<>(lines)), lines,
                    pin.getKey() + " must contain unique, sorted signatures");
            if (policy.currentStatus() == ModApiReleasePolicy.Status.CANDIDATE
                    && pin.getValue().equals(policy.currentApi())) {
                assertEquals(new ArrayList<>(currentSurface), lines,
                        "Candidate pin must exactly equal the current recursive surface");
            } else {
                boolean laterLine = !ModApiReleasePolicy.ReleaseLine.of(pin.getValue())
                        .equals(ModApiReleasePolicy.ReleaseLine.of(policy.currentApi()));
                List<String> violations = compare(new TreeSet<>(lines), currentSurface, laterLine);
                assertTrue(violations.isEmpty(),
                        () -> "Published pin " + pin.getKey() + " is incompatible:\n"
                                + String.join("\n", violations));
            }
        }
    }

    @Test
    void candidateAdditionsAndRemovalsFailExactComparison() {
        Set<String> baseline = Set.of("TYPE A");
        assertFalse(compare(baseline, Set.of("TYPE A", "METHOD A added()"), false).isEmpty());
        assertFalse(compare(baseline, Set.of(), false).isEmpty());
    }

    @Test
    void publishedRemovalsFailButLaterLineAdditionsPassAcrossAnyMajorBoundary() {
        Set<String> baseline = Set.of("TYPE A");
        Set<String> additive = Set.of("TYPE A", "METHOD A added()");
        assertFalse(compare(baseline, Set.of(), true).isEmpty());
        assertTrue(compare(baseline, additive, true).isEmpty(),
                "Forward additions must work for configured 0.7->0.8 and 0.9->1.0 transitions");
    }

    @Test
    void sameLineMaintenanceRequiresExactEquality() {
        Set<String> baseline = Set.of("TYPE A");
        assertFalse(compare(baseline, Set.of("TYPE A", "METHOD A added()"), false).isEmpty());
        assertTrue(compare(baseline, baseline, false).isEmpty());
    }

    private static List<String> compare(Set<String> baseline, Set<String> current,
            boolean laterConfiguredReleaseLine) {
        return ModApiSignatureSurface.signatureViolations(baseline, current,
                laterConfiguredReleaseLine
                        ? ModApiSignatureSurface.Comparison.FORWARD_LATER_RELEASE_LINE
                        : ModApiSignatureSurface.Comparison.EXACT);
    }
}
