package com.openggf.game.soniccd.zones.ttz;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.SonicCdGame;
import com.openggf.game.soniccd.SonicCdZoneRegistry;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.WaterSystem;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.ScrollOutputRebuilder;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

import java.util.function.Supplier;

/**
 * Tidal Tempest background scroll for all ten stage programs.
 *
 * <p>Sources, docs/scddisasm pin 1ecc5f3a. The five Act 1 stages assemble {@code Level/Tidal Tempest/
 * R41x Legacy Scroll.asm} (R41A: {@code R41A Legacy Scroll.asm}; R41B, R41C, R41D: three byte-for-byte
 * equal files); R42A-R43D assemble {@code src/r4/scroll_2a.asm} to {@code scroll_3d.asm} through
 * {@code Level/Tidal Tempest/R4 Shared.asm:63-90}. A normalised comparison of the nine non-Present
 * routines (labels renamed, data tables compared byte for byte) finds one per-frame body in two
 * parameter sets:
 * <ul>
 *   <li><b>PRESENT_ACT1</b> (R41A, {@code R41A Legacy Scroll.asm:165-310}) is the only stage with a
 *       layered background: three layer cameras at 1/16, 3/32 and 3/64 of the foreground movement
 *       (the three scroll calls at :189-210), a background Y
 *       computed from the foreground Y each frame (:211-233), and a deformation of 192 words: 16 of the
 *       ground layer, 32 parallax words built by {@code sub_202E22} (:283-310) and 144 of the middle
 *       layer (:242-257).</li>
 *   <li><b>SHARED</b> (the other nine: {@code R41B Legacy Scroll.asm:138-170}, {@code scroll_2a.asm:
 *       195-246}) is one rigid background: {@code ScrollBgXY} advances X by 12 times and Y by 1/2 of the
 *       foreground movement (:218-229) and all 192 words equal the background X (:238-245). The nine
 *       files differ only in the deformation-block mask ($7F8 in Act 1 at {@code R41B Legacy Scroll.asm:
 *       223}, $3F8 in Acts 2 and 3 at {@code scroll_2a.asm:254}), the init data and the vertical wrap in
 *       {@code ScrollFgY}, which belongs to the camera.</li>
 * </ul>
 * Both produce the line table the same way (:259-366 / scroll_2a.asm:246-313): each of the 232 lines
 * takes the block word of its background row; from the first line at or below {@code water_y} on, the
 * background word gets the signed byte of {@code WobbleTable[(bg_water_deform_hi + bgY + line) & $FF]}
 * added and the foreground word is the plain {@code -scroll_fg_x}. {@code bg_water_deform} is a word that
 * UpdateScroll reads as its high byte and then advances by $80 (:317-319), so the table index advances
 * every second frame. The wobble table is read from the stage file at the offset
 * {@code tools/soniccd/ttz_catalogue.py} found.
 *
 * <p>Shipped {@code FixBugs = 0} behaviour kept: the divide in {@code sub_202E22} is {@code divu.w}
 * with its overflow behaviour (the dividend is left unchanged when the quotient does not fit, :287-290),
 * and the start of the parallax accumulator is the high word of the leftover {@code d3 = 8 * scroll_x_move}
 * (:197-210), so it is $FFFF when the foreground moved left. Block words past the 192 the routine writes
 * are cold RAM that no routine writes; this model reads zero there. The vertical wrap in {@code
 * ScrollFgY} is not visible to a scroll handler; {@code scroll_lock} ({@code UpdateScroll}, r4/scroll_1a.asm:165-169) and the dead player
 * stop the routine through {@link com.openggf.game.soniccd.zones.CdLevelScrollGate}. The native water level comes
 * from {@code UpdateWater} in V-blank; the handler reads the shared water system's level.
 */
public final class TtzScrollHandler extends AbstractZoneScrollHandler implements ScrollOutputRebuilder {
    /** The water level the line table splits at, or a value past the screen when the stage has none. */
    public interface WaterLine {
        int worldY();
    }

