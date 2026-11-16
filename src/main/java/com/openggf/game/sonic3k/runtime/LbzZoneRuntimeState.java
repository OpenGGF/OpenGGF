package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.OptionalInt;

public final class LbzZoneRuntimeState implements S3kZoneRuntimeState {
    /** Widescreen projection selected by the native placed Knuckles final-arena owner. */
    private boolean centerNativeFinalArenaCamera;
    public boolean centerNativeArenaCamera() { return centerNativeFinalArenaCamera; }
    public void setCenterNativeArenaCamera(boolean value) { centerNativeFinalArenaCamera = value; }

    @com.openggf.game.rewind.RewindTransient(reason = "event owner reference; publication captured by events")
    private final com.openggf.game.sonic3k.events.Sonic3kLBZEvents events;
    @com.openggf.game.rewind.RewindTransient(reason = "registry adapter; event owner captures the whole publication")
    private final boolean managed;
    private final int actIndex;
    private final PlayerCharacter playerCharacter;
    private boolean alarmAnimationActive;
    private int activeInteriorLayoutMod;
    private boolean interiorLayoutMod3Disabled;
    private boolean robotnikIntroActive;
    private boolean lbz2LayoutAdjustApplied;
    private boolean lbz2EntryCorridorApplied;
    private int rollingDrumP1Angle;
    private int rollingDrumP2Angle;
    private boolean launchActive;
    private boolean deathEggRumble;
    private int fgLaunchSpeed;
    private int bgLaunchSpeed;
    private int fgAccum;
    private int bgAccum;
    private int launchYDelta;
    private int preLaunchDelay;
    private int detachScroll;
    private int detachWindowCopyTimer;
    private int waterTargetY;
    private boolean launchStartRequested;
    private boolean padCollapseStartRequested;
    private boolean finalFallRequested;
    private boolean launchRiderAnchorRegistered;
    private boolean lbz2RideAnimatedTileGateActive;
    private boolean padCollapseActive;
    private boolean finalFallActive;
    private boolean deathEggTerrainSwapQueued;
    private boolean deathEggTerrainSwapApplied;
    private boolean waterDisabled;
    private boolean lbz1KnucklesCutsceneControlLocked;
    private boolean lbz1KnucklesBoundaryPublishPending;
    private int launchRiderAnchorId;
    private int launchRiderDelta;
    private boolean launchFallingAccelActive;
    private int deathEggDeformWrapLatch;
    private boolean finaleCutsceneAnchorRegistered;
    private int finaleCutsceneAnchorId;
    private int lbz2WaterlinePhase;
    private int lbz2ScrollArtPhaseSource;
    private int publishedBgCameraX;

    public LbzZoneRuntimeState(int actIndex, PlayerCharacter playerCharacter) {
        this(actIndex, playerCharacter, new com.openggf.game.sonic3k.events.Sonic3kLBZEvents(), false);
    }

    public LbzZoneRuntimeState(int actIndex, PlayerCharacter playerCharacter,
            com.openggf.game.sonic3k.events.Sonic3kLBZEvents events) {
        this(actIndex, playerCharacter, events, true);
    }
    private LbzZoneRuntimeState(int actIndex, PlayerCharacter playerCharacter,
            com.openggf.game.sonic3k.events.Sonic3kLBZEvents events, boolean managed) {
        this.managed = managed;
        this.events = Objects.requireNonNull(events);
        this.actIndex = actIndex;
        this.playerCharacter = Objects.requireNonNull(playerCharacter, "playerCharacter");
        events.bindRuntimePublication(this);
    }

    @Override public int zoneIndex() { return Sonic3kZoneIds.ZONE_LBZ; }
    @Override public int actIndex() { return actIndex; }
    @Override public PlayerCharacter playerCharacter() { return playerCharacter; }
    @Override public int getDynamicResizeRoutine() { return 0; }
    @Override public boolean isActTransitionFlagActive() { return false; }
    @Override public boolean advancesOscillationOnSeamlessTransition() { return true; }

    public boolean isAlarmAnimationActive() {
        return alarmAnimationActive;
    }

    public void setAlarmAnimationActive(boolean alarmAnimationActive) {
        this.alarmAnimationActive = alarmAnimationActive;
    }

