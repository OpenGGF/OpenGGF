package com.openggf.mods;

import java.util.Objects;

public record VersionConstraint(VersionOperator operator, SemanticVersion version) {
    public VersionConstraint {
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(version, "version");
    }

    boolean matches(SemanticVersion candidate) {
        int compared = candidate.compareTo(version);
        return switch (operator) {
            case LT -> compared < 0;
            case LTE -> compared <= 0;
            case EQ -> compared == 0;
            case GTE -> compared >= 0;
            case GT -> compared > 0;
        };
    }
}
