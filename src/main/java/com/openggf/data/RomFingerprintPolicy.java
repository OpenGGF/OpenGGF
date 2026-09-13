package com.openggf.data;

public enum RomFingerprintPolicy {
    /** The file was not inspected; the location is the literal configured value. */
    NONE,
    /** Hashes identify the image and break ties between duplicates; a mismatch never blocks boot. */
    IDENTITY
}
