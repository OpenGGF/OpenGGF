package com.openggf.game.sonic2.competition.scroll;

import com.openggf.data.Rom;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic2.scroll.ParallaxTables;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import static com.openggf.game.sonic2.Sonic2RomAddresses.address;

/**
 * Semantic S2 competition deformation, after both foreground cameras have tracked
 * their players and dynamic events have run. No camera tracking or display timing
 * is simulated here. Camera diffs are not read by these three ROM handlers.
 *
 * <p>One entry is one field/raster line, covering two vertical world pixels in
 * the ROM's 8x16-cell format. Horizontal units remain world pixels. Each packed
 * word pair is signed FG in bits 31..16 and signed BG in bits 15..0. P1 generates
 * 112 entries; P2 starts at 108 and replaces four, leaving visible windows of
 * 108 and 116 lines. Vertical words remain in doubled world-pixel units and use
 * the global raster origin: P2 subtracts 224, then both words are even-masked.
 * Consumers must retain that origin when converting to local view coordinates:
 * the first P2 raster line corresponds to camera Y minus eight world pixels.
 *
 * <p>BG banks use native 16.16 coordinates. The handlers replace only BG Y's
 * integer word (EHZ leaves even that untouched). Scratch words are persistent:
 * shipped CNZ P2 reads an unwritten word. Register this owner once in the rewind
 * registry, or delegate capture/restore through the active scroll handler, never
 * both. Load/restore may seed the banks with {@link #initialize}.
 */
public final class Sonic2CompetitionScroll implements RewindSnapshottable<Sonic2CompetitionScroll.State> {
    public static final int CELL_HEIGHT = 16, P1_LINES = 108, P2_LINES = 116;
    public enum Zone { EHZ, MCZ, CNZ }
    public record Camera(int x, int y) { }
    public record BackgroundBank(int x, int y, int x2, int y2, int x3, int y3) {
        public static final BackgroundBank ZERO = new BackgroundBank(0, 0, 0, 0, 0, 0);
        BackgroundBank withY(int word) {
            return new BackgroundBank(x, (word << 16) | (y & 0xffff), x2, y2, x3, y3);
        }
    }
    public record View(int rasterStart, int rasterHeight, int foregroundVScroll,
                       int backgroundVScroll, BackgroundBank background, int[] horizontalScroll) {
        public View { horizontalScroll = horizontalScroll.clone(); }
        @Override public int[] horizontalScroll() { return horizontalScroll.clone(); }
        public int[] lines() {
            return Arrays.copyOfRange(horizontalScroll, rasterStart, rasterStart + rasterHeight);
        }
    }
    public record State(BackgroundBank p1, BackgroundBank p2, short[] scratch,
                        int[] horizontal, int v1, int v2) {
        public State { scratch = scratch.clone(); horizontal = horizontal.clone(); }
        @Override public short[] scratch() { return scratch.clone(); }
        @Override public int[] horizontal() { return horizontal.clone(); }
    }
    private final ParallaxTables tables;
    private final byte[] cnzHeights;
    private BackgroundBank p1 = BackgroundBank.ZERO, p2 = BackgroundBank.ZERO;
    private short[] scratch = new short[24];
    private int[] horizontal = new int[224];
    private int v1, v2;

