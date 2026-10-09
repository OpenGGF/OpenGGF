package starpost.looktest;

/**
 * Sonic's movement on a side-view terrace world, ported from the ROM's movement routines
 * (Sonic_Move, Sonic_RollSpeed, Sonic_Jump, Sonic_JumpHeight and Sonic_SpinDash in sonic3k.asm),
 * in pixels per frame rather than 8.8 fixed point. The ground is a height field: a terrace edge
 * is a wall when it rises more than a step, and a ledge to fall from when it drops. Slopes and
 * loops are not modelled here (the look test's terraces are flat).
 */
public final class Runner {
    /** Sonic_Move: acceleration $C, deceleration $80, friction $C, top speed $600. */
    static final float ACCEL = 0x0C / 256f;
    static final float DECEL = 0x80 / 256f;
    static final float FRICTION = 0x0C / 256f;
    static final float TOP = 0x600 / 256f;
    /** Sonic_RollSpeed: rolling friction $6 (half of walking), braking $20. */
    static final float ROLL_FRICTION = 0x06 / 256f;
    static final float ROLL_BRAKE = 0x20 / 256f;
    /** Sonic_Jump $680, Sonic_JumpHeight's $400 cap on release, gravity $38, air acceleration $18. */
    static final float JUMP = 0x680 / 256f;
    static final float JUMP_RELEASE = 0x400 / 256f;
    static final float GRAVITY = 0x38 / 256f;
    static final float AIR_ACCEL = 0x18 / 256f;
    /** Sonic_SpinDash: $800 base, plus half the charge; each press adds $200 up to $800. */
    static final float DASH_BASE = 0x800 / 256f;
    static final float DASH_STEP = 0x200 / 256f;
    static final float DASH_MAX = 0x800 / 256f;
    /** Ground this far above the feet just ahead is a wall; slopes rise less than that. */
    static final int WALL = 12;
    /** A floor more than this far below the feet is a ledge to fall from. */
    static final int DROP = 20;
    static final int HALF_WIDTH = 9;

    /** The terrain: per-pixel solidity, scanned for floors and walls. */
    public interface Ground {
        boolean solid(int x, int y);

        /** The first solid row at or below {@code fromY}, or a large value when there is none. */
        int floorBelow(int x, int fromY);

        int left();

        int right();
    }

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

    public Runner(float x, float y) {
        this.x = x;
        this.y = y;
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
                    speed -= Math.signum(speed) * ROLL_BRAKE;
                }
                speed -= Math.signum(speed) * Math.min(Math.abs(speed), ROLL_FRICTION);
                if (Math.abs(speed) < 0.5f) {
                    rolling = false;
                    speed = 0;
                }
                if (jumpPressed) {
                    jumped = jump(tick);
                }
            } else {
                ducking = down && Math.abs(speed) < 1;
                if (ducking && jumpPressed) {
                    dashing = true;
                    dashCharge = 0;
                    ducking = false;
                } else if (jumpPressed) {
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
            if (!jumpHeld && ySpeed < -JUMP_RELEASE && jumpedAt >= 0 && !sprung) {
                ySpeed = -JUMP_RELEASE;
            }
            if (left) {
                speed = Math.max(-TOP, speed - AIR_ACCEL);
                facingLeft = true;
            } else if (right) {
                speed = Math.min(TOP, speed + AIR_ACCEL);
                facingLeft = false;
            }
            ySpeed = Math.min(16, ySpeed + GRAVITY);
            moveInAir(ground);
        }
        return jumped;
    }

    private boolean jump(int tick) {
        onGround = false;
        ySpeed = -JUMP;
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
        if (left) {
            if (speed > 0) {
                speed -= DECEL;
                if (speed < 0) {
                    speed = -DECEL;
                }
            } else {
                speed = Math.max(-TOP, speed - ACCEL);
                facingLeft = true;
            }
        } else if (right) {
            if (speed < 0) {
                speed += DECEL;
                if (speed > 0) {
                    speed = DECEL;
                }
            } else {
                speed = Math.min(TOP, speed + ACCEL);
                facingLeft = false;
            }
        } else {
            speed -= Math.signum(speed) * Math.min(Math.abs(speed), FRICTION);
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
        int floor = ground.floorBelow(Math.round(x), Math.round(y) - WALL + 1);
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
        if (ySpeed >= 0) {
            int floor = ground.floorBelow(Math.round(x), Math.round(before) - 2);
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

    private float clampX(Ground ground, float nx) {
        return Math.max(ground.left() + HALF_WIDTH, Math.min(ground.right() - HALF_WIDTH, nx));
    }
}
