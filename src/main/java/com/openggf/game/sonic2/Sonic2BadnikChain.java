package com.openggf.game.sonic2;

import com.openggf.game.internal.BadnikChainPolicy;
import com.openggf.game.rewind.RewindSnapshottable;

/** The single Chain_Bonus_counter shared by BOTH native actors, also in 1P. */
final class Sonic2BadnikChain implements BadnikChainPolicy, RewindSnapshottable<Integer> {
    private int word;

    @Override public int incrementAndAward() {
        // Touch_KillEnemy (s2.asm:85514-85534): index with OLD unsigned word,
        // then test the incremented word. $FFFE wraps before the bonus test.
        // KiS2 Touch_KillEnemy (docs/kis2disasm/s2.asm:88811-88833)
        // uses the same global word, unsigned table limit and wrap ordering.
        int previous = word;
        word = (word + 2) & 0xFFFF;
        if (word >= 0x20) return 10_000;
        // Enemy_Points (s2.asm:85565) stores tens of displayed points.
        return switch (Math.min(previous, 6)) {
            case 0 -> 100;
            case 2 -> 200;
            case 4 -> 500;
            default -> 1000;
        };
    }
    @Override public void reset() {
        // Sonic_ResetOnFloor_Part3 (s2.asm:38142-38147) and
        // Tails_ResetOnFloor_Part3 (s2.asm:41033-41044).
        // fixBugs=0 unconditionally lets even CPU Tails clear the SAME word.
        // fixBugs=1 would protect Sonic's chain from AI Tails; do not enable it.
        // KiS2 Sonic_ResetOnFloor_Part3 (docs/kis2disasm/s2.asm:40997-41020)
        // also clears the word unconditionally. gameRevision=3 has no Tails
        // dispatch; its Knuckles ability/animation tail differs, not this write.
        word = 0;
    }
    @Override public int word() { return word; }
    @Override public void restoreWord(int value) { word = value & 0xFFFF; }
    @Override public String key() { return "sonic2_badnik_chain"; }
    @Override public Integer capture() { return word; }
    @Override public void restore(Integer snapshot) { word = snapshot & 0xFFFF; }
    @Override public void resetForMissingSnapshot() { reset(); }
}
