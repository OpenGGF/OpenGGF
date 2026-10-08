package com.openggf.mods;

import com.openggf.game.ModKeySyntax;
import com.openggf.io.ModInputLimits;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Optional ordering and incompatibility declarations; ordering never creates a dependency. */
public record ModCompositionMetadata(List<String> before, List<String> after,
        List<String> conflictsWith, Set<String> exclusiveContributions) {
    public static final ModCompositionMetadata EMPTY = new ModCompositionMetadata(
            List.of(), List.of(), List.of(), Set.of());
    public ModCompositionMetadata {
        before = owners(before, "before");
        after = owners(after, "after");
        conflictsWith = owners(conflictsWith, "conflictsWith");
        exclusiveContributions = Set.copyOf(Objects.requireNonNull(exclusiveContributions));
        if (exclusiveContributions.size() > ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES)
            throw new IllegalArgumentException("Too many exclusive contribution claims");
        exclusiveContributions.forEach(ModCompositionMetadata::requireContribution);
    }
    void requireDistinctOwner(String owner) {
        if (before.contains(owner) || after.contains(owner) || conflictsWith.contains(owner))
            throw new IllegalArgumentException("Composition metadata cannot reference its own owner: " + owner);
    }
    private static List<String> owners(List<String> values, String field) {
        List<String> frozen = List.copyOf(Objects.requireNonNull(values, field));
        if (frozen.size() > ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES
                || Set.copyOf(frozen).size() != frozen.size())
            throw new IllegalArgumentException("Duplicate or unbounded " + field + " owners");
        frozen.forEach(ModKeySyntax::requireManifestId);
        return frozen;
    }
    public static String requireContribution(String key) {
        Objects.requireNonNull(key, "contribution");
        if (Set.of("startup-scene", "display-width", "game-start").contains(key)) return key;
        if (key.startsWith("art:") && key.length() > 4) {
            ModManifest.requireArtOverrideKey(key.substring(4)); return key;
        }
        if (key.matches("audio:(0|[1-9][0-9]*)")) {
            Integer.parseInt(key.substring(6)); return key;
        }
        throw new IllegalArgumentException("Unknown exclusive contribution: " + key);
    }
}
