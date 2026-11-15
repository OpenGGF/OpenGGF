package com.openggf.game.soniccd.special;

import com.openggf.game.soniccd.program.CdProgramScreenRenderer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * The special stage's picture as the main-CPU program builds it (Special Stage/Main.asm): a tile
 * store, plane A (floor buffers, front background, HUD), plane B (background), a 64-colour palette
 * with its fade and cycles, per-line horizontal scroll and the sprite list. This is the program's
 * own state in engine terms, the same shape {@link CdProgramScreenRenderer} draws for the other
 * CD programs; there is no VDP model. Each method names the routine it reimplements.
 *
 * <p>Layout ({@code StageVDPRegs}, scddisasm/src/Special Stage/Main.asm:3496-3515): H32, plane A at $C000 and B at $E000,
 * 64 x 32 cells each; tiles are addressed by VRAM tile index (32 bytes each).
 */
public final class CdSpecialStageScreen {
    public static final int WIDTH = 256;
    public static final int HEIGHT = 224;
    /** Floor buffers: {@code IMGVRAM} $20 and $20 + IMGLENGTH (Main.asm:20, _Include/Main CPU.inc:469-486). */
    static final int BUFFER_TILE_1 = 1, BUFFER_TILE_2 = 1 + 384;

    private final CdSpecialStageAssets assets;
    private final int stage;
    private final CdSpecialStageFlags flags;
    final byte[] tiles = new byte[0x10000];
    final BitSet dirty = new BitSet(0x800);
    final int[] planeA = new int[64 * 32];
    final int[] planeB = new int[64 * 32];
    final int[] palette = new int[64];
    final int[] fadeTarget = new int[64];
    final int[] cram = new int[64];
    boolean cramPending;
    final int[] scrollA = new int[HEIGHT];
    final int[] scrollB = new int[HEIGHT];
    boolean display;
    final List<CdProgramScreenRenderer.Sprite> sprites = new ArrayList<>();
    private final boolean lineScroll;
    private final short[] numUpper;
    private final short[] numLower;

    // Line-scroll buffers and offsets (Main.asm:38-41, 60-65, 1285-1292).
    private final int[] bgHScroll = new int[256];
    private final int[] bgHScrollFrac = new int[256];
    private final int[] scrollOffset = new int[7];
    private int ss1Shift = 0x100;
    private int ss5ShiftOrWave;
    private int bgScrollMode;
    boolean scrollDisable;
    int scrollFlags;
    // Palette cycle state ({@code .Index}/{@code .Timer} words in each PalCycle, Main.asm:1764-2131).
    private final int[] cycle = new int[4];
    private int timeStonePalDelay = 20;

    CdSpecialStageScreen(CdSpecialStageAssets assets, int stage, CdSpecialStageFlags flags) throws IOException {
        this.assets = assets;
        this.stage = stage;
        this.flags = flags;
        this.lineScroll = stage == 0 || stage == 4 || stage == 5; // CheckLineScroll (scddisasm/src/Special Stage/Main.asm:2221-2229)
        this.numUpper = assets.wordTable("NumTilesUpper");
        this.numLower = assets.wordTable("NumTilesLower");
        start();
    }

    /** {@code Start} up to the first update (Main.asm:99-144). */
    private void start() throws IOException {
        short[] stagePal = CdSpecialStageAssets.words(assets.raw("Pal_Stage"));
        for (int i = 0; i < 64; i++) palette[i] = stagePal[i] & 0xFFFF;
        art("Art_TimeStone", 0x8480);
        art("Art_UFO", 0x8800);
        art("Art_TitleCard", 0xA2C0);
        art("Art_Splash", 0xB040);
        art("Art_Shadow", 0xDB80);
        art("Art_Items", 0xF1E0);
        art("Art_Explosion", 0xF5C0);
        art("Art_HUD", 0xFBA0);
        drawStageMap();
        loadStageData();
    }

    private void art(String name, int vram) throws IOException {
        byte[] data = assets.nemesis(name);
        System.arraycopy(data, 0, tiles, vram, data.length);
        dirty.set(vram >> 5, (vram + data.length + 31) >> 5);
    }

    /** {@code DrawStageMap} (scddisasm/src/Special Stage/Main.asm:3659-3705): two 32 x 12 cell buffers, cells down each column. */
    private void drawStageMap() {
        for (int buffer = 0; buffer < 2; buffer++) {
            int base = buffer == 0 ? 0x2001 : 0x2181;
            for (int row = 0; row < 12; row++) {
                for (int col = 0; col < 32; col++) {
                    int word = base + col * 12 + row;
                    if (col == 31) word += 0x8000; // the rightmost column has the priority bit
                    planeA[(16 + row) * 64 + buffer * 32 + col] = word & 0xFFFF;
                }
            }
        }
    }

