package com.openggf.mods.code;

import com.openggf.io.PackedModAssetRoot;
import com.openggf.io.ModAssetRoot;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.patch.PatchOwner;
import com.openggf.game.patch.RegisteredPatch;
import com.openggf.game.patch.ModPatchPlanAssembler;
import com.openggf.mods.ModDependency;
import com.openggf.mods.ModDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;

/** Boot-scoped, immutable ownership registry for enabled compiled-mod loaders. */
public final class ModRuntime implements AutoCloseable {
    private final Map<String, ModDependencyClassLoader> loaders;
    private final List<String> owners;
    private final Map<String, PackedModAssetRoot> snapshots;
    private final Map<String, ModDescriptor> descriptors;
    private final Map<String, Rejection> rejectedOwners;
    private Map<String, Throwable> registrationFailures = Map.of();
    private final Set<String> runtimeDisabledOwners = new java.util.LinkedHashSet<>();
    private boolean closed;

    ModRuntime(Map<String, ModDependencyClassLoader> loaders,
               Map<String, PackedModAssetRoot> snapshots,
               Map<String, ModDescriptor> descriptors,
               Map<String, Rejection> rejectedOwners) {
        this.loaders = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(loaders, "loaders")));
        this.owners = List.copyOf(loaders.keySet());
        this.snapshots = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(snapshots, "snapshots")));
        this.descriptors = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(descriptors, "descriptors")));
        this.rejectedOwners = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(rejectedOwners, "rejectedOwners")));
    }

    public static ModRuntime empty() {
        return new ModRuntime(Map.of(), Map.of(), Map.of(), Map.of());
    }

    /** Builds fresh entrypoint instances and private transactions for one launch preparation. */
    public synchronized ModuleResolutionService.PatchPlan newRegistrationPlan() {
        if (closed) throw new IllegalStateException("Mod runtime is closed");
        List<RegisteredPatch> registrations = new ArrayList<>();
        Map<PatchOwner, Set<PatchOwner>> dependencies = new LinkedHashMap<>();
        Set<String> failed = new java.util.LinkedHashSet<>();
        Map<String, Throwable> currentFailures = new LinkedHashMap<>();
        for (String owner : owners) {
            ModDescriptor descriptor = descriptors.get(owner);
            if (descriptor == null) continue;
            if (runtimeDisabledOwners.contains(owner)) {
                failed.add(owner);
                currentFailures.put(owner, new ModRegistrationException(owner,
                        "Owner is disabled for the remainder of this process"));
                continue;
            }
            String failedDependency = descriptor.manifest().dependencies().stream()
                    .map(ModDependency::id).filter(failed::contains).findFirst().orElse(null);
            if (failedDependency != null) {
                failed.add(owner);
                runtimeDisabledOwners.add(owner);
                currentFailures.put(owner, new ModRegistrationException(owner,
                        "Dependency registration failed: " + failedDependency));
                continue;
            }
            try {
                Class<?> type = loadOwned(owner, descriptor.manifest().entrypoint());
                if (!GgfMod.class.isAssignableFrom(type)) {
                    throw new ModRegistrationException(owner, "Entrypoint does not implement GgfMod");
                }
                GgfMod entrypoint = (GgfMod) type.getDeclaredConstructor().newInstance();
                ModRegistrationPlan plan;
                try (ModAssetRoot assets = ModAssetRoot.nonClosingView(Objects.requireNonNull(
                        snapshots.get(owner), "owner assets"))) {
                    ModContext context = new ModContext(owner, descriptor.manifest().baseGame(), assets);
                    entrypoint.register(context);
                    plan = context.freeze();
                }
                PatchOwner.Mod patchOwner = new PatchOwner.Mod(owner);
                List<RegisteredPatch> ownerRegistrations;
                if (plan.hasContent()) {
                    ModBackedGamePatch backing = new ModBackedGamePatch(plan);
                    ownerRegistrations = ModPatchPlanAssembler.backingFirst(patchOwner, backing,
                            plan.explicitPatches());
                } else {
                    ownerRegistrations = new ArrayList<>();
                    long index = 0;
                    for (var patch : plan.explicitPatches()) {
                        String namespaced = patch.id().indexOf(':') >= 0
                                ? patch.id() : owner + ":" + patch.id();
                        ownerRegistrations.add(new RegisteredPatch(patchOwner, namespaced, patch,
                                index++));
                    }
                }
                java.util.LinkedHashSet<PatchOwner> required = new java.util.LinkedHashSet<>();
                for (ModDependency dependency : descriptor.manifest().dependencies()) {
                    if (descriptors.containsKey(dependency.id())) required.add(new PatchOwner.Mod(dependency.id()));
                }
                registrations.addAll(ownerRegistrations);
                dependencies.put(patchOwner, Set.copyOf(required));
            } catch (Throwable failure) {
                rethrowIfFatal(failure);
                failed.add(owner);
                runtimeDisabledOwners.add(owner);
                currentFailures.put(owner, failure);
            }
        }
        registrationFailures = Map.copyOf(currentFailures);
        return new ModuleResolutionService.PatchPlan(registrations, dependencies);
    }

    public synchronized Map<String, Throwable> registrationFailures() {
        return registrationFailures;
    }

    public synchronized void disableOwnersForProcess(Set<String> owners) {
        runtimeDisabledOwners.addAll(Set.copyOf(Objects.requireNonNull(owners, "owners")));
    }

    public synchronized Set<String> runtimeDisabledOwners() {
        return Set.copyOf(runtimeDisabledOwners);
    }

    public Map<String, Set<String>> ownerDependencies() {
        LinkedHashMap<String, Set<String>> result = new LinkedHashMap<>();
        for (String owner : owners) {
            ModDescriptor descriptor = descriptors.get(owner);
            if (descriptor == null) continue;
            java.util.LinkedHashSet<String> required = new java.util.LinkedHashSet<>();
            descriptor.manifest().dependencies().stream().map(ModDependency::id)
                    .filter(descriptors::containsKey).forEach(required::add);
            result.put(owner, Set.copyOf(required));
        }
        return Map.copyOf(result);
    }

    /** Resolves a dynamic class through exactly the loader that owns its snapshot. */
    public synchronized Class<?> loadOwned(String ownerModId, String binaryName)
            throws ClassNotFoundException {
        Objects.requireNonNull(ownerModId, "ownerModId");
        Objects.requireNonNull(binaryName, "binaryName");
        if (closed) throw new ClassNotFoundException("Mod runtime is closed");
        ModDependencyClassLoader loader = loaders.get(ownerModId);
        if (loader == null) throw new ClassNotFoundException("No compiled-mod loader for " + ownerModId);
        return loader.loadClass(binaryName);
    }

    synchronized Optional<String> ownerOf(Class<?> type) {
        if (closed) return Optional.empty();
        ClassLoader definingLoader = type.getClassLoader();
        return loaders.entrySet().stream()
                .filter(entry -> entry.getValue() == definingLoader)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public List<String> owners() {
        return owners;
    }

    public Map<String, Rejection> rejectedOwners() {
        return rejectedOwners;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        IOException failure = null;
        List<ModDependencyClassLoader> reverse = new ArrayList<>(loaders.values());
        for (int index = reverse.size() - 1; index >= 0; index--) {
            try {
                reverse.get(index).close();
            } catch (IOException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        List<PackedModAssetRoot> snapshotValues = new ArrayList<>(snapshots.values());
        for (int index = snapshotValues.size() - 1; index >= 0; index--) {
            try {
                snapshotValues.get(index).close();
            } catch (IOException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }

    private static void rethrowIfFatal(Throwable failure) {
        if (failure instanceof VirtualMachineError vm) throw vm;
        if (failure instanceof ThreadDeath death) throw death;
    }

    public enum RejectionReason {
        HASH_MISMATCH,
        VALIDATION_FAILED,
        DEPENDENCY_UNAVAILABLE,
        INSPECTION_BUDGET_EXCEEDED,
        SNAPSHOT_FAILED
    }

    public record Rejection(RejectionReason reason, String detail) {
        public Rejection {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
            if (detail.isBlank()) throw new IllegalArgumentException("detail must be nonblank");
        }
    }
}
