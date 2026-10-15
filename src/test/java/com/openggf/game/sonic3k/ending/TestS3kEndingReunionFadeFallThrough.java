package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ending_ScreenEvent's reunion fade-in has no rts between its states: loc_5AE90 falls
 * into loc_5AEA8 (first Pal_FromBlack/subq on the 48->52 frame) and loc_5AEA8's expiry
 * falls into loc_5AED4/sub_5B18E (credits init on the 52->56 frame), sonic3k.asm:121156-121182.
 * Native declared Tails/Super capture: fg 52 at Level_frame_counter 4459, fg 56 at 4480.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingReunionFadeFallThrough {
    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @Test
    void reunionFadeRunsItsFirstStepAndCreditsInitOnTheTransitionFrames() {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        GameServices.camera().refreshViewportDimensions(config);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(13, 1)
                .withFreshLevelStartLifecycle().build();
        fixture.stepFrame(false, false, false, false, false);
        var state = (EndingZoneRuntimeState) GameServices.zoneRuntimeState();
        var events = (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();

        // loc_5AE90 admits only once Events_routine_bg <= 4.
        state.screen().foregroundRoutine = 48;
        state.screen().backgroundRoutine = 4;
        var beforeFade = registry.capture();
        events.update();
        assertEquals(52, state.screen().foregroundRoutine);
        assertEquals(0x14, state.screen().fadeTimer,
                "loc_5AEA8's first subq runs in the loc_5AE90 dispatch");
        var afterFade = registry.capture();
        registry.restore(beforeFade);
        events.update();
        same(afterFade, registry.capture(), "replay the 48->52 fall-through");

        // Final Pal_FromBlack: the expiring subq falls into loc_5AED4.
        state.screen().fadeTimer = 0;
        assertFalse(state.ending().credits().active());
        var beforeCredits = registry.capture();
        events.update();
        assertEquals(56, state.screen().foregroundRoutine);
        assertTrue(state.ending().credits().active(), "loc_5B1A2 initializes on the 52->56 frame");
        assertEquals(2, state.ending().credits().routine(), "_unkFA86 advanced to the delay state");
        assertEquals(0, GameServices.camera().getY());
        var afterCredits = registry.capture();
        registry.restore(beforeCredits);
        same(beforeCredits, registry.capture(), "restore before credits init");
        events.update();
        same(afterCredits, registry.capture(), "replay the 52->56 fall-through");

        // loc_5B204 advances Camera_Y_pos only; ScreenEvents published Camera_Y_pos_copy first,
        // so the new position reaches V_scroll_value one ScreenEvents pass later.
        var camera = GameServices.camera();
        for (int frame = 0; frame < 2000 && state.ending().credits().routine() != 4; frame++) {
            camera.captureRenderCopy();
            events.update();
        }
        assertEquals(4, state.ending().credits().routine(), "credits reached loc_5B204 scrolling");
        int moved = 0;
        for (int frame = 0; frame < 8; frame++) {
            camera.captureRenderCopy();
            short published = camera.getYCopy();
            short before = camera.getY();
            events.update();
            assertEquals(published, camera.getYCopy(), "the event handler keeps the published copy");
            if (camera.getY() != before) moved++;
        }
        assertTrue(moved > 0, "Camera_Y_pos advanced at the native fractional speed");
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (var key : expected.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diff.isEmpty(), () -> label + " " + key + ": " + diff);
        }
    }
}
