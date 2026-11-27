package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * The level effects object, ID $04 ({@code ObjWaterfall}, Palmtree Panic/Objects/Effects/Main.asm:7-61;
 * the object index calls it the unused, broken waterfall generator). It snaps to a 16 pixel row, falls
 * 4 pixels a frame for $180 pixels and deletes itself, animating {@code Ani_Waterfall} and drawing priority 1,
 * $10 wide (tile $3BA). It has no despawn check.
 *
 * <p>The block writes are unhosted. {@code ObjWaterfall_Main} (Level/Palmtree Panic/Objects/Effects/Main.asm:36-58)
 * calls {@code PlaceBlockAtPos} on aligned rows. The generator is unused in the Present {@code ObjectIndex}
 * (Level/Palmtree Panic/Object Index (Act 1 Present).asm:7-11). {@code TestPpzObjectsA} checks that all ten
 * USA Palmtree Panic layouts have zero placements of object $04. The sibling stubs {@code ObjEarthquake} and
 * {@code ObjEarthquakeSet} (IDs $05 and $08) only {@code rts} (Effects/Main.asm:65-72).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjWaterfall extends CdObject implements ObjectVisualFacing.NonDirectional {
    /** ObjWaterfall_Init: addi.w #$180,oVar2A(a0) (Effects/Main.asm:31). */
    private static final int FALL_DISTANCE = 0x180;

    private int routine;
    private int endY;
    private int mapFrame;
    private CdAnimator animator;

    public ObjWaterfall(ObjectSpawn spawn) {
        super(spawn, "Waterfall");
    }

    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (PpzStage.bossAct(services())) return;
        if (routine == 0) {
            var provider = artProvider();
            if (provider == null) return;
            routine = 2;
            priority = 1;
            width = 0x10;
            yRadius = 0;
            tileWord = PpzObjectArt.WATERFALL_TILE;
            y &= 0xFFF0;                                // andi.w #$FFF0,oY(a0)
            endY = y + FALL_DISTANCE;
            animator = PpzObjectCatalogue.entry(provider.payload()).waterfallAnimations() >= 0 ? PpzObjectArt.animator(artProvider(), e -> e.waterfallAnimations(), 0) : null;
        } else {
            int next = (y + 4) & 0xFFFF;                // addq.w #4,d0
            if (next >= endY) {                         // cmp.w oVar2A(a0),d0; bcs.s .NoDel
                setDestroyed(true);
                return;
            }
            y = next;
            // No USA Palmtree Panic layout reaches the twelve PlaceBlockAtPos calls.
        }
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0 || animator == null) return;
        var provider = artProvider();
        if (provider == null) return;
        String key = PpzObjectArt.key(provider, PpzObjectArt.PPZ_WATERFALL, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, false, false);
    }

    public int mapFrame() { return mapFrame; }
}
