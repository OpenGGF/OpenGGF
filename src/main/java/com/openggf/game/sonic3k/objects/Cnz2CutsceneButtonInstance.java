package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.events.S3kCnzEventWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Cutscene buttons for the CNZ Act 2 rival-Knuckles encounters.
 *
 * <p>ROM reference: {@code Obj_CutsceneButton} subtypes 4 and 6. Subtype 4
 * runs {@code loc_65C78}: shakes the screen, drops the CNZ2 water target,
 * arms the follow-up water button, and starts the CNZ palette flash. Subtype
 * 6 runs {@code loc_65CAC}: shakes the screen and spawns the vacuum-tube
 * controllers used by the second encounter.
 */
public final class Cnz2CutsceneButtonInstance extends AbstractObjectInstance {
    private static final int INIT_Y_OFFSET = 4;
    private static final int PRIORITY = 4;
    private static final int RANGE_LEFT = -0x18;
    private static final int RANGE_RIGHT = 0x30;
    private static final int RANGE_TOP = -0x18;
    private static final int RANGE_BOTTOM = 0x30;
    private static final int CNZ2_CUTSCENE_WATER_TARGET_Y = 0x0350;
    /** ROM: {@code Mean_water_level = Camera_Y_pos + $100} in loc_65C78. */
    private static final int CNZ2_CUTSCENE_WATER_MEAN_OFFSET = 0x0100;
    /** ROM: {@code move.w #$14,(Screen_shake_flag).w} in loc_65C78. */
    private static final int CNZ2_SCREEN_SHAKE_FRAMES = 0x14;
    private static final int WATER_FLASH_SUBTYPE = 4;
    private static final int VACUUM_TUBE_SUBTYPE = 6;

    private int x;
    private int y;
    private int subtype;
    private boolean pressed;
    private CnzLightsFlashChildInstance spawnedFlash;

    public Cnz2CutsceneButtonInstance(ObjectSpawn spawn) {
        super(spawn, "CutsceneButtonCNZ2");
        this.x = spawn.x();
        this.y = spawn.y() + INIT_Y_OFFSET;
        this.subtype = spawn.subtype();
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

        AbstractObjectInstance knuckles = activeKnucklesForSubtype();
        if (knuckles == null) {
            return;
        }
        if (!canPressForCurrentCutsceneStep(knuckles)) {
            return;
        }

        int dx = knuckles.getX() - x;
        int dy = knuckles.getY() - y;
        if (dx >= RANGE_LEFT && dx < RANGE_RIGHT && dy >= RANGE_TOP && dy < RANGE_BOTTOM) {
            press();
        }
    }

    private AbstractObjectInstance activeKnucklesForSubtype() {
        return switch (subtype) {
            case WATER_FLASH_SUBTYPE -> CutsceneKnucklesCnz2AInstance.getActiveInstance();
            case VACUUM_TUBE_SUBTYPE -> CutsceneKnucklesCnz2BInstance.getActiveInstance();
            default -> null;
        };
    }

    private boolean canPressForCurrentCutsceneStep(AbstractObjectInstance knuckles) {
        return subtype != WATER_FLASH_SUBTYPE
                || (knuckles instanceof CutsceneKnucklesCnz2AInstance cnz2a
                && cnz2a.hasReachedButtonImpact());
    }

    /**
     * ROM: {@code loc_65C78}. Sets the rising water target, arms the follow-up
     * water-level button ({@code _unkFAA3}), plays {@code sfx_Geyser}, and spawns
     * the lights-off palette flash child ({@code loc_62480}) with subtype 0 — so
     * the dark variant stays in place and the lights remain off until the player
     * presses the water-level button.
     *
     */
    private void press() {
        pressed = true;
        if (subtype == VACUUM_TUBE_SUBTYPE) {
            pressVacuumTubeButton();
            return;
        }
        // ROM loc_65C78: move.w #$14,(Screen_shake_flag).w
        S3kCnzEventWriteSupport.triggerScreenShake(services(), CNZ2_SCREEN_SHAKE_FRAMES);
        // ROM: Mean_water_level = Camera_Y_pos + $100 — seed the surface so the
        // flood is already risen, then ease the target up to $350.
        int meanY = (services().camera().getY() & 0xFFFF) + CNZ2_CUTSCENE_WATER_MEAN_OFFSET;
        S3kCnzEventWriteSupport.setWaterMeanLevel(services(), meanY);
        S3kCnzEventWriteSupport.setWaterButtonArmed(services(), true);
        S3kCnzEventWriteSupport.setWaterTargetY(services(), CNZ2_CUTSCENE_WATER_TARGET_Y);
        services().playSfx(Sonic3kSfx.GEYSER.id);
        // ROM spawns the flash child with subtype 0 (no restore -> lights stay off).
        spawnedFlash = spawnChild(() -> new CnzLightsFlashChildInstance(buildSpawnAt(x, y), false));
    }

    /**
     * ROM: {@code loc_65CAC}. The second CNZ2 cutscene button shakes the
     * screen, mutates the nearby tube blocks, and allocates the two vacuum-tube
     * controller objects that catch Sonic/Tails after Knuckles exits.
     */
    private void pressVacuumTubeButton() {
        S3kCnzEventWriteSupport.triggerScreenShake(services(), CNZ2_SCREEN_SHAKE_FRAMES);
        spawnChild(() -> new CnzVacuumTubeInstance(new ObjectSpawn(
                0x4740, 0x0828, Sonic3kObjectIds.CNZ_VACUUM_TUBE, 0x4C, 0, false, 0)));
        spawnChild(() -> new CnzVacuumTubeInstance(new ObjectSpawn(
                0x4740, 0x0A28, Sonic3kObjectIds.CNZ_VACUUM_TUBE, 0x20, 0, false, 0)));
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
