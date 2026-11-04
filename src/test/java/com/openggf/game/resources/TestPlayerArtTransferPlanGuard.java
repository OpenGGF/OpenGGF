package com.openggf.game.resources;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shared resource owners consume module semantics rather than selecting a game. */
class TestPlayerArtTransferPlanGuard {
    private static final Pattern SONIC_IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+(?:static\\s+)?com\\.openggf\\.game\\.sonic\\w*\\.");
    private static final Pattern GAME_ID_CONSTANT = Pattern.compile(
            "\\bGameId\\s*\\.\\s*(?:[A-Z][A-Z0-9_]*|\\*)");

    @Test
    void sharedResourceSourcesHaveNoSonicImportsOrGameIdConstants() throws IOException {
        Path root = Path.of(System.getProperty("project.basedir", "."),
                "src/main/java/com/openggf/game/resources");
        List<Path> sources;
        try (var paths = Files.walk(root)) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
        assertFalse(sources.isEmpty(), "the guard must inspect production resource sources");
        for (Path source : sources) {
            assertTrue(isGameIndependent(Files.readString(source)),
                    () -> source + " must consume module-supplied resource semantics");
        }
    }

    @Test
    void rejectsGameSpecificDependenciesAndIdentitySelections() {
        for (String source : List.of(
                "import com.openggf.game.sonic1.constants.Sonic1Constants;",
                "import static com.openggf.game.sonic2.constants.Sonic2Constants.*;",
                "import com.openggf.game.sonic3k.Sonic3kGameModule;",
                "if (game == GameId.S1) {}",
                "if (game == com.openggf.game.GameId . S2) {}",
                "import static com.openggf.game.GameId.*;")) {
            assertFalse(isGameIndependent(source), source);
        }
    }

    @Test
    void acceptsSemanticTransferModels() {
        assertTrue(isGameIndependent("plan.model() == PlayerArtTransferPlan.TransferModel.STAGED_FOR_VINT"));
    }

    private static boolean isGameIndependent(String source) {
        return !SONIC_IMPORT.matcher(source).find()
                && !GAME_ID_CONSTANT.matcher(source).find();
    }
}
