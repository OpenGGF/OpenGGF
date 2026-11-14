package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.physics.ObjectTerrainUtils;

import java.util.List;

/**
 * What the Quartz Quadrant objects share: the collision byte ({@code oColType}), the 16.16
 * position halves ({@code oXSub}/{@code oYSub}), the status and drawn flip bits, {@code AnimateObject}
 * through a stage script table, {@code DestroyInGoodFuture}, and drawing through a catalogue sheet.
 * Citations name the routine in {@code src/objects/r5/<file>.asm}, which every Quartz Quadrant stage
 * assembles in both source dialects.
 *
 * <p>A routine that does not reach {@code DrawObject} on a frame (an init that returns, a deleted
 * slot) clears {@link #drawThisFrame}; the sprite is drawn only on frames that called it, and its
 * on-screen bit is the shared retained flag.
 *
 * <p>Names follow the R5 compatibility aliases (Level/Quartz Quadrant/R5 Compatibility.asm:17-60):
 * {@code obj.width} is {@code oXRadius}, {@code obj.width_2} is {@code oWidth}, {@code obj.height} is
 * {@code oYRadius}, {@code obj.sprite_layer} is {@code oPriority}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
abstract class QqzObject extends CdObject implements TouchResponseProvider, ObjectVisualFacing.Source {
    /** oRoutine and oRoutine2 (a byte stepping by 2). */
    protected int routine;
    protected int routine2;
    /** oColType; every object sets it in its own init, and a hit may clear it. */
    protected int colType;
    /** oFlags bits 0-1 and oSprFlags bits 0-1: the status flip and the drawn flip (Object Animate.asm:36-41). */
    protected int statusFlip;
    protected int spriteFlip;
    /** oXSub and oYSub, the low words of oX and oY. */
    protected int xSub;
    protected int ySub;
    protected int xRadius;
    protected CdAnimator animator;
    /** The catalogue column of the object's script table and {@code oAnim}; the animator is made when art is loaded. */
    protected String animColumn;
    protected int animId;
    protected int mapFrame;
    /** True once the init body has run: {@code oMap} is set, so DrawObject can draw. */
    protected boolean initialised;
    /** False on frames where the routine does not call DrawObject. */
    protected boolean drawThisFrame = true;
    /** True on frames where the routine returns before its CheckObjectDespawn (an init that returns). */
    protected boolean skipDespawnThisFrame;
    /** {@code oSubtype2} for objects a routine made (they are not in the layout); -1 reads the layout. */
    private final int fixedSubtype2;

    protected QqzObject(ObjectSpawn spawn, String name) {
        this(spawn, name, -1);
    }

    protected QqzObject(ObjectSpawn spawn, String name, int fixedSubtype2) {
        super(spawn, name);
        this.fixedSubtype2 = fixedSubtype2;
        // SpawnObject: move.b d1,oSprFlags(a1); move.b d1,oFlags(a1) (Object Spawner.asm:287-290).
        this.statusFlip = spawn.renderFlags() & 3;
        this.spriteFlip = this.statusFlip;
    }

    /** One frame of the object's top-level routine. */
    protected abstract void run(PlayableEntity player);

    @Override
    public final void update(int vIntRunCount, PlayableEntity player) {
        drawThisFrame = true;
        skipDespawnThisFrame = false;
        run(player);
    }


    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return !skipDespawnThisFrame && super.isCustomOutOfRange(cameraX);
    }

    /** {@code oSubtype2}: the layout entry's, or the value the creating routine wrote. */
    protected final int sub2() {
        return fixedSubtype2 >= 0 ? fixedSubtype2 : subtype2();
    }

    // ---- time ----------------------------------------------------------------------------------------

    /**
     * {@code timeZone} (0 past, 1 present, 2 future), read live from the campaign owner
     * ({@link com.openggf.game.soniccd.timetravel.CdTimeTravel}); the loaded stage's period only
     * when no owner is present.
     */
    protected final int liveTimeZone() {
        var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        if (tt != null) return tt.timeZone().nativeValue();
        CdLevel level = cdLevel();
        return level == null ? 1 : CdLevel.nativeTimeZone(level.payload().stage().period());
    }

    /** {@code goodFuture}, read live from the campaign owner; the stage's period only without one. */
    protected final boolean liveGoodFuture() {
        var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        if (tt != null) return tt.goodFuture();
        CdLevel level = cdLevel();
        return level != null && level.payload().stage().period() == CdPeriod.GOOD_FUTURE;
    }

    /**
     * DestroyInGoodFuture (common/time_check.asm:3-27; Level/Object Time Check.asm:12-44): in a good future
     * the object is deleted and its slot becomes an explosion at the same position, except a subtype-0
     * object while the time zone is the present. DeleteObject cleared the slot first, so the explosion has
     * subtype 0, no layer and oVar3E 0; the sound test then reads the cleared flags and never plays.
     * Returning pops the object's own routine ({@code addq.l #4,sp}).
     */
    protected final boolean destroyInGoodFuture() {
        if (!liveGoodFuture()) return false;
        if (liveTimeZone() == 1 && spawn.subtype() == 0) return false;
        becomeExplosion(0, 0, 0, true);
        return true;
    }

    // ---- stage -----------------------------------------------------------------------------------------

    /**
     * Act 3 (R53C, R53D) links the boss object index (r5/object_index_3.asm): IDs $20-$23 are the boss sparks,
     * spike wall, belt and explosion, and the other IDs of this lane ($25, $27, $29, $2B, $2F, $31, $32) are
     * {@code ObjNull}, which clears its slot ({@code move.b #0,(a0)}, r5/object_index_1_2.asm ObjNull). The
     * registry resolves by zone, so this lane's objects delete themselves for a null ID and stand down for
     * a boss ID until the boss lane registers it. True where the object must do nothing this frame.
     */
    protected final boolean standDownInBossAct() {
        CdLevel level = cdLevel();
        if (level == null || !level.payload().name().startsWith("R53")) return false;
        drawThisFrame = false;
        if (spawn.objectId() < 0x20 || spawn.objectId() > 0x23) deleteObject();
        return true;
    }

    // ---- movement helpers ---------------------------------------------------------------------------------

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

    /** {@code move.l d0,oX(a0)}. */
    protected final void setXLong(int position) {
        x = position >>> 16;
        xSub = position & 0xFFFF;
    }

    /** {@code move.l d0,oY(a0)}. */
    protected final void setYLong(int position) {
        y = position >>> 16;
        ySub = position & 0xFFFF;
    }

    /** {@code move.l oX(a0),d0}. */
    protected final int xLong() {
        return (x & 0xFFFF) << 16 | xSub;
    }

    /** {@code move.l oY(a0),d0}. */
    protected final int yLong() {
        return (y & 0xFFFF) << 16 | ySub;
    }

    /** {@code CheckBlockDown} = {@code ObjGetFloorDist} (Level/Collision Check.asm:157-175; R5 Compatibility.asm:370). */
    protected final int floorDistance() {
        return ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();
    }

    /** {@code bchg #0,oSprFlags(a0); bchg #0,oFlags(a0)} (the turn-around). */
    protected final void flipHorizontally() {
        spriteFlip ^= 1;
        statusFlip ^= 1;
    }

    /** AnimateObject (common/object_animate.asm; Level/Object Animate.asm:14-94) with its routine side effects. */
    protected final void animate() {
        if (animator == null && animColumn != null) {
            animator = QqzObjectArt.animator(artProvider(), animColumn, animId);
        }
        if (animator == null) return;
        animator.anim = animId;                   // move.b #n,oAnim(a0)
        int result = animator.step();
        animId = animator.anim;                   // $FD: the script sets oAnim
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

    /** {@code jsr DeleteObject}: the slot is cleared. */
    protected final void deleteObject() {
        setDestroyed(true);
    }

    // ---- player helpers (all values as 16-bit words) ---------------------------------------------------

    /** |a - b| of two unsigned words, the {@code sub.w; bcc; neg.w} idiom. */
    protected static int absDiff(int a, int b) {
        int d = ((a & 0xFFFF) - (b & 0xFFFF)) & 0xFFFF;
        return (a & 0xFFFF) >= (b & 0xFFFF) ? d : (-d) & 0xFFFF;
    }

    /** True where {@code (value + bias) - size} borrows: {@code (value + bias) & $FFFF < size}. */
    protected static boolean inWindow(int value, int bias, int size) {
        return ((value + bias) & 0xFFFF) < size;
    }

    // ---- touch -----------------------------------------------------------------------------------------

    @Override public int getCollisionFlags() { return initialised ? colType : 0; }
    @Override public int getCollisionProperty() { return 0; }

    /**
     * Sonic CD's {@code $C0} collision types ({@code $C0 | $2E}, {@code $3C}, {@code $37}) are touch counters,
     * not Sonic 1's boss type: {@code Player_ObjectCollide} sends them to {@code loc_20726E} or
     * {@code loc_207226}, which only add to {@code oColStatus} (src/r5/player_object_collide.asm:loc_207186,
     * 252-284), with no hurt and no bounce. The shared touch owner's listener-only decode for {@code $C0} flags
     * is selected by this switch (its S3K name is only the mode's label).
     */
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return (getCollisionFlags() & 0xC0) == 0xC0; }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        // The mappings face left; oSprFlags bit 0 mirrors them (unverified for the Quartz Quadrant art).
        return (spriteFlip & 1) != 0 ? ObjectVisualFacing.Basis.RIGHT : ObjectVisualFacing.Basis.LEFT;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    // ---- state for tests and tooling ---------------------------------------------------------------------

    public final int routine() { return routine; }
    public final int priority() { return priority; }
    public final int mapFrame() { return mapFrame; }
    public final int spriteFlip() { return spriteFlip; }
    public final int xSubpixel() { return xSub; }
    public final int ySubpixel() { return ySub; }

    // ---- drawing -----------------------------------------------------------------------------------------

    /** The sheet kind this object draws now; {@code null} draws nothing. */
    protected abstract String sheetKind();

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised || !drawThisFrame || isDestroyed()) return;
        String kind = sheetKind();
        CdObjectArtProvider provider = artProvider();
        if (kind == null || provider == null) return;
        String key = QqzObjectArt.key(provider, kind, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }

    /** {@code obj.x} of the player: the centre X as a word. */
    protected static int centreX(PlayableEntity player) {
        return player.getCentreX() & 0xFFFF;
    }

    /** {@code obj.y} of the player: the centre Y as a word. */
    protected static int centreY(PlayableEntity player) {
        return player.getCentreY() & 0xFFFF;
    }
}
