package com.openggf.game.sonic2.audio;

import com.openggf.audio.session.LegacyCompatibilitySmpsPhysicalPolicy;
import com.openggf.audio.session.SmpsMusicActivation;
import com.openggf.audio.session.SmpsPhysicalPolicy;
import com.openggf.audio.session.SmpsWriteProgram;

/** Named S2 host policy retaining the verified pre-migration 202-write program. */
public final class Sonic2SmpsCompatibilityPolicy
        implements SmpsPhysicalPolicy {
    public static final Sonic2SmpsCompatibilityPolicy INSTANCE =
            new Sonic2SmpsCompatibilityPolicy();

    private static final Identity IDENTITY =
            new Identity("sonic2-compatibility-v1");
    private static final LegacyCompatibilitySmpsPhysicalPolicy DELEGATE =
            LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE;

    private Sonic2SmpsCompatibilityPolicy() {
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
