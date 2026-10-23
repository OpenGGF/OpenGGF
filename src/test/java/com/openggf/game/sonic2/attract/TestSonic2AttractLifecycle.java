package com.openggf.game.sonic2.attract;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.sonic2.attract.Sonic2AttractService.*;
import static com.openggf.game.sonic2.attract.TestSonic2AttractRomData.startNext;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2AttractLifecycle {
    private static final LogicalInputSnapshot NEUTRAL = LogicalInputSnapshot.neutral();

    @Test
    void titleClockExpiresOnThe640thTitleVIntAndUnavailableCompetitionDoesNotSkipOrRotate() throws Exception {
        var service = service();
        assertEquals(LaunchStatus.NOT_DUE, service.requestNextLaunch(true).status());
        for (int tick = 1; tick < 640; tick++) {
            assertFalse(service.onTitleVInt(), "Title V-int " + tick);
            assertEquals(640 - tick, service.timeLeft());
        }
        assertTrue(service.onTitleVInt());
        assertTrue(service.onTitleVInt(), "Native TST.W prevents underflow from zero");
        Snapshot expired = service.capture();
        for (int call = 0; call < 3; call++) {
            LaunchRequest request = service.requestNextLaunch(false);
            assertEquals(LaunchStatus.COMPETITION_UNAVAILABLE, request.status());
            assertEquals(SessionKind.COMPETITION, request.plan().sessionKind());
            assertEquals(expired, service.capture(), "A missing real competition host cannot advance rotation");
        }
        LaunchRequest ready = service.requestNextLaunch(true);
        assertEquals(LaunchStatus.READY, ready.status());
        assertEquals(0, ready.plan().demoNumber());
        assertEquals(1, service.demoNumber());
        assertEquals(Phase.AWAITING_LEVEL_SETUP, service.phase());
        assertThrows(IllegalStateException.class, service::onTitleVInt);
        assertThrows(IllegalStateException.class, service::onLevelUpdates,
                "Palette-fade/title-card waits must not be substituted for Do_Updates");
    }

    @Test
    void fourPlanRotationPersistsAcrossTitleReturnsAndWrapsIndependentlyOfMenuChoices() throws Exception {
        var service = service();
        int[] nativeZones = {0, 0xD, 0xF, 0xC};
        for (int launch = 0; launch < 8; launch++) {
            LaunchPlan plan = startNext(service);
            assertEquals(launch % 4, plan.demoNumber());
            assertEquals(nativeZones[launch % 4], plan.nativeZone());
            service.stepControllers(startEdge(1));
            assertEquals(ReturnTarget.TITLE_SCREEN, service.finishIteration(false).target());
            service.enterTitle();
            assertEquals(640, service.timeLeft());
            assertEquals((launch + 1) % 4, service.demoNumber());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void eitherPhysicalStartEdgeAbortsAfterThisControllerAndWorldIterationWithoutAFade(int pad) throws Exception {
        var service = service();
        startNext(service);
        service.onLevelUpdates();
        ControllerFrame frame = service.stepControllers(startEdge(pad));
        assertEquals(Phase.PLAYBACK, frame.phase());
        assertTrue(frame.physicalStartPressed());
        assertEquals(0, frame.player1HeldByte(), "Physical Start is replaced by the actual first ROM packet");
        assertEquals(new InputCursor(0, 74), service.player1Cursor(), "Abort still executes this MoveDemo_On dispatch");
        assertEquals(1639, service.timeLeft());
        Snapshot pendingAbort = service.capture();
        var restored = service();
        restored.restore(pendingAbort);
        Transition expected = new Transition(Event.COMPLETE, CompletionReason.PHYSICAL_START, ReturnTarget.TITLE_SCREEN);
        assertEquals(expected, service.finishIteration(false));
        assertEquals(expected, restored.finishIteration(false), "Pending abort survives capture/restore");
        assertEquals(Phase.COMPLETE, service.phase());
        assertEquals(1639, service.timeLeft(), "Physical abort does not start the 60-tick expiry fade");
    }

    @Test
    void heldStartDoesNotAbortAndScriptAndTimerClocksCannotDoubleAdvanceEachOther() throws Exception {
        var service = service();
        startNext(service);
        InputCursor first = service.player1Cursor();
        service.onLevelUpdates();
        service.onLevelUpdates();
        assertEquals(first, service.player1Cursor(), "Eligible interrupts do not dispatch MoveSonicInDemo");
        assertEquals(1638, service.timeLeft());
        var heldStart = PlayerInputState.of(0xF, 0, 7, 0, true, false);
        for (int step = 0; step < 20; step++) {
            ControllerFrame frame = service.stepControllers(LogicalInputSnapshot.ofPlayers(heldStart, heldStart));
            assertFalse(frame.physicalStartPressed());
            assertThrows(IllegalStateException.class, () -> service.stepControllers(NEUTRAL));
            assertEquals(Transition.NONE, service.finishIteration(false));
        }
        assertEquals(1638, service.timeLeft(), "Controller dispatch is independent of the timer owner");
        assertEquals(new InputCursor(0, 55), service.player1Cursor());
        assertThrows(IllegalStateException.class, () -> service.finishIteration(false));
    }

    @Test
    void expiryWaitsForTheLastWorldIterationAndPhysicalStartWinsOnThatSameIteration() throws Exception {
        var service = service();
        startNext(service);
        for (int tick = 0; tick < 1640; tick++) service.onLevelUpdates();
        assertEquals(0, service.timeLeft());
        service.onLevelUpdates();
        assertEquals(0, service.timeLeft());
        assertEquals(Phase.PLAYBACK, service.phase(), "Timer service alone cannot bypass the final world step");
        assertThrows(IllegalStateException.class, () -> service.finishIteration(false));
        Snapshot expired = service.capture();
        service.stepControllers(NEUTRAL);
        assertEquals(new Transition(Event.BEGIN_FADE, CompletionReason.TIME_EXPIRED, ReturnTarget.SEGA_SCREEN),
                service.finishIteration(false));
        assertEquals(60, service.timeLeft());
        service.restore(expired);
        service.stepControllers(startEdge(2));
        assertEquals(new Transition(Event.COMPLETE, CompletionReason.PHYSICAL_START, ReturnTarget.TITLE_SCREEN),
                service.finishIteration(false));
    }

    @Test
    void deferredHIntMayServiceTheLastTimerTickDuringThePendingWorldIteration() throws Exception {
        var service = service();
        startNext(service);
        for (int tick = 0; tick < 1639; tick++) service.onLevelUpdates();
        assertEquals(1, service.timeLeft());
        ControllerFrame output = service.stepControllers(NEUTRAL);
        InputCursor afterControllers = service.player1Cursor();
        assertEquals(Phase.PLAYBACK, output.phase());
        service.onLevelUpdates();
        assertEquals(0, service.timeLeft());
        assertEquals(afterControllers, service.player1Cursor(), "H-int does not dispatch controller packets again");
        assertEquals(new Transition(Event.BEGIN_FADE, CompletionReason.TIME_EXPIRED, ReturnTarget.SEGA_SCREEN),
                service.finishIteration(false));
    }

    @Test
    void nativeInactiveExitFadesAndStartDuringFadeChangesDestinationWithoutShorteningIt() throws Exception {
        var service = service();
        startNext(service);
        service.stepControllers(NEUTRAL);
        assertEquals(new Transition(Event.BEGIN_FADE, CompletionReason.LEVEL_INACTIVE, ReturnTarget.SEGA_SCREEN),
                service.finishIteration(true));
        for (int tick = 1; tick <= 60; tick++) {
            service.onLevelUpdates();
            ControllerFrame frame = service.stepControllers(tick == 2 ? startEdge(2) : NEUTRAL);
            assertEquals(Phase.FADING, frame.phase());
            Transition transition = service.finishIteration(false);
            if (tick < 60) {
                assertEquals(Transition.NONE, transition);
                assertEquals(Phase.FADING, service.phase());
            } else {
                assertEquals(new Transition(Event.COMPLETE, CompletionReason.PHYSICAL_START, ReturnTarget.TITLE_SCREEN), transition);
            }
        }
    }

    @Test
    void earlyNativeLevelReloadRestartsTheSameScriptsWithoutRotatingOrLosingPendingStartMode() throws Exception {
        var service = service();
        LaunchPlan plan = startNext(service);
        run(service, 140);
        service.onLevelUpdates();
        service.stepControllers(NEUTRAL);
        // RunObjects requests Level before the normal finishIteration boundary.
        service.restartPlaybackForLevelReload();
        assertEquals(plan, service.activePlan());
        assertEquals(1, service.demoNumber());
        assertEquals(1640, service.timeLeft());
        assertEquals(new InputCursor(0, 75), service.player1Cursor());
        assertEquals(new InputCursor(0, 59), service.player2Cursor());
        assertThrows(IllegalStateException.class, () -> service.finishIteration(false),
                "The abandoned old world's iteration cannot also finish after Level setup");
        Snapshot reloaded = service.capture();
        List<Object> forward = run(service, 90);
        service.restore(reloaded);
        assertEquals(forward, run(service, 90));
        service.stepControllers(startEdge(1));
        service.restartPlaybackForLevelReload();
        service.onLevelUpdates();
        service.stepControllers(NEUTRAL);
        assertEquals(new Transition(Event.COMPLETE, CompletionReason.PHYSICAL_START, ReturnTarget.TITLE_SCREEN),
                service.finishIteration(false), "Level preserves the already-selected title Game_Mode");
        assertThrows(IllegalStateException.class, service::restartPlaybackForLevelReload);
    }

    @Test
    void captureRestoreReplaysTitlePacketsAndFadeForwardWithTheSameOutputsAndTerminalState() throws Exception {
        var service = service();
        for (int tick = 0; tick < 37; tick++) service.onTitleVInt();
        Snapshot title = service.capture();
        for (int tick = 0; tick < 71; tick++) service.onTitleVInt();
        Snapshot advancedTitle = service.capture();
        service.restore(title);
        for (int tick = 0; tick < 71; tick++) service.onTitleVInt();
        assertEquals(advancedTitle, service.capture());
        startNext(service);
        run(service, 75); // Last neutral counter reaches zero: next call must advance on borrow.
        Snapshot packets = service.capture();
        List<Object> firstRun = run(service, 350);
        Snapshot advancedPackets = service.capture();
        var replacement = service();
        replacement.restore(packets);
        assertEquals(firstRun, run(replacement, 350));
        assertEquals(advancedPackets, replacement.capture());
        while (service.phase() == Phase.PLAYBACK) run(service, 1);
        run(service, 17);
        Snapshot fade = service.capture();
        List<Object> fadeRun = run(service, 43);
        Snapshot completed = service.capture();
        assertEquals(Phase.COMPLETE, completed.phase());
        replacement.restore(fade);
        assertEquals(fadeRun, run(replacement, 43));
        assertEquals(completed, replacement.capture());
    }

    @Test
    void snapshotsRejectAnotherRomOrInvalidPlanAtomicallyAndMissingSnapshotResetsFreshRotation() throws Exception {
        var source = service();
        startNext(source);
        run(source, 90);
        Snapshot snapshot = source.capture();
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        byte[] variant = reader.slice(0, reader.size());
        variant[0x100] ^= 1;
        try (Rom other = Rom.fromReader(RomByteReader.fromBytes(variant), "test-owned other ROM generation")) {
            var destination = new Sonic2AttractService(other);
            Snapshot before = destination.capture();
            assertThrows(IllegalArgumentException.class, () -> destination.restore(snapshot));
            assertEquals(before, destination.capture());
        }
        Snapshot beforeInvalid = source.capture();
        var invalid = new Snapshot(snapshot.romFingerprint(), snapshot.phase(), 3, snapshot.activeDemoNumber(),
                snapshot.timeLeft(), snapshot.player1(), snapshot.player2(), snapshot.reason(), false, false);
        assertThrows(IllegalArgumentException.class, () -> source.restore(invalid));
        assertEquals(beforeInvalid, source.capture());
        assertThrows(IllegalArgumentException.class, () -> new InputCursor(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new InputCursor(0, 256));
        RewindSnapshottable<Snapshot> adapter = source;
        assertEquals("sonic2-attract", adapter.key());
        adapter.resetForMissingSnapshot();
        assertEquals(Phase.TITLE, source.phase());
        assertEquals(0, source.demoNumber());
        assertEquals(640, source.timeLeft());
        assertNull(source.activePlan());
    }

    private static Sonic2AttractService service() throws Exception {
        return new Sonic2AttractService(TestEnvironment.currentRom());
    }

    private static LogicalInputSnapshot startEdge(int pad) {
        PlayerInputState start = PlayerInputState.of(0, 0, 0, 0, true, true);
        return LogicalInputSnapshot.ofPlayers(pad == 1 ? start : PlayerInputState.neutral(),
                pad == 2 ? start : PlayerInputState.neutral());
    }

    private static List<Object> run(Sonic2AttractService service, int iterations) {
        var observations = new ArrayList<Object>();
        for (int iteration = 0; iteration < iterations; iteration++) {
            service.onLevelUpdates();
            observations.add(service.stepControllers(NEUTRAL));
            observations.add(service.finishIteration(false));
        }
        return observations;
    }
}
