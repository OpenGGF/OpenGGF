package com.openggf.game.soniccd.objects.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.ObjectTerrainUtils;

/**
 * Bata-pyon, object $30 ({@code ObjBataPyon}, Level/Wacky Workbench/Objects/Bata-pyon/Main.asm:18-244): a
 * hopping badnik that falls to the floor at 7 pixels a frame (gravity $2000, 16.16), lands, waits, and
 * jumps at -6 (subtype 0: eight hops, $A000/65536 pixels a frame left) before turning round at a wall
 * or after its hops; subtype 1 is the damaged variant (four hops, slower, with a stutter while it waits).
 * It swaps between a tall frame (radius 28, collision $30) and a short one (radius 19, collision $2F) as
 * it lands and jumps.
 */
public final class ObjBataPyon extends WwzBadnik {
    private static final int FALL = 2, LANDED = 4, JUMP = 6;
    /** {@code move.l #$70000,oBataYVel(a0)} (:47, :65, :99). */
    private static final int MAX_FALL_SPEED = 0x70000;
    /** {@code addi.l #$2000,oBataYVel(a0)} (:96, :188). */
    private static final int GRAVITY = 0x2000;

    private final boolean damaged;
    private int xVel;
    private int yVel;
    private int jumpsLeft;
    private int jumpCount;
    private int sensorX;
    private boolean wallLeft;
    private int landTime;
    private int floorDist;
    private boolean initialised;

    public ObjBataPyon(ObjectSpawn spawn) {
        super(spawn, "BataPyon");
        this.damaged = spawn.subtype() != 0;
    }

    @Override
    protected void run(PlayableEntity player) {
        switch (routine) {
            case 0 -> {
                init();
                fall();
            }
            case FALL -> fall();
            case LANDED -> landed();
            case JUMP -> jump();
            default -> { }
        }
    }

    /** ObjBataPyon_Init (:39-68); it falls through into ObjBataPyon_Fall. */
    private void init() {
        routine = FALL;
        priority = 3;
        tileWord = WwzObjectArt.BATA_TILE;
        xRadius = 16;
        width = 16;
        yVel = MAX_FALL_SPEED;
        wallLeft = true;                         // move.l #ObjGetLWallDist,oBataWallChk(a0)
        sensorX = -16;
        mapFrame = 1;
        swapFrames();                            // mapFrame 1 -> frame 0, radius 19, collision $2F
        xVel = damaged ? -0x8000 : -0xA000;
        jumpCount = damaged ? 3 : 7;
        jumpsLeft = jumpCount + 1;
        initialised = true;
    }

    /** ObjBataPyon_Fall (:72-116). */
    private void fall() {
        addToX(xVel);
        addToY(yVel);
        floorDist = ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();
        int wall = wallDistance();
        if (wall < 0) {
            if (floorDist >= 0 || wall <= floorDist) {           // bpl.w Flip / cmp.w colDist,d1 / ble.w Flip
                flip();
            } else {
                touchDown();
            }
            return;
        }
        if (floorDist < 0) {
            touchDown();
            return;
        }
        yVel += GRAVITY;
        if (yVel >= MAX_FALL_SPEED) yVel = MAX_FALL_SPEED;       // cmpi.l / blt.s .End
    }

    /** .Landed (:104-116). */
    private void touchDown() {
        routine = LANDED;
        y = (y + floorDist) & 0xFFFF;                            // add.w d0,oY(a0)
        landTime = damaged ? 20 : 1;
    }

    /** ObjBataPyon_Landed (:120-160). */
    private void landed() {
        if (damaged) {
            int d6 = 7;
            switch (landTime) {
                case 6, 15 -> { y = (y + d6) & 0xFFFF; swapFrames(); }   // .StutterDown
                case 11, 18 -> { y = (y - d6) & 0xFFFF; swapFrames(); }  // .StutterUp: neg.w d6
                default -> { }
            }
        }
        landTime = (landTime - 1) & 0xFFFF;                      // .CheckJump
        if (landTime != 0) return;
        routine = JUMP;
        y = (y - 7) & 0xFFFF;
        swapFrames();
        yVel = damaged ? -0x50000 : -0x60000;
        jumpsLeft = (jumpsLeft - 1) & 0xFF;                      // subq.b #1,oBataJumpsLeft / bmi.s Flip
        if ((jumpsLeft & 0x80) != 0) flip();
    }

    /** ObjBataPyon_Jump (:164-200). */
    private void jump() {
        addToX(xVel);
        addToY(yVel);
        int ceiling = ObjectTerrainUtils.checkCeilingDist(x, y, yRadius).distance();   // jsr ObjGetCeilDist
        int wall = wallDistance();
        boolean hitCeiling;
        if (wall < 0) {
            if (ceiling >= 0 || wall <= ceiling) {
                flip();
                return;
            }
            hitCeiling = true;
        } else if (ceiling < 0) {
            hitCeiling = true;
        } else {
            yVel += GRAVITY;
            if (yVel < 0) return;                                // bpl.s .Falling / rts
            hitCeiling = false;
        }
        if (hitCeiling) {
            y = (y - ceiling) & 0xFFFF;                          // sub.w d0,oY(a0)
            yVel = 0;
        }
        routine -= 4;                                            // .Falling
        y = (y - 11) & 0xFFFF;
        swapFrames();
    }

    /** ObjBataPyon_Flip (:204-220): turn round, reset the hop count, swap the wall sensor. */
    private void flip() {
        jumpsLeft = jumpCount;
        flipHorizontally();
        xVel = -xVel;
        sensorX = -sensorX;
        wallLeft = !wallLeft;
    }

    /** {@code jsr (a1)} with d3 = oBataSensorX: ObjGetLWallDist / ObjGetRWallDist (Collision Check.asm:213-250). */
    private int wallDistance() {
        int probe = x + sensorX;
        return wallLeft ? ObjectTerrainUtils.checkLeftWallDist(probe, y).distance()
                : ObjectTerrainUtils.checkRightWallDist(probe, y).distance();
    }

    /** ObjBataPyon_SwapFrames (:224-242). */
    private void swapFrames() {
        if (mapFrame != 0) {
            mapFrame = 0;
            yRadius = 19;
            colType = 0x2F;
        } else {
            mapFrame = 1;
            yRadius = 28;
            colType = 0x30;
        }
    }

    @Override
    protected String sheetKey() {
        return initialised ? WwzObjectArt.key(damaged ? WwzObjectArt.BATA_DAMAGED : WwzObjectArt.BATA) : null;
    }
}
