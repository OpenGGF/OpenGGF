package com.openggf.game.soniccd.title.secrets;

import com.openggf.data.compression.NemesisReader;
import com.openggf.game.soniccd.fmv.CdProgramScreenState;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;

/**
 * What the title's secret programs share ({@code Title Screen/Secrets/Functions.asm} and each program's V-int):
 * video memory, the 64-colour palette and its fade buffer, the VSync and CRAM requests, the pad, and the four
 * fades, each three channels of eight steps with a VSync after each step.
 */
final class CdSecretScreen {
    /** Fades of {@code Functions.asm}: FadeToBlack :11-79, FadeFromBlack :101-172, FadeToWhite :175-237, FadeFromWhite :259-336. */
    enum Fade { TO_BLACK, FROM_BLACK, TO_WHITE, FROM_WHITE }

    static final int FADE_STEPS = 24;
    static final int START = 0x80, A = 0x40, C = 0x20, B = 0x10, RIGHT = 0x08, LEFT = 0x04, DOWN = 0x02, UP = 0x01;

    final CdProgramScreenState screen = new CdProgramScreenState();
    final int[] palette = new int[64];
    final int[] fadePalette = new int[64];
    private boolean vsync, cramRequest;
    int hold, tap;

    /**
     * {@code InitMD} (Functions.asm:417-460) with the program's {@code VDPRegs}, which each caller translates to a
     * screen width and plane size: display off (register 1 cache $8134), VRAM (tiles and planes) and VSRAM cleared.
     */
    void initMd(int screenWidth, int planeColumns, int planeRows) {
        screen.setLayout(screenWidth, planeColumns, planeRows);
        screen.setDisplay(false);
        screen.clearTilesAndPlanes();
        screen.setVScroll(CdProgramScreenState.Plane.A, 0);
        screen.setVScroll(CdProgramScreenState.Plane.B, 0);
    }

    /** The programs' V-int: only after a VSync request; display on; CRAM when asked; the pad (ReadController :924-952). */
    void vInterrupt(int pad) {
        if (!vsync) return;
        vsync = false;
        screen.setDisplay(true);
        if (cramRequest) {
            cramRequest = false;
            for (int i = 0; i < 64; i++) screen.setPalette(i, palette[i]);
        }
        tap = pad & ~hold & 0xFF;
        hold = pad & 0xFF;
    }

    void vsync() { vsync = true; }

    /** Whether the next V-int runs its body (a VSync is pending). */
    boolean pendingVsync() { return vsync; }

    void requestCram() { cramRequest = true; }

    /** {@code PrepFadeFromBlack}/{@code PrepFadeFromWhite}: the palette to the fade buffer, the palette to black or white. */
    void prepFade(boolean white) {
        for (int i = 0; i < 64; i++) {
            fadePalette[i] = palette[i];
            palette[i] = white ? 0x0EEE : 0;
        }
        cramRequest = true;
    }

    /** Step {@code n} (0-23) of a fade, then its VSync. */
    void fadeStep(Fade fade, int n) {
        int channel = n / 8, step = n % 8;
        boolean blueFirst = fade == Fade.FROM_BLACK;
        int shift = blueFirst ? 8 - 4 * channel : 4 * channel;        // red 0, green 4, blue 8
        int mask = 0xE << shift;
        for (int i = 0; i < 64; i++) {
            int current = (palette[i] >> shift) & 0xE, target = (fadePalette[i] >> shift) & 0xE, value;
            switch (fade) {
                case TO_BLACK -> value = Math.max(0, current - 2 * step);
                case FROM_BLACK -> value = Math.min(target, 2 * step);
                case TO_WHITE -> value = Math.min(0xE, current + 2);
                default -> value = Math.max(target, 0xE - 2 * step);
            }
            palette[i] = (palette[i] & ~mask & 0x0EEE) | (value << shift);
        }
        cramRequest = true;
        vsync = true;
    }

    static int word(byte[] data, int at) { return ((data[at] & 0xFF) << 8) | (data[at + 1] & 0xFF); }

    static byte[] nemesis(byte[] data) {
        try {
            return NemesisReader.decompress(Channels.newChannel(new ByteArrayInputStream(data)));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** {@code DrawTilemapH64}/{@code H32}: {@code width} x {@code height} words into a plane from cell 0, row 0. */
    void drawMap(CdProgramScreenState.Plane plane, byte[] map, int width, int height) {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            screen.setPlaneCell(plane, x, y, word(map, 2 * (y * width + x)));
    }

    /** {@code DrawText}'s cell for a TEXTPTR position: plane A ($C000) column {@code x}, row {@code y}. */
    void textCell(int x, int y, int word) { screen.setPlaneCell(CdProgramScreenState.Plane.A, x, y, word); }
}
