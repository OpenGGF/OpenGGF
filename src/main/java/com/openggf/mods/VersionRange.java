package com.openggf.mods;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record VersionRange(List<VersionConstraint> constraints) {
    private static final Comparator<VersionConstraint> CANONICAL_ORDER =
            Comparator.comparingInt((VersionConstraint value) -> rank(value.operator()))
                    .thenComparing(VersionConstraint::version);
    private static final SemanticVersion MIN = new SemanticVersion(0, 0, 0);
    private static final SemanticVersion MAX = new SemanticVersion(
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

    public VersionRange {
        List<VersionConstraint> normalized = new ArrayList<>(
                Objects.requireNonNull(constraints, "constraints"));
        normalized.sort(CANONICAL_ORDER);
        constraints = List.copyOf(normalized);
        if (constraints.size() > 4 || constraints.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("A version range accepts at most four non-null constraints");
        }
        requireNonEmpty(constraints);
    }

    public static VersionRange parse(String text) {
        Objects.requireNonNull(text, "text");
        if (text.equals("*")) {
            return new VersionRange(List.of());
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("Version range must be nonblank");
        }
        String[] tokens = text.trim().split("\\s+");
        if (tokens.length > 4) {
            throw new IllegalArgumentException("A version range accepts at most four comparators");
        }
        List<VersionConstraint> parsed = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            parsed.add(parseConstraint(token));
        }
        return new VersionRange(parsed);
    }

    public boolean contains(SemanticVersion version) {
        Objects.requireNonNull(version, "version");
        return constraints.stream().allMatch(constraint -> constraint.matches(version));
    }

    @Override
    public String toString() {
        if (constraints.isEmpty()) {
            return "*";
        }
        StringBuilder result = new StringBuilder();
        for (VersionConstraint constraint : constraints) {
            if (!result.isEmpty()) result.append(' ');
            if (!(constraints.size() == 1 && constraint.operator() == VersionOperator.EQ)) {
                result.append(switch (constraint.operator()) {
                    case LT -> "<";
                    case LTE -> "<=";
                    case EQ -> "=";
                    case GTE -> ">=";
                    case GT -> ">";
                });
            }
            result.append(constraint.version());
        }
        return result.toString();
    }

    private static VersionConstraint parseConstraint(String token) {
        String operatorText;
        String versionText;
        if (Character.isDigit(token.charAt(0))) {
            operatorText = "=";
            versionText = token;
        } else if (token.startsWith("<=") || token.startsWith(">=")) {
            operatorText = token.substring(0, 2);
            versionText = token.substring(2);
        } else if (token.startsWith("<") || token.startsWith(">") || token.startsWith("=")) {
            operatorText = token.substring(0, 1);
            versionText = token.substring(1);
        } else {
            throw new IllegalArgumentException("Unsupported version comparator: " + token);
        }
        if (versionText.isEmpty()) {
            throw new IllegalArgumentException("Missing comparator version: " + token);
        }
        VersionOperator operator = switch (operatorText) {
            case "<" -> VersionOperator.LT;
            case "<=" -> VersionOperator.LTE;
            case "=" -> VersionOperator.EQ;
            case ">=" -> VersionOperator.GTE;
            case ">" -> VersionOperator.GT;
            default -> throw new IllegalArgumentException("Unsupported version comparator: " + token);
        };
        return new VersionConstraint(operator, SemanticVersion.parse(versionText));
    }

    private static int rank(VersionOperator operator) {
        return switch (operator) {
            case EQ -> 0;
            case GTE -> 1;
            case GT -> 2;
            case LT -> 3;
            case LTE -> 4;
        };
    }

    private static void requireNonEmpty(List<VersionConstraint> constraints) {
        SemanticVersion minimum = MIN;
        SemanticVersion maximum = MAX;
        for (VersionConstraint constraint : constraints) {
            switch (constraint.operator()) {
                case EQ -> {
                    minimum = max(minimum, constraint.version());
                    maximum = min(maximum, constraint.version());
                }
                case GTE -> minimum = max(minimum, constraint.version());
                case GT -> minimum = max(minimum, successor(constraint.version()));
                case LTE -> maximum = min(maximum, constraint.version());
                case LT -> maximum = min(maximum, predecessor(constraint.version()));
            }
        }
        if (minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Version range is contradictory or empty");
        }
        for (VersionConstraint constraint : constraints) {
            if (!constraint.matches(minimum) && !constraint.matches(maximum)) {
                throw new IllegalArgumentException("Version range is contradictory or empty");
            }
        }
    }

    private static SemanticVersion successor(SemanticVersion version) {
        if (version.patch() < Integer.MAX_VALUE) {
            return new SemanticVersion(version.major(), version.minor(), version.patch() + 1);
        }
        if (version.minor() < Integer.MAX_VALUE) {
            return new SemanticVersion(version.major(), version.minor() + 1, 0);
        }
        if (version.major() < Integer.MAX_VALUE) {
            return new SemanticVersion(version.major() + 1, 0, 0);
        }
        throw new IllegalArgumentException("Exclusive lower bound leaves no accepted version");
    }

    private static SemanticVersion predecessor(SemanticVersion version) {
        if (version.patch() > 0) {
            return new SemanticVersion(version.major(), version.minor(), version.patch() - 1);
        }
        if (version.minor() > 0) {
            return new SemanticVersion(version.major(), version.minor() - 1, Integer.MAX_VALUE);
        }
        if (version.major() > 0) {
            return new SemanticVersion(version.major() - 1, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
        throw new IllegalArgumentException("Exclusive upper bound leaves no accepted version");
    }

    private static SemanticVersion min(SemanticVersion left, SemanticVersion right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static SemanticVersion max(SemanticVersion left, SemanticVersion right) {
        return left.compareTo(right) >= 0 ? left : right;
    }
}
