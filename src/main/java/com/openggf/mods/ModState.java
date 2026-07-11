package com.openggf.mods;

import com.openggf.io.ModInputLimits;
import com.openggf.game.ModKeySyntax;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;

/** Persisted pending mod enablement and display order. */
public record ModState(int formatVersion, List<Entry> entries) {
    public static final int CURRENT_FORMAT_VERSION = 1;
    public static final ModState EMPTY = new ModState(CURRENT_FORMAT_VERSION, List.of());

    public ModState {
        if (formatVersion != CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported mod-state formatVersion: " + formatVersion);
        }
        List<Entry> normalized = new ArrayList<>(Objects.requireNonNull(entries, "entries"));
        if (normalized.size() > ModInputLimits.DEFAULT_MAX_COLLECTION_ENTRIES) {
            throw new IllegalArgumentException("Mod state exceeds entry limit");
        }
        normalized.sort(Comparator.comparingInt(Entry::order));
        Set<String> ids = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (Entry entry : normalized) {
            Objects.requireNonNull(entry, "entry");
            if (!ids.add(entry.id())) {
                throw new IllegalArgumentException("Duplicate mod-state id: " + entry.id());
            }
            if (!orders.add(entry.order())) {
                throw new IllegalArgumentException("Duplicate mod-state order: " + entry.order());
            }
        }
        entries = List.copyOf(normalized);
    }

    /** Reconciles persisted state with a stable scanner result without discarding vanished ids. */
    public ModState normalize(List<? extends ModCatalogEntry> scanned) {
        Objects.requireNonNull(scanned, "scanned");
        List<Entry> reconciled = new ArrayList<>(revokeMismatchedTrust(scanned).entries());
        Set<String> retainedIds = new LinkedHashSet<>();
        for (Entry entry : reconciled) {
            retainedIds.add(entry.id());
        }
        for (ModCatalogEntry catalogEntry : scanned) {
            if (catalogEntry instanceof ModDescriptor descriptor
                    && retainedIds.add(descriptor.manifest().id())) {
                reconciled.add(new Entry(descriptor.manifest().id(), false, reconciled.size()));
            }
        }
        List<Entry> contiguous = new ArrayList<>(reconciled.size());
        for (int index = 0; index < reconciled.size(); index++) {
            Entry entry = reconciled.get(index);
            contiguous.add(new Entry(entry.id(), entry.enabled(), index,
                    entry.trusted(), entry.trustedJarSha256()));
        }
        return new ModState(CURRENT_FORMAT_VERSION, contiguous);
    }

    /** Clears grants whose scanned code jar no longer has the approved bytes. */
    public ModState revokeMismatchedTrust(List<? extends ModCatalogEntry> scanned) {
        Objects.requireNonNull(scanned, "scanned");
        Map<String, ModDescriptor> descriptors = uniqueDescriptors(scanned);
        List<Entry> reconciled = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            ModDescriptor descriptor = descriptors.get(entry.id());
            reconciled.add(descriptor != null && descriptor.containsCode()
                    && !entry.trustsSha256(descriptor.sha256()) ? entry.withoutTrust() : entry);
        }
        return new ModState(CURRENT_FORMAT_VERSION, reconciled);
    }

    public Set<String> trustedCodeOwners(List<? extends ModCatalogEntry> scanned) {
        Map<String, ModDescriptor> descriptors = uniqueDescriptors(scanned);
        Set<String> trusted = new LinkedHashSet<>();
        for (Entry entry : entries) {
            ModDescriptor descriptor = descriptors.get(entry.id());
            if (descriptor != null && descriptor.containsCode()
                    && entry.trustsSha256(descriptor.sha256())) {
                trusted.add(entry.id());
            }
        }
        return Set.copyOf(trusted);
    }

    private static Map<String, ModDescriptor> uniqueDescriptors(
            List<? extends ModCatalogEntry> scanned) {
        Map<String, ModDescriptor> descriptors = new LinkedHashMap<>();
        Set<String> duplicates = new HashSet<>();
        for (ModCatalogEntry catalogEntry : scanned) {
            if (catalogEntry instanceof ModDescriptor descriptor) {
                String id = descriptor.manifest().id();
                if (descriptors.putIfAbsent(id, descriptor) != null) {
                    duplicates.add(id);
                }
            }
        }
        duplicates.forEach(descriptors::remove);
        return descriptors;
    }

    public record Entry(String id, boolean enabled, int order, boolean trusted,
                        String trustedJarSha256) {
        public Entry(String id, boolean enabled, int order) {
            this(id, enabled, order, false, null);
        }

        public Entry {
            id = ModKeySyntax.requireManifestId(id);
            if (order < 0) {
                throw new IllegalArgumentException("Mod-state order must be nonnegative");
            }
            if (!trusted) {
                trustedJarSha256 = null;
            } else if (trustedJarSha256 == null
                    || !trustedJarSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Trusted mod-state entries require a lowercase SHA-256");
            }
        }

        public boolean trustsSha256(String sha256) {
            return trusted && Objects.equals(trustedJarSha256, sha256);
        }

        public Entry withoutTrust() {
            return trusted || trustedJarSha256 != null
                    ? new Entry(id, enabled, order, false, null) : this;
        }
    }
}
