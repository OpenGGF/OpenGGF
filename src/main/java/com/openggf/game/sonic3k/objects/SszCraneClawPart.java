package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;

import java.util.List;

/** ChildObjDat_66610: loc_65138 front claw and loc_6515E cable, shared ROM routines with HPZ. */
public final class SszCraneClawPart extends AbstractSszCraneChild {
    private int x;
    private int y;
    private int frame;
    private boolean flipX;
    private boolean visible;

    public SszCraneClawPart(ObjectSpawn spawn, SszCraneClaw claw) {
        super(spawn, "SszCraneClawPart", claw);
        x = spawn.x();
        y = spawn.y();
    }

    @Override public SszCraneClawPart recreateForRewind(RewindRecreateContext context) {
        return new SszCraneClawPart(context.spawn(), null);
    }

    private boolean cable() { return getSpawn().subtype() != 0; }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        visible = false;
        if (retiring) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if (!(parent instanceof SszCraneClaw claw) || parentGone()) {
            retiring = true;
            return;
        }
        x = claw.getX();
        y = (claw.getY() - (cable() ? 0x30 : 0)) & 0xFFFF;
        frame = cable() ? 4 + (claw.cableHeight() >>> 4) : (claw.mappingFrame() + 1) & 0xFF;
        // Cable uses Refresh_ChildPosition, not its Adjusted variant.
        flipX = !cable() && claw.craneFlipped();
        visible = true;
        updateDynamicSpawn(x, y);
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }
    /** word_66506 / word_6650C. */
    @Override public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(cable() ? 0x180 : 0x280);
    }
    @Override public int getOnScreenHalfHeight() { return cable() ? 0x80 : 0xC; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.KNUX_FINAL_BOSS_CRANE);
        if (visible && renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(frame, x, y, flipX, false);
        }
    }
}
