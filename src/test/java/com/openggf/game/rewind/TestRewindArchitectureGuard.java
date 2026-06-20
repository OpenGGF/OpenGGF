package com.openggf.game.rewind;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

class TestRewindArchitectureGuard {
    private static final Path MAIN_ROOT = Path.of("src/main/java");

    private static final List<Path> OBJECT_SOURCE_ROOTS = List.of(
            Path.of("src/main/java/com/openggf/level/objects"),
            Path.of("src/main/java/com/openggf/level/rings"),
            Path.of("src/main/java/com/openggf/game/sonic1"),
            Path.of("src/main/java/com/openggf/game/sonic2"),
            Path.of("src/main/java/com/openggf/game/sonic3k")
    );

    private static final Pattern CAPTURE_OVERRIDE = Pattern.compile(
            "\\bpublic\\s+\\S*PerObjectRewindSnapshot\\s+captureRewindState\\s*\\(");
    private static final Pattern RESTORE_OVERRIDE = Pattern.compile(
            "\\bpublic\\s+void\\s+restoreRewindState\\s*\\(");

    private static final Map<String, Integer> OBJECT_REWIND_OVERRIDE_BASELINE = Map.ofEntries(
            Map.entry("src/main/java/com/openggf/level/objects/AbstractObjectInstance.java#captureRewindState", 2),
            Map.entry("src/main/java/com/openggf/level/objects/AbstractObjectInstance.java#restoreRewindState", 2),
            Map.entry("src/main/java/com/openggf/level/objects/AbstractBadnikInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/level/objects/AbstractBadnikInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/level/objects/AbstractMonitorObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/level/objects/AbstractMonitorObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/ARZPlatformObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/ARZPlatformObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/ConveyorObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/ConveyorObjectInstance.java#restoreRewindState", 1),
            // Obj70 compresses eight ROM SST cog teeth into one multi-piece
            // object plus slot-pressure children, so rewind must capture the
            // rotating tooth phase/offsets and whether child slots were spawned.
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/CogObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/CogObjectInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/badniks/BadnikProjectileInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/badniks/BadnikProjectileInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/badniks/BuzzerBadnikInstance.java#captureRewindState", 2),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/badniks/BuzzerBadnikInstance.java#restoreRewindState", 2),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/badniks/CoconutsBadnikInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/badniks/CoconutsBadnikInstance.java#restoreRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/badniks/MasherBadnikInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/badniks/MasherBadnikInstance.java#restoreRewindState", 1),
            // Obj08 skid dust has transient animation/delete/DPLC-preload state
            // that is not reconstructible from placement data alone.
            Map.entry("src/main/java/com/openggf/level/objects/SkidDustObjectInstance.java#captureRewindState", 1),
            Map.entry("src/main/java/com/openggf/level/objects/SkidDustObjectInstance.java#restoreRewindState", 1)
    );

    private static final Map<String, Integer> OBJECT_REWIND_ANNOTATION_BASELINE = Map.ofEntries(
            Map.entry("src/main/java/com/openggf/level/objects/ShieldObjectInstance.java#@RewindTransient", 3),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/CutsceneKnucklesAiz1Instance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/StarPointerBadnikInstance.java#@RewindTransient", 1),
            // Structural parent pointers on inner particle/support child classes: the parent
            // reference is object-graph structure rebuilt when the parent re-spawns its
            // children, not rewindable state. Same triage precedent as the entries above.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/LbzCupElevatorInstance.java#@RewindTransient", 4),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/IczSnowPileObjectInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/IczTensionPlatformObjectInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/Mhz1CutsceneKnucklesInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/CluckoidBadnikInstance.java#@RewindTransient", 1),
            // BuggernautBaby's parent pointer is live object-graph structure relinked
            // to the nearest live parent on recreate, not rewindable scalar state.
            // Same structural-parent triage precedent as the entries above.
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/badniks/BuggernautBabyInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossArenaHelperInstance.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossHitProxyChild.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossRobotnikHeadChild.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossSpikeChild.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossVisualChild.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossWeatherMachineChild.java#@RewindTransient", 1),
            Map.entry("src/main/java/com/openggf/game/sonic3k/objects/bosses/MhzEndBossWeatherVisualChild.java#@RewindTransient", 1),
            // Obj50 body/wing links are live object graph structure. The child
            // pointer is rebuilt from allocation and the wing's parent pointer
            // mirrors the ROM SST parent pointer rather than rewindable state.
            Map.entry("src/main/java/com/openggf/game/sonic2/objects/badniks/AquisBadnikInstance.java#@RewindTransient", 2)
    );

    private static final Set<String> REWIND_REGISTRY_PRODUCTION_ALLOWLIST = Set.of(
            "src/main/java/com/openggf/game/session/GameplayModeContext.java"
    );

