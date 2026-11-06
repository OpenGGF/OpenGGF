package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.sprites.playable.AbstractPlayableSprite;


/**
 * Button used by the AIZ2 Knuckles post-boss cutscene.
 *
 * <p>ROM reference: Obj_CutsceneButton subtype 0.
 * The button reads the position words at the slot address saved by cutscene
 * Knuckles. It does not inspect the occupant's type or animation phase, and
 * a retired actor's slot can now contain the button itself.
 */
public class S3kCutsceneButtonObjectInstance extends AbstractS3kCutsceneButtonInstance
        implements SpawnRewindRecreatable, ObjectVisualFacing.NonDirectional {

    private boolean cutsceneOverride;

    public S3kCutsceneButtonObjectInstance(ObjectSpawn spawn) {
        this(spawn, false);
    }

    private S3kCutsceneButtonObjectInstance(ObjectSpawn spawn, boolean cutsceneOverride) {
        super(spawn, "CutsceneButton");
        this.cutsceneOverride = cutsceneOverride;
    }

    public static S3kCutsceneButtonObjectInstance createCutsceneOverride() {
        return new S3kCutsceneButtonObjectInstance(
                new ObjectSpawn(0x4B18, 0x0189, 0x83, 0, 0, false, 0), true);
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override protected boolean suppressUpdate() {
        if (!cutsceneOverride && Aiz2BossEndSequenceState.isCutsceneOverrideObjectsActive()) {
            ObjectLifetimeOps.expireDynamic(this);
            return true;
        }
        return false;
    }

    @Override protected TargetPosition targetPosition() {
        int slot = Aiz2BossEndSequenceState.getCutsceneKnucklesSlot();
        var manager = services().objectManager();
        if (slot < 0 || manager == null) {
            return null;
        }
        // Obj_CutsceneButton reads the object pointer in _unkFAA4 and calls
        // Check_InMyRange directly; it does not require a landing/bounce flag
        // (sonic3k.asm:133988-134000).
        var occupant = NativeSstSlotAccess.currentOwner(manager, slot);
        // Delete_Current_Sprite zeros an empty slot. A reused slot exposes
        // its live owner's position words, without a stale Java actor link.
        int targetX = occupant == null ? 0 : (short) occupant.getX();
        int targetY = occupant == null ? 0 : (short) occupant.getY();
        return new TargetPosition(targetX, targetY);
    }

    @Override protected void onPressed(PlayableEntity playerEntity) {
        Aiz2BossEndSequenceState.pressButton();
        if (playerEntity instanceof AbstractPlayableSprite player) {
            // loc_65C56 clears Ctrl_1_locked in the button's own SST slot.
            // Player_1 is SST slot 0 and has already run this frame, so the
            // logical UP word AIZEndBoss_WaitForCutsceneKnuckles wrote is
            // already recorded by that player dispatch. Unlock the input
            // override now so the following player entry reads raw input
            // (sonic3k.asm:134025-134027, 138382-138388).
            player.setControlLocked(false);
            player.clearForcedInputMask();
            player.setForceInputRight(false);
        }
        // loc_65C04 only publishes st (_unkFAA9).w; the draw bridge consumes it
        // from its own, strictly later, SST slot in this same object scan
        // (sonic3k.asm:59662-59668, 134000-134003). Nothing is pushed at the
        // bridge from here.
        // loc_65C04 / loc_65C56 contain no sound request (sonic3k.asm:133993-134048).
    }

}
