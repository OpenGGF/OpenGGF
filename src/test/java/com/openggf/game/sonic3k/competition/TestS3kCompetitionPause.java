package com.openggf.game.sonic3k.competition;

import com.openggf.game.rewind.RewindRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.sonic3k.competition.S3kCompetitionPause.Continuation;
import static com.openggf.game.sonic3k.competition.S3kCompetitionPause.Exit;
import static com.openggf.game.sonic3k.competition.S3kCompetitionPause.Wait;
import static org.junit.jupiter.api.Assertions.*;

/** Literal controller-byte and wait-boundary oracles from Pause_Game/loc_6678. */
class TestS3kCompetitionPause {
    @Test void getDemoPtrSeedsStartHeldWithoutAnEntryPress() {
        var pause=new S3kCompetitionPause();
        assertEquals(0x8000,pause.controllerWord()); assertEquals(0x80,pause.held()); assertEquals(0,pause.pressed());
        assertEquals(Wait.ORDINARY_WAIT,pause.beginWait(3)); assertFalse(pause.gamePaused());
        pause.acceptSample(0x8000,false,false); assertTrue(pause.bodyReady());
        assertEquals(Exit.NONE,pause.completeBody());
        assertEquals(Wait.ORDINARY_WAIT,pause.beginWait(3));
    }

    @Test void entryUsesPreviousStartWhileTheCurrentPressStillRunsItsOrdinaryBody() {
        var pause=new S3kCompetitionPause();
        assertEquals(Wait.ORDINARY_WAIT,pause.beginWait(3));
        pause.acceptSample(0x8080,false,false);
        assertFalse(pause.gamePaused()); assertEquals(Exit.NONE,pause.completeBody());
        assertEquals(Wait.PAUSE_WAIT,pause.beginWait(3)); assertTrue(pause.gamePaused());
    }

    @Test void heldStartWithoutPressedDoesNotEnterPause() {
        var pause=new S3kCompetitionPause();
        for(int i=0;i<17;i++) {
            assertEquals(Wait.ORDINARY_WAIT,pause.beginWait(3));
            pause.acceptSample(0x8000,false,false); pause.completeBody();
        }
        assertFalse(pause.gamePaused());
    }

    @Test void zeroP1LifeByteBypassesPreviousStartAndClearsPause() {
        var pause=new S3kCompetitionPause();
        pause.restore(new S3kCompetitionPause.State(0x8080,true,Continuation.ENTRY,Exit.NONE));
        assertEquals(Wait.ORDINARY_WAIT,pause.beginWait(0)); assertFalse(pause.gamePaused());
        pause.acceptSample(0,false,false); assertEquals(Exit.NONE,pause.completeBody());
        pause.restore(new S3kCompetitionPause.State(0x8080,true,Continuation.ENTRY,Exit.NONE));
        assertEquals(Wait.ORDINARY_WAIT,pause.beginWait(0x100),"native TST.B ignores the high byte");
        assertFalse(pause.gamePaused());
    }

    @Test void nonzeroUnsignedLifeByteAllowsPause() {
        var pause=new S3kCompetitionPause();
        pause.restore(new S3kCompetitionPause.State(0x80,false,Continuation.ENTRY,Exit.NONE));
        assertEquals(Wait.PAUSE_WAIT,pause.beginWait(0xFF)); assertTrue(pause.gamePaused());
    }

    @Test void pauseLoopHasNoDebounceAndNeverReadmitsABodyWhileNeutral() {
        var pause=enteredPause();
        for(int i=0;i<120;i++) {
            pause.acceptSample(0,false,false);
            assertFalse(pause.bodyReady()); assertTrue(pause.gamePaused());
            assertEquals(Wait.PAUSE_WAIT,pause.beginWait(3));
        }
        pause.acceptSample(0x8080,false,false);
        assertFalse(pause.gamePaused()); assertFalse(pause.bodyReady());
        assertEquals(Wait.ORDINARY_WAIT,pause.beginWait(3));
    }

    @Test void heldStartDoesNotResumeWithoutItsPressedEdge() {
        var pause=enteredPause(); pause.acceptSample(0x8000,false,false);
        assertTrue(pause.gamePaused()); assertEquals(Wait.PAUSE_WAIT,pause.beginWait(3));
    }

