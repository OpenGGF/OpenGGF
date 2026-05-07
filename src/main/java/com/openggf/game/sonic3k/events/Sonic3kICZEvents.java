package com.openggf.game.sonic3k.events;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.objects.IczBigSnowPileInstance;
import com.openggf.game.sonic3k.objects.IczSnowboardIntroInstance;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * IceCap Zone dynamic level events.
 *
 * <p>ROM references:
 * <ul>
 *   <li>{@code sonic3k.asm:76984} {@code Obj_LevelIntroICZ1}</li>
 *   <li>{@code sonic3k.asm:110150} {@code ICZ1_BackgroundEvent}</li>
 *   <li>{@code sonic3k.asm:110433} {@code Obj_ICZ1BigSnowPile}</li>
 *   <li>{@code sonic3k.asm:39416} {@code ICZ1_Resize}</li>
 *   <li>{@code sonic3k.asm:39454} {@code ICZ2_Resize}</li>
 * </ul>
 *
 * <p>This pass enables the full ICZ intro system shell for Sonic-alone only.
 * Other player-mode branches remain inactive until their ROM paths are ported.
 */
public class Sonic3kICZEvents extends Sonic3kZoneEvents {
    private static final int ICZ1_EVENTS_FG5_CAMERA_X_1 = 0x3700;
    private static final int ICZ1_EVENTS_FG5_CAMERA_Y_1 = 0x068C;
    private static final int ICZ1_EVENTS_FG5_CAMERA_X_2 = 0x3940;
    private static final int ICZ1_INDOOR_PALETTE_X = 0x3940;
    private static final int ICZ1_BG_INTRO = 0;
    private static final int ICZ1_BG_SNOW_FALL = 4;
    private static final int ICZ1_BG_REFRESH = 8;
    private static final int ICZ1_BIG_SNOW_FINAL_OFFSET = -0x012E;
    private static final int ICZ1_BIG_SNOW_ACCELERATION = 0x2400;
    private static final int ICZ1_BIG_SNOW_RUMBLE_MASK = 0x000F;
    private static final int ICZ2_INDOOR_X_MIN = 0x1000;
    private static final int ICZ2_INDOOR_X_MAX = 0x3600;
    private static final int ICZ2_INDOOR_Y = 0x0720;
    private static final int ICZ2_START_INDOOR_X_MAX = 0x3600;
    private static final int ICZ2_START_INDOOR_Y_HIGH = 0x0720;
    private static final int ICZ2_START_INDOOR_X_LOW = 0x1000;
    private static final int ICZ2_START_INDOOR_Y_LOW = 0x0580;
    private static final int ICZ2_INDOOR_HYSTERESIS_X_MIN = 0x1900;
    private static final int ICZ2_INDOOR_HYSTERESIS_X_MAX = 0x1B80;
    private static final int ICZ2_MIN_X_LOCK_CAMERA_X = 0x0740;
    private static final int ICZ2_MIN_X_LOCK_CAMERA_Y = 0x0400;
    private static final int LINE_4_PALETTE_INDEX = 3;
    private static final int LINE_4_BG_COLOR_START = 1;
    private static final int[] ICZ1_INDOOR_LINE4_COLORS_1_TO_11 = {
            0x0EC0, 0x0E40, 0x0E04, 0x0C00, 0x0600, 0x0200,
            0x0000, 0x0E64, 0x0E24, 0x0A02, 0x0402
    };
    private static final int[] ICZ2_OUTDOOR_LINE4_COLORS_1_TO_10 = {
            0x0EEE, 0x0EEA, 0x0EC8, 0x0EA4, 0x0C82,
            0x0C60, 0x0C40, 0x0E20, 0x0A00, 0x0E00
    };
    private static final int[] ICZ2_INDOOR_LINE4_COLORS_1_TO_11 = {
            0x0EE2, 0x0E24, 0x0E04, 0x0E02, 0x0402, 0x0200,
            0x0000, 0x0E20, 0x0E40, 0x0840, 0x0600
    };
    private static final int[] ICZ2_FROM_ICZ1_LINE4_COLORS_1_TO_10 = {
            0x0EEC, 0x0CC6, 0x0C80, 0x0C60, 0x0C40,
            0x0A40, 0x0820, 0x0620, 0x0200, 0x0600
    };

