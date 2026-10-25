package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Real packaged entrypoint: patch-only any-game captures use the ordinary patch loop. */
@Isolated
class TestDevelopmentPatchLoader {
    @TempDir Path temp;
    @BeforeEach void setup() { TestEnvironment.resetAll(); }
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @ParameterizedTest @ValueSource(strings={"s1", "s2", "s3k"})
    void patchOnlyAnyGameLaunchFiltersAndOrdersPatchesWithFreshRegistration(String game) throws Exception {
        var prepared = DevelopmentPatchLoader.openJar(jar());
        var resolver = prepared.rewindClassResolver();
        assertTrue(resolver.resolve("patch-only", "patchfixture.Fixture").isEmpty());
        GameModule base = switch (game) {
            case "s1" -> new com.openggf.game.sonic1.Sonic1GameModule();
            case "s2" -> new com.openggf.game.sonic2.Sonic2GameModule();
            case "s3k" -> new com.openggf.game.sonic3k.Sonic3kGameModule();
            default -> throw new AssertionError(game);
        };
        var request = new GameplayLaunchRequest(game, "sonic", List.of());
        try (prepared) {
            for (int launch = 0; launch < 2; launch++) {
                var effective = prepared.apply(base, request);
                assertEquals(base.getIdentifier() + ":" + game + "-first-1:" + game + "-second-2",
                        effective.getIdentifier(), "Only matching, admitted patches run, in order, on fresh entrypoint state");
                assertEquals(base.getGameId(), effective.getGameId());
                var owned = resolver.resolve("patch-only", "patchfixture.Fixture").orElseThrow();
                assertEquals("patch-only", resolver.ownerOf(owned).orElseThrow());
            }
        }
        assertTrue(resolver.resolve("patch-only", "patchfixture.Fixture").isEmpty());
        assertThrows(IllegalStateException.class, () -> prepared.apply(base, request));
    }

    private Path jar() throws Exception {
        Path source = temp.resolve("Fixture.java"), classes = temp.resolve("classes");
        Files.createDirectories(classes);
        Files.writeString(source, """
                package patchfixture;
                import com.openggf.mods.code.*;
                import com.openggf.game.*;
                import com.openggf.game.patch.*;
                import java.util.*;
                public class Fixture implements GgfMod {
                    private int applications;
                    public void register(ModContext context) {
                        for (String game : List.of("s1", "s2", "s3k")) {
                            context.registerGamePatch(patch(game, "first", true));
                            context.registerGamePatch(patch(game, "declined", false));
                            context.registerGamePatch(patch(game, "second", true));
                        }
                    }
                    private GamePatch patch(String game, String name, boolean active) {
                        return new GamePatch() {
                            public String id() { return game + "-" + name; }
                            public String displayName() { return id(); }
                            public String baseGameId() { return game; }
                            public boolean activatesFor(GameplayLaunchRequest request) {
                                return active && request.gameId().equals(game);
                            }
                            public Set<LogicalRom> romPrerequisites() { return Set.of(); }
                            public List<String> providedMainCharacters() { return List.of(); }
                            public GameModule apply(GameModule base, PatchContext context) {
                                if (!active || !base.getGameId().code().equals(game))
                                    throw new AssertionError("An unrelated or declined patch ran");
                                String suffix = ":" + id() + "-" + ++applications;
                                return new DelegatingGameModule(base, "patch-only:" + id()) {
                                    public String getIdentifier() { return base.getIdentifier() + suffix; }
                                };
                            }
                        };
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "25", "-classpath", System.getProperty("java.class.path"),
                "-d", classes.toString(), source.toString()));
        Path jar = temp.resolve("patch-only.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("META-INF/openggf-mod.yaml"));
            out.write("""
                    formatVersion: 1
                    id: patch-only
                    name: Patch only
                    version: 1.0.0
                    authors: [OpenGGF]
                    description: Any-game development loader regression
                    engineApiRange: ">=0.7.0 <0.8.0"
                    type: patch
                    baseGame: any
                    entrypoint: patchfixture.Fixture
                    dependencies: []
                    audioOverrides: {}
                    artOverrides: {}
                    """.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            try (var paths = Files.walk(classes)) {
                for (var path : paths.filter(Files::isRegularFile).toList()) {
                    out.putNextEntry(new JarEntry(classes.relativize(path).toString()));
                    Files.copy(path, out);
                    out.closeEntry();
                }
            }
        }
        return jar;
    }
}