    public int getActiveInteriorLayoutMod() {
        return activeInteriorLayoutMod;
    }

    public void setActiveInteriorLayoutMod(int activeInteriorLayoutMod) {
        this.activeInteriorLayoutMod = Math.max(0, Math.min(4, activeInteriorLayoutMod));
    }

    public boolean isInteriorLayoutMod3Disabled() {
        return interiorLayoutMod3Disabled;
    }

    public void setInteriorLayoutMod3Disabled(boolean interiorLayoutMod3Disabled) {
        this.interiorLayoutMod3Disabled = interiorLayoutMod3Disabled;
        if (interiorLayoutMod3Disabled && activeInteriorLayoutMod == 3) {
            activeInteriorLayoutMod = 0;
        }
    }

    /**
     * ROM {@code _unkFAAB}: set by {@code Obj_LBZ1Robotnik} init so the
     * standalone {@code Obj_LBZMinibossBox} deletes itself while the Robotnik
     * intro owns the miniboss handoff.
     */
    public boolean isRobotnikIntroActive() {
        return robotnikIntroActive;
    }

    public void setRobotnikIntroActive(boolean robotnikIntroActive) {
        this.robotnikIntroActive = robotnikIntroActive;
    }

    /** ROM Adjust_LBZ2Layout applied-once latch for the loaded LBZ2 layout. */
    public boolean isLbz2LayoutAdjustApplied() {
        return lbz2LayoutAdjustApplied;
    }

    public void setLbz2LayoutAdjustApplied(boolean applied) {
        this.lbz2LayoutAdjustApplied = applied;
    }

    /** ROM LBZ2_LayoutMod applied-once latch (Events_routine_fg 0 -> 4). */
    public boolean isLbz2EntryCorridorApplied() {
        return lbz2EntryCorridorApplied;
    }

    public void setLbz2EntryCorridorApplied(boolean applied) {
        this.lbz2EntryCorridorApplied = applied;
    }

    public int getRollingDrumAngle(int nativePlayerIndex) {
        return nativePlayerIndex == 0 ? rollingDrumP1Angle : rollingDrumP2Angle;
    }

    public void setRollingDrumAngle(int nativePlayerIndex, int angle) {
        int normalized = angle & 0xFF;
        if (nativePlayerIndex == 0) {
            rollingDrumP1Angle = normalized;
        } else {
            rollingDrumP2Angle = normalized;
        }
    }

    public void setupScreenShake(int frame, boolean dead) { events.setupScreenShake(frame, dead); }
    public void publishScreenShakeForeground() { events.publishScreenShakeForeground(); }
    public void writeScreenShakeFlag(int value) { events.writeScreenShakeFlag(value); }
    public int screenShakeFlag() { return events.screenShakeFlag(); }
    public int screenShakeOffset() { return events.appliedScreenShakeOffset(); }
    public void startTimedScreenShake(int frames) { writeScreenShakeFlag(frames); }
    public int getTimedShakeCountdown() { return Math.max(0, screenShakeFlag()); }
    public int getTimedShakePreparedOffset() { return events.screenShakeOffset(); }
    public int getTimedShakeAppliedOffset() { return screenShakeOffset(); }
    public int getCameraYCopy(int baseCameraY) { return events.foregroundCameraYCopy(baseCameraY); }

    public boolean isLaunchActive() {
        return launchActive;
    }

    public void setLaunchActive(boolean launchActive) {
        this.launchActive = launchActive;
    }

    public boolean isDeathEggRumble() {
        return deathEggRumble;
    }

    public void setDeathEggRumble(boolean deathEggRumble) {
        this.deathEggRumble = deathEggRumble;
    }

    public int getFgLaunchSpeed() {
        return fgLaunchSpeed;
    }

    public void setFgLaunchSpeed(int fgLaunchSpeed) {
        this.fgLaunchSpeed = fgLaunchSpeed;
    }

    public int getBgLaunchSpeed() {
        return bgLaunchSpeed;
    }

    public void setBgLaunchSpeed(int bgLaunchSpeed) {
        this.bgLaunchSpeed = bgLaunchSpeed;
    }

    public int getFgAccum() {
        return fgAccum;
    }

