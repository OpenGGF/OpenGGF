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
    /** ROM: {@code move.w #$14,(Screen_shake_flag).w} in loc_65C78. */
    private static final int CNZ2_SCREEN_SHAKE_FRAMES = 0x14;

    private final int x;
    private final int y;
    private boolean pressed;
    private CnzLightsFlashChildInstance spawnedFlash;

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
            press();
        }
    }

    /**
     * ROM: {@code loc_65C78}. Sets the rising water target, arms the follow-up
     * water-level button ({@code _unkFAA3}), plays {@code sfx_Geyser}, and spawns
     * the lights-off palette flash child ({@code loc_62480}) with subtype 0 — so
     * the dark variant stays in place and the lights remain off until the player
     * presses the water-level button.
     *
     * <p>ROM also writes {@code Mean_water_level=Camera_Y+$100}; that mean-level
     * seed is handled by the water system's ease toward the target.
     */
    private void press() {
        pressed = true;
        // ROM loc_65C78: move.w #$14,(Screen_shake_flag).w
        S3kCnzEventWriteSupport.triggerScreenShake(services(), CNZ2_SCREEN_SHAKE_FRAMES);
        S3kCnzEventWriteSupport.setWaterButtonArmed(services(), true);
        S3kCnzEventWriteSupport.setWaterTargetY(services(), CNZ2_CUTSCENE_WATER_TARGET_Y);
        services().playSfx(Sonic3kSfx.GEYSER.id);
        // ROM spawns the flash child with subtype 0 (no restore -> lights stay off).
        spawnedFlash = spawnChild(() -> new CnzLightsFlashChildInstance(buildSpawnAt(x, y), false));
    }

    /** Test seam: the lights-off flash child spawned on press, or null. */
    CnzLightsFlashChildInstance getSpawnedFlashForTest() {
        return spawnedFlash;
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
