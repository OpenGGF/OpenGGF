package slaytherobotnik.scene;

/**
 * The game's {@code Animate_RawAdjustFlipX} (sonic3k.asm) over an {@code AniRaw_} script read
 * from the ROM: a delay byte, then mapping frames ($40 set flips the sprite), ending in $FC
 * (restart), $F8 (jump by the next byte to another script) or $F4 (run the object's next
 * routine; here {@link #finished}). One {@link #tick} is one call, one frame of the object.
 */
final class RawAnim {
    private final byte[] script;
    private int base;
    private int animFrame;
    private int timer;
    private int frame;
    private boolean flipped;
    private boolean finished;

    /** Starts as an object fresh from SetUp_ObjAttributes: frame 0, about to animate. */
    RawAnim(byte[] script) {
        this.script = script;
    }

    int frame() {
        return frame;
    }

    /** The object's anim_frame: how far through the current script it is. */
    int step() {
        return animFrame;
    }

    /** The object's render_flags bit 0, toggled by frames with $40 set. */
    boolean flipped() {
        return flipped;
    }

    /** True once the script has reached $F4. */
    boolean finished() {
        return finished;
    }

    /** One call of the animation routine. */
    void tick() {
        // subq.b #1,anim_frame_timer / bpl: wait while the timer stays non-negative.
        timer = (byte) (timer - 1);
        if (timer >= 0) {
            return;
        }
        animFrame++;
        int value = script[base + 1 + animFrame] & 0xFF;
        if (value >= 0x80) {
            switch (value) {
                case 0xF8 -> {                       // AnimateRaw_Jump, then restart there
                    base += script[base + 2 + animFrame];
                    restart();
                }
                case 0xF4 -> {                       // AnimateRaw_CustomCode
                    timer = 0;
                    finished = true;
                }
                default -> restart();                // $FC: AnimateRaw_Restart
            }
            animFrame = 0;
            return;
        }
        if ((value & 0x40) != 0) {
            value &= ~0x40;
            flipped = !flipped;
        }
        timer = script[base];
        frame = value;
    }

    private void restart() {
        frame = script[base + 1] & 0xFF;
        timer = script[base];
    }
}
