package com.openggf.data;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.CRC32;

/**
 * Size, CRC32 and SHA-1 of a byte range. Used only to identify images and
 * pick between duplicates; a mismatch never blocks boot.
 */
public record RomFingerprint(long size, String crc32Hex, String sha1Hex) {

    public static RomFingerprint of(byte[] data) {
        return of(data, 0, data.length);
    }

    public static RomFingerprint of(byte[] data, int offset, int length) {
        CRC32 crc = new CRC32();
        crc.update(data, offset, length);
        MessageDigest sha1;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
        sha1.update(data, offset, length);
        return new RomFingerprint(length,
                String.format("%08X", crc.getValue()),
                HexFormat.of().withUpperCase().formatHex(sha1.digest()));
    }

    /** True when the CRC32 and SHA-1 both match, ignoring case. */
    public boolean matches(String crc32Hex, String sha1Hex) {
        return this.crc32Hex.equalsIgnoreCase(crc32Hex) && this.sha1Hex.equalsIgnoreCase(sha1Hex);
    }

    @Override
    public String toString() {
        return "size=" + size + " CRC32=" + crc32Hex + " SHA-1=" + sha1Hex;
    }
}
