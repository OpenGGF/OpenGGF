package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;

import java.util.List;

/**
 * What the shared badniks (several zones, not all) have in common: the collision byte
 * ({@code oColType}), the 16.16 position halves, the flip bits, {@code AnimateObject} over a table
 * from the shared catalogue, {@code DestroyInGoodFuture} and the kill the player routine performs.
 *
 * <p>{@code DestroyInGoodFuture} (common/time_check.asm:3-31) deletes the badnik and rebuilds the slot
 * as explosion $18 at the same position unless the time zone is the present and the subtype is 0;
 * DeleteObject cleared the slot first, so the explosion has subtype 0, no layer and no points, and the
 * {@code oSprFlags} sound test reads the cleared flags and never plays (:20-29). The kill is
 * {@code Player_TouchEnemy .KillEnemy} ({@code CdObject.killAsEnemy}); the attack-or-hurt decision and
 * the bounce are the shared touch owner's.
 */
abstract class SharedBadnik extends CdObject implements TouchResponseProvider, TouchResponseAttackable,
        ObjectVisualFacing.Source {
    /** oRoutine (a byte stepping by 2) and oRoutine2 (oExplodeBadnik, Explosion/Main.asm:7). */
    protected int routine;
    protected int routine2;
    /** oColType; every badnik sets it in its own init. */
    protected int colType;
    /** oFlags bits 0-1 and oSprFlags bits 0-1: the status flip and the drawn flip (Object Animate.asm:36-41). */
    protected int statusFlip;
    protected int spriteFlip;
    /** oXSub and oYSub, the low words of oX and oY. */
    protected int xSub;
    protected int ySub;
    protected CdAnimator animator;
    protected int mapFrame;
    protected boolean initialised;

    protected SharedBadnik(ObjectSpawn spawn, String name) {
        super(spawn, name);
        // SpawnObject: move.b d1,oSprFlags(a1); move.b d1,oFlags(a1) (Object Spawner.asm:287-290).
        this.statusFlip = spawn.renderFlags() & 3;
        this.spriteFlip = this.statusFlip;
    }

    /** The routine chain after {@code DestroyInGoodFuture}. */
    protected abstract void run(PlayableEntity player);

    /** Whether the stage's art is loaded and the badnik may run; the first call may read the catalogue. */
    protected boolean ready() {
        return artProvider() != null && cdLevel() != null;
    }

    /** The key of the sheet the current frame is drawn from. */
    protected abstract String sheetKey();

    @Override
    public final void update(int vIntRunCount, PlayableEntity player) {
        if (!ready()) return;
        if (destroyInGoodFuture()) return;
        run(player);
    }

    /** {@code timeZone} read live from the campaign owner; the loaded stage's period only without one. */
    protected final int liveTimeZone() {
        CdTimeTravel tt = CdTimeTravel.of(services().gameModule());
        if (tt != null) return tt.timeZone().nativeValue();
        CdLevel level = cdLevel();
        return level == null ? 1 : CdLevel.nativeTimeZone(level.payload().stage().period());
    }

    /** {@code goodFuture} read live from the campaign owner; the stage's period only without one. */
    protected final boolean liveGoodFuture() {
        CdTimeTravel tt = CdTimeTravel.of(services().gameModule());
        if (tt != null) return tt.goodFuture();
        CdLevel level = cdLevel();
        return level != null && level.payload().stage().period() == CdPeriod.GOOD_FUTURE;
    }

    /**
     * DestroyInGoodFuture (common/time_check.asm:3-31): true when the object has just become an
     * explosion, which pops the caller's return address ({@code addq.l #4,sp}).
     */
    private boolean destroyInGoodFuture() {
        if (!liveGoodFuture()) return false;
        if (liveTimeZone() == 1 && spawn.subtype() == 0) return false;
        becomeExplosion(0, 0, 0, true);
        return true;
    }

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (isDestroyed() || player == null) return;
        killAsEnemy(player, subtype2(), routine2 == 0);
    }

    @Override public int getCollisionFlags() { return initialised ? colType : 0; }
    @Override public int getCollisionProperty() { return 0; }

    /** {@code add.l d0,oX(a0)}. */
    protected final void addToX(int velocity) {
        int position = ((x & 0xFFFF) << 16 | xSub) + velocity;
        x = position >>> 16;
        xSub = position & 0xFFFF;
    }

    /** {@code add.l d0,oY(a0)}. */
    protected final void addToY(int velocity) {
        int position = ((y & 0xFFFF) << 16 | ySub) + velocity;
        y = position >>> 16;
        ySub = position & 0xFFFF;
    }

    /** {@code bchg #0,oSprFlags(a0); bchg #0,oFlags(a0)} (the badniks' turn-around). */
    protected final void flipHorizontally() {
        spriteFlip ^= 1;
        statusFlip ^= 1;
    }

    /** AnimateObject (Level/Object Animate.asm:14-94) with its routine side effects. */
    protected final void animate() {
        if (animator == null) return;
        int result = animator.step();
        if (result == 2) routine += 2;            // $FC: addq.b #2,oRoutine
        else if (result == -1) routine2 = 0;      // $FB: clr.b oRoutine2
        else if (result == -2) routine2 += 2;     // $FA: addq.b #2,oRoutine2
        mapFrame = animator.mapFrame();
        spriteFlip = (statusFlip ^ animator.flipBits()) & 3;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        // The mappings face left; oSprFlags bit 0 mirrors them.
        return (spriteFlip & 1) != 0 ? ObjectVisualFacing.Basis.RIGHT : ObjectVisualFacing.Basis.LEFT;
    }

    public final int routine() { return routine; }
    public final int mapFrame() { return mapFrame; }
    public final int spriteFlip() { return spriteFlip; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        String key = sheetKey();
        if (key != null) drawFrame(key, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }
}
