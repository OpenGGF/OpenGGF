package com.openggf.level.resources;

import com.openggf.io.ModAssetRoot;

import java.util.Objects;

/** The source from which a level resource operation reads bytes. */
public sealed interface LoadSource {
    record RomAddress(int addr) implements LoadSource {}

    record ModAsset(ModAssetRoot root, String entryPath) implements LoadSource {
        public ModAsset {
            Objects.requireNonNull(root, "root");
            entryPath = ModAssetRoot.requireNormalizedEntry(entryPath);
        }
    }
}
