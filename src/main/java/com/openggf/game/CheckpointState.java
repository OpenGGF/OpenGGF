package com.openggf.game;

import com.openggf.camera.Camera;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.logging.Logger;

/**
 * Stores checkpoint state for save/restore on player death.
 * <p>
 * Based on the Sonic 2 disassembly's Saved_* variables.
 * </p>
 */
public class CheckpointState implements RespawnState {
    private static final Logger LOGGER = Logger.getLogger(CheckpointState.class.getName());

    private int lastCheckpointIndex = -1;
    // Persistent star-post activation high-water: the highest star-post subtype
    // ever activated this life. Unlike lastCheckpointIndex (ROM Last_star_post_hit,
    // zeroed on a star-post BONUS entry), this survives that zeroing so a
    // returned-from-bonus star post is still recognised as already-used. Models the
    // ROM per-object respawn bit kept across the reload by Respawn_table_keep. See
    // RespawnState#getStarPostActivationMark.
    private int starPostActivationMark = -1;
    private int savedX;
    private int savedY;
    private int savedCameraX;
    private int savedCameraY;
    private boolean cameraLock;
    private boolean usedForSpecialStage; // Prevents stars from respawning after SS entry

    // Water state (ROM: v_lamp_wtrpos, v_lamp_wtrrout)
    private int savedWaterLevel;
    private int savedWaterRoutine;
    private boolean hasWaterState;
    private int savedCameraMaxY;
    private int savedDynamicResizeRoutine;
    private boolean hasS3kRuntimeState;
    private byte savedTopSolidBit = 0x0C;
    private byte savedLrbSolidBit = 0x0D;
    private boolean hasSolidBits;

    @com.openggf.game.ModApi
    public record RewindState(
            int lastCheckpointIndex,
            int starPostActivationMark,
            int savedX,
            int savedY,
            int savedCameraX,
            int savedCameraY,
            boolean cameraLock,
            boolean usedForSpecialStage,
            int savedWaterLevel,
            int savedWaterRoutine,
            boolean hasWaterState,
            int savedCameraMaxY,
            int savedDynamicResizeRoutine,
            boolean hasS3kRuntimeState,
            byte savedTopSolidBit,
            byte savedLrbSolidBit,
            boolean hasSolidBits) {
        /** Binary-compatible constructor for the Mod API 2.4 snapshot shape. */
        public RewindState(int lastCheckpointIndex, int savedX, int savedY,
                int savedCameraX, int savedCameraY, boolean cameraLock,
                boolean usedForSpecialStage, int savedWaterLevel, int savedWaterRoutine,
                boolean hasWaterState, int savedCameraMaxY, int savedDynamicResizeRoutine,
                boolean hasS3kRuntimeState, byte savedTopSolidBit, byte savedLrbSolidBit,
                boolean hasSolidBits) {
            this(lastCheckpointIndex, lastCheckpointIndex, savedX, savedY,
                    savedCameraX, savedCameraY, cameraLock, usedForSpecialStage,
                    savedWaterLevel, savedWaterRoutine, hasWaterState, savedCameraMaxY,
                    savedDynamicResizeRoutine, hasS3kRuntimeState, savedTopSolidBit,
                    savedLrbSolidBit, hasSolidBits);
        }
    }

    /**
     * Clear checkpoint state (called on level start/change).
     */
    public void clear() {
        lastCheckpointIndex = -1;
        starPostActivationMark = -1;
        savedX = 0;
        savedY = 0;
        savedCameraX = 0;
        savedCameraY = 0;
        cameraLock = false;
        usedForSpecialStage = false;
        savedWaterLevel = 0;
        savedWaterRoutine = 0;
        hasWaterState = false;
        savedCameraMaxY = 0;
        savedDynamicResizeRoutine = 0;
        hasS3kRuntimeState = false;
        savedTopSolidBit = 0x0C;
        savedLrbSolidBit = 0x0D;
        hasSolidBits = false;
    }

