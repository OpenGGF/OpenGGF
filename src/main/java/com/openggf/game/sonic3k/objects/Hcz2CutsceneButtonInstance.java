package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectVisualFacing;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.logging.Logger;

/**
 * Cutscene button for Hydrocity Zone Act 2.
 *
 * <p>ROM reference: Obj_CutsceneButton subtype 2 (loc_65C72 in sonic3k.asm:134029).
 * Spawned as a child of CutsceneKnux_HCZ2. Triggered when cutscene Knuckles
 * walks within proximity. On press:
 * <ul>
 *   <li>Sets {@code Level_trigger_array[8]} via {@link Sonic3kLevelTriggerManager}</li>
 *   <li>The tension bridge (object $6C, subtype $88) reads this flag and collapses</li>
 * </ul>
 *
 * <p>The button checks proximity against the active {@link CutsceneKnucklesHcz2Instance}
 * via the shared static reference (ROM equivalent: _unkFAA4).
 */
public class Hcz2CutsceneButtonInstance extends AbstractS3kCutsceneButtonInstance
        implements SpawnRewindRecreatable, ObjectVisualFacing.NonDirectional {
    private static final Logger LOG = Logger.getLogger(Hcz2CutsceneButtonInstance.class.getName());

    /** ROM: Level_trigger_array index 8 — read by tension bridge subtype $88. */
    private static final int TRIGGER_INDEX = 8;

    public Hcz2CutsceneButtonInstance(ObjectSpawn spawn) {
        super(spawn, "CutsceneButtonHCZ2");
    }

    @Override protected TargetPosition targetPosition() {
        // This route still obtains its native target through the existing actor authority.
        var knuckles = CutsceneKnucklesHcz2Instance.getActiveInstance();
        return knuckles == null ? null : new TargetPosition(knuckles.getX(), knuckles.getY());
    }

    /**
     * ROM: loc_65C72 (sonic3k.asm:134029) — CutsceneButton subtype 2 action.
     * Sets Level_trigger_array[8] which the tension bridge monitors.
     */
    @Override protected void onPressed(PlayableEntity player) {

        // ROM: st (Level_trigger_array+8).w
        Sonic3kLevelTriggerManager.setAll(TRIGGER_INDEX);

        LOG.info("HCZ2 cutscene button pressed: Level_trigger_array[" + TRIGGER_INDEX + "] set");
    }

}
