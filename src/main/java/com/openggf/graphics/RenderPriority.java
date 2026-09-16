package com.openggf.graphics;

/**
 * Sprite display-list buckets, 0-7, shared by all three games.
 * <p>
 * The ROM keeps eight $80-byte display lists ({@code Sprite_Table_Input} /
 * {@code Object_Display_Lists}). {@code DisplaySprite} (S1/S2) and
 * {@code Draw_Sprite} (S3K) append the object to the list its {@code priority}
 * field selects, and BuildSprites walks list 0 first, so bucket 0 is the
 * front-most. The two encodings differ:
 * <ul>
 *   <li>Sonic 1 and Sonic 2 store the bucket index as a byte:
 *       {@code move.b #4,priority(a0)} is bucket 4.</li>
 *   <li>Sonic 3 &amp; Knuckles stores the list byte offset as a word:
 *       {@code move.w #$280,priority(a0)} is bucket 5. Use {@link #fromS3kWord(int)}.</li>
 * </ul>
 * The art word's bit 15 (sprite-versus-plane priority, {@code make_art_tile(..,1)})
 * is a different property and maps to {@code isHighPriority()}, never to a bucket.
 */
public final class RenderPriority {
    public static final int MIN = 0;
    public static final int MAX = 7;
    public static final int PLAYER_DEFAULT = 2;

    /** Width of one S3K display list in bytes; S3K {@code priority} words are multiples of it. */
    public static final int S3K_WORD_STEP = 0x80;

    private RenderPriority() {
    }

    /**
     * Returns {@code value} when it is a valid bucket index (0-7) and throws otherwise.
     * Use this for literals transcribed from Sonic 1 / Sonic 2 {@code priority} bytes.
     * A raw S3K word such as {@code 0x280} is rejected here instead of being clamped.
     */
    public static int bucket(int value) {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException("sprite priority bucket out of range: " + value
                    + " (S3K priority words must go through RenderPriority.fromS3kWord)");
        }
        return value;
    }

    /**
     * Converts a Sonic 3 &amp; Knuckles {@code priority} word ($0..$380, a multiple of $80)
     * to its bucket index ({@code word / $80}). Rejects anything that is not such a word.
     */
    public static int fromS3kWord(int word) {
        if (word < 0 || word > MAX * S3K_WORD_STEP || (word % S3K_WORD_STEP) != 0) {
            throw new IllegalArgumentException("not an S3K sprite priority word: 0x"
                    + Integer.toHexString(word) + " (expected a multiple of 0x80 up to 0x380)");
        }
        return word / S3K_WORD_STEP;
    }

    /**
     * Clamps a live bucket value into range. Reserved for the render path, which
     * must never throw on a value that an object computes at runtime; object
     * implementations should transcribe literals through {@link #bucket(int)} or
     * {@link #fromS3kWord(int)} so an out-of-range word is caught, not silently
     * folded into bucket 7.
     */
    public static int clamp(int value) {
        if (value < MIN) {
            return MIN;
        }
        if (value > MAX) {
            return MAX;
        }
        return value;
    }
}
