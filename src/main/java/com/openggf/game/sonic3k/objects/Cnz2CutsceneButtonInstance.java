package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.events.S3kCnzEventWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Cutscene button for the first CNZ Act 2 rival-Knuckles encounter.
 *
 * <p>ROM reference: {@code Obj_CutsceneButton} subtype 2, which presses when
 * the cutscene Knuckles object reaches {@code word_65C48}'s proximity box and
 * runs {@code loc_65C78}: shakes the screen, drops the CNZ2 water target, arms
 * the follow-up water button, and starts the CNZ palette flash.
 */
public final class Cnz2CutsceneButtonInstance extends AbstractObjectInstance {
    private static final int INIT_Y_OFFSET = 4;
    private static final int PRIORITY = 4;
    private static final int RANGE_LEFT = -0x18;
    private static final int RANGE_RIGHT = 0x30;
    private static final int RANGE_TOP = -0x18;
    private static final int RANGE_BOTTOM = 0x30;
    private static final int CNZ2_CUTSCENE_WATER_TARGET_Y = 0x0350;

    private final int x;
    private final int y;
    private boolean pressed;

    public Cnz2CutsceneButtonInstance(ObjectSpawn spawn) {
        super(spawn, "CutsceneButtonCNZ2");
        this.x = spawn.x();
        this.y = spawn.y() + INIT_Y_OFFSET;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
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
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (pressed) {
            return;
        }
        CutsceneKnucklesCnz2AInstance knuckles = CutsceneKnucklesCnz2AInstance.getActiveInstance();
        if (knuckles == null) {
            return;
        }

        int dx = knuckles.getX() - x;
        int dy = knuckles.getY() - y;
        if (dx >= RANGE_LEFT && dx < RANGE_RIGHT && dy >= RANGE_TOP && dy < RANGE_BOTTOM) {
            pressed = true;
            S3kCnzEventWriteSupport.setWaterButtonArmed(services(), true);
            S3kCnzEventWriteSupport.setWaterTargetY(services(), CNZ2_CUTSCENE_WATER_TARGET_Y);
            services().playSfx(Sonic3kSfx.GEYSER.id);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.BUTTON);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(pressed ? 1 : 0, x, y, false, false);
    }
}
