package com.openggf.mods;

import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.game.ModKeySyntax;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;

public record ModManifest(int formatVersion, String id, String name,
                          SemanticVersion version, List<String> authors, String description,
                          VersionRange engineApiRange, ModType type,
                          String baseGame, String entrypoint, List<ModDependency> dependencies,
                          Map<Integer, String> audioOverrides, Map<String, String> artOverrides,
                          String insertAfter, OptionalInt patternWindows) {
    private static final Pattern ENTRYPOINT = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+");
    private static final Pattern INSERT_AFTER = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");
    private static final Set<String> BASE_GAMES = Set.of("s1", "s2", "s3k");

    public ModManifest {
        if (formatVersion != 1) throw new IllegalArgumentException("formatVersion must be 1");
        id = ModKeySyntax.requireManifestId(id);
        name = requireDisplayText(name, "name");
        Objects.requireNonNull(version, "version");
        authors = List.copyOf(Objects.requireNonNull(authors, "authors"));
        if (authors.isEmpty() || authors.size() > 32) {
            throw new IllegalArgumentException("authors must contain 1 through 32 entries");
        }
        authors.forEach(author -> requireDisplayText(author, "author"));
        description = requireDisplayText(description, "description");
        Objects.requireNonNull(engineApiRange, "engineApiRange");
        Objects.requireNonNull(type, "type");
        requireBaseGame(type, baseGame);
        entrypoint = requireEntrypoint(entrypoint);
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        Set<String> dependencyIds = new HashSet<>();
        for (ModDependency dependency : dependencies) {
            if (!dependencyIds.add(dependency.id())) {
                throw new IllegalArgumentException("Duplicate dependency id: " + dependency.id());
            }
        }
        audioOverrides = Map.copyOf(Objects.requireNonNull(audioOverrides, "audioOverrides"));
        artOverrides = Map.copyOf(Objects.requireNonNull(artOverrides, "artOverrides"));
        audioOverrides.forEach((musicId, localName) -> {
            if (musicId == null || musicId < 0) {
                throw new IllegalArgumentException("audio override ids must be nonnegative");
            }
            ModKeySyntax.requireLocalName(localName);
        });
        artOverrides.forEach((stockKey, path) -> {
            requireDisplayText(stockKey, "art override key");
            requireArtPath(path, ModInputLimits.DEFAULT_MAX_ENTRY_NAME_BYTES);
        });
        insertAfter = requireInsertAfter(insertAfter);
        Objects.requireNonNull(patternWindows, "patternWindows");
        if (patternWindows.isPresent()
                && (patternWindows.getAsInt() < 1 || patternWindows.getAsInt() > 16)) {
            throw new IllegalArgumentException("patternWindows must be in 1..16");
        }
    }

    private static String requireDisplayText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > ModInputLimits.DEFAULT_MAX_STRING_CHARS) {
            throw new IllegalArgumentException(field + " must be nonblank and bounded");
        }
        return value;
    }

    static void requireBaseGame(ModType type, String baseGame) {
        if (type == ModType.PATCH && !BASE_GAMES.contains(baseGame)) {
            throw new IllegalArgumentException("patch manifests require baseGame s1, s2, or s3k");
        }
        if (type == ModType.STANDALONE && baseGame != null) {
            throw new IllegalArgumentException("standalone manifests forbid baseGame");
        }
    }

    static String requireEntrypoint(String entrypoint) {
        if (entrypoint != null && (entrypoint.length() > ModInputLimits.DEFAULT_MAX_STRING_CHARS
                || !ENTRYPOINT.matcher(entrypoint).matches())) {
            throw new IllegalArgumentException("Invalid entrypoint binary class name");
        }
        return entrypoint;
    }

    static String requireInsertAfter(String insertAfter) {
        if (insertAfter != null && !INSERT_AFTER.matcher(insertAfter).matches()) {
            throw new IllegalArgumentException("Invalid insertAfter stock key");
        }
        return insertAfter;
    }

    static String requireArtPath(String path, int maxUtf8Bytes) {
        String normalized = ModAssetRoot.requireNormalizedEntry(path);
        if (normalized.getBytes(StandardCharsets.UTF_8).length > maxUtf8Bytes) {
            throw new IllegalArgumentException("art override path exceeds entry-name byte limit");
        }
        return normalized;
    }
}
