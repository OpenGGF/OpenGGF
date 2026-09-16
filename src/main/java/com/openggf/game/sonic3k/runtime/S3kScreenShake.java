package com.openggf.game.sonic3k.runtime;

import java.nio.ByteBuffer;

/**
 * ROM {@code Screen_shake_flag}, {@code Screen_shake_offset} and
 * {@code Screen_shake_last_offset} together with {@code ShakeScreen_Setup}
 * (sonic3k.asm:104188-104210), the routine every zone background event
 * tail-calls once per frame to advance them.
 *
 * <p>Objects and events only write the flag: a positive word is a timed
 * countdown that tapers through {@code ScreenShakeArray}, a negative word
 * ({@code st}) is the constant jitter of {@code ScreenShakeArray2} indexed by
 * {@code Level_frame_counter & $3F}, and zero is off. {@code LevelSetup}
 * clears all three words (sonic3k.asm:102193-102194).
 *
 * <p>Zone owners hold one instance and decide when {@link #setup} runs
 * relative to their screen/background events; the value a frame's events read
 * is whatever the previous {@link #setup} left in {@link #offset()}.
 */
public final class S3kScreenShake {
    /**
     * {@code ScreenShakeArray} (sonic3k.asm:104231-104233) followed by
     * {@code ScreenShakeArray2} (104234-104238). The timed branch reads
     * {@code ScreenShakeArray(pc,d0.w)} with the decremented flag and no bound,
     * so a countdown above $14 (e.g. {@code #$1E}, sonic3k.asm:6861) walks into
     * the second table; keeping both contiguous reproduces that.
     */
    private static final byte[] SHAKE_TABLE = {
            // ScreenShakeArray
            1, -1, 1, -1, 2, -2, 2, -2, 3, -3, 3, -3, 4, -4, 4, -4,
            5, -5, 5, -5,
            // ScreenShakeArray2
            1, 2, 1, 3, 1, 2, 2, 1, 2, 3, 1, 2, 1, 2, 0, 0,
            2, 0, 3, 2, 2, 3, 2, 2, 1, 3, 0, 0, 1, 0, 1, 3,
            1, 2, 1, 3, 1, 2, 2, 1, 2, 3, 1, 2, 1, 2, 0, 0,
            2, 0, 3, 2, 2, 3, 2, 2, 1, 3, 0, 0, 1, 0, 1, 3
    };
    private static final int TIMED_TABLE_LENGTH = 20;
    private static final int CONTINUOUS_INDEX_MASK = 0x3F;
    private static final int CAPTURE_BYTES = Integer.BYTES * 3;

    private int flag;
    private int offset;
    private int lastOffset;

    /** ROM {@code Screen_shake_flag}. */
    public int flag() {
        return flag;
    }

    /** ROM {@code Screen_shake_offset}: the value the last {@link #setup} wrote. */
    public int offset() {
        return offset;
    }

    /** ROM {@code Screen_shake_last_offset}. */
    public int lastOffset() {
        return lastOffset;
    }

    /** {@code move.w #n,(Screen_shake_flag).w}; negative values are the {@code st} constant mode. */
    public void writeFlag(int value) {
        flag = (short) value;
    }

    /** {@code LevelSetup}: {@code clr.w (Screen_shake_flag).w / clr.l (Screen_shake_offset).w}. */
    public void clear() {
        flag = 0;
        offset = 0;
        lastOffset = 0;
    }

    /**
     * {@code ShakeScreen_Setup}. The previous offset is saved first; a dead or
     * restarting player ({@code Player_1+routine >= 6}) and a zero flag both
     * produce offset 0 without touching the flag. A positive flag is
     * decremented and indexes {@code ScreenShakeArray} with the new value; a
     * negative flag samples {@code ScreenShakeArray2[Level_frame_counter & $3F]}.
     *
     * @param levelFrameCounter ROM {@code Level_frame_counter} as the routine reads it
     * @param playerDeadOrRestarting {@code Player_1+routine >= 6}
     */
    public void setup(int levelFrameCounter, boolean playerDeadOrRestarting) {
        lastOffset = offset;
        int next = 0;
        if (!playerDeadOrRestarting && flag != 0) {
            if (flag > 0) {
                flag--;
                next = flag < SHAKE_TABLE.length ? SHAKE_TABLE[flag] : 0;
            } else {
                next = SHAKE_TABLE[TIMED_TABLE_LENGTH + (levelFrameCounter & CONTINUOUS_INDEX_MASK)];
            }
        }
        offset = next;
    }

    /** Number of bytes {@link #captureTo} writes. */
    public static int captureBytes() {
        return CAPTURE_BYTES;
    }

    public void captureTo(ByteBuffer buffer) {
        buffer.putInt(flag);
        buffer.putInt(offset);
        buffer.putInt(lastOffset);
    }

    public void restoreFrom(ByteBuffer buffer) {
        flag = buffer.getInt();
        offset = buffer.getInt();
        lastOffset = buffer.getInt();
    }
}
