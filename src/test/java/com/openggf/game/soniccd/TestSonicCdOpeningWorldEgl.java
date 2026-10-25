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
                        com.openggf.game.soniccd.resources.CdObjectPresentation.usaR11aTunnelDoorVerticalDevPreloadedArt(
                                module.requirePlayerFrame().artState(), runtime.powerupArtState(), source.assets().openingObjects(),
                                source.assets().openingObjects().frontierArtBank(),
                                source.assets().openingObjects().contactArtBank(),
                                source.assets().openingObjects().floatBlockArtBank(),
                                source.assets().openingObjects().springArtBank(),
                                source.assets().openingObjects().tunnelDoorArtBank(),
                                banks.player(), banks.ring(), banks.mosqui(), banks.plant(), banks.ramp(),
                                banks.explosion(), banks.points(), banks.flower(), banks.powerup(), banks.floatBlock(), banks.spring(),
                                banks.tunnelDoorVertical()));
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
        assertEquals(expected.camera(), actual.camera()); assertScroll(expected.scroll(), actual.scroll());
        assertEquals(expected.levelFrames(), actual.levelFrames());
        assertPublication(expected.publication(), actual.publication());
        var a = expected.bank().tiles(); var b = actual.bank().tiles();
        assertEquals(24, b.length);
        for (int i = 0; i < 24; i++) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
            assertEquals(a[i].getPixel(x, y), b[i].getPixel(x, y));
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

}
