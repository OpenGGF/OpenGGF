package com.openggf.game.soniccd.zones.ssz;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.SonicCdGame;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.ScrollOutputRebuilder;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;

/**
 * Stardust Speedway background scroll, all ten stages (act 1 and 2 in four time periods, act 3 Good and Bad Future).
 *
 * <p>Sources (docs/scddisasm pin 1ecc5f3a), directory {@code src/r7/}: act 1 Present assembles
 * {@code scroll_1a.asm}, act 1 Past {@code scroll_1b}, Good Future {@code scroll_1c}, Bad Future {@code scroll_1d},
 * act 2 the same four as {@code scroll_2a..2d}, act 3 {@code scroll_3c} and {@code scroll_3d} (selected in
 * Level/Stardust Speedway/R7 Shared.asm:64-104). Labels below are scroll_1a's; the others carry the same routines at
 * other addresses. Normalised for labels, {@code 2a/2b/2c/2d} differ from {@code 1a/1b/1c/1d} only in the spawn
 * position and in the background-Y factor (the two {@code lsr.w} shifts, scroll_2a.asm:118 and :183 against
 * scroll_1a.asm:118 and :183: #3 for act 2, #1 for act 1). Six routines really differ, all of one shape:
 * <ul>
 * <li>{@code InitScroll} ends by calling {@code sub_2028B4} (scroll_1a.asm:96, 114-133), which seeds the layer
 *     cameras from the start camera; {@code UpdateScroll} (:137-223) advances them by the camera delta
 *     ({@code scroll_x_move}) each level frame (:161-185), then builds {@code bg_scroll_lines} as a run of words
 *     (:194-223) and expands it to one long per line in {@code loc_202AC6} (:384-405). The multipliers of the deltas, the seeding
 *     expressions and the background-Y rule differ per family (the {@link Layout} below cites each).</li>
 * <li>The words are built by up to three <i>curve</i> routines ({@code sub_202A08}, {@code sub_202A4C},
 *     {@code sub_202A8A}: :283-312, 316-343, 353-380) and flat runs of {@code -scroll_bgN_x}. A curve steps a value from one layer's
 *     X towards another's (the camera for the first) by {@code ((target - base) << a) / divisor << b} per segment,
 *     the segment lengths coming from a byte table indexed by {@code d6}; the first curve writes downward
 *     ({@code adda.w #N,a1 ; move.w d0,-(a1)}), the others upward.</li>
 * <li>Past (R71B, R72B; scroll_1b.asm:229, 356-404) also hands the bottom of the plane to a sine wobble: once a
 *     block's bottom edge reaches background Y $268 every remaining line gets
 *     {@code -(((sin(angle) lsr.w 5) + scroll_bg3_x))}, the angle starting at the high byte of
 *     {@code bg_water_deform} (a word that gains $80 per frame) and growing by 2 per line.</li>
 * </ul>
 *
 * <p>Shipped {@code FixBugs = 0}, all kept: (1) the seeding routine's {@code lsr.w #1,d2} shifts only the low word
 * of a long, so the initial background Y is twice, not 1.5 or 1.125 times, a quarter of the camera
 * (scroll_1a.asm:115-120); the first {@code UpdateScroll} overwrites it. (2) {@code lsr.w #5} is a logical shift, so a
 * negative sine gives about +2040 instead of -8 (scroll_1b.asm:398); the value is modulo the plane width on hardware.
 * (3) The curve's fraction word is the high word of {@code d3}, which the curves inherit from the earlier
 * {@code ScrollBgX} code ({@code scroll_x_move << 5}) and then from each other; it moves segment boundaries by a pixel
 * and is modelled. (4) {@code divu.w} overflow leaves the dividend unchanged. (5) Words of {@code bg_scroll_lines}
 * that no routine wrote are cold RAM, read here as zero. The routine does not run while {@code scroll_lock} is set
 * (scroll_1a.asm:138-141) or the player is dead ({@link com.openggf.game.soniccd.zones.CdLevelScrollGate}).
 * {@code StageEvents}, which runs
 * first (:146-151), is the events lane's.
 */