    public void setFgAccum(int fgAccum) {
        this.fgAccum = fgAccum;
    }

    public int getBgAccum() {
        return bgAccum;
    }

    public void setBgAccum(int bgAccum) {
        this.bgAccum = bgAccum;
    }

    public int getLaunchYDelta() {
        return launchYDelta;
    }

    public void setLaunchYDelta(int launchYDelta) {
        this.launchYDelta = launchYDelta;
    }

    /**
     * ROM LBZ2_DeathEggMoveScreen: while Scroll_lock the FG launch delta is
     * added to the registered rider object's y_pos (the hang-ride ship).
     * Published by the launch event each frame and consumed by the ship.
     */
    public void setLaunchRiderDelta(int launchRiderDelta) {
        this.launchRiderDelta = launchRiderDelta;
    }

    /** Read the preceding event publication; object observation never clears it. */
    public int getLaunchRiderDelta() { return launchRiderDelta; }
    public boolean isBackedBy(com.openggf.game.sonic3k.events.Sonic3kLBZEvents owner) { return events == owner; }

    /** ROM: LBZ2_EndFallingAccel runs every frame once Events_fg_5 (2nd use) fires. */
    public boolean isLaunchFallingAccelActive() {
        return launchFallingAccelActive;
    }

    public void setLaunchFallingAccelActive(boolean launchFallingAccelActive) {
        this.launchFallingAccelActive = launchFallingAccelActive;
    }

    public int getPreLaunchDelay() {
        return preLaunchDelay;
    }

    public void setPreLaunchDelay(int preLaunchDelay) {
        this.preLaunchDelay = preLaunchDelay;
    }

    public int getDetachScroll() {
        return detachScroll;
    }

    public void setDetachScroll(int detachScroll) {
        this.detachScroll = detachScroll;
    }

    public int getDetachWindowCopyTimer() {
        return detachWindowCopyTimer;
    }

    public void setDetachWindowCopyTimer(int detachWindowCopyTimer) {
        this.detachWindowCopyTimer = Math.max(0, detachWindowCopyTimer);
    }

    public int getWaterTargetY() {
        return waterTargetY;
    }

    public void setWaterTargetY(int waterTargetY) {
        this.waterTargetY = waterTargetY & 0xFFFF;
    }

    public void setWaterTargetYForTest(int waterTargetY) {
        setWaterTargetY(waterTargetY);
    }

    public boolean isPadCollapseActive() {
        return padCollapseActive;
    }

    public void setPadCollapseActive(boolean padCollapseActive) {
        this.padCollapseActive = padCollapseActive;
    }

    public boolean isFinalFallActive() {
        return finalFallActive;
    }

    public void setFinalFallActive(boolean finalFallActive) {
        this.finalFallActive = finalFallActive;
    }

    public boolean isDeathEggTerrainSwapQueued() {
        return deathEggTerrainSwapQueued;
    }

    public void setDeathEggTerrainSwapQueued(boolean deathEggTerrainSwapQueued) {
        this.deathEggTerrainSwapQueued = deathEggTerrainSwapQueued;
    }

    public boolean isDeathEggTerrainSwapApplied() {
        return deathEggTerrainSwapApplied;
    }

    public void setDeathEggTerrainSwapApplied(boolean deathEggTerrainSwapApplied) {
        this.deathEggTerrainSwapApplied = deathEggTerrainSwapApplied;
    }

    public boolean isWaterDisabled() {
        return waterDisabled;
    }

    public void setWaterDisabled(boolean waterDisabled) {
        this.waterDisabled = waterDisabled;
    }

    /**
     * ROM {@code _unkFAA9}: set by {@code CutsceneKnux_LBZ1}'s range helper
     * while Knuckles owns player input, and cleared when Knuckles exits.
     */
    public boolean isLbz1KnucklesCutsceneControlLocked() {
        return lbz1KnucklesCutsceneControlLocked;
    }

    public void setLbz1KnucklesCutsceneControlLocked(boolean locked) {
        this.lbz1KnucklesCutsceneControlLocked = locked;
    }

