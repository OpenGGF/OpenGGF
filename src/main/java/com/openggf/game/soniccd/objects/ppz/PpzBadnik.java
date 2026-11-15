package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.ObjectTerrainUtils;

import java.util.List;

/**
 * What Palmtree Panic's badniks share: the collision byte ({@code oColType}), the 16.16 position
 * halves ({@code oXSub}/{@code oYSub}), the flip bits, {@code AnimateObject} through a stage
 * script table, the time-zone check that turns a badnik into an explosion in a good future, and
 * the kill that Sonic CD's player routine performs.
 *
 * <p><b>Kill</b> ({@code Player_TouchEnemy .KillEnemy}, Level/Palmtree Panic/Player Object
 * Collision.asm:155-197): the badnik's own slot becomes an explosion ({@code $18}, routine 0,
 * subtype 1) and keeps {@code oLayer}; the kill count is the shared chain (100, 200, 500, 1000,
 * then 10000 from the sixteenth, {@code EnemyPoints} times {@code AddPoints}'s 10) and
 * {@code oVar3E} is its table index (0, 2, 4, 6, or $A) which the explosion turns into the points
 * sprite. The explosion's flower and animal belong to other lanes. {@code FM_DESTROY} is not hosted
 * (the FM effect driver has no object-facing API yet).
 *
 * <p>The player-side decisions (attack by rolling or invincibility, hurt otherwise, bounce) are the
 * shared touch response's, which are Sonic 1's React_Enemy in Sonic CD (Player Object Collision.asm:130-197).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
abstract class PpzBadnik extends CdObject
        implements TouchResponseProvider, TouchResponseAttackable, ObjectVisualFacing.Source {
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
    protected int xRadius;
    private boolean artReady;
    /** True once the init body has run: {@code oMap} is set, so DrawObject can draw. */
    protected boolean initialised;

    protected PpzBadnik(ObjectSpawn spawn, String name) {
        super(spawn, name);
        // SpawnObject: move.b d1,oSprFlags(a1); move.b d1,oFlags(a1) (Object Spawner.asm:287-290).
        this.statusFlip = spawn.renderFlags() & 3;
        this.spriteFlip = this.statusFlip;
    }

    /** The SetObjectTileID index of this badnik (Object Tile ID Set.asm:7-17). */
    protected abstract int tileIdIndex();

    /** Renderer kind of the current sheet (CdObjectArtProvider.PPZ_*). */
    protected abstract String sheetKind();

    /** The routine chain after {@code DestroyOnGoodFuture}. */
    protected abstract void run(PlayableEntity player);

    @Override
    public final void update(int vIntRunCount, PlayableEntity player) {
        if (!artReady) {
            CdObjectArtProvider provider = artProvider();
            if (provider == null) return;
            tileWord = provider.objectTileId(tileIdIndex(), subtype2());
            artReady = true;
        }
        if (destroyOnGoodFuture()) return;
        run(player);
    }

    // ---- time zone ---------------------------------------------------------------------------------

    private CdLevel level() {
        var levels = services().levelManager();
        return levels != null && levels.getCurrentLevel() instanceof CdLevel cd ? cd : null;
    }

    /**
     * {@code timeZone} (0 past, 1 present, 2 future), read live from the campaign owner
     * ({@link com.openggf.game.soniccd.timetravel.CdTimeTravel}); the loaded stage's period
     * only when no owner is present.
     */
    protected final int timeZone() {
        var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        if (tt != null) return tt.timeZone().nativeValue();
        CdLevel level = level();
        return level == null ? 1 : CdLevel.nativeTimeZone(level.payload().stage().period());
    }

    /**
     * {@code goodFuture}, read live from the campaign owner: set by a destroyed robot generator,
     * all Time Stones, or a good-future stage entry; the stage's period only without an owner.
     */
    protected final boolean goodFuture() {
        var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        if (tt != null) return tt.goodFuture();
        CdLevel level = level();
        return level != null && level.payload().stage().period() == CdPeriod.GOOD_FUTURE;
    }

    /**
     * DestroyOnGoodFuture (Level/Object Time Check.asm:12-44): in a good future every badnik is deleted
     * and its slot becomes an explosion at the same position, except a subtype-0 badnik while the time
     * zone is the present. DeleteObject cleared the slot first, so the explosion has subtype 0, no layer
     * and oVar3E 0 (a points sprite for the first score, $80+0); the {@code oSprFlags} sound test then
     * reads the cleared flags and never plays (Object Time Check.asm:31-38). Returning pops the
     * badnik's own routine ({@code addq.l #4,sp}).
     */
    private boolean destroyOnGoodFuture() {
        if (!goodFuture()) return false;
        if (timeZone() == 1 && spawn.subtype() == 0) return false;
        becomeExplosion(0, 0, 0, true);
        return true;
    }

    // ---- kill --------------------------------------------------------------------------------------

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (isDestroyed() || player == null) return;
        // .KillEnemy (Player Object Collision.asm:155-187): oLayer (the secondary subtype) stays.
        killAsEnemy(player, subtype2(), routine2 == 0);
    }

    // ---- collision ---------------------------------------------------------------------------------

    @Override public int getCollisionFlags() { return colType; }
    @Override public int getCollisionProperty() { return 0; }

    // ---- movement helpers ---------------------------------------------------------------------------

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

    /** {@code move.l d0,oY(a0)}. */
    protected final void setY(int position) {
        y = position >>> 16;
        ySub = position & 0xFFFF;
    }

    /** ObjGetFloorDist (Level/Collision Check.asm:157-175): the distance below the radius point. */
    protected final int floorDistance() {
        return ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();
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

    /** {@code tst.b oSprFlags(a0)} bit 7: on screen at the last DrawObjects. */
    protected final boolean onScreen() {
        return renderFlagOnScreen();
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        // The mappings face left; oSprFlags bit 0 mirrors them.
        return (spriteFlip & 1) != 0 ? ObjectVisualFacing.Basis.RIGHT : ObjectVisualFacing.Basis.LEFT;
    }

    // ---- state for tests and tooling -------------------------------------------------------------

    public final int routine() { return routine; }
    public final int mapFrame() { return mapFrame; }
    public final int spriteFlip() { return spriteFlip; }
    public final int xSubpixel() { return xSub; }
    public final int ySubpixel() { return ySub; }

    // ---- drawing -----------------------------------------------------------------------------------

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised || !artReady) return;
        CdObjectArtProvider provider = artProvider();
        if (provider == null) return;
        String key = PpzObjectArt.key(provider, sheetKind(), tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }
}
