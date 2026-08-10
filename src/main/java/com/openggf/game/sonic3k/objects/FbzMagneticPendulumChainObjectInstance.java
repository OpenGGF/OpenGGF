package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/** Third real slot in the pendulum graph; renders five inline chain links. */
public final class FbzMagneticPendulumChainObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int[] LINK_DISTANCES = {20, 37, 54, 71, 88};
    private FbzMagneticPendulumEndpointObjectInstance endpoint;
    private FbzMagneticPendulumObjectInstance parent;
    private int endpointSlot;
    private int parentSlot;
    private boolean cascadeDelete;

    FbzMagneticPendulumChainObjectInstance(ObjectSpawn spawn,
                                           FbzMagneticPendulumEndpointObjectInstance endpoint,
                                           FbzMagneticPendulumObjectInstance parent) {
        super(spawn, "FBZMagneticPendulumChain");
        this.endpoint = endpoint;
        this.parent = parent;
        endpointSlot = endpoint == null ? -1 : endpoint.getSlotIndex();
        parentSlot = parent == null ? -1 : parent.getSlotIndex();
    }

    private FbzMagneticPendulumChainObjectInstance(ObjectSpawn spawn) {
        this(spawn, null, null);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (cascadeDelete || parent == null || parent.isDestroyed()) ObjectLifetimeOps.expireDynamic(this);
    }

    void requestCascadeDelete() { cascadeDelete = true; }
    int endpointSlot() { return endpointSlot; }
    FbzMagneticPendulumEndpointObjectInstance endpointMember() { return endpoint; }
    FbzMagneticPendulumObjectInstance parentMember() { return parent; }
    @Override public int getPriorityBucket() { return 3; }

    @Override
    public FbzMagneticPendulumChainObjectInstance recreateForRewind(RewindRecreateContext context) {
        var recreated = new FbzMagneticPendulumChainObjectInstance(context.spawn(), null, null);
        if (context.state() != null && context.state().compactGenericState() != null) {
            GenericFieldCapturer.restoreObjectSubclassScalarsCompact(
                    recreated, context.state().compactGenericState());
        }
        return recreated;
    }

    @Override
    protected void afterRewindRestoreSettled() {
        if (services().objectManager() == null) return;
        for (ObjectInstance candidate : services().objectManager().getActiveObjects()) {
            if (endpoint == null && candidate instanceof FbzMagneticPendulumEndpointObjectInstance point
                    && point.getSlotIndex() == endpointSlot) endpoint = point;
            if (parent == null && candidate instanceof FbzMagneticPendulumObjectInstance pivot
                    && pivot.getSlotIndex() == parentSlot) parent = pivot;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (parent == null || isDestroyed()) return;
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_MAGNETIC_PENDULUM);
        if (renderer == null || !renderer.isReady()) return;
        int sine = TrigLookupTable.sinHex(parent.angleValue());
        int cosine = TrigLookupTable.cosHex(parent.angleValue());
        for (int distance : LINK_DISTANCES) {
            int x = parent.getX() + ((cosine * distance) >> 8);
            int y = parent.getY() + ((sine * distance) >> 8);
            renderer.drawFrameIndex(2, x, y, false, false);
        }
    }
}
