package com.openggf.game.sonic2.competition.state;

import com.openggf.game.rewind.RewindSnapshottable;

/** Retained Rings_anim_counter/frame, independent of the level-frame word. */
public final class Sonic2CompetitionRingAnimation implements RewindSnapshottable<Sonic2CompetitionRingAnimation.State> {
    public record State(int counter, int frame) { }
    private int counter, frame;
    /** Level_ClrRam (s2.asm:4806-4819) clears the oscillating-data span. */
    public void clearLevel() { counter = frame = 0; }
    /** ChangeRingFrame (s2.asm:6081-6093): byte SUBQ/BPL, then modulo-four frame. */
    public void changeRingFrame() {
        counter = (counter - 1) & 255;
        if ((byte) counter < 0) { counter = 7; frame = (frame + 1) & 3; }
    }
    public int frame() { return frame; }
    @Override public String key() { return "sonic2.competition.ring-animation"; }
    @Override public State capture() { return new State(counter, frame); }
    @Override public void restore(State state) { counter = state.counter(); frame = state.frame(); }
}
