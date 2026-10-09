package com.openggf.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

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
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Bounded movie-prefix controls; these do not certify entire routes or donor matrices. */
@RequiresRom(SonicGame.SONIC_3K)
class TestRouteFrameDrawing {
    private record Route(int zone, String movie, int width, String main, String follower,
                         String donor, boolean initialNeutral) {}

    static Stream<Arguments> routes() {
        return Stream.of(
                route(4, "fbz1-knuckles-cold-320", 320, "knuckles", ""),
                route(4, "fbz1-tails-cold-320", 320, "tails", ""),
                route(4, "fbz1-knuckles-cold-320", 800, "knuckles", ""),
                route(4, "fbz1-tails-cold-320", 800, "tails", ""),
                route(9, "lrz1-sonic-tails-cold-corkscrew-320", 320, "sonic", "tails"),
                route(9, "lrz1-sonic-tails-cold-crusher-800", 800, "sonic", "tails"),
                route(9, "lrz-knuckles-cold-miniboss-320", 320, "knuckles", ""),
                route(9, "lrz-tails-cold-act1-clear-320", 320, "tails", ""),
                route(10, "ssz1-sonic-tails-cold-upper-320", 320, "sonic", "tails"),
                route(10, "ssz1-sonic-tails-cold-first-replica-800", 800, "sonic", "tails"),
                route(10, "ssz1-tails-solo-cold-replicas-320", 320, "tails", ""),
                route(10, "ssz1-tails-solo-cold-replicas-800", 800, "tails", ""),
                route(11, "dez1-sonic-tails-cold-upper-320", 320, "sonic", "tails"),
                route(11, "dez1-sonic-cold-800", 800, "sonic", ""),
                route(11, "dez1-tails-solo-cold-complete-320", 320, "tails", ""),
                donorRoute(320, "s1"), donorRoute(800, "s1"),
                donorRoute(320, "s2"), donorRoute(800, "s2"))
                .map(Arguments::of);
    }

    private static Route route(int zone, String movie, int width, String main, String follower) {
        return new Route(zone, movie, width, main, follower, "off", zone == 4);
    }

    private static Route donorRoute(int width, String donor) {
        return new Route(10, "ssz1-sonic-tails-cold-upper-320", width, "sonic",
                donor.equals("s2") ? "tails" : "", donor, false);
    }

    @ParameterizedTest
    @MethodSource("routes")
    void drawingPolicyPreservesCheckpointStateAndPixels(Route route) throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/routes/s3k/" + route.movie() + ".bk2"));
        var donorRom = switch (route.donor()) {
            case "s1" -> RomTestUtils.ensureSonic1RomAvailable().toPath();
            case "s2" -> RomTestUtils.ensureSonic2RomAvailable().toPath();
            default -> null;
        };
        var settings = new GameplayCaptureSession.Settings(route.width(), route.main(),
                route.follower(), route.donor(), donorRom, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), route.zone(), 0, settings);
            var fullyDrawn = new RouteFrameDrawing(true);
            if (route.initialNeutral()) {
                // FBZ's frozen movies follow the production one-shot setup pass.
                session.step(null);
                fullyDrawn.draw(session);
            }
            advance(session, movie, fullyDrawn, 0, 600);
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var saved = registry.capture();
            advance(session, movie, fullyDrawn, 600, 1020);
            var expected = registry.capture();
            var expectedImage = ScreenshotCapture.captureFramebuffer(route.width(), GameplayCaptureSession.HEIGHT);
            assertTrue(Arrays.stream(expectedImage.pixels()).distinct().count() > 1,
                    "the drawn control must contain visible content for " + route);

            registry.restore(saved);
            same(saved, registry.capture());
            session.restoreInputHistory(movie.getFrame(599));
            poisonFramebuffer(route.width());

            var policy = new RouteFrameDrawing(false);
            advance(session, movie, policy, 600, 1020);
            assertEquals(420, policy.skippedFrames());
            assertEquals(1, policy.drawnFrames(), "one reconstructed checkpoint draw");
            same(expected, registry.capture());
            var actualImage = ScreenshotCapture.captureFramebuffer(route.width(), GameplayCaptureSession.HEIGHT);
            assertArrayEquals(expectedImage.pixels(), actualImage.pixels(),
                    "the reconstructed checkpoint must match fully drawn traversal for " + route);
        }
    }

    private static void poisonFramebuffer(int width) {
        glClearColor(1, 0, 1, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glFinish();
        assertEquals(1, Arrays.stream(ScreenshotCapture.captureFramebuffer(
                width, GameplayCaptureSession.HEIGHT).pixels()).distinct().count(),
                "the previous framebuffer must be poisoned before the comparison branch");
    }

    private static void advance(GameplayCaptureSession session, Bk2Movie movie,
                                RouteFrameDrawing drawing, int start, int end) {
        for (int frame = start; frame < end; frame++) {
            session.step(movie.getFrame(frame));
            drawing.afterStep(session);
        }
        drawing.checkpoint(session);
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (var key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), key + ": " + differences);
        }
    }
}
