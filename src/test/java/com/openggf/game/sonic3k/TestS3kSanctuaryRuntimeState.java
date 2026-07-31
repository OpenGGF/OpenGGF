package com.openggf.game.sonic3k;

import com.openggf.game.GameStateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kSanctuaryRuntimeState {
    private GameStateManager gameState;
    private S3kEmeraldProgression progression;

    @BeforeEach
    void setUp() {
        gameState = new GameStateManager();
        progression = S3kEmeraldProgression.from(gameState);
    }

    @Test
    void introCountdownUsesNativeSubqBplBoundaryAndLatchesConversionBeforeFirstChange() {
        S3kEmeraldProgression.restore(gameState, List.of(1, 0, 0, 0, 0, 1, 0), false);
        S3kSanctuaryRuntimeState state = new S3kSanctuaryRuntimeState(progression, false);

        assertEquals(S3kSanctuaryRuntimeState.Phase.INTRO_WAIT, state.phase());
        for (int i = 0; i < 31; i++) {
            assertFalse(state.updateIntro());
        }
        assertFalse(progression.isConverted());

        assertTrue(state.updateIntro());
        assertEquals(S3kSanctuaryRuntimeState.Phase.WAITING_FOR_INTRO_SIGNAL, state.phase());
        assertFalse(progression.isConverted());

        assertTrue(state.signalIntroComplete());
        assertTrue(progression.isConverted());
        assertEquals(List.of(5, 0), state.conversionOrder());
        assertEquals(List.of(1, 0, 0, 0, 0, 1, 0), progression.states());
    }

    @Test
    void scanOrderMatchesRomAndSequentialConversionDoesNotReplayOnReentry() {
        S3kEmeraldProgression.restore(gameState, List.of(1, 1, 1, 1, 1, 1, 1), false);
        S3kSanctuaryRuntimeState state = new S3kSanctuaryRuntimeState(progression, false);
        state.skipIntroForTest();
        state.signalIntroComplete();

        assertEquals(List.of(5, 3, 1, 0, 2, 4, 6), state.conversionOrder());
        for (int index : List.of(5, 3, 1, 0, 2, 4, 6)) {
            assertEquals(index, state.convertNext());
        }
        assertEquals(-1, state.convertNext());
        assertEquals(List.of(2, 2, 2, 2, 2, 2, 2), progression.states());
        assertEquals(S3kSanctuaryRuntimeState.Phase.READY, state.phase());

        S3kSanctuaryRuntimeState reentry = new S3kSanctuaryRuntimeState(progression, true);
        assertEquals(S3kSanctuaryRuntimeState.Phase.READY, reentry.phase());
        assertTrue(reentry.conversionOrder().isEmpty());
    }

    @Test
    void zeroEmeraldsEnableExitButGrayEmeraldsKeepItClosed() {
        S3kEmeraldProgression.restore(gameState, List.of(0, 0, 0, 0, 0, 0, 0), false);
        S3kSanctuaryRuntimeState empty = new S3kSanctuaryRuntimeState(progression, true);
        assertTrue(empty.exitEligible());

        S3kEmeraldProgression.restore(gameState, List.of(0, 2, 0, 0, 0, 0, 0), true);
        S3kSanctuaryRuntimeState incomplete = new S3kSanctuaryRuntimeState(progression, true);
        assertFalse(incomplete.exitEligible());
        incomplete.markSpecialStageResult(1, false);
        assertEquals(2, progression.states().get(1));
        progression.awardSuper(1);
        incomplete.markSpecialStageResult(1, true);
        assertEquals(3, progression.states().get(1));
        assertFalse(incomplete.exitEligible(), "return transform blocks the exit");
        incomplete.completeReturnTransformation();
        assertTrue(incomplete.exitEligible());
        assertTrue(incomplete.isReentry());
    }

    @Test
    void successfulResultOnlyAdvancesTheSelectedGrayState() {
        S3kEmeraldProgression.restore(gameState, List.of(0, 1, 2, 3, 2, 0, 0), true);
        S3kSanctuaryRuntimeState state = new S3kSanctuaryRuntimeState(progression, true);
        state.markSpecialStageResult(0, true);
        state.markSpecialStageResult(1, true);
        state.markSpecialStageResult(3, true);
        assertEquals(List.of(0, 1, 2, 3, 2, 0, 0), progression.states());
        progression.awardSuper(4);
        state.markSpecialStageResult(4, true);
        assertEquals(List.of(0, 1, 2, 3, 3, 0, 0), progression.states());
    }

    @Test
    void snapshotRoundTripsTimersSelectionAndOriginIdentity() {
        S3kEmeraldProgression.restore(gameState, List.of(0, 2, 0, 0, 0, 0, 0), true);
        S3kSanctuaryRuntimeState state = new S3kSanctuaryRuntimeState(progression, true);
        state.setOrigin(7, 1);
        state.beginPedestalSelection(1);
        for (int i = 0; i < 7; i++) {
            assertFalse(state.updatePedestalSelection());
        }
        S3kSanctuaryRuntimeState.Snapshot snapshot = state.capture();

        for (int i = 0; i < 9; i++) {
            state.updatePedestalSelection();
        }
        state.restore(snapshot);

        assertEquals(1, state.selectedStage());
        assertEquals(8, state.selectionTimer());
        assertEquals(7, state.originZone());
        assertEquals(1, state.originAct());
        for (int i = 0; i < 8; i++) {
            assertFalse(state.updatePedestalSelection());
        }
        assertTrue(state.updatePedestalSelection());
    }
}
