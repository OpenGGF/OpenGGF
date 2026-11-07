package com.openggf.game.sonic2.competition.view;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TestSonic2CompetitionRingPhase {
    @Test
    void readsOnlyRetainedPhaseOwnersAndMarksUnwiredColdStateWithoutReadingLfc() {
        assertEquals(-1, Sonic2CompetitionDisplayState.INACTIVE.ringsAnimationFrame());
        assertEquals(0, Sonic2CompetitionDisplaySources.ringFrame(-1, -1));
        assertEquals(3, Sonic2CompetitionDisplaySources.ringFrame(-1, 3));
        assertEquals(2, Sonic2CompetitionDisplaySources.ringFrame(2, 3));
        assertThrows(IllegalArgumentException.class, () -> Sonic2CompetitionDisplaySources.ringFrame(4, -1));
    }
    /** Independent byte-counter reference; no production closed-form phase is implemented. */
    @Test
    void levelInactiveRestartDisprovesClosedFormAcrossTheRequiredLifecycle() {
        var state = new Counter();
        // Level_TtlCard and locked leave: neither LFC nor ChangeRingFrame.
        for (int i = 0; i < 25; i++) state.waitOnly();
        assertEquals(0, state.frame);
        for (int i = 0; i < 8; i++) state.completedLevelLoop();
        assertEquals(8, state.lfc);
        assertEquals(1, state.frame);
        // Pause_Loop, lag interrupt, and TwoPlayerResults loop do not call either owner.
        for (int i = 0; i < 120; i++) state.waitOnly();
        assertEquals(8, state.lfc);
        assertEquals(1, state.frame);
        // Level_MainLoop (s2.asm:5092-5097) increments before RunObjects can set
        // Level_Inactive_flag, and branches to Level before its ring-frame call at line5109.
        state.lfc = (state.lfc + 1) & 65535;
        assertEquals(9, state.lfc);
        assertEquals(1, state.frame);
        assertNotEquals(((state.lfc + 7) / 8) & 3, state.frame, "ceil(LFC/8) is not the retained state on this path");
        // Ordinary 2P act reload: LFC reset at :4773 and Oscillating_Data clear :4810.
        state = new Counter();
        for (int i = 0; i < 25; i++) state.waitOnly();
        assertEquals(0, state.frame);
        state.completedLevelLoop();
        assertEquals(1, state.frame);
    }
    private static final class Counter {
        int lfc, counter, frame;
        void waitOnly() {}
        void completedLevelLoop() {
            lfc = (lfc + 1) & 65535;
            counter = (counter - 1) & 255;
            if ((byte) counter < 0) {
                counter = 7;
                frame = (frame + 1) & 3;
            }
        }
    }
}
