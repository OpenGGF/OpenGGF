package com.openggf.tests.game;

import com.openggf.game.rules.CameraRules;
import com.openggf.game.rules.CollisionRules;
import com.openggf.game.rules.DrowningBubbleRules;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rules.ObjectInteractionRules;
import com.openggf.game.rules.PlayerAnimationRules;
import com.openggf.game.rules.PlayerCapabilityRules;
import com.openggf.game.rules.PlayerMovementRules;
import com.openggf.game.rules.PowerUpRules;
import com.openggf.game.rules.RingRules;
import com.openggf.game.rules.SidekickCpuRules;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPerGameRuleArchitectureGuard {

    private static final Path MAIN_SOURCE_ROOT = Path.of("src/main/java");
    private static final Path BRIDGE_BASELINE =
            Path.of("src/test/resources/architecture/physics-feature-set-bridge-baseline.txt");
    private static final Pattern LEGACY_FEATURE_SET_USAGE =
            Pattern.compile("\\bgetPhysicsFeatureSet\\s*\\(|\\bgetFeatureSet\\s*\\(|\\bPhysicsFeatureSet\\b");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//.*$", Pattern.MULTILINE);
    private static final int MAX_RULE_COMPONENTS = 20;
    // Existing migration surface: keep it frozen until the next split, and do not let other groups grow this large.
    private static final Map<Class<? extends Record>, Integer> FROZEN_RULE_COMPONENT_LIMITS = Map.of(
            PlayerMovementRules.class, 21
    );

    private static final List<String> FEATURE_SET_OWNERS = List.of(
            "src/main/java/com/openggf/game/rules/",
            "src/main/java/com/openggf/game/PhysicsFeatureSet.java",
            "src/main/java/com/openggf/game/PhysicsProvider.java",
            "src/main/java/com/openggf/game/PlayableEntity.java",
            "src/main/java/com/openggf/game/sonic1/Sonic1PhysicsProvider.java",
            "src/main/java/com/openggf/game/sonic2/Sonic2PhysicsProvider.java",
            "src/main/java/com/openggf/game/sonic3k/Sonic3kPhysicsProvider.java"
    );

    private static final List<Class<? extends Record>> RULE_RECORDS = List.of(
            GameRules.class,
            PlayerMovementRules.class,
            PlayerCapabilityRules.class,
            CollisionRules.class,
            PlayerAnimationRules.class,
            CameraRules.class,
            RingRules.class,
            ObjectInteractionRules.class,
            SidekickCpuRules.class,
            PowerUpRules.class,
            DrowningBubbleRules.class
    );

    @Test
    void noNewRuntimePhysicsFeatureSetBridgeUsers() throws IOException {
        Set<String> expected = loadBaseline();
        Set<String> actual = findLegacyFeatureSetUsers();

        assertEquals(expected, actual, () -> """
                Runtime code should consume typed GameRules groups instead of adding broad PhysicsFeatureSet users.
                If a file disappeared, remove it from the baseline. If a file appeared, migrate it to the narrowest
                typed rule/provider/profile owner or document the bridge-era exception before updating the baseline.
                """);
    }

    @Test
    void typedRuleRecordsStaySmallEnoughToReview() {
        for (Class<? extends Record> ruleRecord : RULE_RECORDS) {
            RecordComponent[] components = ruleRecord.getRecordComponents();

            Integer frozenComponents = FROZEN_RULE_COMPONENT_LIMITS.get(ruleRecord);
            if (frozenComponents != null) {
                assertEquals(frozenComponents, components.length,
                        () -> ruleRecord.getSimpleName() + " has changed size; split the group or update the"
                                + " explicit frozen migration limit with architecture review");
                continue;
            }

            assertTrue(components.length <= MAX_RULE_COMPONENTS,
                    () -> ruleRecord.getSimpleName() + " has " + components.length
                            + " components; split it into narrower rule groups before increasing this limit");
        }
    }

    private static Set<String> findLegacyFeatureSetUsers() throws IOException {
        Set<String> result = new TreeSet<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCE_ROOT)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(TestPerGameRuleArchitectureGuard::normalize)
                    .filter(path -> FEATURE_SET_OWNERS.stream().noneMatch(path::startsWith))
                    .filter(TestPerGameRuleArchitectureGuard::containsLegacyFeatureSetUsage)
                    .forEach(result::add);
        }
        return result;
    }

    private static boolean containsLegacyFeatureSetUsage(String normalizedPath) {
        try {
            String source = stripComments(Files.readString(Path.of(normalizedPath)));
            return LEGACY_FEATURE_SET_USAGE.matcher(source).find();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to scan " + normalizedPath, e);
        }
    }

    private static String stripComments(String source) {
        String withoutBlockComments = BLOCK_COMMENT.matcher(source).replaceAll("");
        return LINE_COMMENT.matcher(withoutBlockComments).replaceAll("");
    }

    private static Set<String> loadBaseline() throws IOException {
        Set<String> result = new TreeSet<>();
        for (String line : Files.readAllLines(BRIDGE_BASELINE)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }
}
