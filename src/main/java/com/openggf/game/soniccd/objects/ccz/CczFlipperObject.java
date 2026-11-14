package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdSolidRules;
import com.openggf.game.soniccd.objects.common.CdStandOnObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Pinball flipper, object {@code $1E} ({@code ObjFlipper}, Level/Collision Chaos/Objects/Bumper and Flipper/Main.asm:119-232;
 * {@code FlipperObject} in objects/r3/bumper_flipper.asm). A $20 wide, $10 high sloped platform (tile {@code $49D}, priority 1).
 * While its animation is 0 it tests the player itself ({@code ObjFlipper_CheckPlayer}, not SolidObject): within its width
 * (unless the player is in the boredom animation {@code $2B}) the surface is {@code oY} plus a byte of
 * {@code FlipperTopSlope} (or {@code FlipperBottomSlope} when the player moves up) at the player's column from the left
 * edge ($40 minus it when flipped); when the player's feet (head) are within 8 above to 8 below that surface he is placed
 * on it and stands on the object ({@code StandOnObject}), or, moving up, loses his Y speed and takes his X speed as
 * ground speed; otherwise {@code GetOffObject}. The player's jump routine launches from it ({@link CczFlipperLaunch}),
 * which swings the flipper ({@link #flipperSwing}) and so stops its collision until the animation returns to 0.
 * The slope tables come from the stage file; the despawn is CheckObjDespawn.
 *
 * <p>StandOnObject's player-state writes go through the playable API as the bridge does; the object records the player it
 * holds in {@link #standingOn} because the shared solid latches are not set by an object-owned test. Not rewind-captured.
 */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
public final class CczFlipperObject extends CczObject implements CczFlipper, ObjectVisualFacing.NonDirectional {
    private static final Map<AbstractPlayableSprite, CczFlipperObject> STANDING = new WeakHashMap<>();

    private boolean holding;
    private AbstractPlayableSprite holder;

    public CczFlipperObject(ObjectSpawn spawn) {
        super(spawn, "Flipper");
    }

    /** The flipper {@code player} stands on ({@code oPlayerStandObj} is a {@code $1E}), or {@code null}. */
    public static synchronized CczFlipperObject standingOn(AbstractPlayableSprite player) {
        CczFlipperObject flipper = STANDING.get(player);
        return flipper != null && !flipper.isDestroyed() ? flipper : null;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {                                                // ObjFlipper_Init (:215-224), falls into Main
            routine = 2;
            tileWord = CczObjectArt.FLIPPER_TILE;
            spriteFlip = 0;
            priority = 1;
            width = 0x20;
            yRadius = 0x10;
        }
        if (animator == null) animator = CczObjectArt.animator(provider(), "flipperAnimations", 0);
        if ((animator == null || animator.anim == 0) && player instanceof AbstractPlayableSprite sprite) {
            checkPlayer(sprite);                                           // tst.b oAnim(a0); bne.s .Animate
        }
        animate();
    }

    /** ObjFlipper_CheckPlayer (:249-333). */
    private boolean checkPlayer(AbstractPlayableSprite p) {
        if (p.getAnimationId() == 0x2B) return noCollide(p);
        int d0 = (short) (p.getCentreX() - x + width);
        if (d0 < 0 || d0 >= 2 * width) return noCollide(p);
        int column = (p.getCentreX() - (x - width)) & 0xFFFF;
        if ((statusFlip & 1) != 0) column = (0x40 - column) & 0xFFFF;
        boolean movingUp = p.getYSpeed() < 0;
        var provider = provider();
        if (provider == null) return noCollide(p);
        int slope = CczObjectCatalogue.offset(provider.payload(), movingUp ? "flipperBottomSlope" : "flipperTopSlope");
        if (slope < 0) return noCollide(p);
        int surface = (provider.image().u8(slope + (column & 0x3F)) << 24 >> 24) + y;
        surface &= 0xFFFF;
        int radius = p.getYRadius();
        if (movingUp) radius = -radius;
        int feetY = (surface - radius) & 0xFFFF;                           // sub.w d3,d1
        int d3 = (short) (radius + p.getCentreY() - surface + 8);
        if (d3 < 0 || d3 >= 0x10) return noCollide(p);                     // bmi; cmpi.w #$10,d3; bcc
        NativePositionOps.writeYPosPreserveSubpixel(p, feetY);             // move.w d1,oY(a1)
        if (!movingUp) {
            standOn(p);
        } else {
            p.setYSpeed((short) 0);                                        // .HitBottom
            p.setGSpeed(p.getXSpeed());
        }
        return true;
    }

    /** StandOnObject through {@link CdStandOnObject}; the flipper's {@link #holding} is its "stood on" bit. */
    private void standOn(AbstractPlayableSprite p) {
        CdStandOnObject.apply(p, holding, p.isOnObject() && standingOn(p) == this);
        holding = true;
        holder = p;
        synchronized (CczFlipperObject.class) {
            STANDING.put(p, this);
        }
    }

    /** .NoCollide: GetOffObject (Object Solid.asm:15-55) for a player this flipper holds (the sound for a charge is not modelled). */
    private boolean noCollide(AbstractPlayableSprite p) {
        if (holding && holder == p) {
            holding = false;
            holder = null;
            synchronized (CczFlipperObject.class) {
                STANDING.remove(p, this);
            }
            // GetOffObject's player writes (Object Solid.asm:35-52): no stick, in the air, off the object, object control
            // released unless hidden or drowning, and a boredom ($2B) player is put back on the ground.
            p.setStickToConvex(false);
            p.setAir(true);
            p.setOnObject(false);
            if (!p.isHidden() && p.getAnimationId() != CdSolidRules.INSTANCE.drowningAnimation() && p.isObjectControlled()) {
                p.setObjectControlled(false);
            }
            if (p.getAnimationId() == CdSolidRules.INSTANCE.giveUpAnimation()) p.setAir(false);
        }
        return false;
    }

    @Override public int flipperX() { return x; }
    @Override public int flipperY() { return y; }
    @Override public int flipperWidth() { return width; }
    @Override public boolean flipperFlippedHorizontally() { return (statusFlip & 1) != 0; }

    @Override
    public void flipperSwing() {
        if (animator == null) animator = CczObjectArt.animator(provider(), "flipperAnimations", 0);
        if (animator != null) animator.anim = 1;                           // move.b #1,oAnim(a1)
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawSheet(CczObjectArt.FLIPPER, mapFrame);
    }

    public boolean holding() { return holding; }
}
