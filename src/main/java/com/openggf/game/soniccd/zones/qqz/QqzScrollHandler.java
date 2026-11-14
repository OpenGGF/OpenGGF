package com.openggf.game.soniccd.zones.qqz;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.ScrollOutputRebuilder;
import com.openggf.level.scroll.compose.ScrollEffectComposer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Quartz Quadrant background scroll, all ten stages (R51A-R53D).
 *
 * <p>Sources, all in docs/scddisasm (pin 1ecc5f3a) {@code src/r5/}: {@code scroll_1a..3d.asm} and
 * {@code background_swap_1a..2d.asm}. Act 2's scroll bodies equal act 1's except the start position
 * (the BG parts of scroll_1b/2b, 1c/2c and 1d/2d are identical after label normalisation) and one
 * constant in the Present stages, so seven programs cover ten stages:
 * <ul>
 *   <li>{@link Program#A_ACT1} / {@link Program#A_ACT2}: Present (scroll_1a, scroll_2a). Differ only in
 *       the per-frame background Y: {@code y/2 + y/8 + y/32} (scroll_1a:261-267) against
 *       {@code y/2 + y/8 + y/16} (scroll_2a:273-279).</li>
 *   <li>{@link Program#B}: Past (scroll_1b, scroll_2b).</li>
 *   <li>{@link Program#C}: Good Future acts 1 and 2 (scroll_1c, scroll_2c); {@link Program#C_ACT3}:
 *       act 3 Good Future (scroll_3c) is C with the r5_bg_change=1 line plan only.</li>
 *   <li>{@link Program#D}: Bad Future acts 1 and 2 (scroll_1d, scroll_2d); {@link Program#D_ACT3}:
 *       act 3 Bad Future (scroll_3d) is D's r5_bg_change=0 plan only, with the ramp's two shifts changed
 *       from (6,10) to (7,9).</li>
 * </ul>
 * All of them keep three background cameras as 16.16 longs ({@code scroll_bg_x/bg2_x/bg3_x}) advanced by
 * the foreground's per-frame movement times a fixed rate, a background Y that is a fixed fraction of the
 * foreground Y, and a deformation buffer of one word per 8 background lines that is built from fills
 * and "ramps" (a parallax gradient between two cameras) and expanded to per-line scroll longs.
 *
 * <p>{@code r5_bg_change} (0, 1 or 2) counts how many of the thresholds {@code $E00} and {@code $2300}
 * the player's X has passed (background_swap_*:5-26, 56-65). Its parity selects the line plan, the
 * background Y formula and the palette slots. Crossing a threshold also reloads the background graphics,
 * palette and map (background_swap:28-48), which is the level loader's job: this handler re-runs
 * InitBgScroll (background_swap:50-52) and reports the new state to {@link #setSwapListener}.
 *
 * <p>Shipped {@code FixBugs = 0}. Kept as shipped: the ramp's {@code divu.w} overflows leave the dividend
 * in place (modelled in {@link #ramp}); the ramp starts from the upper word of d3 left by earlier code, a
 * fraction of up to one pixel (modelled for A, B, C, where UpdateScroll loads d3 itself; unknown for D and
 * D_ACT3, where it is whatever the last full-long write left, modelled as the value ScrollFgY leaves when
 * the camera moved vertically, else zero); table words read beyond what the plan wrote return the older
 * contents of {@code deformBuffer}. Not modelled: the vertical wrap in ScrollFgY ({@code andi.w #$3FF,
 * scroll_bg_y}, scroll_1a:733-734, 757-758) belongs to the camera. While {@code scroll_lock} is set
 * (scroll_1a:212-214) or the player is dead the routine does not run
 * ({@link com.openggf.game.soniccd.zones.CdLevelScrollGate}).
 */
public final class QqzScrollHandler extends AbstractZoneScrollHandler implements ScrollOutputRebuilder {
    /** The seven scroll programs; see the class comment. */
    enum Program {
        A_ACT1, A_ACT2, B, C, D, C_ACT3, D_ACT3;

        static Program of(CdStagePayload payload) {
            var stage = payload.stage();
            int act = stage.actNumber();
            CdPeriod period = stage.period();
            if (act == 3) return period == CdPeriod.GOOD_FUTURE ? C_ACT3 : D_ACT3;
            return switch (period) {
                case PRESENT -> act == 1 ? A_ACT1 : A_ACT2;
                case PAST -> B;
                case GOOD_FUTURE -> C;
                case BAD_FUTURE -> D;
            };
        }

        boolean hasSwap() { return this != C_ACT3 && this != D_ACT3; }
        int tableMask() { return this == A_ACT1 || this == A_ACT2 ? 0x3F8 : 0x1F8; }   // scroll_1a:314, others :302/:266
    }

    private enum Cam { BG, BG2, BG3, FG }

    /** One piece of the deformation buffer: {@code words} copies of a camera, or a parallax ramp. */
    private record Step(Cam source, int words, Cam reference, int shiftIn, int divisor, int shiftOut,
                        boolean backward, int[] counts) {
        static Step fill(Cam source, int words) { return new Step(source, words, null, 0, 0, 0, false, null); }
        static Step ramp(Cam source, Cam reference, int shiftIn, int divisor, int shiftOut, boolean backward,
                         int... counts) {
            return new Step(source, 0, reference, shiftIn, divisor, shiftOut, backward, counts);
        }
        boolean isFill() { return counts == null; }
    }

    // Line plans, in the order each routine writes them. Counts are word counts per iteration (the
    // dbf byte plus one), in iteration order (d6 from its start value down to 0).
    // scroll_1a:291-307 (r5_bg_change even): 8 words, sub_202C64 (:322-351), sub_202CAE (:365-392), 8 words.
    private static final Step[] A_EVEN = {
            Step.fill(Cam.BG, 8),
            Step.ramp(Cam.BG3, Cam.FG, 6, 0xC, 10, true, 2, 2, 2, 2, 12, 14),
            Step.ramp(Cam.BG3, Cam.FG, 6, 0xE, 10, false, 2, 2, 2, 2, 2, 20, 16),
            Step.fill(Cam.BG, 8)};
    // scroll_1a:285-286 (odd): sub_202CF2 (:408-437), sub_202D42 (:457-484).
    private static final Step[] A_ODD = {
            Step.ramp(Cam.BG3, Cam.FG, 7, 0x16, 9, true, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2),
            Step.ramp(Cam.BG3, Cam.FG, 6, 0xC, 10, false, 6, 4, 32)};
    // scroll_1b:294-295 (even): sub_202BD4 (:310-339), sub_202C1C (:351-378).
    private static final Step[] B_EVEN = {
            Step.ramp(Cam.BG3, Cam.FG, 6, 8, 10, true, 6, 2, 4, 8),
            Step.ramp(Cam.BG2, Cam.FG, 6, 0xC, 10, false, 7, 1, 1, 2, 3, 2, 2, 2, 2, 2, 2, 2)};
    // scroll_1b:280-289 (odd): sub_202C64 (:398-427), sub_202CAC (:439-466, reference is scroll_bg_x), 18 words.
    private static final Step[] B_ODD = {
            Step.ramp(Cam.BG3, Cam.FG, 6, 8, 10, true, 5, 2, 2, 4),
            Step.ramp(Cam.BG2, Cam.BG, 6, 0xE, 10, false, 3, 2, 2, 2, 3, 1, 3, 1, 3, 1),
            Step.fill(Cam.BG, 18)};
    // scroll_1c:279-307 (even): 10, 22, 4 and 12 words.
    private static final Step[] C_EVEN = {
            Step.fill(Cam.BG, 10), Step.fill(Cam.BG3, 22), Step.fill(Cam.BG2, 4), Step.fill(Cam.BG, 12)};
    // scroll_1c:267-275 (odd) and scroll_3c:254-261: 14 words, sub_202BE0 (scroll_1c:322-349).
    private static final Step[] C_ODD = {
            Step.fill(Cam.BG3, 14),
            Step.ramp(Cam.BG2, Cam.FG, 7, 0xC, 9, false, 10, 4, 40)};
    // scroll_1d:285-299 (even): sub_202C92 (:366-395), 12 and 4 words; then the gradient tail (:327-353).
    private static final Step[] D_EVEN = {
            Step.ramp(Cam.BG, Cam.FG, 6, 6, 10, true, 6, 12, 6), Step.fill(Cam.BG, 12), Step.fill(Cam.BG, 4)};
    // scroll_3d:258-272 uses sub_202AFA (:337-365) whose shifts are (7,9).
    private static final Step[] D3_EVEN = {
            Step.ramp(Cam.BG, Cam.FG, 7, 6, 9, true, 6, 12, 6), Step.fill(Cam.BG, 12), Step.fill(Cam.BG, 4)};
    // scroll_1d:272-280 (odd): 11 words, sub_202CD6 (:399-426).
    private static final Step[] D_ODD = {
            Step.fill(Cam.BG2, 11),
            Step.ramp(Cam.BG2, Cam.FG, 7, 0xC, 9, false, 1, 4, 4, 6, 8, 14)};

    private static final int DEFORM_WORDS = 0x200 / 2;       // deformBuffer rs.b $200 (_Definitions.inc:121)
    private static final int LINE_CAP = 512;                 // hscroll is $400 bytes of longs; spare room
    private static final int FIRST_THRESHOLD = 0xE00;        // background_swap_1a:59-60
    private static final int SECOND_THRESHOLD = 0x2300;

    private final Program program;
    private final IntSupplier playerCentreX;
    private final ScrollEffectComposer composer = new ScrollEffectComposer();

    // Native RAM this routine owns: scroll_bg_x/bg2_x/bg3_x/bg_y are 16.16 longs (_Compatibility :130-157).
    private int bgX;
    private int bg2X;
    private int bg3X;
    private int bgY;
    private int fgX;
    private int fgY;
    private int xMove;                    // scroll_x_move, 8.8 pixels
    private int yDelta;                   // foreground Y change of the latest frame
    private int crossX = 0x10;            // scroll_cross_x (scroll_1a:34)
    private int crossY = 0x10;            // scroll_cross_y
    private boolean foregroundFlags;      // scroll_flags_fg low byte
    private int bgChange;                 // r5_bg_change
    private final short[] deform = new short[DEFORM_WORDS];
    private final short[] lines = new short[LINE_CAP];
    private int deformPos;
    private int linePos;
    private int fraction;                 // the upper word of d3 between ramps
    private int lastFrame = Integer.MIN_VALUE;
    private boolean initialised;
    private boolean bossFlags;
    private boolean conveyorReverse;
    private IntConsumer swapListener = state -> { };

    public QqzScrollHandler(CdStagePayload payload) {
        this(Program.of(payload), focusedSpriteCentreX());
    }

    QqzScrollHandler(Program program, IntSupplier playerCentreX) {
        this.program = program;
        this.playerCentreX = playerCentreX;
    }

    private static IntSupplier focusedSpriteCentreX() {
        return () -> {
            Camera camera = GameServices.camera();
            AbstractPlayableSprite sprite = camera == null ? null : camera.getFocusedSprite();
            return sprite == null ? 0 : sprite.getCentreX() & 0xFFFF;
        };
    }

    // ---------------------------------------------------------------------------------------------
    // State shared with the palette cycle and the other lanes
    // ---------------------------------------------------------------------------------------------

    /** {@code r5_bg_change}: how many swap thresholds the player has passed (0-2). */
    public int backgroundChange() { return bgChange; }

    /** {@code boss_flags}: stops palette slot 0 (palette_cycle_c.asm:4-6, _d.asm:6-10). Set by the boss lane. */
    public boolean bossFlags() { return bossFlags; }
    public void setBossFlags(boolean value) { bossFlags = value; }

    /** {@code r5_conveyor_reverse}: selects the reverse palette table (palette_cycle_a.asm:21-24). Set by the conveyor lane. */
    public boolean conveyorReverse() { return conveyorReverse; }
    public void setConveyorReverse(boolean value) { conveyorReverse = value; }

    /**
     * Called with the new {@code r5_bg_change} when a swap threshold is crossed. The level loader must then
     * load graphics list ${@code $E + (id >> 2)}, palette {@code 4 + (id >> 2)} and the background map layer
     * (background_swap_1a.asm:28-48); this handler cannot.
     */
    public void setSwapListener(IntConsumer listener) { swapListener = listener == null ? state -> { } : listener; }

    /** cameraBgX word, cameraBg2X, cameraBg3X and cameraBgY (16.16), for tests. */
    public int backgroundCameraX() { return bgX; }
    public int backgroundCamera2X() { return bg2X; }
    public int backgroundCamera3X() { return bg3X; }
    public int backgroundCameraY() { return bgY; }

    // ---------------------------------------------------------------------------------------------
    // Level start: InitScroll's InitBgScroll, and the swap's re-init
    // ---------------------------------------------------------------------------------------------

    /** InitBgScroll(d0 = foreground Y, d1 = foreground X): scroll_1a:176-206, scroll_1b:187-209, scroll_1c:187-203, scroll_1d:188-206. */
    void initBgScroll(int y, int x) {
        int d0 = (y & 0xFFFF) << 16;                          // swap d0 of a zero-extended word
        int d1 = x & 0xFFFF;
        int d2;
        switch (program) {
            case A_ACT1, A_ACT2 -> {
                if ((bgChange & 1) != 0) {
                    d0 >>>= 2;                                // scroll_1a:180
                } else {
                    d0 >>>= 1;                                // scroll_1a:186-191
                    d2 = d0 >>> 2;
                    d0 += d2;
                    d2 >>>= 1;
                    d0 += d2;
                }
                bgY = d0;
                d2 = (d1 >>> 2) + d1;                         // scroll_1a:198-201
                bgX = setHigh(bgX, d2);
                bg2X = setHigh(bg2X, d1 >>> 1);               // :202-203
                bg3X = setHigh(bg3X, d1 >>> 2);               // :204-205
            }
            case B -> {
                d0 >>>= 3;                                    // scroll_1b:189-193
                d0 += d0 >>> 1;
                bgY = d0;
                int half = d1 >>> 1;                          // :197-201
                bgX = setHigh(bgX, half + (half >>> 1));
                bg2X = setHigh(bg2X, half >>> 1);             // :202-203
                int sixteenth = half >>> 3;                   // :204-208 (x>>1 >>2 >>1... as lsr.l #1,#2 chain)
                bg3X = setHigh(bg3X, sixteenth + 2 * sixteenth);
            }
            case C, C_ACT3 -> {
                bgY = d0 >>> 2;                               // scroll_1c:189-190
                bgX = setHigh(bgX, d1 >>> 1);                 // :194-195
                bg2X = setHigh(bg2X, d1 >>> 2);               // :196-197
                int sixteenth = d1 >>> 4;                     // :198-202
                bg3X = setHigh(bg3X, sixteenth + 2 * sixteenth);
            }
            case D, D_ACT3 -> {
                d0 >>>= 3;                                    // scroll_1d:190-195
                d2 = d0 >>> 2;
                d0 += d2;
                d2 += d2;
                d0 += d2;
                bgY = d0;
                bgX = setHigh(bgX, d1 >>> 1);                 // :200-205
                bg2X = setHigh(bg2X, d1 >>> 2);
                bg3X = setHigh(bg3X, d1 >>> 3);
            }
        }
    }

    private static int setHigh(int fixed, int word) {
        return ((word & 0xFFFF) << 16) | (fixed & 0xFFFF);    // move.w to a long's address writes its upper word
    }

    /** InitScroll + UpdateScroll's first call (r5/main.asm:207-208); camera as LevelSizeLoad leaves it. */
    void initLevelScroll(int cameraX, int cameraY) {
        Arrays.fill(deform, (short) 0);
        bgX = bg2X = bg3X = bgY = 0;
        bgChange = 0;                                         // r5/main.asm:243
        crossX = crossY = 0x10;
        fgX = cameraX & 0xFFFF;
        fgY = cameraY & 0xFFFF;
        xMove = 0;
        yDelta = 0;
        fraction = 0;
        initBgScroll(fgY, fgX);
        foregroundFlags = false;                              // r5/main.asm:271
        checkBackgroundSwap();                                // r5/main.asm:272
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
        publish(horizScrollBuf);
    }

    @Override
    public void rebuildOutput(int[] horizScrollBuf, int cameraX, int cameraY, int frameCounter, int actId) {
        if (!initialised) initLevelScroll(cameraX, cameraY);
        publish(horizScrollBuf);
    }

    /** UpdateScroll (scroll_1a:210-286 and the matching blocks of the other files) followed by CheckBackgroundSwap (r5/main.asm:373-377). */
    private void advance(int cameraX, int cameraY) {
        int newX = cameraX & 0xFFFF;
        int newY = cameraY & 0xFFFF;
        xMove = (short) ((newX - fgX) << 8);                  // CheckScrollFgX scroll_1a:567-600
        yDelta = (short) (newY - fgY);
        foregroundFlags = false;                              // clr.w scroll_flags_fg (scroll_1a:219)
        if (((newX & 0x10) ^ crossX) == 0) {                  // ScrollFgX scroll_1a:545-560
            crossX ^= 0x10;
            foregroundFlags = true;
        }
        if (((newY & 0x10) ^ crossY) == 0) {                  // ScrollFgY tail scroll_1a:774-789
            crossY ^= 0x10;
            foregroundFlags = true;
        }
        fgX = newX;
        fgY = newY;
        int d4 = xMove;                                       // move.w scroll_x_move,d4 ; ext.l d4
        switch (program) {
            case A_ACT1, A_ACT2 -> {
                bg3X += d4 << 6;                              // scroll_1a:234-238
                bg2X += d4 << 7;                              // :239-243
                bgX += (d4 << 6) * 5;                         // :245-252
            }
            case B -> {
                bg3X += (d4 << 4) * 3;                        // scroll_1b:237-244
                bg2X += d4 << 6;                              // :245-249
                bgX += (d4 << 6) * 3;                         // :251-258
            }
            case C, C_ACT3 -> {
                bg3X += (d4 << 4) * 3;                        // scroll_1c:231-238
                bg2X += d4 << 6;                              // :239-243
                bgX += d4 << 7;                               // :245-249
            }
            case D, D_ACT3 -> {
                bg3X += d4 << 5;                              // scroll_1d:234-238
                bg2X += d4 << 6;                              // :239-243
                bgX += d4 << 7;                               // :245-249
            }
        }
        int y = fgY;
        int d0;
        switch (program) {
            case A_ACT1, A_ACT2 -> {                          // scroll_1a:253-267, scroll_2a:265-279
                if ((bgChange & 1) != 0) {
                    d0 = y >>> 2;
                } else {
                    d0 = y >>> 1;
                    int d2 = (d0 >>> 2) & 0xFFFF;
                    d0 += d2;
                    d2 = program == Program.A_ACT1 ? d2 >>> 2 : d2 >>> 1;
                    d0 += d2;
                }
            }
            case B -> d0 = ((y << 13) + (y << 12)) >>> 16;    // scroll_1b:259-264: fg_y long >>3 + >>4, upper word
            case C, C_ACT3 -> d0 = y >>> 2;                   // scroll_1c:250-252
            case D, D_ACT3 -> {                               // scroll_1d:250-257
                d0 = y >>> 3;
                int d1 = d0 >>> 2;
                d0 += d1;
                d1 += d1;
                d0 += d1;
            }
            default -> throw new IllegalStateException();
        }
        bgY = ((d0 & 0xFFFF) << 16) | (bgY & 0xFFFF);        // ScrollBgY (scroll_1a:872-884) writes the upper word
        checkBackgroundSwap();
    }

    /** CheckBackgroundSwap (background_swap_1a:3-54); no source for act 3 (scddisasm/src/r5/object_index_3.asm:74-76). */
    private void checkBackgroundSwap() {
        if (!program.hasSwap() || foregroundFlags) return;    // tst.b scroll_flags_fg ; bne
        int x = playerCentreX.getAsInt() & 0xFFFF;
        int state = 0;
        if (FIRST_THRESHOLD < x) {                            // cmp.w obj.x,d1 ; bcc stops at the first threshold >= x
            state++;
            if (SECOND_THRESHOLD < x) state++;
        }
        if (state == bgChange) return;
        bgChange = state;
        initBgScroll(fgY, fgX);                               // background_swap_1a:50-52
        swapListener.accept(state);
    }

    // ---------------------------------------------------------------------------------------------
    // Output: the deformation buffer, then the per-line expansion
    // ---------------------------------------------------------------------------------------------

    private static short word(int fixed) {
        return (short) (fixed >>> 16);
    }

    private short camWord(Cam cam) {
        return switch (cam) {
            case BG -> word(bgX);
            case BG2 -> word(bg2X);
            case BG3 -> word(bg3X);
            case FG -> (short) fgX;
        };
    }

    private void publish(int[] horizScrollBuf) {
        short foreground = (short) -fgX;                      // move.w scroll_fg_x,d0 ; neg.w d0 ; swap d0
        deformPos = 0;
        linePos = 0;
        // d3's upper word when the first ramp starts (see the class comment).
        fraction = switch (program) {
            case A_ACT1, A_ACT2, B -> ((xMove << 6) >> 16) & 0xFFFF;     // move.l d4,d3 with d4 = xMove<<6
            case C, C_ACT3 -> ((xMove << 5) >> 16) & 0xFFFF;             // d3 = 2 * (xMove<<4)
            case D, D_ACT3 -> yDelta == 0 ? 0 : ((yDelta & 0xFFFF) << 8 >>> 16);   // ScrollFgY: ror.l #8,d3
        };
        for (Step step : plan()) {
            if (step.isFill()) fill(step); else ramp(step);
        }
        int bgYw = bgY >>> 16;
        int index = (bgYw & program.tableMask()) >>> 3;
        int skip = bgYw & 7;                                  // andi.w #7,d2
        if (program == Program.D || program == Program.D_ACT3) {
            expandWithGradientTail(index, skip);
        } else {
            expand(index, skip, 29);                          // moveq #$1C,d1
        }
        resetScrollTracking();
        composer.reset();
        for (int line = 0; line < ScrollEffectComposer.VISIBLE_LINES; line++) {
            composer.writePackedScrollWord(line, foreground, line < linePos ? lines[line] : 0);
        }
        composer.copyPackedScrollWordsTo(horizScrollBuf);
        vscrollFactorBG = word(bgY);                          // scroll_1a:271
        composer.setVscrollFactorBG(vscrollFactorBG);
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }

    private Step[] plan() {
        boolean odd = (bgChange & 1) != 0;
        return switch (program) {
            case A_ACT1, A_ACT2 -> odd ? A_ODD : A_EVEN;
            case B -> odd ? B_ODD : B_EVEN;
            case C -> odd ? C_ODD : C_EVEN;
            case C_ACT3 -> C_ODD;                             // scroll_3c has no r5_bg_change test
            case D -> odd ? D_ODD : D_EVEN;
            case D_ACT3 -> D3_EVEN;
        };
    }

    private void putDeform(short value) {
        if (deformPos < DEFORM_WORDS) deform[deformPos] = value;
        deformPos++;
    }

    private void fill(Step step) {
        short value = (short) -camWord(step.source());
        for (int i = 0; i < step.words(); i++) putDeform(value);
    }

    /**
     * One parallax ramp (e.g. sub_202C64, scroll_1a:322-351). d2 = ((reference - source) << shiftIn)
     * divided by an unsigned word (divu.w: on overflow d2 is left unchanged), its low word sign-extended
     * and shifted by shiftOut; d3 starts at the source camera and gains d2 once per section, 16.16 with the
     * fraction carried in the upper word between sections and routines.
     */
    private void ramp(Step step) {
        int source = camWord(step.source()) & 0xFFFF;
        int reference = camWord(step.reference()) & 0xFFFF;
        int d2 = rampStep((short) (reference - source), step.shiftIn(), step.divisor(), step.shiftOut());
        int acc = (source << 16) | (fraction & 0xFFFF);       // move.w scroll_bgN_x,d3 keeps d3's upper word
        int[] counts = step.counts();
        short[] values = new short[counts.length];
        for (int i = 0; i < counts.length; i++) {
            values[i] = (short) -(acc >>> 16);                // move.w d3,d0 ; neg.w d0
            acc += d2;                                        // swap d3 ; add.l d2,d3 ; swap d3
        }
        fraction = acc & 0xFFFF;
        if (step.backward()) {                                // adda.w #n,a1 ; move.w d0,-(a1): last section lowest
            for (int i = counts.length - 1; i >= 0; i--) for (int n = 0; n < counts[i]; n++) putDeform(values[i]);
        } else {
            for (int i = 0; i < counts.length; i++) for (int n = 0; n < counts[i]; n++) putDeform(values[i]);
        }
    }

    /**
     * d2 of a ramp: {@code diff} (sub.w, sign-extended) shifted left, divided by the unsigned word
     * {@code divisor} with divu.w (on overflow, quotient above $FFFF, d2 keeps its dividend), its low word
     * sign-extended and shifted left again (scroll_1a:324-332).
     */
    static int rampStep(int diff, int shiftIn, int divisor, int shiftOut) {
        int d2 = diff << shiftIn;                             // asl.l d1,d2
        long dividend = d2 & 0xFFFFFFFFL;
        if ((dividend >>> 16) < divisor) {
            int quotient = (int) (dividend / divisor);
            int remainder = (int) (dividend % divisor);
            d2 = (remainder << 16) | quotient;
        }
        d2 = (short) d2;                                      // ext.l d2
        return d2 << shiftOut;                                // asl.l d1,d2
    }

    private short deformWord(int index) {
        return index >= 0 && index < DEFORM_WORDS ? deform[index] : 0;
    }

    private void emitLine(short value) {
        if (linePos < LINE_CAP) lines[linePos] = value;
        linePos++;
    }

    /** loc_202D82 (scroll_1a:496-517): a part-entered first block, then {@code blocks - 1} whole blocks of 8 lines. */
    private void expand(int index, int skip, int blocks) {
        short word = deformWord(index++);
        for (int i = skip; i < 8; i++) emitLine(word);
        for (int block = 1; block < blocks; block++) {
            word = deformWord(index++);
            for (int i = 0; i < 8; i++) emitLine(word);
        }
    }

    /** scroll_1d:301-353 (also scroll_3d:273-324): the table runs out at word 39, a gradient fills the rest. */
    private void expandWithGradientTail(int index, int skip) {
        boolean odd = (bgChange & 1) != 0;
        int d5 = 0x1D;
        boolean expandTable = true;
        int d1 = 0x1C;
        if (!odd) {
            d1 = 0x27 - index;                                // sub.w d3,d1 ; bcs.s loc_202C50
            if (d1 < 0) {
                expandTable = false;
            } else if (d1 >= 0x1C) {
                d1 = 0x1C;
            }
        }
        if (expandTable) {
            d5 -= d1;                                         // loc_202C46
            expand(index, skip, d1 + 1);
        }
        if (odd) return;                                      // loc_202C50: btst ; bne
        int bgWord = word(bgX) & 0xFFFF;
        int step = ((short) (fgX - bgWord)) << 8;             // scroll_1d:331-337: divs.w #$100 undoes the asl.l #8
        int acc = bgWord << 16;                               // moveq #0,d3 ; move.w d0,d3 ; swap
        for (int i = d5 * 8; i > 0; i--) {
            emitLine((short) -(acc >>> 16));
            acc += step;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Rewind
    // ---------------------------------------------------------------------------------------------

    private record State(int bgX, int bg2X, int bg3X, int bgY, int fgX, int fgY, int xMove, int yDelta,
                         int crossX, int crossY, boolean flags, int bgChange, short[] deform, int lastFrame,
                         boolean initialised, boolean boss, boolean reverse) { }

    @Override
    public Object captureRewindState() {
        return new State(bgX, bg2X, bg3X, bgY, fgX, fgY, xMove, yDelta, crossX, crossY, foregroundFlags,
                bgChange, deform.clone(), lastFrame, initialised, bossFlags, conveyorReverse);
    }

    @Override
    public void restoreRewindState(Object state) {
        if (!(state instanceof State s)) return;
        bgX = s.bgX();
        bg2X = s.bg2X();
        bg3X = s.bg3X();
        bgY = s.bgY();
        fgX = s.fgX();
        fgY = s.fgY();
        xMove = s.xMove();
        yDelta = s.yDelta();
        crossX = s.crossX();
        crossY = s.crossY();
        foregroundFlags = s.flags();
        bgChange = s.bgChange();
        System.arraycopy(s.deform(), 0, deform, 0, deform.length);
        lastFrame = s.lastFrame();
        initialised = s.initialised();
        bossFlags = s.boss();
        conveyorReverse = s.reverse();
    }

    @Override
    public short getVscrollFactorBG() {
        return vscrollFactorBG;
    }
}