public final class SszScrollHandler extends AbstractZoneScrollHandler implements ScrollOutputRebuilder {
    /** The values a curve reads: the camera, or one of the three background layer cameras. */
    enum Source { FG, BG, BG2, BG3 }

    /** One run of words in {@code bg_scroll_lines}. */
    sealed interface Op permits Curve, Flat { }

    /** {@code move.w scroll_bgN_x,d0 ; neg.w d0 ; moveq #words-1,d6 ; move.w d0,(a1)+ ; dbf}. */
    record Flat(Source layer, int words) implements Op { }

    /**
     * A curve routine. {@code counts} is the byte table indexed by {@code d6} (the routine starts at
     * {@code counts.length - 1}), {@code backwardWords} the {@code adda.w #2*n,a1} of the downward writer (0 when it
     * writes upward).
     */
    record Curve(Source base, Source target, boolean doubleTarget, int preShift, int divisor, int postShift,
                 boolean preStep, int backwardWords, int[] counts) implements Op {
        Curve {
            if (backwardWords != 0 && backwardWords != words(counts)) {
                throw new IllegalArgumentException("curve writes " + words(counts) + " words, not " + backwardWords);
            }
        }

        int words() {
            return words(counts);
        }

        private static int words(int[] counts) {
            int total = 0;
            for (int n : counts) total += n + 1;
            return total;
        }
    }

    /** Background-Y rule: {@code scaled} = {@code d0 = (y>>2) + ((y>>2)>>shift)} (words), else {@code d0 = y}. */
    record BackgroundY(boolean scaled, int shift) { }

    /** Everything that differs between the stage families. */
    record Layout(IntUnaryOperator seedBg2, IntUnaryOperator seedBg3, IntUnaryOperator seedBg,
                  IntUnaryOperator deltaBg3, IntUnaryOperator deltaBg2, IntUnaryOperator deltaBg,
                  BackgroundY backgroundY, Op[] ops, boolean wobble) { }

    // ---- byte tables (one byte per segment: words in the segment minus one), indexed by d6 ----
    private static int[] ones(int length, int leadingOnes) {
        int[] table = new int[length];
        Arrays.fill(table, 0, leadingOnes, 1);
        return table;
    }

    // scroll_1a.asm:227-264 (byte_2029D6, d6 from $24; used :302), scroll_1b.asm:235-272 (byte_2029B2, d6 from $24; :306)
    private static final int[] SEGMENTS_44 = ones(0x25, 7);
    // scroll_1c.asm:232-275 (byte_202ADE, d6 from $26; used :295), scroll_3c.asm:212-255 (byte_202ACC, d6 from $26; :276)
    private static final int[] SEGMENTS_46 = ones(0x27, 7);
    // scroll_1d.asm:239-256 (byte_202A6A, d6 from $F; used :292), scroll_3d.asm:231-250 (byte_202A70, d6 from $F; :271)
    private static final int[] SEGMENTS_38 = {3, 3, 1, 1, 0, 0, 3, 3, 1, 3, 1, 1, 1, 1, 0, 0};
    // scroll_1a.asm:267-282 (byte_2029FC, d6 from $B; used :334)
    private static final int[] SEGMENTS_A2 = ones(0xC, 2);
    // scroll_1a.asm:347-352 (byte_202A88, d6 from 1; used :371)
    private static final int[] SEGMENTS_A3 = {9, 7};
    // scroll_1b.asm:275-286 (byte_2029D8, d6 from 7; used :338): eight one-word segments
    private static final int[] SEGMENTS_B2 = new int[8];
    // scroll_1c.asm:343-356 (byte_202B8C, d6 from 9; used :330), scroll_3c.asm:324-337 (byte_202B7C, d6 from 9; :311)
    private static final int[] SEGMENTS_C2 = {7, 3, 3, 5, 3, 3, 0, 0, 0, 0};
    // scroll_1d.asm:257-272 (byte_202A7A, d6 from $A; used :324), scroll_3d.asm:319-334 (byte_202B08, d6 from $A; :306)
    private static final int[] SEGMENTS_D2 = ones(0xB, 5);

