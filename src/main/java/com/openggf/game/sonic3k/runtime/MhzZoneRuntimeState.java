package com.openggf.game.sonic3k.runtime;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kMHZEvents;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Runtime-shared MHZ state.
 *
 * <p>The ROM stores MHZ deform outputs in the events workspace
 * ({@code Camera_X_pos_BG_copy}, {@code Events_bg+$10}, {@code Events_bg+$12})
 * and {@code AnimateTiles_MHZ} reads them later in the frame. This adapter keeps
 * that contract explicit without coupling animation code to the scroll handler.
 */
public final class MhzZoneRuntimeState implements S3kZoneRuntimeState {
    // ROM level setup runs Process_Sprites then Animate_Tiles before LevelLoop
    // (sonic3k.asm:7885-7887). MHZ caps read Anim_Counters+$F during
    // Process_Sprites (82199), and AnimateTiles_MHZ advances it by 2 later
    // in the same frame (54901-54908, 7894-7906).
    private static final int INITIAL_MUSHROOM_CAP_POSITION_COUNTER = 2;

    private final int actIndex;
    private final PlayerCharacter playerCharacter;
    private final Sonic3kMHZEvents events;
    // _unkFAA9 in MHZ1: survives button/door unloading, but loc_60DE clears
    // this work RAM on a full level reload (including checkpoint respawn).
    private boolean cutsceneDoorLowered;
    private int pollenParticleCount;
    private int pollenLeafPatternCounter;
    private int mushroomCapPositionCounter = INITIAL_MUSHROOM_CAP_POSITION_COUNTER;

    public MhzZoneRuntimeState(int actIndex, PlayerCharacter playerCharacter) {
        this(actIndex, playerCharacter, null);
    }

    public MhzZoneRuntimeState(int actIndex, PlayerCharacter playerCharacter, Sonic3kMHZEvents events) {
        this.actIndex = actIndex;
        this.playerCharacter = Objects.requireNonNull(playerCharacter, "playerCharacter");
        this.events = events == null ? new Sonic3kMHZEvents() : events;
    }

    @Override public int zoneIndex() { return Sonic3kZoneIds.ZONE_MHZ; }
    @Override public int actIndex() { return actIndex; }
    /** Captured event/boundary state owns the native-window projection across the reload. */
    public boolean centerNativeArenaCamera() {
        return events != null && (actIndex == 0 ? events.isBossFlag()
                : actIndex == 1 && (events.hasInheritedActOneCameraLock() || events.isEndBossNativeCameraActive()));
    }
    public void activateEndBossNativeCamera() {
        if (events != null) events.activateEndBossNativeCamera();
    }

    @Override public PlayerCharacter playerCharacter() { return playerCharacter; }
    @Override public int getDynamicResizeRoutine() { return events == null ? 0 : events.getDynamicResizeRoutine(); }
    @Override public boolean isActTransitionFlagActive() {
        return events != null && events.isActTransitionFlagActive();
    }

    public Sonic3kMHZEvents.SeasonPaletteMode seasonPaletteMode() {
        return events == null ? Sonic3kMHZEvents.SeasonPaletteMode.GREEN : events.getSeasonPaletteMode();
    }

    public boolean isSeasonFlagSet() {
        return events != null && events.isSeasonFlagSet();
    }

    public void clearSeasonFlag() {
        if (events != null) {
            events.clearSeasonFlag();
        }
    }

    public boolean isAutumnTriggerFlagSet() {
        return events != null && events.isAutumnTriggerFlagSet();
    }

    public boolean isLeafBlowerCutsceneFlagSet() {
        return events != null && events.isLeafBlowerCutsceneFlagSet();
    }

    public void setLeafBlowerCutsceneFlag(boolean active) {
        if (events != null) {
            events.setLeafBlowerCutsceneFlag(active);
        }
    }

    public boolean isShipSequenceActive() {
        return events != null && events.isShipHIntActive();
    }

    public int shipHIntCounter() {
        return events == null ? 0 : events.getShipHIntCounter();
    }

    public void applyShipControllerFrame(int motionAccumulator, int swingOffset) {
        if (events != null) {
            events.applyShipControllerFrame(motionAccumulator, swingOffset);
        }
    }

    public int shipSecondaryBgCameraXFixed() {
        return events == null ? 0 : events.getShipSecondaryBgCameraXFixed();
    }

    public int shipEffectiveBgY() {
        return events == null ? 0 : events.getShipEffectiveBgY();
    }

    public boolean isShipScrollLockSet() {
        return events != null && events.isShipScrollLockSet();
    }

    public void setShipControllerSignalFlag(boolean active) {
        if (events != null) events.setShipControllerSignalFlag(active);
    }

    public boolean isShipControllerSignalFlagSet() {
        return events != null && events.isShipControllerSignalFlagSet();
    }

    public void signalShipTransition() {
        if (events != null) {
            events.setShipTransitionFlag(true);
        }
    }

    public void signalEndBossWalkoffPrep() {
        if (events != null) {
            events.signalEndBossWalkoffPrep();
        }
    }

    public int endBossWalkoffPrepEventFlag() {
        return events == null ? 0 : events.getEndBossWalkoffPrepEventFlag();
    }

