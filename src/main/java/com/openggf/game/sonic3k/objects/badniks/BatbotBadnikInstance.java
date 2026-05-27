package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * S3K Obj $A5 - Batbot.
 *
 * <p>ROM reference: {@code Obj_Batbot} in {@code docs/skdisasm/sonic3k.asm}
 * around {@code loc_89394}. The parent is the only collidable part; the two
 * child sprites created by {@code ChildObjDat_8946C} have collision flags 0
 * and are therefore visual-only for gameplay and headless trace replay.
 */
public final class BatbotBadnikInstance extends AbstractS3kBadnikInstance {

    private static final int COLLISION_SIZE = 0x0D;
    private static final int PRIORITY_BUCKET = 5;
    private static final int ACTIVATION_RANGE = 0x40;
    private static final int WAIT_OFFSCREEN_MARGIN = 0x20;
    private static final int CHASE_MAX_SPEED = 0x200;
    private static final int CHASE_ACCELERATION = 8;
    private static final int INITIAL_ACTIVE_X_SPEED = 0x200;
    private static final int INITIAL_MAPPING_FRAME = 2;
    private static final int BODY_CHILD_FRAME = 3;
    private static final int BODY_CHILD_ACTIVE_FRAME = 4;
    private static final int BODY_CHILD_Y_OFFSET = 0x10;
    private static final int LAMP_CHILD_FRAME = 5;
    private static final int LAMP_CHILD_Y_OFFSET = 0x03;
    private static final int PARENT_ANIM_DELAY = 2;
    private static final int[] PARENT_ANIM_FRAMES = {0, 1, 2, 1};
    private static final int[] BODY_ANIM_FRAMES = {3, 4, 3, 4, 3, 4};
    private static final int[] BODY_ANIM_DELAYS = {0x1D, 2, 1, 2, 0x0E, 2};

    private enum State { INIT, WAIT, CHASE }

    private State state = State.INIT;
    private boolean waitingForOnscreen = true;
    private boolean deleteCurrentSpriteMarker;
    private int parentAnimIndex = -1;
    private int parentAnimTimer;
    private int bodyFrame = BODY_CHILD_FRAME;
    private int bodyAnimIndex = -1;
    private int bodyAnimTimer;

