package com.openggf.game.playback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.graphics.PixelFont;
import com.openggf.i18n.UiMessages;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.catalog.TraceEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestPlaybackLocalizedFacts {
    @AfterEach void restoreLanguage() { UiMessages.selectLanguage("en"); }

    @Test void specialStageSummaryAndDetailsUseMetadataInEveryDirection() throws Exception {
        var metadata = new ObjectMapper().readValue("""
                {"game":"s2","trace_profile":"s2_special_stage","special_stage_index":2}
                """, TraceMetadata.class);
        var trace = new TraceEntry(Path.of("traces/stage"), "s2", 98, 97, 100, 0, 0,
                null, Path.of("movie.bk2"), metadata);
        assertEquals("S2 SPECIAL STAGE 3", trace.displayLabel());
        for (String language : List.of("fr", "ja", "ar")) {
            UiMessages.selectLanguage(language);
            String expected = UiMessages.text("menu.playback.special_stage", Map.of("stage", 3));
            assertNotEquals("Special stage 3", expected, "the test needs a translated stage label");
            var entry = new PlaybackEntry.Trace(trace);
            assertTrue(entry.details().stream().anyMatch(fact -> fact.value().contains(expected)), language);
            var drawn = new ArrayList<String>();
            var browser = new PlaybackBrowser(new PlaybackLibrary(List.of(entry), List.of()), "s2",
                    PlaybackBrowser.Filter.ALL, false, capture(drawn),
                    new PlaybackLauncher((ignored, options) -> fail("read-only rendering"),
                            ignored -> fail("read-only rendering"), () -> false, () -> false));
            browser.render();
            assertTrue(drawn.stream().anyMatch(line -> line.contains(expected)), () -> language + ": " + drawn);
        }
    }

    @Test void recordingEmptyStateIsACompleteLocalizedSentence() {
        for (String language : List.of("fr", "ja", "ar")) {
            UiMessages.selectLanguage(language);
            String expected = UiMessages.text("menu.playback.empty_recordings",
                    Map.of("scope", "all", "game", ""));
            var drawn = new ArrayList<String>();
            var browser = new PlaybackBrowser(new PlaybackLibrary(List.of(), List.of()), "",
                    PlaybackBrowser.Filter.RECORDINGS, false, capture(drawn),
                    new PlaybackLauncher((ignored, options) -> fail("empty library"),
                            ignored -> fail("empty library"), () -> false, () -> false));
            browser.render();
            assertTrue(drawn.contains(expected), () -> language + ": " + drawn);
            assertFalse(expected.contains("engine recordings"));
        }
    }

    @Test void completeExtentCountsKeepTheirUnitAndPlural() {
        UiMessages.selectLanguage("en");
        assertEquals("1 BK2 row", new PlaybackEntry.Extent.MovieRows(1).label());
        assertEquals("2 BK2 rows", new PlaybackEntry.Extent.MovieRows(2).label());
        assertEquals("1 trace frame", new PlaybackEntry.Extent.TraceFrames(1).label());
        assertEquals("2 run trace frames", new PlaybackEntry.Extent.RunTraceFrames(2).label());
    }

    @Test void technicalCountersAndInputOffsetsKeepCanonicalAsciiDigits() throws Exception {
        var metadata = new ObjectMapper().readValue("{\"game\":\"s2\"}", TraceMetadata.class);
        var trace = new TraceEntry(Path.of("traces/level"), "s2", 0, 0, 1234567, 1234567, 0,
                null, Path.of("movie.bk2"), metadata);
        for (String language : List.of("fr", "ar")) {
            UiMessages.selectLanguage(language);
            for (int count : new int[] {0, 1, 2, 1234567}) {
                String value = Integer.toString(count);
                for (var extent : List.of(new PlaybackEntry.Extent.MovieRows(count),
                        new PlaybackEntry.Extent.TraceFrames(count), new PlaybackEntry.Extent.RunTraceFrames(count)))
                    assertTrue(extent.label().contains(value), extent.label());
                assertTrue(UiMessages.text("menu.playback.run_segments", Map.of("count", count, "value", value)).contains(value));
            }
            String input = new PlaybackEntry.Trace(trace).details().stream()
                    .filter(fact -> fact.label().equals(UiMessages.text("menu.playback.input_label")))
                    .findFirst().orElseThrow().value();
            assertTrue(input.contains("1234567"), input);
        }
    }

    private static PixelFont capture(List<String> drawn) {
        return new PixelFont() {
            @Override public void beginMegaBatch() { }
            @Override public void endMegaBatch() { }
            @Override public void drawText(String text, int x, int y, float scale,
                                           float r, float g, float b, float a) { drawn.add(text); }
        };
    }
}