    // LoadSSnData tilemap IDs (move.l #$17161514,d5 ... ; Main.asm:650-805), low byte first.
    private static final int[][] STAGE_MAPS = {
            {0x14, 0x15, 0x16, 0x17, 0x43}, {0x01, 0x02, 0x03, 0x04, 0x05}, {0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x28},
            {0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11}, {0x18, 0x19, 0x1A, 0x1B, 0x1C}, {0x1D, 0x1E, 0x1F, 0x20, 0x4D},
            {0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27}, {0x4E, 0x4F}};

    /** {@code LoadStageData} (scddisasm/src/Special Stage/Main.asm:620-830): background art and maps, HUD base, scroll, palette. */
    private void loadStageData() throws IOException {
        art("Art_SS" + (stage + 1) + "BG", 0x6020);
        if (stage == 5) art("Art_SS6BGWater", 0xF180);
        for (int id : STAGE_MAPS[stage]) drawTilemap(id);
        drawTilemap(0x12);                       // DrawHUDBase (scddisasm/src/Special Stage/Main.asm:836-845)
        if (flags.timeAttack()) drawTilemap(0x13);
        // InitBGLineHScroll (scddisasm/src/Special Stage/Main.asm:855-864): long fills, so each line is A = 0, B = the low word.
        if (stage == 4) for (int line = 0; line < 128; line++) bgHScroll[line * 2 + 1] = 0xFF80;
        short[] pal = CdSpecialStageAssets.words(assets.raw("Pal_SS" + (stage + 1)));
        for (int i = 0; i < 32; i++) palette[16 + i] = pal[i] & 0xFFFF; // LoadStagePal (scddisasm/src/Special Stage/Main.asm:823-830)
    }

    /** {@code DrawTilemaps}/{@code DrawTilemap} (scddisasm/src/Special Stage/Main.asm:3707-4177). */
    void drawTilemap(int id) throws IOException {
        CdSpecialStageAssets.Tilemap map = assets.tilemap(stage, id);
        boolean b = map.vramAddress() >= 0xE000;
        int[] plane = b ? planeB : planeA;
        int cell = (map.vramAddress() - (b ? 0xE000 : 0xC000)) >> 1;
        for (int row = 0; row < map.height(); row++) {
            for (int col = 0; col < map.width(); col++) {
                plane[(cell + row * 64 + col) & 0x7FF] = map.words()[row * map.width() + col];
            }
        }
    }

    // ---- per-frame work of the main loop ----

    /** {@code PrepFadeFromWhite} (scddisasm/src/Special Stage/Main.asm:2474-2487). */
    void prepFadeFromWhite() {
        System.arraycopy(palette, 0, fadeTarget, 0, 64);
        java.util.Arrays.fill(palette, 0x0EEE);
        cramPending = true;
    }

    /**
     * One {@code FadeFromWhite} step (scddisasm/src/Special Stage/Main.asm:2493-2565): {@code step} 0-23, red then green then
     * blue, each channel capped from $E down to the target in eight steps.
     */
    void fadeFromWhiteStep(int step) {
        int channel = step / 8;
        int intensity = 0xE - 2 * (step % 8);
        int shift = 1 + channel * 4;
        for (int i = 0; i < 64; i++) {
            int target = (fadeTarget[i] >> (shift - 1)) & 0xE;
            int value = Math.max(target, intensity);
            palette[i] = (palette[i] & ~(0xE << (shift - 1))) | (value << (shift - 1));
        }
        cramPending = true;
    }

    /** The V-blank's palette upload ({@code ipxVSync} bit 1, Main.asm:897-899). */
    void vblankPalette() {
        if (cramPending) {
            System.arraycopy(palette, 0, cram, 0, 64);
            cramPending = false;
        }
    }

