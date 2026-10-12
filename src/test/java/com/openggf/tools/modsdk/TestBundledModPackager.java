package com.openggf.tools.modsdk;

import com.openggf.ModSubsystem;
import com.openggf.io.ModInputLimits;
import com.openggf.mods.BundledModManifest;
import com.openggf.mods.BundledModSource;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.code.ModClassLoaderFactory;
import com.openggf.mods.code.ModRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The bundled-mod build step end to end with a test-only payload: package a real example
 * project the way Maven does, then boot it from both distribution shapes and load its code.
 */
class TestBundledModPackager {
    private static final Path BASEDIR = Path.of("").toAbsolutePath();

    @TempDir Path temp;

    @Test void examplePayloadPackagesBootsTrustedAndLoadsFromAnInstallDirectory() throws Exception {
        Path install = temp.resolve("install");
        Path classes = Files.createDirectories(temp.resolve("engine-classes"));
        Path manifestFile = classes.resolve(BundledModManifest.RESOURCE);

        BundledModManifest manifest = packageProjects(install.resolve("bundled"), manifestFile,
                List.of("examples/hello-scene"));

        BundledModManifest.Entry entry = manifest.mods().getFirst();
        assertEquals("hello-scene", entry.id());
        assertEquals("hello-scene-" + entry.version() + ".jar", entry.file());
        assertEquals(manifest, BundledModManifest.parse(Files.readAllBytes(manifestFile)));
        byte[] jar = Files.readAllBytes(install.resolve("bundled").resolve(entry.file()));
        assertEquals(entry.size(), jar.length);
        assertEquals(entry.sha256(), BundledModPackager.sha256(jar));
        assertFalse(Files.exists(install.resolve("bundled-work")), "compile scratch is removed");

        BundledModManifest again = packageProjects(temp.resolve("rebuild"), temp.resolve("rebuild.json"),
                List.of("examples/hello-scene"));
        assertEquals(entry.sha256(), again.mods().getFirst().sha256(), "packaging is reproducible");

        try (URLClassLoader resources = new URLClassLoader(new URL[]{classes.toUri().toURL()}, null)) {
            assertLoadsTrusted(resources, Optional.of(install), entry);
        }
    }

    @Test void universalJarShapeEmbedsThePayloadAndExtractsItToTheCache() throws Exception {
        Path classpath = Files.createDirectories(temp.resolve("universal"));
        Path jars = temp.resolve("target/bundled");
        BundledModManifest manifest = packageProjects(jars, classpath.resolve(BundledModManifest.RESOURCE),
                List.of("examples/hello-scene"));
        BundledModManifest.Entry entry = manifest.mods().getFirst();
        Path embedded = classpath.resolve(BundledModManifest.EMBEDDED_PREFIX + entry.file());
        Files.createDirectories(embedded.getParent());
        Files.copy(jars.resolve(entry.file()), embedded);

        try (URLClassLoader resources = new URLClassLoader(new URL[]{classpath.toUri().toURL()}, null)) {
            assertLoadsTrusted(resources, Optional.empty(), entry);
        }
        assertTrue(Files.isRegularFile(temp.resolve("saves/bundled-mod-cache").resolve(entry.sha256() + ".jar")));
    }

    @Test void emptyListWritesAnEmptyManifestAndRemovesStaleJars() throws Exception {
        Path output = Files.createDirectories(temp.resolve("bundled"));
        Files.write(output.resolve("stale-1.0.0.jar"), new byte[]{1});
        Path manifestFile = temp.resolve("classes/" + BundledModManifest.RESOURCE);

        BundledModManifest manifest = packageProjects(output, manifestFile, BundledModPackager.projects(" , "));

        assertTrue(manifest.isEmpty());
        assertEquals(BundledModManifest.EMPTY, BundledModManifest.parse(Files.readAllBytes(manifestFile)));
        try (var files = Files.list(output)) {
            assertEquals(List.of(), files.toList());
        }
        assertEquals(List.of("a", "b/c"), BundledModPackager.projects("a, b/c\n"));
    }