    public BatbotBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Batbot", Sonic3kObjectArtKeys.CNZ_BATBOT,
                COLLISION_SIZE, PRIORITY_BUCKET);
    }

    @Override
    protected void updateMovement(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            return;
        }
        if (deleteCurrentSpriteMarker) {
            setDestroyedByOffscreen();
            return;
        }

        if (waitingForOnscreen) {
            // Obj_WaitOffscreen installs Map_Offscreen with width/height $20
            // and restores Obj_Batbot only after the temporary sprite has been
            // drawn onscreen; the restored object op runs next frame
            // (sonic3k.asm:180266-180297, 186266-186272).
            if (!isOnScreen(WAIT_OFFSCREEN_MARGIN)) {
                updateDynamicSpawn(currentX, currentY);
                return;
            }
            waitingForOnscreen = false;
            updateDynamicSpawn(currentX, currentY);
            return;
        }

        switch (state) {
            case INIT -> initialize();
            case WAIT -> updateWait(playerEntity);
            case CHASE -> updateChase(playerEntity);
        }

        updateDynamicSpawn(currentX, currentY);
        if (isDeleteSpriteIfNotInRange()) {
            // ROM Sprite_CheckDeleteTouch branches through loc_85088/Go_Delete_Sprite:
            // set status bit 7 and install Delete_Current_Sprite, leaving the SST
            // slot occupied until the next ExecuteObjects pass (sonic3k.asm:
            // 179058-179134). CNZ2 slot-pressure guards depend on this marker
            // remaining occupied through the next low-slot allocation window.
            deleteCurrentSpriteMarker = true;
        }
    }

    @Override
    public boolean usesCustomOutOfRangeCheck() {
        return true;
    }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return false;
    }

    private void initialize() {
        mappingFrame = INITIAL_MAPPING_FRAME;
        bodyFrame = BODY_CHILD_FRAME;
        state = State.WAIT;
    }

    private void updateWait(PlayableEntity playerEntity) {
        if (!isPlayerWithinActivationRange(playerEntity)) {
            return;
        }
        state = State.CHASE;
        xVelocity = INITIAL_ACTIVE_X_SPEED;
        bodyFrame = BODY_CHILD_ACTIVE_FRAME;
    }

    private boolean isPlayerWithinActivationRange(PlayableEntity playerEntity) {
        if (playerEntity == null || playerEntity.getDead()) {
            return false;
        }
        int dx = Math.abs(currentX - playerEntity.getCentreX());
        return dx < ACTIVATION_RANGE;
    }

    private void updateChase(PlayableEntity playerEntity) {
        if (playerEntity != null && !playerEntity.getDead()) {
            chase(playerEntity.getCentreX(), playerEntity.getCentreY());
        }
        moveWithVelocity();
        updateParentAnimation();
        updateBodyAnimation();
    }

    private void updateParentAnimation() {
        parentAnimTimer--;
        if (parentAnimTimer >= 0) {
            return;
        }
        parentAnimIndex++;
        if (parentAnimIndex >= PARENT_ANIM_FRAMES.length) {
            parentAnimIndex = 0;
        }
        mappingFrame = PARENT_ANIM_FRAMES[parentAnimIndex];
        parentAnimTimer = PARENT_ANIM_DELAY;
    }

    private void updateBodyAnimation() {
        bodyAnimTimer--;
        if (bodyAnimTimer >= 0) {
            return;
        }
        bodyAnimIndex++;
        if (bodyAnimIndex >= BODY_ANIM_FRAMES.length) {
            bodyAnimIndex = 0;
        }
        bodyFrame = BODY_ANIM_FRAMES[bodyAnimIndex];
        bodyAnimTimer = BODY_ANIM_DELAYS[bodyAnimIndex];
    }

    /**
     * Port of shared ROM helper {@code Chase_Object}: accelerate toward the
     * target on each axis only when the new velocity remains inside +/- max.
     */
    private void chase(int targetX, int targetY) {
        boolean xEqual = currentX == targetX;
        if (!xEqual) {
            int xAccel = currentX > targetX ? -CHASE_ACCELERATION : CHASE_ACCELERATION;
            int nextXVelocity = xVelocity + xAccel;
            if (nextXVelocity >= -CHASE_MAX_SPEED && nextXVelocity <= CHASE_MAX_SPEED) {
                xVelocity = nextXVelocity;
            }
        }

        boolean yEqual = currentY == targetY;
        if (yEqual) {
            if (xEqual) {
                xVelocity = 0;
                yVelocity = 0;
            }
            return;
        }

        int yAccel = currentY > targetY ? -CHASE_ACCELERATION : CHASE_ACCELERATION;
        int nextYVelocity = yVelocity + yAccel;
        if (nextYVelocity >= -CHASE_MAX_SPEED && nextYVelocity <= CHASE_MAX_SPEED) {
            yVelocity = nextYVelocity;
        }
    }

    @Override
    public int getCollisionFlags() {
        return deleteCurrentSpriteMarker || waitingForOnscreen || state == State.INIT ? 0 : super.getCollisionFlags();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.CNZ_BATBOT);
        if (renderer == null) {
            return;
        }
        boolean hFlip = !facingLeft;
        int x = getRenderAnchorX();
        int y = getRenderAnchorY();
        renderer.drawFrameIndex(mappingFrame, x, y, hFlip, false);
        if (state != State.INIT) {
            renderer.drawFrameIndex(bodyFrame, x, y + BODY_CHILD_Y_OFFSET, hFlip, false);
            renderer.drawFrameIndex(LAMP_CHILD_FRAME, x, y + LAMP_CHILD_Y_OFFSET, hFlip, false);
        }
    }

    private boolean isDeleteSpriteIfNotInRange() {
        ObjectServices svc = tryServices();
        if (svc == null || svc.camera() == null) {
            return false;
        }
        int objectCoarse = currentX & 0xFF80;
        int cameraCoarseBack = (svc.camera().getX() - 0x80) & 0xFF80;
        return ((objectCoarse - cameraCoarseBack) & 0xFFFF) > 0x280;
    }

    @Override
    public String traceDebugDetails() {
        return String.format("state=%s waitOn=%s delMark=%s vx=%04X vy=%04X spawn=%04X,%04X",
                state, waitingForOnscreen, deleteCurrentSpriteMarker, xVelocity & 0xFFFF, yVelocity & 0xFFFF,
                spawn.x() & 0xFFFF, spawn.y() & 0xFFFF);
    }
}
