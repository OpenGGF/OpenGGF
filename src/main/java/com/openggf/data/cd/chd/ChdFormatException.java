package com.openggf.data.cd.chd;

import java.io.IOException;

/** A CHD's bytes break the format, or ask for a feature this reader refuses. */
public final class ChdFormatException extends IOException {
    public ChdFormatException(String message) {
        super(message);
    }

    public ChdFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
