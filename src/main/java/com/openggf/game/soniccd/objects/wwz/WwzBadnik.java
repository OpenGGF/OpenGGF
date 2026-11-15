package com.openggf.game.soniccd.objects.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;

/**
 * What Wacky Workbench's badniks share: the collision byte ({@code oColType}), the status flip bit
 * ({@code oFlags}) beside the drawn flip, the time-zone check that turns a badnik into an explosion in a
 * good future (DestroyOnGoodFuture, Level/Object Time Check.asm:12-44, called first by every badnik),
 * and the kill the player's touch routine performs (Level/Wacky Workbench/Player Object Collision.asm:135-197:
 * the shared chain, points and explosion in the badnik's own slot).
 */
public abstract class WwzBadnik extends WwzObject
        implements TouchResponseProvider, TouchResponseAttackable, ObjectVisualFacing.Source {
    /** {@code oColType}; the badnik's init (or frame swap) sets it. */
    protected int colType;
    /** {@code oFlags} bit 0: the status flip, toggled with the drawn flip when a badnik turns. */
    protected int statusFlip;
    protected int xRadius;

    protected WwzBadnik(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.statusFlip = spawn.renderFlags() & 3;
    }

    /** The routine chain after {@code DestroyOnGoodFuture}. */
    protected abstract void run(PlayableEntity player);

    @Override
    public final void update(int vIntRunCount, PlayableEntity player) {
        if (WwzStage.nullObject(services(), getSpawn().objectId())) {
            setDestroyed(true);
            return;
        }
        if (destroyOnGoodFuture()) return;
        run(player);
    }

    /**
     * DestroyOnGoodFuture (Level/Object Time Check.asm:12-44): in a good future the badnik is deleted
     * and its slot becomes an explosion at the same position, except a subtype-0 badnik while the time zone
     * is the present. DeleteObject cleared the slot first, so the explosion has subtype 0, no layer and no
     * points (the sound test reads cleared flags and never plays; :31-38).
     */
    private boolean destroyOnGoodFuture() {
        if (!goodFuture()) return false;
        if (timeZone() == 1 && getSpawn().subtype() == 0) return false;
        becomeExplosion(0, 0, 0, true);
        return true;
    }

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (isDestroyed() || player == null) return;
        // .KillEnemy: the explosion keeps oLayer, the secondary subtype (Player Object Collision.asm:155-187).
        killAsEnemy(player, subtype2(), true);
    }

    @Override public int getCollisionFlags() { return colType; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TouchResponseProfile.fromProvider(this); }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TouchResponseProfile.fromProvider(this, multi); }

    /** {@code bchg #0,oSprFlags(a0); bchg #0,oFlags(a0)}. */
    protected final void flipHorizontally() {
        spriteFlip ^= 1;
        statusFlip ^= 1;
    }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        // The mappings face left; oSprFlags bit 0 mirrors them.
        return (spriteFlip & 1) != 0 ? ObjectVisualFacing.Basis.RIGHT : ObjectVisualFacing.Basis.LEFT;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    public final int colType() { return colType; }
}
