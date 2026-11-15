package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.level.objects.ObjectSpawn;

/**
 * What the Collision Chaos objects share on top of {@link CdObject}: the routine byte, the 16.16 position
 * halves ({@code oXSub}/{@code oYSub}), the status and sprite flip bits (the spawner copies the layout's flip
 * bits into both: SpawnObject, Object Spawner.asm:287-290), {@code AnimateObject} through a stage script
 * table, and the time-zone reads the badniks make.
 */
public abstract class CczObject extends CdObject {
    /** {@code oRoutine}, stepping by 2. */
    protected int routine;
    /** {@code oXSub} and {@code oYSub}, the low words of {@code oX} and {@code oY}. */
    protected int xSub;
    protected int ySub;
    /** {@code oFlags} bits 0-1 and {@code oSprFlags} bits 0-1. */
    protected int statusFlip;
    protected int spriteFlip;
    protected int mapFrame;
    /** {@code oColType}. */
    protected int colType;
    protected CdAnimator animator;

    protected CczObject(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.statusFlip = spawn.renderFlags() & 3;
        this.spriteFlip = this.statusFlip;
    }

    /** {@code add.l d0,oX(a0)} with a 16.16 velocity. */
    protected final void addToX(int velocity) {
        int position = ((x & 0xFFFF) << 16 | xSub) + velocity;
        x = position >>> 16;
        xSub = position & 0xFFFF;
    }

    /** {@code add.l d0,oY(a0)} with a 16.16 velocity. */
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

    /** {@code oY.l}. */
    protected final int yLong() {
        return (y & 0xFFFF) << 16 | ySub;
    }

    /** {@code oX.l}. */
    protected final int xLong() {
        return (x & 0xFFFF) << 16 | xSub;
    }

    /** {@code move.w oXVel,d0; ext.l d0; asl.l #8,d0; add.l d0,oX(a0)} (8.8 velocity). */
    protected final void moveX(int velocity8) {
        addToX((short) velocity8 << 8);
    }

    /** The same for oY. */
    protected final void moveY(int velocity8) {
        addToY((short) velocity8 << 8);
    }

    /** {@code bchg #0,oSprFlags(a0); bchg #0,oFlags(a0)}. */
    protected final void flipHorizontally() {
        spriteFlip ^= 1;
        statusFlip ^= 1;
    }

    /** {@code bset #0,oSprFlags(a0); bset #0,oFlags(a0)} or the matching {@code bclr}. */
    protected final void setFacingRight(boolean right) {
        if (right) {
            spriteFlip |= 1;
            statusFlip |= 1;
        } else {
            spriteFlip &= ~1;
            statusFlip &= ~1;
        }
    }

    /** One AnimateObject call (Object Animate.asm:14-94) with its routine side effects. */
    protected final void animate() {
        if (animator == null) return;
        int result = animator.step();
        if (result == 2) routine += 2;                       // $FC: addq.b #2,oRoutine
        mapFrame = animator.mapFrame();
        spriteFlip = (statusFlip ^ animator.flipBits()) & 3;
    }

    /** Draws frame {@code frame} of the Collision Chaos sheet {@code kind} at the object's tile word, flipped by the sprite flags. */
    protected final void drawSheet(String kind, int frame) {
        if (routine == 0) return;
        CdObjectArtProvider provider = artProvider();
        if (provider == null) return;
        String key = CczObjectArt.key(provider, kind, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, frame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }

    /** The art owner, or {@code null} before the stage's art exists. */
    protected final CdObjectArtProvider provider() {
        return artProvider();
    }

    /**
     * {@code timeZone} (0 past, 1 present, 2 future), read live from the campaign owner
     * ({@link com.openggf.game.soniccd.timetravel.CdTimeTravel}); the loaded stage's period only when no owner exists.
     */
    @Override
    protected final int timeZone() {
        var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        if (tt != null) return tt.timeZone().nativeValue();
        CdLevel level = cdLevel();
        return level == null ? 1 : CdLevel.nativeTimeZone(level.payload().stage().period());
    }

    /** {@code goodFuture}, read live from the campaign owner; the stage's period only without an owner. */
    protected final boolean goodFuture() {
        var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        if (tt != null) return tt.goodFuture();
        CdLevel level = cdLevel();
        return level != null && level.payload().stage().period() == CdPeriod.GOOD_FUTURE;
    }

    /**
     * DestroyOnGoodFuture (Level/Object Time Check.asm:12-44): in a good future every badnik is deleted and its
     * slot becomes an explosion at the same position, except a subtype-0 badnik while the time zone is the
     * present. DeleteObject cleared the slot first, so the explosion has subtype 0, no layer and oVar3E 0; the
     * {@code oSprFlags} sound test then reads the cleared flags and never plays (:31-38). Returning true pops
     * the badnik's own routine ({@code addq.l #4,sp}).
     */
    protected final boolean destroyOnGoodFuture() {
        if (!goodFuture()) return false;
        if (timeZone() == 1 && spawn.subtype() == 0) return false;
        becomeExplosion(0, 0, 0, true);
        return true;
    }

    /**
     * The legacy objects' own despawn test (for example Metal Platform/Main.asm:96-109): the chunk distance of
     * {@code objX} from the camera, {@code (objX & $FF80) - ((cameraX - $80) & $FF80)}, unsigned above $280, with no
     * on-screen exemption.
     */
    protected static boolean outsideChunkRange(int objX, int cameraX) {
        int chunk = objX & 0xFF80;
        int cameraChunk = (cameraX - 0x80) & 0xFF80;
        return ((chunk - cameraChunk) & 0xFFFF) > 0x280;
    }

    public final int routine() { return routine; }
    public final int mapFrame() { return mapFrame; }
    public final int spriteFlip() { return spriteFlip; }
    public final int xSubpixel() { return xSub; }
    public final int ySubpixel() { return ySub; }
    public final int xPosition() { return x; }
    public final int yPosition() { return y; }
}
