package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;

import java.util.List;

/**
 * A part of the swinging mace ({@code loc_20F1B2} and {@code MaceObject_1_*}, objects/r4/mace.asm:185-231): the
 * parent writes the part's 16.16 position each frame ({@code oVar2A}, {@code oVar2E}) and the part copies the
 * integer words into its position. The five links (tile $368, priority 3) draw {@code MaceChainSprites2}; the
 * ball (subtype $FE, tile $370, priority 2) draws {@code MaceChainSprites1} and has collision $A9 (hurt).
 * A part deletes itself when its parent's ID is no longer the mace's ($36).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
final class ObjMacePart extends TtzObject implements TouchResponseProvider, ObjectVisualFacing.NonDirectional {
    private final ObjMace parent;
    /** The slot of {@code parent} the native child stores and tests each turn ({@code cmpi.b #$36,obj.id(a1)}). */
    private int parentSlot = -1;
    private final boolean ball;
    private boolean initialised;
    private int targetX;
    private int targetY;

    ObjMacePart(ObjMace parent, boolean ball) {
        super(childSpawn(parent.getX(), parent.getY(), 0x36, ball ? 0xFE : 0xFF, 0), "MacePart");
        this.parent = parent;
        this.parentSlot = parent == null ? -1 : parent.getSlotIndex();
        this.ball = ball;
        width = 8;
        yRadius = 8;
    }

    void place(int x16, int y16) {
        targetX = x16;
        targetY = y16;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!slotHoldsId(parentSlot, 0x36)) { setDestroyed(true); return; }   // loc_20F1D2: cmpi.b #$36,obj.id(a1); bne
        if (!initialised) {
            initialised = true;
            priority = ball ? 2 : 3;
            tileWord = ball ? TtzObjectArt.MACE_BALL_TILE : TtzObjectArt.MACE_TILE;
        }
        x = targetX >> 16;                                          // move.w obj.var_2a(a0),obj.x(a0)
        y = targetY >> 16;
    }

    @Override public int getCollisionFlags() { return ball && initialised ? 0xA9 : 0; }
    @Override public int getCollisionProperty() { return 0; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        drawSheet(ball ? TtzObjectArt.MACE_BALL : TtzObjectArt.MACE_CHAIN, 0, false, false);
    }
}