    @Test
    void objectRewindOverridesDoNotGrowWithoutExplicitBaselineTriage() throws IOException {
        Map<String, Integer> actual = objectRewindOverrideCounts();

        assertBaselineMatches(
                actual,
                OBJECT_REWIND_OVERRIDE_BASELINE,
                "Object subclasses should prefer generic/schema rewind capture. "
                        + "New per-object capture/restore overrides need explicit triage.");
    }

    @Test
    void objectRewindAnnotationsDoNotGrowWithoutExplicitBaselineTriage() throws IOException {
        Map<String, Integer> actual = objectRewindAnnotationCounts();

        assertBaselineMatches(
                actual,
                OBJECT_REWIND_ANNOTATION_BASELINE,
                "Object packages should prefer central rewind policies/codecs. "
                        + "New @RewindTransient/@RewindDeferred annotations need explicit triage.");
    }

    @Test
    void productionRewindRegistryConstructionStaysGameplayScoped() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSources(MAIN_ROOT)) {
            String normalized = normalize(source);
            if (REWIND_REGISTRY_PRODUCTION_ALLOWLIST.contains(normalized)) {
                continue;
            }
            List<String> lines = Files.readAllLines(source);
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("new RewindRegistry(")) {
                    violations.add(normalized + ":" + (i + 1));
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("Production RewindRegistry ownership must stay in approved gameplay lifecycle code:\n"
                    + String.join("\n", violations));
        }
    }

    @Test
    void objectManagerDynamicRewindCodecsDoNotReferenceConcreteGamePackages() throws IOException {
        Path source = Path.of("src/main/java/com/openggf/level/objects/ObjectManager.java");
        List<String> violations = new ArrayList<>();
        List<String> lines = Files.readAllLines(source);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("com.openggf.game.sonic1")
                    || line.contains("com.openggf.game.sonic2")
                    || line.contains("com.openggf.game.sonic3k")) {
                violations.add(normalize(source) + ":" + (i + 1) + ": " + line.trim());
            }
        }

        if (!violations.isEmpty()) {
            fail("ObjectManager is shared object infrastructure; dynamic rewind codec knowledge "
                    + "for concrete games must live behind ObjectRegistry providers:\n"
                    + String.join("\n", violations));
        }
    }

    private static Map<String, Integer> objectRewindOverrideCounts() throws IOException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Path source : objectSources()) {
            String text = Files.readString(source);
            addCount(counts, normalize(source) + "#captureRewindState",
                    CAPTURE_OVERRIDE.matcher(text).results().count());
            addCount(counts, normalize(source) + "#restoreRewindState",
                    RESTORE_OVERRIDE.matcher(text).results().count());
        }
        return counts;
    }

    private static Map<String, Integer> objectRewindAnnotationCounts() throws IOException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Path source : objectSources()) {
            String text = Files.readString(source);
            addCount(counts, normalize(source) + "#@RewindTransient",
                    countOccurrences(text, "@RewindTransient"));
            addCount(counts, normalize(source) + "#@RewindDeferred",
                    countOccurrences(text, "@RewindDeferred"));
        }
        return counts;
    }

    private static void addCount(Map<String, Integer> counts, String key, long count) {
        if (count > 0) {
            counts.put(key, Math.toIntExact(count));
        }
    }

    private static long countOccurrences(String text, String token) {
        return Pattern.compile(Pattern.quote(token)).matcher(text).results().count();
    }

    private static List<Path> objectSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (Path root : OBJECT_SOURCE_ROOTS) {
            for (Path source : javaSources(root)) {
                String normalized = normalize(source);
                if (normalized.contains("/objects/")) {
                    sources.add(source);
                }
            }
        }
        sources.sort(Comparator.comparing(TestRewindArchitectureGuard::normalize));
        return sources;
    }

    private static List<Path> javaSources(Path root) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.comparing(TestRewindArchitectureGuard::normalize))
                    .toList();
        }
    }

    private static void assertBaselineMatches(Map<String, Integer> actual,
                                              Map<String, Integer> baseline,
                                              String message) {
        List<String> unexpected = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : actual.entrySet()) {
            Integer expected = baseline.get(entry.getKey());
            if (expected == null) {
                unexpected.add(entry.getKey() + " = " + entry.getValue());
            } else if (!expected.equals(entry.getValue())) {
                unexpected.add(entry.getKey() + " = " + entry.getValue()
                        + " (baseline " + expected + ")");
            }
        }

        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : baseline.entrySet()) {
            Integer observed = actual.get(entry.getKey());
            if (observed == null) {
                stale.add(entry.getKey() + " baseline " + entry.getValue()
                        + " is no longer present");
            }
        }

        if (!unexpected.isEmpty() || !stale.isEmpty()) {
            List<String> sections = new ArrayList<>();
            if (!unexpected.isEmpty()) {
                sections.add("Unexpected growth:\n" + String.join("\n", unexpected));
            }
            if (!stale.isEmpty()) {
                sections.add("Stale baseline:\n" + String.join("\n", stale));
            }
            fail(message + "\n" + String.join("\n\n", sections));
        }
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }
}
