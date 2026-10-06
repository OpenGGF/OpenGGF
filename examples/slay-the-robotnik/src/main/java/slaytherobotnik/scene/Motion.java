package slaytherobotnik.scene;

/**
 * An object's position and speed as the game keeps them: positions in 1/256 pixels and speeds
 * in 1/256 pixels a frame ({@code x_vel}/{@code y_vel}), moved the way {@code MoveSprite2} moves
 * them, with gravity added after the move as the game's falling objects do.
 */
final class Motion {
    /** Player gravity in the air ({@code addi.w #$38,y_vel}). */
    static final int PLAYER_GRAVITY = 0x38;
    /** Gravity on a hurt player ({@code Player_Hurt}: {@code addi.w #$30,y_vel}). */
    static final int HURT_GRAVITY = 0x30;

    int x;
    int y;
    int xVel;
    int yVel;

    Motion(float x, float y) {
        this.x = Math.round(x * 256);
        this.y = Math.round(y * 256);
    }

    float x() {
        return x / 256f;
    }

    float y() {
        return y / 256f;
    }

    void place(float px, float py) {
        x = Math.round(px * 256);
        y = Math.round(py * 256);
    }

    /** One frame: moves by the speed, then adds {@code gravity} to the downward speed. */
    void step(int gravity) {
        x += xVel;
        y += yVel;
        yVel += gravity;
    }

    /** A player's jump speed: $680 for Sonic and Tails, $600 for Knuckles (Sonic_Jump / Knux_Jump). */
    static int jumpSpeed(String characterId) {
        return "knuckles".equals(characterId) ? 0x600 : 0x680;
    }

    /**
     * The frames a jump at {@code jumpSpeed} under player gravity takes to rise {@code height}
     * pixels, or -1 if it never gets that high.
     */
    static int framesToRise(int jumpSpeed, float height) {
        int y = 0;
        int vel = -jumpSpeed;
        int target = Math.round(-height * 256);
        for (int frame = 1; vel < 0; frame++) {
            y += vel;
            vel += PLAYER_GRAVITY;
            if (y <= target) {
                return frame;
            }
        }
        return -1;
    }

    /**
     * The frames until a jump at {@code jumpSpeed} comes back down to {@code drop} pixels below
     * where it started (negative: above it), or -1 if it never does within ten seconds.
     */
    static int framesToLand(int jumpSpeed, float drop) {
        int y = 0;
        int vel = -jumpSpeed;
        int target = Math.round(drop * 256);
        for (int frame = 1; frame < 600; frame++) {
            y += vel;
            vel += PLAYER_GRAVITY;
            if (vel > 0 && y >= target) {
                return frame;
            }
        }
        return -1;
    }
}