    public Sonic2CompetitionScroll(Rom rom) throws IOException {
        tables = new ParallaxTables(rom);
        // SwScrl_CNZ2P_RowHeights_P2 (s2.asm:17252-17277), shipped fixBugs=0.
        cnzHeights = rom.readBytes(address(rom, 0xD271), 11);
    }
    /** Cold-cleared session memory. Use initializeLevel for subsequent level loads. */
    public void initialize(BackgroundBank first, BackgroundBank second) {
        p1 = Objects.requireNonNull(first); p2 = Objects.requireNonNull(second);
        scratch = new short[24]; horizontal = new int[224]; v1 = v2 = 0;
    }
    /**
     * Accept banks prepared by the level camera initializer.
     * Level_ClrRam (s2.asm:4806-4819) leaves TempArray_LayerDef intact.
     * InitCam_EHZ (s2.asm:14926-14940) clears its first six words, MCZ/CNZ do not.
     */
    public void initializeLevel(Zone zone, BackgroundBank first, BackgroundBank second) {
        p1 = Objects.requireNonNull(first); p2 = Objects.requireNonNull(second);
        if (zone == Zone.EHZ) Arrays.fill(scratch, 0, 6, (short)0);
        horizontal = new int[224]; v1 = v2 = 0;
    }
    @Override public String key() { return "sonic2.competition.scroll"; }
    @Override public State capture() { return new State(p1, p2, scratch, horizontal, v1, v2); }
    @Override public void restore(State state) {
        if (state.scratch.length != 24 || state.horizontal.length != 224)
            throw new IllegalArgumentException("Competition scroll snapshot shape");
        p1 = state.p1; p2 = state.p2; scratch = state.scratch(); horizontal = state.horizontal();
        v1 = state.v1; v2 = state.v2;
    }
    public View view(int slot) {
        if (slot != 0 && slot != 1) throw new IllegalArgumentException("P1/P2 slot required");
        int v = slot == 0 ? v1 : v2;
        return new View(slot == 0 ? 0 : 108, slot == 0 ? 108 : 116,
                (short)(v >>> 16), (short)v, slot == 0 ? p1 : p2, horizontal);
    }
    /** Once per DeformBgLayer pass; vintRunCount is Vint_runcount, not the level clock. */
    public void update(Zone zone, int act, Camera first, Camera second, int vintRunCount) {
        // loc_C4D0 (s2.asm:15174-15187) publishes P1 factors before zone deformation.
        if (zone == Zone.EHZ && (vintRunCount & 7) == 0) scratch[0]--;
        for (int slot = 0; slot < 2; slot++) {
            Camera camera = slot == 0 ? first : second;
            BackgroundBank bank = slot == 0 ? p1 : p2;
            int y = camera.y & 0xffff;
            if (zone == Zone.MCZ) bank = bank.withY(y / (act == 0 ? 3 : 6) - (act == 0 ? 320 : 16));
            if (zone == Zone.CNZ) bank = bank.withY(y >>> 6);
            int bgY = bank.y >>> 16;
            int offset = slot == 0 ? 0 : 224;
            int bgV = zone == Zone.EHZ && slot == 1 ? 0 : bgY;
            int v = pack((y - offset) & 0xfffe, (bgV - offset) & 0xfffe);
            if (slot == 0) { p1 = bank; v1 = v; } else { p2 = bank; v2 = v; }
            int start = slot == 0 ? 0 : 108, count = slot == 0 ? 112 : 116;
            switch (zone) {
                case EHZ -> ehz(camera.x, start, slot == 0 ? 11 : 15);
                case MCZ -> {
                    mczValues(camera.x);
                    byte[] heights = Arrays.copyOf(tables.getMcz2PRowHeights(), 24);
                    if (slot == 1) heights[0] = 23;
                    rows(camera.x, bgY >>> 1, start, count, heights, false, vintRunCount);
                }
                case CNZ -> {
                    cnzValues(camera.x);
                    // fixBugs=0: P2 prepends a 4-line segment, shifting every value;
                    // the fixed branch extends the first 8-line segment instead.
                    byte[] heights = slot == 0 ? Arrays.copyOfRange(cnzHeights, 1, 11) : cnzHeights;
                    rows(camera.x, bgY >>> 1, start, count, heights, true, vintRunCount);
                }
            }
        }
    }
    private static int pack(int fg, int bg) { return (fg << 16) | (bg & 0xffff); }
    // SwScrl_EHZ_2P (s2.asm:15427-15559).
    private void ehz(int x, int start, int sky) {
        int fg = (short)-x, out = start;
        for (int i = 0; i < sky; i++) horizontal[out++] = pack(fg, 0);
        int far = fg >> 6;
        for (int i = 0; i < 29; i++) horizontal[out++] = pack(fg, far);
        for (int i = 0; i < 11; i++) horizontal[out++] = pack(fg, far + tables.getRippleSigned((scratch[0] & 31) + i));
        for (int i = 0; i < 5; i++) horizontal[out++] = pack(fg, 0);
        for (int i = 0; i < 8; i++) horizontal[out++] = pack(fg, fg >> 4);
        for (int i = 0; i < 8; i++) horizontal[out++] = pack(fg, (fg >> 4) + (fg >> 5));
        int step = (short)((((fg >> 1) - (fg >> 3)) << 8) / 48) << 8;
        int accumulator = (fg >> 3) << 16;
        for (int i = 0; i < 40; i++) {
            horizontal[out++] = pack(fg, accumulator >> 16);
            accumulator += step;
        }
    }
    // SwScrl_MCZ_2P (s2.asm:16645-16973): signed quotient truncates towards zero.
    private void mczValues(int x) {
        int step = (short)(((short)x * 16) / 10) << 12;
        int[] multiples = new int[9];
        for (int i = 0; i < 9; i++) multiples[i] = (step * (i + 1)) >> 16;
        int[] order = {9,8,7,5,4,3,2,1,5,7,8,9,8,7,5,1,2,3,4,5,6,7,8,9};
        for (int i = 0; i < 24; i++) scratch[i] = (short)multiples[order[i] - 1];
    }
    // SwScrl_CNZ_GenerateScrollValues (s2.asm:17073-17100).
    private void cnzValues(int x) {
        int word = (short)x;
        int step = (short)((word >> 3) - word) << 13;
        int accumulator = word << 16;
        for (int i = 0; i < 7; i++) {
            scratch[i] = (short)(accumulator >> 16); accumulator += step;
        }
        scratch[7] = scratch[8] = (short)(word >> 4); scratch[9] = (short)(word >> 3);
        // Word 10 is deliberately retained; P2's shipped row table reads it.
    }
    // SwScrl_CNZ_2P (s2.asm:17106-17246), also MCZ's ordinary rows.
    private void rows(int x, int skip, int start, int count, byte[] heights, boolean ripple, int vint) {
        int segment = 0;
        while (skip >= (heights[segment] & 255)) skip -= heights[segment++] & 255;
        int remaining = (heights[segment] & 255) - skip;
        int out = start, end = start + count;
        while (out < end) {
            horizontal[out++] = pack(-x, -scratch[segment]);
            if (--remaining != 0) continue;
            segment++;
            remaining = heights[segment] & 255;
            if (ripple && remaining == 0) {
                int phase = -((vint & 255) >>> 3) & 31;
                for (int i = 0; i < 8; i++)
                    horizontal[out++] = pack(-x, -scratch[segment] + tables.getRippleSigned(phase + i));
                segment++;
                remaining = heights[segment] & 255;
            }
        }
    }
}
