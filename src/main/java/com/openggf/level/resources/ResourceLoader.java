package com.openggf.level.resources;

import com.openggf.data.Rom;
import com.openggf.tools.KosinskiReader;
import com.openggf.tools.NemesisReader;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Loads level resources from ROM with support for overlay composition.
 *
 * <p>Overlay loading works by:
 * <ol>
 *   <li>Pre-reading bounded mod assets and validating their source/output budgets</li>
 *   <li>Allocating a destination buffer, then decoding ROM sources sequentially</li>
 *   <li>Copying each result at its destOffset</li>
 *   <li>Operations are applied in order, so overlays overwrite base data</li>
 * </ol>
 *
 * <p>This class does NOT cache results. Callers should cache the returned
 * byte arrays if reuse is needed. This ensures that loading HTZ doesn't
 * accidentally mutate cached EHZ data.
 *
 * <p>Example:
 * <pre>{@code
 * ResourceLoader loader = new ResourceLoader(rom);
 *
 * // Load HTZ patterns with overlay
 * List<LoadOp> patternOps = htzPlan.getPatternOps();
 * byte[] patterns = loader.loadWithOverlays(patternOps, 0x10000);
 * }</pre>
 */
public class ResourceLoader {

    private static final Logger LOG = Logger.getLogger(ResourceLoader.class.getName());
    private static final boolean KOS_DEBUG_LOG = false;

    private final Rom rom;
    private final boolean strictModAssetsOnly;

    public ResourceLoader(Rom rom) {
        this.rom = rom;
        this.strictModAssetsOnly = false;
    }

    private ResourceLoader() {
        this.rom = null;
        this.strictModAssetsOnly = true;
    }

    /**
     * Creates a loader which accepts bounded mod assets only.
     *
     * <p>This is deliberately distinct from a nullable-ROM convention: any ROM
     * operation is rejected before a composed load reads its first asset.</p>
     */
    public static ResourceLoader forModAssetsOnly() {
        return new ResourceLoader();
    }

    /** Rejects unsupported sources without reading or allocating any resource data. */
    public void preflightSources(List<LoadOp> ops) throws IOException {
        if (ops == null) {
            throw new IllegalArgumentException("Load operations are required");
        }
        requireSupportedSources(ops);
    }

    /**
     * Loads and composes data from multiple LoadOps into a single buffer.
     *
     * <p>The final buffer size is the maximum of all operations (base + overlays),
     * ensuring all data is included. For proper alignment (e.g., for 128-byte blocks),
     * callers should either use aligned base data or handle alignment themselves.
     *
     * @param ops            List of load operations to apply in order.
     *                       The first op should be the base (destOffset=0).
     * @param initialBufferSize Initial buffer size hint.
     * @return The composed byte array with all operations applied
     * @throws IOException if decompression or source reading fails
     */
    public byte[] loadWithOverlays(List<LoadOp> ops, int initialBufferSize) throws IOException {
        if (ops == null || ops.isEmpty()) {
            throw new IllegalArgumentException("At least one LoadOp is required");
        }
        requireSupportedSources(ops);

        long outputCap = modOutputCap(ops);
        byte[][] cachedModData = null;
        if (outputCap != Long.MAX_VALUE) {
            if (initialBufferSize < 0) {
                throw new IllegalArgumentException("Initial buffer size must not be negative");
            }
            if (initialBufferSize > outputCap) {
                throw new IllegalArgumentException(
                        "Initial buffer size exceeds mod composed-output limit " + outputCap);
            }
            cachedModData = readModAssets(ops);
            preflightKnownModExtents(ops, cachedModData, outputCap);
        }

        byte[] buffer = new byte[initialBufferSize];
        int usedLength = 0;

        for (int i = 0; i < ops.size(); i++) {
            LoadOp op = ops.get(i);
            byte[] decompressed = cachedModData != null && cachedModData[i] != null
                    ? cachedModData[i]
                    : decompress(op);
            int destOffset = op.appendsToPrevious() ? usedLength : op.destOffsetBytes();
            int requiredSize = checkedComposedSize(destOffset, decompressed.length, outputCap);

            // Expand buffer if needed to accommodate this operation
            if (requiredSize > buffer.length) {
                buffer = Arrays.copyOf(buffer, requiredSize);
            }

            // Copy decompressed data into buffer at destOffset
            System.arraycopy(decompressed, 0, buffer, destOffset, decompressed.length);

            // Track the maximum extent of data
            usedLength = Math.max(usedLength, requiredSize);

            if (op.appendsToPrevious()) {
                LOG.fine(String.format("Applied append: %s -> offset 0x%04X (%d bytes)",
                        describeSource(op), destOffset, decompressed.length));
            } else if (op.destOffsetBytes() > 0) {
                LOG.fine(String.format("Applied overlay: %s -> offset 0x%04X (%d bytes)",
                        describeSource(op), op.destOffsetBytes(), decompressed.length));
            } else {
                LOG.fine(String.format("Loaded base: %s (%d bytes)",
                        describeSource(op), decompressed.length));
            }
        }

        // Trim buffer to actual used size
        if (usedLength < buffer.length) {
            buffer = Arrays.copyOf(buffer, usedLength);
        }

        return buffer;
    }

