package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectVisualFacing;

import com.openggf.game.PlayableEntity;
import com.openggf.game.mutation.LayoutMutationContext;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.events.S3kCnzEventWriteSupport;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;


/**
 * Cutscene buttons for the CNZ Act 2 rival-Knuckles encounters.
 *
 * <p>ROM reference: {@code Obj_CutsceneButton} subtypes 4 and 6. Subtype 4
 * runs {@code loc_65C78}: shakes the screen, drops the CNZ2 water target,
 * arms the follow-up water button, and starts the CNZ palette flash. Subtype
 * 6 runs {@code loc_65CAC}: shakes the screen and spawns the vacuum-tube
 * controllers used by the second encounter.
 */
public final class Cnz2CutsceneButtonInstance extends AbstractS3kCutsceneButtonInstance implements SpawnRewindRecreatable, ObjectVisualFacing.NonDirectional {
    private static final int CNZ2_CUTSCENE_WATER_TARGET_Y = 0x0350;
    /** ROM: {@code Mean_water_level = Camera_Y_pos + $100} in loc_65C78. */
    private static final int CNZ2_CUTSCENE_WATER_MEAN_OFFSET = 0x0100;
    /** ROM: {@code move.w #$14,(Screen_shake_flag).w} in loc_65C78. */
    private static final int CNZ2_SCREEN_SHAKE_FRAMES = 0x14;
    private static final int WATER_FLASH_SUBTYPE = 4;
    private static final int VACUUM_TUBE_SUBTYPE = 6;
    private static final int VACUUM_LAYOUT_LAYER = 0;
    private static final int VACUUM_LAYOUT_X = 0x8E;
    private static final int VACUUM_LAYOUT_START_Y = 14;
    private static final int[] VACUUM_LAYOUT_VALUES = {0x14, 0x0F, 0x0F, 0x88};

    private int subtype;
    private CnzLightsFlashChildInstance spawnedFlash;

    public Cnz2CutsceneButtonInstance(ObjectSpawn spawn) {
        super(spawn, "CutsceneButtonCNZ2");
        this.subtype = spawn.subtype();
    }

    @Override
    public boolean isPersistent() {
        return !pressed;
    }

    @Override
    public boolean shouldStayActiveWhenRemembered() {
        return pressed;
    }

    @Override
    public boolean clearsRespawnStateOnCounterBasedOutOfRange() {
        return false;
    }

    @Override
    public void onUnload() {
        if (pressed) {
            ObjectLifetimeOps.removeSpawnFromActive(services().objectManager(), getSpawn());
        }
    }

    @Override protected TargetPosition targetPosition() {
        // Native pointer publication is not yet shared by the CNZ actor owners.
        var knuckles = activeKnucklesForSubtype();
        return knuckles == null ? null : new TargetPosition(knuckles.getX(), knuckles.getY());
    }

    private AbstractObjectInstance activeKnucklesForSubtype() {
        return switch (subtype) {
            case WATER_FLASH_SUBTYPE ->
                    CutsceneKnucklesCnz2AInstance.activeInstance(services().objectManager());
            case VACUUM_TUBE_SUBTYPE ->
                    CutsceneKnucklesCnz2BInstance.activeInstance(services().objectManager());
            default -> null;
        };
    }

    /**
     * ROM: {@code loc_65C78}. Sets the rising water target, arms the follow-up
     * water-level button ({@code _unkFAA3}), plays {@code sfx_Geyser}, and spawns
     * the lights-off palette flash child ({@code loc_62480}) with subtype 0 — so
     * the dark variant stays in place and the lights remain off until the player
     * presses the water-level button.
     *
     */
    @Override protected void onPressed(PlayableEntity player) {
        ObjectLifetimeOps.markSpawnRemembered(services().objectManager(), getSpawn());
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
        spawnedFlash = spawnChild(() -> new CnzLightsFlashChildInstance(buildSpawnAt(x, y), false, this));
    }

    /**
     * ROM: {@code loc_65CAC}. The second CNZ2 cutscene button shakes the
     * screen, mutates the nearby tube blocks, and allocates the two vacuum-tube
     * controller objects that catch Sonic/Tails after Knuckles exits.
     */
    private void pressVacuumTubeButton() {
        S3kCnzEventWriteSupport.triggerScreenShake(services(), CNZ2_SCREEN_SHAKE_FRAMES);
        mutateVacuumTubeLayout();
        spawnChild(() -> new CnzVacuumTubeInstance(new ObjectSpawn(
                0x4740, 0x0828, Sonic3kObjectIds.CNZ_VACUUM_TUBE, 0x4C, 0, false, 0)));
        spawnChild(() -> new CnzVacuumTubeInstance(new ObjectSpawn(
                0x4740, 0x0A28, Sonic3kObjectIds.CNZ_VACUUM_TUBE, 0x20, 0, false, 0)));
    }

    /**
     * ROM {@code loc_65CAC}: writes four bytes down layout column {@code $8E}.
     * Gameplay layout changes must pass through the runtime mutation pipeline so
     * copy-on-write snapshots, dirty regions, and rewind observe one coherent edit.
     */
    private void mutateVacuumTubeLayout() {
        if (services().currentLevel() == null || services().zoneLayoutMutationPipeline() == null) {
            return;
        }
        LevelMutationSurface surface = LevelMutationSurface.forLevel(services().currentLevel());
        LayoutMutationContext context = new LayoutMutationContext(surface, effects -> {
            if (services().levelManager() != null) {
                services().levelManager().applyMutationEffects(effects);
            }
        });
        services().zoneLayoutMutationPipeline().applyImmediately(mutationContext -> {
            for (int row = 0; row < VACUUM_LAYOUT_VALUES.length; row++) {
                mutationContext.surface().setBlockInMap(
                        VACUUM_LAYOUT_LAYER,
                        VACUUM_LAYOUT_X,
                        VACUUM_LAYOUT_START_Y + row,
                        VACUUM_LAYOUT_VALUES[row]);
            }
            return MutationEffects.foregroundRedraw();
        }, context);
    }

    /** Test seam: the lights-off flash child spawned on press, or null. */
    CnzLightsFlashChildInstance getSpawnedFlashForTest() {
        return spawnedFlash;
    }

    boolean ownsFlash(CnzLightsFlashChildInstance flash) {
        return spawnedFlash == flash;
    }

    void clearCompletedFlash(CnzLightsFlashChildInstance flash) {
        if (spawnedFlash == flash) {
            spawnedFlash = null;
        }
    }

    /** Test seam for native respawn-entry persistence assertions. */
    boolean isPressedForTest() {
        return pressed;
    }

}
