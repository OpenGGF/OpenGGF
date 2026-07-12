package com.openggf.game.patch;

import com.openggf.tests.ObjectGuardSourceScanner;
import com.openggf.tests.ObjectGuardSourceScanner.SourceText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the bootstrap-only module accessors from becoming an invisible bypass
 * around session-resolved game-module patches.
 */
class TestBootstrapModuleProviderCachingGuard {

    private static final List<String> BOOTSTRAP_CAPABLE_ACCESSORS = List.of(
            "bootstrapGameModule(",
            "getBootstrapDefault(",
            "currentOrBootstrapGameModule(");

    private static final Pattern METHOD_DECLARATION = Pattern.compile(
            "^\\s*(?:(?:public|protected|private|static|final|synchronized)\\s+)*"
                    + "[\\w<>\\[\\],.?]+\\s+(\\w+)\\s*\\((?:[^;]*$|[^)]*\\)\\s*\\{.*$)");
    private static final Pattern CONSTRUCTOR_DECLARATION = Pattern.compile(
            "^\\s*(?:(?:public|protected|private)\\s+)+(\\w+)\\s*\\([^;]*$");

    /**
     * Exact source-line approvals. Whole classes are never exempt: a new call,
     * a differently shaped call, or a moved responsibility fails the guard.
     */
    private static final List<ApprovedCallSite> APPROVED_CALL_SITES = List.of(
            approved("com.openggf.game.GameModuleRegistry", "getBootstrapDefault", "getBootstrapDefault(",
                    "public static synchronized GameModule getBootstrapDefault() {",
                    "definition of the pre-session compatibility accessor"),
            approved("com.openggf.game.GameServices", "bootstrapGameModule", "bootstrapGameModule(",
                    "public static GameModule bootstrapGameModule() {",
                    "definition of the pre-session compatibility accessor"),
            approved("com.openggf.game.GameServices", "bootstrapGameModule", "getBootstrapDefault(",
                    "return GameModuleRegistry.getBootstrapDefault();",
                    "single composition bridge to the bootstrap registry"),
            approved("com.openggf.game.GameServices", "currentOrBootstrapGameModule", "currentOrBootstrapGameModule(",
                    "public static GameModule currentOrBootstrapGameModule() {",
                    "definition that prefers the active WorldSession module"),
            approved("com.openggf.game.GameServices", "currentOrBootstrapGameModule", "bootstrapGameModule(",
                    "return bootstrapGameModule();",
                    "pre-session fallback after the active WorldSession check"),
            approved("com.openggf.game.CrossGameFeatureProvider", "resolveHostGameId", "currentOrBootstrapGameModule(",
                    "return GameServices.currentOrBootstrapGameModule().getGameId();",
                    "transient game-id read; no provider or module is cached"),
            approved("com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider", "renderAfterBackground",
                    "currentOrBootstrapGameModule(",
                    "&& GameServices.currentOrBootstrapGameModule().getBonusStageProvider()",
                    "transient render-time provider read; active gameplay resolves the session module"),
            approved("com.openggf.integration.presence.RuntimePresenceSnapshotProvider",
                    "menuGameName", "currentOrBootstrapGameModule(",
                    "GameModule module = GameServices.currentOrBootstrapGameModule();",
                    "transient menu/presence snapshot read; no provider or module is retained"),
            approved("com.openggf.level.LevelManager", "activeGameModule", "currentOrBootstrapGameModule(",
                    "return GameServices.currentOrBootstrapGameModule();",
                    "last-resort transient read after instance and WorldSession module checks"),
            approved("com.openggf.level.rings.RingManager", "RingManager", "currentOrBootstrapGameModule(",
                    "GameModule module = GameServices.currentOrBootstrapGameModule();",
                    "gameplay construction occurs after WorldSession resolution; direct construction is test compatibility"),
            approved("com.openggf.level.rings.RingManager", "playerRingRules", "currentOrBootstrapGameModule(",
                    "return moduleRingRules(GameServices.currentOrBootstrapGameModule());",
                    "null-player transient fallback; no module/provider is cached"),
            approved("com.openggf.level.rings.RingManager", "playerRingRules", "currentOrBootstrapGameModule(",
                    "return moduleRingRules(GameServices.currentOrBootstrapGameModule());",
                    "missing-player-rule transient fallback; no module/provider is cached"),
            approved("com.openggf.level.rings.RingManager", "playerCapabilityRules", "currentOrBootstrapGameModule(",
                    "return modulePlayerCapabilityRules(GameServices.currentOrBootstrapGameModule());",
                    "null-player transient fallback; no module/provider is cached"),
            approved("com.openggf.level.rings.RingManager", "playerCapabilityRules", "currentOrBootstrapGameModule(",
                    "return modulePlayerCapabilityRules(GameServices.currentOrBootstrapGameModule());",
                    "missing-player-rule transient fallback; no module/provider is cached"),
            approved("com.openggf.sprites.playable.AbstractPlayableSprite", "resetState",
                    "bootstrapGameModule(",
                    "resolvePhysicsProfile(GameServices.bootstrapGameModule());",
                    "level-reset physics bootstrap is rebound by refreshRuntimeBoundStateIfNeeded"),
            approved("com.openggf.sprites.playable.AbstractPlayableSprite", "AbstractPlayableSprite",
                    "bootstrapGameModule(",
                    "resolvePhysicsProfile(GameServices.bootstrapGameModule());",
                    "pre-session constructor physics bootstrap is rebound by refreshRuntimeBoundStateIfNeeded"),
            approved("com.openggf.sprites.playable.AbstractPlayableSprite", "currentGameModule",
                    "currentOrBootstrapGameModule(",
                    "public final GameModule currentGameModule() { return PlayableSpriteRuntimeServices.currentOrBootstrapGameModule(); }",
                    "runtime module read used by the explicit physics-provider rebind path"),
            approved("com.openggf.sprites.playable.PlayableSpriteRuntimeServices",
                    "currentOrBootstrapGameModule", "currentOrBootstrapGameModule(",
                    "static GameModule currentOrBootstrapGameModule() { return GameServices.currentOrBootstrapGameModule(); }",
                    "definition of the thin sprite service bridge"),
            approved("com.openggf.sprites.playable.PlayableSpriteRuntimeServices",
                    "currentOrBootstrapGameModule", "currentOrBootstrapGameModule(",
                    "static GameModule currentOrBootstrapGameModule() { return GameServices.currentOrBootstrapGameModule(); }",
                    "thin sprite service bridge; active gameplay prefers the WorldSession module"),
            approved("com.openggf.trace.replay.TraceReplaySessionBootstrap", "resolveCurrentLevelStart",
                    "currentOrBootstrapGameModule(",
                    "var module = GameServices.currentOrBootstrapGameModule();",
                    "transient trace start-position read after session bootstrap; no provider is cached")
    );

