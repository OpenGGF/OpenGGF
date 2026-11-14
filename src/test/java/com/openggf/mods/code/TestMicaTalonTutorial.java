package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.level.objects.PlayableSheetReader;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * The original-character tutorial's part 2 checkpoint
 * ({@code examples/mica-and-talon/tutorial/part-2-first-playable}) keeps building, baking its
 * four-animation sheet and passing the mod validator; its frames stay byte-for-byte the finished
 * MICA sheet's, so the tutorial never teaches stale art; and on a real Sonic 2 session the
 * registered character walks, runs and rolls on her own art with Sonic's physics.
 */
@Isolated
class TestMicaTalonTutorial {
    private static final Path PART = Path.of("examples/mica-and-talon/tutorial/part-2-first-playable");
    private static final Path FINISHED = Path.of("examples/mica-and-talon/art/mica/sheet");
    private static final String CODE = "mica-and-talon-part2:mica";

    @TempDir
    Path work;

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
        com.openggf.graphics.RenderContext.reset();
    }

    @Test
    void checkpointBakesItsSheetAndPassesValidation() throws Exception {
        // The checkpoint bakes its sheet with the playable-art pipeline, which needs Pillow.
        org.junit.jupiter.api.Assumptions.assumeTrue(pythonHasPillow(),
                "the checkpoint's sheet bake needs Python with Pillow");
        var compiled = ExampleModHarness.compile(PART, work);
        assertEquals("mica-and-talon-part2", compiled.manifest().id());
        var sheet = PlayableSheetReader.read(Files.readAllBytes(work.resolve("classes/art/mica.ggfp")));
        assertEquals(26, sheet.frames().size());
        assertEquals(Set.of("WAIT", "WALK", "RUN", "ROLL"), sheet.animations().keySet());
        assertTrue(sheet.animations().get("WALK").slopes().isEmpty(), "flat-only first sheet");
        assertTrue(sheet.animations().get("RUN").speedDriven());
    }

    @Test
    void sheetFramesAreTheFinishedSheetsOwn() throws Exception {
        assertArrayEquals(Files.readAllBytes(FINISHED.resolve("palette.yaml")),
                Files.readAllBytes(PART.resolve("art/sheet/palette.yaml")));
        ObjectMapper json = new ObjectMapper();
        Map<String, JsonNode> finished = new HashMap<>();
        json.readTree(FINISHED.resolve("frames.yaml").toFile()).get("frames")
                .forEach(frame -> finished.put(frame.get("id").asText(), frame));
        for (JsonNode frame : json.readTree(PART.resolve("art/sheet/frames.yaml").toFile()).get("frames")) {
            JsonNode original = finished.get(frame.get("id").asText());
            assertEquals(original.get("hotspot"), frame.get("hotspot"), frame.get("id").asText());
            assertEquals(original.get("file"), frame.get("file"));
        }
        try (Stream<Path> files = Files.list(PART.resolve("art/sheet/frames"))) {
            for (Path file : files.sorted().toList()) {
                assertArrayEquals(Files.readAllBytes(FINISHED.resolve("frames").resolve(file.getFileName())),
                        Files.readAllBytes(file), "stale copy of " + file.getFileName());
            }
        }
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void firstPlayableWalksRunsAndRollsInSonic2() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(pythonHasPillow(),
                "the checkpoint's sheet bake needs Python with Pillow");
        try (ExampleModHarness harness = ExampleModHarness.compile(PART, work).open(work.resolve("storage"))) {
            Sonic2GameModule base = new Sonic2GameModule();
            GameModule resolved = harness.launch(base, CODE);
            var config = SonicConfigurationService.getInstance();
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, CODE);
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            GameModuleRegistry.setCurrent(resolved);
            com.openggf.game.session.SessionManager.clear();
            TestEnvironment.activeGameplayMode();
            var world = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
            var mica = world.sprite();
            assertEquals(CODE, mica.characterKey().persisted());
            assertEquals(base.getPhysicsProvider().getProfile("sonic"), mica.getPhysicsProfile());

            world.stepIdleFrames(60);
            assertFalse(mica.getAir(), "settled on the floor");
            int start = mica.getCentreX();
            for (int i = 0; i < 90; i++) world.stepFrame(false, false, false, true, false);
            assertTrue(mica.getCentreX() > start + 100, "holding right moves her");
            var capabilities = base.getDonorCapabilities();
            assertTrue(Set.of(capabilities.resolveNativeId(CanonicalAnimation.WALK),
                            capabilities.resolveNativeId(CanonicalAnimation.RUN))
                    .contains(mica.getAnimationId()), "walk or run cycle");

            world.stepFrame(false, false, false, true, true);
            world.stepFrame(false, false, false, true, true);
            assertTrue(mica.getAir() && mica.getRolling(), "jumping curls her into a ball");
            assertEquals(capabilities.resolveNativeId(CanonicalAnimation.ROLL), mica.getAnimationId());
            assertEquals(Map.of(), harness.findings());
        }
    }

    private static boolean pythonHasPillow() throws Exception {
        Process probe = new ProcessBuilder(System.getenv().getOrDefault("PYTHON_BIN", "python3"),
                "-c", "import PIL").redirectErrorStream(true).start();
        probe.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
        return probe.waitFor() == 0;
    }
}
