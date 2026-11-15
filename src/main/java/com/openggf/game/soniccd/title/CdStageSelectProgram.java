package com.openggf.game.soniccd.title;

import com.openggf.data.compression.NemesisReader;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.program.CdProgramData;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer;
import com.openggf.game.soniccd.program.CdStageSelection;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.util.BitSet;
import java.util.List;

/**
 * The stage-select secret, STSEL_.MMD ({@code Title Screen/Secrets/Stage Select.asm}), as game
 * logic, one call of {@link #frame} per native V-blank. It returns the selected {@code StageSels}
 * row to IPX. Its data (art, background map, palette, character tables and the selection strings)
 * is read from the disc file. Fades follow {@code Secrets/Functions.asm}.
 */
public final class CdStageSelectProgram {
    public enum Phase { BOOT, FADE_IN, MAIN, FADE_OUT, DONE }

    /** {@code TEXTPTR ..., 14, 13} and {@code ..., 14, 9}: plane A cells of the selection and the header. */
    private static final int TEXT_X = 14, SELECTION_Y = 13, HEADER_Y = 9;
    /** Selection text base tile {@code move.w #$6000,d0} (palette line 3) (Stage Select.asm MainLoop). */
    private static final int SELECTION_BASE = 0x6000;
    /** Three channels of eight steps, each followed by VSync (Functions.asm FadeToBlack/FadeFromBlack). */
    private static final int FADE_STEPS = 24;
    private static final int CLEAR_WIDTH = 10;                   // TextDat_Clear: ten spaces

    private final byte[] vram = new byte[0x10000];
    private final BitSet dirty = new BitSet(0x800);
    private final int[] planeA = new int[64 * 32];
    private final int[] planeB = new int[64 * 32];
    private final int[] palette = new int[64];
    private final int[] fadePalette = new int[64];
    private final byte[] tilesUpper, tilesLower;
    private final byte[][] selectionText = new byte[CdStageSelection.MENU_ENTRIES][];
    private final byte[] headerText;
    private final byte[] background;
    private final int[] paletteData;
    private final byte[] art;

    private Phase phase = Phase.BOOT;
    private int step;
    private int selectDelay;
    private int selectId;
    private int held;
    private boolean firstIteration = true;

    public CdStageSelectProgram(CdProgramData data) {
        tilesUpper = data.bytes("tilesUpper");
        tilesLower = data.bytes("tilesLower");
        byte[] file = data.file(CdNativeFile.STAGESEL);
        // MMD origin (header long at 2); a string pointer A lies at file offset A - origin + $100.
        int origin = ((file[3] & 0xFF) << 16) | ((file[4] & 0xFF) << 8) | (file[5] & 0xFF);
        int table = data.offset("textSelections");
        for (int i = 0; i < selectionText.length; i++) {
            int entry = table + 14 * i;                              // dc.w 0, dc.l string, dc.l top, dc.l bottom
            int pointer = ((file[entry + 3] & 0xFF) << 16) | ((file[entry + 4] & 0xFF) << 8) | (file[entry + 5] & 0xFF);
            selectionText[i] = string(file, pointer - origin + 0x100);
        }
        headerText = string(file, data.offset("textStageSelect"));
        background = data.bytes("mapSelectBg");
        paletteData = CdTitleAssets.words(data.bytes("palSelect"));
        try {
            art = NemesisReader.decompress(Channels.newChannel(new ByteArrayInputStream(data.from("artSelect"))));
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot decode the stage-select art", failure);
        }
    }

    private static byte[] string(byte[] file, int at) {
        int end = at;
        while ((file[end] & 0xFF) != 0xFF) end++;
        return java.util.Arrays.copyOfRange(file, at, end);
    }

