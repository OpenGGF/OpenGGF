package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * Water surface, object $04 ({@code WaterSurfaceObject}, objects/r4/water_surface.asm:3-49): the animated
 * line on the water (tile $8280, high priority). Every frame it flips between two screen offsets
 * ({@code byte_20695E}, :47-49: {@code $A0}, {@code $60} sign-extended: {@code camera X + $A0 - $60} and
 * {@code + $A0 + $60}) and sits two pixels below {@code water_y}; it never despawns.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjWaterSurface extends TtzObject implements ObjectVisualFacing.NonDirectional {
    /** {@code object_spawn_pool}, the first dynamic slot, where InitStage creates it (r4/main.asm:249). */
    public static final int LEVEL_START_SLOT = 32;
    private int routine;
    /** oVar2A: the toggle byte (WaterSurfaceObject_0_Routine2, objects/r4/water_surface.asm:25-33). */
    private int toggle;
    private CdAnimator animator;
    private int mapFrame;
    private int flipBits;

    public ObjWaterSurface(ObjectSpawn spawn) {
        super(spawn, "WaterSurface");
    }

    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            routine = 2;
            tileWord = TtzObjectArt.WATER_SURFACE_TILE;
            animator = TtzObjectArt.animator(artProvider(), "waterSurfaceAnimations", 0);
        }
        var camera = services().camera();
        int d0 = (camera == null ? 0 : camera.getX()) + 0xA0;
        int d1 = toggle + 1;
        if (d1 >= 2) d1 = 0;
        toggle = d1;
        d0 += d1 == 0 ? (byte) 0xA0 : 0x60;                      // byte_20695E, ext.w
        x = d0 & 0xFFFF;
        y = (waterY() + 2) & 0xFFFF;                              // move.w water_y,obj.y; addq.w #2
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
            flipBits = animator.flipBits();
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0 || animator == null) return;
        drawSheet(TtzObjectArt.WATER_SURFACE, mapFrame, (flipBits & 1) != 0, (flipBits & 2) != 0);
    }
}
