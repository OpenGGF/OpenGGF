package com.openggf.audio.session;

import com.openggf.audio.rewind.SmpsDriverSnapshot;

/** Host-owned native driver pause, independent of speaker/window suspension. */
public interface SmpsNativePausePolicy {
    SmpsNativePausePolicy NONE = new SmpsNativePausePolicy() { };

    default void requireSupported() {
        throw new UnsupportedOperationException(
                "native audio pause is unavailable for this host profile");
    }

    default SmpsWriteProgram pause() {
        requireSupported();
        throw new UnsupportedOperationException("native pause writes unavailable");
    }

    default SmpsWriteProgram resume(SmpsDriverSnapshot logical) {
        requireSupported();
        throw new UnsupportedOperationException("native resume projection unavailable");
    }

    /** S2 returns to DAC even on resume; S3K continues zUpdateEverything. */
    default boolean resumeContinuesService() {
        return true;
    }
}
