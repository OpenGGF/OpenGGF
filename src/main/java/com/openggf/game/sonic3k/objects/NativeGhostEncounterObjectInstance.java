package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import java.util.List;

/** Encounter-local Hyudoro owner; it never edits the active zone's lighting or checkpoint gates. */
public final class NativeGhostEncounterObjectInstance extends AbstractObjectInstance
        implements HyudoroContext, SpawnRewindRecreatable {
    private int remainingTicks = 720;
    private int count;
    private int timer;
    private int darkness = 5;

    public NativeGhostEncounterObjectInstance(ObjectSpawn spawn) { super(spawn, "NativeGhostEncounter"); }

    @Override public void update(int vIntRunCount, PlayableEntity main) {
        if (--remainingTicks < 0) darkness = 0;
        if (darkness == 0) {
            if (count == 0) ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        if (count >= 3 || --timer >= 0) return;
        // Native $3F delay between CreateChild6_Simple allocations. Here failed admission cannot leak count.
        var body = spawnChild(() -> new SozHyudoroBodyObjectInstance(
                new ObjectSpawn(0x120, 0xA0, 0xAA, 0, 0, false, 0), this));
        if (body != null && body.getSlotIndex() >= 0 && !body.isDestroyed()) count++;
        timer = 0x3F;
    }

    public void finishEncounter() { darkness = 0; }
    @Override public int darkness() { return darkness; }
    @Override public void ghostDeleted() { count = Math.max(0, count - 1); }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
