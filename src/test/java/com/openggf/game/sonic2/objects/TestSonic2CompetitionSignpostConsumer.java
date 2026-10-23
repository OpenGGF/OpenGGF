package com.openggf.game.sonic2.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic2.competition.Sonic2CompetitionResults;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.Sonic2CompetitionObjectFixture;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
class TestSonic2CompetitionSignpostConsumer {
    private Sonic2CompetitionObjectFixture f;
    @BeforeEach void setUp() { GameServices.graphics().initHeadless(); f = new Sonic2CompetitionObjectFixture(); }
    private SignpostObjectInstance signpost(int x) {
        var signpost = new SignpostObjectInstance(new ObjectSpawn(x, 0x100, 0xD, 0, 0, false, 0), "Signpost");
        signpost.setServices(f.services);
        f.objects.addDynamicObject(signpost);
        return signpost;
    }
    private void position(int slot, int x) { NativePositionOps.writeXPosPreserveSubpixel(f.session.player(slot), x); }
    private void update(SignpostObjectInstance signpost, int from, int count) {
        for (int i = from; i < from + count; i++) signpost.update(i, f.p1);
    }

    @Test void nativeActTwoSignpostArmsCountdownAndDoesNotDeleteOrPauseOrdinaryBank() {
        f.act = 1;
        position(0, 0x1000); position(1, 0x2000);
        var signpost = signpost(0x1000);
        signpost.update(0, f.p1);
        assertFalse(signpost.isDestroyed());
        assertEquals(0x3C3C, f.session.loserTimeLeft());
        assertTrue(f.session.levelState(0).isTimerPaused());
        assertFalse(f.session.levelState(1).isTimerPaused());
        assertEquals(f.cameras[0].getMaxX(), f.cameras[0].getMinX());
        assertNotEquals(f.cameras[1].getMaxX(), f.cameras[1].getMinX());
        assertFalse(f.ordinary.isTimerPaused());
        assertFalse(f.ordinaryGame.isActCompletionSignalActive());
    }

    @Test void simultaneousArrivalSamplesP1ThenP2OnNextExecutionAndWaitsFinalHold() {
        position(0, 0x1000); position(1, 0x1000);
        var signpost = signpost(0x1000);
        signpost.update(0, f.p1);
        assertTrue(f.session.levelState(0).isTimerPaused());
        assertFalse(f.session.levelState(1).isTimerPaused());
        signpost.update(1, f.p1);
        assertTrue(f.session.levelState(1).isTimerPaused());
        assertEquals(0, f.session.firstFinisher());
        update(signpost, 2, 182);
        assertEquals(Sonic2CompetitionSession.Phase.ACT, f.session.phase());
        signpost.update(184, f.p1);
        assertEquals(Sonic2CompetitionSession.Phase.ACT_RESULTS, f.session.phase());
        assertEquals(new Sonic2CompetitionResults.Wins(0, 0), f.session.actResult(0, 0));
        assertTrue(f.objects.activeObjectsOfType(ResultsScreenObjectInstance.class).isEmpty());
        assertFalse(f.events.contains("music:ACT_CLEAR"));
    }

    @Test void secondArrivalRearmsAfterCompletedFirstSpinWithoutChangingFirstWinner() {
        for (int first = 0; first < 2; first++) {
            f = new Sonic2CompetitionObjectFixture();
            position(first, 0x1000); position(1 - first, 0x2000);
            var signpost = signpost(0x1000);
            update(signpost, 0, 184);
            assertEquals(Sonic2CompetitionSession.Phase.ACT, f.session.phase());
            assertEquals(first, f.session.firstFinisher());
            position(1 - first, 0x1000);
            update(signpost, 184, 183);
            assertEquals(Sonic2CompetitionSession.Phase.ACT, f.session.phase());
            signpost.update(367, f.p1);
            assertEquals(Sonic2CompetitionSession.Phase.ACT_RESULTS, f.session.phase());
            assertEquals(first + 3, f.session.signpostFace());
        }
    }

    @Test void unsignedNoBorrowIntervalRejectsWrapAndExclusiveRightEdge() {
        var signpost = signpost(0xFFFF);
        position(0, 0); position(1, 0x1E);
        signpost.update(0, f.p1);
        assertEquals(-1, f.session.firstFinisher(), "sub.w carry rejects wrap from FFFF to 0000");
        f.objects.reset(0);
        signpost = signpost(0x8000);
        position(0, 0x7FFF); position(1, 0x8020);
        signpost.update(1, f.p1);
        assertEquals(-1, f.session.firstFinisher());
        position(1, 0x801F);
        signpost.update(2, f.p1);
        assertEquals(1, f.session.firstFinisher());
    }

    @Test void finalHoldCaptureRecreationAndForwardReplayPublishTheSameResultOnce() {
        position(0, 0x1000); position(1, 0x1000);
        var original = signpost(0x1000);
        update(original, 0, 150);
        var state = f.session.capture();
        var objects = f.objects.rewindSnapshottable().capture();
        update(original, 150, 35);
        var expected = f.session.capture();
        f.objects.reset(0); f.replacePlayers(); f.session.restore(state);
        position(0, 0x1000); position(1, 0x1000);
        f.objects.setRewindInPlaceRestoreEnabledForTest(false);
        f.objects.rewindSnapshottable().restore(objects);
        var restored = f.objects.activeObjectsOfType(SignpostObjectInstance.class).getFirst();
        assertNotSame(original, restored);
        update(restored, 150, 35);
        assertEquals(expected.phase(), f.session.phase());
        assertEquals(expected.ledger(), f.session.capture().ledger());
        assertEquals(expected.p1Result(), f.session.resultMetrics(0));
        assertEquals(expected.p2Result(), f.session.resultMetrics(1));
        assertEquals(expected.spinComplete(), f.session.capture().spinComplete());
        update(restored, 185, 5);
        assertEquals(expected.ledger(), f.session.capture().ledger());
        assertEquals(expected.phase(), f.session.phase());
        assertFalse(f.p1.isControlLocked());
        assertFalse(f.p2.isControlLocked());
    }
}