    private static final int NO_WATER = 0x7FFF;
    private static final int NATIVE_LINES = 0xE8;             // move.w #$E7,d6 ; dbf (:324)
    private static final int WORDS = 0xC0;                    // 16 + 32 + 144 / 192 x $C0 (:242-257)
    private static final int ACT1_MASK = 0x7F8;               // andi.w #$7F8,d0 (R41A Legacy Scroll.asm:266)
    private static final int ACT23_MASK = 0x3F8;              // andi.w #$3F8,d0 (scroll_2a.asm:254)
    private static final int WATER_DEFORM_STEP = 0x80;        // addi.w #$80,bg_water_deform (:319)
    // byte_202E1C (R41A Legacy Scroll.asm:273-279): parallax section lengths minus one, read from index 4 down.
    private static final int[] PARALLAX_SECTION_WORDS = {2, 2, 4, 10, 14};
    private static final int PARALLAX_CAMERA_SPLIT = 5;       // divu.w #5,d2 (:290)

    private final CdStagePayload payload;
    private final boolean layered;                            // R41A
    private final int blockMask;
    private final int zoneIndex;
    private final int actIndex;
    private final WaterLine water;
    private final Supplier<CdStageImage> stage;
    private final ScrollEffectComposer composer = new ScrollEffectComposer();

    // Native RAM this routine owns: the layer cameras are 16.16 longs, bg_water_deform a word.
    private int bgX;
    private int bg2X;
    private int bg3X;
    private int bgY;
    private int waterDeform;
    private int parallaxCarry;                                // high word of d3 left by UpdateScroll
    private final short[] deform = new short[WORDS];
    private byte[] wobble;
    private int lastCameraX;
    private int lastCameraY;
    private int lastFrame = Integer.MIN_VALUE;
    private boolean initialised;
    private final short[] lines = new short[NATIVE_LINES];
    private short foreground;

    public TtzScrollHandler(CdStagePayload payload) {
        this(payload, defaultWaterLine(payload), TtzScrollHandler::liveStage);
    }

    public TtzScrollHandler(CdStagePayload payload, WaterLine water, Supplier<CdStageImage> stage) {
        this.payload = payload;
        this.layered = payload.stage().period() == CdPeriod.PRESENT && payload.stage().actNumber() == 1;
        this.blockMask = payload.stage().actNumber() == 1 ? ACT1_MASK : ACT23_MASK;
        this.zoneIndex = SonicCdZoneRegistry.zoneIndex(payload);
        this.actIndex = SonicCdZoneRegistry.actIndex(payload);
        this.water = water;
        this.stage = stage;
    }

    private static WaterLine defaultWaterLine(CdStagePayload payload) {
        int zone = SonicCdZoneRegistry.zoneIndex(payload);
        int act = SonicCdZoneRegistry.actIndex(payload);
        return () -> {
            WaterSystem system = GameServices.waterOrNull();
            return system == null || !system.hasWater(zone, act) ? NO_WATER : system.getVisualWaterLevelY(zone, act);
        };
    }

    private static CdStageImage liveStage() {
        var level = GameServices.levelOrNull();
        return level != null && level.getGame() instanceof SonicCdGame game ? game.currentImage() : null;
    }

    /** True for R41A, the one stage whose background has parallax layers. */
    public boolean isLayered() {
        return layered;
    }

    // ---------------------------------------------------------------------------------------------
    // Level start: InitScroll
    // ---------------------------------------------------------------------------------------------

