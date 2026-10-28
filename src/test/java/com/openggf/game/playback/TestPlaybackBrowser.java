package com.openggf.game.playback;

import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.recording.*;
import com.openggf.game.save.SelectedTeam;
import com.openggf.graphics.PixelFont;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.testmode.TraceLaunchStatus;
import com.openggf.testmode.TraceRunFailureStatus;
import com.openggf.trace.TraceFixtures;
import com.openggf.trace.catalog.TraceEntry;
import com.openggf.version.BuildIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.mockito.Mockito.mock;

class TestPlaybackBrowser {
    @BeforeEach @AfterEach
    void clearFailures() {
        TraceLaunchStatus.clear();
        TraceRunFailureStatus.clear();
    }

    @Test
    void nativeRecordingAndTraceUseOneListAndSelectionSurvivesDetails() {
        var recording = recording("s1");
        var trace = trace("s1", "trace");
        var browser = browser(List.of(recording, trace), "s1", false, null, new AtomicInteger());
        assertSame(recording, browser.selectedEntry());
        press(browser, GLFW_KEY_DOWN);
        assertSame(trace, browser.selectedEntry());
        press(browser, GLFW_KEY_F1);
        press(browser, GLFW_KEY_ESCAPE);
        assertSame(trace, browser.selectedEntry());
        assertEquals(PlaybackBrowser.Result.NONE, browser.consumeResult());
    }

    @Test
    void gameAndKindFiltersCanBeChangedWithControllerDirections() {
        var s1 = recording("s1");
        var s2 = trace("s2", "trace");
        var browser = browser(List.of(s1, s2), "s1", false, null, new AtomicInteger());
        controller(browser, AbstractPlayableSprite.INPUT_UP, 0);
        controller(browser, AbstractPlayableSprite.INPUT_UP, 0);
        controller(browser, AbstractPlayableSprite.INPUT_RIGHT, 0);
        controller(browser, AbstractPlayableSprite.INPUT_DOWN, 0);
        controller(browser, AbstractPlayableSprite.INPUT_DOWN, 0);
        assertSame(s2, browser.selectedEntry());
        controller(browser, AbstractPlayableSprite.INPUT_UP, 0);
        controller(browser, AbstractPlayableSprite.INPUT_RIGHT, 0);
        assertNull(browser.selectedEntry(), "The selected game has no engine recordings");
        controller(browser, AbstractPlayableSprite.INPUT_RIGHT, 0);
        assertSame(s2, browser.selectedEntry());
    }

    @Test
    void automaticTestModeCannotChangeIntoNativeOrBareMovieLaunches() {
        var trace = trace("s1", "trace");
        var browser = browser(List.of(recording("s1"), trace, new PlaybackEntry.Movie(Path.of("raw.bk2"), 10, null)),
                "", true, null, new AtomicInteger());
        assertSame(trace, browser.selectedEntry());
        press(browser, GLFW_KEY_UP);
        for (int i = 0; i < 6; i++) press(browser, GLFW_KEY_RIGHT);
        browser.selectEntry(recording("s1"));
        assertSame(trace, browser.selectedEntry());
    }

    @Test
    void successfulNativeLaunchReportsLaunchedRatherThanBack() {
        var calls = new AtomicInteger();
        var browser = browser(List.of(recording("s1")), "", false, null, calls);
        press(browser, GLFW_KEY_ENTER);
        assertEquals(0, calls.get());
        press(browser, GLFW_KEY_ENTER);
        assertEquals(1, calls.get());
        assertEquals(PlaybackBrowser.Result.LAUNCHED, browser.consumeResult());
        assertEquals(PlaybackBrowser.Result.NONE, browser.consumeResult());
    }

