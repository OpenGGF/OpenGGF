package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * Stardust Speedway's swing ({@code SwingObject}, objects/r7/swing.asm:3-175): an anchor (frame 0) and
 * {@code subtype & 15} pieces on a pendulum, spawned by the anchor at its first update, each on a radius
 * of 16 pixels times its link number from the anchor. All pieces run the same swing: the angle word
 * {@code var_3a} advances by the speed {@code var_30}, which gains {@code var_3e} a frame up to
 * {@code var_34} and then loses it down to {@code var_38}; subtype bits 4-7 select the table row
 * ({@code word_20CEEC}). The outermost piece is {@link ObjSwingEnd}: frame 1, a spiked solid.
 * Palmtree Panic's, Tidal Tempest's and Quartz Quadrant's swings are other routines under the same name.
 *
 * <p>An anchor with a zero link count would spawn 256 pieces in the original ({@code dbf} on a byte
 * decrement, :80-99); no placement does and it is not modelled.
 */
public class ObjSwing extends CdObject implements ObjectVisualFacing.NonDirectional {
    protected final int link;                // obj.var_3c: the link number (0 for the anchor)
    protected final int links;               // obj.var_2f: the link count (subtype & 15)
    protected int anchorX;                   // obj.var_36
    protected int anchorY;                   // obj.var_32
    protected int mapFrame;
    protected boolean initialised;
    /** obj.var_3a, var_30, var_3e, var_34, var_38, var_3d (:108-111, 137-157). */
    private int angle;
    private int speed;
    private int accel;
    private int maxSpeed;
    private int minSpeed;
    private boolean decelerating;

    public ObjSwing(ObjectSpawn spawn) {
        this(spawn, 0, spawn.subtype() & 0xF, spawn.x(), spawn.y(), 0);
    }

    ObjSwing(ObjectSpawn spawn, int link, int links, int anchorX, int anchorY, int mapFrame) {
        super(spawn, "Swing");
        this.link = link;
        this.links = links;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.mapFrame = mapFrame;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialised && !init()) return;
        int oldX = x;
        int oldY = y;
        swing();
        afterSwing(player, oldX, oldY);
    }

    /** SwingObject_0_Routine0 (:62-111); it falls into Routine2 on the same frame. */
    private boolean init() {
        var level = cdLevel();
        if (level == null || artProvider() == null) return false;
        var payload = level.payload();
        var image = artProvider().image();
        priority = 3;
        tileWord = SharedObjectCatalogue.offset(payload, "swingTile");
        width = 0x20;
        yRadius = 8;
        if (link == 0 && !(this instanceof ObjSwingEnd)) {
            anchorX = x;
            anchorY = y;
            var objects = services().objectManager();
            if (objects == null) return false;
            for (int i = links; i >= 1; i--) {                          // the loop of :82-99, link numbers links..1
                boolean end = i == links;
                ObjectSpawn spawn = new ObjectSpawn(x, y, getSpawn().objectId(), getSpawn().subtype(), 0, false, 0);
                objects.addDynamicObject(end
                        ? new ObjSwingEnd(spawn, i, links, anchorX, anchorY)
                        : new ObjSwing(spawn, i, links, anchorX, anchorY, 2));
            }
        }
        int row = SharedObjectCatalogue.offset(payload, "swingMotion") + ((getSpawn().subtype() & 0xF0) >> 1);
        angle = image.u16(row);
        accel = (short) image.u16(row + 2);
        maxSpeed = (short) image.u16(row + 4);
        minSpeed = (short) image.u16(row + 6);
        initialised = true;
        return true;
    }

    /** sub_20D024 (:137-175): the angle step and the position on the pendulum. */
    private void swing() {
        if (!decelerating) {
            speed = (short) (speed + accel);
            angle = (angle + speed) & 0xFFFF;
            decelerating = speed == maxSpeed;                           // cmp.w obj.var_34(a0),d0; seq obj.var_3d(a0)
        } else {
            speed = (short) (speed - accel);
            angle = (angle + speed) & 0xFFFF;
            if (speed == minSpeed) decelerating = false;
        }
        int a = (angle >> 8) & 0xFF;                                    // move.b obj.var_3a(a0),d0: the word's high byte
        int radius = link * 16;                                         // move.b obj.var_3c(a0),d2; asl.w #4,d2
        int dx = (TrigLookupTable.sinHex(a) * radius) >> 8;             // mulu.w d2,d0; asr.l #4; asr.w #4
        int dy = (TrigLookupTable.cosHex(a) * radius) >> 8;
        x = (anchorX + dx) & 0xFFFF;
        y = (anchorY + dy) & 0xFFFF;
    }

    /** Routine2's tail: only the outermost piece goes on (:119-121). */
    void afterSwing(PlayableEntity player, int oldX, int oldY) { }

    /** CheckObjectDespawn2 on the anchor's X (move.w obj.var_36(a0),d0, :9-10). */
    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return anchorX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) drawFrame(SharedObjectArt.SWING, mapFrame, hFlip(), vFlip());
    }

    public int link() { return link; }
    public int mapFrame() { return mapFrame; }
}
