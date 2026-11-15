package com.openggf.game.soniccd.zones.ppz;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.ScrollOutputRebuilder;
import com.openggf.level.scroll.compose.ScrollEffectComposer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;

/**
 * Palmtree Panic background scroll: act 1 and acts 2 and 3, all time periods.
 *
 * <p>Sources, all under {@code src/Level/Palmtree Panic/} in docs/scddisasm (pin 1ecc5f3a):
 * {@code Scroll (Act 1 Present).asm} (PRESENT), {@code ...Past).asm}, {@code ...Good Future).asm}
 * and {@code ...Bad Future).asm}, with the shared {@code src/Level/Scroll Flag Set.asm}. The four
 * routines are one scheme (a 16.16 camera per parallax layer, one deformation word per 8 background
 * lines, expanded to per-line longs) with different tables, so one class carries them. Where they
 * really differ the code branches on the period and cites the lines:
 * <ul>
 *   <li>PRESENT has the cloud speeds in {@code scrlSectSpeeds} (19 sections), a 256-word deformation
 *       buffer, per-section water deformation (ApplyBGHScroll, Present:433-534), a background Y that
 *       is derived from the camera Y (Present:240-255) and a 3D-ramp X offset (Present:203-226).</li>
 *   <li>PAST, GOOD_FUTURE and BAD_FUTURE keep four cloud speeds in the first 16 bytes of
 *       {@code deformBuffer}, accumulate the background Y from {@code scrollYDiff} (Past:231-235) and
 *       differ in layer rates, section lengths and tail (Past:211-334, Good Future:211-398,
 *       Bad Future:211-333).</li>
 * </ul>
 *
 *
 * <p>Acts 2 and 3 (R12A-D, R13C-D): Act 2 Present has its own file ({@code Scroll (Act 2 Present).asm});
 * Act 2 Past, Act 2 Good Future, Act 2 Bad Future and both Act 3 stages all include the one file
 * {@code Scroll (Act 2 Past).asm} ({@code Scroll (Act 2 Good Future).asm:7}, {@code (Act 2 Bad Future).asm:7},
 * {@code (Act 3).asm:7}) and select by the assembler switches {@code ACT2_FUTURE_VARIANT} (0 Past, 1 Good
 * Future, 2 Bad Future) and {@code ACT3_SCROLL_VARIANT} (set by {@code Act 3 Good Future.asm:10} and
 * {@code Act 3 Bad Future.asm:10}). That is the same scheme as act 1 with different numbers, so the
 * per-frame layer arithmetic, the cloud speeds and the line table are data in {@link Act23}; only the
 * line expansion ({@code FillHScroll}, {@code LevelScroll_InterpolateHScroll}) is separate code, because
 * act 1 expands through its section table and water splitter instead. Unlike act 1 Present, act 2
 * Present follows the Past rule for the background Y (it accumulates {@code scrollYDiff}, Act 2
 * Present:229-238) and has no 3D ramp.
 *
 * <p>Shipped {@code FixBugs = 0}. Behaviour kept as shipped: BAD_FUTURE reads deformation words past the
 * 30 it writes when the background is scrolled down (Bad Future:316-321); this model reads zero there
 * because {@code deformBuffer} is cold RAM that the routine never writes. The vertical wrap in
 * {@code ScrollCamY} ({@code andi.w #$3FF,cameraBgY.w}, Present:724,746) belongs to the camera and is not
 * seen here. The native routine does not run while {@code scrollLock} is set (Present:178-180) or the player
 * is dead ({@link com.openggf.game.soniccd.zones.CdLevelScrollGate}).
 */
public final class PpzScrollHandler extends AbstractZoneScrollHandler implements ScrollOutputRebuilder {
    /** Source of the player facts the PRESENT routine reads. */
    public interface PlayerView {
        /** {@code objPlayerSlot+oX}: the ROM centre X (Present:136,241). */
        int centreX();

        /** {@code oXVel} (Present:208). */
        int xSpeed();

        /** {@code oPlayerCtrl} bit 1, set by Handle3DRamp (Present:204). */
        boolean on3dRamp();
    }

    /**
     * The focused sprite, with Handle3DRamp's control bit from the shared player owner.
     */
    private static PlayerView focusedSprite() {
        return new PlayerView() {
            private AbstractPlayableSprite sprite() {
                Camera camera = GameServices.camera();
                return camera == null ? null : camera.getFocusedSprite();
            }
            @Override public int centreX() { var s = sprite(); return s == null ? 0 : s.getCentreX() & 0xFFFF; }
            @Override public int xSpeed() { var s = sprite(); return s == null ? 0 : s.getXSpeed(); }
            @Override public boolean on3dRamp() {
                return com.openggf.game.soniccd.player.CdPlayerStage.onPseudo3dRamp(sprite());
            }
        };
    }

