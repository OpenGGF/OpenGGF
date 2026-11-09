package com.openggf.game.sonic2.competition.view;

import com.openggf.game.internal.RetainedPlaneDescriptorSource;
import com.openggf.game.rewind.RewindSnapshottable;
import java.util.List;

/** Three retained 64x32 images of native 8x16 cells; gameplay geometry is independent. */
public final class Sonic2CompetitionPlanes implements RewindSnapshottable<Sonic2CompetitionPlanes.State> {
    public enum PlaneId { FOREGROUND_P1, FOREGROUND_P2, BACKGROUND }
    private short[][] cells = new short[3][2048];
    private long[] revisions = {1, 1, 1};
    // Derived renderer generations are deliberately outside captured semantic state.
    private final long[] displayRevisions = {1, 1, 1};
    private final RetainedPlaneDescriptorSource.Plane[] fields = new RetainedPlaneDescriptorSource.Plane[3];

    public Sonic2CompetitionPlanes() {
        for (var id : PlaneId.values()) fields[id.ordinal()] = new RetainedPlaneDescriptorSource.Plane() {
            public int widthTiles() { return 64; }
            public int heightTiles() { return 64; }
            public long revision() { return displayRevisions[id.ordinal()]; }
            public int descriptorAt(int x, int y) {
                int word = cell(id, x >> 3, y >> 4);
                // Whole-cell V flip exchanges its top and bottom 8-row halves.
                return (word & 0xF800) | ((word & 0x3FF) * 2)
                        | (((y >> 3) & 1) ^ ((word >> 12) & 1));
            }
        };
    }
    public int cell(PlaneId id, int column, int row) {
        return cells[id.ordinal()][(row & 31) * 64 + (column & 63)] & 65535;
    }
    public void write(PlaneId id, int column, int row, int word) {
        int p = id.ordinal(), offset = (row & 31) * 64 + (column & 63);
        if (cells[p][offset] != (short) word) {
            cells[p][offset] = (short) word;
            revisions[p]++; displayRevisions[p]++;
        }
    }
    public void apply(List<Sonic2CompetitionPlaneDraw.CellWrite> writes) {
        for (var write : writes) {
            var position = write.position();
            for (int i = 0; i < write.words().size(); i++)
                write(position.plane(), position.column() + i, position.row(), write.words().get(i));
        }
    }
    public RetainedPlaneDescriptorSource.Plane fieldPlane(PlaneId id) { return fields[id.ordinal()]; }
    public RetainedPlaneDescriptorSource source(PlaneId foreground) {
        return new RetainedPlaneDescriptorSource() {
            public Plane foregroundPlane() { return fieldPlane(foreground); }
            public Plane backgroundPlane() { return fieldPlane(PlaneId.BACKGROUND); }
        };
    }
    @Override public String key() { return "sonic2.competition.planes"; }
    @Override public State capture() { return new State(cells, revisions); }
    @Override public void restore(State state) {
        cells = state.cells(); revisions = state.revisions();
        for (int i = 0; i < 3; i++) displayRevisions[i]++;
    }
    public record State(short[][] cells, long[] revisions) {
        public State { cells = copy(cells); revisions = revisions.clone(); }
        @Override public short[][] cells() { return copy(cells); }
        @Override public long[] revisions() { return revisions.clone(); }
        private static short[][] copy(short[][] source) {
            return java.util.Arrays.stream(source).map(short[]::clone).toArray(short[][]::new);
        }
    }
}
