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
    private static final int FLASH_ANIMATION_FRAMES = 30;
    private static final int FADE_TO_WHITE_FRAMES = 29;
    private static final int SHIP_HOLD_FRAMES = 0x120;
    private static final int DEBRIS_HOLD_FRAMES = 0xC0;
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
                if (--timer < 0) { stage = 1; timer = FLASH_ANIMATION_FRAMES - 1; }
            }
            case 1 -> {
                if (--timer < 0) {
                    // loc_79416: the flash callback suspends AnPal_LRZ3 while the generic
                    // eight-step fade raises the normal palette to white.
                    lrz.setLrz3PaletteCycleGate(0x80);
                    stage = 2;
                    timer = FADE_TO_WHITE_FRAMES - 1;
                }
            }
            case 2 -> {
                if (--timer < 0) {
                    // loc_79486: fade completion re-enables the post-flash accent channel,
                    // publishes the first terrain request and creates the bridge at ($60,$4D0).
                    lrz.setLrz3PaletteCycleGate(1);
                    lrz.setCutsceneFlag(0);
                    lrz.setLrz3TerrainRequest(-1);
                    spawnFlashBridge();
                    lockPlayers(false);
                    stage = 3;
                    timer = SHIP_HOLD_FRAMES - 1;
                }
            }
            case 3 -> {
                if (--timer < 0) {
                    lrz.setCutsceneFlag(1);
                    stage = 4;
                    timer = DEBRIS_HOLD_FRAMES - 1;
                }
            }
            case 4 -> {
                if (--timer < 0) {
                    lrz.setLrz3TerrainRequest(-1);
                    ObjectLifetimeOps.deleteNoRespawn(this);
                }
            }
            default -> { }
        }
    }

    private void spawnFlashBridge() {
        if (services().objectManager() == null) return;
        spawnChild(() -> new CollapsingBridgeObjectInstance(
                new ObjectSpawn(0x60, 0x4D0, 0x0F, 0, 0, false, 0)));
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
    public int timerForTest() { return timer; }
}