    /** Copies one half of a collected floor image into a buffer's tiles ({@code COPYIMG}). */
    void copyFloor(byte[] image, int buffer, int half) {
        int firstTile = buffer == 0 ? BUFFER_TILE_1 : BUFFER_TILE_2;
        int from = half == 0 ? 0 : 256, to = half == 0 ? 256 : 384;
        for (int cell = from; cell < to; cell++) {
            int col = cell / 12, row = cell % 12;
            int at = (firstTile + cell) * 32;
            for (int y = 0; y < 8; y++) {
                int src = (row * 8 + y) * CdStampFloor.IMAGE_WIDTH + col * 8;
                for (int x = 0; x < 8; x += 2) {
                    tiles[at + y * 4 + x / 2] = (byte) ((image[src + x] << 4) | (image[src + x + 1] & 15));
                }
            }
            dirty.set(firstTile + cell);
        }
    }

    /** {@code ShowStageBuf1}/{@code 2} (scddisasm/src/Special Stage/Main.asm:1022-1062): plane A floor lines show one buffer. */
    void showBuffer(int buffer) {
        int value = buffer == 0 ? 0 : 0x100;
        // Line stages DMA stageHScroll1/2: buffer 1's words were filled with d1 left over from
        // InitBGLineHScroll, which is 0 (FixBugs = 0; the BUG note at Main.asm:137-139).
        for (int line = 128; line < HEIGHT; line++) scrollA[line] = value;
    }

    /** {@code CopyHScroll} (scddisasm/src/Special Stage/Main.asm:1543-1693). */
    void copyHScroll() {
        if (scrollDisable) return;
        switch (stage) {
            case 0, 4, 5 -> { // CopyHScroll_Line: 128 lines of A and B
                for (int line = 0; line < 128; line++) {
                    scrollA[line] = bgHScroll[line * 2];
                    scrollB[line] = bgHScroll[line * 2 + 1];
                }
            }
            case 1, 3 -> { // CopyHScroll_SS2 / _SS4
                defaultBgScroll();
                if (stage == 1) {
                    rows(scrollA, 3, 13, scrollOffset[2]);
                    rows(scrollB, 0, 16, scrollOffset[3]);
                } else {
                    rows(scrollA, 3, 13, scrollOffset[2]);
                    rows(scrollB, 0, 11, scrollOffset[1]);
                    rows(scrollB, 11, 5, scrollOffset[3]);
                }
            }
            case 2 -> { // CopyHScroll_SS3
                int[] d = speed(0x80000, 0x10000);
                int d0 = d[0];
                scrollOffset[1] += d0;
                for (int k = 2; k <= 6; k++) { d0 -= d[1]; scrollOffset[k] += d0; }
                rows(scrollA, 3, 13, scrollOffset[5]);
                rows(scrollB, 0, 6, 0);
                rows(scrollB, 6, 3, scrollOffset[2]);
                rows(scrollB, 9, 1, scrollOffset[3]);
                rows(scrollB, 10, 4, scrollOffset[4]);
                rows(scrollB, 14, 2, scrollOffset[6]);
            }
            case 6 -> { // CopyHScroll_SS7
                int[] d = speed(0x80000, 0x18000);
                int d0 = d[0];
                scrollOffset[1] += d0;
                for (int k = 2; k <= 4; k++) { d0 -= d[1]; scrollOffset[k] += d0; }
                rows(scrollA, 3, 11, scrollOffset[2]);
                rows(scrollA, 14, 2, scrollOffset[4]);
                rows(scrollB, 0, 10, scrollOffset[1]);
                rows(scrollB, 10, 6, scrollOffset[3]);
            }
            default -> { } // CopyHScroll_SS8: rts
        }
    }

    /** {@code CopyHScrollSects} (scddisasm/src/Special Stage/Main.asm:1528-1537): row-scroll stages refresh on every V-blank. */
    void copyHScrollSects() {
        if (!lineScroll) copyHScroll();
    }

    private static void rows(int[] scroll, int firstRow, int count, int offset) {
        int value = (offset >> 16) & 0xFFFF;
        for (int line = firstRow * 8; line < (firstRow + count) * 8; line++) scroll[line] = value;
    }

    /** {@code DefaultBGScroll} (scddisasm/src/Special Stage/Main.asm:1489-1497). */
    private void defaultBgScroll() {
        int d0 = speed(0x40000, 0)[0];
        scrollOffset[3] += d0;
        d0 >>= 1;
        scrollOffset[2] += d0;
        d0 >>= 1;
        scrollOffset[1] += d0;
    }

    /** {@code GetScrollSpeed} (scddisasm/src/Special Stage/Main.asm:1510-1521): left keeps the sign, right negates, else zero. */
    private int[] speed(int d0, int d1) {
        if ((scrollFlags & 4) != 0) return new int[] {d0, d1};
        if ((scrollFlags & 8) != 0) return new int[] {-d0, -d1};
        return new int[] {0, 0};
    }

