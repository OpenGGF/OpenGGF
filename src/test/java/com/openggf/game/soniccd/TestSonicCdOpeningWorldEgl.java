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
import com.openggf.level.NativeBackgroundTestAccess;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane;
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

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Finite USA normal-controller routes and explicitly constructed component cuts, with full/course restore.
 * Opt in with soniccd.media.root and soniccd.opening.egl=true; an opted-in run
 * fails on missing media, EGL selection or pixels. Optional soniccd.*.output properties
 * save labelled actual engine pictures and observations in new Downloads/Sonic-CD directories.
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
                int pictureBase = com.openggf.graphics.PatternAtlasRange.OBJECTS.base();
                var banks = new CdOpeningObjectRuntime.LogicalBanks(pictureBase, pictureBase + 24, pictureBase + 38,
                        pictureBase + 108, pictureBase + 136, pictureBase + 199, pictureBase + 269,
                        pictureBase + 274, pictureBase + 313, pictureBase + 349, pictureBase + 364);
                var expectedPicture = com.openggf.game.soniccd.resources.CdObjectPresentation.convert(pickupCpu,
                        com.openggf.game.soniccd.resources.CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(
                                module.requirePlayerFrame().artState(), runtime.powerupArtState(), source.assets().openingObjects(),
                                source.assets().openingObjects().frontierArtBank(),
                                source.assets().openingObjects().contactArtBank(),
                                source.assets().openingObjects().floatBlockArtBank(),
                                source.assets().openingObjects().springArtBank(),
                                source.assets().openingObjects().tunnelDoorArtBank(),
                                source.assets().openingObjects().monitorTimeArtBank(), runtime.sectionArtState().residency(),
                                banks.player(), banks.ring(), banks.mosqui(), banks.plant(), banks.ramp(),
                                banks.explosion(), banks.points(), banks.flower(), banks.powerup(), banks.floatBlock(), banks.spring(),
                                banks.tunnelDoorVertical(), banks.monitorTime(), banks.boulder(), banks.diagonalSpring(), banks.spikes(), banks.checkpoint(), banks.anton()));
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

    @Test void actualEglConstructedFallingFloatBlockReplaysAndRetiresItsNewPatternSpan() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual EGL/media opt-in");
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        String media = System.getProperty("soniccd.media.root"); assertNotNull(media);
        CdGameDataSource source;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); source = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
        String log = InputScriptCompiler.compileToInputLog("8 -");
        var movie = new Bk2MovieLoader().parseInputLogText(Path.of("constructed-float-block-input"), log.lines().toList());
        Path output = openCaptureOutput(log, "soniccd.floatblock.output");
        try (var boot = new HeadlessGameBoot(320, 224)) {
            try {
                var loop = boot.bootSonicCdOpeningObjects(source);
                var mode = SessionManager.getCurrentGameplayMode();
                var module = assertInstanceOf(SonicCdGameModule.class, mode.getWorldSession().getGameModule());
                var runtime = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                step(loop, movie.getFrame(0)); // Real player Init; no player or global setter below.
                var pool = runtime.placement().pool(); pool.clearDynamicSlots();
                // Explicit constructed falling routine, not a Present route into the Future-only Fall transition.
                var s = pool.state(40).moveByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.ID, 0x12)
                        .moveByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.ROUTINE, 4)
                        .moveByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.SPRITE_FLAGS, 0x84)
                        .moveByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.PRIORITY, 1)
                        .moveByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.WIDTH, 12)
                        .moveByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.Y_RADIUS, 12)
                        .moveByte(com.openggf.game.soniccd.objects.CdObjectState.ByteField.MAP_FRAME, 5)
                        .moveLong(com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS, 0x20A3FA)
                        .moveWord(com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE, 0x4334)
                        .moveWord(com.openggf.game.soniccd.objects.CdObjectState.WordField.X, 200)
                        .moveWord(com.openggf.game.soniccd.objects.CdObjectState.WordField.Y, 0x740);
                pool.write(s);
                step(loop, movie.getFrame(1));
                assertEquals(0x742, pool.state(40).centreY());
                var registry = mode.getRewindRegistry(); var full = registry.capture(); var course = registry.captureCourse();
                var before = capture(module); var beforePixels = render(module, true);
                var expected = new ArrayList<Capture>(); var expectedPixels = new ArrayList<int[]>();
                for (int i = 2; i < 8; i++) {
                    step(loop, movie.getFrame(i)); expected.add(capture(module));
                    expectedPixels.add(render(module, true).pixels().clone());
                }
                var after = capture(module); var image = render(module, true); var terrain = render(module, false);
                assertEquals(0x74E, pool.state(40).centreY(), "seven source Fall calls advance two pixels each");
                assertFalse(Arrays.equals(image.pixels(), terrain.pixels()), "actual object overlay must reach the GPU");
                int first = com.openggf.graphics.PatternAtlasRange.OBJECTS.base() + 349;
                var floatTiles = runtime.displayedFrame().tiles().stream()
                        .filter(t -> t.patternId() >= first && t.patternId() < first + 15).toList();
                assertEquals(9, floatTiles.size());
                int changedFloatPixels = 0;
                var imagePixels = image.pixels(); var terrainPixels = terrain.pixels();
                for (var tile : floatTiles) {
                    assertTrue(tile.x() >= 180 && tile.x() + tile.width() <= 220, "Float-only region excludes Sonic near X64");
                    for (var other : runtime.displayedFrame().tiles()) {
                        if (other.patternId() >= first && other.patternId() < first + 15) continue;
                        assertTrue(other.x() + other.width() <= tile.x() || other.x() >= tile.x() + tile.width()
                                        || other.y() + other.height() <= tile.y() || other.y() >= tile.y() + tile.height(),
                                "control footprint cannot include any other sprite");
                    }
                    for (int y = (int) tile.y(); y < tile.y() + tile.height(); y++) {
                        for (int x = (int) tile.x(); x < tile.x() + tile.width(); x++) {
                            assertTrue(x >= 0 && x < 320 && y >= 0 && y < 224);
                            if (imagePixels[y * 320 + x] != terrainPixels[y * 320 + x]) changedFloatPixels++;
                        }
                    }
                }
                assertTrue(changedFloatPixels > 0, "GPU must draw inside the Float-only footprint, independently of Sonic");
                var atlas = GameServices.graphics().getPatternAtlas();
                for (int i = 6; i < 15; i++) {
                    assertNotNull(atlas.getEntry(first + i));
                    assertEquals(com.openggf.game.soniccd.resources.CdObjectPresentation.patternVersion(
                                    source.assets().openingObjects().floatBlockArtBank().tile(0x334 + i)),
                            runtime.displayedFrame().patternVersions().get(first + i));
                }
                for (boolean courseOnly : List.of(false, true)) {
                    if (courseOnly) registry.restoreCourse(course); else registry.restore(full);
                    assertCapture(before, module); assertArrayEquals(beforePixels.pixels(), render(module, true).pixels());
                    for (int i = 2; i < 8; i++) {
                        step(loop, movie.getFrame(i)); assertCapture(expected.get(i - 2), module);
                        assertArrayEquals(expectedPixels.get(i - 2), render(module, true).pixels(), "rendered row " + i);
                    }
                    assertCapture(after, module); assertArrayEquals(image.pixels(), render(module, true).pixels());
                }
                if (output != null) {
                    ScreenshotCapture.savePNG(image, output.resolve("constructed-float-block.png"));
                    ScreenshotCapture.savePNG(terrain, output.resolve("terrain-control.png"));
                }
                module.closeTerrainPlayer();
                for (int i = 6; i < 15; i++) assertNull(atlas.getEntry(first + i),
                        "retirement must remove Float patterns beyond the old349-pattern picture");
                if (output != null) Files.writeString(output.resolve("outcome.txt"),
                        "PASS: constructed falling FloatBlock, real World live dispatch, decoded nine-tile picture with Float-only GPU differences, full/course six-row pixel replay and new-bank retirement.\n"
                        + "All dynamic slots were explicitly cleared, then one routine4 FloatBlock was placed at(200,$740). No player/global setter.\n"
                        + "This is constructed component ingress, not a Present route into Future falling or standing-object MODE support.\n"
                        + "USA DEV preload only; no native timing, live PLC residency, pixel, act or full-game parity claim.\n", StandardOpenOption.CREATE_NEW);
            } finally {
                try { SessionManager.closeGameplaySession(); }
                finally { GraphicsManager.destroyForReinit(); }
            }
        }
    }

    @Test void actualEglConstructedNormalSpringWorldAnimatesAll36TilesReplaysAndRetires400Patterns() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual EGL/media opt-in");
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        String media = System.getProperty("soniccd.media.root"); assertNotNull(media); assertFalse(media.isBlank());
        CdGameDataSource source;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); source = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
        String log = InputScriptCompiler.compileToInputLog("8 -");
        var movie = new Bk2MovieLoader().parseInputLogText(Path.of("constructed-normal-spring-world-input"), log.lines().toList());
        Path output = openCaptureOutput(log, "soniccd.spring.output");
        // Hand literals from MapSpr_Spring1/2 (Spring/Data/Mappings.asm:1-44, native pin1ecc5f3af):
        // each piece is {signed Y, size, tile offset, signed X}; each root has its own three-frame index.
        int[][][] pieces = {
                {{-8, 12, 0, -16}, {0, 12, 4, -16}}, {{0, 12, 0, -16}},
                {{-32, 12, 0, -16}, {-24, 6, 8, -8}, {0, 12, 14, -16}},
                {{-16, 3, 18, 0}, {-16, 3, 22, -8}}, {{-16, 3, 18, -8}},
                {{-16, 3, 18, 24}, {-8, 9, 26, 0}, {-16, 3, 32, -8}}
        };
        var offsets = List.of(List.of(4, 5, 6, 7, 0, 1, 2, 3), List.of(0, 1, 2, 3),
                List.of(14, 15, 16, 17, 8, 11, 9, 12, 10, 13, 0, 1, 2, 3),
                List.of(22, 23, 24, 25, 18, 19, 20, 21), List.of(18, 19, 20, 21),
                List.of(32, 33, 34, 35, 26, 28, 30, 27, 29, 31, 18, 19, 20, 21));
        // .Compress (Spring/Data/Animations.asm:4-8): delay0, frames0,1,1,2,... .
        // Six real animator calls prepare [0,1,1,2,2,2]; the NEXT V-int displays [0,0,1,1,2,2].
        int[] cpuFrames = {0, 1, 1, 2, 2, 2}, displayedFrames = {0, 0, 1, 1, 2, 2};
        try (var boot = new HeadlessGameBoot(320, 224)) {
            try {
                var loop = boot.bootSonicCdOpeningObjects(source);
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
                var mode = SessionManager.getCurrentGameplayMode();
                var module = assertInstanceOf(SonicCdGameModule.class, mode.getWorldSession().getGameModule());
                var runtime = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                var frame = module.requirePlayerFrame();
                step(loop, movie.getFrame(0)); // Real player Init before the explicit constructed object ingress.
                var pool = runtime.placement().pool();
                pool.clearDynamicSlots(); // Deliberately isolate glyphs: ALL dynamic objects are cleared; Sonic remains live.
                int cameraX = GameServices.camera().getX(), cameraY = GameServices.camera().getY();
                int[] worldX = {cameraX + 112, cameraX + 192, cameraX + 272};
                int worldY = cameraY + 72;
                for (int family = 0; family < 3; family++) {
                    pool.write(pool.state(40 + family).moveByte(ID, 0x0A).moveWord(X, worldX[family]).moveWord(Y, worldY)
                            .moveByte(SUBTYPE, family == 1 ? 6 : 0).moveByte(SPRITE_FLAGS, family == 2 ? 2 : 0));
                }
                step(loop, movie.getFrame(1)); // Actual physical ID$0A dispatcher runs native Init, not a direct body call.
                assertEquals(List.of(2, 8, 14), List.of(pool.state(40).routine(), pool.state(41).routine(), pool.state(42).routine()));
                assertEquals(List.of(0x209BA0, 0x209BA6, 0x209BA0), List.of(pool.state(40).longBits(MAPPINGS),
                        pool.state(41).longBits(MAPPINGS), pool.state(42).longBits(MAPPINGS)));
                assertEquals(List.of(0x520, 0x2520, 0x520), List.of(pool.state(40).word(TILE), pool.state(41).word(TILE), pool.state(42).word(TILE)));
                int[] animationRoutines = {4, 10, 16};
                for (int family = 0; family < 3; family++) {
                    assertEquals(0x80, pool.state(40 + family).spriteFlags() & 0x80, "The real queue made this Spring visible");
                    // EXPLICIT constructed routine cuts after actual Init. This does NOT prove a contact/launch route.
                    // No MAP_FRAME/script/timer patch: each later frame comes from the real source animator dispatch.
                    pool.write(pool.state(40 + family).moveByte(ROUTINE, animationRoutines[family]));
                }
                var atlas = GameServices.graphics().getPatternAtlas();
                int base = com.openggf.graphics.PatternAtlasRange.OBJECTS.base();
                var banks = new CdOpeningObjectRuntime.LogicalBanks(base, base + 24, base + 38);
                assertEquals(base + 364, banks.spring());
                // Reservation is the existing exclusive governed OBJECTS range; only the400 picture IDs are retired.
                for (int offset = 0; offset < 400; offset++) {
                    int id = base + offset;
                    assertEquals(1, atlas.registeredRangesForTesting().stream()
                            .filter(range -> id >= range.base() && id < range.base() + range.size()).count(), "reservation " + id);
                }
                var objects = source.assets().openingObjects(); var springArt = objects.springArtBank();
                var policy = com.openggf.game.soniccd.resources.CdObjectPresentation.usaR11aSpringDevPreloadedArt(
                        frame.artState(), runtime.powerupArtState(), objects, objects.frontierArtBank(), objects.contactArtBank(),
                        objects.floatBlockArtBank(), springArt, banks.player(), banks.ring(), banks.mosqui(), banks.plant(), banks.ramp(),
                        banks.explosion(), banks.points(), banks.flower(), banks.powerup(), banks.floatBlock(), banks.spring());
                int[] nativeFirst = {0x780, 0x7AE, 0x3A0, 0x424, 0x441, 0x680, 0x6C6, 0x6D7, 0x544, 0x334, 0x520};
                int[] counts = {24, 14, 70, 28, 63, 70, 5, 39, 36, 15, 36};
                int logical = base;
                for (int span = 0; span < counts.length; span++) for (int tile = 0; tile < counts[span]; tile++) {
                    assertEquals(logical++, policy.resolve(nativeFirst[span] + tile).logicalPatternId());
                }
                assertEquals(base + 400, logical, "All eleven actual policy spans cover exactly400 logical IDs");
                var registry = mode.getRewindRegistry(); var full = registry.capture(); var course = registry.captureCourse();
                var before = capture(module); var beforePixels = render(module, true);
                var expected = new ArrayList<Capture>(); var expectedPixels = new ArrayList<int[]>();
                var reached = new java.util.TreeSet<Integer>();
                var phasePictures = new RgbaImage[3];
                var previousCpu = runtime.cpuSat();
                int previousCameraX = GameServices.camera().getX(), previousCameraY = GameServices.camera().getY();
                for (int row = 2; row < 8; row++) {
                    step(loop, movie.getFrame(row));
                    var completed = capture(module); expected.add(completed);
                    var published = completed.publication().published();
                    assertEquals(previousCameraX, published.cameraX()); assertEquals(previousCameraY, published.cameraY());
                    // Validate BOTH the prior CPU SAT now displayed and the newly completed CPU SAT, without calling convert.
                    for (int cut = 0; cut < 2; cut++) {
                        var sat = cut == 0 ? previousCpu : runtime.cpuSat(); var raw = sat.bytes();
                        int mapFrame = cut == 0 ? displayedFrames[row - 2] : cpuFrames[row - 2];
                        int cx = cut == 0 ? previousCameraX : GameServices.camera().getX();
                        int cy = cut == 0 ? previousCameraY : GameServices.camera().getY();
                        for (int family = 0; family < 3; family++) {
                            var literal = pieces[(family == 1 ? 3 : 0) + mapFrame]; int seen = 0;
                            for (int entry = 0; entry < sat.spriteCount(); entry++) {
                                var origin = sat.provenance().get(entry);
                                if (origin.slotRef().wholeIndex() != 40 + family) continue;
                                assertTrue(seen < literal.length); var p = literal[seen];
                                assertEquals(4, origin.queueBucket()); assertEquals(seen++, origin.mappingPieceOrdinal());
                                int height = (p[1] & 3) + 1, relativeY = family == 2 ? -p[0] - height * 8 : p[0];
                                int at = entry * 8;
                                assertEquals(worldY - cy + 128 + relativeY, ((raw[at] & 255) << 8) | (raw[at + 1] & 255));
                                assertEquals(p[1], raw[at + 2] & 255); assertEquals(entry + 1, raw[at + 3] & 255);
                                assertEquals(((family == 1 ? 0x2520 : 0x520) + p[2]) ^ (family == 2 ? 0x1000 : 0),
                                        ((raw[at + 4] & 255) << 8) | (raw[at + 5] & 255));
                                assertEquals((worldX[family] - cx + 128 + p[3]) & 0x1FF,
                                        ((raw[at + 6] & 255) << 8) | (raw[at + 7] & 255));
                            }
                            assertEquals(literal.length, seen, "Every native Spring piece must traverse its physical queue slot");
                        }
                    }
                    var picture = runtime.displayedFrame();
                    var image = render(module, true); expectedPixels.add(image.pixels().clone());
                    var terrain = renderTerrain(module, published);
                    assertTerrainOutsideSprites(image, terrain, picture);
                    for (int family = 0; family < 3; family++) {
                        int centreX = worldX[family] - previousCameraX, centreY = worldY - previousCameraY;
                        var tiles = picture.tiles().stream().filter(t -> t.patternId() >= banks.spring() && t.patternId() < banks.spring() + 36
                                && t.x() >= centreX - 32 && t.x() < centreX + 40).toList();
                        int index = (family == 1 ? 3 : 0) + displayedFrames[row - 2];
                        var expectedOffsets = offsets.get(index);
                        assertEquals(expectedOffsets.stream().map(offset -> banks.spring() + offset).toList(),
                                tiles.stream().map(com.openggf.graphics.SpritePresentation.Tile::patternId).toList());
                        int tileIndex = 0, changed = 0;
                        // DrawObjects/DrawSprite source order, then reverse-piece painter order; these are literal mappings above.
                        for (int piece = pieces[index].length - 1; piece >= 0; piece--) {
                            var p = pieces[index][piece]; int width = (p[1] >>> 2) + 1, height = (p[1] & 3) + 1;
                            int relativeY = family == 2 ? -p[0] - height * 8 : p[0];
                            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                                var tile = tiles.get(tileIndex++);
                                assertEquals((float) (centreX + p[3] + x * 8), tile.x());
                                assertEquals((float) (centreY + relativeY + (family == 2 ? height - 1 - y : y) * 8), tile.y());
                                assertEquals(8f, tile.width()); assertEquals(8f, tile.height());
                                assertEquals(family == 1 ? 1 : 0, tile.palette());
                                assertFalse(tile.hFlip()); assertEquals(family == 2, tile.vFlip()); assertFalse(tile.priority());
                                int offset = tile.patternId() - banks.spring(); reached.add(offset);
                                var version = picture.patternVersions().get(tile.patternId()); assertNotNull(version);
                                var decoded = new com.openggf.level.Pattern(); decoded.fromSegaFormat(springArt.tile(0x520 + offset).copy());
                                long[] words = {version.a(), version.b(), version.c(), version.d()};
                                for (int pixel = 0; pixel < 64; pixel++) assertEquals(decoded.getPixel(pixel % 8, pixel / 8) & 15,
                                        (int) ((words[pixel / 16] >>> ((pixel % 16) * 4)) & 15), "actual Spring pixel " + offset + ":" + pixel);
                                for (var other : picture.tiles()) {
                                    if (other.patternId() >= banks.spring() && other.patternId() < banks.spring() + 36) continue;
                                    assertTrue(other.x() + other.width() <= tile.x() || other.x() >= tile.x() + tile.width()
                                                    || other.y() + other.height() <= tile.y() || other.y() >= tile.y() + tile.height(),
                                            "Sonic/other glyphs must not contaminate the isolated Spring GPU footprint");
                                }
                                for (int py = (int) tile.y(); py < tile.y() + 8; py++) for (int px = (int) tile.x(); px < tile.x() + 8; px++) {
                                    assertTrue(px >= 0 && px < 320 && py >= 0 && py < 224);
                                    if (image.pixels()[py * 320 + px] != terrain.pixels()[py * 320 + px]) changed++;
                                }
                            }
                        }
                        assertTrue(changed > 0, "Actual EGL must draw this Spring family/frame independently of Sonic");
                        assertEquals(cpuFrames[row - 2], pool.state(40 + family).unsignedByte(MAP_FRAME));
                        assertEquals(row - 1, pool.state(40 + family).unsignedByte(ANIMATION_FRAME), "One real animator call per physical dispatch");
                        assertEquals(animationRoutines[family], pool.state(40 + family).routine());
                        assertEquals(0x80, pool.state(40 + family).spriteFlags() & 0x80);
                    }
                    assertCapture(completed, module); // Rendering/control must leave complete World state, Plane bytes/copies and sounds intact.
                    int phase = displayedFrames[row - 2];
                    if (phasePictures[phase] == null) {
                        phasePictures[phase] = image;
                        if (output != null) {
                            ScreenshotCapture.savePNG(image, output.resolve("constructed-up-side-down-frame-" + phase + ".png"));
                            ScreenshotCapture.savePNG(terrain, output.resolve("terrain-control-frame-" + phase + ".png"));
                        }
                    }
                    previousCpu = runtime.cpuSat(); previousCameraX = GameServices.camera().getX(); previousCameraY = GameServices.camera().getY();
                }
                assertEquals(java.util.stream.IntStream.range(0, 36).boxed().collect(java.util.stream.Collectors.toSet()), reached);
                assertFalse(Arrays.equals(phasePictures[0].pixels(), phasePictures[1].pixels()));
                assertFalse(Arrays.equals(phasePictures[1].pixels(), phasePictures[2].pixels()), "The constructed animation must change actual rendered output");
                var after = capture(module); var afterPixels = render(module, true);
                for (boolean courseOnly : List.of(false, true)) {
                    if (courseOnly) registry.restoreCourse(course); else registry.restore(full);
                    assertCapture(before, module); assertArrayEquals(beforePixels.pixels(), render(module, true).pixels());
                    assertCapture(before, module);
                    for (int row = 2; row < 8; row++) {
                        step(loop, movie.getFrame(row)); assertCapture(expected.get(row - 2), module);
                        assertArrayEquals(expectedPixels.get(row - 2), render(module, true).pixels(), "Spring rendered replay row " + row);
                        assertCapture(expected.get(row - 2), module);
                    }
                    assertCapture(after, module); assertArrayEquals(afterPixels.pixels(), render(module, true).pixels());
                }
                int materialized = 0;
                for (int offset = 0; offset < 400; offset++) if (atlas.getEntry(base + offset) != null) materialized++;
                for (int offset = 0; offset < 36; offset++) {
                    var entry = atlas.getEntry(banks.spring() + offset); assertNotNull(entry, "All36 source tiles reached actual presentation/GPU");
                    assertTrue(glIsTexture(atlas.getTextureId(entry.atlasIndex())), "Real EGL atlas page, never a headless texture fake");
                }
                assertNotNull(atlas.getEntry(banks.spring())); assertNotNull(atlas.getEntry(banks.spring() + 35));
                module.closeTerrainPlayer();
                assertTrue(runtime.closed()); assertTrue(frame.closed());
                assertSame(atlas, GameServices.graphics().getPatternAtlas(), "Check logical retirement before graphics/context destruction");
                assertNull(atlas.getEntry(banks.spring())); assertNull(atlas.getEntry(banks.spring() + 35));
                for (int offset = 0; offset < 400; offset++) assertNull(atlas.getEntry(base + offset), "retired picture ID " + (base + offset));
                if (output != null) Files.writeString(output.resolve("outcome.txt"),
                        "PASS: constructed normal up/side/down Spring World; real ID$0A Init and source animator, six rendered forward rows, all36 normal tiles, full/course byte-and-pixel replay,400-ID reservation/removal.\n"
                        + "All dynamic object slots were explicitly cleared after real player Init; slots40/41/42 were seeded and routines4/10/16 explicitly selected after Spring Init. Sonic remained live; isolated Spring footprints excluded him.\n"
                        + "This is constructed component evidence, NOT a natural controller route/contact/launch proof.\n"
                        + "All400 actual preload policy IDs were checked; " + materialized + " entries were resident before close, including first/last and all36 Spring logical textures; all400 entries were absent after close. No fabricated atlas entries were supplied.\n"
                        + "USA DEV preload and bounded overlay priority only; no native pixel/timing parity, diagonal art, live PLC/section residency, act or full-game claim.\n",
                        StandardOpenOption.CREATE_NEW);
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
                        assertEquals(beforeCut.camera(), cut.camera()); assertScroll(beforeCut.scroll(), cut.scroll());
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


    @Test void actualEglNativePlaneBSelectsThePublishedImageAndReplaysFullAndCoursePixels() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual EGL/media opt-in");
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        String media = System.getProperty("soniccd.media.root"); assertNotNull(media); assertFalse(media.isBlank());
        CdGameDataSource source;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); source = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
        String inputLog = InputScriptCompiler.compileToInputLog("2 -\n60 R\n24 R+B\n7 R");
        var movie = new Bk2MovieLoader().parseInputLogText(Path.of("native-plane-b-input-log"), inputLog.lines().toList());
        assertEquals(93, movie.getFrameCount());
        Path output = openCaptureOutput(inputLog, "soniccd.nativeplane.output");
        try (var boot = new HeadlessGameBoot(320, 224)) {
            try {
                var loop = boot.bootSonicCdOpeningObjects(source);
                var mode = SessionManager.getCurrentGameplayMode();
                var module = assertInstanceOf(SonicCdGameModule.class, mode.getWorldSession().getGameModule());
                var presentation = (SonicCdPlayerPresentation) module.getScrollHandlerProvider();
                var registry = mode.getRewindRegistry(); var coldCut = registry.capture(); var coldState = capture(module);
                var coldPixels = render(module, true); assertNativeGpuBackground(module);
                assertNull(presentation.captureScrollPublication().prepared()); assertNull(presentation.captureScrollPublication().published());
                assertCapture(coldState, module);
                assertArrayEquals(coldPixels.pixels(), render(module, true).pixels());
                step(loop, movie.getFrame(0)); step(loop, movie.getFrame(1));
                var full = registry.capture(); var course = registry.captureCourse(); var checkpoint = capture(module);
                var checkpointPixels = render(module, true);
                var states = new ArrayList<Capture>(); var images = new ArrayList<int[]>();
                boolean distinguishesNextCpuPlane = false, distinguishesNextCpuScroll = false;
                for (int row = 2; row < movie.getFrameCount(); row++) {
                    var previous = presentation.captureScrollPublication().prepared();
                    step(loop, movie.getFrame(row)); states.add(capture(module));
                    var selected = presentation.captureScrollPublication();
                    assertSame(previous, selected.published(), "same V-int selection as the displayed SAT");
                    distinguishesNextCpuScroll |= !selected.published().equals(selected.prepared());
                    distinguishesNextCpuPlane |= !Arrays.equals(selected.plane().nameTable().copy(),
                            nextCpuPlaneForComparison(module).nameTable().copy());
                    images.add(render(module, true).pixels().clone());
                    assertNativeGpuBackground(module); assertCapture(states.getLast(), module);
                }
                assertTrue(distinguishesNextCpuPlane, "route must distinguish selected N from the next CPU candidate N+1");
                assertTrue(distinguishesNextCpuScroll, "route must distinguish selected and prepared scroll");
                var finalState = capture(module); var finalPixels = render(module, true);
                // Keep FG, scroll, art and camera fixed; only the control's descriptor alpha hides Plane B.
                // Replay alone cannot detect an omitted background draw. This real GPU counterfactual must.
                var withNativeBackground = render(module, false);
                var withoutBackground = renderWithoutBackground(module);
                assertFalse(Arrays.equals(withNativeBackground.pixels(), withoutBackground.pixels()),
                        "the selected native Plane B must contribute visible framebuffer pixels");
                assertCapture(finalState, module);
                assertArrayEquals(finalPixels.pixels(), render(module, true).pixels(),
                        "ordinary draw reinstalls the selected native image after the negative control");
                for (boolean courseOnly : List.of(false, true)) {
                    if (courseOnly) registry.restoreCourse(course); else registry.restore(full);
                    assertCapture(checkpoint, module);
                    assertArrayEquals(checkpointPixels.pixels(), render(module, true).pixels());
                    for (int row = 2; row < movie.getFrameCount(); row++) {
                        step(loop, movie.getFrame(row)); assertCapture(states.get(row - 2), module);
                        assertArrayEquals(images.get(row - 2), render(module, true).pixels(), "native-plane replay row " + row);
                    }
                    assertCapture(finalState, module);
                    assertArrayEquals(finalPixels.pixels(), render(module, true).pixels());
                }
                registry.restore(coldCut);
                assertCapture(coldState, module);
                assertArrayEquals(coldPixels.pixels(), render(module, true).pixels());
                assertNull(presentation.captureScrollPublication().prepared()); assertNull(presentation.captureScrollPublication().published());
                if (output != null) {
                    ScreenshotCapture.savePNG(coldPixels, output.resolve("cold-native-plane-b.png"));
                    ScreenshotCapture.savePNG(checkpointPixels, output.resolve("checkpoint-native-plane-b.png"));
                    ScreenshotCapture.savePNG(finalPixels, output.resolve("route-native-plane-b.png"));
                    Files.writeString(output.resolve("scope.txt"),
                            "Actual engine captures only when this opted-in method completes. Bounded preloaded800 base-level art; "
                            + "not native PLC/section residency or mixed BG/FG/sprite priority or native pixel parity.\n",
                            StandardOpenOption.CREATE_NEW);
                }
            } finally {
                try { SessionManager.closeGameplaySession(); }
                finally { GraphicsManager.destroyForReinit(); }
            }
        }
    }

    @Test void actualEglMissingNativeLevelAtlasEntryRejectsBeforeTerrainAndClosesBothSourceSiblings() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual EGL/media opt-in");
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        String media = System.getProperty("soniccd.media.root"); assertNotNull(media); assertFalse(media.isBlank());
        CdGameDataSource source;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); source = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
        try (var boot = new HeadlessGameBoot(320, 224)) {
            try {
                boot.bootSonicCdOpeningObjects(source);
                var module = assertInstanceOf(SonicCdGameModule.class, SessionManager.getCurrentWorldSession().getGameModule());
                render(module, true); assertNativeGpuBackground(module);
                var presentation = (SonicCdPlayerPresentation) module.getScrollHandlerProvider();
                int id = presentation.publishedPlane().nameTable().unsignedWord(0) & 0x7FF;
                var graphics = GameServices.graphics(); var atlas = graphics.getPatternAtlas();
                assertTrue(atlas.isInitialized()); assertNotNull(atlas.getEntry(id));
                assertTrue(graphics.uncachePattern(id)); assertNull(atlas.getEntry(id));
                var frame = module.requirePlayerFrame(); var body = frame.state(); var clock = frame.vIntClock();
                var runtime = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                var level = GameServices.level(); var bytes = NativeBackgroundTestAccess.descriptors(level);
                var geometry = NativeBackgroundTestAccess.geometry(level); var flags = NativeBackgroundTestAccess.flags(level);
                var failure = assertThrows(IllegalStateException.class, module.gameplayFrameController()::drawOverlay);
                assertTrue(failure.getMessage().contains("resident base-level pattern"), failure.getMessage());
                assertTrue(frame.closed()); assertTrue(runtime.closed());
                assertEquals(body, frame.state()); assertEquals(clock, frame.vIntClock());
                assertArrayEquals(bytes, NativeBackgroundTestAccess.descriptors(level));
                assertArrayEquals(geometry, NativeBackgroundTestAccess.geometry(level));
                assertArrayEquals(flags, NativeBackgroundTestAccess.flags(level));
                assertThrows(IllegalStateException.class, module.gameplayFrameController()::drawOverlay);
            } finally {
                try { SessionManager.closeGameplaySession(); }
                finally { GraphicsManager.destroyForReinit(); }
            }
        }
    }

    private static byte[] oracleNativeDescriptors(CdR11aBackgroundPlane.State plane) {
        byte[] out = new byte[8192];
        for (int cell = 0; cell < 2048; cell++) {
            int word = plane.nameTable().unsignedWord(cell * 2), pattern = word % 2048;
            out[cell * 4] = (byte) (pattern % 256);
            out[cell * 4 + 1] = (byte) (pattern / 256 + ((word / 8192) % 4) * 8
                    + ((word / 2048) % 2) * 32 + ((word / 4096) % 2) * 64 + (word / 32768) * 128);
            out[cell * 4 + 3] = (byte) 255;
        }
        return out;
    }

    private static void assertNativeGpuBackground(SonicCdGameModule module) {
        var level = GameServices.level(); var graphics = GameServices.graphics(); var tm = level.getTilemapManager();
        var presentation = (SonicCdPlayerPresentation) module.getScrollHandlerProvider();
        assertArrayEquals(oracleNativeDescriptors(presentation.publishedPlane()), NativeBackgroundTestAccess.descriptors(level));
        assertArrayEquals(new int[] {64, 32, 0, 0, 512, 32, 0, -1}, NativeBackgroundTestAccess.geometry(level));
        assertArrayEquals(new boolean[] {false, true, false}, NativeBackgroundTestAccess.flags(level));
        assertTrue(graphics.isGlInitialized()); assertFalse(graphics.isHeadlessMode());
        assertTrue(graphics.getPatternAtlas().isInitialized());
        var gpu = graphics.getTilemapGpuRenderer(); assertNotNull(gpu);
        assertEquals(0, gpu.getBackgroundRingBaseXTiles()); assertEquals(0, gpu.getBackgroundRingBaseYTiles());
        assertTrue(gpu.hasBackgroundBaseline(tm.getBackgroundTilemapData(), 64, 32));
    }

    private static CdR11aBackgroundPlane.State nextCpuPlaneForComparison(SonicCdGameModule module) {
        var cut = assertInstanceOf(SonicCdPlayerPresentation.ScrollCapture.class,
                module.getScrollHandlerProvider().getHandler(0).captureRewindState());
        var b = cut.background();
        var one = new CdR11aBackgroundPlane.Camera(b.cameraBgX(), b.cameraBgY());
        var two = new CdR11aBackgroundPlane.Camera(b.cameraBg2X(), b.cameraBg2Y());
        var three = new CdR11aBackgroundPlane.Camera(b.cameraBg3X(), b.cameraBg3Y());
        return CdR11aBackgroundPlane.bind(module.requirePlayerFrame().assets()).incremental(cut.plane(),
                new CdR11aBackgroundPlane.LiveCameras(one, two, three),
                new CdR11aBackgroundPlane.CopiedCameras(one, two, three),
                new CdR11aBackgroundPlane.FlagWords(b.scrollFlagsBg(), b.scrollFlagsBg2(), b.scrollFlagsBg3())).state();
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
        if (objects) {
            module.gameplayFrameController().drawOverlay(); assertNativeGpuBackground(module);
        } else {
            var selected = ((SonicCdPlayerPresentation) module.getScrollHandlerProvider()).captureScrollPublication();
            LevelSpritePresentation.withScrollAndBackground(level, selected.published(), oracleNativeDescriptors(selected.plane()),
                    () -> level.drawWithRenderOptions(GameServices.sprites(), LevelManager.LevelRenderOptions.tilesOnly()));
        }
        graphics.flush(); graphics.flushScreenSpace(); glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }

    private static RgbaImage renderWithoutBackground(SonicCdGameModule module) {
        var graphics = GameServices.graphics(); var level = GameServices.level();
        graphics.runPendingRenderThreadTasks(); level.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); graphics.resetForFixedFunction();
        var selected = ((SonicCdPlayerPresentation) module.getScrollHandlerProvider()).captureScrollPublication();
        // A test-only transparent descriptor image: no source-plane, gameplay, art or palette mutation.
        LevelSpritePresentation.withScrollAndBackground(level, selected.published(), new byte[64 * 32 * 4], () ->
                level.drawWithRenderOptions(GameServices.sprites(), LevelManager.LevelRenderOptions.tilesOnly()));
        graphics.flush(); graphics.flushScreenSpace(); glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }

    private static RgbaImage renderTerrain(SonicCdGameModule module, LevelSpritePresentation.ScrollFrame scroll) {
        var graphics = GameServices.graphics(); var level = GameServices.level();
        graphics.runPendingRenderThreadTasks(); level.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); graphics.resetForFixedFunction();
        var selected = ((SonicCdPlayerPresentation) module.getScrollHandlerProvider()).publishedPlane();
        LevelSpritePresentation.withScrollAndBackground(level, scroll, oracleNativeDescriptors(selected), () ->
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
        assertReservedImages(expected.objects().reserved(), actual.objects().reserved());
        assertEquals(expected.objects().placement().status(), actual.objects().placement().status());
        assertArrayEquals(expected.objects().placement().savedFlags().copy(), actual.objects().placement().savedFlags().copy());
        assertEquals(expected.objects().ringGlobals(), actual.objects().ringGlobals());
        assertEquals(expected.objects().spillGlobals(), actual.objects().spillGlobals());
        assertEquals(expected.objects().devGlobals(), actual.objects().devGlobals());
        assertEquals(expected.objects().contactGlobals(), actual.objects().contactGlobals());
        assertEquals(expected.objects().scoreGlobals(), actual.objects().scoreGlobals());
        assertEquals(expected.objects().levelGlobals(), actual.objects().levelGlobals());
        assertEquals(expected.objects().powerupArtState(), actual.objects().powerupArtState());
        assertEquals(expected.objects().sectionArtState(), actual.objects().sectionArtState());
        assertEquals(expected.objects().lastSoundRequests(), actual.objects().lastSoundRequests());
        assertArrayEquals(expected.cpu().bytes(), actual.cpu().bytes());
        assertEquals(expected.cpu().provenance(), actual.cpu().provenance());
        assertEquals(expected.objects().displayedFrame(), actual.objects().displayedFrame());
        assertEquals(expected.camera(), actual.camera()); assertScroll(expected.scroll(), actual.scroll());
        assertEquals(expected.levelFrames(), actual.levelFrames());
        assertPublication(expected.publication(), actual.publication());
        var a = expected.bank().tiles(); var b = actual.bank().tiles();
        assertEquals(24, b.length);
        for (int i = 0; i < 24; i++) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
            assertEquals(a[i].getPixel(x, y), b[i].getPixel(x, y));
    }
    private static void assertReservedImages(
            com.openggf.game.soniccd.objects.CdReservedObjectBank.Snapshot expected,
            com.openggf.game.soniccd.objects.CdReservedObjectBank.Snapshot actual) {
        assertEquals(31, expected.slots().size()); assertEquals(31, actual.slots().size());
        for (int i = 0; i < 31; i++) {
            assertEquals(i + 1, expected.slots().get(i).identity().wholeIndex());
            assertEquals(expected.slots().get(i).identity(), actual.slots().get(i).identity());
            assertArrayEquals(expected.slots().get(i).copyBytes(), actual.slots().get(i).copyBytes(),
                    "complete reserved whole " + (i + 1));
        }
    }

    private static void assertPlane(com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.State expected,
                                    com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.State actual) {
        assertEquals(4096, expected.nameTable().length()); assertEquals(4096, actual.nameTable().length());
        assertArrayEquals(expected.nameTable().copy(), actual.nameTable().copy());
    }
    private static void assertCopies(SonicCdPlayerPresentation.DrawCopies expected,
                                     SonicCdPlayerPresentation.DrawCopies actual) {
        assertEquals(expected.fgX16_16(), actual.fgX16_16()); assertEquals(expected.fgY16_16(), actual.fgY16_16());
        assertEquals(expected.bg().bg1(), actual.bg().bg1()); assertEquals(expected.bg().bg2(), actual.bg().bg2());
        assertEquals(expected.bg().bg3(), actual.bg().bg3()); assertEquals(expected.fgFlags(), actual.fgFlags());
        assertEquals(expected.bgFlags().bg1(), actual.bgFlags().bg1());
        assertEquals(expected.bgFlags().bg2(), actual.bgFlags().bg2()); assertEquals(expected.bgFlags().bg3(), actual.bgFlags().bg3());
    }
    private static void assertPublication(SonicCdPlayerPresentation.ScrollPublication expected,
                                          SonicCdPlayerPresentation.ScrollPublication actual) {
        assertSame(expected.owner(), actual.owner());
        assertEquals(expected.prepared(), actual.prepared()); assertEquals(expected.published(), actual.published());
        assertPlane(expected.plane(), actual.plane());
    }
    private static void assertScroll(Object expected, Object actual) {
        var a = assertInstanceOf(SonicCdPlayerPresentation.ScrollCapture.class, expected);
        var b = assertInstanceOf(SonicCdPlayerPresentation.ScrollCapture.class, actual);
        assertSame(a.owner(), b.owner());
        assertEquals(a.state(), b.state());
        assertPlane(a.plane(), b.plane()); assertCopies(a.copies(), b.copies());
        var x = a.background(); var y = b.background();
        assertEquals(x.cameraBgX(), y.cameraBgX()); assertEquals(x.cameraBgY(), y.cameraBgY());
        assertEquals(x.cameraBg2X(), y.cameraBg2X()); assertEquals(x.cameraBg2Y(), y.cameraBg2Y());
        assertEquals(x.cameraBg3X(), y.cameraBg3X()); assertEquals(x.cameraBg3Y(), y.cameraBg3Y());
        assertEquals(x.scrollFlagsBg(), y.scrollFlagsBg()); assertEquals(x.scrollFlagsBg2(), y.scrollFlagsBg2());
        assertEquals(x.scrollFlagsBg3(), y.scrollFlagsBg3()); assertEquals(x.vscroll(), y.vscroll());
        assertArrayEquals(x.crossingRam().copy(), y.crossingRam().copy());
        assertArrayEquals(x.cloudRam().copy(), y.cloudRam().copy());
        assertArrayEquals(x.deformationRam().copy(), y.deformationRam().copy());
        assertArrayEquals(x.hscrollRam().copy(), y.hscrollRam().copy());
    }


    @Test void actualEglConstructedVerticalTunnelDoorWorldClosesOpensReplaysAndRetires412Patterns() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual EGL/media opt-in");
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        String media = System.getProperty("soniccd.media.root"); assertNotNull(media); assertFalse(media.isBlank());
        CdGameDataSource source;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); source = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
        // Main.asm:34-83 at native pin1ecc5f3af: Init falls into Main with incoming bit7 clear,
        // then32 ADDQ.B #8 Reset calls carry. Only the FOLLOWING Main opens, and the NEXT V-int displays it.
        String log = InputScriptCompiler.compileToInputLog("36 -");
        var movie = new Bk2MovieLoader().parseInputLogText(Path.of("constructed-vertical-door-world-input"), log.lines().toList());
        Path output = openCaptureOutput(log, "soniccd.tunneldoor.output");
        String construction = "ENGINE constructed USA R11A vertical Tunnel Door component; not a natural route/contact.\n"
                + "Input row0: actual Sonic Init. Then clear ALL dynamic slots; seed slot40 ID$35, X=cameraX+192, Y=cameraY+72, subtype2=0, spriteFlags=0.\n"
                + "Before row1 and EACH row2..35: restore/rebind the same live Sonic actor at DoorX-24, DoorY; zero velocities/angle/jump/charge/moveLock/stick/control, routine2, flags2, spriteFlags4, radii9/19, animation0; retain previousAnimation, angle copies, animation cursor, header, timers, history, rotation and object links; clear display publication.\n"
                + "Actual player Body, native Door body/Solid/DrawObject, DrawObjects/SAT and completed ordinary tail execute on every step. No Door routine/map-frame/timer/mapping/tile write after construction.\n"
                + "Silent Init row1; Reset rows2..33; bit7-triggered Main/FM_A4 row34; next-V-int open display row35. Full/course replay repeats only row2..35 actor ingress and inputs.\n"
                + "USA DEV object-art preload, current native Plane B scope and bounded overlay priority; no native palette/VDP-priority/pixel/timing parity, PLC service/residency, natural contact, route or full-game claim.\n";
        if (output != null) Files.writeString(output.resolve("construction.txt"), construction, StandardOpenOption.CREATE_NEW);
        try (var boot = new HeadlessGameBoot(320, 224)) {
            try {
                var loop = boot.bootSonicCdOpeningObjects(source);
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
                var mode = SessionManager.getCurrentGameplayMode();
                var module = assertInstanceOf(SonicCdGameModule.class, mode.getWorldSession().getGameModule());
                var runtime = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                var frame = module.requirePlayerFrame();
                step(loop, movie.getFrame(0));
                var pool = runtime.placement().pool();
                pool.clearDynamicSlots(); // EXPLICIT ingress: clear all96 dynamic slots, preserve reserved live Sonic.
                int doorX = GameServices.camera().getX() + 192, doorY = GameServices.camera().getY() + 72;
                pool.write(pool.state(40).moveByte(ID, 0x35).moveWord(X, doorX).moveWord(Y, doorY)
                        .moveByte(SUBTYPE2, 0).moveByte(SPRITE_FLAGS, 0));
                stageTunnelDoorPlayer(module, doorX, doorY);
                step(loop, movie.getFrame(1)); // Actual dispatcher Init -> Main -> Solid -> Draw -> complete SAT/tail.
                assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase());
                assertEquals(4, pool.state(40).routine()); assertEquals(0, pool.state(40).unsignedByte(MAP_FRAME));
                assertEquals(0, pool.state(40).word(TUNNEL_DOOR_TIMER));
                assertEquals(0x20CE9A, pool.state(40).longBits(MAPPINGS)); assertEquals(0x8328, pool.state(40).word(TILE));
                assertEquals(1, pool.state(40).unsignedByte(PRIORITY)); assertEquals(4, pool.state(40).unsignedByte(WIDTH));
                assertEquals(24, pool.state(40).unsignedByte(Y_RADIUS)); assertEquals(0x84, pool.state(40).spriteFlags());
                assertTrue(runtime.lastSoundRequests().isEmpty(), "Init sees incoming bit7 clear, so Main must be silent/frame0");
                assertTunnelDoorSat(runtime.cpuSat(), GameServices.camera().getX(), GameServices.camera().getY(), doorX, doorY, 0);
                int base = com.openggf.graphics.PatternAtlasRange.OBJECTS.base();
                var banks = new CdOpeningObjectRuntime.LogicalBanks(base, base + 24, base + 38, base + 108, base + 136,
                        base + 199, base + 269, base + 274, base + 313, base + 349, base + 364, base + 400);
                assertEquals(banks.spring() + 36, banks.tunnelDoorVertical());
                var objects = source.assets().openingObjects(); var doorArt = objects.tunnelDoorArtBank();
                var binding = doorArt.binding(com.openggf.game.soniccd.level.CdR11aAsset.ART_TUNNEL_DOOR);
                assertEquals(0x328, binding.firstTileId()); assertEquals(12, binding.tileCount());
                assertEquals(0x8328, binding.baseWord()); assertEquals(0x6500, binding.plc().vramAddress());
                var mapping = objects.view(com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.TUNNEL_DOOR_VERTICAL_MAPPINGS);
                assertEquals(0x20CE9A, mapping.range().nativeAddress());
                assertArrayEquals(java.util.HexFormat.of().parseHex("0004001002E8011804FCF8031800FC0002E80C0006FCE804000A1C00"),
                        mapping.bytes().copy(), "Real declared28-byte map: roots4/16, two pieces per frame, real zero pads15/27");
                var policy = com.openggf.game.soniccd.resources.CdObjectPresentation.usaR11aTunnelDoorVerticalDevPreloadedArt(
                        frame.artState(), runtime.powerupArtState(), objects, objects.frontierArtBank(), objects.contactArtBank(),
                        objects.floatBlockArtBank(), objects.springArtBank(), doorArt, banks.player(), banks.ring(), banks.mosqui(),
                        banks.plant(), banks.ramp(), banks.explosion(), banks.points(), banks.flower(), banks.powerup(),
                        banks.floatBlock(), banks.spring(), banks.tunnelDoorVertical());
                int[] nativeFirst = {0x780, 0x7AE, 0x3A0, 0x424, 0x441, 0x680, 0x6C6, 0x6D7, 0x544, 0x334, 0x520, 0x328};
                int[] counts = {24, 14, 70, 28, 63, 70, 5, 39, 36, 15, 36, 12};
                var atlas = GameServices.graphics().getPatternAtlas(); int logical = base;
                for (int span = 0; span < counts.length; span++) for (int tile = 0; tile < counts[span]; tile++) {
                    int id = logical++;
                    assertEquals(id, policy.resolve(nativeFirst[span] + tile).logicalPatternId());
                    assertEquals(1, atlas.registeredRangesForTesting().stream()
                            .filter(range -> id >= range.base() && id < range.base() + range.size()).count(), "reservation " + id);
                }
                assertEquals(base + 412, logical, "The exact12-span current-art policy covers412 logical IDs");
                var registry = mode.getRewindRegistry(); var full = registry.capture(); var course = registry.captureCourse();
                var before = capture(module); var beforePixels = render(module, true); assertCapture(before, module);
                var expected = new ArrayList<Capture>(); var expectedPixels = new ArrayList<int[]>();
                var reached = new java.util.TreeSet<Integer>(); var phasePictures = new RgbaImage[2];
                var phaseControls = new RgbaImage[2];
                var previousCpu = runtime.cpuSat();
                int previousCameraX = GameServices.camera().getX(), previousCameraY = GameServices.camera().getY();
                for (int row = 2; row < 36; row++) {
                    stageTunnelDoorPlayer(module, doorX, doorY); // Explicit actor-only construction at a CLOSED_CUT.
                    step(loop, movie.getFrame(row)); // NEVER publish/draw/restore at the intermediate SERVICED boundary.
                    assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase());
                    int cpuFrame = row >= 34 ? 1 : 0, displayFrame = row == 35 ? 1 : 0;
                    assertEquals(row == 33 ? 2 : 4, pool.state(40).routine());
                    assertEquals(cpuFrame, pool.state(40).unsignedByte(MAP_FRAME));
                    int timerHigh = row <= 33 ? ((row - 1) * 8) & 255 : row == 34 ? 0 : 8;
                    assertEquals(timerHigh << 8, pool.state(40).word(TUNNEL_DOOR_TIMER));
                    assertEquals(0x84, pool.state(40).spriteFlags()); assertEquals(0, pool.state(40).flags());
                    assertEquals(2, frame.state().motion().routine(), "Sonic stays live through the actual player Body");
                    assertEquals(24, doorX - (frame.state().motion().centreX16_16() >>> 16), "Inside Main's48, outside Solid's14");
                    assertTrue(Math.abs(doorY - (frame.state().motion().centreY16_16() >>> 16)) < 32);
                    assertEquals(row == 34 ? List.of(new CdOpeningObjectRuntime.TunnelDoorVerticalFm(
                                    com.openggf.game.soniccd.objects.CdTunnelDoorVertical.FmSound.FM_A4)) : List.of(),
                            runtime.lastSoundRequests(), "Only the Main AFTER the32nd Reset may request A4");
                    var completed = capture(module); expected.add(completed);
                    var published = completed.publication().published();
                    assertEquals(previousCameraX, published.cameraX()); assertEquals(previousCameraY, published.cameraY());
                    assertTunnelDoorSat(previousCpu, previousCameraX, previousCameraY, doorX, doorY, displayFrame);
                    assertTunnelDoorSat(runtime.cpuSat(), GameServices.camera().getX(), GameServices.camera().getY(), doorX, doorY, cpuFrame);
                    var nowPixels = com.openggf.game.soniccd.resources.CdObjectPresentation.usaR11aTunnelDoorVerticalDevPreloadedArt(
                            frame.artState(), runtime.powerupArtState(), objects, objects.frontierArtBank(), objects.contactArtBank(),
                            objects.floatBlockArtBank(), objects.springArtBank(), doorArt, banks.player(), banks.ring(), banks.mosqui(),
                            banks.plant(), banks.ramp(), banks.explosion(), banks.points(), banks.flower(), banks.powerup(),
                            banks.floatBlock(), banks.spring(), banks.tunnelDoorVertical());
                    assertEquals(com.openggf.game.soniccd.resources.CdObjectPresentation.convert(previousCpu, nowPixels), runtime.displayedFrame(),
                            "Display is the PREVIOUS actual SAT using NOW-serviced player/powerup windows and real Door pixels");
                    var picture = runtime.displayedFrame(); var image = render(module, true);
                    expectedPixels.add(image.pixels().clone()); var terrain = renderTerrain(module, published);
                    assertNativeGpuBackground(module); assertTerrainOutsideSprites(image, terrain, picture);
                    assertTunnelDoorPixels(picture, image, terrain, doorArt, banks.tunnelDoorVertical(),
                            doorX - previousCameraX, doorY - previousCameraY, displayFrame, reached);
                    if (row == 2 || row == 35) {
                        // Regression for review P2: upload/capture is insufficient if the GPU omits one tile.
                        // Remove a complete visible tile but retain a visible contribution from the other five.
                        var doorTiles = picture.tiles().stream().filter(t -> t.patternId() >= banks.tunnelDoorVertical()
                                && t.patternId() < banks.tunnelDoorVertical() + 12).toList();
                        var omitted = doorTiles.getFirst(); var partial = image.copy();
                        int removedRgb = 0, retainedRgb = 0;
                        for (var tile : doorTiles) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                            int at = ((int) tile.y() + y) * 320 + (int) tile.x() + x;
                            boolean visible = (image.pixels()[at] & 0xFFFFFF) != (terrain.pixels()[at] & 0xFFFFFF);
                            if (tile == omitted) {
                                if (visible) removedRgb++;
                                partial.pixels()[at] = terrain.pixels()[at];
                            } else if (visible) retainedRgb++;
                        }
                        assertTrue(removedRgb > 0); assertTrue(retainedRgb > 0);
                        assertThrows(AssertionError.class, () -> assertTunnelDoorPixels(picture, partial, terrain, doorArt,
                                banks.tunnelDoorVertical(), doorX - published.cameraX(), doorY - published.cameraY(),
                                displayFrame, new java.util.TreeSet<>()), "A partially missing Door must fail the GPU oracle");
                    }
                    assertArrayEquals(image.pixels(), render(module, true).pixels(), "Repeated draw must reconstruct the same closed picture");
                    assertCapture(completed, module); // Draw/control must preserve complete Plane/copies/publication, state and sound ledger.
                    if (phasePictures[displayFrame] == null) { phasePictures[displayFrame] = image; phaseControls[displayFrame] = terrain; }
                    previousCpu = runtime.cpuSat(); previousCameraX = GameServices.camera().getX(); previousCameraY = GameServices.camera().getY();
                }
                assertEquals(java.util.stream.IntStream.range(0, 12).boxed().collect(java.util.stream.Collectors.toSet()), reached);
                assertFalse(Arrays.equals(phasePictures[0].pixels(), phasePictures[1].pixels()));
                var after = capture(module); var afterPixels = render(module, true); assertCapture(after, module);
                for (boolean courseOnly : List.of(false, true)) {
                    if (courseOnly) registry.restoreCourse(course); else registry.restore(full);
                    assertCapture(before, module); assertArrayEquals(beforePixels.pixels(), render(module, true).pixels());
                    assertCapture(before, module);
                    for (int row = 2; row < 36; row++) {
                        stageTunnelDoorPlayer(module, doorX, doorY); step(loop, movie.getFrame(row));
                        assertCapture(expected.get(row - 2), module);
                        assertArrayEquals(expectedPixels.get(row - 2), render(module, true).pixels(), "Door full/course rendered replay row " + row);
                        assertCapture(expected.get(row - 2), module);
                    }
                    assertCapture(after, module); assertArrayEquals(afterPixels.pixels(), render(module, true).pixels());
                    assertCapture(after, module);
                }
                for (int offset = 0; offset < 12; offset++) {
                    var entry = atlas.getEntry(banks.tunnelDoorVertical() + offset); assertNotNull(entry, "Every actual Door tile reached the GPU atlas");
                    assertTrue(glIsTexture(atlas.getTextureId(entry.atlasIndex())), "Real EGL texture, never an injected/headless glyph");
                }
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
                if (output != null) for (int pose = 0; pose < 2; pose++) {
                    String label = pose == 0 ? "closed-frame0" : "open-frame1";
                    ScreenshotCapture.savePNG(phasePictures[pose], output.resolve("ENGINE-constructed-door-" + label + ".png"));
                    ScreenshotCapture.savePNG(phaseControls[pose], output.resolve("ENGINE-terrain-control-" + label + ".png"));
                }
                module.closeTerrainPlayer(); // Retire private/World pictures while the same graphics/context still exist.
                assertTrue(runtime.closed()); assertTrue(frame.closed());
                assertSame(atlas, GameServices.graphics().getPatternAtlas());
                for (int offset = 0; offset < 412; offset++) assertNull(atlas.getEntry(base + offset),
                        "retired picture ID " + (base + offset) + "; includes ALL12 new suffix IDs+400..411");
            } finally {
                try { SessionManager.closeGameplaySession(); }
                finally { GraphicsManager.destroyForReinit(); }
            }
        }
        if (output != null) Files.writeString(output.resolve("outcome.txt"),
                "PASS: constructed native ID$35 World Init/32Reset/Main-A4 cycle, closed0/open1 exact current-palette opaque and terrain-transparent EGL pixels, omitted-tile GPU-oracle negatives, all12 decoded source tiles, full/course complete-state and framebuffer replay, all412 logical IDs absent after module close.\n"
                + construction, StandardOpenOption.CREATE_NEW); // Only after ALL assertions, retirement AND session/graphics/boot cleanup.
    }

    /** Explicit test construction only; preserves source owners, shared globals and all buffers/services. */
    private static void stageTunnelDoorPlayer(SonicCdGameModule module, int doorX, int doorY) {
        var runtime = (CdOpeningObjectRuntime) module.getGameService(NativeDynamicObjectPass.class);
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase());
        module.requirePlayerActor().requireCompletedNativeFrame();
        var frame = module.requirePlayerFrame(); var actor = frame.captureActor(); var old = actor.state(); var m = old.motion();
        assertEquals(2, m.routine(), "Only the existing live Sonic actor may enter this constructed cut");
        var motion = new com.openggf.game.soniccd.player.CdPlayerMotionState((doorX - 24) << 16, doorY << 16,
                0, 0, 0, 2, 0, 2, 4, 9, 19, 0, m.previousAnimation(), 0, 0, 0, 0, 0,
                m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var state = new com.openggf.game.soniccd.player.CdPlayerBody.State(motion, old.animation(), old.header(), old.timers(),
                old.history(), old.rotationDistance(), old.objectLinks());
        frame.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), state, java.util.Optional.empty()));
        module.requirePlayerActor().rebindAfterRestore();
    }

    private static void assertTunnelDoorSat(CdObjectDrawQueue.CpuSat sat, int cameraX, int cameraY,
                                            int doorX, int doorY, int frame) {
        // Literal N MapSpr_FlapDoorV roots4/16, Mappings.asm:1-27. Full ADD.W with base$8328 precedes object flips.
        int[][] literal = frame == 0 ? new int[][] {{-24, 1, 0x9B2C, -4}, {-8, 3, 0x9B28, -4}}
                : new int[][] {{-24, 12, 0x832E, -4}, {-24, 4, 0x8332, 28}};
        byte[] raw = sat.bytes(); int seen = 0;
        assertTrue(sat.provenance().stream().anyMatch(p -> p.slotRef().wholeIndex() == 0), "Live Sonic must use the shared native SAT");
        for (int entry = 0; entry < sat.spriteCount(); entry++) {
            var origin = sat.provenance().get(entry); if (origin.slotRef().wholeIndex() != 40) continue;
            assertTrue(seen < 2); int[] p = literal[seen];
            assertEquals(1, origin.queueBucket()); assertEquals(seen++, origin.mappingPieceOrdinal());
            int at = entry * 8;
            assertEquals(doorY - cameraY + 128 + p[0], ((raw[at] & 255) << 8) | (raw[at + 1] & 255));
            assertEquals(p[1], raw[at + 2] & 255); assertEquals(entry + 1, raw[at + 3] & 255);
            assertEquals(p[2], ((raw[at + 4] & 255) << 8) | (raw[at + 5] & 255));
            assertEquals((doorX - cameraX + 128 + p[3]) & 511, ((raw[at + 6] & 255) << 8) | (raw[at + 7] & 255));
        }
        assertEquals(2, seen, "Both actual counted pieces must traverse native DrawObjects");
    }

    private static void assertTunnelDoorPixels(com.openggf.graphics.SpritePresentation.Frame picture, RgbaImage image,
                                               RgbaImage terrain, com.openggf.game.soniccd.level.CdR11aObjectAssets.TunnelDoorArtBank art,
                                               int logicalBase, int centreX, int centreY, int frame, java.util.Set<Integer> reached) {
        var tiles = picture.tiles().stream().filter(t -> t.patternId() >= logicalBase && t.patternId() < logicalBase + 12).toList();
        // Reverse native piece order, column-major source offsets, final mapping flips. No call to production convert/geometry for this oracle.
        int[][] literal = frame == 0 ? new int[][] {
                {0, -4, 16}, {1, -4, 8}, {2, -4, 0}, {3, -4, -8}, {4, -4, -16}, {5, -4, -24}}
                : new int[][] {{10, 28, -24}, {11, 36, -24}, {6, -4, -24}, {7, 4, -24}, {8, 12, -24}, {9, 20, -24}};
        assertEquals(6, tiles.size()); int changed = 0, opaque = 0, transparent = 0;
        // Independent expected image: native nibbles + literal screen geometry/flips + current engine palette.
        // The existing display-colour policy is shared; no production sprite converter/renderer builds this oracle.
        var graphics = GameServices.graphics(); var palette = GameServices.level().getCurrentLevel().getPalette(0);
        assertFalse(graphics.getPaletteFadePresentation().affects(0), "This ordinary constructed cut has no palette fade");
        int[] paletteArgb = new int[16];
        for (int index = 1; index < 16; index++) {
            var colour = palette.getColor(index);
            int rgb = com.openggf.graphics.color.DisplayColorConverter.toPackedRgb(colour.r & 255, colour.g & 255,
                    colour.b & 255, graphics.getDisplayColorProfile(), graphics.getDisplayColorExpansion());
            paletteArgb[index] = 0xFF000000 | rgb;
        }
        for (int index = 0; index < literal.length; index++) {
            int[] p = literal[index]; var tile = tiles.get(index); reached.add(p[0]);
            assertEquals(logicalBase + p[0], tile.patternId());
            assertEquals((float) (centreX + p[1]), tile.x()); assertEquals((float) (centreY + p[2]), tile.y());
            assertEquals(8f, tile.width()); assertEquals(8f, tile.height()); assertEquals(0, tile.palette());
            assertEquals(frame == 0, tile.hFlip()); assertEquals(frame == 0, tile.vFlip()); assertTrue(tile.priority());
            assertEquals(com.openggf.graphics.SpritePresentation.Layer.OBJECT, tile.layer());
            assertTrue(tile.priorityShader()); assertEquals(0, tile.occlusionMask());
            assertFalse(tile.ghost()); assertFalse(tile.ghostColour()); assertEquals(1f, tile.ghostAlpha());
            assertEquals(0, tile.rowStart()); assertEquals(8, tile.rowEnd()); assertFalse(tile.rowClipped());
            assertEquals(com.openggf.graphics.SpritePresentation.Subject.WORLD, tile.subject());
            var decoded = new com.openggf.level.Pattern(); decoded.fromSegaFormat(art.tile(0x328 + p[0]).copy());
            var version = picture.patternVersions().get(tile.patternId()); assertNotNull(version);
            long[] words = {version.a(), version.b(), version.c(), version.d()};
            for (int pixel = 0; pixel < 64; pixel++) assertEquals(decoded.getPixel(pixel % 8, pixel / 8) & 15,
                    (int) ((words[pixel / 16] >>> ((pixel % 16) * 4)) & 15), "decoded native Door pixel " + p[0] + ":" + pixel);
            for (var other : picture.tiles()) {
                if (other.patternId() >= logicalBase && other.patternId() < logicalBase + 12) continue;
                assertTrue(other.x() + other.width() <= tile.x() || other.x() >= tile.x() + tile.width()
                                || other.y() + other.height() <= tile.y() || other.y() >= tile.y() + tile.height(),
                        "Sonic and every other glyph must be outside this Door-only GPU footprint");
            }
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                int px = (int) tile.x() + x, py = (int) tile.y() + y;
                assertTrue(px >= 0 && px < 320 && py >= 0 && py < 224);
                int nativePixel = decoded.getPixel(frame == 0 ? 7 - x : x, frame == 0 ? 7 - y : y) & 15;
                int at = py * 320 + px;
                int expected = nativePixel == 0 ? terrain.pixels()[at] : paletteArgb[nativePixel];
                assertEquals(expected, image.pixels()[at], "Door " + frame + " tile " + p[0] + " pixel " + x + ":" + y
                        + " must match the independent current-palette/terrain image, including alpha");
                if (nativePixel == 0) transparent++;
                else {
                    opaque++;
                    if ((image.pixels()[at] & 0xFFFFFF) != (terrain.pixels()[at] & 0xFFFFFF)) changed++;
                }
            }
        }
        assertTrue(opaque > 0); assertEquals(6 * 64, opaque + transparent);
        assertTrue(changed > 0, "Actual GPU must draw inside this pose's isolated native opaque Door pixels, independently of Sonic");
    }

    // BEGIN constructed Monitor World proof. All native expectations are source-derived, not execution observations.
    @Test void actualEglConstructedShoesMonitorWorldBreaksAwardsReplaysAndRetires494Patterns() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual EGL/media opt-in");
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        String media = System.getProperty("soniccd.media.root"); assertNotNull(media); assertFalse(media.isBlank());
        CdGameDataSource source;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); source = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
        String log = InputScriptCompiler.compileToInputLog("1358 -");
        var movie = new Bk2MovieLoader().parseInputLogText(Path.of("constructed-shoes-monitor-world-input"), log.lines().toList());
        Path output = openCaptureOutput(log, "soniccd.monitor.output");
        String construction = "ENGINE constructed USA R11A shoes Monitor component; NOT a natural route/contact.\n"
                + "Row0 actual Sonic Init. At its CLOSED_CUT clear ALL96 dynamic slots, retain the saved-object bank/placement cursors and reserved actors. Construct ONLY slot40 ID19, X=cameraX+192, Y=cameraY+96, subtype4, savedID7, layer0, all other bytes from that cleared slot. No source callback/effect replay.\n"
                + "Rows1/2 actual Monitor Init then one visible Main: map0 then box16. Before row3 ONLY stage the current completed Sonic at MonitorX, MonitorY-22, zero X/GVel, YVel0100, routine2, angle0, flags6, spriteFlags4, radii7/14, animation2, playerJump1, charge/moveLock/stick/control0. Preserve previousAnimation, both previous-angle bytes, the complete animation cursor, header, ALL four actor timers, history/index, rotation and object links. Clear actor Display publication and rebind that SAME actor.\n"
                + "Actual Body MONITOR touch precedes dynamic pass: parent Break emits FM_DESTROY; Find allocates item32 then explosion33, BOTH already visited so both wait. No Monitor routine/frame/saved-bit or item routine/timer/position write follows construction.\n"
                + "Before row4 ONLY move that same Sonic to the authenticated load plan initial centre, routine2/angle0/flags0/spriteFlags4/radii9,19/animation0; velocities/jump/charge/moveLock/stick/control0. Preserve the SAME cursor/header/ALL timers/history/rotation/links/previousAnimation/angle copies, clear Display and rebind. This standing MODE may genuinely request FM_CHARGESTOP; every Body sound is included. No further staging.\n"
                + "Item movement calls1..32 at rows4..35; award call33 at row36 writes real USA shoes1320/flag1/physicsC00,18,80 and SHOESMUS6C, no Past FADEPCM82 (raw timeZone1). Next Display row37 gives1319. Explosion25=1 deletes without Points/Flower; item Delete30 row66; next publication row67 has no item.\n"
                + "Full AND course replay from pre-break row2 repeats EXACTLY row3 contact and row4 move-away ingress. Full AND course replay from nonzero-award row36 repeats NO staging, rows37..67. Every replay compares complete captures AND framebuffer at EVERY row.\n"
                + "Then neutral ordinary GameLoop rows68..1357 consume the genuinely earned timer to expiry (row1356) and one further Display. No reward/global/physics/timer setter, artificial expiry seed or monitor/item mutation. Independent native MODE/Display sound expectations include ChargeStop where its actual terrain predicate is reached.\n"
                + "At the final retirement cut ONLY, upload all494 actual current-policy tile versions into their already-owned atlas IDs, including UNUSED suffix+412..493, to prove every entry is removed. This upload is teardown coverage, NOT proof that every tile drew on screen.\n"
                + "Independent counted16/broken17 and header-free shoes4 SAT/geometry/nibble/current-palette opaque ARGB/zero-terrain oracles, omitted-visible-tile negative and native Plane B terrain controls. USA DEV preload/current display policy; excludes native palette, mixed native priority, native PLC residency/service, hardware audio, timing, natural-route and full-game parity.\n";
        if (output != null) {
            Files.writeString(output.resolve("construction.txt"), construction, StandardOpenOption.CREATE_NEW);
            Files.writeString(output.resolve("monitor-state.csv"),
                    "ENGINE-row,monitorX,monitorY,parentRoutine,parentFrame,itemID,itemY16_16,itemYVel,itemRoutine,shoesTimer,shoesFlag,cameraX,cameraY,sounds\n",
                    StandardOpenOption.CREATE_NEW);
        }
        try (var boot = new HeadlessGameBoot(320, 224)) {
            try {
                var loop = boot.bootSonicCdOpeningObjects(source);
                var mode = SessionManager.getCurrentGameplayMode();
                var module = assertInstanceOf(SonicCdGameModule.class, mode.getWorldSession().getGameModule());
                var runtime = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                var frame = module.requirePlayerFrame(); var actor = module.requirePlayerActor();
                assertSame(source, mode.getWorldSession().getDataSource());
                assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
                assertNull(GameServices.audio().captureLogicalSnapshot().presentation().activeMusic());
                step(loop, movie.getFrame(0)); // Actual Sonic Init, no synthesized actor/header.
                assertEquals(List.of(), actor.lastSoundRequests());
                var pool = runtime.placement().pool(); pool.clearDynamicSlots(); // Explicit ALL96 dynamic-slot ingress.
                assertTrue(pool.capture().slots().stream().allMatch(s -> Arrays.equals(new byte[64], s.copyBytes())));
                int monitorX = GameServices.camera().getX() + 192, monitorY = GameServices.camera().getY() + 96;
                var bounds = frame.environment().movement().bounds();
                assertTrue(monitorX - 24 >= bounds.left() && monitorX + 24 < bounds.right());
                assertTrue(monitorY >= 72 && monitorY + 24 < bounds.bottom() + 224);
                // Solidity selects a probe; a top-solid tile can contain air above its floor shape.
                // Check the actual motion/glyph envelope, ending at centre+16, not below it in the ground.
                var terrainLookup = new com.openggf.game.soniccd.level.CdTerrainCollision(source.assets().terrain());
                var terrainProbe = new com.openggf.game.soniccd.player.CdPlayerTerrain(source.assets().terrain());
                for (int y = monitorY - 72; y <= monitorY + 16; y += 8) for (int x = monitorX - 24; x <= monitorX + 24; x += 8) {
                    var placement = terrainLookup.lookup(x, y, 4);
                    assertFalse(placement instanceof com.openggf.game.soniccd.level.CdTerrainCollision.Unavailable);
                    assertTrue(terrainProbe.floor(x,y,13,0,16,4).distance() >= 0,
                            "Construction floor penetration at " + x + "," + y);
                    assertTrue(terrainProbe.wall(x,y,14,0,16,4).distance() >= 0,
                            "Construction wall penetration at " + x + "," + y);
                }
                assertEquals(0, runtime.placement().savedFlag(7, 1) & 1);
                pool.write(pool.state(40).moveByte(ID, 0x19).moveWord(X, monitorX).moveWord(Y, monitorY)
                        .moveByte(SUBTYPE, 4).moveByte(SAVED_FLAGS_ID, 7).moveByte(LAYER, 0));
                monitorWorldStep(loop, movie.getFrame(1), module, List.of());
                assertEquals(2, pool.state(40).routine()); assertEquals(0, pool.state(40).unsignedByte(MAP_FRAME));
                assertEquals(0x46, pool.state(40).unsignedByte(COLLISION_TYPE));
                assertEquals(0x20AA4E, pool.state(40).longBits(MAPPINGS)); assertEquals(0x05A8, pool.state(40).word(TILE));
                assertEquals(0x84, pool.state(40).spriteFlags()); assertEquals(4, pool.state(40).unsignedByte(ANIMATION));
                assertEquals(14, pool.state(40).unsignedByte(X_RADIUS)); assertEquals(14, pool.state(40).unsignedByte(Y_RADIUS));
                assertEquals(3, pool.state(40).unsignedByte(PRIORITY)); assertEquals(15, pool.state(40).unsignedByte(WIDTH));
                monitorWorldStep(loop, movie.getFrame(2), module, List.of());
                assertEquals(16, pool.state(40).unsignedByte(MAP_FRAME)); assertEquals(2, pool.state(40).routine());
                assertMonitorWorldSat(runtime.cpuSat(), GameServices.camera().getX(), GameServices.camera().getY(), monitorX, monitorY, 16, null);
                int base = com.openggf.graphics.PatternAtlasRange.OBJECTS.base();
                var banks = new CdOpeningObjectRuntime.LogicalBanks(base, base + 24, base + 38, base + 108, base + 136,
                        base + 199, base + 269, base + 274, base + 313, base + 349, base + 364, base + 400);
                assertEquals(base + 412, banks.monitorTime()); assertEquals(banks.tunnelDoorVertical() + 12, banks.monitorTime());
                var objects = source.assets().openingObjects(); var art = objects.monitorTimeArtBank();
                var binding = art.binding(com.openggf.game.soniccd.level.CdR11aAsset.ART_MONITOR_TIME_POSTS);
                assertEquals(0x5A8, binding.firstTileId()); assertEquals(82, binding.tileCount());
                assertEquals(0x05A8, binding.baseWord()); assertEquals(0xB500, binding.plc().vramAddress());
                var policy = monitorWorldPolicy(module, objects, banks);
                int[] nativeFirst = {0x780, 0x7AE, 0x3A0, 0x424, 0x441, 0x680, 0x6C6, 0x6D7, 0x544, 0x334, 0x520, 0x328, 0x5A8};
                int[] counts = {24, 14, 70, 28, 63, 70, 5, 39, 36, 15, 36, 12, 82};
                var atlas = GameServices.graphics().getPatternAtlas(); int logical = base;
                for (int span = 0; span < counts.length; span++) for (int tile = 0; tile < counts[span]; tile++) {
                    int id = logical++; assertEquals(id, policy.resolve(nativeFirst[span] + tile).logicalPatternId());
                    assertEquals(1, atlas.registeredRangesForTesting().stream()
                            .filter(range -> id >= range.base() && id < range.base() + range.size()).count(), "reservation " + id);
                }
                assertEquals(base + 494, logical, "Exact thirteen-span reservation, including every Monitor suffix ID");
                var registry = mode.getRewindRegistry(); var preFull = registry.capture(); var preCourse = registry.captureCourse();
                var before = capture(module); var beforePixels = render(module, true); assertCapture(before, module);
                var rows = new ArrayList<Capture>(); var pixels = new ArrayList<int[]>();
                var phasePictures = new ArrayList<RgbaImage>(); var phaseControls = new ArrayList<RgbaImage>();
                var previousCpu = runtime.cpuSat(); int previousX = GameServices.camera().getX(), previousY = GameServices.camera().getY();
                int previousItemY = 0; var savedBefore = before.objects().placement().savedFlags().copy();
                var scoreBefore = runtime.scoreGlobals(); var ringsBefore = runtime.ringGlobals();
                var contactBefore = runtime.contactGlobals(); var levelBefore = runtime.levelGlobals();
                for (int row = 3; row <= 67; row++) {
                    if (row == 3) stageMonitorWorldPlayer(module, monitorX, monitorY - 22, true);
                    if (row == 4) {
                        var initial = source.assets().loadPlan().initialCentre();
                        stageMonitorWorldPlayer(module, initial.centreX(), initial.centreY(), false);
                    }
                    List<CdOpeningObjectRuntime.SoundRequest> extra = row == 3
                            ? List.of(new CdOpeningObjectRuntime.MonitorFm(com.openggf.game.soniccd.objects.CdMonitor.FmSound.FM_DESTROY))
                            : row == 36 ? List.of(new CdOpeningObjectRuntime.MonitorItemSubCpu(
                                    com.openggf.game.soniccd.player.CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_SHOESMUS)) : List.of();
                    monitorWorldStep(loop, movie.getFrame(row), module, extra);
                    assertSame(actor, module.requirePlayerActor());
                    assertEquals(8, pool.state(40).routine()); assertEquals(17, pool.state(40).unsignedByte(MAP_FRAME));
                    assertEquals(0, pool.state(40).unsignedByte(COLLISION_TYPE)); assertEquals(1, runtime.placement().savedFlag(7, 1) & 1);
                    var expectedSaved = savedBefore.clone(); expectedSaved[24] |= 1;
                    assertArrayEquals(expectedSaved, runtime.placement().capture().savedFlags().copy(), "ONLY Monitor saved bit0 changes");
                    assertEquals(scoreBefore, runtime.scoreGlobals()); assertEquals(contactBefore, runtime.contactGlobals());
                    assertEquals(levelBefore, runtime.levelGlobals());
                    assertEquals(ringsBefore.rings(), runtime.ringGlobals().rings()); assertEquals(ringsBefore.lives(), runtime.ringGlobals().lives());
                    assertEquals(ringsBefore.timeStopTimer(), runtime.ringGlobals().timeStopTimer());
                    assertTrue(pool.capture().slots().stream().allMatch(s -> s.id() == 0 || s.identity().wholeIndex() == 40
                            || s.identity().wholeIndex() == 32 || s.identity().wholeIndex() == 33), "No Points, Flower or extra object allocation");
                    if (row == 3) {
                        assertTrue(frame.state().motion().yVelocity() < 0, "Actual Body MONITOR downward bounce precedes SAME-pass Break");
                        assertEquals(0x1A, pool.state(32).id()); assertEquals(0, pool.state(32).routine());
                        assertEquals(monitorY << 16, pool.state(32).longBits(Y_POSITION));
                        assertEquals(0x18, pool.state(33).id()); assertEquals(0, pool.state(33).routine());
                        assertEquals(1, pool.state(33).unsignedByte(ROUTINE_2)); assertEquals(1, pool.state(33).subtype());
                        assertEquals(0, frame.state().timers().speedShoes()); assertEquals(0, frame.environment().movement().gates().speedShoes());
                    }
                    if (row >= 4 && row <= 65) {
                        int moves = Math.min(row - 3, 32);
                        int displacement = -0x300 * moves + 0x18 * moves * (moves - 1) / 2;
                        var item = pool.state(32); assertEquals(0x1A, item.id()); assertEquals(monitorX << 16, item.longBits(X_POSITION));
                        assertEquals((monitorY << 16) + (displacement << 8), item.longBits(Y_POSITION), "OLD negative Y velocity supplies all32 moves");
                        assertEquals((short) (-0x300 + moves * 0x18), item.signedWord(Y_VELOCITY));
                        assertEquals(0x20AACF, item.longBits(MAPPINGS)); assertEquals(4, item.unsignedByte(MAP_FRAME));
                        assertEquals(0x85A8, item.word(TILE)); assertEquals(0xA4, item.spriteFlags());
                        assertEquals(3, item.unsignedByte(PRIORITY)); assertEquals(8, item.unsignedByte(WIDTH));
                        assertEquals(row < 36 ? 2 : 4, item.routine());
                        if (row >= 36) assertEquals(29 - (row - 36), item.word(MONITOR_ITEM_DELETE));
                    }
                    if (row >= 4 && row <= 24) {
                        assertEquals(0x18, pool.state(33).id()); assertEquals(1, pool.state(33).unsignedByte(ROUTINE_2));
                        assertEquals(row == 24 ? 4 : 2, pool.state(33).routine());
                        assertEquals(Math.min((row - 4) / 4, 4), pool.state(33).unsignedByte(MAP_FRAME));
                    }
                    if (row >= 25) assertArrayEquals(new byte[64], pool.state(33).copyBytes(), "Non-badnik Explosion deletes, never Flower/Points");
                    if (row >= 66) assertArrayEquals(new byte[64], pool.state(32).copyBytes(), "Delete30 clears the entire item slot");
                    assertEquals(row < 36 ? 0 : 1320 - (row - 36), frame.state().timers().speedShoes());
                    assertEquals(row < 36 ? 0 : 1, frame.environment().movement().gates().speedShoes());
                    assertEquals(new com.openggf.game.soniccd.player.CdPlayerMovement.Physics(row < 36 ? 0x600 : 0xC00,
                            row < 36 ? 0xC : 0x18, 0x80), frame.environment().movement().physics());
                    assertEquals(0, frame.state().timers().invincibility()); assertEquals(0, frame.environment().globals().invincible());
                    if (row == 36) {
                        assertEquals(0x6C, com.openggf.game.soniccd.player.CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_SHOESMUS.nativeId());
                        assertEquals(0x82, com.openggf.game.soniccd.player.CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_FADEPCM.nativeId());
                        assertEquals(1, frame.environment().movement().gates().timeZone());
                    }
                    assertMonitorWorldSat(runtime.cpuSat(), GameServices.camera().getX(), GameServices.camera().getY(),
                            monitorX, monitorY, 17, row >= 4 && row < 66 ? pool.state(32).centreY() : null);
                    var completed = capture(module); rows.add(completed);
                    var publication = completed.publication().published();
                    assertEquals(previousX, publication.cameraX()); assertEquals(previousY, publication.cameraY());
                    assertEquals(com.openggf.game.soniccd.resources.CdObjectPresentation.convert(previousCpu,
                            monitorWorldPolicy(module, objects, banks)), runtime.displayedFrame(), "Actual prior SAT with NOW-serviced resident windows");
                    var image = render(module, true); pixels.add(image.pixels().clone());
                    var terrain = renderTerrain(module, publication); assertNativeGpuBackground(module);
                    assertTerrainOutsideSprites(image, terrain, runtime.displayedFrame());
                    if (row == 3 || row >= 26) {
                        boolean displayedItem = row >= 26 && row <= 66;
                        int displayFrame = row == 3 ? 16 : 17;
                        var literal = monitorWorldLiteralTiles(monitorX - previousX, monitorY - previousY, displayFrame,
                                displayedItem ? previousItemY - previousY : null);
                        assertMonitorWorldPixels(runtime.displayedFrame(), image, terrain, art, banks.monitorTime(), literal);
                        if (row == 3 || row == 36) {
                            assertMonitorWorldOmittedTileRejected(runtime.displayedFrame(), image, terrain, art, banks.monitorTime(), literal);
                            phasePictures.add(image); phaseControls.add(terrain);
                        }
                    }
                    assertArrayEquals(image.pixels(), render(module, true).pixels()); assertCapture(completed, module);
                    previousCpu = runtime.cpuSat(); previousX = GameServices.camera().getX(); previousY = GameServices.camera().getY();
                    previousItemY = pool.state(32).centreY();
                    if (output != null) Files.writeString(output.resolve("monitor-state.csv"), String.format(Locale.ROOT,
                            "%d,%d,%d,%d,%d,%d,%08X,%d,%d,%d,%d,%d,%d,%s%n", row, monitorX, monitorY,
                            pool.state(40).routine(), pool.state(40).unsignedByte(MAP_FRAME), pool.state(32).id(),
                            pool.state(32).longBits(Y_POSITION), pool.state(32).signedWord(Y_VELOCITY), pool.state(32).routine(),
                            frame.state().timers().speedShoes(), frame.environment().movement().gates().speedShoes(),
                            GameServices.camera().getX(), GameServices.camera().getY(), runtime.lastSoundRequests().toString().replace(',', ';')),
                            StandardOpenOption.APPEND);
                }
                var end = capture(module); var endPixels = render(module, true); assertCapture(end, module);
                // Pre-break replay repeats just the two declared actor ingress operations; never the pool clear/construction.
                for (boolean courseOnly : List.of(false, true)) {
                    if (courseOnly) registry.restoreCourse(preCourse); else registry.restore(preFull);
                    assertCapture(before, module); assertArrayEquals(beforePixels.pixels(), render(module, true).pixels());
                    for (int row = 3; row <= 67; row++) {
                        if (row == 3) stageMonitorWorldPlayer(module, monitorX, monitorY - 22, true);
                        if (row == 4) {
                            var initial = source.assets().loadPlan().initialCentre();
                            stageMonitorWorldPlayer(module, initial.centreX(), initial.centreY(), false);
                        }
                        step(loop, movie.getFrame(row)); assertCapture(rows.get(row - 3), module);
                        assertArrayEquals(pixels.get(row - 3), render(module, true).pixels(), "Monitor pre-break full/course replay row " + row);
                        assertCapture(rows.get(row - 3), module);
                    }
                }
                // Recreate the REAL nonzero-award cut by exact pre-break replay, then capture both registry partitions there.
                registry.restore(preFull);
                for (int row = 3; row <= 36; row++) {
                    if (row == 3) stageMonitorWorldPlayer(module, monitorX, monitorY - 22, true);
                    if (row == 4) {
                        var initial = source.assets().loadPlan().initialCentre();
                        stageMonitorWorldPlayer(module, initial.centreX(), initial.centreY(), false);
                    }
                    step(loop, movie.getFrame(row)); assertCapture(rows.get(row - 3), module);
                    assertArrayEquals(pixels.get(row - 3), render(module, true).pixels());
                }
                assertEquals(1320, frame.state().timers().speedShoes());
                var awardFull = registry.capture(); var awardCourse = registry.captureCourse();
                for (boolean courseOnly : List.of(false, true)) {
                    if (courseOnly) registry.restoreCourse(awardCourse); else registry.restore(awardFull);
                    assertCapture(rows.get(33), module); assertArrayEquals(pixels.get(33), render(module, true).pixels());
                    for (int row = 37; row <= 67; row++) {
                        step(loop, movie.getFrame(row)); assertCapture(rows.get(row - 3), module);
                        assertArrayEquals(pixels.get(row - 3), render(module, true).pixels(), "Nonzero award full/course replay row " + row);
                        assertCapture(rows.get(row - 3), module);
                    }
                    assertCapture(end, module); assertArrayEquals(endPixels.pixels(), render(module, true).pixels());
                }
                // No artificial timer seed: consume the remaining1289 Displays of the award, then prove no repeated expiry.
                for (int row = 68; row <= 1357; row++) {
                    monitorWorldStep(loop, movie.getFrame(row), module, List.of());
                    assertEquals(Math.max(0, 1320 - (row - 36)), frame.state().timers().speedShoes());
                    assertEquals(row < 1356 ? 1 : 0, frame.environment().movement().gates().speedShoes());
                    assertEquals(new com.openggf.game.soniccd.player.CdPlayerMovement.Physics(row < 1356 ? 0xC00 : 0x600,
                            row < 1356 ? 0x18 : 0xC, 0x80), frame.environment().movement().physics());
                    assertEquals(scoreBefore, runtime.scoreGlobals()); assertEquals(contactBefore, runtime.contactGlobals());
                    assertEquals(levelBefore, runtime.levelGlobals());
                    assertEquals(0x19, pool.state(40).id()); assertEquals(8, pool.state(40).routine());
                    assertEquals(17, pool.state(40).unsignedByte(MAP_FRAME)); assertEquals(1, runtime.placement().savedFlag(7, 1) & 1);
                    assertArrayEquals(new byte[64], pool.state(32).copyBytes()); assertArrayEquals(new byte[64], pool.state(33).copyBytes());
                }
                var expired = capture(module); var expiredPixels = render(module, true); assertNativeGpuBackground(module);
                assertTerrainOutsideSprites(expiredPixels, render(module, false), runtime.displayedFrame()); assertCapture(expired, module);
                if (output != null) {
                    for (int pose = 0; pose < phasePictures.size(); pose++) {
                        String label = pose == 0 ? "box16-before-break" : "real-award-shoes4-broken17";
                        ScreenshotCapture.savePNG(phasePictures.get(pose), output.resolve("ENGINE-constructed-monitor-" + label + ".png"));
                        ScreenshotCapture.savePNG(phaseControls.get(pose), output.resolve("ENGINE-native-plane-terrain-" + label + ".png"));
                    }
                    ScreenshotCapture.savePNG(expiredPixels, output.resolve("ENGINE-earned-shoes-expired.png"));
                }
                // Fill ALL already-reserved IDs from the actual current policy solely to make retirement exhaustive.
                // No injected glyph, missing geometry surrogate, SAT change, new scope or gameplay owner.
                var retirementPolicy = monitorWorldPolicy(module, objects, banks); logical = base;
                for (int span = 0; span < counts.length; span++) for (int tile = 0; tile < counts[span]; tile++) {
                    var sample = retirementPolicy.resolve(nativeFirst[span] + tile); assertEquals(logical++, sample.logicalPatternId());
                    var v = sample.version(); long[] words = {v.a(), v.b(), v.c(), v.d()}; var pattern = new com.openggf.level.Pattern();
                    for (int at = 0; at < 64; at++) pattern.setPixel(at % 8, at / 8, (byte) ((words[at / 16] >>> ((at % 16) * 4)) & 15));
                    GameServices.graphics().cachePatternTexture(pattern, sample.logicalPatternId());
                }
                assertEquals(base + 494, logical); assertCapture(expired, module);
                for (int offset = 0; offset < 494; offset++) {
                    var entry = atlas.getEntry(base + offset); assertNotNull(entry, "Every retirement witness is installed: " + offset);
                    assertTrue(glIsTexture(atlas.getTextureId(entry.atlasIndex())));
                }
                module.closeTerrainPlayer(); assertTrue(runtime.closed()); assertTrue(frame.closed());
                assertSame(atlas, GameServices.graphics().getPatternAtlas());
                for (int offset = 0; offset < 494; offset++) assertNull(atlas.getEntry(base + offset),
                        "ALL494 retired logical IDs; explicit Monitor suffix+412..493: " + offset);
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
            } finally {
                try { SessionManager.closeGameplaySession(); }
                finally { GraphicsManager.destroyForReinit(); }
            }
        }
        if (output != null) Files.writeString(output.resolve("outcome.txt"),
                "PASS: constructed actual ID19 Init/Main/Body-MONITOR touch/Break, earlier-slot item/explosion visits,32 native rise moves, call33 REAL USA award/next-Display decrement/genuine expiry, no Points/Flower, Delete30, counted/static SAT and exact native-decoded current-palette/terrain EGL pixels plus omitted-tile negatives, pre-break/nonzero-award full/course complete-state and per-row pixel replay, ALL494 installed retirement witnesses absent; session/graphics/boot closed.\n"
                + construction, StandardOpenOption.CREATE_NEW); // AFTER every assertion, all494 cleanup and finally closures.
    }

    /** Explicit actor-only construction at an existing completed cut; ALL header/timer/cursor/history bytes preserved. */
    private static void stageMonitorWorldPlayer(SonicCdGameModule module, int x, int y, boolean rollingContact) {
        var runtime = (CdOpeningObjectRuntime) module.getGameService(NativeDynamicObjectPass.class);
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase());
        module.requirePlayerActor().requireCompletedNativeFrame();
        var frame = module.requirePlayerFrame(); var actor = frame.captureActor(); var old = actor.state(); var m = old.motion();
        assertEquals(2, m.routine());
        var motion = new com.openggf.game.soniccd.player.CdPlayerMotionState(x << 16, y << 16, 0, rollingContact ? 0x100 : 0, 0,
                2, 0, rollingContact ? 6 : 0, 4, rollingContact ? 7 : 9, rollingContact ? 14 : 19,
                rollingContact ? 2 : 0, m.previousAnimation(), rollingContact ? 1 : 0, 0, 0, 0, 0,
                m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var state = new com.openggf.game.soniccd.player.CdPlayerBody.State(motion, old.animation(), old.header(), old.timers(),
                old.history(), old.rotationDistance(), old.objectLinks());
        frame.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), state, java.util.Optional.empty()));
        module.requirePlayerActor().rebindAfterRestore();
        assertSame(old.header(), frame.state().header()); assertSame(old.timers(), frame.state().timers());
    }

    /** Native neutral MODE predicates, then Display expiry: complete expected Body sound list, never a filtered ledger. */
    private static List<CdPlayerFrameRuntime.Sound> monitorWorldPlayerSounds(CdPlayerFrameRuntime frame) {
        var s = frame.state(); var m = s.motion(); var e = frame.environment(); var g = e.movement().gates();
        assertEquals(2, m.routine()); assertEquals(0, m.charge()); assertEquals(0, m.moveLock());
        assertEquals(0, m.flags() & 8); assertEquals(0, m.playerControl() & 0xCD);
        assertEquals(0, g.zoneAct()); assertEquals(1, g.timeZone()); assertEquals(0, g.waterSlide());
        assertEquals(0, g.windTunnel()); assertEquals(0, e.globals().invincible()); assertEquals(0, e.globals().bossMusic());
        var sounds = new ArrayList<CdPlayerFrameRuntime.Sound>();
        // MoveGround.CheckBalance, Sonic/Main.asm:837-941: neutral, no charge/support/lock, zero GVel, ordinary angle.
        if ((m.flags() & 6) == 0 && ((m.angle() + 0x20) & 0xC0) == 0 && m.groundVelocity() == 0) {
            var floor = new com.openggf.game.soniccd.player.CdPlayerTerrain(frame.assets().terrain())
                    .floor(m.centreX16_16() >>> 16, (m.centreY16_16() >>> 16) + m.yRadius(), 13, 0, 16, m.spriteFlags());
            if (floor.distance() >= 12) sounds.add(new CdPlayerFrameRuntime.MovementSound(
                    com.openggf.game.soniccd.player.CdPlayerMovement.Sound.FM_CHARGESTOP));
            else sounds.add(new CdPlayerFrameRuntime.ChargeSound(
                    com.openggf.game.soniccd.player.CdPlayerCharge.Sound.FM_CHARGESTOP)); // CheckCharge:1033-1036, even at zero charge.
        }
        assertNotEquals(4, m.flags() & 6, "No grounded rolling/charge owner is constructed in this fixture");
        // ObjSonic_Display:373-441 follows MODE. No invincibility/boss owner masks this actual earned expiry.
        if (g.speedShoes() != 0 && s.timers().speedShoes() == 1)
            sounds.add(new CdPlayerFrameRuntime.BodySound(com.openggf.game.soniccd.player.CdPlayerBody.Sound.SCMD_R1AMUS));
        return List.copyOf(sounds);
    }

    private static void monitorWorldStep(GameLoop loop, Bk2FrameInput input, SonicCdGameModule module,
                                         List<CdOpeningObjectRuntime.SoundRequest> objectSounds) {
        var expectedPlayer = monitorWorldPlayerSounds(module.requirePlayerFrame());
        var expected = new ArrayList<CdOpeningObjectRuntime.SoundRequest>();
        expectedPlayer.forEach(sound -> expected.add(new CdOpeningObjectRuntime.PlayerSound(sound))); expected.addAll(objectSounds);
        step(loop, input);
        var runtime = (CdOpeningObjectRuntime) module.getGameService(NativeDynamicObjectPass.class);
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase());
        assertEquals(expectedPlayer, module.requirePlayerActor().lastSoundRequests(), "ALL Body owners in native call order, including ChargeStop/expiry");
        assertEquals(expected, runtime.lastSoundRequests(), "Complete Body prefix then actual Monitor/item calls, never effect replay");
    }

    private static com.openggf.game.soniccd.resources.CdObjectPresentation.PatternSource monitorWorldPolicy(
            SonicCdGameModule module, com.openggf.game.soniccd.level.CdR11aObjectAssets objects,
            CdOpeningObjectRuntime.LogicalBanks banks) throws Exception {
        var runtime = (CdOpeningObjectRuntime) module.getGameService(NativeDynamicObjectPass.class);
        return com.openggf.game.soniccd.resources.CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(
                module.requirePlayerFrame().artState(), runtime.powerupArtState(), objects, objects.frontierArtBank(), objects.contactArtBank(),
                objects.floatBlockArtBank(), objects.springArtBank(), objects.tunnelDoorArtBank(), objects.monitorTimeArtBank(), runtime.sectionArtState().residency(),
                banks.player(), banks.ring(), banks.mosqui(), banks.plant(), banks.ramp(), banks.explosion(), banks.points(), banks.flower(),
                banks.powerup(), banks.floatBlock(), banks.spring(), banks.tunnelDoorVertical(), banks.monitorTime(), banks.boulder(), banks.diagonalSpring(), banks.spikes(), banks.checkpoint(), banks.anton());
    }

    /** Literal N MM:157-167 and MM:55-60. Static item has ONE piece despite counted frame4 having four. */
    private static void assertMonitorWorldSat(CdObjectDrawQueue.CpuSat sat, int cameraX, int cameraY,
                                              int monitorX, int monitorY, int parentFrame, Integer itemY) {
        int[][] parent = parentFrame == 16 ? new int[][] {{-16, 6, 0x05A8, -16}, {-16, 6, 0x0DA8, 0}, {8, 12, 0x05AE, -16}}
                : new int[][] {{0, 13, 0x05B2, -16}};
        int parentSeen = 0, itemSeen = 0; byte[] raw = sat.bytes();
        assertTrue(sat.provenance().stream().anyMatch(p -> p.slotRef().wholeIndex() == 0));
        for (int entry = 0; entry < sat.spriteCount(); entry++) {
            var origin = sat.provenance().get(entry); int whole = origin.slotRef().wholeIndex();
            if (whole != 40 && whole != 32) continue;
            assertEquals(3, origin.queueBucket()); int[] p; int centreY;
            if (whole == 40) { assertTrue(parentSeen < parent.length); p = parent[parentSeen]; centreY = monitorY;
                assertEquals(parentSeen++, origin.mappingPieceOrdinal()); }
            else { assertNotNull(itemY); assertEquals(0, itemSeen++); assertEquals(0, origin.mappingPieceOrdinal());
                p = new int[] {-10, 5, 0x85CA, -8}; centreY = itemY; }
            int at = entry * 8;
            assertEquals((centreY - cameraY + 128 + p[0]) & 0xFFFF, ((raw[at] & 255) << 8) | (raw[at + 1] & 255));
            assertEquals(p[1], raw[at + 2] & 255); assertEquals(entry + 1, raw[at + 3] & 255);
            assertEquals(p[2], ((raw[at + 4] & 255) << 8) | (raw[at + 5] & 255));
            assertEquals((monitorX - cameraX + 128 + p[3]) & 511, ((raw[at + 6] & 255) << 8) | (raw[at + 7] & 255));
        }
        assertEquals(parent.length, parentSeen); assertEquals(itemY == null ? 0 : 1, itemSeen);
    }

    private record MonitorWorldTile(int offset, int x, int y, boolean hFlip, boolean priority) { }

    /** Test-only literal geometry, independent of production converter/mapping decoder/SpritePieceRenderer. */
    private static List<MonitorWorldTile> monitorWorldLiteralTiles(int x, int y, int parentFrame, Integer itemY) {
        var out = new ArrayList<MonitorWorldTile>();
        if (parentFrame == 16) {
            // Reverse counted piece order; source tiles are column-major, the right panel is horizontally mirrored.
            for (int col = 0; col < 4; col++) out.add(new MonitorWorldTile(6 + col, x - 16 + 8 * col, y + 8, false, false));
            for (int row = 0; row < 3; row++) for (int col = 0; col < 2; col++)
                out.add(new MonitorWorldTile(col * 3 + row, x + 8 - 8 * col, y - 16 + 8 * row, true, false));
            for (int row = 0; row < 3; row++) for (int col = 0; col < 2; col++)
                out.add(new MonitorWorldTile(col * 3 + row, x - 16 + 8 * col, y - 16 + 8 * row, false, false));
        } else {
            assertEquals(17, parentFrame);
            for (int row = 0; row < 2; row++) for (int col = 0; col < 4; col++)
                out.add(new MonitorWorldTile(10 + col * 2 + row, x - 16 + col * 8, y + row * 8, false, false));
        }
        if (itemY != null) for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++)
            out.add(new MonitorWorldTile(0x22 + col * 2 + row, x - 8 + col * 8, itemY - 10 + row * 8, false, true));
        return List.copyOf(out);
    }

    private static void assertMonitorWorldPixels(com.openggf.graphics.SpritePresentation.Frame picture, RgbaImage image,
            RgbaImage terrain, com.openggf.game.soniccd.level.CdR11aObjectAssets.MonitorTimeArtBank art,
            int logicalBase, List<MonitorWorldTile> literal) {
        var tiles = picture.tiles().stream().filter(t -> t.patternId() >= logicalBase && t.patternId() < logicalBase + 82).toList();
        assertEquals(literal.size(), tiles.size(), "Exact counted/static tile geometry; atlas presence/change alone is insufficient");
        var graphics = GameServices.graphics(); var palette = GameServices.level().getCurrentLevel().getPalette(0);
        assertFalse(graphics.getPaletteFadePresentation().affects(0)); int[] paletteArgb = new int[16];
        for (int index = 1; index < 16; index++) {
            var colour = palette.getColor(index);
            paletteArgb[index] = 0xFF000000 | com.openggf.graphics.color.DisplayColorConverter.toPackedRgb(colour.r & 255,
                    colour.g & 255, colour.b & 255, graphics.getDisplayColorProfile(), graphics.getDisplayColorExpansion());
        }
        int opaque = 0, transparent = 0, changed = 0;
        for (int index = 0; index < literal.size(); index++) {
            var p = literal.get(index); var tile = tiles.get(index);
            assertEquals(logicalBase + p.offset(), tile.patternId()); assertEquals((float) p.x(), tile.x()); assertEquals((float) p.y(), tile.y());
            assertEquals(8f, tile.width()); assertEquals(8f, tile.height()); assertEquals(0, tile.palette());
            assertEquals(p.hFlip(), tile.hFlip()); assertFalse(tile.vFlip()); assertEquals(p.priority(), tile.priority());
            assertEquals(com.openggf.graphics.SpritePresentation.Layer.OBJECT, tile.layer());
            assertTrue(tile.priorityShader()); assertEquals(0, tile.occlusionMask()); assertFalse(tile.ghost()); assertFalse(tile.ghostColour());
            assertEquals(1f, tile.ghostAlpha()); assertEquals(0, tile.rowStart()); assertEquals(8, tile.rowEnd()); assertFalse(tile.rowClipped());
            assertEquals(com.openggf.graphics.SpritePresentation.Subject.WORLD, tile.subject());
            var decoded = new com.openggf.level.Pattern(); decoded.fromSegaFormat(art.tile(0x5A8 + p.offset()).copy());
            var version = picture.patternVersions().get(tile.patternId()); assertNotNull(version);
            long[] words = {version.a(), version.b(), version.c(), version.d()};
            for (int at = 0; at < 64; at++) assertEquals(decoded.getPixel(at % 8, at / 8) & 15,
                    (int) ((words[at / 16] >>> ((at % 16) * 4)) & 15), "Native Monitor source nibble " + p.offset() + ":" + at);
            for (var other : picture.tiles()) {
                if (other.patternId() >= logicalBase && other.patternId() < logicalBase + 82) continue;
                assertTrue(other.x() + other.width() <= tile.x() || other.x() >= tile.x() + tile.width()
                        || other.y() + other.height() <= tile.y() || other.y() >= tile.y() + tile.height(), "No other owner may overlap the Monitor oracle footprint");
            }
            for (int dy = 0; dy < 8; dy++) for (int dx = 0; dx < 8; dx++) {
                int px = p.x() + dx, py = p.y() + dy; assertTrue(px >= 0 && px < 320 && py >= 0 && py < 224);
                int pixel = decoded.getPixel(p.hFlip() ? 7 - dx : dx, dy) & 15; int at = py * 320 + px;
                assertEquals(pixel == 0 ? terrain.pixels()[at] : paletteArgb[pixel], image.pixels()[at],
                        "Exact native Monitor geometry + current palette/terrain ARGB " + p.offset() + ":" + dx + ":" + dy);
                if (pixel == 0) transparent++; else { opaque++; if ((image.pixels()[at] & 0xFFFFFF) != (terrain.pixels()[at] & 0xFFFFFF)) changed++; }
            }
        }
        assertTrue(opaque > 0); assertTrue(transparent > 0); assertEquals(literal.size() * 64, opaque + transparent); assertTrue(changed > 0);
    }

    private static void assertMonitorWorldOmittedTileRejected(com.openggf.graphics.SpritePresentation.Frame picture, RgbaImage image,
            RgbaImage terrain, com.openggf.game.soniccd.level.CdR11aObjectAssets.MonitorTimeArtBank art,
            int logicalBase, List<MonitorWorldTile> literal) {
        int omitted = -1, removed = 0, retained = 0;
        for (int i = 0; i < literal.size(); i++) {
            var tile = literal.get(i); int changed = 0;
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                int at = (tile.y() + y) * 320 + tile.x() + x;
                if ((image.pixels()[at] & 0xFFFFFF) != (terrain.pixels()[at] & 0xFFFFFF)) changed++;
            }
            if (omitted < 0 && changed > 0) { omitted = i; removed = changed; } else retained += changed;
        }
        assertTrue(omitted >= 0 && removed > 0 && retained > 0, "Remove one visibly contributing tile while retaining other actual geometry");
        var missing = literal.get(omitted); var partial = image.copy();
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            int at = (missing.y() + y) * 320 + missing.x() + x; partial.pixels()[at] = terrain.pixels()[at];
        }
        assertThrows(AssertionError.class, () -> assertMonitorWorldPixels(picture, partial, terrain, art, logicalBase, literal),
                "An omitted visible Monitor tile MUST fail even while the other geometry remains visible");
    }
    // END constructed Monitor World proof.

    // BEGIN constructed Boulder World proof. Source-only authoring; execution belongs to Root.
    // Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
    @Test void actualEglConstructedBoulderWorldUsesCurrentNativePixelsAndRetires513Patterns() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual EGL/media opt-in");
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        String media = System.getProperty("soniccd.media.root"); assertNotNull(media); assertFalse(media.isBlank());
        CdGameDataSource source;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); source = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
        String log = InputScriptCompiler.compileToInputLog("4 -");
        var movie = new Bk2MovieLoader().parseInputLogText(Path.of("constructed-boulder-world-input"), log.lines().toList());
        Path output = openCaptureOutput(log, "soniccd.boulder.output");
        String construction = "ENGINE constructed USA R11A Boulder component; no natural-route/contact claim.\n"
                + "Row0: actual Sonic Init. Four independent CLOSED_CUT scenes use native flip bits0,1,2,3.\n"
                + "Each scene clears ALL96 dynamics and seeds cleared slot40 ONLY: ID1B, X=cameraX+200, Y=cameraY+72, subtype2=0, spriteFlags=flip, SOLID_TYPE/routine2=1 (explicit dirty helper witness).\n"
                + "Once before each scene's preInit capture, restore/rebind the same live Sonic at BoulderX-144/BoulderY; velocities/angle/jump/charge/moveLock/stick/control=0, routine2, flags0, spriteFlags4, radii9/19, animation0. Retain previousAnimation/angle copies, animation state, header, timers, history, rotation, object links, owner and shared state; clear actor display publication.\n"
                + "No object routine/frame/map/tile/timer/flag writes after construction. Clean bit7 Init falls into Main but skips Solid, retains SOLID_TYPE1; real DrawObjects marks bit7; next Main runs shared Solid GetOff and clears SOLID_TYPE0. Sonic remains left of the complete40-pixel picture, not merely the16-pixel collision/cull radius.\n"
                + "Rows1..3 use existing GameLoop normalFrame and complete Body sound oracle (including reached ChargeStop), real body/DrawObject/DrawObjects/SAT/V-int/tail. Every preInit/postInit/postMain/nextDisplay cut captures full AND course state/pixels; each is restored and forward-replayed without repeating construction or reloading art.\n"
                + "At the final retirement cut ONLY, upload all513 actual NOW-policy tile versions into their already-owned IDs and require every entry present before close. Teardown coverage only; not proof every tile drew.\n"
                + "At that same teardown cut, collect actual resident native11-bit entries plus registered virtual ranges outside the513 owned IDs; native IDs are implicitly governed and absent from registeredRangesForTesting. Preserve each actual shared entry by identity after CD close; no synthetic shared entry or registration.\n"
                + "DEV cold19 Boulder tiles in the ONE current residency; current palette and native Plane B controls, no native palette/hardware mixed-priority/timing/pixel parity, native cold PLC history or full-game certification.\n";
        if (output != null) Files.writeString(output.resolve("construction.txt"), construction, StandardOpenOption.CREATE_NEW);
        var observations = new StringBuilder();
        try (var boot = new HeadlessGameBoot(320, 224)) {
            try {
                var loop = boot.bootSonicCdOpeningObjects(source);
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
                var mode = SessionManager.getCurrentGameplayMode();
                var module = assertInstanceOf(SonicCdGameModule.class, mode.getWorldSession().getGameModule());
                var runtime = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                var frame = module.requirePlayerFrame();
                assertSame(source, mode.getWorldSession().getDataSource()); assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
                step(loop, movie.getFrame(0)); // Only actual Sonic Init before any constructed scene.
                int base = com.openggf.graphics.PatternAtlasRange.OBJECTS.base();
                var banks = new CdOpeningObjectRuntime.LogicalBanks(base, base + 24, base + 38);
                assertEquals(base + 494, banks.boulder()); assertEquals(banks.monitorTime() + 82, banks.boulder());
                var objects = source.assets().openingObjects();
                var sources = objects.sectionArtSources(); assertEquals(10, sources.sources().size());
                var boulder = sources.source(com.openggf.game.soniccd.level.CdR11aAsset.ART_BOULDER);
                assertEquals(2, boulder.canonicalPlcId()); assertEquals(0, boulder.canonicalRowIndex());
                assertEquals(0x6E80, boulder.canonicalPlc().vramAddress()); assertEquals(19, boulder.tileCount());
                assertEquals(0x8013, boulder.readerBytes().unsignedWord(0)); assertEquals(608, boulder.decode().bytes().length());
                var mapping = objects.view(com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.BOULDER_MAPPINGS);
                assertEquals(0x209654, mapping.range().nativeAddress());
                assertArrayEquals(java.util.HexFormat.of().parseHex("000202F8020000ECF00F0003F400"), mapping.bytes().copy());
                assertEquals(1, objects.boulder().mappingFrames().size()); assertEquals(0x374, objects.tileWord(0xB, 0));
                for (int tile = 0; tile < 19; tile++) assertArrayEquals(boulder.decode().bytes().slice(tile * 32, 32).copy(),
                        com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(runtime.sectionArtState().residency(), 0x374 + tile).copy(),
                        "Explicit DEV seed, not per-object art: " + tile);
                int[] nativeFirst = {0x780, 0x7AE, 0x3A0, 0x424, 0x441, 0x680, 0x6C6, 0x6D7, 0x544, 0x334, 0x520, 0x328, 0x5A8, 0x374};
                int[] counts = {24, 14, 70, 28, 63, 70, 5, 39, 36, 15, 36, 12, 82, 19};
                var atlas = GameServices.graphics().getPatternAtlas();
                var policy = monitorWorldPolicy(module, objects, banks); int logical = base;
                for (int span = 0; span < counts.length; span++) for (int tile = 0; tile < counts[span]; tile++) {
                    int id = logical++;
                    assertEquals(id, policy.resolve(nativeFirst[span] + tile).logicalPatternId());
                    assertEquals(1, atlas.registeredRangesForTesting().stream()
                            .filter(range -> id >= range.base() && id < range.base() + range.size()).count());
                }
                assertEquals(base + 513, logical, "Exactly14 spans/513 IDs; old13-span C23 stays494");
                var registry = mode.getRewindRegistry(); var pool = runtime.placement().pool();
                for (int flip = 0; flip < 4; flip++) {
                    assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase());
                    pool.clearDynamicSlots();
                    assertEquals(96, pool.capture().slots().size());
                    assertTrue(pool.capture().slots().stream().allMatch(s -> Arrays.equals(new byte[64], s.copyBytes())));
                    int boulderX = GameServices.camera().getX() + 200, boulderY = GameServices.camera().getY() + 72;
                    pool.write(pool.state(40).moveByte(ID, 0x1B).moveWord(X, boulderX).moveWord(Y, boulderY)
                            .moveByte(SUBTYPE2, 0).moveByte(SPRITE_FLAGS, flip).moveByte(SOLID_TYPE, 1));
                    stageBoulderWorldPlayer(module, boulderX - 144, boulderY);
                    assertEquals(0, pool.state(40).routine()); assertEquals(flip, pool.state(40).spriteFlags());
                    var fullCuts = new ArrayList<>(List.of(registry.capture()));
                    var courseCuts = new ArrayList<>(List.of(registry.captureCourse()));
                    var states = new ArrayList<>(List.of(capture(module)));
                    var pictures = new ArrayList<>(List.of(render(module, true)));
                    assertCapture(states.getFirst(), module);
                    var previousCpu = runtime.cpuSat();
                    int previousCameraX = GameServices.camera().getX(), previousCameraY = GameServices.camera().getY();
                    for (int row = 1; row <= 3; row++) {
                        boulderWorldStep(loop, movie.getFrame(row), module); // Actual complete Body prefix, no direct Boulder FM.
                        var live = pool.state(40); var motion = frame.state().motion();
                        assertEquals(0x1B, live.id()); assertEquals(2, live.routine());
                        assertEquals(row == 1 ? 1 : 0, live.unsignedByte(SOLID_TYPE),
                                "Init must retain dirty1; only next Main's actual shared Solid GetOff clears it");
                        assertEquals(0x84 | flip, live.spriteFlags(), "Only real DrawObjects sets bit7");
                        assertEquals(4, live.unsignedByte(PRIORITY)); assertEquals(16, live.unsignedByte(WIDTH));
                        assertEquals(16, live.unsignedByte(Y_RADIUS)); assertEquals(0, live.unsignedByte(MAP_FRAME));
                        assertEquals(0x209654, live.longBits(MAPPINGS)); assertEquals(0x374, live.word(TILE));
                        assertEquals(boulderX, live.centreX()); assertEquals(boulderY, live.centreY()); assertEquals(0, live.flags());
                        assertEquals(2, motion.routine()); assertNotEquals(0x17, motion.animation());
                        assertEquals(0, motion.playerControl() & 0x40); assertEquals(0, frame.environment().globals().debugMode());
                        assertTrue((motion.centreX16_16() >>> 16) + 32 < boulderX - 20, "Actor stays outside full native picture");
                        assertTrue(pool.capture().slots().stream().allMatch(s -> s.id() == 0 || s.identity().wholeIndex() == 40));
                        assertBoulderWorldSat(runtime.cpuSat(), GameServices.camera().getX(), GameServices.camera().getY(), boulderX, boulderY, flip);
                        var completed = capture(module); var published = completed.publication().published();
                        assertEquals(previousCameraX, published.cameraX()); assertEquals(previousCameraY, published.cameraY());
                        assertEquals(com.openggf.game.soniccd.resources.CdObjectPresentation.convert(previousCpu,
                                        monitorWorldPolicy(module, objects, banks)), runtime.displayedFrame(),
                                "Previous actual CPU SAT + NOW player/powerup/section current art, never a per-object source resolver");
                        var image = render(module, true); var terrain = renderTerrain(module, published);
                        assertNativeGpuBackground(module); assertTerrainOutsideSprites(image, terrain, runtime.displayedFrame());
                        if (row >= 2) {
                            assertBoulderWorldSat(previousCpu, previousCameraX, previousCameraY, boulderX, boulderY, flip);
                            var literal = boulderWorldLiteralTiles(boulderX - published.cameraX(), boulderY - published.cameraY(), flip);
                            observations.append("flip=").append(flip).append(" row=").append(row).append(' ')
                                    .append(assertBoulderWorldPixels(runtime.displayedFrame(), image, terrain,
                                            runtime.sectionArtState().residency(), banks.boulder(), literal,
                                            boulderX - published.cameraX(), boulderY - published.cameraY())).append('\n');
                            assertBoulderWorldOmissions(runtime.displayedFrame(), image, terrain, runtime.sectionArtState().residency(),
                                    banks.boulder(), literal, boulderX - published.cameraX(), boulderY - published.cameraY());
                            var withoutPlane = renderWithoutBackground(module);
                            assertFalse(Arrays.equals(terrain.pixels(), withoutPlane.pixels()), "Actual native Plane B contributes to terrain control");
                            assertArrayEquals(image.pixels(), render(module, true).pixels(), "Ordinary draw reinstalls selected native Plane B");
                            for (int tile = 0; tile < 19; tile++) {
                                var entry = atlas.getEntry(banks.boulder() + tile); assertNotNull(entry, "Real picture installed tile " + tile);
                                assertTrue(glIsTexture(atlas.getTextureId(entry.atlasIndex())));
                                assertEquals(runtime.displayedFrame().patternVersions().get(banks.boulder() + tile),
                                        com.openggf.graphics.SpritePresentation.patternSample(GameServices.graphics(), banks.boulder() + tile));
                            }
                        }
                        assertCapture(completed, module); states.add(completed); pictures.add(image);
                        fullCuts.add(registry.capture()); courseCuts.add(registry.captureCourse());
                        if (output != null) {
                            ScreenshotCapture.savePNG(image, output.resolve("ENGINE-constructed-boulder-flip" + flip + "-row" + row + ".png"));
                            ScreenshotCapture.savePNG(terrain, output.resolve("ENGINE-native-plane-terrain-flip" + flip + "-row" + row + ".png"));
                        }
                        previousCpu = runtime.cpuSat(); previousCameraX = GameServices.camera().getX(); previousCameraY = GameServices.camera().getY();
                    }
                    // Restore EVERY selected cut, including both sides of actual Init/Main, then ordinary forward replay.
                    for (int cut = 0; cut <= 3; cut++) for (boolean courseOnly : List.of(false, true)) {
                        if (courseOnly) registry.restoreCourse(courseCuts.get(cut)); else registry.restore(fullCuts.get(cut));
                        assertCapture(states.get(cut), module); assertArrayEquals(pictures.get(cut).pixels(), render(module, true).pixels());
                        assertCapture(states.get(cut), module);
                        for (int row = cut + 1; row <= 3; row++) {
                            boulderWorldStep(loop, movie.getFrame(row), module); // NO construction/seed/callback/effect replay.
                            assertCapture(states.get(row), module);
                            assertArrayEquals(pictures.get(row).pixels(), render(module, true).pixels(), "flip/cut/row " + flip + "/" + cut + "/" + row);
                            assertCapture(states.get(row), module);
                        }
                    }
                }
                // Exhaustive retirement witness, using only actual current policy versions in the owned range.
                var beforeRetirement = capture(module);
                var retirementPolicy = monitorWorldPolicy(module, objects, banks); logical = base;
                for (int span = 0; span < counts.length; span++) for (int tile = 0; tile < counts[span]; tile++) {
                    var sample = retirementPolicy.resolve(nativeFirst[span] + tile); assertEquals(logical++, sample.logicalPatternId());
                    var v = sample.version(); long[] words = {v.a(), v.b(), v.c(), v.d()}; var pattern = new com.openggf.level.Pattern();
                    for (int at = 0; at < 64; at++) pattern.setPixel(at % 8, at / 8, (byte) ((words[at / 16] >>> ((at % 16) * 4)) & 15));
                    GameServices.graphics().cachePatternTexture(pattern, sample.logicalPatternId());
                }
                assertEquals(base + 513, logical); assertCapture(beforeRetirement, module);
                for (int tile = 0; tile < 513; tile++) {
                    var entry = atlas.getEntry(base + tile); assertNotNull(entry, "Installed retirement witness " + tile);
                    assertTrue(glIsTexture(atlas.getTextureId(entry.atlasIndex())));
                }
                var sharedEntries = new java.util.HashMap<Integer, Object>();
                // PatternAtlas.validatePatternIdGovernance admits native 11-bit IDs implicitly;
                // registeredRangesForTesting lists explicit virtual ranges, not all resident entries.
                for (int id = 0; id <= 0x7FF; id++) {
                    var entry = atlas.getEntry(id); if (entry != null) sharedEntries.put(id, entry);
                }
                for (var range : atlas.registeredRangesForTesting()) for (int id = range.base(); id < range.base() + range.size(); id++) {
                    if (id >= base && id < base + 513) continue;
                    var entry = atlas.getEntry(id); if (entry != null) sharedEntries.put(id, entry);
                }
                // CPU preloaded art is not an eager GPU-entry count: retain EVERY actual shared entry.
                assertFalse(sharedEntries.isEmpty(), "Actual shared producer entries must exist");
                assertTrue(sharedEntries.keySet().stream().anyMatch(id -> id <= 0x7FF),
                        "Actual native level entries are preserved without adding a synthetic producer");
                observations.append("shared-installed=").append(sharedEntries.size()).append('\n');
                module.closeTerrainPlayer();
                assertTrue(runtime.closed()); assertTrue(frame.closed()); assertSame(atlas, GameServices.graphics().getPatternAtlas());
                for (int tile = 0; tile < 513; tile++) assertNull(atlas.getEntry(base + tile), "Retired owned ID " + (base + tile));
                sharedEntries.forEach((id, entry) -> assertSame(entry, atlas.getEntry(id), "Shared other-producer entry " + id));
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
            } finally {
                try { SessionManager.closeGameplaySession(); }
                finally { GraphicsManager.destroyForReinit(); }
            }
        }
        if (output != null) Files.writeString(output.resolve("outcome.txt"),
                "PASS: constructed ID1B Init/DrawObjects/next-Main actual shared-Solid1-to0 witness, all four native flips/two exact SAT pieces,19 current decoded tile versions/full geometry current-palette opaque + transparent terrain pixels, visible-tile/piece omission negatives, every selected full/course state+pixel restore and forward replay, all513 actual current-policy retirement witnesses installed then absent and shared producers retained; session/graphics/boot cleanup completed.\n"
                        + construction + observations, StandardOpenOption.CREATE_NEW); // PASS only AFTER retirement and finally/boot cleanup.
    }

    /**
     * Neutral, unsupported-by-an-object fixture only. MoveGround.BalanceGround (Sonic/Main.asm:905-938)
     * emits a movement stop for floor distance >=12; unless either previous angle equals3 it THEN reaches
     * CheckCharge/CheckUnleashPeelout (:1029-1036), which emits a separate charge stop even at charge0.
     * The older Monitor fixture's either/or oracle never exercised this two-request branch.
     */
    private static void boulderWorldStep(GameLoop loop, Bk2FrameInput input, SonicCdGameModule module) {
        var frame = module.requirePlayerFrame(); var state = frame.state(); var m = state.motion();
        var e = frame.environment();
        assertEquals(0, e.movement().globals().lookMode()); assertEquals(0, e.movement().gates().speedShoes());
        assertEquals(0, state.objectLinks().standObject()); assertEquals(0, state.objectLinks().pushObject());
        var expectedPlayer = new ArrayList<>(monitorWorldPlayerSounds(frame));
        if ((m.flags() & 6) == 0 && ((m.angle() + 0x20) & 0xC0) == 0 && m.groundVelocity() == 0) {
            var floor = new com.openggf.game.soniccd.player.CdPlayerTerrain(frame.assets().terrain())
                    .floor(m.centreX16_16() >>> 16, (m.centreY16_16() >>> 16) + m.yRadius(), 13, 0, 16, m.spriteFlags());
            if (floor.distance() >= 12 && m.previousPrimaryAngle() != 3 && m.previousSecondaryAngle() != 3)
                expectedPlayer.add(new CdPlayerFrameRuntime.ChargeSound(
                        com.openggf.game.soniccd.player.CdPlayerCharge.Sound.FM_CHARGESTOP));
        }
        var expected = new ArrayList<CdOpeningObjectRuntime.SoundRequest>();
        expectedPlayer.forEach(sound -> expected.add(new CdOpeningObjectRuntime.PlayerSound(sound)));
        step(loop, input);
        var runtime = (CdOpeningObjectRuntime) module.getGameService(NativeDynamicObjectPass.class);
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase());
        assertEquals(expectedPlayer, module.requirePlayerActor().lastSoundRequests(), "Complete neutral Body prefix including both reached stop calls");
        assertEquals(expected, runtime.lastSoundRequests(), "Boulder GetOff with no stand/push link adds no direct sound");
    }

    /** Explicit initial-only actor ingress; no global, buffer, art, clock, source or helper ingress. */
    private static void stageBoulderWorldPlayer(SonicCdGameModule module, int x, int y) {
        var runtime = (CdOpeningObjectRuntime) module.getGameService(NativeDynamicObjectPass.class);
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase());
        module.requirePlayerActor().requireCompletedNativeFrame();
        var frame = module.requirePlayerFrame(); var actor = frame.captureActor(); var old = actor.state(); var m = old.motion();
        assertEquals(2, m.routine());
        var motion = new com.openggf.game.soniccd.player.CdPlayerMotionState(x << 16, y << 16, 0, 0, 0,
                2, 0, 0, 4, 9, 19, 0, m.previousAnimation(), 0, 0, 0, 0, 0, m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var state = new com.openggf.game.soniccd.player.CdPlayerBody.State(motion, old.animation(), old.header(), old.timers(),
                old.history(), old.rotationDistance(), old.objectLinks());
        frame.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), state, java.util.Optional.empty()));
        module.requirePlayerActor().rebindAfterRestore();
        assertSame(old.animation(), frame.state().animation()); assertSame(old.header(), frame.state().header());
        assertSame(old.timers(), frame.state().timers()); assertSame(old.history(), frame.state().history());
        assertEquals(old.rotationDistance(), frame.state().rotationDistance()); assertEquals(old.objectLinks(), frame.state().objectLinks());
    }

    private static void assertBoulderWorldSat(CdObjectDrawQueue.CpuSat sat, int cameraX, int cameraY, int x, int y, int flip) {
        // N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Boulder/Data/Mappings.asm:1-7;
        // DrawObjects/Object Functions.asm performs ADD.W then flip XOR, not art flips or priority promotion.
        int[][] pieces = {{(flip & 2) != 0 ? -16 : -8, 2, 0x374 ^ (flip << 11), (flip & 1) != 0 ? 12 : -20},
                {-16, 15, 0x377 ^ (flip << 11), (flip & 1) != 0 ? -20 : -12}};
        int seen = 0; byte[] raw = sat.bytes();
        assertTrue(sat.provenance().stream().anyMatch(p -> p.slotRef().wholeIndex() == 0));
        for (int entry = 0; entry < sat.spriteCount(); entry++) {
            var p = sat.provenance().get(entry); if (p.slotRef().wholeIndex() != 40) continue;
            assertTrue(seen < 2); assertEquals(4, p.queueBucket()); assertEquals(0, p.queueOrdinal());
            assertEquals(seen, p.mappingPieceOrdinal()); var literal = pieces[seen++]; int at = entry * 8;
            assertEquals((y - cameraY + 128 + literal[0]) & 0xFFFF, ((raw[at] & 255) << 8) | (raw[at + 1] & 255));
            assertEquals(literal[1], raw[at + 2] & 255); assertEquals(entry + 1, raw[at + 3] & 255);
            int word = ((raw[at + 4] & 255) << 8) | (raw[at + 5] & 255);
            assertEquals(literal[2], word); assertEquals(0, word & 0xE000, "Palette0/hardware priority0 despite software bucket4");
            assertEquals((x - cameraX + 128 + literal[3]) & 511, ((raw[at + 6] & 255) << 8) | (raw[at + 7] & 255));
        }
        assertEquals(2, seen);
    }

    private record BoulderWorldTile(int offset, int x, int y, boolean hFlip, boolean vFlip, int piece) { }
    private static List<BoulderWorldTile> boulderWorldLiteralTiles(int x, int y, int flip) {
        boolean h = (flip & 1) != 0, v = (flip & 2) != 0; var result = new ArrayList<BoulderWorldTile>();
        // Converter visits native SAT in reverse: piece1 (32x32, offsets3..18) then piece0 (8x24, offsets0..2).
        for (int row = 0; row < 4; row++) for (int col = 0; col < 4; col++) result.add(new BoulderWorldTile(
                3 + col * 4 + row, x + (h ? -20 : -12) + (h ? 3 - col : col) * 8,
                y - 16 + (v ? 3 - row : row) * 8, h, v, 1));
        for (int row = 0; row < 3; row++) result.add(new BoulderWorldTile(row, x + (h ? 12 : -20),
                y + (v ? -16 : -8) + (v ? 2 - row : row) * 8, h, v, 0));
        return List.copyOf(result);
    }

    private static String assertBoulderWorldPixels(com.openggf.graphics.SpritePresentation.Frame picture, RgbaImage image,
            RgbaImage terrain, com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State residency,
            int base, List<BoulderWorldTile> literal, int centreX, int centreY) {
        var tiles = picture.tiles().stream().filter(t -> t.patternId() >= base && t.patternId() < base + 19).toList();
        assertEquals(19, tiles.size()); assertEquals(19, literal.size());
        var graphics = GameServices.graphics(); var level = GameServices.level();
        assertFalse(graphics.isWaterEnabled());
        assertFalse(graphics.getPaletteFadePresentation().affects(0));
        var palette = level.getCurrentLevel().getPalette(0); int[] argb = new int[16];
        for (int i = 1; i < 16; i++) {
            var colour = palette.getColor(i);
            argb[i] = 0xFF000000 | com.openggf.graphics.color.DisplayColorConverter.toPackedRgb(colour.r & 255, colour.g & 255,
                    colour.b & 255, graphics.getDisplayColorProfile(), graphics.getDisplayColorExpansion());
        }
        var expected = terrain.pixels().clone(); int opaque = 0, transparent = 0, changed = 0;
        var zeroOpacity = new ArrayList<Integer>(); var reached = new java.util.TreeSet<Integer>();
        for (int index = 0; index < 19; index++) {
            var p = literal.get(index); var t = tiles.get(index); reached.add(p.offset());
            assertEquals(base + p.offset(), t.patternId()); assertEquals((float) p.x(), t.x()); assertEquals((float) p.y(), t.y());
            assertEquals(8f, t.width()); assertEquals(8f, t.height()); assertEquals(0, t.palette());
            assertEquals(p.hFlip(), t.hFlip()); assertEquals(p.vFlip(), t.vFlip()); assertFalse(t.priority());
            assertEquals(com.openggf.graphics.SpritePresentation.Layer.OBJECT, t.layer()); assertTrue(t.priorityShader());
            assertEquals(0, t.occlusionMask()); assertFalse(t.ghost()); assertFalse(t.ghostColour()); assertEquals(1f, t.ghostAlpha());
            assertEquals(0, t.rowStart()); assertEquals(8, t.rowEnd()); assertFalse(t.rowClipped());
            assertEquals(com.openggf.graphics.SpritePresentation.Subject.WORLD, t.subject());
            var bytes = com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(residency, 0x374 + p.offset());
            var decoded = new com.openggf.level.Pattern(); decoded.fromSegaFormat(bytes.copy());
            var version = picture.patternVersions().get(base + p.offset()); assertNotNull(version);
            long[] words = {version.a(), version.b(), version.c(), version.d()}; int tileOpaque = 0;
            for (int pixel = 0; pixel < 64; pixel++) assertEquals(decoded.getPixel(pixel % 8, pixel / 8) & 15,
                    (int) ((words[pixel / 16] >>> ((pixel % 16) * 4)) & 15), "Actual current tile nibble " + p.offset() + ":" + pixel);
            for (int dy = 0; dy < 8; dy++) for (int dx = 0; dx < 8; dx++) {
                int px = p.x() + dx, py = p.y() + dy; assertTrue(px >= 0 && px < 320 && py >= 0 && py < 224);
                int value = decoded.getPixel(p.hFlip() ? 7 - dx : dx, p.vFlip() ? 7 - dy : dy) & 15, at = py * 320 + px;
                if (value == 0) transparent++; else {
                    opaque++; tileOpaque++; expected[at] = argb[value];
                    if ((argb[value] & 0xFFFFFF) != (terrain.pixels()[at] & 0xFFFFFF)) changed++;
                }
            }
            if (tileOpaque == 0) zeroOpacity.add(p.offset()); // Real all-zero source tiles remain transparent, never invented witnesses.
        }
        assertEquals(java.util.stream.IntStream.range(0, 19).boxed().collect(java.util.stream.Collectors.toSet()), reached);
        for (var other : picture.tiles()) {
            if (other.patternId() >= base && other.patternId() < base + 19) continue;
            assertTrue(other.x() + other.width() <= centreX - 20 || other.x() >= centreX + 20
                    || other.y() + other.height() <= centreY - 16 || other.y() >= centreY + 16, "No other producer overlaps COMPLETE40x32 geometry");
        }
        // Include holes beside the narrow piece; collision width16 is NEVER a picture-clearance approximation.
        for (int y = centreY - 16; y < centreY + 16; y++) for (int x = centreX - 20; x < centreX + 20; x++)
            assertEquals(expected[y * 320 + x], image.pixels()[y * 320 + x], "Current-palette/transparent native-plane geometry " + x + ":" + y);
        assertTrue(opaque > 0); assertTrue(transparent > 0); assertEquals(19 * 64, opaque + transparent); assertTrue(changed > 0);
        return "opaque=" + opaque + " transparent=" + transparent + " changed=" + changed + " zero-opacity-offsets=" + zeroOpacity;
    }

    private static void assertBoulderWorldOmissions(com.openggf.graphics.SpritePresentation.Frame picture, RgbaImage image,
            RgbaImage terrain, com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State residency,
            int base, List<BoulderWorldTile> literal, int centreX, int centreY) {
        int visibleTiles = 0, visiblePieces = 0;
        for (int index = 0; index < literal.size() + 2; index++) {
            boolean wholePiece = index >= literal.size(); int selector = wholePiece ? index - literal.size() : index;
            var partial = image.copy(); int removed = 0, retained = 0, sourceOpaque = 0;
            for (int tile = 0; tile < literal.size(); tile++) {
                var p = literal.get(tile); boolean omit = wholePiece ? p.piece() == selector : tile == selector;
                var decoded = new com.openggf.level.Pattern();
                decoded.fromSegaFormat(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(residency, 0x374 + p.offset()).copy());
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                    int at = (p.y() + y) * 320 + p.x() + x;
                    boolean changed = (image.pixels()[at] & 0xFFFFFF) != (terrain.pixels()[at] & 0xFFFFFF);
                    if (omit) {
                        if ((decoded.getPixel(p.hFlip() ? 7 - x : x, p.vFlip() ? 7 - y : y) & 15) != 0) sourceOpaque++;
                        if (changed) removed++; partial.pixels()[at] = terrain.pixels()[at];
                    } else if (changed) retained++;
                }
            }
            if (sourceOpaque == 0) { assertEquals(0, removed); continue; } // No fabricated opacity for empty source bytes.
            assertTrue(removed > 0, "A nonempty source tile/piece needs an actual visible omission witness: " + index);
            if (wholePiece) visiblePieces++; else { visibleTiles++; assertTrue(retained > 0, "Partial geometry must remain visible"); }
            assertThrows(AssertionError.class, () -> assertBoulderWorldPixels(picture, partial, terrain, residency, base, literal, centreX, centreY),
                    "Every omitted visible native tile/piece must fail while source-transparent tiles remain terrain");
        }
        assertTrue(visibleTiles > 1); assertTrue(visiblePieces > 0);
    }
    // END constructed Boulder World proof.

    // BEGIN constructed diagonal Spring World proof. Native pin1ecc5f3af; source-only authoring, execution is Root-owned.
    @Test void actualEglConstructedDiagonalSpringWorldLaunchesAnimatesReplaysAndRetires559Patterns() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual EGL/media opt-in");
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        String media = System.getProperty("soniccd.media.root"); assertNotNull(media); assertFalse(media.isBlank());
        CdGameDataSource source;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); source = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
        String log = InputScriptCompiler.compileToInputLog("14 -");
        var movie = new Bk2MovieLoader().parseInputLogText(Path.of("ENGINE-constructed-diagonal-spring-world-input"), log.lines().toList());
        Path output = openCaptureOutput(log, "soniccd.diagonal.spring.output");
        String construction = "ENGINE constructed actual-USA R11A primary diagonal Spring component; no emulator, natural-route or full-game claim.\n"
                + "Row0 runs actual Sonic Init. Restore that same valid World cut before each of eight independent scenes (subtypes08/0A x native H/V0..3).\n"
                + "At each initial CLOSED_CUT ONLY clear ALL96 dynamics, seed cleared slot40 ONLY with ID0A/X/Y/subtype08 or0A/subtype2zero, and set BOTH native placement sprite/status low flip bits. No saved/helper/routine/frame/tile/map/timer/bit7 ingress.\n"
                + "Spring X=initial cameraX+160; Y=initial cameraY+56 for every orientation, with actual two-foot floor-clearance checks before the first Body. Same primary Sonic is restored/rebound ONCE at SpringX+(H?-32:32), SpringY-32, fractionsABCD/1234; XV/YV/GVel0, routine2, angle0, flags2, sprite4, radii9/19, animation0, jump/charge/moveLock/stick/control0. PreviousAnimation/previous angles, animation state, header, timers, history, rotation, links and owner retained; only actor display publication cleared. No repeated ingress.\n"
                + "Row1 Body integrates zero speed then adds38 gravity; real Spring Init returns without Solid/launch/animation, and real DrawObjects supplies bit7. Row2 Body integrates oldYV38, fraction1234->4A34; real top Solid at vertical penetration1 < horizontal2 aligns Y to SpringY-31, sets stand40/angle0/jump0/GVel0. Launch THEN moves post-Solid X by H?+8:-8 and Y by V?-8:+8, preserves fractions, writes strong2896 or weak1810 signed H/V velocities, retains the post-Solid stand link/GVel/angle/radii. Whole-frame position is not a delta from pre-Body.\n"
                + "Rows2..11 consume actual delay0 tokens0,1,1,2,2,2,2,2,2,1; row12 FC leaves frame1/index10 and selects24; row13 separate Reset selects20/frame0/previous1. No animator/helper/Body/Draw rerun, no fake Solid return, no sound effect replay.\n"
                + "Every row checks current CPU SAT separately from PRIOR CPU SAT now displayed with NOW-serviced player/powerup/current residency and prior camera. Player Body/Draw runs before Spring; no retroactive player presentation is invented. Early rows2..6 compare full native7/7/8-piece opaque+transparent current-palette geometry against real native Plane B controls. A separate clearly TEST-only contrast clear background outside the current palette makes source-opaque tile/piece omissions observable even when a tile matches native terrain RGB; no authoritative glyph/art/palette changes. Reach all46 current decoded tile versions.\n"
                + "Selected preInit/postInit/postLaunch/frame2/FC/Reset full AND course cuts restore complete state/pixels and forward through row13 WITHOUT construction, reseed, rebind or injected effects.\n"
                + "Only final retirement installs all559 actual CURRENT policy versions in already-owned IDs; require all present, close, then all absent. Inventory existing native0..7FF plus explicit virtual ranges outside owned559 and preserve actual other-producer entry identities; never register a synthetic producer. PASS is written only after retirement and session/graphics/boot finally cleanup.\n"
                + "Physical9200..97C0 uses ONE current AT_9000 authority, DEV46 cold writes only; later BigRing9100..9E40 replaces all46 rather than selecting art by actor. No native cold-PLC history, mixed-priority/palette/timing/pixel parity or restart-flow certification.\n";
        if (output != null) Files.writeString(output.resolve("construction.txt"), construction, StandardOpenOption.CREATE_NEW);
        var observations = new StringBuilder();
        int[] cpuFrames = {0, 0, 0, 1, 1, 2, 2, 2, 2, 2, 2, 1, 1, 0};
        int[] nativeFirst = {0x780, 0x7AE, 0x3A0, 0x424, 0x441, 0x680, 0x6C6, 0x6D7, 0x544, 0x334, 0x520, 0x328, 0x5A8, 0x374, 0x490};
        int[] counts = {24, 14, 70, 28, 63, 70, 5, 39, 36, 15, 36, 12, 82, 19, 46};
        try (var boot = new HeadlessGameBoot(320, 224)) {
            try {
                var loop = boot.bootSonicCdOpeningObjects(source);
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
                var mode = SessionManager.getCurrentGameplayMode();
                var module = assertInstanceOf(SonicCdGameModule.class, mode.getWorldSession().getGameModule());
                var runtime = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                var frame = module.requirePlayerFrame();
                assertSame(source, mode.getWorldSession().getDataSource()); assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
                step(loop, movie.getFrame(0));
                int baseId = com.openggf.graphics.PatternAtlasRange.OBJECTS.base();
                var banks = new CdOpeningObjectRuntime.LogicalBanks(baseId, baseId + 24, baseId + 38);
                assertEquals(baseId + 513, banks.diagonalSpring()); assertEquals(banks.boulder() + 19, banks.diagonalSpring());
                var objects = source.assets().openingObjects(); var sources = objects.sectionArtSources();
                assertEquals(10, sources.sources().size());
                var diagonal = sources.source(com.openggf.game.soniccd.level.CdR11aAsset.ART_DIAGONAL_SPRING);
                assertEquals(1, diagonal.canonicalPlcId()); assertEquals(5, diagonal.canonicalRowIndex());
                assertEquals(0x9200, diagonal.canonicalPlc().vramAddress()); assertEquals(46, diagonal.tileCount());
                assertEquals(0x002E, diagonal.readerBytes().unsignedWord(0)); assertEquals(1472, diagonal.decode().bytes().length());
                assertEquals(0x490, objects.tileWord(0xF, 0));
                assertEquals(80, objects.springDiagonal().mappingTableOffset()); assertEquals(3, objects.springDiagonal().mappingFrames().size());
                for (int tile = 0; tile < 46; tile++) assertArrayEquals(diagonal.decode().bytes().slice(tile * 32, 32).copy(),
                        com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(runtime.sectionArtState().residency(), 0x490 + tile).copy(),
                        "Actual decoded source equals explicit DEV cold current tile " + tile);
                var atlas = GameServices.graphics().getPatternAtlas(); var initialPolicy = monitorWorldPolicy(module, objects, banks);
                int logical = baseId;
                for (int span = 0; span < counts.length; span++) for (int tile = 0; tile < counts[span]; tile++) {
                    int id = logical++; assertEquals(id, initialPolicy.resolve(nativeFirst[span] + tile).logicalPatternId());
                    assertEquals(1, atlas.registeredRangesForTesting().stream()
                            .filter(range -> id >= range.base() && id < range.base() + range.size()).count());
                }
                assertEquals(baseId + 559, logical, "Fifteen final logical spans; one physical current reader per native tile");
                var registry = mode.getRewindRegistry(); var pristine = registry.capture(); var pool = runtime.placement().pool();
                for (boolean weak : List.of(false, true)) for (int flip = 0; flip < 4; flip++) {
                    registry.restore(pristine);
                    assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase());
                    pool.clearDynamicSlots(); assertEquals(96, pool.capture().slots().size());
                    assertTrue(pool.capture().slots().stream().allMatch(s -> Arrays.equals(new byte[64], s.copyBytes())));
                    int springX = GameServices.camera().getX() + 160;
                    int springY = GameServices.camera().getY() + 56; // Airborne component cut; old H scene at+128 penetrated real terrain.
                    int playerX = springX + ((flip & 1) != 0 ? -32 : 32);
                    pool.write(pool.state(40).moveByte(ID, 0x0A).moveWord(X, springX).moveWord(Y, springY)
                            .moveByte(SUBTYPE, weak ? 0x0A : 0x08).moveByte(SUBTYPE2, 0)
                            .moveByte(SPRITE_FLAGS, flip).moveByte(FLAGS, flip)); // Actual placement supplies BOTH flip bytes.
                    stageDiagonalWorldPlayer(module, (playerX << 16) | 0xABCD, ((springY - 32) << 16) | 0x1234);
                    var terrainProbe = new com.openggf.game.soniccd.player.CdPlayerTerrain(frame.assets().terrain());
                    for (int foot : new int[]{playerX + 9, playerX - 9})
                        assertTrue(terrainProbe.floor(foot & 0xFFFF, (springY - 32 + 19) & 0xFFFF,
                                13, 0, 16, 4).distance() >= 0, "Initial air cut must not land before Spring Init; weak/flip=" + weak + "/" + flip);
                    assertEquals(0, pool.state(40).routine()); assertEquals(flip, pool.state(40).spriteFlags());
                    assertEquals(flip, pool.state(40).flags()); assertEquals(0, pool.state(40).unsignedByte(ANIMATION_TIME));
                    var fullCuts = new ArrayList<>(List.of(registry.capture()));
                    var courseCuts = new ArrayList<>(List.of(registry.captureCourse()));
                    var states = new ArrayList<>(List.of(capture(module)));
                    var pictures = new ArrayList<>(List.of(render(module, true))); assertCapture(states.getFirst(), module);
                    var reached = new java.util.TreeSet<Integer>();
                    var phasePictures = new RgbaImage[3];
                    var previousCpu = runtime.cpuSat();
                    int previousCameraX = GameServices.camera().getX(), previousCameraY = GameServices.camera().getY();
                    for (int row = 1; row <= 13; row++) {
                        long clock = frame.vIntClock().absoluteValue().orElseThrow(); int levelFrames = GameServices.level().getFrameCounter();
                        diagonalWorldStep(loop, movie.getFrame(row), module, row == 2);
                        assertEquals(clock + 1, frame.vIntClock().absoluteValue().orElseThrow(), "Actual World V-int, not an animator clock");
                        assertEquals(levelFrames + 1, GameServices.level().getFrameCounter());
                        var live = pool.state(40); var m = frame.state().motion();
                        assertEquals(0x0A, live.id()); assertEquals(row == 1 || row == 13 ? 20 : row == 12 ? 24 : 22, live.routine());
                        assertEquals(0x209BF0, live.longBits(MAPPINGS)); assertEquals(weak ? 0x2490 : 0x0490, live.word(TILE));
                        assertEquals(24, live.unsignedByte(WIDTH)); assertEquals(12, live.unsignedByte(Y_RADIUS));
                        assertEquals(4, live.unsignedByte(PRIORITY)); assertEquals(springX, live.word(SPRING_ANCHOR_X));
                        assertEquals(weak ? 0xF600 : 0xF000, live.word(SPRING_SPEED));
                        assertEquals(0, live.word(SPRING_PARENT)); assertEquals(springX, live.centreX()); assertEquals(springY, live.centreY());
                        assertEquals(0x84 | flip, live.spriteFlags(), "Bit7 is from actual DrawObjects; no ingress setter");
                        assertEquals(flip, live.flags()); assertEquals(0, live.unsignedByte(ROUTINE_2));
                        assertEquals(cpuFrames[row], live.unsignedByte(MAP_FRAME));
                        assertEquals(row == 1 ? 0 : Math.min(row - 1, 10), live.unsignedByte(ANIMATION_FRAME));
                        assertEquals(0, live.unsignedByte(ANIMATION_TIME), "Delay0 reloads BEFORE the token, including FC");
                        assertEquals(row == 13 ? 1 : 0, live.unsignedByte(PREVIOUS_ANIMATION));
                        if (row == 1) {
                            assertEquals((playerX << 16) | 0xABCD, m.centreX16_16());
                            assertEquals(((springY - 32) << 16) | 0x1234, m.centreY16_16(), "Airborne Init row, weak/flip=" + weak + "/" + flip);
                            assertEquals(0x38, m.yVelocity()); assertEquals(0, frame.state().objectLinks().standObject());
                        }
                        if (row == 2) {
                            int magnitude = weak ? 1810 : 2896;
                            // N Spring/Main.asm:430-465 AFTER Object Solid.asm:335-429, not a pre-Body position delta.
                            assertEquals(((playerX + ((flip & 1) != 0 ? 8 : -8)) << 16) | 0xABCD, m.centreX16_16());
                            assertEquals(((springY - 31 + ((flip & 2) != 0 ? -8 : 8)) << 16) | 0x4A34, m.centreY16_16());
                            assertEquals((flip & 1) != 0 ? -magnitude : magnitude, m.xVelocity());
                            assertEquals((flip & 2) != 0 ? magnitude : -magnitude, m.yVelocity());
                            assertEquals(2 | (flip & 1), m.flags()); assertEquals(40, frame.state().objectLinks().standObject());
                            assertEquals(0, frame.state().objectLinks().pushObject());
                            assertEquals(0, m.groundVelocity()); assertEquals(0, m.angle()); assertEquals(0, m.playerJump());
                            assertEquals(9, m.xRadius()); assertEquals(19, m.yRadius());
                            assertEquals(0, m.moveLock()); assertEquals(0, m.charge()); assertEquals(0, m.playerControl());
                        }
                        assertEquals(2, m.routine()); assertEquals(0, frame.environment().globals().debugMode());
                        assertTrue(pool.capture().slots().stream().allMatch(s -> s.id() == 0 || s.identity().wholeIndex() == 40),
                                "Bounded scene retains only the constructed Spring; spawner breadth is not silently ignored");
                        assertDiagonalWorldSat(runtime.cpuSat(), GameServices.camera().getX(), GameServices.camera().getY(),
                                springX, springY, flip, weak, cpuFrames[row]);
                        var completed = capture(module); var published = completed.publication().published();
                        assertEquals(previousCameraX, published.cameraX()); assertEquals(previousCameraY, published.cameraY());
                        assertEquals(com.openggf.game.soniccd.resources.CdObjectPresentation.convert(previousCpu,
                                monitorWorldPolicy(module, objects, banks)), runtime.displayedFrame(),
                                "Prior CPU SAT and NOW-serviced current art; no conversion rewrites the completed player Body");
                        if (row >= 2) assertDiagonalWorldSat(previousCpu, previousCameraX, previousCameraY,
                                springX, springY, flip, weak, cpuFrames[row - 1]);
                        var image = render(module, true); var terrain = renderTerrain(module, published);
                        assertNativeGpuBackground(module); assertTerrainOutsideSprites(image, terrain, runtime.displayedFrame());
                        if (row >= 2 && row <= 6) {
                            int sourceFrame = cpuFrames[row - 1];
                            var literal = diagonalWorldLiteralTiles(springX - published.cameraX(), springY - published.cameraY(), flip, sourceFrame);
                            observations.append("weak=").append(weak).append(" flip=").append(flip).append(" row=").append(row)
                                    .append(" clock=").append(frame.vIntClock().absoluteValue().orElseThrow()).append(' ')
                                    .append(assertDiagonalWorldPixels(runtime.displayedFrame(), image, terrain,
                                            runtime.sectionArtState().residency(), banks.diagonalSpring(), literal, weak ? 1 : 0)).append('\n');
                            // A real renderer-only isolation control proves the entire glyph even where Sonic covers it.
                            // No actor/art/clock/SAT/publication mutation or extra player ingress occurs here.
                            var springOnly=diagonalWorldSpringOnly(runtime.displayedFrame(),banks.diagonalSpring());
                            var isolated=renderDiagonalWorldPicture(module,springOnly,null);
                            assertDiagonalWorldPixels(springOnly,isolated,terrain,runtime.sectionArtState().residency(),
                                    banks.diagonalSpring(),literal,weak ? 1 : 0);
                            // A source-opaque tile may equal real terrain RGB. Make every opaque omission observable
                            // on a clearly TEST-only contrast backdrop; keep BOTH actual native-plane checks above.
                            int contrastRgb=diagonalWorldContrastRgb(weak ? 1 : 0);
                            var contrast=renderDiagonalWorldPicture(module,com.openggf.graphics.SpritePresentation.Frame.empty(),contrastRgb);
                            var contrastSpring=renderDiagonalWorldPicture(module,springOnly,contrastRgb);
                            assertDiagonalWorldPixels(springOnly,contrastSpring,contrast,runtime.sectionArtState().residency(),
                                    banks.diagonalSpring(),literal,weak ? 1 : 0);
                            assertDiagonalWorldOmissions(springOnly, contrastSpring, contrast, runtime.sectionArtState().residency(),
                                    banks.diagonalSpring(), literal, weak ? 1 : 0);
                            assertArrayEquals(image.pixels(),render(module,true).pixels(),"Render control leaves the actual World picture unchanged");
                            for (var tile : literal) {
                                reached.add(tile.offset()); var entry = atlas.getEntry(banks.diagonalSpring() + tile.offset());
                                assertNotNull(entry); assertTrue(glIsTexture(atlas.getTextureId(entry.atlasIndex())));
                                assertEquals(runtime.displayedFrame().patternVersions().get(banks.diagonalSpring() + tile.offset()),
                                        com.openggf.graphics.SpritePresentation.patternSample(GameServices.graphics(), banks.diagonalSpring() + tile.offset()));
                            }
                            var withoutPlane = renderWithoutBackground(module);
                            assertFalse(Arrays.equals(terrain.pixels(), withoutPlane.pixels()), "Native Plane B contributes to the transparent control");
                            assertArrayEquals(image.pixels(), render(module, true).pixels());
                            if (phasePictures[sourceFrame] == null) {
                                phasePictures[sourceFrame] = image;
                                if (output != null) {
                                    ScreenshotCapture.savePNG(image, output.resolve("ENGINE-constructed-diagonal-weak" + weak + "-flip" + flip + "-frame" + sourceFrame + ".png"));
                                    ScreenshotCapture.savePNG(terrain, output.resolve("ENGINE-native-plane-diagonal-weak" + weak + "-flip" + flip + "-frame" + sourceFrame + ".png"));
                                }
                            }
                        }
                        assertCapture(completed, module); states.add(completed); pictures.add(image);
                        fullCuts.add(registry.capture()); courseCuts.add(registry.captureCourse());
                        previousCpu = runtime.cpuSat(); previousCameraX = GameServices.camera().getX(); previousCameraY = GameServices.camera().getY();
                    }
                    assertEquals(java.util.stream.IntStream.range(0, 46).boxed().collect(java.util.stream.Collectors.toSet()), reached);
                    assertFalse(Arrays.equals(phasePictures[0].pixels(), phasePictures[1].pixels()));
                    assertFalse(Arrays.equals(phasePictures[1].pixels(), phasePictures[2].pixels()));
                    for (int cut : new int[]{0, 1, 2, 5, 12, 13}) for (boolean courseOnly : List.of(false, true)) {
                        if (courseOnly) registry.restoreCourse(courseCuts.get(cut)); else registry.restore(fullCuts.get(cut));
                        assertCapture(states.get(cut), module); assertArrayEquals(pictures.get(cut).pixels(), render(module, true).pixels());
                        assertCapture(states.get(cut), module);
                        for (int row = cut + 1; row <= 13; row++) {
                            diagonalWorldStep(loop, movie.getFrame(row), module, row == 2); // No seed, reconstruction, staging or effect replay.
                            assertCapture(states.get(row), module);
                            assertArrayEquals(pictures.get(row).pixels(), render(module, true).pixels(), "weak/flip/cut/row " + weak + "/" + flip + "/" + cut + "/" + row);
                            assertCapture(states.get(row), module);
                        }
                    }
                }
                // Teardown-only materialization: actual NOW policy, never a claim that all559 tiles rendered.
                var beforeRetirement = capture(module); var retirementPolicy = monitorWorldPolicy(module, objects, banks); logical = baseId;
                for (int span = 0; span < counts.length; span++) for (int tile = 0; tile < counts[span]; tile++) {
                    var sample = retirementPolicy.resolve(nativeFirst[span] + tile); assertEquals(logical++, sample.logicalPatternId());
                    var v = sample.version(); long[] words = {v.a(), v.b(), v.c(), v.d()}; var pattern = new com.openggf.level.Pattern();
                    for (int at = 0; at < 64; at++) pattern.setPixel(at % 8, at / 8, (byte) ((words[at / 16] >>> ((at % 16) * 4)) & 15));
                    GameServices.graphics().cachePatternTexture(pattern, sample.logicalPatternId());
                }
                assertEquals(baseId + 559, logical); assertCapture(beforeRetirement, module);
                for (int tile = 0; tile < 559; tile++) {
                    var entry = atlas.getEntry(baseId + tile); assertNotNull(entry, "Installed actual retirement witness " + tile);
                    assertTrue(glIsTexture(atlas.getTextureId(entry.atlasIndex())));
                }
                var sharedEntries = new java.util.HashMap<Integer, Object>();
                for (int id = 0; id <= 0x7FF; id++) {
                    var entry = atlas.getEntry(id); if (entry != null) sharedEntries.put(id, entry);
                }
                for (var range : atlas.registeredRangesForTesting()) for (int id = range.base(); id < range.base() + range.size(); id++) {
                    if (id >= baseId && id < baseId + 559) continue;
                    var entry = atlas.getEntry(id); if (entry != null) sharedEntries.put(id, entry);
                }
                assertFalse(sharedEntries.isEmpty()); assertTrue(sharedEntries.keySet().stream().anyMatch(id -> id <= 0x7FF));
                observations.append("shared-installed=").append(sharedEntries.size()).append('\n');
                module.closeTerrainPlayer(); assertTrue(runtime.closed()); assertTrue(frame.closed());
                assertSame(atlas, GameServices.graphics().getPatternAtlas());
                for (int tile = 0; tile < 559; tile++) assertNull(atlas.getEntry(baseId + tile), "Retired owned current ID " + (baseId + tile));
                sharedEntries.forEach((id, entry) -> assertSame(entry, atlas.getEntry(id), "Actual other-producer identity " + id));
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
            } finally {
                try { SessionManager.closeGameplaySession(); }
                finally { GraphicsManager.destroyForReinit(); }
            }
        }
        if (output != null) Files.writeString(output.resolve("outcome.txt"),
                "PASS: ENGINE constructed actual-USA primary diagonal Spring component, both strengths/all4 native H/V; real Init RTS/next-Main shared top Solid/diagonal launch and same-call FM/animation; source tokens/FC/separate Reset, prior-SAT+NOW current-art display, all3 source frames/literal7,7,8 pieces/all46 current decoded versions, ordered player/Spring World pixels plus renderer-only full Spring isolation/native Plane B transparency plus explicitly TEST-only contrast omission controls; selected full/course complete-state+pixel restore and forward without reseed/rebind; all559 actual current-policy retirement witnesses installed then absent, existing other-producer identities preserved; session/graphics/boot cleanup completed. No native emulator, natural route, native startup/pixel/timing parity or full-game claim.\n"
                        + construction + observations, StandardOpenOption.CREATE_NEW);
    }

    /** Initial CLOSED_CUT ingress only, once per independently restored scene; no new Frame API. */
    private static void stageDiagonalWorldPlayer(SonicCdGameModule module, int x16_16, int y16_16) {
        var runtime = (CdOpeningObjectRuntime) module.getGameService(NativeDynamicObjectPass.class);
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase()); module.requirePlayerActor().requireCompletedNativeFrame();
        var frame = module.requirePlayerFrame(); var actor = frame.captureActor(); var old = actor.state(); var m = old.motion();
        assertEquals(2, m.routine()); assertEquals(0, old.objectLinks().standObject()); assertEquals(0, old.objectLinks().pushObject());
        var motion = new com.openggf.game.soniccd.player.CdPlayerMotionState(x16_16, y16_16, 0, 0, 0,
                2, 0, 2, 4, 9, 19, 0, m.previousAnimation(), 0, 0, 0, 0, 0, m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var state = new com.openggf.game.soniccd.player.CdPlayerBody.State(motion, old.animation(), old.header(), old.timers(),
                old.history(), old.rotationDistance(), old.objectLinks());
        frame.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), state, java.util.Optional.empty()));
        module.requirePlayerActor().rebindAfterRestore();
        assertSame(old.animation(), frame.state().animation()); assertSame(old.header(), frame.state().header());
        assertSame(old.timers(), frame.state().timers()); assertSame(old.history(), frame.state().history());
        assertEquals(old.rotationDistance(), frame.state().rotationDistance()); assertEquals(old.objectLinks(), frame.state().objectLinks());
    }

    /** Entire reached neutral Body prefix, including BOTH stops, followed only by the one real Spring FM port. */
    private static void diagonalWorldStep(GameLoop loop, Bk2FrameInput input, SonicCdGameModule module, boolean springFm) {
        var frame = module.requirePlayerFrame(); var state = frame.state(); var m = state.motion();
        assertEquals(0, frame.environment().movement().globals().lookMode());
        assertEquals(0, frame.environment().movement().gates().speedShoes());
        var expectedPlayer = new ArrayList<>(monitorWorldPlayerSounds(frame));
        // Same complete neutral branch as boulderWorldStep; MovementStop does not suppress the later ChargeStop.
        if ((m.flags() & 6) == 0 && ((m.angle() + 0x20) & 0xC0) == 0 && m.groundVelocity() == 0) {
            var floor = new com.openggf.game.soniccd.player.CdPlayerTerrain(frame.assets().terrain())
                    .floor(m.centreX16_16() >>> 16, (m.centreY16_16() >>> 16) + m.yRadius(), 13, 0, 16, m.spriteFlags());
            if (floor.distance() >= 12 && m.previousPrimaryAngle() != 3 && m.previousSecondaryAngle() != 3)
                expectedPlayer.add(new CdPlayerFrameRuntime.ChargeSound(
                        com.openggf.game.soniccd.player.CdPlayerCharge.Sound.FM_CHARGESTOP));
        }
        step(loop, input);
        var runtime = (CdOpeningObjectRuntime) module.getGameService(NativeDynamicObjectPass.class);
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase());
        assertEquals(expectedPlayer, module.requirePlayerActor().lastSoundRequests());
        var actual = runtime.lastSoundRequests(); assertEquals(expectedPlayer.size() + (springFm ? 1 : 0), actual.size());
        for (int i = 0; i < expectedPlayer.size(); i++)
            assertEquals(new CdOpeningObjectRuntime.PlayerSound(expectedPlayer.get(i)), actual.get(i), "Ordered complete Body prefix " + i);
        if (springFm) {
            // Existing semantic record is inspected; no new sound type, reflection, audio execution or effect replay.
            var sound = actual.getLast(); assertEquals("SpringFm", sound.getClass().getSimpleName());
            assertTrue(sound.toString().contains("FM_SPRING"), "Exactly the existing semantic FM98 request follows Body");
        }
    }

    /** Hand literals, N Spring/Data/Mappings.asm:46-82. No decoder-derived geometry. */
    private static int[][] diagonalWorldLiteralPieces(int sourceFrame) {
        return switch (sourceFrame) {
            case 0 -> new int[][]{{8,0,0,-16},{0,0,1,-16},{8,0,2,-8},{-16,8,3,-16},{-8,0,6,-16},{-8,9,7,-8},{8,4,13,0}};
            case 1 -> new int[][]{{8,0,0,-16},{0,0,15,-16},{8,0,16,-8},{-16,5,17,-16},{-8,0,5,0},{0,0,21,-8},{0,5,22,0}};
            case 2 -> new int[][]{{8,0,0,-16},{0,0,26,-16},{8,0,27,-8},{-32,8,3,0},{-24,14,28,0},{-8,0,40,-16},{-16,2,41,-8},{0,1,44,0}};
            default -> throw new AssertionError("Not a diagonal source frame: " + sourceFrame);
        };
    }

    private static void assertDiagonalWorldSat(CdObjectDrawQueue.CpuSat sat, int cameraX, int cameraY,
            int x, int y, int flip, boolean weak, int sourceFrame) {
        int[][] pieces = diagonalWorldLiteralPieces(sourceFrame); assertEquals(sourceFrame == 2 ? 8 : 7, pieces.length);
        byte[] raw = sat.bytes(); int seen = 0;
        assertTrue(sat.provenance().stream().anyMatch(p -> p.slotRef().wholeIndex() == 0), "Real primary player shares this SAT");
        for (int entry = 0; entry < sat.spriteCount(); entry++) {
            var origin = sat.provenance().get(entry); if (origin.slotRef().wholeIndex() != 40) continue;
            assertTrue(seen < pieces.length); var p = pieces[seen];
            assertEquals(4, origin.queueBucket()); assertEquals(0, origin.queueOrdinal()); assertEquals(seen++, origin.mappingPieceOrdinal());
            int width = (p[1] >>> 2) + 1, height = (p[1] & 3) + 1;
            int relativeX = (flip & 1) != 0 ? -p[3] - width * 8 : p[3];
            int relativeY = (flip & 2) != 0 ? -p[0] - height * 8 : p[0]; int at = entry * 8;
            assertEquals((y - cameraY + 128 + relativeY) & 0xFFFF, ((raw[at] & 255) << 8) | (raw[at + 1] & 255));
            assertEquals(p[1], raw[at + 2] & 255); assertEquals(entry + 1, raw[at + 3] & 255);
            int word = ((raw[at + 4] & 255) << 8) | (raw[at + 5] & 255);
            assertEquals(((weak ? 0x2490 : 0x0490) + p[2]) ^ (flip << 11), word);
            assertEquals(weak ? 0x2000 : 0, word & 0xE000, "Palette strength and hardware priority0; software bucket4 is separate");
            assertEquals((x - cameraX + 128 + relativeX) & 511, ((raw[at + 6] & 255) << 8) | (raw[at + 7] & 255));
        }
        assertEquals(pieces.length, seen);
    }

    private record DiagonalWorldTile(int offset, int x, int y, boolean hFlip, boolean vFlip, int piece) { }
    private static List<DiagonalWorldTile> diagonalWorldLiteralTiles(int x, int y, int flip, int sourceFrame) {
        int[][] pieces = diagonalWorldLiteralPieces(sourceFrame); boolean h = (flip & 1) != 0, v = (flip & 2) != 0;
        var result = new ArrayList<DiagonalWorldTile>();
        for (int piece = pieces.length - 1; piece >= 0; piece--) {
            var p = pieces[piece]; int width = (p[1] >>> 2) + 1, height = (p[1] & 3) + 1;
            int left = h ? -p[3] - width * 8 : p[3], top = v ? -p[0] - height * 8 : p[0];
            for (int row = 0; row < height; row++) for (int col = 0; col < width; col++)
                result.add(new DiagonalWorldTile(p[2] + col * height + row,
                        x + left + (h ? width - 1 - col : col) * 8, y + top + (v ? height - 1 - row : row) * 8, h, v, piece));
        }
        return List.copyOf(result);
    }

    private static String assertDiagonalWorldPixels(com.openggf.graphics.SpritePresentation.Frame picture, RgbaImage image,
            RgbaImage terrain, com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State residency,
            int baseId, List<DiagonalWorldTile> literal, int paletteIndex) {
        var tiles = picture.tiles().stream().filter(t -> t.patternId() >= baseId && t.patternId() < baseId + 46).toList();
        assertEquals(literal.size(), tiles.size()); assertFalse(literal.isEmpty());
        var graphics = GameServices.graphics(); assertFalse(graphics.isWaterEnabled());
        assertFalse(graphics.getPaletteFadePresentation().affects(paletteIndex));
        var palette = GameServices.level().getCurrentLevel().getPalette(paletteIndex); int[] argb = new int[16];
        for (int i = 1; i < 16; i++) {
            var c = palette.getColor(i); argb[i] = 0xFF000000 | com.openggf.graphics.color.DisplayColorConverter.toPackedRgb(
                    c.r & 255, c.g & 255, c.b & 255, graphics.getDisplayColorProfile(), graphics.getDisplayColorExpansion());
        }
        int left = literal.stream().mapToInt(DiagonalWorldTile::x).min().orElseThrow();
        int top = literal.stream().mapToInt(DiagonalWorldTile::y).min().orElseThrow();
        int right = literal.stream().mapToInt(p -> p.x() + 8).max().orElseThrow();
        int bottom = literal.stream().mapToInt(p -> p.y() + 8).max().orElseThrow();
        assertTrue(left >= 0 && top >= 0 && right <= 320 && bottom <= 224, "Full literal early-frame geometry is inside viewport");
        var expected = terrain.pixels().clone(); int opaque = 0, transparent = 0, changed = 0;
        var zeroOpacity = new ArrayList<Integer>(); int index = 0;
        assertTrue(picture.primitives().isEmpty());
        // Preserve actual painter order. The earlier player Draw can overlap a launching Spring.
        for (var t : picture.tiles()) {
            if (t.patternId() < baseId || t.patternId() >= baseId + 46) {
                paintDiagonalWorldOtherSprite(picture,t,expected,left,top,right,bottom);
                continue;
            }
            var p = literal.get(index++);
            assertEquals(baseId + p.offset(), t.patternId()); assertEquals((float) p.x(), t.x()); assertEquals((float) p.y(), t.y());
            assertEquals(8f, t.width()); assertEquals(8f, t.height()); assertEquals(paletteIndex, t.palette());
            assertEquals(p.hFlip(), t.hFlip()); assertEquals(p.vFlip(), t.vFlip()); assertFalse(t.priority());
            assertEquals(com.openggf.graphics.SpritePresentation.Layer.OBJECT, t.layer()); assertTrue(t.priorityShader());
            assertEquals(0, t.occlusionMask()); assertFalse(t.ghost()); assertFalse(t.ghostColour()); assertEquals(1f, t.ghostAlpha());
            assertEquals(0, t.rowStart()); assertEquals(8, t.rowEnd()); assertFalse(t.rowClipped());
            assertEquals(com.openggf.graphics.SpritePresentation.Subject.WORLD, t.subject());
            var decoded = new com.openggf.level.Pattern(); decoded.fromSegaFormat(
                    com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(residency, 0x490 + p.offset()).copy());
            var version = picture.patternVersions().get(baseId + p.offset()); assertNotNull(version);
            long[] words = {version.a(), version.b(), version.c(), version.d()}; int tileOpaque = 0;
            for (int at = 0; at < 64; at++) assertEquals(decoded.getPixel(at % 8, at / 8) & 15,
                    (int) ((words[at / 16] >>> ((at % 16) * 4)) & 15), "Current physical tile nibble " + p.offset() + ":" + at);
            for (int dy = 0; dy < 8; dy++) for (int dx = 0; dx < 8; dx++) {
                int value = decoded.getPixel(p.hFlip() ? 7 - dx : dx, p.vFlip() ? 7 - dy : dy) & 15;
                int at = (p.y() + dy) * 320 + p.x() + dx;
                if (value == 0) transparent++; else {
                    opaque++; tileOpaque++; expected[at] = argb[value];
                    if ((argb[value] & 0xFFFFFF) != (terrain.pixels()[at] & 0xFFFFFF)) changed++;
                }
            }
            if (tileOpaque == 0) zeroOpacity.add(p.offset());
        }
        assertEquals(literal.size(),index);
        // Includes holes: transparent Spring pixels preserve the actual ordered player/terrain composition.
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++)
            assertEquals(expected[y * 320 + x], image.pixels()[y * 320 + x], "Complete current-palette native-plane geometry " + x + ":" + y);
        assertTrue(opaque > 0); assertTrue(transparent > 0); assertTrue(changed > 0);
        assertEquals(literal.size() * 64, opaque + transparent);
        return "opaque=" + opaque + " transparent=" + transparent + " changed=" + changed + " source-zero-offsets=" + zeroOpacity;
    }

    private static void assertDiagonalWorldOmissions(com.openggf.graphics.SpritePresentation.Frame picture, RgbaImage image,
            RgbaImage terrain, com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State residency,
            int baseId, List<DiagonalWorldTile> literal, int paletteIndex) {
        int pieceCount = literal.stream().mapToInt(DiagonalWorldTile::piece).max().orElseThrow() + 1;
        int visibleTiles = 0, visiblePieces = 0;
        for (int index = 0; index < literal.size() + pieceCount; index++) {
            boolean wholePiece = index >= literal.size(); int selector = wholePiece ? index - literal.size() : index;
            var partial = image.copy(); int removed = 0, retained = 0, sourceOpaque = 0;
            for (int tile = 0; tile < literal.size(); tile++) {
                var p = literal.get(tile); boolean omit = wholePiece ? p.piece() == selector : tile == selector;
                var decoded = new com.openggf.level.Pattern(); decoded.fromSegaFormat(
                        com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(residency, 0x490 + p.offset()).copy());
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                    int at = (p.y() + y) * 320 + p.x() + x;
                    boolean changed = (image.pixels()[at] & 0xFFFFFF) != (terrain.pixels()[at] & 0xFFFFFF);
                    if (omit) {
                        if ((decoded.getPixel(p.hFlip() ? 7 - x : x, p.vFlip() ? 7 - y : y) & 15) != 0) sourceOpaque++;
                        if (changed) removed++; partial.pixels()[at] = terrain.pixels()[at];
                    } else if (changed) retained++;
                }
            }
            if (sourceOpaque == 0) { assertEquals(0, removed); continue; } // Real transparent tiles do not invent witnesses.
            assertTrue(removed > 0, "Source-nonempty tile/piece must have a real visible omission witness " + index);
            if (wholePiece) visiblePieces++; else { visibleTiles++; assertTrue(retained > 0); }
            assertThrows(AssertionError.class, () -> assertDiagonalWorldPixels(picture, partial, terrain, residency, baseId, literal, paletteIndex));
        }
        assertTrue(visibleTiles > 1); assertTrue(visiblePieces > 0);
    }
    /** Test-only same-renderer counterfactual; the authoritative World Frame stays immutable and untouched. */
    private static com.openggf.graphics.SpritePresentation.Frame diagonalWorldSpringOnly(
            com.openggf.graphics.SpritePresentation.Frame full,int base) {
        assertTrue(full.primitives().isEmpty());
        var tiles=full.tiles().stream().filter(t->t.patternId()>=base&&t.patternId()<base+46).toList();
        var versions=new java.util.HashMap<Integer,com.openggf.graphics.SpritePresentation.PatternVersion>();
        for(var tile:tiles)versions.put(tile.patternId(),full.patternVersions().get(tile.patternId()));
        return new com.openggf.graphics.SpritePresentation.Frame(tiles,List.of(),versions);
    }

    private static RgbaImage renderDiagonalWorldPicture(SonicCdGameModule module,
            com.openggf.graphics.SpritePresentation.Frame picture,Integer contrastRgb) {
        var graphics=GameServices.graphics();var level=GameServices.level();
        graphics.runPendingRenderThreadTasks();
        if(contrastRgb==null)level.setClearColor();else glClearColor(((contrastRgb>>>16)&255)/255f,
                ((contrastRgb>>>8)&255)/255f,(contrastRgb&255)/255f,1f);
        glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);graphics.resetForFixedFunction();
        if(contrastRgb==null) {
            var selected=((SonicCdPlayerPresentation)module.getScrollHandlerProvider()).captureScrollPublication();
            LevelSpritePresentation.withScrollAndBackground(level,selected.published(),oracleNativeDescriptors(selected.plane()),()->
                    level.drawWithRenderOptions(GameServices.sprites(),LevelManager.LevelRenderOptions.tilesOnly()));
        }
        com.openggf.level.render.SpritePresentationRenderer.draw(graphics,picture,
                GameServices.camera().getX(),GameServices.camera().getY(),layer->true);
        graphics.flush();graphics.flushScreenSpace();glFinish();return ScreenshotCapture.captureFramebuffer(320,224);
    }

    /** Test-only clear colour outside the actual current Spring palette; never a native background claim. */
    private static int diagonalWorldContrastRgb(int paletteIndex) {
        var colours=new java.util.HashSet<Integer>();var graphics=GameServices.graphics();
        var palette=GameServices.level().getCurrentLevel().getPalette(paletteIndex);
        for(int index=1;index<16;index++) {
            var c=palette.getColor(index);colours.add(com.openggf.graphics.color.DisplayColorConverter.toPackedRgb(
                    c.r&255,c.g&255,c.b&255,graphics.getDisplayColorProfile(),graphics.getDisplayColorExpansion())&0xFFFFFF);
        }
        int rgb=0x010203;while(colours.contains(rgb))rgb++;assertFalse(colours.contains(rgb));return rgb;
    }

    /** Other sprites are a measured occlusion input; Spring geometry/bytes remain independent source literals. */
    private static void paintDiagonalWorldOtherSprite(com.openggf.graphics.SpritePresentation.Frame picture,
            com.openggf.graphics.SpritePresentation.Tile tile,int[] expected,int left,int top,int right,int bottom) {
        assertEquals(8f,tile.width());assertEquals(8f,tile.height());assertFalse(tile.rowClipped());
        assertFalse(tile.ghost());assertFalse(tile.ghostColour());assertEquals(1f,tile.ghostAlpha());
        var version=picture.patternVersions().get(tile.patternId());assertNotNull(version);
        long[] words={version.a(),version.b(),version.c(),version.d()};
        var graphics=GameServices.graphics();assertFalse(graphics.getPaletteFadePresentation().affects(tile.palette()));
        var palette=GameServices.level().getCurrentLevel().getPalette(tile.palette());
        for(int dy=0;dy<8;dy++)for(int dx=0;dx<8;dx++) {
            int x=(int)tile.x()+dx,y=(int)tile.y()+dy;if(x<left||x>=right||y<top||y>=bottom)continue;
            int sample=(tile.vFlip()?7-dy:dy)*8+(tile.hFlip()?7-dx:dx);
            int value=(int)((words[sample/16]>>>((sample%16)*4))&15);if(value==0)continue;
            var c=palette.getColor(value);expected[y*320+x]=0xFF000000|com.openggf.graphics.color.DisplayColorConverter.toPackedRgb(
                    c.r&255,c.g&255,c.b&255,graphics.getDisplayColorProfile(),graphics.getDisplayColorExpansion());
        }
    }

    // END constructed diagonal Spring World proof.


    // BEGIN constructed Spikes World proof. All literal outcomes are UNRUN until actual Root execution.

    @Test void actualEglConstructedSpikesWorldDirectHurtAllFlipsReplaysAndRetires567Patterns() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual EGL/media opt-in");
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        String media = System.getProperty("soniccd.media.root"); assertNotNull(media); assertFalse(media.isBlank());
        CdGameDataSource source;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); source = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
        String pickupLog = InputScriptCompiler.compileToInputLog("2 -\n60 R\n24 R+B\n16 R");
        var pickup = new Bk2MovieLoader().parseInputLogText(Path.of("ENGINE-first-ring-before-constructed-spikes"), pickupLog.lines().toList());
        assertEquals(102, pickup.getFrameCount());
        String sceneLog = InputScriptCompiler.compileToInputLog("4 -");
        var movie = new Bk2MovieLoader().parseInputLogText(Path.of("ENGINE-constructed-spikes-four-neutral-rows"), sceneLog.lines().toList());
        assertEquals(4, movie.getFrameCount());
        String construction = "ENGINE constructed actual-USA R11A Spikes component, not a natural Spikes route or native emulator capture.\n"
                + "Execute the EXISTING 102-row first-Ring proposal only through its first real pickup; require rings1, then retain that pristine CLOSED_CUT. Never forge Ring globals.\n"
                + "Restore the same pristine cut for each native flip0..3. At that initial cut ONLY clear all96 dynamics, seed actual whole40 with ID26/XY/subtypes0/BOTH native placement low flip bytes, and restore/rebind the SAME native actor ONCE. No object routine/frame/tile/map/timer/collision/bit7/helper/Hurt-result ingress.\n"
                + "SpikesXY=pristineCamera+(160,56), actorXY=(SpikesX.ABCD,SpikesY-29.1234), XV/YV/GVel0, routine2/angle0/flags2/sprite4/radii9,19/animation0/jump,charge,lock,stick,control0. Preserve previousAnimation/previousangles and animation/header/timers/history/rotation/links/owner; clear only actor display publication. Require actual terrain clearance before each constructed scene.\n"
                + "Row1 actual Init falls through Main: shared Solid still runs with bit7clear and GetOff does no landing. Real DrawObjects sets bit7. Row2 upright collision0 prevents scanner admission; actual landing sets stand40 and zero YV before the wrapper's fullLONG Y subtraction. This is explicitly a SAME-VALUE 32-bit prefix at (SpikesY-27).4A34, not nonzero/fraction-wrap coverage.\n"
                + "Upright direct Hurt is after Body and after physical32's visit: actor4/anim1A/hurt120/XV+200/YV-400 and earlier32 has ONLY ID11+integerXY/routine0, rings remain1; NEXT pass actual32 Init/Main clears rings and emits FM94. Ordinary Hurt has no FM93. Native vertical flips set collision83: their separate row2 Body scanner Hurt precedes32, so LostRing Init/Main/FM94 already runs in row2; no upright direct-Hurt assertion is applied there.\n"
                + "Row3 upright Hurt's actual ObjMove retains stand40 until Spikes' shared above-range GetOff releases flags/link; GetOff does not alter routine/hurt. Row4 remains a bounded ordinary continuation. Whole reached Body sound prefix is checked via monitorWorldPlayerSounds for Main; airborne unrolled Hurt has no Display/MODE/touch/charge owner and no collision reset sound.\n"
                + "All4 flips use real CPU DrawObjects/SAT, literal four size05 pieces/16cells/reused8 patterns and selected priorSAT+NOW current physical AT6400 art. Ordered World pixels preserve legitimate player overlap; render-only isolated glyph/native PlaneB plus explicitly TEST outside-palette contrast controls exercise omissions without moving actors.\n"
                + "Selected full/course CLOSED cuts restore complete captured state and pixels, then advance without rebind/reseed/reload. Install and verify all567 actual current-policy IDs only for retirement; remove base..base+566 while actual terrain/other-producer entry identities survive. Images/input/construction/PASS are written ONLY after module retirement plus session/graphics/boot cleanup.\n"
                + "UNRUN source literals before Root execution. No completed call561, natural Spikes route, native timing/startup/pixel parity, JP/EU, restart-flow or full-game claim. Nonzero fullY rewind/fraction-wrap belongs to the separate actual component tests.\n";
        var observations = new StringBuilder();
        var artifacts = new java.util.LinkedHashMap<String, RgbaImage>();
        int[] nativeFirst = {0x780,0x7AE,0x3A0,0x424,0x441,0x680,0x6C6,0x6D7,0x544,0x334,0x520,0x328,0x5A8,0x374,0x490,0x320};
        int[] counts = {24,14,70,28,63,70,5,39,36,15,36,12,82,19,46,8};
        assertEquals(16, nativeFirst.length); assertEquals(16, counts.length); assertEquals(567, Arrays.stream(counts).sum());
        try (var boot = new HeadlessGameBoot(320, 224)) {
            try {
                var loop = boot.bootSonicCdOpeningObjects(source);
                var mode = SessionManager.getCurrentGameplayMode();
                var module = assertInstanceOf(SonicCdGameModule.class, mode.getWorldSession().getGameModule());
                var runtime = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                var frame = module.requirePlayerFrame(); var objects = source.assets().openingObjects();
                assertSame(source, mode.getWorldSession().getDataSource()); assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
                int pickupRow = -1;
                for (int row = 0; row < pickup.getFrameCount(); row++) {
                    step(loop, pickup.getFrame(row)); render(module, true);
                    if (runtime.ringGlobals().rings() > 0) { pickupRow = row + 1; break; }
                }
                assertTrue(pickupRow > 0, "Actual existing first-Ring input must earn rings before construction");
                assertEquals(1, runtime.ringGlobals().rings()); assertEquals(2, frame.state().motion().routine());
                assertTrue(runtime.lastSoundRequests().contains(new CdOpeningObjectRuntime.RingFm(CdRing.FmSound.FM_RING)));
                assertEquals(0, frame.state().objectLinks().standObject()); assertEquals(0, frame.state().objectLinks().pushObject());
                assertEquals(0, runtime.contactGlobals().timeWarp()); assertEquals(0, runtime.contactGlobals().shield());
                assertEquals(0, runtime.contactGlobals().combineRing()); assertEquals(0, frame.environment().globals().invincible());
                assertEquals(0, frame.environment().globals().debugMode()); assertEquals(0, frame.environment().movement().gates().miniSonic());
                observations.append("actual-first-ring-input-rows=").append(pickupRow).append(" rings=1\n");
                int base = com.openggf.graphics.PatternAtlasRange.OBJECTS.base();
                var banks = new CdOpeningObjectRuntime.LogicalBanks(base, base + 24, base + 38);
                assertEquals(base + 559, banks.spikes()); assertEquals(banks.diagonalSpring() + 46, banks.spikes());
                var sources = objects.sectionArtSources(); assertEquals(10, sources.sources().size());
                var spikes = sources.source(com.openggf.game.soniccd.level.CdR11aAsset.ART_SPIKES);
                assertEquals(1, spikes.canonicalPlcId()); assertEquals(0, spikes.canonicalRowIndex());
                assertEquals(0x6400, spikes.canonicalPlc().vramAddress()); assertEquals(8, spikes.tileCount());
                assertEquals(0x8008, spikes.readerBytes().unsignedWord(0)); assertEquals(256, spikes.decode().bytes().length());
                var mapping = objects.view(com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.SPIKES_MAPPINGS);
                assertEquals(0x20C1F0, mapping.range().nativeAddress()); assertEquals(24, mapping.bytes().length());
                assertArrayEquals(java.util.HexFormat.of().parseHex("000204f0050000f000050004f0f005000000000500040000"), mapping.bytes().copy());
                assertEquals(0, objects.spikes().mappingTableOffset()); assertEquals(1, objects.spikes().mappingFrames().size());
                assertEquals(4, objects.spikes().mappingFrames().getFirst().pieces().size());
                assertTrue(objects.spikes().animations().isEmpty()); assertEquals(0, objects.spikes().animationTable().length());
                assertEquals(0x320, objects.tileWord(0xA, 0));
                for (int tile = 0; tile < 8; tile++) assertArrayEquals(spikes.decode().bytes().slice(tile * 32, 32).copy(),
                        com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(runtime.sectionArtState().residency(), 0x320 + tile).copy(),
                        "Actual USA source equals explicit current DEV cold residency, not a draw fallback: " + tile);
                var atlas = GameServices.graphics().getPatternAtlas(); var policy = monitorWorldPolicy(module, objects, banks); int logical = base;
                for (int span = 0; span < counts.length; span++) for (int tile = 0; tile < counts[span]; tile++) {
                    int id = logical++; assertEquals(id, policy.resolve(nativeFirst[span] + tile).logicalPatternId());
                    assertEquals(1, atlas.registeredRangesForTesting().stream().filter(r -> id >= r.base() && id < r.base() + r.size()).count());
                }
                assertEquals(base + 567, logical, "C26 has sixteen spans; all earlier logical IDs are unchanged");
                var registry = mode.getRewindRegistry(); var pristine = registry.capture(); var pool = runtime.placement().pool();
                for (int flip = 0; flip < 4; flip++) {
                    registry.restore(pristine);
                    assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase());
                    pool.clearDynamicSlots(); assertEquals(96, pool.capture().slots().size());
                    assertTrue(pool.capture().slots().stream().allMatch(s -> Arrays.equals(new byte[64], s.copyBytes())));
                    int spikesX = GameServices.camera().getX() + 160, spikesY = GameServices.camera().getY() + 56;
                    pool.write(pool.state(40).moveByte(ID, 0x26).moveWord(X, spikesX).moveWord(Y, spikesY)
                            .moveByte(SUBTYPE, 0).moveByte(SUBTYPE2, 0).moveByte(SPRITE_FLAGS, flip).moveByte(FLAGS, flip));
                    stageSpikesWorldPlayer(module, (spikesX << 16) | 0xABCD, ((spikesY - 29) << 16) | 0x1234);
                    var probe = new com.openggf.game.soniccd.player.CdPlayerTerrain(frame.assets().terrain());
                    for (int foot : new int[]{spikesX + 9, spikesX - 9}) for (int centreY : new int[]{spikesY - 29, spikesY - 27})
                        assertTrue(probe.floor(foot & 0xFFFF, (centreY + 19) & 0xFFFF, 13, 0, 16, 4).distance() >= 0,
                                "Actual two-foot clearance for initial Body and reached shared Solid floor; flip=" + flip);
                    assertEquals(0, pool.state(40).routine()); assertEquals(0, pool.state(40).unsignedByte(COLLISION_TYPE));
                    assertEquals(flip, pool.state(40).spriteFlags()); assertEquals(flip, pool.state(40).flags());
                    var fullCuts = new ArrayList<>(List.of(registry.capture()));
                    var courseCuts = new ArrayList<>(List.of(registry.captureCourse()));
                    var states = new ArrayList<>(List.of(capture(module))); var pictures = new ArrayList<>(List.of(render(module, true)));
                    assertCapture(states.getFirst(), module);
                    var reached = new java.util.TreeSet<Integer>();
                    var previousCpu = runtime.cpuSat(); int previousX = GameServices.camera().getX(), previousY = GameServices.camera().getY();
                    for (int row = 1; row <= 4; row++) {
                        long clock = frame.vIntClock().absoluteValue().orElseThrow(); int levelFrames = GameServices.level().getFrameCounter();
                        spikesWorldStep(loop, movie.getFrame(row - 1), module, flip, row);
                        assertEquals(clock + 1, frame.vIntClock().absoluteValue().orElseThrow());
                        assertEquals(levelFrames + 1, GameServices.level().getFrameCounter());
                        assertSpikesWorldContact(module, spikesX, spikesY, flip, row);
                        assertTrue(pool.capture().slots().stream().allMatch(s -> s.id() == 0 || s.identity().wholeIndex() == 40
                                || s.identity().wholeIndex() == 32), "Only actual constructed Spikes and the real earlier-slot LostRing are admitted");
                        assertSpikesWorldSat(runtime.cpuSat(), GameServices.camera().getX(), GameServices.camera().getY(), spikesX, spikesY, flip);
                        var completed = capture(module); var published = completed.publication().published();
                        assertEquals(previousX, published.cameraX()); assertEquals(previousY, published.cameraY());
                        assertEquals(com.openggf.game.soniccd.resources.CdObjectPresentation.convert(previousCpu,
                                monitorWorldPolicy(module, objects, banks)), runtime.displayedFrame(), "Prior CPU SAT plus NOW-serviced C26 art");
                        var image = render(module, true); var terrain = renderTerrain(module, published);
                        assertNativeGpuBackground(module); assertTerrainOutsideSprites(image, terrain, runtime.displayedFrame());
                        if (row >= 2) {
                            assertSpikesWorldSat(previousCpu, previousX, previousY, spikesX, spikesY, flip);
                            var literal = spikesWorldLiteralTiles(spikesX - published.cameraX(), spikesY - published.cameraY(), flip);
                            observations.append("flip=").append(flip).append(" row=").append(row).append(" clock=")
                                    .append(frame.vIntClock().absoluteValue().orElseThrow()).append(' ')
                                    .append(assertSpikesWorldPixels(runtime.displayedFrame(), image, terrain,
                                            runtime.sectionArtState().residency(), banks.spikes(), literal)).append('\n');
                            var only = spikesWorldOnly(runtime.displayedFrame(), banks.spikes());
                            var isolated = renderDiagonalWorldPicture(module, only, null);
                            assertSpikesWorldPixels(only, isolated, terrain, runtime.sectionArtState().residency(), banks.spikes(), literal);
                            int rgb = diagonalWorldContrastRgb(0);
                            var contrast = renderDiagonalWorldPicture(module, com.openggf.graphics.SpritePresentation.Frame.empty(), rgb);
                            var contrastGlyph = renderDiagonalWorldPicture(module, only, rgb);
                            assertSpikesWorldPixels(only, contrastGlyph, contrast, runtime.sectionArtState().residency(), banks.spikes(), literal);
                            assertSpikesWorldOmissions(only, contrastGlyph, contrast, runtime.sectionArtState().residency(), banks.spikes(), literal);
                            for (var tile : literal) {
                                reached.add(tile.offset()); var entry = atlas.getEntry(banks.spikes() + tile.offset()); assertNotNull(entry);
                                assertTrue(glIsTexture(atlas.getTextureId(entry.atlasIndex())));
                                assertEquals(runtime.displayedFrame().patternVersions().get(banks.spikes() + tile.offset()),
                                        com.openggf.graphics.SpritePresentation.patternSample(GameServices.graphics(), banks.spikes() + tile.offset()));
                            }
                            assertFalse(Arrays.equals(terrain.pixels(), renderWithoutBackground(module).pixels()), "Actual native Plane B contributes to transparency control");
                            assertArrayEquals(image.pixels(), render(module, true).pixels(), "Render-only controls leave authoritative World publication unchanged");
                            if (row == 2) {
                                artifacts.put("ENGINE-constructed-spikes-flip" + flip + "-row2.png", image);
                                artifacts.put("ENGINE-native-plane-spikes-flip" + flip + "-row2.png", terrain);
                                artifacts.put("ENGINE-render-only-isolated-spikes-flip" + flip + "-row2.png", isolated);
                            }
                        }
                        assertCapture(completed, module); states.add(completed); pictures.add(image);
                        fullCuts.add(registry.capture()); courseCuts.add(registry.captureCourse());
                        previousCpu = runtime.cpuSat(); previousX = GameServices.camera().getX(); previousY = GameServices.camera().getY();
                    }
                    assertEquals(java.util.stream.IntStream.range(0, 8).boxed().collect(java.util.stream.Collectors.toSet()), reached);
                    for (int cut : new int[]{0,1,2,3,4}) for (boolean courseOnly : List.of(false, true)) {
                        if (courseOnly) registry.restoreCourse(courseCuts.get(cut)); else registry.restore(fullCuts.get(cut));
                        assertCapture(states.get(cut), module); assertArrayEquals(pictures.get(cut).pixels(), render(module, true).pixels());
                        assertCapture(states.get(cut), module);
                        for (int row = cut + 1; row <= 4; row++) {
                            spikesWorldStep(loop, movie.getFrame(row - 1), module, flip, row); // No actor ingress, rebind, reseed or source reload.
                            assertSpikesWorldContact(module, spikesX, spikesY, flip, row); assertCapture(states.get(row), module);
                            assertArrayEquals(pictures.get(row).pixels(), render(module, true).pixels(), "flip/cut/row=" + flip + "/" + cut + "/" + row);
                            assertCapture(states.get(row), module);
                        }
                    }
                }
                // Actual CURRENT policy retirement witnesses: all567 installed explicitly, no all567 scene claim.
                var beforeRetirement = capture(module); var retirement = monitorWorldPolicy(module, objects, banks); logical = base;
                for (int span = 0; span < counts.length; span++) for (int tile = 0; tile < counts[span]; tile++) {
                    var sample = retirement.resolve(nativeFirst[span] + tile); assertEquals(logical++, sample.logicalPatternId());
                    var v = sample.version(); long[] words = {v.a(),v.b(),v.c(),v.d()}; var pattern = new com.openggf.level.Pattern();
                    for (int at = 0; at < 64; at++) pattern.setPixel(at % 8, at / 8, (byte) ((words[at / 16] >>> ((at % 16) * 4)) & 15));
                    GameServices.graphics().cachePatternTexture(pattern, sample.logicalPatternId());
                }
                assertEquals(base + 567, logical); assertCapture(beforeRetirement, module);
                for (int tile = 0; tile < 567; tile++) {
                    var entry = atlas.getEntry(base + tile); assertNotNull(entry, "Installed actual current retirement witness " + tile);
                    assertTrue(glIsTexture(atlas.getTextureId(entry.atlasIndex())));
                }
                var shared = new java.util.HashMap<Integer, Object>();
                for (int id = 0; id <= 0x7FF; id++) { var entry = atlas.getEntry(id); if (entry != null) shared.put(id, entry); }
                for (var range : atlas.registeredRangesForTesting()) for (int id = range.base(); id < range.base() + range.size(); id++) {
                    if (id >= base && id < base + 567) continue;
                    var entry = atlas.getEntry(id); if (entry != null) shared.put(id, entry);
                }
                assertFalse(shared.isEmpty()); assertTrue(shared.keySet().stream().anyMatch(id -> id <= 0x7FF));
                observations.append("all567-installed-current-policy; actual-other-producer-identities=").append(shared.size()).append('\n');
                module.closeTerrainPlayer(); assertTrue(runtime.closed()); assertTrue(frame.closed());
                assertSame(atlas, GameServices.graphics().getPatternAtlas());
                // Behavioral RED against old559: FIRST residual ID is base+559 (131631), after all preceding assertions.
                for (int tile = 0; tile < 567; tile++) assertNull(atlas.getEntry(base + tile), "Retired owned current ID " + (base + tile));
                shared.forEach((id, entry) -> assertSame(entry, atlas.getEntry(id), "Preserved actual other-producer identity " + id));
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
            } finally {
                try { SessionManager.closeGameplaySession(); }
                finally { GraphicsManager.destroyForReinit(); }
            }
        }
        // No success artifacts (including input or pictures) exist before all module/session/graphics/boot cleanup returns.
        Path output = openCaptureOutput(pickupLog + "\n; separate constructed scene, repeated from pristine for flips0..3\n" + sceneLog, "soniccd.spikes.output");
        if (output != null) {
            Files.writeString(output.resolve("construction.txt"), construction, StandardOpenOption.CREATE_NEW);
            for (var artifact : artifacts.entrySet()) ScreenshotCapture.savePNG(artifact.getValue(), output.resolve(artifact.getKey()));
            Files.writeString(output.resolve("outcome.txt"),
                    "PASS: ENGINE constructed actual-USA Spikes component; real first-Ring prefix, upright direct-Hurt after actual Solid and separate vertical scanner path, same-value fullY32 prefix, actual earlier32 delayed/same-pass LostRing order, complete Body/FM ledgers, all4 XYflips/four pieces/16cells/eight current AT6400 patterns, ordered native-plane World and renderer-only isolated/contrast omission controls, selected full/course state+pixel restore/forward, all567 actual retirement witnesses removed with actual shared entry identities preserved, then session/graphics/boot cleanup. No natural Spikes route, emulator capture, nonzero fullY rewind, completed call561, native timing/pixel/parity, JP/EU or full-game certification.\n"
                            + construction + observations, StandardOpenOption.CREATE_NEW);
        }
    }

    /** Initial CLOSED_CUT native actor ingress, exactly once per independent flip scene. */
    private static void stageSpikesWorldPlayer(SonicCdGameModule module, int x16_16, int y16_16) {
        var runtime = (CdOpeningObjectRuntime) module.getGameService(NativeDynamicObjectPass.class);
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase()); module.requirePlayerActor().requireCompletedNativeFrame();
        var frame = module.requirePlayerFrame(); var actor = frame.captureActor(); var old = actor.state(); var m = old.motion();
        assertEquals(2, m.routine()); assertEquals(0, old.objectLinks().standObject()); assertEquals(0, old.objectLinks().pushObject());
        assertEquals(0, old.timers().hurt());
        var motion = new com.openggf.game.soniccd.player.CdPlayerMotionState(x16_16, y16_16, 0, 0, 0,
                2, 0, 2, 4, 9, 19, 0, m.previousAnimation(), 0, 0, 0, 0, 0, m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var state = new com.openggf.game.soniccd.player.CdPlayerBody.State(motion, old.animation(), old.header(), old.timers(),
                old.history(), old.rotationDistance(), old.objectLinks());
        frame.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), state, java.util.Optional.empty()));
        module.requirePlayerActor().rebindAfterRestore();
        assertSame(old.animation(), frame.state().animation()); assertSame(old.header(), frame.state().header());
        assertSame(old.timers(), frame.state().timers()); assertSame(old.history(), frame.state().history());
        assertEquals(old.rotationDistance(), frame.state().rotationDistance()); assertEquals(old.objectLinks(), frame.state().objectLinks());
    }

    /** Complete Main prefix uses the existing oracle; Hurt's admitted airborne unrolled branch has no sound owner. */
    private static void spikesWorldStep(GameLoop loop, Bk2FrameInput input, SonicCdGameModule module, int flip, int row) {
        var frame = module.requirePlayerFrame(); var before = frame.state(); var m = before.motion();
        var expectedPlayer = new ArrayList<CdPlayerFrameRuntime.Sound>();
        if (m.routine() == 2) {
            expectedPlayer.addAll(monitorWorldPlayerSounds(frame));
            // Same full neutral CheckBalance then CheckCharge prefix as the existing Boulder/diagonal helpers.
            if ((m.flags() & 6) == 0 && ((m.angle() + 0x20) & 0xC0) == 0 && m.groundVelocity() == 0) {
                var floor = new com.openggf.game.soniccd.player.CdPlayerTerrain(frame.assets().terrain())
                        .floor(m.centreX16_16() >>> 16, (m.centreY16_16() >>> 16) + m.yRadius(), 13, 0, 16, m.spriteFlags());
                if (floor.distance() >= 12 && m.previousPrimaryAngle() != 3 && m.previousSecondaryAngle() != 3)
                    expectedPlayer.add(new CdPlayerFrameRuntime.ChargeSound(com.openggf.game.soniccd.player.CdPlayerCharge.Sound.FM_CHARGESTOP));
            }
        } else {
            assertEquals(4, m.routine()); assertTrue(row >= 3);
            assertEquals(2, m.flags() & 6); assertEquals(0, m.charge()); assertEquals(0, m.moveLock());
            assertEquals(0, m.flags() & 0x40); assertEquals(120, before.timers().hurt());
            // N Sonic/Main:2351-2366,2372-2391: no Display/MODE/touch/charge; no rolling bit -> no ResetOnFloor FM.
            // The post-step literal air motion/flags in assertSpikesWorldContact reject collision/landing/kill alternatives.
        }
        var expected = new ArrayList<CdOpeningObjectRuntime.SoundRequest>();
        expectedPlayer.forEach(sound -> expected.add(new CdOpeningObjectRuntime.PlayerSound(sound)));
        if (((flip & 2) != 0 && row == 2) || ((flip & 2) == 0 && row == 3))
            expected.add(new CdOpeningObjectRuntime.LostRingFm(com.openggf.game.soniccd.objects.CdLostRing.Sound.FM_RINGLOSS));
        step(loop, input);
        var runtime = (CdOpeningObjectRuntime) module.getGameService(NativeDynamicObjectPass.class);
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase());
        assertEquals(expectedPlayer, module.requirePlayerActor().lastSoundRequests(), "Complete reached Body prefix; ordinary unrolled Hurt emits no FM93");
        assertEquals(expected, runtime.lastSoundRequests(), "Actual ordered Body prefix then reached physical LostRing FM94, with no effect replay");
        assertFalse(runtime.lastSoundRequests().contains(new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(
                com.openggf.game.soniccd.player.CdPlayerTouchResponse.FmSound.FM_HURT))), "Ordinary Hurt is not KillPlayer's FM93 path");
    }

    /** N Spikes/Main:19-83, Solid:15-55,65-146,344-430; Collision:228-268; Sonic/Main:2351-2391. */
    private static void assertSpikesWorldContact(SonicCdGameModule module, int x, int y, int flip, int row) {
        var runtime = (CdOpeningObjectRuntime) module.getGameService(NativeDynamicObjectPass.class);
        var pool = runtime.placement().pool(); var live = pool.state(40); var state = module.requirePlayerFrame().state(); var m = state.motion();
        boolean vertical = (flip & 2) != 0;
        assertEquals(0x26, live.id()); assertEquals(2, live.routine()); assertEquals(0x20C1F0, live.longBits(MAPPINGS));
        assertEquals(0x320, live.word(TILE)); assertEquals(3, live.unsignedByte(PRIORITY));
        assertEquals(vertical ? 0x12 : 0x10, live.unsignedByte(WIDTH)); assertEquals(8, live.unsignedByte(Y_RADIUS));
        assertEquals(vertical ? 0x83 : 0, live.unsignedByte(COLLISION_TYPE)); assertEquals(0, live.unsignedByte(MAP_FRAME));
        assertEquals(0, live.unsignedByte(ROUTINE_2)); assertEquals(0, live.unsignedByte(ANIMATION_TIME));
        assertEquals(x, live.centreX()); assertEquals(y, live.centreY()); assertEquals(0x84 | flip, live.spriteFlags());
        assertEquals(0, live.longBits(X_POSITION) & 0xFFFF); assertEquals(0, live.longBits(Y_POSITION) & 0xFFFF);
        assertEquals(0, live.unsignedByte(SUBTYPE)); assertEquals(0, live.subtype2());
        assertEquals(9, m.xRadius()); assertEquals(19, m.yRadius()); assertEquals(0, m.groundVelocity());
        assertEquals(0, m.playerJump()); assertEquals(0, m.charge()); assertEquals(0, m.moveLock()); assertEquals(0, m.playerControl());
        assertEquals(0, state.objectLinks().pushObject());
        if (row == 1) {
            assertEquals((x << 16) | 0xABCD, m.centreX16_16()); assertEquals(((y - 29) << 16) | 0x1234, m.centreY16_16());
            assertEquals(2, m.routine()); assertEquals(2, m.flags()); assertEquals(0x38, m.yVelocity()); assertEquals(0, m.xVelocity());
            assertEquals(0, state.timers().hurt()); assertEquals(0, state.objectLinks().standObject()); assertEquals(flip, live.flags());
            assertEquals(1, runtime.ringGlobals().rings()); assertEquals(0, pool.state(32).id());
        } else {
            int landingY = ((y - (vertical ? 29 : 27)) << 16) | 0x4A34;
            int expectedY = row == 2 ? landingY : row == 3 ? landingY - (0x400 << 8) : landingY - (0x400 << 8) - (0x3D0 << 8);
            assertEquals(((x + 2 * (row - 2)) << 16) | 0xABCD, m.centreX16_16()); assertEquals(expectedY, m.centreY16_16());
            assertEquals(4, m.routine()); assertEquals(0x1A, m.animation()); assertEquals(120, state.timers().hurt());
            assertEquals(0x200, m.xVelocity()); assertEquals(-0x400 + 0x30 * (row - 2), m.yVelocity());
            assertEquals(!vertical && row == 2 ? 0x0A : 2, m.flags());
            assertEquals(!vertical && row == 2 ? 40 : 0, state.objectLinks().standObject());
            assertEquals(flip | (!vertical && row == 2 ? 8 : 0), live.flags());
            if (!vertical && row == 2) {
                int postSolidY32 = ((y - 27) << 16) | 0x4A34, postSolidYVelocity = 0;
                assertEquals(postSolidY32 - (postSolidYVelocity << 8), m.centreY16_16(), "Actual new landing: fullLONG same-value prefix, not nonzero rewind coverage");
                assertEquals(1, runtime.ringGlobals().rings(), "Earlier physical32 cannot Init after direct Hurt at40 in this pass");
                byte[] expectedLost = new byte[64]; expectedLost[0] = 0x11;
                expectedLost[8] = (byte) (x >>> 8); expectedLost[9] = (byte) x;
                expectedLost[12] = (byte) ((y - 27) >>> 8); expectedLost[13] = (byte) (y - 27);
                assertArrayEquals(expectedLost, pool.state(32).copyBytes(), "Native Hurt stores ONLY ID and integer XY in actual earlier32");
            } else {
                assertEquals(0, runtime.ringGlobals().rings(), "Actual earlier32 Init, not Hurt, clears Rings");
                var lost = pool.state(32); assertEquals(0x11, lost.id()); assertEquals(2, lost.routine());
                assertEquals(0x47, lost.unsignedByte(COLLISION_TYPE)); assertEquals(3, lost.unsignedByte(PRIORITY));
                assertEquals(8, lost.unsignedByte(WIDTH)); assertEquals(8, lost.unsignedByte(X_RADIUS)); assertEquals(8, lost.unsignedByte(Y_RADIUS));
                assertEquals(1, pool.capture().slots().stream().filter(s -> s.id() == 0x11).count());
                assertEquals(0x80, runtime.ringGlobals().updateHudRings()); assertEquals(0, runtime.ringGlobals().livesFlags());
                assertEquals(0xFE - (row - (vertical ? 2 : 3)), runtime.spillGlobals().timerByte());
            }
        }
    }

    /** Hand literals: N Spikes/Data/Mappings.asm:1-24, root20C1F0, one frame/four size05 pieces. */
    private static int[][] spikesWorldLiteralPieces() {
        return new int[][]{{-16,5,0,-16},{0,5,4,-16},{-16,5,0,0},{0,5,4,0}};
    }

    private static void assertSpikesWorldSat(CdObjectDrawQueue.CpuSat sat, int cameraX, int cameraY, int x, int y, int flip) {
        int[][] pieces = spikesWorldLiteralPieces(); assertEquals(4, pieces.length); byte[] raw = sat.bytes(); int seen = 0;
        assertTrue(sat.provenance().stream().anyMatch(p -> p.slotRef().wholeIndex() == 0), "Actual primary player shares native SAT");
        for (int entry = 0; entry < sat.spriteCount(); entry++) {
            var origin = sat.provenance().get(entry); if (origin.slotRef().wholeIndex() != 40) continue;
            assertTrue(seen < 4); var p = pieces[seen];
            assertEquals(3, origin.queueBucket()); assertEquals(seen++, origin.mappingPieceOrdinal());
            // A later-frame LostRing can share bucket3 before40; its real ordinal must be retained.
            boolean earlierRingQueued = sat.provenance().stream().anyMatch(pv -> pv.slotRef().wholeIndex() == 32 && pv.queueBucket() == 3);
            assertEquals(earlierRingQueued ? 1 : 0, origin.queueOrdinal());
            if (earlierRingQueued) for (int earlier = 0; earlier < sat.spriteCount(); earlier++) {
                if (sat.provenance().get(earlier).slotRef().wholeIndex() == 32) assertTrue(earlier < entry, "Real earlier-slot Ring precedes Spikes in shared bucket3");
            }
            int relativeX = (flip & 1) != 0 ? -p[3] - 16 : p[3], relativeY = (flip & 2) != 0 ? -p[0] - 16 : p[0]; int at = entry * 8;
            assertEquals((y - cameraY + 128 + relativeY) & 0xFFFF, ((raw[at] & 255) << 8) | (raw[at + 1] & 255));
            assertEquals(5, raw[at + 2] & 255); assertEquals(entry + 1, raw[at + 3] & 255);
            int word = ((raw[at + 4] & 255) << 8) | (raw[at + 5] & 255);
            assertEquals((0x320 + p[2]) ^ (flip << 11), word); assertEquals(0, word & 0xE000);
            assertEquals((x - cameraX + 128 + relativeX) & 511, ((raw[at + 6] & 255) << 8) | (raw[at + 7] & 255));
        }
        assertEquals(4, seen);
    }

    private record SpikesWorldTile(int offset, int x, int y, boolean hFlip, boolean vFlip, int piece) { }
    private static List<SpikesWorldTile> spikesWorldLiteralTiles(int x, int y, int flip) {
        int[][] pieces = spikesWorldLiteralPieces(); var result = new ArrayList<SpikesWorldTile>(); boolean h = (flip & 1) != 0, v = (flip & 2) != 0;
        for (int piece = 3; piece >= 0; piece--) {
            var p = pieces[piece]; int left = h ? -p[3] - 16 : p[3], top = v ? -p[0] - 16 : p[0];
            for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++) result.add(new SpikesWorldTile(
                    p[2] + col * 2 + row, x + left + (h ? 1 - col : col) * 8, y + top + (v ? 1 - row : row) * 8, h, v, piece));
        }
        assertEquals(16, result.size()); return List.copyOf(result);
    }

    private static String assertSpikesWorldPixels(com.openggf.graphics.SpritePresentation.Frame picture, RgbaImage image,
            RgbaImage terrain, com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State residency,
            int base, List<SpikesWorldTile> literal) {
        var tiles = picture.tiles().stream().filter(t -> t.patternId() >= base && t.patternId() < base + 8).toList();
        assertEquals(16, literal.size()); assertEquals(16, tiles.size()); assertTrue(picture.primitives().isEmpty());
        var graphics = GameServices.graphics(); assertFalse(graphics.isWaterEnabled()); assertFalse(graphics.getPaletteFadePresentation().affects(0));
        var palette = GameServices.level().getCurrentLevel().getPalette(0); int[] argb = new int[16];
        for (int i = 1; i < 16; i++) {
            var c = palette.getColor(i); argb[i] = 0xFF000000 | com.openggf.graphics.color.DisplayColorConverter.toPackedRgb(
                    c.r & 255, c.g & 255, c.b & 255, graphics.getDisplayColorProfile(), graphics.getDisplayColorExpansion());
        }
        int left = literal.stream().mapToInt(SpikesWorldTile::x).min().orElseThrow(), top = literal.stream().mapToInt(SpikesWorldTile::y).min().orElseThrow();
        int right = literal.stream().mapToInt(p -> p.x() + 8).max().orElseThrow(), bottom = literal.stream().mapToInt(p -> p.y() + 8).max().orElseThrow();
        assertTrue(left >= 0 && top >= 0 && right <= 320 && bottom <= 224, "Complete32x32 glyph remains in actual viewport");
        assertEquals(32, right - left); assertEquals(32, bottom - top);
        int[] expected = terrain.pixels().clone(); int opaque = 0, transparent = 0, changed = 0, index = 0;
        var sampled = new java.util.TreeSet<Integer>();
        for (var tile : picture.tiles()) {
            if (tile.patternId() < base || tile.patternId() >= base + 8) {
                paintDiagonalWorldOtherSprite(picture, tile, expected, left, top, right, bottom); continue;
            }
            var p = literal.get(index++); assertEquals(base + p.offset(), tile.patternId());
            assertEquals((float) p.x(), tile.x()); assertEquals((float) p.y(), tile.y()); assertEquals(8f, tile.width()); assertEquals(8f, tile.height());
            assertEquals(0, tile.palette()); assertEquals(p.hFlip(), tile.hFlip()); assertEquals(p.vFlip(), tile.vFlip()); assertFalse(tile.priority());
            assertEquals(com.openggf.graphics.SpritePresentation.Layer.OBJECT, tile.layer()); assertTrue(tile.priorityShader());
            assertEquals(0, tile.occlusionMask()); assertFalse(tile.ghost()); assertFalse(tile.ghostColour()); assertEquals(1f, tile.ghostAlpha());
            assertEquals(0, tile.rowStart()); assertEquals(8, tile.rowEnd()); assertFalse(tile.rowClipped());
            assertEquals(com.openggf.graphics.SpritePresentation.Subject.WORLD, tile.subject());
            var decoded = new com.openggf.level.Pattern(); decoded.fromSegaFormat(
                    com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(residency, 0x320 + p.offset()).copy());
            sampled.add(p.offset()); var version = picture.patternVersions().get(base + p.offset()); assertNotNull(version);
            long[] words = {version.a(), version.b(), version.c(), version.d()};
            for (int at = 0; at < 64; at++) assertEquals(decoded.getPixel(at % 8, at / 8) & 15,
                    (int) ((words[at / 16] >>> ((at % 16) * 4)) & 15), "Actual current native320 tile=" + p.offset() + ":" + at);
            for (int dy = 0; dy < 8; dy++) for (int dx = 0; dx < 8; dx++) {
                int value = decoded.getPixel(p.hFlip() ? 7 - dx : dx, p.vFlip() ? 7 - dy : dy) & 15, at = (p.y() + dy) * 320 + p.x() + dx;
                if (value == 0) transparent++; else { opaque++; expected[at] = argb[value]; if (argb[value] != terrain.pixels()[at]) changed++; }
            }
        }
        assertEquals(16, index); assertEquals(java.util.stream.IntStream.range(0,8).boxed().collect(java.util.stream.Collectors.toSet()), sampled);
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++)
            assertEquals(expected[y * 320 + x], image.pixels()[y * 320 + x], "Full ordered current-palette/native-plane glyph " + x + ":" + y);
        assertTrue(opaque > 0); assertTrue(transparent > 0); assertTrue(changed > 0); assertEquals(1024, opaque + transparent);
        return "pieces=4 cells=16 current-patterns=8 opaque=" + opaque + " transparent=" + transparent + " changed=" + changed;
    }

    /** Each16 literal cell and each4 piece is independently omitted on the explicitly TEST contrast control. */
    private static void assertSpikesWorldOmissions(com.openggf.graphics.SpritePresentation.Frame picture, RgbaImage image,
            RgbaImage terrain, com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State residency,
            int base, List<SpikesWorldTile> literal) {
        int witnessedCells = 0, witnessedPieces = 0;
        for (int selector = 0; selector < 20; selector++) {
            boolean wholePiece = selector >= 16; int selected = wholePiece ? selector - 16 : selector;
            var partial = image.copy(); int removed = 0, sourceOpaque = 0;
            for (int index = 0; index < literal.size(); index++) {
                var p = literal.get(index); if (!(wholePiece ? p.piece() == selected : index == selected)) continue;
                var decoded = new com.openggf.level.Pattern(); decoded.fromSegaFormat(
                        com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(residency, 0x320 + p.offset()).copy());
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                    int at = (p.y() + y) * 320 + p.x() + x;
                    if ((decoded.getPixel(p.hFlip() ? 7 - x : x, p.vFlip() ? 7 - y : y) & 15) != 0) sourceOpaque++;
                    if (image.pixels()[at] != terrain.pixels()[at]) removed++; partial.pixels()[at] = terrain.pixels()[at];
                }
            }
            if (sourceOpaque == 0) { assertEquals(0, removed); continue; } // No invented opaque witness for actual empty source pixels.
            assertTrue(removed > 0, "Real nonempty cell/piece contrast omission witness " + selector);
            if (wholePiece) witnessedPieces++; else witnessedCells++;
            assertThrows(AssertionError.class, () -> assertSpikesWorldPixels(picture, partial, terrain, residency, base, literal));
        }
        assertTrue(witnessedCells > 1); assertEquals(4, witnessedPieces);
    }

    /** Render-only isolated real glyph; never rewrites the World/actor/SAT/art/clock or selected publication. */
    private static com.openggf.graphics.SpritePresentation.Frame spikesWorldOnly(com.openggf.graphics.SpritePresentation.Frame full, int base) {
        assertTrue(full.primitives().isEmpty());
        var tiles = full.tiles().stream().filter(t -> t.patternId() >= base && t.patternId() < base + 8).toList();
        var versions = new java.util.HashMap<Integer, com.openggf.graphics.SpritePresentation.PatternVersion>();
        for (var tile : tiles) versions.put(tile.patternId(), full.patternVersions().get(tile.patternId()));
        return new com.openggf.graphics.SpritePresentation.Frame(tiles, List.of(), versions);
    }

    // END constructed Spikes World proof.

    // BEGIN constructed Checkpoint World proof. SOURCE-ONLY literals; Root owns all execution and repairs.

    @Test void actualEglConstructedCheckpointWorldSavesSpinsAnimatesReplaysAndRetires579Patterns() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual EGL/media opt-in");
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        String media = System.getProperty("soniccd.media.root"); assertNotNull(media); assertFalse(media.isBlank());
        CdGameDataSource source;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); source = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
        String bootLog = InputScriptCompiler.compileToInputLog("2 -");
        var bootInputs = new Bk2MovieLoader().parseInputLogText(Path.of("ENGINE-checkpoint-two-ordinary-boot-rows"), bootLog.lines().toList());
        String sceneLog = InputScriptCompiler.compileToInputLog("44 -");
        var movie = new Bk2MovieLoader().parseInputLogText(Path.of("ENGINE-constructed-checkpoint-neutral-input"), sceneLog.lines().toList());
        assertEquals(2, bootInputs.getFrameCount()); assertEquals(44, movie.getFrameCount());
        String construction = "ENGINE constructed actual-USA R11A Checkpoint component; not an emulator capture or natural contact route.\n"
                + "Actual HeadlessGameBoot and two ordinary neutral controller rows first. At ONE initial CLOSED_CUT clear all96 dynamics; keep placement cursors/saved flags/reserved actors. Construct ONLY whole40, ID13, integerXY=currentCamera+(160,56), subtype2 at28/subtype2byte0 at29, native placement flags0. SavedID/routine/frame/map/tile/active/collision/animation/parent bytes remain cleared.\n"
                + "Restore/rebind the SAME completed primary Sonic ONCE at (PoleX.ABCD,(PoleY-24).1234), XV/YV/GVel0, routine2/angle0/flags2/sprite4/radii9,19/animation0/jump,charge,lock,stick,control0. Preserve previous animation/angles, animation cursor/header/timers/history/rotation/links/owner; clear only actor Display publication. Assert real two-foot terrain clearance; no terrain edits or alternate pose retry.\n"
                + "After that initial cut NO actor/pole/ball setters, rebind, source reload, store/effect replay, forced bit7 or manual child. Row1 real Init ASSIGNS spr4 and RETURNS: earlier32 was already visited, so its newly allocated routine4 ball is not run/drawn. Row2 actual Body SPECIAL23/E3 increments pole status; earlier32 reads inactive parent before pole40 clears collision/activates/checkpoint2/spawn1, completes SaveData then requestsAE once. Row3 starts actual ball spin; rows3..34 give32 +8 visits; row34 routine6/angle0, row35 real animation frame2/timer3, row39 frame3/timer3, row43 FF loop to2.\n"
                + "World live water/time are represented actual clear/raw DEV0, not elapsed native HUD time; verify final31 saved bytes/knowledge including the real overlapping water addresses, actual post-Body XY/bottom/mini and eight PRE-scroll cameras. World cannot distinguish same-value duplicate water stores; Root's private18-store cursor guards that ordering beforeAE. Nonzero water-alias/time vectors belong to separate component evidence.\n"
                + "Every ordinary row checks complete reached Body sounds using the existing oracle plus its later ChargeStop branch, then exactly one actual typed CheckpointFm(AE) at contact. Literal4-piece pole/2-piece ball SAT and12/4 cells retain06CB mapping attributes; all12 current physical tiles are reached over frames0,1,2,3. Ordered World pixels use current terrain/native PlaneB; isolated and explicitly TEST contrast renderer controls omit individual nonempty cells/pieces without changing gameplay.\n"
                + "Full/course closed cuts0 preInit,1 postInit,2 contact,18 midSpin,34 startAnimate,35 nonzero timer restore complete state/pixels then forward44 with NO ingress. Saved/live checkpoint owner is compared explicitly in addition to the unchanged existing aggregate oracle.\n"
                + "ALL579 actual atlas entries must exist before close; only missing older567 entries get TEST-only transparent upload witnesses, never checkpoint pixels. All579 must be absent after module close; actual terrain/other producer entry identities survive. Optional labelled ENGINE PNG/input/construction/PASS files are written ONLY after module retirement AND finally session/graphics/boot cleanup.\n"
                + "UNRUN author source. Root owns RED/compile/positive/review/repairs, actual Module/clear/load/restart integration and independent natural route. No restart, native timing/pixel parity, all579 scene-rendered, JP/EU or full-game claim.\n";
        var observations = new StringBuilder(); var artifacts = new java.util.LinkedHashMap<String, RgbaImage>();
        int[] nativeFirst = {0x780,0x7AE,0x3A0,0x424,0x441,0x680,0x6C6,0x6D7,0x544,0x334,0x520,0x328,0x5A8,0x374,0x490,0x320,0x6CB};
        int[] counts = {24,14,70,28,63,70,5,39,36,15,36,12,82,19,46,8,12};
        assertEquals(17, nativeFirst.length); assertEquals(17, counts.length); assertEquals(579, Arrays.stream(counts).sum());
        try (var boot = new HeadlessGameBoot(320, 224)) {
            try {
                var loop = boot.bootSonicCdOpeningObjects(source); var mode = SessionManager.getCurrentGameplayMode();
                var module = assertInstanceOf(SonicCdGameModule.class, mode.getWorldSession().getGameModule());
                var runtime = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                var frame = module.requirePlayerFrame(); var objects = source.assets().openingObjects();
                assertSame(source, mode.getWorldSession().getDataSource()); assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
                assertNull(GameServices.audio().captureLogicalSnapshot().presentation().activeMusic());
                for (int row = 0; row < 2; row++) step(loop, bootInputs.getFrame(row));
                assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase());
                assertEquals(2, frame.state().motion().routine()); assertEquals(0, frame.state().timers().hurt());
                assertEquals(0, frame.state().objectLinks().standObject()); assertEquals(0, frame.state().objectLinks().pushObject());
                assertEquals(0, runtime.levelGlobals().checkpoint()); assertEquals(0, runtime.levelGlobals().spawnMode());
                assertEquals(0, frame.environment().globals().debugCheat()); assertEquals(0, frame.environment().globals().debugMode());
                assertEquals(0, frame.environment().globals().invincible()); assertEquals(0, frame.environment().movement().gates().miniSonic());
                assertEquals(0, runtime.contactGlobals().timeWarp()); assertEquals(0, runtime.contactGlobals().shield());
                assertEquals(0, runtime.contactGlobals().combineRing());
                int base = com.openggf.graphics.PatternAtlasRange.OBJECTS.base();
                var banks = new CdOpeningObjectRuntime.LogicalBanks(base,base+24,base+38,base+108,base+136,base+199,
                        base+269,base+274,base+313,base+349,base+364,base+400,base+412,base+494,base+513,base+559);
                assertEquals(base + 567, banks.checkpoint()); assertEquals(banks.spikes() + 8, banks.checkpoint());
                assertEquals(banks, new CdOpeningObjectRuntime.LogicalBanks(base,base+24,base+38,base+108,base+136,base+199,
                        base+269,base+274,base+313,base+349,base+364,base+400,base+412,base+494,base+513,base+559,base+567));
                var sources = objects.sectionArtSources(); assertEquals(10, sources.sources().size());
                var art = sources.source(com.openggf.game.soniccd.level.CdR11aAsset.ART_CHECKPOINT);
                assertEquals(0, art.canonicalPlcId()); assertEquals(1, art.canonicalRowIndex());
                assertEquals(0xD960, art.canonicalPlc().vramAddress()); assertEquals(12, art.tileCount());
                assertEquals(0x800C, art.readerBytes().unsignedWord(0)); assertEquals(384, art.decode().bytes().length());
                var mapping = objects.view(com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.CHECKPOINT_MAPPINGS);
                assertEquals(0x23F120, mapping.range().nativeAddress()); assertEquals(66, mapping.bytes().length());
                assertArrayEquals(java.util.HexFormat.of().parseHex("0008001e002a0036"
                        +"04e8030000f8e80308000008010004f8080108040000"
                        +"02f8010006f8f8010806000002f8010008f8f8010808000002f801000af8f801080a0000"),mapping.bytes().copy());
                assertArrayEquals(new int[]{8,30,42,54}, java.util.stream.IntStream.range(0,4).map(i -> mapping.bytes().unsignedWord(i*2)).toArray());
                assertArrayEquals(java.util.HexFormat.of().parseHex("0002030203ff"),
                        objects.view(com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.CHECKPOINT_ANIMATIONS).bytes().copy());
                assertEquals(0, objects.checkpoint().mappingTableOffset()); assertEquals(4, objects.checkpoint().mappingFrames().size());
                assertEquals(1, objects.checkpoint().animations().size());
                for (int tile = 0; tile < 12; tile++) assertArrayEquals(art.decode().bytes().slice(tile*32,32).copy(),
                        com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(runtime.sectionArtState().residency(),0x6CB+tile).copy(),
                        "Actual cold current physical AT_D960 bytes, never per-object fallback " + tile);
                var atlas = GameServices.graphics().getPatternAtlas(); var policy = monitorWorldPolicy(module, objects, banks); int logical = base;
                for (int span = 0; span < counts.length; span++) for (int tile = 0; tile < counts[span]; tile++) {
                    int id = logical++; assertEquals(id, policy.resolve(nativeFirst[span]+tile).logicalPatternId());
                    assertEquals(1, atlas.registeredRangesForTesting().stream().filter(r -> id >= r.base() && id < r.base()+r.size()).count());
                }
                assertEquals(base+579, logical);
                var live = runtime.checkpointState().live(); assertEquals(0xFF, live.knownMask());
                for (var field : com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.values())
                    assertEquals(0, com.openggf.game.soniccd.objects.CdCheckpointLiveAux.read(live, field), "Owned cold clear/raw DEV0 " + field);
                var pool = runtime.placement().pool(); pool.clearDynamicSlots();
                assertEquals(96, pool.capture().slots().size());
                assertTrue(pool.capture().slots().stream().allMatch(s -> Arrays.equals(new byte[64], s.copyBytes())));
                int poleX = GameServices.camera().getX()+160, poleY = GameServices.camera().getY()+56;
                pool.write(pool.state(40).moveByte(ID,0x13).moveWord(X,poleX).moveWord(Y,poleY)
                        .moveByte(SUBTYPE,2).moveByte(SUBTYPE2,0).moveByte(FLAGS,0).moveByte(SPRITE_FLAGS,0));
                stageCheckpointWorldPlayer(module, (poleX<<16)|0xABCD, ((poleY-24)<<16)|0x1234);
                var terrainProbe = new com.openggf.game.soniccd.player.CdPlayerTerrain(frame.assets().terrain());
                for (int foot : new int[]{poleX-9,poleX+9})
                    assertTrue(terrainProbe.floor(foot&0xFFFF,(poleY-5)&0xFFFF,13,0,16,4).distance() >= 0,
                            "Initial real two-foot terrain clearance is required; no alternate pose/terrain repair");
                assertEquals(0, pool.state(40).routine()); assertEquals(0, pool.state(40).longBits(MAPPINGS));
                assertEquals(0, pool.state(40).unsignedByte(COLLISION_TYPE)); assertEquals(0, pool.state(40).unsignedByte(CHECKPOINT_ACTIVE));
                assertEquals(0, pool.state(40).savedFlagsId()); assertEquals(0, pool.state(32).id());
                assertTrue(com.openggf.game.soniccd.player.CdPlayerTouchResponse.ownsSpecialFlagCollision(0xE3));
                var registry = mode.getRewindRegistry();
                var fullCuts = new ArrayList<>(List.of(registry.capture())); var courseCuts = new ArrayList<>(List.of(registry.captureCourse()));
                var states = new ArrayList<>(List.of(capture(module))); var pictures = new ArrayList<>(List.of(render(module,true)));
                assertCheckpointWorldCapture(states.getFirst(), module);
                var initial = states.getFirst(); var reached = new java.util.TreeSet<Integer>();
                var math = new com.openggf.game.soniccd.player.CdPlayerMath(frame.assets().player()); int aeCalls = 0;
                for (int row = 1; row <= 44; row++) {
                    var previous = capture(module); long clock = frame.vIntClock().absoluteValue().orElseThrow();
                    int levelFrames = GameServices.level().getFrameCounter();
                    checkpointWorldStep(loop,movie.getFrame(row-1),module,row==2);
                    assertEquals(clock+1,frame.vIntClock().absoluteValue().orElseThrow()); assertEquals(levelFrames+1,GameServices.level().getFrameCounter());
                    aeCalls += (int) runtime.lastSoundRequests().stream().filter(CdOpeningObjectRuntime.CheckpointFm.class::isInstance).count();
                    assertCheckpointWorldObjects(module,poleX,poleY,row,math);
                    assertCheckpointWorldNonwrites(initial,module,row);
                    assertEquals(live,runtime.checkpointState().live(),"Save and ordinary rows never tick/invent/normalize live auxiliary values");
                    if (row<2) assertEquals(initial.objects().checkpointState(),runtime.checkpointState(),"Init does not save");
                    else if (row==2) {
                        var motion = frame.state().motion(); assertEquals(2,motion.flags()&6,"Actual airborne E3 contact, before any floor landing");
                        assertEquals(0xABCD,motion.centreX16_16()&0xFFFF); assertEquals(0x4A34,motion.centreY16_16()&0xFFFF);
                        assertCheckpointWorldSaved(module,previous);
                    } else assertEquals(states.get(2).objects().checkpointState(),runtime.checkpointState(),"No resave after active prefix");
                    var completed = capture(module); var selected = completed.publication().published();
                    assertEquals(previous.publication().prepared(),selected,"Native V-int selects previous completed scroll");
                    assertEquals(com.openggf.game.soniccd.resources.CdObjectPresentation.convert(previous.cpu(),monitorWorldPolicy(module,objects,banks)),
                            runtime.displayedFrame(),"Prior CPU SAT with NOW-serviced player/powerup/current C27 pixels");
                    assertCheckpointWorldSat(runtime.cpuSat(),GameServices.camera().getX(),GameServices.camera().getY(),pool.state(40),row>=2?pool.state(32):null);
                    var image = render(module,true); var terrain = renderTerrain(module,selected);
                    assertNativeGpuBackground(module); assertTerrainOutsideSprites(image,terrain,runtime.displayedFrame());
                    if (row>=2) {
                        var priorPole = previous.objects().placement().pool().slots().get(40-32);
                        var priorBall = row>=3?previous.objects().placement().pool().slots().getFirst():null;
                        assertCheckpointWorldSat(previous.cpu(),selected.cameraX(),selected.cameraY(),priorPole,priorBall);
                        var literal = checkpointWorldLiteralTiles(priorPole,priorBall,selected.cameraX(),selected.cameraY());
                        observations.append("row=").append(row).append(" clock=").append(frame.vIntClock().absoluteValue().orElseThrow())
                                .append(' ').append(assertCheckpointWorldPixels(runtime.displayedFrame(),image,terrain,
                                        runtime.sectionArtState().residency(),banks.checkpoint(),literal)).append('\n');
                        for (var cell : literal) {
                            reached.add(cell.offset()); var entry = atlas.getEntry(banks.checkpoint()+cell.offset()); assertNotNull(entry);
                            assertTrue(glIsTexture(atlas.getTextureId(entry.atlasIndex())));
                            assertEquals(runtime.displayedFrame().patternVersions().get(banks.checkpoint()+cell.offset()),
                                    com.openggf.graphics.SpritePresentation.patternSample(GameServices.graphics(),banks.checkpoint()+cell.offset()));
                        }
                        if (row==2 || row==3 || row==36 || row==40) {
                            var only = checkpointWorldOnly(runtime.displayedFrame(),banks.checkpoint());
                            var isolated = renderDiagonalWorldPicture(module,only,null);
                            assertCheckpointWorldPixels(only,isolated,terrain,runtime.sectionArtState().residency(),banks.checkpoint(),literal);
                            int contrastRgb = diagonalWorldContrastRgb(0);
                            var contrast = renderDiagonalWorldPicture(module,com.openggf.graphics.SpritePresentation.Frame.empty(),contrastRgb);
                            var contrastGlyph = renderDiagonalWorldPicture(module,only,contrastRgb);
                            assertCheckpointWorldPixels(only,contrastGlyph,contrast,runtime.sectionArtState().residency(),banks.checkpoint(),literal);
                            assertCheckpointWorldOmissions(module,only,contrastGlyph,contrast,runtime.sectionArtState().residency(),banks.checkpoint(),literal,contrastRgb);
                            assertFalse(Arrays.equals(terrain.pixels(),renderWithoutBackground(module).pixels()),"Selected actual native Plane B contributes to transparent-pixel control");
                            artifacts.put("ENGINE-constructed-checkpoint-row"+row+".png",image);
                            artifacts.put("ENGINE-native-plane-control-row"+row+".png",terrain);
                            artifacts.put("ENGINE-render-only-isolated-checkpoint-row"+row+".png",isolated);
                        }
                    }
                    assertArrayEquals(image.pixels(),render(module,true).pixels(),"Renderer controls never change the selected World publication");
                    assertCheckpointWorldCapture(completed,module); states.add(completed); pictures.add(image);
                    fullCuts.add(registry.capture()); courseCuts.add(registry.captureCourse());
                }
                assertEquals(1,aeCalls); assertEquals(java.util.stream.IntStream.range(0,12).boxed().collect(java.util.stream.Collectors.toSet()),reached);
                assertEquals(2,pool.state(32).unsignedByte(ANIMATION_TIME),"Row44 is a real nonzero frame2 timer2");
                for (int cut : new int[]{0,1,2,18,34,35}) for (boolean courseOnly : List.of(false,true)) {
                    if (courseOnly) registry.restoreCourse(courseCuts.get(cut)); else registry.restore(fullCuts.get(cut));
                    assertCheckpointWorldCapture(states.get(cut),module);
                    assertArrayEquals(pictures.get(cut).pixels(),render(module,true).pixels()); assertCheckpointWorldCapture(states.get(cut),module);
                    for (int row = cut+1; row<=44; row++) {
                        checkpointWorldStep(loop,movie.getFrame(row-1),module,row==2); // NO restaging/rebind/reload/default reset/callback replay.
                        assertCheckpointWorldObjects(module,poleX,poleY,row,math); assertCheckpointWorldCapture(states.get(row),module);
                        assertArrayEquals(pictures.get(row).pixels(),render(module,true).pixels(),"Checkpoint full/course cut/row="+cut+"/"+row);
                        assertCheckpointWorldCapture(states.get(row),module);
                    }
                }
                var beforeRetirement = capture(module); int transparentWitnesses = 0;
                for (int offset = 0; offset<579; offset++) {
                    int id = base+offset;
                    if (atlas.getEntry(id)==null) {
                        assertTrue(offset<567,"All12 checkpoint versions must be naturally installed by actual rendered frames");
                        GameServices.graphics().cachePatternTexture(new com.openggf.level.Pattern(),id); transparentWitnesses++;
                    }
                    var entry = atlas.getEntry(id); assertNotNull(entry,"ALL579 installed retirement witness " + id);
                    assertTrue(glIsTexture(atlas.getTextureId(entry.atlasIndex())));
                }
                assertCheckpointWorldCapture(beforeRetirement,module);
                var shared = new java.util.HashMap<Integer,Object>();
                for (int id=0;id<=0x7FF;id++) { var entry=atlas.getEntry(id); if(entry!=null) shared.put(id,entry); }
                for(var range:atlas.registeredRangesForTesting()) for(int id=range.base();id<range.base()+range.size();id++) {
                    if(id>=base && id<base+579) continue; var entry=atlas.getEntry(id); if(entry!=null) shared.put(id,entry);
                }
                assertFalse(shared.isEmpty()); assertTrue(shared.keySet().stream().anyMatch(id -> id<=0x7FF));
                observations.append("all579-installed; TEST-only-old-missing-transparent-witnesses=").append(transparentWitnesses)
                        .append("; actual-shared-entry-identities=").append(shared.size()).append('\n');
                module.closeTerrainPlayer(); assertTrue(runtime.closed()); assertTrue(frame.closed());
                assertSame(atlas,GameServices.graphics().getPatternAtlas());
                // Proposed behavioral RED against J567: first residual is base+567, only AFTER the complete proof prefix.
                for(int offset=0;offset<579;offset++) assertNull(atlas.getEntry(base+offset),"ALL579 owned IDs retired " +(base+offset));
                shared.forEach((id,entry)->assertSame(entry,atlas.getEntry(id),"Actual other-producer identity survives " +id));
                assertEquals(NullAudioBackend.class,GameServices.audio().getBackend().getClass());
            } finally {
                try { SessionManager.closeGameplaySession(); }
                finally { GraphicsManager.destroyForReinit(); }
            }
        }
        // This block is reached only after579 retirement AND session/graphics/owned boot-context closures.
        Path output = openCaptureOutput(bootLog+"\n; ONE constructed closed-cut ingress, then controller-only scene\n"+sceneLog,"soniccd.checkpoint.output");
        if(output!=null) {
            Files.writeString(output.resolve("construction.txt"),construction,StandardOpenOption.CREATE_NEW);
            for(var artifact:artifacts.entrySet()) ScreenshotCapture.savePNG(artifact.getValue(),output.resolve(artifact.getKey()));
            Files.writeString(output.resolve("outcome.txt"),
                    "PASS: ENGINE constructed actual-USA Checkpoint World; actual Init/earlier32 child order/Body SPECIAL E3 contact, final31 saved bytes+knowledge/pre-scroll8 cameras/raw DEV0/live nonwrites/oneAE,32 real spin visits, native delay3 frames2/3/FF loop, literal4/2 piece SAT/current12 patterns/ordered terrain+native PlaneB pixels/isolated renderer contrast omissions, selected full/course state+pixel forward replay without ingress, ALL579 installed retirement witnesses removed and actual shared identities preserved; module/session/graphics/boot cleanup returned. World zero alias cannot prove nonzero alias or repeated same-value store execution; no restart/natural route/emulator/native parity/all579 rendered/full-game claim.\n"
                            +construction+observations,StandardOpenOption.CREATE_NEW);
        }
    }

    /** ONE initial closed-cut actor ingress; never called from a replay or row loop. */
    private static void stageCheckpointWorldPlayer(SonicCdGameModule module,int x16_16,int y16_16) {
        var runtime=(CdOpeningObjectRuntime)module.getGameService(NativeDynamicObjectPass.class);
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT,runtime.phase()); module.requirePlayerActor().requireCompletedNativeFrame();
        var frame=module.requirePlayerFrame();var actor=frame.captureActor();var old=actor.state();var m=old.motion();
        assertEquals(2,m.routine());assertEquals(0,old.objectLinks().standObject());assertEquals(0,old.objectLinks().pushObject());
        var motion=new com.openggf.game.soniccd.player.CdPlayerMotionState(x16_16,y16_16,0,0,0,2,0,2,4,9,19,0,
                m.previousAnimation(),0,0,0,0,0,m.previousPrimaryAngle(),m.previousSecondaryAngle());
        var state=new com.openggf.game.soniccd.player.CdPlayerBody.State(motion,old.animation(),old.header(),old.timers(),old.history(),old.rotationDistance(),old.objectLinks());
        frame.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(),state,java.util.Optional.empty())); module.requirePlayerActor().rebindAfterRestore();
        assertSame(old.animation(),frame.state().animation());assertSame(old.header(),frame.state().header());assertSame(old.timers(),frame.state().timers());
        assertSame(old.history(),frame.state().history());assertEquals(old.rotationDistance(),frame.state().rotationDistance());assertEquals(old.objectLinks(),frame.state().objectLinks());
    }

    /** Complete reached neutral Body prefix, including the separate later CheckCharge stop after a movement stop. */
    private static void checkpointWorldStep(GameLoop loop,Bk2FrameInput input,SonicCdGameModule module,boolean checkpointFm) {
        var frame=module.requirePlayerFrame();var m=frame.state().motion();
        assertEquals(0,frame.state().objectLinks().standObject());assertEquals(0,frame.state().objectLinks().pushObject());
        assertEquals(0,frame.environment().movement().globals().lookMode());assertEquals(0,frame.environment().movement().gates().speedShoes());
        var player=new ArrayList<>(monitorWorldPlayerSounds(frame));
        if((m.flags()&6)==0 && ((m.angle()+0x20)&0xC0)==0 && m.groundVelocity()==0) {
            var floor=new com.openggf.game.soniccd.player.CdPlayerTerrain(frame.assets().terrain()).floor(
                    m.centreX16_16()>>>16,(m.centreY16_16()>>>16)+m.yRadius(),13,0,16,m.spriteFlags());
            if(floor.distance()>=12 && m.previousPrimaryAngle()!=3 && m.previousSecondaryAngle()!=3)
                player.add(new CdPlayerFrameRuntime.ChargeSound(com.openggf.game.soniccd.player.CdPlayerCharge.Sound.FM_CHARGESTOP));
        }
        var expected=new ArrayList<CdOpeningObjectRuntime.SoundRequest>();player.forEach(s->expected.add(new CdOpeningObjectRuntime.PlayerSound(s)));
        if(checkpointFm) expected.add(new CdOpeningObjectRuntime.CheckpointFm(com.openggf.game.soniccd.objects.CdCheckpoint.FmSound.FM_CHECKPOINT_AE));
        assertEquals(0xAE,com.openggf.game.soniccd.objects.CdCheckpoint.FmSound.FM_CHECKPOINT_AE.nativeId());
        step(loop,input);var runtime=(CdOpeningObjectRuntime)module.getGameService(NativeDynamicObjectPass.class);
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT,runtime.phase());assertEquals(player,module.requirePlayerActor().lastSoundRequests());
        assertEquals(expected,runtime.lastSoundRequests(),"Entire ordered ordinary Body ledger, then AE exactly at the actual save, never replay effects");
    }

    /** Native Checkpoint/Main:71-117,126-143,149-189; compare ALL64 bytes, including every unwritten byte/fraction. */
    private static void assertCheckpointWorldObjects(SonicCdGameModule module,int x,int y,int row,
            com.openggf.game.soniccd.player.CdPlayerMath math) {
        var runtime=(CdOpeningObjectRuntime)module.getGameService(NativeDynamicObjectPass.class);var pool=runtime.placement().pool();
        assertTrue(pool.capture().slots().stream().allMatch(s->s.id()==0 || s.identity().wholeIndex()==32 || s.identity().wholeIndex()==40));
        byte[] pole=new byte[64];pole[0]=0x13;pole[1]=(byte)0x84;checkpointWorldPut(pole,2,2,0x06CB);checkpointWorldPut(pole,4,4,0x23F120);
        checkpointWorldPut(pole,8,2,x);checkpointWorldPut(pole,12,2,y);pole[0x16]=24;pole[0x18]=4;pole[0x19]=8;pole[0x24]=2;pole[0x28]=2;
        pole[0x20]=(byte)(row==1?0xE3:0);pole[0x21]=(byte)(row==1?0:1);pole[0x2E]=(byte)(row==1?0:1);
        assertArrayEquals(pole,pool.state(40).copyBytes(),"Complete pole image; spr4 assignment plus real DrawObjects bit7, no invented writes row="+row);
        byte[] ball=new byte[64];ball[0]=0x13;ball[1]=(byte)(row==1?4:0x84);checkpointWorldPut(ball,2,2,0x06CB);checkpointWorldPut(ball,4,4,0x23F120);
        ball[0x16]=8;ball[0x18]=3;ball[0x19]=8;ball[0x1A]=1;ball[0x24]=(byte)(row<34?4:6);
        checkpointWorldPut(ball,0x2A,2,x);checkpointWorldPut(ball,0x2C,2,y-24);checkpointWorldPut(ball,0x30,4,0xFFFFDA00);
        int bx=x,by=y-32;
        if(row>=3) {
            int angle=(Math.min(32,row-2)*8)&255;var values=math.calcSine(angle);
            bx=(x+(((short)values.sine()*8)>>>8))&0xFFFF;by=(y-24+(((short)values.cosine()*-8)>>>8))&0xFFFF;
            ball[0x2E]=1;ball[0x34]=(byte)angle;
        }
        checkpointWorldPut(ball,8,2,bx);checkpointWorldPut(ball,12,2,by);
        if(row>=35) {
            int animationRow=row-35;ball[0x1A]=(byte)(2+((animationRow/4)&1));ball[0x1B]=(byte)(1+((animationRow/4)&1));
            ball[0x1E]=(byte)(3-animationRow%4);
        }
        assertArrayEquals(ball,pool.state(32).copyBytes(),"Actual earlier32 visit/32-step native table orbit/real animation and nonwrites row="+row);
        assertEquals(0,pool.state(32).word(X_FRACTION));assertEquals(0,pool.state(32).word(Y_FRACTION));
        if(row==1) {assertEquals(0,runtime.levelGlobals().checkpoint());assertEquals(0,runtime.levelGlobals().spawnMode());}
        else {assertEquals(2,runtime.levelGlobals().checkpoint());assertEquals(1,runtime.levelGlobals().spawnMode());}
        if(row==18) {assertEquals(0x80,pool.state(32).unsignedByte(CHECKPOINT_BALL_ANGLE));assertEquals(x,pool.state(32).centreX());assertEquals(y-16,pool.state(32).centreY());}
        if(row==34) {assertEquals(0,pool.state(32).unsignedByte(CHECKPOINT_BALL_ANGLE));assertEquals(x,pool.state(32).centreX());assertEquals(y-32,pool.state(32).centreY());}
    }

    private static void checkpointWorldPut(byte[] image,int at,int width,int value) {
        for(int i=0;i<width;i++) image[at+i]=(byte)(value>>>((width-1-i)*8));
    }

    private static void assertCheckpointWorldNonwrites(Capture initial,SonicCdGameModule module,int row) {
        var runtime=(CdOpeningObjectRuntime)module.getGameService(NativeDynamicObjectPass.class);var before=initial.objects();var level=before.levelGlobals();
        var ring=before.ringGlobals();
        assertEquals(6,ring.ringAnimationTimer());assertEquals(1,ring.ringAnimationFrame());
        // Native UpdateGlobalAnims.Rings (Level/Main.asm:520-525): the ordinary tail runs once per row.
        var expectedRing=new com.openggf.game.soniccd.objects.CdRingGlobals(ring.rings(),ring.lives(),ring.livesFlags(),
                ring.updateHudRings(),ring.updateHudLives(),ring.timeStopTimer(),(6-row)&7,(1+(row+1)/8)&3);
        assertEquals(expectedRing,runtime.ringGlobals());assertEquals(before.contactGlobals(),runtime.contactGlobals());
        assertEquals(before.scoreGlobals(),runtime.scoreGlobals());assertEquals(before.devGlobals(),runtime.devGlobals());
        assertEquals(new com.openggf.game.soniccd.objects.CdLevelGlobals(level.levelRestart(),level.updateHudTime(),row<2?0:2,row<2?0:1,
                level.plcLoadFlags(),level.flowerCount(),level.fadeEnableDisplay(),level.powerup(),level.timeOver()),runtime.levelGlobals());
        assertArrayEquals(before.placement().savedFlags().copy(),runtime.placement().capture().savedFlags().copy(),"Checkpoint has no saved-object-bank membership");
        var old=initial.shared().environment().movement().gates();var now=module.requirePlayerFrame().environment().movement().gates();
        assertEquals(old.timeZone(),now.timeZone());assertEquals(old.timeWarpDirection(),now.timeWarpDirection());assertEquals(old.miniSonic(),now.miniSonic());
    }

    /** Literal address writes, NOT a second invocation of SaveData; eight cameras are sampled from the PRE-scroll cut. */
    private static void assertCheckpointWorldSaved(SonicCdGameModule module,Capture beforeContact) {
        var runtime=(CdOpeningObjectRuntime)module.getGameService(NativeDynamicObjectPass.class);var frame=module.requirePlayerFrame();
        var owner=runtime.checkpointState();var live=beforeContact.objects().checkpointState().live();var saved=owner.saved();
        assertEquals(live,owner.live());assertEquals(0xFF,live.knownMask());
        var scroll=assertInstanceOf(SonicCdPlayerPresentation.ScrollCapture.class,beforeContact.scroll());var bg=scroll.background();
        int[] cameras={scroll.state().cameraX(),scroll.state().cameraY16_16()>>>16,bg.cameraBgX()>>>16,bg.cameraBgY()>>>16,
                bg.cameraBg2X()>>>16,bg.cameraBg2Y()>>>16,bg.cameraBg3X()>>>16,bg.cameraBg3Y()>>>16};
        int waterRoutine=com.openggf.game.soniccd.objects.CdCheckpointLiveAux.read(live,com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.WATER_ROUTINE);
        int height=com.openggf.game.soniccd.objects.CdCheckpointLiveAux.read(live,com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.WATER_HEIGHT2);
        int full=com.openggf.game.soniccd.objects.CdCheckpointLiveAux.read(live,com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.WATER_FULLSCREEN);
        int rawTime=com.openggf.game.soniccd.objects.CdCheckpointLiveAux.read(live,com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.TIME_LONG);
        assertEquals(0,rawTime,"Explicit DEV HUD omission/raw0, not an elapsed native timer");assertEquals(0,height);assertEquals(0,waterRoutine);assertEquals(0,full);
        byte[] expected=new byte[31];expected[0]=1;
        checkpointWorldPut(expected,1,2,frame.state().motion().centreX16_16()>>>16);checkpointWorldPut(expected,3,2,frame.state().motion().centreY16_16()>>>16);
        checkpointWorldPut(expected,5,4,Integer.compareUnsigned(rawTime,0x00050000)>=0?0x00050000:rawTime);
        assertEquals(beforeContact.shared().environment().movement().bounds().bottom(),frame.environment().movement().bounds().bottom());
        checkpointWorldPut(expected,9,2,frame.environment().movement().bounds().bottom());
        for(int i=0;i<8;i++) checkpointWorldPut(expected,11+i*2,2,cameras[i]);
        checkpointWorldPut(expected,27,2,height);expected[28]=(byte)waterRoutine;expected[29]=(byte)full;
        expected[30]=(byte)frame.environment().movement().gates().miniSonic();
        int[] addresses=java.util.stream.IntStream.concat(java.util.stream.IntStream.concat(java.util.stream.IntStream.rangeClosed(0xFF1523,0xFF1527),
                java.util.stream.IntStream.rangeClosed(0xFF152A,0xFF152D)),java.util.stream.IntStream.concat(java.util.stream.IntStream.rangeClosed(0xFF1530,0xFF1544),
                java.util.stream.IntStream.of(0xFF1591))).toArray();
        assertEquals(31,addresses.length);assertEquals(31,saved.knownMask().length());
        byte[] allKnown=new byte[31];Arrays.fill(allKnown,(byte)1);assertArrayEquals(allKnown,saved.knownMask().copy());
        for(int i=0;i<31;i++) assertEquals(expected[i]&255,saved.knownByte(addresses[i]).orElseThrow(),"Every actual saved byte/address "+Integer.toHexString(addresses[i]));
        assertEquals(((height&0xFF00)|waterRoutine),saved.read(com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.WATER_HEIGHT_WORD));
        assertEquals(waterRoutine,saved.read(com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.WATER_ROUTINE));
        assertEquals(18,com.openggf.game.soniccd.objects.CdCheckpointSaveData.Store.values().length,"Source18 including repeated routine store; NOT an observed store trace");
    }

    /** Extend the existing byte-exact aggregate oracle without modifying any older helper/test. */
    private static void assertCheckpointWorldCapture(Capture expected,SonicCdGameModule module) {
        assertCapture(expected,module);var runtime=(CdOpeningObjectRuntime)module.getGameService(NativeDynamicObjectPass.class);
        assertEquals(expected.objects().checkpointState(),runtime.checkpointState());
        assertEquals(expected.objects().checkpointState(),runtime.captureClosedCut().checkpointState());
    }

    /** Independent N Mappings:1-63 literals: pole4 pieces/12 cells; each ball2 pieces/4 cells. */
    private static int[][] checkpointWorldPieces(int frame) {
        if(frame==0) return new int[][]{{-24,3,0,-8},{-24,3,0x0800,0},{8,1,4,-8},{8,1,0x0804,0}};
        if(frame>=1 && frame<=3) {int first=4+frame*2;return new int[][]{{-8,1,first,-8},{-8,1,first|0x0800,0}};}
        throw new AssertionError("Unowned literal Checkpoint frame "+frame);
    }

    private static void assertCheckpointWorldSat(CdObjectDrawQueue.CpuSat sat,int cameraX,int cameraY,
            com.openggf.game.soniccd.objects.CdObjectState pole,com.openggf.game.soniccd.objects.CdObjectState ball) {
        assertTrue(sat.provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==0),"Actual Sonic shares native SAT");
        byte[] raw=sat.bytes();int poleSeen=0,ballSeen=0,lastBall=-1,firstPole=-1;
        for(int entry=0;entry<sat.spriteCount();entry++) {
            var provenance=sat.provenance().get(entry);int whole=provenance.slotRef().wholeIndex();if(whole!=32 && whole!=40) continue;
            var object=whole==40?pole:ball;assertNotNull(object);int ordinal=whole==40?poleSeen++:ballSeen++;
            var pieces=checkpointWorldPieces(object.unsignedByte(MAP_FRAME));assertTrue(ordinal<pieces.length);int[] p=pieces[ordinal];
            assertEquals(whole==40?4:3,provenance.queueBucket());assertEquals(0,provenance.queueOrdinal());assertEquals(ordinal,provenance.mappingPieceOrdinal());
            int at=entry*8;assertEquals((object.centreY()-cameraY+128+p[0])&0xFFFF,((raw[at]&255)<<8)|(raw[at+1]&255));
            assertEquals(p[1],raw[at+2]&255);assertEquals(entry+1,raw[at+3]&255);
            int tileWord=((raw[at+4]&255)<<8)|(raw[at+5]&255);assertEquals(0x06CB+p[2],tileWord);assertEquals(0,tileWord&0xE000,"Native hardware priority0/palette0, distinct from queue3/4");
            assertEquals((object.centreX()-cameraX+128+p[3])&511,((raw[at+6]&255)<<8)|(raw[at+7]&255));
            if(whole==32) lastBall=entry;else if(firstPole<0) firstPole=entry;
        }
        assertEquals(4,poleSeen);assertEquals(ball==null?0:2,ballSeen);if(ball!=null) assertTrue(lastBall<firstPole,"Native ball bucket3 precedes pole bucket4");
    }

    private record CheckpointWorldCell(int offset,int x,int y,boolean hFlip,int piece) { }
    private static List<CheckpointWorldCell> checkpointWorldLiteralTiles(com.openggf.game.soniccd.objects.CdObjectState pole,
            com.openggf.game.soniccd.objects.CdObjectState ball,int cameraX,int cameraY) {
        var out=new ArrayList<CheckpointWorldCell>();
        // Renderer is reverse SAT: pole bucket4 first, then ball bucket3, then actual player bucket2.
        var objects=new ArrayList<com.openggf.game.soniccd.objects.CdObjectState>();objects.add(pole);if(ball!=null) objects.add(ball);
        for(var object:objects) {
            int[][] pieces=checkpointWorldPieces(object.unsignedByte(MAP_FRAME));
            for(int piece=pieces.length-1;piece>=0;piece--) {
                var p=pieces[piece];int height=(p[1]&3)+1;assertEquals(0,p[1]>>>2);boolean h=(p[2]&0x0800)!=0;
                for(int cell=0;cell<height;cell++) out.add(new CheckpointWorldCell((p[2]&0x07FF)+cell,
                        object.centreX()-cameraX+p[3],object.centreY()-cameraY+p[0]+cell*8,h,(object==pole?0:4)+piece));
            }
        }
        assertEquals(ball==null?12:16,out.size());return List.copyOf(out);
    }

    /** Independent current-tile nibble/palette geometry oracle in actual painter order, including all holes and overlaps. */
    private static String assertCheckpointWorldPixels(com.openggf.graphics.SpritePresentation.Frame picture,RgbaImage image,
            RgbaImage terrain,com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State residency,int base,List<CheckpointWorldCell> literal) {
        assertTrue(picture.primitives().isEmpty());var tiles=picture.tiles().stream().filter(t->t.patternId()>=base && t.patternId()<base+12).toList();
        assertEquals(literal.size(),tiles.size());var graphics=GameServices.graphics();assertFalse(graphics.isWaterEnabled());assertFalse(graphics.getPaletteFadePresentation().affects(0));
        int[] argb=new int[16];var palette=GameServices.level().getCurrentLevel().getPalette(0);
        for(int i=1;i<16;i++){var c=palette.getColor(i);argb[i]=0xFF000000|com.openggf.graphics.color.DisplayColorConverter.toPackedRgb(c.r&255,c.g&255,c.b&255,
                graphics.getDisplayColorProfile(),graphics.getDisplayColorExpansion());}
        int left=literal.stream().mapToInt(CheckpointWorldCell::x).min().orElseThrow(),top=literal.stream().mapToInt(CheckpointWorldCell::y).min().orElseThrow();
        int right=literal.stream().mapToInt(p->p.x()+8).max().orElseThrow(),bottom=literal.stream().mapToInt(p->p.y()+8).max().orElseThrow();
        assertTrue(left>=0 && top>=0 && right<=320 && bottom<=224,"Entire current pole/ball geometry in viewport");
        int[] expected=terrain.pixels().clone();int index=0,opaque=0,transparent=0,changed=0;
        for(var tile:picture.tiles()) {
            if(tile.patternId()<base || tile.patternId()>=base+12){paintDiagonalWorldOtherSprite(picture,tile,expected,left,top,right,bottom);continue;}
            var p=literal.get(index++);assertEquals(base+p.offset(),tile.patternId());assertEquals((float)p.x(),tile.x());assertEquals((float)p.y(),tile.y());
            assertEquals(8f,tile.width());assertEquals(8f,tile.height());assertEquals(0,tile.palette());assertEquals(p.hFlip(),tile.hFlip());assertFalse(tile.vFlip());assertFalse(tile.priority());
            assertEquals(com.openggf.graphics.SpritePresentation.Layer.OBJECT,tile.layer());assertTrue(tile.priorityShader());assertEquals(0,tile.occlusionMask());
            assertFalse(tile.ghost());assertFalse(tile.ghostColour());assertEquals(1f,tile.ghostAlpha());assertEquals(0,tile.rowStart());assertEquals(8,tile.rowEnd());assertFalse(tile.rowClipped());
            assertEquals(com.openggf.graphics.SpritePresentation.Subject.WORLD,tile.subject());
            var decoded=new com.openggf.level.Pattern();decoded.fromSegaFormat(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(residency,0x6CB+p.offset()).copy());
            var version=picture.patternVersions().get(base+p.offset());assertNotNull(version);long[] words={version.a(),version.b(),version.c(),version.d()};
            for(int n=0;n<64;n++)assertEquals(decoded.getPixel(n%8,n/8)&15,(int)((words[n/16]>>>((n%16)*4))&15),"Current physical checkpoint nibble "+p.offset()+":"+n);
            for(int dy=0;dy<8;dy++)for(int dx=0;dx<8;dx++){
                int value=decoded.getPixel(p.hFlip()?7-dx:dx,dy)&15,at=(p.y()+dy)*320+p.x()+dx;
                if(value==0)transparent++;else{opaque++;expected[at]=argb[value];if(argb[value]!=terrain.pixels()[at])changed++;}
            }
        }
        assertEquals(literal.size(),index);
        for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)assertEquals(expected[y*320+x],image.pixels()[y*320+x],"Complete ordered Checkpoint/other-sprite/current native-plane pixels "+x+":"+y);
        assertEquals(literal.size()*64,opaque+transparent);assertTrue(opaque>0);assertTrue(transparent>0);assertTrue(changed>0);
        return "cells="+literal.size()+" opaque="+opaque+" transparent="+transparent+" changed="+changed;
    }

    /** Same actual renderer, TEST-only backdrop; omission changes only a derived picture, never source/current art or actors. */
    private static void assertCheckpointWorldOmissions(SonicCdGameModule module,com.openggf.graphics.SpritePresentation.Frame picture,
            RgbaImage full,RgbaImage terrain,com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State residency,int base,
            List<CheckpointWorldCell> literal,int contrastRgb) {
        int pieceCount=literal.stream().mapToInt(CheckpointWorldCell::piece).max().orElseThrow()+1,witnessedCells=0,witnessedPieces=0;
        for(int selector=0;selector<literal.size()+pieceCount;selector++) {
            boolean whole=selector>=literal.size();int selected=whole?selector-literal.size():selector,sourceOpaque=0;
            var retained=new ArrayList<com.openggf.graphics.SpritePresentation.Tile>();
            for(int i=0;i<literal.size();i++) {
                var p=literal.get(i);boolean omit=whole?p.piece()==selected:i==selected;
                if(!omit){retained.add(picture.tiles().get(i));continue;}
                var decoded=new com.openggf.level.Pattern();decoded.fromSegaFormat(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(residency,0x6CB+p.offset()).copy());
                for(int y=0;y<8;y++)for(int x=0;x<8;x++)if((decoded.getPixel(x,y)&15)!=0)sourceOpaque++;
            }
            var partial=renderDiagonalWorldPicture(module,checkpointWorldFrame(retained,picture),contrastRgb);
            if(sourceOpaque==0){assertArrayEquals(full.pixels(),partial.pixels(),"Genuine empty source cell supplies no invented omission witness");continue;}
            assertFalse(Arrays.equals(full.pixels(),partial.pixels()),"Actual renderer omission witness cell/piece "+selector);
            assertThrows(AssertionError.class,()->assertCheckpointWorldPixels(picture,partial,terrain,residency,base,literal));
            if(whole)witnessedPieces++;else witnessedCells++;
        }
        assertTrue(witnessedCells>1);assertEquals(pieceCount,witnessedPieces);
    }

    private static com.openggf.graphics.SpritePresentation.Frame checkpointWorldOnly(com.openggf.graphics.SpritePresentation.Frame full,int base) {
        assertTrue(full.primitives().isEmpty());return checkpointWorldFrame(full.tiles().stream().filter(t->t.patternId()>=base && t.patternId()<base+12).toList(),full);
    }
    private static com.openggf.graphics.SpritePresentation.Frame checkpointWorldFrame(List<com.openggf.graphics.SpritePresentation.Tile> tiles,
            com.openggf.graphics.SpritePresentation.Frame source) {
        var versions=new java.util.HashMap<Integer,com.openggf.graphics.SpritePresentation.PatternVersion>();
        for(var tile:tiles)versions.put(tile.patternId(),source.patternVersions().get(tile.patternId()));
        return new com.openggf.graphics.SpritePresentation.Frame(tiles,List.of(),versions);
    }

    // END constructed Checkpoint World proof.
    // Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>

    // BEGIN constructed Checkpoint -> actual native death -> fresh World restart proof.
    /**
     * ONE initial component cut, then only ordinary GameLoop/controller calls. Native source pin
     * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Spikes/Main:36-83;
     * Player Object Collision:229-298; Sonic/Main:2397-2494; Load Saved Data:44-87;
     * PP Scroll:61-170; Load Level Data:37-39; Section Art Load:7-22.
     * ENGINE actual-USA EGL evidence, never native pixel/timing parity or a natural checkpoint route.
     */
    @Test void actualEglConstructedCheckpointContactDiesNativelyRestartsLoadsAndReplaysOnlyTheFreshEpoch() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual EGL/media opt-in");
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        String media = System.getProperty("soniccd.media.root"); assertNotNull(media); assertFalse(media.isBlank());
        CdGameDataSource source;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); source = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
        String bootLog = InputScriptCompiler.compileToInputLog("2 -");
        var bootInputs = new Bk2MovieLoader().parseInputLogText(Path.of("ENGINE-checkpoint-restart-two-boot-rows"), bootLog.lines().toList());
        // Finite failure budget, NOT a claimed native restart call or a fitted gameplay clock.
        String sceneLog = InputScriptCompiler.compileToInputLog("512 -");
        var movie = new Bk2MovieLoader().parseInputLogText(Path.of("ENGINE-checkpoint-native-death-neutral-bound"), sceneLog.lines().toList());
        assertEquals(2, bootInputs.getFrameCount()); assertEquals(512, movie.getFrameCount());
        try (var boot = new HeadlessGameBoot(320, 224)) {
            try {
                var loop = boot.bootSonicCdOpeningObjects(source); var mode = SessionManager.getCurrentGameplayMode();
                var module = assertInstanceOf(SonicCdGameModule.class, mode.getWorldSession().getGameModule());
                var old = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                var oldFrame = module.requirePlayerFrame(); var oldActor = module.requirePlayerActor();
                assertSame(source, mode.getWorldSession().getDataSource()); assertSame(source.assets(), oldFrame.assets());
                assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
                assertNull(GameServices.audio().captureLogicalSnapshot().presentation().activeMusic());
                for (int row = 0; row < 2; row++) step(loop, bootInputs.getFrame(row));
                assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, old.phase()); oldActor.requireCompletedNativeFrame();
                assertEquals(2, oldFrame.state().motion().routine()); assertEquals(0, oldFrame.state().timers().hurt());
                assertEquals(0, oldFrame.state().timers().reset());
                assertEquals(0, oldFrame.state().objectLinks().standObject()); assertEquals(0, oldFrame.state().objectLinks().pushObject());
                assertEquals(0, oldFrame.environment().globals().debugCheat()); assertEquals(0, oldFrame.environment().globals().debugMode());
                assertEquals(0, oldFrame.environment().globals().invincible()); assertEquals(0, oldFrame.environment().movement().gates().miniSonic());
                assertEquals(0, old.contactGlobals().timeWarp()); assertEquals(0, old.contactGlobals().shield()); assertEquals(0, old.contactGlobals().combineRing());
                assertEquals(0, old.ringGlobals().rings()); assertEquals(3, old.ringGlobals().lives());
                assertEquals(6, old.ringGlobals().ringAnimationTimer()); assertEquals(1, old.ringGlobals().ringAnimationFrame());
                assertEquals(0, old.levelGlobals().checkpoint()); assertEquals(0, old.levelGlobals().spawnMode());
                assertEquals(3, old.levelGlobals().plcLoadFlags() & 3, "Actual title-loaded skip branch required for checkpoint restart");
                var coldLive = old.checkpointState().live(); assertEquals(0xFF, coldLive.knownMask());
                for (var field : com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.values())
                    assertEquals(0, com.openggf.game.soniccd.objects.CdCheckpointLiveAux.read(coldLive, field), "Actual represented clear/raw DEV0 " + field);

                // The SAME initial cut/provenance as C27, plus an actual owned upright native hazard.
                // No routine/map/tile/collision/active/bit7/child/helper/result ingress.
                var pool = old.placement().pool(); pool.clearDynamicSlots();
                assertEquals(96, pool.capture().slots().size());
                assertTrue(pool.capture().slots().stream().allMatch(s -> Arrays.equals(new byte[64], s.copyBytes())));
                int poleX = GameServices.camera().getX() + 160, poleY = GameServices.camera().getY() + 56;
                pool.write(pool.state(40).moveByte(ID, 0x13).moveWord(X, poleX).moveWord(Y, poleY)
                        .moveByte(SUBTYPE, 2).moveByte(SUBTYPE2, 0).moveByte(FLAGS, 0).moveByte(SPRITE_FLAGS, 0));
                pool.write(pool.state(48).moveByte(ID, 0x26).moveWord(X, poleX).moveWord(Y, poleY + 8)
                        .moveByte(SUBTYPE, 0).moveByte(SUBTYPE2, 0).moveByte(FLAGS, 0).moveByte(SPRITE_FLAGS, 0));
                stageCheckpointWorldPlayer(module, (poleX << 16) | 0xABCD, ((poleY - 24) << 16) | 0x1234); // ONE restore/rebind.
                var terrain = new com.openggf.game.soniccd.player.CdPlayerTerrain(oldFrame.assets().terrain());
                // Initial feet at PoleY-5; Solid's actual stand feet at (PoleY+8)-8 = PoleY.
                // If either footprint is obstructed, STOP: no alternate pose, height, hazard or terrain tuning.
                for (int foot : new int[]{poleX - 9, poleX + 9}) for (int footY : new int[]{poleY - 5, poleY})
                    assertTrue(terrain.floor(foot & 0xFFFF, footY & 0xFFFF, 13, 0, 16, 4).distance() >= 0,
                            "Fixed initial/actual Solid terrain prerequisite; no fallback construction");
                for (int slot : new int[]{40, 48}) {
                    assertEquals(0, pool.state(slot).routine()); assertEquals(0, pool.state(slot).longBits(MAPPINGS));
                    assertEquals(0, pool.state(slot).unsignedByte(COLLISION_TYPE)); assertEquals(0, pool.state(slot).savedFlagsId());
                }
                var initial = capture(module); var initialRing = old.ringGlobals();
                var registry = mode.getRewindRegistry(); long generation = GameServices.level().getCompletedProductionLoadGeneration();
                int base = com.openggf.graphics.PatternAtlasRange.OBJECTS.base(); var banks = new CdOpeningObjectRuntime.LogicalBanks(base, base + 24, base + 38);
                assertEquals(base + 567, banks.checkpoint()); var objects = source.assets().openingObjects();
                var reached = new java.util.TreeSet<Integer>(); int aeRequests = 0;
                int deathRow = -1, goneRow = -1, restartRow = -1, newVisitRow = -1;
                Capture savedCut = null, beforeRestart = null, retired = null;
                com.openggf.game.rewind.CompositeSnapshot retiredFull = null, retiredCourse = null;
                var freshInputs = new ArrayList<Bk2FrameInput>(); var freshStates = new ArrayList<Capture>();
                var freshPixels = new ArrayList<RgbaImage>();
                var freshFull = new ArrayList<com.openggf.game.rewind.CompositeSnapshot>();
                var freshCourse = new ArrayList<com.openggf.game.rewind.CompositeSnapshot>();
                for (int row = 1; row <= movie.getFrameCount(); row++) {
                    var before = capture(module); var input = movie.getFrame(row - 1);
                    step(loop, input); // Sole authority after the one initial component cut.
                    var runtime = assertInstanceOf(CdOpeningObjectRuntime.class, module.getGameService(NativeDynamicObjectPass.class));
                    var frame = module.requirePlayerFrame(); var state = frame.state(); var motion = state.motion();
                    assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase());
                    assertSame(source, mode.getWorldSession().getDataSource()); assertSame(source.assets(), frame.assets());
                    assertSame(module, mode.getWorldSession().getGameModule(), "Stable module owns a fresh per-load Runtime/Frame/actor visit");
                    if (runtime == old) {
                        assertEquals(generation, GameServices.level().getCompletedProductionLoadGeneration()); assertFalse(old.closed());
                        assertEquals(initial.levelFrames() + row, GameServices.level().getFrameCounter());
                        assertEquals(initial.shared().vIntClock().absoluteValue().orElseThrow() + row, frame.vIntClock().absoluteValue().orElseThrow());
                        int ae = (int) runtime.lastSoundRequests().stream().filter(CdOpeningObjectRuntime.CheckpointFm.class::isInstance).count();
                        aeRequests += ae; assertEquals(row == 2 ? 1 : 0, ae, "AE is reached only after actual SaveData completion");
                        assertEquals(coldLive, runtime.checkpointState().live(), "Ordinary rows omit HUD time and do not invent water writers");
                        if (row <= 2) {
                            assertEquals(2, motion.routine(), "No hazard death before/at actual E3 checkpoint contact");
                            assertEquals(0, frame.state().objectLinks().standObject()); assertEquals(0, pool.state(48).flags() & 8);
                            assertEquals((poleX << 16) | 0xABCD, motion.centreX16_16());
                            assertEquals(((poleY - 24) << 16) | (row == 1 ? 0x1234 : 0x4A34), motion.centreY16_16());
                            assertEquals(0, pool.state(48).unsignedByte(COLLISION_TYPE), "Upright Spikes is direct Solid/Hurt, never a scanner hazard");
                            if (row == 1) {
                                assertEquals(initial.objects().checkpointState(), runtime.checkpointState());
                                assertEquals(0, runtime.levelGlobals().checkpoint()); assertEquals(0, runtime.levelGlobals().spawnMode());
                                assertEquals(0xE3, pool.state(40).unsignedByte(COLLISION_TYPE)); assertEquals(0, pool.state(40).unsignedByte(CHECKPOINT_ACTIVE));
                                assertEquals(4, pool.state(32).routine(), "Earlier32's actual child allocation is not dispatched in Init's row");
                            } else {
                                assertEquals(2, runtime.levelGlobals().checkpoint()); assertEquals(1, runtime.levelGlobals().spawnMode());
                                assertEquals(1, pool.state(40).unsignedByte(CHECKPOINT_ACTIVE)); assertEquals(0, pool.state(40).unsignedByte(COLLISION_TYPE));
                                assertEquals(new CdOpeningObjectRuntime.CheckpointFm(com.openggf.game.soniccd.objects.CdCheckpoint.FmSound.FM_CHECKPOINT_AE),
                                        runtime.lastSoundRequests().stream().filter(CdOpeningObjectRuntime.CheckpointFm.class::isInstance).findFirst().orElseThrow());
                                assertCheckpointWorldSaved(module, before); savedCut = capture(module);
                            }
                        }
                        if (row > 2) {
                            assertNotNull(savedCut); assertSame(savedCut.objects().checkpointState().saved(), runtime.checkpointState().saved());
                            assertEquals(2, runtime.levelGlobals().checkpoint()); assertEquals(1, runtime.levelGlobals().spawnMode());
                        }
                        if (deathRow < 0 && motion.routine() == 6) {
                            deathRow = row;
                            // Native ObjMove uses prior YV, then gravity+$38. With fraction1234 and SpikeY=PoleY+8,
                            // rows1..5 stay above the29-pixel Solid overlap; row6 first stands at SpikeY-27.
                            assertEquals(6, deathRow); assertNotNull(savedCut); assertEquals(0, runtime.ringGlobals().rings());
                            assertEquals(0x18, motion.animation()); assertEquals(-0x700, motion.yVelocity());
                            assertEquals(0, motion.xVelocity()); assertEquals(0, motion.groundVelocity());
                            assertEquals((poleY - 19) << 16 | 0x5A34, motion.centreY16_16(), "Actual Solid WORD snap retains fraction; post-Solid rewind sees YV0");
                            assertEquals(48, frame.state().objectLinks().standObject()); assertEquals(8, pool.state(48).flags() & 8);
                            assertEquals(0x8000, state.header().tile() & 0x8000); assertEquals(0, state.header().priority());
                            assertTrue(runtime.lastSoundRequests().contains(new CdOpeningObjectRuntime.PlayerSound(
                                    new CdPlayerFrameRuntime.ContactFmSound(com.openggf.game.soniccd.player.CdPlayerTouchResponse.FmSound.FM_HURT))),
                                    "Actual shared KillPlayer FM93, no helper/result replay");
                            assertEquals(3, runtime.ringGlobals().lives());
                        }
                        if (goneRow < 0 && motion.routine() == 8) {
                            goneRow = row; assertTrue(goneRow > deathRow && deathRow > 2);
                            assertEquals(60, state.timers().reset()); assertEquals(2, runtime.ringGlobals().lives());
                            assertEquals(initialRing.updateHudLives() + 1, runtime.ringGlobals().updateHudLives());
                        } else if (goneRow >= 0) {
                            assertEquals(8, motion.routine()); assertEquals(60 - (row - goneRow), state.timers().reset(), "Actual native reset WORD counts once per ordinary call");
                        }
                        if (runtime.levelGlobals().levelRestart() != 0) {
                            restartRow = row; assertEquals(goneRow + 60, restartRow); assertNotNull(beforeRestart);
                            assertEquals(1, runtime.levelGlobals().levelRestart()); assertEquals(0, state.timers().reset());
                            retired = capture(module); retiredFull = registry.capture(); retiredCourse = registry.captureCourse();
                            assertArrayEquals(beforeRestart.cpu().bytes(), retired.cpu().bytes()); assertEquals(beforeRestart.cpu().provenance(), retired.cpu().provenance());
                            assertEquals(beforeRestart.camera(), retired.camera()); assertScroll(beforeRestart.scroll(), retired.scroll());
                            assertEquals(beforeRestart.objects().ringGlobals(), retired.objects().ringGlobals(), "Restart branch skips ordinary animation tail");
                            assertEquals(beforeRestart.objects().sectionArtState(), retired.objects().sectionArtState());
                            assertCheckpointWorldCapture(retired, module); render(module, true); assertCheckpointWorldCapture(retired, module);
                        }
                        int ordinaryRows = row - (restartRow == row ? 1 : 0);
                        assertEquals(new com.openggf.game.soniccd.objects.CdRingGlobals(initialRing.rings(), goneRow < 0 ? 3 : 2,
                                initialRing.livesFlags(), initialRing.updateHudRings(), initialRing.updateHudLives() + (goneRow < 0 ? 0 : 1),
                                initialRing.timeStopTimer(), (6 - ordinaryRows) & 7, (1 + (ordinaryRows + 1) / 8) & 3), runtime.ringGlobals(),
                                "Natural Ring tail before retirement, with all six other represented fields accounted for");
                        if (motion.routine() == 8 && state.timers().reset() == 1) beforeRestart = capture(module);
                        if (row <= 44) {
                            var completed = capture(module); var image = render(module, true); assertNativeGpuBackground(module);
                            var published = capture(module).publication().published(); var terrainImage = renderTerrain(module, published);
                            assertTerrainOutsideSprites(image, terrainImage, runtime.displayedFrame());
                            if (row >= 2) {
                                var priorPole = before.objects().placement().pool().slots().get(40 - 32);
                                var priorBall = row >= 3 ? before.objects().placement().pool().slots().getFirst() : null;
                                var literal = checkpointWorldLiteralTiles(priorPole, priorBall, published.cameraX(), published.cameraY());
                                assertCheckpointWorldPixels(runtime.displayedFrame(), image, terrainImage, runtime.sectionArtState().residency(), banks.checkpoint(), literal);
                                for (var cell : literal) {
                                    reached.add(cell.offset()); var atlas = GameServices.graphics().getPatternAtlas();
                                    var entry = atlas.getEntry(banks.checkpoint() + cell.offset()); assertNotNull(entry);
                                    assertTrue(glIsTexture(atlas.getTextureId(entry.atlasIndex())), "Actual current checkpoint texture on owned EGL context");
                                }
                            }
                            assertCheckpointWorldCapture(completed, module);
                        }
                    } else {
                        if (newVisitRow < 0) {
                            newVisitRow = row; assertEquals(restartRow + 1, newVisitRow); assertNotNull(retired);
                            assertTrue(old.closed()); assertTrue(oldFrame.closed()); assertNotSame(oldFrame, frame); assertNotSame(oldActor, module.requirePlayerActor());
                            assertEquals(generation + 1, GameServices.level().getCompletedProductionLoadGeneration());
                            assertCheckpointRestartLoaded(retired, module);
                        }
                        int newRow = row - newVisitRow + 1;
                        assertTrue(newRow >= 1 && newRow <= 7); assertEquals(generation + 1, GameServices.level().getCompletedProductionLoadGeneration());
                        assertEquals(newRow, GameServices.level().getFrameCounter()); assertEquals(newRow, frame.environment().globals().levelFrames());
                        assertEquals(newRow, frame.vIntClock().absoluteValue().orElseThrow());
                        assertEquals(2, frame.state().motion().routine()); assertEquals(0, frame.state().timers().reset());
                        assertEquals(2, runtime.levelGlobals().checkpoint()); assertEquals(0, runtime.levelGlobals().spawnMode());
                        assertEquals(0, runtime.levelGlobals().levelRestart()); assertEquals(0, runtime.levelGlobals().updateHudTime());
                        assertEquals(2, runtime.ringGlobals().lives()); assertEquals(0, runtime.ringGlobals().rings());
                        assertEquals((8 - newRow) & 7, runtime.ringGlobals().ringAnimationTimer()); assertEquals(1, runtime.ringGlobals().ringAnimationFrame());
                        assertEquals(0, runtime.ringGlobals().timeStopTimer()); assertEquals(0, runtime.ringGlobals().livesFlags());
                        assertEquals(retired.objects().ringGlobals().updateHudLives(), runtime.ringGlobals().updateHudLives());
                        assertEquals(retired.objects().ringGlobals().updateHudRings(), runtime.ringGlobals().updateHudRings());
                        assertSame(retired.objects().checkpointState().saved(), runtime.checkpointState().saved());
                        assertEquals(0x0000FF3B, com.openggf.game.soniccd.objects.CdCheckpointLiveAux.read(runtime.checkpointState().live(),
                                com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.TIME_LONG), "Actual raw saved0 -> frames59/seconds-byte underflow, HUD remains omitted");
                        assertCheckpointRestartSection(retired, module, newRow);
                        var stable = capture(module); var image = render(module, true);
                        assertNativeGpuBackground(module); assertTrue(Arrays.stream(image.pixels()).distinct().count() > 8);
                        assertTerrainOutsideSprites(image, renderTerrain(module, stable.publication().published()), runtime.displayedFrame());
                        assertCheckpointWorldCapture(stable, module);
                        if (newRow == 1) {
                            var staleFull = retiredFull; var staleCourse = retiredCourse;
                            assertThrows(IllegalArgumentException.class, () -> registry.restore(staleFull)); assertCheckpointWorldCapture(stable, module);
                            assertThrows(IllegalArgumentException.class, () -> registry.restoreCourse(staleCourse)); assertCheckpointWorldCapture(stable, module);
                            assertArrayEquals(image.pixels(), render(module, true).pixels()); assertCheckpointWorldCapture(stable, module);
                        }
                        freshInputs.add(input); freshStates.add(stable); freshPixels.add(image);
                        freshFull.add(registry.capture()); freshCourse.add(registry.captureCourse());
                        if (newRow == 7) break; // Do not consume the next Animals source after Boulder completes on row8.
                    }
                }
                assertEquals(1, aeRequests); assertEquals(6, deathRow); assertTrue(goneRow > deathRow);
                assertEquals(goneRow + 60, restartRow); assertEquals(restartRow + 1, newVisitRow); assertEquals(7, freshStates.size());
                assertEquals(java.util.stream.IntStream.range(0, 12).boxed().collect(java.util.stream.Collectors.toSet()), reached,
                        "Actual old-visit current12 display/pixels reached before retirement; no reconstructed postrestart glyph fallback");
                assertNotNull(savedCut); assertNotNull(retired); assertSame(savedCut.objects().checkpointState().saved(), retired.objects().checkpointState().saved());
                assertEquals(1, ((CdOpeningObjectRuntime) module.getGameService(NativeDynamicObjectPass.class)).sectionArtState().remainingTilesWord());
                assertTrue(((CdOpeningObjectRuntime) module.getGameService(NativeDynamicObjectPass.class)).displayedFrame().tiles().stream()
                        .anyMatch(t -> t.patternId() >= base && t.patternId() < base + 24), "Fresh visit actually displays Sonic through the ordinary publication");
                int replayRows = 0;
                // Indices0/1/3 mean completed fresh rows1/2/4. No restore/replay crosses the epoch boundary.
                for (int cut : new int[]{0, 1, 3}) for (boolean courseOnly : List.of(false, true)) {
                    if (courseOnly) registry.restoreCourse(freshCourse.get(cut)); else registry.restore(freshFull.get(cut));
                    assertCheckpointWorldCapture(freshStates.get(cut), module);
                    assertArrayEquals(freshPixels.get(cut).pixels(), render(module, true).pixels()); assertCheckpointWorldCapture(freshStates.get(cut), module);
                    for (int index = cut + 1; index < freshStates.size(); index++) {
                        step(loop, freshInputs.get(index)); replayRows++;
                        assertCheckpointWorldCapture(freshStates.get(index), module);
                        assertArrayEquals(freshPixels.get(index).pixels(), render(module, true).pixels(), "Fresh checkpoint full/course cut/index=" + cut + "/" + index);
                        assertCheckpointWorldCapture(freshStates.get(index), module);
                    }
                    assertEquals(generation + 1, GameServices.level().getCompletedProductionLoadGeneration());
                }
                assertEquals(28, replayRows); assertCheckpointWorldCapture(freshStates.getLast(), module);
                assertEquals(NullAudioBackend.class, GameServices.audio().getBackend().getClass());
            } finally {
                try { SessionManager.closeGameplaySession(); }
                finally { GraphicsManager.destroyForReinit(); }
            }
        }
        // No PASS/artifact output: returning requires session/graphics/owned boot cleanup as well as every assertion.
    }

    /** Scalar/native-order consequences at the FIRST actual completed new World row, never a load invocation. */
    private static void assertCheckpointRestartLoaded(Capture retired, SonicCdGameModule module) {
        var runtime = (CdOpeningObjectRuntime) module.getGameService(NativeDynamicObjectPass.class); var frame = module.requirePlayerFrame();
        var saved = retired.objects().checkpointState().saved(); var state = frame.state(); var m = state.motion();
        int x = saved.read(com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.PRIMARY_X);
        int y = saved.read(com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.PRIMARY_Y);
        int bottom = saved.read(com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.BOTTOM_BOUND);
        assertEquals(1, saved.read(com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.SPAWN_MODE));
        assertTrue(x >= 160 && x < 0x680 && y >= 0x60 && y < 0x800, "Fixed admitted PP0 SetupCamera branch, no fallback/default-start path");
        assertEquals(x << 16, m.centreX16_16()); assertEquals(y << 16, m.centreY16_16(), "Fresh cleared fractions, not the contact's ABCD/4A34");
        assertEquals(x, module.requirePlayerActor().getCentreX()); assertEquals(y, module.requirePlayerActor().getCentreY());
        assertEquals(2, m.routine()); assertEquals(0, m.xVelocity()); assertEquals(0, m.yVelocity()); assertEquals(0, m.groundVelocity());
        assertEquals(0, m.flags()); assertEquals(0, state.history().index());
        assertArrayEquals(retired.actor().state().history().bytes().copy(), state.history().bytes().copy(), "Init has no RecordPos call");
        assertArrayEquals(retired.shared().artState().residentBank().copy(), frame.artState().residentBank().copy());
        assertArrayEquals(retired.objects().powerupArtState().residentBank().copy(), runtime.powerupArtState().residentBank().copy());
        assertTrue(runtime.displayedFrame().tiles().isEmpty(), "Fresh first V-int selected the actual cleared prior SAT");
        assertNotSame(retired.actor().owner(), frame.captureActor().owner()); assertNotSame(retired.shared().owner(), frame.captureShared().owner());
        var oldScroll = assertInstanceOf(SonicCdPlayerPresentation.ScrollCapture.class, retired.scroll());
        var now = assertInstanceOf(SonicCdPlayerPresentation.ScrollCapture.class, capture(module).scroll());
        assertNotSame(oldScroll.owner(), now.owner()); assertNotSame(retired.publication().owner(), capture(module).publication().owner());
        // PP SetupCamera WORD clamps; this fixed opening cut does not reach a clamp or 3D-ramp remap.
        int cx = x - 160, cy = y - 0x60;
        assertTrue(cx < module.requirePlayerFrame().assets().loadPlan().bounds().right()); assertTrue(cy < bottom);
        assertEquals(cx, GameServices.camera().getX()); assertEquals(cy, GameServices.camera().getY());
        assertEquals(cx, now.state().cameraX()); assertEquals(cy << 16, now.state().cameraY16_16());
        assertEquals(bottom, frame.environment().movement().bounds().bottom()); assertEquals(bottom, now.state().destBottomBound());
        assertEquals(0, now.state().scrollXDiff()); assertEquals(0, now.state().scrollYDiff());
        assertEquals(cx << 16, now.copies().fgX16_16()); assertEquals(cy << 16, now.copies().fgY16_16());
        assertEquals(0x0400, now.copies().fgFlags(), "Actual load-scroll forced FG HIGH flag is copied by first V-int before ordinary scroll clears it");
        assertEquals(0, now.state().scrollFlags());
        var selected = capture(module).publication().published(); assertNotNull(selected);
        assertEquals(cx, selected.cameraX()); assertEquals(cy, selected.cameraY());
        assertNotEquals(saved.read(com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.FG_Y), cy,
                "Saved cameras are intermediate LoadData WORD stores; actual SetupCamera follows restored XY");
        var bg = now.background();
        assertEquals((cx >>> 1) << 16, bg.cameraBgX()); assertEquals((3 * (cx >>> 4)) << 16, bg.cameraBg2X());
        assertEquals((3 * (cx >>> 3)) << 16, bg.cameraBg3X());
        assertEquals(cy << 16, bg.cameraBgY()); assertEquals(cy << 16, bg.cameraBg2Y()); assertEquals(cy << 16, bg.cameraBg3Y());
        assertEquals((cy << 16) | cy, bg.vscroll());
        int[] cloudSpeeds = {0x10000, 0xE000, 0xC000, 0xA000, 0x8000, 0x6000, 0x4800, 0x4000, 0x2800, 0x2000,
                0x2000, 0x4000, 0x8000, 0xC000, 0x10000, 0xC000, 0x8000, 0x4000, 0x2000};
        byte[] clouds = new byte[512];
        for (int i = 0; i < cloudSpeeds.length; i++) checkpointWorldPut(clouds, i * 4, 4, cloudSpeeds[i] * 2);
        assertArrayEquals(clouds, bg.cloudRam().copy(), "ClearFlowers/Init19 then one actual load scroll plus first ordinary scroll, no fabricated load V-int");
        assertEquals(1, frame.vIntClock().absoluteValue().orElseThrow()); assertEquals(1, GameServices.level().getFrameCounter());
        assertEquals(7, runtime.ringGlobals().ringAnimationTimer()); assertEquals(1, runtime.ringGlobals().ringAnimationFrame(),
                "New-visit ClearFlowers clears carried timer/frame BEFORE its first actual ordinary tail");
        assertEquals(0, runtime.ringGlobals().timeStopTimer(), "ClearFlowers owns this WORD too; actual old value is0, no dirty ingress API is invented");
        assertEquals(0xFF, runtime.checkpointState().live().knownMask());
        for (var field : com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.values()) if (field != com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.TIME_LONG)
            assertEquals(0, com.openggf.game.soniccd.objects.CdCheckpointLiveAux.read(runtime.checkpointState().live(), field), "Actual represented water clear plus R11A routine restore " + field);
        assertEquals(0, runtime.levelGlobals().updateHudTime()); assertEquals(0, runtime.levelGlobals().spawnMode());
        assertEquals(0, runtime.levelGlobals().levelRestart()); assertEquals(2, runtime.levelGlobals().checkpoint());
    }

    /** Inspect the actual append/prepare/service state; never call LoadSection/prepare/decode to manufacture it. */
    private static void assertCheckpointRestartSection(Capture retired, SonicCdGameModule module, int newRow) {
        var runtime = (CdOpeningObjectRuntime) module.getGameService(NativeDynamicObjectPass.class); var section = runtime.sectionArtState();
        var plan = module.requirePlayerFrame().assets().loadPlan(); var selected = plan.sections().getFirst();
        assertEquals(0x680, selected.rightThresholdWord()); assertEquals(2, selected.initialPlcId()); assertEquals(8, selected.updatePlcId());
        assertEquals(retired.objects().sectionArtState().sectionWord() & 0xFF, section.sectionWord(), "LoadSectionArt writes the section HIGH byte once; ordinary section0 update preserves it");
        var list = plan.plcs().get(selected.initialPlcId()); assertEquals(2, list.nativeId()); assertEquals(7, list.entries().size());
        int[] destinations = {0x6E80, 0x7100, 0x7400, 0x8120, 0x8480, 0x8820, 0x9EE0};
        var decoder = section.decoder().orElseThrow();
        assertEquals(list.entries().getFirst().sourceRange().nativeAddress(), decoder.nativeAddress());
        assertEquals(19, decoder.headerWord() & 0x7FFF); assertEquals(19 - 3 * (newRow - 1), section.remainingTilesWord());
        assertEquals(section.remainingTilesWord(), decoder.remainingTiles()); assertEquals(decoder.sourcePointer(), section.queue().headSource());
        assertEquals(0x6E80 + 96 * (newRow - 1), section.queue().headDestination());
        byte[] queue = new byte[96];
        for (int i = 0; i < list.entries().size(); i++) {
            var entry = list.entries().get(i); assertEquals(destinations[i], entry.vramAddress());
            long sourceAddress = i == 0 ? decoder.sourcePointer() : entry.sourceRange().nativeAddress();
            checkpointWorldPut(queue, i * 6, 4, (int) sourceAddress);
            checkpointWorldPut(queue, i * 6 + 4, 2, i == 0 ? section.queue().headDestination() : destinations[i]);
        }
        assertArrayEquals(queue, section.queue().bytes().copy(), "Exactly one complete initial-list APPEND, no update-list replacement or duplicate entries");
        assertFalse(section.stopped());
        if (newRow == 1) {
            assertEquals(0, section.processBudgetWord()); assertEquals(19, section.remainingTilesWord());
            assertEquals(retired.objects().sectionArtState().residency(), section.residency(), "LoadSection APPEND and first-tail prepare write no VRAM; exact current-art carry");
            assertTrue(decoder.sourcePointer() > decoder.nativeAddress(), "First actual ordinary ProcessPLCs prepared the real header/table/prefetch");
        } else assertEquals(0, section.processBudgetWord(), "Each completed3-tile service drains its native budget WORD to0");
        assertEquals(0, section.hintWord()); assertEquals(0, section.padWord());
        for (int tile = 0; tile < 12; tile++) assertArrayEquals(
                com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(retired.objects().sectionArtState().residency(), 0x6CB + tile).copy(),
                com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(section.residency(), 0x6CB + tile).copy(),
                "Current checkpoint12 survive actual Boulder service; no cold preload/default glyph fallback " + tile);
    }
    // END constructed Checkpoint restart proof; original17 tests and all inherited bytes remain untouched.


    // BEGIN constructed Anton World proof. All literal oracles and fixture prerequisites are UNRUN.
    /**
     * Immutable native authority 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
     * ObjAnton_Init/Place/Main (Anton/Main.asm:24-94), Ani_Anton (Data/Animations.asm:1-5),
     * MapSpr_Anton (Data/Mappings.asm:1-109), DrawObjects (Object Functions.asm:243-361).
     * Root owns compile/RED/positive/review/R2/import and the independent natural controller604 route.
     */
    @Test void actualEglConstructedAntonWorldUsesServicedCurrentPixelsReplaysAndRetires601Patterns() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("soniccd.opening.egl"), "Explicit actual EGL/media opt-in");
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"));
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        String media = System.getProperty("soniccd.media.root"); assertNotNull(media); assertFalse(media.isBlank());
        CdGameDataSource source;
        try (var files = Files.list(Path.of(media).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); source = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
        String prefixLog = InputScriptCompiler.compileToInputLog("649 R");
        var prefix = new Bk2MovieLoader().parseInputLogText(Path.of("ENGINE-Anton-ordinary-controller-649-prerequisite"), prefixLog.lines().toList());
        String sceneLog = InputScriptCompiler.compileToInputLog("40 -");
        var movie = new Bk2MovieLoader().parseInputLogText(Path.of("ENGINE-constructed-Anton-neutral-rows"), sceneLog.lines().toList());
        assertEquals(649, prefix.getFrameCount()); assertEquals(40, movie.getFrameCount());
        String construction = "ENGINE constructed actual-USA R11A Anton World, ALL author oracles UNRUN. Constructed scene is separate from the ordinary controller prefix; no emulator/native parity or natural enemy-contact claim.\n"
                + "Real USA HeadlessGameBoot/GameLoop/NullAudioBackend; ordinary heldRight649 prefix, no state/section/queue/readiness/service changes. Cold canonical409..41E remain UNKNOWN. After649 require all22 actual current tiles byte-equal to SAME cached TEN-source ART_ANTON, canonicalPLC9,row1/8120. Java484 and native-reader485 remain different finite extents; no new decode/upload/preload/fallback producer.\n"
                + "Eight independent scenes subtype00/FF x raw placement flips0..3 restore the SAME warmed full cut. Exactly ONE initial CLOSED_CUT per scene: clear96 dynamics, preserve actual placement cursors/saved/reserved storage, stage whole40 ID3F, primary subtype, secondary0,savedID0, raw placement flip flags and TEST-only fractional XY ingress ABCD/1234 (not a native placement observation) only. No routine/map/frame/tile/angle/delta setter. One initial restore/rebind of SAME completed Sonic at its warmed X2743, independently queried two-foot surface-19, neutral supported pose (no camera-forward/future-object admission); retain actual animation cursor/header/timers/history/links/owner. Actual owned terrain must support actor feet and every prescribed Anton probe; no fake ground/gates, retry position or terrain mutation.\n"
                + "Root R2 actual terrain fixture: after649 x=currentCameraX+240=2875, probeY=currentCameraY+112=763; real floor distance-2/raw angle08. Fixed shaped support, no search/retry or terrain edits. Surface=probeY+distance; Anton starts surface-21. Real floor at surface-1/0/+1/+2 must return +1/0/-1/-2. Rows1/2 Init+Place stay routine0; row3 negative Place0->2; row4 negative Place2->4; full Y+10000 each, fractions retained, NO Place snap. Row5 first full Xdelta Main/floor Y-WORD correction/shared animation. Literal flat support at ALL reached X probes and Sonic feet is a required fixture predicate, never fitted production.\n"
                + "40 ordinary neutral GameLoop rows only after ingress. Same existing complete neutral/no-touch Body sound oracle (including reached ChargeStop), no Anton sound/scanner imitation. All4 genuine mapping frames/two scripts/all22 selected current tiles across scenes; native bit7, five-piece SAT coordinates/attributes/order, both priorCPU/NOW displayed conversion and currentCPU/NOW conversion checked against captured actual residency. Actual primaryAngle is last reached Anton floor probe, not Sonic angle; component RuntimeCAS/cursor proof owns store ordering/count. World angle0 cannot prove each same-value synchronous store or its count.\n"
                + "Ordered current-palette World pixels over terrain+actual nativePlaneB, actual isolated renderer, explicitly TEST contrast same-renderer cell/piece/all-Anton omission controls; no hand-pixel-only negative. Full/course cuts0,1,3,4,5,6,first frame change restore state+pixels and forward through40 with NO ingress/rebind/reload/effect replay. Physical alias/dirty opaque limits stay component proof; no artificial World producer write or fabricated handler boundary.\n"
                + "ALL601 actual atlas entries installed before module close; transparent TEST uploads allowed ONLY for missing old579, never Anton pixels. All601 absent after module close, actual shared-producer entry identities retained. 601 is installed retirement extent, not all601 scene-rendered. Optional unchanged ENGINE PNG/input/construction/outcome output ONLY after retirement AND finally session/graphics/boot cleanup. Fixed predicate failures are RootR2 fixture prerequisites, never a fallback route or a production fit.\n";
        var observations = new StringBuilder(); var artifacts = new java.util.LinkedHashMap<String,RgbaImage>();
        int[] nativeFirst = {0x780,0x7AE,0x3A0,0x424,0x441,0x680,0x6C6,0x6D7,0x544,0x334,0x520,0x328,0x5A8,0x374,0x490,0x320,0x6CB,0x409};
        int[] counts = {24,14,70,28,63,70,5,39,36,15,36,12,82,19,46,8,12,22};
        assertEquals(18,nativeFirst.length); assertEquals(18,counts.length); assertEquals(601,Arrays.stream(counts).sum());
        try (var boot = new HeadlessGameBoot(320,224)) {
            try {
                var loop = boot.bootSonicCdOpeningObjects(source); var mode = SessionManager.getCurrentGameplayMode();
                var module = assertInstanceOf(SonicCdGameModule.class,mode.getWorldSession().getGameModule());
                var runtime = assertInstanceOf(CdOpeningObjectRuntime.class,module.getGameService(NativeDynamicObjectPass.class));
                var frame = module.requirePlayerFrame(); var objects = source.assets().openingObjects();
                assertSame(source,mode.getWorldSession().getDataSource()); assertEquals(GameMode.LEVEL,loop.getCurrentGameMode());
                assertEquals(NullAudioBackend.class,GameServices.audio().getBackend().getClass());
                assertNull(GameServices.audio().captureLogicalSnapshot().presentation().activeMusic());
                var sources = objects.sectionArtSources(); var producerIdentities = sources.sources(); assertEquals(10,producerIdentities.size());
                var antonSource = sources.source(com.openggf.game.soniccd.level.CdR11aAsset.ART_ANTON);
                assertEquals(9,antonSource.canonicalPlcId()); assertEquals(1,antonSource.canonicalRowIndex());
                assertEquals(0x8120,antonSource.canonicalPlc().vramAddress()); assertEquals(22,antonSource.tileCount());
                assertEquals(484,antonSource.decode().sourceBlobRange().length()); assertEquals(485,antonSource.readerInputRange().length());
                assertEquals(704,antonSource.decode().bytes().length()); assertEquals(485,antonSource.readerBytes().length());
                var cold = runtime.sectionArtState().residency();
                assertArrayEquals(new byte[704],cold.region(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.AT_8120).knownMask().copy());
                for(int tile=0;tile<22;tile++) {
                    final int pattern = 0x409+tile;
                    var boundary = assertThrows(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.OwnershipBoundary.class,
                            () -> com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(cold,pattern));
                    assertEquals(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Boundary.UNKNOWN_PATTERN,boundary.boundary());
                    assertEquals(pattern,boundary.nativePatternId());
                }
                for(int row=0;row<649;row++) {
                    step(loop,prefix.getFrame(row));
                    assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT,runtime.phase(),"Ordinary completed controller prerequisite row "+(row+1));
                }
                // Runtime-observed old frontier649 is a TEST construction bound, never production authority.
                assertAntonWorldCurrentSource(runtime,objects,sources,producerIdentities,antonSource);
                assertEquals(2,frame.state().motion().routine()); assertEquals(0,frame.state().timers().hurt());
                assertEquals(0,frame.state().objectLinks().standObject()); assertEquals(0,frame.state().objectLinks().pushObject());
                int base = com.openggf.graphics.PatternAtlasRange.OBJECTS.base();
                var banks = new CdOpeningObjectRuntime.LogicalBanks(base,base+24,base+38,base+108,base+136,base+199,
                        base+269,base+274,base+313,base+349,base+364,base+400,base+412,base+494,base+513,base+559,base+567,base+579);
                assertEquals(banks,new CdOpeningObjectRuntime.LogicalBanks(base,base+24,base+38,base+108,base+136,base+199,
                        base+269,base+274,base+313,base+349,base+364,base+400,base+412,base+494,base+513,base+559,base+567));
                assertEquals(banks,new CdOpeningObjectRuntime.LogicalBanks(base,base+24,base+38));
                assertEquals(banks.checkpoint()+12,banks.anton()); assertEquals(base+579,banks.anton());
                var policy = monitorWorldPolicy(module,objects,banks); var atlas = GameServices.graphics().getPatternAtlas(); int logical = base;
                for(int span=0;span<18;span++) for(int tile=0;tile<counts[span];tile++) {
                    int id = logical++; assertEquals(id,policy.resolve(nativeFirst[span]+tile).logicalPatternId());
                    assertEquals(1,atlas.registeredRangesForTesting().stream().filter(r->id>=r.base() && id<r.base()+r.size()).count());
                }
                assertEquals(base+601,logical);
                assertEquals(4,objects.anton().mappingFrames().size()); assertEquals(2,objects.anton().animations().size());
                assertArrayEquals(java.util.HexFormat.of().parseHex("00040008030001ff070203ff"),
                        objects.view(com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.ANTON_ANIMATIONS).bytes().copy());
                assertEquals(0x20B3AA,objects.view(com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.ANTON_MAPPINGS).range().nativeAddress());
                assertArrayEquals(java.util.HexFormat.of().parseHex(
                        "00080022003c005605ed050000f4fd080004f405040007fc04050009f8fc0000110e"
                        +"05ec050000f4fc080004f404040007fc0405000df8fb00001112"
                        +"05ed050012f4fd080004f405040007fc04050009f8fc0000110e"
                        +"05ec050012f4fc080004f404040007fc0405000df8fb00001112"),
                        objects.view(com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.ANTON_MAPPINGS).bytes().copy());
                var registry = mode.getRewindRegistry(); var warmFull = registry.capture(); var warm = capture(module);
                assertEquals(2,runtime.levelGlobals().checkpoint());assertEquals(1,runtime.levelGlobals().spawnMode());
                byte[] savedKnown=new byte[31];Arrays.fill(savedKnown,(byte)1);assertArrayEquals(savedKnown,runtime.checkpointState().saved().knownMask().copy());
                artifacts.put("ENGINE-controller-Right-call649-natural-checkpoint-save.png",render(module,true));
                assertAntonWorldCapture(warm,module); // Capture before any constructed scene; drawing changes no gameplay owner.
                var reachedTiles = new java.util.TreeSet<Integer>(); var reachedFrames = new java.util.TreeSet<Integer>();
                for(int subtype : new int[]{0,0xFF}) for(int flip=0;flip<4;flip++) {
                    registry.restore(warmFull); assertAntonWorldCapture(warm,module);
                    assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT,runtime.phase());
                    assertEquals(0,runtime.devGlobals().goodFuture()); assertEquals(0,runtime.contactGlobals().timeWarp());
                    assertEquals(0,frame.environment().globals().invincible()); assertEquals(0,frame.environment().globals().debugMode());
                    assertEquals(0,frame.environment().globals().debugCheat()); assertEquals(0,frame.environment().movement().gates().miniSonic());
                    assertEquals(0,frame.environment().movement().gates().speedShoes());
                    // Root R2 fixed real controller649 terrain: x2875/probe763, source slope angle08.
                    // This fixture uses the existing pure terrain owner for a detached support vector, never Anton output.
                    int x = GameServices.camera().getX()+240, probeY = GameServices.camera().getY()+112;
                    var terrain = new com.openggf.game.soniccd.player.CdPlayerTerrain(frame.assets().terrain());
                    var origin = terrain.floor(x,probeY,13,0,16,4|flip);
                    assertEquals(2875,x);assertEquals(763,probeY);assertEquals(-2,origin.distance());
                    assertEquals(java.util.OptionalInt.of(8),origin.angleWrite());
                    int surface = probeY+origin.distance();
                    assertTrue(surface-GameServices.camera().getY()>=96 && surface-GameServices.camera().getY()<=176);
                    assertAntonWorldFloor(frame.assets().terrain(),terrain,x,surface,0,4|flip);
                    int delta = subtype==0 ? -0x10000 : -0x8000;
                    int[] expectedY=new int[41], expectedAngle=new int[41];expectedY[0]=surface-21;
                    for(int row=1;row<=4;row++) {
                        expectedY[row]=expectedY[row-1]+1;
                        assertAntonWorldFloor(frame.assets().terrain(),terrain,x,expectedY[row]+19,2-row,4|flip);
                        expectedAngle[row]=terrain.floor(x,expectedY[row]+19,13,0,16,4|flip).angleWrite().orElseThrow();
                    }
                    for(int row=5;row<=40;row++) {
                        int probeX=(((x<<16)|0xABCD)+(row-4)*delta)>>>16;
                        var support=terrain.floor(probeX,expectedY[row-1]+19,13,0,16,4|flip);
                        assertTrue(support.distance()>=-7&&support.distance()<=7,"Fixed real slope support; no reversal/fake floor");
                        assertAntonWorldFloor(frame.assets().terrain(),terrain,probeX,expectedY[row-1]+19,support.distance(),4|flip);
                        expectedY[row]=(expectedY[row-1]+support.distance())&0xFFFF;
                        expectedAngle[row]=support.angleWrite().orElseThrow();
                    }
                    int actorX=frame.state().motion().centreX16_16()>>>16;
                    assertEquals(2743,actorX); // Preserve the warmed horizontal route/camera window; no future-object admission workaround.
                    var left=terrain.floor(actorX-9,probeY,13,0,16,4);var right=terrain.floor(actorX+9,probeY,13,0,16,4);
                    assertTrue(left.angleWrite().isPresent()&&right.angleWrite().isPresent());
                    int actorSurface=probeY+Math.min(left.distance(),right.distance());
                    assertAntonWorldFloor(frame.assets().terrain(),terrain,actorX-9,actorSurface,probeY+left.distance()-actorSurface,4);
                    assertAntonWorldFloor(frame.assets().terrain(),terrain,actorX+9,actorSurface,probeY+right.distance()-actorSurface,4);
                    var saved = runtime.placement().capture().savedFlags(); var status = runtime.placement().capture().status();
                    var reserved = runtime.captureClosedCut().reserved(); var pool = runtime.placement().pool();
                    pool.clearDynamicSlots(); assertEquals(96,pool.capture().slots().size());
                    assertTrue(pool.capture().slots().stream().allMatch(s->Arrays.equals(new byte[64],s.copyBytes())));
                    // TEST-only fractional XY ingress, not an observed native placement or a route repair.
                    pool.write(pool.state(40).moveByte(ID,0x3F).moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,0)
                            .moveByte(FLAGS,flip).moveByte(SPRITE_FLAGS,flip)
                            .moveLong(X_POSITION,(x<<16)|0xABCD).moveLong(Y_POSITION,((surface-21)<<16)|0x1234));
                    stageAntonWorldPlayer(module,actorX<<16,(actorSurface-19)<<16); // ONCE per independent scene only.
                    assertArrayEquals(saved.copy(),runtime.placement().capture().savedFlags().copy()); assertEquals(status,runtime.placement().capture().status());
                    assertReservedImages(reserved,runtime.captureClosedCut().reserved());
                    assertAntonWorldObject(pool.state(40),x,surface,subtype,flip,expectedY,0);
                    var fullCuts = new ArrayList<>(List.of(registry.capture())); var courseCuts = new ArrayList<>(List.of(registry.captureCourse()));
                    var states = new ArrayList<>(List.of(capture(module))); var pictures = new ArrayList<>(List.of(render(module,true)));
                    String scene = "subtype"+Integer.toHexString(subtype)+"-flip"+flip;
                    observations.append("scene=").append(scene).append(" x=").append(x).append(" probeY=").append(probeY)
                            .append(" realFloorDistance=").append(origin.distance()).append(" surface=").append(surface).append('\n');
                    artifacts.put("ENGINE-Anton-"+scene+"-preInit.png",pictures.getFirst());
                    for(int row=1;row<=40;row++) {
                        var previous = capture(module); long clock = frame.vIntClock().absoluteValue().orElseThrow();
                        int levelFrames = GameServices.level().getFrameCounter();
                        checkpointWorldStep(loop,movie.getFrame(row-1),module,false); // Existing actual complete neutral Body sound oracle.
                        assertEquals(clock+1,frame.vIntClock().absoluteValue().orElseThrow()); assertEquals(levelFrames+1,GameServices.level().getFrameCounter());
                        assertAntonWorldObject(pool.state(40),x,surface,subtype,flip,expectedY,row);
                        assertEquals(1,pool.capture().slots().stream().filter(s->s.id()!=0).count(),"No extra live floor owner may follow Anton");
                        assertEquals(states.getFirst().objects().contactGlobals(),runtime.contactGlobals());
                        assertEquals(states.getFirst().objects().scoreGlobals(),runtime.scoreGlobals());
                        assertEquals(states.getFirst().objects().checkpointState(),runtime.checkpointState());
                        assertArrayEquals(saved.copy(),runtime.placement().capture().savedFlags().copy());
                        assertEquals(2,frame.state().motion().routine()); assertEquals(0,frame.state().timers().hurt());
                        assertTrue(pool.state(40).centreX()-(frame.state().motion().centreX16_16()>>>16)>=96,"No actual shared scanner contact");
                        int probeBottom = (row<=4?expectedY[row]:expectedY[row-1])+19;
                        var lastProbe = terrain.floor(pool.state(40).centreX(),probeBottom,13,0,16,pool.state(40).spriteFlags());
                        assertEquals(java.util.OptionalInt.of(expectedAngle[row]),lastProbe.angleWrite());
                        assertEquals(lastProbe.angleWrite().orElse(0),frame.environment().movement().globals().primaryAngle(),
                                "Existing Frame scratch is LAST actual Anton probe, never substituted player oAngle; component CAS owns store order");
                        var completed = capture(module); var selected = completed.publication().published();
                        assertEquals(previous.publication().prepared(),selected);
                        assertAntonWorldCurrentSource(runtime,objects,sources,producerIdentities,antonSource);
                        var nowResidency = runtime.sectionArtState().residency(); var nowPolicy = monitorWorldPolicy(module,objects,banks);
                        assertEquals(com.openggf.game.soniccd.resources.CdObjectPresentation.convert(previous.cpu(),nowPolicy),
                                runtime.displayedFrame(),"Prior completed CPU SAT converted using captured NOW-serviced current art");
                        var cpuPicture = com.openggf.game.soniccd.resources.CdObjectPresentation.convert(runtime.cpuSat(),nowPolicy);
                        assertAntonWorldVersions(cpuPicture,nowResidency,banks.anton());
                        assertAntonWorldSat(runtime.cpuSat(),GameServices.camera().getX(),GameServices.camera().getY(),pool.state(40));
                        var image = render(module,true); var backdrop = renderTerrain(module,selected);
                        assertNativeGpuBackground(module); assertTerrainOutsideSprites(image,backdrop,runtime.displayedFrame());
                        if(row>=2) {
                            var prior = previous.objects().placement().pool().slots().get(40-32);
                            var literal = antonWorldTiles(prior,selected.cameraX(),selected.cameraY());
                            assertAntonWorldSat(previous.cpu(),selected.cameraX(),selected.cameraY(),prior);
                            assertAntonWorldVersions(runtime.displayedFrame(),nowResidency,banks.anton());
                            String pixels = assertAntonWorldPixels(runtime.displayedFrame(),image,backdrop,nowResidency,banks.anton(),literal);
                            reachedFrames.add(prior.unsignedByte(MAP_FRAME));
                            for(var cell:literal) {
                                reachedTiles.add(cell.offset()); int id = banks.anton()+cell.offset(); var entry = atlas.getEntry(id); assertNotNull(entry);
                                assertTrue(glIsTexture(atlas.getTextureId(entry.atlasIndex())));
                                assertEquals(runtime.displayedFrame().patternVersions().get(id),
                                        com.openggf.graphics.SpritePresentation.patternSample(GameServices.graphics(),id));
                            }
                            observations.append(scene).append(" row=").append(row).append(" routine=").append(pool.state(40).routine())
                                    .append(" frame=").append(pool.state(40).unsignedByte(MAP_FRAME)).append(" timer=").append(pool.state(40).unsignedByte(ANIMATION_TIME))
                                    .append(" primaryAngle=").append(frame.environment().movement().globals().primaryAngle()).append(' ').append(pixels).append('\n');
                            if(row==2 || row==5 || row==6 || row==(subtype==0?10:14)) {
                                var only = antonWorldOnly(runtime.displayedFrame(),banks.anton());
                                var isolated = renderDiagonalWorldPicture(module,only,null);
                                assertAntonWorldPixels(only,isolated,backdrop,nowResidency,banks.anton(),literal);
                                int contrastRgb = diagonalWorldContrastRgb(1);
                                var contrast = renderDiagonalWorldPicture(module,com.openggf.graphics.SpritePresentation.Frame.empty(),contrastRgb);
                                var glyph = renderDiagonalWorldPicture(module,only,contrastRgb);
                                assertAntonWorldPixels(only,glyph,contrast,nowResidency,banks.anton(),literal);
                                observations.append(scene).append(" row=").append(row).append(' ')
                                        .append(assertAntonWorldOmissions(module,only,glyph,contrast,nowResidency,banks.anton(),literal,contrastRgb)).append('\n');
                                assertFalse(Arrays.equals(backdrop.pixels(),renderWithoutBackground(module).pixels()),"Actual selected native Plane B contributes");
                                artifacts.put("ENGINE-Anton-"+scene+"-row"+row+".png",image);
                                artifacts.put("ENGINE-Anton-"+scene+"-nativePlane-row"+row+".png",backdrop);
                                artifacts.put("ENGINE-render-only-Anton-"+scene+"-row"+row+".png",isolated);
                            }
                        }
                        if(row==1) {
                            // Actual current CPU SAT renderer control for postInit, distinct from the production previous-SAT display.
                            var only = antonWorldOnly(cpuPicture,banks.anton()); var cpuImage = renderDiagonalWorldPicture(module,only,null);
                            assertAntonWorldPixels(only,cpuImage,backdrop,nowResidency,banks.anton(),
                                    antonWorldTiles(pool.state(40),GameServices.camera().getX(),GameServices.camera().getY()));
                            artifacts.put("ENGINE-render-only-currentCPU-Anton-"+scene+"-postInit.png",cpuImage);
                        }
                        assertArrayEquals(image.pixels(),render(module,true).pixels()); assertAntonWorldCapture(completed,module);
                        states.add(completed); pictures.add(image); fullCuts.add(registry.capture()); courseCuts.add(registry.captureCourse());
                    }
                    for(int cut : new int[]{0,1,3,4,5,6,subtype==0?9:13}) for(boolean courseOnly:List.of(false,true)) {
                        if(courseOnly) registry.restoreCourse(courseCuts.get(cut)); else registry.restore(fullCuts.get(cut));
                        assertAntonWorldCapture(states.get(cut),module); assertArrayEquals(pictures.get(cut).pixels(),render(module,true).pixels());
                        assertAntonWorldCapture(states.get(cut),module);
                        for(int row=cut+1;row<=40;row++) {
                            checkpointWorldStep(loop,movie.getFrame(row-1),module,false); // No ingress, rebind, source reload or effect replay.
                            assertAntonWorldObject(pool.state(40),x,surface,subtype,flip,expectedY,row);
                            assertAntonWorldCapture(states.get(row),module);
                            assertArrayEquals(pictures.get(row).pixels(),render(module,true).pixels(),"Anton full/course "+scene+" cut/row="+cut+"/"+row);
                            assertAntonWorldCapture(states.get(row),module);
                        }
                    }
                }
                assertEquals(java.util.Set.of(0,1,2,3),reachedFrames);
                assertEquals(java.util.stream.IntStream.range(0,22).boxed().collect(java.util.stream.Collectors.toSet()),reachedTiles);
                assertAntonWorldCurrentSource(runtime,objects,sources,producerIdentities,antonSource);
                var beforeRetirement = capture(module); int transparentWitnesses = 0;
                for(int offset=0;offset<601;offset++) {
                    int id = base+offset;
                    if(atlas.getEntry(id)==null) {
                        assertTrue(offset<579,"All22 Anton entries must come from genuine selected current pixels, never TEST uploads");
                        GameServices.graphics().cachePatternTexture(new com.openggf.level.Pattern(),id); transparentWitnesses++;
                    }
                    var entry = atlas.getEntry(id); assertNotNull(entry,"ALL601 installed retirement witness "+id);
                    assertTrue(glIsTexture(atlas.getTextureId(entry.atlasIndex())));
                }
                assertAntonWorldCapture(beforeRetirement,module);
                var shared = new java.util.HashMap<Integer,Object>();
                for(int id=0;id<=0x7FF;id++) { var entry=atlas.getEntry(id); if(entry!=null) shared.put(id,entry); }
                for(var range:atlas.registeredRangesForTesting()) for(int id=range.base();id<range.base()+range.size();id++) {
                    if(id>=base && id<base+601) continue; var entry=atlas.getEntry(id); if(entry!=null) shared.put(id,entry);
                }
                assertFalse(shared.isEmpty()); assertTrue(shared.keySet().stream().anyMatch(id->id<=0x7FF));
                observations.append("ALL601 installed, selected Anton22 actual; TEST-only missing-old579 transparent witnesses=")
                        .append(transparentWitnesses).append("; actual shared entry identities=").append(shared.size()).append('\n');
                module.closeTerrainPlayer(); assertTrue(runtime.closed()); assertTrue(frame.closed());
                assertSame(atlas,GameServices.graphics().getPatternAtlas());
                // Intended old579 behavioral RED: first remaining owned entry is base+579, AFTER the complete proof prefix.
                for(int offset=0;offset<601;offset++) assertNull(atlas.getEntry(base+offset),"ALL601 owned IDs retired "+(base+offset));
                shared.forEach((id,entry)->assertSame(entry,atlas.getEntry(id),"Actual shared producer identity survives "+id));
                assertEquals(NullAudioBackend.class,GameServices.audio().getBackend().getClass());
            } finally {
                try { SessionManager.closeGameplaySession(); }
                finally { GraphicsManager.destroyForReinit(); }
            }
        }
        // Reached only after module retirement AND finally session/graphics/owned boot-context cleanup.
        Path output = openCaptureOutput(prefixLog+"\n; Independent initial-only constructed scenes, each followed by\n"+sceneLog,"soniccd.anton.output");
        if(output!=null) {
            Files.writeString(output.resolve("construction.txt"),construction,StandardOpenOption.CREATE_NEW);
            for(var artifact:artifacts.entrySet()) ScreenshotCapture.savePNG(artifact.getValue(),output.resolve(artifact.getKey()));
            Files.writeString(output.resolve("outcome.txt"),
                    "PASS: ENGINE constructed actual-USA Anton World; actual ordinary649 serviced source prerequisite; native Init/positive Place/0->2->4/Main/shared two-script animation, all4 mapping frames/raw flips/current22 patterns, actual SAT and ordered current-palette terrain/nativePlaneB pixels, actual isolated/TEST contrast omission renderer controls, full/course state+pixel forward replay without ingress, ALL601 installed retirement witnesses removed and actual shared identities retained; module/session/graphics/boot cleanup returned. 601 is installed extent, not all rendered. No natural enemy-contact, kill/scanner attack, emulator/native parity, alias/dirty-opaque completion, JP/EU or full-game claim.\n"
                            +construction+observations,StandardOpenOption.CREATE_NEW);
        }
    }

    /** Read-only fixture predicate over real owned terrain; failure never tries another point. */
    private static void assertAntonWorldFloor(com.openggf.game.soniccd.level.CdR11aTerrain owner,
            com.openggf.game.soniccd.player.CdPlayerTerrain terrain,int x,int y,int distance,int flags) {
        var result = terrain.floor(x,y,13,0,16,flags);
        assertEquals(distance,result.distance(),"Fixed actual terrain prerequisite at "+x+":"+y);
        assertTrue(result.angleWrite().isPresent(),"Real shaped floor must publish an angle");
        // The returned surface must resolve a real, nonzero solid collision shape in that same owner.
        var placement = assertInstanceOf(com.openggf.game.soniccd.level.CdTerrainCollision.Available.class,
                new com.openggf.game.soniccd.level.CdTerrainCollision(owner).lookup(x,y+distance+1,flags));
        int raw = placement.word().rawWord();
        assertTrue((raw&0x7FF)!=0); assertTrue((raw&(1<<13))!=0);
        assertTrue(owner.collision().idForBlock(raw&0x7FF)!=0,"No blank/no-shape floor witness");
    }

    /** ONE initial actor ingress per independent scene; never called from row/forward-replay loops. */
    private static void stageAntonWorldPlayer(SonicCdGameModule module,int x16_16,int y16_16) {
        var runtime=(CdOpeningObjectRuntime)module.getGameService(NativeDynamicObjectPass.class);
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT,runtime.phase()); module.requirePlayerActor().requireCompletedNativeFrame();
        var frame=module.requirePlayerFrame();var actor=frame.captureActor();var old=actor.state();var m=old.motion();
        assertEquals(2,m.routine());assertEquals(0,old.objectLinks().standObject());assertEquals(0,old.objectLinks().pushObject());
        var motion=new com.openggf.game.soniccd.player.CdPlayerMotionState(x16_16,y16_16,0,0,0,2,0,0,4,9,19,0,
                m.previousAnimation(),0,0,0,0,0,m.previousPrimaryAngle(),m.previousSecondaryAngle());
        var state=new com.openggf.game.soniccd.player.CdPlayerBody.State(motion,old.animation(),old.header(),old.timers(),
                old.history(),old.rotationDistance(),old.objectLinks());
        frame.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(),state,java.util.Optional.empty()));
        module.requirePlayerActor().rebindAfterRestore();
        assertSame(old.animation(),frame.state().animation());assertSame(old.header(),frame.state().header());assertSame(old.timers(),frame.state().timers());
        assertSame(old.history(),frame.state().history());assertEquals(old.rotationDistance(),frame.state().rotationDistance());assertEquals(old.objectLinks(),frame.state().objectLinks());
    }

    /** Literal addressed-byte oracle, never calls Anton/shared animator to calculate its expected image. */
    private static void assertAntonWorldObject(com.openggf.game.soniccd.objects.CdObjectState actual,
            int x,int surface,int subtype,int flip,int[] expectedY,int row) {
        byte[] expected=new byte[64];
        checkpointWorldPut(expected,0,1,0x3F);checkpointWorldPut(expected,1,1,flip);checkpointWorldPut(expected,0x22,1,flip);
        checkpointWorldPut(expected,0x28,1,subtype);checkpointWorldPut(expected,8,4,(x<<16)|0xABCD);
        checkpointWorldPut(expected,12,4,((surface-21)<<16)|0x1234);
        if(row>0) {
            int animation=subtype==0?0:1,delta=subtype==0?-0x10000:-0x8000,main=Math.max(0,row-4);
            int y=expectedY[row];
            checkpointWorldPut(expected,1,1,0x84|flip);checkpointWorldPut(expected,2,2,0x2409);checkpointWorldPut(expected,4,4,0x20B3AA);
            checkpointWorldPut(expected,8,4,((x<<16)|0xABCD)+main*delta);checkpointWorldPut(expected,12,4,(y<<16)|0x1234);
            checkpointWorldPut(expected,0x16,1,0x13);checkpointWorldPut(expected,0x17,1,0x18);
            checkpointWorldPut(expected,0x18,1,4);checkpointWorldPut(expected,0x19,1,0x18);checkpointWorldPut(expected,0x1C,1,animation);
            checkpointWorldPut(expected,0x20,1,0x29);checkpointWorldPut(expected,0x24,1,row<3?0:row==3?2:4);
            checkpointWorldPut(expected,0x2A,4,delta);checkpointWorldPut(expected,0x2E,2,x);
            if(main>0) {
                int period=animation==0?4:8,phase=((main-1)/period)&1;
                checkpointWorldPut(expected,0x1A,1,animation*2+phase);checkpointWorldPut(expected,0x1B,1,phase+1);
                checkpointWorldPut(expected,0x1D,1,animation);checkpointWorldPut(expected,0x1E,1,period-1-(main-1)%period);
            }
        }
        assertArrayEquals(expected,actual.copyBytes(),"ALL64 addressed Anton bytes with independent terrain-vector Y row="+row+" subtype="+subtype+" flip="+flip);
    }

    private static void assertAntonWorldCapture(Capture expected,SonicCdGameModule module) {
        assertCapture(expected,module);
        var runtime=(CdOpeningObjectRuntime)module.getGameService(NativeDynamicObjectPass.class);
        assertEquals(expected.objects().checkpointState(),runtime.checkpointState());
        assertEquals(expected.objects().checkpointState(),runtime.captureClosedCut().checkpointState());
    }

    /** SAME existing cached bank/source identities, actual read-only current bytes after ordinary real service. */
    private static void assertAntonWorldCurrentSource(CdOpeningObjectRuntime runtime,
            com.openggf.game.soniccd.level.CdR11aObjectAssets objects,
            com.openggf.game.soniccd.level.CdR11aObjectAssets.SectionArtSourceBank bank,
            List<com.openggf.game.soniccd.level.CdR11aObjectAssets.SectionArtSource> identities,
            com.openggf.game.soniccd.level.CdR11aObjectAssets.SectionArtSource anton) throws Exception {
        assertSame(bank,objects.sectionArtSources());assertSame(identities,bank.sources());assertEquals(10,identities.size());
        for(int i=0;i<10;i++)assertSame(identities.get(i),objects.sectionArtSources().sources().get(i));
        assertSame(anton,bank.source(com.openggf.game.soniccd.level.CdR11aAsset.ART_ANTON));
        var current=runtime.sectionArtState().residency();
        for(int tile=0;tile<22;tile++)assertArrayEquals(anton.decode().bytes().slice(tile*32,32).copy(),
                com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(current,0x409+tile).copy(),
                "Actual serviced current canonical tile, not source fallback "+tile);
    }

    private static int[][] antonWorldPieces(int frame) {
        return switch(frame) {
            case 0 -> new int[][]{{-19,5,0,-12},{-3,8,4,-12},{5,4,7,-4},{4,5,9,-8},{-4,0,17,14}};
            case 1 -> new int[][]{{-20,5,0,-12},{-4,8,4,-12},{4,4,7,-4},{4,5,13,-8},{-5,0,17,18}};
            case 2 -> new int[][]{{-19,5,18,-12},{-3,8,4,-12},{5,4,7,-4},{4,5,9,-8},{-4,0,17,14}};
            case 3 -> new int[][]{{-20,5,18,-12},{-4,8,4,-12},{4,4,7,-4},{4,5,13,-8},{-5,0,17,18}};
            default -> throw new AssertionError("Unowned literal Anton mapping frame "+frame);
        };
    }

    /** Actual native DrawObjects SAT, not a synthetic CPU table or direct draw callback. */
    private static void assertAntonWorldSat(CdObjectDrawQueue.CpuSat sat,int cameraX,int cameraY,
            com.openggf.game.soniccd.objects.CdObjectState object) {
        assertEquals(0x80,object.spriteFlags()&0x80,"Actual SAT drain owns bit7");assertTrue(sat.spriteCount()<80);
        byte[] raw=sat.bytes();int seen=0,lastSonic=-1,firstAnton=-1;var pieces=antonWorldPieces(object.unsignedByte(MAP_FRAME));
        for(int entry=0;entry<sat.spriteCount();entry++) {
            var p=sat.provenance().get(entry);if(p.slotRef().wholeIndex()==0)lastSonic=entry;
            if(p.slotRef().wholeIndex()!=40)continue;if(firstAnton<0)firstAnton=entry;
            assertTrue(seen<5);int[] piece=pieces[seen];
            assertEquals(4,p.queueBucket());assertEquals(0,p.queueOrdinal());assertEquals(seen,p.mappingPieceOrdinal());
            int cols=(piece[1]>>>2)+1,rows=(piece[1]&3)+1,flags=object.spriteFlags();
            int sx=(flags&1)==0?piece[3]:-piece[3]-cols*8,sy=(flags&2)==0?piece[0]:-piece[0]-rows*8;
            int at=entry*8;
            assertEquals((object.centreY()-cameraY+128+sy)&0xFFFF,((raw[at]&255)<<8)|(raw[at+1]&255));
            assertEquals(piece[1],raw[at+2]&255);assertEquals(entry+1,raw[at+3]&255);
            int word=((raw[at+4]&255)<<8)|(raw[at+5]&255);assertEquals((0x2409+piece[2])^((flags&3)<<11),word);
            assertEquals(0x2000,word&0xE000,"Native palette1/hardware-priority0, independent of queue4");
            int hardwareX=(object.centreX()-cameraX+128+sx)&511;assertNotEquals(0,hardwareX);
            assertEquals(hardwareX,((raw[at+6]&255)<<8)|(raw[at+7]&255));seen++;
        }
        assertEquals(5,seen);assertTrue(lastSonic>=0 && lastSonic<firstAnton,"Actual player SAT precedes queue4; renderer paints reverse SAT");
    }

    private record AntonWorldCell(int offset,int x,int y,boolean hFlip,boolean vFlip,int piece) { }

    /** Independent literal native piece -> column-major tile oracle in actual reverse-SAT painter order. */
    private static List<AntonWorldCell> antonWorldTiles(com.openggf.game.soniccd.objects.CdObjectState object,int cameraX,int cameraY) {
        var out=new ArrayList<AntonWorldCell>();var pieces=antonWorldPieces(object.unsignedByte(MAP_FRAME));int flip=object.spriteFlags()&3;
        for(int p=4;p>=0;p--) {
            int[] piece=pieces[p];int cols=(piece[1]>>>2)+1,rows=(piece[1]&3)+1;
            int word=(0x2409+piece[2])^(flip<<11);boolean h=(word&0x800)!=0,v=(word&0x1000)!=0;
            int ox=object.centreX()-cameraX+((flip&1)==0?piece[3]:-piece[3]-cols*8);
            int oy=object.centreY()-cameraY+((flip&2)==0?piece[0]:-piece[0]-rows*8);
            for(int row=0;row<rows;row++)for(int col=0;col<cols;col++)out.add(new AntonWorldCell(
                    (word&0x7FF)-0x409+col*rows+row,ox+(h?cols-1-col:col)*8,oy+(v?rows-1-row:row)*8,h,v,p));
        }
        assertEquals(14,out.size());return List.copyOf(out);
    }

    private static void assertAntonWorldVersions(com.openggf.graphics.SpritePresentation.Frame picture,
            com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State residency,int base) {
        int selected=0;
        for(var tile:picture.tiles())if(tile.patternId()>=base && tile.patternId()<base+22) {
            selected++;var current=com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(residency,0x409+tile.patternId()-base);
            assertEquals(com.openggf.game.soniccd.resources.CdObjectPresentation.patternVersion(current),picture.patternVersions().get(tile.patternId()));
        }
        assertEquals(14,selected,"Actual selected mapping uses14 cells, not all22 every frame");
    }

    /** Current palette/nibbles, all holes/overlaps, and actual other sprites over current terrain/nativePlaneB. */
    private static String assertAntonWorldPixels(com.openggf.graphics.SpritePresentation.Frame picture,RgbaImage image,RgbaImage terrain,
            com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State residency,int base,List<AntonWorldCell> literal) {
        assertTrue(picture.primitives().isEmpty());assertEquals(14,literal.size());
        int left=literal.stream().mapToInt(AntonWorldCell::x).min().orElseThrow(),top=literal.stream().mapToInt(AntonWorldCell::y).min().orElseThrow();
        int right=literal.stream().mapToInt(t->t.x()+8).max().orElseThrow(),bottom=literal.stream().mapToInt(t->t.y()+8).max().orElseThrow();
        assertTrue(left>=0 && top>=0 && right<=320 && bottom<=224,"Fixed fixture must keep all native Anton geometry visible; no camera/pose retry");
        var graphics=GameServices.graphics();assertFalse(graphics.isWaterEnabled());assertFalse(graphics.getPaletteFadePresentation().affects(1));
        var palette=GameServices.level().getCurrentLevel().getPalette(1);int[] argb=new int[16];
        for(int n=1;n<16;n++){var c=palette.getColor(n);argb[n]=0xFF000000|com.openggf.graphics.color.DisplayColorConverter.toPackedRgb(
                c.r&255,c.g&255,c.b&255,graphics.getDisplayColorProfile(),graphics.getDisplayColorExpansion());}
        int[] expected=terrain.pixels().clone();int at=0,opaque=0,transparent=0,changed=0;
        for(var tile:picture.tiles()) {
            if(tile.patternId()<base || tile.patternId()>=base+22){paintDiagonalWorldOtherSprite(picture,tile,expected,left,top,right,bottom);continue;}
            assertTrue(at<literal.size());var cell=literal.get(at++);
            assertEquals(base+cell.offset(),tile.patternId());assertEquals((float)cell.x(),tile.x());assertEquals((float)cell.y(),tile.y());
            assertEquals(8f,tile.width());assertEquals(8f,tile.height());assertEquals(1,tile.palette());assertEquals(cell.hFlip(),tile.hFlip());assertEquals(cell.vFlip(),tile.vFlip());
            assertFalse(tile.priority());assertTrue(tile.priorityShader());assertEquals(0,tile.occlusionMask());assertFalse(tile.ghost());assertFalse(tile.ghostColour());
            assertEquals(1f,tile.ghostAlpha());assertEquals(0,tile.rowStart());assertEquals(8,tile.rowEnd());assertFalse(tile.rowClipped());
            assertEquals(com.openggf.graphics.SpritePresentation.Layer.OBJECT,tile.layer());assertEquals(com.openggf.graphics.SpritePresentation.Subject.WORLD,tile.subject());
            var decoded=new com.openggf.level.Pattern();decoded.fromSegaFormat(
                    com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(residency,0x409+cell.offset()).copy());
            var version=picture.patternVersions().get(base+cell.offset());assertNotNull(version);long[] words={version.a(),version.b(),version.c(),version.d()};
            for(int n=0;n<64;n++)assertEquals(decoded.getPixel(n%8,n/8)&15,(int)((words[n/16]>>>((n%16)*4))&15));
            for(int dy=0;dy<8;dy++)for(int dx=0;dx<8;dx++) {
                int value=decoded.getPixel(cell.hFlip()?7-dx:dx,cell.vFlip()?7-dy:dy)&15,index=(cell.y()+dy)*320+cell.x()+dx;
                if(value==0)transparent++;else{opaque++;expected[index]=argb[value];if(argb[value]!=terrain.pixels()[index])changed++;}
            }
        }
        assertEquals(14,at);
        for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)assertEquals(expected[y*320+x],image.pixels()[y*320+x],"Ordered current Anton/native plane pixel "+x+":"+y);
        assertEquals(14*64,opaque+transparent);assertTrue(opaque>0);assertTrue(transparent>0);assertTrue(changed>0);
        return "cells=14 opaque="+opaque+" transparent="+transparent+" changed="+changed;
    }

    /** Actual renderer counterfactuals; partial pictures are TEST controls, never source/gameplay/art mutation. */
    private static String assertAntonWorldOmissions(SonicCdGameModule module,com.openggf.graphics.SpritePresentation.Frame picture,
            RgbaImage full,RgbaImage terrain,com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State residency,
            int base,List<AntonWorldCell> literal,int contrastRgb) {
        var omittedAll=renderDiagonalWorldPicture(module,com.openggf.graphics.SpritePresentation.Frame.empty(),contrastRgb);
        assertArrayEquals(terrain.pixels(),omittedAll.pixels());assertFalse(Arrays.equals(full.pixels(),omittedAll.pixels()));
        assertThrows(AssertionError.class,()->assertAntonWorldPixels(picture,omittedAll,terrain,residency,base,literal));
        int cellWitnesses=0,pieceWitnesses=0,unchanged=0;
        for(int selector=0;selector<14+5;selector++) {
            boolean piece=selector>=14;int selected=piece?selector-14:selector;var retained=new ArrayList<com.openggf.graphics.SpritePresentation.Tile>();
            for(int i=0;i<14;i++)if(!(piece?literal.get(i).piece()==selected:i==selected))retained.add(picture.tiles().get(i));
            var partial=renderDiagonalWorldPicture(module,checkpointWorldFrame(retained,picture),contrastRgb);
            if(Arrays.equals(full.pixels(),partial.pixels())){unchanged++;continue;} // Empty/occluded/same-colour cells invent no witness.
            assertThrows(AssertionError.class,()->assertAntonWorldPixels(picture,partial,terrain,residency,base,literal));
            if(piece)pieceWitnesses++;else cellWitnesses++;
        }
        assertTrue(cellWitnesses>1);assertTrue(pieceWitnesses>0);
        return "TEST actual-renderer omission cellWitnesses="+cellWitnesses+" pieceWitnesses="+pieceWitnesses+" unchangedControls="+unchanged;
    }

    private static com.openggf.graphics.SpritePresentation.Frame antonWorldOnly(com.openggf.graphics.SpritePresentation.Frame picture,int base) {
        assertTrue(picture.primitives().isEmpty());
        return checkpointWorldFrame(picture.tiles().stream().filter(t->t.patternId()>=base && t.patternId()<base+22).toList(),picture);
    }
    // END constructed Anton World proof.
    // Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
}
