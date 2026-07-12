package com.openggf.mods;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Boot-lifetime runtime diagnostics, replaced owner-by-owner after each preparation attempt. */
public final class ModRuntimeFindingStore {
    private final Map<String, List<ModFinding>> byOwner = new LinkedHashMap<>();

    public synchronized void replaceOwner(String owner, List<ModFinding> findings) {
        Objects.requireNonNull(owner, "owner");
        List<ModFinding> immutable = List.copyOf(Objects.requireNonNull(findings, "findings"));
        if (immutable.isEmpty()) byOwner.remove(owner); else byOwner.put(owner, immutable);
    }

    /** Appends or replaces one stable-code finding without clobbering unrelated owner diagnostics. */
    public synchronized void upsertOwnerFinding(String owner, ModFinding finding) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(finding, "finding");
        java.util.ArrayList<ModFinding> updated = new java.util.ArrayList<>(findingsFor(owner));
        updated.removeIf(existing -> existing.code().equals(finding.code()));
        updated.add(finding);
        byOwner.put(owner, List.copyOf(updated));
    }

    public synchronized List<ModFinding> findingsFor(String owner) {
        return byOwner.getOrDefault(Objects.requireNonNull(owner, "owner"), List.of());
    }

    public synchronized Map<String, List<ModFinding>> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(byOwner));
    }
}