    /** {@code BGScroll1}/{@code 2}/{@code 3} (scddisasm/src/Special Stage/Main.asm:1068-1281) for the line-scroll stages. */
    void bgScroll(int section, boolean stageOver) {
        if (stageOver || scrollDisable) return;
        switch (stage) {
            case 0 -> ss1(section);
            case 4 -> ss5(section);
            case 5 -> ss6(section);
            default -> { }
        }
    }

    private void ss1(int section) {
        switch (section) {
            case 1 -> {
                int d0 = speed(0x80000, 0)[0];
                scrollOffset[2] += d0;
                scrollOffset[1] += d0 >> 1;
                if (bgScrollMode == 0) {
                    ss1Shift++;
                    if (ss1Shift >= 0x200) { ss1Shift = 0x1FF; bgScrollMode = 1; }
                } else {
                    ss1Shift--;
                    if (ss1Shift < 0) { ss1Shift = 0; bgScrollMode = 0; }
                }
                applySs1(0, 0x10000, 0, 24);
            }
            case 2 -> applySs1(24, 0xA000, scrollOffset[2] >> 16, 72);
            default -> applySs1(96, -0x8000, scrollOffset[1] >> 16, 32);
        }
    }

    /** {@code ApplyBGScroll_SS1} (scddisasm/src/Special Stage/Main.asm:1366-1400). */
    private void applySs1(int line, int d0, int planeA, int count) {
        for (int i = 0; i < count; i++, line++) {
            bgHScroll[line * 2] = planeA & 0xFFFF;
            bgHScrollFrac[line * 2] = 0;
            int value = (bgHScroll[line * 2 + 1] << 16) | bgHScrollFrac[line * 2 + 1];
            value = bgScrollMode == 0 ? value + d0 : value - d0;
            bgHScrollFrac[line * 2 + 1] = value & 0xFFFF;
            bgHScroll[line * 2 + 1] = (value >>> 16) & 0xFFFF;
            d0 -= 0x400;
        }
    }

    private void ss5(int section) {
        short[] speeds = assets.wordTable("SS5BGScrollSpeeds");
        if (section == 1) {
            scrollOffset[1] += speed(0x40000, 0)[0];
            bgScrollMode = 0;
            if ((scrollFlags & 4) != 0) {
                bgScrollMode = 1;
                ss5ShiftOrWave = (ss5ShiftOrWave + 1) & 0xF;
                if (ss5ShiftOrWave == 0) bgScrollMode = 2;
            }
            if ((scrollFlags & 8) != 0) {
                bgScrollMode = -1;
                ss5ShiftOrWave = (ss5ShiftOrWave - 1) & 0xF;
                if (ss5ShiftOrWave == 0) bgScrollMode = 2;
            }
            for (int line = 0; line < 17; line++) { bgHScroll[line * 2] = 0; bgHScroll[line * 2 + 1] = 0; bgHScrollFrac[line * 2] = 0; bgHScrollFrac[line * 2 + 1] = 0; }
            applySs5(17, 7, 0, speeds, 0);
        } else if (section == 2) {
            applySs5(24, 56, scrollOffset[1] >> 16, speeds, 7);
        } else {
            applySs5(80, 48, scrollOffset[1] >> 16, speeds, 63);
        }
    }

    /** {@code ApplyBGScroll_SS5} (scddisasm/src/Special Stage/Main.asm:1413-1458). */
    private void applySs5(int line, int count, int planeA, short[] speeds, int speedIndex) {
        if (bgScrollMode == 0) return;
        for (int i = 0; i < count; i++, line++) {
            bgHScroll[line * 2] = planeA & 0xFFFF;
            bgHScrollFrac[line * 2] = planeA & 0xFFFF;
            if (bgScrollMode == 2) {
                bgHScroll[line * 2 + 1] = 0xFF80;
                bgHScrollFrac[line * 2 + 1] = 0;
                continue;
            }
            int sp = ((speeds[(speedIndex + i) * 2] & 0xFFFF) << 16) | (speeds[(speedIndex + i) * 2 + 1] & 0xFFFF);
            int value = (bgHScroll[line * 2 + 1] << 16) | bgHScrollFrac[line * 2 + 1];
            value = bgScrollMode > 0 ? value + sp : value - sp;
            bgHScrollFrac[line * 2 + 1] = value & 0xFFFF;
            bgHScroll[line * 2 + 1] = (value >>> 16) & 0xFFFF;
        }
    }

