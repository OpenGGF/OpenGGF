package com.openggf.game.soniccd.service.graphics;

/**
 * An unsigned 32-bit residual in one explicit CD reset/rebase domain. This is neither a
 * video-frame number nor a global timestamp. The caller supplies the domain; no epoch is inferred
 * from a decreasing counter. Source: GPGX 051d430d, core/cd_hw/gfx.h, gfx_t (45-46), and
 * core/cd_hw/scd.c, scd_end_frame (1990-2003).
 */
public record CdStampClock(long resetEpoch, long epoch, long cycles) {
    public static final long UINT32_MASK = 0xffffffffL;

    public CdStampClock {
        if (resetEpoch < 0 || epoch < 0) { throw new IllegalArgumentException("CD epochs must be nonnegative"); }
        requireUnsignedCycles(cycles);
    }

    /** Convenience for a domain that has not been reset. */
    public CdStampClock(long epoch, long cycles) { this(0, epoch, cycles); }

    /** One explicit native end-of-frame subtraction, including inactive unsigned wrap. */
    public record Rebase(long resetEpoch, long fromEpoch, long toEpoch, long subtractedCycles) {
        public Rebase {
            if (resetEpoch < 0 || fromEpoch < 0 || fromEpoch == Long.MAX_VALUE || toEpoch != fromEpoch + 1) {
                throw new IllegalArgumentException("A rebase must enter the immediately following CD epoch");
            }
            requireUnsignedCycles(subtractedCycles);
        }

        public Rebase(long fromEpoch, long toEpoch, long subtractedCycles) {
            this(0, fromEpoch, toEpoch, subtractedCycles);
        }
    }

    static void requireUnsignedCycles(long cycles) {
        if (cycles < 0 || cycles > UINT32_MASK) {
            throw new IllegalArgumentException("CD residual must fit an unsigned 32-bit counter");
        }
    }
}
