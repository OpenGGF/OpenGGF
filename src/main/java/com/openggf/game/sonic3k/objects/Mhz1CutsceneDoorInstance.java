package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * MHZ1 Knuckles switch-door child created by {@code Obj_MHZ1CutsceneButton}.
 *
 * <p>ROM reference: {@code ChildObjDat_665B6 -> loc_6300C}. The child starts
 * at the fixed door coordinates used by {@code Map_MHZKnuxDoor}; movement is
 * driven by the parent button's switch bits in the following routines.
 */
public final class Mhz1CutsceneDoorInstance extends AbstractObjectInstance implements SolidObjectProvider {
    private static final int INITIAL_X = 0x0390;
    private static final int INITIAL_Y = 0x0620;
    private static final int PRIORITY = 1;
    private static final int SLIDE_SPEED = 0x0100;
    private static final int SLIDE_WAIT = 0x3F;
    private static final int AUTO_RAISE_MAX_X_DISTANCE = 0x40;
    private static final int AUTO_RAISE_MIN_Y_DISTANCE = 0x60;
    private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(0x1B, 0x20, 0x20);

    private final Mhz1CutsceneButtonInstance parent;
    private final SubpixelMotion.State motion = new SubpixelMotion.State(INITIAL_X, INITIAL_Y, 0, 0, 0, 0);
    private State state = State.IDLE;
    private int waitTimer;

    public Mhz1CutsceneDoorInstance(Mhz1CutsceneButtonInstance parent) {
        super(new ObjectSpawn(INITIAL_X, INITIAL_Y, parent.getSpawn().objectId(), 0, 0, false, 0),
                "MHZ1CutsceneDoor");
        this.parent = parent;
    }

    @Override
    public int getX() {
        return motion.x;
    }

    @Override
    public int getY() {
        return motion.y;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public boolean isHighPriority() {
        return false;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (state == State.IDLE) {
            if (parent.isDoorSwitchActive()) {
                parent.clearDoorSwitchActive();
                startSlide();
                return;
            }
            if (shouldAutoRaiseFromPlayerPosition(playerEntity)) {
                parent.setDoorLowered(false);
                startSlide();
            }
            return;
        }

        SubpixelMotion.moveSprite2(motion);
        if (--waitTimer < 0) {
            state = State.IDLE;
            motion.yVel = 0;
            parent.setDoorMoving(false);
        }
    }

    private boolean shouldAutoRaiseFromPlayerPosition(PlayableEntity playerEntity) {
        if (!parent.isDoorLowered() || parent.isCutsceneDoorLatched() || playerEntity == null) {
            return false;
        }
        int playerX = playerEntity.getCentreX() & 0xFFFF;
        int playerY = playerEntity.getCentreY() & 0xFFFF;
        if (Integer.compareUnsigned(playerX, motion.x) > 0) {
            return false;
        }
        int xDistance = Math.abs(motion.x - playerX);
        int yDistance = Math.abs(motion.y - playerY);
        return xDistance < AUTO_RAISE_MAX_X_DISTANCE
                && yDistance >= AUTO_RAISE_MIN_Y_DISTANCE;
    }

    private void startSlide() {
        state = State.MOVING;
        parent.setDoorMoving(true);
        motion.yVel = parent.isDoorLowered() ? SLIDE_SPEED : -SLIDE_SPEED;
        waitTimer = SLIDE_WAIT;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.MHZ1_CUTSCENE_KNUCKLES_DOOR);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(0, motion.x, motion.y, false, false);
    }

    private enum State {
        IDLE,
        MOVING
    }
}