    // Present:264-282 (the 19 addi.l before the cloud loops).
    private static final int[] PRESENT_CLOUD_RATES = {
            0x10000, 0xE000, 0xC000, 0xA000, 0x8000, 0x6000, 0x4800, 0x4000, 0x2800, 0x2000,
            0x2000, 0x4000, 0x8000, 0xC000, 0x10000, 0xC000, 0x8000, 0x4000, 0x2000};
    // Past/Good Future/Bad Future:248-251.
    private static final int[] BLOCK_CLOUD_RATES = {0x10000, 0xC000, 0x8000, 0x4000};
    // Present:402-412 CloudSectSizes and 414-424 CloudUpsideDownSectSizes (line counts, written as n-1).
    private static final int[] CLOUD_SECT_LINES_TOP = {2, 4, 6, 8, 8, 8, 4, 6, 6, 4};
    private static final int[] CLOUD_SECT_LINES_FLIPPED = {2, 4, 6, 8, 16, 4, 10, 4, 2};
    // Present:426-429 WaterDeformSects: {start line, line count}.
    private static final int[][] WATER_SECTS = {{0x280, 0xE0}, {0x780, 0x80}, {0x7FFF, 0x360}};
    private static final int DEFORM_WORDS = 0x200 / 2;       // deformBuffer rs.b $200 (_Definitions.inc:121)
    private static final int LINE_SINK = 512;                // hscroll is $400 bytes of longs, 256 lines; spare room
    private static final int PRESENT_FIRST_RAMP_X = 0x800;   // Present:136,241
    private static final int PRESENT_FIRST_RAMP_Y = 0x1E0;   // Present:138,243,248

    private final CdPeriod period;
    private final int act;
    private final Act23 act23;
    private final PlayerView player;
    private final ScrollEffectComposer composer = new ScrollEffectComposer();

    // Native RAM this routine owns: cameraBgX/cameraBg2X/cameraBg3X/cameraBgY are 16.16 longs.
    private int bgX;
    private int bg2X;
    private int bg3X;
    private int bgY;
    private final int[] speeds = new int[19];
    private final short[] deform = new short[DEFORM_WORDS];
    private final short[] sink = new short[LINE_SINK];
    private int sinkPos;
    private final boolean[] sinkStatic = new boolean[LINE_SINK];
    private final boolean[] wordStatic = new boolean[DEFORM_WORDS];
    private boolean emitStatic;
    private boolean bgWindowed;
    private int lastCameraX;
    private int lastCameraY;
    private int lastFrame = Integer.MIN_VALUE;
    private boolean initialised;
    private int publishedCameraX;
    private short foreground;

    /** Act 1 of {@code period}. */
    public PpzScrollHandler(CdPeriod period) {
        this(period, focusedSprite());
    }

    /** Act 1 of {@code period} with an explicit player view. */
    public PpzScrollHandler(CdPeriod period, PlayerView player) {
        this(period, 1, player);
    }

    /** Any Palmtree Panic stage. */
    public PpzScrollHandler(CdStagePayload payload) {
        this(payload.stage().period(), payload.stage().actNumber(), focusedSprite());
    }

    private PpzScrollHandler(CdPeriod period, int act, PlayerView player) {
        this.period = period;
        this.act = act;
        this.act23 = act >= 2 ? Act23.of(period, act == 3) : null;
        this.player = player;
    }

    // ---------------------------------------------------------------------------------------------
    // Level start: InitLevelScroll
    // ---------------------------------------------------------------------------------------------

