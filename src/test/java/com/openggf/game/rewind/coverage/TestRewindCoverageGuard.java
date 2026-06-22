package com.openggf.game.rewind.coverage;

import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class TestRewindCoverageGuard {
    private static final Path BASELINE =
            Path.of("src/test/resources/rewind/coverage-baseline.txt");

    /** Collects codec class names from all three per-game registries. */
    private static Set<String> allGameCodecClassNames() {
        return Stream.of(
                java.util.List.<com.openggf.level.objects.DynamicObjectRewindCodec>of(),
                java.util.List.<com.openggf.level.objects.DynamicObjectRewindCodec>of(),
                java.util.List.<com.openggf.level.objects.DynamicObjectRewindCodec>of()
        ).flatMap(List::stream)
         .map(DynamicObjectRewindCodec::className)
         .collect(Collectors.toUnmodifiableSet());
    }

    @Test
    void noNewCoverageGapsBeyondBaseline() throws Exception {
        // Compare stable gap KEYS, not class names: class#recreate,
        // class#finalScalar#field, class#objectRef#field.
        Set<String> current = new TreeSet<>(RewindCoverageAnalyzer.analyzeAll(allGameCodecClassNames()).gapKeys());
        Set<String> baseline = new TreeSet<>(Files.readAllLines(BASELINE));
        baseline.removeIf(String::isBlank);

        Set<String> regressions = new TreeSet<>(current);
        regressions.removeAll(baseline);
        assertTrue(regressions.isEmpty(),
                "New rewind-coverage gap keys introduced (not in baseline):\n  "
                        + String.join("\n  ", regressions)
                        + "\nFix the object's rewind coverage, or (only if intentional) "
                        + "add the key(s) to " + BASELINE + ".");

        // Informational: surface gaps closed since baseline so the baseline can be tightened.
        Set<String> closed = new TreeSet<>(baseline);
        closed.removeAll(current);
        if (!closed.isEmpty()) {
            System.out.println("[rewind-coverage] gaps closed since baseline (tighten baseline):\n  "
                    + String.join("\n  ", closed));
        }
    }
}
