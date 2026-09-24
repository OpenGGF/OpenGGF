package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** loc_5711E/loc_57156: the screen-positioned Death Egg in LRZ2's background. */
public final class LrzDeathEggBackgroundInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private boolean artQueued;
    private long artOrdinal = -1;

    public LrzDeathEggBackgroundInstance(ObjectSpawn spawn) { super(spawn, "LRZ2DeathEggBackground"); }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        var state = (LrzZoneRuntimeState) services().zoneRuntimeState();
        if (state.playerCharacter() == PlayerCharacter.KNUCKLES) {
            // loc_5711E deletes for Player_mode=3; the background owner has no retry.
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        claimArtIfReady();
        if (!artQueued && active(state.deathEggScrollWord(), services().camera().getWidth())) {
            // ExecuteObjects reads the previous background pass's coordinate. The later
            // SwScrlLrz pass publishes the current frame used by appendRenderCommands.
            try {
                var physical = services().kosinskiModuleQueue();
                if (physical != null) {
                    Sonic3kPlcLoader.bindRuntimePatternDmaTarget(physical, services());
                    physical.enqueue(services().rom(), 0x15A112, 0x39F * 32);
                }
                artOrdinal = S3kRuntimeArtCoordinator.from(services()).moduleQueue()
                        .queue(services().rom(), 0x15A112, 0x39F).ordinal();
                artQueued = true; // st $2E(a0); the native routine does not wait to draw.
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }

    private void claimArtIfReady() {
        if (artOrdinal < 0) return;
        var queue = S3kRuntimeArtCoordinator.from(services()).moduleQueue();
        var handle = services().hardwareTiming().pendingHandle(HardwareWorkKind.KOS_MODULE_QUEUE,
                artOrdinal).orElseThrow();
        if (queue.isReady(handle)) { queue.claim(handle); artOrdinal = -1; }
    }

    public static boolean active(int scrollWord, int width) {
        // sub_57082: signed ($678 - HScroll_table+$004) <= -$7E0. Native320
        // opens while the sprite is still beyond the right edge. Widen that lead-in
        // by the extra viewport width so the same body enters widescreen smoothly,
        // rather than appearing abruptly inside the enlarged view at native's gate.
        return (short) (0x678 - scrollWord) <= -0x7E0 + Math.max(0, width - 320);
    }

    public static int screenX(int scrollWord) {
        // Render_Sprites masks SAT X to9bits, then the VDP removes its $80 bias.
        // Unwrap the relevant $A00 turn into one continuous body: at the native
        // gate X=-$7E0 this gives416 (offscreen); crossing -$800 gives384 and
        // enters from the right. Widescreen must not repeat a body every512pixels.
        return (short) (0x678 - scrollWord) + 0xA00 - 0x80;
    }

    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x380); }
    @Override public boolean isHighPriority() { return false; } // art_tile=$639F, bit15 clear.
    @Override public boolean isPersistent() { return true; } // Draw_Sprite, no range-delete.
    @Override public boolean participatesInRomWorldTransitionOffset() { return false; }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var state = (LrzZoneRuntimeState) services().zoneRuntimeState();
        var camera = services().camera();
        if (!active(state.deathEggScrollWord(), camera.getWidth())) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.LRZ2_DEATH_EGG_BACKGROUND);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(0, camera.getX() + screenX(state.deathEggScrollWord()),
                    camera.getY() + 0xC0 - state.backgroundCameraY() - 0x80, false, false);
        }
    }
}
