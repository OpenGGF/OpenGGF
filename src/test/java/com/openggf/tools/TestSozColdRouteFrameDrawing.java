package com.openggf.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A drawing gap must preserve the next checkpoint's entire world and visible pixels. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozColdRouteFrameDrawing {
    @ParameterizedTest
    @CsvSource({"0,sonic,320", "0,sonic,800", "1,knuckles,320", "1,knuckles,800"})
    void skippedTraversalPreservesCheckpointStateAndPixels(int act, String main, int width) throws Exception {
        String suffix = act == 1 && width == 800 ? "-800" : "";
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/soz" + (act + 1) + "-cold-" + main + suffix + ".bk2"));
        var settings = new GameplayCaptureSession.Settings(width, main, "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            GameServices.configuration().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, false);
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, act, settings);
            assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
            var everyFrame = new RouteFrameDrawing(true);
            advance(session, movie, everyFrame, 0, 600);
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var saved = registry.capture();

            advance(session, movie, everyFrame, 600, 1020);
            var expected = registry.capture();
            var expectedImage = ScreenshotCapture.captureFramebuffer(width, GameplayCaptureSession.HEIGHT);
            assertTrue(Arrays.stream(expectedImage.pixels()).distinct().count() > 1,
                    "the fully drawn control must have visible content");

            registry.restore(saved);
            same(saved, registry.capture());
            session.restoreInputHistory(movie.getFrame(599));
            // Poison the previous branch's framebuffer so stale pixels cannot
            // make the next-frame comparison pass without actually drawing.
            glClearColor(1, 0, 1, 1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glFinish();
            assertEquals(1, Arrays.stream(ScreenshotCapture.captureFramebuffer(
                    width, GameplayCaptureSession.HEIGHT).pixels()).distinct().count());

            advance(session, movie, new RouteFrameDrawing(false), 600, 1020);
            same(expected, registry.capture());
            var actualImage = ScreenshotCapture.captureFramebuffer(width, GameplayCaptureSession.HEIGHT);
            assertArrayEquals(expectedImage.pixels(), actualImage.pixels(),
                    "the next drawn SOZ checkpoint must match fully drawn traversal");
        }
    }

    private void advance(GameplayCaptureSession session, Bk2Movie movie,
            RouteFrameDrawing drawing, int start, int end) {
        for (int frame = start; frame < end; frame++) {
            session.step(movie.getFrame(frame));
            drawing.afterStep(session);
        }
        drawing.checkpoint(session);
    }

    private void same(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (var key : expected.entries().keySet()) {
            var difference = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(difference.isEmpty(), key + ": " + difference);
        }
    }
}
