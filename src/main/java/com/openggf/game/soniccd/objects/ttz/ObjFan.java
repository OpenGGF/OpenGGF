package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * Fan, object $35 ({@code FanObject}): a switch-controlled wind fan (tile $4380, priority 3) whose subtype
 * bits select the facing (bit 0 flips both axes), the vertical shape (bit 1: $10 by 8 becomes 8 by $10) and,
 * for the A/B variant (R41A, R41B, R42A, R42B; FanObject, objects/r4/fan_a_b.asm:3-60), whether it starts running
 * (bit 7 sets {@code oVar3F}). The secondary subtype names a switch: the first frame its flag is set the fan
 * toggles (A/B: starts or stops its animation; C/D variant, R41C, R41D, R42C, R42D and R43, FanObject,
 * objects/r4/fan_c_d.asm:3-54: swaps between its two still frames). A/B draws {@code FanAnimsAB} while running and frame 0
 * otherwise and ends in CheckObjectDespawn; C/D reads its creator through {@code oVar2A} and deletes itself if
 * that slot's ID byte changed, which a layout-placed fan (creator 0) never sees.
 *
 * <p>{@code switch_flags} are the shared Switch objects' (see {@link TtzObject#switchFlag}). Not hosted: the wind
 * that pushes the player is player-side code and is not part of this object.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjFan extends TtzObject implements ObjectVisualFacing.NonDirectional {
    private boolean variantAB;
    private int routine;
    private boolean armed;          // oVar3E: the switch has been seen
    private boolean running;        // oVar3F (A/B)
    private int mapFrame;
    private int flip;
    private boolean vertical;
    private CdAnimator animator;

    public ObjFan(ObjectSpawn spawn) {
        super(spawn, "Fan");
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return variantAB; }

    @Override public int getOnScreenHalfWidth() { return width; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            var provider = artProvider();
            if (provider == null) return;
            variantAB = TtzObjectCatalogue.offset(provider.payload(), "fanABMappings1") >= 0;
            init();
        }
        int switchIndex = subtype2();
        if (switchIndex < 0x80 && !armed && switchFlag(switchIndex)) {      // move.b obj.subtype_2(a0),d0; bmi
            armed = true;
            if (variantAB) running = !running;                              // not.b obj.var_3f(a0)
            else mapFrame ^= 1;                                             // eori.b #1,obj.sprite_frame(a0)
        }
        if (!variantAB) return;
        if (!running) {
            mapFrame = 0;
        } else {
            if (animator == null) animator = TtzObjectArt.animator(artProvider(), "fanAnimations", 0);
            if (animator != null) {
                animator.step();
                mapFrame = animator.mapFrame();
            }
        }
    }

    /** FanObject first frame (objects/r4/fan_a_b.asm:3-31). */
    private void init() {
        routine = 2;
        priority = 3;
        tileWord = TtzObjectArt.FAN_TILE;
        int subtype = spawn.subtype();
        if (variantAB) running = subtype >= 0x80;
        else if (subtype >= 0x80) mapFrame = 1;
        vertical = (subtype & 2) != 0;
        width = vertical ? 8 : 0x10;
        yRadius = vertical ? 0x10 : 8;
        flip = (subtype & 1) != 0 ? 3 : 0;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawSheet(vertical ? TtzObjectArt.FAN2 : variantAB ? TtzObjectArt.FAN_AB1 : TtzObjectArt.FAN_CD1, mapFrame, (flip & 1) != 0, (flip & 2) != 0);
    }
}