    @Test
    void productionBootstrapCapableModuleAccessesAreExplicitlyAudited() throws IOException {
        Path srcMain = ObjectGuardSourceScanner.findSourceRoot();
        if (srcMain == null) {
            return;
        }

        List<ModuleAccess> accesses = collectBootstrapCapableAccesses(srcMain);
        List<String> violations = findUnapprovedAccesses(accesses, APPROVED_CALL_SITES).stream()
                .map(ModuleAccess::description)
                .toList();

        if (!violations.isEmpty()) {
            fail("Bootstrap-capable module access must be audited. Resolve through the active WorldSession, "
                    + "or document a pre-session/transient/rebinding seam in APPROVED_CALL_SITES.\n\n  "
                    + String.join("\n  ", violations));
        }
    }

    @Test
    void approvedCallSitesStillMatchProductionSource() throws IOException {
        Path srcMain = ObjectGuardSourceScanner.findSourceRoot();
        if (srcMain == null) {
            return;
        }

        List<ModuleAccess> accesses = collectBootstrapCapableAccesses(srcMain);
        List<ModuleAccess> remaining = new ArrayList<>(accesses);
        for (ApprovedCallSite approved : APPROVED_CALL_SITES) {
            int match = indexOfMatch(remaining, approved);
            assertTrue(match >= 0, () -> "Remove or update stale bootstrap access approval: " + approved);
            remaining.remove(match);
        }
    }

    @Test
    void scanDetectsBootstrapProviderCachingThroughSplitLocalAssignment(@TempDir Path tempDir) throws IOException {
        Path srcMain = writeFixture(tempDir, "SplitAssignment.java", """
                package fixture;
                final class SplitAssignment {
                    Object cache() {
                        var module = GameServices.currentOrBootstrapGameModule();
                        return module.getPhysicsProvider();
                    }
                }
                """);

        List<ModuleAccess> accesses = collectBootstrapCapableAccesses(srcMain);

        assertEquals(1, accesses.size());
        assertEquals("currentOrBootstrapGameModule(", accesses.getFirst().accessor());
    }

    @Test
    void scanDetectsBootstrapProviderCachingThroughHelperCall(@TempDir Path tempDir) throws IOException {
        Path srcMain = writeFixture(tempDir, "HelperCall.java", """
                package fixture;
                final class HelperCall {
                    Object cache() {
                        return module().getPhysicsProvider();
                    }
                    Object module() {
                        return GameServices.bootstrapGameModule();
                    }
                }
                """);

        List<ModuleAccess> accesses = collectBootstrapCapableAccesses(srcMain);

        assertEquals(1, accesses.size());
        assertEquals("bootstrapGameModule(", accesses.getFirst().accessor());
    }

