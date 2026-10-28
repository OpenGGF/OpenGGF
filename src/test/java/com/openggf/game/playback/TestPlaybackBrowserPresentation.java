package com.openggf.game.playback;

import com.openggf.control.InputHandler;
import com.openggf.game.MenuChrome;
import com.openggf.game.recording.*;
import com.openggf.game.save.SelectedTeam;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.TexturedQuadRenderer;
import com.openggf.testmode.TraceLaunchStatus;
import com.openggf.testmode.TraceRunFailureStatus;
import com.openggf.tools.PresentationOffscreenTestContext;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.catalog.TraceEntry;
import com.openggf.version.BuildIdentity;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.mockito.Mockito.mock;

/** Declared library summaries exercise real menu pixels, without booting gameplay or audio. */
@Isolated
class TestPlaybackBrowserPresentation {
    @Test
    void mixedLibraryAndSelectedPanelsDrawAtNativeAndWideSizes() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "Select surfaceless EGL for browser pixel QA");
        for (int width : new int[] {320, 400}) {
            try (var context = PresentationOffscreenTestContext.openConfigured(width, 224,
                    PresentationOffscreenTestContext.LegacyProfile.CORE_41)) {
                var renderer = new TexturedQuadRenderer();
                var font = new MenuPixelFont();
                try {
                    renderer.init();
                    font.init("pixel-font.png", renderer);
                    int margin = (width - 320) / 2;
                    renderer.setProjectionMatrix(new Matrix4f().ortho2D(-margin, width - margin, 0, 224).get(new float[16]));
                    glViewport(0, 0, width, 224);
                    glDisable(GL_DEPTH_TEST);
                    glEnable(GL_BLEND);
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

                    var nativeMovie = recording();
                    var trace = trace();
                    var bare = new PlaybackEntry.Movie(Path.of("downloaded-movie-without-launch-metadata.bk2"), 8700, null);
                    var invalid = new PlaybackEntry.Movie(Path.of("damaged-input.bk2"), 0, "Cannot read input archive");
                    var library = new PlaybackLibrary(List.of(nativeMovie, trace, bare, invalid), List.of());
                    var browser = browser(library, font);
                    RgbaImage mixed = draw(browser, width, renderer, font);
                    assertGold(mixed.argb(margin + 100, MenuChrome.HEADER_H - 2));
                    assertTrue(changedPixels(mixed, 40, 170) > 300, "Rows must replace the cleared framebuffer");
                    save(mixed, width, "01-library");

                    press(browser, GLFW_KEY_ENTER);
                    RgbaImage options = draw(browser, width, renderer, font);
                    assertTrue(differences(mixed, options, 45, 200) > 100, "Selecting a recording must draw its options");
                    save(options, width, "02-recording-options");
                    press(browser, GLFW_KEY_ESCAPE);
                    browser.selectEntry(bare);
                    press(browser, GLFW_KEY_ENTER);
                    RgbaImage setup = draw(browser, width, renderer, font);
                    assertTrue(differences(options, setup, 45, 200) > 100, "Unknown bootstrap must have a distinct details page");
                    save(setup, width, "03-bare-movie");

                    TraceLaunchStatus.record(trace.trace(), "The selected trace payload could not be read. Acknowledge to return and retry.");
                    var failureBrowser = browser(library, font);
                    RgbaImage failure = draw(failureBrowser, width, renderer, font);
                    assertTrue(differences(mixed, failure, 45, 200) > 100, "Held trace failure must draw before another launch");
                    save(failure, width, "04-trace-failure");
                    TraceLaunchStatus.clear();

                    var empty = browser(new PlaybackLibrary(List.of(), List.of()), font);
                    RgbaImage emptyImage = draw(empty, width, renderer, font);
                    assertTrue(differences(mixed, emptyImage, 45, 200) > 100, "An empty catalog must not retain previous rows");
                    save(emptyImage, width, "05-empty");

                    captureReviewScenes(width, renderer, font);
                    assertEquals(GL_NO_ERROR, glGetError());
                } finally {
                    TraceLaunchStatus.clear();
                    TraceRunFailureStatus.clear();
                    font.cleanup();
                    renderer.cleanup();
                }
            }
        }
    }

    /** Synthetic catalog states for design review; UI evidence only, not replay determinism. */
    private static void captureReviewScenes(int width, TexturedQuadRenderer renderer, MenuPixelFont font) throws Exception {
        var dense = denseLibrary();
        var browser = browser(dense, font);
        for (int i = 0; i < 7; i++) press(browser, GLFW_KEY_DOWN);
        save(draw(browser, width, renderer, font), width, "06-dense-scrolled-long-name");
        for (int i = 0; i < 7; i++) press(browser, GLFW_KEY_UP);
        press(browser, GLFW_KEY_UP);
        save(draw(browser, width, renderer, font), width, "07-show-filter-focus");
        press(browser, GLFW_KEY_UP);
        press(browser, GLFW_KEY_RIGHT);
        press(browser, GLFW_KEY_RIGHT);
        press(browser, GLFW_KEY_RIGHT);
        save(draw(browser, width, renderer, font), width, "08-game-filter-s3k");
        for (int i = 0; i < 4; i++) press(browser, GLFW_KEY_DOWN);
        press(browser, GLFW_KEY_F1);
        save(draw(browser, width, renderer, font), width, "09-trace-details");

        var missing = new PlaybackBrowser(new PlaybackLibrary(dense.entries().stream()
                .filter(entry -> !entry.gameId().equals("s1")).toList(), List.of()), "s1",
                PlaybackBrowser.Filter.RECORDINGS, false, font, launcher());
        save(draw(missing, width, renderer, font), width, "10-missing-game-scope");

        var partial = new PlaybackBrowser(new PlaybackLibrary(List.of(), List.of(
                "Trace catalog folder traces/s3k-archive could not be read: permission denied")),
                "", PlaybackBrowser.Filter.ALL, false, font, launcher());
        save(draw(partial, width, renderer, font), width, "11-empty-with-source-issue");
        press(partial, GLFW_KEY_F1);
        save(draw(partial, width, renderer, font), width, "12-source-issue-details");

        browser = browser(dense, font);
        browser.selectEntry(dense.entries().get(2));
        save(draw(browser, width, renderer, font), width, "13-invalid-selected");
        press(browser, GLFW_KEY_ENTER);
        save(draw(browser, width, renderer, font), width, "14-invalid-details");

        browser = browser(dense, font);
        press(browser, GLFW_KEY_ENTER);
        press(browser, GLFW_KEY_DOWN);
        press(browser, GLFW_KEY_ENTER);
        save(draw(browser, width, renderer, font), width, "15-recording-details");

        browser = browser(dense, font);
        browser.selectEntry(dense.entries().get(5));
        press(browser, GLFW_KEY_ENTER);
        save(draw(browser, width, renderer, font), width, "16-trace-loading");

        TraceRunFailureStatus.recordComparison(3,
                "level/S3K/HCZ/1 at boss arena camera lock after cutscene handover",
                "level/S3K/AIZ/2 still waiting for the results tally to close", 48213, 48960);
        save(draw(browser(dense, font), width, renderer, font), width, "17-run-comparison-failure");
        TraceRunFailureStatus.clear();
        TraceLaunchStatus.record(((PlaybackEntry.Trace) dense.entries().get(5)).trace(),
                "hardware_timing.jsonl row 1882: kind VDP_DMA ordinal 44 fingerprint 0x6c1f2a90 does not match "
                        + "the prepared production job at service boundary vblank_after_plc; "
                        + "contract violation ".repeat(12) + "final diagnostic");
        var paged = browser(dense, font);
        save(draw(paged, width, renderer, font), width, "18-launch-failure-page-1");
        press(paged, GLFW_KEY_RIGHT);
        save(draw(paged, width, renderer, font), width, "19-launch-failure-page-2");
        TraceLaunchStatus.clear();

        var traceOnly = new PlaybackBrowser(dense, "", PlaybackBrowser.Filter.ALL, true, font, launcher());
        save(draw(traceOnly, width, renderer, font), width, "20-trace-test-mode");
    }

    private static PlaybackLibrary denseLibrary() {
        return new PlaybackLibrary(List.of(
                recording("s2", "Emerald Hill route with Sonic and Tails", 5400, "2026-10-28T10:00:00Z",
                        RecordingVersionWarning.PRERELEASE_BUILD_MISMATCH, null),
                recording("s3k", "Angel Island 1 ring route practice for the full-game attempt with Knuckles", 21950,
                        "2026-10-27T21:14:00Z", RecordingVersionWarning.NONE, null),
                recording("s1", "Damaged recording", 0, "2026-10-26T08:30:00Z", RecordingVersionWarning.NONE,
                        "Movie payload is truncated after row 312"),
                recording("s1", "Green Hill 1 quick run", 2730, "2026-10-25T18:05:00Z", RecordingVersionWarning.NONE, null),
                recording("s3k", "Hydrocity 2 boss", 7320, "2026-10-21T12:00:00Z", RecordingVersionWarning.DIRTY_BUILD, null),
                trace("s3k", "aiz1_tails_cutscene_skip", 0, 0, 9200),
                trace("s1", "ghz1_fullrun", 0, 0, 3612),
                trace("s2", "ehz1_fullrun", 0, 0, 4011),
                trace("s3k", "s3k_aiz2_hcz1_knuckles_full_route_with_signpost_and_results", 1, 0, 30117),
                trace("s2", "cpz2_boss_sonic_tails", 1, 1, 5820),
                new PlaybackEntry.Movie(Path.of("tasvideos-submission-sonic2-any-percent-final-v3-2026.bk2"), 61203, null),
                new PlaybackEntry.Movie(Path.of("damaged-input.bk2"), 0, "Cannot read input archive: Input Log.txt is missing")),
                List.of());
    }

    private static PlaybackEntry.Recording recording(String game, String name, int frames, String at,
                                                     RecordingVersionWarning warning, String error) {
        Instant time = Instant.parse(at);
        var manifest = error != null ? null : new UserRecordingManifest(1, name,
                new BuildIdentity("0.7.prerelease", "sample", false),
                new RecordingLaunchContext(game, game.equals("s3k") ? 1 : 0, 0, game.equals("s3k") ? "knuckles" : "sonic",
                        game.equals("s2") ? List.of("tails") : List.of(), false, "current-act-fresh-start"),
                UserRecordingSidecarMetadata.everyFrame(), new RecordingDeterminismMetadata(null, null), "A", frames,
                UserRecordingStopReason.USER_STOPPED, time);
        return new PlaybackEntry.Recording(game, new UserRecordingEntry(
                Path.of("recordings/" + game + "/" + name.toLowerCase().replace(' ', '-') + ".bk2"),
                name, manifest, frames, time, warning, error));
    }

    private static PlaybackEntry.Trace trace(String game, String name, int zone, int act, int frames) {
        return new PlaybackEntry.Trace(new TraceEntry(Path.of("traces/" + game + "/" + name), game, zone, act,
                frames, 876, 0, new SelectedTeam(game.equals("s3k") ? "knuckles" : "sonic",
                game.equals("s2") ? List.of("tails") : List.of()), Path.of("traces/_movies/" + name + ".bk2"),
                mock(TraceMetadata.class)));
    }

    private static PlaybackLauncher launcher() {
        return new PlaybackLauncher((entry, options) -> { throw new AssertionError("UI capture must not launch gameplay"); },
                entry -> { throw new AssertionError("UI capture must not launch a trace"); }, () -> false, () -> false);
    }

    private static PlaybackBrowser browser(PlaybackLibrary library, MenuPixelFont font) {
        return new PlaybackBrowser(library, "", PlaybackBrowser.Filter.ALL, false, font,
                new PlaybackLauncher((entry, options) -> { throw new AssertionError("UI capture must not launch gameplay"); },
                        entry -> { throw new AssertionError("UI capture must not launch a trace"); }, () -> false, () -> false));
    }

    private static RgbaImage draw(PlaybackBrowser browser, int width, TexturedQuadRenderer renderer, MenuPixelFont font) {
        glClearColor(.25f, .1f, .08f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        int margin = (width - 320) / 2;
        renderer.setProjectionMatrix(new Matrix4f().ortho2D(0, width, 0, 224).get(new float[16]));
        if (margin > 0) {
            font.beginMegaBatch();
            MenuChrome.widePageFrame(font, width, margin);
            font.endMegaBatch();
        }
        renderer.setProjectionMatrix(new Matrix4f().ortho2D(-margin, width - margin, 0, 224).get(new float[16]));
        browser.render();
        glFinish();
        return ScreenshotCapture.captureFramebuffer(width, 224);
    }

    private static void press(PlaybackBrowser browser, int key) {
        var input = new InputHandler();
        input.handleKeyEvent(key, GLFW_PRESS);
        browser.update(input);
        input.handleKeyEvent(key, GLFW_RELEASE);
        input.update();
        browser.update(input);
    }

    private static PlaybackEntry.Recording recording() {
        var manifest = new UserRecordingManifest(1, "Emerald Hill route with Sonic and Tails",
                new BuildIdentity("0.7.prerelease", "sample", false),
                new RecordingLaunchContext("s2", 0, 0, "sonic", List.of("tails"), false, "current-act-fresh-start"),
                UserRecordingSidecarMetadata.everyFrame(), new RecordingDeterminismMetadata(null, null), "A", 5400,
                UserRecordingStopReason.USER_STOPPED, Instant.parse("2026-10-28T10:00:00Z"));
        return new PlaybackEntry.Recording("s2", new UserRecordingEntry(Path.of("recordings/s2/emerald-hill.bk2"),
                manifest.movieName(), manifest, 5400, manifest.createdAt(), RecordingVersionWarning.PRERELEASE_BUILD_MISMATCH, null));
    }

    private static PlaybackEntry.Trace trace() {
        return new PlaybackEntry.Trace(new TraceEntry(Path.of("traces/s3k/aiz1-knuckles-comparison"), "s3k", 0, 0,
                12400, 876, 0, new SelectedTeam("knuckles", List.of()), Path.of("traces/_movies/knuckles.bk2"),
                mock(TraceMetadata.class)));
    }

    private static void assertGold(int argb) {
        assertTrue(((argb >> 16) & 255) > 220 && ((argb >> 8) & 255) > 170 && (argb & 255) < 160,
                "Expected gold browser header, got " + Integer.toHexString(argb));
    }

    private static int changedPixels(RgbaImage image, int top, int bottom) {
        int clear = image.argb(0, 223);
        int count = 0;
        for (int y = top; y < bottom; y++) for (int x = 0; x < image.width(); x++) {
            if (image.argb(x, y) != clear) count++;
        }
        return count;
    }

    private static int differences(RgbaImage first, RgbaImage second, int top, int bottom) {
        int count = 0;
        for (int y = top; y < bottom; y++) for (int x = 0; x < first.width(); x++) {
            if (first.argb(x, y) != second.argb(x, y)) count++;
        }
        return count;
    }

    private static void save(RgbaImage image, int width, String scene) throws Exception {
        String directory = System.getProperty("openggf.playback.captureDir");
        if (directory == null || directory.isBlank()) return;
        Path output = Path.of(directory);
        Files.createDirectories(output);
        ScreenshotCapture.savePNG(image, output.resolve(width + "-" + scene + ".png"));
    }
}