    private void ss6(int section) {
        short[] waveWords = assets.wordTable("SS6BGWave");
        int planeA = 0;
        int count;
        int line;
        if (section == 1) {
            scrollOffset[1] += speed(0x40000, 0)[0];
            ss5ShiftOrWave = (ss5ShiftOrWave + 1) & 0x7F;
            line = 0;
            count = 24;
        } else {
            planeA = scrollOffset[1] >> 16;
            line = section == 2 ? 24 : 80;
            count = section == 2 ? 56 : 48;
        }
        // ApplyBGScroll_SS6 (scddisasm/src/Special Stage/Main.asm:1470-1483): byte wave, sign-extended.
        int d2 = ss5ShiftOrWave;
        for (int i = 0; i < count; i++, line++) {
            bgHScroll[line * 2] = planeA & 0xFFFF;
            int b = (waveWords[d2 >> 1] >> ((d2 & 1) == 0 ? 8 : 0)) & 0xFF;
            bgHScroll[line * 2 + 1] = ((byte) b) & 0xFFFF;
            d2 = (d2 + 1) & 0x7F;
        }
        ss5ShiftOrWave = d2;
    }

    // ---- HUD (Main.asm:2976-3098) ----

    /** {@code DrawRingCount} and {@code DrawUFOCount}. */
    void drawRingsAndUfos(int rings, int ufos) {
        drawNumber(0xC0B6, rings & 0xFFFF, 3);
        drawNumber(0xC08A, ufos & 0xFF, 2);
    }

    /** {@code DrawTimer}: three digits of seconds, or Time Attack minutes, seconds and hundredths. */
    void drawTimer(int timer) {
        if (!flags.timeAttack()) {
            drawNumber(0xC0A0, timer, 3);
            return;
        }
        drawNumber(0xC09A, (timer >>> 16) & 0xFF, 2);
        drawNumber(0xC0A0, (timer >>> 8) & 0xFF, 2);
        drawNumber(0xC0A6, (timer & 0xFF) * 100 / 60, 2);
    }

    /** {@code DrawNumber}: digit by repeated subtraction, top tile at the row, bottom tile below. */
    private void drawNumber(int vram, int value, int digits) {
        long remaining = value & 0xFFFFFFFFL;
        for (int d = digits - 1; d >= 0; d--) {
            long unit = (long) Math.pow(10, d);
            int digit = 0;
            while (remaining >= unit) { remaining -= unit; digit++; }
            int cell = ((vram - 0xC000) >> 1) + (digits - 1 - d);
            planeA[cell] = numUpper[digit] & 0xFFFF;
            planeA[cell + 64] = numLower[digit] & 0xFFFF;
        }
    }

    // ---- palette ----

    /**
     * {@code LoadTimeStonePal} (scddisasm/src/Special Stage/Main.asm:574-594): 20 passes after the last UFO, the stage's Time
     * Stone colours replace palette entries $31-$35.
     */
    void loadTimeStonePalette(int ufoCount) {
        if (ufoCount != 0 || timeStonePalDelay == 0) return;
        if (--timeStonePalDelay != 0) return;
        short[] table = assets.wordTable("TimeStonePalettes");
        for (int i = 0; i < 5; i++) palette[0x31 + i] = table[stage * 5 + i] & 0xFFFF;
        cramPending = true;
    }

