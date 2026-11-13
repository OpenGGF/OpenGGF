package com.openggf.game.rewind;

import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.DebugColor;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.presentation.TransportHud;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.WorldSession;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestLiveRewindHudOverlay {

    @Test
    void palBudgetSecondsUseTheRegionalCadence() {
        var message = LiveRewindHudOverlay.message(9,
                new LiveRewindManager.BudgetHudState(true, false, 450, 500, false, 50));
        assertEquals("9.0s", message.value());
        assertEquals("10.0s", message.valueReserve());
    }

    @Test
    void renderDrawsNothingWithoutAnEligibleFrameOrBudget() {
        RecordingTextRenderer text = new RecordingTextRenderer();
        LiveRewindHudOverlay.render(text, null, 320, null, null);

        assertTrue(text.draws.isEmpty(), "an idle manager supplies neither an eligible frame nor a visible budget");
    }

    @Test
    void renderDrawsTitleAndFrameWhenRewindFrameIsAvailable() {
        RecordingTextRenderer text = new RecordingTextRenderer();
        LiveRewindHudOverlay.render(text, null, 320, 12, null);

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
        var message = LiveRewindHudOverlay.message(1248, budget(384, 600, false));

        assertEquals(TransportHud.Status.REWIND, message.status());
        assertEquals("6.4s", message.value());
        assertEquals("10.0s", message.valueReserve(), "the plate keeps the maximum's width while counting");
        assertEquals(64, message.progressPercent());
        assertEquals("0.1s", LiveRewindHudOverlay.seconds(1), "any remaining frame reads above zero");
        assertEquals(1, LiveRewindHudOverlay.message(1, budget(1, 600, false)).progressPercent(),
                "the meter keeps a lit pixel until the stop");
    }

    @Test
    void lowAndDrainedBudgetsChangeStatusRatherThanOnlyTheirNumbers() {
        assertEquals(TransportHud.Status.REWIND, LiveRewindHudOverlay.message(9, budget(151, 600, false)).status());
        assertEquals(TransportHud.Status.REWIND_LOW, LiveRewindHudOverlay.message(9, budget(150, 600, false)).status());
        var drained = LiveRewindHudOverlay.message(null, budget(0, 600, true));
        assertEquals(TransportHud.Status.REWIND_EMPTY, drained.status(), "a denied press shows the drained plate");
        assertEquals("0.0s", drained.value());
        assertEquals(0, drained.progressPercent());
        assertEquals(TransportHud.Status.REWIND_EMPTY,
                LiveRewindHudOverlay.message(9, budget(4, 600, true)).status(), "empty outranks a stale count");
    }

    @Test
    void unlimitedOrHiddenBudgetKeepsTheFrameCounterWithoutAMeter() {
        var unlimited = LiveRewindHudOverlay.message(12,
                new LiveRewindManager.BudgetHudState(true, true, 0, 0, false));
        assertEquals("F:12", unlimited.value());
        assertEquals(-1, unlimited.progressPercent());
        assertNull(LiveRewindHudOverlay.message(null, new LiveRewindManager.BudgetHudState(false, false, 0, 600, true)));
    }

    @Test
    void frameZeroAndTheLargestFrameRemainCompleteCounters() {
        assertEquals("F:0", LiveRewindHudOverlay.message(0, null).value());
        assertEquals("F:2147483647", LiveRewindHudOverlay.message(Integer.MAX_VALUE, null).value());
    }

    @Test
    void drainedPlateStaysAboveTheLivesRowInOneRow() {
        RecordingTextRenderer text = new RecordingTextRenderer();
        LiveRewindHudOverlay.render(text, null, 320, null, budget(0, 600, true));

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

    @Test
    void scriptedRewindKeepsItsHistoryAndPresentationWithoutTheLiveHud() {
        TestEnvironment.resetAll();
        var config = SonicConfigurationService.createStandalone();
        config.resetToDefaults();
        config.setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        var audio = EngineServices.current().audio();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        ScriptedRewind script = mock(ScriptedRewind.class);
        when(script.requested()).thenReturn(true);
        // A scripted zero-step frame holds the picture without needing recorded history.
        when(script.stepsThisFrame()).thenReturn(0);
        var module = new DelegatingGameModule(GameModuleRegistry.getCurrent(), "test:scripted-rewind-hud") {
            @Override public ScriptedRewind scriptedRewind() { return script; }
        };
        GameplayModeContext owner = new GameplayModeContext(new WorldSession(module));
        GameplaySessionFactory.attachManagers(owner, EngineServices.current());
        LiveRewindManager manager = new LiveRewindManager(config, audio);
        try {
            manager.bindOwner(owner, false, GameMode.LEVEL);
            assertNotNull(owner.getRewindController(), "the scripted owner must receive ordinary rewind history");
            assertTrue(manager.handleRealtimeRewindInput(GameMode.LEVEL, false, new InputHandler()));
            assertTrue(manager.isRewindingOrReleasing(), "the real scripted branch must be active");
            assertFalse(manager.budgetHudState().visible(), "scripted rewind owns its presentation");
            RecordingTextRenderer text = new RecordingTextRenderer();

            manager.renderHud(GameMode.LEVEL, text, null, 320);

            assertTrue(text.draws.isEmpty(), "neither the frame counter nor the budget belongs over scripted rewind");
        } finally {
            manager.bindOwner(null, false, GameMode.MASTER_TITLE_SCREEN);
            owner.destroy();
            audio.resetState();
        }
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
