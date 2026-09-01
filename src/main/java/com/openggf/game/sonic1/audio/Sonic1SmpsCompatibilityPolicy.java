package com.openggf.game.sonic1.audio;

import com.openggf.audio.session.LegacyCompatibilitySmpsPhysicalPolicy;
import com.openggf.audio.session.SmpsMusicActivation;
import com.openggf.audio.session.SmpsPhysicalPolicy;
import com.openggf.audio.session.SmpsWriteProgram;

/** Named S1 host policy retaining the verified pre-migration 202-write program. */
public final class Sonic1SmpsCompatibilityPolicy
        implements SmpsPhysicalPolicy {
    public static final Sonic1SmpsCompatibilityPolicy INSTANCE =
            new Sonic1SmpsCompatibilityPolicy();

    private static final Identity IDENTITY =
            new Identity("sonic1-compatibility-v1");
    private static final LegacyCompatibilitySmpsPhysicalPolicy DELEGATE =
            LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE;

    private Sonic1SmpsCompatibilityPolicy() {
    }

    @Override
    public Identity identity() {
        return IDENTITY;
    }

    @Override
    public SmpsWriteProgram boot() {
        return DELEGATE.boot();
    }

    @Override
    public SmpsWriteProgram stopAll() {
        return DELEGATE.stopAll();
    }

    @Override
    public SmpsWriteProgram activateMusic(SmpsMusicActivation activation) {
        return DELEGATE.activateMusic(activation);
    }
}
