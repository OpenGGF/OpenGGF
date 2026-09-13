package com.openggf.game.patch;

import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.ModApi;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Maps a logical ROM identity to bytes from the available physical images.
 *
 * <p>The engine's resolver is a facade over the ROM catalogue owned by
 * {@link RomManager}: any {@link LogicalRom} the user's images contain is
 * available, including windows of lock-on dumps and composites. The
 * {@link CombinedRomBytesSource} constructor remains for callers that hold a
 * combined Sonic 3 &amp; Knuckles image in memory; it serves {@code SK} as the
 * first 2 MiB, {@code S3} as the second, and {@code S3K} as the whole image,
 * and every window rejects reads outside its own half.
 */
@ModApi
public final class LogicalRomResolver {

    static final int SK_CART_SIZE = 0x200000;
    static final int S3_CART_SIZE = 0x200000;

    /** Supplies combined-ROM bytes, or {@code null} when none are available. */
    @FunctionalInterface
    @ModApi
    public interface CombinedRomBytesSource {
        byte[] get();
    }

    /** Engine-internal backend: how each logical ROM is looked up and opened. */
    interface Backend {
        /** Empty when nothing serves the ROM; present readers are fully resolved. */
        Optional<RomByteReader> open(LogicalRom rom) throws IOException;
    }

    private final Backend backend;

    public LogicalRomResolver(CombinedRomBytesSource combinedRomBytes) {
        this(new CombinedImageBackend(Objects.requireNonNull(combinedRomBytes, "combinedRomBytes")));
    }

    private LogicalRomResolver(Backend backend) {
        this.backend = backend;
    }

    /** Resolves every logical ROM through the manager's image catalogue. */
    public static LogicalRomResolver fromRomManager(RomManager romManager) {
        Objects.requireNonNull(romManager, "romManager");
        return new LogicalRomResolver(rom -> {
            if (romManager.resolveLogicalRom(rom.identity()).isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(romManager.openLogicalRom(rom.identity()));
        });
    }

    static LogicalRomResolver fromBackend(Backend backend) {
        return new LogicalRomResolver(Objects.requireNonNull(backend, "backend"));
    }

    public boolean isAvailable(LogicalRom rom) {
        return resolve(rom).reader() != null;
    }

    public RomByteReader openOrThrow(LogicalRom rom) {
        Resolution resolved = resolve(rom);
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

    /**
     * Not memoised here: the catalogue backend caches its own readers and is
     * rebuilt when the user changes ROM settings, and the combined-image
     * backend fetches its bytes once. Failures therefore surface again on the
     * next call instead of pinning a stale verdict for the engine's lifetime.
     */
    private Resolution resolve(LogicalRom rom) {
        Objects.requireNonNull(rom, "rom");
        try {
            return backend.open(rom).map(Resolution::available).orElseGet(Resolution::missing);
        } catch (java.io.UncheckedIOException e) {
            return Resolution.failed(e.getCause());
        } catch (IOException | RuntimeException e) {
            return Resolution.failed(e);
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

    /** Serves the S&amp;K, S3 and S3K windows of one combined image held in memory. */
    private static final class CombinedImageBackend implements Backend {
        private final CombinedRomBytesSource source;
        private RomByteReader combined;
        private boolean fetched;

        CombinedImageBackend(CombinedRomBytesSource source) {
            this.source = source;
        }

        @Override
        public synchronized Optional<RomByteReader> open(LogicalRom rom) throws IOException {
            if (!fetched) {
                byte[] data = source.get();
                fetched = true;
                if (data != null) {
                    if (data.length < SK_CART_SIZE) {
                        throw new IOException("Combined ROM smaller than the S&K cart (need >= 0x200000 bytes, got 0x"
                                + Integer.toHexString(data.length) + ")");
                    }
                    combined = RomByteReader.fromBytes(data);
                }
            }
            if (combined == null) {
                return Optional.empty();
            }
            return switch (rom) {
                case SK -> Optional.of(combined.window(0, SK_CART_SIZE));
                case S3 -> combined.size() >= SK_CART_SIZE + S3_CART_SIZE
                        ? Optional.of(combined.window(SK_CART_SIZE, S3_CART_SIZE)) : Optional.empty();
                case S3K -> combined.size() >= SK_CART_SIZE + S3_CART_SIZE
                        ? Optional.of(combined.window(0, SK_CART_SIZE + S3_CART_SIZE)) : Optional.empty();
                default -> Optional.empty();
            };
        }
    }
}
