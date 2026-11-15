package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;

import java.util.List;

/**
 * The hurting spikes under a {@link MmzSpikeCrusher} part ({@code SpikeCrusherObject} with {@code oSubtype} $FF,
 * loc_20D0BE, objects/r8/trap_2.asm:430-455 and loc_20DC9A, objects/r8/trap_3.asm:377-398): frame 6 twelve pixels below the part,
 * with the collision type the part's frame selects. It is deleted when the part is gone and has no despawn check.
 */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
final class MmzSpikeCrusherTip extends MmzObject implements TouchResponseProvider, ObjectVisualFacing.NonDirectional {
    /** byte_20D114 (objects/r8/trap_2.asm:457-463): collision type by the part's frame; frames 0 and 3 do not hurt. */
    private static final int[] COLLISION = {0x00, 0x9C, 0x9C, 0x00, 0x9B, 0x9D};

    private final MmzSpikeCrusher parent;       // oVar34
    /** The slot of {@code parent} the native child stores and tests each turn ({@code cmpi.b #$33,obj.id(a1)}). */
    private int parentSlot = -1;
    private final int parentFrame;              // oVar3C
    private int collision;
    private boolean placed;

    MmzSpikeCrusherTip(ObjectSpawn spawn, MmzSpikeCrusher parent, int parentFrame) {
        super(spawn, "SpikeCrusherTip");
        this.parent = parent;
        this.parentSlot = parent == null ? -1 : parent.getSlotIndex();
        this.parentFrame = parentFrame;
        this.tileWord = MmzObjectArt.SPIKE_CRUSHER_TIP_TILE;     // move.w #$4400,oTile(a0)
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!slotHoldsId(parentSlot, 0x33)) {                              // cmpi.b #$33,0(a1); bne DeleteObject
            setDestroyed(true);
            return;
        }
        y = (parent.getY() + 0xC) & 0xFFFF;                      // move.w $C(a1),d0; addi.w #$C,d0
        collision = COLLISION[Math.min(parentFrame, COLLISION.length - 1)];
        placed = true;
    }

    @Override public int getCollisionFlags() { return placed ? collision : 0; }
    @Override public int getCollisionProperty() { return 0; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!placed) return;
        drawSheet(MmzObjectArt.SPIKE_CRUSHER_TIP, 6, false, false);
    }
}
