package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import java.util.List;

/** One isolated Cluckoid pressure phase; frame 7..13 of byte_8E418 lasts 70 native dispatches. */
public final class NativeGustObjectInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private int remainingTicks = 70;
    private boolean sounded;

    public NativeGustObjectInstance(ObjectSpawn spawn) { super(spawn, "NativeGust"); }

    @Override public void update(int vIntRunCount, PlayableEntity main) {
        if (remainingTicks-- <= 0) { ObjectLifetimeOps.expireDynamic(this); return; }
        if (!sounded) { services().playSfx(Sonic3kSfx.ENEMY_BREATH.id); sounded = true; }
        boolean left = (spawn.renderFlags() & 1) == 0;
        CluckoidWindPressure.apply(getX(), getY(), left, main);
        var second = services().playerQuery().nativeP2OrNull();
        if (second != main) CluckoidWindPressure.apply(getX(), getY(), left, second);
    }

    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
