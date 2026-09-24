package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.runtime.DezFinalCamera;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** DEZ3 Obj_5A7C8 moving support, Obj_5A8E6 entry support and Obj_5A872 falling blocks. */
public final class DezFinalArenaFloor extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {
    public static final int MOVING = 0, ENTRY = 1, FALLING = 2;
    private final SubpixelMotion.State motion = new SubpixelMotion.State(0, 0, 0, 0, 0, 0);
    private int mode;
    private int previousCameraX = 0x80;
    private int frame;
    private boolean initialized;
    private boolean renderOnScreen = true;

    public DezFinalArenaFloor(ObjectSpawn spawn) {
        super(spawn, "DEZFinalArenaFloor");
        mode = spawn.subtype(); motion.x = spawn.x(); motion.y = spawn.y();
    }
    public static DezFinalArenaFloor moving() {
        return new DezFinalArenaFloor(new ObjectSpawn(0, 0, 0, MOVING, 0, false, 0));
    }
    public static DezFinalArenaFloor entry() {
        return new DezFinalArenaFloor(new ObjectSpawn(0, 0, 0, ENTRY, 0, false, 0));
    }
    private DezFinalBossZoneRuntimeState state() {
        return (DezFinalBossZoneRuntimeState) services().zoneRuntimeState();
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialized) {
            initialized = true; motion.y = 0xF0;
            if (mode == MOVING) motion.x = 0x130;
            else if (mode == ENTRY) motion.x = 0x40;
            else frame = (motion.x & 0x60) >>> 5;
        }
        if (mode == FALLING) {
            // loc_5A8B2 tests the previous render sign before MoveSprite2 and +$1A gravity.
            if (!renderOnScreen) motion.x = (DezFinalCamera.nativeX(services().camera()) & 0xFF80) - 0x80 + 0x400;
            SubpixelMotion.moveSprite2(motion); motion.yVel = (short) (motion.yVel + 0x1A);
            solid(); coarseXCullViewport(getX()); return;
        }
        if (services().currentZone() != 0x17 || mode == ENTRY && state().windowBase() != 0x6C0) {
            ObjectLifetimeOps.deleteNoRespawn(this); return;
        }
        if (mode == MOVING) {
            int request = state().breakRequest();
            if (request != 0) {
                state().breakRequest(0);
                if (request >= state().breakFrontier()) {
                    var child = spawnChild(() -> new DezFinalArenaFloor(
                            new ObjectSpawn(request, 0, 0, FALLING, 0, false, 0)));
                    if (child != null && !child.isDestroyed() && ((previousCameraX - 0x20) & 0xFFFF) <= request)
                        state().redrawRequest(request);
                    state().breakFrontier(state().breakFrontier() + 0x20);
                    if (request >= previousCameraX) motion.x = (motion.x + 0x20) & 0xFFFF;
                }
            }
            int camera = DezFinalCamera.nativeCopyX(services().camera()) & 0xFFE0;
            if (camera != previousCameraX) {
                if (camera < previousCameraX) motion.x = (motion.x - 0x20) & 0xFFFF;
                else if (previousCameraX >= state().breakFrontier()) motion.x = (motion.x + 0x20) & 0xFFFF;
                previousCameraX = camera;
            }
        }
        solid();
    }
    private void solid() {
        var execution = services().solidExecution();
        if (execution != null && !execution.isInert()) execution.resolveSolidNowAll();
    }
    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }
    @Override public SolidObjectParams getSolidParams() {
        return SolidObjectParams.of(mode == MOVING ? 0xB0 : mode == ENTRY ? 0x40 : 0x10, 0x10, 0x10);
    }
    // loc_5A860 / loc_5A8C4 load d4 from the current x_pos AFTER moving
    // the collision window/block. SolidObjectTop -> MvSonicOnPtfm therefore
    // carries by zero X, even when the camera advances/retracts a $20 column.
    // The default platform carry would drag a grounded player with this
    // invisible support, making camera deadzone changes alter player movement.
    @Override public boolean carriesRiderOnHorizontalMove(PlayableEntity player) { return false; }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public boolean usesCollisionHalfWidthForTopLanding() { return true; }
    @Override public boolean isSkipSolidContactThisFrame() { return false; }
    @Override public boolean providesPreMovementGroundAttachmentSupport() { return mode != FALLING; }
    @Override public boolean isPersistent() { return true; }
    @Override public int getX() { return motion.x & 0xFFFF; }
    @Override public int getY() { return motion.y & 0xFFFF; }
    @Override public int getOnScreenHalfWidth() { return 0xC; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }
    @Override public int getPriorityBucket() { return 1; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int romObjectCodePointerHighWord() { return 5; }
    @Override public void refreshPostCameraRenderState() {
        if (mode == FALLING) renderOnScreen = isWithinRenderSpriteBounds(0xC, 0x10);
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (mode != FALLING || !initialized || isDestroyed()) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_FINAL_ARENA_BLOCK);
        if (renderer != null && renderer.isReady())
            renderer.drawFrameIndexForcedPriority(frame, getX(), getY(), false, false, 2, true);
    }
    int modeForTest() { return mode; }
    int velocityForTest() { return motion.yVel; }
}
