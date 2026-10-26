package com.openggf.game.sonic3k.competition;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.RewindSnapshottable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** The three independently published scroll registers used by the competition VInt/HInt pair. */
public final class S3kCompetitionViewRegisters implements RewindSnapshottable<S3kCompetitionViewRegisters.State> {
    public record State(boolean published, int foreground1, int background1,
                        int foreground2, int background2, List<Integer> horizontalScroll) {
        public State {
            horizontalScroll = List.copyOf(horizontalScroll);
            if (horizontalScroll.size() != 224) throw new IllegalArgumentException("Native Hscroll needs 224 longs");
        }
    }

    private State state = cleared(0, 0);

    /** Comp_BackgroundInit seeds the P2 copy before the first represented VBlank. */
    public void initialize(int foreground2, int background2) {
        state = cleared((short) foreground2, (short) background2);
    }

    /**
     * VInt_8/VInt_10 (sonic3k.asm:697-782), VInt_A_C (798-841),
     * VInt_0_NoWater (611-650), and Do_ControllerPal (926-944).
     * HInt reads the retained P2 copy, rather than current camera RAM (955-1003).
     */
    public void publish(PlcLifecyclePhase phase, int foreground1, int background1,
                        int foreground2, int background2, List<Integer> horizontalScroll) {
        Objects.requireNonNull(phase, "phase");
        boolean full = phase == PlcLifecyclePhase.ORDINARY_LEVEL
                || phase == PlcLifecyclePhase.NORMAL_PAUSE || phase == PlcLifecyclePhase.LEVEL_TITLE_CARD;
        if (!full && phase != PlcLifecyclePhase.LAG && phase != PlcLifecyclePhase.PALETTE_FADE)
            throw new IllegalArgumentException("Not a native race VInt: " + phase);
        // VInt12 uploads Hscroll but does not copy the P2 Vscroll words.
        state = new State(true, (short) foreground1, (short) background1,
                full ? (short) foreground2 : state.foreground2(),
                full ? (short) background2 : state.background2(),
                phase == PlcLifecyclePhase.LAG ? state.horizontalScroll() : horizontalScroll);
    }

    private static State cleared(int foreground2, int background2) {
        return new State(false, 0, 0, foreground2, background2, Collections.nCopies(224, 0));
    }

    @Override public String key() { return "sonic3k.competition.view-registers"; }
    @Override public State capture() { return state; }
    @Override public void restore(State snapshot) { state = Objects.requireNonNull(snapshot); }
    @Override public void resetForMissingSnapshot() { state = cleared(0, 0); }
}
