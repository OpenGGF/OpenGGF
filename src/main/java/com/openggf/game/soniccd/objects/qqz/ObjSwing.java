package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.SonicCdZoneRegistry;
import com.openggf.game.soniccd.zones.qqz.QqzScrollHandler;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Swing, object $2A in the R5 index of acts 1 and 2 ({@code SwingObject}, objects/r5/swing.asm:16-28): a chain that
 * swings from the placed object, ending in a $40 wide top-solid platform. The placed object (secondary subtype 0,
 * {@code SwingInit}, :30-80) makes {@code length + 1} children with {@code SpawnObjectAfter}: {@code length} chain links
 * (secondary subtype 1) and the platform (the last, secondary subtype $FF), stores their slot numbers and positions
 * them every frame ({@code SwingMain}, :82-160): an angle that runs at speed $100 and, with a maximum, turns back at
 * it, and a chain laid out along the angle's sine and cosine. The layout subtype picks {@code SwingProperties} (a
 * length and a maximum angle per pair of subtypes, :265-269) and bit 0 reverses the direction. The links and the
 * platform delete themselves when the slot they keep for the placed object no longer holds {@code $2A} (:213-221); only
 * the placed object runs CheckObjectDespawn. The platform is solid from the top ({@code SwingPlatformSolid}, :230-259):
 * a probe without its X speed and with a taller box, then, on contact, the real call, and the conveyor moves the rider by
 * 2 pixels (the other way while {@code r5_conveyor_reverse} is set).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjSwing extends QqzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private static final int ID = 0x2A;
    private enum Role { MAIN, LINK, PLATFORM }

    private final Role role;
    /** swing.parent (oVar2A): the placed object's slot, tested each turn by the links and the platform. */
    private int parentSlot = -1;
    /** swing.angle and swing.speed: 32-bit values whose low word steps the angle byte (the high byte of the word). */
    private int angle;
    private int maxAngle;
    private int speed;
    private int length;
    /** swing.links (oVar38..): the slot of each chain link and, last, the platform. */
    private int[] childSlots = new int[0];
    private int xSpeed;

    public ObjSwing(ObjectSpawn spawn) {
        super(spawn, "Swing");
        this.role = Role.MAIN;
    }

    private ObjSwing(ObjectSpawn spawn, Role role, int parentSlot) {
        super(spawn, "Swing", role == Role.LINK ? 1 : 0xFF);
        this.role = role;
        this.parentSlot = parentSlot;
    }

    @Override public ObjectVisualFacing.Basis nativeFacing() { return ObjectVisualFacing.Basis.NON_DIRECTIONAL; }

    @Override protected boolean despawnsByCheckObjDespawn() { return role == Role.MAIN; }

    @Override protected String sheetKind() { return QqzObjectArt.SWING; }

    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }
    @Override public boolean solidRenderedOnScreen() { return renderFlagOnScreen(); }
    @Override public int solidXVelocity() { return xSpeed; }

    @Override
    protected void run(PlayableEntity player) {
        if (standDownInBossAct()) return;
        switch (role) {
            case MAIN -> main();
            case LINK -> link();
            case PLATFORM -> platform(player);
        }
    }

    // ---- the placed object ---------------------------------------------------------------------------------

    private void main() {
        if (routine == 0) {
            if (!init()) return;
        }
        swingMain();
    }

    /** SwingInit (objects/r5/swing.asm:30-80). False when a child could not be placed ({@code bne.w SwingDelete}). */
    private boolean init() {
        routine = 2;
        priority = 3;
        xRadius = 8;
        width = 8;
        yRadius = 8;
        tileWord = QqzObjectArt.SWING_TILE;
        mapFrame = 0;
        initialised = true;
        int table = QqzObjectCatalogue.offset(artProvider().payload(), "swingProperties");
        int entry = table + ((spawn.subtype() & 0xFE) << 1);                   // andi.w #$FFFE,d0; add.w d0,d0
        var image = artProvider().image();
        length = table < 0 ? 0 : image.u16(entry);                              // move.w (a1)+,swing.length(a0)
        maxAngle = table < 0 ? 0 : image.u16(entry + 2);                        // move.w (a1),swing.max_angle+2(a0)
        speed = 0x100;                                                          // move.w #$100,swing.speed+2(a0)
        if ((spawn.subtype() & 1) != 0) {                                       // btst #0,obj.subtype(a0)
            maxAngle = -maxAngle;
            speed = -speed;
        }
        childSlots = new int[length + 1];
        for (int i = 0; i <= length; i++) {                                     // moveq/dbf: length + 1 children
            ObjectSpawn childSpawn = new ObjectSpawn(x, y, ID, 0, spriteFlip, false, 0);
            boolean last = i == length;
            ObjSwing child = new ObjSwing(childSpawn, last ? Role.PLATFORM : Role.LINK, getSlotIndex());
            if (!findNextObjSlot(child)) {                                      // jsr SpawnObjectAfter; bne.w SwingDelete
                deleteObject();
                return false;
            }
            childSlots[i] = child.getSlotIndex();                               // lsr.w #6,d0; move.b d0,(a6)+
        }
        return true;
    }

    /** SwingMain (objects/r5/swing.asm:82-160). */
    private void swingMain() {
        int d6 = angle + speed;                                                 // move.l swing.angle-2(a0),d6; add.l swing.speed(a0),d6
        int d5 = maxAngle;
        if (d5 != 0) {                                                          // beq.s .UpdateAngle
            int d1 = d6;
            int d0 = d5;
            if (d5 < 0) {                                                       // bpl.s .CheckMinAngle
                d1 = -d1;
                d0 = -d0;
            }
            if (d1 < 0) {                                                       // tst.l d1; bge.s .CheckMaxAngle
                d6 = 0;                                                         // cap at the minimum angle, then reverse
                speed = -speed;
            } else if (d1 > d0) {                                               // cmp.l d0,d1; ble.s .UpdateAngle
                d6 = d5;
                speed = -speed;
            }
        }
        angle = d6;
        int hex = (angle >> 8) & 0xFF;                                          // move.b swing.angle(a0),d0
        int cos = (TrigLookupTable.cosHex(hex) << 16) >> 4;                     // swap d1; asr.l #4,d1
        int sin = (TrigLookupTable.sinHex(hex) << 16) >> 4;
        int d5x = xLong();
        int d4y = yLong();
        for (int i = 0; i < length; i++) {                                      // loc_20E8BC: the chain links
            d5x += cos;
            d4y += sin;
            if (slotOccupant(childSlots[i]) instanceof ObjSwing link) {
                link.setXLong(d5x);
                link.setYLong(d4y);
            }
        }
        d5x += cos;
        d4y += sin;
        if (slotOccupant(childSlots[length]) instanceof ObjSwing platform) {   // the platform: X speed from the distance moved
            int before = platform.xLong();
            platform.setXLong(d5x);
            platform.setYLong(d4y);
            platform.xSpeed = (short) ((d5x - before) >> 8);                    // sub.l d3,d5; asr.l #8,d5; move.w d5,obj.x_speed(a1)
        }
    }

    // ---- chain link and platform ---------------------------------------------------------------------------

    /** SwingLinkObject (:162-182) and SwingLinkUpdate (:213-221). */
    private void link() {
        if (routine == 0) {                                                     // SwingLinkInit: sets up, then rts
            routine = 2;
            mapFrame = 1;
            xRadius = 8;
            width = 8;
            yRadius = 8;
            priority = 3;
            tileWord = QqzObjectArt.SWING_TILE;
            initialised = true;
            return;
        }
        parentCheck();
    }

    /** SwingPlatformObject (:184-211). */
    private void platform(PlayableEntity player) {
        if (routine == 0) {                                                     // SwingPlatformInit, then the update
            routine = 2;
            mapFrame = 2;
            xRadius = 0x20;
            width = 0x20;
            yRadius = 8;
            priority = 2;
            tileWord = QqzObjectArt.SWING_PLATFORM_TILE;
            initialised = true;
        }
        platformSolid(player);
        parentCheck();
    }

    /** SwingLinkUpdate (objects/r5/swing.asm:213-221): {@code cmpi.b #$2A,obj.id(a1); bne.s SwingDelete}. */
    private void parentCheck() {
        if (!slotHoldsId(parentSlot, ID)) {
            deleteObject();
            drawThisFrame = false;
        }
    }

    /** SwingPlatformSolid (objects/r5/swing.asm:230-259). */
    private void platformSolid(PlayableEntity player) {
        int saved = xSpeed;                                                     // move.w obj.x_speed(a0),-(sp)
        xSpeed = 0;
        yRadius = 0xA;                                                          // extend the height
        boolean contact = solidObject(Entry.TOP);
        xSpeed = saved;
        yRadius = 8;
        if (!contact) return;                                                   // tst.b d0; beq.s .End
        if (!solidObject(Entry.TOP)) return;                                    // jsr TopSolidObject; beq.s .End
        int step = conveyorReverse() ? -2 : 2;                                  // moveq #2,d0; tst.b r5_conveyor_reverse; neg.w d0
        if (player instanceof AbstractPlayableSprite sprite) NativePositionOps.addXPosPreserveSubpixel(sprite, step);
    }

    private boolean conveyorReverse() {
        var level = cdLevel();
        var parallax = services().parallaxManager();
        return level != null && parallax != null
                && parallax.getHandler(SonicCdZoneRegistry.zoneIndex(level.payload())) instanceof QqzScrollHandler h
                && h.conveyorReverse();
    }
}
