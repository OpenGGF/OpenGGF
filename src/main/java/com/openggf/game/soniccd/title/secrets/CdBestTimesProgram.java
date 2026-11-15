package com.openggf.game.soniccd.title.secrets;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.fmv.CdFmvAssets;
import com.openggf.game.soniccd.fmv.CdProgramScreenState;

/**
 * Best staff times (DUMMY4.MMD, {@code Title Screen/Secrets/Best Staff Times.asm}), opened by the title code Right
 * Right Up Up Down C (Objects/Menu/Main.asm:436-470): the team's times as text on a 32 x 128-cell H32 plane, which
 * Up and Down scroll two lines a frame within 0-$1FF, until Start fades to black and IPX returns to
 * {@code .GameLoop} ({@code BestStaffTimes}, IPX.asm:91-95).
 */
public final class CdBestTimesProgram {
    private enum Phase { FADE_IN, MAIN, FADE_OUT, DONE }

    private final CdSecretScreen screen = new CdSecretScreen();
    private Phase phase = Phase.FADE_IN;
    private int step;
    private int scroll;                                    // scrollPos (:76-77), in the program image
    private boolean firstPass = true;

    public CdBestTimesProgram(CdFmvAssets assets) {
        // Start (:23-71): InitMD, scroll table, art, background (DrawTilemapH32), both text lists, palette.
        // VDPRegs (:471-491): H32, 32 x 128-cell planes, plane A $C000, plane B $E000, scroll table $B400 (cleared).
        screen.initMd(256, 32, 128);
        screen.screen.setHScroll(CdProgramScreenState.Plane.A, 0);
        screen.screen.setHScroll(CdProgramScreenState.Plane.B, 0);
        byte[] art = CdSecretScreen.nemesis(assets.bytes("artBestTimes"));
        screen.screen.writeTiles(0, art, 0, art.length);         // NemDec from VRAM 0
        byte[] map = assets.bytes("mapBestTimesBg");
        screen.drawMap(CdProgramScreenState.Plane.B, map, 0x20, 0x1C);   // DrawTilemapH32 at $E000
        byte[] file = assets.data().file(CdNativeFile.STAFFTIMES);
        byte[] upper = assets.bytes("bestTimesTilesUpper"), lower = assets.bytes("bestTimesTilesLower");
        drawList(file, assets.offset("bestTimesHeader"), 0, upper, lower);
        drawList(file, assets.offset("bestTimesList"), 0x6000, upper, lower);
        byte[] pal = assets.bytes("palBestTimes");
        for (int i = 0; i < 64; i++) screen.palette[i] = CdSecretScreen.word(pal, 2 * i);
        screen.requestCram();
        screen.prepFade(false);
        screen.vsync();
    }

    /** {@code DrawText} over a TEXTSTART list: strings found through their pointers (MMD origin $FF2000). */
    private void drawList(byte[] file, int at, int base, byte[] upper, byte[] lower) {
        int origin = ((file[3] & 0xFF) << 16) | ((file[4] & 0xFF) << 8) | (file[5] & 0xFF);
        int count = CdSecretScreen.word(file, at) + 1;
        for (int e = 0; e < count; e++) {
            int entry = at + 2 + 12 * e;
            int pointer = ((file[entry + 1] & 0xFF) << 16) | ((file[entry + 2] & 0xFF) << 8) | (file[entry + 3] & 0xFF);
            // The entry's top-row write command names plane A ($C000, H32: $40 bytes a row) column and row.
            int top = command(file, entry + 4) - 0xC000;
            int x = (top & 0x3F) / 2, y = top / 0x40;
            for (int s = pointer - origin + 0x100, i = 0; (file[s] & 0xFF) != 0xFF; s++, i++) {
                int c = file[s] & 0xFF;
                screen.textCell(x + i, y, ((upper[c] & 0xFF) + base) & 0xFFFF);
                screen.textCell(x + i, y + 1, ((lower[c] & 0xFF) + base) & 0xFFFF);
            }
        }
    }

    /** A VRAM write command's address. */
    private static int command(byte[] file, int at) {
        int hi = CdSecretScreen.word(file, at), lo = CdSecretScreen.word(file, at + 2);
        return (hi & 0x3FFF) | ((lo & 3) << 14);
    }

    /** One V-int (with {@code scrollPos} to VSRAM A, :441-443) and the program up to its next VSync. */
    public void frame(int pad) {
        boolean vint = screen.pendingVsync();
        screen.vInterrupt(pad);
        if (vint) screen.screen.setVScroll(CdProgramScreenState.Plane.A, scroll);   // scrollPos to VSRAM 0
        switch (phase) {
            case FADE_IN -> {
                screen.fadeStep(CdSecretScreen.Fade.FROM_BLACK, step++);
                if (step == CdSecretScreen.FADE_STEPS) phase = Phase.MAIN;
            }
            case MAIN -> {
                if (!firstPass && (screen.hold & CdSecretScreen.START) != 0) {
                    phase = Phase.FADE_OUT;
                    step = 0;
                    screen.fadeStep(CdSecretScreen.Fade.TO_BLACK, step++);
                    return;
                }
                firstPass = false;
                if ((screen.hold & CdSecretScreen.DOWN) != 0) scroll = Math.min(scroll + 2, 0x1FF);
                if ((screen.hold & CdSecretScreen.UP) != 0) scroll = Math.max(scroll - 2, 0);
                screen.vsync();
            }
            case FADE_OUT -> {
                if (step == CdSecretScreen.FADE_STEPS) { phase = Phase.DONE; return; }
                screen.fadeStep(CdSecretScreen.Fade.TO_BLACK, step++);
            }
            case DONE -> { }
        }
    }

    public boolean done() { return phase == Phase.DONE; }

    public int scroll() { return scroll; }

    public CdProgramScreenState screen() { return screen.screen; }
}
