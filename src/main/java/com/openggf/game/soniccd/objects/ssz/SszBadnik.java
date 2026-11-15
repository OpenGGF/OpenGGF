package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;

/**
 * What Stardust Speedway's two badniks share: the collision byte ({@code oColType}), the time-zone check
 * that turns a badnik into an explosion in a good future, and the kill that Sonic CD's player routine
 * performs. {@code DestroyInGoodFuture} (common/time_check.asm:3-31) deletes the badnik and rebuilds the
 * slot as explosion $18 at the same position unless the time zone is the present and the subtype is 0;
 * DeleteObject cleared the slot first, so the explosion has subtype 0, no layer and no points, and the
 * {@code oSprFlags} sound test reads the cleared flags and never plays (:20-29). Kill, bounce and the
 * hurt-or-attack decision are the shared touch owner's ({@link TouchResponseProvider}); the kill is
 * {@code Player_TouchEnemy .KillEnemy} (see {@code CdObject.killAsEnemy}).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
abstract class SszBadnik extends SszObject implements TouchResponseProvider, TouchResponseAttackable,
        ObjectVisualFacing.Source {
    /** oColType; each badnik sets it in its own routine. */
    protected int colType;
    /** oRoutine2 (oExplodeBadnik, Explosion/Main.asm:7). */
    protected int routine2;

    protected SszBadnik(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    /** The loaded stage's good-future flag, read live from the campaign owner. */
    protected final boolean liveGoodFuture() {
        CdTimeTravel tt = CdTimeTravel.of(services().gameModule());
        if (tt != null) return tt.goodFuture();
        CdLevel level = cdLevel();
        return level != null && level.payload().stage().period() == CdPeriod.GOOD_FUTURE;
    }

    /**
     * DestroyInGoodFuture (common/time_check.asm:3-31): true when the object has just become an explosion,
     * which pops the caller's return address ({@code addq.l #4,sp}) so the badnik's own routine is skipped.
     */
    protected final boolean destroyInGoodFuture() {
        if (!liveGoodFuture()) return false;                 // tst.b good_future; beq.s .Return
        if (liveTimeZone() == 1 && spawn.subtype() == 0) return false;   // cmpi.b #1,time_zone; ... tst.b obj.subtype(a0); beq.s .Return
        becomeExplosion(0, 0, 0, true);
        return true;
    }

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (isDestroyed() || player == null) return;
        killAsEnemy(player, subtype2(), routine2 == 0);
    }

    @Override public int getCollisionFlags() { return colType; }
    @Override public int getCollisionProperty() { return 0; }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        return (spriteFlip & 1) != 0 ? ObjectVisualFacing.Basis.RIGHT : ObjectVisualFacing.Basis.LEFT;
    }
}
