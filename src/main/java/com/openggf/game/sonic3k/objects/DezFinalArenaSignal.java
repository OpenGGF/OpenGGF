package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.runtime.DezFinalCamera;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** Independently allocated DEZ3 quake/sound worker $8642E and camera worker $810A0. */
public final class DezFinalArenaSignal extends AbstractObjectInstance implements SpawnRewindRecreatable {
    public static final int QUAKE = 0, CAMERA = 1;
    private int kind;
    private boolean initialized;
    public DezFinalArenaSignal(ObjectSpawn spawn) { super(spawn, "DEZFinalArenaSignal"); kind = spawn.subtype(); }
    public static DezFinalArenaSignal quake() {
        return new DezFinalArenaSignal(new ObjectSpawn(0, 0, 0, QUAKE, 0, false, 0));
    }
    public static DezFinalArenaSignal camera() {
        return new DezFinalArenaSignal(new ObjectSpawn(0, 0, 0, CAMERA, 0, false, 0));
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        var state = (DezFinalBossZoneRuntimeState) services().zoneRuntimeState();
        if (kind == QUAKE) {
            if (!initialized) { initialized = true; state.screenShake().writeFlag(0xFF00 | (state.screenShake().flag() & 0xFF)); }
            if (state.screenShake().flag() == 0) ObjectLifetimeOps.deleteNoRespawn(this);
            else if ((vIntRunCount & 15) == 0) services().playSfx(Sonic3kSfx.RUMBLE_2.id);
        } else {
            var camera = services().camera();
            int x = DezFinalCamera.nativeX(camera);
            camera.setMinX((short) x);
            if (x >= 0x520) {
                state.bossSignals(state.bossSignals() | 4);
                camera.setMinX((short) 0x520); camera.setMaxX((short) 0x5C0);
                state.windowBase(0x2C0);
                ObjectLifetimeOps.deleteNoRespawn(this);
            }
        }
    }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean participatesInRomWorldTransitionOffset() { return false; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
