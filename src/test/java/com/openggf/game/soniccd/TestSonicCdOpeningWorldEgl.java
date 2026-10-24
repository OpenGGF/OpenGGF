package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.internal.NativeDynamicObjectPass;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.objects.CdRing;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.LevelManager;
import com.openggf.level.LevelSpritePresentation;
import com.openggf.sprites.render.PlayerResidentBanks;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.HeadlessGameBoot;
import com.openggf.tools.InputScriptCompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Finite USA normal-input route and full/course restore acceptance.
 * Opt in with soniccd.media.root and soniccd.opening.egl=true; an opted-in run
 * fails on missing media, EGL selection or pixels. Optional soniccd.opening.output
 * saves actual engine pictures and observations in a new Downloads/Sonic-CD directory.
 */
@Isolated
class TestSonicCdOpeningWorldEgl {
    @BeforeEach void setup() {
        TestEnvironment.resetAll();
        var c = GameServices.configuration();
        c.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        c.setSessionOverride(SonicConfiguration.SCREEN_WIDTH, 320);
        c.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT, 224);
        c.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        c.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        c.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        c.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        c.resolveDisplayAspect();
    }

    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test void actualEglNormalInputPickupAndFullCourseReplay() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual EGL/media opt-in");
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY"));
        assertNull(System.getenv("WAYLAND_DISPLAY"));
        String media = System.getProperty("soniccd.media.root");
        assertNotNull(media, "An opted-in rendering run requires actual media");
        assertFalse(media.isBlank());
        CdGameDataSource source;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            source = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
        // This exact source Input Log is parsed by the existing production loader;
        // it never positions the actor/objects or supplies native state.
        // Initial 88-row observation ended at X214, before the first Ring at X240.
        // Continue ordinary Right input; no production movement/touch state changes.
        String sourceInputLog = InputScriptCompiler.compileToInputLog("2 -\n60 R\n24 R+B\n16 R");
        var movie = new Bk2MovieLoader().parseInputLogText(Path.of("opening-world-proposed-input-log"), sourceInputLog.lines().toList());
        assertEquals(102, movie.getFrameCount());
        Path output = openCaptureOutput(sourceInputLog);
        try (var boot = new HeadlessGameBoot(320, 224)) {
            try {
                var loop = boot.bootSonicCdOpeningObjects(source);
                var mode = SessionManager.getCurrentGameplayMode();
                var module = assertInstanceOf(SonicCdGameModule.class, mode.getWorldSession().getGameModule());
                var runtime = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                assertSame(source, mode.getWorldSession().getDataSource());
                assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
                assertNull(GameServices.audio().captureLogicalSnapshot().presentation().activeMusic());
                var cold = render(module, true);
                observe(output, 0, null, module, cold);
                assertEquals(320, cold.width()); assertEquals(224, cold.height());
                assertTrue(Arrays.stream(cold.pixels()).distinct().count() > 8, "Actual decoded terrain must be visible");
                assertArrayEquals(cold.pixels(), render(module, false).pixels(), "Empty DEV display has no private actor fallback");
                step(loop, movie.getFrame(0));
                var firstCompletedScroll = LevelSpritePresentation.captureScroll(GameServices.level());
                step(loop, movie.getFrame(1));
                var before = capture(module);
                var beforeImage = render(module, true);
                var beforeTerrain = renderTerrain(module, firstCompletedScroll);
                assertTerrainOutsideSprites(beforeImage, beforeTerrain, runtime.displayedFrame());
                observe(output, 2, movie.getFrame(1), module, beforeImage);
                assertFalse(Arrays.equals(beforeImage.pixels(), beforeTerrain.pixels()), "Actual Ring picture must draw");
                assertArrayEquals(beforeImage.pixels(), render(module, true).pixels());
                assertCapture(before, module);
                var registry = mode.getRewindRegistry();
                var full = registry.capture();
                var course = registry.captureCourse();
                int initialX = module.requirePlayerActor().getCentreX();
                var prefix = new ArrayList<Bk2FrameInput>();
                var expectedRows = new ArrayList<Capture>();
                int pickupIndex = -1;
                for (int index = 2; index < movie.getFrameCount(); index++) {
                    var input = movie.getFrame(index);
                    var previousCompletedScroll = LevelSpritePresentation.captureScroll(GameServices.level());
                    step(loop, input);
                    prefix.add(input);
                    expectedRows.add(capture(module));
                    var picture = render(module, true); // Production overlay and GL at every admitted row.
                    assertTerrainOutsideSprites(picture, renderTerrain(module, previousCompletedScroll), runtime.displayedFrame());
                    observe(output, index + 1, input, module, picture);
                    if (runtime.ringGlobals().rings() > 0) { pickupIndex = index; break; }
                }
                assertTrue(pickupIndex >= 0, "Normal-input prefix did not pick up a Ring within 102 rows; inspect source state before revising input");
                assertTrue(module.requirePlayerActor().getCentreX() > initialX, "Pickup route must move through normal input");
                assertEquals(1, runtime.ringGlobals().rings(), "First live touch commits one Ring");
                assertTrue(runtime.lastSoundRequests().contains(new CdOpeningObjectRuntime.RingFm(CdRing.FmSound.FM_RING)),
                        "Other genuine player sound requests may precede the Ring request");
                assertFalse(Arrays.equals(before.objects().placement().savedFlags().copy(), runtime.placement().capture().savedFlags().copy()));
                // At pickup the displayed picture is still the previous CPU table.
                // One neutral ordinary V-int must publish that completed pickup SAT.
                var pickupCpu = runtime.cpuSat();
                assertTrue(pickupCpu.provenance().stream().anyMatch(p -> p.slotRef().wholeIndex() == 0),
                        "The shared SAT must include the real reserved player, not only Rings");
                var neutral = new Bk2FrameInput(pickupIndex + 1, 0, 0, false, "post-pickup neutral");
                var pickupCompletedScroll = LevelSpritePresentation.captureScroll(GameServices.level());
                step(loop, neutral); prefix.add(neutral); expectedRows.add(capture(module));
                var expectedPicture = com.openggf.game.soniccd.resources.CdObjectPresentation.convert(pickupCpu,
                        com.openggf.game.soniccd.resources.CdObjectPresentation.usaR11aGameOverDevPreloadedArt(
                                module.requirePlayerFrame().artState(), runtime.powerupArtState(), source.assets().openingObjects(),
                                source.assets().openingObjects().frontierArtBank(),
                                source.assets().openingObjects().contactArtBank(),
                                com.openggf.graphics.PatternAtlasRange.OBJECTS.base(),
                                com.openggf.graphics.PatternAtlasRange.OBJECTS.base() + 24,
                                com.openggf.graphics.PatternAtlasRange.OBJECTS.base() + 38,
                                com.openggf.graphics.PatternAtlasRange.OBJECTS.base() + 108,
                                com.openggf.graphics.PatternAtlasRange.OBJECTS.base() + 136,
                                com.openggf.graphics.PatternAtlasRange.OBJECTS.base() + 199,
                                com.openggf.graphics.PatternAtlasRange.OBJECTS.base() + 269,
                                com.openggf.graphics.PatternAtlasRange.OBJECTS.base() + 274,
                                com.openggf.graphics.PatternAtlasRange.OBJECTS.base() + 313));
                assertEquals(expectedPicture, runtime.displayedFrame(), "Next V-int uses the pickup CPU table and now-serviced art");
                int playerBase = com.openggf.graphics.PatternAtlasRange.OBJECTS.base();
                assertTrue(runtime.displayedFrame().patternVersions().keySet().stream()
                        .anyMatch(id -> id >= playerBase && id < playerBase + 24));
                var after = capture(module);
                var afterImage = render(module, true);
                var afterTerrain = renderTerrain(module, pickupCompletedScroll);
                assertTerrainOutsideSprites(afterImage, afterTerrain, runtime.displayedFrame());
                observe(output, pickupIndex + 2, neutral, module, afterImage);
                if (output != null) ScreenshotCapture.savePNG(afterImage, output.resolve("pickup-next-vint.png"));
                assertFalse(Arrays.equals(afterImage.pixels(), afterTerrain.pixels()));
                assertFalse(Arrays.equals(beforeImage.pixels(), afterImage.pixels()), "Route must produce a changed framebuffer");
                assertArrayEquals(afterImage.pixels(), render(module, true).pixels());
                assertCapture(after, module);
                for (boolean courseOnly : List.of(false, true)) {
                    if (courseOnly) registry.restoreCourse(course); else registry.restore(full);
                    assertCapture(before, module);
                    assertArrayEquals(beforeImage.pixels(), render(module, true).pixels(), "Restore must draw the frozen publication");
                    assertCapture(before, module); // Drawing after restore cannot create clocks/art/touch/sounds.
                    for (int i = 0; i < prefix.size(); i++) {
                        step(loop, prefix.get(i));
                        assertCapture(expectedRows.get(i), module);
                        render(module, true);
                    }
                    assertCapture(after, module);
                    assertArrayEquals(afterImage.pixels(), render(module, true).pixels());
                }
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
                var oldFrame = module.requirePlayerFrame();
                GameServices.configuration().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
                assertThrows(IllegalArgumentException.class, () -> boot.bootSonicCdOpeningObjects(source));
                GameServices.configuration().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
                assertSame(mode, SessionManager.getCurrentGameplayMode(), "Rejected boot preserves the current World");
                assertCapture(after, module);
                boot.bootSonicCdOpeningObjects(source); // Fresh World through the actual host; no retained display/CPU.
                assertTrue(runtime.closed()); assertTrue(oldFrame.closed());
                var freshModule = (SonicCdGameModule) SessionManager.getCurrentWorldSession().getGameModule();
                var freshRuntime = (CdOpeningObjectRuntime) freshModule.getGameService(NativeDynamicObjectPass.class);
                assertNotSame(runtime, freshRuntime);
                assertEquals(0, freshRuntime.cpuSat().spriteCount());
                assertTrue(freshRuntime.displayedFrame().tiles().isEmpty());
                assertEquals(0, freshRuntime.ringGlobals().rings());
                assertArrayEquals(cold.pixels(), render(freshModule, true).pixels(), "Fresh boot must not reuse the old picture/GL bank");
                if (output != null) Files.writeString(output.resolve("outcome.txt"),
                        "PASS: actual normal-input Ring pickup, shared displayed SAT, full/course replay and framebuffer equality; fresh/rejected boot ownership.\n"
                        + "Pickup input index: " + pickupIndex + "; route rows after two-row checkpoint: " + prefix.size() + "\n"
                        + "Development USA R11A only; silent audio and explicit object-art preload, no native or full-game parity claim.\n",
                        StandardOpenOption.CREATE_NEW);
            } finally {
                // Retire world-owned banks/pictures BEFORE process graphics and the EGL context.
                try { SessionManager.closeGameplaySession(); }
                finally { GraphicsManager.destroyForReinit(); }
            }
        }
    }

    private static Path openCaptureOutput(String sourceInputLog) throws Exception {
        Path output = openCaptureOutput(sourceInputLog, "soniccd.opening.output");
        if (output != null) Files.writeString(output.resolve("state.csv"),
                "call,input_mask,action_mask,level_frames,x16_16,y16_16,x_velocity,y_velocity,ground_velocity,routine,flags,animation,rings,mosqui_id,mosqui_routine,mosqui_x,mosqui_y,camera_x,camera_y,prepared_camera_x,prepared_camera_y,published_camera_x,published_camera_y,cpu_sprites,displayed_tiles,sounds\n",
                StandardOpenOption.CREATE_NEW);
        return output;
    }

    private static Path openCaptureOutput(String sourceInputLog, String property) throws Exception {
        String requested = System.getProperty(property);
        if (requested == null || requested.isBlank()) return null;
        Path taskRoot = Path.of(System.getProperty("user.home"), "Downloads", "Sonic-CD").toRealPath();
        Path output = Path.of(requested).toAbsolutePath().normalize();
        assertTrue(output.getParent().toRealPath().startsWith(taskRoot), "Capture parent must be in Downloads/Sonic-CD");
        Files.createDirectory(output); // Never replace an earlier capture.
        Files.writeString(output.resolve("input-log.txt"), sourceInputLog, StandardOpenOption.CREATE_NEW);
        return output;
    }

    private static void observe(Path output, int call, Bk2FrameInput input, SonicCdGameModule module,
                                RgbaImage image) throws Exception {
        if (output == null) return;
        var runtime = (CdOpeningObjectRuntime) module.getGameService(NativeDynamicObjectPass.class);
        var motion = module.requirePlayerFrame().state().motion();
        var mosqui = runtime.placement().pool().state(34);
        String sounds = runtime.lastSoundRequests().toString().replace("\"", "\"\"");
        var scroll = ((SonicCdPlayerPresentation) module.getScrollHandlerProvider()).captureScrollPublication();
        String row = String.format(Locale.ROOT,
                "%d,%d,%d,%d,%08X,%08X,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,\"%s\"%n",
                call, input == null ? 0 : input.p1InputMask(), input == null ? 0 : input.p1ActionMask(),
                GameServices.level().getFrameCounter(), motion.centreX16_16(), motion.centreY16_16(),
                motion.xVelocity(), motion.yVelocity(), motion.groundVelocity(), motion.routine(), motion.flags(),
                motion.animation(), runtime.ringGlobals().rings(), mosqui.id(), mosqui.routine(), mosqui.centreX(), mosqui.centreY(),
                GameServices.camera().getX(), GameServices.camera().getY(),
                scroll.prepared() == null ? -1 : scroll.prepared().cameraX(),
                scroll.prepared() == null ? -1 : scroll.prepared().cameraY(),
                scroll.published() == null ? -1 : scroll.published().cameraX(),
                scroll.published() == null ? -1 : scroll.published().cameraY(), runtime.cpuSat().spriteCount(),
                runtime.displayedFrame().tiles().size(), sounds);
        Files.writeString(output.resolve("state.csv"), row, StandardOpenOption.APPEND);
        ScreenshotCapture.savePNG(image, output.resolve(String.format(Locale.ROOT, "frame-%03d.png", call)));
    }

    @Test void actualEglNormalInputEnemyDefeatAndAftermathReplay() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual EGL/media opt-in");
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        String media = System.getProperty("soniccd.media.root"); assertNotNull(media);
        CdGameDataSource source;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); source = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
        // Fixed held-state input proposal; revise only from observed engine state if the route fails.
        // No native rows, frame fences, positions or slot values are supplied to gameplay.
        String script = System.getProperty("soniccd.contact.script", "2 -;80 R;repeat 14 {8 R+B;7 R}");
        String log = InputScriptCompiler.compileToInputLog(script);
        var movie = new Bk2MovieLoader().parseInputLogText(Path.of("contact-world-input-log"), log.lines().toList());
        Path output = openCaptureOutput(log, "soniccd.contact.output");
        if (output != null) Files.writeString(output.resolve("contact-state.csv"),
                "call,centre_x16_16,centre_y16_16,x_velocity,y_velocity,routine,flags,rings,score,camera_x,camera_y\n",
                StandardOpenOption.CREATE_NEW);
        try (var boot = new HeadlessGameBoot(320, 224)) {
            try {
                var loop = boot.bootSonicCdOpeningObjects(source);
                var mode = SessionManager.getCurrentGameplayMode();
                var module = assertInstanceOf(SonicCdGameModule.class, mode.getWorldSession().getGameModule());
                var runtime = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                for (int i = 0; i < 82; i++) step(loop, movie.getFrame(i));
                var registry = mode.getRewindRegistry(); var full = registry.capture(); var course = registry.captureCourse();
                var before = capture(module); var beforePixels = render(module, true);
                var rows = new ArrayList<Bk2FrameInput>(); var expected = new ArrayList<Capture>();
                int killCall = -1, victim = -1;
                RgbaImage previous = beforePixels;
                for (int index = 82; index < movie.getFrameCount(); index++) {
                    var input = movie.getFrame(index);
                    var priorScroll = LevelSpritePresentation.captureScroll(GameServices.level());
                    step(loop, input); rows.add(input); expected.add(capture(module));
                    var image = render(module, true);
                    assertTerrainOutsideSprites(image, renderTerrain(module, priorScroll), runtime.displayedFrame());
                    if (output != null) {
                        var motion = module.requirePlayerFrame().state().motion();
                        String row = String.format(Locale.ROOT, "%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n", index + 1,
                                motion.centreX16_16(), motion.centreY16_16(), motion.xVelocity(), motion.yVelocity(),
                                motion.routine(), motion.flags(), runtime.ringGlobals().rings(), runtime.scoreGlobals().score(),
                                GameServices.camera().getX(), GameServices.camera().getY());
                        Files.writeString(output.resolve("contact-state.csv"), row, StandardOpenOption.APPEND);
                    }
                    if (killCall < 0 && runtime.scoreGlobals().score() != 0) {
                        killCall = index + 1;
                        victim = runtime.placement().pool().capture().slots().stream().filter(s -> s.id() == 0x18)
                                .findFirst().orElseThrow().identity().wholeIndex();
                        assertEquals(10, runtime.scoreGlobals().score());
                        assertTrue(runtime.lastSoundRequests().stream().anyMatch(request -> request instanceof
                                CdOpeningObjectRuntime.PlayerSound sound && sound.sound() instanceof
                                CdPlayerFrameRuntime.ContactFmSound fm && fm.sound() ==
                                com.openggf.game.soniccd.player.CdPlayerTouchResponse.FmSound.FM_DESTROY));
                        if (output != null) {
                            ScreenshotCapture.savePNG(previous, output.resolve("before-defeat.png"));
                            ScreenshotCapture.savePNG(image, output.resolve("defeat.png"));
                        }
                    }
                    if (output != null && killCall >= 0 && index + 1 == killCall + 8) {
                        ScreenshotCapture.savePNG(image, output.resolve("explosion-points.png"));
                    }
                    previous = image;
                    if (killCall >= 0 && index + 1 >= killCall + 26) break;
                }
                assertTrue(killCall >= 0, "Fixed normal-input proposal reached no kill; inspect state before revising input");
                assertEquals(killCall + 26, 82 + rows.size(), "the fixed route must include the complete aftermath interval");
                assertEquals(0x1F, runtime.placement().pool().state(victim).id(), "the live explosion became a flower");
                var after = capture(module); var finalPixels = render(module, true);
                if (output != null) ScreenshotCapture.savePNG(finalPixels, output.resolve("after-defeat.png"));
                for (boolean courseOnly : List.of(false, true)) {
                    if (courseOnly) registry.restoreCourse(course); else registry.restore(full);
                    assertCapture(before, module);
                    assertArrayEquals(beforePixels.pixels(), render(module, true).pixels());
                    for (int i = 0; i < rows.size(); i++) {
                        step(loop, rows.get(i)); assertCapture(expected.get(i), module);
                        render(module, true);
                        assertCapture(expected.get(i), module);
                    }
                    assertCapture(after, module);
                    assertArrayEquals(finalPixels.pixels(), render(module, true).pixels());
                }
                if (output != null) Files.writeString(output.resolve("outcome.txt"),
                        "PASS: normal-input enemy defeat, live aftermath, completed-scroll terrain and full/course rendered replay.\n"
                        + "Defeat call: " + killCall + "; victim slot: " + victim + "; continuation rows: " + rows.size() + "\n"
                        + "Development USA R11A only; silent audio and explicit preloaded object art, no native/full-game parity claim.\n",
                        StandardOpenOption.CREATE_NEW);
            } finally {
                // Retire world banks and pictures while graphics/context remain alive, as in the Ring route.
                try { SessionManager.closeGameplaySession(); }
                finally { GraphicsManager.destroyForReinit(); }
            }
        }
    }

    @Test void actualEglWalkingHitSpillHurtLandingAndFullCourseReplay() throws Exception {
        var route = runWalkingHurtRoute(System.getProperty("soniccd.hurt.script", "2 -;108 R;500 -;100 R"),
                "soniccd.hurt.output", 8, 2);
        assertEquals(new HurtRouteOutcome(644, 686, 2), route);
    }

    @Test void actualEglNativeAlignedDivingHurtSpillLandingAndReplay() throws Exception {
        // Retained native controls first move at level frame198. The neutral lead-in changes
        // only controller timing, never actor/pool/clock state or production behavior.
        var route = runWalkingHurtRoute("197 -;108 R;252 -;153 R", "soniccd.hurt.diving.output", 6, 3);
        assertEquals(new HurtRouteOutcome(600, 643, 3), route);
    }

    @Test void actualEglSecondWalkingContactFallsLosesOneLifeAndReplaysTheResetDelay() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual EGL/media opt-in");
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        String media = System.getProperty("soniccd.media.root"); assertNotNull(media);
        CdGameDataSource source;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); source = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
        // One fixed controller proposal, compiled and re-parsed by production input owners.
        // No coordinates, actor/object bytes, clocks or reference rows are injected.
        String script = System.getProperty("soniccd.death.script", "2 -;108 R;500 -;77 R;230 -;100 R;120 -");
        String log = InputScriptCompiler.compileToInputLog(script);
        var movie = new Bk2MovieLoader().parseInputLogText(Path.of("death-world-input-log"), log.lines().toList());
        Path output = openCaptureOutput(log, "soniccd.death.output");
        if (output != null) Files.writeString(output.resolve("death-state.csv"),
                "call,x16_16,y16_16,x_velocity,y_velocity,ground_velocity,routine,animation,hurt,rings,lives,reset,hud_lives,level_restart,powerup,vint,camera_x,camera_y,lost_rings,game_over_slots,mosqui_routine,mosqui_x,mosqui_y,input_mask,action_mask\n",
                StandardOpenOption.CREATE_NEW);
        try (var boot = new HeadlessGameBoot(320, 224)) {
            try {
                var loop = boot.bootSonicCdOpeningObjects(source);
                var mode = SessionManager.getCurrentGameplayMode();
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
                var module = assertInstanceOf(SonicCdGameModule.class, mode.getWorldSession().getGameModule());
                var runtime = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                for (int i = 0; i < 82; i++) step(loop, movie.getFrame(i));
                var registry = mode.getRewindRegistry(); var full = registry.capture(); var course = registry.captureCourse();
                var before = capture(module); var beforePixels = render(module, true);
                var rows = new ArrayList<Bk2FrameInput>(); var expected = new ArrayList<Capture>();
                int firstHurt = -1, deathCall = -1, lifeCall = -1, deathCameraX = -1, deathCameraY = -1;
                RgbaImage previousImage = beforePixels;
                for (int index = 82; index < movie.getFrameCount(); index++) {
                    var input = movie.getFrame(index);
                    var previousState = module.requirePlayerFrame().state();
                    int previousRings = runtime.ringGlobals().rings(), previousLives = runtime.ringGlobals().lives();
                    var priorScroll = LevelSpritePresentation.captureScroll(GameServices.level());
                    step(loop, input); rows.add(input); expected.add(capture(module));
                    var state = module.requirePlayerFrame().state(); var motion = state.motion();
                    var image = render(module, true);
                    assertTerrainOutsideSprites(image, renderTerrain(module, priorScroll), runtime.displayedFrame());
                    var pool = runtime.placement().pool(); var mosqui = pool.state(34);
                    long lost = pool.capture().slots().stream().filter(slot -> slot.id() == 0x11).count();
                    long gameOver = pool.capture().slots().stream().filter(slot -> slot.id() == 0x3B).count();
                    if (output != null) Files.writeString(output.resolve("death-state.csv"), String.format(Locale.ROOT,
                            "%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                            index + 1, motion.centreX16_16(), motion.centreY16_16(), motion.xVelocity(), motion.yVelocity(),
                            motion.groundVelocity(), motion.routine(), motion.animation(), state.timers().hurt(),
                            runtime.ringGlobals().rings(), runtime.ringGlobals().lives(), state.timers().reset(),
                            runtime.ringGlobals().updateHudLives(), runtime.levelGlobals().levelRestart(), runtime.levelGlobals().powerup(),
                            module.requirePlayerFrame().vIntClock().absoluteValue().orElseThrow(),
                            GameServices.camera().getX(), GameServices.camera().getY(), lost, gameOver,
                            mosqui.routine(), mosqui.centreX(), mosqui.centreY(), input.p1InputMask(), input.p1ActionMask()),
                            StandardOpenOption.APPEND);
                    if (firstHurt < 0 && motion.routine() == 4) {
                        firstHurt = index + 1;
                        assertTrue(previousRings > 0); assertEquals(0, runtime.ringGlobals().rings());
                    }
                    if (deathCall < 0 && motion.routine() == 6) {
                        deathCall = index + 1;
                        assertTrue(firstHurt > 0 && firstHurt < deathCall, "The first hit must precede this second contact");
                        assertEquals(0, previousRings); assertEquals(2, previousState.motion().routine());
                        assertEquals(0, state.timers().hurt()); assertEquals(0x18, motion.animation());
                        assertEquals(-0x700, motion.yVelocity()); assertEquals(3, runtime.ringGlobals().lives());
                        assertTrue(runtime.lastSoundRequests().contains(new CdOpeningObjectRuntime.PlayerSound(
                                new CdPlayerFrameRuntime.ContactFmSound(com.openggf.game.soniccd.player.CdPlayerTouchResponse.FmSound.FM_HURT))));
                        deathCameraX = GameServices.camera().getX(); deathCameraY = GameServices.camera().getY();
                        if (output != null) {
                            ScreenshotCapture.savePNG(previousImage, output.resolve("before-death.png"));
                            ScreenshotCapture.savePNG(image, output.resolve("death-call.png"));
                        }
                    }
                    if (deathCall > 0) {
                        assertEquals(deathCameraX, GameServices.camera().getX());
                        assertEquals(deathCameraY, GameServices.camera().getY());
                        assertEquals(deathCameraY & 0xFFFF, module.requirePlayerFrame().environment().movement().bounds().bottom());
                        assertEquals(0, runtime.levelGlobals().levelRestart(), "Stop before the actual reload boundary");
                        if (output != null && index + 1 == deathCall + 4)
                            ScreenshotCapture.savePNG(image, output.resolve("death-flight.png"));
                    }
                    if (lifeCall < 0 && runtime.ringGlobals().lives() < previousLives) {
                        lifeCall = index + 1;
                        assertTrue(lifeCall > deathCall); assertEquals(6, previousState.motion().routine());
                        assertEquals(2, runtime.ringGlobals().lives()); assertEquals(1, runtime.ringGlobals().updateHudLives());
                        assertEquals(8, motion.routine()); assertEquals(60, state.timers().reset());
                        assertEquals(0, gameOver, "The lives-remaining GameOver owner deletes its newly allocated slot in this same pass");
                        assertEquals(8, runtime.levelGlobals().powerup());
                        assertTrue(runtime.lastSoundRequests().contains(new CdOpeningObjectRuntime.GameOverSubCpu(
                                com.openggf.game.soniccd.objects.CdGameOver.SubCpuCommand.SCMD_FADEPCM)));
                        if (output != null) ScreenshotCapture.savePNG(image, output.resolve("life-lost.png"));
                    }
                    previousImage = image;
                    if (lifeCall > 0 && index + 1 == lifeCall + 4) {
                        assertEquals(8, motion.routine()); assertEquals(56, state.timers().reset()); break;
                    }
                }
                assertTrue(deathCall > firstHurt && firstHurt > 0,
                        "Fixed proposal reached no second zero-ring contact; inspect the retained CSV before revising input");
                assertTrue(lifeCall > deathCall, "Death fall did not reach its native gone line within this input proposal");
                assertEquals(lifeCall + 4, 82 + rows.size());
                var after = capture(module); var finalPixels = render(module, true);
                if (output != null) ScreenshotCapture.savePNG(finalPixels, output.resolve("reset-delay.png"));
                for (boolean courseOnly : List.of(false, true)) {
                    if (courseOnly) registry.restoreCourse(course); else registry.restore(full);
                    assertCapture(before, module); assertArrayEquals(beforePixels.pixels(), render(module, true).pixels());
                    for (int i = 0; i < rows.size(); i++) {
                        step(loop, rows.get(i)); assertCapture(expected.get(i), module);
                        render(module, true); assertCapture(expected.get(i), module);
                    }
                    assertCapture(after, module); assertArrayEquals(finalPixels.pixels(), render(module, true).pixels());
                }
                if (output != null) Files.writeString(output.resolve("outcome.txt"),
                        "PASS: normal-controller second contact, death fall, one life lost, live GameOver deletion, reset delay and full/course rendered replay.\n"
                        + "First hurt: " + firstHurt + "; death: " + deathCall + "; life lost: " + lifeCall + "; replay rows: " + rows.size() + "\n"
                        + "Actual OpenGGF USA R11A DEV: silent audio, explicit preload/clock0, no completed level rebuild or native timing/pixel/full-game parity claim.\n",
                        StandardOpenOption.CREATE_NEW);
            } finally {
                try { SessionManager.closeGameplaySession(); }
                finally { GraphicsManager.destroyForReinit(); }
            }
        }
    }

    @Test void actualEglConstructedGameOverUsesServicedArtReplaysAndRetiresGlyphBank() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual EGL/media opt-in");
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        String media = System.getProperty("soniccd.media.root"); assertNotNull(media);
        CdGameDataSource source;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); source = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
        String log = InputScriptCompiler.compileToInputLog("21 -");
        var movie = new Bk2MovieLoader().parseInputLogText(Path.of("constructed-game-over-input"), log.lines().toList());
        Path output = openCaptureOutput(log, "soniccd.gameover.output");
        try (var boot = new HeadlessGameBoot(320, 224)) {
            try {
                var loop = boot.bootSonicCdOpeningObjects(source);
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
                var mode = SessionManager.getCurrentGameplayMode();
                var module = assertInstanceOf(SonicCdGameModule.class, mode.getWorldSession().getGameModule());
                var runtime = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                step(loop, movie.getFrame(0)); // Real Init, followed by an explicitly constructed component cut.
                for (int expectedLives : new int[] {2, 1, 0}) {
                    var frame = module.requirePlayerFrame(); var actor = frame.captureActor(); var old = actor.state();
                    var m = old.motion(); int goneY = frame.environment().movement().bounds().bottom() + 0x110;
                    var motion = new com.openggf.game.soniccd.player.CdPlayerMotionState(64 << 16, goneY << 16,
                            0, -0x700, 0, 6, 0, 2, 4, 9, 19, 0x18, 0x18, 0, 0, 0, 0, 0,
                            m.previousPrimaryAngle(), m.previousSecondaryAngle());
                    var header = old.header();
                    var state = new com.openggf.game.soniccd.player.CdPlayerBody.State(motion,
                            new com.openggf.game.soniccd.player.CdPlayerBody.AnimationCursor(0, 0, 0),
                            new com.openggf.game.soniccd.player.CdPlayerBody.Header(header.slotId(), header.mappingAddress(),
                                    header.tile() | 0x8000, 0, header.width()),
                            new com.openggf.game.soniccd.player.CdPlayerBody.Timers(0, 0, 0, 0), old.history(), old.rotationDistance(), old.objectLinks());
                    frame.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), state, java.util.Optional.empty()));
                    module.requirePlayerActor().rebindAfterRestore();
                    runtime.placement().pool().clearDynamicSlots();
                    step(loop, movie.getFrame(0));
                    assertEquals(expectedLives, runtime.ringGlobals().lives());
                }
                assertEquals(8, module.requirePlayerFrame().state().motion().routine());
                assertEquals(480, module.requirePlayerFrame().state().timers().reset());
                assertEquals(0x88, runtime.levelGlobals().powerup());
                assertEquals(2, runtime.placement().pool().capture().slots().stream().filter(slot -> slot.id() == 0x3B).count());
                var registry = mode.getRewindRegistry(); var full = registry.capture(); var course = registry.captureCourse();
                var before = capture(module); var beforePixels = render(module, true);
                var expected = new ArrayList<Capture>();
                for (int index = 0; index < movie.getFrameCount(); index++) {
                    step(loop, movie.getFrame(index)); expected.add(capture(module)); render(module, true);
                    assertEquals(8, runtime.levelGlobals().powerup());
                }
                var after = capture(module); var image = render(module, true); var terrain = render(module, false);
                assertEquals(32, runtime.displayedFrame().tiles().size());
                int first = com.openggf.graphics.PatternAtlasRange.OBJECTS.base() + 313;
                assertEquals(32, runtime.displayedFrame().patternVersions().size());
                assertTrue(runtime.displayedFrame().tiles().stream().allMatch(tile ->
                        tile.patternId() >= first && tile.patternId() < first + 32));
                assertArrayEquals(source.assets().shield().copy(3).bytes().copy(),
                        runtime.powerupArtState().residentBank().slice(0, 0x400).copy());
                assertFalse(Arrays.equals(image.pixels(), terrain.pixels()), "Real GPU output must contain the serviced text");
                for (boolean courseOnly : List.of(false, true)) {
                    if (courseOnly) registry.restoreCourse(course); else registry.restore(full);
                    assertCapture(before, module); assertArrayEquals(beforePixels.pixels(), render(module, true).pixels());
                    for (int index = 0; index < movie.getFrameCount(); index++) {
                        step(loop, movie.getFrame(index)); assertCapture(expected.get(index), module);
                        render(module, true); assertCapture(expected.get(index), module);
                    }
                    assertCapture(after, module); assertArrayEquals(image.pixels(), render(module, true).pixels());
                }
                var atlas = GameServices.graphics().getPatternAtlas();
                for (int id = first; id < first + 32; id++) assertNotNull(atlas.getEntry(id));
                if (output != null) {
                    ScreenshotCapture.savePNG(image, output.resolve("game-over.png"));
                    ScreenshotCapture.savePNG(terrain, output.resolve("terrain-control.png"));
                }
                module.closeTerrainPlayer();
                for (int id = first; id < first + 32; id++) assertNull(atlas.getEntry(id),
                        "World retirement must remove the newly added powerup patterns beyond the old 313-pattern window");
                if (output != null) Files.writeString(output.resolve("outcome.txt"),
                        "PASS: constructed zero-lives cut, native GameOver bodies, serviced GAME art, actual EGL text, full/course replay and glyph-bank retirement.\n"
                        + "This is component ingress through the real World, not a normal-controller route to zero lives.\n"
                        + "Three constructed gone-line actor cuts, each preceded by clearing all dynamic object slots, precede the 21 neutral forward inputs.\n"
                        + "No lives/powerup/global setter; actor cuts and object-slot clears are explicit construction. World retirement removed patterns +313..+344.\n"
                        + "No native pixel/timing/full-game parity claim.\n", StandardOpenOption.CREATE_NEW);
            } finally {
                try { SessionManager.closeGameplaySession(); }
                finally { GraphicsManager.destroyForReinit(); }
            }
        }
    }

    @Test void actualEglControllerDeathRebuildsTheLevelKeepsTwoLivesAndReplaysTheNewVisit() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual EGL/media opt-in");
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        String media = System.getProperty("soniccd.media.root"); assertNotNull(media);
        CdGameDataSource source;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); source = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
        String log = InputScriptCompiler.compileToInputLog("2 -;108 R;500 -;77 R;230 -;100 R;120 -;30 R");
        var movie = new Bk2MovieLoader().parseInputLogText(Path.of("restart-world-input"), log.lines().toList());
        Path output = openCaptureOutput(log, "soniccd.restart.output");
        if (output != null) Files.writeString(output.resolve("restart-state.csv"),
                "call,load_generation,level_frames,vint_delta,routine,x16_16,y16_16,lives,rings,reset,level_restart,powerup,camera_x,camera_y,input_mask,action_mask\n",
                StandardOpenOption.CREATE_NEW);
        try (var boot = new HeadlessGameBoot(320, 224)) {
            try {
                var loop = boot.bootSonicCdOpeningObjects(source);
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
                var mode = SessionManager.getCurrentGameplayMode();
                var module = assertInstanceOf(SonicCdGameModule.class, mode.getWorldSession().getGameModule());
                var old = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                var oldFrame = module.requirePlayerFrame();
                long generation = GameServices.level().getCompletedProductionLoadGeneration();
                var registry = mode.getRewindRegistry();
                com.openggf.game.rewind.CompositeSnapshot retiredFull = null, retiredCourse = null;
                com.openggf.game.rewind.CompositeSnapshot newFull = null, newCourse = null;
                Capture beforeCut = null, cut = null, beforeReplay = null;
                RgbaImage cutPixels = null, replayPixels = null;
                var rows = new ArrayList<Bk2FrameInput>(); var expected = new ArrayList<Capture>();
                int restartCall = -1, newVisitCall = -1;
                for (int index = 0; index < movie.getFrameCount(); index++) {
                    var input = movie.getFrame(index); step(loop, input);
                    var runtime = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                    var frame = module.requirePlayerFrame(); var state = frame.state(); var motion = state.motion();
                    var image = render(module, true);
                    if (output != null) Files.writeString(output.resolve("restart-state.csv"), String.format(Locale.ROOT,
                            "%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n", index + 1,
                            GameServices.level().getCompletedProductionLoadGeneration(), GameServices.level().getFrameCounter(),
                            frame.vIntClock().deltaSinceAdmittedCut(), motion.routine(), motion.centreX16_16(),
                            motion.centreY16_16(), runtime.ringGlobals().lives(), runtime.ringGlobals().rings(),
                            state.timers().reset(), runtime.levelGlobals().levelRestart(), runtime.levelGlobals().powerup(),
                            GameServices.camera().getX(), GameServices.camera().getY(), input.p1InputMask(), input.p1ActionMask()),
                            StandardOpenOption.APPEND);
                    if (runtime == old && state.timers().reset() == 1 && motion.routine() == 8) beforeCut = capture(module);
                    if (restartCall < 0 && runtime.levelGlobals().levelRestart() != 0) {
                        restartCall = index + 1; assertEquals(1112, restartCall); assertNotNull(beforeCut);
                        assertSame(old, runtime); assertFalse(old.closed()); assertEquals(2, runtime.ringGlobals().lives());
                        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase());
                        cut = capture(module); cutPixels = image;
                        assertArrayEquals(beforeCut.cpu().bytes(), cut.cpu().bytes());
                        assertEquals(beforeCut.cpu().provenance(), cut.cpu().provenance());
                        assertEquals(beforeCut.camera(), cut.camera()); assertEquals(beforeCut.scroll(), cut.scroll());
                        assertEquals(beforeCut.objects().ringGlobals(), cut.objects().ringGlobals());
                        assertEquals(beforeCut.objects().spillGlobals(), cut.objects().spillGlobals());
                        retiredFull = registry.capture(); retiredCourse = registry.captureCourse();
                        if (output != null) ScreenshotCapture.savePNG(image, output.resolve("restart-cut.png"));
                    }
                    if (newVisitCall < 0 && runtime != old) {
                        newVisitCall = index + 1; assertEquals(restartCall + 1, newVisitCall); assertEquals(1113, newVisitCall);
                        assertNotNull(cut); assertTrue(old.closed()); assertTrue(oldFrame.closed());
                        assertEquals(generation + 1, GameServices.level().getCompletedProductionLoadGeneration());
                        assertEquals(1, GameServices.level().getFrameCounter());
                        assertEquals(1, frame.environment().globals().levelFrames());
                        assertEquals(2, runtime.ringGlobals().lives()); assertEquals(0, runtime.ringGlobals().rings());
                        assertEquals(2, motion.routine()); assertEquals(64 << 16, motion.centreX16_16());
                        assertEquals(0x76B << 16, motion.centreY16_16());
                        assertEquals(0, GameServices.camera().getX()); assertEquals(0x70B, GameServices.camera().getY());
                        assertEquals(0, runtime.levelGlobals().levelRestart());
                        assertArrayEquals(cut.actor().state().history().bytes().copy(), state.history().bytes().copy());
                        assertEquals(0, state.history().index());
                        assertArrayEquals(cut.shared().artState().residentBank().copy(), frame.artState().residentBank().copy());
                        assertArrayEquals(cut.objects().powerupArtState().residentBank().copy(), runtime.powerupArtState().residentBank().copy());
                        assertTrue(runtime.displayedFrame().tiles().isEmpty());
                        var stable = capture(module); var staleFull = retiredFull; var staleCourse = retiredCourse;
                        assertThrows(IllegalArgumentException.class, () -> registry.restore(staleFull));
                        assertThrows(IllegalArgumentException.class, () -> registry.restoreCourse(staleCourse));
                        assertCapture(stable, module);
                        newFull = registry.capture(); newCourse = registry.captureCourse();
                        beforeReplay = stable; replayPixels = image;
                        if (output != null) ScreenshotCapture.savePNG(image, output.resolve("new-visit-init.png"));
                    } else if (newVisitCall > 0) {
                        rows.add(input); expected.add(capture(module));
                    }
                    if (output != null && newVisitCall > 0 && index + 1 == newVisitCall + 5)
                        ScreenshotCapture.savePNG(image, output.resolve("new-visit-sonic.png"));
                }
                assertEquals(1112, restartCall); assertEquals(1113, newVisitCall); assertNotNull(beforeReplay);
                assertEquals(movie.getFrameCount() - newVisitCall, rows.size());
                var runtime = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                var after = capture(module); var finalPixels = render(module, true);
                int playerBase = com.openggf.graphics.PatternAtlasRange.OBJECTS.base();
                assertTrue(runtime.displayedFrame().tiles().stream().anyMatch(tile ->
                        tile.patternId() >= playerBase && tile.patternId() < playerBase + 24),
                        "The rebuilt visit must actually display Sonic, not only scenery");
                assertFalse(Arrays.equals(cutPixels.pixels(), finalPixels.pixels()));
                assertEquals(2, runtime.ringGlobals().lives());
                for (boolean courseOnly : List.of(false, true)) {
                    if (courseOnly) registry.restoreCourse(newCourse); else registry.restore(newFull);
                    assertCapture(beforeReplay, module); assertArrayEquals(replayPixels.pixels(), render(module, true).pixels());
                    for (int index = 0; index < rows.size(); index++) {
                        step(loop, rows.get(index)); assertCapture(expected.get(index), module);
                        render(module, true); assertCapture(expected.get(index), module);
                    }
                    assertCapture(after, module); assertArrayEquals(finalPixels.pixels(), render(module, true).pixels());
                }
                if (output != null) {
                    ScreenshotCapture.savePNG(finalPixels, output.resolve("new-visit-final.png"));
                    Files.writeString(output.resolve("outcome.txt"), "PASS: controller-only death, native restart cut and rebuilt USA R11A visit retaining two lives.\n"
                            + "Restart cut " + restartCall + "; new visit " + newVisitCall + "; rendered new-visit replay rows " + rows.size() + " in each rewind mode.\n"
                            + "Actual OpenGGF DEV: no LevelStart fade/loading/title/HUD/audio/PLC timing; clock seed0 and raw-held carry are explicit policy.\n"
                            + "No native timing/pixel/full-game parity claim.\n", StandardOpenOption.CREATE_NEW);
                }
            } finally {
                try { SessionManager.closeGameplaySession(); }
                finally { GraphicsManager.destroyForReinit(); }
            }
        }
    }

    private record HurtRouteOutcome(int hitCall, int landingCall, int spilled) { }

    private HurtRouteOutcome runWalkingHurtRoute(String script, String outputProperty,
                                               int expectedEnemyRoutine, int expectedRings) throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual EGL/media opt-in");
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        String media = System.getProperty("soniccd.media.root"); assertNotNull(media);
        CdGameDataSource source;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); source = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
        // Both scripts use normal held controllers, including any initial neutral input.
        // No actor, object, clock or collision observation supplies gameplay state.
        String log = InputScriptCompiler.compileToInputLog(script);
        var movie = new Bk2MovieLoader().parseInputLogText(Path.of("hurt-world-input-log"), log.lines().toList());
        Path output = openCaptureOutput(log, outputProperty);
        if (output != null) Files.writeString(output.resolve("hurt-state.csv"),
                "call,x16_16,y16_16,x_velocity,y_velocity,ground_velocity,routine,flags,animation,hurt,rings,lost_rings,spill_timer,spill_accumulator,spill_frame,vint,camera_x,camera_y,mosqui_id,mosqui_routine,mosqui_x,mosqui_y,mosqui_sprite_flags,input_mask,action_mask\n",
                StandardOpenOption.CREATE_NEW);
        try (var boot = new HeadlessGameBoot(320, 224)) {
            try {
                var loop = boot.bootSonicCdOpeningObjects(source);
                var mode = SessionManager.getCurrentGameplayMode();
                var module = assertInstanceOf(SonicCdGameModule.class, mode.getWorldSession().getGameModule());
                var runtime = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                for (int i = 0; i < 82; i++) step(loop, movie.getFrame(i));
                var registry = mode.getRewindRegistry(); var full = registry.capture(); var course = registry.captureCourse();
                var before = capture(module); var beforePixels = render(module, true);
                var rows = new ArrayList<Bk2FrameInput>(); var expected = new ArrayList<Capture>();
                int hitCall = -1, landingCall = -1, spilled = -1;
                RgbaImage previousImage = beforePixels;
                for (int index = 82; index < movie.getFrameCount(); index++) {
                    var input = movie.getFrame(index);
                    var previousState = module.requirePlayerFrame().state();
                    int previousRings = runtime.ringGlobals().rings();
                    var priorScroll = LevelSpritePresentation.captureScroll(GameServices.level());
                    step(loop, input); rows.add(input); expected.add(capture(module));
                    var state = module.requirePlayerFrame().state(); var motion = state.motion();
                    var image = render(module, true);
                    assertTerrainOutsideSprites(image, renderTerrain(module, priorScroll), runtime.displayedFrame());
                    int lostRings = (int) runtime.placement().pool().capture().slots().stream().filter(s -> s.id() == 0x11).count();
                    if (output != null) {
                        var spill = runtime.spillGlobals(); var mosqui = runtime.placement().pool().state(34);
                        String row = String.format(Locale.ROOT, "%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                                index + 1, motion.centreX16_16(), motion.centreY16_16(), motion.xVelocity(), motion.yVelocity(),
                                motion.groundVelocity(), motion.routine(), motion.flags(), motion.animation(), state.timers().hurt(),
                                runtime.ringGlobals().rings(), lostRings, spill.timerByte(), spill.accumWord(), spill.frameByte(),
                                module.requirePlayerFrame().vIntClock().absoluteValue().orElseThrow(),
                                GameServices.camera().getX(), GameServices.camera().getY(), mosqui.id(), mosqui.routine(),
                                mosqui.centreX(), mosqui.centreY(), mosqui.spriteFlags(), input.p1InputMask(), input.p1ActionMask());
                        Files.writeString(output.resolve("hurt-state.csv"), row, StandardOpenOption.APPEND);
                    }
                    if (hitCall < 0 && motion.routine() == 4) {
                        hitCall = index + 1; spilled = Math.min(previousRings, 32);
                        assertEquals(2, previousState.motion().routine());
                        assertTrue(previousRings > 0, "Natural pickup must precede the walking hit");
                        assertEquals(expectedRings, previousRings);
                        var enemy = runtime.placement().pool().state(34);
                        assertEquals(0x3D, enemy.id()); assertEquals(expectedEnemyRoutine, enemy.routine());
                        assertEquals(0, runtime.ringGlobals().rings()); assertEquals(spilled, lostRings);
                        assertEquals(0x1A, motion.animation()); assertEquals(120, state.timers().hurt());
                        assertEquals(0x200, Math.abs(motion.xVelocity())); assertEquals(-0x400, motion.yVelocity());
                        assertTrue(runtime.lastSoundRequests().stream().anyMatch(CdOpeningObjectRuntime.LostRingFm.class::isInstance));
                        if (output != null) {
                            ScreenshotCapture.savePNG(previousImage, output.resolve("before-hit.png"));
                            ScreenshotCapture.savePNG(image, output.resolve("hit-call.png"));
                        }
                    } else if (hitCall >= 0 && landingCall < 0 && motion.routine() == 2) {
                        landingCall = index + 1;
                        assertEquals(4, previousState.motion().routine()); assertEquals(0, motion.flags() & 2);
                        assertEquals(0, motion.xVelocity()); assertEquals(0, motion.yVelocity());
                        assertEquals(0, motion.groundVelocity()); assertEquals(120, state.timers().hurt());
                        if (output != null) ScreenshotCapture.savePNG(image, output.resolve("landing.png"));
                    } else if (hitCall >= 0 && landingCall < 0) {
                        assertEquals(4, motion.routine()); assertEquals(120, state.timers().hurt(), "Hurt skips Main's Display countdown");
                    }
                    if (output != null && hitCall >= 0 && index + 1 == hitCall + 4)
                        ScreenshotCapture.savePNG(image, output.resolve("hurt-spill.png"));
                    previousImage = image;
                    if (landingCall >= 0 && index + 1 == landingCall + 1) {
                        assertEquals(2, motion.routine()); assertEquals(119, state.timers().hurt(), "First Main after landing resumes Display");
                        break;
                    }
                }
                assertTrue(hitCall >= 0, "Fixed walking input reached no hit; inspect state before revising the proposal");
                assertTrue(landingCall > hitCall, "Hurt did not land within the fixed input proposal");
                assertEquals(landingCall + 1, 82 + rows.size());
                var after = capture(module); var finalPixels = render(module, true);
                if (output != null) ScreenshotCapture.savePNG(finalPixels, output.resolve("after-landing.png"));
                for (boolean courseOnly : List.of(false, true)) {
                    if (courseOnly) registry.restoreCourse(course); else registry.restore(full);
                    assertCapture(before, module);
                    assertArrayEquals(beforePixels.pixels(), render(module, true).pixels());
                    for (int i = 0; i < rows.size(); i++) {
                        step(loop, rows.get(i)); assertCapture(expected.get(i), module);
                        render(module, true); assertCapture(expected.get(i), module);
                    }
                    assertCapture(after, module);
                    assertArrayEquals(finalPixels.pixels(), render(module, true).pixels());
                }
                if (output != null) Files.writeString(output.resolve("outcome.txt"),
                        "PASS: controller-only walking hit, real ring spill, routine-4 recovery, landing and full/course rendered replay.\n"
                        + "Hit call: " + hitCall + "; rings spilled: " + spilled + "; landing call: " + landingCall
                        + "; continuation rows: " + rows.size() + "\n"
                        + "Actual OpenGGF USA R11A DEV: silent audio, preloaded object art, declared V-int seed 0; no native timing/pixel/full-game parity claim.\n",
                        StandardOpenOption.CREATE_NEW);
                return new HurtRouteOutcome(hitCall, landingCall, spilled);
            } finally {
                try { SessionManager.closeGameplaySession(); }
                finally { GraphicsManager.destroyForReinit(); }
            }
        }
    }

    private static void step(GameLoop loop, Bk2FrameInput row) {
        loop.getInputHandler().setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(row.p1InputMask(), 0, row.p1ActionMask(), 0, row.p1StartPressed(), false),
                PlayerInputState.neutral()));
        loop.step(); // Existing GameLoop/controlled-frame/input/PLC/camera owners, no direct body calls.
    }

    private static RgbaImage render(SonicCdGameModule module, boolean objects) {
        var graphics = GameServices.graphics(); var level = GameServices.level();
        graphics.runPendingRenderThreadTasks();
        // Clear on EVERY draw, including the tiles-only control, to reject stale framebuffer evidence.
        level.setClearColor(); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        graphics.resetForFixedFunction();
        if (objects) module.gameplayFrameController().drawOverlay();
        else {
            GameServices.parallax().update(0, 0, GameServices.camera(), level.getFrameCounter());
            level.drawWithRenderOptions(GameServices.sprites(),
                    LevelManager.LevelRenderOptions.tilesOnly());
        }
        graphics.flush(); graphics.flushScreenSpace(); glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }

    private static RgbaImage renderTerrain(SonicCdGameModule module, LevelSpritePresentation.ScrollFrame scroll) {
        var graphics = GameServices.graphics(); var level = GameServices.level();
        graphics.runPendingRenderThreadTasks(); level.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); graphics.resetForFixedFunction();
        LevelSpritePresentation.withScroll(level, scroll, () ->
                level.drawWithRenderOptions(GameServices.sprites(), LevelManager.LevelRenderOptions.tilesOnly()));
        graphics.flush(); graphics.flushScreenSpace(); glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }

    private static void assertTerrainOutsideSprites(RgbaImage actual, RgbaImage terrain,
                                                     com.openggf.graphics.SpritePresentation.Frame displayed) {
        boolean[] covered = new boolean[320 * 224];
        for (var tile : displayed.tiles()) {
            int left = Math.max(0, (int) Math.floor(tile.x()));
            int right = Math.min(320, (int) Math.ceil(tile.x() + tile.width()));
            int top = Math.max(0, (int) Math.floor(tile.y()));
            int bottom = Math.min(224, (int) Math.ceil(tile.y() + tile.height()));
            for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) covered[y * 320 + x] = true;
        }
        int compared = 0;
        for (int at = 0; at < covered.length; at++) {
            if (covered[at]) continue;
            compared++;
            if (actual.pixels()[at] != terrain.pixels()[at]) {
                fail("Terrain and displayed SAT use different completed loops at pixel " + (at % 320) + "," + (at / 320));
            }
        }
        assertTrue(compared > covered.length / 2, "The control must compare substantial terrain outside real sprite bounds");
    }

    private record Capture(CdPlayerFrameRuntime.ActorState actor, CdPlayerFrameRuntime.SharedState shared,
                           CdOpeningObjectRuntime.Snapshot objects, CdObjectDrawQueue.CpuSat cpu,
                           List<CdPlayerFrameRuntime.Sound> actorSounds, Object camera, Object scroll,
                           int levelFrames, PlayerResidentBanks.Snapshot bank,
                           SonicCdPlayerPresentation.ScrollPublication publication) { }
    private static Capture capture(SonicCdGameModule module) {
        var runtime = (CdOpeningObjectRuntime) module.getGameService(NativeDynamicObjectPass.class);
        var frame = module.requirePlayerFrame(); var actor = module.requirePlayerActor();
        return new Capture(frame.captureActor(), frame.captureShared(), runtime.captureClosedCut(), runtime.cpuSat(),
                actor.lastSoundRequests(), GameServices.camera().capture(), module.getScrollHandlerProvider().getHandler(0).captureRewindState(),
                GameServices.level().getFrameCounter(), PlayerResidentBanks.capture(actor.getSpriteRenderer()),
                ((SonicCdPlayerPresentation) module.getScrollHandlerProvider()).captureScrollPublication());
    }
    private static void assertCapture(Capture expected, SonicCdGameModule module) {
        var actual = capture(module);
        assertEquals(expected.actor(), actual.actor());
        assertEquals(expected.actorSounds(), actual.actorSounds());
        assertEquals(expected.shared().environment(), actual.shared().environment());
        assertEquals(expected.shared().vIntClock(), actual.shared().vIntClock());
        assertEquals(expected.shared().paused(), actual.shared().paused());
        assertEquals(expected.shared().artState().lastFrame(), actual.shared().artState().lastFrame());
        assertEquals(expected.shared().artState().dirty(), actual.shared().artState().dirty());
        assertArrayEquals(expected.shared().artState().cpuBuffer().copy(), actual.shared().artState().cpuBuffer().copy());
        assertArrayEquals(expected.shared().artState().residentBank().copy(), actual.shared().artState().residentBank().copy());
        assertEquals(expected.objects().placement().pool().slots(), actual.objects().placement().pool().slots());
        assertEquals(expected.objects().placement().status(), actual.objects().placement().status());
        assertArrayEquals(expected.objects().placement().savedFlags().copy(), actual.objects().placement().savedFlags().copy());
        assertEquals(expected.objects().ringGlobals(), actual.objects().ringGlobals());
        assertEquals(expected.objects().spillGlobals(), actual.objects().spillGlobals());
        assertEquals(expected.objects().devGlobals(), actual.objects().devGlobals());
        assertEquals(expected.objects().contactGlobals(), actual.objects().contactGlobals());
        assertEquals(expected.objects().scoreGlobals(), actual.objects().scoreGlobals());
        assertEquals(expected.objects().levelGlobals(), actual.objects().levelGlobals());
        assertEquals(expected.objects().powerupArtState(), actual.objects().powerupArtState());
        assertEquals(expected.objects().lastSoundRequests(), actual.objects().lastSoundRequests());
        assertArrayEquals(expected.cpu().bytes(), actual.cpu().bytes());
        assertEquals(expected.cpu().provenance(), actual.cpu().provenance());
        assertEquals(expected.objects().displayedFrame(), actual.objects().displayedFrame());
        assertEquals(expected.camera(), actual.camera()); assertEquals(expected.scroll(), actual.scroll());
        assertEquals(expected.levelFrames(), actual.levelFrames());
        assertEquals(expected.publication(), actual.publication());
        var a = expected.bank().tiles(); var b = actual.bank().tiles();
        assertEquals(24, b.length);
        for (int i = 0; i < 24; i++) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
            assertEquals(a[i].getPixel(x, y), b[i].getPixel(x, y));
    }
}
