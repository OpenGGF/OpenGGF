package com.openggf.game.soniccd.objects;

import java.util.Objects;

/**
 * Pure shipped-ROM UpdateGlobalAnims composition.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Level/Main.asm:513-549.
 * The caller owns the actual clear/store history, admission, adoption and capture;
 * these values introduce no clock, Frame, mutable bank or invocation authority.
 */
public final class CdGlobalObjectAnimation {
    private CdGlobalObjectAnimation() { }

    /** Native log/unused BYTEs plus the existing spill value; Ring stays in its sole parent store. */
    public record Extras(int logSpikeTimerByte, int logSpikeFrameByte,
                         int unusedTimerByte, int unusedFrameByte, CdRingSpillGlobals spill) {
        public Extras {
            requireByte(logSpikeTimerByte, "logSpikeAnimTimer");
            requireByte(logSpikeFrameByte, "logSpikeAnimFrame");
            requireByte(unusedTimerByte, "unkAnimTimer");
            requireByte(unusedFrameByte, "unkAnimFrame");
            Objects.requireNonNull(spill, "spill");
        }
    }

    /** Detached values for the caller's existing stores; no second Ring owner. */
    public record Result(Extras extras, CdRingGlobals ring) {
        public Result {
            Objects.requireNonNull(extras, "extras");
            Objects.requireNonNull(ring, "ring");
        }
    }

    /** One ordered log-spike, Ring, unused, Spill pass over known finite native values. */
    public static Result step(Extras extras, CdRingGlobals ring) {
        Objects.requireNonNull(extras, "extras");
        Objects.requireNonNull(ring, "ring");

        int logTimer = (extras.logSpikeTimerByte() - 1) & 0xFF;
        int logFrame = extras.logSpikeFrameByte();
        if ((logTimer & 0x80) != 0) {
            logTimer = 0x0B;
            logFrame = (logFrame - 1) & 7;
        }

        var nextRing = ring.advanceGlobalRingAnimation();

        int unusedTimer = (extras.unusedTimerByte() - 1) & 0xFF;
        int unusedFrame = extras.unusedFrameByte();
        if ((unusedTimer & 0x80) != 0) {
            unusedTimer = 7;
            unusedFrame = (unusedFrame + 1) & 0xFF;
            if (unusedFrame >= 6) unusedFrame = 0;
        }

        var nextSpill = extras.spill().advanceTail();
        return new Result(new Extras(logTimer, logFrame, unusedTimer, unusedFrame, nextSpill), nextRing);
    }

    private static void requireByte(int value, String name) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException(name + " exceeds native BYTE");
    }
}
