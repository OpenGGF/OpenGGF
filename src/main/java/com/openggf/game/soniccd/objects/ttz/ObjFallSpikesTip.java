package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;

import java.util.List;

/**
 * The hurt tip of the falling spikes ({@code loc_20EAF6}, objects/r4/fall_spikes.asm:181-190, created at
 * :56-76): the same ID with subtype $FF, $30 below the block, collision $B0, frame 1, $20 wide ($10 radius).
 * The block adds its fall to the tip's Y; the tip only draws while its parent's ID is still $32.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
final class ObjFallSpikesTip extends TtzObject implements TouchResponseProvider, ObjectVisualFacing.NonDirectional {
    private final ObjFallSpikes parent;
    /** The slot of {@code parent} the native child stores and tests each turn ({@code cmpi.b #$32,obj.id(a1)}). */
    private int parentSlot = -1;
    private int yFixed;

    ObjFallSpikesTip(ObjFallSpikes parent) {
        super(childSpawn(parent.getX(), parent.getY() + 0x30, 0x32, 0xFF, 0), "FallSpikesTip");
        this.parent = parent;
        this.parentSlot = parent == null ? -1 : parent.getSlotIndex();
        this.priority = 3;
        this.width = 0x10;
        this.yRadius = 0x10;
        this.tileWord = TtzObjectArt.FALL_SPIKES_TILE;
        this.yFixed = y << 16;
    }

    void follow(int d0) {
        yFixed += d0;
        y = yFixed >> 16;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!slotHoldsId(parentSlot, 0x32)) setDestroyed(true);             // cmpi.b #$32,obj.id(a1); bne -> DeleteObject
    }

    @Override public int getCollisionFlags() { return 0xB0; }
    @Override public int getCollisionProperty() { return 0; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawSheet(TtzObjectArt.FALL_SPIKES, 1, false, false);
    }
}