    /**
     * Continuous ROM {@code Screen_shake_flag=-1} bridge for the LBZ1 Knuckles
     * bomb sequence from the post-drop wait through the completed building
     * collapse.
     */
    public boolean isLbz1KnucklesBombShakeActive() {
        return (screenShakeFlag() & 0xFF00) != 0;
    }

    public void setLbz1KnucklesBombShakeActive(boolean active) {
        writeScreenShakeFlag(active ? 0xFF00 | (screenShakeFlag() & 0xFF) : 0);
    }

    public int getDeathEggDeformWrapLatch() {
        return deathEggDeformWrapLatch;
    }

    public void setDeathEggDeformWrapLatch(int deathEggDeformWrapLatch) {
        this.deathEggDeformWrapLatch = deathEggDeformWrapLatch & 0xFFFF;
    }

    public void requestLaunchStart() {
        launchStartRequested = true;
    }

    public boolean consumeLaunchStartRequested() {
        boolean requested = launchStartRequested;
        launchStartRequested = false;
        return requested;
    }

    public void requestPadCollapseStart() {
        padCollapseStartRequested = true;
    }

    public boolean consumePadCollapseStartRequested() {
        boolean requested = padCollapseStartRequested;
        padCollapseStartRequested = false;
        return requested;
    }

    public void requestFinalFall() {
        finalFallRequested = true;
    }

    public boolean consumeFinalFallRequested() {
        boolean requested = finalFallRequested;
        finalFallRequested = false;
        return requested;
    }

    public void requestLbz2RideAnimatedTiles() {
        setLbz2RideAnimatedTileGateActive(true);
    }

    public boolean consumeLbz2RideAnimatedTilesRequested() {
        return isLbz2RideAnimatedTileGateActive();
    }

    public boolean isLbz2RideAnimatedTileGateActive() {
        return lbz2RideAnimatedTileGateActive;
    }

    public void requestLbz1KnucklesBoundaryPublish() {
        lbz1KnucklesBoundaryPublishPending = true;
    }

    public boolean isLbz1KnucklesBoundaryPublishPending() {
        return lbz1KnucklesBoundaryPublishPending;
    }

    public void clearLbz1KnucklesBoundaryPublishPending() {
        lbz1KnucklesBoundaryPublishPending = false;
    }

    public void setLbz2RideAnimatedTileGateActive(boolean active) {
        lbz2RideAnimatedTileGateActive = active;
    }

    /**
     * Publishes the ROM global values used by {@code AnimateTiles_LBZ2}.
     *
     * <p>{@code Events_bg+$10} drives the dynamic waterline art, while
     * {@code Events_bg+$12 - Camera_X_pos_BG_copy} drives the scroll-art phase.
     * The scroll/deform routine owns these values, including the Death Egg
     * deform path where they intentionally diverge from the normal camera
     * formula.
     */
    public void publishLbz2DeformOutputs(int waterlinePhase, int scrollArtPhaseSource, int bgCameraX) {
        this.lbz2WaterlinePhase = waterlinePhase & 0xFFFF;
        this.lbz2ScrollArtPhaseSource = scrollArtPhaseSource & 0xFFFF;
        this.publishedBgCameraX = bgCameraX & 0xFFFF;
    }

    public int lbz2WaterlinePhase() {
        return lbz2WaterlinePhase;
    }

    public int lbz2ScrollArtPhaseSource() {
        return lbz2ScrollArtPhaseSource;
    }

    public int publishedBgCameraX() {
        return publishedBgCameraX;
    }

    public int lbz2ScrollArtPhase() {
        return (lbz2ScrollArtPhaseSource - publishedBgCameraX) & 0x0F;
    }

    /**
     * Reference-free launch rider identity for later object/event agents.
     *
     * <p>The ROM uses an object pointer in {@code Events_bg+0}; the Java
     * runtime state stores only a caller-supplied stable anchor id so rewind
     * snapshots never capture object references.
     */
    public void registerLaunchRiderAnchor(int anchorId) {
        launchRiderAnchorRegistered = true;
        launchRiderAnchorId = anchorId;
    }

    public OptionalInt getLaunchRiderAnchorId() {
        return launchRiderAnchorRegistered ? OptionalInt.of(launchRiderAnchorId) : OptionalInt.empty();
    }