    // seeding: sub_2028B4 (scddisasm/src/r7/scroll_1a.asm:114-133), d1 = start camera X (high word zero)
    private static final IntUnaryOperator SEED_BG_A = x -> 3 * (x >>> 4);                     // :128-132 after two halvings, then >>2
    private static final IntUnaryOperator SEED_BG2_HALF = x -> x >>> 1;                      // :124-125
    private static final IntUnaryOperator SEED_BG3_A = x -> x >>> 2;                         // :126-127
    private static final IntUnaryOperator SEED_BG3_PAST = x -> (x >>> 2) + (x >>> 3);        // scroll_1b.asm:126-130 (scroll_1c.asm:126-130, scroll_1d.asm:126-130)

    // per-frame deltas (UpdateScroll, scroll_1a.asm:161-179): d4 as 32-bit
    private static final IntUnaryOperator DELTA_BG = xm -> 3 * (xm << 4);                    // :172-179
    private static final IntUnaryOperator DELTA_BG2_A = xm -> xm << 7;                       // :166-170
    private static final IntUnaryOperator DELTA_BG3_A = xm -> xm << 6;                       // :161-165
    private static final IntUnaryOperator DELTA_BG3_PAST = xm -> {                           // scroll_1b.asm:164-171 (the same in scroll_1c.asm and scroll_1d.asm)
        int d4 = xm << 6;
        return d4 + (d4 >> 1);
    };

    private static final Op[] OPS_A = {                                                       // scroll_1a.asm:194-223, 283-380
            new Curve(Source.BG, Source.FG, false, 7, 0x25, 9, false, 0x58 / 2, SEGMENTS_44),
            new Flat(Source.BG, 4),
            new Curve(Source.BG, Source.BG3, false, 7, 0xC, 9, false, 0, SEGMENTS_A2),
            new Flat(Source.BG3, 0x10),
            new Curve(Source.BG2, Source.FG, false, 7, 8, 9, false, 0, SEGMENTS_A3)};
    private static final Op[] OPS_B = {                                                       // scroll_1b.asm:200-231, 287-347
            new Curve(Source.BG, Source.FG, false, 7, 0x25, 9, false, 0x58 / 2, SEGMENTS_44),
            new Flat(Source.BG, 4),
            new Curve(Source.BG, Source.BG3, false, 7, 8, 9, false, 0, SEGMENTS_B2),
            new Flat(Source.BG3, 0x14)};
    private static final Op[] OPS_C = {                                                       // scroll_1c.asm:200-228, 276-339
            new Curve(Source.BG, Source.FG, false, 6, 0x27, 0xA, false, 0x5C / 2, SEGMENTS_46),
            new Flat(Source.BG, 4),
            new Curve(Source.BG, Source.BG3, false, 6, 0xA, 0xA, true, 0, SEGMENTS_C2),
            new Flat(Source.BG2, 0xC)};
    private static final Op[] OPS_D = {                                                       // scroll_1d.asm:200-235, 273-333
            new Curve(Source.BG, Source.FG, false, 6, 0x10, 0xA, false, 0x4C / 2, SEGMENTS_38),
            new Flat(Source.BG, 8),
            new Curve(Source.BG, Source.BG3, false, 6, 0xB, 0xA, false, 0, SEGMENTS_D2),
            new Flat(Source.BG3, 0x10),
            new Flat(Source.BG2, 0x12)};
    private static final Op[] OPS_3C = {                                                      // scroll_3c.asm:180-208, 256-320
            new Curve(Source.BG, Source.FG, true, 6, 0x27, 0xA, false, 0x5C / 2, SEGMENTS_46),
            new Flat(Source.BG, 4),
            new Curve(Source.BG, Source.BG2, false, 4, 0xA, 0xC, true, 0, SEGMENTS_C2),
            new Flat(Source.BG2, 0xC)};
    private static final Op[] OPS_3D = {                                                      // scroll_3d.asm:192-227, 251-315
            new Curve(Source.BG, Source.FG, true, 5, 0x10, 0xB, false, 0x4C / 2, SEGMENTS_38),
            new Flat(Source.BG, 8),
            new Curve(Source.BG, Source.BG3, false, 5, 0xB, 0xB, true, 0, SEGMENTS_D2),
            new Flat(Source.BG3, 0x10),
            new Flat(Source.BG2, 0x12)};

