package com.openggf.game.soniccd.zones.mmz;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.SonicCdGame;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.ScrollOutputRebuilder;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

import java.util.Arrays;
import java.util.List;

/**
 * Metallic Madness background scroll, all ten stages (act 1 and 2 in four time periods, act 3 Good and Bad Future).
 *
 * <p>Sources (docs/scddisasm pin 1ecc5f3a): every stage assembles {@code r8/scroll_<stage>.asm}
 * ({@code Level/Metallic Madness/R8 Shared.asm}, R8_VARIANT 1..10 = R81A, R81B, R81C, R81D, R82A, R82B, R82C, R82D,
 * R83C, R83D). Line numbers below are for R81A ({@code scroll_1a.asm}) unless a file is named. The routines share one
 * skeleton and differ in three kinds of data:
 * <ol>
 *   <li><b>Layer rates and the background Y.</b> {@code UpdateScroll} (:156-209) advances three 16.16 layer cameras by
 *       the foreground camera delta {@code scroll_x_move} (8.8): BG3 {@code <<6} (:180-185), BG2 {@code <<7}
 *       (:186-190), BG {@code *48} ({@code <<4} plus twice that, :191-199), and sets the background Y to
 *       {@code cameraY >> 2} ({@code lsr.w #2,d0 ; ScrollBgY}, :200-202). Acts 2 and 3 use {@code >> 3}
 *       ({@code lsr.l #3,d0 ; swap d0}, scroll_2a.asm:189-191, same in 2b 2c 2d 3c; {@code lsr.w #3} in
 *       scroll_3d.asm:180). R83C changes the BG3 rate to {@code <<7} (scroll_3c.asm:222); R83D uses BG3 {@code *192},
 *       BG2 {@code <<8}, BG {@code <<7} (scroll_3d.asm:162-178). The init routine ({@code sub_202A04}, :136-152)
 *       starts the layers at the same fractions of the camera; R83C and R83D have their own (scroll_3c.asm:176-192,
 *       scroll_3d.asm:114-132, including {@code +$70}).</li>
 *   <li><b>The deformation program.</b> After the layer update, each stage fills {@code bg_scroll_lines} (256 words) with a
 *       fixed sequence of <i>fills</i> (one layer's {@code -x} for n words, e.g. :216-223) and <i>gradients</i> (a helper
 *       such as {@code sub_202B38}, :267-298: a linear ramp between two layers, one value per segment, each segment
 *       repeated {@code table[d6]+1} times). {@code scroll_1a.asm} = {@code 1c} = {@code 1d} and {@code 2a} = {@code 2d}
 *       have identical {@code UpdateScroll} bodies (checked by normalised comparison); 1B, 2B, 2C, 3C and 3D differ
 *       and each has its own program in {@link #layoutFor}.</li>
 *   <li><b>Segment-length tables.</b> Read from the stage file at the offsets
 *       {@code tools/soniccd/mmz_presentation_catalogue.py} found. Slot order is the file order of the reading
 *       instruction: R81A/C/D slot 0 is the only table; R81B slot 0/1 are the tables passed before the first/second
 *       call (scroll_1b.asm:213-217, 231-235); in R82A-R83C slot 0 is the ascending helper's table and slot 1 the
 *       descending helper's.</li>
 * </ol>
 * The gradient step is {@code ((minuend - subtrahend) << a) / divisor << b} through {@code divu.w}: an overflowing
 * quotient (every negative difference, and large positive ones) leaves the dividend unchanged, so the following
 * {@code ext.l} takes its low word. Each segment adds the step to a long whose high half is the carried leftover of
 * {@code d3}; that leftover is {@code (scroll_x_move << 5) >> 16}, the high word of the BG-rate scratch
 * ({@code scroll_1a.asm:191-196}) that {@code ScrollBgX}/{@code ScrollBgY} only partly overwrite, and it is carried
 * from one helper call to the next (the loops end with {@code swap d3}).
 *
 * <p>Every deformation word covers 8 lines, written by the block loop at :246-265 starting at
 * {@code (bgY & $3F8) >> 3} with a partial first block and 29 more.
 *
 * <p>Shipped {@code FixBugs = 0}; the routine has no fixed-bug branch. Behaviour kept as shipped: it writes only
 * 48..96 words of the 256 in {@code bg_scroll_lines} and reads up to 30 blocks from {@code bgY / 8}; words it
 * never wrote are cold RAM, which this model reads as zero. {@code scroll_bg_y} is overwritten each frame, so the
 * vertical wrap in {@code ScrollFgY} never survives to a frame's output and is not modelled. The native routine does
 * not run while {@code scroll_lock} is set (:158-160) or the player is dead
 * ({@link com.openggf.game.soniccd.zones.CdLevelScrollGate}). {@code StageEvents} (:166-171, region-ordered with the foreground scroll) is the level-events lane.
 */
