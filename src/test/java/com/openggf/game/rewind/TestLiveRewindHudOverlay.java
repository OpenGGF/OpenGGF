package com.openggf.game.rewind;

import com.openggf.debug.DebugColor;
import com.openggf.graphics.PixelFontTextRenderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import com.openggf.game.presentation.TransportHud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLiveRewindHudOverlay {

    @Test
    void palBudgetSecondsUseTheRegionalCadence() {
        var message = LiveRewindHudOverlay.message("REWIND 9",
                new LiveRewindManager.BudgetHudState(true, false, 450, 500, false, 50));
        assertEquals("9.0s", message.value());
        assertEquals("10.0s", message.valueReserve());
    }

    @Test
    void renderDrawsNothingWhenStatusIsBlank() {
        RecordingTextRenderer text = new RecordingTextRenderer();
        LiveRewindHudOverlay overlay = new LiveRewindHudOverlay(() -> "");

        overlay.render(text, null, 320);

        assertTrue(text.draws.isEmpty(), "blank status should not render a live rewind HUD");
    }

    @Test
    void renderDrawsTitleAndStatusWhenRewindingStatusIsAvailable() {
        RecordingTextRenderer text = new RecordingTextRenderer();
        LiveRewindHudOverlay overlay = new LiveRewindHudOverlay(() -> "REWIND 12");

        overlay.render(text, null, 320);

        assertTrue(text.draws.stream().anyMatch(draw -> draw.text.equals("REW")));
        assertTrue(text.draws.stream().anyMatch(draw -> draw.text.equals("F:12")));
        assertTrue(text.draws.stream().allMatch(draw -> draw.scale == 1),
                "gameplay glyphs must keep their authored integer pixel size");
        assertTrue(text.draws.stream().allMatch(draw -> draw.y + 10 < 200),
                "rewind text must finish above the original lives row");
        assertFalse(text.batchOpen, "overlay should close its text batch");
    }

    @Test
    void finiteBudgetShowsRoundedUpSecondsAgainstTheMaximumsReservedWidth() {
        var message = LiveRewindHudOverlay.message("REWIND 1248", budget(384, 600, false));

        assertEquals(TransportHud.Status.REWIND, message.status());
        assertEquals("6.4s", message.value());
        assertEquals("10.0s", message.valueReserve(), "the plate keeps the maximum's width while counting");
        assertEquals(64, message.progressPercent());
        assertEquals("0.1s", LiveRewindHudOverlay.seconds(1), "any remaining frame reads above zero");
        assertEquals(1, LiveRewindHudOverlay.message("REWIND 1", budget(1, 600, false)).progressPercent(),
                "the meter keeps a lit pixel until the stop");
    }

    @Test
    void lowAndDrainedBudgetsChangeStatusRatherThanOnlyTheirNumbers() {
        assertEquals(TransportHud.Status.REWIND, LiveRewindHudOverlay.message("REWIND 9", budget(151, 600, false)).status());
        assertEquals(TransportHud.Status.REWIND_LOW, LiveRewindHudOverlay.message("REWIND 9", budget(150, 600, false)).status());
        var drained = LiveRewindHudOverlay.message(null, budget(0, 600, true));
        assertEquals(TransportHud.Status.REWIND_EMPTY, drained.status(), "a denied press shows the drained plate");
        assertEquals("0.0s", drained.value());
        assertEquals(0, drained.progressPercent());
        assertEquals(TransportHud.Status.REWIND_EMPTY,
                LiveRewindHudOverlay.message("REWIND 9", budget(4, 600, true)).status(), "empty outranks a stale count");
    }

    @Test
    void unlimitedOrHiddenBudgetKeepsTheFrameCounterWithoutAMeter() {
        var unlimited = LiveRewindHudOverlay.message("REWIND 12",
                new LiveRewindManager.BudgetHudState(true, true, 0, 0, false));
        assertEquals("F:12", unlimited.value());
        assertEquals(-1, unlimited.progressPercent());
        assertNull(LiveRewindHudOverlay.message(null, new LiveRewindManager.BudgetHudState(false, false, 0, 600, true)));
    }

    @Test
    void drainedPlateStaysAboveTheLivesRowInOneRow() {
        RecordingTextRenderer text = new RecordingTextRenderer();
        new LiveRewindHudOverlay(() -> null, () -> budget(0, 600, true)).render(text, null, 320);

        assertTrue(text.draws.stream().anyMatch(draw -> draw.text.equals("0.0s")));
        assertEquals(1, text.draws.stream().map(Draw::y).distinct().count(), "one row");
        assertTrue(text.draws.stream().allMatch(draw -> draw.y + 10 < 200));
    }

    private static LiveRewindManager.BudgetHudState budget(int remaining, int maximum, boolean empty) {
        return new LiveRewindManager.BudgetHudState(true, false, remaining, maximum, empty);
    }

    @Test
    void liveRewindManagerDrawsNothingWhenEnabledButIdle() {
        RecordingTextRenderer text = new RecordingTextRenderer();
        var config = com.openggf.configuration.SonicConfigurationService.getInstance();
        config.resetToDefaults();
        config.setConfigValue(com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
        LiveRewindManager manager = new LiveRewindManager(config, com.openggf.audio.AudioManager.getInstance());
        manager.bindOwner(com.openggf.tests.TestEnvironment.activeGameplayMode(), false, com.openggf.game.GameMode.LEVEL);

        manager.renderHud(com.openggf.game.GameMode.LEVEL, text, null, 320);

        assertTrue(text.draws.isEmpty(), "idle live rewind must not render HUD text");
    }

    private static final class RecordingTextRenderer extends PixelFontTextRenderer {
        private final List<Draw> draws = new ArrayList<>();
        private boolean batchOpen;

        @Override
        public void beginBatch() {
            batchOpen = true;
        }

        @Override
        public void endBatch() {
            batchOpen = false;
        }

        @Override
        public void drawShadowedText(String text, int x, int y, DebugColor color, float scale) {
            draws.add(new Draw(text, x, y, color, scale));
        }
    }

    private record Draw(String text, int x, int y, DebugColor color, float scale) {}
}
