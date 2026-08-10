package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.events.S3kFbzEventWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/** {@code loc_8642E}: global {@code Screen_shake_flag} owner and Rumble2 cadence. */
final class Fbz2SubbossRumbleController extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private boolean initialized;

    Fbz2SubbossRumbleController() {
        this(new ObjectSpawn(0, 0, 0xAB, 0, 0, false, 0));
    }

    public Fbz2SubbossRumbleController(ObjectSpawn spawn) {
        super(spawn, "FBZ2SubbossRumble");
        setRomWorldPositioned(false);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        boolean active;
        if (!initialized) {
            initialized = true;
            S3kFbzEventWriteSupport.setScreenShakeState(services(), true, 0, vIntRunCount & 0x3F);
            active = true;
        } else {
            active = S3kFbzEventWriteSupport.isScreenShakeActive(services());
        }
        if (!active) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        if ((vIntRunCount & 0x0F) == 0) services().playSfx(Sonic3kSfx.RUMBLE_2.id);
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) { }

    /** Native loc_8642E is a global controller, not a world-position-cullable sprite. */
    @Override public boolean isPersistent() { return true; }
}
