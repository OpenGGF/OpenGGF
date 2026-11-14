package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;

/**
 * What Collision Chaos' enemies share: the collision byte ({@code oColType}), the kill that Sonic CD's player routine
 * performs ({@code Player_TouchEnemy .KillEnemy}, Level/Palmtree Panic/Player Object Collision.asm:155-197: the slot
 * becomes an explosion that keeps {@code oLayer}) and CheckObjectDespawn. The mappings face left; {@code oSprFlags}
 * bit 0 mirrors them.
 */
abstract class CczBadnik extends CczObject implements TouchResponseProvider, TouchResponseAttackable,
        ObjectVisualFacing.Source {
    protected CczBadnik(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (isDestroyed() || player == null) return;
        killAsEnemy(player, subtype2(), true);
    }

    @Override public int getCollisionFlags() { return colType; }
    @Override public int getCollisionProperty() { return 0; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        return (spriteFlip & 1) != 0 ? ObjectVisualFacing.Basis.RIGHT : ObjectVisualFacing.Basis.LEFT;
    }
}
