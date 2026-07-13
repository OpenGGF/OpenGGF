package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/** Real after-current endpoint/interactor slot of the FBZ pendulum. */
public final class FbzMagneticPendulumEndpointObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    @RewindTransient(reason = "relinked by stable parent slot")
    private FbzMagneticPendulumObjectInstance parent;
    private int parentSlot;
    private int x;
    private int y;
    private boolean chainAllocationAttempted;
    private boolean cascadeDelete;
    @RewindTransient(reason = "relinked through the endpoint slot")
    private FbzMagneticPendulumChainObjectInstance chain;

    FbzMagneticPendulumEndpointObjectInstance(ObjectSpawn spawn, FbzMagneticPendulumObjectInstance parent) {
        super(spawn, "FBZMagneticPendulumEndpoint");
        this.parent = parent;
        parentSlot = parent == null ? -1 : parent.getSlotIndex();
        x = spawn.x();
        y = spawn.y();
    }

    private FbzMagneticPendulumEndpointObjectInstance(ObjectSpawn spawn) {
        this(spawn, null);
    }

    @Override
    public void update(int frameCounter, PlayableEntity ignored) {
        if (cascadeDelete || parent == null || parent.isDestroyed()) {
            if (chain != null) chain.requestCascadeDelete();
            setDestroyed(true);
            return;
        }
        if (!chainAllocationAttempted) {
            chainAllocationAttempted = true;
            chain = spawnChild(() -> new FbzMagneticPendulumChainObjectInstance(
                    buildSpawnAt(parent.getX(), parent.getY()), this, parent));
            if (chain.isDestroyed()) chain = null;
        }
        int sine = TrigLookupTable.sinHex(parent.angleValue());
        int cosine = TrigLookupTable.cosHex(parent.angleValue());
        // ROM: half minus one-eighth = 7/16 of the 256-entry trig value.
        x = parent.getX() + ((cosine * 7) >> 4);
        y = parent.getY() + ((sine * 7) >> 4);
        updateDynamicSpawn(x, y);
        parent.tryCapture(services().playerQuery().mainPlayerOrNull(), x, y);
    }

    void requestCascadeDelete() { cascadeDelete = true; }
    int parentSlot() { return parentSlot; }
    FbzMagneticPendulumObjectInstance parentMember() { return parent; }
    FbzMagneticPendulumChainObjectInstance chainMember() { return chain; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return 3; }

    @Override
    public FbzMagneticPendulumEndpointObjectInstance recreateForRewind(RewindRecreateContext context) {
        var recreated = new FbzMagneticPendulumEndpointObjectInstance(context.spawn(), null);
        if (context.state() != null && context.state().compactGenericState() != null) {
            GenericFieldCapturer.restoreObjectSubclassScalarsCompact(recreated, context.state().compactGenericState());
        }
        return recreated;
    }

    @Override
    protected void afterRewindRestoreSettled() {
        if (services().objectManager() == null) return;
        for (ObjectInstance candidate : services().objectManager().getActiveObjects()) {
            if (parent == null && candidate instanceof FbzMagneticPendulumObjectInstance pivot
                    && pivot.getSlotIndex() == parentSlot) {
                parent = pivot;
            }
            if (chain == null && candidate instanceof FbzMagneticPendulumChainObjectInstance links
                    && links.endpointSlot() == getSlotIndex()) chain = links;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_MAGNETIC_PENDULUM);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(3, x, y, false, false);
    }
}