public final class MmzScrollHandler extends AbstractZoneScrollHandler implements ScrollOutputRebuilder {
    /** Which camera value a fill or gradient reads. */
    enum Layer { FG, BG, BG2, BG3 }

    /** One block of the deformation program. */
    sealed interface Op permits Fill, Gradient { }

    /** {@code move.w scroll_bgN_x,d0 ; neg.w d0 ; moveq #n-1,d6 ; move.w d0,(a1)+ ; dbf} (:216-223). */
    record Fill(Layer layer, int words) implements Op { }

    /**
     * A gradient helper. {@code top} is the {@code d6} the helper starts from; {@code words} the span in
     * {@code bg_scroll_lines} (the {@code adda.w} size of a descending helper); {@code base} the layer whose word
     * seeds {@code d3}; the step is {@code ((minuend - subtrahend) << shiftIn) / divisor << shiftOut}.
     */
    record Gradient(boolean descending, int slot, int top, int words, Layer base, Layer minuend, Layer subtrahend,
                    int shiftIn, int divisor, int shiftOut) implements Op { }

    /** Layer rates (multipliers of the 8.8 camera delta added to each 16.16 layer) and the Y shift. */
    record Rates(int bg3, int bg2, int bg, int bgYShift) { }

    /** How the layers start (the init routine called from InitScroll). */
    enum Init { QUARTER, R83C, R83D }

    /** Per-stage data. */
    record Layout(Rates rates, Init init, List<Op> ops) { }

    private static final Rates ACT1 = new Rates(64, 128, 48, 2);         // :180-202
    private static final Rates ACT2 = new Rates(64, 128, 48, 3);         // scroll_2a.asm:172-191
    private static final Rates R83C_RATES = new Rates(128, 128, 48, 3);  // scroll_3c.asm:222-241
    private static final Rates R83D_RATES = new Rates(192, 256, 128, 3); // scroll_3d.asm:162-180

    private static Gradient lights(boolean descending, int slot, int top, int words) {
        // sub_202B38 / sub_202B10 / sub_202B36 (:267-298, scddisasm/src/r8/scroll_2a.asm:262-318): (bg2 - bg3) << 7 / 5 << 9 from BG3.
        return new Gradient(descending, slot, top, words, Layer.BG3, Layer.BG2, Layer.BG3, 7, 5, 9);
    }

