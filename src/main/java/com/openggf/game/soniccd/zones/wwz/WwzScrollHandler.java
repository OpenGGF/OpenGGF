package com.openggf.game.soniccd.zones.wwz;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.SonicCdGame;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.ScrollOutputRebuilder;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

import java.util.Arrays;

/**
 * Wacky Workbench background scroll, all ten stages (act 1 and 2 in four time periods, act 3 Good and Bad Future).
 *
 * <p>Sources (docs/scddisasm pin 1ecc5f3a): act 1 Present assembles {@code Level/Wacky Workbench/Scroll (Act 1
 * Present).asm} with {@code Level/Scroll Flag Set.asm}; the other nine stages assemble {@code r6/scroll_{1b,1c,1d,2a,
 * 2b,2c,2d,3c,3d}.asm}, the same engine in the other source dialect (the labels below are the r6 ones, with the Level
 * dialect line numbers where the Present file is cited). All ten share {@code InitLevelScroll}
 * ({@code R6ScrollSetLayerPositions}, scddisasm/src/r6/scroll_1b.asm:179-195 = Present:153-171) and the first half of
 * {@code UpdateScroll} (scroll_1c.asm:198-247 and the same lines in every file = Present:177-240): three layer cameras
 * as 16.16 longs advanced by the camera delta (BG3 {@code <<6}, BG2 {@code <<7}, BG {@code *48}), and the background Y
 * set to {@code cameraY >> 2}. They differ only in how the 8-line deformation words are built and which layer fills them:
 * <ul>
 *   <li><b>LIGHTS</b> (R61A, R62A, R61C, R61D, R62C, R62D, R63C, R63D): a perspective curve of sine-weighted words
 *       ({@code ScrollBGLights}, Present:295-327 = {@code R6ScrollUpdateBackgroundCurve}, scroll_1c.asm:93-119), then one
 *       run of BG3 and one of BG2 words. A/A2: 10 curve segments, BG3 20 words, BG2 46 words (Present:250-264,
 *       r6/scroll_1a.asm); Good/Bad Future and act 3: 9 segments, BG3 24 words, BG2 46 words (scroll_1c.asm:57-69).</li>
 *   <li><b>LINEAR</b> (R61B, R62B): a straight-line curve of 17 segments built with {@code divu.w #$11}
 *       ({@code R6ScrollUpdateBackgroundCurve}, scddisasm/src/r6/scroll_1b.asm:356-385), then alternating BG, BG3 and BG2 runs
 *       (scroll_1b.asm:258-320 for R61B with 9 runs; scroll_2b.asm:58-99 for R62B with 7 runs).</li>
 * </ul>
 * Every deformation word covers 8 lines, written by {@code R6ScrollWriteScrollLinePattern} (scddisasm/src/r6/scroll_1b.asm:389-410)
 * starting at {@code (bgY & $3F8) >> 3} with a partial first block and 29 more.
 *
 * <p>Shipped {@code FixBugs = 0}. Behaviour kept as shipped: the routine writes 96 words (R62B: 80) of the 256 in
 * {@code bg_scroll_lines} and reads up to 30 blocks from {@code bgY / 8}; words it never wrote are cold RAM, which
 * this model reads as zero. {@code scroll_bg_y} is overwritten each frame by {@code cameraY >> 2}, so the vertical wrap
 * in {@code ScrollFgY} ({@code andi.w #$3FF,scroll_bg_y}, Present:555,577) never survives to a frame's output and is not
 * modelled. The native routine does not run while {@code scroll_lock} is set (Present:180-182) or the player is dead
 * ({@link com.openggf.game.soniccd.zones.CdLevelScrollGate}). In the LINEAR curve the low half of
 * {@code d3} that the curve inherits from earlier code ({@code scroll_x_move << 5}, scroll_1b.asm:234-239) is the
 * initial fraction of the running offset; it is modelled because it moves segment boundaries by a pixel.
 */
public final class WwzScrollHandler extends AbstractZoneScrollHandler implements ScrollOutputRebuilder {
    /** Which deformation layout a stage uses. */
    enum Family { LIGHTS, LINEAR }