    @Test
    void traceLaunchWaitsForLoadingDrawAndFailureAcknowledgmentAllowsExplicitRetry() {
        var trace = trace("s1", "trace");
        var calls = new AtomicInteger();
        var launcher = new PlaybackLauncher((e, o) -> fail(), entry -> {
            calls.incrementAndGet();
            TraceLaunchStatus.record(entry, "Unreadable trace payload");
            return false;
        }, () -> false, () -> false);
        var browser = new PlaybackBrowser(new PlaybackLibrary(List.of(trace), List.of()), "",
                PlaybackBrowser.Filter.ALL, false, mock(PixelFont.class), launcher);
        press(browser, GLFW_KEY_ENTER);
        assertEquals(0, calls.get());
        browser.render();
        browser.update(new InputHandler());
        assertEquals(1, calls.get());
        assertTrue(TraceLaunchStatus.current().isPresent());
        press(browser, GLFW_KEY_ENTER);
        assertTrue(TraceLaunchStatus.current().isEmpty());
        assertEquals(1, calls.get());
        assertSame(trace, browser.selectedEntry());
        press(browser, GLFW_KEY_ENTER);
        browser.render();
        browser.update(new InputHandler());
        assertEquals(2, calls.get());
    }

    @Test
    void bareMovieExplainsMissingSetupAndCannotSubmitAPlaybackRequest() {
        var calls = new AtomicInteger();
        var text = new ArrayList<String>();
        var movie = new PlaybackEntry.Movie(Path.of("raw.bk2"), 12, null);
        var browser = browser(List.of(movie), "", false, font(text), calls);
        press(browser, GLFW_KEY_ENTER);
        browser.render();
        assertEquals(0, calls.get());
        assertTrue(String.join("", text).toLowerCase().contains("setup"));
        press(browser, GLFW_KEY_ESCAPE);
        assertEquals(PlaybackBrowser.Result.NONE, browser.consumeResult());
        press(browser, GLFW_KEY_ESCAPE);
        assertEquals(PlaybackBrowser.Result.BACK, browser.consumeResult());
    }

    @Test
    void aReturnedRunFailureIsPresentedBeforeTheLibraryAndDoesNotLaunch() {
        TraceRunFailureStatus.recordReason(2, "wrong destination", 123, 456);
        var text = new ArrayList<String>();
        var calls = new AtomicInteger();
        var browser = browser(List.of(recording("s1")), "", false, font(text), calls);
        browser.markFailureFeedbackAnnounced();
        browser.render();
        assertTrue(String.join("", text).contains("wrong destination"));
        press(browser, GLFW_KEY_ENTER);
        assertTrue(TraceRunFailureStatus.current().isEmpty());
        assertEquals(0, calls.get());
        assertEquals(PlaybackBrowser.Result.NONE, browser.consumeResult());
    }

    @Test
    void restoreSelectionMatchesNormalizedPathAndKindWithoutEscapingFilters() {
        var first = trace("s1", "first");
        var second = trace("s1", "second");
        var browser = browser(List.of(first, second), "s1", false, null, new AtomicInteger());
        browser.selectEntry(trace("s1", "./second"));
        assertSame(second, browser.selectedEntry());
        browser.selectEntry(recording("s2"));
        assertSame(second, browser.selectedEntry());
    }

    @Test
    void largeLibraryEmptyFiltersSourceIssuesAndDetailsStayWithinNativeBounds() {
        List<PlaybackEntry> entries = new ArrayList<>();
        for (int i = 0; i < 30; i++) entries.add(trace("s1", "trace-" + i + "-" + "long-name".repeat(8)));
        List<String> text = new ArrayList<>();
        var browser = new PlaybackBrowser(new PlaybackLibrary(entries, List.of("Trace directory could not be read")),
                "", PlaybackBrowser.Filter.ALL, false, font(text), launcher(new AtomicInteger()));
        for (int i = 0; i < 30; i++) {
            browser.render();
            press(browser, GLFW_KEY_DOWN);
        }
        press(browser, GLFW_KEY_F1);
        for (int i = 0; i < 20; i++) { browser.render(); press(browser, GLFW_KEY_DOWN); }
        assertFalse(text.isEmpty());
    }

