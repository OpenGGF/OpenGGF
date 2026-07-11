package com.openggf.mods;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Valid manifest metadata and immutable-content identity for one packed mod. */
public record ModDescriptor(Path jarPath, ModManifest manifest, String sha256,
                            boolean containsCode, List<ModFinding> findings)
        implements ModCatalogEntry {
    public ModDescriptor {
        Objects.requireNonNull(jarPath, "jarPath");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(sha256, "sha256");
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hexadecimal characters");
        }
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
    }

    public boolean hasErrors() {
        return findings.stream().anyMatch(finding -> finding.severity() == ModFindingSeverity.ERROR);
    }

    @Override
    public Path sourcePath() {
        return jarPath;
    }
}