    /** InitLevelScroll with the camera LevelSizeLoad_StartPos leaves in d0/d1 (Present:135-171). */
    void initLevelScroll(int cameraX, int cameraY) {
        int d0 = cameraY & 0xFFFF;
        int d1 = cameraX & 0xFFFF;
        java.util.Arrays.fill(speeds, 0);        // Present:165-170 clears 19 longs; Past:172-176 clears 4
        java.util.Arrays.fill(deform, (short) 0);
        // ClearCamera zeroed every fraction before LevelSizeLoad (Level/Main.asm:210-216).
        if (act23 != null) {
            bgY = act23.initBgY(d0);
            int[] x = act23.initBgX(d1);
            bgX = x[0] << 16;
            bg2X = x[1] << 16;
            bg3X = x[2] << 16;
        } else if (period == CdPeriod.PRESENT) {
            if (player.centreX() >= PRESENT_FIRST_RAMP_X) d0 = rampAdjustedY(d0);   // Present:136-143
            bgY = d0 << 16;                                  // Present:146-149 (d0 is zero-extended)
            d1 = (d1 >>> 1) & 0xFFFF;
            bgX = d1 << 16;                                  // Present:152-153
            d1 >>>= 2;
            bg3X = ((d1 + d1 + d1) & 0xFFFF) << 16;          // Present:154-158
            d1 >>>= 1;
            bg2X = ((d1 + d1 + d1) & 0xFFFF) << 16;          // Present:159-163
        } else {
            bgY = (d0 << 16 >> 4) << 1;                      // Past:154-157: swap, asr.l #4, add.l d0,d0
            if (period == CdPeriod.PAST) {
                d1 = (d1 >>> 1) & 0xFFFF;
                bgX = d1 << 16;                              // Past:162-163
                d1 >>>= 2;
                bg3X = d1 << 16;                             // Past:164-165
                d1 >>>= 1;
                bg2X = ((d1 + d1 + d1) & 0xFFFF) << 16;      // Past:166-170
            } else {
                d1 = (d1 >>> 2) & 0xFFFF;
                bg2X = d1 << 16;                             // Good Future:162-163
                d1 >>>= 1;
                bg3X = d1 << 16;                             // Good Future:164-165
                d1 >>>= 1;
                bgX = ((d1 + d1 + d1) & 0xFFFF) << 16;       // Good Future:166-170
            }
        }
        lastCameraX = cameraX;
        lastCameraY = cameraY;
        initialised = true;
    }

