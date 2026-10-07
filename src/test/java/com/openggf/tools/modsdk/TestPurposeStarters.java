package com.openggf.tools.modsdk;

import com.openggf.tests.TestSessionOutputPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Every scope must be authorable from embedded SDK resources, including authored binaries. */
class TestPurposeStarters {
    @TempDir Path temp;

    @Test void everyPurposeHasCompleteCompilableSourcesAndRepeatedBuildConfiguration() throws Exception {
        for (String kind : List.of("music", "reskin", "object", "character", "zone", "scene", "standalone")) {
            Path project = new ProjectScaffolder().scaffold(temp.resolve(kind), "my-" + kind, "example." + kind, kind);
            String pom = Files.readString(project.resolve("pom.xml"));
            assertTrue(pom.contains("<version>3.14.0</version>"), kind);
            assertTrue(pom.contains("<phase>initialize</phase>"), kind);
            assertTrue(pom.contains("my-" + kind + "-mod.jar"), kind);
            try (var paths = Files.walk(project)) {
                assertFalse(paths.anyMatch(p -> p.toString().endsWith(".base64") || p.getFileName().toString().equals("binary-assets.properties")), kind);
            }
            List<String> sources;
            try (var paths = Files.walk(project)) { sources = paths.filter(p -> p.toString().endsWith(".java")).map(Path::toString).toList(); }
            if (!sources.isEmpty()) {
                Path classes = temp.resolve(kind + "-classes"); Files.createDirectories(classes);
                var args = new ArrayList<>(List.of("--release", "21", "-cp", TestSessionOutputPaths.compiledClasses().toAbsolutePath().toString(), "-d", classes.toString()));
                args.addAll(sources);
                assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)), kind);
            }
            if (kind.equals("reskin")) {
                Path baked = temp.resolve("reskin.ggfs");
                new ArtConverter().convert(project.resolve("src/main/mod/sample.png"), project.resolve("src/main/mod/sample-sheet.yaml"), baked);
                assertTrue(Files.readString(project.resolve("src/main/resources/META-INF/openggf-mod.yaml")).contains("signpost:"));
                var sheet = com.openggf.level.objects.BakedSheetReader.read(Files.readAllBytes(baked), com.openggf.io.ModInputLimits.production());
                assertEquals(6, sheet.toObjectSpriteSheet().getFrameCount());
                assertEquals(6, sheet.toObjectSpriteSheet().getPatterns().length, "Every animation face has distinct authored art");
            }
            if (kind.equals("character") || kind.equals("standalone")) {
                new PlayableArtConverter().convert(project.resolve("src/main/mod/runner.png"), project.resolve("src/main/mod/runner-sheet.yaml"), temp.resolve(kind + ".ggfp"));
            }
            if (kind.equals("standalone"))
                new LevelConverter().convert(project.resolve("src/main/mod/level-source"), temp.resolve("standalone-level"));
        }
    }

    @Test void maintainedStarterResourceInventoryMatchesSourceFiles() throws Exception {
        List<String> expected = new ArrayList<>();
        Path root = Path.of("src/test/resources");
        for (String fixture : List.of("sample-character-src", "sample-standalone-src")) {
            Path project = root.resolve("mods/" + fixture + "/project");
            try (var files = Files.walk(project)) {
                files.filter(Files::isRegularFile).filter(p -> !p.startsWith(project.resolve("target")))
                        .map(root::relativize).map(p -> p.toString().replace('\\', '/')).forEach(expected::add);
            }
        }
        expected.sort(String::compareTo);
        assertEquals(expected, Files.readAllLines(Path.of("src/main/resources/META-INF/openggf-mod-sdk/starters/index.txt")));
    }
}