    static Layout layoutFor(CdStagePayload payload) {
        int shift = payload.stage().actNumber() == 1 ? 1 : 3;          // lsr.w #1 (act 1) / #3 (act 2), scroll_{1,2}a.asm:118, 183
        BackgroundY scaled = new BackgroundY(true, shift);
        return switch (payload) {
            case R71A, R72A -> new Layout(SEED_BG2_HALF, SEED_BG3_A, SEED_BG_A,
                    DELTA_BG3_A, DELTA_BG2_A, DELTA_BG, scaled, OPS_A, false);
            case R71B, R72B -> new Layout(SEED_BG2_HALF, SEED_BG3_PAST, SEED_BG_A,
                    DELTA_BG3_PAST, DELTA_BG2_A, DELTA_BG, scaled, OPS_B, true);
            case R71C, R72C -> new Layout(SEED_BG2_HALF, SEED_BG3_PAST, SEED_BG_A,
                    DELTA_BG3_PAST, DELTA_BG2_A, DELTA_BG, scaled, OPS_C, false);
            case R71D, R72D -> new Layout(SEED_BG2_HALF, SEED_BG3_PAST, SEED_BG_A,
                    DELTA_BG3_PAST, DELTA_BG2_A, DELTA_BG, scaled, OPS_D, false);
            case R73C -> new Layout(                                                         // scroll_3c.asm:103-119, 148-171
                    x -> (x >>> 1) + (x >>> 2), x -> x >>> 1, x -> 3 * (x >>> 4),
                    xm -> xm << 7, xm -> 3 * (xm << 7), DELTA_BG, new BackgroundY(false, 0), OPS_3C, false);
            case R73D -> new Layout(                                                         // scroll_3d.asm:103-125, 154-183
                    x -> (x >>> 2) + (x >>> 4),
                    x -> {
                        int d1 = x >>> 3;
                        return (((d1 >>> 1) + d1) >>> 1) + d1;
                    },
                    x -> 3 * (x >>> 4),
                    xm -> 7 * (xm << 5), xm -> 5 * (xm << 6), DELTA_BG, new BackgroundY(false, 0), OPS_3D, false);
            default -> throw new IllegalArgumentException(payload + " is not a Stardust Speedway stage");
        };
    }

    private static final int DEFORM_WORDS = 0x200 / 2;       // bg_scroll_lines / deformBuffer rs.b $200
    private static final int LINE_SINK = 512;                // spare room past the 240 lines a frame can write
    private static final int SINE_WORDS = 320;               // SineTable: 256 words plus 64 for cosine (common/math.asm:14-54)
    private static final int WOBBLE_FROM = 0x268;            // cmpi.w #$268,d4 (scroll_1b.asm:367)

    private final CdStagePayload payload;
    private final Layout layout;
    private int[] sine;                                       // signed words, read from the stage file
    private final ScrollEffectComposer composer = new ScrollEffectComposer();

