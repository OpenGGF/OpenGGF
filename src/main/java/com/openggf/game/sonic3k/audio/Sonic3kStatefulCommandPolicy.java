package com.openggf.game.sonic3k.audio;

import com.openggf.audio.session.SmpsStatefulCommandOperation;
import com.openggf.audio.session.SmpsStatefulCommandPolicy;

/** Host-owned S3K stateful-command semantics, independent of donor content. */
public final class Sonic3kStatefulCommandPolicy
        implements SmpsStatefulCommandPolicy {
    public static final Sonic3kStatefulCommandPolicy INSTANCE =
            new Sonic3kStatefulCommandPolicy();

    private static final Identity IDENTITY = new Identity("sonic3k-e4-v1");

    private Sonic3kStatefulCommandPolicy() {
    }

    @Override
    public Identity identity() {
        return IDENTITY;
    }

    @Override
    public SmpsStatefulCommandOperation prepare(
            SmpsStatefulCommandOperation.Input input) {
        if (!(input.command()
                instanceof com.openggf.audio.session.SmpsSessionCommand.StopSmpsSfx)) {
            return SmpsStatefulCommandOperation.none(input);
        }
        S3kE4StopSfxPlan plan = S3kE4StopSfxPlan.prepare(
                S3kE4Projection.capture(input.ownership()));
        return plan.accepted()
                ? SmpsStatefulCommandOperation.stopSmpsSfx(input,
                        new com.openggf.audio.session.SmpsWriteProgram(
                                plan.writes()))
                : SmpsStatefulCommandOperation.reject(input);
    }
}
