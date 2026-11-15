package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * Water splash, objects $0B-$0E ({@code SplashObject}, objects/r4/splash.asm:3-43): the subtype is its
 * animation (tile $298, priority 1). It plays {@code FM $A2} (subtypes 0 and 1) or {@code FM $A1}, then each
 * frame sits four pixels below {@code water_y}, animates and draws; the script's {@code $FC} sends it to
 * routine 4, which deletes it. The player's water entry and exit create it; this class is the object only.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjSplash extends TtzObject implements ObjectVisualFacing.NonDirectional {
    private int routine;
    private CdAnimator animator;
    private int mapFrame;
    private int flipBits;

    public ObjSplash(ObjectSpawn spawn) {
        super(spawn, "Splash");
    }

    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            routine = 2;
            priority = 1;
            tileWord = TtzObjectArt.SPLASH_TILE;
            animator = TtzObjectArt.animator(artProvider(), "splashAnimations", spawn.subtype());
            services().playSfx(spawn.subtype() < 2 ? 0xA2 : 0xA1);   // PlayFmSound (SplashObject_0_Routine0, objects/r4/splash.asm:25-33)
        }
        if (routine == 4) { setDestroyed(true); return; }            // SplashObject_0_Routine4: DeleteObject
        y = (waterY() + 4) & 0xFFFF;
        if (animator != null && animator.step() == 2) routine = 4;   // bsr.w AnimateObject: $FC adds 2 to the routine
        if (animator != null) {
            mapFrame = animator.mapFrame();
            flipBits = animator.flipBits();
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0 || animator == null) return;
        drawSheet(TtzObjectArt.SPLASH, mapFrame, (flipBits & 1) != 0, (flipBits & 2) != 0);
    }
}