    private boolean eventsFg5;
    private boolean introSpawned;
    private boolean indoorPaletteCyclingActive;
    private int backgroundRoutine;
    private int bigSnowOffset;
    private int bigSnowOffsetSubpixels;
    private int bigSnowVelocity;
    private boolean bigSnowPileSpawned;

    @Override
    public void init(int act) {
        super.init(act);
        eventsFg5 = false;
        introSpawned = false;
        backgroundRoutine = 0;
        bigSnowOffset = 0;
        bigSnowOffsetSubpixels = 0;
        bigSnowVelocity = 0;
        bigSnowPileSpawned = false;
        indoorPaletteCyclingActive = initialIndoorPaletteCycleState(act);
        applyInitialBackgroundPalette(act);
        if (act == 0 && playerCharacter() == PlayerCharacter.SONIC_ALONE) {
            spawnSonicSnowboardIntro();
        }
    }

    @Override
    public void update(int act, int frameCounter) {
        if (act == 0) {
            updateAct1Resize();
            updateAct1ScreenEvent();
            updateAct1BackgroundEvent(frameCounter);
        } else if (act == 1) {
            updateAct2Resize();
        }
        updateIndoorPaletteCycleGate(act);
    }

    public boolean isEventsFg5() {
        return eventsFg5;
    }

    public void setEventsFg5(boolean value) {
        eventsFg5 = value;
    }

    /**
     * ROM: Events_bg+$16. AnPal_ICZ uses this word to decide whether palette
     * line 4 colors 12-15 are allowed to cycle.
     */
    public boolean isIndoorPaletteCyclingActive() {
        return indoorPaletteCyclingActive;
    }

    public void setIndoorPaletteCyclingActive(boolean value) {
        indoorPaletteCyclingActive = value;
    }

    public int getIcz1BigSnowOffset() {
        return bigSnowOffset;
    }

    public int getIcz1BackgroundRoutine() {
        return backgroundRoutine;
    }

    private void spawnSonicSnowboardIntro() {
        if (introSpawned) {
            return;
        }
        introSpawned = true;
        ObjectSpawn spawn = new ObjectSpawn(
                IczSnowboardIntroInstance.INITIAL_SNOWBOARD_X,
                IczSnowboardIntroInstance.INITIAL_SNOWBOARD_Y,
                0, 0, 0, false,
                IczSnowboardIntroInstance.INITIAL_SNOWBOARD_Y);
        spawnObject(() -> new IczSnowboardIntroInstance(spawn));
    }

    private void updateAct1Resize() {
        int cameraX = camera().getX();
        int cameraY = camera().getY();
        switch (eventRoutine) {
            case 0 -> {
                if (cameraX >= ICZ1_EVENTS_FG5_CAMERA_X_1
                        && cameraY >= ICZ1_EVENTS_FG5_CAMERA_Y_1) {
                    eventsFg5 = true;
                    eventRoutine = 2;
                }
            }
            case 2 -> {
                // In the ROM, the quake lock prevents Sonic from carrying the camera
                // into the indoor refresh trigger before Obj_ICZ1BigSnowPile has
                // finished and released him. The snowboard intro object can otherwise
                // cross $3940 during its object-control handoff in the engine.
                if (backgroundRoutine == ICZ1_BG_SNOW_FALL
                        && (bigSnowOffset > ICZ1_BIG_SNOW_FINAL_OFFSET || isFocusedPlayerControlLocked())) {
                    return;
                }
                if (cameraX >= ICZ1_EVENTS_FG5_CAMERA_X_2) {
                    eventsFg5 = true;
                    eventRoutine = 4;
                }
            }
            default -> {
                // ICZ1 routine 4 is a ROM rts terminal state.
            }
        }
    }

    private void updateAct1BackgroundEvent(int frameCounter) {
        switch (backgroundRoutine) {
            case ICZ1_BG_INTRO -> updateAct1BackgroundIntro(frameCounter);
            case ICZ1_BG_SNOW_FALL -> updateAct1BackgroundSnowFall(frameCounter);
            default -> {
                // ICZ1 background routine 8 is the ROM's post-fall refresh/terminal state.
            }
        }
    }

