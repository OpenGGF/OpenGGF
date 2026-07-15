package com.openggf.mods.code;

import com.openggf.io.ModAssetRoot;
import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModInputLimits;
import com.openggf.tools.modsdk.GgfModCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSampleRomArtRemixRegistration {
    private static final Path SAMPLE = Path.of(
            "src/test/resources/mods/sample-rom-art-remix-src/project");

    @TempDir Path temp;

    @Test
    void registersExactTailsWindowWithoutGameplayPolicies() throws Exception {
        ModRegistrationPlan plan = compileAndRegister();

        RomArtRequest request = plan.romObjectArt().get(
                "sample-rom-art-remix:tails-flight");
        assertNotNull(request);
        assertEquals(0x64320, request.artAddress());
        assertEquals(RomArtCompression.UNCOMPRESSED, request.compression());
        assertEquals(0xB8C0, request.uncompressedByteSize());
        assertEquals(0x739E2, request.mappingAddress());
        assertEquals(0x7446C, request.dplcAddress());
        assertEquals(0, request.paletteLine());
        assertEquals(1, request.bankSize());
        assertTrue(plan.launchTeams().isEmpty());
        assertTrue(plan.inputFilters().isEmpty());
        assertTrue(plan.hudProfiles().isEmpty());
        assertTrue(plan.objectFactories().containsKey(
                "sample-rom-art-remix:tails-flight-art"));
        assertEquals(1, plan.zones().size());
        assertEquals("rom-art-gallery", plan.zones().getFirst().localKey());
        assertEquals("ehz2", plan.zones().getFirst().insertAfter());
        assertEquals("levels/rom-art-gallery/level.json",
                plan.zones().getFirst().level().levelJsonEntry());
    }

    private ModRegistrationPlan compileAndRegister() throws Exception {
        Path classes = temp.resolve("classes");
        Files.createDirectories(classes);
        List<String> arguments = new ArrayList<>(List.of(
                "--release", "21", "-classpath",
                Path.of("target/classes").toAbsolutePath().toString(),
                "-d", classes.toString()));
        try (var sources = Files.walk(SAMPLE.resolve("src/main/java"))) {
            sources.filter(path -> path.toString().endsWith(".java")).sorted()
                    .map(Path::toString).forEach(arguments::add);
        }
        int exit = ToolProvider.getSystemJavaCompiler().run(
                null, null, null, arguments.toArray(String[]::new));
        assertEquals(0, exit, "sample source must compile against the current SDK surface");

        try (URLClassLoader loader = new URLClassLoader(
                new java.net.URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
            GgfMod mod = Class.forName("example.romartremix.RomArtRemixMod", true, loader)
                    .asSubclass(GgfMod.class).getConstructor().newInstance();
            Path assetsDirectory = materializeBakedLevel();
            try (ModAssetRoot assets = ModAssetRoot.snapshotDirectory(
                    assetsDirectory, assetsDirectory, ModInputLimits.production(),
                    DirectoryAccess.TEST)) {
                ModContext context = new ModContext("sample-rom-art-remix", "s2", assets);
                mod.register(context);
                return context.freeze();
            }
        }
    }

    private Path materializeBakedLevel() throws Exception {
        Path source = SAMPLE.resolve("src/main/mod/level-source");
        Path export = temp.resolve("level-export");
        Files.createDirectories(export);
        Files.copy(source.resolve("level.json"), export.resolve("level.json"));
        Properties properties = new Properties();
        try (var input = Files.newInputStream(source.resolve("binary-assets.properties"))) {
            properties.load(input);
        }
        for (String name : properties.stringPropertyNames()) {
            Files.write(export.resolve(name), Base64.getDecoder().decode(properties.getProperty(name)));
        }

        Path assets = temp.resolve("assets");
        Path output = assets.resolve("levels/rom-art-gallery");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int exit = GgfModCli.run(new String[]{"convert", "level", "--from-export",
                export.toString(), "--out", output.toString()}, new PrintStream(bytes));
        assertEquals(0, exit, bytes.toString(StandardCharsets.UTF_8));
        return assets;
    }
}
