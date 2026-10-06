package slaytherobotnik.scene;

/**
 * A Giant Ring and something jumping into it, as {@code Obj_SSEntryRing} and
 * {@code Obj_SSEntryFlash} play it: the ring forms and turns on {@code AniRaw_SSEntryRing};
 * a jumper whose centre comes within {@code SSEntry_Range} (-$18..+$18 across, -$28..+$28 up and
 * down) vanishes into it with sfx_BigRing; the flash runs {@code AniRaw_SSEntryFlash} (one frame
 * each), deleting the ring when it changes frame at anim_frame 3 (SSEntryFlash_Main);
 * {@code SSEntryFlash_Finished} then waits $20 frames before sfx_EnterSS. Positions are the
 * objects' centres.
 */
final class GiantRingEntry {
    static final int IDLE = 0;
    static final int JUMPING = 1;
    static final int FLASHING = 2;
    static final int WAITING = 3;
    static final int ENTERED = 4;

    /** Obj_Wait's $2E count after the flash: $20 frames. */
    private static final int ENTRY_WAIT = 0x20;

    private final byte[] ringScript;
    private final byte[] flashScript;
    private final float ringX;
    private final float ringY;
    private RawAnim ring;
    private RawAnim flash;
    private Motion jumper;
    private int gravity;
    private float startX;
    private float startY;
    private int speed;
    private int driftLeft;
    private int phase = IDLE;
    private int wait;
    private boolean touchedNow;
    private boolean enteredNow;

    GiantRingEntry(byte[] ringScript, byte[] flashScript, float ringX, float ringY) {
        this.ringScript = ringScript;
        this.ring = new RawAnim(ringScript);
        this.flashScript = flashScript;
        this.ringX = ringX;
        this.ringY = ringY;
    }

    /**
     * Jumps from centre ({@code x}, {@code y}) at {@code jumpSpeed} (the player's jump, $680 or
     * $600), running fast enough to be under the ring's centre as it rises to the ring.
     */
    void jump(float x, float y, int jumpSpeed) {
        startX = x;
        startY = y;
        speed = jumpSpeed;
        jumper = new Motion(x, y);
        jumper.yVel = -jumpSpeed;
        int frames = Motion.framesToRise(jumpSpeed, y - ringY);
        if (frames < 0) {
            frames = Math.max(1, jumpSpeed / Motion.PLAYER_GRAVITY);
        }
        jumper.xVel = Math.round((ringX - x) * 256 / frames);
        gravity = Motion.PLAYER_GRAVITY;
        phase = JUMPING;
    }

    /** Sends something drifting into the ring at a steady speed, {@code frames} to reach its centre. */
    void drift(float x, float y, int frames) {
        jumper = new Motion(x, y);
        jumper.xVel = Math.round((ringX - x) * 256 / frames);
        jumper.yVel = Math.round((ringY - y) * 256 / frames);
        driftLeft = frames;
        gravity = 0;
        phase = JUMPING;
    }

    /** A new ring forming where the old one was (its forming frames 0-7 again); a mod flourish. */
    void reform() {
        ring = new RawAnim(ringScript);
    }

    /** One frame. */
    void tick() {
        touchedNow = false;
        enteredNow = false;
        if (ring != null) {
            ring.tick();
        }
        switch (phase) {
            case JUMPING -> {
                jumper.step(gravity);
                // Check_PlayerInRange with SSEntry_Range, once the ring has formed (frame 8 on).
                float dx = jumper.x() - ringX;
                float dy = jumper.y() - ringY;
                if (ring != null && ring.frame() >= 8 && dx >= -0x18 && dx < 0x18 && dy >= -0x28 && dy < 0x28) {
                    touchedNow = true;
                    flash = new RawAnim(flashScript);
                    phase = FLASHING;
                } else if (gravity == 0 && --driftLeft <= 0) {
                    // A drifter waits at the ring's centre until the ring has formed.
                    jumper.place(ringX, ringY);
                    jumper.xVel = 0;
                    jumper.yVel = 0;
                } else if (gravity != 0 && jumper.yVel > 0 && jumper.y() >= startY) {
                    // Came down without going in (the ring was still forming): jump again.
                    jump(startX, startY, speed);
                }
            }
            case FLASHING -> {
                int before = flash.frame();
                flash.tick();
                // SSEntryFlash_Main: the ring goes once the flash changes frame with anim_frame at 3.
                if (flash.frame() != before && flash.step() == 3) {
                    ring = null;
                }
                if (flash.finished()) {
                    phase = WAITING;
                    wait = ENTRY_WAIT;
                }
            }
            case WAITING -> {
                if (--wait < 0) {
                    phase = ENTERED;
                    enteredNow = true;
                }
            }
            default -> {
            }
        }
    }

    int phase() {
        return phase;
    }

    /** The ring's mapping frame (Map_SSEntryRing), or -1 once it has gone. */
    int ringFrame() {
        return ring == null ? -1 : ring.frame();
    }

    /** The flash's mapping frame (Map_SSEntryFlash), or -1 when no flash is showing. */
    int flashFrame() {
        return phase == FLASHING ? flash.frame() : -1;
    }

    boolean flashFlipped() {
        return flash != null && flash.flipped();
    }

    /** The jumper, while it is in the air on its way in; null before and after. */
    Motion jumper() {
        return phase == JUMPING ? jumper : null;
    }

    /** True on the frame the jumper touched the ring (sfx_BigRing). */
    boolean touchedNow() {
        return touchedNow;
    }

    /** True on the frame the wait ended (sfx_EnterSS). */
    boolean enteredNow() {
        return enteredNow;
    }

    float ringX() {
        return ringX;
    }

    float ringY() {
        return ringY;
    }
}
