package com.openggf.game;

import com.openggf.data.Rom;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Durable session capability for either a stock ROM or bounded standalone assets.
 * The world session retains this same immutable source across gameplay/editor
 * context rebuilds.
 */
@ModApi
public interface GameDataSource {
    /** Returns the ROM capability for stock sessions; standalone sources are empty. */
    Optional<Rom> rom();

    /** Opens one normalized asset. Implementations must enforce their own byte/root limits. */
    InputStream openAsset(String normalizedPath) throws IOException;

    /** Stable diagnostic/cache identity for the immutable source snapshot. */
    String identity();

}
