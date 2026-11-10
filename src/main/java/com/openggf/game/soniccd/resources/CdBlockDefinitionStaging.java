package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable knowledge of 1024 four-WORD block definitions, separate from patterns
 * and canonical DATA. Native proof: N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff
 * Level/_Definitions.inc blockBuffer:114-115; IPX.asm RunMMD:1311-1319;
 * common/nemesis.asm WriteNemesisRow/WriteNemesisRowXor:128-143.
 * Pure values/transforms have no source, original-clear, invocation or restore
 * authority. The enclosing original/loading/World owner must retain and validate
 * its actual source, thread and lifetime; no live value adopter is provided here.
 * UNKNOWN bytes are never readable as zero. Authored Java UNRUN.
 */
public final class CdBlockDefinitionStaging {
    public static final int BYTE_LENGTH = 8192;
    public static final int BLOCK_COUNT = 1024;
    private final byte[] values;
    private final byte[] known;

    private CdBlockDefinitionStaging(byte[] values, byte[] known) {
        this.values = values;
        this.known = known;
    }
    public static CdBlockDefinitionStaging unknown() {
        return new CdBlockDefinitionStaging(new byte[BYTE_LENGTH], new byte[BYTE_LENGTH]);
    }
    /** Pure effect of a completed real file-buffer clear; not proof it occurred. */
    public static CdBlockDefinitionStaging afterFileBufferClear() {
        byte[] mask = new byte[BYTE_LENGTH];
        Arrays.fill(mask, (byte) 1);
        return new CdBlockDefinitionStaging(new byte[BYTE_LENGTH], mask);
    }
    /** Pure overwrite of reached complete LONG rows at offset0; retain the tail.
     * Caller provenance is essential: neither canonical DATA nor a supplied prefix
     * can authorize a production load, World mutation or native-clear history.
     */
    public CdBlockDefinitionStaging withCompletedPrefix(CdAssetBytes prefix) {
        Objects.requireNonNull(prefix, "completed rows");
        int count = prefix.length();
        if (count > BYTE_LENGTH || (count & 3) != 0)
            throw new IllegalArgumentException("Expected completed four-byte rows within block definitions");
        if (count == 0) return this;
        byte[] updated = values.clone();
        byte[] mask = known.clone();
        System.arraycopy(prefix.copy(), 0, updated, 0, count);
        Arrays.fill(mask, 0, count, (byte) 1);
        return new CdBlockDefinitionStaging(updated, mask);
    }
    public int unsignedByte(int byteOffset) {
        Objects.checkIndex(byteOffset, BYTE_LENGTH);
        if (known[byteOffset] == 0) throw new UnknownRead(byteOffset);
        return Byte.toUnsignedInt(values[byteOffset]);
    }
    public int unsignedWord(int byteOffset) {
        Objects.checkFromIndexSize(byteOffset, 2, BYTE_LENGTH);
        return (unsignedByte(byteOffset) << 8) | unsignedByte(byteOffset + 1);
    }
    /** Caller has already applied the native chunk-word block selection/mask. */
    public int blockWord(int blockIndex, int wordIndex) {
        Objects.checkIndex(blockIndex, BLOCK_COUNT);
        Objects.checkIndex(wordIndex, 4);
        return unsignedWord(blockIndex * 8 + wordIndex * 2);
    }
    public CdAssetBytes knownMask() { return CdAssetBytes.copyOf(known); }
    public static final class UnknownRead extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final int byteOffset;
        private UnknownRead(int byteOffset) {
            super("BLOCK_STAGING unknown byte +" + byteOffset);
            this.byteOffset = byteOffset;
        }
        public int byteOffset() { return byteOffset; }
    }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof CdBlockDefinitionStaging value
                && Arrays.equals(values, value.values) && Arrays.equals(known, value.known);
    }
    @Override public int hashCode() {
        return 31 * Arrays.hashCode(values) + Arrays.hashCode(known);
    }
}
