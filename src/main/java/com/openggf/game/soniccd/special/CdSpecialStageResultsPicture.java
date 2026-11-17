package com.openggf.game.soniccd.special;

import com.openggf.game.soniccd.program.CdProgramScreenRenderer;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * The special stage results picture as the main-CPU program builds it ({@code Results},
 * scddisasm/src/Special Stage/Main.asm:281-357): a tile store, both planes and the palette, in the
 * shape {@link CdProgramScreenRenderer} draws. There is no VDP model; each method names the
 * routine it reimplements.
 *
 * <p>Layout ({@code ResultsVDPRegs}, scddisasm/src/Special Stage/Main.asm:3518-3537): H40, plane A at
 * $C000 and B at $E000, 64 x 32 cells, backdrop colour 0. {@code InitMD} clears VRAM and VSRAM
 * (Main.asm:3483-3486), so the horizontal scroll and the sprite table are zero; {@code Results}
 * then writes VSRAM long $00100000 (Main.asm:326-327): plane A scrolls down 16 lines, plane B 0.
 */
final class CdSpecialStageResultsPicture {
    static final int WIDTH = 320;
    /** {@code move.l #$100000,VDPDATA} to VSRAM 0 (Main.asm:327): the plane A word. */
    private static final int VSCROLL_A = 0x10;
    /** Header tilemaps by {@link CdSpecialStageResults.Header} ordinal ({@code DrawResultsBase}, Main.asm:495-503). */
    private static final int[] HEADER_MAPS = {0x3D, 0x3E, 0x3F};
    /** Time Stone tilemaps $29-$2F, one per {@code timeStones} bit (Main.asm:505-548). */
    private static final int FIRST_STONE_MAP = 0x29;
    /** {@code .DrawSmallText}: {@code move.l #$33323130,d5} and {@code #$363534} (Main.asm:550-554). */
    private static final int[] SMALL_TEXT_MAPS = {0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36};
    /**
     * {@code DrawResultsScore}, {@code DarwResRingBonus}, {@code DrawResTimeBonus} (Main.asm:2777-2823): the
     * top-row VRAM address of each 8-digit number ($C730, $C8B0, $CA30), bottom row one plane row lower.
     */
    private static final int SCORE_VRAM = 0xC730, RING_BONUS_VRAM = 0xC8B0, TIME_BONUS_VRAM = 0xCA30;
    private static final int DIGITS = 8;

    private final CdSpecialStageAssets assets;
    private final int stage;
    private final byte[] tiles = new byte[0x10000];
    private final BitSet dirty = new BitSet(0x800);
    private final int[] planeA = new int[64 * 32];
    private final int[] planeB = new int[64 * 32];
    private final int[] palette = new int[64];
    private final int[] fadeTarget = new int[64];
    private final short[] numUpper;
    private final short[] numLower;
    private final byte[] iconMaps;
    // DrawExtraPlayers .AnimFrame/.AnimTimer (Main.asm:2768-2771): program data, zero when the program loads.
    private int iconFrame;
    private int iconTimer;

    CdSpecialStageResultsPicture(CdSpecialStageAssets assets, int stage, CdSpecialStageResults.Header header,
                                 int timeStones) throws IOException {
        this.assets = assets;
        this.stage = stage;
        this.numUpper = assets.wordTable("ResultsNumTilesUpper");
        this.numLower = assets.wordTable("ResultsNumTilesLower");
        this.iconMaps = assets.raw("ExtraPlayerIconMaps");
        // Pal_Results, Art_Results at VRAM 0 and (USA) Art_ExtraPlayersText at $4000 (Main.asm:331-346).
        short[] pal = CdSpecialStageAssets.words(assets.raw("Pal_Results"));
        for (int i = 0; i < 64; i++) palette[i] = pal[i] & 0xFFFF;
        art("Art_Results", 0);
        art("Art_ExtraPlayersText", 0x4000);
        drawResultsBase(header, timeStones);
        // PrepFadeFromWhite (Main.asm:2474-2487): the target saved, the palette all white.
        System.arraycopy(palette, 0, fadeTarget, 0, 64);
        Arrays.fill(palette, 0x0EEE);
    }

