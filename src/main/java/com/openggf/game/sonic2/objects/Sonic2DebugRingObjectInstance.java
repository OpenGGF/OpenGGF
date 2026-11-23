package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.rings.RingManagerInternalAccess;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** Slot-backed Obj25 used by Debug_SpawnObject (s2.asm:88759-88786). */
public final class Sonic2DebugRingObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener, SpawnRewindRecreatable,
        ObjectVisualFacing.NonDirectional {
    // Obj25_Index through Obj25_Delete (s2.asm:24980-25020).
    private static final int SPARKLE_FRAME_TICKS = 6;
    private static final int SPARKLE_FRAMES = 4;

    private int sparkleTicks = -1;
    private int vIntRunCount;

    public Sonic2DebugRingObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Ring");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        this.vIntRunCount = vIntRunCount;
        if (sparkleTicks < 0) {
            coarseXCullViewport(spawn.x());
        } else if (++sparkleTicks == SPARKLE_FRAME_TICKS * SPARKLE_FRAMES) {
            ObjectLifetimeOps.expireDynamic(this);
        }
    }

    @Override
    public int getCollisionFlags() {
        return sparkleTicks < 0 ? 0x47 : 0;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public void onTouchResponse(PlayableEntity entity, TouchResponseResult result, int frameCounter) {
        if (sparkleTicks >= 0 || result.category() != TouchCategory.SPECIAL
                || !(entity instanceof AbstractPlayableSprite player)) return;
        var rings = services().ringManager();
        if (rings != null && RingManagerInternalAccess.awardDynamicStageRing(rings, player)) {
            sparkleTicks = 0;
        }
    }

    @Override
    public int getOnScreenHalfWidth() {
        return 8;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return 8;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.bucket(sparkleTicks < 0 ? 2 : 1);
    }

    @Override
    public boolean isPersistent() {
        return sparkleTicks >= 0;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        var rings = services().ringManager();
        if (rings == null) return;
        if (sparkleTicks < 0) {
            rings.drawRingAt(getX(), getY(), vIntRunCount);
        } else {
            rings.drawSparkleAt(getX(), getY(), sparkleTicks / SPARKLE_FRAME_TICKS);
        }
    }
}
