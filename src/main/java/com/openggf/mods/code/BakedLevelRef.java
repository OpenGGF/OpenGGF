package com.openggf.mods.code;

import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Validated pointer to the canonical metadata entry of one baked level export. */
@com.openggf.game.ModApi
public record BakedLevelRef(String levelJsonEntry) {
    public BakedLevelRef {
        levelJsonEntry = ModAssetRoot.requireNormalizedEntry(
                Objects.requireNonNull(levelJsonEntry, "levelJsonEntry"));
        if (!(levelJsonEntry.equals("level.json") || levelJsonEntry.endsWith("/level.json"))) {
            throw new IllegalArgumentException("Baked level reference must name level.json");
        }
        if (levelJsonEntry.getBytes(StandardCharsets.UTF_8).length
                > ModInputLimits.DEFAULT_MAX_ENTRY_NAME_BYTES) {
            throw new IllegalArgumentException("Baked level entry path exceeds UTF-8 byte limit");
        }
    }

    String resolveAsset(String relativeAsset) {
        String parent = levelJsonEntry.equals("level.json") ? ""
                : levelJsonEntry.substring(0, levelJsonEntry.length() - "level.json".length());
        return ModAssetRoot.requireNormalizedEntry(parent + relativeAsset);
    }
}
