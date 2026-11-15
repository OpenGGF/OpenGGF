package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;

import java.util.List;

/**
 * What Tidal Tempest's badniks share, as the Palmtree Panic owner's badniks do (the same Sonic CD routines):
 * the collision byte, the 16.16 position halves, the flip bits, {@code AnimateObject} over a stage script
 * table, {@code DestroyInGoodFuture} (an alias of DestroyOnGoodFuture, R4 Compatibility.asm:211) and the kill
 * that {@code Player_TouchEnemy .KillEnemy} performs ({@link #killAsEnemy}). The player-side decisions are the
 * shared touch response's.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
abstract class TtzBadnik extends TtzObject
        implements TouchResponseProvider, TouchResponseAttackable, ObjectVisualFacing.Source {
    protected int routine;
    /** oRoutine2, which is oExplodeBadnik for the explosion (Level/_Objects/Explosion/Main.asm:7). */
    protected int routine2;
    protected int colType;
    protected int statusFlip;
    protected int spriteFlip;
    protected int xSub;
    protected int ySub;
    protected CdAnimator animator;
    protected int mapFrame;
    protected boolean initialised;

    protected TtzBadnik(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.statusFlip = spawn.renderFlags() & 3;
        this.spriteFlip = statusFlip;
    }

    /** The routine chain after {@code DestroyInGoodFuture}. */
    protected abstract void run(PlayableEntity player);

    /** The sheet kind this badnik draws (a {@link TtzObjectArt} kind). */
    protected abstract String sheetKind();

    @Override
    public final void update(int vIntRunCount, PlayableEntity player) {
        if (artProvider() == null) return;
        if (destroyOnGoodFuture()) return;
        run(player);
    }

    private CdLevel level() {
        var levels = services().levelManager();
        return levels != null && levels.getCurrentLevel() instanceof CdLevel cd ? cd : null;
    }

    protected final int badnikTimeZone() {
        var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        if (tt != null) return tt.timeZone().nativeValue();
        CdLevel level = level();
        return level == null ? 1 : CdLevel.nativeTimeZone(level.payload().stage().period());
    }

    protected final boolean goodFuture() {
        var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        if (tt != null) return tt.goodFuture();
        CdLevel level = level();
        return level != null && level.payload().stage().period() == CdPeriod.GOOD_FUTURE;
    }

    /**
     * DestroyOnGoodFuture (Level/Object Time Check.asm:12-44): in a good future every badnik is deleted and its
     * slot becomes an explosion at the same position, except a subtype-0 badnik while the time zone is the
     * present; returning pops the badnik's own routine.
     */
    private boolean destroyOnGoodFuture() {
        if (!goodFuture()) return false;
        if (badnikTimeZone() == 1 && spawn.subtype() == 0) return false;
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

    /** {@code move.l d0,oY(a0)}. */
    protected final void setY(int position) {
        y = position >>> 16;
        ySub = position & 0xFFFF;
    }

    protected final void flipHorizontally() {
        spriteFlip ^= 1;
        statusFlip ^= 1;
    }

    /** AnimateObject with its routine side effects (Level/Object Animate.asm:14-94). */
    protected final void animate() {
        if (animator == null) return;
        int result = animator.step();
        if (result == 2) routine += 2;
        else if (result == -1) routine2 = 0;
        else if (result == -2) routine2 += 2;
        mapFrame = animator.mapFrame();
        spriteFlip = (statusFlip ^ animator.flipBits()) & 3;
    }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        return (spriteFlip & 1) != 0 ? ObjectVisualFacing.Basis.RIGHT : ObjectVisualFacing.Basis.LEFT;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    public final int routine() { return routine; }
    public final int mapFrame() { return mapFrame; }
    public final int spriteFlip() { return spriteFlip; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        var provider = artProvider();
        if (provider == null) return;
        var camera = services().camera();
        String key = TtzObjectArt.key(provider, sheetKind(), tileWord, camera == null ? 0 : camera.getX());
        if (key != null) drawFrame(key, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }
}
