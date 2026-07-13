package com.openggf.game;

import com.openggf.data.Rom;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import static java.security.MessageDigest.getInstance;

/** Composition-root helper for pinning one already-open stock ROM to a world session. */
public final class StockGameDataSources {
    private StockGameDataSources() { }

    public static RomDataSource pinned(Rom rom, GameModule module) throws IOException {
        Objects.requireNonNull(rom, "rom");
        Objects.requireNonNull(module, "module");
        try {
            String digest = HexFormat.of().formatHex(
                    getInstance("SHA-256").digest(rom.readAllBytes()));
            return new RomDataSource(rom,
                    "rom:" + module.getIdentifier() + ":sha256:" + digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