    @Test
    void scanDetectsWhitespaceCommentsAndMethodReferences(@TempDir Path tempDir) throws IOException {
        Path srcMain = writeFixture(tempDir, "LexicalForms.java", """
                package fixture;
                final class LexicalForms {
                    Object spaced() {
                        return GameServices.bootstrapGameModule /* audited formatting */ ();
                    }
                    Object multiline() {
                        return GameServices.currentOrBootstrapGameModule
                                ();
                    }
                    Object reference() {
                        return (java.util.function.Supplier<Object>) GameServices::bootstrapGameModule;
                    }
                }
                """);

        List<ModuleAccess> accesses = collectBootstrapCapableAccesses(srcMain);

        assertEquals(3, accesses.size());
    }

    @Test
    void scanIgnoresCommentsStringsAndLongerIdentifiers(@TempDir Path tempDir) throws IOException {
        Path srcMain = writeFixture(tempDir, "NonCodeTokens.java", """
                package fixture;
                final class NonCodeTokens {
                    String text = "bootstrapGameModule(";
                    void safe() {
                        // GameServices.currentOrBootstrapGameModule();
                        Object value = null; // GameServices.getBootstrapDefault();
                        notbootstrapGameModule();
                    }
                    void notbootstrapGameModule() {}
                }
                """);

        List<ModuleAccess> accesses = collectBootstrapCapableAccesses(srcMain);

        assertTrue(accesses.isEmpty(), () -> "Expected non-code/longer tokens to be ignored, got: " + accesses);
    }

    @Test
    void oneApprovalDoesNotAuthorizeASecondIdenticalAccess() {
        ModuleAccess first = new ModuleAccess("fixture.Duplicate", "sameMethod", 10,
                "currentOrBootstrapGameModule(",
                "return GameServices.currentOrBootstrapGameModule();");
        ModuleAccess second = new ModuleAccess("fixture.Duplicate", "sameMethod", 20,
                "currentOrBootstrapGameModule(",
                "return GameServices.currentOrBootstrapGameModule();");
        ApprovedCallSite approval = approved("fixture.Duplicate", "sameMethod", "currentOrBootstrapGameModule(",
                "return GameServices.currentOrBootstrapGameModule();",
                "first audited access only");

        List<ModuleAccess> violations = findUnapprovedAccesses(List.of(first, second), List.of(approval));

        assertEquals(List.of(second), violations);
    }

    private static Path writeFixture(Path tempDir, String fileName, String source) throws IOException {
        Path srcMain = tempDir.resolve("src/main/java");
        Path fixture = srcMain.resolve("fixture").resolve(fileName);
        Files.createDirectories(fixture.getParent());
        Files.writeString(fixture, source);
        return srcMain;
    }

    private static List<ModuleAccess> collectBootstrapCapableAccesses(Path srcMain) throws IOException {
        List<ModuleAccess> accesses = new ArrayList<>();
        try (Stream<Path> files = Files.walk(srcMain)) {
            for (Path sourceFile : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                SourceText source = ObjectGuardSourceScanner.sourceWithoutCommentOnlyLines(
                        Files.readAllLines(sourceFile));
                String code = maskCommentsAndLiterals(source.text());
                String className = ObjectGuardSourceScanner.className(srcMain, sourceFile);
                for (String accessor : BOOTSTRAP_CAPABLE_ACCESSORS) {
                    String methodName = accessor.substring(0, accessor.length() - 1);
                    Matcher tokens = Pattern.compile(
                            "(?<![A-Za-z0-9_$])" + Pattern.quote(methodName) + "(?![A-Za-z0-9_$])")
                            .matcher(code);
                    while (tokens.find()) {
                        int match = tokens.start();
                        if (!isInvocationOrMethodReference(code, tokens.start(), tokens.end())) {
                            continue;
                        }
                        int lineNumber = source.lineAt(match);
                        accesses.add(new ModuleAccess(className, enclosingMethod(source.lines(), lineNumber),
                                lineNumber, accessor,
                                source.lineTextAt(match).trim()));
                    }
                }
            }
        }
        return accesses;
    }

    private static boolean isInvocationOrMethodReference(String code, int start, int end) {
        int after = end;
        while (after < code.length() && Character.isWhitespace(code.charAt(after))) {
            after++;
        }
        if (after < code.length() && code.charAt(after) == '(') {
            return true;
        }

        int before = start - 1;
        while (before >= 0 && Character.isWhitespace(code.charAt(before))) {
            before--;
        }
        return before >= 1 && code.charAt(before) == ':' && code.charAt(before - 1) == ':';
    }

