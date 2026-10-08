package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kSpriteMaskSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** ChildObjDat_665C4: loc_65B0E mask, loc_65A8C cloud, five loc_65B42 trails. */
public final class SszDeathEggChild extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private int parentSlot = -1;
    private int x, y, yFixed, timer, riseVelocity;
    private boolean initialized, pendingDelete, visible, trackingClaimed, cloudDrifting;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();
    private transient S3kRawAnimation animator;

    public SszDeathEggChild(ObjectSpawn spawn) {
        super(spawn, "SszDeathEggChild");
        x = spawn.x(); y = spawn.y(); yFixed = y << 16;
    }

    SszDeathEggChild(ObjectSpawn spawn, int parentSlot) {
        this(spawn);
        this.parentSlot = parentSlot;
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        var state = (SszZoneRuntimeState) services().zoneRuntimeState();
        if (!initialized) {
            initialized = true;
            if (spawn.subtype() == 2) {
                // ObjSlot_664B6 admits one cloud in Slotted_object_bits+2.
                // Its live owner supplies the claim, so rewind/deletion cannot
                // leave a separate uncaptured Java tracking bit behind.
                for (var other : services().objectManager().activeObjectsOfType(SszDeathEggChild.class)) {
                    if (other != this && other.trackingClaimed && !other.isDestroyed()) {
                        ObjectLifetimeOps.deleteNoRespawn(this);
                        return;
                    }
                }
                trackingClaimed = true;
                timer = 0x190;
                animation.script = 0x66739;
            } else if (spawn.subtype() >= 4) {
                animation.mappingFrame = 1;
                animation.script = 0x66760;
            }
        }
        visible = true;
        if (spawn.subtype() == 0) {
            if (state.cutsceneFlag(2)) { pendingDelete = true; visible = false; return; }
            moveWithBackground(state);
        } else if (spawn.subtype() == 2) {
            if (!cloudDrifting) {
                animator().animateMultiDelay(animation, () -> { });
                if (--timer < 0) { cloudDrifting = true; riseVelocity = 0x10; timer = 0x180; }
            } else if (--timer < 0) {
                trackingClaimed = false; // Remove_From_TrackingSlot before Go_Delete_Sprite.
                pendingDelete = true;
            } else {
                animator().animateMultiDelay(animation, () -> { });
            }
            // loc_65A8C still moves and draws on the callback/deletion pass.
            moveWithBackground(state);
        } else {
            // Refresh_ChildPosition reads the SST slot, without a parent-alive
            // guard. The trails' animation callback, not parent deletion, owns exit.
            int px = 0, py = 0;
            for (var object : services().objectManager().getActiveObjects()) {
                if (object instanceof AbstractObjectInstance instance && instance.getSlotIndex() == parentSlot) { px = object.getX(); py = object.getY(); break; }
            }
            int dx = switch (spawn.subtype()) {
                case 4 -> -0x20; case 6 -> -0x10; case 8 -> 8; case 10 -> 0x10; case 12 -> 0x28;
                default -> throw new IllegalStateException("SSZ trail subtype " + spawn.subtype());
            };
            x = (px + dx) & 0xFFFF; y = (py + 0x1D) & 0xFFFF;
            // Animate_RawNoSSTMultiDelay passes the script anew on each call.
            // The shared interpreter implements exactly the same command tail.
            animation.script = 0x66760;
            animator().animateMultiDelay(animation, () -> pendingDelete = true);
        }
    }

    private void moveWithBackground(SszZoneRuntimeState state) {
        x = (x + state.backgroundCameraDelta()) & 0xFFFF;
        yFixed += riseVelocity << 8;
        y = ((yFixed >> 16) - (state.cloudOscillator() >> 2)) & 0xFFFF;
    }

    private S3kRawAnimation animator() {
        if (animator == null) {
            try { animator = S3kRawAnimation.load(services().romReader(), 0x66739, 0x38); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
        return animator;
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isPersistent() { return true; }
    @Override public int getPriorityBucket() { return spawn.subtype() == 0 ? 4 : spawn.subtype() == 2 ? 5 : 6; }
    @Override public int getOnScreenHalfWidth() { return spawn.subtype() >= 4 ? 4 : 0x40; }
    @Override public int getOnScreenHalfHeight() { return spawn.subtype() == 0 ? 0x30 : spawn.subtype() == 2 ? 0x10 : 0x1C; }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible) return;
        if (spawn.subtype() == 0) {
            try { S3kSpriteMaskSupport.submitFrame(services().graphicsManager(), services().rom(), 12, x, y); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        } else {
            var renderer = getRenderer(spawn.subtype() == 2
                    ? Sonic3kObjectArtKeys.SSZ_DEATH_EGG_CLOUD : Sonic3kObjectArtKeys.SSZ_DEATH_EGG_SMALL);
            if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(animation.mappingFrame, x, y, false, false);
        }
    }
}
