package com.openggf.game;

import com.openggf.data.Rom;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/** Durable session capability for either a stock ROM or bounded mod assets. */
@ModApi
public interface GameDataSource {
    Optional<Rom> rom();

    /** Opens one normalized asset. Implementations must enforce their own byte/root limits. */
    InputStream openAsset(String normalizedPath) throws IOException;

    /** Stable diagnostic/cache identity for the immutable source snapshot. */
    String identity();

}