    /** Masks comments and literals with spaces while preserving length/newlines for source attribution. */
    private static String maskCommentsAndLiterals(String source) {
        StringBuilder masked = new StringBuilder(source);
        LexicalState state = LexicalState.CODE;
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            char nextNext = i + 2 < source.length() ? source.charAt(i + 2) : '\0';

            if (state == LexicalState.CODE) {
                if (current == '/' && next == '/') {
                    masked.setCharAt(i, ' ');
                    masked.setCharAt(++i, ' ');
                    state = LexicalState.LINE_COMMENT;
                } else if (current == '/' && next == '*') {
                    masked.setCharAt(i, ' ');
                    masked.setCharAt(++i, ' ');
                    state = LexicalState.BLOCK_COMMENT;
                } else if (current == '"' && next == '"' && nextNext == '"') {
                    masked.setCharAt(i, ' ');
                    masked.setCharAt(++i, ' ');
                    masked.setCharAt(++i, ' ');
                    state = LexicalState.TEXT_BLOCK;
                } else if (current == '"') {
                    masked.setCharAt(i, ' ');
                    state = LexicalState.STRING;
                } else if (current == '\'') {
                    masked.setCharAt(i, ' ');
                    state = LexicalState.CHARACTER;
                }
                continue;
            }

            if (current == '\n' || current == '\r') {
                if (state == LexicalState.LINE_COMMENT) {
                    state = LexicalState.CODE;
                }
                continue;
            }
            masked.setCharAt(i, ' ');

            if (state == LexicalState.BLOCK_COMMENT && current == '*' && next == '/') {
                masked.setCharAt(++i, ' ');
                state = LexicalState.CODE;
            } else if (state == LexicalState.TEXT_BLOCK
                    && current == '"' && next == '"' && nextNext == '"') {
                masked.setCharAt(++i, ' ');
                masked.setCharAt(++i, ' ');
                state = LexicalState.CODE;
            } else if ((state == LexicalState.STRING || state == LexicalState.CHARACTER)
                    && current == '\\' && next != '\0') {
                masked.setCharAt(++i, ' ');
            } else if (state == LexicalState.STRING && current == '"') {
                state = LexicalState.CODE;
            } else if (state == LexicalState.CHARACTER && current == '\'') {
                state = LexicalState.CODE;
            }
        }
        return masked.toString();
    }

    private enum LexicalState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER,
        TEXT_BLOCK
    }

    private static List<ModuleAccess> findUnapprovedAccesses(
            List<ModuleAccess> accesses, List<ApprovedCallSite> approvals) {
        List<ApprovedCallSite> remaining = new ArrayList<>(approvals);
        List<ModuleAccess> violations = new ArrayList<>();
        for (ModuleAccess access : accesses) {
            int match = indexOfMatch(remaining, access);
            if (match >= 0) {
                remaining.remove(match);
            } else {
                violations.add(access);
            }
        }
        return violations;
    }

    private static int indexOfMatch(List<ApprovedCallSite> approvals, ModuleAccess access) {
        for (int i = 0; i < approvals.size(); i++) {
            if (approvals.get(i).matches(access)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfMatch(List<ModuleAccess> accesses, ApprovedCallSite approved) {
        for (int i = 0; i < accesses.size(); i++) {
            if (approved.matches(accesses.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String enclosingMethod(List<String> lines, int oneBasedLineNumber) {
        for (int i = oneBasedLineNumber - 1; i >= 0; i--) {
            String line = lines.get(i);
            Matcher method = METHOD_DECLARATION.matcher(line);
            if (method.matches()) {
                return method.group(1);
            }
            Matcher constructor = CONSTRUCTOR_DECLARATION.matcher(line);
            if (constructor.matches()) {
                return constructor.group(1);
            }
        }
        return "<type>";
    }

    private record ModuleAccess(
            String className, String enclosingMethod, int lineNumber, String accessor, String lineText) {
        private String description() {
            return className + "#" + enclosingMethod + ":" + lineNumber
                    + " uses " + accessor + " in `" + lineText + "`";
        }
    }

    private record ApprovedCallSite(
            String className, String enclosingMethod, String accessor, String sourceLine, String reason) {
        private ApprovedCallSite {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Bootstrap access approval must document a reason");
            }
        }

        private boolean matches(ModuleAccess access) {
            return className.equals(access.className())
                    && enclosingMethod.equals(access.enclosingMethod())
                    && accessor.equals(access.accessor())
                    && sourceLine.equals(access.lineText());
        }
    }

    private static ApprovedCallSite approved(
            String className, String enclosingMethod, String accessor, String sourceLine, String reason) {
        return new ApprovedCallSite(className, enclosingMethod, accessor, sourceLine, reason);
    }
}
