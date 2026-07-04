package com.openggf.game.timeattack;

/** Physics build identity for replay verification routing (security spec §6.2). IO-free. */
public record DeterminismFingerprint(String engineVersion, int romChecksum) {
    public String asString() {
        return engineVersion + ":" + Integer.toHexString(romChecksum);
    }
}