    /** {@code PaletteCycle} (scddisasm/src/Special Stage/Main.asm:1741-2131), run in V-blank routines 1 and 3. */
    void paletteCycle() {
        switch (stage) {
            case 0 -> {
                int row = incValue(0, 8, 0, 1000) & 0xFFF0;
                byte[] t = assets.raw("PalCycle_SS1.Cycle");
                for (int i = 0; i < 16; i++) {
                    int c = t[row + i] & 0xFF;
                    palette[0x20 + i] = c < 0x10 ? c : c << 4;
                }
            }
            case 1 -> {
                int d0 = cycle[0];
                cycle[0] = (cycle[0] + 2) & 7;
                short[] t = assets.wordTable("PalCycle_SS2.Cycle");
                for (int i = 0; i < 4; i++) palette[0x1A + i] = t[d0 / 2 + i] & 0xFFFF;
            }
            case 2 -> {
                int d0 = incValue(0, 2, 0, 0x2E) & 0xFFF8;
                short[] t1 = assets.wordTable("PalCycle_SS3.Cycle1");
                for (int i = 0; i < 4; i++) palette[0x1A + i] = t1[d0 / 2 + i] & 0xFFFF;
                if (incValue(1, 1, 0, 4) == 0) {
                    palette[0x21] = assets.wordTable("PalCycle_SS3.Cycle2")[incValue(2, 2, 0, 0xA) / 2] & 0xFFFF;
                }
            }
            case 3 -> {
                int d0 = cycle[0] & 0x18;
                cycle[0] = (cycle[0] + 2) & 0xFFFF;
                short[] t1 = assets.wordTable("PalCycle_SS4.Cycle1");
                for (int i = 0; i < 4; i++) palette[0x1A + i] = t1[d0 / 2 + i] & 0xFFFF;
                if (incValue(1, 1, 0, 2) == 0) {
                    int i2 = cycle[2] & 0xE;
                    cycle[2] = (cycle[2] + 2) & 0xFFFF;
                    palette[0x28] = assets.wordTable("PalCycle_SS4.Cycle2")[i2 / 2] & 0xFFFF;
                }
            }
            case 4 -> {
                palette[0x1A] = assets.wordTable("PalCycle_SS5.Cycle1")[incValue(0, 2, 0, 0xC) / 2] & 0xFFFF;
                palette[0x2D] = assets.wordTable("PalCycle_SS5.Cycle2")[incValue(1, 2, 0, 0x32) / 2] & 0xFFFF;
                palette[0x2E] = assets.wordTable("PalCycle_SS5.Cycle3")[(incValue(2, 1, 0, 0x33) & 0xFFFE) / 2] & 0xFFFF;
                palette[0x2F] = assets.wordTable("PalCycle_SS5.Cycle4")[(incValue(3, 1, 0, 0x37) & 0xFFFE) / 2] & 0xFFFF;
            }
            case 5 -> {
                int d0 = incValue(0, 2, 0, 0x18);
                short[] t1 = assets.wordTable("PalCycle_SS6.Cycle1");
                for (int i = 0; i < 13; i++) palette[0x21 + i] = t1[d0 / 2 + i] & 0xFFFF;
                int d1 = incValue(1, 2, 0, 0x16) & 0xFFFC;
                short[] t2 = assets.wordTable("PalCycle_SS6.Cycle2");
                palette[0x1D] = t2[d1 / 2] & 0xFFFF;
                palette[0x1E] = t2[d1 / 2 + 1] & 0xFFFF;
            }
            case 6 -> palette[0x1F] = assets.wordTable("PalCycle_SS7.Cycle")[(incValue(0, 1, 0, 0x17) & 0xFFFE) / 2] & 0xFFFF;
            default -> { return; } // PalCycle_SS8: rts, no CRAM request
        }
        cramPending = true;
    }

    /** {@code IncValue} (scddisasm/src/Special Stage/Main.asm:2960-2973): add, wrap to the minimum past the maximum (unsigned). */
    private int incValue(int slot, int add, int min, int max) {
        int value = (cycle[slot] + add) & 0xFFFF;
        if (Integer.compareUnsigned(value, max) > 0) value = min;
        cycle[slot] = value;
        return value;
    }

    /** The sprite table as the program's list holds it, in list order. */
    final java.util.List<CdSpecialSprites.Entry> spriteTable = new ArrayList<>();

    /**
     * {@code CopySprites} (scddisasm/src/Special Stage/Main.asm:1013-1016): the sub-CPU's sprite list becomes the sprite table
     * and Sonic's $300 art bytes go to tiles $5E0-$5F7 ($BC00). The engine list follows the links
     * from entry 0, converted to screen coordinates.
     */
    void copySprites(java.util.List<CdSpecialSprites.Entry> entries, byte[] sonicArt) {
        spriteTable.clear();
        spriteTable.addAll(entries);
        System.arraycopy(sonicArt, 0, tiles, 0xBC00, sonicArt.length);
        dirty.set(0x5E0, 0x5F8);
        sprites.clear();
        for (CdSpecialSprites.Entry e : entries) {
            sprites.add(new CdProgramScreenRenderer.Sprite((e.x() & 0x1FF) - 128, (e.y() & 0x3FF) - 128, e.size(), e.tile()));
        }
    }

    /** The frame for {@link CdProgramScreenRenderer}. */
    public CdProgramScreenRenderer.Frame frame() {
        int[] a = scrollA.clone(), b = scrollB.clone();
        return new CdProgramScreenRenderer.Frame(tiles, dirty, planeA, planeB, cram,
                line -> a[line], line -> b[line], List.copyOf(sprites), WIDTH, display);
    }
}