    public void clearLaunchRiderAnchor() {
        launchRiderAnchorRegistered = false;
        launchRiderAnchorId = 0;
    }

    public void registerFinaleCutsceneAnchor(int anchorId) {
        finaleCutsceneAnchorRegistered = true;
        finaleCutsceneAnchorId = anchorId;
    }

    public OptionalInt getFinaleCutsceneAnchorId() {
        return finaleCutsceneAnchorRegistered ? OptionalInt.of(finaleCutsceneAnchorId) : OptionalInt.empty();
    }

    public void clearFinaleCutsceneAnchor() {
        finaleCutsceneAnchorRegistered = false;
        finaleCutsceneAnchorId = 0;
    }

    @Override public byte[] captureBytes() { return managed ? new byte[0] : events.captureLbzState(); }
    /** Constructor identity is structural to the scalar codec, so frame it explicitly. */
    public byte[] captureNumericPublication() {
        byte[] scalars = com.openggf.game.rewind.schema.ZoneEventSchemaSidecar.capture(this);
        return ByteBuffer.allocate(12 + scalars.length).putInt(actIndex)
                .putInt(playerCharacter.ordinal()).putInt(scalars.length).put(scalars).array();
    }
    public static int capturedAct(byte[] bytes) { return ByteBuffer.wrap(bytes).getInt(); }
    public static PlayerCharacter capturedPlayer(byte[] bytes) { return PlayerCharacter.values()[ByteBuffer.wrap(bytes).getInt(4)]; }
    public static boolean hasValidRuntimePublication(byte[] bytes) {
        if (bytes == null || bytes.length < 12) return false;
        var in = ByteBuffer.wrap(bytes);
        int act = in.getInt(), player = in.getInt(), length = in.getInt();
        if (act < 0 || act > 1 || player < 0 || player >= PlayerCharacter.values().length || length != in.remaining()) return false;
        var probe = new LbzZoneRuntimeState(act, PlayerCharacter.values()[player]);
        if (length != com.openggf.game.rewind.schema.ZoneEventSchemaSidecar.capture(probe).length) return false;
        byte[] scalars = new byte[length]; in.get(scalars);
        if (!com.openggf.game.rewind.schema.ZoneEventSchemaSidecar.hasValidVariableLengthPayload(LbzZoneRuntimeState.class, scalars)) return false;
        com.openggf.game.rewind.schema.ZoneEventSchemaSidecar.restore(probe, scalars);
        return java.util.Arrays.equals(bytes, probe.captureNumericPublication())
                && probe.activeInteriorLayoutMod >= 0 && probe.activeInteriorLayoutMod <= 4
                && (probe.rollingDrumP1Angle & ~255) == 0 && (probe.rollingDrumP2Angle & ~255) == 0
                && probe.launchYDelta == (short) probe.launchYDelta
                && probe.launchRiderDelta == (short) probe.launchRiderDelta
                && unsignedWords(probe.fgLaunchSpeed, probe.preLaunchDelay, probe.detachScroll,
                        probe.detachWindowCopyTimer, probe.waterTargetY, probe.deathEggDeformWrapLatch,
                        probe.lbz2WaterlinePhase, probe.lbz2ScrollArtPhaseSource, probe.publishedBgCameraX);
    }
    private static boolean unsignedWords(int... values) {
        for (int value : values) if ((value & ~0xFFFF) != 0) return false;
        return true;
    }
    public void restoreRuntimePublication(byte[] bytes) {
        if (!hasValidRuntimePublication(bytes) || capturedAct(bytes) != actIndex || capturedPlayer(bytes) != playerCharacter)
            throw new IllegalArgumentException("Invalid LBZ runtime publication or identity");
        com.openggf.game.rewind.schema.ZoneEventSchemaSidecar.restore(this, java.util.Arrays.copyOfRange(bytes, 12, bytes.length));
    }
    @Override public void restoreBytes(byte[] bytes) {
        if (managed && bytes != null && bytes.length == 0) return;
        if (!com.openggf.game.sonic3k.events.Sonic3kLBZEvents.hasRuntimeIdentity(bytes, actIndex, playerCharacter))
            throw new IllegalArgumentException("Invalid LBZ publication identity");
        events.restoreLbzState(bytes);
    }
}