    private void updateAct1BackgroundIntro(int frameCounter) {
        if (!eventsFg5) {
            return;
        }
        eventsFg5 = false;
        if (playerCharacter() == PlayerCharacter.KNUCKLES) {
            backgroundRoutine = ICZ1_BG_SNOW_FALL;
            return;
        }

        spawnBigSnowPile();
        bigSnowOffset = 0;
        bigSnowOffsetSubpixels = 0;
        bigSnowVelocity = 0;
        updateBigSnowFall(frameCounter);
        lockFocusedPlayerForIntroQuake();
        backgroundRoutine = ICZ1_BG_SNOW_FALL;
    }

    private void updateAct1BackgroundSnowFall(int frameCounter) {
        if (eventsFg5) {
            eventsFg5 = false;
            backgroundRoutine = ICZ1_BG_REFRESH;
            gameState().setScreenShakeActive(false);
            return;
        }
        if (playerCharacter() != PlayerCharacter.KNUCKLES) {
            updateBigSnowFall(frameCounter);
        }
    }

    private void updateAct1ScreenEvent() {
        if (backgroundRoutine != ICZ1_BG_INTRO || !gameState().isScreenShakeActive()) {
            return;
        }
        lockFocusedPlayerForIntroQuake();
    }

    private void lockFocusedPlayerForIntroQuake() {
        AbstractPlayableSprite player = camera().getFocusedSprite();
        if (player == null || player.isControlLocked()) {
            return;
        }
        player.setControlLocked(true);
        player.clearLogicalInputState();
    }

    private boolean isFocusedPlayerControlLocked() {
        AbstractPlayableSprite player = camera().getFocusedSprite();
        return player != null && player.isControlLocked();
    }

    private void spawnBigSnowPile() {
        if (bigSnowPileSpawned) {
            return;
        }
        bigSnowPileSpawned = true;
        ObjectSpawn spawn = new ObjectSpawn(
                IczBigSnowPileInstance.X_POSITION,
                IczBigSnowPileInstance.BASE_Y,
                0, 0, 0, false,
                IczBigSnowPileInstance.BASE_Y);
        spawnObject(() -> new IczBigSnowPileInstance(spawn, this));
    }

    private void updateBigSnowFall(int frameCounter) {
        if (bigSnowOffset > ICZ1_BIG_SNOW_FINAL_OFFSET) {
            gameState().setScreenShakeActive(true);
            bigSnowVelocity += ICZ1_BIG_SNOW_ACCELERATION;
            bigSnowOffsetSubpixels -= bigSnowVelocity;
            bigSnowOffset = bigSnowOffsetSubpixels >> 16;
            if (((frameCounter - 1) & ICZ1_BIG_SNOW_RUMBLE_MASK) == 0) {
                audio().playSfx(Sonic3kSfx.RUMBLE_2.id);
            }
        }

        if (bigSnowOffset <= ICZ1_BIG_SNOW_FINAL_OFFSET) {
            gameState().setScreenShakeActive(true);
            bigSnowOffset = ICZ1_BIG_SNOW_FINAL_OFFSET;
            bigSnowOffsetSubpixels = ICZ1_BIG_SNOW_FINAL_OFFSET << 16;
        }
    }

    private void updateAct2Resize() {
        if (eventRoutine != 0) {
            return;
        }
        if (camera().getX() >= ICZ2_MIN_X_LOCK_CAMERA_X
                && camera().getY() < ICZ2_MIN_X_LOCK_CAMERA_Y) {
            camera().setMinX((short) ICZ2_MIN_X_LOCK_CAMERA_X);
            eventRoutine = 2;
        }
    }

    private boolean initialIndoorPaletteCycleState(int act) {
        try {
            int x = camera().getX() & 0xFFFF;
            int y = camera().getY() & 0xFFFF;
            if (act == 0) {
                return x >= ICZ1_INDOOR_PALETTE_X;
            }
            if (act == 1) {
                return shouldAct2StartIndoors(x, y);
            }
        } catch (IllegalStateException ignored) {
            // Some tests construct event state before a gameplay camera exists.
        }
        return false;
    }

