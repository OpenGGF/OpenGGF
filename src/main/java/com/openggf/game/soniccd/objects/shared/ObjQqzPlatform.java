package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Quartz Quadrant's platform, object $30 ({@code PlatformObject}, objects/r5/platform.asm:20-617; Wacky Workbench's is another
 * routine, {@link ObjWwzPlatform}). A top-solid platform of three widths (frame, width and conveyor flag per subtype from the
 * stage's {@code PlatformProperties}), with up to three springs, that is static, slides $30 or $20 pixels along X, slides $30 along Y,
 * rises when stood on until a ceiling is $30 above, or falls once stood on. A platform that is not moving up or down sinks up to four
 * pixels under a rider (:516-546). A conveyor form carries a rider along X by the time zone's speed, reversed by
 * {@code r5_conveyor_reverse} (:571-615).
 */
public final class ObjQqzPlatform extends CdObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** {@code ptfm.flags} bit 7: sinking is enabled; bit 0: conveyor (:77-131). */
    private static final int FLAG_SINK = 0x80, FLAG_CONVEYOR = 1;
    /** PlatformSink: {@code cmpi.b #$40,ptfm.sink} (:528-531). */
    private static final int SINK_MAX = 0x40;
    /** PlatformMoveUp / PlatformFall timers: {@code move.b #30,ptfm.timer} (:358-360, :430-434). */
    private static final int TIMER = 30;
    /** The subtype table has 25 entries (:50-75). */
    private static final int TYPES = 25;

    private int routine;
    private int xSub;
    private int ySub;
    /** {@code obj.x_speed} / {@code obj.y_speed}. */
    private int xSpeed;
    private int ySpeed;
    private int originX;                // ptfm.x
    private int originY;                // ptfm.y
    private int offset;                 // ptfm.offset (word)
    private int speed;                  // ptfm.speed (word)
    private int timer;                  // ptfm.timer
    private boolean directionLeft;      // ptfm.direction
    private int standState;             // ptfm.stood_on
    private int sink;                   // ptfm.sink
    private int flags;                  // ptfm.flags
    private int frame;
    private AbstractPlayableSprite rider;

    public ObjQqzPlatform(ObjectSpawn spawn) {
        super(spawn, "Platform");
        originX = spawn.x();
        originY = spawn.y();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        rider = player instanceof AbstractPlayableSprite sprite ? sprite : null;
        if (routine == 0 && !init()) return;
        main();
    }

    /** PlatformInit (:163-232): sprite, size and flags from the subtype, then the springs. */
    private boolean init() {
        var provider = artProvider();
        var level = cdLevel();
        if (provider == null || level == null) return false;
        routine = 2;
        priority = 3;
        tileWord = SharedObjectCatalogue.offset(level.payload(), act() == 2 ? "qqzPlatformTileAct3" : "qqzPlatformTile");
        originX = x;
        originY = y;
        yRadius = 0x10;
        var image = provider.image();
        int type = getSpawn().subtype() & 0x1F;
        if (type >= TYPES) return false;
        int properties = SharedObjectCatalogue.offset(level.payload(), "qqzPlatformProperties") + type * 2;
        frame = image.u8(properties);
        width = image.u8(properties + 1);
        flags = image.u8(SharedObjectCatalogue.offset(level.payload(), "qqzPlatformConveyorFlags") + type);
        int springs = image.u8(SharedObjectCatalogue.offset(level.payload(), "qqzPlatformSpringCounts") + type);
        switch (springs) {
            case 1 -> spawnSpring(0);
            case 2 -> { spawnSpring(-0x10); spawnSpring(0x10); }
            case 3 -> { spawnSpring(-0x20); spawnSpring(0); spawnSpring(0x20); }
            default -> { }
        }
        return true;
    }

    /** PlatformSpawnSpring (:552-566): an upward yellow spring attached 24 pixels above, {@code d2} to the side. */
    private void spawnSpring(int dx) {
        var objects = services().objectManager();
        if (objects == null) return;
        ObjectSpawn spawn = new ObjectSpawn(x, y, 0x0A, 2, 0, false, 0);
        objects.addDynamicObject(new SharedAttachedSpring(spawn, this, dx, -0x18));
    }

    /** PlatformMain (:235-246): the type's movement, sinking, the conveyor, then TopSolidObject. */
    private void main() {
        switch (getSpawn().subtype() & 0x1F) {
            case 0, 7, 17, 18 -> flags |= FLAG_SINK;                         // PlatformStatic
            case 1, 2, 12, 13, 14, 20, 21, 22 -> moveX48();
            case 3, 4, 10, 11, 19 -> moveY48();
            case 8, 9 -> moveX32();
            case 5, 15, 23 -> moveUp();
            case 6, 16, 24 -> fall();
            default -> { return; }
        }
        if (isDestroyed()) return;
        sink();
        conveyor();
        if (rider != null) solidObject(Entry.TOP);                           // PlatformSolid (:40-48)
    }

    // ---- Types ---------------------------------------------------------------------------------------

    /** PlatformMoveX48 (:287-296). */
    private void moveX48() {
        flags |= FLAG_SINK;
        move(0x30);
        moveX();
    }

    /** PlatformMoxeX32 (:337-347). */
    private void moveX32() {
        flags |= FLAG_SINK;
        move(0x20);
        moveX();
    }

    /** PlatformMoveX (:298-315): the speed, reversed by subtype bit 7, applied to the 16.16 X. */
    private void moveX() {
        int velocity = (short) speed;
        if ((getSpawn().subtype() & 0x80) != 0) velocity = (short) -velocity;
        xSpeed = velocity & 0xFFFF;
        int position = ((x & 0xFFFF) << 16 | xSub) + (velocity << 8);
        x = position >>> 16;
        xSub = position & 0xFFFF;
    }

    /** PlatformMoveY48 (:317-335). */
    private void moveY48() {
        move(0x30);
        int velocity = (short) speed;
        if ((getSpawn().subtype() & 0x80) != 0) velocity = (short) -velocity;
        addToY(velocity << 8);
    }

    /**
     * PlatformMove (:483-514): accelerate by 4 toward the end of the range {@code range}; the turn tests the high byte of the
     * offset word ({@code cmp.b ptfm.offset(a0),d4}), unsigned.
     */
    private void move(int range) {
        int acceleration = 4;
        if (!directionLeft) {
            speed = (speed + acceleration) & 0xFFFF;
            offset = (offset + speed) & 0xFFFF;
            if (Integer.compareUnsigned(range, offset >> 8) <= 0) directionLeft = true;       // cmp.b; bhi.s .End
        } else {
            speed = (speed - acceleration) & 0xFFFF;
            offset = (offset + speed) & 0xFFFF;
            if (Integer.compareUnsigned(range, offset >> 8) > 0) directionLeft = false;       // cmp.b; bls.s .End
        }
    }

    /** PlatformFall (:349-402): once stood on, sink for 30 frames, then fall until off the screen. */
    private void fall() {
        if (standState == 0) {
            flags |= FLAG_SINK;
            if (rider == null || !stoodOn(rider)) return;
            timer = TIMER;
            standState = 1;
        }
        if (timer != 0) {
            timer = (timer - 1) & 0xFF;
            if (timer != 0) return;
            flags &= ~FLAG_SINK;
        }
        yRadius = 0x10;                                                      // .SetHeight (:369-378)
        if (Integer.compareUnsigned(ySpeed & 0xFFFF, 0x200) >= 0) yRadius = 0x12;
        if (Integer.compareUnsigned(ySpeed & 0xFFFF, 0x300) >= 0) yRadius = 0x14;
        if (Integer.compareUnsigned(ySpeed & 0xFFFF, 0x400) < 0) ySpeed = (ySpeed + 0x20) & 0xFFFF;   // .Fall
        addToY((short) ySpeed << 8);
        int camY = services().camera().getY() & 0xFFFF;                      // scroll_fg_y
        if (Integer.compareUnsigned((camY + 0xE0) & 0xFFFF, y & 0xFFFF) >= 0) return;
        if (rider != null) ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), rider, this);
        setDestroyed(true);                                                  // jmp DeleteObject
    }

    /** PlatformMoveUp (:408-481): wait for a rider, then rise at up to -$300 until a ceiling is $30 above. */
    private void moveUp() {
        switch (standState) {
            case 0 -> {                                                      // PlatformMoveUpWait
                flags |= FLAG_SINK;
                if (rider == null || !stoodOn(rider)) return;
                standState += 2;
                timer = TIMER;
                moveUpDo();
            }
            case 2 -> moveUpDo();
            default -> { }                                                   // PlatformMoveUpDone
        }
    }

    /** PlatformMoveUpDo (:436-470). */
    private void moveUpDo() {
        if (timer != 0) {
            timer = (timer - 1) & 0xFF;
            if (timer != 0) return;
            flags &= ~FLAG_SINK;
            yRadius = 0xE;
        }
        int d0 = (ySpeed - 8) & 0xFFFF;                                      // .Accelerate
        if (Integer.compareUnsigned(d0, 0xFD00) < 0) d0 = 0xFD00;            // cmpi.w #-$300,d0; bcc
        ySpeed = d0;
        addToX((short) xSpeed << 8);                                         // jsr MoveObject
        addToY((short) ySpeed << 8);
        int distance = ObjectTerrainUtils.checkCeilingDist(x, y - 0x30, yRadius).distance();   // CheckBlockUp at y - $30
        if (distance >= 0) return;
        yRadius = 0x10;
        ySpeed = 0;
        originY = y;
        flags |= FLAG_SINK;
        standState += 2;
    }

    // ---- Sinking and the conveyor ---------------------------------------------------------------------

    /** PlatformSink (:516-546). */
    private void sink() {
        if ((flags & FLAG_SINK) == 0) return;
        if (rider != null && stoodOn(rider)) {
            if (sink == SINK_MAX) return;
            sink += 8;
        } else {
            if (sink == 0) return;
            sink -= 8;
        }
        int sine = TrigLookupTable.sinHex(sink & 0xFF);
        y = (originY + ((sine * 0x400) >> 16)) & 0xFFFF;                     // muls.w #$400,d0; swap d0; add.w ptfm.y
    }

    /** PlatformConveyor (:571-615): a rider of a conveyor platform moves by the time zone's step, reversed by the switch. */
    private void conveyor() {
        if ((flags & FLAG_CONVEYOR) == 0 || rider == null || !stoodOn(rider) || !rider.isOnObject()) return;
        int step = switch (timeZone()) {
            case 0 -> 0x8000;
            case 1 -> 0x10000;
            default -> 0x20000;
        };
        if (conveyorReverse()) step = -step;
        NativePositionOps.addXPos16_16(rider, step);
    }

    private boolean conveyorReverse() {
        var level = cdLevel();
        var parallax = services().parallaxManager();
        return level != null && parallax != null
                && parallax.getHandler(com.openggf.game.soniccd.SonicCdZoneRegistry.zoneIndex(level.payload()))
                instanceof com.openggf.game.soniccd.zones.qqz.QqzScrollHandler handler
                && handler.conveyorReverse();
    }

    private void addToX(int velocity) {
        int position = ((x & 0xFFFF) << 16 | xSub) + velocity;
        x = position >>> 16;
        xSub = position & 0xFFFF;
    }

    private void addToY(int velocity) {
        int position = ((y & 0xFFFF) << 16 | ySub) + velocity;
        y = position >>> 16;
        ySub = position & 0xFFFF;
    }

    // ---- Solidity and lifetime ----------------------------------------------------------------------------

    @Override public int solidXVelocity() { return (short) xSpeed; }

    @Override public int solidYVelocity() { return (short) ySpeed; }

    /** CheckObjectDespawn2 on the placed X (:25-26). */
    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override protected int despawnX() { return originX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine != 0) drawFrame(SharedObjectArt.QQZ_PLATFORM, frame, hFlip(), vFlip());
    }
}