    /**
     * Save checkpoint state from raw values.
     * Game-agnostic — callers extract position/flags from their game-specific checkpoint object.
     */
    public void saveCheckpoint(int checkpointIndex, int x, int y, boolean cameraLockFlag) {
        this.lastCheckpointIndex = checkpointIndex;
        // Bump the persistent activation high-water (never lowered by a save).
        this.starPostActivationMark = Math.max(this.starPostActivationMark, checkpointIndex);
        this.savedX = x;
        this.savedY = y;
        this.cameraLock = cameraLockFlag;

        Camera camera = GameServices.camera();
        this.savedCameraX = camera.getX();
        this.savedCameraY = camera.getY();
        saveProviderRuntimeStateIfPresent(camera);
        savePlayerSolidBitsIfPresent();

        LOGGER.fine("Saved checkpoint " + lastCheckpointIndex + " at (" + savedX + ", " + savedY + ")");
    }

    private void savePlayerSolidBitsIfPresent() {
        Camera camera = GameServices.cameraOrNull();
        if (camera == null || !(camera.getFocusedSprite() instanceof AbstractPlayableSprite playable)) {
            savedTopSolidBit = 0x0C;
            savedLrbSolidBit = 0x0D;
            hasSolidBits = false;
            return;
        }
        savedTopSolidBit = playable.getTopSolidBit();
        savedLrbSolidBit = playable.getLrbSolidBit();
        hasSolidBits = true;
    }

    private void saveProviderRuntimeStateIfPresent(Camera camera) {
        LevelEventProvider eventProvider = GameServices.module().getLevelEventProvider();
        if (camera == null || !(eventProvider instanceof CheckpointRuntimeStateProvider provider)) {
            savedCameraMaxY = 0;
            savedDynamicResizeRoutine = 0;
            hasS3kRuntimeState = false;
            return;
        }
        savedCameraMaxY = camera.getMaxY();
        savedDynamicResizeRoutine = provider.checkpointDynamicResizeRoutine();
        hasS3kRuntimeState = true;
    }

    /**
     * Restore state after player death.
     * ROM behavior: restores position, camera, clears rings.
     */
    public void restoreToPlayer(AbstractPlayableSprite player, Camera camera) {
        if (!isActive()) {
            return;
        }

        // ROM checkpoint x_pos/y_pos are playable centre coordinates.
        player.setCentreX((short) savedX);
        player.setCentreY((short) savedY);

        // Clear player state for fresh start
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setAir(false);
        player.setRolling(false);

        // Clear rings (ROM behavior)
        player.setRingCount(0);

        // Restore camera position directly from saved values (ROM-accurate)
        if (camera != null) {
            camera.setX((short) savedCameraX);
            camera.setY((short) savedCameraY);
            camera.setFocusedSprite(player);
            if (hasS3kRuntimeState) {
                camera.setMaxY((short) savedCameraMaxY);
                camera.setMaxYTarget((short) savedCameraMaxY);
            }

            // Apply camera min X lock if subtype bit 7 was set
            if (cameraLock) {
                int minX = savedX - 0xA0;
                camera.setMinX((short) Math.max(0, minX));
            }
        }

        LOGGER.info("Restored from checkpoint " + lastCheckpointIndex);
    }

    public boolean isActive() {
        return lastCheckpointIndex >= 0;
    }

    public int getLastCheckpointIndex() {
        return lastCheckpointIndex;
    }

    @Override
    public int getStarPostActivationMark() {
        return starPostActivationMark;
    }

    @Override
    public void restoreStarPostActivationMark(int mark) {
        this.starPostActivationMark = mark;
    }

    public int getSavedX() {
        return savedX;
    }

    public int getSavedY() {
        return savedY;
    }

    public int getSavedCameraX() {
        return savedCameraX;
    }

    public int getSavedCameraY() {
        return savedCameraY;
    }

    /**
     * Save water state at checkpoint time.
     * ROM: Lamp_StoreInfo saves v_waterpos2 and v_wtr_routine.
     */
    public void saveWaterState(int waterLevel, int waterRoutine) {
        this.savedWaterLevel = waterLevel;
        this.savedWaterRoutine = waterRoutine;
        this.hasWaterState = true;
    }

