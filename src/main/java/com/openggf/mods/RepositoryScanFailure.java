package com.openggf.mods;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** A repository-wide failure rendered as a banner rather than as a mod row. */
public record RepositoryScanFailure(Path repositoryPath, List<ModFinding> findings)
        implements ModCatalogEntry {
    public RepositoryScanFailure {
        Objects.requireNonNull(repositoryPath, "repositoryPath");
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        if (findings.isEmpty()
                || findings.stream().noneMatch(finding -> finding.severity() == ModFindingSeverity.ERROR)) {
            throw new IllegalArgumentException("Repository scan failures require at least one error finding");
        }
    }

    @Override
    public Path sourcePath() {
        return repositoryPath;
    }
}
