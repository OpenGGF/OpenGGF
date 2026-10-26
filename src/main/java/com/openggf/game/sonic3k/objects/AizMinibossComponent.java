package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.objects.boss.BossChildComponent;

import java.util.List;

/** AIZ parent3 is a physical SST address, not a Java boss lifetime or an inline dispatch. */
abstract class AizMinibossComponent extends AbstractObjectInstance implements BossChildComponent {
    private int parentSlot;
    private int priority;
    private int childOrdinal;
    private int lastUpdatedVIntRunCount = -1;
    protected int currentX;
    protected int currentY;
    protected int nativeRenderFlags;
    protected boolean nativeRetirementPending;

    AizMinibossComponent(AbstractBossInstance parent, String name, int priority, int ordinal) {
        this(new ObjectSpawn(parent.getX(), parent.getY(), 0x90, ordinal, 0, false, 0), name, priority);
        parentSlot = parent.getSlotIndex();
        nativeRenderFlags = parent.getState().renderFlags & 3;
    }

    AizMinibossComponent(ObjectSpawn spawn, String name, int priority) {
        super(spawn, name);
        this.priority = priority;
        childOrdinal = spawn.subtype();
        parentSlot = -1; // Pure recreation restores the captured physical address before dispatch.
        currentX = spawn.x();
        currentY = spawn.y();
    }

    @Override public com.openggf.game.mutators.ObjectHeadRole headPresentationRole() {
        return com.openggf.game.mutators.ObjectHeadRole.BOSS;
    }

    final int physicalParentSlot() { return parentSlot; }
    public final int getChildOrdinal() { return childOrdinal; }

    final AbstractBossInstance bossParent() {
        var owner = NativeSstSlotAccess.currentOwner(services().objectManager(), parentSlot);
        return owner instanceof AbstractBossInstance boss ? boss : null;
    }

    final S3kChildDrawParentHeader parentHeader() {
        return S3kChildDrawParentHeader.read(services().objectManager(), parentSlot);
    }

    final boolean beginOwnDispatch(int vIntRunCount) {
        if (lastUpdatedVIntRunCount == vIntRunCount) return false;
        lastUpdatedVIntRunCount = vIntRunCount;
        if (nativeRetirementPending) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return false;
        }
        return true;
    }

    final void refreshAdjustedPosition(int dx, int dy) {
        setAdjustedPosition(parentHeader(), dx, dy);
    }

    final void initializeAdjustedPosition(AbstractBossInstance parent, int dx, int dy) {
        setAdjustedPosition(S3kChildDrawParentHeader.fromBoss(parent), dx, dy);
    }

    private void setAdjustedPosition(S3kChildDrawParentHeader header, int dx, int dy) {
        // Refresh_ChildPositionAdjusted: copied facing bits and signed byte offsets,
        // with native word position writes (skdisasm044fa467:177386-177412).
        nativeRenderFlags = header.renderFlags() & 3;
        currentX = (header.x() + ((nativeRenderFlags & 1) != 0 ? -dx : dx)) & 0xFFFF;
        currentY = (header.y() + ((nativeRenderFlags & 2) != 0 ? -dy : dy)) & 0xFFFF;
    }

    final void finishNativeDrawTail() {
        // Child_Draw*Sprite2 tests bit4 after routine work. Go_Delete_Sprite_2 installs
        // Delete_Current_Sprite; the following OWN dispatch clears this SST.
        // Parent status7 suppresses touch separately and does not own deletion.
        if ((parentHeader().flags38() & 0x10) != 0) nativeRetirementPending = true;
        updateDynamicSpawn();
    }

    protected final void updateDynamicSpawn() { updateDynamicSpawn(currentX, currentY); }
    @Override protected void afterRewindRestoreSettled() {
        super.afterRewindRestoreSettled();
        updateDynamicSpawn(); // Restore captured pose; do not execute the parent read or a routine.
    }
    @Override public int getX() { return currentX; }
    @Override public int getY() { return currentY; }
    @Override public int getPriorityBucket() { return priority; }
    @Override public boolean isPersistent() { return true; }

    static List<AizMinibossComponent> family(ObjectManager manager, int parentSlot) {
        return manager.getActiveObjects().stream().filter(AizMinibossComponent.class::isInstance)
                .map(AizMinibossComponent.class::cast)
                .filter(child -> child.parentSlot == parentSlot && !child.isDestroyed()).toList();
    }
}