    private static final List<Op> OPS_1A = List.of(                       // :211-245 (R81A; R81C, R81D identical)
            lights(true, 0, 4, 14), new Fill(Layer.BG, 16), new Fill(Layer.BG2, 28),
            lights(true, 0, 4, 14), new Fill(Layer.BG, 12), new Fill(Layer.BG2, 12));
    private static final List<Op> OPS_1B = List.of(                       // scroll_1b.asm:210-250
            lights(true, 0, 8, 26), new Fill(Layer.BG, 4), new Fill(Layer.BG2, 28),
            lights(true, 1, 6, 18), new Fill(Layer.BG, 8), new Fill(Layer.BG2, 12));
    private static final List<Op> OPS_2A = List.of(                       // scroll_2a.asm:201-228 (R82A; R82D identical)
            new Fill(Layer.BG, 6), lights(false, 0, 6, 10), new Fill(Layer.BG2, 18), lights(true, 1, 6, 18),
            new Fill(Layer.BG, 10));
    private static final List<Op> OPS_2B = List.of(                       // scroll_2b.asm:213-240
            // sub_202AF2 (:303-320): (fg - bg) << 6 / 12 << 10 from BG, via sub_202B2A with d6 = $C.
            new Gradient(true, 1, 0xB, 28, Layer.BG, Layer.FG, Layer.BG, 6, 12, 10), new Fill(Layer.BG, 4),
            // sub_202AC2 (:278-296): (bg3 - bg) << 6 / 8 << 10 from BG, d6 = 8.
            new Gradient(false, 0, 7, 10, Layer.BG, Layer.BG3, Layer.BG, 6, 8, 10),
            new Fill(Layer.BG3, 10), new Fill(Layer.BG2, 12));
    private static final List<Op> OPS_2C = List.of(                       // scroll_2c.asm:189-209
            // sub_202BB8 (:266-291) with sub_202BF0 (:293-304): (fg - bg) << 5 / 5 << $B from BG.
            new Gradient(true, 1, 4, 20, Layer.BG, Layer.FG, Layer.BG, 5, 5, 0xB), new Fill(Layer.BG3, 14),
            // sub_202B88 (:241-264): (bg2 - bg3) << 5 / 7 << $B from BG3.
            new Gradient(false, 0, 6, 18, Layer.BG3, Layer.BG2, Layer.BG3, 5, 7, 0xB), new Fill(Layer.BG2, 12));
    private static final List<Op> OPS_3C = List.of(                       // scroll_3c.asm:251-263
            // sub_202C04 (:319-340) with sub_202C3C (:342-352): (fg - bg) << 6 / 5 << $A from BG.
            new Gradient(true, 1, 4, 14, Layer.BG, Layer.FG, Layer.BG, 6, 5, 0xA), new Fill(Layer.BG3, 18),
            // sub_202BD4 (:294-318): (fg - bg3) << 6 / 5 << $A from BG3.
            new Gradient(false, 0, 4, 16, Layer.BG3, Layer.FG, Layer.BG3, 6, 5, 0xA));
    private static final List<Op> OPS_3D = List.of(                       // scroll_3d.asm:190-215: three fills, no gradient
            new Fill(Layer.BG, 34), new Fill(Layer.BG3, 6), new Fill(Layer.BG2, 8));

    static Layout layoutFor(CdStagePayload payload) {
        return switch (payload) {
            case R81A, R81C, R81D -> new Layout(ACT1, Init.QUARTER, OPS_1A);
            case R81B -> new Layout(ACT1, Init.QUARTER, OPS_1B);
            case R82A, R82D -> new Layout(ACT2, Init.QUARTER, OPS_2A);
            case R82B -> new Layout(ACT2, Init.QUARTER, OPS_2B);
            case R82C -> new Layout(ACT2, Init.QUARTER, OPS_2C);
            case R83C -> new Layout(R83C_RATES, Init.R83C, OPS_3C);
            case R83D -> new Layout(R83D_RATES, Init.R83D, OPS_3D);
            default -> throw new IllegalArgumentException(payload + " is not a Metallic Madness stage");
        };
    }

    private static final int DEFORM_WORDS = 0x200 / 2;       // bg_scroll_lines / deformBuffer rs.b $200 (_Definitions.inc:121)
    private static final int LINE_SINK = 512;                // spare room past the 240 lines a frame can write
    private static final int TABLE_BYTES = 16;               // longest segment table read (d6 up to $B)

    private final CdStagePayload payload;
    private final Layout layout;
    private int[][] tables;                                   // segment lengths per slot, read from the stage file
    private final ScrollEffectComposer composer = new ScrollEffectComposer();