    /**
     * Loads and composes data from multiple LoadOps, with alignment enforcement.
     *
     * <p>Similar to {@link #loadWithOverlays(List, int)}, but the final buffer size
     * is rounded UP to the nearest multiple of the specified alignment.
     *
     * @param ops            List of load operations to apply in order.
     * @param initialBufferSize Initial buffer size hint.
     * @param alignment      Required alignment in bytes (e.g., 128 for blocks).
     * @return The composed byte array, sized to a multiple of alignment
     * @throws IOException if decompression or ROM reading fails
     */
    public byte[] loadWithOverlaysAligned(List<LoadOp> ops, int initialBufferSize, int alignment) throws IOException {
        byte[] buffer = loadWithOverlays(ops, initialBufferSize);

        // Round up to alignment boundary
        int remainder = buffer.length % alignment;
        if (remainder != 0) {
            int alignedSize = checkedComposedSize(
                    buffer.length, alignment - remainder, modOutputCap(ops));
            buffer = Arrays.copyOf(buffer, alignedSize);
        }

        return buffer;
    }

    /**
     * Loads a single LoadOp without overlay composition.
     * Equivalent to loadWithOverlays with a single-element list.
     */
    public byte[] loadSingle(LoadOp op) throws IOException {
        return decompress(op);
    }

    /**
     * Reads an operation from its source, decompressing ROM data when required.
     */
    private byte[] decompress(LoadOp op) throws IOException {
        if (op.source() instanceof LoadSource.ModAsset asset) {
            return asset.root().readBounded(
                    asset.entryPath(), asset.root().limits().maxAssetBytes());
        }

        requireRomAvailable();

        int romAddr = op.romAddr();
        return switch (op.compressionType()) {
            case KOSINSKI -> decompressKosinski(romAddr);
            case KOSINSKI_MODULED -> decompressKosinskiModuled(romAddr);
            case NEMESIS -> decompressNemesis(romAddr);
            case UNCOMPRESSED -> throw new UnsupportedOperationException(
                    "Uncompressed loading requires a size parameter. Use loadUncompressed() instead.");
        };
    }

    private static String describeSource(LoadOp op) {
        if (op.source() instanceof LoadSource.RomAddress source) {
            return String.format("ROM 0x%06X", source.addr());
        }
        LoadSource.ModAsset asset = (LoadSource.ModAsset) op.source();
        return asset.root().describe() + "!" + asset.entryPath();
    }

    private static long modOutputCap(List<LoadOp> ops) {
        long cap = Long.MAX_VALUE;
        for (LoadOp op : ops) {
            if (op.source() instanceof LoadSource.ModAsset asset) {
                cap = Math.min(cap, asset.root().limits().maxModValidationBytes());
            }
        }
        return cap;
    }

    private static byte[][] readModAssets(List<LoadOp> ops) throws IOException {
        byte[][] data = new byte[ops.size()][];
        for (int i = 0; i < ops.size(); i++) {
            if (ops.get(i).source() instanceof LoadSource.ModAsset asset) {
                data[i] = asset.root().readBounded(
                        asset.entryPath(), asset.root().limits().maxAssetBytes());
            }
        }
        return data;
    }

