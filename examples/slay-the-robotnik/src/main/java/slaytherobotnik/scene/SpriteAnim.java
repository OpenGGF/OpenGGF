package slaytherobotnik.scene;

/**
 * The game's {@code Animate_Sprite} (sonic3k.asm) over an {@code Ani_} table read from the ROM:
 * a word offset per animation, then each script's delay byte and mapping frames, ending in a
 * command: $FF loops, $FE steps back by the next byte, $FD switches to the animation in the
 * next byte, $FC advances the object's routine (here {@link #advanced}). One {@link #tick} is
 * one call, one frame of the object.
 */
final class SpriteAnim {
    private final byte[] table;
    private int anim;
    private int prevAnim = -1;
    private int animFrame;
    private int timer;
    private int frame;
    private boolean advanced;

    SpriteAnim(byte[] table, int anim) {
        this.table = table;
        this.anim = anim;
    }

    /** Selects an animation; it starts from its first frame on the next tick, as anim(a0) does. */
    void set(int anim) {
        this.anim = anim;
    }

    int anim() {
        return anim;
    }

    /** The mapping frame showing. */
    int frame() {
        return frame;
    }

    /** True once a $FC command has run (the object's routine would advance). */
    boolean advanced() {
        return advanced;
    }

    /** The object has taken its routine step: clear the flag. */
    void clearAdvanced() {
        advanced = false;
    }

    /** One call of Animate_Sprite. */
    void tick() {
        if (anim != prevAnim) {
            prevAnim = anim;
            animFrame = 0;
            timer = 0;
        }
        // subq.b #1,anim_frame_timer / bcc: wait while the timer had time left.
        if (timer > 0) {
            timer--;
            return;
        }
        int script = word(anim * 2);
        timer = table[script] & 0xFF;
        int index = animFrame;
        int value = table[script + 1 + index] & 0xFF;
        if (value < 0x80) {
            show(value);
            return;
        }
        switch (value) {
            case 0xFF -> {                      // loop from the first frame
                animFrame = 0;
                show(table[script + 1] & 0xFF);
            }
            case 0xFE -> {                      // repeat from an earlier frame
                int back = table[script + 2 + index] & 0xFF;
                animFrame -= back;
                show(table[script + 1 + index - back] & 0xFF);
            }
            case 0xFD -> anim = table[script + 2 + index] & 0xFF; // start another animation
            case 0xFC -> {                      // the object's routine advances
                advanced = true;
                timer = 0;
                animFrame++;
            }
            default -> {
            }
        }
    }

    private void show(int mappingFrame) {
        frame = mappingFrame;
        animFrame++;
    }

    private int word(int offset) {
        return ((table[offset] & 0xFF) << 8) | (table[offset + 1] & 0xFF);
    }
}
