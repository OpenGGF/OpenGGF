package com.openggf.game.soniccd.ending;

import com.openggf.game.soniccd.fmv.CdFmvAssets;
import com.openggf.game.soniccd.fmv.CdProgramScreenState;

/**
 * The ending's last image (Presented by SEGA), the code the program jumps to when the video ends
 * ({@code Ending_ImageStartupInitialize}, FMV/Main (Ending).asm:14847-14911, retail $FFC100-$FFC194). It keeps a
 * 64-colour buffer ($FFB200) that its own V-int copies to CRAM when asked ({@code EndingVInterrupt},
 * :14934-14966), fades out what is on screen, draws the image, fades it in, holds it, and fades it out. Each fade
 * moves one colour channel at a time, eight steps a channel, one V-int a step
 * ({@code Ending_ImageInitializeTileBuffer}/{@code ...RestoreTileBuffer}, :15009-15120).
 */
final class CdEndingImage {
    private enum Step { FADE_OUT_SCREEN, FADE_IN, HOLD, FADE_OUT_IMAGE, DONE }

    /** {@code EndingDelay}: 600 V-ints (USA only, :14893-14896). */
    static final int HOLD_VINTS = 0x258;

    private final CdFmvAssets assets;
    private final CdProgramScreenState screen;
    private final int[] buffer = new int[64];              // $FFB200
    private final int[] saved = new int[64];               // $FFB280
    private boolean vsync;                                 // $FF0F00 bit 0
    private boolean cramRequest;                           // $FF0F00 bit 1
    private int delay;
    private int padHold;                                   // ReadControllers into $A1201E
    private Step step;
    private int channel;                                   // 0-2: which channel group of the current fade
    private int level;                                     // d0: 0, 2 ... 14

    CdEndingImage(CdFmvAssets assets, CdProgramScreenState screen) {
        this.assets = assets;
        this.screen = screen;
    }

    /** Entry: the main program cleared the buffer and left the end text's two colours in it (:88-97). */
    void begin() {
        java.util.Arrays.fill(buffer, 0);
        buffer[0x2B] = 0x046A;
        buffer[0x2F] = 0x00EE;
        step = Step.FADE_OUT_SCREEN;
        channel = 0;
        level = 0;
    }

    /** {@code EndingVInterrupt}: only after a VSync request; CRAM from the buffer when asked; pad; the hold count. */
    void vInterrupt(int pad) {
        if (!vsync) return;
        vsync = false;
        screen.setDisplay(true);                           // bset #6,$ff0f17 (the register 1 cache)
        if (cramRequest) {
            cramRequest = false;
            for (int i = 0; i < 64; i++) screen.setPalette(i, buffer[i]);
        }
        padHold = pad;
        if (delay > 0) delay--;
    }

    /** Run until the next {@code Ending_WaitForVBlank}; true when the program returns. */
    boolean step() {
        switch (step) {
            case FADE_OUT_SCREEN -> {
                if (fadeOutStep()) {
                    setUp();
                    step = Step.FADE_IN;
                    channel = 0;
                    level = 0;
                }
            }
            case FADE_IN -> {
                if (fadeInStep()) {
                    delay = HOLD_VINTS;                    // move.w #$258,EndingDelay
                    step = Step.HOLD;
                    vsync = true;
                }
            }
            case HOLD -> {
                // .WaitLoop: after each VSync, end on the count or when Start is held (btst #7,$a1201e).
                if (delay == 0 || (padHold & 0x80) != 0) {
                    step = Step.FADE_OUT_IMAGE;
                    channel = 0;
                    level = 0;
                    return step();
                }
                vsync = true;
            }
            case FADE_OUT_IMAGE -> {
                if (fadeOutStep()) {
                    step = Step.DONE;
                    return true;                           // rts, d0 = $10
                }
            }
            case DONE -> { return true; }
        }
        return false;
    }

    /** One step of {@code Ending_ImageInitializeTileBuffer}: channel groups red, green, blue (d1 = 1, 5, 9). */
    private boolean fadeOutStep() {
        if (channel == 3) return true;
        int shift = 1 + 4 * channel;
        for (int i = 0; i < 64; i++) {
            int d2 = ror(rol(buffer[i], 1), shift);
            int d3 = d2 & 0xEEE0;
            d2 &= 0x0E;
            d2 = d2 - level < 0 ? 0 : d2 - level;
            buffer[i] = rol(ror(d2 | d3, 1), shift);
        }
        cramRequest = true;
        vsync = true;
        advance();
        return false;
    }

    /** One step of {@code Ending_ImageRestoreTileBuffer}: blue, green, red (d1 = 9, 5, 1), each up to its target. */
    private boolean fadeInStep() {
        if (channel == 3) return true;
        int shift = 9 - 4 * channel;
        for (int i = 0; i < 64; i++) {
            int target = ror(rol(saved[i], 1), shift) & 0x0E;
            int rest = ror(rol(buffer[i], 1), shift) & 0xEEE0;
            if (target > level) target = level;
            buffer[i] = ror(rol(target | rest, shift), 1);
        }
        cramRequest = true;
        vsync = true;
        advance();
        return false;
    }

    private void advance() {
        level += 2;
        if (level > 14) {
            level = 0;
            channel++;
        }
    }

    /** $FFC10C-$FFC164: registers, VRAM cleared, the Nemesis art at 0, the map, the palette saved and cleared. */
    private void setUp() {
        // Ending_InitializeVdp (:15247-15298) with EndingVDPRegisters (:14969-14974): display off ($34), H40 ($81),
        // 64 x 32-cell planes ($01), plane A and plane B both at $C000 ($30, $06: one map serves both), the scroll
        // table at $E400 ($39). VRAM cleared (tiles and planes), VSRAM cleared; CRAM is left as it is.
        screen.setLayout(320, 64, 32);
        screen.setDisplay(false);
        screen.clearTilesAndPlanes();
        screen.setVScroll(CdProgramScreenState.Plane.A, 0);
        screen.setVScroll(CdProgramScreenState.Plane.B, 0);
        screen.setHScroll(CdProgramScreenState.Plane.A, 0);       // move.l #0 to the scroll table
        screen.setHScroll(CdProgramScreenState.Plane.B, 0);
        byte[] art = CdEndingProgram.nemesis(assets.bytes("endImageArt"));
        screen.writeTiles(0, art, 0, art.length);                  // the decoder writes from VRAM 0
        byte[] map = assets.bytes("endImageMap");
        // Ending_ImageUploadMap at $C594 ($45940003): cell 10 of rows 11-13, $80 bytes a row; the shared table.
        for (int row = 0; row < 3; row++) for (int c = 0; c < 0x14; c++) {
            int word = CdEndingProgram.word(map, 2 * (row * 0x14 + c));
            screen.setPlaneCell(CdProgramScreenState.Plane.A, 10 + c, 11 + row, word);
            screen.setPlaneCell(CdProgramScreenState.Plane.B, 10 + c, 11 + row, word);
        }
        byte[] palette = assets.bytes("endImagePalette");
        for (int i = 0; i < 64; i++) saved[i] = CdEndingProgram.word(palette, 2 * i);
        java.util.Arrays.fill(buffer, 0);                  // Ending_ImageSnapshotTileBuffer
        cramRequest = true;
        vsync = true;                                      // Ending_WaitForVBlank before the fade-in
    }

    private static int rol(int w, int n) { n &= 15; return ((w << n) | ((w & 0xFFFF) >>> (16 - n))) & 0xFFFF; }

    private static int ror(int w, int n) { n &= 15; return (((w & 0xFFFF) >>> n) | (w << (16 - n))) & 0xFFFF; }
}
