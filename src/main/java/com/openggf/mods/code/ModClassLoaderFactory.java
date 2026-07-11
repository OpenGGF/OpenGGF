package com.openggf.mods.code;

import com.openggf.mods.EffectiveModCatalog;
import com.openggf.mods.ModDependency;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.validation.ModValidationReport;
import com.openggf.mods.validation.ModValidator;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.io.PackedModAssetRoot;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/** Builds one frozen, dependency-ordered compiled-mod runtime per engine boot. */
public final class ModClassLoaderFactory {
    private final ClassLoader engineParent;
    private final BiFunction<ModDescriptor, java.nio.file.Path, ModValidationReport> validator;
    private final ModInputLimits limits;

    public ModClassLoaderFactory(ClassLoader engineParent) {
        this(engineParent, (descriptor, snapshot) -> new ModValidator().validate(
                snapshot, descriptor.manifest().entrypoint()), ModInputLimits.production());
    }

    ModClassLoaderFactory(ClassLoader engineParent,
                          BiFunction<ModDescriptor, java.nio.file.Path, ModValidationReport> validator) {
        this(engineParent, validator, ModInputLimits.production());
    }

    ModClassLoaderFactory(ClassLoader engineParent,
                          BiFunction<ModDescriptor, java.nio.file.Path, ModValidationReport> validator,
                          ModInputLimits limits) {
        this.engineParent = Objects.requireNonNull(engineParent, "engineParent");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /**
     * Production remains deny-by-default until the trust workstream supplies its
     * engine-owned approved owner set.
     */
    public ModRuntime create(EffectiveModCatalog catalog) throws IOException {
        return create(catalog, Set.of());
    }

    public ModRuntime create(EffectiveModCatalog catalog, Set<String> trustedCodeOwners)
            throws IOException {
        Objects.requireNonNull(catalog, "catalog");
        Set<String> trusted = Set.copyOf(Objects.requireNonNull(trustedCodeOwners,
                "trustedCodeOwners"));
        Map<String, ModDependencyClassLoader> loaders = new LinkedHashMap<>();
        Map<String, PackedModAssetRoot> snapshots = new LinkedHashMap<>();
        Map<String, ModDescriptor> loadedDescriptors = new LinkedHashMap<>();
        Map<String, ModRuntime.Rejection> rejections = new LinkedHashMap<>();
        long inspectedBytes = 0;
        try {
            for (ModDescriptor descriptor : catalog.orderedEnabled()) {
                String owner = descriptor.manifest().id();
                if (!descriptor.containsCode() || descriptor.manifest().entrypoint() == null
                        || !trusted.contains(owner)) {
                    continue;
                }
                DependencyResolution dependencyResolution = directDependencies(
                        descriptor, catalog, loaders, trusted);
                if (dependencyResolution.rejection() != null) {
                    rejections.put(owner, dependencyResolution.rejection());
                    continue;
                }
                java.nio.file.Path source = descriptor.jarPath().toAbsolutePath().normalize();
                final long currentSourceBytes;
                try {
                    currentSourceBytes = java.nio.file.Files.size(source);
                } catch (IOException expectedOwnerFailure) {
                    rejections.put(owner, rejection(ModRuntime.RejectionReason.SNAPSHOT_FAILED,
                            "immutable snapshot unavailable"));
                    continue;
                }
                if (currentSourceBytes > limits.maxRepositoryValidationBytes() - inspectedBytes) {
                    rejections.put(owner, rejection(
                            ModRuntime.RejectionReason.INSPECTION_BUDGET_EXCEEDED,
                            "boot loader inspection budget exceeded"));
                    continue;
                }
                inspectedBytes += currentSourceBytes;
                PackedModAssetRoot snapshot;
                try {
                    snapshot = ModAssetRoot.jar(source.getParent(), source,
                            limits, currentSourceBytes);
                } catch (IOException expectedOwnerFailure) {
                    rejections.put(owner, rejection(ModRuntime.RejectionReason.SNAPSHOT_FAILED,
                            "immutable snapshot unavailable"));
                    continue;
                }
                snapshots.put(owner, snapshot);
                final String snapshotHash;
                final java.nio.file.Path snapshotPath;
                try {
                    snapshotHash = snapshot.immutableSha256();
                    snapshotPath = snapshot.immutableContentPath();
                } catch (IOException expectedOwnerFailure) {
                    rejections.put(owner, rejection(ModRuntime.RejectionReason.SNAPSHOT_FAILED,
                            "immutable snapshot unavailable"));
                    closeRejected(snapshot);
                    snapshots.remove(owner);
                    continue;
                }
                if (!descriptor.sha256().equals(snapshotHash)) {
                    rejections.put(owner, rejection(ModRuntime.RejectionReason.HASH_MISMATCH,
                            "catalog SHA-256 differs from immutable snapshot"));
                    closeRejected(snapshot);
                    snapshots.remove(owner);
                    continue;
                }
                ModValidationReport validation = validator.apply(descriptor, snapshotPath);
                if (!validation.eligible()) {
                    rejections.put(owner, rejection(ModRuntime.RejectionReason.VALIDATION_FAILED,
                            validationSummary(validation)));
                    closeRejected(snapshot);
                    snapshots.remove(owner);
                    continue;
                }
                URL jarUrl = snapshotPath.toUri().toURL();
                ModDependencyClassLoader loader = new ModDependencyClassLoader(owner,
                        new URL[] {jarUrl}, engineParent, dependencyResolution.loaders());
                if (loaders.putIfAbsent(owner, loader) != null) {
                    loader.close();
                    throw new IOException("Duplicate compiled-mod owner: " + owner);
                }
                loadedDescriptors.put(owner, descriptor);
            }
            return new ModRuntime(loaders, snapshots, loadedDescriptors, rejections);
        } catch (IOException | RuntimeException failure) {
            closeAll(loaders, snapshots, rejections);
            throw failure;
        }
    }

    private static DependencyResolution directDependencies(ModDescriptor descriptor,
            EffectiveModCatalog catalog, Map<String, ModDependencyClassLoader> loaders,
            Set<String> trusted) throws IOException {
        Map<String, ModDescriptor> descriptors = new LinkedHashMap<>();
        for (ModDescriptor candidate : catalog.orderedEnabled()) {
            descriptors.put(candidate.manifest().id(), candidate);
        }
        java.util.ArrayList<ModDependencyClassLoader> result = new java.util.ArrayList<>();
        for (ModDependency dependency : descriptor.manifest().dependencies()) {
            ModDescriptor dependencyDescriptor = descriptors.get(dependency.id());
            if (dependencyDescriptor == null) {
                return dependencyRejected(dependency.id(), "missing compiled dependency");
            }
            if (!dependencyDescriptor.containsCode()) continue;
            if (!trusted.contains(dependency.id())) {
                return dependencyRejected(dependency.id(), "compiled dependency is not trusted");
            }
            ModDependencyClassLoader loader = loaders.get(dependency.id());
            if (loader == null) {
                return dependencyRejected(dependency.id(), "compiled dependency was rejected");
            }
            result.add(loader);
        }
        return new DependencyResolution(List.copyOf(result), null);
    }

    private static void closeAll(Map<String, ModDependencyClassLoader> loaders,
                                 Map<String, PackedModAssetRoot> snapshots,
                                 Map<String, ModRuntime.Rejection> rejections) {
        try {
            new ModRuntime(loaders, snapshots, Map.of(), rejections).close();
        } catch (IOException ignored) {
            // Preserve the construction failure; no runtime escaped.
        }
    }

    private static DependencyResolution dependencyRejected(String dependencyId, String cause) {
        return new DependencyResolution(List.of(), rejection(
                ModRuntime.RejectionReason.DEPENDENCY_UNAVAILABLE,
                dependencyId + ": " + cause));
    }

    private static ModRuntime.Rejection rejection(ModRuntime.RejectionReason reason, String detail) {
        return new ModRuntime.Rejection(reason, detail);
    }

    private static String validationSummary(ModValidationReport report) {
        String summary = report.findings().stream()
                .filter(finding -> finding.severity()
                        == com.openggf.mods.validation.ModValidationFinding.Severity.ERROR)
                .map(com.openggf.mods.validation.ModValidationFinding::code)
                .distinct().sorted().collect(java.util.stream.Collectors.joining(","));
        return summary.isEmpty() ? "structural validation failed" : summary;
    }

    private static void closeRejected(PackedModAssetRoot snapshot) {
        try {
            snapshot.close();
        } catch (IOException ignored) {
            // The owner remains rejected and the snapshot cannot reach a loader.
        }
    }

    private record DependencyResolution(List<ModDependencyClassLoader> loaders,
                                        ModRuntime.Rejection rejection) { }
}