    @Test void resumeConsumesAnotherOrdinaryWaitBeforeItCanCompleteABody() {
        var pause=enteredPause(); pause.acceptSample(0x8080,false,false);
        assertFalse(pause.gamePaused()); assertEquals(Continuation.ORDINARY_WAIT,pause.continuation());
        assertThrows(IllegalStateException.class,pause::completeBody);
        assertEquals(Wait.ORDINARY_WAIT,pause.beginWait(3));
        pause.acceptSample(0x8000,false,false); assertTrue(pause.bodyReady());
        assertEquals(Exit.NONE,pause.completeBody());
        assertEquals(Wait.ORDINARY_WAIT,pause.beginWait(3),"the consumed resume edge cannot repause");
    }

    @Test void timeAttackBQueuesSelectionOnlyAfterTheFollowingOrdinaryBody() {
        var pause=enteredPause(); pause.acceptSample(0x1010,false,true);
        assertEquals(Exit.TIME_ATTACK_SELECTION,pause.pendingExit()); assertFalse(pause.gamePaused());
        assertFalse(pause.bodyReady()); assertThrows(IllegalStateException.class,pause::completeBody);
        assertEquals(Wait.ORDINARY_WAIT,pause.beginWait(3)); pause.acceptSample(0,false,true);
        assertEquals(Exit.TIME_ATTACK_SELECTION,pause.completeBody()); assertEquals(Continuation.EXITED,pause.continuation());
        assertThrows(IllegalStateException.class,() -> pause.beginWait(3));
    }

    @Test void ordinaryModeBAndTimeAttackHeldBRemainPaused() {
        var pause=enteredPause(); pause.acceptSample(0x1010,false,false);
        assertEquals(Exit.NONE,pause.pendingExit()); assertTrue(pause.gamePaused());
        assertEquals(Wait.PAUSE_WAIT,pause.beginWait(3)); pause.acceptSample(0x1000,false,true);
        assertEquals(Exit.NONE,pause.pendingExit()); assertTrue(pause.gamePaused());
        assertEquals(Continuation.PAUSE_WAIT,pause.continuation());
    }

    @Test void slowMotionHeldBAdvancesOneBodyAndRetainsPauseForTheNextEntry() {
        var pause=enteredPause(); pause.acceptSample(0x1000,true,false);
        assertTrue(pause.gamePaused()); assertEquals(Exit.NONE,pause.pendingExit());
        assertEquals(Wait.ORDINARY_WAIT,pause.beginWait(3)); pause.acceptSample(0x1000,true,false);
        assertEquals(Exit.NONE,pause.completeBody()); assertTrue(pause.gamePaused());
        assertEquals(Wait.PAUSE_WAIT,pause.beginWait(3));
    }

    @Test void slowMotionCNeedsPressedWhileAHeldAloneDoesNotExit() {
        var pause=enteredPause(); pause.acceptSample(0x6000,true,false);
        assertEquals(Continuation.PAUSE_WAIT,pause.continuation()); assertEquals(Exit.NONE,pause.pendingExit());
        assertEquals(Wait.PAUSE_WAIT,pause.beginWait(3)); pause.acceptSample(0x2020,true,false);
        assertEquals(Continuation.ORDINARY_WAIT,pause.continuation()); assertTrue(pause.gamePaused());
    }

    @Test void slowMotionAPressedPrecedesFrameAdvanceAndTimeAttackB() {
        var pause=enteredPause(); pause.acceptSample(0xF0F0,true,true);
        assertEquals(Exit.TITLE_SCREEN,pause.pendingExit()); assertFalse(pause.gamePaused());
        assertEquals(Wait.ORDINARY_WAIT,pause.beginWait(3)); pause.acceptSample(0,true,true);
        assertEquals(Exit.TITLE_SCREEN,pause.completeBody());
    }

    @Test void slowMotionBHeldPrecedesTimeAttackBPressedAndStartResume() {
        var pause=enteredPause(); pause.acceptSample(0x9090,true,true);
        assertEquals(Exit.NONE,pause.pendingExit()); assertTrue(pause.gamePaused());
        assertEquals(Wait.ORDINARY_WAIT,pause.beginWait(3)); pause.acceptSample(0,true,true); pause.completeBody();
        assertEquals(Wait.PAUSE_WAIT,pause.beginWait(3));
    }

