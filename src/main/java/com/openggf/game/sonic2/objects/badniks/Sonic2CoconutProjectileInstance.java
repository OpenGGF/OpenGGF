package com.openggf.game.sonic2.objects.badniks;

import com.openggf.game.sonic2.competition.view.Sonic2NativeDisplaySource;
import com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.Mapping;
import com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.ObjectSlot;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;

/** Obj9D_CreateCoconut (s2.asm:75366-75386): an Obj98 child, not its Obj9D parent. */
public final class Sonic2CoconutProjectileInstance extends BadnikProjectileInstance
        implements Sonic2NativeDisplaySource, com.openggf.level.objects.SpawnRewindRecreatable,
        com.openggf.level.objects.ObjectVisualFacing.NonDirectional {
    public Sonic2CoconutProjectileInstance(ObjectSpawn spawn, int x, int y,
                                         int xVelocity, int yVelocity, boolean gravity, boolean hFlip) {
        super(spawn, ProjectileType.COCONUT, x, y, xVelocity, yVelocity, gravity, hFlip);
    }

    private Sonic2CoconutProjectileInstance(ObjectSpawn spawn) {
        this(spawn, spawn.x(), spawn.y(), 0, 0, true, false);
    }

    @Override
    public Sonic2CoconutProjectileInstance recreateForRewind(RewindRecreateContext context) {
        return new Sonic2CoconutProjectileInstance(context.spawn());
    }

    @Override
    public ObjectSlot nativeDisplaySlot() {
        if (isDestroyed()) return null;
        // Obj9D_SubObjData2 (s2.asm:74860-74861) fixes flags $84; the throw does not copy facing.
        return new ObjectSlot(0x98, 0x84, getX() & 0xFFFF, getY() & 0xFFFF, 8, 0,
                new Mapping(0x37D96, 3), 0x01F7, null);
    }
}
