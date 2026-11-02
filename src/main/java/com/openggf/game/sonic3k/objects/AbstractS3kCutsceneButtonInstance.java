package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import java.util.List;

/** Shared Obj_CutsceneButton setup, proximity, press latch and drawing. */
abstract class AbstractS3kCutsceneButtonInstance extends AbstractObjectInstance
        implements ObjectVisualFacing.NonDirectional {
    protected int x;
    protected int y;
    protected boolean pressed;

    /** A caller supplies the position words owned by its existing cutscene authority. */
    protected record TargetPosition(int x, int y) { }

    AbstractS3kCutsceneButtonInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        x = spawn.x();
        y = spawn.y() + 4;
    }

    @Override public final int getX() { return x; }
    @Override public final int getY() { return y; }
    // ObjDat_CutsceneButton (sonic3k.asm:134880-134884): priority=$100, art bit 15 set.
    @Override public final int getPriorityBucket() { return RenderPriority.fromS3kWord(0x100); }
    @Override public final boolean isHighPriority() { return true; }

    protected boolean suppressUpdate() { return false; }
    protected abstract TargetPosition targetPosition();
    protected abstract void onPressed(PlayableEntity player);

    @Override public final void update(int vIntRunCount, PlayableEntity player) {
        if (suppressUpdate() || pressed) return;
        TargetPosition target = targetPosition();
        if (target == null) return;
        // loc_65C04 uses Check_InMyRange's signed word endpoint comparisons;
        // the subtype action is dispatched in the same pass (sonic3k.asm:133993-134014).
        int targetX = (short) target.x();
        int targetY = (short) target.y();
        if (targetX >= (short) (x - 0x18) && targetX < (short) (x + 0x18)
                && targetY >= (short) (y - 0x18) && targetY < (short) (y + 0x18)) {
            pressed = true;
            onPressed(player);
        }
    }

    @Override public final void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.BUTTON);
        if (renderer != null && renderer.isReady())
            renderer.drawFrameIndex(pressed ? 1 : 0, x, y, false, false);
    }
}
