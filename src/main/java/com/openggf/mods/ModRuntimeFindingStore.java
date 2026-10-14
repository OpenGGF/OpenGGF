package com.openggf.mods;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owner findings and enabled-combination compatibility evidence have independent lifetimes. */
public final class ModRuntimeFindingStore {
    private static final int MAX_COMPATIBILITY_ENTRIES = 512;

    private final Map<String, List<ModFinding>> byOwner = new LinkedHashMap<>();
    private final Map<CompatibilitySource, ModCompatibilityDiagnostic> compatibility = new LinkedHashMap<>();
    private String compatibilityContext = "";

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

    /** A different enabled-set/artifact fingerprint invalidates compatibility evidence only. */
    public synchronized void beginCompatibilityContext(String fingerprint) {
        requireNonBlank(fingerprint, "fingerprint");
        if (!compatibilityContext.equals(fingerprint)) {
            compatibility.clear();
            compatibilityContext = fingerprint;
        }
    }

    /** Empty until the first compatibility context is established. */
    public synchronized String compatibilityContext() {
        return compatibilityContext;
    }

    /** Replaces one producer's reports without erasing reports published by other producers. */
    public synchronized void replaceCompatibility(String producer, List<ModCompatibilityDiagnostic> diagnostics) {
        requireNonBlank(producer, "producer");
        var replacement = new LinkedHashMap<CompatibilitySource, ModCompatibilityDiagnostic>();
        for (ModCompatibilityDiagnostic diagnostic : List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"))) {
            replacement.put(new CompatibilitySource(producer, CompatibilityKey.of(diagnostic)), diagnostic);
        }
        compatibility.keySet().removeIf(source -> source.producer().equals(producer));
        for (var entry : replacement.entrySet()) {
            compatibility.put(entry.getKey(), entry.getValue());
            enforceCompatibilityBound();
        }
    }

    /** Updates one complete identity; reports with the same code on other targets remain visible. */
    public synchronized void upsertCompatibility(String producer, ModCompatibilityDiagnostic diagnostic) {
        requireNonBlank(producer, "producer");
        Objects.requireNonNull(diagnostic, "diagnostic");
        compatibility.put(new CompatibilitySource(producer, CompatibilityKey.of(diagnostic)), diagnostic);
        enforceCompatibilityBound();
    }

    /** A multi-owner report is visible to every participant, with immutable snapshot semantics. */
    public synchronized List<ModCompatibilityDiagnostic> compatibilityFor(String owner) {
        Objects.requireNonNull(owner, "owner");
        return compatibilitySnapshot().stream()
                .filter(diagnostic -> diagnostic.participants().contains(owner)).toList();
    }

    /**
     * Deduplicates identities across producers and retains their distinct evidence in publication order.
     * The first surviving producer supplies the summary; removing it exposes the remaining report.
     */
    public synchronized List<ModCompatibilityDiagnostic> compatibilitySnapshot() {
        var unique = new LinkedHashMap<CompatibilityKey, ModCompatibilityDiagnostic>();
        for (var entry : compatibility.entrySet()) {
            unique.merge(entry.getKey().key(), entry.getValue(), ModRuntimeFindingStore::mergeCompatibilityEvidence);
        }
        return List.copyOf(unique.values());
    }

    /**
     * Keeps at most 512 producer-owned reports. Oldest nonblockers are evicted first; if every
     * report is a verified blocker, the oldest blocker is evicted. Updating an identity does
     * not renew its age. This also bounds duplicate reports retained for independent producers.
     */
    private void enforceCompatibilityBound() {
        while (compatibility.size() > MAX_COMPATIBILITY_ENTRIES) {
            var iterator = compatibility.entrySet().iterator();
            boolean removed = false;
            while (iterator.hasNext()) {
                if (!iterator.next().getValue().blocksPlay()) {
                    iterator.remove();
                    removed = true;
                    break;
                }
            }
            if (!removed) {
                var oldest = compatibility.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
        }
    }

    private static ModCompatibilityDiagnostic mergeCompatibilityEvidence(ModCompatibilityDiagnostic first,
                                                                        ModCompatibilityDiagnostic second) {
        var evidence = new LinkedHashSet<>(first.evidence());
        evidence.addAll(second.evidence());
        List<String> mergedEvidence = List.copyOf(evidence);
        var blockedOwners = new LinkedHashSet<>(first.blockedOwners());
        blockedOwners.addAll(second.blockedOwners());
        List<String> mergedBlockedOwners = List.copyOf(blockedOwners);
        if (mergedEvidence.equals(first.evidence()) && mergedBlockedOwners.equals(first.blockedOwners())) {
            return first;
        }
        return new ModCompatibilityDiagnostic(first.level(), first.code(), first.target(),
                first.participants(), first.summary(), mergedEvidence, mergedBlockedOwners);
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Compatibility " + name + " must not be blank");
        }
    }

    private record CompatibilitySource(String producer, CompatibilityKey key) { }

    private record CompatibilityKey(ModCompatibilityDiagnostic.Level level, String code, String target,
                                    List<String> participants, List<String> blockedOwners) {
        private static CompatibilityKey of(ModCompatibilityDiagnostic diagnostic) {
            return new CompatibilityKey(diagnostic.level(), diagnostic.code(), diagnostic.target(),
                    diagnostic.participants(), diagnostic.blockedOwners());
        }
    }
}
