package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** Obj_5A922 timed opening collapse and Obj_5A94C boss/chase-driven floor collapse. */
public final class DezFinalArenaBreakup extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private int mode;
    private int remaining = 0x13;
    private int nextX;
    private int timer;

    public DezFinalArenaBreakup(ObjectSpawn spawn) {
        super(spawn, "DEZFinalArenaBreakup"); mode = spawn.subtype(); nextX = spawn.x();
    }
    public static DezFinalArenaBreakup opening() {
        return new DezFinalArenaBreakup(new ObjectSpawn(0x2D0, 0, 0, 0, 0, false, 0));
    }
    public static DezFinalArenaBreakup chase(int x) {
        return new DezFinalArenaBreakup(new ObjectSpawn(x, 0, 0, 1, 0, false, 0));
    }
    @Override public void update(int clock, PlayableEntity player) {
        if (mode != 0 && services().currentZone() != 0x17) {
            ObjectLifetimeOps.deleteNoRespawn(this); return;
        }
        var state = (DezFinalBossZoneRuntimeState) services().zoneRuntimeState();
        if (mode == 0) {
            timer = (short) (timer - 1);
            if (timer >= 0) return;
            timer = 0xF; publish(state);
            remaining = (short) (remaining - 1);
            if (remaining == 0) {
                state.screenShake().writeFlag(0); ObjectLifetimeOps.deleteNoRespawn(this);
            }
            return;
        }
        if (state.windowBase() == 0) {
            // loc_5A95A has no timer gate: one column per pass once the camera catches it.
            if (((services().camera().getXCopy() + 0x9C) & 0xFFFF) >= nextX) publish(state);
        } else if (state.bossY() < 0x110) {
            timer = (short) (timer - 1);
            if (timer < 0 && ((state.bossX() + 0x90) & 0xFFFF) >= nextX) {
                publish(state); timer = 0xD;
            }
        }
    }
    private void publish(DezFinalBossZoneRuntimeState state) {
        state.breakRequest(nextX); nextX = (nextX + 0x20) & 0xFFFF;
    }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean participatesInRomWorldTransitionOffset() { return false; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
