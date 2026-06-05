package com.openggf.tests;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TestArchitecturalSourceGuard {
    private static final Path SRC_MAIN = Path.of("src", "main", "java");
    private static final String ENGINE_PATH = "com/openggf/Engine.java";
    private static final int ENGINE_MAX_LARGE_METHODS = 3;
    private static final int ENGINE_LARGE_METHOD_THRESHOLD = 100;

    private static final Set<String> GAME_ID_BRANCH_APPROVED_FILES = Set.of(
            "com/openggf/Engine.java",
            "com/openggf/GameLoop.java",
            "com/openggf/game/CrossGameFeatureProvider.java",
            "com/openggf/game/session/GameplayTeamBootstrap.java",
            "com/openggf/game/startup/DataSelectPresentationResolution.java"
    );
    private static final List<String> GAME_ID_BRANCH_APPROVED_PREFIXES = List.of(
            "com/openggf/game/dataselect/"
    );

    private static final Set<String> OBJECT_ART_ALLOWED_LEGACY_NAMES = Set.of(
            "checkpointSheet",
            "checkpointStarSheet",
            "hexBumperSheet",
            "bonusBlockSheet",
            "flipperSheet",
            "speedBoosterSheet",
            "blueBallsSheet",
            "resultsSheet",
            "leavesSheet",
            "checkpointAnimations",
            "flipperAnimations",
            "pipeExitSpringAnimations",
            "tippingFloorAnimations",
            "springboardAnimations"
    );
    private static final Pattern OBJECT_ART_FORBIDDEN_NAME =
            Pattern.compile("(?i).*(sonic|s1|s2|s3k|ghz|lz|mz|slz|syz|sbz|ehz|cpz|arz|cnz|htz|mcz|ooz|mtz|wfz|scz|dez|aiz|hcz|mgz|cnz|icz|lbz|mhz|fbz|soz|lrz|hpz|ssz|ddz|plc|dplc|romAddr|artAddr|mappingAddr|patternBase).*");

    private static final Set<String> COORDINATE_HAZARD_ALLOWED = Set.of(
            "com/openggf/game/sonic2/objects/CheckpointStarInstance.java",
            "com/openggf/game/sonic2/objects/FallingPillarObjectInstance.java",
            "com/openggf/game/sonic2/objects/PipeExitSpringObjectInstance.java",
            "com/openggf/game/sonic3k/objects/Aiz2BossEndSequenceController.java",
            "com/openggf/game/sonic3k/objects/AizEmeraldScatterInstance.java",
            "com/openggf/game/sonic3k/objects/AizHollowTreeObjectInstance.java",
            // Cnz2CutsceneButtonInstance compares the cutscene Knuckles NPC's
            // object position (knuckles.getX/getY) against the button's proximity
            // box — object-to-object, not a player top-left hazard. Same pattern as
            // the allowlisted Hcz2CutsceneButtonInstance / S3kCutsceneButtonObjectInstance.
            "com/openggf/game/sonic3k/objects/Cnz2CutsceneButtonInstance.java",
            "com/openggf/game/sonic3k/objects/Hcz2CutsceneButtonInstance.java",
            "com/openggf/game/sonic3k/objects/HCZLargeFanObjectInstance.java",
            "com/openggf/game/sonic3k/objects/PachinkoFlipperObjectInstance.java",
            "com/openggf/game/sonic3k/objects/S3kCutsceneButtonObjectInstance.java"
    );
    private static final List<String> COORDINATE_SCAN_PREFIXES = List.of(
            "com/openggf/level/objects/",
            "com/openggf/game/sonic1/objects/",
            "com/openggf/game/sonic2/objects/",
            "com/openggf/game/sonic3k/objects/"
    );

    private static final Pattern GAME_ID_BRANCH = Pattern.compile(
            "\\b(?:if|while)\\s*\\([^)]*(?:getGameId\\s*\\(\\s*\\)|\\b\\w*GameId\\b)[^)]*(?:==|!=)[^)]*GameId\\s*\\."
                    + "|\\bswitch\\s*\\([^)]*(?:getGameId\\s*\\(\\s*\\)|\\b\\w*GameId\\b)[^)]*\\)");
    private static final Pattern RUNTIME_DISASM_PATH_LITERAL = Pattern.compile(
            "\"(?:docs/|docs\\\\\\\\)?(?:s1disasm|s2disasm|skdisasm)(?:/|\\\\\\\\)");
    private static final Pattern OBJECT_ART_DECLARATION = Pattern.compile(
            "\\b(?:private\\s+final\\s+[^;=]+|public\\s+[^;=]+|[A-Za-z0-9_<>\\[\\]]+)\\s+(\\w+)\\s*(?:[;,)=]|$)");
    private static final Pattern OBJECT_ART_ACCESSOR = Pattern.compile(
            "\\bpublic\\s+[^;=()]+\\s+(\\w+)\\s*\\(\\s*\\)");
    private static final Pattern PLAYER_TOP_LEFT_ACCESS = Pattern.compile(
            "\\b(?:player|playable|entity|sonic|tails|knuckles)\\s*\\.\\s*get[XY]\\s*\\(\\s*\\)");
    private static final Pattern COORDINATE_CONTEXT = Pattern.compile(
            "(?i)\\b(rom|parity|x_pos|y_pos|distance|threshold|trigger|dx|dy|Math\\s*\\.\\s*abs|>=|<=|>|<)\\b");

    @Test
    void productionGameIdBehaviorBranchesStayInRoutingAndCompositionCode() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : productionFiles()) {
            String relative = relative(file);
            if (isApprovedGameIdBranchFile(relative)) {
                continue;
            }
            String source = stripCommentsAndStrings(Files.readString(file));
            Matcher matcher = GAME_ID_BRANCH.matcher(source);
            while (matcher.find()) {
                violations.add(relative + ":" + lineNumberForOffset(source, matcher.start())
                        + " - branch on GameId outside routing/composition surface");
            }
        }

        assertNoViolations("GameId behavior branches must use feature flags or approved routing/composition code",
                violations);
    }

    @Test
    void runtimeProductionCodeDoesNotReadDisassemblyAssets() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : productionFiles()) {
            String relative = relative(file);
            if (relative.startsWith("com/openggf/tools/")) {
                continue;
            }
            String source = stripComments(Files.readString(file));
            Matcher matcher = RUNTIME_DISASM_PATH_LITERAL.matcher(source);
            while (matcher.find()) {
                violations.add(relative + ":" + lineNumberForOffset(source, matcher.start())
                        + " - runtime read from docs disassembly asset");
            }
        }

        assertNoViolations("Runtime production code must load gameplay assets from ROM, not docs disassembly trees",
                violations);
    }

    @Test
    void engineDoesNotGainNewLargeMethods() throws IOException {
        // Intent: Engine.java shouldn't grow uncontrollably by gaining a new
        // large method. We deliberately do NOT enforce specific line counts on
        // init/display/draw -- that would trip on any legitimate edit and push
        // people toward unrelated cleanup to make room. The structural signal
        // is just "no fourth >=100-line method appears here without review."
        SourceFile engine = SourceFile.read(SRC_MAIN.resolve(ENGINE_PATH));
        List<MethodSpan> largeMethods = engine.methods().stream()
                .filter(method -> method.lineCount() >= ENGINE_LARGE_METHOD_THRESHOLD)
                .toList();
        if (largeMethods.size() > ENGINE_MAX_LARGE_METHODS) {
            List<String> names = largeMethods.stream()
                    .map(method -> method.name() + " (" + method.lineCount() + " lines)")
                    .toList();
            fail("Engine.java now has " + largeMethods.size() + " methods at or above "
                    + ENGINE_LARGE_METHOD_THRESHOLD + " lines; cap is " + ENGINE_MAX_LARGE_METHODS
                    + ". Extract responsibilities into focused collaborators before adding new large methods.\n"
                    + "  Current large methods: " + String.join(", ", names));
        }
    }

    @Test
    void objectArtDataDoesNotGainNewGameOrZoneSpecificSurface() throws IOException {
        String source = stripCommentsAndStrings(Files.readString(
                SRC_MAIN.resolve("com/openggf/level/objects/ObjectArtData.java")));
        List<String> names = objectArtDataSurfaceNames(source);
        List<String> violations = names.stream()
                .filter(name -> OBJECT_ART_FORBIDDEN_NAME.matcher(name).matches())
                .filter(name -> !OBJECT_ART_ALLOWED_LEGACY_NAMES.contains(name))
                .distinct()
                .sorted()
                .toList();

        assertNoViolations("ObjectArtData must stay game-agnostic; add game/zone art to object art providers instead",
                violations);
    }

    @Test
    void sharedObjectArtSplitTypesExistAtProviderBoundary() {
        assertTrue(Files.exists(SRC_MAIN.resolve("com/openggf/level/objects/art/ObjectArtBundle.java")),
                "ObjectArtBundle should be the shared, game-agnostic render data contract");
        assertTrue(Files.exists(SRC_MAIN.resolve("com/openggf/level/objects/art/ObjectArtRegistration.java")),
                "ObjectArtRegistration should hold provider-owned art registration metadata");
    }

    @Test
    void objectArtDataDoesNotExposeSonic2ProviderSpecificFields() throws IOException {
        String source = stripCommentsAndStrings(Files.readString(
                SRC_MAIN.resolve("com/openggf/level/objects/ObjectArtData.java")));
        List<String> names = objectArtDataSurfaceNames(source);
        List<String> forbidden = names.stream()
                .filter(Set.of(
                        "breakableBlockSheet",
                        "cpzPlatformSheet",
                        "cpzStairBlockSheet",
                        "sidewaysPformSheet",
                        "cpzPylonSheet",
                        "pipeExitSpringSheet",
                        "tippingFloorSheet",
                        "barrierSheet",
                        "springboardSheet")::contains)
                .sorted()
                .toList();

        assertEquals(List.of(), forbidden,
                "Sonic 2 conditional/eager sheets belong in Sonic2ObjectArtProvider registrations, not ObjectArtData");
    }

    @Test
    void objectCodeDoesNotAddSuspiciousPlayerTopLeftCoordinateHazards() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : productionFiles()) {
            String relative = relative(file);
            if (!isCoordinateScanned(relative) || COORDINATE_HAZARD_ALLOWED.contains(relative)) {
                continue;
            }
            violations.addAll(scanCoordinateHazards(relative, Files.readString(file)));
        }

        assertNoViolations("Suspicious player.getX()/getY() top-left coordinate use in object code", violations);
    }

    @Test
    void sampleScannerDetectsGameIdBranchButAllowsFeatureFlagBranch() {
        List<String> violations = scanGameIdBranches("sample/Physics.java", """
                class Physics {
                    void bad(GameModule module) {
                        if (module.getGameId() == GameId.S1) runS1();
                    }
                    void good(PhysicsFeatureSet features) {
                        if (features.spindashEnabled()) run();
                    }
                }
                """);

        assertEquals(List.of("sample/Physics.java:3 - branch on GameId outside routing/composition surface"),
                violations);
    }

    @Test
    void sampleScannerDetectsRuntimeDisassemblyAssetRead() {
        List<String> violations = scanRuntimeDisasmReads("sample/RuntimeLoader.java", """
                class RuntimeLoader {
                    byte[] bad() throws Exception {
                        return Files.readAllBytes(Path.of("docs/skdisasm/General/Sprites.bin"));
                    }
                }
                """);

        assertEquals(List.of("sample/RuntimeLoader.java:3 - runtime read from docs disassembly asset"),
                violations);
    }

    @Test
    void sampleScannerDetectsObjectArtDataGameSpecificName() {
        List<String> names = objectArtDataSurfaceNames("""
                class ObjectArtData {
                    private final ObjectSpriteSheet monitorSheet;
                    private final ObjectSpriteSheet ghzBridgeSheet;
                    public ObjectArtData(ObjectSpriteSheet monitorSheet, ObjectSpriteSheet ghzBridgeSheet) {}
                    public ObjectSpriteSheet s3kMonitorSheet() { return null; }
                }
                """);

        List<String> violations = names.stream()
                .filter(name -> OBJECT_ART_FORBIDDEN_NAME.matcher(name).matches())
                .filter(name -> !OBJECT_ART_ALLOWED_LEGACY_NAMES.contains(name))
                .distinct()
                .sorted()
                .toList();

        assertEquals(List.of("ghzBridgeSheet", "s3kMonitorSheet"), violations);
    }

    @Test
    void sampleScannerDetectsCoordinateHazardNearDistanceExpression() {
        List<String> violations = scanCoordinateHazards("sample/Object.java", """
                class Object {
                    void update(PlayableEntity player) {
                        int dx = Math.abs(player.getX() - x);
                    }
                }
                """);

        assertEquals(List.of("sample/Object.java:3 - player.getX() near coordinate hazard context"),
                violations);
    }

    private static List<Path> productionFiles() throws IOException {
        if (!Files.isDirectory(SRC_MAIN)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(SRC_MAIN)) {
            return stream.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static boolean isApprovedGameIdBranchFile(String relative) {
        return GAME_ID_BRANCH_APPROVED_FILES.contains(relative)
                || GAME_ID_BRANCH_APPROVED_PREFIXES.stream().anyMatch(relative::startsWith);
    }

    private static boolean isCoordinateScanned(String relative) {
        return COORDINATE_SCAN_PREFIXES.stream().anyMatch(relative::startsWith);
    }

    private static List<String> scanGameIdBranches(String relative, String source) {
        String stripped = stripCommentsAndStrings(source);
        List<String> violations = new ArrayList<>();
        Matcher matcher = GAME_ID_BRANCH.matcher(stripped);
        while (matcher.find()) {
            violations.add(relative + ":" + lineNumberForOffset(stripped, matcher.start())
                    + " - branch on GameId outside routing/composition surface");
        }
        return violations;
    }

    private static List<String> scanRuntimeDisasmReads(String relative, String source) {
        String stripped = stripComments(source);
        List<String> violations = new ArrayList<>();
        Matcher matcher = RUNTIME_DISASM_PATH_LITERAL.matcher(stripped);
        while (matcher.find()) {
            violations.add(relative + ":" + lineNumberForOffset(stripped, matcher.start())
                    + " - runtime read from docs disassembly asset");
        }
        return violations;
    }

    private static List<String> objectArtDataSurfaceNames(String source) {
        Set<String> names = new HashSet<>();
        Matcher matcher = OBJECT_ART_DECLARATION.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!Set.of("class", "return", "new", "if").contains(name)) {
                names.add(name);
            }
        }
        matcher = OBJECT_ART_ACCESSOR.matcher(source);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names.stream().sorted().toList();
    }

    private static List<String> scanCoordinateHazards(String relative, String source) {
        String stripped = stripCommentsAndStrings(source);
        List<String> violations = new ArrayList<>();
        Matcher matcher = PLAYER_TOP_LEFT_ACCESS.matcher(stripped);
        while (matcher.find()) {
            int start = Math.max(0, matcher.start() - 100);
            int end = Math.min(stripped.length(), matcher.end() + 100);
            String context = stripped.substring(start, end);
            if (COORDINATE_CONTEXT.matcher(context).find()) {
                violations.add(relative + ":" + lineNumberForOffset(stripped, matcher.start())
                        + " - " + matcher.group().replaceAll("\\s+", "") + " near coordinate hazard context");
            }
        }
        return violations;
    }

    private static void assertNoViolations(String message, List<String> violations) {
        if (!violations.isEmpty()) {
            fail(message + ":\n  " + String.join("\n  ", violations));
        }
    }

    private static String relative(Path file) {
        return SRC_MAIN.relativize(file).toString().replace('\\', '/');
    }

    private static String stripCommentsAndStrings(String source) {
        return strip(source, true);
    }

    private static String stripComments(String source) {
        return strip(source, false);
    }

    private static String strip(String source, boolean stripStrings) {
        StringBuilder stripped = new StringBuilder(source.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaping = false;

        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (current == '\n' || current == '\r') {
                    inLineComment = false;
                    stripped.append(current);
                } else {
                    stripped.append(' ');
                }
                continue;
            }
            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    stripped.append("  ");
                    i++;
                    inBlockComment = false;
                } else {
                    stripped.append(current == '\n' || current == '\r' ? current : ' ');
                }
                continue;
            }
            if (inString) {
                stripped.append(stripStrings && current != '\n' && current != '\r' && current != '"' ? ' ' : current);
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                stripped.append(stripStrings && current != '\n' && current != '\r' && current != '\'' ? ' ' : current);
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (current == '/' && next == '/') {
                stripped.append("  ");
                i++;
                inLineComment = true;
                continue;
            }
            if (current == '/' && next == '*') {
                stripped.append("  ");
                i++;
                inBlockComment = true;
                continue;
            }
            if (current == '"') {
                inString = true;
                stripped.append(current);
                continue;
            }
            if (current == '\'') {
                inChar = true;
                stripped.append(current);
                continue;
            }
            stripped.append(current);
        }
        return stripped.toString();
    }

    private static int lineNumberForOffset(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private record SourceFile(String text, List<String> lines) {
        private static final Pattern METHOD_START = Pattern.compile(
                "^\\s*(?:public|private|protected)\\s+(?:static\\s+)?(?:synchronized\\s+)?[\\w<>\\[\\].?, ]+\\s+(\\w+)\\s*\\([^;]*\\)\\s*\\{\\s*$");

        static SourceFile read(Path path) throws IOException {
            return new SourceFile(Files.readString(path), Files.readAllLines(path));
        }

        List<MethodSpan> methods() {
            List<MethodSpan> methods = new ArrayList<>();
            boolean inMethod = false;
            int startLine = 0;
            int braceDepth = 0;
            String methodName = "";

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (!inMethod) {
                    Matcher matcher = METHOD_START.matcher(line);
                    if (matcher.matches()) {
                        inMethod = true;
                        startLine = i + 1;
                        methodName = matcher.group(1);
                        braceDepth = braceDelta(line);
                    }
                } else {
                    braceDepth += braceDelta(line);
                    if (braceDepth <= 0) {
                        methods.add(new MethodSpan(methodName, startLine, i + 1));
                        inMethod = false;
                    }
                }
            }
            return methods;
        }

        private static int braceDelta(String line) {
            int delta = 0;
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == '{') {
                    delta++;
                } else if (line.charAt(i) == '}') {
                    delta--;
                }
            }
            return delta;
        }
    }

    private record MethodSpan(String name, int startLine, int endLine) {
        int lineCount() {
            return endLine - startLine + 1;
        }
    }
}
