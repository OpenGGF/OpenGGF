package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.events.S3kFbzEventWriteSupport;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;

import java.util.List;

/** Locked-on {@code Obj_FBZEndBossEventControl} (sonic3k.asm:109825-109909). */
public final class FbzEndBossEventControlInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable {
    private static final int X_STEP_16_16 = 0x7800;
    private static final int Y_STEP_16_16 = 0xA000;
    private static final int X_ENDPOINT = 0x45C;
    private static final int Y_ENDPOINT = 0x5D0;
    private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(0x4C0, 0x11, 0x11);

    public enum Phase { WAIT_P1_TRIGGER, MOVING, WAIT_CAMERA_Y_LOCK, WAIT_ARENA_LOCK, WAIT_BOSS_STAGE, COMPLETE }

    public record NativeState(Phase phase, int offsetX16_16, int offsetY16_16,
                              boolean backgroundCollision, boolean screenShakeActive,
                              boolean bossSpawnRequested, boolean rebasePending) {
        public static NativeState initial() {
            return new NativeState(Phase.WAIT_P1_TRIGGER, 0, 0, false, true, false, false);
        }
        public NativeState withPhase(Phase value) {
            return new NativeState(value, offsetX16_16, offsetY16_16, backgroundCollision,
                    screenShakeActive, bossSpawnRequested, rebasePending);
        }
    }

    private int phaseOrdinal = Phase.WAIT_P1_TRIGGER.ordinal();
    private int offsetX16_16;
    private int offsetY16_16;
    private boolean backgroundCollision;
    private boolean screenShakeActive = true;
    private boolean bossSpawnRequested;
    private boolean rebasePending;
    private boolean initialized;
    private boolean bossSpawnAttempted;
    private int x = 0x31C0;
    private int y = 0x690;

    public FbzEndBossEventControlInstance() {
        this(new ObjectSpawn(0x31C0, 0x690, 0, 0, 0, false, 0));
    }

    public FbzEndBossEventControlInstance(ObjectSpawn spawn) {
        super(spawn, "FBZEndBossEventControl");
    }

    public static NativeState stepNative(NativeState state, int p1X, int p1Y,
                                          int cameraY, int cameraMinY, int foregroundStage) {
        Phase phase = state.phase();
        int ox = state.offsetX16_16();
        int oy = state.offsetY16_16();
        boolean collision = state.backgroundCollision();
        boolean shake = state.screenShakeActive();
        boolean spawn = state.bossSpawnRequested();
        boolean rebase = state.rebasePending();

        if (phase == Phase.WAIT_P1_TRIGGER) {
            if ((p1X & 0xFFFF) < 0x2E80) return state;
            phase = Phase.MOVING; // native pointer write falls through this call
        }
        if (phase == Phase.MOVING) {
            collision = true;
            oy += Y_STEP_16_16;
            ox += X_STEP_16_16;
            if ((oy >>> 16) >= Y_ENDPOINT) {
                oy = (Y_ENDPOINT << 16) | (oy & 0xFFFF);
                ox = (X_ENDPOINT << 16) | (ox & 0xFFFF);
                collision = false;
                phase = Phase.WAIT_CAMERA_Y_LOCK;
            }
        }
        // loc_53322 is the next instruction after the motion endpoint, not a
        // separately scheduled routine call. A satisfied camera word therefore
        // falls through on the endpoint frame. MOVING and an unsatisfied
        // loc_53322 both branch to the same loc_5333A tail; update() applies that
        // shared camera/arena tail exactly once after this state step.
        if (phase == Phase.WAIT_CAMERA_Y_LOCK && (short) cameraY == (short) cameraMinY) {
            phase = Phase.WAIT_ARENA_LOCK;
        }
        if (phase == Phase.WAIT_BOSS_STAGE && foregroundStage == 0x0C) {
            // loc_53372: allocation is attempted once, but the failure branch still
            // clears shake, publishes Events_bg+$06, and rebases the solid.
            spawn = true;
            rebase = true;
            shake = false;
            collision = false;
            phase = Phase.COMPLETE;
        }
        return new NativeState(phase, ox, oy, collision, shake, spawn, rebase);
    }

    public static NativeState normalizeBossLoadHighWords(NativeState state) {
        return new NativeState(state.phase(), state.offsetX16_16() & 0xFFFF,
                state.offsetY16_16() & 0xFFFF, false, state.screenShakeActive(),
                state.bossSpawnRequested(), state.rebasePending());
    }

    public void clearPublishedHighWordsAfterBossLoad() {
        apply(normalizeBossLoadHighWords(state()));
    }

    public static NativeState stepNativeWithExtraSidekicks(NativeState state, int p1X, int p1Y,
                                                            List<Integer> ignoredSidekickXs,
                                                            int cameraY, int cameraMinY,
                                                            int foregroundStage) {
        return stepNative(state, p1X, p1Y, cameraY, cameraMinY, foregroundStage);
    }

