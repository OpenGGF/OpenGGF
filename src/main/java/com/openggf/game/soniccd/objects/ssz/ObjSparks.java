package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

/**
 * Sparks, object $09 ({@code SparksObject}, objects/r7/sparks.asm:3-32), the flash the booster spawns
 * (ObjBooster). Routine 0 loads tile $8338 and falls into routine 2, which runs {@code SparksAnims}
 * (delay 3, frames 0 1 0 1, then {@code $FC}); the {@code $FC} steps to routine 4, which deletes the
 * object (:25-32). It is drawn every frame and never range-checked, and its priority stays 0 (:19-23).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjSparks extends SszObject implements ObjectVisualFacing.NonDirectional {
    private SszAnimator animator;

    public ObjSparks(ObjectSpawn spawn) {
        super(spawn, "Sparks");
    }

    @Override protected String sheetKind() { return SszObjectArt.SPARKS; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {                                  // SparksObject_0_Routine0 (:19-23)
            if (artProvider() == null) return;
            routine = 2;
            tileWord = SszObjectArt.SPARKS_TILE;
            animator = SszObjectArt.animator(artProvider(), SszObjectCatalogue.Entry::sparksAnimations, 0);
            initialised = true;
        }
        if (routine == 2) {                                  // SparksObject_0_Routine2 (:25-27)
            if (animator.step() == 2) routine += 2;          // $FC: addq.b #2,oRoutine
            mapFrame = animator.mapFrame();
            if (animator.showedFrame()) spriteFlip = (statusFlip ^ animator.flipBits()) & 3;   // AnimateObject (Object Animate.asm:36-41)
        } else {
            setDestroyed(true);                              // SparksObject_0_Routine4: jmp DeleteObject (:31-32)
        }
    }
}