    private void updateIndoorPaletteCycleGate(int act) {
        int x = camera().getX() & 0xFFFF;
        int y = camera().getY() & 0xFFFF;
        if (act == 0) {
            if (!indoorPaletteCyclingActive && x >= ICZ1_INDOOR_PALETTE_X) {
                indoorPaletteCyclingActive = true;
                applyIcz1IndoorPalette();
            }
            return;
        }
        if (act == 1) {
            boolean nextState = isAct2IndoorPaletteCycleActive(x, y);
            if (nextState != indoorPaletteCyclingActive) {
                indoorPaletteCyclingActive = nextState;
                if (nextState) {
                    applyIcz2IndoorPalette();
                } else {
                    applyIcz2OutdoorPalette(x);
                }
            }
        }
    }

    private void applyInitialBackgroundPalette(int act) {
        try {
            int x = camera().getX() & 0xFFFF;
            int y = camera().getY() & 0xFFFF;
            if (act == 0) {
                if (indoorPaletteCyclingActive) {
                    applyIcz1IndoorPalette();
                }
                return;
            }
            if (act == 1) {
                if (shouldAct2StartIndoors(x, y)) {
                    applyIcz2IndoorPalette();
                } else {
                    applyIcz2OutdoorPalette(x);
                }
            }
        } catch (IllegalStateException ignored) {
            // Some tests construct event state before a gameplay camera exists.
        }
    }

    private void applyIcz1IndoorPalette() {
        applyLine4BackgroundPalette(ICZ1_INDOOR_LINE4_COLORS_1_TO_11);
    }

    private void applyIcz2IndoorPalette() {
        applyLine4BackgroundPalette(ICZ2_INDOOR_LINE4_COLORS_1_TO_11);
    }

    private void applyIcz2OutdoorPalette(int cameraX) {
        if (cameraX < 0x0720) {
            applyLine4BackgroundPalette(ICZ2_FROM_ICZ1_LINE4_COLORS_1_TO_10);
        } else {
            applyLine4BackgroundPalette(ICZ2_OUTDOOR_LINE4_COLORS_1_TO_10);
        }
    }

    private void applyLine4BackgroundPalette(int[] segaWords) {
        Level level = levelManager().getCurrentLevel();
        if (level == null || segaWords == null) {
            return;
        }
        Palette palette = level.getPalette(LINE_4_PALETTE_INDEX);
        if (palette == null) {
            return;
        }
        byte[] patch = new byte[segaWords.length * 2];
        for (int i = 0; i < segaWords.length; i++) {
            int offset = i * 2;
            patch[offset] = (byte) ((segaWords[i] >>> 8) & 0xFF);
            patch[offset + 1] = (byte) (segaWords[i] & 0xFF);
        }
        S3kPaletteWriteSupport.applyContiguousPatch(
                paletteRegistryOrNull(),
                level,
                graphics(),
                S3kPaletteOwners.ZONE_EVENT_PALETTE_LOAD,
                S3kPaletteOwners.PRIORITY_ZONE_EVENT,
                LINE_4_PALETTE_INDEX,
                LINE_4_BG_COLOR_START,
                patch);
        S3kPaletteWriteSupport.resolvePendingWritesNow(paletteRegistryOrNull(), level, graphics());
    }

    private boolean shouldAct2StartIndoors(int x, int y) {
        if (x >= ICZ2_START_INDOOR_X_MAX) {
            return false;
        }
        if (y >= ICZ2_START_INDOOR_Y_HIGH) {
            return true;
        }
        return x >= ICZ2_START_INDOOR_X_LOW && y >= ICZ2_START_INDOOR_Y_LOW;
    }

    private boolean isAct2IndoorPaletteCycleActive(int x, int y) {
        if (!indoorPaletteCyclingActive) {
            return x >= ICZ2_INDOOR_X_MIN && x < ICZ2_INDOOR_X_MAX && y >= ICZ2_INDOOR_Y;
        }
        if (x >= ICZ2_INDOOR_HYSTERESIS_X_MIN && x < ICZ2_INDOOR_HYSTERESIS_X_MAX) {
            return true;
        }
        return !(x < ICZ2_INDOOR_X_MIN || x >= ICZ2_INDOOR_X_MAX || y < ICZ2_INDOOR_Y);
    }
}
