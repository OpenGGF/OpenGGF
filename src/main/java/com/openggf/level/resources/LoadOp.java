package com.openggf.level.resources;

import com.openggf.io.ModAssetRoot;

import java.util.Objects;

/**
 * Describes a single load operation for level resources.
 * Multiple LoadOps can be combined to create composite resources
 * (e.g., base + overlay pattern).
 *
 * @param source         ROM address or bounded mod asset to load from
 * @param compressionType Type of compression used (KOSINSKI, NEMESIS, or UNCOMPRESSED)
 * @param destOffsetBytes Byte offset in the destination buffer where this data should be written.
 *                        Use 0 for base loads; use specific offsets for overlays; use
 *                        {@link #APPEND_TO_PREVIOUS} to append after the currently composed extent.
 */
public record LoadOp(LoadSource source, CompressionType compressionType, int destOffsetBytes) {
    /** Sentinel destination meaning "append after the data composed so far". */
    public static final int APPEND_TO_PREVIOUS = -1;

    public LoadOp {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(compressionType, "compressionType");
        if (destOffsetBytes < 0 && destOffsetBytes != APPEND_TO_PREVIOUS) {
            throw new IllegalArgumentException("Invalid destination offset: " + destOffsetBytes);
        }
        if (source instanceof LoadSource.ModAsset && compressionType != CompressionType.UNCOMPRESSED) {
            throw new IllegalArgumentException("Mod assets must use UNCOMPRESSED operations");
        }
    }

    /**
     * Creates a load operation with offset 0 (base load).
     */
    public static LoadOp base(int romAddr, CompressionType compressionType) {
        return new LoadOp(new LoadSource.RomAddress(romAddr), compressionType, 0);
    }

    /**
     * Creates an overlay load operation at the specified byte offset.
     */
    public static LoadOp overlay(int romAddr, CompressionType compressionType, int destOffsetBytes) {
        return new LoadOp(new LoadSource.RomAddress(romAddr), compressionType, destOffsetBytes);
    }

    /**
     * Creates a load operation that appends after the currently composed extent.
     */
    public static LoadOp append(int romAddr, CompressionType compressionType) {
        return new LoadOp(new LoadSource.RomAddress(romAddr), compressionType, APPEND_TO_PREVIOUS);
    }

    /**
     * Creates a Kosinski-compressed base load operation.
     */
    public static LoadOp kosinskiBase(int romAddr) {
        return base(romAddr, CompressionType.KOSINSKI);
    }

    /**
     * Creates a Kosinski-compressed overlay load operation.
     */
    public static LoadOp kosinskiOverlay(int romAddr, int destOffsetBytes) {
        return overlay(romAddr, CompressionType.KOSINSKI, destOffsetBytes);
    }

    /**
     * Creates a Kosinski-compressed append load operation.
     */
    public static LoadOp kosinskiAppend(int romAddr) {
        return append(romAddr, CompressionType.KOSINSKI);
    }

    /**
     * Creates a Kosinski Moduled base load operation.
     */
    public static LoadOp kosinskiMBase(int romAddr) {
        return base(romAddr, CompressionType.KOSINSKI_MODULED);
    }

    /**
     * Creates a Kosinski Moduled overlay load operation.
     */
    public static LoadOp kosinskiMOverlay(int romAddr, int destOffsetBytes) {
        return overlay(romAddr, CompressionType.KOSINSKI_MODULED, destOffsetBytes);
    }

    /**
     * Creates a Kosinski Moduled append load operation.
     */
    public static LoadOp kosinskiMAppend(int romAddr) {
        return append(romAddr, CompressionType.KOSINSKI_MODULED);
    }

    /**
     * Creates an uncompressed base load operation.
     */
    public static LoadOp uncompressedBase(int romAddr) {
        return base(romAddr, CompressionType.UNCOMPRESSED);
    }

    public static LoadOp modAssetBase(ModAssetRoot root, String entryPath) {
        return new LoadOp(new LoadSource.ModAsset(root, entryPath), CompressionType.UNCOMPRESSED, 0);
    }

    public static LoadOp modAssetOverlay(ModAssetRoot root, String entryPath, int destOffsetBytes) {
        return new LoadOp(new LoadSource.ModAsset(root, entryPath), CompressionType.UNCOMPRESSED, destOffsetBytes);
    }

    public static LoadOp modAssetAppend(ModAssetRoot root, String entryPath) {
        return new LoadOp(new LoadSource.ModAsset(root, entryPath), CompressionType.UNCOMPRESSED, APPEND_TO_PREVIOUS);
    }

    /**
     * Legacy compatibility accessor for ROM-sourced operations. Prefer {@link #source()} in new code.
     *
     * @throws IllegalStateException when this operation reads from a mod asset
     */
    public int romAddr() {
        if (source instanceof LoadSource.RomAddress rom) {
            return rom.addr();
        }
        throw new IllegalStateException("LoadOp has a non-ROM source: " + source);
    }

    /**
     * Returns true when this operation should append after the current composed extent.
     */
    public boolean appendsToPrevious() {
        return destOffsetBytes == APPEND_TO_PREVIOUS;
    }
}
