package com.openggf.game.patch;

import com.openggf.data.RomByteReader;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Maps a logical ROM identity to bytes from the available physical images.
 * The S&amp;K cart occupies {@code 0x000000-0x1FFFFF} in a combined S3K image,
 * so the returned window rejects every read into the S3 half.
 */
public final class LogicalRomResolver {

    static final int SK_CART_SIZE = 0x200000;

    /** Supplies combined-ROM bytes, or {@code null} when none are available. */
    @FunctionalInterface
    public interface CombinedRomBytesSource {
        byte[] get();
    }

    private final CombinedRomBytesSource combinedRomBytes;
    private volatile Resolution resolution;

    public LogicalRomResolver(CombinedRomBytesSource combinedRomBytes) {
        this.combinedRomBytes = Objects.requireNonNull(combinedRomBytes, "combinedRomBytes");
    }

    /** Uses the configured combined S3K image as the physical S&amp;K source. */
    public static LogicalRomResolver fromRomManager(RomManager romManager) {
        Objects.requireNonNull(romManager, "romManager");
        return new LogicalRomResolver(() -> {
            try {
                Rom combined = romManager.getSecondaryRom("s3k");
                return combined == null ? null : combined.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException("Combined S3K ROM unavailable", e);
            }
        });
    }

    public boolean isAvailable(LogicalRom rom) {
        requireSupported(rom);
        return resolve().reader() != null;
    }

    public RomByteReader openOrThrow(LogicalRom rom) {
        requireSupported(rom);
        Resolution resolved = resolve();
        if (resolved.failure() != null) {
            throw new IllegalStateException("Failed to open physical ROM for logical ROM " + rom,
                    resolved.failure());
        }
        if (resolved.reader() == null) {
            throw new IllegalStateException("No physical ROM available for logical ROM " + rom);
        }
        return resolved.reader();
    }

    public static RomByteReader windowSkFromCombined(byte[] combined) throws IOException {
        if (combined.length < SK_CART_SIZE) {
            throw new IOException("Combined ROM smaller than the S&K cart (need >= 0x200000 bytes, got 0x"
                    + Integer.toHexString(combined.length) + ")");
        }
        return RomByteReader.fromBytes(combined, 0, SK_CART_SIZE);
    }

    private Resolution resolve() {
        Resolution current = resolution;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (resolution == null) {
                resolution = resolveOnce();
            }
            return resolution;
        }
    }

    private Resolution resolveOnce() {
        try {
            byte[] data = combinedRomBytes.get();
            return data == null ? Resolution.missing() : Resolution.available(windowSkFromCombined(data));
        } catch (UncheckedIOException e) {
            return Resolution.failed(e.getCause());
        } catch (IOException | RuntimeException e) {
            return Resolution.failed(e);
        }
    }

    private static void requireSupported(LogicalRom rom) {
        if (Objects.requireNonNull(rom, "rom") != LogicalRom.SK) {
            throw new IllegalArgumentException("Unsupported logical ROM " + rom);
        }
    }

    private record Resolution(RomByteReader reader, Exception failure) {
        private static Resolution available(RomByteReader reader) {
            return new Resolution(reader, null);
        }

        private static Resolution missing() {
            return new Resolution(null, null);
        }

        private static Resolution failed(Exception failure) {
            return new Resolution(null, failure);
        }
    }
}
