package sitarhero.stage;

import sitarhero.model.Role;
import java.util.Arrays;
import java.util.Objects;

/** Cosmetic note-on envelopes. No chart, score, held-button or audio-clock authority. */
final class PerformerMotion {
    static final int ATTACK_TICKS = 12;
    static final int RING_TICKS = 18;
    private final long[][] strikes = new long[Role.values().length][5];
    private long lastTick = Long.MIN_VALUE;

    PerformerMotion() { clear(); }

    void notePlayed(Role role, int lanes, long ticks) {
        Objects.requireNonNull(role, "role");
        if ((lanes & ~31) != 0 || lanes == 0) throw new IllegalArgumentException("Expected five-lane hit mask");
        if (ticks < lastTick) clear();
        lastTick = ticks;
        for (int lane = 0; lane < 5; lane++) {
            if ((lanes & (1 << lane)) != 0) strikes[role.ordinal()][lane] = ticks;
        }
    }

    void beginDraw(long ticks, boolean playing) {
        // A paused/menu draw cancels pending gestures: resuming cannot replay an old hit.
        if (!playing || ticks < lastTick) clear();
        lastTick = ticks;
    }

    int age(Role role, int lanes, long ticks) {
        int age = RING_TICKS;
        for (int lane = 0; lane < 5; lane++) {
            long at = strikes[role.ordinal()][lane];
            if ((lanes & (1 << lane)) == 0 || at == Long.MIN_VALUE || ticks < at) continue;
            long elapsed = ticks - at;
            if (elapsed >= 0 && elapsed < age) age = (int) elapsed;
        }
        return age;
    }

    int stroke(Role role, int lanes, long ticks) {
        int age = age(role, lanes, ticks);
        if (age == 0) return -2; // anticipation
        if (age < 4) return 3;   // contact
        if (age < 7) return -2;  // rebound
        if (age < ATTACK_TICKS) return -1;
        return 0;
    }

    private void clear() { for (long[] role : strikes) Arrays.fill(role, Long.MIN_VALUE); }
}
