package com.openggf.tools.modsdk;

import com.openggf.tests.TestSessionOutputPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

/** Every scope must be authorable from embedded SDK resources, including authored binaries. */
class TestPurposeStarters {
    @TempDir Path temp;

    @Test void packagedSdkStandaloneMaterializesAudioAndPackagesWithoutCheckoutScripts() throws Exception {
        Path compiled = temp.resolve("sdk-build/classes");
        Path sessionClasses = TestSessionOutputPaths.compiledClasses();
        for (String relative : List.of("com/openggf/tools/modsdk", "META-INF/openggf-mod-sdk")) {
            try (var paths = Files.walk(sessionClasses.resolve(relative))) {
                for (Path source : paths.filter(Files::isRegularFile).toList()) {
                    Path destination = compiled.resolve(sessionClasses.relativize(source));
                    Files.createDirectories(destination.getParent());
                    Files.copy(source, destination);
                }
            }
        }
        Path sdkClasses = temp.resolve("sdk-build/classifier");
        ModApiSdkPackager.prepare(compiled, Path.of("src/main/java"), sdkClasses,
                temp.resolve("sdk-build/javadocs"), List.of(com.openggf.mods.code.GgfMod.class));
        Path sdkJar = temp.resolve("sdk.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(sdkJar));
             var files = Files.walk(sdkClasses)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                output.putNextEntry(new JarEntry(sdkClasses.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
        // Tools and their resources must come from the classifier, never the parent test classpath.
        try (var sdk = new URLClassLoader(new URL[]{sdkJar.toUri().toURL()}, GgfModCli.class.getClassLoader()) {
            @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith("com.openggf.tools.modsdk.")) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) type = findClass(name);
                if (resolve) resolveClass(type);
                return type;
            }
            @Override public URL getResource(String name) {
                return name.startsWith("META-INF/openggf-mod-sdk/") ? findResource(name) : super.getResource(name);
            }
        }) {
            Class<?> cli = sdk.loadClass(GgfModCli.class.getName());
            assertEquals(sdk, cli.getClassLoader());
            Path project = temp.resolve("generated-standalone");
            runSdk(cli, "init", project.toString(), "--id", "sdk-standalone", "--kind", "standalone",
                    "--package", "creator.standalone");
            Path audio = project.resolve("src/main/resources/audio/sample-tone.wav");
            assertTrue(Files.isRegularFile(audio), "The audio manifest's WAV must be a Maven resource");
            try (var archive = new JarFile(sdkJar.toFile())) {
                String entry = "META-INF/openggf-mod-sdk/starters/mods/sample-standalone-src/project/src/main/mod/sample-tone.wav.base64";
                assertArrayEquals(Base64.getMimeDecoder().decode(archive.getInputStream(archive.getJarEntry(entry)).readAllBytes()),
                        Files.readAllBytes(audio));
            }
            assertFalse(Files.exists(project.resolve("src/main/mod/sample-tone.wav")));

            Path classes = project.resolve("target/classes");
            Files.createDirectories(classes);
            var compilerArgs = new ArrayList<>(List.of("--release", "21", "-cp", sessionClasses.toAbsolutePath().toString(),
                    "-d", classes.toString()));
            try (var sources = Files.walk(project.resolve("src/main/java"))) {
                sources.filter(path -> path.toString().endsWith(".java")).map(Path::toString).forEach(compilerArgs::add);
            }
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, compilerArgs.toArray(String[]::new)));
            Path resources = project.resolve("src/main/resources");
            try (var files = Files.walk(resources)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    Path destination = classes.resolve(resources.relativize(file));
                    Files.createDirectories(destination.getParent());
                    Files.copy(file, destination);
                }
            }
            runSdk(cli, "convert", "art", "--playable", "--image", project.resolve("src/main/mod/runner.png").toString(),
                    "--sheet", project.resolve("src/main/mod/runner-sheet.yaml").toString(), "--out", classes.resolve("art/runner.ggfp").toString());
            runSdk(cli, "convert", "level", "--from-export", project.resolve("src/main/mod/level-source").toString(),
                    "--out", classes.resolve("levels/sample").toString());
            Path modJar = project.resolve("target/sdk-standalone-mod.jar");
            runSdk(cli, "package", "--input", classes.toString(), "--out", modJar.toString(), "--warnings", "error");
            var report = new ModJarValidator().validate(modJar);
            assertTrue(report.valid(), () -> report.numberedLines().toString());
            assertTrue(report.findings().isEmpty(), () -> report.numberedLines().toString());
            try (var archive = new JarFile(modJar.toFile())) {
                assertArrayEquals(Files.readAllBytes(audio), archive.getInputStream(archive.getJarEntry("audio/sample-tone.wav")).readAllBytes());
            }
        }
    }

    private static void runSdk(Class<?> cli, String... args) throws Exception {
        var captured = new ByteArrayOutputStream();
        try (var output = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            assertEquals(0, cli.getMethod("run", String[].class, PrintStream.class).invoke(null, (Object) args, output),
                    () -> captured.toString(StandardCharsets.UTF_8));
        }
    }

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