    /** One V-blank (controller read, Functions.asm ReadController) then the program up to its next VSync. */
    public void frame(int p1) {
        if (phase != Phase.BOOT) held = p1 & 0xFF;
        switch (phase) {
            case BOOT -> boot();
            case FADE_IN -> {
                fadeFromBlackStep(step++);
                if (step == FADE_STEPS) phase = Phase.MAIN;
            }
            case MAIN -> {
                // After each VSync: move.b ctrlHold,d0; andi.b #%11110000 (Stage Select.asm MainLoop).
                if (!firstIteration && (held & 0xF0) != 0) {
                    phase = Phase.FADE_OUT;
                    step = 0;
                    fadeToBlackStep(step++);
                    return;
                }
                firstIteration = false;
                iterate();
            }
            case FADE_OUT -> {
                fadeToBlackStep(step++);
                if (step == FADE_STEPS) phase = Phase.DONE;
            }
            case DONE -> { }
        }
    }

    /** Start: art at VRAM 0, the H40 background at $E000, the header, the palette, then VSync. */
    private void boot() {
        System.arraycopy(art, 0, vram, 0, Math.min(art.length, vram.length));
        dirty.set(0, 0x800);
        for (int y = 0; y < 28; y++) for (int x = 0; x < 40; x++) {
            int at = 2 * (y * 40 + x);
            planeB[y * 64 + x] = ((background[at] & 0xFF) << 8) | (background[at + 1] & 0xFF);
        }
        drawText(headerText, HEADER_Y, 0);
        System.arraycopy(paletteData, 0, fadePalette, 0, 64);    // .LoadPal then PrepFadeFromBlack
        phase = Phase.FADE_IN;
        step = 0;
    }

    /** One MainLoop pass up to its VSync. */
    private void iterate() {
        selectDelay = (selectDelay + 1) & 0xFFFF;
        if ((selectDelay & 7) == 0) {
            int max = CdStageSelection.MENU_ENTRIES - 1;
            if ((held & 1) != 0) selectId = selectId - 1 < 0 ? max : selectId - 1;   // DecValueW, wrap to max
            if ((held & 2) != 0) selectId = selectId + 1 > max ? 0 : selectId + 1;   // IncValueW, wrap to min
        }
        drawText(new byte[CLEAR_WIDTH], SELECTION_Y, 0);
        drawText(selectionText[selectId], SELECTION_Y, SELECTION_BASE);
    }

    /** DrawText: two plane A rows per character from .TilesUpper/.TilesLower plus the base. */
    private void drawText(byte[] text, int row, int base) {
        for (int i = 0; i < text.length; i++) {
            int c = text[i] & 0xFF;
            planeA[row * 64 + TEXT_X + i] = ((tilesUpper[c] & 0xFF) + base) & 0xFFFF;
            planeA[(row + 1) * 64 + TEXT_X + i] = ((tilesLower[c] & 0xFF) + base) & 0xFFFF;
        }
    }

    /** FadeFromBlack: blue, green then red, intensity 0, 2 .. 14, capped at the target channel. */
    private void fadeFromBlackStep(int n) {
        int shift = n < 8 ? 8 : n < 16 ? 4 : 0;
        int intensity = (n & 7) * 2;
        for (int i = 0; i < 64; i++) {
            int target = (fadePalette[i] >> shift) & 0xE;
            palette[i] = (palette[i] & ~(0xE << shift)) | (Math.min(target, intensity) << shift);
        }
    }

    /** FadeToBlack: red, green then blue, subtracting intensity 0, 2 .. 14 with a floor of zero. */
    private void fadeToBlackStep(int n) {
        int shift = n < 8 ? 0 : n < 16 ? 4 : 8;
        int intensity = (n & 7) * 2;
        for (int i = 0; i < 64; i++) {
            int channel = Math.max(0, ((palette[i] >> shift) & 0xE) - intensity);
            palette[i] = (palette[i] & ~(0xE << shift)) | (channel << shift);
        }
    }

    public Phase phase() { return phase; }
    public boolean done() { return phase == Phase.DONE; }
    public int selection() { return selectId; }
    public int iterations() { return selectDelay; }

    public CdProgramScreenRenderer.Frame screen() {
        return new CdProgramScreenRenderer.Frame(vram, dirty, planeA, planeB, palette, line -> 0, line -> 0,
                List.of(), 320, phase != Phase.BOOT);
    }
}
