package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.ShieldType;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelContinuationCarry;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import java.io.IOException;
import java.util.List;

/** SKL $AE: Obj_LRZ2CutsceneKnuckles ($63B22), including the $1600 fresh-load request. */
public final class LrzBoulderCutsceneObjectInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private int routine;
    private boolean initialized;
    private long artOrdinal = -1;

    public LrzBoulderCutsceneObjectInstance(ObjectSpawn spawn) { super(spawn, "LRZ2BoulderCutscene"); }

    @Override public void update(int vIntRunCount, PlayableEntity entity) {
        if (!(entity instanceof AbstractPlayableSprite player)) return;
        if ("knuckles".equals(player.getCode())) { com.openggf.level.objects.ObjectLifetimeOps.deleteNoRespawn(this); return; }
        var runtime = S3kRuntimeStates.currentLrz(services().zoneRuntimeRegistry()).orElseThrow();
        var camera = services().camera();
        if (!initialized) {
            initialized = true;
            spawnFreeChild(S3kNativeP2LockInstance::new);
        }
        claimArtIfReady();
        switch (routine) {
            case 0 -> {
                // word_63B94: offset -$10/width $10, offset -$240/height $240.
                if (player.getCentreX() < spawn.x() - 0x10 || player.getCentreX() >= spawn.x()
                        || player.getCentreY() < spawn.y() - 0x240 || player.getCentreY() >= spawn.y()) return;
                routine = 2;
                camera.setMinX(camera.getX());
                spawnFreeChild(() -> new CutsceneKnucklesLrz2Instance(
                        new ObjectSpawn(0, 0, 0x82, 0x24, 0, false, 0)));
                spawnFreeChild(() -> SongFadeTransitionInstance.transitionTo(Sonic3kMusic.KNUCKLES.id));
                spawnFreeChild(LrzCutsceneBoulderObjectInstance::new);
                try {
                    var queue = services().kosinskiModuleQueue();
                    if (queue != null) {
                        Sonic3kPlcLoader.bindRuntimePatternDmaTarget(queue, services());
                        queue.enqueue(services().rom(), 0x172214, 0x500 * 32);
                    }
                    artOrdinal = S3kRuntimeArtCoordinator.from(services()).moduleQueue()
                            .queue(services().rom(), 0x172214, 0x500).ordinal();
                } catch (IOException ex) { throw new IllegalStateException("LRZ boulder art", ex); }
            }
            case 2 -> {
                if ((player.getCentreX() & 0xFFFF) < 0x39B0) return;
                routine = 4;
                player.setControlLocked(true);
                player.setXSpeed((short) 0); player.setYSpeed((short) 0); player.setGSpeed((short) 0);
                player.clearLogicalInputState();
            }
            case 4 -> {
                if (player.getAir()) return;
                routine = 6;
                runtime.setCutsceneFlag(0);
                camera.setMinX((short) 0);
                camera.setScrollLocked(true);
                player.setControlLocked(false);
                ObjectControlState.nativeBit7FullControl().applyTo(player); // object_control=$81
                player.setAnimationId(7);
            }
            case 6 -> {
                int y = (camera.getY() - 2) & 0xFFFF;
                camera.setY((short) y);
                if (y <= 0x90) { routine = 8; runtime.setCutsceneFlag(1); }
            }
            case 8 -> {
                if ((player.getCentreY() & 0xFFFF) < 0x4C0) return;
                routine = 10;
                var level = services().levelManager();
                // SpawnLevelMainSprites_SpawnPowerup masks to elemental shield bits.
                ShieldType shield = player.hasShield() && player.getShieldType() != ShieldType.BASIC
                        ? player.getShieldType() : null;
                LevelContinuationCarry.request(level, 0x16, 0, player.getRingCount(),
                        level.getLevelGamestate().getTimerFrames(), shield);
            }
            default -> { }
        }
    }

    private void claimArtIfReady() {
        if (artOrdinal < 0) return;
        var queue = S3kRuntimeArtCoordinator.from(services()).moduleQueue();
        var handle = services().hardwareTiming().pendingHandle(
                com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE, artOrdinal).orElseThrow();
        if (queue.isReady(handle)) { queue.claim(handle); artOrdinal = -1; }
    }

    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
