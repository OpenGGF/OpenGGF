package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseResult;

/**
 * A Quartz Quadrant object with enemy collision (type bits 0, size {@code $2F}-{@code $35}): an attack
 * by a rolling or invincible player kills it as Sonic CD's {@code Player_TouchEnemy .KillEnemy} does
 * (src/r5/player_object_collide.asm:sub_206FA6, 83-124): the slot becomes an explosion ({@code $18},
 * routine 0, subtype 1) keeping {@code oLayer}, and the shared kill chain scores. The decision to attack
 * or to hurt is the shared touch response's (Sonic 1's React_Enemy). Shipped {@code FixBugs = 0}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
abstract class QqzBadnik extends QqzObject implements TouchResponseAttackable {
    protected QqzBadnik(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    protected QqzBadnik(ObjectSpawn spawn, String name, int subtype2) {
        super(spawn, name, subtype2);
    }

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (isDestroyed() || player == null) return;
        // .KillEnemy: oLayer (the secondary subtype) stays; oExplodeBadnik is oRoutine2.
        killAsEnemy(player, sub2(), routine2 == 0);
    }
}
