package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

/**
 * One log of the bridge, object $2F routine 4 (BridgePiece, objects/r5/bridge.asm:99-110; made by
 * BridgeInit, :52-84): a 16 by 16 sprite (priority 3, tile {@code $4300}, frame 0) whose Y the hub sets
 * every frame it bends; it deletes itself when its hub's slot no longer holds a bridge hub. It has no collision and no
 * despawn check.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjBridgePiece extends QqzObject implements ObjectVisualFacing.NonDirectional {
    /** bridge.parent: the hub's slot ({@code move.w a0,bridge.parent(a1)}, objects/r5/bridge.asm:73). */
    private final int hubSlot;
    private final int originY;      // bridge.y

    ObjBridgePiece(ObjectSpawn spawn, ObjBridge hub, int pieceId) {
        super(spawn, "Bridge piece");
        this.hubSlot = hub.getSlotIndex();
        this.originY = spawn.y();
        this.routine = 4;
        this.priority = 3;
        this.tileWord = QqzObjectArt.BRIDGE_TILE;
        this.yRadius = 8;
        this.width = 8;
        this.initialised = true;
    }

    @Override public ObjectVisualFacing.Basis nativeFacing() { return ObjectVisualFacing.Basis.NON_DIRECTIONAL; }

    @Override protected boolean despawnsByCheckObjDespawn() { return false; }

    @Override protected String sheetKind() { return QqzObjectArt.BRIDGE; }

    @Override
    protected void run(PlayableEntity player) {
        // BridgePiece (objects/r5/bridge.asm:99-108): the piece reads the byte at its hub's slot each turn, not the
        // hub itself. It goes when that slot no longer holds ID $2F (cmpi.b #$2F,obj.id(a1); bne.s .Delete) or holds
        // another bridge's piece (tst.b bridge.is_piece(a1); bne.s .Delete); a new bridge hub in the slot keeps it.
        if (!slotHoldsId(hubSlot, 0x2F) || slotOccupant(hubSlot) instanceof ObjBridgePiece) {
            deleteObject();
            drawThisFrame = false;
        }
    }

    /** {@code add.w bridge.y(a1),d0; move.w d0,obj.y(a1)}. */
    void setBend(int offset) {
        y = (originY + offset) & 0xFFFF;
    }
}
