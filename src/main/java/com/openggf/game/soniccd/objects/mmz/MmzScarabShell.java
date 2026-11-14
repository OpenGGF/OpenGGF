package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * The beetle's shell ({@code ScarabObject} with {@code oSubtype2} 1) at loc_20E174 (objects/r8/scarab.asm:475-489): a second sprite
 * the beetle moves to its own position every call, three frames after its own, drawn in front of the player (priority 3).
 * It is deleted when the beetle is gone.
 */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
final class MmzScarabShell extends MmzObject implements ObjectVisualFacing.NonDirectional {
    private final MmzScarabBody beetle;          // oVar2A
    /** The beetle's slot, which the shell tests each turn (objects/r8/scarab.asm:476-477). */
    private final int beetleSlot;
    private int routine;
    private int mapFrame;
    private int spriteFlip;

    MmzScarabShell(ObjectSpawn spawn, MmzScarabBody beetle, int spriteFlip) {
        super(spawn, "ScarabShell");
        this.beetle = beetle;
        this.beetleSlot = beetle.getSlotIndex();
        this.spriteFlip = spriteFlip;
        this.tileWord = MmzObjectArt.SCARAB_TILE;
        this.width = 0x20;
        this.yRadius = 0xB;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!slotHoldsId(beetleSlot, 0x2F)) {                        // cmpi.b #$2F,oID(a1); bne DeleteObject
            setDestroyed(true);
            return;
        }
        if (routine == 0) {                                          // loc_20E18E (:485-489)
            routine = 2;
            priority = 3;
        }
    }

    /** The beetle's {@code move.w oX(a0),oX(a1)} ... {@code addq.b #3,d0; move.b d0,oMapFrame(a1)} (:326-339). */
    void follow(int newX, int newY, int beetleFrame) {
        x = newX;
        y = newY;
        mapFrame = (beetleFrame + 3) & 0xFF;
    }

    void setFlip(int flip) { spriteFlip = flip; }

    int mapFrame() { return mapFrame; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        drawSheet(MmzObjectArt.SCARAB, mapFrame, (spriteFlip & 1) != 0, false);
    }
}
