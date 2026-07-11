package com.openggf.mods;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SemanticVersion(int major, int minor, int patch)
        implements Comparable<SemanticVersion> {
    private static final Pattern STRICT_VERSION = Pattern.compile("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)");

    public SemanticVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Semantic version components must be nonnegative");
        }
    }

    public static SemanticVersion parse(String text) {
        Matcher matcher = STRICT_VERSION.matcher(Objects.requireNonNull(text, "text"));
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Version must be a strict MAJOR.MINOR.PATCH triple: " + text);
        }
        try {
            return new SemanticVersion(Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Version component exceeds Integer.MAX_VALUE: " + text, ex);
        }
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int compared = Integer.compare(major, other.major);
        if (compared == 0) compared = Integer.compare(minor, other.minor);
        if (compared == 0) compared = Integer.compare(patch, other.patch);
        return compared;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
