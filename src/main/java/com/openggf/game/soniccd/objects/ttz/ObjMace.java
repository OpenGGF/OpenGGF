package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * Swinging mace, object $36 in R41A, R42A, R42C and R42D ({@code MaceObject}, objects/r4/mace.asm:3-177): a hub
 * (tile $368, priority 3) with six {@link ObjMacePart parts} on a rotating arm: five chain links (subtype
 * $FF, tile $368) at 16, 32, 48, 64 and 80 pixels from the hub and the spiked ball (subtype $FE, tile $370,
 * collision $A9) at 96. The angle {@code oVar2A} advances by {@code oVar2C} ($100, negated for subtype 1) a
 * frame and the parts sit at {@code hub + (cos, sin) * radius}, the radius coming from the angle's sine and
 * cosine (SineCosine, ±$100) shifted into 16.16 pixels: {@code asr.l #2} gives 64 pixels, then 32 and 16.
 * If any of the six spawns fails the hub deletes itself (loc_20EFBC, objects/r4/mace.asm:39-117). It ends in CheckObjectDespawn.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjMace extends TtzObject implements ObjectVisualFacing.NonDirectional {
    private int routine;
    /** oVar2A: the angle word; oVar2C its step (MaceObject_0_Routine0, objects/r4/mace.asm:26-38). */
    private int angle;
    private int step;
    private final ObjMacePart[] parts = new ObjMacePart[6];

    public ObjMace(ObjectSpawn spawn) {
        super(spawn, "Mace");
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            routine = 2;
            priority = 3;
            width = 8;
            yRadius = 8;
            tileWord = TtzObjectArt.MACE_TILE;
            step = spawn.subtype() == 0 ? 0x100 : -0x100;
            for (int i = 0; i < 6; i++) {
                parts[i] = new ObjMacePart(this, i == 5);       // subtype $FF for 0-4, $FE for the ball
                if (!spawnChild(parts[i])) { setDestroyed(true); return; }   // jmp DeleteObject
            }
        }
        angle = (angle + step) & 0xFFFF;                         // add.w d0,obj.var_2a(a0)
        int a = (angle >> 8) & 0xFF;                             // lsr.w #8,d0
        int sin = TrigLookupTable.sinHex(a);
        int cos = TrigLookupTable.cosHex(a);
        // swap; clr.w; asr.l #2: the radius-64 offsets in 16.16, then 32 and 16 by two more asr.l #1.
        int r64s = (sin << 16) >> 2, r64c = (cos << 16) >> 2;
        int r32s = r64s >> 1, r32c = r64c >> 1;
        int r16s = r32s >> 1, r16c = r32c >> 1;
        int hubX = x << 16, hubY = y << 16;
        parts[0].place(hubX + r16c, hubY + r16s);
        parts[1].place(hubX + r32c, hubY + r32s);
        parts[2].place(hubX + r16c + r32c, hubY + r16s + r32s);
        parts[3].place(hubX + r64c, hubY + r64s);
        parts[4].place(hubX + r16c + r64c, hubY + r16s + r64s);
        parts[5].place(hubX + r32c + r64c, hubY + r32s + r64s);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawSheet(TtzObjectArt.MACE, 0, false, false);
    }
}
