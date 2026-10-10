package starpost.ruins;

import starpost.valley.Ground;

/** Offline reachability approximation for ChamberGen/Reach only. Not a gameplay controller.
 * Retained because daily generation explores thousands of short programs without loading an act;
 * native route tests, rather than this model, establish real playable reachability. */
public final class GenerationRunner {
    /** Sonic_Move: acceleration $C, deceleration $80, friction $C, top speed $600. */
    public static final float ACCEL = 0x0C / 256f;
    public static final float DECEL = 0x80 / 256f;
    public static final float FRICTION = 0x0C / 256f;
    public static final float TOP = 0x600 / 256f;
    /** Sonic_RollSpeed: rolling friction $6 (half of walking), braking $20. */
    public static final float ROLL_FRICTION = 0x06 / 256f;
    public static final float ROLL_BRAKE = 0x20 / 256f;
    /** Sonic_Jump $680, Sonic_JumpHeight's $400 cap on release, gravity $38, air acceleration $18. */
    public static final float JUMP = 0x680 / 256f;
    public static final float JUMP_RELEASE = 0x400 / 256f;
    public static final float GRAVITY = 0x38 / 256f;
    public static final float AIR_ACCEL = 0x18 / 256f;
    /** Sonic_SpinDash: $800 base, plus half the charge; each press adds $200 up to $800. */
    public static final float DASH_BASE = 0x800 / 256f;
    public static final float DASH_STEP = 0x200 / 256f;
    public static final float DASH_MAX = 0x800 / 256f;
    /**
     * Sonic 1 under water (Sonic_Water halves top speed, acceleration and deceleration; Sonic_Jump
     * uses son_jumpspeed-$300; Sonic_JumpHeight caps a released jump at $200; Sonic_MdJump takes
     * gravity-$10 back off ObjectFall's $38).
     */
    public static final float WATER_TOP = 0x300 / 256f;
    public static final float WATER_ACCEL = 0x06 / 256f;
    public static final float WATER_DECEL = 0x40 / 256f;
    public static final float WATER_JUMP = 0x380 / 256f;
    public static final float WATER_JUMP_RELEASE = 0x200 / 256f;
    public static final float WATER_GRAVITY = 0x10 / 256f;
    /** Ground this far above the feet just ahead is a wall; slopes rise less than that. */
    public static final int WALL = 12;
    /** A floor more than this far below the feet is a ledge to fall from. */
    public static final int DROP = 20;
    public static final int HALF_WIDTH = 9;
    /** Feet to head: Sonic's y radius is 19 standing and 14 rolling (twice that, tall). */
    public static final int STAND_HEIGHT = 38;
    public static final int ROLL_HEIGHT = 28;
    /** Sonic_Jump refuses to jump with less than 6 pixels of headroom. */
    public static final int JUMP_HEADROOM = 6;

    /** The terrain: per-pixel solidity, scanned for floors and walls. */

