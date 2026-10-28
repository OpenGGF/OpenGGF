package com.openggf.game.sonic3k.titlescreen;

import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import java.io.IOException;
import java.util.Arrays;

import static org.lwjgl.opengl.GL11.glClearColor;

/** Native SK title flow and Sonic/Knuckles selection, over a real standalone cartridge. */
final class SonicKnucklesTitleScreen {
    private enum Phase { INACTIVE, TO_BLACK, FROM_BLACK, SEGA, SCENE, EXITING }
    private final Sonic3kTitleState nativeState;
    private SonicKnucklesTitleArt art;
    private Phase phase = Phase.INACTIVE;
    private int age;
    private int fadeRemaining;
    private int whiteFadeDelay;
    private int sonicY;
    private int eggYFixed;
    private int eggBackupYFixed;
    private int backgroundYFixed;
    private int foregroundY;
    private int sceneRoutine;
    private int shakeTimer;
    private int dissolveTimer;
    private int landingRoutine;
    private int landingTimer;
    private boolean landed;
    private int bannerDelay;
    private boolean bannerVisible;
    private boolean menuReady;
    private int bannerOffset;
    private int bannerVelocity;
    private boolean bannerAbove;
    private int bannerY;
    private int iconAngle;
    private int iconStep;
    private final int[] palette = new int[64];
    private final int[] target = new int[64];

    SonicKnucklesTitleScreen(Sonic3kTitleState nativeState) {
        this.nativeState = nativeState;
    }

    void initialize() {
        try {
            art = new SonicKnucklesTitleArt(GameServices.rom().getRom(), GameServices.graphics());
            Arrays.fill(palette, 0);
            Arrays.fill(target, 0xEEE);
            for (int line = 0; line < 4; line++) target[line * 16] = 0;
            setTarget(16, art.palette(0x55A8, 32));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load the native Sonic & Knuckles title", failure);
        }
        phase = Phase.TO_BLACK;
        age = 0;
        fadeRemaining = 22;
        whiteFadeDelay = 0;
        sonicY = 0x16;
        eggYFixed = 0xB0 << 16;
        eggBackupYFixed = eggYFixed;
        backgroundYFixed = 0;
        foregroundY = 0;
        sceneRoutine = 0;
        shakeTimer = 0;
        dissolveTimer = 0;
        landingRoutine = 0;
        landingTimer = 0;
        landed = false;
        bannerDelay = 0;
        bannerVisible = false;
        menuReady = false;
        bannerY = 0x180;
        GameServices.audio().fadeOutMusic();
    }

    void update(InputHandler input) {
        boolean start = input.logical().player1().startPressed();
        switch (phase) {
            case INACTIVE, EXITING -> { }
            case TO_BLACK -> {
                if (--fadeRemaining == 0) {
                    phase = Phase.FROM_BLACK;
                    fadeRemaining = 22;
                }
            }
            case FROM_BLACK -> {
                stepPalette(false);
                if (--fadeRemaining == 0) {
                    phase = Phase.SEGA;
                    age = 180;
                    // The same no-device audio owner serves title and gameplay.
                    GameServices.audio().playSegaPcm();
                }
            }
            case SEGA -> {
                if (start || --age == 0) startScene();
            }
            case SCENE -> updateScene(input, start);
        }
    }

    private void startScene() {
        GameServices.audio().stopSegaPcm();
        try {
            setTarget(0, art.palette(0x5588, 128));
        } catch (IOException failure) { throw new IllegalStateException(failure); }
        art.startScene();
        phase = Phase.SCENE;
        fadeRemaining = 22;
        GameServices.audio().playMusic(com.openggf.game.sonic3k.audio.Sonic3kAudioResourceProfile.SONIC_KNUCKLES
                .musicId(Sonic3kMusic.TITLE.id));
    }

    private void updateScene(InputHandler input, boolean start) {
        if (fadeRemaining > 0) {
            stepPalette(true);
            fadeRemaining--;
        }
        if (menuReady) {
            updateDeathEgg(); // its native background scroll continues after the menu appears
            updateIcon(input);
            if (start) phase = Phase.EXITING;
            return;
        }
        // loc_52F0: Start finishes the falling scene only after its palette fade.
        // It does not launch a game on that same press.
        if (start && fadeRemaining == 0) {
            sceneRoutine = 3;
            shakeTimer = 0;
            foregroundY = 0x100;
            backgroundYFixed = 0x100 << 16;
            eggBackupYFixed = 0x70 << 16;
            setKnucklesPalette();
            finishLanding();
            return;
        }

        updateFallingSonic();
        updateDeathEgg();
        // SKTitle_DeformBG advances the dissolve only after the impact shake.
        if (sceneRoutine == 2 && --dissolveTimer == 0) {
            sceneRoutine = 3;
            setKnucklesPalette();
            art.initialHands();
        }
        updateBanner();
    }

    private void updateFallingSonic() {
        if (landed) return;
        if (sonicY != 0xF0) sonicY++;
        if (sceneRoutine != 3) return;
        if (landingRoutine == 0) {
            if (foregroundY < 0xE0) foregroundY += 8;
            else {
                if (foregroundY == 0xE0) landingTimer = 6;
                if (foregroundY < 0x100) foregroundY += 8;
                else landingRoutine = 1;
            }
        } else if (landingRoutine == 1) {
            if (--landingTimer < 0) { landingRoutine = 2; landingTimer = 4; }
        } else if (--landingTimer < 0) finishLanding();
    }

