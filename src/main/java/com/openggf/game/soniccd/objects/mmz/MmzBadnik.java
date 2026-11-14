package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;

/**
 * What the Metallic Madness badniks share: the collision byte ({@code oColType}), the 16.16 position halves
 * ({@code oXSub}/{@code oYSub}), the flip bits, {@code AnimateObject} through a stage script table,
 * {@code DestroyInGoodFuture} (common/time_check.asm:2-27) and the kill the player's touch response performs
 * (Level/_Objects, {@code Player_TouchEnemy .KillEnemy}, {@code CdObject#killAsEnemy}).
 */
abstract class MmzBadnik extends MmzObject
        implements TouchResponseProvider, TouchResponseAttackable, ObjectVisualFacing.Source {
    protected int routine;
    protected int colType;
    /** oFlags and oSprFlags bits 0-1: the status flip and the drawn flip (Object Animate.asm:36-41). */
    protected int statusFlip;
    protected int spriteFlip;
    protected CdAnimator animator;
    protected int mapFrame;
    protected boolean initialised;

    protected MmzBadnik(ObjectSpawn spawn, String name) {
        super(spawn, name);
        // SpawnObject: oSprFlags and oFlags take the layout flip bits (Object Spawner.asm:287-290).
        this.statusFlip = spawn.renderFlags() & 3;
        this.spriteFlip = this.statusFlip;
    }

    /** The body of the badnik's routine, after {@code DestroyInGoodFuture}. */
    protected abstract void run(PlayableEntity player);

    @Override
    public final void update(int vIntRunCount, PlayableEntity player) {
        if (destroyInGoodFuture()) return;
        run(player);
    }

    /**
     * {@code goodFuture}, read live from the campaign owner; the stage's period only without an owner.
     */
    protected final boolean goodFuture() {
        var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        if (tt != null) return tt.goodFuture();
        CdLevel level = cdLevel();
        return level != null && level.payload().stage().period() == CdPeriod.GOOD_FUTURE;
    }

    /** {@code timeZone} read live from the campaign owner. */
    protected final int liveTimeZone() {
        var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        return tt != null ? tt.timeZone().nativeValue() : timeZone();
    }

    /**
     * DestroyInGoodFuture (common/time_check.asm:2-27): in a good future every badnik becomes an explosion at its
     * position, except a subtype-0 badnik while the time zone is the present. The caller does not resume
     * ({@code addq.l #4,sp}).
     */
    private boolean destroyInGoodFuture() {
        if (!goodFuture()) return false;
        if (liveTimeZone() == 1 && spawn.subtype() == 0) return false;
        becomeExplosion(0, 0, 0, true);
        return true;
    }

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (isDestroyed() || player == null) return;
        killAsEnemy(player, subtype2(), true);
    }

    @Override public int getCollisionFlags() { return colType; }
    @Override public int getCollisionProperty() { return 0; }

    /** CheckBlockDown (common/object_block_collide.asm:128-160): the distance from the radius point to the floor. */
    protected final int floorDistance() {
        return com.openggf.physics.ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();
    }

    /** {@code bchg #0,oSprFlags(a0); bchg #0,oFlags(a0)}. */
    protected final void flipHorizontally() {
        spriteFlip ^= 1;
        statusFlip ^= 1;
    }

    /** AnimateObject (Level/Object Animate.asm:14-94); returns the routine increment the script asked for. */
    protected final int animate() {
        if (animator == null) return 0;
        int result = animator.step();
        mapFrame = animator.mapFrame();
        spriteFlip = (statusFlip ^ animator.flipBits()) & 3;
        return result;
    }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        return (spriteFlip & 1) != 0 ? ObjectVisualFacing.Basis.RIGHT : ObjectVisualFacing.Basis.LEFT;
    }

    public final int routine() { return routine; }
    public final int mapFrame() { return mapFrame; }
}
