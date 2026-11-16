package com.openggf.game.sonic3k.events;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestIczComposedRewindState {
    @Test void allShakeWordsRestoreAndMalformedPayloadsRejectAtomically() {
        var source = new Sonic3kICZEvents();
        source.triggerScreenShake(30);
        byte[] saved = source.captureIczState();
        assertTrue(Sonic3kICZEvents.hasValidIczState(saved));
        var target = new Sonic3kICZEvents();
        target.restoreIczState(saved);
        assertArrayEquals(saved, target.captureIczState());
        for (byte[] bad : new byte[][] {new byte[0], Arrays.copyOf(saved, saved.length - 1),
                Arrays.copyOf(saved, saved.length + 1), corrupt(saved, 0, 0),
                corrupt(saved, 4, 2), corrupt(saved, 8, -1),
                corrupt(saved, saved.length - 12, 65536),
                corrupt(saved, saved.length - 8, 65536), corrupt(saved, saved.length - 4, 65536)}) {
            assertFalse(Sonic3kICZEvents.hasValidIczState(bad));
            assertThrows(IllegalArgumentException.class, () -> target.restoreIczState(bad));
            assertArrayEquals(saved, target.captureIczState(), "rejection must preserve every word");
        }
        assertThrows(IllegalArgumentException.class, () -> target.restoreIczState(null));
        assertArrayEquals(saved, target.captureIczState());
    }
    @Test void malformedComponentCannotMutateManagerBaseState() {
        com.openggf.tests.TestEnvironment.resetAll();
        try {
            var manager = new com.openggf.game.sonic3k.Sonic3kLevelEventManager();
            manager.initLevel(com.openggf.game.sonic3k.constants.Sonic3kZoneIds.ZONE_ICZ, 0);
            manager.getIczEvents().triggerScreenShake(30);
            var before = manager.capture();
            byte[] extra = before.extra();
            int start = 31 + 6 + 4; // manager, five absent zones, ICZ presence/length
            assertEquals(0x49435A31, ByteBuffer.wrap(extra).getInt(start));
            ByteBuffer.wrap(extra).putInt(start + 4, 99);
            var bad = new com.openggf.game.rewind.snapshot.LevelEventSnapshot(
                    99, 99, 99, 99, 99, 99, true, new short[8], new byte[8], extra);
            manager.restore(bad);
            var after = manager.capture();
            assertEquals(before.currentZone(), after.currentZone());
            assertEquals(before.currentAct(), after.currentAct());
            assertEquals(before.frameCounter(), after.frameCounter());
            assertArrayEquals(before.extra(), after.extra());
        } finally {
            com.openggf.game.session.SessionManager.clear();
        }
    }

    private static byte[] corrupt(byte[] source, int at, int value) {
        byte[] result = source.clone();
        ByteBuffer.wrap(result).putInt(at, value);
        return result;
    }
}
