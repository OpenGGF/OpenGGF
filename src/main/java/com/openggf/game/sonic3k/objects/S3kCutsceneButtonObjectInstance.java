package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Button used by the AIZ2 Knuckles post-boss cutscene.
 *
 * <p>ROM reference: Obj_CutsceneButton subtype 0.
 * The button reads the position words at the slot address saved by cutscene
 * Knuckles. It does not inspect the occupant's type or animation phase, and
 * a retired actor's slot can now contain the button itself.
 */
public class S3kCutsceneButtonObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable, ObjectVisualFacing.NonDirectional {

    // Obj_CutsceneButton renders only the switch; the cutscene character is separately owned.

    private static final int INIT_Y_OFFSET = 4;
    private static final int PRIORITY = 4;
    private static final int RANGE_LEFT = -0x18;
    private static final int RANGE_RIGHT = RANGE_LEFT + 0x30;
    private static final int RANGE_TOP = -0x18;
    private static final int RANGE_BOTTOM = RANGE_TOP + 0x30;

    private int x;
    private int y;
    private boolean cutsceneOverride;
    private boolean pressed;

    public S3kCutsceneButtonObjectInstance(ObjectSpawn spawn) {
        this(spawn, false);
    }

    private S3kCutsceneButtonObjectInstance(ObjectSpawn spawn, boolean cutsceneOverride) {
        super(spawn, "CutsceneButton");
        this.x = spawn.x();
        this.y = spawn.y() + INIT_Y_OFFSET;
        this.cutsceneOverride = cutsceneOverride;
    }

    public static S3kCutsceneButtonObjectInstance createCutsceneOverride() {
        return new S3kCutsceneButtonObjectInstance(
                new ObjectSpawn(0x4B18, 0x0189, 0x83, 0, 0, false, 0), true);
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
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (!cutsceneOverride && Aiz2BossEndSequenceState.isCutsceneOverrideObjectsActive()) {
            setDestroyed(true);
            return;
        }
        if (pressed) {
            return;
        }
        int slot = Aiz2BossEndSequenceState.getCutsceneKnucklesSlot();
        var manager = services().objectManager();
        if (slot < 0 || manager == null) {
            return;
        }
        // Obj_CutsceneButton reads the object pointer in _unkFAA4 and calls
        // Check_InMyRange directly; it does not require a landing/bounce flag
        // (sonic3k.asm:133988-134000).
        var occupant = NativeSstSlotAccess.currentOwner(manager, slot);
        // Delete_Current_Sprite zeros an empty slot. A reused slot exposes
        // its live owner's position words, without a stale Java actor link.
        int targetX = occupant == null ? 0 : (short) occupant.getX();
        int targetY = occupant == null ? 0 : (short) occupant.getY();
        // Check_InMyRange performs signed word comparisons against each
        // endpoint (sonic3k.asm:180055-180073), not a Java unbounded delta.
        if (targetX >= (short) (x + RANGE_LEFT) && targetX < (short) (x + RANGE_RIGHT)
                && targetY >= (short) (y + RANGE_TOP) && targetY < (short) (y + RANGE_BOTTOM)) {
            // loc_65C04 installs loc_65C50, then immediately dispatches the
            // subtype action through off_65C40 in this same object pass.
            pressButton(playerEntity);
        }
    }

    private void pressButton(PlayableEntity playerEntity) {
        pressed = true;
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
        services().playSfx(Sonic3kSfx.SWITCH.id);
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
