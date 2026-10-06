package slaytherobotnik.scene;

/**
 * An object moving as the game's own routines move it, in the ROM's units: positions in
 * 1/256 pixel (the 68000's 16.16 position with velocities added at bit 8), velocities in
 * 1/256 pixel a frame. Each step is one frame of the routine named on it, so a picture's
 * rabbit hops, rings fall and sparks scatter with the same arcs and timing as in a level.
 */
final class RomBody {
    /** MoveSprite's gravity (sonic3k.asm MoveSprite: addi.w #$38,y_vel). */
    static final int GRAVITY = 0x38;
    /** The lighter gravity bouncing rings, sparks and monitor icons add themselves after MoveSprite2. */
    static final int LIGHT_GRAVITY = 0x18;

    int x;
    int y;
    int xVel;
    int yVel;

    RomBody(float px, float py, int xVel, int yVel) {
        this.x = Math.round(px * 256);
        this.y = Math.round(py * 256);
        this.xVel = xVel;
        this.yVel = yVel;
    }

    float px() {
        return x / 256f;
    }

    float py() {
        return y / 256f;
    }

    /** MoveSprite: move by the current speed, then gravity $38 (the speed added is the old one). */
    void moveSprite() {
        x += xVel;
        y += yVel;
        yVel += GRAVITY;
    }

    /** MoveSprite2: move by the current speed, no gravity. */
    void moveSprite2() {
        x += xVel;
        y += yVel;
    }

    /**
     * One frame of a spilled ring falling (Obj_Bouncing_Ring loc_1A75C): MoveSprite2, then $18
     * gravity; when {@code testFloor} and it is moving down into the floor, it is put back on
     * top and bounces up with three quarters of its speed ({@code asr.w #2 / sub.w / neg.w}).
     * The ROM tests the floor only when {@code (V_int_run_count + slot) & 7} is 0, so its rings
     * sink a little between tests. Returns true on a bounce.
     */
    boolean fallRing(boolean testFloor, float floorY) {
        moveSprite2();
        yVel += LIGHT_GRAVITY;
        if (yVel < 0 || !testFloor) {
            return false;
        }
        int floor = Math.round(floorY * 256);
        if (y < floor) {
            return false;
        }
        y = floor;
        yVel = -(yVel - (yVel >> 2));
        return true;
    }

    /**
     * One frame of a ring drawn to a point (AttractedRing_Move, the Lightning Shield's pull):
     * on each axis it gains $30 towards the target, or $C0 while still moving away, then
     * MoveSprite2.
     */
    void attractTo(float tx, float ty) {
        xVel += pull(Math.round(tx * 256) >= x, xVel);
        yVel += pull(Math.round(ty * 256) >= y, yVel);
        moveSprite2();
    }

    private static int pull(boolean towardsPositive, int speed) {
        int d = 0x30;
        if (towardsPositive) {
            return speed < 0 ? d * 4 : d;
        }
        return speed >= 0 ? -d * 4 : -d;
    }

    /**
     * One frame of a monitor's icon rising (Obj_MonitorContents sub_1D820): while it still
     * moves up, MoveSprite2 and $18 gravity. Returns false once it has stopped (y_vel no longer
     * negative), when the ROM hands over the power-up and holds the icon for 30 frames.
     */
    boolean riseIcon() {
        if (yVel >= 0) {
            return false;
        }
        moveSprite2();
        yVel += LIGHT_GRAVITY;
        return true;
    }

    /**
     * One frame of a hopping animal (Obj_Animal loc_2CA3C): MoveSprite; once falling and at or
     * below the floor, it is put on the floor and hops again with {@code hopSpeed} (word_2C7EA's
     * second word: -$400 for the rabbit). Returns the mapping frame the ROM shows: 1 while
     * rising, 0 while falling.
     */
    int hop(float floorY, int hopSpeed) {
        moveSprite();
        if (yVel < 0) {
            return 1;
        }
        int floor = Math.round(floorY * 256);
        if (y >= floor) {
            y = floor;
            yVel = hopSpeed;
        }
        return 0;
    }
}
