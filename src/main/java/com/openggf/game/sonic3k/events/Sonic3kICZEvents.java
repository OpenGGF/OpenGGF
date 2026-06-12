package com.openggf.game.sonic3k.events;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.save.SaveReason;
import com.openggf.game.save.SessionSaveRequests;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.IczBigSnowPileInstance;
import com.openggf.game.sonic3k.objects.IczSnowboardIntroInstance;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Palette;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;

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
 * <p>This pass enables the ICZ1 snowboard intro for the ROM Sonic player modes.
 * Tails-alone and Knuckles branches remain inactive until their ROM paths are ported.
 */
public class Sonic3kICZEvents extends Sonic3kZoneEvents {
    private static final int ICZ1_EVENTS_FG5_CAMERA_X_1 = 0x3700;
    private static final int ICZ1_EVENTS_FG5_CAMERA_Y_1 = 0x068C;
    private static final int ICZ1_EVENTS_FG5_CAMERA_X_2 = 0x3940;
    private static final int ICZ1_INDOOR_PALETTE_X = 0x3940;
    private static final int ICZ1_BG_INTRO = 0;
    private static final int ICZ1_BG_SNOW_FALL = 4;
    private static final int ICZ1_BG_REFRESH = 8;
    private static final int ICZ1_BG_REFRESH_2 = 12;
    private static final int ICZ1_BG_NORMAL = 16;
    private static final int ICZ1_BG_TRANSITION = 20;
    private static final int ICZ1_TRANSITION_CAMERA_X = 0x6900;
    private static final int ICZ1_TO_ICZ2_OFFSET_X = -0x6880;
    private static final int ICZ1_TO_ICZ2_OFFSET_Y = 0x0100;
    private static final int ICZ2_CAMERA_MIN_X = 0x0000;
    private static final int ICZ2_CAMERA_MAX_X = 0x7000;
    private static final int ICZ2_CAMERA_MIN_Y = 0x0000;
    private static final int ICZ2_CAMERA_MAX_Y = 0x0B20;
    private static final int ICZ1_BIG_SNOW_FINAL_OFFSET = -0x012E;
    private static final int ICZ1_BIG_SNOW_ACCELERATION = 0x2400;
    private static final int ICZ1_BIG_SNOW_RUMBLE_MASK = 0x000F;
    private static final int ICZ_SLIDE_EXIT_MOVE_LOCK = 5;
    private static final int ICZ_SLIDE_FRICTION = 4;
    private static final int ICZ_SLIDE_ANIMATION = 0x19;
    private static final int[] ICZ1_SLIDE_BLOCKS = {
            0x2E, 0xC6, 0x33, 0xC5, 0x24, 0x2A, 0x44, 0x1F, 0x27, 0x2B
    };
    private static final int[] ICZ1_SLIDE_SPEEDS = {
            -8, -8, 8, 8, -12, -12, -12, 12, 12, 12
    };
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
    private boolean act2TransitionRequested;
    private int activeAct;

