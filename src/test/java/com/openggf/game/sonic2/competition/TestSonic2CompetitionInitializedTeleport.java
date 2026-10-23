package com.openggf.game.sonic2.competition;

import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestSonic2CompetitionInitializedTeleport {
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
    private static final class Host implements Sonic2CompetitionHost {
        int swaps, frozen;
        boolean legalRoutines = true;
        @Override public boolean supportsZone(int zone) { return false; }
        @Override public void loadAct(Sonic2CompetitionSession s, int z, int a) { throw new AssertionError(); }
        @Override public void step(Sonic2CompetitionSession s, InputHandler input) { throw new AssertionError(); }
        @Override public void draw(Sonic2CompetitionSession s) { throw new AssertionError(); }
        @Override public void freezeObjects(int ticks) { frozen = ticks; }
        @Override public boolean swapNativeActorWorldState() { if (!legalRoutines) return false; swaps++; return true; }
    }
}
