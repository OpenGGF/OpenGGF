package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.ShieldType;
import com.openggf.level.objects.ObjectSpawn;

/**
 * SKL {@code $6D}, {@code Obj_InvisibleShockBlock} (sonic3k.asm:43299-43301).
 * The object sets shield-reaction bit 5 and then falls directly into the shared horizontal
 * invisible-hurt-block routine, making only the lightning shield immune.
 */
public final class Sonic3kInvisibleShockBlockObjectInstance
        extends Sonic3kInvisibleHurtBlockHObjectInstance {
    public Sonic3kInvisibleShockBlockObjectInstance(ObjectSpawn spawn) {
        super(spawn);
    }

    @Override
    protected boolean isShieldImmune(PlayableEntity playerEntity) {
        return playerEntity.hasShield() && playerEntity.getShieldType() == ShieldType.LIGHTNING;
    }
}