    private enum Layer { BG, BG2, BG3 }

    private record Run(Layer layer, int words) { }

    /** Per-stage layout data. */
    record Layout(Family family, int[] curveLengthsMinusOne, Run[] runs) {
        int curveWords() {
            int total = 0;
            for (int n : curveLengthsMinusOne) total += n + 1;
            return total;
        }
    }

    // scroll_1a.asm BGLightSects / Present:280-291: n-1 per segment, indexed by d6 (9 down to 0).
    private static final int[] LIGHTS_A = {5, 3, 3, 2, 2, 1, 1, 1, 1, 1};
    // scroll_1c.asm:81-91 (R6ScrollCurveSegmentLengths, 9 used); d6 8 down to 0.
    private static final int[] LIGHTS_F = {5, 3, 2, 2, 1, 1, 1, 1, 1};
    // scroll_1b.asm:334-352; d6 $10 down to 0 (the 18th byte is padding).
    private static final int[] LINEAR_B = {1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 1, 1, 1, 0, 0};

    private static final Run[] RUNS_A = {new Run(Layer.BG3, 0x14), new Run(Layer.BG2, 0x2E)};            // Present:250-264
    private static final Run[] RUNS_F = {new Run(Layer.BG3, 0x18), new Run(Layer.BG2, 0x2E)};            // scroll_1c.asm:58-69
    private static final Run[] RUNS_1B = {                                                                 // scroll_1b.asm:258-320
            new Run(Layer.BG, 0xE), new Run(Layer.BG3, 2), new Run(Layer.BG2, 4), new Run(Layer.BG3, 0xC),
            new Run(Layer.BG2, 4), new Run(Layer.BG3, 0xC), new Run(Layer.BG2, 4), new Run(Layer.BG3, 0xC),
            new Run(Layer.BG2, 4)};
    private static final Run[] RUNS_2B = Arrays.copyOf(RUNS_1B, 7);                                      // scroll_2b.asm:58-99

    static Layout layoutFor(CdStagePayload payload) {
        return switch (payload) {
            case R61A, R62A -> new Layout(Family.LIGHTS, LIGHTS_A, RUNS_A);
            case R61C, R61D, R62C, R62D, R63C, R63D -> new Layout(Family.LIGHTS, LIGHTS_F, RUNS_F);
            case R61B -> new Layout(Family.LINEAR, LINEAR_B, RUNS_1B);
            case R62B -> new Layout(Family.LINEAR, LINEAR_B, RUNS_2B);
            default -> throw new IllegalArgumentException(payload + " is not a Wacky Workbench stage");
        };
    }

    private static final int DEFORM_WORDS = 0x200 / 2;       // bg_scroll_lines / deformBuffer rs.b $200 (_Definitions.inc:121)
    private static final int LINE_SINK = 512;                // spare room past the 240 lines a frame can write
    private static final int SINE_WORDS = 320;               // SineTable: 256 words plus 64 for cosine (Functions (Misc).asm:76-96)

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
    private final short[] deform = new short[DEFORM_WORDS];
    private final short[] sink = new short[LINE_SINK];
    private int sinkPos;
    private int lastCameraX;
    private int lastFrame = Integer.MIN_VALUE;
    private boolean initialised;
    private short foreground;

    public WwzScrollHandler(CdStagePayload payload) {
        this(payload, null);
    }

    /** {@code sineTable} is the 320-word SineTable; {@code null} reads it from the loaded stage on first use. */
    public WwzScrollHandler(CdStagePayload payload, int[] sineTable) {
        this.payload = payload;
        this.layout = layoutFor(payload);
        this.sine = sineTable;
    }

    private int[] sine() {
        if (sine == null) {
            var game = GameServices.level().getGame();
            CdStageImage image = game instanceof SonicCdGame cd ? cd.currentImage() : null;
            if (image == null) throw new IllegalStateException("The Wacky Workbench scroll needs the loaded stage file");
            sine = readSine(payload, image);
        }
        return sine;
    }

