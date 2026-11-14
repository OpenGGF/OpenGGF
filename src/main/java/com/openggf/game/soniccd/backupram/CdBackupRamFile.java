package com.openggf.game.soniccd.backupram;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * One directory entry of a backup-RAM volume as the BIOS reports it: an 11-byte name, the protect
 * (ECC) flag the file was written with, its first block and its length in blocks.
 */
public record CdBackupRamFile(byte[] name, int mode, int startBlock, int blocks) {
    public static final int NAME_BYTES = 11;   // BRFILENAMESZ (Backup RAM.inc:31)

    public CdBackupRamFile {
        Objects.requireNonNull(name, "name");
        if (name.length != NAME_BYTES) throw new IllegalArgumentException("A file name is 11 bytes");
        if (mode < 0 || mode > 0xFF) throw new IllegalArgumentException("mode is a byte");
        name = name.clone();
    }

    public static byte[] name(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length != NAME_BYTES) throw new IllegalArgumentException("A file name is 11 bytes: " + text);
        return bytes;
    }

    /** The BIOS stores the data of a file whose mode byte is non-zero in protected (ECC) blocks of 32 bytes. */
    public boolean protect() { return mode != 0; }

    public String nameText() { return new String(name, StandardCharsets.US_ASCII); }

    public boolean named(byte[] other) { return Arrays.equals(name, other); }

    /** The 16-byte descriptor: name, protect flag, start block, size (buram.c buram_write_file_desc). */
    byte[] descriptor() {
        byte[] out = new byte[16];
        System.arraycopy(name, 0, out, 0, NAME_BYTES);
        out[11] = (byte) mode;
        out[12] = (byte) (startBlock >> 8);
        out[13] = (byte) startBlock;
        out[14] = (byte) (blocks >> 8);
        out[15] = (byte) blocks;
        return out;
    }

    static CdBackupRamFile fromDescriptor(byte[] payload, int at) {
        return new CdBackupRamFile(Arrays.copyOfRange(payload, at, at + NAME_BYTES), payload[at + 11] & 0xFF,
                ((payload[at + 12] & 0xFF) << 8) | (payload[at + 13] & 0xFF),
                ((payload[at + 14] & 0xFF) << 8) | (payload[at + 15] & 0xFF));
    }

    @Override public boolean equals(Object o) {
        return o instanceof CdBackupRamFile f && Arrays.equals(name, f.name) && mode == f.mode
                && startBlock == f.startBlock && blocks == f.blocks;
    }

    @Override public int hashCode() { return Objects.hash(Arrays.hashCode(name), mode, startBlock, blocks); }

    @Override public String toString() { return nameText() + (protect() ? " protected" : "") + " @" + startBlock + " x" + blocks; }
}
