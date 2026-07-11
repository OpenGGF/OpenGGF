package com.openggf.mods.validation;

import java.util.List;
import java.util.Objects;

/** Immutable result of validating one untrusted compiled-mod jar. */
public record ModValidationReport(List<ModValidationFinding> findings) {
    public ModValidationReport {
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
    }

    public boolean eligible() {
        return findings.stream().noneMatch(f -> f.severity() == ModValidationFinding.Severity.ERROR);
    }
}
