package com.openggf.game.sonic2;

import com.openggf.data.Rom;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.level.Level;
import com.openggf.level.animation.AnimatedPaletteManager;

import java.io.IOException;
import java.util.Objects;

/** Palette-only composition for native worlds; ordinary combined animation is unchanged. */
public final class Sonic2PaletteCycleInternalAccess {
    private Sonic2PaletteCycleInternalAccess() { }

    /**
     * PalCycle_Load (s2.asm:2689-2696) dispatches only by Current_Zone, without a 2P gate.
     * Level_MainLoop (s2.asm:5105-5107) runs it after AniArt_Load in either mode.
     * PalCycle_EHZ (s2.asm:2729-2742), PalCycle_MCZ (s2.asm:2867-2880) and
     * PalCycle_CNZ (s2.asm:2883-2949) have no 2P branch; boss gates remain with the cycler.
     */
    public static Owner create(Rom rom, Level level, int zoneIndex) throws IOException {
        return new Owner(new Sonic2PaletteCycler(rom, level, zoneIndex));
    }

    public static final class Owner implements AnimatedPaletteManager, RewindSnapshottable<State> {
        private final Sonic2PaletteCycler cycler;
        private Owner(Sonic2PaletteCycler cycler) { this.cycler = cycler; }
        @Override public String key() { return "sonic2.competition.palette-cycles"; }
        @Override public void update() { cycler.update(); }
        @Override public State capture() { return new State(cycler.captureCyclerState()); }
        @Override public void restore(State state) { cycler.restoreCyclerState(Objects.requireNonNull(state).bytes()); }
    }

    public record State(byte[] bytes) {
        public State { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
}