    // Native RAM this routine owns: scroll_bg_x/bg2_x/bg3_x/bg_y are 16.16 longs.
    private int bgX;
    private int bg2X;
    private int bg3X;
    private int bgY;
    private int scrollXMove;                                  // scroll_x_move, 8.8 camera delta
    private final short[] deform = new short[DEFORM_WORDS];
    private final short[] sink = new short[LINE_SINK];
    private int sinkPos;
    private int lastCameraX;
    private int lastFrame = Integer.MIN_VALUE;
    private boolean initialised;
    private short foreground;

    public MmzScrollHandler(CdStagePayload payload) {
        this(payload, null);
    }

    /** {@code segmentTables[slot][d6]} are the gradient segment lengths; {@code null} reads them from the loaded stage on first use. */
    public MmzScrollHandler(CdStagePayload payload, int[][] segmentTables) {
        this.payload = payload;
        this.layout = layoutFor(payload);
        this.tables = segmentTables;
    }

    private int[][] tables() {
        if (tables == null) {
            var game = GameServices.level().getGame();
            CdStageImage image = game instanceof SonicCdGame cd ? cd.currentImage() : null;
            if (image == null) throw new IllegalStateException("The Metallic Madness scroll needs the loaded stage file");
            tables = readTables(payload, image);
        }
        return tables;
    }

