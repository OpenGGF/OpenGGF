package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/** LRZ2 {@code Obj_LRZ2CutsceneKnuckles} ({@code sonic3k.asm:131172-131253}). */
public final class Lrz2CutsceneControllerObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, RomObjectCodePointerProvider {
    private int stage;
    private boolean childrenSpawned;
    private boolean transitionRequested;

    private record Extra(int stage, boolean childrenSpawned, boolean transitionRequested)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public Lrz2CutsceneControllerObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZ2CutsceneKnuckles");
    }

    @Override public int romObjectCodePointerHighWord() { return 0x0006; }

    @Override
    public void update(int vIntRunCount, PlayableEntity ignored) {
        LrzZoneRuntimeState lrz = lrz();
        if (lrz == null || lrz.playerCharacter() == PlayerCharacter.KNUCKLES) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        AbstractPlayableSprite player = mainPlayer();
        if (player == null) return;
        switch (stage) {
            case 0 -> waitForRange(player);
            case 1 -> waitForApproach(player);
            case 2 -> waitForLanding(player, lrz);
            case 3 -> raiseCamera(lrz);
            case 4 -> requestBossSlot(player, lrz);
            default -> { }
        }
    }

    private void waitForRange(AbstractPlayableSprite player) {
        int dx = (short) (player.getCentreX() - getSpawn().x());
        int dy = (short) (player.getCentreY() - getSpawn().y());
        if (dx < -0x10 || dx >= 0x10 || dy < -0x240 || dy >= 0x240) return;
        services().camera().setMinX(services().camera().getX());
        if (!childrenSpawned) {
            childrenSpawned = true;
            spawnChild(() -> new CutsceneKnucklesLrz2Instance(new ObjectSpawn(
                    0x3A38, 0x00EC, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 0x24, 0, false, 0)));
            spawnChild(LrzKnucklesBoulderObjectInstance::new);
            spawnDynamicObject(SongFadeTransitionInstance.transitionTo(Sonic3kMusic.KNUCKLES.id));
        }
        stage = 1;
    }

    private void waitForApproach(AbstractPlayableSprite player) {
        if ((player.getCentreX() & 0xFFFF) < 0x39B0) return;
        player.setControlLocked(true);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.clearLogicalInputState();
        player.setForcedInputMask(0);
        stage = 2;
    }

    private void waitForLanding(AbstractPlayableSprite player, LrzZoneRuntimeState lrz) {
        if (player.getAir()) return;
        lrz.setCutsceneFlag(0);
        services().camera().setMinX((short) 0);
        player.setControlLocked(false);
        ObjectControlState.nativeBit7FullControl().applyTo(player); // object_control = $81
        player.setAnimationId(7);
        stage = 3;
    }

    private void raiseCamera(LrzZoneRuntimeState lrz) {
        int y = (services().camera().getY() & 0xFFFF) - 2;
        services().camera().setY((short) y);
        if (y <= 0x90) {
            services().camera().setY((short) 0x90);
            lrz.setCutsceneFlag(1);
            stage = 4;
        }
    }

    private void requestBossSlot(AbstractPlayableSprite player, LrzZoneRuntimeState lrz) {
        if ((player.getCentreY() & 0xFFFF) < 0x4C0 || transitionRequested) return;
        transitionRequested = true;
        lrz.setAct3CarryActive(true);
        services().requestZoneAndAct(Sonic3kZoneIds.ZONE_LRZ_BOSS_HPZ, 0, true);
    }

    private AbstractPlayableSprite mainPlayer() {
        PlayableEntity player = services().playerQuery().mainPlayerOrNull();
        return player instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    private LrzZoneRuntimeState lrz() {
        return services().zoneRuntimeState() instanceof LrzZoneRuntimeState state ? state : null;
    }

    @Override public Lrz2CutsceneControllerObjectInstance recreateForRewind(RewindRecreateContext c) {
        return new Lrz2CutsceneControllerObjectInstance(c.spawn());
    }
    @Override public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext c) {
        return super.captureRewindState(c).withObjectSubclassExtra(
                new Extra(stage, childrenSpawned, transitionRequested));
    }
    @Override public void restoreRewindState(PerObjectRewindSnapshot s, RewindCaptureContext c) {
        super.restoreRewindState(s, c);
        if (s.objectSubclassExtra() instanceof Extra e) {
            stage=e.stage(); childrenSpawned=e.childrenSpawned(); transitionRequested=e.transitionRequested();
        }
    }
    public int stageForTest() { return stage; }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
