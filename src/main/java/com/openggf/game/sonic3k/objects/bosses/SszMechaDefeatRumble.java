package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** loc_8642E: independent global shake flag and V-int-gated rumble sound owner. */
public final class SszMechaDefeatRumble extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private boolean initialized;
    public SszMechaDefeatRumble(ObjectSpawn spawn) { super(spawn, "SSZMechaDefeatRumble"); }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        var shake = ((SszZoneRuntimeState) services().zoneRuntimeState()).screenShake();
        if (!initialized) {
            initialized = true;
            // ST writes the high byte only; it does not clear a pre-existing low byte.
            shake.writeFlag(0xFF00 | (shake.flag() & 255));
        }
        if (shake.flag() == 0) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if ((vIntRunCount & 15) == 0) services().playSfx(Sonic3kSfx.RUMBLE_2.id);
    }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean participatesInRomWorldTransitionOffset() { return false; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