    public boolean hasWaterState() {
        return hasWaterState;
    }

    public int getSavedWaterLevel() {
        return savedWaterLevel;
    }

    public int getSavedWaterRoutine() {
        return savedWaterRoutine;
    }

    public boolean isUsedForSpecialStage() {
        return usedForSpecialStage;
    }

    public boolean hasCameraLock() {
        return cameraLock;
    }

    public boolean hasS3kRuntimeState() {
        return hasS3kRuntimeState;
    }

    public int getSavedCameraMaxY() {
        return savedCameraMaxY;
    }

    public int getSavedDynamicResizeRoutine() {
        return savedDynamicResizeRoutine;
    }

    public boolean hasSolidBits() {
        return hasSolidBits;
    }

    public byte getSavedTopSolidBit() {
        return savedTopSolidBit;
    }

    public byte getSavedLrbSolidBit() {
        return savedLrbSolidBit;
    }

    public void markUsedForSpecialStage() {
        this.usedForSpecialStage = true;
        LOGGER.fine("Checkpoint " + lastCheckpointIndex + " marked as used for special stage entry");
    }

    /**
     * Restore checkpoint state from previously saved values.
     * Called after loadLevel() clears the state but we still need checkpoint for
     * respawn.
     */
    public void restoreFromSaved(int x, int y, int cameraX, int cameraY, int checkpointIndex) {
        this.lastCheckpointIndex = checkpointIndex;
        this.savedX = x;
        this.savedY = y;
        this.savedCameraX = cameraX;
        this.savedCameraY = cameraY;
        LOGGER.fine("Restored checkpoint " + checkpointIndex + " state at (" + x + ", " + y + ")");
    }

    public void saveS3kRuntimeState(int cameraMaxY, int dynamicResizeRoutine) {
        this.savedCameraMaxY = cameraMaxY;
        this.savedDynamicResizeRoutine = dynamicResizeRoutine;
        this.hasS3kRuntimeState = true;
    }

    public void saveSolidBits(byte topSolidBit, byte lrbSolidBit) {
        this.savedTopSolidBit = topSolidBit;
        this.savedLrbSolidBit = lrbSolidBit;
        this.hasSolidBits = true;
    }

    public RewindState captureRewindState() {
        return new RewindState(
                lastCheckpointIndex,
                starPostActivationMark,
                savedX,
                savedY,
                savedCameraX,
                savedCameraY,
                cameraLock,
                usedForSpecialStage,
                savedWaterLevel,
                savedWaterRoutine,
                hasWaterState,
                savedCameraMaxY,
                savedDynamicResizeRoutine,
                hasS3kRuntimeState,
                savedTopSolidBit,
                savedLrbSolidBit,
                hasSolidBits);
    }

    public void restoreRewindState(RewindState state) {
        if (state == null) {
            clear();
            return;
        }
        this.lastCheckpointIndex = state.lastCheckpointIndex();
        this.starPostActivationMark = state.starPostActivationMark();
        this.savedX = state.savedX();
        this.savedY = state.savedY();
        this.savedCameraX = state.savedCameraX();
        this.savedCameraY = state.savedCameraY();
        this.cameraLock = state.cameraLock();
        this.usedForSpecialStage = state.usedForSpecialStage();
        this.savedWaterLevel = state.savedWaterLevel();
        this.savedWaterRoutine = state.savedWaterRoutine();
        this.hasWaterState = state.hasWaterState();
        this.savedCameraMaxY = state.savedCameraMaxY();
        this.savedDynamicResizeRoutine = state.savedDynamicResizeRoutine();
        this.hasS3kRuntimeState = state.hasS3kRuntimeState();
        this.savedTopSolidBit = state.savedTopSolidBit();
        this.savedLrbSolidBit = state.savedLrbSolidBit();
        this.hasSolidBits = state.hasSolidBits();
    }
}
