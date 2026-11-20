package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;

import java.util.List;

/** Present swing. The layout object is the pivot, and only the far end is a solid platform. */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjTtzSwing extends TtzObject
        implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional, SpawnRewindRecreatable {
    static final int ID = 0x29; // ObjectIndex (r4/object_index_1a.asm:44-52)
    static final int LINK_TILE = 0x0340;
    static final int SEAT_TILE = 0x4340;
    private static final int SEAT_LINK = 6;

    private int anchorX;
    private int anchorY;
    private int link;
    private int phase;
    private int xSpeed;
    private int ySpeed;
    private boolean initialized;

    public ObjTtzSwing(ObjectSpawn spawn) {
        super(spawn, "Swing");
        anchorX = x;
        anchorY = y;
    }

    private ObjTtzSwing(ObjTtzSwing pivot, int link) {
        super(childSpawn(0, 0, ID, pivot.spawn.subtype(), 0), "Swing");
        anchorX = pivot.anchorX;
        anchorY = pivot.anchorY;
        this.link = link;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialized) initialize();

        // sub_20D0F2 (objects/r4/swing.asm:127-159): 8.8 angle, signed sine/cosine at 16 px per link.
        int oldX = x;
        int oldY = y;
        phase = (phase + ((spawn.subtype() & 0x10) != 0 ? 0x100 : -0x80)) & 0xFFFF;
        int angle = phase >>> 8;
        x = (anchorX + ((TrigLookupTable.cosHex(angle) * link) >> 4)) & 0xFFFF;
        y = (anchorY + ((TrigLookupTable.sinHex(angle) * link) >> 4)) & 0xFFFF;

        if (link == SEAT_LINK) {
            // SwingObject_0_Routine2 (objects/r4/swing.asm:104-120) runs the solid call after the move.
            xSpeed = (short) ((x - oldX) << 8);
            ySpeed = (short) ((y - oldY) << 8);
            if (solidObject(Entry.TOP) && player != null) {
                // sub_20CFC6 (objects/r4/swing.asm:32-46): re-seat Sonic 2 px into the platform top.
                NativePositionOps.writeYPosPreserveSubpixel(player, y - yRadius - player.getYRadius() + 2);
            }
        }
    }

    private void initialize() {
        // SwingObject_0_Routine0 (objects/r4/swing.asm:53-102).
        initialized = true;
        priority = 4;
        width = 0x20;
        yRadius = 0xA;
        phase = 0xC000;
        tileWord = link == SEAT_LINK ? SEAT_TILE : LINK_TILE;
        if (link == SEAT_LINK) {
            findNextObjSlot(new TtzSwitch(
                    childSpawn(0, 0, 0x27, spawn.subtype() & 0xF, 0), getSlotIndex(), anchorX));
        } else if (link == 0) {
            for (int i = SEAT_LINK; i > 0; i--) {
                findObjSlot(new ObjTtzSwing(this, i));
            }
        }
    }

    @Override protected int despawnX() { return anchorX; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        // SwingObject (objects/r4/swing.asm:8-22) culls every link by the pivot's X.
        return outsideChunkRange(anchorX, cameraX);
    }

    @Override public int solidXVelocity() { return xSpeed; }
    @Override public int solidYVelocity() { return ySpeed; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialized) drawSheet(TtzObjectArt.SWING, link == 0 ? 0 : link == SEAT_LINK ? 1 : 2, false, false);
    }
}
