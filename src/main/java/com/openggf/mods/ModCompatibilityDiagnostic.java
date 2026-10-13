package com.openggf.mods;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Evidence about an enabled mod combination; an overlap alone never blocks play. */
public record ModCompatibilityDiagnostic(Level level, String code, String target,
                                         List<String> participants, String summary,
                                         List<String> evidence, List<String> blockedOwners) {
    /** Most producers report affected participants; dependency reports may identify a narrower blocked subset. */
    public ModCompatibilityDiagnostic(Level level, String code, String target,
                                      List<String> participants, String summary, List<String> evidence) {
        this(level, code, target, participants, summary, evidence,
                level == Level.VERIFIED_BLOCKER ? participants : List.of());
    }

    public ModCompatibilityDiagnostic {
        Objects.requireNonNull(level, "level");
        requireNonBlank(code, "code");
        if (!code.matches("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*")) {
            throw new IllegalArgumentException("Compatibility code must be uppercase snake case: " + code);
        }
        requireNonBlank(target, "target");
        requireNonBlank(summary, "summary");
        participants = normalizeOwners(participants, "participants");
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("Compatibility participants must not be empty");
        }
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        blockedOwners = normalizeOwners(blockedOwners, "blockedOwners");
        if (!participants.containsAll(blockedOwners)) {
            throw new IllegalArgumentException("Compatibility blocked owners must be participants");
        }
        if (level.blocking() && blockedOwners.isEmpty()) {
            throw new IllegalArgumentException("A verified blocker must identify blocked owners");
        }
        if (!level.blocking() && !blockedOwners.isEmpty()) {
            throw new IllegalArgumentException("Only a verified blocker may identify blocked owners");
        }
    }

    public boolean blocksPlay() {
        return level.blocking();
    }

    /** Participants can provide context without being unavailable themselves. */
    public boolean blocksPlayFor(String owner) {
        Objects.requireNonNull(owner, "owner");
        return level.blocking() && blockedOwners.contains(owner);
    }

    public enum Level {
        OBSERVED_OVERLAP,
        SUSPECTED_INCOMPATIBILITY,
        VERIFIED_BLOCKER;

        public boolean blocking() {
            return this == VERIFIED_BLOCKER;
        }
    }

    private static List<String> normalizeOwners(List<String> owners, String name) {
        var normalized = new TreeSet<String>();
        for (String owner : Objects.requireNonNull(owners, name)) {
            normalized.add(requireNonBlank(owner, name + " owner").strip());
        }
        return List.copyOf(normalized);
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Compatibility " + name + " must not be blank");
        }
        return value;
    }
}
