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

    /** The split view and the zoom over the route's first frames at one logical width, every 50th frame from 300. */
    private static List<RgbaImage[]> pictures(int width, int frames) throws Exception {
        var route = TestS3kArenaZoomFraming.route();
        var settings = new GameplayCaptureSession.Settings(width, "sonic", "", "off", null, null, null);
        var pictures = new java.util.ArrayList<RgbaImage[]>();
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
                int previous = 0;
                for (int frame = 0; frame < frames; frame++) {
                    var in = TestS3kArenaZoomFraming.input(route.get(frame), previous);
                    previous = route.get(frame);
                    context.plcFrameLifecycle().runLogicalIteration(() -> { }, token -> {
                        NativeCompetitionIterationAccess.runAdmitted(token, () -> session.step(in));
                        return null;
                    });
                    if (frame < 300 || frame % 50 != 0) continue;
                    var split = draw(() -> view.get().nativeViews().drawArena(context, host, host.scroll()));
                    glViewport(0, 0, width, 224);
                    glClearColor(1, 0, 1, 1);
                    glClear(GL_COLOR_BUFFER_BIT);
                    session.draw();
                    pictures.add(new RgbaImage[] {split, ScreenshotCapture.captureFramebuffer(width, 224)});
                }
            }
        }
        return pictures;
    }

    /**
     * The native split picture is 320 columns at every logical width, and a wider zoom shows
     * more of those columns at the same scale. A widescreen display must not rescale the planes
     * under bodies that are placed by window column.
     */
    @Test void aWiderDisplayShowsMoreColumnsOfTheSameNativePicture() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned EGL");
        Files.createDirectories(PICTURES);
        var narrow = pictures(320, 801);
        var wide = pictures(400, 801);
        assertEquals(narrow.size(), wide.size());
        assertTrue(narrow.size() >= 10, "too few compared frames: " + narrow.size());
        int inset = (400 - 320) / 2, rows = 2 * S3kCompetitionScroll.PLAYER_1_RASTER_LINES;
        for (int i = 0; i < narrow.size(); i++) {
            int frame = 300 + 50 * i;
            if (!sameRgb(narrow.get(i)[0], wide.get(i)[0])) {
                ScreenshotCapture.savePNG(narrow.get(i)[0], PICTURES.resolve("split-320-" + frame + ".png"));
                ScreenshotCapture.savePNG(wide.get(i)[0], PICTURES.resolve("split-400-" + frame + ".png"));
                fail("the native split view changed with the logical width at frame " + frame);
            }
            // The 320 zoom is the middle of the 400 zoom: same planes, same body, 40 pixels further in.
            // Screen-anchored HUD pieces sit in the same place by design, so most, not all, pixels agree.
            var zoom = narrow.get(i)[1];
            var wideZoom = wide.get(i)[1];
            int equal = 0, shifted = 0;
            for (int y = 0; y < rows; y++) for (int x = 0; x < 320; x++) {
                if ((zoom.argb(x, y) & 0xFFFFFF) == (wideZoom.argb(x + inset, y) & 0xFFFFFF)) equal++;
                if ((zoom.argb(x, y) & 0xFFFFFF) == (wideZoom.argb(x + inset + 2, y) & 0xFFFFFF)) shifted++;
            }
            double exact = equal / (double) (320 * rows), control = shifted / (double) (320 * rows);
            if (exact <= 0.9) {
                ScreenshotCapture.savePNG(zoom, PICTURES.resolve("zoom-320-" + frame + ".png"));
                ScreenshotCapture.savePNG(wideZoom, PICTURES.resolve("zoom-400-" + frame + ".png"));
            }
            assertTrue(exact > 0.9, "the 400 zoom is not the 320 zoom with more columns at frame " + frame + ": " + exact);
            assertTrue(exact > control + 0.02, "column control did not separate at frame " + frame);
        }
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
