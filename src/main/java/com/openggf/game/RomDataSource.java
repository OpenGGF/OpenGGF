package com.openggf.game;

import com.openggf.data.Rom;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

/** Stock-game source backed by one already-open ROM. */
@ModApi
public final class RomDataSource implements GameDataSource {
    private final Rom rom;
    private final String identity;

    public RomDataSource(Rom rom, String identity) {
        this.rom = Objects.requireNonNull(rom, "rom");
        this.identity = requireIdentity(identity);
    }

    @Override public Optional<Rom> rom() { return Optional.of(rom); }

    @Override public InputStream openAsset(String path) throws IOException {
        requireNormalizedPath(path);
        throw new IOException("ROM sources do not expose named assets");
    }

    @Override public String identity() { return identity; }

    static String requireIdentity(String value) {
        Objects.requireNonNull(value, "identity");
        if (value.isBlank()) throw new IllegalArgumentException("identity must not be blank");
        return value;
    }

    static void requireNormalizedPath(String path) {
        Objects.requireNonNull(path, "path");
        if (path.isEmpty() || path.indexOf('\\') >= 0 || path.startsWith("/")
                || java.nio.file.Path.of(path).isAbsolute()) {
            throw new IllegalArgumentException("asset path must be normalized and relative");
        }
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("asset path must be normalized and relative");
            }
        }
    }
}
