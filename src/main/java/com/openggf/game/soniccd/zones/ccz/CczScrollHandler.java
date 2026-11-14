package com.openggf.game.soniccd.zones.ccz;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.ScrollOutputRebuilder;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Collision Chaos background scroll for all ten stage programs (R31A-D, R32A-D, R33C-D).
 *
 * <p>Sources (docs/scddisasm pin 1ecc5f3a). R31A: {@code src/Level/Collision Chaos/Scroll (Act 1
 * Present).asm} (LevelScroll :242-542, the label prefix {@code CollisionChaos_Act1Present_Scroll}).
 * The other nine stages: {@code src/r3/scroll_1b,1c,1d,2a,2b,2c,2d,3c,3d.asm} (UpdateScroll
 * about :213-535). R32A does not use {@code Scroll (Act 2).asm}: that file is reached only through the
 * unassembled {@code Act 2 Shared.asm}. The ten routines are one program with different numbers, so one
 * class runs them from {@link CczScrollProfiles}, which {@code tools/soniccd/ccz_scroll_profiles.py}
 * generates from those files with a citation per row. What every stage shares and this class hardcodes
 * (the generator asserts each of them against all ten files):
 * <ul>
 *   <li>layer rates: cameraBg3X += scrollXDiff&lt;&lt;4, cameraBg2X += 24*scrollXDiff, cameraBgX +=
 *       12*scrollXDiff, all 16.16 (Scroll (Act 1 Present):272-293; r3/scroll_1b:196-225);</li>
 *   <li>background Y: {@code $218 - ((($520 - cameraY) &gt;&gt; 1)}, {@code $218} below {@code $520},
 *       floored at 0 (Scroll (Act 1 Present):294-304; r3/scroll_1b:225-234);</li>
 *   <li>InitLevelScroll: same rule for Y, cameraBg3X = x&gt;&gt;4, cameraBg2X = 3*(x&gt;&gt;5), cameraBgX =
 *       3*(x&gt;&gt;6) (Scroll (Act 1 Present):199-232; r3/scroll_1b:168-205);</li>
 *   <li>the section writer that lays down the water-affected lines (CollisionChaos_Act1Present_
 *       ScrollWriteWaterRows, :544-632; r3/scroll_1b sub_202B3A) is identical code in all ten, except for
 *       its interpolation window, which is a profile number.</li>
 * </ul>
 * Where the stages really differ is data: the deformation rates, the block list that builds the
 * per-8-line word table, the water limits, and the wobble form (R31A indexes the table by pairs, the
 * other nine double the amplitude). The code below cites the R31A labels; the profile rows cite each
 * stage's own lines.
 *
 * <p>Shipped {@code FixBugs = 0}. Kept as shipped: the d3 fraction word a perspective ramp starts from is
 * what the preceding {@code ScrollBgX} left in the high half of d3, which is the sign of the frame's
 * scrollXDiff times 8 ({@code move.l d4,d3 / add.l d3,d3}, :288-291), and the next ramp continues from the
 * first one's final fraction. R33D's word table ends one word before the 768 lines the other stages cover;
 * a read past its end sees the next RAM word, modelled as zero. The vertical wrap in ScrollCamY
 * ({@code andi.w #$3FF,cameraBgY}) belongs to the camera.
 *
 * <p>{@code LevelScroll} returns at once while {@code scrollLock} is set ((Act 1 Present):242-246), so the
 * layers, the deformation arrays and the water phase stand still. The title card object sets the lock when its
 * vertical slide-out ends and clears it when the PLC queue is empty ({@code Level/_Objects/Title Card/Main.asm}
 * :144-146 and :181-186), which in the retail trace is 3 frames in R31A and 20 to 35 in the others; the arrays
 * therefore hold fewer advances than the level frame counter says. The level main loop also skips
 * {@code LevelScroll} while the player is dead (routine 6 or more) or in debug mode (Level/Main.asm:409-415), so
 * the layers stand still from the frame of death. Both gates are {@link
 * com.openggf.game.soniccd.zones.CdLevelScrollGate}.
 */
public final class CczScrollHandler extends AbstractZoneScrollHandler implements ScrollOutputRebuilder {
    private static final int LINE_SINK = 512;              // hscroll holds 256 long entries; spare room
    private static final int ROW_WORDS = 0x200 / 2;
    private static final int WOBBLE_SIZE = 256;            // d5 and $FF (r3) or (d5 and $7F)*2 (R31A)

    private final CdStagePayload payload;
    private final CczScrollProfiles.Profile profile;
    private final Supplier<CdStageImage> image;
    private final ScrollEffectComposer composer = new ScrollEffectComposer();

    private byte[] wobble;

    // Native RAM this routine owns: cameraBgX/Bg2X/Bg3X/BgY are 16.16 longs (_Definitions.inc), the
    // deformation array is deformBuffer (rates first, then the 64-entry curve), bgWaterDeform is a word.
    private int bgX;
    private int bg2X;
    private int bg3X;
    private int bgY;
    private final int[] deform;
    private int waterPhase;
    private int lastCameraX;
    private int lastXDiff;
    private int lastFrame = Integer.MIN_VALUE;
    private boolean initialised;

    private final short[] rows = new short[ROW_WORDS];
    private int rowCount;
    private final short[] lineFg = new short[LINE_SINK];
    private final short[] lineBg = new short[LINE_SINK];
    private int linePos;
    private int d3;                                        // carried between perspective ramps

    /** The scroll handler for one stage; the stage file is read when the wobble table is first needed. */
    public CczScrollHandler(CdStagePayload payload, Supplier<CdStageImage> image) {
        this.payload = payload;
        this.profile = CczScrollProfiles.of(payload);
        this.image = image;
        this.deform = new int[profile.rates().length + profile.curveCount()];
    }

    /** The stage file the focused level was loaded from, when the module is the hosted Sonic CD game. */
    static CdStageImage loadedStage() {
        var level = GameServices.levelOrNull();
        var game = level == null ? null : level.getGame();
        if (game instanceof com.openggf.game.soniccd.SonicCdGame cd) return cd.currentImage();
        return null;
    }

    private byte[] wobble() {
        if (wobble == null) {
            CdStageImage stage = image.get();
            if (stage == null) throw new IllegalStateException(payload + ": stage file not loaded yet");
            wobble = stage.slice(CczDiscTables.rows(payload, "wobble").get(0).a(), WOBBLE_SIZE);
        }
        return wobble;
    }

    // ---------------------------------------------------------------------------------------------
    // Level start: InitLevelScroll
    // ---------------------------------------------------------------------------------------------

    /** InitLevelScroll with the camera LevelSizeLoad_StartPos leaves in d0/d1 (Scroll (Act 1 Present):199-232). */
    void initLevelScroll(int cameraX, int cameraY) {
        bgY = backgroundY(cameraY) << 16;                      // :200-211, cameraBgY+2 cleared
        int d1 = cameraX & 0xFFFF;
        d1 >>>= 4;
        bg3X = d1 << 16;                                       // :214-215 lsr.w #4
        d1 >>>= 1;
        bg2X = ((d1 + d1 + d1) & 0xFFFF) << 16;                // :216-220
        d1 >>>= 1;
        bgX = ((d1 + d1 + d1) & 0xFFFF) << 16;                 // :221-225
        Arrays.fill(deform, 0, profile.rates().length, 0);     // :226-231 clears the rate longs only
        lastCameraX = cameraX;
        lastXDiff = 0;
        initialised = true;
        // LevelStart's LevelScroll after LevelSizeLoad (Level/Main.asm:245-246) is the level load's first update
        // (CdFirstMainLoopIteration).
    }

    /** {@code $218 - (($520 - cameraY) >> 1)}, {@code $218} when cameraY is past $520, floored at 0 (:294-301). */
    private static int backgroundY(int cameraY) {
        int d0 = 0x218;
        int d2 = (0x520 - (cameraY & 0xFFFF)) & 0xFFFF;
        boolean borrow = (cameraY & 0xFFFF) > 0x520;           // sub.w ; bcs
        if (borrow) return d0;
        d0 = (d0 - (d2 >>> 1)) & 0xFFFF;
        return (d0 & 0x8000) != 0 ? 0 : d0;                    // bpl.s ; moveq #0,d0
    }

    // ---------------------------------------------------------------------------------------------
    // Per frame: LevelScroll
    // ---------------------------------------------------------------------------------------------

    @Override
    public void init(int actId, int cameraX, int cameraY) {
        initLevelScroll(cameraX, cameraY);
        lastFrame = Integer.MIN_VALUE;
    }

    @Override
    public void update(int[] horizScrollBuf, int cameraX, int cameraY, int frameCounter, int actId) {
        if (!initialised) initLevelScroll(cameraX, cameraY);
        // One LevelScroll per frame: the engine may recompute the same frame's output.
        if (frameCounter != lastFrame) {
            lastFrame = frameCounter;
            if (com.openggf.game.soniccd.zones.CdLevelScrollGate.runs()) advance(cameraX, cameraY);   // :244-246 tst.b scrollLock ; rts
        }
        publish(horizScrollBuf, cameraX, cameraY);
    }

    @Override
    public void rebuildOutput(int[] horizScrollBuf, int cameraX, int cameraY, int frameCounter, int actId) {
        if (!initialised) initLevelScroll(cameraX, cameraY);
        publish(horizScrollBuf, cameraX, cameraY);
    }

    /** scrollXDiff is the per-frame camera delta in 8.8 (Scroll (Act 1 Present):635-678, MoveScreenHoriz). */
    private void advance(int cameraX, int cameraY) {
        int diff = (short) ((cameraX - lastCameraX) << 8);
        lastCameraX = cameraX;
        lastXDiff = diff;
        bg3X += diff << 4;                                     // :272-276 asl.l #4, SetHorizScrollFlagsBG3
        bg2X += diff * 24;                                     // :277-284 (d4<<3) * 3
        bgX += diff * 12;                                      // :286-293 (d4<<2) * 3
        bgY = (backgroundY(cameraY) << 16) | (bgY & 0xFFFF);   // :294-304 SetVertiScrollFlagsBG2 keeps the fraction
        if (profile.water().rows() - ((bgY >>> 16 & 0x3F8) >>> 3) >= 0) {                // :462-476 sub.w d3,d1 ; bcs
            waterPhase = (waterPhase + 0x40) & 0xFFFF;                                    // addi.w #$40,bgWaterDeform
        }
        int rates = profile.rates().length;
        for (int i = 0; i < rates; i++) deform[i] += profile.rates()[i];                // :314-328
        for (int j = 0; j < profile.curveCount(); j++) {                                // :329-338
            deform[rates + j] += j * profile.curveMultiplier() + profile.curveHalf();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Output: the per-8-line word table, then the per-line hscroll
    // ---------------------------------------------------------------------------------------------

    private void publish(int[] horizScrollBuf, int cameraX, int cameraY) {
        buildRows(cameraX);
        writeLines(cameraX, cameraY);
        resetScrollTracking();
        composer.reset();
        for (int line = 0; line < ScrollEffectComposer.VISIBLE_LINES; line++) {
            composer.writePackedScrollWord(line, line < linePos ? lineFg[line] : (short) -cameraX,
                    line < linePos ? lineBg[line] : 0);
        }
        composer.copyPackedScrollWordsTo(horizScrollBuf);
        vscrollFactorBG = (short) (bgY >>> 16);                // vscrollScreen+2 (:305)
        composer.setVscrollFactorBG(vscrollFactorBG);
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }

    private static short word(int fixed) {
        return (short) (fixed >>> 16);
    }

    private void addRows(int count, short value) {
        for (int i = 0; i < count && rowCount < ROW_WORDS; i++) rows[rowCount++] = value;
    }

    /**
     * The row table: one word per 8 background lines, written block by block exactly as the routine
     * writes it at deformBuffer+(rates+64)*4 (CollisionChaos_Act1Present_ScrollBuildPerspectiveCurve to
     * ...ScrollWriteBgXRowsAfterPerspective, :339-450).
     */
    private void buildRows(int cameraX) {
        rowCount = 0;
        Arrays.fill(rows, (short) 0);
        // d3's high word is what ScrollBgX left of move.l d4,d3 / add.l d3,d3, which is scrollXDiff*8
        // (…Act1Present_ScrollProcess :288-291): only its byte and word halves are overwritten afterwards.
        d3 = (lastXDiff << 3) & 0xFFFF0000;
        for (CczScrollProfiles.Block block : profile.blocks()) {
            if (block instanceof CczScrollProfiles.Fill fill) {
                int source = switch (fill.layer()) {
                    case BGX -> bgX;
                    case BG2X -> bg2X;
                    case BG3X -> bg3X;
                };
                addRows(fill.count(), (short) -word(source));                  // move.w d0,(a1)+ ; dbf
            } else if (block instanceof CczScrollProfiles.Deform d) {
                for (int i = 0; i < d.count(); i++) {                          // d6 counts down while a2 counts up
                    short value = (short) -(word(deform[i]) + word(bgX));      // swap d1 ; add.w ; neg.w
                    addRows(d.repeats()[d.count() - 1 - i] + 1, value);
                }
            } else if (block instanceof CczScrollProfiles.Persp p) {
                perspective(p, cameraX);
            }
        }
    }

    /** One perspective ramp (…ScrollBuildPerspectiveCurve :342-363 and ...ScrollBuildPerspectiveRows :423-443). */
    private void perspective(CczScrollProfiles.Persp p, int cameraX) {
        int d2 = (short) ((cameraX & 0xFFFF) - (word(bgX) & 0xFFFF));          // sub.w ; ext.l
        d2 <<= p.shift1();
        d2 = divs(d2, p.divisor());                                             // divs.w #divisor
        d2 = ((short) d2) << p.shift2();                                        // ext.l ; asl.l
        d3 = (d3 & 0xFFFF0000) | (word(bgX) & 0xFFFF);                          // move.w cameraBgX,d3
        int n = p.count();
        short[] values = new short[n];
        for (int j = 0; j < n; j++) {
            values[j] = (short) -(d3 & 0xFFFF);                                 // move.w d3,d0 ; neg.w d0
            d3 = Integer.rotateLeft(d3, 16);                                    // swap d3
            d3 += d2;                                                           // add.l d2,d3
            d3 = Integer.rotateLeft(d3, 16);                                    // swap d3
        }
        if (p.back()) {
            // Iteration j (d6 = n-1-j) writes downward from the end of the block (move.w d0,-(a1)).
            int total = 0;
            for (int j = 0; j < n; j++) total += repeat(p, n - 1 - j) + 1;
            int end = rowCount + total;
            for (int j = 0; j < n; j++) {
                int count = repeat(p, n - 1 - j) + 1;
                for (int k = 0; k < count && end - 1 - k >= 0 && end - 1 - k < ROW_WORDS; k++) {
                    rows[end - 1 - k] = values[j];
                }
                end -= count;
            }
            rowCount += total;
        } else {
            for (int j = 0; j < n; j++) addRows(repeat(p, n - 1 - j) + 1, values[j]);
        }
    }

    /** The repeat count for iteration d6 (the table is indexed by d6), or 0 for a ramp without one. */
    private static int repeat(CczScrollProfiles.Persp p, int d6) {
        return p.repeats() == null ? 0 : p.repeats()[d6];
    }

    /** divs.w #divisor,d2 then the caller's ext.l: on overflow d2 is left unchanged (…ScrollBuildPerspectiveCurve :345-351). */
    private static int divs(int dividend, int divisor) {
        long quotient = dividend / (long) divisor;                              // truncates toward zero, like divs
        if (quotient >= Short.MIN_VALUE && quotient <= Short.MAX_VALUE) return (int) quotient & 0xFFFF;
        return dividend;
    }

    /**
     * The high word of the k-th long after the deformation rates. The 64-entry curve comes first; past it the
     * long read overlaps the word table that follows it in deformBuffer (two words per long, high word first).
     */
    private int curveWord(int k) {
        if (k < profile.curveCount()) return word(deform[profile.rates().length + k]);
        int row = 2 * (k - profile.curveCount());
        return row < rowCount ? rows[row] : 0;
    }

    private short rowWord(int index) {
        return index >= 0 && index < rowCount ? rows[index] : 0;
    }

    private void emit(short fg, short bg) {
        if (linePos < LINE_SINK) {
            lineFg[linePos] = fg;
            lineBg[linePos] = bg;
            linePos++;
        }
    }

    private void emitBlock(short fg, short bg, int lines) {
        for (int i = 0; i < lines; i++) emit(fg, bg);
    }

    /**
     * The hscroll pass (…Act1Present_ScrollWaterRowsSetup :451-476, ...ScrollWriteWaterRowsMain :506-542).
     * Registers: d1 groups left, d2 first-group skip, d4 background line of the next group, a2 row word,
     * a3 section bound, d5 wobble phase.
     */
    private void writeLines(int cameraX, int cameraY) {
        CczScrollProfiles.Water w = profile.water();
        linePos = 0;
        short fgWord = (short) -cameraX;                       // move.w cameraX,d0 / neg.w / swap (:339-341)
        int bgYw = bgY >>> 16;
        int d4 = bgYw;
        int rowIndex = (bgYw & 0x3F8) >>> 3;                   // andi.w #$3F8 ; lsr.w #2 (byte offset, 2 per word)
        int d1 = w.rows() - rowIndex;
        int d5 = w.total();
        if (d1 >= 0) {                                         // sub.w d3,d1 ; bcs.s
            if (d1 >= w.compare()) d1 = w.clamp();             // cmpi.w ; bcs.s ; moveq
            d5 -= d1;
            d4 = sectionWriter(w, d1, bgYw, d4, rowIndex, fgWord);
        }
        int lines = d5 * 8 - 1;                                // move.w d5,d1 ; lsl.w #3 ; subq.w #1
        if (lines < 0 && w.checksNegative()) return;           // bmi.s (only R31B and R32B have it)
        if (lines < 0) lines = 0xFFFF;                         // dbf d1 from -1 would run 65536 times
        int phase = (waterPhase >>> 8) & 0xFF;                 // move.b bgWaterDeform,d5
        d4 = (d4 - bgYw) & 0xFFFF;                             // sub.w cameraBgY,d4
        int threshold = (w.waterLine() - cameraY) & 0xFFFF;    // move.w #$5C0,d3 ; sub.w cameraY,d3
        short fg = fgWord;
        byte[] table = wobble();
        for (int k = 0; k <= lines && linePos < LINE_SINK; k++) {
            short bg = (short) -(curveWord(k) + word(bgX));    // move.l (a2)+,d2 ; swap ; add.w ; neg.w (:514-521)
            if (d4 >= threshold) {                             // cmp.w d3,d4 ; bcs.s (unsigned)
                int d3w;
                if (w.doubledWobble()) {
                    d3w = table[phase & 0xFF] * 2;             // andi.w #$FF ; move.b ; ext.w ; add.w d3,d3
                } else {
                    d3w = table[(phase & 0x7F) * 2];           // andi.w #$7F ; add.w d6,d6 ; move.b ; ext.w
                }
                fg = (short) -(d3w + cameraX);                 // add.w cameraX,d3 ; neg.w d3
            }
            emit(fg, bg);
            d4 = (d4 + 1) & 0xFFFF;
            phase = (phase + 1) & 0xFFFF;
        }
    }

    /**
     * The section writer (…ScrollWriteWaterRows :544-632; r3 sub_202B3A): rows of 8 lines from the word
     * table, stopping at the section bounds to write wobble-interpolated lines instead. Returns d4.
     */
    private int sectionWriter(CczScrollProfiles.Water w, int d1, int d2, int d4, int a2, short fg) {
        int[] bounds = w.bounds();
        int a3 = 0;
        byte[] table = wobble();
        final int align = 0, runLoop = 1, loadRun = 2, found = 3, next = 4, interpolate = 5;
        int state = d4 >= (bounds[a3] & 0xFFFF) ? found : align;               // cmp.w (a3),d4 ; bcc.s
        int d3 = 0;
        short d0 = 0;
        while (true) {
            switch (state) {
                case align -> {
                    d2 &= 7;
                    d4 = (d4 + 8 - d2) & 0xFFFF;                               // addq.w #8 ; sub.w d2,d4
                    d0 = rowWord(a2++);
                    emitBlock(fg, d0, 8 - d2);                                 // jmp into the move.l run
                    if (--d1 < 0) return d4;                                   // dbf d1
                    state = runLoop;
                }
                case runLoop -> {
                    if (d1 < 0) return d4;                                     // tst.w d1 ; bmi.s
                    state = d4 >= (bounds[a3] & 0xFFFF) ? next : loadRun;      // cmp.w (a3),d4 ; bcc.s
                }
                case loadRun -> {
                    d0 = rowWord(a2++);
                    d4 = (d4 + 8) & 0xFFFF;
                    emitBlock(fg, d0, 8);
                    if (--d1 < 0) return d4;
                    state = runLoop;
                }
                case found -> {                                                // loc_202B70
                    d3 = ((bounds[a3++] & 0xFFFF) + w.window() - d4) & 0xFFFF;
                    if ((short) d3 > 0) {
                        state = interpolate;
                    } else if (d4 < (bounds[a3] & 0xFFFF)) {                   // cmp.w (a3),d4 ; bcs.s
                        state = align;
                    } else {
                        d3 = ((bounds[a3++] & 0xFFFF) + w.window() - d4) & 0xFFFF;
                        state = (short) d3 <= 0 ? align : interpolate;
                    }
                }
                case next -> {                                                 // loc_202B8A
                    d3 = ((bounds[a3++] & 0xFFFF) + w.window() - d4) & 0xFFFF;
                    state = (short) d3 <= 0 ? loadRun : interpolate;
                }
                default -> {                                                   // interpolate, loc_202B94
                    d3 = (d3 - 1) & 0xFFFF;
                    int d6 = d3;
                    int d2w = (waterPhase >>> 8) & 0xFF;                       // move.b bgWaterDeform,d2
                    int count = d3 + 1;
                    for (int k = 0; k < count; k++) {                          // dbf d3
                        d2w &= 0xFF;
                        short bg = (short) -(table[d2w] + word(bgX));          // ext.w ; add.w cameraBgX ; neg.w
                        emit(fg, bg);
                        d4 = (d4 + 1) & 0xFFFF;
                        d2w++;
                    }
                    d6 = (d6 & 0xFFFF) >>> 3;                                  // lsr.w #3,d6
                    for (int k = 0; k <= d6; k++) {                            // move.w (a2)+,d0 ; subq.w #1,d1 ; dbf d6
                        d0 = rowWord(a2++);
                        d1--;
                    }
                    state = runLoop;
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Rewind
    // ---------------------------------------------------------------------------------------------

    private record State(int bgX, int bg2X, int bg3X, int bgY, List<Integer> deform, int waterPhase,
                         int lastCameraX, int lastXDiff, int lastFrame, boolean initialised) { }

    @Override
    public Object captureRewindState() {
        List<Integer> copy = new ArrayList<>(deform.length);
        for (int value : deform) copy.add(value);
        return new State(bgX, bg2X, bg3X, bgY, List.copyOf(copy), waterPhase, lastCameraX, lastXDiff, lastFrame,
                initialised);
    }

    @Override
    public void restoreRewindState(Object state) {
        if (!(state instanceof State s)) return;
        bgX = s.bgX();
        bg2X = s.bg2X();
        bg3X = s.bg3X();
        bgY = s.bgY();
        for (int i = 0; i < deform.length; i++) deform[i] = s.deform().get(i);
        waterPhase = s.waterPhase();
        lastCameraX = s.lastCameraX();
        lastXDiff = s.lastXDiff();
        lastFrame = s.lastFrame();
        initialised = s.initialised();
    }

    @Override
    public short getVscrollFactorBG() {
        return vscrollFactorBG;
    }

    /** cameraBgX long, for tests against the native {@code globals.cameraBgX}. */
    public int backgroundCameraX() {
        return bgX;
    }

    /** cameraBgY long, for tests against the native {@code globals.cameraBgY}. */
    public int backgroundCameraY() {
        return bgY;
    }

    /** The i-th deformation long (rates first, then the curve), for tests; each advance adds the stage's rate. */
    public int deformationLong(int index) {
        return deform[index];
    }

    /** cameraBg2X long. */
    public int background2CameraX() {
        return bg2X;
    }

    /** cameraBg3X long. */
    public int background3CameraX() {
        return bg3X;
    }
}