    /** Present:240-249 (and 136-144): background Y once the player is past the first 3D ramp. */
    private static int rampAdjustedY(int y) {
        int t = y - PRESENT_FIRST_RAMP_Y;
        boolean borrow = t < 0;
        int d0 = t & 0xFFFF;
        if (!borrow) d0 >>>= 1;                              // bcs.s .ChgDir skips the lsr.w
        return (d0 + PRESENT_FIRST_RAMP_Y) & 0xFFFF;
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
            // LevelScroll returns at once while scrollLock is set (Scroll (Act 1 Present).asm:177-180, Scroll
            // (Act 2 Past).asm:268-270), and Level_MainLoop skips it while the player is dead: the layer cameras and
            // cloud speeds stand still while the hscroll buffer keeps being displayed.
            if (com.openggf.game.soniccd.zones.CdLevelScrollGate.runs()) advance(cameraX, cameraY);
        }
        publish(horizScrollBuf, cameraX);
    }

    @Override
    public void rebuildOutput(int[] horizScrollBuf, int cameraX, int cameraY, int frameCounter, int actId) {
        if (!initialised) initLevelScroll(cameraX, cameraY);
        publish(horizScrollBuf, cameraX);
    }

    /** scrollXDiff / scrollYDiff are the per-frame camera deltas in 8.8 (Present:564-593, 754-760). */
    private void advance(int cameraX, int cameraY) {
        int diffX = (short) ((cameraX - lastCameraX) << 8);
        int diffY = (short) ((cameraY - lastCameraY) << 8);
        lastCameraX = cameraX;
        lastCameraY = cameraY;
        if (act23 != null) {
            advanceAct23(diffX, diffY);
            return;
        }
        switch (period) {
            case PRESENT -> advancePresent(diffX, cameraY);
            case PAST -> advancePast(diffX, diffY);
            case GOOD_FUTURE, BAD_FUTURE -> advanceFuture(diffX, diffY);
        }
    }

    /**
     * Act 2 Past:293-376 (Act 2 Present:212-253): the three layer cameras and the background Y move by
     * the camera delta times the variant's multipliers, then the cloud speeds advance by their rates.
     */
    private void advanceAct23(int diffX, int diffY) {
        bg3X += diffX * act23.bg3Mul();
        bg2X += diffX * act23.bg2Mul();
        bgX += diffX * act23.bgMul();
        bgY += diffY * act23.bgYMul();
        for (int i = 0; i < act23.cloudRates().length; i++) speeds[i] += act23.cloudRates()[i];
    }

    /** Present:203-282. */
    private void advancePresent(int diffX, int cameraY) {
        int ramp = 0;
        if (player.on3dRamp() && diffX != 0) ramp = player.xSpeed() << 8;     // Present:203-210
        bg3X += (diffX << 5) + ramp;                          // Present:213-218, SetHorizScrollFlagsBG3
        bg2X += diffX * 48 + 2 * ramp;                        // Present:220-229: (d4<<4)*3 + 2*d5
        bgX += (diffX << 7) + ramp;                           // Present:233-238, SetHorizScrollFlagsBG
        int y = cameraY & 0xFFFF;
        if (player.centreX() >= PRESENT_FIRST_RAMP_X) y = rampAdjustedY(y);  // Present:240-249
        bgY = (y << 16) | (bgY & 0xFFFF);                     // SetVertiScrollFlagsBG2, Scroll Flag Set:102-104
        for (int i = 0; i < PRESENT_CLOUD_RATES.length; i++) speeds[i] += PRESENT_CLOUD_RATES[i];
    }

    /** Past:211-251. The vertical scroll follows scrollYDiff at 1/8 speed (asl.l #4 then add.l d5,d5). */
    private void advancePast(int diffX, int diffY) {
        bg3X += diffX << 5;                                   // Past:211-215
        bg2X += diffX * 48;                                   // Past:217-224: (d4<<4)*3
        bgX += diffX << 7;                                    // Past:228-230, SetScrollFlagsBG
        bgY += diffY << 5;                                    // Past:231-235
        addCloudRates();
    }

    /** Good Future:211-251; Bad Future:211-251 is identical. */
    private void advanceFuture(int diffX, int diffY) {
        bg3X += diffX << 5;                                   // Good Future:211-215
        bg2X += diffX << 6;                                   // Good Future:217-221
        bgX += diffX * 48;                                    // Good Future:225-230
        bgY += diffY << 5;                                    // Good Future:231-235
        addCloudRates();
    }

    private void addCloudRates() {
        for (int i = 0; i < BLOCK_CLOUD_RATES.length; i++) speeds[i] += BLOCK_CLOUD_RATES[i];   // :247-251
    }

    // ---------------------------------------------------------------------------------------------
    // Output: deformation words, then the per-line expansion
    // ---------------------------------------------------------------------------------------------

    private void publish(int[] horizScrollBuf, int cameraX) {
        publishedCameraX = cameraX;
        foreground = (short) -cameraX;                        // Present:284-286
        sinkPos = 0;
        emitStatic = false;
        java.util.Arrays.fill(deform, (short) 0);
        if (act23 != null) {
            act23Lines();
        } else {
            switch (period) {
                case PRESENT -> presentLines();
                case PAST -> pastLines();
                case GOOD_FUTURE -> goodFutureLines();
                case BAD_FUTURE -> badFutureLines();
            }
        }
        resetScrollTracking();
        composer.reset();
        for (int line = 0; line < ScrollEffectComposer.VISIBLE_LINES; line++) {
            composer.writePackedScrollWord(line, foreground, line < sinkPos ? sink[line] : 0);
        }
        composer.copyPackedScrollWordsTo(horizScrollBuf);
        vscrollFactorBG = (short) (bgY >>> 16);               // Present:253, Past:237
        composer.setVscrollFactorBG(vscrollFactorBG);
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
        bgWindowed = false;
        if (act23 != null || period != CdPeriod.PRESENT) chooseBackgroundWindow(320);   // act 1 Present keeps its 512 window
    }

    private static short word(int fixed) {
        return (short) (fixed >>> 16);
    }

    private short cloudWord(int speed, int layer3) {          // move.l,swap,add.w cameraBg3X,neg.w
        return (short) -(word(speed) + word(layer3));
    }

    private int deformCursor;

    private void deformFill(int count, short value) {
        for (int i = 0; i < count && deformCursor < DEFORM_WORDS; i++) deform[deformCursor++] = value;
    }

    /** Present:287-385: 256 words, one per 8 lines of the 2048-line background. */
    private void presentLines() {
        deformCursor = 0;
        short layer3 = (short) -word(bg3X);
        short mountains = (short) -word(bg2X);
        short ground = (short) -word(bgX);
        int s = 0;
        for (int d6 = 9; d6 >= 0; d6--) {                     // Present:291-304 (sizes read from index d6)
            deformFill(CLOUD_SECT_LINES_TOP[d6], cloudWord(speeds[s++], bg3X));
        }
        deformFill(20, mountains);                            // Present:306-312
        deformFill(4, ground);                                // Present:314-320
        deformFill(28 * 2, ground);                           // Present:322-328 (top and flipped water)
        deformFill(4, ground);                                // Present:330-336
        deformFill(20, mountains);                            // Present:338-344
        for (int d6 = 8; d6 >= 0; d6--) {                     // Present:346-361
            deformFill(CLOUD_SECT_LINES_FLIPPED[d6], cloudWord(speeds[s++], bg3X));
        }
        deformFill(20, mountains);                            // Present:363-369
        deformFill(4, ground);                                // Present:371-377
        deformFill(16, ground);                               // Present:379-385
        applyBgHScroll();
    }

    private static int sectStart(int index) {
        return index < WATER_SECTS.length ? WATER_SECTS[index][0] : 0x10000;   // the table ends at $7FFF
    }

    /**
     * ApplyBGHScroll (Present:433-534), register for register: d1 blocks left, d2 first-block skip,
     * d4 background line of the next block, a2 deformation word, a3 water section.
     */
    private void applyBgHScroll() {
        int bgYw = bgY >>> 16;
        int d4 = bgYw;                                        // Present:391-392
        int d2 = bgYw;
        int a2 = (bgYw & 0x7F8) >>> 3;                        // Present:393-396: byte offset, 2 bytes a word
        int d1 = (240 / 8) - 1;                               // Present:395
        int a3 = 0;                                           // Present:397
        final int unmodified = 0, loop = 1, found = 2;
        int state = d4 >= sectStart(a3) ? found : unmodified; // Present:434-435 cmp.w (a3),d4 ; bcc
        while (true) {
            switch (state) {
                case unmodified -> {                          // .ScrollUnmodified, Present:437-444
                    d2 &= 7;
                    d4 = (d4 - d2 + 8) & 0xFFFF;
                    emitBlock(deformWord(a2++), 8 - d2);
                    d1--;                                     // .ScrollBlock dbf d1,.ScrollLoop (Present:459)
                    if (d1 == -1) return;
                    state = loop;
                }
                case loop -> {                                // .ScrollLoop, Present:446-453
                    if (d1 < 0) return;
                    if (d4 >= sectStart(a3)) {
                        state = found;
                    } else {
                        d4 = (d4 + 8) & 0xFFFF;
                        emitBlock(deformWord(a2++), 8);
                        d1--;
                        if (d1 == -1) return;
                    }
                }
                default -> {                                  // .FoundWaterSection, Present:464-534
                    int d5 = (d4 - sectStart(a3)) & 0xFFFF;
                    int size = WATER_SECTS[a3][1];
                    int d6 = size - d5;
                    if (d6 < 0) {                             // .SectOffscreen, Present:531-534
                        a3++;
                        d2 = d4;
                        state = unmodified;
                        break;
                    }
                    if (d6 != 0) {                            // beq .NextSection
                        int diff = (short) ((lastCameraX & 0xFFFF) - word(bgX));      // Present:473-476
                        int inc = waterIncrement(diff, 224);                          // Present:472,477-481
                        int acc = (bgX >>> 16) << 16;                                 // Present:482-483
                        for (int n = 0; n < d5; n++) acc += inc;                      // Present:484-492
                        d5 = d6 >>> 3;                                                // Present:495-496
                        int left = d1 - d5;                                           // Present:497
                        boolean empty = false;
                        if (left < 0) {                                               // bcc not taken
                            d6 -= (-left) << 3;                                       // Present:500-503
                            empty = d6 == 0;                                          // Present:504
                        }
                        d1 = left;
                        if (!empty) {
                            for (int n = d6; n > 0; n--) {                            // Present:506-525
                                emitLine((short) -word(acc));
                                acc += inc;
                                d4 = (d4 + 1) & 0xFFFF;
                                if ((d4 & 7) == 0) a2++;                              // Present:518-522
                            }
                        }
                    }
                    a3++;                                     // .NextSection, Present:527-529
                    state = loop;
                }
            }
        }
    }

    /** divs.w #divisor on (diff<<8), ext.l, asl.l #8 (Present:472-481; Good Future:339-343). */
    private static int waterIncrement(int diff, int divisor) {
        int dividend = diff << 8;
        long quotient = dividend / (long) divisor;            // truncates toward zero, like divs
        int d2 = dividend;
        if (quotient >= Short.MIN_VALUE && quotient <= Short.MAX_VALUE) d2 = (int) quotient;
        return ((short) d2) << 8;                             // ext.l, asl.l #8
    }

    private short deformWord(int index) {
        return index >= 0 && index < DEFORM_WORDS ? deform[index] : 0;
    }

    private void emitBlock(short word, int lines) {
        for (int i = 0; i < lines; i++) emitLine(word);
    }

    private void emitLine(short word) {
        if (sinkPos < LINE_SINK) {
            sinkStatic[sinkPos] = emitStatic;
            sink[sinkPos++] = word;
        }
    }

    /** Past:253-334. */
    private void pastLines() {
        deformCursor = 0;
        markCloudWords(8);
        for (int i = 0; i < 4; i++) deformFill(2, cloudWord(speeds[i], bg3X));   // Past:257-291
        deformFill(10, (short) -word(bg3X));                                      // Past:293-299
        deformFill(24, (short) -word(bg2X));                                      // Past:301-307
        scrollBlocksFromBackgroundY(29);
    }

    /** Good Future:253-399. */
    private void goodFutureLines() {
        deformCursor = 0;
        markCloudWords(12);
        deformFill(4, cloudWord(speeds[0], bg3X));            // Good Future:257-264
        deformFill(4, cloudWord(speeds[1], bg3X));            // Good Future:266-273
        deformFill(2, cloudWord(speeds[2], bg3X));            // Good Future:275-282
        deformFill(2, cloudWord(speeds[3], bg3X));            // Good Future:284-291
        deformFill(6, (short) -word(bg3X));                   // Good Future:293-299 mountains
        deformFill(2, (short) -word(bgX));                    // Good Future:301-307 bushes
        deformFill(8, (short) -word(bg2X));                   // Good Future:309-315 waterfalls
        int bgYw = bgY >>> 16;
        int blockIndex = (bgYw & 0x1F8) >>> 3;                // Good Future:320-325
        int d5 = 224 / 8;
        int d1 = (224 / 8) - 1 - blockIndex;
        if (d1 >= 0) {                                        // sub.w d3,d1 ; bcs .ScrollWater
            d5 -= d1;                                         // Good Future:331
            scrollBlocks(blockIndex, bgYw & 7, d1);           // Good Future:332-333, ScrollBlocks:364-380
        }
        int diff = (short) ((lastCameraX & 0xFFFF) - word(bg2X));   // Good Future:336-338
        int inc = waterIncrement(diff, 0x100);                // Good Future:339-343
        int acc = (bg2X >>> 16) << 16;
        for (int n = d5 * 8; n > 0; n--) {                    // Good Future:348-359
            emitLine((short) -word(acc));
            acc += inc;
        }
    }

    /** Bad Future:253-333. */
    private void badFutureLines() {
        deformCursor = 0;
        markCloudWords(14);
        deformFill(4, cloudWord(speeds[0], bg3X));            // Bad Future:257-264
        deformFill(6, cloudWord(speeds[1], bg3X));            // Bad Future:266-273
        deformFill(4, cloudWord(speeds[2], bg3X));            // Bad Future:275-282
        deformFill(8, (short) -word(bg3X));                   // Bad Future:284-290
        deformFill(2, (short) -word(bgX));                    // Bad Future:292-298
        deformFill(6, (short) -word(bg2X));                   // Bad Future:300-306
        scrollBlocksFromBackgroundY(232 / 8 - 1);                     // Bad Future:316-321
    }

    /** Past:312-334 and Bad Future:311-333: the first block is entered part way, then d1 more blocks. */
    private void scrollBlocksFromBackgroundY(int d1) {
        int bgYw = bgY >>> 16;
        scrollBlocks((bgYw & 0x1F8) >>> 3, bgYw & 7, d1);
    }

    /** ScrollBlocks (Good Future:364-380): a partial first block of 8-r lines, then d1 whole blocks. */
    private void scrollBlocks(int firstWord, int skippedLines, int d1) {
        int a2 = firstWord;
        emitStatic = isCloudWord(a2);
        emitBlock(deformWord(a2++), 8 - skippedLines);
        for (int i = 0; i < d1; i++) {
            emitStatic = isCloudWord(a2);
            emitBlock(deformWord(a2++), 8);
        }
        emitStatic = false;
    }

    /** The first {@code count} words of the table are the cloud groups (the BGSTATIC rows with non-repeating art). */
    private void markCloudWords(int count) {
        java.util.Arrays.fill(wordStatic, false);
        for (int i = 0; i < count; i++) wordStatic[i] = true;
    }

    private boolean isCloudWord(int index) {
        return index >= 0 && index < DEFORM_WORDS && wordStatic[index];
    }

    // ---------------------------------------------------------------------------------------------
    // Acts 2 and 3
    // ---------------------------------------------------------------------------------------------

    /** Which layer camera a run of deformation words follows. */
    private enum Layer { BG3, BG, BG2 }

    private record Run(Layer layer, int words) { }

    /**
     * The numbers of one variant of Act 2 Present.asm / Act 2 Past.asm. {@code cloudWords[i]} is the word
     * count of the cloud group that follows speed {@code i}; {@code runs} is the rest of the word table in
     * order. {@code lastIndex >= 0} selects the interpolating tail (Act 2 Present:305-346, Act 2
     * Past:471-515, Good Future), otherwise {@code fixedBlocks} more blocks are expanded after the first.
     */
    private record Act23(boolean futureInit, int bgYInitMul, int bg3Mul, int bg2Mul, int bgMul, int bgYMul,
                         int[] cloudRates, int[] cloudWords, List<Run> runs, int lastIndex, int fixedBlocks) {

        static Act23 of(CdPeriod period, boolean act3) {
            return switch (period) {
                // Act 2 Present: initial 153-172, per frame 212-253, words 259-300, tail 305-346.
                case PRESENT -> new Act23(false, 3, 32, 48, 128, 48,
                        new int[] {0x10000, 0xC000, 0x8000}, new int[] {4, 4, 4},
                        List.of(new Run(Layer.BG3, 20), new Run(Layer.BG2, 4)), 0x23, 0);
                // Act 2 Past (ACT2_FUTURE_VARIANT=0): initial 165-235 (else branches), per frame 293-376,
                // CloudLineCounts 538-541, words 383-455, tail 524-526.
                case PAST -> new Act23(false, 3, 32, 48, 128, 48,
                        new int[] {0x10000, 0xE000, 0xC000, 0xA000, 0x8000, 0x6000, 0x5000, 0x4000},
                        new int[] {2, 2, 2, 2, 2, 2, 2, 2},
                        List.of(new Run(Layer.BG3, 8), new Run(Layer.BG2, 8), new Run(Layer.BG2, 32)), -1, 0x1D);
                // ACT2_FUTURE_VARIANT=1: initial 168-226, per frame (bg3 x24, bg x32) 295-346, CloudLineCounts
                // 490-491 read from the top (d6=4..0), words 384-439, tail 471-515.
                case GOOD_FUTURE -> new Act23(true, 3, 24, 48, 32, 48,
                        new int[] {0x10000, 0xE000, 0xC000, 0xA000, 0x8000}, new int[] {2, 4, 4, 4, 2},
                        List.of(new Run(Layer.BG3, 6), new Run(Layer.BG, 2), new Run(Layer.BG2, 8)), 0x1F, 0);
                // ACT2_FUTURE_VARIANT=2: Act 2 background Y x2 (168-175, 333-340), x3 in Act 3
                // (ACT3_SCROLL_VARIANT, 169-172, 334-338); CloudLineCounts 521-522, tail 516-520.
                case BAD_FUTURE -> new Act23(true, act3 ? 3 : 2, 24, 48, 32, act3 ? 48 : 32,
                        new int[] {0x10000, 0xC000, 0x8000, 0x4000}, new int[] {4, 4, 5, 1},
                        List.of(new Run(Layer.BG3, 8), new Run(Layer.BG, 2), new Run(Layer.BG2, 6)), -1, 0x1C);
            };
        }

        /** {@code cameraBgY} long from the start camera Y: swap, asr.l #4, then x2 or x3 (Act 2 Past:166-181). */
        int initBgY(int cameraY) {
            return ((cameraY & 0xFFFF) << 16 >> 4) * bgYInitMul;
        }

        /** {bgX, bg2X, bg3X} words from the start camera X (Act 2 Past:186-235, Act 2 Present:164-172). */
        int[] initBgX(int cameraX) {
            int x = cameraX & 0xFFFF;
            if (futureInit) {
                int bg = x >>> 3;                          // lsr/asr.w #3, :186-195
                int bg2 = 3 * (x >>> 4) & 0xFFFF;          // :200-213
                int bg3 = 3 * (x >>> 5) & 0xFFFF;          // :214-226
                return new int[] {bg, bg2, bg3};
            }
            return new int[] {x >>> 1, 3 * (x >>> 4) & 0xFFFF, x >>> 3};   // :196-198, 227-235
        }
    }

    private short layerWord(Layer layer) {
        return (short) -word(switch (layer) { case BG3 -> bg3X; case BG -> bgX; case BG2 -> bg2X; });
    }

    /** Act 2 Past:358-526 and FillHScroll:547-561 (Act 2 Present:259-346, FillHScroll:355-369). */
    private void act23Lines() {
        deformCursor = 0;
        java.util.Arrays.fill(wordStatic, false);
        for (int i = 0; i < act23.cloudWords().length; i++) {   // ScrollCloudsOuter:392-404, words -(speed.w + bg3X.w)
            for (int n = 0; n < act23.cloudWords()[i]; n++) wordStatic[deformCursor + n] = true;
            deformFill(act23.cloudWords()[i], cloudWord(speeds[i], bg3X));
        }
        for (Run run : act23.runs()) deformFill(run.words(), layerWord(run.layer()));
        int bgYw = bgY >>> 16;
        int block = (bgYw & 0x1F8) >>> 3;                      // andi.w #$1F8 ; lsr.w #2 byte offset = 2 bytes a word
        int skipped = bgYw & 7;                                // FillHScroll:548 andi.w #7,d2
        if (act23.lastIndex() < 0) {                           // no interpolation: moveq #$1D/#$1C,d1 ; bra FillHScroll
            scrollBlocks(block, skipped, act23.fixedBlocks());
            return;
        }
        int d5 = 224 / 8;                                      // moveq #$1C,d5 (Act 2 Present:312, Act 2 Past:475)
        int d1 = act23.lastIndex() - block;                    // moveq #$23/#$1F,d1 ; sub.w d3,d1
        if (d1 >= 0) {                                         // bcs skips the fill when the block is past the table
            if (d1 >= 0x1B) d1 = 0x1B;                         // cmpi.w #$1B ; bcs
            d5 -= d1;
            scrollBlocks(block, skipped, d1);
        }
        int diff = (short) ((lastCameraX & 0xFFFF) - word(bg2X));       // Act 2 Present:325-327
        int inc = waterIncrement(diff, 0x100);                           // :328-332 divs.w #$100
        int acc = word(bg2X) << 16;                                      // :333-334
        emitStatic = false;
        for (int n = d5 * 8; n > 0; n--) {                               // :335-346
            emitLine((short) -word(acc));
            acc += inc;
        }
    }

    /**
     * Whether the shared renderer's Plane B window must follow the layers. Native keeps one 512-pixel plane
     * whose BGSTATIC rows were drawn from layout columns 0-511 only (DrawLevelBG1 .StaticRow,
     * Scroll (Act 2 Past).asm:974-982: GetBlockVDPCmdAbsX at X 0, DrawBlockRowAbsX 512 pixels) and so wrap at
     * 512, while each dynamic section streams the columns its own camera reaches. The renderer has one window
     * for all lines: a 512 window at the layout origin shows static rows exactly and dynamic rows only while
     * they read below 512; a window that follows the layers shows dynamic rows exactly and static rows only
     * where the layout beyond 512 looks like the layout below it. The cloud rows are the static rows whose art
     * does not repeat (measured: with the window following the layers they differ from native once their scroll
     * passes 512, while the lower static water matches), so the window follows the layers when some line needs
     * it and no visible cloud line would wrap (measured on the ten fixtures: the opposite rule, or always
     * following, breaks cloud rows that pass today; never following breaks the band rows at far camera X). The
     * proper fix is a window per band in the shared renderer.
     */
    private void chooseBackgroundWindow(int viewportWidth) {
        int needWindow = 0;
        int cloudWraps = 0;
        for (int line = 0; line < Math.min(sinkPos, ScrollEffectComposer.VISIBLE_LINES); line++) {
            int source = -sink[line];
            boolean fits = source >= 0 && source + viewportWidth <= 512;
            if (fits) continue;
            if (sinkStatic[line]) cloudWraps++;
            else needWindow++;
        }
        bgWindowed = needWindow > 0 && cloudWraps == 0;
    }

    // ---------------------------------------------------------------------------------------------
    // Rewind
    // ---------------------------------------------------------------------------------------------

    private record State(int bgX, int bg2X, int bg3X, int bgY, List<Integer> speeds, int lastCameraX,
                         int lastCameraY, int lastFrame, boolean initialised) { }

    @Override
    public Object captureRewindState() {
        List<Integer> copy = new ArrayList<>(speeds.length);
        for (int speed : speeds) copy.add(speed);
        return new State(bgX, bg2X, bg3X, bgY, List.copyOf(copy), lastCameraX, lastCameraY, lastFrame, initialised);
    }

    @Override
    public void restoreRewindState(Object state) {
        if (!(state instanceof State s)) return;
        bgX = s.bgX();
        bg2X = s.bg2X();
        bg3X = s.bg3X();
        bgY = s.bgY();
        for (int i = 0; i < speeds.length; i++) speeds[i] = s.speeds().get(i);
        lastCameraX = s.lastCameraX();
        lastCameraY = s.lastCameraY();
        lastFrame = s.lastFrame();
        initialised = s.initialised();
    }

    /**
     * The leftmost background column any visible line reads, so the shared renderer builds its Plane B window
     * around the layers instead of the layout's first 512 pixels. Native streams each background band into
     * the plane by that band's own camera (DrawLevelBG1's per-section camera, Scroll (Act 2 Past).asm:940-1043),
     * so a band whose camera is far from the others still shows the layout columns it scrolled to; a
     * fixed window shows them wrapped at 512 once the layers have drifted apart.
     */
    @Override
    public int getBgCameraX() {
        return bgWindowed && maxScrollOffset != Integer.MIN_VALUE ? publishedCameraX - maxScrollOffset : Integer.MIN_VALUE;
    }

    /** The window must span every band's scroll plus the screen: same rule as the Sonic 1 SBZ handler. */
    @Override
    public int getBgPeriodWidth() {
        // The configured width, or the native 320 where isolated scroll tests have no session.
        int viewportWidth = Math.max(320, com.openggf.level.spawn.PlacementViewportWidth.current());
        int left = getBgCameraX();
        int spread = bgWindowed && maxScrollOffset != Integer.MIN_VALUE ? maxScrollOffset - minScrollOffset : 0;
        int coverage = spread + viewportWidth + (left == Integer.MIN_VALUE ? 0 : Math.floorMod(left, 16));
        int width = 512;
        while (width < coverage) width <<= 1;
        return width;
    }

    @Override
    public short getVscrollFactorBG() {
        return vscrollFactorBG;
    }

    /** cameraBgX word, for tests against the native {@code globals.cameraBgX}. */
    public int backgroundCameraX() {
        return bgX;
    }

    /** cameraBgY long, for tests against the native {@code globals.cameraBgY}. */
    public int backgroundCameraY() {
        return bgY;
    }
}
