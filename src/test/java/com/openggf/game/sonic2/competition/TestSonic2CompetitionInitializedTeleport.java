package com.openggf.game.sonic2.competition;

import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.data.RomByteReader;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class TestSonic2CompetitionInitializedTeleport {
    @Test @RequiresRom(SonicGame.SONIC_2)
    void retailInitializedIconChecksBothActorRoutinesAtAwardRatherThanTimerOrMenuPhase() throws Exception {
        var rom = RomByteReader.fromRom(TestEnvironment.currentRom());
        assertEquals("52520c380006b02464080c380006b06465024e75",
                HexFormat.of().formatHex(rom.slice(0x12AA6, 20)),
                "teleport_monitor checks MainCharacter/Sidekick routine <6 before its native swap");
    }

    @Test void initializedTeleportSurvivesLaterFinishButNewSelectionAndIllegalRoutineDoNot() {
        var session = new Sonic2CompetitionSession();
        session.updateMenu(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()), true);
        var host = new Host();
        assertEquals(8, session.monitorContents(7, false));
        assertTrue(session.observeGoal(0, 100, 100));
        assertEquals(7, session.monitorContents(7, false));
        assertFalse(session.teleport(host));
        assertEquals(0, host.swaps);
        assertTrue(session.teleportInitializedMonitor(host));
        assertEquals(1, host.swaps); assertEquals(0x40, host.frozen);
        assertEquals(0x40, session.teleportTimer());
        host.legalRoutines = false;
        assertFalse(session.teleportInitializedMonitor(host));
        assertEquals(1, host.swaps);
    }

    @Test void selectedIconCanAwardAfterFinalSpinPublishesResultsInTheSameObjectPassAndRestoreForward() throws Exception {
        var session = new Sonic2CompetitionSession();
        session.updateMenu(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()), true);
        assertEquals(8, session.monitorContents(7, false), "the rising icon already selected teleport");
        session.levelState(0).setRings(5); session.levelState(1).setRings(7);
        assertTrue(session.observeGoal(0, 100, 100));
        assertTrue(session.observeGoal(1, 100, 100));
        var beforeSpin = session.capture();
        Sonic2CompetitionSession.Snapshot expected = null;
        for (int replay = 0; replay < 2; replay++) {
            var host = new Host();
            session.completeSignpostSpin();
            assertEquals(Sonic2CompetitionSession.Phase.ACT_RESULTS, session.phase());
            var result = session.actResult(0, 0);
            assertEquals(7, session.monitorContents(7, false));
            assertFalse(session.teleport(host), "new selection still requires the active running act");
            host.liveWorld = false;
            assertFalse(session.teleportInitializedMonitor(host));
            assertEquals(0, host.swaps); assertEquals(0, host.frozen);
            assertEquals(0, session.teleportTimer());
            host.liveWorld = true; host.legalRoutines = false;
            assertFalse(session.teleportInitializedMonitor(host));
            assertEquals(0, host.swaps); assertEquals(0, host.frozen);
            host.legalRoutines = true;
            assertTrue(session.teleportInitializedMonitor(host), "the queued initialized icon uses the live-world transaction");
            assertEquals(1, host.swaps); assertEquals(0x40, host.frozen);
            assertEquals(0x40, session.teleportTimer());
            assertEquals(Sonic2CompetitionSession.Phase.ACT_RESULTS, session.phase());
            assertEquals(result, session.actResult(0, 0));
            assertEquals(5, session.levelState(0).getRings());
            assertEquals(7, session.levelState(1).getRings());
            if (replay == 0) { expected = session.capture(); session.restore(beforeSpin); }
            else assertSnapshotValue(expected, session.capture());
        }
    }

    /** Snapshot records own cloned emerald arrays; compare values through each record boundary. */
    private static void assertSnapshotValue(Object expected, Object actual) throws Exception {
        if (expected == null || actual == null) { assertEquals(expected, actual); return; }
        assertEquals(expected.getClass(), actual.getClass());
        if (expected.getClass().isArray()) {
            assertTrue(java.util.Objects.deepEquals(expected, actual), "snapshot array contents");
        } else if (expected.getClass().isRecord()) {
            for (var component : expected.getClass().getRecordComponents()) {
                var accessor = component.getAccessor();
                assertTrue(accessor.trySetAccessible());
                assertSnapshotValue(accessor.invoke(expected), accessor.invoke(actual));
            }
        } else assertEquals(expected, actual);
    }

    private static final class Host implements Sonic2CompetitionHost {
        int swaps, frozen;
        boolean liveWorld = true, legalRoutines = true;
        @Override public boolean supportsZone(int zone) { return false; }
        @Override public void loadAct(Sonic2CompetitionSession s, int z, int a) { throw new AssertionError(); }
        @Override public void step(Sonic2CompetitionSession s, InputHandler input) { throw new AssertionError(); }
        @Override public void draw(Sonic2CompetitionSession s) { throw new AssertionError(); }
        @Override public void freezeObjects(int ticks) { frozen = ticks; }
        @Override public boolean swapNativeActorWorldState() { if (!liveWorld || !legalRoutines) return false; swaps++; return true; }
    }
}
