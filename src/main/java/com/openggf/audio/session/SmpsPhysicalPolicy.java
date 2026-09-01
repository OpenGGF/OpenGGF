package com.openggf.audio.session;

import java.util.Objects;

public interface SmpsPhysicalPolicy {
    record Identity(String value) {
        public Identity {
            value = Objects.requireNonNull(value, "value");
        }
    }

    Identity identity();

    SmpsWriteProgram boot();

    SmpsWriteProgram stopAll();

    SmpsWriteProgram activateMusic(SmpsMusicActivation activation);
}
