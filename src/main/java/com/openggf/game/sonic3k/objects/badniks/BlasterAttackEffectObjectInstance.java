package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Concrete {@code ChildObjDat_89726}; parent-relative, but independently terminating. */
public final class BlasterAttackEffectObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int[] SCRIPT = {0, 4, 4, 5, 0xF4};
    private BlasterBadnikInstance parent;
    private int parentSlot;
    private int currentX;
    private int currentY;
    // SetUp_ObjAttributes3 supplies mapping 4. Animate_RawNoSST then
    // pre-increments anim_frame and reads byte_89763[2] on its first call.
    private int scriptIndex = 2;
    private int mappingFrame = 4;
    private boolean initialized;

    BlasterAttackEffectObjectInstance(ObjectSpawn spawn, BlasterBadnikInstance parent) {
        super(spawn, "BlasterAttackEffect");
        this.parent = parent;
        this.parentSlot = parent == null ? -1 : parent.getSlotIndex();
        currentX = spawn.x();
        currentY = spawn.y();
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialized) { initialized = true; return; } // loc_89626 does not draw on creation tick.
        refreshFromParent();
        if (scriptIndex < SCRIPT.length - 1) mappingFrame = SCRIPT[scriptIndex++];
        else ObjectLifetimeOps.expireDynamic(this);
        updateDynamicSpawn(currentX, currentY);
    }

    private void refreshFromParent() {
        if (parent == null) return; // no parent-status delete: preserve the last valid position.
        int dx = parent.badnikFacingLeft() ? -0x1B : 0x1B;
        currentX = parent.getX() + dx;
        currentY = parent.getY() - 0x16;
    }

    static int[] animationScript() { return SCRIPT.clone(); }
    BlasterBadnikInstance parentMember() { return parent; }
    int familySlot() { return parentSlot; }
    int mappingFrame() { return mappingFrame; }
    @Override public int getX() { return currentX; }
    @Override public int getY() { return currentY; }
    @Override public int getPriorityBucket() { return 4; }

    @Override public BlasterAttackEffectObjectInstance recreateForRewind(RewindRecreateContext context) {
        var restored = new BlasterAttackEffectObjectInstance(context.spawn(), null);
        if (context.state() != null && context.state().compactGenericState() != null) {
            GenericFieldCapturer.restoreObjectSubclassScalarsCompact(restored, context.state().compactGenericState());
        }
        return restored;
    }

    @Override protected void afterRewindRestoreSettled() {
        if (parent != null || tryServices() == null || services().objectManager() == null) return;
        for (ObjectInstance candidate : services().objectManager().getActiveObjects()) {
            if (candidate instanceof BlasterBadnikInstance blaster && blaster.getSlotIndex() == parentSlot) {
                parent = blaster;
                return;
            }
        }
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialized || isDestroyed()) return;
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_BLASTER);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndexForcedPriority(mappingFrame, currentX, currentY,
                    parent != null && !parent.badnikFacingLeft(), false, -1, true);
        }
    }
}
