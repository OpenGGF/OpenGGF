package com.openggf.game.sonic2.competition.view;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import java.util.BitSet;

/** Derived field-projected cells. Both parity banks share the normal level atlas range. */
public final class Sonic2CompetitionFieldPatterns implements com.openggf.level.animation.AnimatedPatternManager,
        com.openggf.game.rewind.RewindSnapshottable<Sonic2CompetitionFieldPatterns.State> {
    public record State() { }
    @Override public String key() { return "sonic2.competition.animated-art"; }
    @Override public State capture() { return new State(); }
    @Override public void restore(State state) { java.util.Objects.requireNonNull(state); rebuild(); }
    @Override public void update() { refresh(); }
    private final Sonic2CompetitionArtResidency residency;
    private final Pattern[] patterns = new Pattern[2048];
    private final byte[][] seen = new byte[2048][];
    private final BitSet dirty = new BitSet(2048);
    public Sonic2CompetitionFieldPatterns(Sonic2CompetitionArtResidency residency) {
        this.residency = java.util.Objects.requireNonNull(residency);
        for (int i = 0; i < patterns.length; i++) patterns[i] = new Pattern();
        refresh();
    }
    public void rebuild() {
        java.util.Arrays.fill(seen, null);
        refresh();
    }
    public Pattern[] live() { refresh(); return patterns; }
    /** Recomputes only cells whose copy-on-write source slots changed. */
    public void refresh() {
        for (int cell = 0; cell < 1024; cell++) {
            byte[] top = residency.slot(cell * 2), bottom = residency.slot(cell * 2 + 1);
            if (seen[cell * 2] == top && seen[cell * 2 + 1] == bottom) continue;
            for (int bank = 0; bank < 2; bank++) {
                int id = (bank << 10) | cell;
                var pattern = patterns[id];
                for (int y = 0; y < 8; y++) {
                    int row = y * 2 + bank;
                    byte[] source = row < 8 ? top : bottom;
                    int offset = (row & 7) * 4;
                    for (int x = 0; x < 8; x++)
                        pattern.setPixel(x, y, (byte) ((source[offset + x / 2] >>> ((x & 1) == 0 ? 4 : 0)) & 15));
                }
                dirty.set(id);
            }
            seen[cell * 2] = top; seen[cell * 2 + 1] = bottom;
        }
    }
    public void flush(GraphicsManager graphics) {
        refresh();
        for (int id = dirty.nextSetBit(0); id >= 0; id = dirty.nextSetBit(id + 1))
            graphics.cachePatternTexture(patterns[id], id);
        dirty.clear();
    }
}
