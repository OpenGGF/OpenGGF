package com.openggf.route;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The reference-following candidate families, moved here from {@code ReferencePathInputAuthor}
 * (cold LRZ→HPZ→SSZ campaign, 2026-10-15): time-shifted and delayed source rows, nineteen reactive
 * steer/jump policies toward a target forty indices ahead, spin dashes and wait-then-jump. They
 * choose rows from copied observations only; the search records the rows the host accepted.
 */
final class ReferenceCandidates {
    static final int POLICY_LEAD = 40;
    private static final int POLICIES = 19;
    private static final int RIGHT = PhysicalButton.RIGHT.bit();
    private static final int LEFT = PhysicalButton.LEFT.bit();
    private static final int DOWN = PhysicalButton.DOWN.bit();
    private static final int A = PhysicalButton.A.bit();

    private ReferenceCandidates() {
    }

    /** A row policy; a fresh instance per trial, so per-trial state starts clean. */
    interface RowPolicy {
        PadRow row(int t, RouteSample now, PadRow previous, int prog);
    }

    static List<Supplier<RowPolicy>> families(ReferenceProgress progress, int k, boolean wide) {
        ReferencePath ref = progress.reference();
        var list = new ArrayList<Supplier<RowPolicy>>();
        int[] shifts = wide ? new int[]{0, -3, 3, -8, 8, -16, 16, -30, 30} : new int[]{0, -4, 4, -12, 12};
        for (int s : shifts) {
            int from = k + 1 + s;
            list.add(() -> (t, now, previous, prog) -> source(ref, from + t));
        }
        for (int w : wide ? new int[]{6, 12, 24, 40, 64} : new int[]{8, 20}) {
            int from = k + 1;
            list.add(() -> (t, now, previous, prog) -> t < w ? PadRow.NEUTRAL : source(ref, from + t - w));
        }
        for (int policy = 0; policy < POLICIES; policy++) {
            int pol = policy;
            list.add(() -> (t, now, previous, prog) -> policyRow(pol, t, now, previous, progress.target(prog)[0]));
        }
        for (int charges : wide ? new int[]{1, 2, 4} : new int[]{1, 3}) {
            list.add(() -> spinDash(charges, false, progress));
            if (wide) list.add(() -> spinDash(charges, true, progress));
        }
        int[] waits = wide ? new int[]{0, 8, 16, 24, 32, 44, 56, 72, 96} : new int[]{0, 12, 24, 40};
        for (int dirMode = 0; dirMode < 3; dirMode++) {
            for (int w : waits) {
                int mode = dirMode;
                list.add(() -> waitThenJump(mode, w, progress));
            }
        }
        return list;
    }

    /** A source row by index, held at the source's ends (a candidate choice, not tape clamping). */
    private static PadRow source(ReferencePath ref, int index) {
        return ref.sourceRow(Math.max(0, Math.min(ref.size() - 1, index)));
    }

    /** Reactive pad: direction mode (hold toward, steer, neutral, hold away) by jump mode. */
    private static PadRow policyRow(int policy, int t, RouteSample now, PadRow previous, int tx) {
        PlayerView player = now.p1();
        boolean held = (previous.p1() & A) != 0;
        int dx = tx - player.centreX();
        int dirMode;
        int jumpMode;
        if (policy == 12) { dirMode = 2; jumpMode = 0; }
        else if (policy > 12) { dirMode = (policy - 13) / 2; jumpMode = 4 + (policy - 13) % 2; }
        else { dirMode = policy / 4 == 2 ? 3 : policy / 4; jumpMode = policy % 4; }
        int toward = dx > 0 ? RIGHT : dx < 0 ? LEFT : 0;
        int dir = 0;
        if (dirMode == 0) dir = Math.abs(dx) > 2 ? toward : 0;
        else if (dirMode == 1) {
            double desired = Math.max(-6, Math.min(6, dx / 8.0));
            double speed = player.xSpeed() / 256.0;
            dir = speed > desired + 0.2 ? LEFT : speed < desired - 0.2 ? RIGHT : 0;
        } else if (dirMode == 3) dir = toward == RIGHT ? LEFT : toward == LEFT ? RIGHT : 0;
        boolean jump = switch (jumpMode) {
            case 1 -> player.air() ? held : !held;
            case 2 -> t == 0 ? !player.air() && !held : held && player.air();
            case 3 -> player.air() ? held && t % 40 < 8 : !held;
            case 4 -> true;
            case 5 -> t % 10 < 5;
            default -> false;
        };
        return new PadRow(dir | (jump ? A : 0), 0);
    }

    /** Holds toward the target (or neutral), jumps once grounded after {@code wait}, holds A 24 rows. */
    private static RowPolicy waitThenJump(int dirMode, int wait, ReferenceProgress progress) {
        int[] jumpAt = {-1};
        return (t, now, previous, prog) -> {
            int[] r = progress.target(prog);
            int dx = r[0] - now.p1().centreX();
            int toward = Math.abs(dx) <= 2 ? 0 : dx > 0 ? RIGHT : LEFT;
            int dir = dirMode == 0 ? toward : dirMode == 1 ? 0 : (t < wait ? 0 : toward);
            if (jumpAt[0] < 0 && t >= wait && !now.p1().air()) jumpAt[0] = t;
            boolean jump = jumpAt[0] >= 0 && t - jumpAt[0] < 24;
            return new PadRow(dir | (jump ? A : 0), 0);
        };
    }

    /**
     * Faces the target for one row, holds Down for the duck, charges with {@code charges} A
     * presses, then releases Down (neutral, or holding toward the target).
     */
    private static RowPolicy spinDash(int charges, boolean holdToward, ReferenceProgress progress) {
        final int duckRows = 12;
        return (t, now, previous, prog) -> {
            int[] r = progress.target(prog);
            int toward = r[0] >= now.p1().centreX() ? RIGHT : LEFT;
            if (t == 0) return new PadRow(toward, 0);
            if (t < duckRows) return new PadRow(DOWN, 0);
            int c = t - duckRows;
            if (c < charges * 2 + 2) return new PadRow(DOWN | (c < charges * 2 && c % 2 == 0 ? A : 0), 0);
            return new PadRow(holdToward ? toward : 0, 0);
        };
    }
}
