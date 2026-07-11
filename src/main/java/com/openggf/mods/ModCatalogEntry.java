package com.openggf.mods;

import java.nio.file.Path;
import java.util.List;

/**
 * One tagged scanner result: a valid mod descriptor, an invalid per-jar entry,
 * or a repository-wide failure that downstream UI must render separately.
 */
public sealed interface ModCatalogEntry permits ModDescriptor, InvalidModEntry, RepositoryScanFailure {
    Path sourcePath();
    /**
     * Compatibility accessor for the pinned catalog API. Callers MUST pattern-match
     * {@link RepositoryScanFailure} before treating this path as a jar; for that tagged
     * variant it is the repository path and must be rendered as a banner, never a mod row.
     */
    default Path jarPath() { return sourcePath(); }
    List<ModFinding> findings();
}