    private void updateDeathEgg() {
        if (sceneRoutine == 0) {
            if ((eggYFixed >>> 16) == 0xF0) {
                eggBackupYFixed = eggYFixed;
                backgroundYFixed = (backgroundYFixed & 0xFFFF0000) | 0x8000;
                sceneRoutine = 1;
                shakeTimer = 0x1E;
                dissolveTimer = 0x22;
            } else {
                eggYFixed += 0x8000;
                if ((backgroundYFixed >>> 16) != 0x100) backgroundYFixed += 0x10000;
            }
        } else {
            if (shakeTimer > 0 && --shakeTimer == 0) sceneRoutine++;
            if ((backgroundYFixed >>> 16) != 0x100) {
                eggBackupYFixed -= 0x8000;
                backgroundYFixed += 0x8000;
            }
        }
    }

    private void setKnucklesPalette() {
        try {
            byte[] knuckles = art.palette(0x5608, 32);
            setTarget(16, knuckles);
            for (int i = 16; i < 32; i++) palette[i] = target[i];
        } catch (IOException failure) { throw new IllegalStateException(failure); }
    }

    private void finishLanding() {
        if (landed) return;
        landed = true;
        art.initialHands();
        try { art.finishLanding(); }
        catch (IOException failure) { throw new IllegalStateException(failure); }
        bannerDelay = 30;
    }

    private void updateBanner() {
        if (!landed || menuReady) return;
        if (bannerDelay > 0) {
            if (--bannerDelay == 0) {
                bannerOffset = -0x600000;
                bannerVelocity = 0x400;
                bannerAbove = false;
                bannerVisible = true;
            }
            return;
        }
        // Obj_SKTitle_BannerMain (sonic3k.asm:6964-6997), signed native words.
        bannerOffset += bannerVelocity << 8;
        int offset = (short) (bannerOffset >> 16);
        if (offset == 0 && bannerVelocity == -0x5B) {
            menuReady = true;
            nativeState.enterStandaloneMenu();
            iconAngle = nativeState.option() << 7;
            iconStep = 8;
        } else {
            boolean above = offset >= 0;
            bannerVelocity = (short) (bannerVelocity + (above ? -0x40 : 0x40));
            if (above != bannerAbove) bannerVelocity >>= 1;
            bannerAbove = above;
        }
        bannerY = 0x118 - offset;
    }

    private void updateIcon(InputHandler input) {
        if ((iconAngle & 0x7F) == 0) {
            boolean up = input.logical().menuUp();
            boolean down = input.logical().menuDown();
            int option = nativeState.option();
            if (up) { iconAngle = (iconAngle - 8) & 255; iconStep = -8; option = (option - 1) & 1; }
            if (down) { iconAngle = (iconAngle + 8) & 255; iconStep = 8; option = (option + 1) & 1; }
            nativeState.setOption(option);
            if (up || down) GameServices.audio().playSfx(Sonic3kSfx.SWITCH.id);
        }
        if ((iconAngle & 0x7F) != 0) iconAngle = (iconAngle + iconStep) & 255;
    }

    private void setTarget(int first, byte[] bytes) {
        for (int i = 0; i < bytes.length / 2; i++) target[first + i] = (bytes[i * 2] & 255) << 8 | bytes[i * 2 + 1] & 255;
    }

    private void stepPalette(boolean fromWhite) {
        // Pal_FromWhite/Pal_DecColor2 lower all components every third call;
        // Pal_FromBlack/Pal_AddColor raises the first admissible component.
        if (fromWhite) {
            if (--whiteFadeDelay >= 0) return;
            whiteFadeDelay = 2;
        }
        for (int i = 0; i < palette.length; i++) {
            int current = palette[i], goal = target[i];
            for (int shift : fromWhite ? new int[]{0, 4, 8} : new int[]{8, 4, 0}) {
                int c = current >>> shift & 14, g = goal >>> shift & 14;
                if ((!fromWhite && c < g) || (fromWhite && c > g)) {
                    current += (fromWhite ? -2 : 2) << shift;
                    if (!fromWhite) break;
                }
            }
            palette[i] = current;
        }
    }

    void draw() {
        if (art == null || phase == Phase.INACTIVE || phase == Phase.TO_BLACK || phase == Phase.EXITING) return;
        byte[] bytes = new byte[128];
        for (int i = 0; i < palette.length; i++) { bytes[i * 2] = (byte) (palette[i] >>> 8); bytes[i * 2 + 1] = (byte) palette[i]; }
        art.draw(this, bytes);
    }

    void setClearColor() { glClearColor(0, 0, 0, 1); }
    void reset() { phase = Phase.INACTIVE; art = null; GameServices.audio().stopSegaPcm(); }
    TitleScreenProvider.State state() {
        return switch (phase) {
            case INACTIVE -> TitleScreenProvider.State.INACTIVE;
            case EXITING -> TitleScreenProvider.State.EXITING;
            case SCENE -> menuReady ? TitleScreenProvider.State.ACTIVE : TitleScreenProvider.State.FADE_IN;
            case SEGA -> TitleScreenProvider.State.SEGA_LOGO;
            default -> TitleScreenProvider.State.INTRO_TEXT_FADE_IN;
        };
    }
    int option() { return nativeState.option() & 1; }
    boolean sceneStarted() { return phase == Phase.SCENE; }
    boolean landed() { return landed; }
    boolean bannerVisible() { return bannerVisible; }
    boolean menuReady() { return menuReady; }
    int bannerY() { return bannerY; }
    int backgroundY() { return backgroundYFixed >> 16; }
    int foregroundY() { return foregroundY; }
    int sonicY() { return sonicY; }
    int deathEggY() { return (sceneRoutine == 0 ? eggYFixed : eggBackupYFixed) / 0x10000 - 128; }
    int mappingFrame() { return landingRoutine == 0 ? foregroundY < 0xE0 ? 0 : 1 : 2; }
}
