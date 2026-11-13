package com.openggf.game.sonic2.competition;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic2NativeControllerSample {
    @Test void everyWaitPollsBothHeldBytesRatherThanReplayingOuterPresses() {
        // Joypad_Read (s2.asm:1368-1386), independently for both controller words.
        var previous = LogicalInputSnapshot.neutral();
        for (int button = 1; button <= 0x80; button <<= 1) {
            var held = LogicalInputSnapshot.ofPlayers(player(button), player(0xff ^ button));
            var first = Sonic2NativeControllerSample.poll(previous, held);
            assertEquals(button * 0x101, Sonic2CompetitionPause.convertLogical(first.player1()));
            assertEquals((0xff ^ button) * 0x101, Sonic2CompetitionPause.convertLogical(first.player2()));
            var repeated = Sonic2NativeControllerSample.poll(first, held);
            assertEquals(button << 8, Sonic2CompetitionPause.convertLogical(repeated.player1()));
            assertEquals((0xff ^ button) << 8, Sonic2CompetitionPause.convertLogical(repeated.player2()));
            var released = Sonic2NativeControllerSample.poll(repeated, LogicalInputSnapshot.neutral());
            assertEquals(0, Sonic2CompetitionPause.convertLogical(released.player1()));
            assertEquals(first, Sonic2NativeControllerSample.poll(released, held));
        }
    }
    @Test void freshHeldTransitionsWinEvenWhenTheOuterPressSnapshotIsStale() {
        var held = LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0, 0, 0, 0, true, false), PlayerInputState.neutral());
        assertTrue(Sonic2NativeControllerSample.poll(LogicalInputSnapshot.neutral(), held).player1().startPressed());
        var stale = LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0, 0, 0, 0, false, true), PlayerInputState.neutral());
        assertFalse(Sonic2NativeControllerSample.poll(held, stale).player1().startPressed());
    }
    private static int actions(int bits) {
        return ((bits & 0x40) != 0 ? 1 : 0) | ((bits & 0x10) != 0 ? 2 : 0) | ((bits & 0x20) != 0 ? 4 : 0);
    }
    private static PlayerInputState player(int nativeBits) {
        // Translate native A/B/C bits to logical action masks.
        return PlayerInputState.of(nativeBits & 15, nativeBits & 15,
                actions(nativeBits), actions(nativeBits), (nativeBits & 0x80) != 0, (nativeBits & 0x80) != 0);
    }
}
