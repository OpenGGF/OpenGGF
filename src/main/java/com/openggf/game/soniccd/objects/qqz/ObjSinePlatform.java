package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.TrigLookupTable;

/**
 * Sine platform, object $29 ({@code SinePlatformObject}, objects/r5/sine_platform.asm:17-23): two top-solid
 * platforms ({@code $20} wide, {@code $10} tall, priority 3, tile {@code $4487}) that slide left half a
 * pixel a frame over a 256-pixel track centred on the object, wrapping from the left end to the right, while
 * bobbing on a cosine of a phase that advances {@code $100} (one angle step) per frame. The object itself is
 * the platform {@code $40} pixels right of its X, a second is made {@code $40} left of it; subtype 0 starts at
 * the top ({@code $8000}), others at the bottom (:25-86).
 *
 * <p>The riders are carried as the ROM does it (:139-155): TopSolidObject runs once with the X speed
 * cleared and a Y radius of {@code $12}, so a rider on the way down stays on the top, then again with the
 * speed restored and radius {@code $10}. The Y offset is {@code (cos * $6000 + $8000) >> 16} of a register
 * whose low word is left over from the X-speed arithmetic ({@code swap d1} puts the cosine in the high word and
 * the stale low word of {@code d1}, {@code $FFFF} after a normal step and 0 on the wrapping step, below it,
 * :112-123); that is reproduced. The parent also runs CheckObjectDespawn2 at its start X. Shipped
 * {@code FixBugs = 0}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjSinePlatform extends QqzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private ObjSinePlatform parent;     // oVar2A (null for the first platform)
    /** The slot of {@code parent} the native child stores and tests each turn ({@code cmpi.b #$29,obj.id(a1)}). */
    private int parentSlot = -1;
    private int originX;                // oVar2C
    private int leftBound;              // oVar2E, 16.16
    private int rightBound;             // oVar32, 16.16
    private int originY;                // oVar36
    private int angle;                  // oVar38
    private int xSpeed;                 // oXVel

    public ObjSinePlatform(ObjectSpawn spawn) {
        super(spawn, "Sine platform");
    }

    @Override public ObjectVisualFacing.Basis nativeFacing() { return ObjectVisualFacing.Basis.NON_DIRECTIONAL; }

    @Override protected boolean despawnsByCheckObjDespawn() { return parent == null; }

    @Override protected int despawnX() { return originX; }

    @Override protected String sheetKind() { return QqzObjectArt.SINE_PLATFORM; }

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
        if (parent != null && !slotHoldsId(parentSlot, 0x29)) {        // cmpi.b #$29,obj.id(a1); jmp DeleteObject
            deleteObject();
            drawThisFrame = false;
            return;
        }
        move();
        solidAndDraw();
    }

    /** SinePlatformInit (objects/r5/sine_platform.asm:25-86). */
    private void init() {
        originX = x;
        int left = (x << 16) - 0x800000;
        leftBound = left;
        rightBound = left + 0x800000 * 2;
        int platformX = x + 0x40;
        int startAngle = spawn.subtype() == 0 ? 0x8000 : 0;  // addi.w #$8000,d3 wraps to 0
        initPlatform(startAngle);
        x = platformX & 0xFFFF;
        var objects = services().objectManager();
        if (objects == null) return;
        ObjectSpawn childSpawn = new ObjectSpawn(platformX - 0x80, y, 0x29, spawn.subtype(), spriteFlip, false, 0);
        ObjSinePlatform child = new ObjSinePlatform(childSpawn);
        child.parent = this;
        child.parentSlot = getSlotIndex();
        child.leftBound = leftBound;
        child.rightBound = rightBound;
        child.originX = originX;
        child.initPlatform(startAngle);
        objects.addDynamicObject(child);                     // jsr SpawnObject
    }

    /** .InitFirstPlatform (objects/r5/sine_platform.asm:72-85). */
    private void initPlatform(int startAngle) {
        routine = 2;
        priority = 3;
        xRadius = 0x20;
        width = 0x20;
        yRadius = 0x10;
        tileWord = QqzObjectArt.SINE_PLATFORM_TILE;
        originY = y;
        angle = startAngle;
        initialised = true;
    }

    /** SinePlatformMain (objects/r5/sine_platform.asm:88-123): slide, then bob. */
    private void move() {
        int previous = xLong();
        int next = previous - 0x8000;
        if (!(next > leftBound)) {                           // cmp.l; bgt.s .SetX
            previous = rightBound;
            next = rightBound;
        }
        setXLong(next);
        int travelled = (next - previous) >> 8;              // sub.l d0,d1; asr.l #8,d1
        xSpeed = (short) travelled;
        int staleLowWord = (travelled >>> 16) & 0xFFFF;      // d1's upper word, which swap d1 moves to the low half
        angle = (angle + 0x100) & 0xFFFF;
        int cos = TrigLookupTable.cosHex(((short) angle >> 8) & 0xFF);
        int d1 = ((cos & 0xFFFF) << 16) | staleLowWord;      // swap d1
        d1 >>= 2;                                            // asr.l #2,d1
        int d0 = (d1 >> 1) + d1;                             // asr.l #1; add.l d1
        d0 += 0x8000;
        y = ((d0 >> 16) + originY) & 0xFFFF;                 // swap d0; add.w sine_ptfm.y(a0),d0
    }

    /** .Update and .Solid (objects/r5/sine_platform.asm:132-155). */
    private void solidAndDraw() {
        int saved = xSpeed;
        xSpeed = 0;
        yRadius = 0x12;
        boolean contact = solidObject(Entry.TOP);
        xSpeed = saved;
        yRadius = 0x10;
        if (contact) solidObject(Entry.TOP);
    }
}
