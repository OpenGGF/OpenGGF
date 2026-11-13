package com.openggf.game.sonic3k.competition;

import com.openggf.control.InputHandler;
import com.openggf.game.arena.HostedArenaRequest;
import com.openggf.game.arena.MonitorRule;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.nativemenu.NativeMenuHostContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

/**
 * Pixels of the hosted zoom against the native split view it wraps, on the recorded
 * Desert Palace route: the zoom shows P1's native band magnified twice, drawing it never
 * perturbs the split view, and full-size bodies keep the miniature's tile priority.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kArenaZoomPixels {
    private static final Path PICTURES = Path.of("target/arena-zoom/pixel-diagnostics");

    private static RgbaImage draw(Runnable draw) {
        glViewport(0, 0, 320, 224);
        glClearColor(1, 0, 1, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        draw.run();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }

    private static boolean sameRgb(RgbaImage first, RgbaImage second) {
        for (int i = 0; i < first.pixels().length; i++)
            if ((first.pixels()[i] & 0xFFFFFF) != (second.pixels()[i] & 0xFFFFFF)) return false;
        return true;
    }

    /** Fraction of zoom pixels in P1's 108 split rows equal to the native pixel they magnify. */
    private static double magnifiedMatch(RgbaImage zoom, RgbaImage split, int rowShift) {
        int left = S3kSoloArenaViews.windowLeft(320), equal = 0, total = 0;
        for (int y = 0; y < 2 * S3kCompetitionScroll.PLAYER_1_RASTER_LINES; y++) {
            int nativeRow = y / 2 + rowShift;
            if (nativeRow < 0 || nativeRow >= S3kCompetitionScroll.PLAYER_1_RASTER_LINES) continue;
            for (int x = 0; x < 320; x++, total++)
                if ((zoom.argb(x, y) & 0xFFFFFF) == (split.argb(left + x / 2, nativeRow) & 0xFFFFFF)) equal++;
        }
        return equal / (double) total;
    }

    @Test void zoomMagnifiesTheNativeP1BandAndKeepsTilePriority() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned EGL");
        Files.createDirectories(PICTURES);
        var route = TestS3kArenaZoomFraming.route();
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var capture = new GameplayCaptureSession(settings)) {
            capture.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            capture.step(null);
            var engine = EngineServices.current();
            var view = new java.util.concurrent.atomic.AtomicReference<S3kSoloArenaViews>();
            var provider = new S3kHostedArenaProvider(ctx -> {
                var next = new S3kSoloArenaViews(ctx.engine().graphics());
                view.set(next);
                return next;
            });
            try (var session = provider.open(new HostedArenaRequest(TestS3kArenaZoomFraming.DESERT_PALACE, "sonic", false),
                    new NativeMenuHostContext(engine, new Sonic3kGameModule(), new InputHandler(), capture.loop()::setGameplayMode))) {
                var context = SessionManager.getCurrentGameplayMode();
                var competition = GameplayRunPolicy.competition();
                context.beginGameplayRun(new GameplayRunPolicy(competition.specialStageEntry(), competition.bonusStageEntry(),
                        competition.actCompletion(), competition.liveRewind(), competition.editorEntry(),
                        competition.deathRestart(), competition.livesCounter(), competition.zoneIntros(), MonitorRule.OFF),
                        TestS3kArenaZoomFraming.DESERT_PALACE, 0);
                var host = (S3kCompetitionNativeHost) NativeCompetitionRoster.from(context.getWorldSession());
                int previous = 0, magnified = 0, occluded = 0;
                for (int frame = 0; frame < route.size(); frame++) {
                    var in = TestS3kArenaZoomFraming.input(route.get(frame), previous);
                    previous = route.get(frame);
                    context.plcFrameLifecycle().runLogicalIteration(() -> { }, token -> {
                        NativeCompetitionIterationAccess.runAdmitted(token, () -> session.step(in));
                        return null;
                    });
                    if (frame < 300 || frame % 50 != 0) continue;
                    var before = context.getRewindRegistry().capture();
                    var split = draw(() -> view.get().nativeViews().drawArena(context, host, host.scroll()));
                    var zoom = draw(session::draw);
                    var splitAgain = draw(() -> view.get().nativeViews().drawArena(context, host, host.scroll()));
                    assertTrue(sameRgb(split, splitAgain), "drawing the zoom changed the native split view at frame " + frame);
                    var after = context.getRewindRegistry().capture();
                    assertEquals(before.entries().keySet(), after.entries().keySet());
                    for (var key : before.entries().keySet())
                        assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,
                                before.entries().get(key), after.entries().get(key)), key + " written by draw at " + frame);
                    double exact = magnifiedMatch(zoom, split, 0);
                    if (exact <= 0.85) {
                        ScreenshotCapture.savePNG(split, PICTURES.resolve("split-" + frame + ".png"));
                        ScreenshotCapture.savePNG(zoom, PICTURES.resolve("zoom-" + frame + ".png"));
                    }
                    // Bodies, ghosts and HUD differ by design; the rest is the native band, pixel for pixel.
                    assertTrue(exact > 0.85, "zoom is not P1's magnified band at frame " + frame + ": " + exact);
                    // Negative control: one native row lower must match measurably worse.
                    assertTrue(exact > magnifiedMatch(zoom, split, 1) + 0.02, "row control did not separate at " + frame);
                    magnified++;

                    var player = host.session().player(0);
                    if (player.highPriority() || !player.bodyVisible()) continue;
                    // Compare the live image with and without the body's priority bit.
                    view.get().discardPublishedImage();
                    var behind = draw(session::draw);
                    player.setHighPriority(true);
                    RgbaImage front;
                    try { front = draw(session::draw); }
                    finally { player.setHighPriority(false); view.get().discardPublishedImage(); }
                    if (!sameRgb(behind, front)) {
                        occluded++;
                        ScreenshotCapture.savePNG(behind, PICTURES.resolve("occluded-" + frame + ".png"));
                        ScreenshotCapture.savePNG(front, PICTURES.resolve("unoccluded-" + frame + ".png"));
                    }
                }
                assertTrue(magnified >= 40, "too few compared frames: " + magnified);
                // Desert Palace's pillars are high-priority Plane A: the route passes behind them.
                assertTrue(occluded > 0, "no frame showed high-priority tiles hiding the body");
            }
        }
    }
}
