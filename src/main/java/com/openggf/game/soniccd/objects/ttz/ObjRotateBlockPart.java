package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * A solid part of the rotate block ({@code RotateBlockObject_0_*}, objects/r4/rotate_block.asm:248-338): part 0
 * (the top surface) is $20 wide and $10 tall, part 1 (the side) $10 by $10; both draw frame 8 and follow the
 * parent's turn through byte_20EF04 / byte_20EF0C. Part 0 publishes "stood on" as {@code var_3e}
 * ({@code sne}, loc_20EEA0 :295-303); part 1 publishes the first frame of a push (loc_20EED8, :315-329).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
final class ObjRotateBlockPart extends TtzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** byte_20EF04 (byte_20EF04, objects/r4/rotate_block.asm:340-344): part 0's offset for the parent's frames 0-3. */
    private static final int[][] STAND_OFFSETS = {{-0x10, 0}, {-0x10, 0}, {0x10, 0}, {0x10, 0}};
    /** byte_20EF0C (byte_20EF0C, objects/r4/rotate_block.asm:346-350): part 1's offsets. */
    private static final int[][] PUSH_OFFSETS = {{0, -0x20}, {0, 0x20}, {0, 0x20}, {0, -0x20}};

    private final ObjRotateBlock parent;
    private final int kind;
    private int routine;
    /** oVar3E as a word: $FFFF set, 0 clear; the byte tests read its high byte. */
    private int flagWord;

    ObjRotateBlockPart(int x, int y, ObjRotateBlock parent, int kind) {
        super(childSpawn(x, y, 0x3D, 0, 0), "RotateBlockPart");
        this.parent = parent;
        this.kind = kind;
    }

    boolean standingFlag() { return (flagWord & 0x8000) != 0; }

    boolean pushFlag() { return (flagWord & 0xFF00) != 0; }

    @Override public boolean usesCustomOutOfRangeCheck() { return true; }

    /** loc_20EE16: CheckObjectDespawn2 on the parent's original X (oVar36, loc_20EE16 :248-255). */
    @Override public boolean isCustomOutOfRange(int cameraX) {
        return outsideDespawnRange(parentX, cameraX, renderFlagOnScreen());
    }

    private int parentX;

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            routine += 2;
            priority = 3;
            tileWord = TtzObjectArt.ROTATE_BLOCK_TILE;
            parentX = parent.getX();                            // move.w obj.x(a0),obj.var_36(a1) (RotateBlockObject_1_Routine0, objects/r4/rotate_block.asm:48)
            width = kind == 0 ? 0x20 : 0x10;
            yRadius = 0x10;
        }
        if (parent.isDestroyed()) return;
        int frame = parent.mapFrame();
        if (frame >= 4) {                                      // loc_20EEFA: GetOffObject
            var p = mainPlayer();
            if (p != null) ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), p, this);
            return;
        }
        int[] off = (kind == 0 ? STAND_OFFSETS : PUSH_OFFSETS)[frame];
        x = parent.getX() + off[0];
        y = parent.getY() + off[1];
        solidObject(Entry.SOLID);
        var p = mainPlayer();
        if (kind == 0) {
            flagWord = p != null && stoodOn(p) ? 0xFFFF : 0;     // btst #3,flags; sne var_3e
            return;
        }
        if (p != null && pushedBy(p)) {                         // loc_20EED8
            if ((flagWord & 0xFF) != 0) return;                 // tst.b var_3f; bne
            flagWord = 0xFFFF;
            clearPushedBy(p);                                   // bclr #5,flags
        } else {
            flagWord = 0;                                       // loc_20EEF4: clr.w var_3e
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0 || parent.isDestroyed()) return;
        drawSheet(TtzObjectArt.ROTATE_BLOCK, 8, false, false);  // move.b #8,obj.sprite_frame(a0)
    }
}
