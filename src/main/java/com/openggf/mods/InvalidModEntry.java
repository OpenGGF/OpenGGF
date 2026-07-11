package com.openggf.mods;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** A discovered jar that could not produce a trustworthy manifest descriptor. */
public record InvalidModEntry(Path jarPath, List<ModFinding> findings)
        implements ModCatalogEntry {
    public InvalidModEntry {
        Objects.requireNonNull(jarPath, "jarPath");
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        if (findings.isEmpty()) {
            throw new IllegalArgumentException("Invalid mod entries require at least one finding");
        }
        if (findings.stream().noneMatch(finding -> finding.severity() == ModFindingSeverity.ERROR)) {
            throw new IllegalArgumentException("Invalid mod entries require at least one error finding");
        }
    }

    @Override
    public Path sourcePath() {
        return jarPath;
    }
}
