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
    /** Sonic_Hurt: gravity-8 ($30) while knocked back, $10 under water. */
    public static final float HURT_GRAVITY = 0x30 / 256f;
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
    /** Knocked back by a hit: no control, lighter gravity, until the next landing. */
    public boolean hurt;
    /** "sonic", "tails" or "knuckles": the second press of jump in the air is their own move. */
    public String character = "sonic";
    /** Tails's flight (Tails_FlyingTimer): eight seconds of flapping, then he tires and sinks. */
    public boolean flying;
    public int flyTimer;
    public static final int FLY_TIME = 8 * 60;
    /** Tails_Fly: gravity $08 while flying; a press of jump lifts him. */
    public static final float FLY_GRAVITY = 0x08 / 256f;
    public static final float FLY_LIFT = 0x200 / 256f;
    /** Knuckles: gliding (held jump, $400 forward, a slow sink), or clinging to a wall and climbing. */
    public boolean gliding;
    public boolean climbing;
    public static final float GLIDE_SPEED = 0x400 / 256f;
    public static final float GLIDE_SINK = 0x80 / 256f;

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
        if (hurt) {
            return underwater ? WATER_GRAVITY : HURT_GRAVITY;
        }
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
        if (climbing) {
            climb(ground, down, jumpPressed, tick);
            return false;
        }
        boolean jumped = false;
        pushing = false;
        if (hurt) {
            // Sonic_Hurt: no control until he lands.
            left = right = down = jumpPressed = jumpHeld = false;
            if (onGround) {
                hurt = false;
            }
        }
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
            if (jumpPressed && !hurt && jumpedAt >= 0) {
                secondMove();
            }
            float top = top();
            if (gliding) {
                glide(left, right, jumpHeld);
            } else {
                if (left) {
                    speed = Math.max(-top, speed - airAccel());
                    facingLeft = true;
                } else if (right) {
                    speed = Math.min(top, speed + airAccel());
                    facingLeft = false;
                }
                if (flying) {
                    if (flyTimer > 0) {
                        flyTimer--;
                    }
                    ySpeed = Math.min(flyTimer > 0 ? 1.5f : 4, ySpeed + (flyTimer > 0 ? FLY_GRAVITY : gravity()));
                } else {
                    ySpeed = Math.min(16, ySpeed + gravity());
                }
            }
            moveInAir(ground);
        }
        return jumped;
    }

    /** The second press of jump: Tails takes off or flaps, Knuckles starts gliding. */
    private void secondMove() {
        if (character.equals("tails")) {
            if (!flying) {
                flying = true;
                flyTimer = FLY_TIME;
                rolling = false;
            }
            if (flyTimer > 0) {
                ySpeed = Math.max(-2, ySpeed - FLY_LIFT);
            }
        } else if (character.equals("knuckles") && !gliding) {
            gliding = true;
            rolling = false;
            speed = (facingLeft ? -1 : 1) * Math.max(GLIDE_SPEED, Math.abs(speed));
            ySpeed = Math.min(ySpeed, 0);
        }
    }

    /** Knuckles_GlideControl: hold jump to keep gliding; pressing the other way turns him round. */
    private void glide(boolean left, boolean right, boolean jumpHeld) {
        if (!jumpHeld) {
            gliding = false;   // let go: he drops
            return;
        }
        float want = facingLeft ? -GLIDE_SPEED : GLIDE_SPEED;
        if (left && !facingLeft || right && facingLeft) {
            speed -= Math.signum(speed) * 0.25f;
            if (Math.abs(speed) < 0.5f) {
                facingLeft = !facingLeft;
            }
        } else {
            speed += Math.signum(want - speed) * Math.min(Math.abs(want - speed), 0.0625f);
        }
        ySpeed = ySpeed < GLIDE_SINK ? Math.min(GLIDE_SINK, ySpeed + 0.125f) : GLIDE_SINK;
    }

    /**
     * Knuckles on a wall: up and down climb (pressing toward the screen moves him down), jump
     * kicks off the wall, and climbing past the top pulls him up onto the ledge.
     */
    private void climb(Ground ground, boolean down, boolean jumpPressed, int tick) {
        int side = facingLeft ? -1 : 1;
        if (jumpPressed) {
            climbing = false;
            facingLeft = !facingLeft;
            speed = -side * 3;
            ySpeed = -4;
            jumpedAt = tick;
            rolling = true;
            return;
        }
        y += down ? 1 : climbUp ? -1 : 0;
        int wallX = Math.round(x + side * (HALF_WIDTH + 1));
        if (!ground.solid(wallX, Math.round(y) - height() + 4)) {
            // Over the top: pull up onto the ledge.
            x += side * (HALF_WIDTH + 6);
            y = ground.floorBelow(Math.round(x), Math.round(y) - height() - 8);
            climbing = false;
            onGround = true;
        } else if (ground.solid(Math.round(x), Math.round(y) + 1) && down) {
            climbing = false;
            onGround = true;
        }
    }

    /** Set by the screen each tick: up is held (Knuckles climbs). */
    public boolean climbUp;

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
        hurt = false;
        jumpedAt = -1;
    }

    /**
     * HurtSonic: knocked up and away from what hit him at (-$400, $200), or (-$200, $100) under
     * water, with no ground speed.
     */
    public void knockBack(boolean awayToLeft) {
        onGround = false;
        rolling = false;
        dashing = false;
        ducking = false;
        sprung = false;
        hurt = true;
        jumpedAt = -1;
        ySpeed = underwater ? -0x200 / 256f : -0x400 / 256f;
        float push = underwater ? 0x100 / 256f : 0x200 / 256f;
        speed = awayToLeft ? -push : push;
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
            if (gliding) {
                // Knuckles grabs the wall (Knuckles_GlideCheckWall) and clings.
                gliding = false;
                climbing = true;
                facingLeft = speed < 0;
                ySpeed = 0;
            }
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
                flying = false;
                gliding = false;
                if (hurt) {
                    speed = 0;   // Sonic_HurtStop: the knock-back ends with no speed
                }
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
