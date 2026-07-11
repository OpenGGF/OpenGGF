package com.openggf.mods.code;

import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** A validated pointer to one baked-sheet entry in the owning mod jar. */
public record BakedSheetRef(String entryPath) {
    public BakedSheetRef {
        entryPath = ModAssetRoot.requireNormalizedEntry(
                Objects.requireNonNull(entryPath, "entryPath"));
        if (entryPath.getBytes(StandardCharsets.UTF_8).length
                > ModInputLimits.DEFAULT_MAX_ENTRY_NAME_BYTES) {
            throw new IllegalArgumentException("Baked sheet entry path exceeds UTF-8 byte limit");
        }
    }
}
