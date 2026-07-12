package com.openggf;

import com.openggf.data.Rom;
import com.openggf.game.GameDataSource;
import com.openggf.game.ModKeySyntax;
import com.openggf.io.ModAssetRoot;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

/** No-ROM source backed by an immutable bounded mod-asset snapshot. */
final class ModAssetDataSource implements GameDataSource {
    private final String identity;
    private final ModAssetRoot assets;

    ModAssetDataSource(String ownerModId, ModAssetRoot assets) throws IOException {
        this.assets = Objects.requireNonNull(assets, "assets");
        if (!(assets instanceof com.openggf.io.SnapshotModAssetRoot snapshot)) {
            throw new IllegalArgumentException("Mod game sources require an immutable asset snapshot");
        }
        this.identity = "mod:" + ModKeySyntax.requireManifestId(ownerModId) + ":"
                + snapshot.immutableSha256();
    }

    @Override public Optional<Rom> rom() { return Optional.empty(); }

    @Override public InputStream openAsset(String normalizedPath) throws IOException {
        String path = ModAssetRoot.requireNormalizedEntry(normalizedPath);
        return new ByteArrayInputStream(assets.readBounded(path, assets.limits().maxAssetBytes()));
    }

    @Override public String identity() { return identity; }
}
