package com.openggf.game.rewind;

import com.openggf.debug.DebugColor;
import com.openggf.graphics.PixelFontTextRenderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLiveRewindHudOverlay {

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