    @Override
    public int levelRepeatOffset() {
        return events == null ? 0 : events.getLevelRepeatOffset();
    }

    public int shipHScrollCameraCopy() {
        return events == null ? 0 : events.getShipHScrollCameraCopy();
    }

    public int shipPrimaryHScroll() {
        return events == null ? 0 : events.getShipPrimaryHScroll();
    }

    public boolean isEndBossArenaBackgroundActive() {
        return events != null && events.isEndBossArenaBackgroundActive();
    }

    public boolean isBossAreaBackgroundDeformActive() {
        return events != null && events.isBossAreaBackgroundDeformActive();
    }

    public boolean isEndBossArenaForegroundRefreshActive() {
        return events != null && events.isEndBossArenaForegroundRefreshActive();
    }

    public int screenShakeOffset() {
        return events == null ? 0 : events.getScreenShakeOffset();
    }

    public int shipPropellerOneX() {
        return events == null ? 0 : events.getShipPropellerOneX();
    }

    public int shipPropellerTwoX() {
        return events == null ? 0 : events.getShipPropellerTwoX();
    }

    public int shipPropellerY() {
        return events == null ? 0 : events.getShipPropellerY();
    }

    public void requestScreenShake(int frames) { if (events != null) events.requestScreenShake(frames); }
    public int appliedScreenShakeOffset() { return events.appliedScreenShakeOffset(); }
    public void publishDeformOutputs(int bg, int middle, int near) { events.publishDeformOutputs(bg, middle, near); }
    public void publishEndBossArenaHelperXPositions(int support, int[] spikes) { events.publishEndBossArenaHelperXPositions(support, spikes); }
    public int loopAdjustedCameraX() { return events == null ? 0 : events.loopAdjustedCameraX(); }
    public int endBossArenaTallSupportX() { return events == null ? 0 : events.endBossArenaTallSupportX(); }
    public int endBossArenaSpikeX(int index) { return events == null || index < 0 || index >= 6 ? 0 : events.endBossArenaSpikeX(index); }

    public boolean isBackedBy(Sonic3kMHZEvents candidate) {
        return events == candidate;
    }

    public int publishedBgCameraX() { return events == null ? 0 : events.publishedBgCameraX(); }
    public int middleBgCameraX() { return events == null ? 0 : events.middleBgCameraX(); }
    public int nearBgCameraX() { return events == null ? 0 : events.nearBgCameraX(); }
    public int backgroundLayer1Phase() { return (nearBgCameraX() - publishedBgCameraX()) & 0x1F; }
    public int backgroundLayer2Phase() { return (middleBgCameraX() - publishedBgCameraX()) & 0x3F; }

    public boolean tryReservePollenParticle() {
        if (pollenParticleCount >= 0x10) {
            return false;
        }
        pollenParticleCount++;
        return true;
    }

    public void reservePollenParticleAfterSpawnerGate() {
        pollenParticleCount++;
    }

    public void releasePollenParticle() {
        if (pollenParticleCount > 0) {
            pollenParticleCount--;
        }
    }

    public boolean isCutsceneDoorLowered() {
        return cutsceneDoorLowered;
    }

    public void setCutsceneDoorLowered(boolean lowered) {
        cutsceneDoorLowered = lowered;
    }

    public int pollenParticleCount() {
        return pollenParticleCount;
    }

    public int pollenLeafPatternCounter() {
        return pollenLeafPatternCounter;
    }

    public boolean nextPollenParticleUsesBigLeaf() {
        int counter = pollenLeafPatternCounter;
        pollenLeafPatternCounter = (pollenLeafPatternCounter + 1) & 0xFF;
        int index = counter & 7;
        return index != 2 && index != 6 && index != 7;
    }

    public int mushroomCapPositionCounter() {
        return mushroomCapPositionCounter;
    }

    public void publishMushroomCapPositionCounter(int counter) {
        mushroomCapPositionCounter = counter & 0xFF;
    }

    public void advanceMushroomCapPositionCounter() {
        int next = (mushroomCapPositionCounter + 2) & 0xFF;
        mushroomCapPositionCounter = next < 0x58 ? next : 0;
    }

    @Override
    public byte[] captureBytes() {
        return ByteBuffer.allocate(20).putInt(pollenParticleCount).putInt(pollenLeafPatternCounter)
                .putInt(mushroomCapPositionCounter).putInt(cutsceneDoorLowered ? 1 : 0).putInt(1).array();
    }
    @Override
    public void restoreBytes(byte[] bytes) {
        if (bytes == null || bytes.length != 20) throw new IllegalArgumentException("Invalid MHZ runtime payload");
        ByteBuffer in = ByteBuffer.wrap(bytes);
        int count = in.getInt(), leaf = in.getInt(), cap = in.getInt(), door = in.getInt(), version = in.getInt();
        if (count < 0 || leaf < 0 || leaf > 255 || cap < 0 || cap > 255
                || door < 0 || door > 1 || version != 1) throw new IllegalArgumentException("Invalid MHZ runtime values");
        pollenParticleCount = count; pollenLeafPatternCounter = leaf;
        mushroomCapPositionCounter = cap; cutsceneDoorLowered = door == 1;
    }
}
