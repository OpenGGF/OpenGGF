package com.openggf.game;

import com.openggf.data.Rom;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Durable session source with optional cartridge ROM and bounded named-asset capabilities.
 * The world session retains this same immutable source across gameplay/editor
 * context rebuilds.
 */
@ModApi
public interface GameDataSource {
    /** Returns cartridge bytes when this source has a ROM capability. */
    Optional<Rom> rom();

    /** Opens one normalized asset. Implementations must enforce their own byte/root limits. */
    InputStream openAsset(String normalizedPath) throws IOException;

    /** Stable diagnostic/cache identity for the immutable source snapshot. */
    String identity();

}
