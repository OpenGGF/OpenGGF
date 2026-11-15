package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

/**
 * Light edge ({@code LightEdgeObject}, objects/r7/light_edge.asm:3-69): a pair of lit strips pinned to the
 * camera's X ({@code +$50} and {@code +$F0} alternately) at a Y derived from the water line. It is linked into
 * every Stardust Speedway stage (r7 "Stardust Speedway/R7 Shared.asm" includes the file) but no
 * {@code ObjectIndex} slot names it (r7/object_index_1_2.asm, r7/object_index_3.asm), so nothing can run it and
 * it is not registered. The class is the routine as assembled, with {@code water_y} and {@code water_speed} read
 * as the cleared RAM they are in a zone without water.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjLightEdge extends SszObject implements ObjectVisualFacing.NonDirectional {
    private static final int[] COLUMN = {0x50, 0xF0};
    private int column;
    private SszAnimator animator;

    public ObjLightEdge(ObjectSpawn spawn) {
        super(spawn, "Light edge");
    }

    @Override protected String sheetKind() { return SszObjectArt.LIGHT_EDGE; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {                                  // sub_206604 (:18-22)
            if (artProvider() == null) return;
            routine += 2;
            tileWord = SszObjectArt.LIGHT_EDGE_TILE;
            animator = SszObjectArt.animator(artProvider(), SszObjectCatalogue.Entry::lightEdgeAnimations, 0);
            initialised = true;
        }
        statusFlip |= 2;                                     // bset #1,obj.flags(a0) (:27)
        int waterY = 0;                                      // move.w water_y,d0: no water in this zone
        int d0 = (waterY + 0x80) & 0xFFFF;
        int top = cameraY() & 0xFFFF;
        if (d0 >= top) {                                     // cmp.w d1,d0; bcs.s loc_20664E
            if (d0 >= ((top + 0xE0) & 0xFFFF)) {             // loc_206644 (:43-45)
                d0 = (d0 - 0x100) & 0xFFFF;
                statusFlip &= ~2;
            }
            // else: tst.b water_speed; bmi.s loc_20664E; addq.w #4,d0 (water_speed is zero)
            else d0 = (d0 + 4) & 0xFFFF;
        }
        y = d0;
        column = (column + 1) >= 2 ? 0 : column + 1;         // :50-57
        x = (cameraX() + COLUMN[column]) & 0xFFFF;
        animator.step();
        mapFrame = animator.mapFrame();
        if (animator.showedFrame()) spriteFlip = (statusFlip ^ animator.flipBits()) & 3;
    }
}
