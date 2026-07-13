package com.openggf.tools.modsdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestModApiSdkPackager {
    @TempDir Path temp;

    @Test
    void copiesOnlySdkToolClassesTemplatesAndServiceResourcesAndGeneratesApiJavadocs() throws Exception {
        Class<?> packagePrivate = Class.forName("com.openggf.io.AbstractModAssetRoot");
        Class<?> delegatingGameModule = com.openggf.game.patch.DelegatingGameModule.class;
        Class<?> abstractLevelInitProfile = com.openggf.game.AbstractLevelInitProfile.class;
        Class<?> initCallback = Class.forName(
                "com.openggf.game.AbstractLevelInitProfile$IORunnable");
        Class<?> groundSensor = com.openggf.physics.GroundSensor.class;
        Path compiled = temp.resolve("build/classes");
        copyCompiledPackage(compiled, Path.of("com/openggf/tools/modsdk"));
        copyCompiledPackage(compiled, Path.of("META-INF/openggf-mod-sdk"));
        copyCompiledFixture(compiled, com.openggf.mods.code.GgfMod.class);
        copyCompiledFixture(compiled, packagePrivate);
        Path classes = temp.resolve("build/sdk-classes");
        Path docs = temp.resolve("build/sdk-javadocs");

        ModApiSdkPackager.prepare(compiled, Path.of("src/main/java"),
                classes, docs, List.of(com.openggf.mods.code.GgfMod.class,
                        packagePrivate, delegatingGameModule, abstractLevelInitProfile,
                        initCallback, groundSensor));

        Set<String> copied;
        try (var paths = Files.walk(classes)) {
            copied = paths.filter(Files::isRegularFile)
                    .map(classes::relativize).map(Path::toString)
                    .map(value -> value.replace('\\', '/')).collect(Collectors.toSet());
        }
        assertTrue(copied.contains("com/openggf/tools/modsdk/GgfModCli.class"), copied::toString);
        assertTrue(copied.stream().anyMatch(value -> value.startsWith(
                "com/openggf/tools/modsdk/") && value.contains("$")), copied::toString);
        assertTrue(copied.contains("META-INF/openggf-mod-sdk/templates/pom.xml.template"), copied::toString);
        assertTrue(copied.stream().allMatch(value -> value.startsWith("com/openggf/tools/modsdk/")
                || value.startsWith("META-INF/openggf-mod-sdk/templates/")
                || value.startsWith("META-INF/services/")), copied::toString);
        assertFalse(copied.contains("com/openggf/mods/code/GgfMod.class"));
        assertFalse(copied.contains("com/openggf/io/AbstractModAssetRoot.class"));
        assertFalse(Files.exists(classes.resolve(
                "com/openggf/level/objects/BootstrapObjectServices.class")));
        assertTrue(Files.isRegularFile(docs.resolve("index.html")));
        assertTrue(Files.isRegularFile(
                docs.resolve("com/openggf/io/AbstractModAssetRoot.html")));
        assertTrue(Files.isRegularFile(docs.resolve(
                "com/openggf/game/patch/DelegatingGameModule.html")));
        assertTrue(Files.isRegularFile(docs.resolve(
                "com/openggf/game/AbstractLevelInitProfile.html")));
        assertTrue(Files.isRegularFile(docs.resolve(
                "com/openggf/game/AbstractLevelInitProfile.IORunnable.html")));
        assertTrue(Files.isRegularFile(docs.resolve(
                "com/openggf/physics/GroundSensor.html")));
        Set<String> typePages;
        try (var paths = Files.walk(docs.resolve("com/openggf"))) {
            typePages = paths.filter(Files::isRegularFile)
                    .map(docs::relativize).map(Path::toString)
                    .map(value -> value.replace('\\', '/'))
                    .filter(value -> value.endsWith(".html"))
                    .filter(value -> !value.endsWith("/package-summary.html"))
                    .filter(value -> !value.endsWith("/package-tree.html"))
                    .collect(Collectors.toSet());
        }
        assertEquals(Set.of(
                "com/openggf/mods/code/GgfMod.html",
                "com/openggf/io/AbstractModAssetRoot.html",
                "com/openggf/game/patch/DelegatingGameModule.html",
                "com/openggf/game/AbstractLevelInitProfile.html",
                "com/openggf/game/AbstractLevelInitProfile.IORunnable.html",
                "com/openggf/physics/GroundSensor.html"), typePages);
    }

    @Test
    void rejectsAnyRecursiveDeleteTargetOutsideTheCompiledClassesBuildRoot() throws Exception {
        Path compiled = temp.resolve("build/classes");
        copyCompiledFixture(compiled, com.openggf.mods.code.GgfMod.class);

        assertThrows(IllegalArgumentException.class, () -> ModApiSdkPackager.prepare(
                compiled, Path.of("src/main/java"), temp.resolve("outside-sdk"),
                temp.resolve("build/docs"), List.of(com.openggf.mods.code.GgfMod.class)));
        assertFalse(Files.exists(temp.resolve("outside-sdk")));
    }

    @Test
    void rejectsCompilerOrSourceDescendantsBeforeDeletingAnything() throws Exception {
        Path compiled = temp.resolve("build/classes");
        copyCompiledFixture(compiled, com.openggf.mods.code.GgfMod.class);
        Path sourceRoot = temp.resolve("build/sources");
        Files.createDirectories(sourceRoot);
        Path compiledMarker = compiled.resolve("sdk/keep.txt");
        Path sourceMarker = sourceRoot.resolve("generated/keep.txt");
        Files.createDirectories(compiledMarker.getParent());
        Files.createDirectories(sourceMarker.getParent());
        Files.writeString(compiledMarker, "keep");
        Files.writeString(sourceMarker, "keep");

        assertThrows(IllegalArgumentException.class, () -> ModApiSdkPackager.prepare(
                compiled, sourceRoot, compiled.resolve("sdk"), temp.resolve("build/docs"),
                List.of(com.openggf.mods.code.GgfMod.class)));
        assertTrue(Files.isRegularFile(compiledMarker));

        assertThrows(IllegalArgumentException.class, () -> ModApiSdkPackager.prepare(
                compiled, sourceRoot, temp.resolve("build/sdk"), sourceRoot.resolve("generated"),
                List.of(com.openggf.mods.code.GgfMod.class)));
        assertTrue(Files.isRegularFile(sourceMarker));
    }

    @Test
    void pomAttachesSdkBinaryAndJavadocClassifiersFromPreparedExactDirectories()
            throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(pom.contains("com.openggf.tools.modsdk.ModApiSdkPackager"));
        assertTrue(pom.contains("<classifier>openggf-mod-sdk</classifier>"));
        assertTrue(pom.contains("<classifier>openggf-mod-sdk-javadoc</classifier>"));
        assertTrue(pom.contains("${project.build.directory}/mod-sdk/classes"));
        assertTrue(pom.contains("${project.build.directory}/mod-sdk/javadocs"));
        assertTrue(pom.contains("com/openggf/tools/modsdk/**"));
        assertTrue(pom.contains("src/assembly/openggf-jar-with-dependencies.xml"));
    }

    private static void copyCompiledFixture(Path compiledRoot, Class<?> type) throws Exception {
        Path relative = Path.of(type.getName().replace('.', '/') + ".class");
        Path destination = compiledRoot.resolve(relative);
        Files.createDirectories(destination.getParent());
        Files.copy(Path.of("target/classes").resolve(relative), destination);
    }

    private static void copyCompiledPackage(Path compiledRoot, Path relativeRoot) throws Exception {
        Path sourceRoot = Path.of("target/classes").resolve(relativeRoot);
        try (var paths = Files.walk(sourceRoot)) {
            for (Path source : paths.filter(Files::isRegularFile).toList()) {
                Path destination = compiledRoot.resolve(Path.of("target/classes").relativize(source));
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination);
            }
        }
    }
}
