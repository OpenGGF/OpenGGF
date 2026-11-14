package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * The rings a prize switch rains, object $40 ({@code RingPrizeObject}, objects/r4/prize.asm:374-458): a ring that falls
 * from $100 with gravity $18, bounces on the floor after $30 frames (a quarter of the speed lost), spins, and is
 * collected on touch ({@code oColType $47}: the touch advances the routine, {@code RingPrizeObject_0_Routine4} then runs
 * {@code CollectRing} and the sparkle animation). It deletes itself after $FF frames or below the bottom boundary.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjRingPrize extends TtzObject implements TouchResponseProvider, TouchResponseListener, ObjectVisualFacing.NonDirectional {
    static final int TILE = 0xA7AE;
    private int routine;
    private int colType;
    private int yFixed;
    private int ySpeed;
    private int spin;                  // obj.var_36
    private int spinSpeed;             // obj.var_38
    private int life;                  // obj.var_3a
    private int wait;                  // obj.var_3b
    private int mapFrame;
    private CdAnimator animator;
    private boolean touched;

    public ObjRingPrize(ObjectSpawn spawn) {
        super(spawn, "RingPrize");
        this.yFixed = y << 16;
    }

    @Override public int getCollisionFlags() { return colType; }
    @Override public int getCollisionProperty() { return 0; }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        touched = true;                // the touch routine's addq.b #2,obj.routine(a0)
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {                                                 // RingPrizeObject_0_Routine0 (:391-405)
                routine = 2;
                yRadius = 8;
                width = 8;
                tileWord = TILE;
                priority = 3;
                colType = 0x47;
                spinSpeed = 0x800;
                ySpeed = 0x100;
                life = 0xFF;
                wait = 0x30;
                fall();
            }
            case 2 -> {
                if (touched) { routine = 4; update(vIntRunCount, player); return; }
                fall();
            }
            case 4 -> {                                                 // RingPrizeObject_0_Routine4 (:442-447)
                routine = 6;
                colType = 0;
                priority = 1;
                collect(player);
                sparkle();
            }
            case 6 -> sparkle();
            default -> setDestroyed(true);
        }
    }

    /** RingPrizeObject_0_Routine2 (prize.asm:406-440). */
    private void fall() {
        yFixed += ySpeed << 8;                                          // jsr MoveObject
        y = yFixed >> 16;
        ySpeed += 0x18;
        if (wait == 0 || --wait == 0) {                                 // tst.b obj.var_3b(a0); beq; subq.b; bne
            int d1 = ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();   // jsr CheckBlockDown
            if (d1 < 0) {                                               // tst.w d1; bpl.s
                y = (y + d1) & 0xFFFF;
                yFixed = y << 16;
                ySpeed = -(ySpeed - (ySpeed >> 2));                     // asr.w #2; sub; neg.w
            }
        }
        spin = (spin + spinSpeed) & 0xFFFF;
        mapFrame = (spin >> 12) & 3;                                    // rol.w #4,d0; andi.b #3,d0
        spinSpeed -= 8;
        if (--life == 0 || y > bottomBoundary() + 0xE0) setDestroyed(true);   // subq.b #1,obj.var_3a; beq; cmp bottom_bound+$E0
    }

    private int bottomBoundary() {
        var camera = services().camera();
        return camera == null ? 0x7FFF : camera.getMaxY();
    }

    /** {@code jsr sub_208ACA}: CollectRing (R4 Compatibility.asm:243). */
    private void collect(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) sprite.addRings(1);
    }

    private void sparkle() {
        if (animator == null) animator = TtzObjectArt.animator(artProvider(), "ringPrizeAnimations", 0);
        if (animator != null && animator.step() == 2) routine = 8;
        if (animator != null) mapFrame = animator.mapFrame();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0 || routine == 8) return;
        drawSheet(TtzObjectArt.RING_PRIZE, mapFrame, false, false);
    }
}
