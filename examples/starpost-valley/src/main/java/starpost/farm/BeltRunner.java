package starpost.farm;

/**
 * Sonic in the belt-scroller view: Sonic_Move's acceleration and top speed along the valley,
 * a slower walk into and out of the screen across the field's depth, and a jump that lifts
 * him off his shadow. He stays side-on throughout, as characters do in a belt-scroller.
 */
public final class BeltRunner {
    static final float DEPTH_TOP = 2.0f;
    static final float DEPTH_ACCEL = 0.25f;

    public float x;
    public float depth;        // 0 at the back of the field, increasing toward the screen
    public float height;       // above the ground (jumping)
    public float speed;
    public float depthSpeed;
    public float ySpeed;
    public boolean facingLeft;
    public boolean rolling;
    /** Grounded, holding a direction against the field boundary. */
    public boolean pushing;
    /**
     * Ticks left of the skid animation ($D, AniSonic0D: four frames of four ticks), started by
     * braking hard on the ground; 0 when not skidding. The dust drops its puffs meanwhile.
     */
    public int skid;
    public static final int SKID_TICKS = 16;

    public BeltRunner(float x, float depth) {
        this.x = x;
        this.depth = depth;
    }

    /** One frame. Returns true when a jump started. */
    public boolean step(float minX, float maxX, float maxDepth, boolean left, boolean right, boolean up,
            boolean down, boolean jumpPressed, boolean jumpHeld) {
        boolean airborne = height > 0 || ySpeed < 0;
        if (skid > 0) {
            skid--;
        }
        boolean braking = !airborne && (left && speed > 0 || right && speed < 0);
        if (left) {
            speed = speed > 0 ? speed - starpost.valley.Runner.DECEL : Math.max(-starpost.valley.Runner.TOP, speed - (airborne ? starpost.valley.Runner.AIR_ACCEL : starpost.valley.Runner.ACCEL));
            facingLeft |= !braking;          // braking, he still faces the way he slides (Sonic_Move)
        } else if (right) {
            speed = speed < 0 ? speed + starpost.valley.Runner.DECEL : Math.min(starpost.valley.Runner.TOP, speed + (airborne ? starpost.valley.Runner.AIR_ACCEL : starpost.valley.Runner.ACCEL));
            facingLeft &= braking;
        } else if (!airborne) {
            speed -= Math.signum(speed) * Math.min(Math.abs(speed), starpost.valley.Runner.FRICTION * 4);
        }
        if (up) {
            depthSpeed = Math.max(-DEPTH_TOP, depthSpeed - DEPTH_ACCEL);
        } else if (down) {
            depthSpeed = Math.min(DEPTH_TOP, depthSpeed + DEPTH_ACCEL);
        } else {
            depthSpeed -= Math.signum(depthSpeed) * Math.min(Math.abs(depthSpeed), DEPTH_ACCEL);
        }
        // Sonic_Move's skid (sub_113F6 / sub_11482): braking hard on the ground; turning round
        // or leaving the ground ends it.
        if (braking && skid == 0 && skidsAt(speed)) {
            skid = SKID_TICKS;
        } else if (!braking && (left || right) || airborne) {
            skid = 0;
        }
        boolean jumped = false;
        if (!airborne && jumpPressed) {
            skid = 0;
            ySpeed = -starpost.valley.Runner.JUMP;
            rolling = true;
            jumped = true;
        }
        if (height > 0 || ySpeed < 0) {
            if (!jumpHeld && ySpeed < -starpost.valley.Runner.JUMP_RELEASE) {
                ySpeed = -starpost.valley.Runner.JUMP_RELEASE;
            }
            ySpeed += starpost.valley.Runner.GRAVITY;
            height -= ySpeed;
            if (height <= 0) {
                height = 0;
                ySpeed = 0;
                rolling = false;
            }
        }
        float nextX = x + speed;
        pushing = height == 0 && !rolling && (left && nextX <= minX || right && nextX >= maxX);
        x = Math.max(minX, Math.min(maxX, nextX));
        if (pushing) {
            speed = 0;
            skid = 0;
        }
        depth = Math.max(0, Math.min(maxDepth, depth + depthSpeed));
        return jumped;
    }

    /**
     * Whether braking that leaves this ground speed starts a skid: Sonic_MoveLeft/Right
     * (sub_113F6, sub_11482) skid at $400 or more, but test the speed after the angle check has
     * overwritten its low byte (FixBugs off: "move.b angle(a0),d0" instead of d1). On flat ground
     * the comparison sees the speed with its low byte cleared, so braking from the right needs
     * $400 and from the left only more than $300; the fixed branch compares the whole speed.
     */
    public static boolean skidsAt(float speed) {
        int word = Math.round(speed * 256) & ~0xFF;
        return word >= 0x400 || word <= -0x400;
    }
}