    public float x;
    public float y;            // feet
    public float speed;        // ground speed, or x speed in the air
    public float ySpeed;
    public boolean onGround = true;
    public boolean rolling;
    public boolean ducking;
    public boolean dashing;
    public float dashCharge;
    public boolean facingLeft;
    public boolean sprung;
    public boolean pushing;
    public int jumpedAt = -1;
    /** Sonic 1's water physics (the Ruins' flooded chambers); see {@link #setUnderwater}. */
    public boolean underwater;
    public GenerationRunner(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float top() {
        return underwater ? WATER_TOP : TOP;
    }

    private float accel() {
        return underwater ? WATER_ACCEL : ACCEL;
    }

    private float decel() {
        return underwater ? WATER_DECEL : DECEL;
    }

    /** Sonic_Move's friction is the acceleration. */
    private float friction() {
        return underwater ? WATER_ACCEL : FRICTION;
    }

    /** Sonic_RollSpeed: half the acceleration as friction, a quarter of the deceleration to brake. */
    private float rollFriction() {
        return underwater ? WATER_ACCEL / 2 : ROLL_FRICTION;
    }

    private float rollBrake() {
        return underwater ? WATER_DECEL / 4 : ROLL_BRAKE;
    }

    /** Sonic_JumpDirection: twice the acceleration. */
    private float airAccel() {
        return underwater ? WATER_ACCEL * 2 : AIR_ACCEL;
    }

    private float gravity() {

        return underwater ? WATER_GRAVITY : GRAVITY;
    }

    /** Feet to head for the current pose. */
    public int height() {
        return rolling || dashing || ducking ? ROLL_HEIGHT : STAND_HEIGHT;
    }

    /**
     * Enters or leaves water as Sonic_Water does: entering halves x speed and quarters y speed;
     * leaving doubles y speed, capped at -$1000. Returns true when Sonic crossed the surface
     * moving vertically (the splash).
     */
    public boolean setUnderwater(boolean value) {
        if (value == underwater) {
            return false;
        }
        underwater = value;
        if (value) {
            speed /= 2;
            ySpeed /= 4;
        } else {
            ySpeed = Math.max(-16, ySpeed * 2);
        }
        return ySpeed != 0;
    }

    /** One frame. Returns true when a jump started (for the jump sound). */
    public boolean step(Ground ground, boolean left, boolean right, boolean down, boolean jumpPressed,
            boolean jumpHeld, int tick) {

        boolean jumped = false;
        pushing = false;

        if (onGround) {
            if (dashing) {
                if (jumpPressed) {
                    dashCharge = Math.min(DASH_MAX, dashCharge + DASH_STEP);
                }
                dashCharge -= dashCharge / 32f; // the charge bleeds away while held
                if (!down) {
                    dashing = false;
                    rolling = true;
                    speed = (facingLeft ? -1 : 1) * (DASH_BASE + dashCharge / 2);
                }
            } else if (rolling) {
                if (left && speed > 0 || right && speed < 0) {
                    speed -= Math.signum(speed) * rollBrake();
                }
                speed -= Math.signum(speed) * Math.min(Math.abs(speed), rollFriction());
                if (Math.abs(speed) < 0.5f) {
                    rolling = false;
                    speed = 0;
                }
                if (jumpPressed && headroom(ground)) {
                    jumped = jump(tick);
                }
            } else {
                ducking = down && Math.abs(speed) < 1;
                if (ducking && jumpPressed) {
                    dashing = true;
                    dashCharge = 0;
                    ducking = false;
                } else if (jumpPressed && headroom(ground)) {
                    jumped = jump(tick);
                } else {
                    walk(left && !ducking, right && !ducking);
                    if (down && Math.abs(speed) >= 1) {
                        rolling = true;
                    }
                }
            }
            if (onGround) {
                moveAlongGround(ground);
            }
        } else {
            float release = underwater ? WATER_JUMP_RELEASE : JUMP_RELEASE;
            if (!jumpHeld && ySpeed < -release && jumpedAt >= 0 && !sprung) {
                ySpeed = -release;
            }
            float top = top();
            if (left) { speed=Math.max(-top,speed-airAccel()); facingLeft=true; }
            else if (right) { speed=Math.min(top,speed+airAccel()); facingLeft=false; }
            ySpeed=Math.min(16,ySpeed+gravity());
            moveInAir(ground);
        }
        return jumped;
    }

    /** Sonic_Jump's headroom check (always passes where the ground has no ceilings). */
    private boolean headroom(Ground ground) {
        int head = Math.round(y) - height();
        for (int dy = 1; dy <= JUMP_HEADROOM; dy++) {
            if (ground.ceiling(Math.round(x), head - dy)) {
                return false;
            }
        }
        return true;
    }

    private boolean jump(int tick) {
        onGround = false;
        ySpeed = -(underwater ? WATER_JUMP : JUMP);
        rolling = true;
        jumpedAt = tick;
        sprung = false;
        return true;
    }

    /** A spring underfoot: straight up at {@code power} pixels per frame. */
    public void spring(float power) {
        onGround = false;
        ySpeed = -power;
        rolling = false;
        dashing = false;
        sprung = true;
        jumpedAt = -1;
    }

    private void walk(boolean left, boolean right) {
        float top = top(), accel = accel(), decel = decel();
        if (left) {
            if (speed > 0) {
                speed -= decel;
                if (speed < 0) {
                    speed = -decel;
                }
            } else {
                speed = Math.max(-top, speed - accel);
                facingLeft = true;
            }
        } else if (right) {
            if (speed < 0) {
                speed += decel;
                if (speed > 0) {
                    speed = decel;
                }
            } else {
                speed = Math.min(top, speed + accel);
                facingLeft = false;
            }
        } else {
            speed -= Math.signum(speed) * Math.min(Math.abs(speed), friction());
        }
    }

    private void moveAlongGround(Ground ground) {
        float nx = clampX(ground, x + speed);
        int probe = Math.round(nx + Math.signum(speed) * HALF_WIDTH);
        if (speed != 0 && ground.solid(probe, Math.round(y) - WALL)) {
            // Ground a wall's height above the feet just ahead: stop against it.
            pushing = true;
            speed = 0;
            rolling = false;
            return;
        }
        x = nx;
        int floor = feetFloor(ground, Math.round(y) - WALL + 1);
        if (floor > y + DROP) {
            onGround = false; // walked off a ledge
            ySpeed = 0;
            jumpedAt = -1;
        } else {
            y = floor;
        }
    }

    private void moveInAir(Ground ground) {
        float nx = clampX(ground, x + speed);
        int probe = Math.round(nx + Math.signum(speed) * HALF_WIDTH);
        if (speed != 0 && ground.solid(probe, Math.round(y) - WALL)) {

            speed = 0;
        } else {
            x = nx;
        }
        float before = y;
        y += ySpeed;
        if (ySpeed < 0) {
            // The head meets a ceiling: align below it and stop rising (Sonic_DoLevelCollision).
            int head = Math.round(y) - height();
            if (ground.ceiling(Math.round(x), head)) {
                int clear = head;
                while (clear < Math.round(before) - height() && ground.ceiling(Math.round(x), clear)) {
                    clear++;
                }
                y = clear + height();
                ySpeed = 0;
            }
        }
        if (ySpeed >= 0) {
            int floor = feetFloor(ground, Math.round(before) - 2);
            if (y >= floor) {
                y = floor;
                onGround = true;
                ySpeed = 0;
                sprung = false;
                jumpedAt = -1;
                rolling = false; // Sonic_ResetOnFloor: landing ends the jump's roll

            }
        }
    }

    /**
     * Sonic_AnglePos / Sonic_FindFloor: two foot sensors at x - obWidth and x + obWidth (9 pixels)
     * look down for the floor and the nearer one holds him. There is no sensor at his centre, so a
     * gap narrower than his feet never takes him, and he balances on a ledge until both feet are off.
     */
    private int feetFloor(Ground ground, int fromY) {
        int cx = Math.round(x);
        return Math.min(ground.floorBelow(cx - HALF_WIDTH, fromY), ground.floorBelow(cx + HALF_WIDTH, fromY));
    }

    private float clampX(Ground ground, float nx) {
        return Math.max(ground.left() + HALF_WIDTH, Math.min(ground.right() - HALF_WIDTH, nx));
    }
}