    @Test void alreadyRunningPauseLoopDoesNotRepeatTheEntryLifeCheck() {
        var pause=enteredPause(); pause.acceptSample(0,false,false);
        assertEquals(Wait.PAUSE_WAIT,pause.beginWait(0)); assertTrue(pause.gamePaused());
        pause.acceptSample(0x8080,false,false);
        assertEquals(Wait.ORDINARY_WAIT,pause.beginWait(0)); assertFalse(pause.gamePaused());
    }

    @Test void capturedPauseAndPendingExitReplaySeventeenBoundariesAfterRestoration() {
        var pause=enteredPause(); pause.acceptSample(0,false,true);
        var registry=new RewindRegistry(); registry.register(pause); var snapshot=registry.capture();
        var before=pause.capture(); var expected=seventeenBoundaries(pause);
        assertEquals(Continuation.EXITED,pause.continuation());
        registry.restore(snapshot); assertEquals(before,pause.capture()); assertTrue(pause.gamePaused());
        assertEquals(expected,seventeenBoundaries(pause));
        // Also restore after B's pause wait but before its forced ordinary wait.
        pause.restore(new S3kCompetitionPause.State(0x1010,false,Continuation.ORDINARY_WAIT,Exit.TIME_ATTACK_SELECTION));
        var pending=pause.capture();
        assertEquals(Wait.ORDINARY_WAIT,pause.beginWait(3)); pause.acceptSample(0,false,true);
        var ready=pause.capture(); assertEquals(Exit.TIME_ATTACK_SELECTION,pause.completeBody());
        pause.restore(pending); assertEquals(Wait.ORDINARY_WAIT,pause.beginWait(3)); pause.acceptSample(0,false,true);
        assertEquals(ready,pause.capture()); assertEquals(Exit.TIME_ATTACK_SELECTION,pause.completeBody());
    }

    @Test void resetAndBoundaryValidationPreventReusingAConsumedWaitOrBody() {
        var pause=new S3kCompetitionPause();
        assertThrows(IllegalStateException.class,() -> pause.acceptSample(0,false,false));
        assertThrows(IllegalStateException.class,pause::completeBody);
        pause.beginWait(3); assertThrows(IllegalStateException.class,() -> pause.beginWait(3));
        assertThrows(IllegalArgumentException.class,() -> pause.acceptSample(256,0,false,false));
        assertThrows(IllegalArgumentException.class,() -> pause.acceptSample(-1,false,false));
        pause.acceptSample(0x8080,false,false);
        assertThrows(IllegalStateException.class,() -> pause.acceptSample(0,false,false));
        pause.completeBody(); assertThrows(IllegalStateException.class,pause::completeBody);
        pause.resetForMissingSnapshot();
        assertEquals(new S3kCompetitionPause().capture(),pause.capture());
        assertEquals("sonic3k.competition.pause",pause.key());
        assertThrows(IllegalArgumentException.class,() -> new S3kCompetitionPause.State(0x10000,false,Continuation.ENTRY,Exit.NONE));
        assertThrows(NullPointerException.class,() -> pause.restore(null));
    }

    private static S3kCompetitionPause enteredPause() {
        var pause=new S3kCompetitionPause(); pause.beginWait(3);
        pause.acceptSample(0x8080,false,false); pause.completeBody();
        assertEquals(Wait.PAUSE_WAIT,pause.beginWait(3)); return pause;
    }
    private static List<S3kCompetitionPause.State> seventeenBoundaries(S3kCompetitionPause pause) {
        var states=new ArrayList<S3kCompetitionPause.State>();
        for(int i=0;i<15;i++) {
            assertEquals(Wait.PAUSE_WAIT,pause.beginWait(3));
            pause.acceptSample((i&1)==0?0x0400:0x0800,false,true); states.add(pause.capture());
        }
        assertEquals(Wait.PAUSE_WAIT,pause.beginWait(3)); pause.acceptSample(0x1010,false,true); states.add(pause.capture());
        assertEquals(Wait.ORDINARY_WAIT,pause.beginWait(3)); pause.acceptSample(0,false,true);
        assertEquals(Exit.TIME_ATTACK_SELECTION,pause.completeBody()); states.add(pause.capture());
        assertEquals(17,states.size()); return List.copyOf(states);
    }
}
