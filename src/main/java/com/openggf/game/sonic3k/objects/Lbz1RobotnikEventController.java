package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Minimal LBZ1 Robotnik event controller.
 *
 * <p>ROM: {@code Obj_LBZ1Robotnik} routine {@code loc_8CC5A} arms the
 * {@code LBZ1_EventVScroll} building collapse once Sonic reaches the miniboss
 * approach on the ground. Full Robotnik/miniboss movement and child visuals are
 * separate boss work; this controller preserves the route-critical event
 * ownership so CutsceneKnux_LBZ1 does not start the foreground collapse itself.
 */
public final class Lbz1RobotnikEventController extends AbstractObjectInstance {
    private static final int ROUTINE_WAIT_FOR_COLLAPSE_TRIGGER = 0x06;
    private static final int ROUTINE_WAIT_FOR_COLLAPSE_CLEAR = 0x08;
    private static final int ROUTINE_AFTER_COLLAPSE = 0x0A;
    private static final int TRIGGER_CAMERA_X = 0x3B40;
    private static final int TRIGGER_PLAYER_Y = 0x01C0;
    private static final int POST_COLLAPSE_CAMERA_MAX_X = 0x3EA0;
    private static final int POST_COLLAPSE_MIN_FOLLOW_LIMIT_X = 0x3E50;
    private static final int INC_LEVEL_END_X_STEP = 0x4000;

    private int routine = ROUTINE_WAIT_FOR_COLLAPSE_TRIGGER;
    private int postCollapseMaxXAccumulator;
    private boolean postCollapseMaxXActive;

    public Lbz1RobotnikEventController(ObjectSpawn spawn) {
        super(spawn, "LBZ1Robotnik");
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (isPlayerKnuckles()) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        if (!(playerEntity instanceof AbstractPlayableSprite player)) {
            return;
        }
        if (routine == ROUTINE_WAIT_FOR_COLLAPSE_TRIGGER) {
            updateWaitForCollapseTrigger(player);
        } else if (routine == ROUTINE_WAIT_FOR_COLLAPSE_CLEAR && collapseEventFinished()) {
            unlockPostCollapseCamera();
            routine = ROUTINE_AFTER_COLLAPSE;
        } else if (routine == ROUTINE_AFTER_COLLAPSE) {
            updatePostCollapseCameraMin();
            updatePostCollapseCameraMax();
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Full Obj_LBZ1Robotnik rendering belongs with the LBZ miniboss port.
    }

    public int getRoutineForTest() {
        return routine;
    }

    private void updateWaitForCollapseTrigger(AbstractPlayableSprite player) {
        if ((services().camera().getX() & 0xFFFF) < TRIGGER_CAMERA_X) {
            return;
        }
        if ((player.getCentreY() & 0xFFFF) < TRIGGER_PLAYER_Y) {
            return;
        }
        if (player.getAir()) {
            return;
        }
        if (services().levelEventProvider() instanceof Sonic3kLevelEventManager manager
                && manager.getLbzEvents() != null) {
            manager.getLbzEvents().startEndingCollapse();
            routine = ROUTINE_WAIT_FOR_COLLAPSE_CLEAR;
        }
    }

    private boolean collapseEventFinished() {
        return services().levelEventProvider() instanceof Sonic3kLevelEventManager manager
                && manager.getLbzEvents() != null
                && manager.getLbzEvents().isEndingCollapseFinished();
    }

    private void unlockPostCollapseCamera() {
        postCollapseMaxXAccumulator = 0;
        postCollapseMaxXActive = true;
    }

    private void updatePostCollapseCameraMin() {
        int cameraX = services().camera().getX() & 0xFFFF;
        if (cameraX < POST_COLLAPSE_MIN_FOLLOW_LIMIT_X) {
            services().camera().setMinX((short) cameraX);
        }
    }

    private void updatePostCollapseCameraMax() {
        if (!postCollapseMaxXActive) {
            return;
        }
        int currentMax = services().camera().getMaxX() & 0xFFFF;
        if (currentMax >= POST_COLLAPSE_CAMERA_MAX_X) {
            services().camera().setMaxX((short) POST_COLLAPSE_CAMERA_MAX_X);
            postCollapseMaxXActive = false;
            return;
        }

        postCollapseMaxXAccumulator += INC_LEVEL_END_X_STEP;
        int delta = postCollapseMaxXAccumulator >>> 16;
        int nextMax = currentMax + delta;
        if (nextMax >= POST_COLLAPSE_CAMERA_MAX_X) {
            services().camera().setMaxX((short) POST_COLLAPSE_CAMERA_MAX_X);
            postCollapseMaxXActive = false;
        } else {
            services().camera().setMaxX((short) nextMax);
        }
    }

    private boolean isPlayerKnuckles() {
        return S3kRuntimeStates.resolvePlayerCharacter(
                services().zoneRuntimeRegistry(),
                services().configuration()) == PlayerCharacter.KNUCKLES;
    }
}
