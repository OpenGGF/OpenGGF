package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.TrigLookupTable;

/**
 * Circle platform, object $27 ({@code CirclePlatformObject}, objects/r5/circle_platform.asm:15-21): eight
 * solid {@code 16 x 16} platforms (priority 3, tile {@code $4300}) circling the object at a radius of 96
 * pixels ({@code 0.375 * 256}); the object itself is the first. They form four pairs a quarter turn apart,
 * the second of each pair {@code $800} of the angle word ahead of the first (:23-69). The turning speed is
 * {@code $80}, {@code $C0} or {@code $100} by time zone, negated for a non-zero subtype
 * ({@code CirclePtfmProperties}, :159-162).
 *
 * <p>Riders are carried as in the sine platform (:118-143): SolidObject runs with the X speed cleared and a
 * Y radius of {@code $A}, then again with the speed ({@code (new X - old X) >> 8}) and radius 8. Only the
 * first platform runs CheckObjectDespawn2 at the origin X (:144-150); the others delete themselves when it
 * is gone. The initialisation frame returns without drawing (:65-69). Shipped {@code FixBugs = 0}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjCirclePlatform extends QqzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** CirclePtfmProperties (objects/r5/circle_platform.asm:159-162): speed by time zone (the adjacent count is 2-1 in each row). */
    private static final int[] SPEEDS = {0x80, 0xC0, 0x100};

    private ObjCirclePlatform parent;   // circle_ptfm.parent
    /** The slot of {@code parent} the native child stores and tests each turn ({@code cmpi.b #$27,obj.id(a1)}). */
    private int parentSlot = -1;
    private int originXLong;            // circle_ptfm.x, 16.16
    private int originYLong;            // circle_ptfm.y, 16.16
    private int angle;                  // circle_ptfm.angle
    private int speed;                  // circle_ptfm.speed
    private int xSpeed;                 // oXVel

    public ObjCirclePlatform(ObjectSpawn spawn) {
        super(spawn, "Circle platform");
    }

    @Override public ObjectVisualFacing.Basis nativeFacing() { return ObjectVisualFacing.Basis.NON_DIRECTIONAL; }

    @Override protected boolean despawnsByCheckObjDespawn() { return parent == null; }

    @Override protected int despawnX() { return originXLong >> 16; }

    @Override protected String sheetKind() { return QqzObjectArt.CIRCLE_PLATFORM; }

    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }
    @Override public boolean solidRenderedOnScreen() { return renderFlagOnScreen(); }
    @Override public int solidXVelocity() { return xSpeed; }

    @Override
    protected void run(PlayableEntity player) {
        if (standDownInBossAct()) return;
        if (routine == 0) {
            init();
            drawThisFrame = false;
            skipDespawnThisFrame = true;                           // .End: rts, no DrawObject this frame
            return;
        }
        if (parent != null && !slotHoldsId(parentSlot, 0x27)) {        // cmpi.b #$27,obj.id(a1); jmp DeleteObject
            deleteObject();
            drawThisFrame = false;
            return;
        }
        move();
        solidAndDraw();
    }

    /** CirclePlatformInit (objects/r5/circle_platform.asm:23-69). */
    private void init() {
        int tz = Math.max(0, Math.min(2, liveTimeZone()));
        int turning = spawn.subtype() == 0 ? SPEEDS[tz] : -SPEEDS[tz];   // neg.w d1 for a reverse platform
        speed = turning;
        initPlatform(0, this, turning);
        var objects = services().objectManager();
        if (objects == null) return;
        int angleBase = 0;                                   // d4: the quadrant
        boolean first = true;                                // the first platform is this object
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            int pairAngle = angleBase;                       // d3
            for (int adjacent = 0; adjacent <= 1; adjacent++, pairAngle = (pairAngle + 0x800) & 0xFFFF) {
                if (first) {
                    first = false;                           // .InitFirstPlatform with a1 = a0
                    angle = pairAngle & 0xFFFF;
                    continue;
                }
                ObjectSpawn childSpawn = new ObjectSpawn(x, y, 0x27, spawn.subtype(), spriteFlip, false, 0);
                ObjCirclePlatform child = new ObjCirclePlatform(childSpawn);
                child.initPlatform(pairAngle & 0xFFFF, this, turning);
                objects.addDynamicObject(child);             // jsr SpawnObject
                if (child.isDestroyed()) return;             // bne.s .End
            }
            angleBase = (angleBase + 0x4000) & 0xFFFF;
        }
    }

    /** .Initialize (objects/r5/circle_platform.asm:71-91): what each of the eight platforms is given. */
    private void initPlatform(int startAngle, ObjCirclePlatform head, int turning) {
        routine = 2;
        priority = 3;
        xRadius = 8;
        width = 8;
        yRadius = 8;
        tileWord = QqzObjectArt.CIRCLE_PLATFORM_TILE;
        parent = head == this ? null : head;
        parentSlot = parent == null ? -1 : parent.getSlotIndex();                 // clr.w circle_ptfm.parent(a0) for the first
        originXLong = head.xLong();
        originYLong = head.yLong();
        angle = startAngle;
        speed = turning;
        initialised = true;
    }

    /** CirclePlatformMain .Move (objects/r5/circle_platform.asm:102-124). */
    private void move() {
        angle = (angle + speed) & 0xFFFF;
        int hex = (angle >> 8) & 0xFF;                       // move.b circle_ptfm.angle(a0),d0
        int d1 = TrigLookupTable.cosHex(hex) << 16;          // swap d1
        int d0 = TrigLookupTable.sinHex(hex) << 16;          // swap d0
        d1 >>= 2;
        d0 >>= 2;
        int d5 = d1;
        int d4 = d0;
        d5 >>= 1;
        d4 >>= 1;
        d5 += d1;
        d4 += d0;
        d5 += originXLong;
        d4 += originYLong;
        int previous = xLong();
        setXLong(d5);
        setYLong(d4);
        xSpeed = (short) ((d5 - previous) >> 8);             // sub.l d3,d5; asr.l #8,d5; move.w d5,obj.x_speed(a0)
    }

    /** objects/r5/circle_platform.asm:126-143. */
    private void solidAndDraw() {
        int saved = xSpeed;
        xSpeed = 0;
        yRadius = 0xA;
        boolean contact = solidObject(Entry.SOLID);
        xSpeed = saved;
        yRadius = 8;
        if (contact) solidObject(Entry.SOLID);
    }
}