    // Native RAM this routine owns: scroll_bg_x/bg2_x/bg3_x/bg_y are 16.16 longs.
    private int bgX;
    private int bg2X;
    private int bg3X;
    private int bgY;
    private int scrollXMove;                                  // scroll_x_move, 8.8 camera delta
    private int waterDeform;                                  // bg_water_deform word, +$80 per UpdateScroll (Past)
    private final short[] deform = new short[DEFORM_WORDS];
    private final short[] sink = new short[LINE_SINK];
    private int sinkPos;
    private int lastCameraX;
    private int lastFrame = Integer.MIN_VALUE;
    private boolean initialised;
    private short foreground;

    public SszScrollHandler(CdStagePayload payload) {
        this(payload, null);
    }

    /** {@code sineTable} is the 320-word SineTable; {@code null} reads it from the loaded stage on first use. */
    public SszScrollHandler(CdStagePayload payload, int[] sineTable) {
        this.payload = payload;
        this.layout = layoutFor(payload);
        this.sine = sineTable;
    }

    private int[] sine() {
        if (sine == null) {
            var game = GameServices.level().getGame();
            CdStageImage image = game instanceof SonicCdGame cd ? cd.currentImage() : null;
            if (image == null) throw new IllegalStateException("The Stardust Speedway wobble needs the loaded stage file");
            sine = readSine(payload, image);
        }
        return sine;
    }

    /** Reads SineTable from the stage file at the offset the generator found. */
    static int[] readSine(CdStagePayload payload, CdStageImage image) {
        var rows = SszCatalogue.rows(payload, "sine");
        int[] words = new int[SINE_WORDS];
        int at = rows.isEmpty() ? -1 : rows.get(0).a();
        for (int i = 0; at >= 0 && i < SINE_WORDS; i++) words[i] = (short) image.u16(at + 2 * i);
        return words;
    }

    // ---------------------------------------------------------------------------------------------
    // Level start: InitScroll's seeding routine (sub_2028B4, scroll_1a.asm:114-134)
    // ---------------------------------------------------------------------------------------------

