package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.NativeSstStateSource;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** AIZMiniboss_BarrelShotFlare: Init, ROM raw animation, then deferred physical retirement. */
public class AizMinibossBarrelShotFlareChild extends AbstractObjectInstance implements RewindRecreatable {
    private static final int SCRIPT = 0x6913F;
    private static final int SCRIPT_SIZE = 13;
    private static final int Y_OFFSET = 4;

    // parent3 is a physical SST address: Refresh_ChildPositionAdjusted has no parent-status gate.
    private int parentSlot = -1;
    private int currentX;
    private int currentY;
    private int renderFlags;
    private boolean initialized;
    private boolean pendingDelete;
    // Opted-in attack policy owns a bounded warning, separate from stock Go_Delete.
    private boolean policyWarning;
    private int warningRemainingTicks;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();
    private transient S3kRawAnimation animator;

    public AizMinibossBarrelShotFlareChild(AbstractObjectInstance anchor) {
        this(new ObjectSpawn(anchor == null ? 0 : anchor.getX(),
                anchor == null ? 0 : (anchor.getY() + Y_OFFSET) & 0xFFFF,
                0x90, 0, 0, false, 0));
        parentSlot = anchor == null ? -1 : anchor.getSlotIndex();
    }

    static AizMinibossBarrelShotFlareChild warning(AbstractObjectInstance anchor, int delayTicks) {
        if (delayTicks < 16 || delayTicks > 60) {
            throw new IllegalArgumentException("Warning delay must be 16..60 updates");
        }
        var flare = new AizMinibossBarrelShotFlareChild(anchor);
        flare.policyWarning = true;
        flare.warningRemainingTicks = delayTicks;
        return flare;
    }

    private AizMinibossBarrelShotFlareChild(ObjectSpawn spawn) {
        super(spawn, "AIZMinibossBarrelShotFlare");
        currentX = spawn.x();
        currentY = spawn.y();
    }

    @Override
    public AizMinibossBarrelShotFlareChild recreateForRewind(RewindRecreateContext ctx) {
        // The captured numeric link and raw cursor are restored after this pure birth shell.
        return new AizMinibossBarrelShotFlareChild(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (pendingDelete && !policyWarning) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        if (policyWarning && NativeSstSlotAccess.currentOwner(services().objectManager(), parentSlot) == null) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        Header header = parentHeader();
        currentX = header.x();
        currentY = (header.y() + ((header.flags() & 2) == 0 ? Y_OFFSET : -Y_OFFSET)) & 0xFFFF;
        renderFlags = header.flags();
        if (!initialized) {
            // AIZMiniboss_BarrelShotFlare_Init returns after SetUp_ObjAttributes2.
            initialized = true;
            animation.mappingFrame = 7;
            animation.script = SCRIPT;
            return;
        }
        // Init/birth precedes the admitted warning delay. The stock raw cursor
        // remains literal; a longer policy warning holds its terminal pose.
        if (policyWarning && --warningRemainingTicks <= 0) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        if (pendingDelete) return;
        if (animator == null) {
            try { animator = S3kRawAnimation.load(services().romReader(), SCRIPT, SCRIPT_SIZE); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
        // F4 runs Go_Delete_Sprite; the wrapper still reaches Draw_Sprite on this dispatch.
        animator.animateMultiDelay(animation, () -> pendingDelete = true);
    }

    private record Header(int x, int y, int flags) { }

    private Header parentHeader() {
        if (parentSlot < 0) throw new IllegalStateException("Uninitialized AIZ flare parent3");
        var owner = NativeSstSlotAccess.currentOwner(services().objectManager(), parentSlot);
        if (owner == null) return new Header(0, 0, 0);
        int flags;
        if (owner instanceof AizMinibossFlameBarrelChild barrel) {
            flags = barrel.nativeRefreshRenderFlags();
        } else if (owner instanceof AizMinibossBarrelShotFlareChild flare) {
            flags = flare.renderFlags;
        } else if (owner instanceof NativeSstStateSource source) {
            flags = source.nativeSstState().renderFlags();
        } else {
            throw new IllegalStateException("Unknown AIZ flare native header at SST" + parentSlot
                    + ": " + owner.getClass().getName());
        }
        return new Header(owner.getX() & 0xFFFF, owner.getY() & 0xFFFF, flags & 3);
    }

    // This wrapper has only Draw_Sprite; its own F4/Delete_Current pair owns the lifetime.
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
    @Override public int getX() { return currentX; }
    @Override public int getY() { return currentY; }
    @Override public int getPriorityBucket() { return initialized ? 4 : 0; }
    @Override public int getOnScreenHalfWidth() { return initialized ? 8 : 0; }
    @Override public int getOnScreenHalfHeight() { return initialized ? 8 : 0; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialized || isDestroyed()) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.AIZ_MINIBOSS);
        if (renderer != null && renderer.isReady())
            renderer.drawFrameIndex(animation.mappingFrame, currentX, currentY,
                    (renderFlags & 1) != 0, (renderFlags & 2) != 0, 0);
    }
}
