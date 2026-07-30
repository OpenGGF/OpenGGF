package com.openggf.level;

import com.openggf.game.BonusStageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestLevelTransitionCoordinatorPeeks {
    @Test
    void bonusPeekDoesNotConsume() {
        LevelTransitionCoordinator c = new LevelTransitionCoordinator();
        assertNull(c.peekBonusStageRequest());
        c.requestBonusStageEntry(BonusStageType.GUMBALL);
        assertEquals(BonusStageType.GUMBALL, c.peekBonusStageRequest());
        assertEquals(BonusStageType.GUMBALL, c.peekBonusStageRequest()); // still pending
        assertEquals(BonusStageType.GUMBALL, c.consumeBonusStageRequest()); // consumer still works
        assertNull(c.peekBonusStageRequest()); // cleared by consume, not by peek
    }

    @Test
    void specialStagePeekDoesNotConsume() {
        LevelTransitionCoordinator c = new LevelTransitionCoordinator();
        assertFalse(c.isSpecialStageRequested());
        c.requestSpecialStageFromCheckpoint();
        assertTrue(c.isSpecialStageRequested());
        assertTrue(c.isSpecialStageRequested()); // still pending
        assertTrue(c.consumeSpecialStageRequest());
        assertFalse(c.isSpecialStageRequested());
    }

    @Test
    void sanctuaryReentryAndExitRetainSavedReturnUntilOriginRestoreCompletes() {
        LevelTransitionCoordinator c = new LevelTransitionCoordinator();
        BigRingReturnState saved = new BigRingReturnState(
                0x1234, 0x456, 0x1100, 0x300, 42,
                (byte) 0x0C, (byte) 0x0D, 0x700, 6, 0x380,
                7, 1, 3, 4, 0x220, 0x330, 0x100, 0x200);
        c.saveBigRingReturn(saved);

        c.markSanctuaryReentry(5);
        assertEquals(5, c.sanctuaryReentryStage().orElseThrow());
        assertTrue(c.sanctuaryReturnContext().isEmpty(),
                "legacy overload retains progression-derived success semantics");
        assertSame(saved, c.getBigRingReturn());

        assertTrue(c.requestSanctuaryExit());
        assertFalse(c.requestSanctuaryExit(), "exit request is idempotent");
        assertEquals(7, c.getRequestedZone());
        assertEquals(1, c.getRequestedAct());
        assertTrue(c.isSanctuaryOriginRestorePending(7, 1));
        assertSame(saved, c.getBigRingReturn());

        c.completeSanctuaryOriginRestore();
        assertTrue(c.sanctuaryReentryStage().isEmpty());
        assertFalse(c.hasBigRingReturn());
        assertFalse(c.isSanctuaryOriginRestorePending(7, 1));
    }

    @Test
    void sanctuaryTransitionStateRoundTripsForRewind() {
        LevelTransitionCoordinator c = new LevelTransitionCoordinator();
        BigRingReturnState saved = new BigRingReturnState(
                1, 2, 3, 4, 5, (byte) 6, (byte) 7, 8, 9, 10);
        c.saveBigRingReturn(saved);
        c.markSanctuaryReentry(2, true);
        LevelTransitionRewindAdapter adapter = new LevelTransitionRewindAdapter(c);
        LevelTransitionCoordinator.SanctuaryRewindState snapshot = adapter.capture();

        c.requestSanctuaryExit();
        c.completeSanctuaryOriginRestore();
        adapter.restore(snapshot);

        assertEquals(2, c.sanctuaryReentryStage().orElseThrow());
        assertEquals(new SanctuaryReturnContext(2, true),
                c.sanctuaryReturnContext().orElseThrow());
        assertSame(saved, c.getBigRingReturn());
        assertFalse(c.isSanctuaryOriginRestorePending(-1, -1));
        assertFalse(c.consumeZoneActRequest(),
                "rewind before exit request must not retain a generic zone request");
    }

    @Test
    void sanctuaryExitRequestAndPendingRestoreRoundTripAtomically() {
        LevelTransitionCoordinator c = new LevelTransitionCoordinator();
        BigRingReturnState saved = new BigRingReturnState(
                1, 2, 3, 4, 5, (byte) 6, (byte) 7, 8, 9, 10,
                7, 1, 2, 3, 4, 5, 6, 7);
        c.saveBigRingReturn(saved);
        c.markSanctuaryReentry(4);
        assertTrue(c.requestSanctuaryExit());
        LevelTransitionRewindAdapter adapter = new LevelTransitionRewindAdapter(c);
        LevelTransitionCoordinator.SanctuaryRewindState snapshot = adapter.capture();

        assertTrue(c.consumeZoneActRequest());
        c.completeSanctuaryOriginRestore();
        c.setSuppressNextMusicChange(true);
        adapter.restore(snapshot);

        assertTrue(c.isSanctuaryOriginRestorePending(7, 1));
        assertTrue(c.consumeZoneActRequest(),
                "rewind after exit request must restore its generic request edge");
        assertEquals(7, c.getRequestedZone());
        assertEquals(1, c.getRequestedAct());
        assertEquals(-1, c.getRequestedMusicId());
        assertTrue(c.isLevelInactiveForTransition());
        assertFalse(c.isSuppressNextMusicChange());
        assertSame(saved, c.getBigRingReturn());
    }
}