    /** Reads the gradient segment-length tables from the stage file at the offsets the generator found. */
    static int[][] readTables(CdStagePayload payload, CdStageImage image) {
        var rows = MmzCatalogue.rows(payload, "grad");
        int[][] out = new int[rows.size()][TABLE_BYTES];
        for (var row : rows) {
            for (int i = 0; i < TABLE_BYTES; i++) out[row.slot()][i] = image.u8(row.a() + i);
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------
    // Level start: InitScroll
    // ---------------------------------------------------------------------------------------------

    /** The init routine with the camera InitScroll leaves in d0/d1 (:136-152, scroll_3c.asm:176-192, scroll_3d.asm:114-132). */
    void initLevelScroll(int cameraX, int cameraY) {
        int d0 = cameraY & 0xFFFF;
        int d1 = cameraX & 0xFFFF;
        Arrays.fill(deform, (short) 0);
        // ClearCamera zeroed every fraction before LevelSizeLoad (Level/Main.asm:210-216).
        bgY = (d0 << 16) >>> layout.rates().bgYShift();       // swap d0 ; lsr.l #2|#3,d0 ; move.l d0,scroll_bg_y
        switch (layout.init()) {
            case QUARTER -> {
                d1 >>>= 1;
                bg2X = d1 << 16;                              // lsr.l #1,d1 ; move.w d1,scroll_bg2_x
                d1 >>>= 1;
                bg3X = d1 << 16;                              // lsr.l #1,d1 ; move.w d1,scroll_bg3_x
                d1 >>>= 2;
                bgX = ((d1 * 3) & 0xFFFF) << 16;              // lsr.l #2,d1 ; d1 + 2*d1 ; move.w d1,scroll_bg_x
            }
            case R83C -> {
                d1 >>>= 1;
                bg2X = d1 << 16;                              // scroll_3c.asm:182-183
                bg3X = ((d1 + 0x70) & 0xFFFF) << 16;          // move.w d1,scroll_bg3_x ; addi.w #$70,scroll_bg3_x
                d1 >>>= 2;
                bgX = ((d1 * 3) & 0xFFFF) << 16;
            }
            case R83D -> {
                bg2X = ((d1 + 0x70) & 0xFFFF) << 16;          // scroll_3d.asm:119-120
                d1 >>>= 2;
                bg3X = ((d1 * 3 + 0x70) & 0xFFFF) << 16;      // d2 = 3*d1 ; move.w d2,scroll_bg3_x ; addi.w #$70
                bgX = (((d1 << 1) + 0x70) & 0xFFFF) << 16;    // lsl.l #1,d1 ; move.w d1,scroll_bg_x ; addi.w #$70
            }
        }
        scrollXMove = 0;
        lastCameraX = cameraX;
        initialised = true;
    }

    // ---------------------------------------------------------------------------------------------
    // Per frame: UpdateScroll
    // ---------------------------------------------------------------------------------------------

    @Override
    public void init(int actId, int cameraX, int cameraY) {
        initLevelScroll(cameraX, cameraY);
        lastFrame = Integer.MIN_VALUE;
    }

    @Override
    public void update(int[] horizScrollBuf, int cameraX, int cameraY, int frameCounter, int actId) {
        if (!initialised) initLevelScroll(cameraX, cameraY);
        // One UpdateScroll per frame: the engine may recompute the same frame's output.
        if (frameCounter != lastFrame) {
            lastFrame = frameCounter;
            if (com.openggf.game.soniccd.zones.CdLevelScrollGate.runs()) advance(cameraX, cameraY);   // scroll_lock and the dead player (CdLevelScrollGate)
        }
        publish(horizScrollBuf, cameraX);
    }

    @Override
    public void rebuildOutput(int[] horizScrollBuf, int cameraX, int cameraY, int frameCounter, int actId) {
        if (!initialised) initLevelScroll(cameraX, cameraY);
        publish(horizScrollBuf, cameraX);
    }

    /** scroll_x_move is the per-frame camera delta in 8.8 (CheckScrollFgX, :430-445); the layers add it at their rates. */
    private void advance(int cameraX, int cameraY) {
        int diff = (short) ((cameraX - lastCameraX) << 8);
        lastCameraX = cameraX;
        scrollXMove = diff;
        Rates r = layout.rates();
        bg3X += diff * r.bg3();                               // ext.l ; asl.l #6 (:180-185); R83D adds twice itself
        bg2X += diff * r.bg2();                               // asl.l #7 (:186-190)
        bgX += diff * r.bg();                                 // (d4<<4) + 2*(d4<<4) (:191-199)
        // ScrollBgY: move.w d0,scroll_bg_y writes the long's high word only (:200-202; ScrollBgY in every file).
        bgY = (((cameraY & 0xFFFF) >>> r.bgYShift()) << 16) | (bgY & 0xFFFF);
    }

    // ---------------------------------------------------------------------------------------------
    // Output: deformation words, then the per-line expansion
    // ---------------------------------------------------------------------------------------------

    private void publish(int[] horizScrollBuf, int cameraX) {
        foreground = (short) -cameraX;                        // move.w scroll_fg_x,d0 ; neg.w d0 ; swap d0 (:211-214)
        sinkPos = 0;
        Arrays.fill(deform, (short) 0);
        buildDeformation(cameraX);
        scrollLines();
        resetScrollTracking();
        composer.reset();
        for (int line = 0; line < ScrollEffectComposer.VISIBLE_LINES; line++) {
            composer.writePackedScrollWord(line, foreground, line < sinkPos ? sink[line] : 0);
        }
        composer.copyPackedScrollWordsTo(horizScrollBuf);
        vscrollFactorBG = (short) (bgY >>> 16);               // move.w scroll_bg_y,scroll_y+2 (:203)
        composer.setVscrollFactorBG(vscrollFactorBG);
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }

    private static short word(int fixed) {
        return (short) (fixed >>> 16);
    }

    private short layerWord(Layer layer, int cameraX) {
        return switch (layer) {
            case FG -> (short) cameraX;                       // move.w scroll_fg_x
            case BG -> word(bgX);
            case BG2 -> word(bg2X);
            case BG3 -> word(bg3X);
        };
    }

    private void buildDeformation(int cameraX) {
        int cursor = 0;
        // d3's high word still holds scroll_x_move << 5 from the BG-rate arithmetic (:191-196); its low half is replaced
        // by `move.w base,d3` at every helper entry, and the loops carry the high half on.
        int d3 = ((scrollXMove << 5) >>> 16) << 16;
        for (Op op : layout.ops()) {
            if (op instanceof Fill fill) {
                short value = (short) -layerWord(fill.layer(), cameraX);
                for (int i = 0; i < fill.words() && cursor < DEFORM_WORDS; i++) deform[cursor++] = value;
            } else if (op instanceof Gradient g) {
                d3 = gradient(g, cursor, cameraX, d3);
                cursor += g.words();
            }
        }
    }

    /** One helper call; returns d3 as the loop leaves it. */
    private int gradient(Gradient g, int start, int cameraX, int d3) {
        int[] counts = tables()[g.slot()];
        int d2 = step(g, cameraX);
        d3 = (d3 & 0xFFFF0000) | (layerWord(g.base(), cameraX) & 0xFFFF);   // move.w scroll_bgN_x,d3
        int written = 0;
        for (int d6 = g.top(); d6 >= 0; d6--) {
            short value = (short) -(d3 & 0xFFFF);              // move.w d3,d0 ; neg.w d0
            int count = counts[d6] + 1;                        // moveq #0,d5 ; move.b table(pc,d6.w),d5 ; dbf
            for (int i = 0; i < count; i++) {
                int at = g.descending() ? start + g.words() - 1 - written : start + written;
                if (at >= start && at < start + g.words() && at < DEFORM_WORDS) deform[at] = value;
                written++;
            }
            d3 = Integer.rotateLeft(d3, 16);                   // swap d3 ; add.l d2,d3 ; swap d3
            d3 += d2;
            d3 = Integer.rotateLeft(d3, 16);
        }
        if (written != g.words()) {
            throw new IllegalStateException(payload + ": gradient table covers " + written + " words, the routine spans "
                    + g.words());
        }
        return d3;
    }

    /** sub.w ; ext.l ; asl.l ; divu.w ; ext.l ; asl.l (sub_202B38 :268-277). */
    private int step(Gradient g, int cameraX) {
        int d2 = (short) (layerWord(g.minuend(), cameraX) - layerWord(g.subtrahend(), cameraX));
        d2 <<= g.shiftIn();
        long dividend = d2 & 0xFFFFFFFFL;                      // divu.w: unsigned 32 / 16
        long quotient = dividend / g.divisor();
        if (quotient <= 0xFFFF) d2 = (int) (((dividend % g.divisor()) << 16) | quotient);   // else overflow: d2 unchanged
        d2 = (short) d2;                                       // ext.l d2
        return d2 << g.shiftOut();
    }

    private short deformWord(int index) {
        return index >= 0 && index < DEFORM_WORDS ? deform[index] : 0;
    }

    /** The block loop (:246-265): a partial first block, then 29 whole blocks. */
    private void scrollLines() {
        int bgYw = bgY >>> 16;
        int a2 = (bgYw & 0x3F8) >>> 3;                         // andi.w #$3F8,d0 ; lsr.w #2,d0 (bytes) -> word index
        int skipped = bgYw & 7;
        emitBlock(deformWord(a2++), 8 - skipped);
        for (int i = 0; i < 0x1D; i++) emitBlock(deformWord(a2++), 8);   // moveq #$1C,d1 ; dbf
    }

    private void emitBlock(short word, int lines) {
        for (int i = 0; i < lines && sinkPos < LINE_SINK; i++) sink[sinkPos++] = word;
    }

    // ---------------------------------------------------------------------------------------------
    // Rewind
    // ---------------------------------------------------------------------------------------------

    private record State(int bgX, int bg2X, int bg3X, int bgY, int scrollXMove, int lastCameraX, int lastFrame,
                         boolean initialised) { }

    @Override
    public Object captureRewindState() {
        return new State(bgX, bg2X, bg3X, bgY, scrollXMove, lastCameraX, lastFrame, initialised);
    }

    @Override
    public void restoreRewindState(Object state) {
        if (!(state instanceof State s)) return;
        bgX = s.bgX();
        bg2X = s.bg2X();
        bg3X = s.bg3X();
        bgY = s.bgY();
        scrollXMove = s.scrollXMove();
        lastCameraX = s.lastCameraX();
        lastFrame = s.lastFrame();
        initialised = s.initialised();
    }

    @Override
    public short getVscrollFactorBG() {
        return vscrollFactorBG;
    }

    /** scroll_bg_x long, for tests. */
    public int backgroundCameraX() {
        return bgX;
    }

    /** scroll_bg_y long, for tests. */
    public int backgroundCameraY() {
        return bgY;
    }
}
