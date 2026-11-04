package com.openggf.route;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** A SHA-256 content identity, lower-case hex. */
public record ContentHash(String sha256) {
    public ContentHash {
        Objects.requireNonNull(sha256, "sha256");
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("not a lower-case SHA-256: " + sha256);
        }
    }

    public static ContentHash of(byte[] bytes) {
        return new ContentHash(HexFormat.of().formatHex(digest().digest(bytes)));
    }

    static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public String shortForm() {
        return sha256.substring(0, 12);
    }

    @Override
    public String toString() {
        return sha256;
    }
}