    @Test
    void rowsNameTracesByIdentityWithLengthAndDetailsWrapWholeWords() {
        List<String> text = new ArrayList<>();
        var movie = new PlaybackEntry.Movie(Path.of("downloaded-movie-without-launch-metadata.bk2"), 8700, null);
        var browser = browser(List.of(trace("s1", "ghz1_fullrun"), movie), "", false, font(text), new AtomicInteger());
        browser.render();
        assertTrue(text.contains("ghz1_fullrun"), text.toString());
        assertTrue(text.contains("0:01"), "Rows show length as minutes and seconds: " + text);
        assertTrue(text.stream().noneMatch(line -> line.contains("Zone: ")), "Zone index text stays out of rows");
        assertTrue(text.contains("SETUP"));
        text.clear();
        press(browser, GLFW_KEY_DOWN);
        press(browser, GLFW_KEY_F1);
        browser.render();
        List<String> status = text.subList(text.indexOf("Status") + 1, text.indexOf("Length"));
        assertEquals(movie.availability().reason(), String.join("", status), "The complete reason is drawn");
        assertTrue(status.size() > 1, "The long reason wraps at native width");
        status.subList(0, status.size() - 1).forEach(line -> assertTrue(line.endsWith(" "),
                "Wraps fall between words, not at a fixed character count: " + status));
    }

    @Test
    void emptyScopesNameTheMissingKindAndWhereToAddIt() {
        List<String> text = new ArrayList<>();
        var browser = new PlaybackBrowser(new PlaybackLibrary(List.of(recording("s2")), List.of()), "s1",
                PlaybackBrowser.Filter.TRACES, false, font(text), launcher(new AtomicInteger()));
        browser.render();
        assertTrue(text.contains("No Sonic 1 ROM traces"), text.toString());
        // The help sentence wraps by measured width, so it names the place on whichever line it lands.
        assertTrue(text.stream().anyMatch(line -> line.contains("Advanced settings.")), text.toString());
        assertTrue(text.stream().noneMatch(line -> line.equals("OPEN")), "Nothing to open in an empty list");
        assertTrue(text.stream().noneMatch(line -> line.equals("DETAILS")), "No details for an empty, readable library");
    }

    private static PlaybackBrowser browser(List<PlaybackEntry> entries, String game, boolean traceOnly,
                                           PixelFont font, AtomicInteger calls) {
        return new PlaybackBrowser(new PlaybackLibrary(entries, List.of()), game,
                PlaybackBrowser.Filter.ALL, traceOnly, font, launcher(calls));
    }

    private static PlaybackLauncher launcher(AtomicInteger calls) {
        return new PlaybackLauncher((e, o) -> calls.incrementAndGet(), e -> { calls.incrementAndGet(); return true; },
                () -> false, () -> false);
    }

    private static void press(PlaybackBrowser browser, int key) {
        var input = new InputHandler();
        input.handleKeyEvent(key, GLFW_PRESS);
        browser.update(input);
        input.handleKeyEvent(key, GLFW_RELEASE);
        input.update();
        browser.update(input);
    }

    private static void controller(PlaybackBrowser browser, int direction, int action) {
        var input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(direction, direction, action, action, false, false), PlayerInputState.neutral()));
        browser.update(input);
    }

    private static PixelFont font(List<String> text) {
        return new PixelFont() {
            @Override public void beginMegaBatch() { }
            @Override public void endMegaBatch() { }
            @Override public void drawText(String value, int x, int y, float scale, float r, float g, float b, float a) {
                text.add(value);
                assertTrue(x >= 0 && x + com.openggf.graphics.MenuPixelFont.textWidth(value, scale) <= 320, value);
                assertTrue(y >= 0 && y + (scale < 1 ? 8 : 10) <= 224, value);
            }
        };
    }

    private static PlaybackEntry.Recording recording(String game) {
        var at = Instant.parse("2026-06-29T14:30:22Z");
        var manifest = new UserRecordingManifest(1, "Engine recording", new BuildIdentity("0.7.prerelease", "abc", false),
                new RecordingLaunchContext(game, 0, 0, "sonic", List.of(), false, "current-act-fresh-start"),
                UserRecordingSidecarMetadata.everyFrame(), new RecordingDeterminismMetadata(0, null), "A", 120,
                UserRecordingStopReason.USER_STOPPED, at);
        return new PlaybackEntry.Recording(game,
                new UserRecordingEntry(Path.of(game + ".bk2"), "Engine recording", manifest, 120, at,
                        RecordingVersionWarning.NONE, null));
    }

    private static PlaybackEntry.Trace trace(String game, String name) {
        return new PlaybackEntry.Trace(new TraceEntry(Path.of(name), game, 0, 0, 100, 0, 0,
                new SelectedTeam("sonic", List.of()), Path.of(name + ".bk2"), TraceFixtures.metadata(game, 0, 0)));
    }
}
