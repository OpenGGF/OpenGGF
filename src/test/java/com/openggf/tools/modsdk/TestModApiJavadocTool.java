package com.openggf.tools.modsdk;

import com.openggf.level.objects.BootstrapObjectServices;
import com.openggf.mods.code.ModApiSurfaceInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestModApiJavadocTool {
    @TempDir Path temp;

    @Test
    void sceneMusicJobsGenerateExactSdkPagesWithTheirBoundedSemantics() throws Exception {
        Path output = temp.resolve("music-job-docs");
        List<Class<?>> exact = List.of(
                com.openggf.mods.scene.SceneMusic.class,
                com.openggf.mods.scene.SceneMusicPreparation.class,
                com.openggf.mods.scene.SceneMusicPreparation.State.class,
                com.openggf.mods.scene.ScenePreparedMusic.class);
        ModApiJavadocTool.generate(Path.of("src/main/java"), output, exact);
        assertEquals(expectedTypePages(exact), generatedTypePages(output));
        String music = Files.readString(output.resolve("com/openggf/mods/scene/SceneMusic.html"));
        assertTrue(music.contains("256 MiB"));
        assertTrue(music.contains("preparePartAsync"));
        String job = Files.readString(output.resolve("com/openggf/mods/scene/SceneMusicPreparation.html"));
        assertTrue(job.contains("cancel"));
        assertTrue(job.contains("progressPercent"));
    }

    @Test
    void scenePeerContractsGenerateExactSdkPagesWithTheirBoundedSemantics() throws Exception {
        Path output = temp.resolve("network-docs");
        List<Class<?>> exact = List.of(
                com.openggf.mods.scene.SceneNetwork.class,
                com.openggf.mods.scene.ScenePeer.class,
                com.openggf.mods.scene.ScenePeer.State.class,
                com.openggf.mods.scene.ScenePeer.Message.class);
        ModApiJavadocTool.generate(Path.of("src/main/java"), output, exact);
        assertEquals(expectedTypePages(exact), generatedTypePages(output));
        String peer = Files.readString(output.resolve("com/openggf/mods/scene/ScenePeer.html"));
        assertTrue(peer.contains("4096"));
        assertTrue(peer.contains("256"));
        assertTrue(peer.contains("plaintext"));
    }

    @Test
    void canonicalInventoryIsExactSortedAndContainsTheMandatedRoots() {
        List<String> names = ModApiJavadocTool.annotatedTypeNames();

        assertEquals(ModApiSurfaceInventory.annotatedTypeNames(), names);
        assertEquals(names.stream().sorted().toList(), names);
        assertTrue(names.containsAll(List.of(
                "com.openggf.mods.code.GgfMod",
                "com.openggf.mods.code.ModContext",
                "com.openggf.mods.code.BakedSheetRef",
                "com.openggf.game.patch.GamePatch",
                "com.openggf.game.patch.DelegatingGameModule",
                "com.openggf.game.AbstractLevelInitProfile",
                "com.openggf.game.patch.PatchContext",
                "com.openggf.game.patch.GameplayLaunchRequest",
                "com.openggf.level.objects.ObjectServices",
                "com.openggf.level.objects.AbstractObjectInstance",
                "com.openggf.level.objects.AbstractBadnikInstance",
                "com.openggf.level.objects.ObjectSpawn",
                "com.openggf.sprites.playable.ObjectControlState",
                "com.openggf.level.objects.ObjectLifetimeOps",
                "com.openggf.level.objects.ObjectPlayerQuery",
                "com.openggf.game.PhysicsProfile",
                "com.openggf.level.objects.SubpixelMotion",
                "com.openggf.level.objects.PatrolMovementHelper",
                "com.openggf.level.objects.PlatformBobHelper",
                "com.openggf.level.objects.SpringBounceHelper",
                "com.openggf.level.objects.DestructionEffects")));
        assertTrue(names.contains("com.openggf.physics.GroundSensor"));
        assertFalse(names.contains(BootstrapObjectServices.class.getName()));
        assertThrows(IllegalArgumentException.class,
                () -> ModApiJavadocTool.requireAnnotatedInventory(
                        List.of(BootstrapObjectServices.class)));
    }

    @Test
    void documentationToolGeneratesOnlyTheExactRequestedAnnotatedInventory() throws Exception {
        Path output = temp.resolve("api-docs");
        Class<?> initCallback = Class.forName(
                "com.openggf.game.AbstractLevelInitProfile$IORunnable");
        List<Class<?>> exact = List.of(
                com.openggf.mods.code.GgfMod.class,
                com.openggf.mods.code.ModContext.class,
                com.openggf.mods.code.BakedSheetRef.class,
                com.openggf.game.patch.DelegatingGameModule.class,
                com.openggf.game.AbstractLevelInitProfile.class,
                initCallback,
                com.openggf.physics.GroundSensor.class);

        ModApiJavadocTool.generate(Path.of("src/main/java"), output, exact);

        String allClasses = Files.readString(output.resolve("allclasses-index.html"));
        assertTrue(allClasses.contains("GgfMod"));
        assertTrue(allClasses.contains("ModContext"));
        assertTrue(allClasses.contains("BakedSheetRef"));
        assertTrue(allClasses.contains("DelegatingGameModule"));
        assertTrue(allClasses.contains("AbstractLevelInitProfile"));
        assertTrue(allClasses.contains("IORunnable"));
        assertTrue(allClasses.contains("GroundSensor"));
        assertFalse(allClasses.contains("BootstrapObjectServices"));
        assertEquals(expectedTypePages(exact), generatedTypePages(output));
    }

    @Test
    void documentationIncludesCompleteCreatorFacingSignatures() throws Exception {
        Path output = temp.resolve("signature-docs");
        List<Class<?>> exact = List.of(
                ModApiJavadocFixture.class,
                ModApiJavadocAnnotationFixture.class);

        ModApiJavadocTool.generate(Path.of("src/test/java"), output, exact);

        String typePage = Files.readString(output.resolve(
                "com/openggf/tools/modsdk/ModApiJavadocFixture.html"));
        assertTrue(typePage.contains("@java.lang.Deprecated(since=&quot;1.0&quot;)"));
        assertTrue(typePage.contains("ModApiJavadocFixture&lt;T extends java.lang.Number &amp; java.lang.Comparable&lt;T&gt;&gt;"));
        assertTrue(typePage.contains("extends java.util.ArrayList&lt;T&gt; implements java.io.Serializable"));
        assertTrue(typePage.contains("@com.openggf.tools.modsdk.FixtureTypeUse T value"));
        assertTrue(typePage.contains("&lt;R extends java.lang.CharSequence&gt; @com.openggf.tools.modsdk.FixtureTypeUse R convert"));
        assertTrue(typePage.contains("@com.openggf.tools.modsdk.FixtureParameter @com.openggf.tools.modsdk.FixtureTypeUse T input"));
        assertTrue(typePage.contains("throws java.io.IOException"));

        String annotationPage = Files.readString(output.resolve(
                "com/openggf/tools/modsdk/ModApiJavadocAnnotationFixture.html"));
        assertTrue(annotationPage.contains("java.lang.String value() default &quot;default-value&quot;"));
    }

    private static Set<String> expectedTypePages(List<Class<?>> types) {
        return types.stream().map(type -> {
            String packageName = type.getPackageName();
            String localName = type.getCanonicalName().substring(packageName.length() + 1);
            return packageName.replace('.', '/') + "/" + localName + ".html";
        }).collect(Collectors.toSet());
    }

    private static Set<String> generatedTypePages(Path output) throws Exception {
        Path packages = output.resolve("com/openggf");
        try (var paths = Files.walk(packages)) {
            return paths.filter(Files::isRegularFile)
                    .map(output::relativize)
                    .map(Path::toString)
                    .map(value -> value.replace('\\', '/'))
                    .filter(value -> value.endsWith(".html"))
                    .filter(value -> !value.endsWith("/package-summary.html"))
                    .filter(value -> !value.endsWith("/package-tree.html"))
                    .collect(Collectors.toSet());
        }
    }

    @Test
    void packagePrivateTopLevelTypeResolvesThroughItsClassfileSourceFileAttribute() throws Exception {
        Class<?> packagePrivate = Class.forName("com.openggf.io.AbstractModAssetRoot");

        assertEquals(List.of(Path.of("src/main/java").toAbsolutePath().normalize()
                        .resolve("com/openggf/io/ModAssetRoot.java")),
                ModApiJavadocTool.sourceFilesForTests(
                        Path.of("src/main/java"), List.of(packagePrivate)));
    }

    @Test
    void rejectsNonEmptyOutputWithoutMutatingIt() throws Exception {
        Path output = temp.resolve("existing-docs");
        Files.createDirectories(output);
        Path marker = output.resolve("stale.html");
        Files.writeString(marker, "keep");

        assertThrows(IllegalArgumentException.class, () -> ModApiJavadocTool.generate(
                Path.of("src/main/java"), output,
                List.of(com.openggf.mods.code.GgfMod.class)));

        assertEquals("keep", Files.readString(marker));
    }
}
