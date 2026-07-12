package com.openggf.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestStandaloneGameCodeRouting {
    @Test
    void builtinModulesDefaultGameCodeToGameIdCode() {
        GameModule module = mock(GameModule.class, CALLS_REAL_METHODS);
        when(module.getGameId()).thenReturn(GameId.S2);

        assertEquals("s2", module.getGameCode());
    }

    @Test
    void standaloneBaseOwnsStandaloneIdentityAndUsesIdentifierAsGameCode() {
        AbstractStandaloneGameModule module = mock(
                AbstractStandaloneGameModule.class, CALLS_REAL_METHODS);
        when(module.getIdentifier()).thenReturn("owner-game");

        assertEquals(GameId.STANDALONE, module.getGameId());
        assertEquals("owner-game", module.getGameCode());
    }

    @Test
    void exhaustiveGameIdSwitchesMustRouteStandaloneExplicitly() throws Exception {
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                gameIdSwitchViolations(Files.readString(file)).stream()
                        .map(message -> file + ": " + message)
                        .forEach(violations::add);
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
        assertFalse(Files.readString(Path.of("src/main/java/com/openggf/Engine.java"))
                .contains("switch (module.getGameId())"),
                "save namespaces must derive from GameModule.getGameCode()");
    }

    @Test
    void partialGameIdSwitchWithDefaultStillFailsGuard() {
        String unsafe = "return switch (module.getGameId()) { case S1 -> 1; default -> 2; };";
        String safe = "return switch (module.getGameId()) { "
                + "case S1 -> 1; case STANDALONE -> 2; default -> 3; };";

        assertEquals(1, gameIdSwitchViolations(unsafe).size());
        assertTrue(gameIdSwitchViolations(safe).isEmpty());
    }

    private static List<String> gameIdSwitchViolations(String source) {
        Pattern selector = Pattern.compile("switch\\s*\\(\\s*(?:(?:[A-Za-z_$][\\w$]*\\.)*"
                + "getGameId\\s*\\(\\s*\\)|[A-Za-z_$][\\w$]*GameId)\\s*\\)\\s*\\{");
        List<String> violations = new ArrayList<>();
        var matcher = selector.matcher(source);
        while (matcher.find()) {
            int depth = 1;
            int cursor = matcher.end();
            while (cursor < source.length() && depth > 0) {
                char next = source.charAt(cursor++);
                if (next == '{') depth++;
                else if (next == '}') depth--;
            }
            String body = source.substring(matcher.end(), Math.max(matcher.end(), cursor - 1));
            if (!body.contains("case STANDALONE")) {
                violations.add("GameId switch lacks an explicit STANDALONE route");
            }
        }
        return violations;
    }
}
