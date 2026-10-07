package sitarhero.model;

/** Curated logical gems, measured in the prepared song's stereo-sample coordinate. */
public record ChartNote(long onset, long end, int lanes, boolean hopo, int phrase, int sustainTicks) {
    public ChartNote {
        if (onset < 0 || end < onset || lanes <= 0 || (lanes & ~31) != 0 || phrase < -1 || sustainTicks < 0)
            throw new IllegalArgumentException("Invalid chart note");
        if (hopo && Integer.bitCount(lanes) != 1) throw new IllegalArgumentException("Chord HOPO");
    }
}
