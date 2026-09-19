package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** ROM {@code Obj_LRZ3Autoscroll} ({@code $9E}), the entry flash/control coordinator. */
public final class Lrz3AutoscrollControllerObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, RomObjectCodePointerProvider {
    private int stage;
    private int timer = 59;
    private record Extra(int stage, int timer)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra { }

    public Lrz3AutoscrollControllerObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZ3Autoscroll");
    }
    @Override public int romObjectCodePointerHighWord() { return 0x0007; }

    @Override public void update(int vIntRunCount, PlayableEntity ignored) {
        LrzZoneRuntimeState lrz = services().zoneRuntimeState() instanceof LrzZoneRuntimeState s ? s : null;
        if (lrz == null) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        switch (stage) {
            case 0 -> {
                lockPlayers(true);
                if (timer-- < 0) { stage = 1; timer = 0x11F; }
            }
            case 1 -> {
                // loc_793E2..loc_79486: Death Egg flash/fade. The palette owner performs the
                // actual fades; this controller preserves its ROM-duration gate and signals.
                if (timer-- < 0) {
                    lrz.setCutsceneFlag(0);
                    lrz.setLrz3TerrainRequest(-1);
                    lockPlayers(false);
                    stage = 2;
                    timer = 0xBF;
                }
            }
            case 2 -> {
                if (timer-- < 0) {
                    lrz.setLrz3TerrainRequest(-1);
                    ObjectLifetimeOps.deleteNoRespawn(this);
                }
            }
            default -> { }
        }
    }

    private void lockPlayers(boolean locked) {
        java.util.List<PlayableEntity> players = new java.util.ArrayList<>();
        players.add(services().playerQuery().mainPlayerOrNull());
        players.add(services().playerQuery().nativeP2OrNull());
        for (PlayableEntity candidate : players) {
            if (!(candidate instanceof AbstractPlayableSprite p)) continue;
            p.setControlLocked(locked);
            if (locked) p.clearLogicalInputState();
        }
    }

    @Override public Lrz3AutoscrollControllerObjectInstance recreateForRewind(RewindRecreateContext c) {
        return new Lrz3AutoscrollControllerObjectInstance(c.spawn());
    }
    @Override public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext c) {
        return super.captureRewindState(c).withObjectSubclassExtra(new Extra(stage,timer));
    }
    @Override public void restoreRewindState(PerObjectRewindSnapshot s, RewindCaptureContext c) {
        super.restoreRewindState(s,c);
        if (s.objectSubclassExtra() instanceof Extra e) { stage=e.stage(); timer=e.timer(); }
    }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
    public int stageForTest() { return stage; }
}
