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

    public BeltRunner(float x, float depth) {
        this.x = x;
        this.depth = depth;
    }

    /** One frame. Returns true when a jump started. */
    public boolean step(float minX, float maxX, float maxDepth, boolean left, boolean right, boolean up,
            boolean down, boolean jumpPressed, boolean jumpHeld) {
        boolean airborne = height > 0 || ySpeed < 0;
        if (left) {
            speed = speed > 0 ? speed - starpost.valley.Runner.DECEL : Math.max(-starpost.valley.Runner.TOP, speed - (airborne ? starpost.valley.Runner.AIR_ACCEL : starpost.valley.Runner.ACCEL));
            facingLeft = true;
        } else if (right) {
            speed = speed < 0 ? speed + starpost.valley.Runner.DECEL : Math.min(starpost.valley.Runner.TOP, speed + (airborne ? starpost.valley.Runner.AIR_ACCEL : starpost.valley.Runner.ACCEL));
            facingLeft = false;
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
        boolean jumped = false;
        if (!airborne && jumpPressed) {
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
        x = Math.max(minX, Math.min(maxX, x + speed));
        depth = Math.max(0, Math.min(maxDepth, depth + depthSpeed));
        return jumped;
    }
}