    @Test void validatorWarningsFailTheBuildAndProjectsMustStayInsideTheCheckout() throws Exception {
        Path project = temp.resolve("checkout/mods/leaky");
        Files.createDirectories(project.resolve("src/main/java/leaky"));
        Files.writeString(project.resolve("src/main/java/leaky/LeakyMod.java"), """
                package leaky;
                public final class LeakyMod implements com.openggf.mods.code.GgfMod {
                    @Override public void register(com.openggf.mods.code.ModContext context) {
                        // Engine internals outside the @ModApi surface are a validator warning.
                        com.openggf.game.save.SavePaths.root();
                    }
                }
                """);
        Path manifest = project.resolve("src/main/resources/META-INF/openggf-mod.yaml");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, """
                formatVersion: 1
                id: leaky
                name: Leaky
                version: 1.0.0
                authors: [OpenGGF]
                description: Uses an engine internal.
                engineApiRange: ">=0.7.0 <0.8.0"
                type: patch
                baseGame: s2
                entrypoint: leaky.LeakyMod
                dependencies: []
                audioOverrides: {}
                artOverrides: {}
                """);
        Path checkout = temp.resolve("checkout");
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                BundledModPackager.packageProjects(checkout, temp.resolve("out"), temp.resolve("m.json"),
                        List.of("mods/leaky"), System.getProperty("java.class.path"),
                        new PrintStream(log, true, StandardCharsets.UTF_8)));
        assertTrue(failure.getMessage().contains("warnings are errors"), failure.getMessage());
        assertTrue(log.toString(StandardCharsets.UTF_8).contains("NON_API_ENGINE_REFERENCE"));
        assertFalse(Files.exists(temp.resolve("m.json")), "a failed build publishes no manifest");

        assertThrows(IllegalArgumentException.class, () -> BundledModPackager.packageProjects(checkout,
                temp.resolve("out"), temp.resolve("m.json"), List.of("../outside"),
                System.getProperty("java.class.path"), System.out));
    }

    @Test void buildWiringEmbedsOnlyInTheUniversalJarAndNativeImagesCarryNoBundledResources() throws Exception {
        String pom = Files.readString(BASEDIR.resolve("pom.xml"));
        assertTrue(pom.contains("<id>prepare-bundled-mods</id>"));
        assertTrue(pom.contains("com.openggf.tools.modsdk.BundledModPackager"));
        assertTrue(pom.contains("<argument>${project.build.outputDirectory}/META-INF/openggf/bundled-mods.json</argument>"));
        assertTrue(pom.contains("<openggf.bundled.embed.directory>${project.build.directory}/bundled</openggf.bundled.embed.directory>"),
                "the universal-jar profile embeds target/bundled");
        assertTrue(pom.contains("<openggf.bundled.skip>true</openggf.bundled.skip>"), "native builds skip bundling");
        String assembly = Files.readString(BASEDIR.resolve("src/assembly/openggf-jar-with-dependencies.xml"));
        assertTrue(assembly.contains("<directory>${openggf.bundled.embed.directory}</directory>"));
        assertTrue(assembly.contains("<outputDirectory>openggf-bundled</outputDirectory>"));
        String nativeResources = Files.readString(BASEDIR.resolve(
                "src/main/resources/META-INF/native-image/com.openggf/OpenGGF/resource-config.json"));
        assertFalse(nativeResources.contains("bundled"), "native images must not include bundled mods or their manifest");
        assertFalse(nativeResources.contains("META-INF/openggf/"), "no broad META-INF/openggf pattern either");
        String release = Files.readString(BASEDIR.resolve(".github/workflows/release.yml"));
        assertTrue(release.contains("reject_bundled(archive_path, names)"), "native archives are checked");
        assertTrue(release.contains("META-INF/openggf/bundled-mods.json"), "the universal jar manifest is checked");
    }

    private BundledModManifest packageProjects(Path output, Path manifest, List<String> projects) throws Exception {
        return BundledModPackager.packageProjects(BASEDIR, output, manifest, projects,
                System.getProperty("java.class.path"), System.out);
    }

    private void assertLoadsTrusted(ClassLoader resources, Optional<Path> install, BundledModManifest.Entry entry)
            throws Exception {
        Path mods = Files.createDirectories(temp.resolve("mods-" + install.isPresent()));
        ModSubsystem subsystem = ModSubsystem.normalBootLoader(() -> mods.toAbsolutePath().normalize(),
                ModInputLimits.production(), (game, id) -> true, new Boundary(),
                limits -> BundledModSource.open(resources, install,
                        temp.resolve("saves/bundled-mod-cache"), limits)).get();
        try {
            ModDescriptor effective = subsystem.processCatalog().effective().orderedEnabled().getFirst();
            assertEquals(entry.id(), effective.manifest().id());
            assertEquals(entry.sha256(), effective.sha256());
            assertFalse(effective.hasErrors(), () -> effective.findings().toString());
            assertEquals(java.util.Set.of(entry.id()), subsystem.trustedCodeOwners());
            try (ModRuntime runtime = new ModClassLoaderFactory(getClass().getClassLoader())
                    .create(subsystem.processCatalog().effective(), subsystem.trustedCodeOwners(), true)) {
                assertEquals(java.util.Map.of(), runtime.rejectedOwners(), "the engine validator accepts the jar");
                assertTrue(runtime.owners().contains(entry.id()));
                assertNotNull(runtime.loadOwned(entry.id(), "hello.HelloSceneMod"));
            }
            assertFalse(Files.exists(mods.resolve("modstate.json")));
        } finally {
            subsystem.close();
        }
    }

    private static final class Boundary implements ModSubsystem.SessionAudioBoundary {
        @Override public void install(com.openggf.audio.StreamedMusicPort port) { }
        @Override public void clear() { }
    }
}
