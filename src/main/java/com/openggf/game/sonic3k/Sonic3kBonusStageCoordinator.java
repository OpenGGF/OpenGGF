package com.openggf.game.sonic3k;

import com.openggf.game.AbstractBonusStageCoordinator;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStagePlayerPriorityPolicy;
import com.openggf.game.BonusStageShieldEntryPolicy;
import com.openggf.game.BonusStageDebugControls;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotBonusStageRuntime;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.PachinkoEnergyTrapObjectInstance;
import com.openggf.game.sonic3k.objects.GumballMachineObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

/**
 * S3K-specific bonus stage coordinator.
 * Provides zone ID and music ID mapping for Gumball, Pachinko, and Slots.
 */
public class Sonic3kBonusStageCoordinator extends AbstractBonusStageCoordinator
        implements BonusStageShieldEntryPolicy, BonusStagePlayerPriorityPolicy, BonusStageDebugControls {

    private static final int ZONE_GUMBALL  = 0x1300;
    private static final int ZONE_PACHINKO = 0x1400;
    private static final int ZONE_SLOTS    = 0x1500;

    private static final int MUS_GUMBALL  = 0x1E;
    private static final int MUS_PACHINKO = 0x1B;
    private static final int MUS_SLOTS    = 0x1D;

    private static final int RING_THRESHOLD = 20;
    private static final int RING_DIVISOR   = 15;

    private static final BonusStageProvider.BootstrapObject PACHINKO_BOOTSTRAP =
            new BonusStageProvider.BootstrapObject(
                    new ObjectSpawn(0x78, 0x0F30,
                            Sonic3kObjectIds.PACHINKO_ENERGY_TRAP,
                            0, 0, false, 0),
                    PachinkoEnergyTrapObjectInstance.class,
                    PachinkoEnergyTrapObjectInstance::new);

    private S3kSlotBonusStageRuntime slotRuntime;

    @Override
    public boolean supportsRewind() {
        return getActiveType() == BonusStageType.SLOT_MACHINE || super.supportsRewind();
    }

    @Override protected Object captureBonusRuntimeState() {
        return slotRuntime == null ? null : slotRuntime.captureSlotRewindState();
    }
    @Override protected void restoreBonusRuntimeState(Object state) {
        if (state == null) {
            if (slotRuntime != null && slotRuntime.isInitialized())
                throw new IllegalStateException("Slot snapshot predates completed runtime assembly");
        } else {
            if (slotRuntime == null) throw new IllegalStateException("Slot runtime has retired");
            slotRuntime.restoreSlotRewindState(state);
        }
    }
    @Override protected void resetBonusRuntimeStateForMissingSnapshot() {
        if (slotRuntime != null && slotRuntime.isInitialized())
            throw new IllegalStateException("Active Slot runtime requires its captured state");
    }
    @Override public void afterBonusRuntimeRewindRestore() {
        if (slotRuntime != null) slotRuntime.rebindRuntimeRewindObjects();
    }

    @Override
    public boolean restoresSavedShieldOnEntry() {
        // SpawnLevelMainSprites_SpawnPowerup (sonic3k.asm:8299-8309)
        // restores zones $13/$14, but
        // returns for $15 (Slots). Saved shield bits remain for the return.
        return getActiveType() != BonusStageType.SLOT_MACHINE;
    }

    @Override
    public boolean shouldForcePlayerHighPriority() {
        // Obj_GumballMachine sets both players' art_tile priority bits. Slots
        // uses Obj_Sonic_RotatingSlotBonus / loc_4B9E8's make_art_tile(...,0,0)
        // instead: promoting it after physics hides the foreground glass.
        return getActiveType() != BonusStageType.SLOT_MACHINE;
    }

    @Override
    public BonusStageProvider.BootstrapObject bootstrapObject(BonusStageType type) {
        return type == BonusStageType.GLOWING_SPHERE ? PACHINKO_BOOTSTRAP : null;
    }

    @Override
    public void cycleObjectRenderFilter() {
        // These diagnostics retain their effects while any native bonus type is active.
        GumballMachineObjectInstance.cycleDebugFilter();
    }

    @Override
    public void cycleObjectSourceFilter() {
        GumballMachineObjectInstance.cycleDebugSourceFilter();
    }

    @Override
    public BonusStageType selectBonusStage(int ringCount) {
        return selectBonusStage(ringCount, Sonic3kCartridgePolicy.isStandaloneSk());
    }

    public static BonusStageType selectBonusStage(int ringCount, boolean standaloneSk) {
        if (ringCount < RING_THRESHOLD) return BonusStageType.NONE;
        // loc_2D47E / loc_2D4AE (sonic3k.asm:61931-61957):
        // Standalone S&K uses divisor 2, so only Slots and Pachinko are reachable.
        int stageCount = standaloneSk ? 2 : 3;
        int remainder = ((ringCount - RING_THRESHOLD) / RING_DIVISOR) % stageCount;
        return switch (remainder) {
            case 0 -> BonusStageType.SLOT_MACHINE;
            case 1 -> BonusStageType.GLOWING_SPHERE;
            case 2 -> BonusStageType.GUMBALL;
            default -> BonusStageType.NONE;
        };
    }

    @Override
    public int getZoneId(BonusStageType type) {
        return switch (type) {
            case GUMBALL -> ZONE_GUMBALL;
            case GLOWING_SPHERE -> ZONE_PACHINKO;
            case SLOT_MACHINE -> ZONE_SLOTS;
            default -> -1;
        };
    }

    @Override
    public int getMusicId(BonusStageType type) {
        return switch (type) {
            case GUMBALL -> MUS_GUMBALL;
            case GLOWING_SPHERE -> MUS_PACHINKO;
            case SLOT_MACHINE -> MUS_SLOTS;
            default -> -1;
        };
    }

    @Override
    public void onDeferredSetupComplete() {
        if (getActiveType() != BonusStageType.SLOT_MACHINE) {
            return;
        }
        slotRuntime = new S3kSlotBonusStageRuntime();
        slotRuntime.bootstrap();
        if (!slotRuntime.isInitialized()) {
            slotRuntime = null;
            return;
        }
    }

    @Override
    public void onFrameUpdate() {
        if (slotRuntime != null) {
            slotRuntime.update(romLevelFrameCounter());
            if (slotRuntime.isExitTriggered()) {
                requestExit();
            }
        }
    }

    @Override
    public boolean updateDuringLevelFrame() {
        return slotRuntime != null;
    }

    @Override
    public boolean suppressesDefaultCameraStep() {
        return slotRuntime != null;
    }

    @Override
    public void onExit() {
        if (slotRuntime != null) {
            slotRuntime.shutdown();
            slotRuntime = null;
        }
        super.onExit();
    }

    /**
     * The ROM's object-visible {@code Level_frame_counter}.
     *
     * <p>Every S3K/S2/S1 main loop increments it immediately after
     * {@code Wait_VSync} and BEFORE the object pass -- for the bonus/special
     * stages see {@code sonic3k.asm:10778-10780} and {@code :63247-63249}, where
     * {@code addq.w #1,(Level_frame_counter).w} sits between {@code Wait_VSync}
     * and {@code Process_Sprites} -- so an object running this frame reads the
     * already-incremented value. The engine advances its own counter at the same
     * point, in {@code LevelFrameStep} via
     * {@code LevelManager.advanceLevelFrameCounter()}, so every ported
     * {@code Level_frame_counter} gate reads {@code getFrameCounter()} with no
     * adjustment.
     *
     * <p>This replaces a free-running counter that was seeded from the level
     * counter once at stage setup and then self-incremented. It read one below
     * the ROM's object-visible value, so the cage's reward-spawn gate
     * {@code btst #0,(Level_frame_counter+1).w} ({@code sonic3k.asm:99481},
     * {@code :99463}) fired on the ROM's EVEN frames instead of its odd ones.
     */
    private int romLevelFrameCounter() {
        var levelManager = GameServices.levelOrNull();
        return levelManager != null ? levelManager.getFrameCounter() : 0;
    }

    public S3kSlotBonusStageRuntime activeSlotRuntime() {
        return slotRuntime;
    }

    public S3kSlotBonusStageRuntime activeSlotRuntimeForTest() {
        return activeSlotRuntime();
    }
}