    private void art(String name, int vram) throws IOException {
        byte[] data = assets.nemesis(name);
        System.arraycopy(data, 0, tiles, vram, data.length);
        dirty.set(vram >> 5, (vram + data.length + 31) >> 5);
    }

    /** {@code DrawResultsBase} (scddisasm/src/Special Stage/Main.asm:495-555). */
    private void drawResultsBase(CdSpecialStageResults.Header header, int timeStones) throws IOException {
        drawTilemap(HEADER_MAPS[header.ordinal()]);
        for (int bit = 0; bit < 7; bit++) if ((timeStones & (1 << bit)) != 0) drawTilemap(FIRST_STONE_MAP + bit);
        for (int id : SMALL_TEXT_MAPS) drawTilemap(id);
    }

    /** {@code DrawTilemaps} (scddisasm/src/Special Stage/Main.asm:3707-3740). */
    private void drawTilemap(int id) throws IOException {
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

    /** One {@code FadeFromWhite} step, 0-23 (scddisasm/src/Special Stage/Main.asm:2493-2565). */
    void fadeFromWhiteStep(int step) {
        CdSpecialStageScreen.fadeFromWhiteStep(palette, fadeTarget, step);
    }

    /**
     * {@code VInt_Results} (scddisasm/src/Special Stage/Main.asm:963-969): score, time bonus and ring bonus
     * (units of ten), then the extra-life icons.
     */
    void vint(int score, int timeBonus, int ringBonus, int extraPlayers) throws IOException {
        drawNumber(SCORE_VRAM, score);
        drawNumber(TIME_BONUS_VRAM, timeBonus);
        drawNumber(RING_BONUS_VRAM, ringBonus);
        drawExtraPlayers(extraPlayers);
    }

    /**
     * {@code DrawResultsNum} (scddisasm/src/Special Stage/Main.asm:2825-2920): the value times ten in eight
     * two-cell digits, leading zeros blank; a zero value is seven blanks and one zero ({@code .DrawZero}).
     */
    private void drawNumber(int vram, int value) {
        int cell = (vram - 0xC000) >> 1;
        long number = (value & 0xFFFFFFFFL) * 10 & 0xFFFFFFFFL;
        boolean lead = false;
        long power = 10_000_000L;
        for (int digit = 0; digit < DIGITS; digit++, power /= 10) {
            int d = (int) (number / power % 10);
            if (number == 0) d = digit == DIGITS - 1 ? 0 : -1;
            else if (!lead && d == 0) d = -1;
            else lead = true;
            planeA[(cell + digit) & 0x7FF] = d < 0 ? 0 : numUpper[d] & 0xFFFF;
            planeA[(cell + 64 + digit) & 0x7FF] = d < 0 ? 0 : numLower[d] & 0xFFFF;
        }
    }

    /**
     * {@code DrawExtraPlayers} (scddisasm/src/Special Stage/Main.asm:2698-2771): every eighth V-blank the frame
     * toggles and up to five icons are drawn in it ({@code .IconMaps} pairs, second frame first).
     */
    private void drawExtraPlayers(int count) throws IOException {
        iconTimer = (iconTimer + 1) & 7;
        if (iconTimer != 0) return;
        iconFrame = (iconFrame + 1) & 1;
        if (count == 0) return;
        int icons = Math.min(count, 5);
        for (int icon = icons - 1; icon >= 0; icon--) drawTilemap(iconMaps[icon * 2 + iconFrame] & 0xFF);
    }

    int colour(int index) { return palette[index]; }

    int planeA(int row, int column) { return planeA[row * 64 + column]; }

    short digitUpper(int digit) { return numUpper[digit]; }

    CdProgramScreenRenderer.Frame frame() {
        return new CdProgramScreenRenderer.Frame(tiles, dirty, planeA, planeB, palette,
                line -> 0, line -> 0, List.of(), WIDTH, true, VSCROLL_A, 0);
    }
}
