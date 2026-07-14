package com.openggf.mods.code;

import com.openggf.game.ModApi;
import java.util.Objects;

/**
 * Staged request to materialize object art from the user's Sonic 2 ROM at gameplay launch.
 * Registration happens with no ROM open; addresses are validated against static bounds at
 * registration and against the real ROM during materialization. {@code paletteLine} is a
 * palette line index (0-3) into the active zone palette, not color data. {@code dplcAddress}
 * of 0 means the mapping frames reference art tiles directly (no DPLC flattening).
 * ROM-derived bytes are never persisted to disk.
 */
@ModApi
public record RomArtRequest(
        int artAddress,
        RomArtCompression compression,
        int uncompressedByteSize,
        int mappingAddress,
        int dplcAddress,
        int paletteLine,
        int bankSize) {

    public RomArtRequest {
        Objects.requireNonNull(compression, "compression");
        if (artAddress < 0 || mappingAddress < 0 || dplcAddress < 0) {
            throw new IllegalArgumentException("ROM addresses must be non-negative");
        }
        if (compression == RomArtCompression.UNCOMPRESSED) {
            if (uncompressedByteSize <= 0 || uncompressedByteSize % 32 != 0) {
                throw new IllegalArgumentException(
                        "uncompressedByteSize must be a positive multiple of 32 for UNCOMPRESSED art");
            }
        } else if (uncompressedByteSize != 0) {
            throw new IllegalArgumentException(
                    "uncompressedByteSize is only valid for UNCOMPRESSED art");
        }
        if (paletteLine < 0 || paletteLine > 3) {
            throw new IllegalArgumentException("paletteLine must be 0-3");
        }
        if (bankSize < 1) {
            throw new IllegalArgumentException("bankSize must be >= 1");
        }
    }

    public boolean hasDplc() {
        return dplcAddress != 0;
    }
}
