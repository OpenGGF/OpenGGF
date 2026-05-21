package com.openggf.level.objects;

import com.openggf.tests.ObjectGuardSourceScanner;
import com.openggf.tests.ObjectGuardSourceScanner.SourceText;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestObjectPhysicsStandardizationGuard {
    private static final Pattern OBJECT_CONTROL_SETTER = Pattern.compile(
            "\\.setObjectControl(?:led|AllowsCpu|SuppressesMovement)\\s*\\(");
    private static final Pattern NATIVE_P2_SIDEKICK_ACCESS = Pattern.compile(
            "(?:getFirst\\s*\\(\\s*\\)|\\.get\\s*\\(\\s*0\\s*\\)|\\.stream\\s*\\(\\s*\\)\\.findFirst\\s*\\()");
    private static final Pattern DIRECT_LIFECYCLE_OPERATION = Pattern.compile(
            "(?:setSlotIndex\\s*\\(\\s*-\\s*1\\s*\\)|\\.markRemembered\\s*\\(|"
                    + "\\.removeFromActiveSpawns\\s*\\(|\\.addDynamicObjectAtSlot\\s*\\()");
    private static final List<Pattern> DIRECT_TOUCH_POLICY_CALLS = List.of(
            Pattern.compile("(?<!\\btouchProfile)\\.requiresRenderFlagForTouch\\s*\\("),
            Pattern.compile("(?<!\\btouchProfile)\\.requiresContinuousTouchCallbacks\\s*\\("),
            Pattern.compile("\\.usesS3kTouchSpecialPropertyResponse\\s*\\("),
            Pattern.compile("\\.usesSonic2TouchSpecialPropertyResponse\\s*\\("),
            Pattern.compile("\\bTouchResponseProvider\\s+touchProfile\\b"),
            Pattern.compile("\\bvar\\s+touchProfile\\s*=\\s*(?:\\(\\s*TouchResponseProvider\\s*\\)\\s*)?provider\\b"),
            Pattern.compile("\\btouchProfile\\s*=\\s*(?:\\(\\s*TouchResponseProvider\\s*\\)\\s*)?provider\\b"));

    private static final List<BaselineViolation> BASELINE = List.of(
            baseline("com/openggf/game/sonic3k/objects/AbstractS3kFloatingEndEggCapsuleInstance.java", "sprite.setObjectControlled(true);", ViolationKind.DIRECT_OBJECT_CONTROL_SETTER, ReasonCode.BOSS_OR_CUTSCENE_ESCAPE_HATCH, 1),
            baseline("com/openggf/game/sonic3k/objects/bosses/HczEndBossEggCapsuleInstance.java", "sprite.setObjectControlled(true);", ViolationKind.DIRECT_OBJECT_CONTROL_SETTER, ReasonCode.BOSS_OR_CUTSCENE_ESCAPE_HATCH, 1),
            baseline("com/openggf/game/sonic3k/objects/bosses/HczEndBossGeyserCutscene.java", "player.setObjectControlled(true);", ViolationKind.DIRECT_OBJECT_CONTROL_SETTER, ReasonCode.BOSS_OR_CUTSCENE_ESCAPE_HATCH, 1),
            baseline("com/openggf/game/sonic3k/objects/bosses/HczEndBossWaterColumn.java", "sprite.setObjectControlled(false);", ViolationKind.DIRECT_OBJECT_CONTROL_SETTER, ReasonCode.BOSS_OR_CUTSCENE_ESCAPE_HATCH, 3),
            baseline("com/openggf/game/sonic3k/objects/bosses/HczEndBossWaterColumn.java", "sprite.setObjectControlled(true);", ViolationKind.DIRECT_OBJECT_CONTROL_SETTER, ReasonCode.BOSS_OR_CUTSCENE_ESCAPE_HATCH, 1),
            baseline("com/openggf/game/sonic3k/objects/IczSnowboardIntroInstance.java", "player.setObjectControlled(false);", ViolationKind.DIRECT_OBJECT_CONTROL_SETTER, ReasonCode.CUTSCENE_SCRIPT, 2),
            baseline("com/openggf/game/sonic3k/objects/IczSnowboardIntroInstance.java", "player.setObjectControlled(true);", ViolationKind.DIRECT_OBJECT_CONTROL_SETTER, ReasonCode.CUTSCENE_SCRIPT, 4),
            baseline("com/openggf/game/sonic3k/objects/IczSnowboardIntroInstance.java", "player.setObjectControlSuppressesMovement(false);", ViolationKind.DIRECT_OBJECT_CONTROL_SETTER, ReasonCode.CUTSCENE_SCRIPT, 3),
            baseline("com/openggf/game/sonic3k/objects/IczSnowboardIntroInstance.java", "player.setObjectControlSuppressesMovement(true);", ViolationKind.DIRECT_OBJECT_CONTROL_SETTER, ReasonCode.CUTSCENE_SCRIPT, 2)
    );

    @Test
    void objectManagerUsesNativePositionOpsForPlayablePreserveSubpixelWrites() throws IOException {
        SourceText source = source("com/openggf/level/objects/ObjectManager.java");

        assertEquals(List.of(), forbiddenLines(source,
                "setCentreXPreserveSubpixel(",
                "setCentreYPreserveSubpixel("));
    }

    @Test
    void badnikDestructionUsesLifetimeOpsForSlotTransferAndDestroyedFlag() throws IOException {
        SourceText source = source("com/openggf/level/objects/AbstractBadnikInstance.java");

        assertEquals(List.of(), forbiddenLines(source,
                "setSlotIndex(-1)",
                "setDestroyed(true)"));
    }

    @Test
    void destructionEffectsUsesLifetimeOpsForSpawnAndReplacementLifecycle() throws IOException {
        SourceText source = source("com/openggf/level/objects/DestructionEffects.java");

        assertEquals(List.of(), forbiddenLines(source,
                ".markRemembered(",
                ".removeFromActiveSpawns(",
                ".addDynamicObjectAtSlot("));
    }

    @Test
    void objectManagerTouchDispatchConsumesTouchResponseProfileForStablePolicy() throws IOException {
        SourceText source = source("com/openggf/level/objects/ObjectManager.java");

        assertEquals(List.of(), forbiddenLines(source, DIRECT_TOUCH_POLICY_CALLS));
    }

    @Test
    void productionObjectPhysicsStandardizationBaselinesDoNotGrow() throws IOException {
        assertEquals(baselineCounts(), violationCounts(scanProductionSources()));
    }

    @Test
    void s3kResultsScreenUsesObjectControlStatePolicyInsteadOfBaseline() {
        assertEquals(List.of(), BASELINE.stream()
                .filter(baseline -> baseline.path().endsWith("S3kResultsScreenObjectInstance.java"))
                .toList());
    }

    @Test
    void s3kSignpostUsesObjectControlStatePolicyInsteadOfBaseline() {
        assertEquals(List.of(), BASELINE.stream()
                .filter(baseline -> baseline.path().endsWith("S3kSignpostInstance.java"))
                .toList());
    }

    @Test
    void sonic3kSsEntryRingUsesObjectControlStatePolicyInsteadOfBaseline() {
        assertEquals(List.of(), BASELINE.stream()
                .filter(baseline -> baseline.path().endsWith("Sonic3kSSEntryRingObjectInstance.java"))
                .toList());
    }

    @Test
    void cnzEndBossUsesObjectControlStatePolicyInsteadOfBaseline() {
        assertEquals(List.of(), BASELINE.stream()
                .filter(baseline -> baseline.path().endsWith("bosses/CnzEndBossInstance.java"))
                .toList());
    }

    @Test
    void hczMinibossUsesObjectControlStatePolicyInsteadOfBaseline() {
        assertEquals(List.of(), BASELINE.stream()
                .filter(baseline -> baseline.path().endsWith("HczMinibossInstance.java"))
                .toList());
    }

    @Test
    void aizPlaneIntroUsesObjectControlStatePolicyInsteadOfBaseline() {
        assertEquals(List.of(), BASELINE.stream()
                .filter(baseline -> baseline.path().endsWith("AizPlaneIntroInstance.java"))
                .toList());
    }

    @Test
    void cutsceneKnucklesAiz1UsesObjectControlStatePolicyInsteadOfBaseline() {
        assertEquals(List.of(), BASELINE.stream()
                .filter(baseline -> baseline.path().endsWith("CutsceneKnucklesAiz1Instance.java"))
                .toList());
    }

    @Test
    void guardDetectsDirectObjectControlSetterInSampleSource() {
        SourceText source = ObjectGuardSourceScanner.sourceWithoutCommentOnlyLines(List.of(
                "class Sample {",
                "  void update(Player player) {",
                "    player.setObjectControlled (true);",
                "  }",
                "}"));

        String path = "com/openggf/game/sonic2/objects/Sample.java";

        assertEquals(List.of(new SourceViolation(
                        path,
                        "player.setObjectControlled (true);",
                        ViolationKind.DIRECT_OBJECT_CONTROL_SETTER)),
                scanSource(path, source));
    }

    @Test
    void guardDetectsRawNativeP2SidekickAccessInSampleSource() {
        SourceText source = ObjectGuardSourceScanner.sourceWithoutCommentOnlyLines(List.of(
                "class Sample {",
                "  void update(List<Player> sidekicks) {",
                "    Player p2 = sidekicks.getFirst();",
                "    Player p2b = sidekicks.get( 0 );",
                "    Player p2c = getSidekicks().stream().findFirst().orElse(null);",
                "  }",
                "}"));

        String path = "com/openggf/game/sonic2/objects/Sample.java";

        assertEquals(List.of(
                        new SourceViolation(
                        path,
                        "Player p2 = sidekicks.getFirst();",
                        ViolationKind.RAW_NATIVE_P2_SIDEKICK_ACCESS),
                        new SourceViolation(
                                path,
                                "Player p2b = sidekicks.get( 0 );",
                                ViolationKind.RAW_NATIVE_P2_SIDEKICK_ACCESS),
                        new SourceViolation(
                                path,
                                "Player p2c = getSidekicks().stream().findFirst().orElse(null);",
                                ViolationKind.RAW_NATIVE_P2_SIDEKICK_ACCESS)),
                scanSource(path, source));
    }

    @Test
    void guardDetectsDirectLifecycleOperationInSampleSource() {
        SourceText source = ObjectGuardSourceScanner.sourceWithoutCommentOnlyLines(List.of(
                "class Sample {",
                "  void update(ObjectManager objectManager, Spawn spawn) {",
                "    objectManager.markRemembered (spawn);",
                "    objectManager.addDynamicObjectAtSlot (null, 0);",
                "    setSlotIndex(- 1);",
                "  }",
                "}"));

        assertEquals(List.of(
                        new SourceViolation(
                                "Sample.java",
                                "objectManager.markRemembered (spawn);",
                                ViolationKind.DIRECT_LIFECYCLE_OPERATION),
                        new SourceViolation(
                                "Sample.java",
                                "objectManager.addDynamicObjectAtSlot (null, 0);",
                                ViolationKind.DIRECT_LIFECYCLE_OPERATION),
                        new SourceViolation(
                                "Sample.java",
                                "setSlotIndex(- 1);",
                                ViolationKind.DIRECT_LIFECYCLE_OPERATION)),
                scanSource("Sample.java", source));
    }

    private static SourceText source(String relativePath) throws IOException {
        Path srcMain = ObjectGuardSourceScanner.findSourceRoot();
        if (srcMain == null) {
            throw new IOException("Could not locate src/main/java");
        }
        return ObjectGuardSourceScanner.sourceWithoutCommentOnlyLines(
                Files.readAllLines(srcMain.resolve(relativePath)));
    }

    private static List<String> forbiddenLines(SourceText source, String... fragments) {
        return source.lines().stream()
                .map(String::trim)
                .filter(line -> containsAny(line, fragments))
                .toList();
    }

    private static List<String> forbiddenLines(SourceText source, List<Pattern> patterns) {
        return source.lines().stream()
                .map(String::trim)
                .filter(line -> matchesAny(line, patterns))
                .toList();
    }

    private static boolean containsAny(String line, String... fragments) {
        for (String fragment : fragments) {
            if (line.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAny(String line, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    private static List<SourceViolation> scanProductionSources() throws IOException {
        Path srcMain = ObjectGuardSourceScanner.findSourceRoot();
        if (srcMain == null) {
            throw new IOException("Could not locate src/main/java");
        }
        List<SourceViolation> violations = new ArrayList<>();
        for (Path sourceFile : ObjectGuardSourceScanner.javaFilesUnderPackages(
                srcMain, ObjectGuardSourceScanner.OBJECT_PACKAGE_PATHS)) {
            String path = srcMain.relativize(sourceFile).toString().replace('\\', '/');
            SourceText source = ObjectGuardSourceScanner.sourceWithoutCommentOnlyLines(
                    Files.readAllLines(sourceFile));
            violations.addAll(scanSource(path, source));
        }
        return violations;
    }

    private static List<SourceViolation> scanSource(String path, SourceText source) {
        if (isObjectPhysicsStandardizationOwner(path)) {
            return List.of();
        }
        boolean gameObjectPath = isGameObjectPath(path);
        List<SourceViolation> violations = new ArrayList<>();
        for (String line : source.lines()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (gameObjectPath && OBJECT_CONTROL_SETTER.matcher(trimmed).find()) {
                violations.add(new SourceViolation(path, trimmed,
                        ViolationKind.DIRECT_OBJECT_CONTROL_SETTER));
            }
            if (gameObjectPath && isRawNativeP2SidekickAccess(trimmed)) {
                violations.add(new SourceViolation(path, trimmed,
                        ViolationKind.RAW_NATIVE_P2_SIDEKICK_ACCESS));
            }
            if (isDirectLifecycleOperation(trimmed)) {
                violations.add(new SourceViolation(path, trimmed,
                        ViolationKind.DIRECT_LIFECYCLE_OPERATION));
            }
        }
        return violations;
    }

    private static boolean isObjectPhysicsStandardizationOwner(String path) {
        return path.equals("com/openggf/level/objects/ObjectManager.java")
                || path.equals("com/openggf/level/objects/ObjectLifetimeOps.java")
                || path.equals("com/openggf/level/objects/ObjectPlayerQuery.java");
    }

    private static boolean isGameObjectPath(String path) {
        for (String packagePath : ObjectGuardSourceScanner.GAME_OBJECT_PACKAGE_PATHS) {
            if (path.startsWith(packagePath + "/")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRawNativeP2SidekickAccess(String trimmed) {
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return NATIVE_P2_SIDEKICK_ACCESS.matcher(trimmed).find()
                && (lower.contains("sidekick") || trimmed.contains("getSidekicks()"));
    }

    private static boolean isDirectLifecycleOperation(String trimmed) {
        return DIRECT_LIFECYCLE_OPERATION.matcher(trimmed).find();
    }

    private static Map<ViolationKey, Integer> baselineCounts() {
        Map<ViolationKey, Integer> counts = new TreeMap<>();
        for (BaselineViolation baseline : BASELINE) {
            counts.merge(baseline.key(), baseline.count(), Integer::sum);
        }
        return counts;
    }

    private static Map<ViolationKey, Integer> violationCounts(List<SourceViolation> violations) {
        Map<ViolationKey, Integer> counts = new TreeMap<>();
        for (SourceViolation violation : violations) {
            counts.merge(violation.key(), 1, Integer::sum);
        }
        return counts;
    }

    private static BaselineViolation baseline(String path, String lineFragment, ViolationKind kind,
                                              ReasonCode reasonCode, int count) {
        return new BaselineViolation(path, lineFragment, kind, reasonCode, count);
    }

    private enum ViolationKind {
        DIRECT_OBJECT_CONTROL_SETTER,
        RAW_NATIVE_P2_SIDEKICK_ACCESS,
        DIRECT_LIFECYCLE_OPERATION
    }

    private enum ReasonCode {
        BOSS_OR_CUTSCENE_ESCAPE_HATCH,
        CUTSCENE_SCRIPT,
        PENDING_PARITY_TRIAGE
    }

    private record SourceViolation(String path, String lineFragment, ViolationKind kind) {
        ViolationKey key() {
            return new ViolationKey(path, lineFragment, kind);
        }
    }

    private record BaselineViolation(String path, String lineFragment, ViolationKind kind,
                                     ReasonCode reasonCode, int count) {
        BaselineViolation {
            if (count < 1) {
                throw new IllegalArgumentException("baseline count must be positive");
            }
        }

        ViolationKey key() {
            return new ViolationKey(path, lineFragment, kind);
        }
    }

    private record ViolationKey(String path, String lineFragment, ViolationKind kind)
            implements Comparable<ViolationKey> {
        @Override
        public int compareTo(ViolationKey other) {
            int pathCompare = path.compareTo(other.path);
            if (pathCompare != 0) {
                return pathCompare;
            }
            int kindCompare = kind.compareTo(other.kind);
            if (kindCompare != 0) {
                return kindCompare;
            }
            return lineFragment.compareTo(other.lineFragment);
        }
    }
}