    private static void preflightKnownModExtents(
            List<LoadOp> ops, byte[][] cachedModData, long outputCap) throws IOException {
        int knownUsedLength = 0;
        boolean extentKnown = true;
        for (int i = 0; i < ops.size(); i++) {
            byte[] data = cachedModData[i];
            if (data == null) {
                extentKnown = false;
                continue;
            }
            LoadOp op = ops.get(i);
            if (op.appendsToPrevious() && !extentKnown) {
                continue;
            }
            int destOffset = op.appendsToPrevious() ? knownUsedLength : op.destOffsetBytes();
            int requiredSize = checkedComposedSize(destOffset, data.length, outputCap);
            if (extentKnown) {
                knownUsedLength = Math.max(knownUsedLength, requiredSize);
            }
        }
    }

    private static int checkedComposedSize(int destOffset, int dataLength, long outputCap)
            throws IOException {
        long requiredSize = (long) destOffset + dataLength;
        if (requiredSize > Integer.MAX_VALUE) {
            throw new IOException("Composed resource size exceeds Java array limit: " + requiredSize);
        }
        if (requiredSize > outputCap) {
            throw new IOException("Composed resource size " + requiredSize
                    + " exceeds mod output limit " + outputCap);
        }
        return (int) requiredSize;
    }

    /**
     * Decompresses Kosinski-compressed data from the specified ROM address.
     */
    private byte[] decompressKosinski(int romAddr) throws IOException {
        FileChannel channel = rom.getFileChannel();
        // Rom exposes a shared FileChannel; lock around seek+decode so concurrent
        // readers cannot move the channel position mid-stream.
        synchronized (rom) {
            channel.position(romAddr);
            return KosinskiReader.decompress(channel, KOS_DEBUG_LOG);
        }
    }

    /**
     * Decompresses Kosinski Moduled data from the specified ROM address.
     * KosM data has a 2-byte BE header giving the total uncompressed size,
     * followed by standard Kosinski modules at 16-byte aligned boundaries.
     */
    private byte[] decompressKosinskiModuled(int romAddr) throws IOException {
        // Read KosM 2-byte BE header to get total decompressed size
        byte[] header = rom.readBytes(romAddr, 2);
        if (header.length < 2) {
            throw new IOException(String.format(
                    "Insufficient ROM data for KosM header at 0x%X: got %d bytes", romAddr, header.length));
        }
        int fullSize = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);

        // Compressed data is smaller than decompressed, so fullSize is a safe upper bound
        // for input. Add extra for module alignment padding. Cap at 256KB to prevent issues,
        // and also cap against remaining ROM space (data near end of ROM, e.g., Gumball bonus
        // stage art at 0x3FEE2E in a 4MB ROM).
        int remaining;
        try {
            remaining = (int) Math.min(rom.getSize() - romAddr, Integer.MAX_VALUE);
        } catch (IOException e) {
            remaining = 0x40000; // Fallback: use cap if size unavailable
        }
        int inputSize = Math.min(Math.min(Math.max(fullSize + 256, 0x10000), 0x40000), remaining);
        byte[] romData = rom.readBytes(romAddr, inputSize);
        if (romData.length < inputSize) {
            throw new IOException("Short read for KosM data at 0x" + Integer.toHexString(romAddr));
        }
        return KosinskiReader.decompressModuled(romData, 0);
    }

    /**
     * Decompresses Nemesis-compressed data from the specified ROM address.
     */
    private byte[] decompressNemesis(int romAddr) throws IOException {
        FileChannel channel = rom.getFileChannel();
        // Rom exposes a shared FileChannel; lock around seek+decode so concurrent
        // readers cannot move the channel position mid-stream.
        synchronized (rom) {
            channel.position(romAddr);
            return NemesisReader.decompress(channel);
        }
    }

    /**
     * Loads uncompressed data from ROM.
     *
     * @param romAddr ROM address to read from
     * @param size    Number of bytes to read
     * @return The raw bytes from ROM
     * @throws IOException if reading fails
     */
    public byte[] loadUncompressed(int romAddr, int size) throws IOException {
        requireRomAvailable();
        return rom.readBytes(romAddr, size);
    }

    private void requireSupportedSources(List<LoadOp> ops) throws IOException {
        if (!strictModAssetsOnly) {
            return;
        }
        for (LoadOp op : ops) {
            if (op.source() instanceof LoadSource.RomAddress) {
                throw new IOException("ROM-backed LoadOp is not allowed by a mod-assets-only loader");
            }
        }
    }

    private void requireRomAvailable() throws IOException {
        if (rom == null) {
            throw new IOException("ROM-backed LoadOp is not allowed by a mod-assets-only loader");
        }
    }
}
