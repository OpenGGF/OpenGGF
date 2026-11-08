package com.openggf.game.sonic2.competition;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;

/** S2 Joypad_Read's held-to-press latch, used by represented synchronous waits. */
final class Sonic2NativeControllerSample {
    private Sonic2NativeControllerSample() { }

    /**
     * Joypad_Read (s2.asm:1368-1386): pressed = held & ~previousHeld.
     * The outer logical sample may span several real waits. Its press bits
     * belong to its outer poll and cannot be replayed at every native poll.
     * All held buttons survive, including Start; release/repress creates a new edge.
     */
    static LogicalInputSnapshot poll(LogicalInputSnapshot previous, LogicalInputSnapshot held) {
        return LogicalInputSnapshot.ofPlayers(poll(previous.player1(), held.player1()),
                poll(previous.player2(), held.player2()));
    }

    private static PlayerInputState poll(PlayerInputState previous, PlayerInputState held) {
        return PlayerInputState.of(held.heldMask(), held.heldMask() & ~previous.heldMask(),
                held.actionHeldMask(), held.actionHeldMask() & ~previous.actionHeldMask(),
                held.startHeld(), held.startHeld() && !previous.startHeld());
    }
}
