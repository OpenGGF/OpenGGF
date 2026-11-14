package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Ga, object $2D ({@code ObjGa}, Level/Collision Chaos/Objects/Ga/Main.asm:14-203). The object splits on its subtype:
 * {@code subtype - 1} negative (0, or $81 and up) is the badnik, anything else is its dust trail ({@link Dust}).
 * The badnik waits $3D frames, then $3D more with collision off, hovers into collision {@code $25} ($33 frames), turns
 * to {@code $26}, picks a horizontal speed of -$8000 (long, 16.16; -$4000 for a subtype above $80) away from the player's
 * side and a vertical speed of $8000, and flies on, flipping the vertical speed every $41 (or $61) frames. A subtype-0 Ga also
 * leaves a dust object at its position each flip. Collision $25 and $26; priority 1; width $10, Y radius 8; tile $A421.
 * It is deleted by DestroyOnGoodFuture and by CheckObjectDespawn.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class CczGa extends CczBadnik {
    private int timer;               // oVar2A
    private int hVel;                // oVar2C (long)
    private int vVel;                // oVar30 (long)
    private boolean dustSpawner;     // oVar3F
    private boolean secondSheet;

    public CczGa(ObjectSpawn spawn) {
        super(spawn, "Ga");
    }

    /** ObjGa: {@code subq.b #1,d0; bmi.s ObjGa_Parent} else the dust (:28-37). */
    public static boolean isParent(int subtype) {
        return ((subtype - 1) & 0xFF) >= 0x80;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (destroyOnGoodFuture()) return;
        switch (routine) {
            case 0 -> {
                init();
                routine2();
            }
            case 2 -> routine2();
            case 4 -> routine4();
            case 6 -> routine6(player);
            default -> routine8();
        }
    }

    /** ObjGa_Init (:45-62). */
    private void init() {
        routine = 2;
        spriteFlip = 0;                                                    // move.b #4,oSprFlags(a0)
        mapFrame = 0;
        priority = 1;
        width = 0x10;
        yRadius = 8;
        tileWord = CczObjectArt.GA_TILE;
        timer = 0x3D;
        secondSheet = (byte) spawn.subtype() < 0;                          // tst.b oSubtype(a0); bmi.s .SetMap
        dustSpawner = !secondSheet;                                        // st oVar3F(a0)
    }

    /** ObjGa_Routine2 (:64-72). */
    private void routine2() {
        timer = (timer - 1) & 0xFFFF;
        if (timer != 0) return;
        routine += 2;
        timer = 0x3D;
    }

    /** ObjGa_Routine4 (:74-87). */
    private void routine4() {
        ensureAnimator();
        timer = (timer - 1) & 0xFFFF;
        if (timer == 0) {
            colType = 0x25;
            routine += 2;
            anim();
            timer = 0x33;
        }
        animate();
    }

    /** ObjGa_Routine6 (:123-157). */
    private void routine6(PlayableEntity player) {
        ensureAnimator();
        timer = (timer - 1) & 0xFFFF;
        if (timer == 0) {
            colType = 0x26;
            routine += 2;
            anim();
            vVel = 0x8000;
            timer = 0x20;
            hVel = -0x8000;
            if ((byte) spawn.subtype() < 0) {                              // tst.b oSubtype(a0); bpl.s .SetDirection
                timer = 0x30;
                hVel = -0x4000;
            }
            int px = player instanceof AbstractPlayableSprite sprite ? sprite.getCentreX() : 0;
            if ((short) (x - px) < 0) {                                    // sub.w oX(a1),d0; bpl.s .Animate
                hVel = -hVel;
                setFacingRight(true);                                      // bset #0,oSprFlags / oFlags
            }
        }
        animate();
    }

    /** ObjGa_Routine8 (:159-196). */
    private void routine8() {
        ensureAnimator();
        addToX(hVel);
        addToY(vVel);
        timer = (timer - 1) & 0xFFFF;
        if (timer == 0) {
            timer = (byte) spawn.subtype() < 0 ? 0x61 : 0x41;
            vVel = -vVel;
            if (dustSpawner) spawnDust();
        }
        animate();
    }

    /** {@code jsr FindObjSlot}; same id, X and Y longs, subtype 1 (:140-149). */
    private void spawnDust() {
        var objects = services().objectManager();
        if (objects == null) return;
        Dust dust = new Dust(new ObjectSpawn(x, y, 0x2D, 1, 0, false, 0));
        dust.xSub = xSub;
        dust.ySub = ySub;
        objects.addDynamicObject(dust);
    }

    private void anim() {
        ensureAnimator();
        if (animator != null) animator.anim++;                             // addq.b #1,oAnim(a0)
    }

    private void ensureAnimator() {
        if (animator == null) animator = CczObjectArt.animator(provider(), "gaAnimations", 0);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        CdObjectArtProvider provider = provider();
        if (provider == null) return;
        String key = CczObjectArt.key(provider, secondSheet ? CczObjectArt.GA2 : CczObjectArt.GA1, tileWord,
                services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }

    public int timer() { return timer; }
    public int horizontalVelocity() { return hVel; }
    public int verticalVelocity() { return vVel; }

    /** Ga's dust trail ({@code ObjGa_Dust}, :235-242): a ring-sheet sparkle that falls one pixel a frame for $B4 frames. */
    public static final class Dust extends CczObject implements com.openggf.level.objects.ObjectVisualFacing.NonDirectional {
        private int timer;

        public Dust(ObjectSpawn spawn) {
            super(spawn, "Ga dust");
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (routine == 0) {                                            // ObjGa_Dust_Init (:251-262), falls into Main
                routine = 2;
                priority = 1;
                width = 6;
                yRadius = 6;
                tileWord = CdObjectArtProvider.RING_TILE;
                timer = 0xB4;
            }
            timer = (timer - 1) & 0xFFFF;
            if (timer == 0) {
                setDestroyed(true);
                return;
            }
            addToY(0x10000);
            if (animator == null) animator = CczObjectArt.animator(provider(), "gaDustAnimations", 0);
            animate();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (routine == 0) return;
            drawFrame(CdObjectArtProvider.RING, mapFrame, false, false);
        }

        public int timer() { return timer; }
    }
}