    @Override
    public void init(int act) {
        super.init(act);
        activeAct = act;
        eventsFg5 = false;
        introSpawned = false;
        backgroundRoutine = 0;
        bigSnowOffset = 0;
        bigSnowOffsetSubpixels = 0;
        bigSnowVelocity = 0;
        bigSnowPileSpawned = false;
        act2TransitionRequested = false;
        indoorPaletteCyclingActive = initialIndoorPaletteCycleState(act);
        applyInitialBackgroundPalette(act);
        if (act == 0 && hasSonicSnowboardIntroPlayerMode()) {
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

    public void forceAct1NormalBackgroundRoutineForTest() {
        backgroundRoutine = ICZ1_BG_NORMAL;
    }

    public boolean isAct2TransitionRequested() {
        return act2TransitionRequested;
    }

    /**
     * Number of bytes that {@link #writeRewindState(java.nio.ByteBuffer)} produces.
     * 5 booleans (5 bytes) + 5 ints (20 bytes) = 25 bytes.
     */
    public static int rewindStateBytes() {
        return 5 + 5 * 4;
    }

    /**
     * Serializes all gameplay-mutable ICZ state into the supplied buffer.
     * Layout must match {@link #readRewindState(java.nio.ByteBuffer)} exactly
     * and produce {@link #rewindStateBytes()} bytes.
     */
    public void writeRewindState(java.nio.ByteBuffer buf) {
        buf.put((byte) (eventsFg5 ? 1 : 0));
        buf.put((byte) (introSpawned ? 1 : 0));
        buf.put((byte) (indoorPaletteCyclingActive ? 1 : 0));
        buf.put((byte) (bigSnowPileSpawned ? 1 : 0));
        buf.put((byte) (act2TransitionRequested ? 1 : 0));
        buf.putInt(eventRoutine);
        buf.putInt(backgroundRoutine);
        buf.putInt(bigSnowOffset);
        buf.putInt(bigSnowOffsetSubpixels);
        buf.putInt(bigSnowVelocity);
    }

    /** Inverse of {@link #writeRewindState(java.nio.ByteBuffer)}. */
    public void readRewindState(java.nio.ByteBuffer buf) {
        eventsFg5 = buf.get() != 0;
        introSpawned = buf.get() != 0;
        indoorPaletteCyclingActive = buf.get() != 0;
        bigSnowPileSpawned = buf.get() != 0;
        act2TransitionRequested = buf.get() != 0;
        eventRoutine = buf.getInt();
        backgroundRoutine = buf.getInt();
        bigSnowOffset = buf.getInt();
        bigSnowOffsetSubpixels = buf.getInt();
        bigSnowVelocity = buf.getInt();
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

    public boolean shouldEnterIntroSidekickDormantMarker(AbstractPlayableSprite sidekick) {
        return sidekick != null && activeAct == 0 && hasSonicSnowboardIntroPlayerMode();
    }

    /**
     * ROM: {@code sub_714E -> loc_71D2 -> sub_71E4}. ICZ1 slide terrain runs
     * as a level event after the current player slot has moved. It sets
     * {@code status_secondary} bit 7 for the next frame's {@code Sonic_Move},
     * which skips input/friction but leaves the just-finished friction frame
     * intact.
     */
    public void updateSlideTerrainAfterPlayablePhysics(int act, AbstractPlayableSprite player) {
        if (act != 0 || player == null || player.getDead() || player.isDebugMode()) {
            return;
        }
        LevelManager manager = levelManager();
        int blockId = manager != null ? manager.getBlockIdAt(player.getCentreX(), player.getCentreY()) : -1;
        applyIcz1SlideTerrainForBlock(player, blockId);
    }

    static void applyIcz1SlideTerrainForBlock(AbstractPlayableSprite player, int blockId) {
        if (player == null) {
            return;
        }
        if (player.getAir() || player.isOnObject()) {
            exitIczSlide(player);
            return;
        }
        int tableIndex = findIcz1SlideTableIndex(blockId);
        if (tableIndex < 0) {
            exitIczSlide(player);
            return;
        }

        int targetSpeed = ICZ1_SLIDE_SPEEDS[tableIndex];
        if (targetSpeed != 0) {
            applyDirectionalIczSlide(player);
        } else {
            applyFrictionIczSlide(player);
        }
    }

    static int findIcz1SlideTableIndex(int blockId) {
        for (int i = 0; i < ICZ1_SLIDE_BLOCKS.length; i++) {
            if (ICZ1_SLIDE_BLOCKS[i] == (blockId & 0xFF)) {
                return i;
            }
        }
        return -1;
    }

    private static void applyDirectionalIczSlide(AbstractPlayableSprite player) {
        int inertia = player.getGSpeed();
        int inertiaHigh = (byte) (inertia & 0xFF);
        if (!player.isSliding()) {
            player.setDirection(inertiaHigh < 0 ? Direction.LEFT : Direction.RIGHT);
            player.setAnimationId(ICZ_SLIDE_ANIMATION);
        }
        player.setSliding(true);
    }

    private static void applyFrictionIczSlide(AbstractPlayableSprite player) {
        int inertia = player.getGSpeed();
        if ((player.getLogicalInputState() & AbstractPlayableSprite.INPUT_LEFT) != 0) {
            player.setAnimationId(Sonic3kAnimationIds.WALK.id());
            player.setDirection(Direction.LEFT);
            inertia -= ICZ_SLIDE_FRICTION;
            if (inertia < 0) {
                inertia -= ICZ_SLIDE_FRICTION;
            }
        }
        if ((player.getLogicalInputState() & AbstractPlayableSprite.INPUT_RIGHT) != 0) {
            player.setAnimationId(Sonic3kAnimationIds.WALK.id());
            player.setDirection(Direction.RIGHT);
            inertia += ICZ_SLIDE_FRICTION;
            if (inertia >= 0) {
                inertia += ICZ_SLIDE_FRICTION;
            }
        }
        if (inertia > 0) {
            inertia -= ICZ_SLIDE_FRICTION;
            if (inertia <= 0) {
                inertia = 0;
                player.setAnimationId(Sonic3kAnimationIds.WAIT.id());
            }
        } else if (inertia < 0) {
            inertia += ICZ_SLIDE_FRICTION;
            if (inertia >= 0) {
                inertia = 0;
                player.setAnimationId(Sonic3kAnimationIds.WAIT.id());
            }
        } else {
            player.setAnimationId(Sonic3kAnimationIds.WAIT.id());
        }
        player.setGSpeed((short) inertia);
        player.setSliding(true);
    }

    private static void exitIczSlide(AbstractPlayableSprite player) {
        if (player.isSliding()) {
            player.setMoveLockTimer(ICZ_SLIDE_EXIT_MOVE_LOCK);
            player.setSliding(false);
        }
    }

    public boolean hasSonicSnowboardIntroPlayerMode() {
        PlayerCharacter character = playerCharacter();
        return character == PlayerCharacter.SONIC_AND_TAILS
                || character == PlayerCharacter.SONIC_ALONE;
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
            case ICZ1_BG_REFRESH -> updateAct1BackgroundRefresh();
            case ICZ1_BG_REFRESH_2 -> updateAct1BackgroundRefresh2();
            case ICZ1_BG_NORMAL -> updateAct1BackgroundNormal();
            case ICZ1_BG_TRANSITION -> requestIcz2Transition();
            default -> {
                // ICZ1 unknown background stages are terminal until ported.
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

    private void updateAct1BackgroundRefresh() {
        backgroundRoutine = ICZ1_BG_REFRESH_2;
    }

    private void updateAct1BackgroundRefresh2() {
        indoorPaletteCyclingActive = true;
        applyLine4BackgroundPalette(ICZ1_INDOOR_LINE4_COLORS_1_TO_11);
        backgroundRoutine = ICZ1_BG_NORMAL;
    }

    private void updateAct1BackgroundNormal() {
        if ((camera().getX() & 0xFFFF) < ICZ1_TRANSITION_CAMERA_X) {
            return;
        }
        backgroundRoutine = ICZ1_BG_TRANSITION;
        requestIcz2Transition();
    }

    private void requestIcz2Transition() {
        if (act2TransitionRequested) {
            return;
        }
        act2TransitionRequested = true;

        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest.builder(
                        SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(Sonic3kZoneIds.ZONE_ICZ, 1)
                .deactivateLevelNow(false)
                .preserveMusic(true)
                .preserveLevelGamestate(true)
                .showInLevelTitleCard(false)
                .preserveOffsetCameraPosition(true)
                .postTransitionMinX(ICZ2_CAMERA_MIN_X)
                .postTransitionMaxX(ICZ2_CAMERA_MAX_X)
                .postTransitionMinY(ICZ2_CAMERA_MIN_Y)
                .postTransitionMaxY(ICZ2_CAMERA_MAX_Y)
                .postTransitionMaxYTarget(ICZ2_CAMERA_MAX_Y)
                .playerOffset(ICZ1_TO_ICZ2_OFFSET_X, ICZ1_TO_ICZ2_OFFSET_Y)
                .cameraOffset(ICZ1_TO_ICZ2_OFFSET_X, ICZ1_TO_ICZ2_OFFSET_Y)
                .build();

        SessionSaveRequests.requestCurrentSessionSave(SaveReason.PROGRESSION_SAVE);
        if (levelManager().getCurrentLevel() == null) {
            levelManager().requestSeamlessTransition(request);
            return;
        }
        try {
            levelManager().executeActTransition(request);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to apply ICZ act transition", e);
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
