package flappytails;

/**
 * Tails' flight, ported from the Sonic 3 &amp; Knuckles routine that flies him
 * ({@code Tails_FlyingSwimming} and {@code Tails_Move_FlySwim} in the disassembly), so the
 * scene's Tails climbs, glides and tires exactly as the game's own does.
 *
 * <p>Units are the ROM's: {@link #y()} is in pixels, but the position and speed are kept in
 * 1/256 pixel ({@code y_vel} is an 8.8 fixed-point word added to {@code y_pos} once a frame by
 * {@code MoveSprite2}). Only vertical motion is modelled: the course scrolls past a Tails who
 * holds his screen column, as the native Flappy sample pins his X.
 *
 * <p>Per frame, in the ROM's order:
 * <ol>
 *   <li>Every other frame (odd {@code Level_frame_counter}) the flight timer
 *       ({@code double_jump_property}) counts down towards zero.</li>
 *   <li>While a flap is in progress ({@code double_jump_flag} 2 to $1F) each frame subtracts $20
 *       from the speed until it is faster than -$100 upwards or 30 frames have passed; then the
 *       flag returns to 1 (gliding).</li>
 *   <li>While gliding, a newly pressed A, B or C starts the next flap (flag 2) if Tails is not
 *       already rising faster than $100 and the timer has not run out. Gliding adds 8 (gravity)
 *       every frame, including the frame a flap is requested.</li>
 *   <li>At the top of the camera (its minimum Y plus $10) an upward speed is cancelled.</li>
 *   <li>{@code MoveSprite2} adds the speed to the position (no further gravity).</li>
 * </ol>
 * The flag's starting value 1 and the timer's (8*60)/2 come from {@code Tails_Test_For_Flight}.
 */
final class Flight {
    /** {@code double_jump_property} when flight begins: (8*60)/2, about eight seconds at one tick per two frames. */
    static final int FULL_TIMER = (8 * 60) / 2;
    /** The flap's push per frame ({@code subi.w #$20,y_vel(a0)}). */
    static final int LIFT = 0x20;
    /** A flap stops pushing once Tails rises faster than this ({@code cmpi.w #-$100,y_vel(a0)}). */
    static final int RISE_LIMIT = -0x100;
    /** Gliding gravity ({@code addi.w #8,y_vel(a0)}). */
    static final int GLIDE_GRAVITY = 8;
    /** The flap flag counts from 2 to this, then glides ({@code cmpi.b #$20,double_jump_flag(a0)}). */
    static final int FLAP_FRAMES_END = 0x20;

    private int yPos;        // 1/256 pixel
    private int yVel;        // 1/256 pixel per frame, a signed word
    private int flag = 1;    // double_jump_flag: 1 gliding, 2..$1F flapping
    private int timer = FULL_TIMER;

    /** Starts flying at {@code y} pixels, still, gliding, with a full timer. */
    void start(int y) {
        yPos = y << 8;
        yVel = 0;
        flag = 1;
        timer = FULL_TIMER;
    }

    /**
     * Advances one frame.
     *
     * @param pressed     A, B or C pressed this frame (a new press, not held)
     * @param oddFrame    whether the frame counter is odd, the frames the timer counts down on
     * @param ceilingY    the camera's minimum Y in screen pixels; the ROM stops rising $10 below it
     * @return true when this frame starts a flap
     */
    boolean tick(boolean pressed, boolean oddFrame, int ceilingY) {
        if (oddFrame && timer > 0) {
            timer--;
        }
        boolean flapStarted = false;
        if (flag != 1) {
            if (yVel >= RISE_LIMIT) {
                yVel = word(yVel - LIFT);
                flag++;
                if (flag == FLAP_FRAMES_END) {
                    flag = 1;
                }
            } else {
                flag = 1;
            }
        } else {
            // Tails_Move_FlySwim also refuses a flap underwater while carrying a player; the
            // scene has neither, so only the speed and timer gates apply.
            if (pressed && yVel >= RISE_LIMIT && timer > 0) {
                flag = 2;
                flapStarted = true;
            }
            yVel = word(yVel + GLIDE_GRAVITY);
        }
        if ((yPos >> 8) <= ceilingY + 0x10 && yVel < 0) {
            yVel = 0;
        }
        yPos += yVel;
        return flapStarted;
    }

    /** Pins Tails to {@code y} pixels without changing his speed, for the ground and for respawns. */
    void setY(int y) { yPos = y << 8; }

    /** Sets the speed directly, in 1/256 pixel per frame: knockbacks and the defeat hop. */
    void setYVel(int velocity) { yVel = word(velocity); flag = 1; }

    /**
     * Moves without flying ({@code MoveSprite2}, then {@code gravity} added to the speed), as the
     * hurt routine does; the top of the screen still stops him, {@code ceiling} pixels down.
     */
    void ballistic(int gravity, int ceiling) {
        yPos += yVel;
        yVel = word(yVel + gravity);
        if ((yPos >> 8) < ceiling) {
            yPos = ceiling << 8;
            if (yVel < 0) yVel = 0;
        }
    }

    /** Fills the timer, as the native sample does every frame and as a ring does in Sonic rules. */
    void refill(int amount) { timer = Math.min(FULL_TIMER, timer + amount); }

    int y() { return yPos >> 8; }
    int yFine() { return yPos; }
    int yVel() { return yVel; }
    int timer() { return timer; }
    boolean tired() { return timer == 0; }
    boolean flapping() { return flag != 1; }

    /** {@code Tails_Set_Flying_Animation} out of water and not carrying: tired, rising or flying. */
    int animation() {
        if (timer == 0) return TailsArt.ANIM_TIRED;
        return yVel < 0 ? TailsArt.ANIM_FLY_UP : TailsArt.ANIM_FLY;
    }

    /**
     * Whether the ROM would play its flying (or, tired, its tired) sound this frame:
     * {@code (Level_frame_counter + 8) & $F == 0}, every sixteen frames.
     */
    static boolean buzzDue(long frameCounter) {
        return ((frameCounter + 8) & 0xF) == 0;
    }

    /** A copy for looking ahead (the autopilot simulates presses on copies). */
    Flight copy() {
        Flight f = new Flight();
        f.yPos = yPos;
        f.yVel = yVel;
        f.flag = flag;
        f.timer = timer;
        return f;
    }

    /** Wraps to a signed 16-bit word, as the 68000 does. */
    private static int word(int value) {
        return (short) value;
    }
}
