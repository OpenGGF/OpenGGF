package com.openggf.game.soniccd.title.secrets;

import com.openggf.game.soniccd.fmv.CdFmvAssets;
import com.openggf.game.soniccd.fmv.CdProgramScreenState;

/**
 * The five hidden pictures the sound test's codes open (NISI.MMD, DUMMY0-3.MMD, one source,
 * {@code Title Screen/Secrets/Sound Test Image.asm} built with {@code EASTEREGG} 0-4): the picture fades in from
 * white, waits for Start held, fades to black and returns to IPX, which returns to {@code .GameLoop}
 * (the sound test exits 2-6, IPX.asm:1083-1121).
 */
public final class CdSecretImageProgram {
    /** The pictures in exit-code order 2-6. */
    public enum Picture { FUN_IS_INFINITE("FunIsInfinite"), MC_SONIC("McSonic"), TAILS("Tails"), BATMAN("Batman"), CUTE_SONIC("CuteSonic");
        final String asset;
        Picture(String asset) { this.asset = asset; }
    }

    private enum Phase { FADE_IN, WAIT, FADE_OUT, DONE }

    private final CdSecretScreen screen = new CdSecretScreen();
    private Phase phase;
    private int step;

    public CdSecretImageProgram(CdFmvAssets assets, Picture picture) {
        // Start (Sound Test Image.asm:23-60). VDPRegs (:122-142): H40, 64 x 32-cell planes, plane A $C000, plane B
        // $E000, the scroll table at $F400 (cleared: both planes' horizontal scroll 0).
        screen.initMd(320, 64, 32);
        screen.screen.setHScroll(CdProgramScreenState.Plane.A, 0);
        screen.screen.setHScroll(CdProgramScreenState.Plane.B, 0);
        byte[] art = CdSecretScreen.nemesis(assets.bytes("art" + picture.asset));
        screen.screen.writeTiles(0, art, 0, art.length);         // NemDec from VRAM 0
        screen.drawMap(CdProgramScreenState.Plane.B, assets.bytes("map" + picture.asset), 0x28, 0x1C);   // $E000
        byte[] pal = assets.bytes("pal" + picture.asset);
        for (int i = 0; i < 64; i++) screen.palette[i] = CdSecretScreen.word(pal, 2 * i);
        screen.requestCram();
        screen.prepFade(true);
        screen.vsync();
        phase = Phase.FADE_IN;
        step = 0;
    }

    /** One V-int and the program up to its next VSync. */
    public void frame(int pad) {
        screen.vInterrupt(pad);
        switch (phase) {
            case FADE_IN -> {
                screen.fadeStep(CdSecretScreen.Fade.FROM_WHITE, step++);
                if (step == CdSecretScreen.FADE_STEPS) phase = Phase.WAIT;
            }
            case WAIT -> {
                // .Loop: VSync, then btst #7,ctrlHold (Sound Test Image.asm:62-66). The first pass VSyncs first.
                if (step++ > CdSecretScreen.FADE_STEPS && (screen.hold & CdSecretScreen.START) != 0) {
                    phase = Phase.FADE_OUT;
                    step = 0;
                    screen.fadeStep(CdSecretScreen.Fade.TO_BLACK, step++);
                    return;
                }
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

    public CdProgramScreenState screen() { return screen.screen; }
}