    public static boolean isCloudBossBackgroundStage(int stage) { return stage == 0x10; }
    public static int nativeCameraMinY(PlayerCharacter character) {
        return character == PlayerCharacter.TAILS_ALONE ? 0x40 : 0x3C;
    }
    public static Sonic3kFBZEvents.PlaneAssignmentMode planeModeForBackgroundStage(int stage) {
        return isCloudBossBackgroundStage(stage)
                ? Sonic3kFBZEvents.PlaneAssignmentMode.REVERSED
                : Sonic3kFBZEvents.PlaneAssignmentMode.NORMAL;
    }

    @Override
    public void update(int frameCounter, PlayableEntity mainPlayer) {
        if (!initialized) initializeNativeBounds();
        Phase before = phase();
        NativeState next = stepNative(state(), mainPlayer == null ? 0 : mainPlayer.getCentreX(),
                mainPlayer == null ? 0 : mainPlayer.getCentreY(), services().camera().getY(),
                services().camera().getMinY(), S3kFbzEventWriteSupport.getAct2ForegroundStage(services()));
        apply(next);

        if (before != Phase.WAIT_ARENA_LOCK && phase() == Phase.WAIT_ARENA_LOCK) {
            services().camera().setMaxY(services().camera().getMinY());
            services().camera().setMaxYTarget(services().camera().getMinY());
        }

        if (reachesArenaLockTail(phase())) {
            if (mainPlayer != null && (mainPlayer.getCentreY() & 0xFFFF) < 0x280) {
                services().camera().setMinX(services().camera().getX());
            }
            if (services().camera().getMinY() == services().camera().getMaxY()
                    && services().camera().getMinX() == services().camera().getMaxX()) {
                S3kFbzEventWriteSupport.setAct2ForegroundStage(services(), 8);
                phaseOrdinal = Phase.WAIT_BOSS_STAGE.ordinal();
            }
        }
        if (bossSpawnRequested && !bossSpawnAttempted) {
            bossSpawnAttempted = true;
            attemptBossAllocation();
            S3kFbzEventWriteSupport.setScreenShakeActive(services(), false);
            S3kFbzEventWriteSupport.setBossLoadPositionAdjustmentPending(services(), true);
        }

        int wordX = offsetX16_16 >>> 16;
        int wordY = offsetY16_16 >>> 16;
        x = phase() == Phase.COMPLETE ? 0x31C0 : (0x31C0 + wordX) & 0xFFFF;
        y = phase() == Phase.COMPLETE ? 0x690 : (0x690 - wordY) & 0xFFFF;
        updateDynamicSpawn(x, y);
        S3kFbzEventWriteSupport.setBossApproachMotionState(services(), wordX, wordY, backgroundCollision);
    }

    private static boolean reachesArenaLockTail(Phase phase) {
        return phase == Phase.MOVING
                || phase == Phase.WAIT_CAMERA_Y_LOCK
                || phase == Phase.WAIT_ARENA_LOCK;
    }

    private void initializeNativeBounds() {
        initialized = true;
        services().camera().setMaxX((short) 0x32B8);
        PlayerCharacter character = S3kRuntimeStates.resolvePlayerCharacter(
                services().zoneRuntimeRegistry(), services().configuration());
        services().camera().setMinY((short) nativeCameraMinY(character));
        screenShakeActive = S3kFbzEventWriteSupport.isScreenShakeActive(services());
    }

    private void attemptBossAllocation() {
        ObjectSpawn bossSpawn = new ObjectSpawn(0x31C0, 0x690,
                Sonic3kObjectIds.FBZ_END_BOSS, 0, 0, false, 0);
        services().objectManager().createDynamicObject(
                () -> services().gameModule().createObjectRegistry().create(bossSpawn));
    }

    private NativeState state() {
        return new NativeState(phase(), offsetX16_16, offsetY16_16, backgroundCollision,
                screenShakeActive, bossSpawnRequested, rebasePending);
    }

    private void apply(NativeState state) {
        phaseOrdinal = state.phase().ordinal();
        offsetX16_16 = state.offsetX16_16();
        offsetY16_16 = state.offsetY16_16();
        backgroundCollision = state.backgroundCollision();
        screenShakeActive = state.screenShakeActive();
        bossSpawnRequested = state.bossSpawnRequested();
        rebasePending = state.rebasePending();
    }

    private Phase phase() { return Phase.values()[phaseOrdinal]; }
    // Obj_FBZEndBossEventControl reaches SolidObjectTop on every native path,
    // including COMPLETE; it never calls out_of_range, MarkObjGone, or DeleteObject.
    @Override public boolean isPersistent() { return true; }
    @Override public SolidObjectParams getSolidParams() { return SOLID_PARAMS; }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public boolean usesInstanceSolidStateLatchKey() { return true; }
    @Override public boolean seedsNewRideCarryFromPreUpdateX() { return true; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
