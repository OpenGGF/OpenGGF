package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.RewindStateful;

/**
 * ROM {@code Gradual_SwingOffset} (sonic3k.asm:92484-92515).
 *
 * <p>{@code $2E(a0)} is a 16.16 speed, {@code $32(a0)} a 16.16 accumulated offset and
 * {@code $36(a0)} the direction flag. The caller passes an initial speed in {@code d0} and a
 * per-frame acceleration in {@code d1}; the routine returns {@code move.w $32(a0),d0}, which on a
 * big-endian long is the offset's high word, i.e. its integer pixels.
 *
 * <p>The first call finds speed, offset and flag all zero, so it takes the "reset going up"
 * branch: speed becomes {@code -d0} and the returned offset is 0. The caller watches the speed
 * sign ({@code tst.l $2E(a0) / bmi}) to know the swing is still rising.
 *
 * <p>{@link SSZHPZTeleporterObjectInstance} keeps its own inline copy because its swing fields are
 * part of that class's rewind extra; this holder is the owner for objects written since.
 */
final class S3kGradualSwing implements RewindStateful<S3kGradualSwing.Value> {
    /** {@code $2E}/{@code $32}/{@code $36}. */
    record Value(int speed, int offset, boolean reversed) {}

    private int speed;
    private int offset;
    private boolean reversed;

    /**
     * Pre-seeds the {@code $2E} speed longword, as {@code loc_57B6A}'s
     * {@code move.w #$8000,$30(a0)} does before its first call: with a non-zero speed the first
     * step moves instead of taking the reset branch, so the swing starts in the other direction.
     */
    void seedSpeed(int value) {
        speed = value;
    }

    /** Returns the integer offset for this frame. */
    int step(int initialSpeed, int acceleration) {
        int stepValue = acceleration;
        if (reversed) {
            stepValue = -stepValue;
            offset += speed;
            if (offset < 0) {
                speed -= stepValue;
            } else {
                speed = initialSpeed;
                offset = 0;
                reversed = false;
            }
        } else {
            offset += speed;
            if (offset > 0) {
                speed -= stepValue;
            } else {
                speed = -initialSpeed;
                offset = 0;
                reversed = true;
            }
        }
        return (short) (offset >> 16);
    }

    /** {@code tst.l $2E(a0) / bmi}: the swing has not come back to its release point. */
    boolean rising() {
        return speed < 0;
    }

    @Override
    public Value captureRewindStateValue() {
        return new Value(speed, offset, reversed);
    }

    @Override
    public void restoreRewindStateValue(Value value) {
        speed = value.speed();
        offset = value.offset();
        reversed = value.reversed();
    }
}
