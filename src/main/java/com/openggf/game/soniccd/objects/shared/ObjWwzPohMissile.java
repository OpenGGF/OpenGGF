package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;

import java.util.List;

/**
 * Wacky Workbench's Poh-Bee missile ({@code PohBeeMissileObject}, objects/r6/poh_bee.asm:255-311): the
 * Poh-Bee's own object ID with subtype $FF, spawned by its dive. It waits four frames, then moves ten,
 * then (while on screen) keeps moving and animates; off screen it is deleted. Collision type $B3 hurts.
 */
public final class ObjWwzPohMissile extends CdObject implements TouchResponseProvider, ObjectVisualFacing.NonDirectional {
    /** move.b #$B3,obj.collide_type(a1) (objects/r6/poh_bee.asm:197). */
    private static final int COLLISION = 0xB3;

    private final int xVelocity;
    private final int yVelocity = 0x20000;         // move.l #$20000,obj.var_30(a1) (:200)
    private int routine;
    private int xSub;
    private int ySub;
    private int timer;
    private int mapFrame;
    /** oSprFlags bits 0-1 copied from the Poh-Bee (:190); AnimateObject rewrites them from oFlags, which is clear. */
    private int spriteFlip;
    private CdAnimator animator;
    private boolean initialised;

    ObjWwzPohMissile(int x, int y, int id, int spriteFlip, int xVelocity, int tile) {
        super(new ObjectSpawn(x, y, id, 0xFF, 0, false, 0), "Poh-Bee missile");
        this.xVelocity = xVelocity;
        this.spriteFlip = spriteFlip & 3;
        this.priority = 1;
        this.width = 0x10;
        this.yRadius = 0x10;
        this.tileWord = tile;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (artProvider() == null || cdLevel() == null) return;
        initialised = true;
        switch (routine) {
            case 0 -> { routine += 2; timer = 3; pause(); }              // Routine0 falls into Routine2 (:274-277)
            case 2 -> pause();
            case 4 -> {                                                 // PohBeeObject_0_Routine4 (:289-297)
                move();
                if (--timer < 0) routine += 2;
            }
            case 6 -> {                                                 // PohBeeObject_0_Routine6 (:301-311)
                if (!renderFlagOnScreen()) {
                    setDestroyed(true);                                 // jmp DeleteObject
                    return;
                }
                move();
                if (animator == null) {
                    var payload = cdLevel().payload();
                    animator = new CdAnimator(artProvider().image(),
                            SharedObjectCatalogue.offset(payload, "wwzPohMissileAnims"), 0);
                }
                animator.step();
                mapFrame = animator.mapFrame();
                spriteFlip = animator.flipBits() & 3;                   // oFlags is clear for a new slot
            }
            default -> { }
        }
    }

    /** PohBeeObject_0_Routine2 (:279-287). */
    private void pause() {
        if (--timer >= 0) return;
        routine += 2;
        mapFrame = 1;
        timer = 0x0A;
    }

    private void move() {
        int px = ((x & 0xFFFF) << 16 | xSub) + xVelocity;
        x = px >>> 16;
        xSub = px & 0xFFFF;
        int py = ((y & 0xFFFF) << 16 | ySub) + yVelocity;
        y = py >>> 16;
        ySub = py & 0xFFFF;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override public int getCollisionFlags() { return initialised ? COLLISION : 0; }
    @Override public int getCollisionProperty() { return 0; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) drawFrame(SharedObjectArt.WWZ_POH_MISSILE, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }
}
