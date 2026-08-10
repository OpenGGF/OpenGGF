package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Independent visual companion allocated once after the spider crane grabs P1. */
public final class FbzSpiderCraneCompanionObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private FbzSpiderCraneObjectInstance owner;
    private int ownerSlot;
    private int x;
    private int y;
    private int mappingFrame = 0xB;

    FbzSpiderCraneCompanionObjectInstance(ObjectSpawn spawn, FbzSpiderCraneObjectInstance owner) {
        super(spawn, "FBZSpiderCraneCompanion");
        this.owner = owner;
        ownerSlot = owner == null ? -1 : owner.getSlotIndex();
        x = spawn.x();
        y = spawn.y();
    }

    private FbzSpiderCraneCompanionObjectInstance(ObjectSpawn spawn) {
        this(spawn, null);
    }

    void follow(int x, int y) { this.x = x; this.y = y; updateDynamicSpawn(x, y); }
    void releaseToInertFrame() { owner = null; ownerSlot = -1; mappingFrame = 0; }
    int ownerSlot() { return ownerSlot; }
    FbzSpiderCraneObjectInstance ownerMember() { return owner; }
    @Override public void update(int vIntRunCount, PlayableEntity player) { }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return 5; }

    @Override
    public FbzSpiderCraneCompanionObjectInstance recreateForRewind(RewindRecreateContext context) {
        var recreated = new FbzSpiderCraneCompanionObjectInstance(context.spawn(), null);
        if (context.state() != null && context.state().compactGenericState() != null) {
            GenericFieldCapturer.restoreObjectSubclassScalarsCompact(
                    recreated, context.state().compactGenericState());
        }
        return recreated;
    }

    @Override
    protected void afterRewindRestoreSettled() {
        if (owner != null || ownerSlot < 0 || services().objectManager() == null) return;
        for (ObjectInstance candidate : services().objectManager().getActiveObjects()) {
            if (candidate instanceof FbzSpiderCraneObjectInstance crane
                    && crane.getSlotIndex() == ownerSlot) {
                owner = crane;
                return;
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_SPIDER_CRANE);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(mappingFrame, x, y, false, false);
    }
}
