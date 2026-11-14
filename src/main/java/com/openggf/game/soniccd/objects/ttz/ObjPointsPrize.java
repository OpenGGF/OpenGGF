package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * The points sprite a prize switch makes, object $41 ({@code PointsPrizeObject}, objects/r4/prize.asm:470-512): it rises
 * from {@code -$300} with gravity {@code $18} and deletes itself when it turns downward.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjPointsPrize extends TtzObject implements ObjectVisualFacing.NonDirectional {
    static final int TILE = 0x036E;
    private int routine;
    private int yFixed;
    private int ySpeed;

    public ObjPointsPrize(ObjectSpawn spawn) {
        super(spawn, "PointsPrize");
        this.yFixed = y << 16;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {                                             // PointsPrizeObject_0_Routine0 (:485-492)
            routine = 2;
            tileWord = TILE;
            priority = 1;
            width = 8;
            ySpeed = -0x300;
        }
        if (ySpeed >= 0) {                                              // PointsPrizeObject_0_Routine2: tst.w obj.y_speed(a0); bpl.s DeleteObject
            setDestroyed(true);
            return;
        }
        yFixed += ySpeed << 8;                                          // jsr MoveObject
        y = yFixed >> 16;
        ySpeed += 0x18;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawSheet(TtzObjectArt.POINTS_PRIZE, 0, false, false);
    }
}