    /**
     * InitScroll's background setup with the camera the spawn code leaves in d0/d1
     * ({@code sub_202C94}, R41A Legacy Scroll.asm:110-161; {@code sub_202CEC}, scroll_2a.asm:172-191).
     */
    void initLevelScroll(int cameraX, int cameraY) {
        int d0 = cameraY & 0xFFFF;
        int d1 = cameraX & 0xFFFF;
        java.util.Arrays.fill(deform, (short) 0);
        // ClearCamera zeroed the fractions and the variable RAM, bg_water_deform included.
        waterDeform = 0;
        parallaxCarry = 0;
        if (layered) {
            if (d0 < 0x200) {                                  // :113-118
                if (d1 >= 0x280) d0 = 0x200;
                bgY = d0 << 16;                                // :137-138
            } else {
                int t = ((d0 - 0x200) << 16) >>> 2;            // :123-126
                bgY = t * 3 + 0x2000000;                       // :127-130 (add.l d2,d2 ; add.l d2,d0)
                d0 = bgY >>> 16;                               // :131 swap d0
            }
        } else {
            d0 = (d0 >>> 1) & 0xFFFF;                          // scroll_2a.asm:173-174 lsr.w #1,d0
            bgY = d0 << 16;                                    // :175-176
        }
        int d = d1 >>> 4;                                      // lsr.w #4,d1 -> scroll_bg3_x
        bg3X = (d & 0xFFFF) << 16;
        d >>>= 1;
        bg2X = ((d + d + d) & 0xFFFF) << 16;                   // lsr.w #1 ; d2 = 3 * d1 -> scroll_bg2_x
        d >>>= 1;
        bgX = ((d + d + d) & 0xFFFF) << 16;                    // lsr.w #1 ; d2 = 3 * d1 -> scroll_bg_x
        lastCameraX = cameraX;
        lastCameraY = cameraY;
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
            if (com.openggf.game.soniccd.zones.CdLevelScrollGate.runs()) {   // scroll_lock and the dead player (CdLevelScrollGate)
                advance(cameraX, cameraY);
                buildLines(cameraX, cameraY);
            }
        }
        emit(horizScrollBuf, cameraX);
    }

    @Override
    public void rebuildOutput(int[] horizScrollBuf, int cameraX, int cameraY, int frameCounter, int actId) {
        if (!initialised) initLevelScroll(cameraX, cameraY);
        emit(horizScrollBuf, cameraX);
    }

    /** scroll_x_move / scroll_y_move are the per-frame camera deltas in 8.8 (CheckScrollFgX, :655-688). */
    private void advance(int cameraX, int cameraY) {
        int moveX = (short) ((cameraX - lastCameraX) << 8);
        int moveY = (short) ((cameraY - lastCameraY) << 8);
        lastCameraX = cameraX;
        lastCameraY = cameraY;
        if (layered) {
            bg3X += moveX << 4;                                // :189-193 asl.l #4
            bg2X += (moveX << 3) * 3;                          // :194-201 (d4 << 3) * 3
            bgX += (moveX << 2) * 3;                           // :203-210 (d4 << 2) * 3
            parallaxCarry = moveX < 0 ? 0xFFFF : 0;            // d3 = 8 * move left behind by :203-210
            int fgY = cameraY & 0xFFFF;
            int d0;
            if (fgY < 0x200) {                                 // :212-218
                d0 = (cameraX & 0xFFFF) >= 0x280 ? 0x200 : fgY;
            } else {
                int t = ((fgY - 0x200) << 16) >>> 2;           // :222-230
                d0 = (t * 3 + 0x2000000) >>> 16;
            }
            bgY = (d0 << 16) | (bgY & 0xFFFF);                 // ScrollBgY: move.w d0,(scroll_bg_y).w (:961-962)
        } else {
            bgX += (moveX << 2) * 3;                           // scroll_2a.asm:219-225 -> ScrollBgXY d4
            bgY += moveY << 7;                                 // :226-229 d5 = scroll_y_move << 7
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Output: deformation words, then the per-line table
    // ---------------------------------------------------------------------------------------------

    private static short word(int fixed) {
        return (short) (fixed >>> 16);
    }

    private void buildDeform(int cameraX) {
        java.util.Arrays.fill(deform, (short) 0);
        if (!layered) {
            java.util.Arrays.fill(deform, (short) -word(bgX));      // scroll_2a.asm:238-245: $C0 words
            return;
        }
        int at = 0;
        short ground = (short) -word(bgX);                          // :242-248
        for (int i = 0; i < 16; i++) deform[at++] = ground;
        // sub_202E22 (:283-310)
        int bg3Word = word(bg3X);
        int d2 = (short) (cameraX - bg3Word);                       // sub.w d0,d2 ; ext.l d2
        d2 <<= 7;                                                   // asl.l d1,d2 with d1 = 7
        long dividend = d2 & 0xFFFFFFFFL;                           // divu.w #5: unsigned 32-bit dividend
        long quotient = dividend / PARALLAX_CAMERA_SPLIT;
        if (quotient <= 0xFFFF) d2 = (int) (((dividend % PARALLAX_CAMERA_SPLIT) << 16) | quotient);
        d2 = ((short) d2) << 9;                                     // ext.l d2 ; asl.l #9
        int d3 = (bg3Word & 0xFFFF) | (parallaxCarry << 16);        // move.w bg3_x,d3 on the leftover high word
        for (int section = PARALLAX_SECTION_WORDS.length - 1; section >= 0; section--) {
            short value = (short) -(d3 & 0xFFFF);                   // move.w d3,d0 ; neg.w d0
            for (int i = 0; i < PARALLAX_SECTION_WORDS[section]; i++) deform[at++] = value;
            d3 = Integer.rotateLeft(Integer.rotateLeft(d3, 16) + d2, 16);   // swap d3 ; add.l d2,d3 ; swap d3
        }
        short middle = (short) -word(bg2X);                         // :251-257: $90 words
        while (at < WORDS) deform[at++] = middle;
    }

    private void buildLines(int cameraX, int cameraY) {
        buildDeform(cameraX);
        byte[] table = wobbleTable();
        int waterY = water.worldY();
        int bgYWord = word(bgY) & 0xFFFF;
        int block = (bgYWord & blockMask) >>> 3;                    // lea (a2,d0.w),a2 after lsr.w #2
        int rowInBlock = bgYWord & 7;                               // move.w d0,d2 ; andi.w #7,d2
        int deformHigh = (waterDeform >>> 8) & 0xFF;                // move.b bg_water_deform,d3
        int index = (deformHigh + bgYWord) & 0xFF;                  // add.w scroll_bg_y,d3 ; andi.w #$FF,d3
        waterDeform = (waterDeform + WATER_DEFORM_STEP) & 0xFFFF;   // addi.w #$80,bg_water_deform
        int worldY = cameraY & 0xFFFF;
        short word = deformWord(block++);
        boolean underwater = false;
        for (int line = 0; line < NATIVE_LINES; line++) {
            if (!underwater && (short) worldY >= waterY) underwater = true;       // cmp.w water_y,d1 ; bge
            lines[line] = underwater ? (short) (word + (table == null ? 0 : table[index]))   // :352-354
                                     : word;
            if (!underwater) worldY = (worldY + 1) & 0xFFFF;        // addq.w #1,d1 (:333)
            index = (index + 1) & 0xFF;                             // addq.b #1,d3
            if (++rowInBlock == 8) {                                // addq.b #1,d2 ; cmpi.b #8,d2
                rowInBlock = 0;
                word = deformWord(block++);
            }
        }
    }

    private void emit(int[] horizScrollBuf, int cameraX) {
        foreground = (short) -cameraX;                              // neg.w d0 (:260-261)
        resetScrollTracking();
        composer.reset();
        for (int line = 0; line < ScrollEffectComposer.VISIBLE_LINES; line++) {
            composer.writePackedScrollWord(line, foreground, lines[line]);
        }
        composer.copyPackedScrollWordsTo(horizScrollBuf);
        vscrollFactorBG = word(bgY);                                // move.w scroll_bg_y,scroll_y+2 (:234)
        composer.setVscrollFactorBG(vscrollFactorBG);
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }

    private short deformWord(int index) {
        return index >= 0 && index < WORDS ? deform[index] : 0;
    }

    private byte[] wobbleTable() {
        if (wobble == null) {
            CdStageImage image = stage.get();
            if (image != null) wobble = image.slice(TtzCatalogue.wobbleOffset(payload), 256);
        }
        return wobble;
    }

    // ---------------------------------------------------------------------------------------------
    // Rewind
    // ---------------------------------------------------------------------------------------------

    private record State(int bgX, int bg2X, int bg3X, int bgY, int waterDeform, int parallaxCarry, int lastCameraX,
                         int lastCameraY, int lastFrame, boolean initialised) { }

    @Override
    public Object captureRewindState() {
        return new State(bgX, bg2X, bg3X, bgY, waterDeform, parallaxCarry, lastCameraX, lastCameraY, lastFrame,
                initialised);
    }

    @Override
    public void restoreRewindState(Object state) {
        if (!(state instanceof State s)) return;
        bgX = s.bgX();
        bg2X = s.bg2X();
        bg3X = s.bg3X();
        bgY = s.bgY();
        waterDeform = s.waterDeform();
        parallaxCarry = s.parallaxCarry();
        lastCameraX = s.lastCameraX();
        lastCameraY = s.lastCameraY();
        lastFrame = s.lastFrame();
        initialised = s.initialised();
    }

    @Override
    public short getVscrollFactorBG() {
        return vscrollFactorBG;
    }

    /** cameraBgX long, for tests. */
    public int backgroundCameraX() {
        return bgX;
    }

    /** cameraBgY long, for tests. */
    public int backgroundCameraY() {
        return bgY;
    }

    /** {@code bg_water_deform}, the wobble phase word, for tests. */
    public int waterDeformWord() {
        return waterDeform;
    }

    /** Background word of one screen line on the last frame, for tests. */
    public short lineWord(int line) {
        return lines[line];
    }
}