    /** Reads SineTable from the stage file at the offset the generator found. */
    static int[] readSine(CdStagePayload payload, CdStageImage image) {
        var rows = WwzCatalogue.rows(payload, "sine");
        int[] words = new int[SINE_WORDS];
        int at = rows.isEmpty() ? -1 : rows.get(0).a();
        for (int i = 0; at >= 0 && i < SINE_WORDS; i++) words[i] = (short) image.u16(at + 2 * i);
        return words;
    }

    // ---------------------------------------------------------------------------------------------
    // Level start: InitLevelScroll
    // ---------------------------------------------------------------------------------------------

    /** InitLevelScroll with the camera LevelSizeLoad_StartPos leaves in d0/d1 (Present:153-171, scroll_1b.asm:179-195). */
    void initLevelScroll(int cameraX, int cameraY) {
        int d0 = cameraY & 0xFFFF;
        int d1 = cameraX & 0xFFFF;
        Arrays.fill(deform, (short) 0);
        // ClearCamera zeroed every fraction before LevelSizeLoad (Level/Main.asm:210-216).
        bgY = (d0 << 16) >>> 2;                               // swap d0 ; lsr.l #2,d0 ; move.l d0,scroll_bg_y (:154-156)
        d1 >>>= 1;
        bg2X = d1 << 16;                                      // :161-162 (move.w into the long's high word)
        d1 >>>= 1;
        bg3X = d1 << 16;                                      // :163-164
        d1 >>>= 2;
        bgX = ((d1 + d1 + d1) & 0xFFFF) << 16;                // :165-169
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

    /** scroll_x_move is the per-frame camera delta in 8.8 (Present:205, 211, 219; scroll_1c.asm:223-240). */
    private void advance(int cameraX, int cameraY) {
        int diff = (short) ((cameraX - lastCameraX) << 8);
        lastCameraX = cameraX;
        scrollXMove = diff;
        bg3X += diff << 6;                                    // ext.l ; asl.l #6 ; ScrollBg3X (scroll_1c.asm:223-227)
        bg2X += diff << 7;                                    // asl.l #7 ; ScrollBg2X (:228-232)
        bgX += diff * 48;                                     // (d4<<4) + 2*(d4<<4) ; ScrollBgX (:233-241)
        // ScrollBgY: move.w d0,scroll_bg_y writes the long's high word only (Scroll Flag Set.asm:102-104).
        bgY = (((cameraY & 0xFFFF) >>> 2) << 16) | (bgY & 0xFFFF);   // lsr.w #2,d0 (:242-244)
    }

    // ---------------------------------------------------------------------------------------------
    // Output: deformation words, then the per-line expansion
    // ---------------------------------------------------------------------------------------------

    private void publish(int[] horizScrollBuf, int cameraX) {
        foreground = (short) -cameraX;                        // move.w scroll_fg_x,d0 ; neg.w d0 ; swap d0 (:253-256)
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
        vscrollFactorBG = (short) (bgY >>> 16);               // move.w scroll_bg_y,scroll_y+2 (:245)
        composer.setVscrollFactorBG(vscrollFactorBG);
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }

    private static short word(int fixed) {
        return (short) (fixed >>> 16);
    }

    private void buildDeformation(int cameraX) {
        int cursor = layout.curveWords();
        if (layout.family() == Family.LIGHTS) lightsCurve(cameraX); else linearCurve(cameraX);
        for (Run run : layout.runs()) {
            short value = switch (run.layer()) {                // move.w scroll_bgN_x,d0 ; neg.w d0 (:258-269)
                case BG -> (short) -word(bgX);
                case BG2 -> (short) -word(bg2X);
                case BG3 -> (short) -word(bg3X);
            };
            for (int i = 0; i < run.words() && cursor < DEFORM_WORDS; i++) deform[cursor++] = value;
        }
    }

    /**
     * ScrollBGLights / R6ScrollUpdateBackgroundCurve (Present:295-327, scroll_1c.asm:93-119): each segment's words are
     * {@code -(((256 - cos(angle)) * (cameraX - bgX) >> 8) + bgX)}, written downward from the end of the curve with the
     * angle growing by 6 per segment.
     */
    private void lightsCurve(int cameraX) {
        int[] lengths = layout.curveLengthsMinusOne();
        int[] table = sine();
        int d2 = (short) ((cameraX & 0xFFFF) - word(bgX));    // move.w cameraX,d2 ; sub.w d0,d2 ; ext.l d2
        int d4 = word(bgX) & 0xFFFF;
        int angle = 0;                                         // moveq #0,d3
        int position = layout.curveWords();
        for (int d6 = lengths.length - 1; d6 >= 0; d6--) {
            int cosine = table[(angle & 0xFF) + 64];           // CalcSine: d1 = SineTable[(angle + 64)] (Functions (Misc).asm:69-76)
            int d5 = (0x100 - cosine) & 0xFFFF;                // sub.w d1,d5
            int product = (short) d5 * (short) d2;             // muls.w d2,d5
            short value = (short) -(((product >>> 8) + d4) & 0xFFFF);   // lsr.l #8 ; add.w d4,d5 ; neg.w d5
            int count = lengths[d6] + 1;
            for (int i = 0; i < count; i++) deform[--position] = value;  // move.w d5,-(a1)
            angle = (angle + 6) & 0xFF;                        // addq.b #6,d3
        }
    }

    /**
     * R6ScrollUpdateBackgroundCurve of R61B/R62B (scddisasm/src/r6/scroll_1b.asm:356-385): a running offset stepped by
     * {@code ((cameraX - bgX) << 7) / 17 << 9} per segment from the bottom of the curve upward.
     */
    private void linearCurve(int cameraX) {
        int[] lengths = layout.curveLengthsMinusOne();
        int d2 = (short) ((cameraX & 0xFFFF) - word(bgX));    // move.w scroll_fg_x,d2 ; sub.w d0,d2 ; ext.l d2
        d2 <<= 7;                                              // moveq #7,d1 ; asl.l d1,d2
        long dividend = d2 & 0xFFFFFFFFL;                      // divu.w #$11,d2: unsigned 32 / 16
        long quotient = dividend / 0x11;
        if (quotient <= 0xFFFF) d2 = (int) (((dividend % 0x11) << 16) | quotient);   // else overflow: d2 unchanged
        d2 = (short) d2;                                       // ext.l d2
        d2 <<= 9;                                              // moveq #9,d1 ; asl.l d1,d2
        // d3's high word is whatever the earlier code left: scroll_x_move << 5 (scroll_1b.asm:234-239), whose
        // low byte/word were then replaced by ScrollBgX's and ScrollBgY's move.b/move.w to d3.
        int leftover = (scrollXMove << 5) >>> 16;
        int d3 = (leftover << 16) | (word(bgX) & 0xFFFF);      // move.w scroll_bg_x,d3
        int position = layout.curveWords();
        for (int d6 = lengths.length - 1; d6 >= 0; d6--) {
            short value = (short) -(d3 & 0xFFFF);              // move.w d3,d0 ; neg.w d0
            int count = lengths[d6] + 1;                       // moveq #0,d5 ; move.b table(pc,d6.w),d5
            for (int i = 0; i < count; i++) deform[--position] = value;
            d3 = Integer.rotateLeft(d3, 16);                   // swap d3 ; add.l d2,d3 ; swap d3
            d3 += d2;
            d3 = Integer.rotateLeft(d3, 16);
        }
    }

    private short deformWord(int index) {
        return index >= 0 && index < DEFORM_WORDS ? deform[index] : 0;
    }

    /** R6ScrollWriteScrollLinePattern (scddisasm/src/r6/scroll_1b.asm:389-410): a partial first block, then 29 whole blocks. */
    private void scrollLines() {
        int bgYw = bgY >>> 16;
        int a2 = (bgYw & 0x3F8) >>> 3;                         // andi.w #$3F8,d0 ; lsr.w #2,d0 (bytes) -> word index
        int skipped = bgYw & 7;
        emitBlock(deformWord(a2++), 8 - skipped);
        for (int i = 0; i < 0x1D; i++) emitBlock(deformWord(a2++), 8);   // moveq #$1D,d1 ; dbf
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
