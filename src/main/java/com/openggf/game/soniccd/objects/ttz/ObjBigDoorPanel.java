package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * A big-door panel ({@code BigDoorObject_0_Routine4}, objects/r4/big_door.asm:89-98): $20 wide (xRadius and
 * width $10), solid, 16.16 vertical speed of one pixel a frame for $81 frames ({@code move.w #$80}, then
 * {@code subq.w #1; bmi}) and then deleted. No despawn check.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
final class ObjBigDoorPanel extends TtzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private final int frame;
    private final int speed;
    private int yFixed;
    private int timer = 0x80;

    ObjBigDoorPanel(int x, int y, int tileWord, int frame, int speed, int priority) {
        super(childSpawn(x, y, 0x25, 0, 0), "BigDoorPanel");
        this.tileWord = tileWord;
        this.frame = frame;
        this.speed = speed;
        this.priority = priority;
        this.width = 0x10;
        this.yRadius = 0x40;
        this.yFixed = y << 16;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        timer = (timer - 1) & 0xFFFF;
        if ((timer & 0x8000) != 0) { setDestroyed(true); return; }                   // bmi.s loc_20CFE2
        yFixed += speed;
        y = yFixed >> 16;
        solidObject(Entry.SOLID);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawSheet(TtzObjectArt.BIG_DOOR, frame, false, false);
    }
}