    void initLevelScroll(int cameraX, int cameraY) {
        int x = cameraX & 0xFFFF;
        int y = cameraY & 0xFFFF;
        Arrays.fill(deform, (short) 0);
        int d0 = y << 16;                                     // swap d0
        if (layout.backgroundY().scaled()) {
            d0 >>>= 2;                                        // lsr.l #2,d0 (:116)
            int d2 = (d0 & 0xFFFF0000) | ((d0 & 0xFFFF) >>> layout.backgroundY().shift());   // lsr.w #n,d2 ; add.l d2,d0 (:117-119)
            d0 += d2;
        }
        bgY = d0;                                             // move.l d0,scroll_bg_y
        bg2X = (layout.seedBg2().applyAsInt(x) & 0xFFFF) << 16;   // move.w into the long's high word
        bg3X = (layout.seedBg3().applyAsInt(x) & 0xFFFF) << 16;
        bgX = (layout.seedBg().applyAsInt(x) & 0xFFFF) << 16;
        scrollXMove = 0;
        waterDeform = 0;                                      // cleared with the variables
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

    /** scroll_x_move is the per-frame camera delta in 8.8 (scroll_1a.asm:161-179). */
    private void advance(int cameraX, int cameraY) {
        int diff = (short) ((cameraX - lastCameraX) << 8);
        lastCameraX = cameraX;
        scrollXMove = diff;
        bg3X += layout.deltaBg3().applyAsInt(diff);           // ScrollBg3X
        bg2X += layout.deltaBg2().applyAsInt(diff);           // ScrollBg2X
        bgX += layout.deltaBg().applyAsInt(diff);             // ScrollBgX
        int d0 = cameraY & 0xFFFF;
        if (layout.backgroundY().scaled()) {
            d0 >>>= 2;                                        // lsr.w #2,d0 (:181)
            d0 = (d0 + (d0 >>> layout.backgroundY().shift())) & 0xFFFF;   // lsr.w #n,d1 ; add.w d1,d0 (:182-184)
        }
        bgY = (d0 << 16) | (bgY & 0xFFFF);                    // ScrollBgY: move.w d0,scroll_bg_y writes the high word
        waterDeform = (waterDeform + 0x80) & 0xFFFF;          // addi.w #$80,bg_water_deform (scroll_1b.asm:229)
    }

    // ---------------------------------------------------------------------------------------------
    // Output: deformation words, then the per-line expansion
    // ---------------------------------------------------------------------------------------------

    private void publish(int[] horizScrollBuf, int cameraX) {
        foreground = (short) -cameraX;                        // move.w scroll_fg_x,d0 ; neg.w d0 ; swap d0 (:195-197)
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
        vscrollFactorBG = (short) (bgY >>> 16);               // move.w scroll_bg_y,scroll_y+2 (:186)
        composer.setVscrollFactorBG(vscrollFactorBG);
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }

    private static short word(int fixed) {
        return (short) (fixed >>> 16);
    }

    private short layerWord(Source source, int cameraX) {
        return switch (source) {
            case FG -> (short) cameraX;
            case BG -> word(bgX);
            case BG2 -> word(bg2X);
            case BG3 -> word(bg3X);
        };
    }

    private void buildDeformation(int cameraX) {
        int cursor = 0;
        // d3's high word entering the first curve: ScrollBgX leaves d3 = (scroll_x_move << 4) * 2 with only its low
        // byte, then its low word, replaced (scroll_1a.asm:174-179, ScrollBgX :792, ScrollBgY :761).
        int fraction = (scrollXMove << 5) >>> 16;
        for (Op op : layout.ops()) {
            if (op instanceof Flat flat) {
                short value = (short) -layerWord(flat.layer(), cameraX);   // move.w scroll_bgN_x,d0 ; neg.w d0
                for (int i = 0; i < flat.words() && cursor < DEFORM_WORDS; i++) deform[cursor++] = value;
            } else if (op instanceof Curve curve) {
                fraction = curve(curve, cursor, cameraX, fraction);
                cursor += curve.words();
            }
        }
    }

    /**
     * One curve routine (scroll_1a.asm:283-380 for the three of Present; the other files' curves are at the lines the {@code OPS_} comments give). Returns the fraction word left in
     * {@code d3}'s high half, which the next curve inherits.
     */
    private int curve(Curve c, int cursor, int cameraX, int fraction) {
        short base = layerWord(c.base(), cameraX);                       // move.w scroll_bg_x,d0
        int target = layerWord(c.target(), cameraX);                     // move.w scroll_fg_x,d2
        if (c.doubleTarget()) target = (short) (target + target);        // add.w d2,d2 (scroll_3c.asm:259)
        int d2 = (short) (target - base);                                // sub.w d0,d2 ; ext.l d2
        d2 <<= c.preShift();                                             // moveq #n,d1 ; asl.l d1,d2
        long dividend = d2 & 0xFFFFFFFFL;                                // divu.w #div,d2: unsigned 32 / 16
        long quotient = dividend / c.divisor();
        if (quotient <= 0xFFFF) d2 = (int) (((dividend % c.divisor()) << 16) | quotient);   // else overflow: d2 unchanged
        d2 = (short) d2;                                                 // ext.l d2
        d2 <<= c.postShift();
        int d3 = (fraction << 16) | (base & 0xFFFF);                     // move.w scroll_bg_x,d3 keeps the high word
        if (c.preStep()) {                                               // swap d3 ; add.l d2,d3 ; swap d3 (scroll_1c.asm:320-323)
            d3 = Integer.rotateLeft(d3, 16);
            d3 += d2;
            d3 = Integer.rotateLeft(d3, 16);
        }
        short[] written = new short[c.words()];
        int at = 0;
        for (int d6 = c.counts().length - 1; d6 >= 0; d6--) {
            short value = (short) -(d3 & 0xFFFF);                        // move.w d3,d0 ; neg.w d0
            int count = c.counts()[d6] + 1;                              // moveq #0,d5 ; move.b table(pc,d6.w),d5
            for (int i = 0; i < count; i++) written[at++] = value;
            d3 = Integer.rotateLeft(d3, 16);                             // swap d3 ; add.l d2,d3 ; swap d3
            d3 += d2;
            d3 = Integer.rotateLeft(d3, 16);
        }
        for (int i = 0; i < written.length; i++) {
            // The downward writer (move.w d0,-(a1) from a1 + 2N) fills the span from its top.
            int index = c.backwardWords() != 0 ? cursor + written.length - 1 - i : cursor + i;
            if (index < DEFORM_WORDS) deform[index] = written[i];
        }
        return d3 >>> 16;
    }

    private short deformWord(int index) {
        return index >= 0 && index < DEFORM_WORDS ? deform[index] : 0;
    }

    /** loc_202AC6 and its block loop (scroll_1a.asm:384-405; scroll_1b.asm:356-382 with the wobble exit at :367). */
    private void scrollLines() {
        int bgYw = bgY >>> 16;
        int a2 = (bgYw & 0x3F8) >>> 3;                         // andi.w #$3F8,d0 ; lsr.w #2,d0 (bytes) -> word index
        int skipped = bgYw & 7;
        int d4 = (bgYw + 8 - skipped) & 0xFFFF;                // scroll_1b.asm:200-228 (d4 = scroll_bg_y), :357-359 (only the wobble reads it)
        emitBlock(deformWord(a2++), 8 - skipped);              // partial first block (jump into the unrolled move.l run)
        d4 = (d4 + 8) & 0xFFFF;
        for (int block = 1; block <= 0x1D; block++) {          // moveq #$1D,d1 ; dbf
            if (layout.wobble() && d4 >= WOBBLE_FROM) {        // cmpi.w #$268,d4 ; bcc.s (scroll_1b.asm:367)
                wobble((0x1D - block + 1) * 8);                // addq.w #1,d1 ; lsl.w #3,d1 (scroll_1b.asm:387-388): the rest of the blocks
                return;
            }
            emitBlock(deformWord(a2++), 8);
            d4 = (d4 + 8) & 0xFFFF;
        }
    }

    /** scroll_1b.asm:386-404: one line at a time from the sine table, the angle starting at bg_water_deform's high byte. */
    private void wobble(int lines) {
        int[] table = sine();
        int angle = (waterDeform >>> 8) & 0xFF;                // move.b bg_water_deform,d2: the word's first (high) byte
        short bg3 = word(bg3X);
        for (int i = 0; i < lines; i++) {
            int sin = table[angle & 0xFF];                     // SineCosine: andi.w #$FF,d0 ; word at 2*angle
            int shifted = (sin & 0xFFFF) >>> 5;                // lsr.w #5,d0: logical, so negative sines become ~+2040
            short value = (short) -(short) (shifted + bg3);    // add.w scroll_bg3_x,d0 ; neg.w d0
            if (sinkPos < LINE_SINK) sink[sinkPos++] = value;
            angle = (angle + 2) & 0xFF;                        // addq.w #2,d2
        }
    }

    private void emitBlock(short word, int lines) {
        for (int i = 0; i < lines && sinkPos < LINE_SINK; i++) sink[sinkPos++] = word;
    }

    // ---------------------------------------------------------------------------------------------
    // Rewind
    // ---------------------------------------------------------------------------------------------

    private record State(int bgX, int bg2X, int bg3X, int bgY, int scrollXMove, int waterDeform, int lastCameraX,
                         int lastFrame, boolean initialised) { }

    @Override
    public Object captureRewindState() {
        return new State(bgX, bg2X, bg3X, bgY, scrollXMove, waterDeform, lastCameraX, lastFrame, initialised);
    }

    @Override
    public void restoreRewindState(Object state) {
        if (!(state instanceof State s)) return;
        bgX = s.bgX();
        bg2X = s.bg2X();
        bg3X = s.bg3X();
        bgY = s.bgY();
        scrollXMove = s.scrollXMove();
        waterDeform = s.waterDeform();
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
