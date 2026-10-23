package com.openggf.game.sonic2.competition.lifecycle;

import com.openggf.game.sonic2.Sonic2GameOverFlowProvider;
import com.openggf.game.sonic2.competition.Sonic2CompetitionResults;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.game.sonic2.objects.CheckpointObjectInstance;
import com.openggf.level.objects.*;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionDeathCards {
    @BeforeEach void headless() { TestEnvironment.activeGameplayMode(); }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void actualCardClockPlcHoldAndFlickerLeadToOwnerSpecificResultWithoutBothFinished(int slot) throws Exception {
        var f = new LifecycleFixture(); f.lives(slot, 1); f.die(slot);
        assertEquals(0xB000 + slot * 0x40, f.card(2).getNativeParentWord());
        assertEquals(0, f.card(3).getNativeParentWord(), "OVER parent was not written");
        f.plcBusy = true; f.stepCards(4);
        assertEquals(0, f.card(2).getRoutine()); assertFalse(f.card(2).isDisplayedThisFrame());
        f.plcBusy = false; f.stepCards(13);
        assertEquals(0x120, f.card(2).getVdpX()); assertEquals(2, f.card(2).getRoutine());
        assertTrue(f.card(2).isDisplayedThisFrame());
        f.stepCards(1);
        assertEquals(720, f.card(2).getWaitTimer()); assertEquals(4, f.card(2).getRoutine());
        assertFalse(f.card(2).isDisplayedThisFrame(), "Shipped fixBugs=0 conjoining RTS");
        f.stepCards(720);
        assertEquals(0, f.card(2).getWaitTimer()); assertFalse(f.card(2).isDismissed());
        assertEquals(720, f.card(3).getWaitTimer(), "Odd OVER frame never consumes its clock");
        f.stepCards(1);
        assertTrue(f.card(2).isDismissed()); assertFalse(f.card(3).isDismissed());
        assertEquals(Sonic2CompetitionSession.Phase.ZONE_RESULTS, f.session.phase());
        assertEquals(slot == 0 ? new Sonic2CompetitionResults.Wins(254, 255)
                : new Sonic2CompetitionResults.Wins(255, 254), f.session.actResult(0, 0));
        verify(f.actors.level, never()).requestRespawn();
        verify(f.actors.level, never()).requestGameOverExit(any());
    }

    @ParameterizedTest @CsvSource({"0,1", "0,2", "0,4", "1,1", "1,2", "1,4"})
    void eitherPadAbcEdgeDismissesButStartAndOddOverNeverDo(int pad, int action) throws Exception {
        var f = new LifecycleFixture(); f.lives(0, 1); f.die(0); f.stepCards(14);
        f.press(pad, 0, true); f.stepCards(1);
        assertFalse(f.card(2).isDismissed()); assertEquals(719, f.card(2).getWaitTimer());
        f.press(pad, action, false); f.card(3).update(19, f.actor(0));
        assertFalse(f.card(3).isDismissed()); assertEquals(Sonic2CompetitionSession.Phase.ACT, f.session.phase());
        f.card(2).update(19, f.actor(0));
        assertTrue(f.card(2).isDismissed()); assertEquals(Sonic2CompetitionSession.Phase.ZONE_RESULTS, f.session.phase());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void eitherTimeoutSelectsActResultsAndClearsOnlyP1SavedTimer(int timedOut) throws Exception {
        var f = new LifecycleFixture();
        for (int slot = 0; slot < 2; slot++) {
            f.session.levelState(slot).setTimerFrames(1000 + slot * 200);
            f.lifecycle.saveCheckpoint(slot, 2, 0x500, 0x400);
        }
        f.timeout(timedOut); f.die(timedOut);
        assertEquals(1000, f.lifecycle.checkpoint(0).timerFrames());
        f.stepCards(14); f.press(1, 2, false); f.stepCards(1);
        assertEquals(Sonic2CompetitionSession.Phase.ACT_RESULTS, f.session.phase());
        assertEquals(0, f.lifecycle.checkpoint(0).timerFrames());
        assertEquals(1200, f.lifecycle.checkpoint(1).timerFrames());
        assertEquals(-1, f.ordinaryCheckpoint.getLastCheckpointIndex());
        verify(f.actors.level, never()).requestRespawn();
        verify(f.actors.level, never()).requestGameOverExit(any());
    }

    @Test void bothFinalDeathsOverwriteSharedPairAndP2LastWriteWinsWithoutInventingTie() throws Exception {
        var f = new LifecycleFixture(); f.lives(0, 1); f.lives(1, 1);
        f.die(0); var retired = f.card(2); f.die(1);
        assertEquals(List.of("0:false", "1:false"), f.cardRequests);
        assertNotSame(retired, f.card(2)); assertFalse(f.objects.getActiveObjects().contains(retired));
        assertEquals(0xB040, f.card(2).getNativeParentWord());
        f.stepCards(14); f.press(0, 1, false); f.stepCards(1);
        assertEquals(new Sonic2CompetitionResults.Wins(255, 254), f.session.actResult(0, 0));
    }

    @Test void occupiedCardsRetainRoutinePositionAndTimerAndOnlyWrittenFrameAndParentBytesChange() throws Exception {
        var f = new LifecycleFixture();
        var provider = new Sonic2GameOverFlowProvider();
        provider.beginNativeGameOverCard(f.services, 0, true); f.stepCards(14 + 37);
        var previous = f.card(2); int timer = previous.getWaitTimer(); int overTimer = f.card(3).getWaitTimer();
        provider.beginNativeGameOverCard(f.services, 1, false);
        assertNotSame(previous, f.card(2)); assertEquals(4, f.card(2).getRoutine());
        assertEquals(0x120, f.card(2).getVdpX()); assertEquals(timer, f.card(2).getWaitTimer());
        assertEquals(2, f.card(2).getMappingFrame(), "GAME does not write the left mapping-frame byte");
        assertEquals(1, f.card(3).getMappingFrame()); assertEquals(overTimer, f.card(3).getWaitTimer());
        assertEquals(0xB040, f.card(2).getNativeParentWord());
        provider.beginNativeGameOverCard(f.services, 0, true);
        assertEquals(2, f.card(2).getMappingFrame()); assertEquals(3, f.card(3).getMappingFrame());
        assertEquals(timer, f.card(2).getWaitTimer()); assertEquals(0xB000, f.card(2).getNativeParentWord());
    }

    @Test void unsupportedForeignPrefixRamFailsBeforeEitherCardIsChanged() throws Exception {
        var f = new LifecycleFixture();
        var foreign = new CheckpointObjectInstance(new ObjectSpawn(0x500, 0x400, 0x79, 1, 0, false, 0), "foreign");
        f.objects.addDynamicObjectAtSlot(foreign, 3);
        assertThrows(IllegalStateException.class,
                () -> new Sonic2GameOverFlowProvider().beginNativeGameOverCard(f.services, 0, false));
        assertNull(NativeSstSlotAccess.currentOwner(f.objects, 2));
        assertSame(foreign, NativeSstSlotAccess.currentOwner(f.objects, 3));
    }

    @Test void destructiveCardGraphRestoreRecreatesParentAndTimerThenForwardsToSameLedger() throws Exception {
        var f = new LifecycleFixture(); f.lives(0, 1); f.die(0); f.stepCards(34);
        var objects = f.objects.rewindSnapshottable();
        var before = objects.capture(); var session = f.session.capture(); var life = f.lifecycle.capture();
        var retired = f.card(2);
        f.press(1, 4, false); f.stepCards(1);
        var expected = f.session.actResult(0, 0);
        // Retire both captured code owners, forcing graph reconstruction rather than in-place reuse.
        for (int slot : new int[]{2, 3}) f.card(slot).setDestroyed(true);
        f.objects.update(0, f.actor(0), List.of(f.actor(1)), 3, false);
        f.session.restore(session); f.lifecycle.restore(life); objects.restore(before);
        assertNotSame(retired, f.card(2)); assertEquals(0xB000, f.card(2).getNativeParentWord());
        assertEquals(700, f.card(2).getWaitTimer());
        assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey("objects", before, objects.capture()));
        f.stepCards(1); assertEquals(expected, f.session.actResult(0, 0));
    }

    @Test void eachHumanActivatesSameNativePoleWithoutOrdinaryCheckpointOrSpecialStars() throws Exception {
        var f = new LifecycleFixture();
        var post = ObjectConstructionContext.construct(f.services, () -> new CheckpointObjectInstance(
                new ObjectSpawn(0x500, 0x400, 0x79, 0x83, 0, false, 0), "native checkpoint"));
        f.objects.addDynamicObject(post);
        f.actor(0).setCentreX((short) 0x500); f.actor(0).setCentreY((short) 0x400);
        f.actor(1).setCentreX((short) 0x900); f.actor(1).setCentreY((short) 0x400);
        f.session.levelState(0).setRings(60); f.session.levelState(1).setRings(70);
        post.update(1, f.actor(0));
        assertEquals(0x83, f.lifecycle.checkpoint(0).lastStarPost()); assertEquals(0, f.lifecycle.checkpoint(1).lastStarPost());
        f.actor(1).setCentreX((short) 0x500); post.update(2, f.actor(0));
        assertEquals(0x83, f.lifecycle.checkpoint(1).lastStarPost());
        assertEquals(2, f.audio.size());
        assertEquals(2, f.objects.getActiveObjects().stream()
                .filter(o -> o instanceof com.openggf.game.sonic2.objects.CheckpointDongleInstance).count());
        assertTrue(f.objects.getActiveObjects().stream()
                .noneMatch(o -> o instanceof com.openggf.game.sonic2.objects.CheckpointStarInstance));
        assertFalse(f.ordinaryCheckpoint.isActive());
        assertEquals(f.environment, f.lifecycle.checkpoint(0).primaryEnvironment());
        assertNull(f.lifecycle.checkpoint(1).primaryEnvironment());
    }
}
