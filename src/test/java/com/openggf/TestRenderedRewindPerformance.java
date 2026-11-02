package com.openggf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import jdk.jfr.Recording;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Measures held rewind through the live Engine scheduler and isolated EGL rendering.
 * Inputs are openggf.perf zone, act, optional route/routeX, repeats, variant, jfr and output properties.
 * Writes per-service JSON and optional JFR. Run separately built baseline and fixed engines
 * in counterbalanced order under tools/performance/run_isolated.py.
 * Origin: LRZ1 and SOZ2 rewind-performance investigation, 2026-11-02.
 */
@Tag("performance-measurement")
@RequiresRom(SonicGame.SONIC_3K)
class TestRenderedRewindPerformance {
    record Sample(int service, int before, int after, long totalNanos, long drawNanos) { }

    public static void main(String[] args) throws Exception {
        new TestRenderedRewindPerformance().heldRewind();
    }

    @Test
    void heldRewind() throws Exception {
        int zone = Integer.getInteger("openggf.perf.zone", 9);
        int act = Integer.getInteger("openggf.perf.act", 0);
        int repeats = Integer.getInteger("openggf.perf.repeats", 1);
        Path output = Path.of(System.getProperty("openggf.perf.output", "target/rewind-performance"));
        Files.createDirectories(output);
        List<Object> runs = new ArrayList<>();
        for (int repeat = 0; repeat < repeats; repeat++) {
            var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null, null, null);
            Engine engine = null;
            try (var session = new GameplayCaptureSession(settings)) {
                var config = GameServices.configuration();
                config.setSessionOverride(SonicConfiguration.REGION, "NTSC");
                config.setSessionOverride(SonicConfiguration.FPS, 60);
                config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
                config.setSessionOverride(SonicConfiguration.LIVE_REWIND_KEY, GLFW_KEY_F10);
                config.setSessionOverride(SonicConfiguration.LIVE_REWIND_TAPE_COAST_ENABLED, false);
                String route = System.getProperty("openggf.perf.route");
                if (route != null) config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, false);
                session.enableAudio();
                session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), zone, act, settings);
                Bk2Movie movie = route == null ? null : new Bk2MovieLoader().loadMovieOrInputLog(Path.of(route));
                int cursor = 0;
                if (movie != null) {
                    assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
                    int routeX = Integer.getInteger("openggf.perf.routeX", 0x4000);
                    while ((GameServices.camera().getFocusedSprite().getCentreX() & 0xFFFF) < routeX) {
                        assertTrue(cursor < movie.getFrameCount(), "recorded route must reach the pre-boss window");
                        session.step(movie.getFrame(cursor++));
                        if (cursor % 30 == 0) session.renderFrame();
                        assertFalse(GameServices.camera().getFocusedSprite().getDead(), "route death at " + cursor);
                    }
                    session.renderFrame();
                }
                for (int setup = 0; movie == null; setup++) {
                    assertTrue(setup < 600, "native entry must finish");
                    if (session.loop().getCurrentGameMode() == GameMode.LEVEL
                            && !GameServices.level().hasPendingFreshLevelTransitionBoundary()
                            && !session.player().isControlLocked() && !session.player().getAir()
                            && !GameServices.fade().isActive()) break;
                    session.step(null);
                    session.renderFrame();
                }
                session.loop().closePresence();
                engine = new Engine();
                var input = new InputHandler(InputBindingFactory.supplier(config));
                engine.setInputHandler(input);
                var bind = Engine.class.getDeclaredMethod("bindGameplayMode", GameplayModeContext.class);
                bind.setAccessible(true);
                bind.invoke(engine, SessionManager.getCurrentGameplayMode());
                var reshape = Engine.class.getDeclaredMethod("reshape", int.class, int.class);
                reshape.setAccessible(true);
                reshape.invoke(engine, 320, 224);
                GameServices.graphics().setProjectionMatrixBuffer(engine.getProjectionMatrixBuffer());
                var render = Engine.class.getDeclaredMethod("renderScheduledFrame", LivePictureRequest.class);
                render.setAccessible(true);
                Engine owner = engine;
                long[] clock = {0}, draw = {0};
                var runtime = engine.createLiveFrameRuntime(new Engine.LiveEndpoints(picture -> {
                    long started = System.nanoTime();
                    try { render.invoke(owner, picture); }
                    catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                    glFinish();
                    draw[0] += System.nanoTime() - started;
                }, () -> { }, interval -> { }, () -> DisplayOutput.UNKNOWN, () -> false, () -> clock[0]));
                runtime.iterate();
                Map<Integer, int[]> forwardPictures = new LinkedHashMap<>();
                for (int frame = 0; frame < 360; frame++) {
                    if (movie != null) {
                        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(movie.getFrame(cursor),
                                movie.getFrame(cursor - 1)));
                        cursor++;
                    }
                    clock[0] += 16_666_667;
                    runtime.iterate();
                    var history = SessionManager.getCurrentGameplayMode().getRewindController();
                    if (history != null && List.of(209, 269, 314).contains(frame)) {
                        forwardPictures.put(history.currentFrame(), session.render().pixels());
                    }
                }
                var history = SessionManager.getCurrentGameplayMode().getRewindController();
                assertNotNull(history);
                assertTrue(history.currentFrame() >= 300, "forward history must be recorded");
                assertFalse(GameServices.sprites().getMainPlayable().getDead());
                input.clearLogicalOverride();
                input.handleKeyEvent(GLFW_KEY_F10, GLFW_PRESS);
                for (int warmup = 0; warmup < 30; warmup++) {
                    clock[0] += 16_666_667;
                    runtime.iterate();
                }
                var tilemaps = GameServices.level().getTilemapManager();
                var rebuildCounter = tilemaps.getClass().getDeclaredField("bgFullRebuildCount");
                rebuildCounter.setAccessible(true);
                int rebuilds = rebuildCounter.getInt(tilemaps);
                int foregroundRebuilds = 0;
                List<Sample> samples = new ArrayList<>();
                int checkedPictures = 0;
                try (var recording = new Recording()) {
                    if (Boolean.getBoolean("openggf.perf.jfr")) {
                        recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
                        recording.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(1));
                        recording.start();
                    }
                    for (int frame = 0; frame < 180; frame++) {
                        int before = history.currentFrame();
                        byte[] foreground = tilemaps.getForegroundTilemapData();
                        draw[0] = 0;
                        long started = System.nanoTime();
                        clock[0] += 16_666_667;
                        runtime.iterate();
                        long elapsed = System.nanoTime() - started;
                        int after = history.currentFrame();
                        if (foreground != tilemaps.getForegroundTilemapData()) foregroundRebuilds++;
                        assertEquals(before - 1, after, "each held service selects one earlier frame");
                        assertTrue(GameServices.audio().isReverseAudioOutputActive());
                        samples.add(new Sample(frame, before, after, elapsed, draw[0]));
                        if (forwardPictures.containsKey(after)) {
                            assertArrayEquals(forwardPictures.get(after), session.render().pixels(),
                                    "restored engine picture at history frame " + after);
                            checkedPictures++;
                        }
                    }
                    if (Boolean.getBoolean("openggf.perf.jfr")) {
                        recording.stop();
                        recording.dump(output.resolve("rewind-" + repeat + ".jfr"));
                    }
                }
                int heldRebuilds = rebuildCounter.getInt(tilemaps) - rebuilds;
                input.handleKeyEvent(GLFW_KEY_F10, GLFW_RELEASE);
                clock[0] += 16_666_667;
                runtime.iterate();
                assertFalse(GameServices.audio().isReverseAudioOutputActive());
                var run = new LinkedHashMap<String, Object>();
                run.put("zone", zone); run.put("act", act); run.put("samples", samples);
                run.put("protocol", 3);
                run.put("variant", System.getProperty("openggf.perf.variant", "unspecified"));
                run.put("route", route);
                run.put("routeFrame", cursor);
                run.put("backgroundRebuilds", heldRebuilds);
                run.put("foregroundRebuilds", foregroundRebuilds);
                run.put("backgroundWidthTiles", tilemaps.getBackgroundTilemapWidthTiles());
                run.put("backgroundHeightTiles", tilemaps.getBackgroundTilemapHeightTiles());
                var wrap = tilemaps.getClass().getDeclaredField("lastBackgroundWrap");
                wrap.setAccessible(true);
                run.put("backgroundWrap", wrap.get(tilemaps));
                run.put("checkedPictures", checkedPictures);
                assertEquals(3, checkedPictures);
                run.put("renderer", org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
                runs.add(run);
            } finally {
                if (engine != null) engine.getGameLoop().closePresence();
                Engine.clearGlobalInstance();
            }
        }
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("samples.json").toFile(), runs);
    }
}
