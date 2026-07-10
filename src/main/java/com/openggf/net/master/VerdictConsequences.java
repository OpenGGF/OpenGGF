package com.openggf.net.master;

import com.openggf.net.protocol.VerdictCodec;

import java.util.Objects;
import java.util.function.LongSupplier;

/** Persists verdicts and applies all trust consequences at one boundary. */
public final class VerdictConsequences {
    private final IdentityStore store;
    private final TrustLadder ladder;
    private final LongSupplier clock;
    private final long cheatBanMillis;

    public VerdictConsequences(IdentityStore store, TrustLadder ladder,
                               LongSupplier clock, long cheatBanMillis) {
        this.store = Objects.requireNonNull(store, "store");
        this.ladder = Objects.requireNonNull(ladder, "ladder");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cheatBanMillis = cheatBanMillis;
    }

    public boolean apply(IdentityStore.VerdictRecord verdict, String workerId) {
        if (!VerdictCodec.isWorkerResult(verdict.result())
                && !VerdictCodec.RESULT_VOID_NO_UPLOAD.equals(verdict.result())) {
            throw new IllegalArgumentException("unknown verdict result");
        }
        store.addVerdict(verdict);
        if (VerdictCodec.isFail(verdict.result())) {
            long now = clock.getAsLong();
            long expiry = cheatBanMillis <= 0 ? Long.MAX_VALUE
                    : saturatingAdd(now, cheatBanMillis);
            ladder.sanction(new IdentityStore.SanctionRecord(
                    verdict.fingerprint(), "BAN",
                    "cheat verdict: " + verdict.result(),
                    "verifier:" + Objects.requireNonNull(workerId, "workerId"),
                    now, expiry));
        }
        return VerdictCodec.isPass(verdict.result());
    }

    private static long saturatingAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
