package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;

import java.util.List;

/**
 * Palmtree Panic's swinging platform, object $29 ({@code ObjSwing}, Palmtree Panic/Objects/Swing/Main.asm:20-196). The anchor
 * (part 0, frame 0) spawns {@code subtype & 15} pieces (FindObjSlot, link numbers count..1, frame 2; the outermost, link
 * {@code count}, frame 1) and each piece swings on the same pendulum: the angle velocity {@code oVar30} gains {@code oVar3E} a
 * frame to {@code oVar34} and then loses it to {@code oVar38}, the position angle {@code oVar3A} accumulates it, and a piece sits
 * at {@code (sin, cos) * link * 16 / 256} from the anchor (ObjSwing_Motion, :161-196; the {@code mulu.w} of a negative sine is
 * unsigned, but only the low word survives the shifts, so the result is the signed product shifted right). Subtype bits 4-7
 * select a row of ObjSwing_MotionData (:71-81). Only the outermost piece is solid (top only, Y radius 8 then $C, lifting a
 * player in contact onto it) and its X/Y velocity is its movement this frame (:137-159).
 *
 * <p>A piece is created in a zeroed slot, so its first movement runs from (0,0) as in the original. The despawn test is
 * {@code CheckObjDespawn2} on the anchor's X (:26-27). An anchor with a zero link count would spawn 256 pieces (a {@code dbf}
 * on a byte decrement, :105-123); no placement has one and it is not modelled. The second player slot ({@code objPlayerSlot2},
 * :67) is not a player in this build.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class PpzSwing extends CdObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private final int part;               // oSwingPart (oVar3C)
    private int count;                    // oSwingPartCount (oVar2F)
    private int centerX;                  // oSwingCenterX (oVar36)
    private int centerY;                  // oSwingCenterY (oVar32)
    private int angleVelocity;            // oSwingAngle (oVar30)
    private int angleStep;                // oSwingAngleStep (oVar3E)
    private int angleCurrent;             // oSwingAngleCurrent (oVar3A)
    private int angleMax;                 // oSwingAngleMax (oVar34)
    private int angleMin;                 // oSwingAngleMin (oVar38)
    private boolean reversing;            // oSwingReversing (oVar3D)
    private int mapFrame;
    private int xSpeed;
    private int ySpeed;
    private boolean initialised;

    public PpzSwing(ObjectSpawn spawn) {
        this(spawn, 0, 0, 0, 0, 0);
    }

    private PpzSwing(ObjectSpawn spawn, int part, int count, int centerX, int centerY, int mapFrame) {
        super(spawn, "Swing");
        this.part = part;
        this.count = count;
        this.centerX = centerX;
        this.centerY = centerY;
        this.mapFrame = mapFrame;
    }

    @Override public int solidXVelocity() { return xSpeed; }
    @Override public int solidYVelocity() { return ySpeed; }
    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return centerX; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialised && !init()) return;
        main(player);
    }

    /** ObjSwing_Init (:83-136); it falls into ObjSwing_Main on the same frame. */
    private boolean init() {
        var provider = artProvider();
        if (provider == null) return false;
        var entry = PpzObjectCatalogue.entry(provider.payload());
        priority = 3;
        tileWord = provider.objectTileId(PpzObjectArt.TILE_ID_SWING, subtype2());
        width = 0x18;
        yRadius = 8;
        if (part == 0) {                                        // tst.b oSwingPart(a0); bne.s .SetMotion
            centerX = x;
            centerY = y;
            count = getSpawn().subtype() & 0xF;
            var objects = services().objectManager();
            if (objects != null) {
                for (int link = count; link >= 1; link--) {   // .SpawnLoop: FindObjSlot, bne.s .SpawnNext
                    ObjectSpawn piece = new ObjectSpawn(0, 0, getSpawn().objectId(), getSpawn().subtype(), 0, false, 0);
                    objects.addDynamicObject(new PpzSwing(piece, link, count, centerX, centerY, link == count ? 1 : 2));
                }
            }
        }
        int row = entry.swingMotion() + ((getSpawn().subtype() & 0xF0) >> 1);   // .SetMotion
        var image = provider.image();
        angleCurrent = image.u16(row);
        angleStep = (short) image.u16(row + 2);
        angleMax = (short) image.u16(row + 4);
        angleMin = (short) image.u16(row + 6);
        initialised = true;
        return true;
    }

    /** ObjSwing_Main (:137-159). */
    private void main(PlayableEntity player) {
        int oldX = x;
        int oldY = y;
        motion();
        if (count == part) {                                   // cmp.b oSwingPart(a0),d4; bne.s .End
            xSpeed = (short) ((x - oldX) << 8);                // asr.l #8 of the 16.16 difference, move.w
            ySpeed = (short) ((y - oldY) << 8);
            solid(player);
        }
    }

    /** ObjSwing_Motion (:161-196). */
    private void motion() {
        if (!reversing) {
            angleVelocity = (short) (angleVelocity + angleStep);
            angleCurrent = (angleCurrent + angleVelocity) & 0xFFFF;
            reversing = angleVelocity == angleMax;             // seq oSwingReversing(a0)
        } else {
            angleVelocity = (short) (angleVelocity - angleStep);
            angleCurrent = (angleCurrent + angleVelocity) & 0xFFFF;
            if (angleVelocity == angleMin) reversing = false;
        }
        int angle = (angleCurrent >> 8) & 0xFF;                // move.b oSwingAngleCurrent(a0),d0: the word's high byte
        int dx = (TrigLookupTable.sinHex(angle) * part) >> 4;  // mulu.w #part*16; asr.l #4; asr.w #4
        int dy = (TrigLookupTable.cosHex(angle) * part) >> 4;
        y = (centerY + dy) & 0xFFFF;
        x = (centerX + dx) & 0xFFFF;
    }

    /** ObjSwing_Solid / ObjSwing_SolidPlayer (:37-69): height 8 then $C, and lift the player onto the top. */
    private void solid(PlayableEntity player) {
        yRadius = 8;
        boolean contact = solidObject(Entry.TOP);
        if (!contact) {
            yRadius = 0xC;
            contact = solidObject(Entry.TOP);
        }
        if (contact) {
            yRadius = 8;
            if (player != null && (y & 0xFFFF) >= (player.getCentreY() & 0xFFFF)) {   // cmp.w oY(a1),d0; bcs.s .End
                NativePositionOps.writeYPosPreserveSubpixel(player, y - yRadius - player.getYRadius() + 2);
            }
        }
        yRadius = 8;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        var provider = artProvider();
        if (provider == null) return;
        String key = PpzObjectArt.key(provider, PpzObjectArt.PPZ_SWING, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, false, false);
    }

    public int part() { return part; }
    public int mapFrame() { return mapFrame; }
}
