package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Concrete parent-owned {@code ChildObjDat_89B24} attached sprite slot. */
public final class TechnoSqueekAttachmentObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int[] HORIZONTAL_OFFSETS = {0x14,4, 0x0C,4, 0,4};
    private static final int[] VERTICAL_OFFSETS = {-4,0x14, -4,0x0C, -4,0};
    private static final int[] HORIZONTAL_FRAMES = {2, 3, 4};
    private static final int[] VERTICAL_FRAMES = {7, 8, 9};
    @RewindTransient(reason = "relinked by exact parent slot after rewind settle")
    private TechnoSqueekBadnikInstance parent;
    private int parentSlot;
    private int currentX;
    private int currentY;
    private int mappingFrame = 2;
    private int animationTimer;
    private int animationIndex;
    private boolean initialized;

    TechnoSqueekAttachmentObjectInstance(ObjectSpawn spawn, TechnoSqueekBadnikInstance parent) {
        super(spawn, "TechnoSqueekAttachment");
        this.parent = parent;
        parentSlot = parent == null ? -1 : parent.getSlotIndex();
        currentX = spawn.x();
        currentY = spawn.y();
    }

    @Override public void update(int frameCounter, PlayableEntity player) {
        if (!initialized) { initialized = true; return; } // creation frame draws frame 2 only.
        if (parent == null || parent.isDestroyed()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        if (--animationTimer < 0) {
            int[] frames = parent.verticalMotion() ? VERTICAL_FRAMES : HORIZONTAL_FRAMES;
            animationIndex = (animationIndex + 1) % frames.length;
            mappingFrame = frames[animationIndex];
            animationTimer = 3;
        }
        if (!parent.childFrozen()) refreshPosition();
        updateDynamicSpawn(currentX, currentY);
    }

    private void refreshPosition() {
        int[] offsets = parent.verticalMotion() ? VERTICAL_OFFSETS : HORIZONTAL_OFFSETS;
        int index;
        if (parent.childUsesTerminalOffset()) index = 2;
        else index = parent.mappingFrame == (parent.verticalMotion() ? 6 : 1) ? 1 : 0;
        int dx = offsets[index * 2];
        int dy = offsets[index * 2 + 1];
        if (!parent.badnikFacingLeft()) dx = -dx;
        if (parent.verticalPresentation()) dy = -dy;
        currentX = parent.getX() + dx;
        currentY = parent.getY() + dy;
    }

    static int[] horizontalOffsets() { return HORIZONTAL_OFFSETS.clone(); }
    static int[] verticalOffsets() { return VERTICAL_OFFSETS.clone(); }
    TechnoSqueekBadnikInstance parentMember() { return parent; }
    int familySlot() { return parentSlot; }
    int mappingFrame() { return mappingFrame; }
    @Override public int getX() { return currentX; }
    @Override public int getY() { return currentY; }
    @Override public int getPriorityBucket() { return 5; }

    @Override public TechnoSqueekAttachmentObjectInstance recreateForRewind(RewindRecreateContext context) {
        var restored = new TechnoSqueekAttachmentObjectInstance(context.spawn(), null);
        if (context.state() != null && context.state().compactGenericState() != null) {
            GenericFieldCapturer.restoreObjectSubclassScalarsCompact(restored, context.state().compactGenericState());
        }
        return restored;
    }

    @Override protected void afterRewindRestoreSettled() {
        if (parent != null || tryServices() == null || services().objectManager() == null) return;
        for (ObjectInstance candidate : services().objectManager().getActiveObjects()) {
            if (candidate instanceof TechnoSqueekBadnikInstance squeek && squeek.getSlotIndex() == parentSlot) {
                parent = squeek;
                return;
            }
        }
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialized || isDestroyed()) return;
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_TECHNOSQUEEK);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndexForcedPriority(mappingFrame, currentX, currentY,
                    parent != null && !parent.badnikFacingLeft(),
                    parent != null && parent.verticalPresentation(), -1, true);
        }
    }
}
