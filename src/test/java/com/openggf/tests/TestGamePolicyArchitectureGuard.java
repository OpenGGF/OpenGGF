package com.openggf.tests;

import com.openggf.audio.GameAudioProfile;
import com.openggf.game.GameModule;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rules.GamePolicies;
import com.openggf.game.rules.GameRules;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps game-wide policy ownership on {@link GameModule#getPolicies()}.
 *
 * <p>See {@code docs/architecture/designs/2026-11-15-game-policies.md} and the
 * admission checklist in {@code docs/architecture/per-game-rule-placement.md}.
 * Each allow-list here is a ceiling: shrink it, do not grow it without an
 * architecture note.
 */
class TestGamePolicyArchitectureGuard {

    private static final Path SRC_MAIN = Path.of("src", "main", "java");

    /** Composition code that may name the aggregate; consumers read narrow records. */
    private static final Set<String> GAME_POLICIES_COMPOSITION_FILES = Set.of(
            "com/openggf/game/GameModule.java",
            "com/openggf/game/patch/DelegatingGameModule.java",
            "com/openggf/game/AbstractStandaloneGameModule.java",
            "com/openggf/game/StandaloneGameSpec.java",
            "com/openggf/game/CrossGameFeatureProvider.java",
            "com/openggf/game/rules/GamePolicies.java",
            "com/openggf/game/rules/CrossGamePolicyComposer.java",
            "com/openggf/game/sonic1/Sonic1GameModule.java",
            "com/openggf/game/sonic2/Sonic2GameModule.java",
            "com/openggf/game/sonic2/kis2/Kis2GameModule.java",
            "com/openggf/game/sonic3k/Sonic3kGameModule.java",
            "com/openggf/game/soniccd/SonicCdGameModule.java");

    /** Existing zone-owned providers and the hardware-timing work record; no new bag names. */
    private static final Set<String> FEATURE_NAMED_TYPES = Set.of(
            "Sonic2CompetitionZoneFeatures",
            "S3kCompetitionZoneFeatures",
            "CdZoneFeatures",
            "HardwareWorkFeatures");

    private static final Pattern FEATURE_BAG_DECLARATION = Pattern.compile(
            "\\b(?:class|record|interface|enum)\\s+(\\w*(?:FeatureSet|FeatureFlags|FeatureBag|Features|Toggles|Switches))\\b");

    /** Shared runtime: no game-code branches here, whatever form the game name takes. */
    private static final List<String> SHARED_RUNTIME_PREFIXES = List.of(
            "com/openggf/level/",
            "com/openggf/sprites/",
            "com/openggf/physics/",
            "com/openggf/camera/",
            "com/openggf/timer/",
            "com/openggf/graphics/",
            "com/openggf/game/rules/",
            "com/openggf/audio/");

    /** The standalone audio tool host validates its own command-line game argument. */
    private static final Set<String> SHARED_RUNTIME_EXEMPT_PREFIXES = Set.of("com/openggf/audio/debug/");

    private static final String GAME_CODE = "\"(?:s1|s2|s3k|scd|kis2|sonic1|sonic2|sonic3k|soniccd)\"";
    private static final Pattern GAME_CODE_BRANCH = Pattern.compile(
            GAME_CODE + "\\s*\\.\\s*equals(?:IgnoreCase)?\\s*\\("
                    + "|\\.\\s*equals(?:IgnoreCase)?\\s*\\(\\s*" + GAME_CODE + "\\s*\\)"
                    + "|\\bcase\\s+" + GAME_CODE
                    + "|(?:==|!=)\\s*" + GAME_CODE
                    + "|\\binstanceof\\s+(?:[\\w.]+\\.)?(?:Sonic1|Sonic2|Sonic3k|SonicCd|Kis2)GameModule\\b");

    @Test
    void moduleIsTheOnlyPolicyOwner() throws Exception {
        assertEquals(GamePolicies.class, GameModule.class.getMethod("getPolicies").getReturnType());
        for (Class<?> type : List.of(GameModule.class, DelegatingGameModule.class,
                PhysicsProvider.class, GameAudioProfile.class)) {
            for (Method method : type.getMethods()) {
                assertTrue(!method.getName().equals("getRules"), type.getSimpleName() + " exposes " + method);
                if (method.getReturnType() == GameRules.class) {
                    throw new AssertionError(type.getSimpleName() + " hands out GameRules directly: " + method);
                }
            }
        }
        for (Method method : PhysicsProvider.class.getMethods()) {
            assertTrue(method.getReturnType() != GamePolicies.class, "physics supplies policies: " + method);
        }
        for (Method method : GameAudioProfile.class.getMethods()) {
            assertTrue(method.getReturnType() != GamePolicies.class, "audio factory supplies policies: " + method);
        }
    }

    @Test
    void policyAggregateComponentsArePinned() {
        List<String> components = Arrays.stream(GamePolicies.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("rules"), components,
                "a new policy category needs an architecture note and the rule-placement checklist");
    }

    /**
     * A module-level accessor returning a record of switches would be a parallel rule
     * source. Records with six or more boolean components returned from GameModule are
     * rule bags; game-wide divergences go to the narrow GameRules groups instead.
     */
    @Test
    void gameModuleExposesNoNewBroadSwitchRecord() {
        List<String> violations = new ArrayList<>();
        for (Method method : GameModule.class.getMethods()) {
            Class<?> type = method.getReturnType();
            if (!type.isRecord() || type == GamePolicies.class) continue;
            long booleans = Arrays.stream(type.getRecordComponents())
                    .filter(component -> component.getType() == boolean.class)
                    .count();
            if (booleans >= 6) violations.add(method.getName() + " -> " + type.getName() + " (" + booleans + ")");
        }
        assertEquals(List.of(), violations, "GameModule switch records act as broad feature bags");
    }

    @Test
    void noBroadFeatureBagTypesAreDeclared() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : productionFiles()) {
            String source = stripComments(Files.readString(file));
            Matcher matcher = FEATURE_BAG_DECLARATION.matcher(source);
            while (matcher.find()) {
                if (!FEATURE_NAMED_TYPES.contains(matcher.group(1))) {
                    violations.add(relative(file) + ": " + matcher.group(1));
                }
            }
        }
        assertEquals(List.of(), violations,
                "use the narrowest GameRules group, provider or zone owner instead of a feature bag");
    }

    @Test
    void sharedRuntimeHasNoGameNameBranches() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : productionFiles()) {
            String relative = relative(file);
            if (SHARED_RUNTIME_PREFIXES.stream().noneMatch(relative::startsWith)
                    || SHARED_RUNTIME_EXEMPT_PREFIXES.stream().anyMatch(relative::startsWith)) {
                continue;
            }
            violations.addAll(scanGameNameBranches(relative, Files.readString(file)));
        }
        assertEquals(List.of(), violations, "shared runtime must read typed policies, not game names");
    }

    @Test
    void scannerDetectsEachGameNameBranchForm() {
        String source = """
                class Sample {
                    void f(String game, Object module) {
                        if ("s3k".equals(game)) a();
                        if (game.equals("s1")) a();
                        switch (game) { case "scd" -> a(); default -> b(); }
                        if (module instanceof com.openggf.game.sonic2.Sonic2GameModule) a();
                        // "s2".equals(game) in a comment is not code
                        if (rules.ring().checkpointRestoresSavedRings()) a();
                    }
                }
                """;
        assertEquals(List.of("sample/Sample.java:3", "sample/Sample.java:4", "sample/Sample.java:5",
                "sample/Sample.java:6"), scanGameNameBranches("sample/Sample.java", source));
    }

    @Test
    void gamePoliciesTypeIsNamedOnlyByCompositionCode() throws IOException {
        Pattern reference = Pattern.compile("\\bGamePolicies\\b");
        List<String> violations = new ArrayList<>();
        for (Path file : productionFiles()) {
            String relative = relative(file);
            if (GAME_POLICIES_COMPOSITION_FILES.contains(relative)) continue;
            String source = stripCommentsAndStrings(Files.readString(file));
            if (reference.matcher(source).find()) violations.add(relative);
        }
        assertEquals(List.of(), violations,
                "consumers depend on the narrow rule record they read, not on GamePolicies");
    }

    @Test
    void ruleCompositionHelpersStayWithTheirOwners() throws IOException {
        Pattern derivation = Pattern.compile("\\bCrossGameRuleComposer\\s*\\.\\s*(withPlayerRules|withPlayerAndCameraRules)\\s*\\(");
        Pattern donation = Pattern.compile("\\bCrossGameRuleComposer\\s*\\.\\s*compose\\s*\\(");
        Set<String> derivationOwners = Set.of(
                "com/openggf/game/sonic2/kis2/Kis2Rules.java",
                "com/openggf/game/soniccd/player/SonicCdRules.java");
        List<String> violations = new ArrayList<>();
        for (Path file : productionFiles()) {
            String relative = relative(file);
            String source = stripCommentsAndStrings(Files.readString(file));
            if (!derivationOwners.contains(relative) && derivation.matcher(source).find()) {
                violations.add(relative + " derives stock rules outside a stock rule factory");
            }
            if (!relative.equals("com/openggf/game/rules/CrossGamePolicyComposer.java")
                    && donation.matcher(source).find()) {
                violations.add(relative + " donates rules outside CrossGamePolicyComposer");
            }
        }
        assertEquals(List.of(), violations);
    }

    private static List<String> scanGameNameBranches(String relative, String source) {
        String code = stripComments(source);
        List<String> violations = new ArrayList<>();
        Matcher matcher = GAME_CODE_BRANCH.matcher(code);
        while (matcher.find()) {
            violations.add(relative + ":" + lineOf(code, matcher.start()));
        }
        return violations;
    }

    private static List<Path> productionFiles() throws IOException {
        try (Stream<Path> stream = Files.walk(SRC_MAIN)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static String relative(Path file) {
        return SRC_MAIN.relativize(file).toString().replace('\\', '/');
    }

    private static int lineOf(String source, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (source.charAt(i) == '\n') line++;
        }
        return line;
    }

    /** Removes comments, keeping string literals and line structure. */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '"' || c == '\'') {
                int end = literalEnd(source, i);
                out.append(source, i, end);
                i = end;
            } else if (source.startsWith("//", i)) {
                while (i < source.length() && source.charAt(i) != '\n') i++;
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                end = end < 0 ? source.length() : end + 2;
                for (int k = i; k < end; k++) if (source.charAt(k) == '\n') out.append('\n');
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Removes comments and blanks literals, keeping line structure. */
    private static String stripCommentsAndStrings(String source) {
        String code = stripComments(source);
        StringBuilder out = new StringBuilder(code.length());
        int i = 0;
        while (i < code.length()) {
            char c = code.charAt(i);
            if (c == '"' || c == '\'') {
                int end = literalEnd(code, i);
                for (int k = i; k < end; k++) out.append(code.charAt(k) == '\n' ? '\n' : ' ');
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int literalEnd(String source, int start) {
        if (source.startsWith("\"\"\"", start)) {
            int end = source.indexOf("\"\"\"", start + 3);
            return end < 0 ? source.length() : end + 3;
        }
        char quote = source.charAt(start);
        int i = start + 1;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote || c == '\n') {
                return i + 1;
            } else {
                i++;
            }
        }
        return source.length();
    }
}
