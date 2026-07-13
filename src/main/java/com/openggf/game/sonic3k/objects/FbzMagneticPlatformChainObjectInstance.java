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

import java.util.List;

/** Real after-current chain visual allocated by the magnetic platform. */
public final class FbzMagneticPlatformChainObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    @RewindTransient(reason = "relinked by stable owner slot after restore")
    private FbzMagneticPlatformObjectInstance parent;
    private int parentSlot;

    FbzMagneticPlatformChainObjectInstance(ObjectSpawn spawn, FbzMagneticPlatformObjectInstance parent) {
        super(spawn, "FBZMagneticPlatformChain");
        this.parent = parent;
        this.parentSlot = parent == null ? -1 : parent.getSlotIndex();
    }

    private FbzMagneticPlatformChainObjectInstance(ObjectSpawn spawn) {
        this(spawn, null);
    }

    @Override public void update(int frameCounter, PlayableEntity player) { }
    int visiblePieces() {
        if (parent == null) return 0;
        return Math.min(8, ((parent.displacement() + 0x18) >>> 5) + 1);
    }
    FbzMagneticPlatformObjectInstance parentMember() { return parent; }

    @Override public int getPriorityBucket() { return 4; }
    @Override public int getX() { return parent == null ? spawn.x() : parent.getX(); }
    @Override public int getY() { return parent == null ? spawn.y() : parent.getY() - 0x70; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_MAGNETIC_PLATFORM);
        if (renderer == null || !renderer.isReady() || parent == null) return;
        renderer.drawFrameIndex(0, spawn.x(), spawn.y(), false, false);
        int pieces = visiblePieces() - 1;
        int linkY = parent.getY() + 0x18;
        int displacement = parent.displacement();
        for (int i = 0; i < pieces; i++, linkY += 0x20) {
            int frame = 2;
            if (i == pieces - 1 && (((displacement - 8) & 0x1F) < 0x10)) frame = 1;
            renderer.drawFrameIndex(frame, parent.getX(), linkY, false, false);
        }
    }

    @Override
    public FbzMagneticPlatformChainObjectInstance recreateForRewind(RewindRecreateContext context) {
        var recreated = new FbzMagneticPlatformChainObjectInstance(context.spawn(), null);
        if (context.state() != null && context.state().compactGenericState() != null) {
            GenericFieldCapturer.restoreObjectSubclassScalarsCompact(recreated, context.state().compactGenericState());
        }
        return recreated;
    }

    @Override
    protected void afterRewindRestoreSettled() {
        if (parent != null || services().objectManager() == null) return;
        for (ObjectInstance candidate : services().objectManager().getActiveObjects()) {
            if (candidate instanceof FbzMagneticPlatformObjectInstance platform
                    && platform.getSlotIndex() == parentSlot) {
                parent = platform;
                return;
            }
        }
    }
}
