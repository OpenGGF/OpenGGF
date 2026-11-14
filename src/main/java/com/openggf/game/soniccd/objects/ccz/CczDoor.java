package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Collision Chaos' door, object {@code $26} ({@code ObjDoor}, Level/Collision Chaos/Objects/Door/Main.asm:20-132, legacy ABI; the
 * Wacky Workbench, Tidal Tempest and Metallic Madness doors are other routines). A $1C wide, 8 high top-solid slab
 * (tile {@code $371}, priority 3) that slides along X by {@code oDoorOffset}, a byte stepping $10 a frame between 0 and $40:
 * up while the player's Y is at or below the door's, down while it is above. The subtype's bit 7 slides it toward +X,
 * otherwise toward -X; the low nibble picks a start delay ({@code ObjDoor_InitialDelays}) and, when 0, a $3C-frame
 * delay on closing, neither of which gates the motion. The despawn is the object's own chunk test on the base X with
 * no on-screen exemption, then {@code DespawnObjectR3} (:25-39).
 */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
public final class CczDoor extends CczObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private int baseX;               // oVar36
    private int offset;              // oVar3B (byte)
    private int delay;               // oVar3A
    private int timer;               // oVar3C

    public CczDoor(ObjectSpawn spawn) {
        super(spawn, "Door");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {                                                // ObjDoor_Init (:81-96), falls into Update
            routine = 2;
            tileWord = CczObjectArt.DOOR_TILE;
            priority = 3;
            width = 0x1C;
            yRadius = 8;
            baseX = x;
            delay = new int[] {0, 30, 60, 0}[spawn.subtype() & 3];         // ObjDoor_InitialDelays, indexed by the low nibble (:72-79)
        }
        updateOffset(player);
        int signed = (spawn.subtype() & 0x80) != 0 ? offset : -offset;
        x = (baseX + signed) & 0xFFFF;
        solidObject(Entry.TOP);                                            // TopSolidObject with oX, oY (:90-96)
    }

    /** ObjDoor_UpdateOpeningOffset (:98-129). */
    private void updateOffset(PlayableEntity player) {
        int py = player instanceof AbstractPlayableSprite sprite ? sprite.getCentreY() & 0xFFFF : 0;
        if (py >= (y & 0xFFFF)) {                                          // sub.w oY(a0),d0; bcc.s .Open
            offset += 0x10;
            if (offset >= 0x40) {
                offset = 0x40;
                delay = 8;
            }
            return;
        }
        offset -= 0x10;
        if (offset >= 0) return;                                           // subi.b #$10; bcc.s .End
        offset = 0;
        if ((spawn.subtype() & 0xF) == 0 && timer == 0) {
            delay = 0x3C;
            timer = 0xFF;
        }
    }

    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return baseX; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return routine != 0 && outsideChunkRange(baseX, cameraX);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawSheet(CczObjectArt.DOOR, 0);
    }

    public int offset() { return offset; }
    public int delay() { return delay; }
}
