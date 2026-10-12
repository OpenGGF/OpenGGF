package com.openggf.mods.code;

import com.openggf.io.PackedModAssetRoot;
import com.openggf.io.SnapshotModAssetRoot;
import com.openggf.io.ModAssetRoot;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.patch.PatchOwner;
import com.openggf.game.patch.RegisteredPatch;
import com.openggf.game.patch.ModPatchPlanAssembler;
import com.openggf.mods.ModDependency;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.runtime.OwnerBoundGamePatch;
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
    private final Map<String, SnapshotModAssetRoot> snapshots;
    private final Map<String, ModDescriptor> descriptors;
    private final Set<String> availableOwners;
    private final Map<String, Rejection> rejectedOwners;
    private Map<String, Throwable> registrationFailures = Map.of();
    private Map<String, ModRegistrationPlan> registrationPlans = Map.of();
    private List<OwnedTitleEntry> titleEntries = List.of();
    private ModContributionReport contributionReport = new ModContributionReport(List.of());
    private Map<String, com.openggf.game.GameModule> standaloneModules = Map.of();
    private final Set<String> runtimeDisabledOwners = new java.util.LinkedHashSet<>();
    private boolean closed;
    private ModFaultBoundary faultBoundary;
    private java.nio.file.Path storageRoot = com.openggf.game.save.SavePaths.root();
    private java.util.function.BiConsumer<String,
            com.openggf.game.sonic2.dataselect.S2SaveFinding> saveFindingSink = (owner, finding) -> {};

    ModRuntime(Map<String, ModDependencyClassLoader> loaders,
               Map<String, SnapshotModAssetRoot> snapshots,
               Map<String, ModDescriptor> descriptors,
               Map<String, Rejection> rejectedOwners) {
        this(loaders, snapshots, descriptors,
                new java.util.LinkedHashSet<>(descriptors.keySet()), rejectedOwners);
    }

    ModRuntime(Map<String, ModDependencyClassLoader> loaders,
               Map<String, SnapshotModAssetRoot> snapshots,
               Map<String, ModDescriptor> descriptors,
               Set<String> availableOwners,
               Map<String, Rejection> rejectedOwners) {
        this.loaders = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(loaders, "loaders")));
        this.owners = List.copyOf(descriptors.keySet());
        this.snapshots = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(snapshots, "snapshots")));
        this.descriptors = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(descriptors, "descriptors")));
        this.availableOwners = Set.copyOf(Objects.requireNonNull(availableOwners, "availableOwners"));
        if (!this.availableOwners.containsAll(this.descriptors.keySet())) {
            throw new IllegalArgumentException("Every retained descriptor owner must be available");
        }
        this.rejectedOwners = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(rejectedOwners, "rejectedOwners")));
    }

    public static ModRuntime empty() {
        return new ModRuntime(Map.of(), Map.of(), Map.of(), Set.of(), Map.of());
    }

    /** Builds fresh entrypoint instances and private transactions for one launch preparation. */
    public synchronized ModuleResolutionService.PatchPlan newRegistrationPlan() {
        if (closed) throw new IllegalStateException("Mod runtime is closed");
        registrationPlans = Map.of();
        titleEntries = List.of();
        contributionReport = new ModContributionReport(List.of());
        Map<String, ModRegistrationPlan> currentPlans = new LinkedHashMap<>();
        List<RegisteredPatch> registrations = new ArrayList<>();
        Map<PatchOwner, Set<PatchOwner>> dependencies = new LinkedHashMap<>();
        Set<String> failed = new java.util.LinkedHashSet<>();
        Map<String, Throwable> currentFailures = new LinkedHashMap<>();
        Map<String, com.openggf.game.GameModule> currentStandaloneModules = new LinkedHashMap<>();
        Map<String, OwnedTitleEntry> currentTitleEntries = new LinkedHashMap<>();
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
                ModRegistrationPlan plan;
                try (ModAssetRoot assets = ModAssetRoot.nonClosingView(Objects.requireNonNull(
                        snapshots.get(owner), "owner assets"))) {
                    ModContext context = new ModContext(owner, descriptor.manifest().baseGame(), assets,
                            descriptor.manifest().insertAfter(),
                            descriptor.manifest().type() == com.openggf.mods.ModType.STANDALONE, storageRoot);
                    descriptor.manifest().artOverrides().forEach((key, path) ->
                            context.registerManifestArtOverride(key, new BakedSheetRef(path)));
                    if (descriptor.manifest().entrypoint() != null && loaders.containsKey(owner)) {
                        Class<?> type = loadOwned(owner, descriptor.manifest().entrypoint());
                        if (!GgfMod.class.isAssignableFrom(type)) {
                            throw new ModRegistrationException(owner,
                                    "Entrypoint does not implement GgfMod");
                        }
                        GgfMod entrypoint = (GgfMod) type.getDeclaredConstructor().newInstance();
                        entrypoint.register(context);
                    }
                    plan = context.freeze().prepareObjectArt(assets).prepareZones(assets);
                    ModTitleEntry entry = context.titleEntry();
                    if (entry != null) {
                        if (faultBoundary == null) {
                            throw new ModRegistrationException(owner,
                                    "Title entry requires an installed fault boundary");
                        }
                        currentTitleEntries.put(owner, new OwnedTitleEntry(owner, entry.label(),
                                new OwnedSceneFactory(owner, entry.factory(), faultBoundary)));
                    }
                }
                PatchOwner.Mod patchOwner = new PatchOwner.Mod(owner);
                List<RegisteredPatch> ownerRegistrations;
                com.openggf.game.GameModule standalone = null;
                if (plan.standaloneModule() != null) {
                    if (faultBoundary == null) {
                        throw new ModRegistrationException(owner,
                                "Standalone module requires an installed fault boundary");
                    }
                    standalone = OwnerBoundGamePatch.wrapStandaloneRewinds(owner,
                            OwnerAwareStandaloneModule.wrap(owner, plan.standaloneModule(), faultBoundary,
                                    plan.characters(), plan.preparedObjectArt()), faultBoundary);
                    standalone = OwnedModServices.decorate(standalone, plan, faultBoundary);
                    standalone.getPlayableCharacterRegistry();
                    ownerRegistrations = List.of();
                } else if (plan.hasContent()) {
                    ownerRegistrations = new ArrayList<>();
                    for (ModRegistrationPlan stockPlan : plan.stockScenePlans()) {
                        ModBackedGamePatch backing = "any".equals(plan.baseGameId())
                                ? new ModBackedGamePatch(stockPlan, faultBoundary, saveFindingSink,
                                        "content-" + stockPlan.baseGameId())
                                : new ModBackedGamePatch(stockPlan, faultBoundary, saveFindingSink);
                        ownerRegistrations.addAll(ModPatchPlanAssembler.backingFirst(patchOwner, backing,
                                stockPlan.explicitPatches().stream()
                                        .map(patch -> OwnerBoundGamePatch.wrap(owner, patch, faultBoundary)).toList(),
                                ownerRegistrations.size()));
                    }
                } else {
                    ownerRegistrations = new ArrayList<>();
                    long index = 0;
                    for (var patch : plan.explicitPatches()) {
                        String namespaced = patch.id().indexOf(':') >= 0
                                ? patch.id() : owner + ":" + patch.id();
                        ownerRegistrations.add(new RegisteredPatch(patchOwner, namespaced,
                                OwnerBoundGamePatch.wrap(owner, patch, faultBoundary),
                                index++));
                    }
                }
                java.util.LinkedHashSet<PatchOwner> required = new java.util.LinkedHashSet<>();
                for (ModDependency dependency : descriptor.manifest().dependencies()) {
                    if (!availableOwners.contains(dependency.id())) {
                        throw new ModRegistrationException(owner,
                                "Declared dependency is unavailable in runtime: " + dependency.id());
                    }
                    required.add(new PatchOwner.Mod(dependency.id()));
                }
                currentPlans.put(owner, plan);
                registrations.addAll(ownerRegistrations);
                dependencies.put(patchOwner, Set.copyOf(required));
                if (standalone != null) currentStandaloneModules.put(owner, standalone);
            } catch (Throwable failure) {
                rethrowIfFatal(failure);
                failed.add(owner);
                runtimeDisabledOwners.add(owner);
                currentFailures.put(owner, failure);
            }
        }
        Map<String, String> rejectedClaims = new java.util.TreeMap<>();
        currentPlans.forEach((owner, plan) -> {
            Set<String> published = ModContributionReport.contributions(plan, descriptors.get(owner));
            for (String claim : new java.util.TreeSet<>(descriptors.get(owner).manifest().composition().exclusiveContributions()))
                if (!published.contains(claim) || claim.startsWith("art:")
                        && !com.openggf.mods.StockArtOverrideCatalog.contains(plan.baseGameId(), claim.substring(4)))
                    rejectedClaims.put(owner, "Unused exclusive contribution claim or unknown target: " + claim);
        });
        ModContributionReport.sources(currentPlans, descriptors).forEach((target, claimants) -> {
            String claim = target.substring(target.indexOf('/')+1);
            if (claimants.size() > 1 && claimants.stream().anyMatch(owner ->
                    descriptors.get(owner).manifest().composition().exclusiveContributions().contains(claim))) {
                List<String> sorted = claimants.stream().sorted().toList();
                for (String owner : sorted) rejectedClaims.put(owner,
                        "Exclusive contribution conflict at " + target + ": " + String.join(", ", sorted));
            }
        });
        boolean changed;
        do {
            changed = false;
            for (String owner : currentPlans.keySet()) {
                if (rejectedClaims.containsKey(owner)) continue;
                String failedDependency = descriptors.get(owner).manifest().dependencies().stream().map(ModDependency::id)
                        .filter(rejectedClaims::containsKey).sorted().findFirst().orElse(null);
                if (failedDependency != null) { rejectedClaims.put(owner, "Dependency contribution rejected: " + failedDependency); changed = true; }
            }
        } while (changed);
        rejectedClaims.forEach((owner, reason) -> {
            currentFailures.put(owner, new ModRegistrationException(owner, "MOD_COMPOSITION_REJECTED", reason, null, null));
            runtimeDisabledOwners.add(owner); currentPlans.remove(owner); currentStandaloneModules.remove(owner);
            currentTitleEntries.remove(owner);
            registrations.removeIf(registration -> registration.owner().equals(new PatchOwner.Mod(owner)));
            dependencies.remove(new PatchOwner.Mod(owner));
        });
        Map<PatchOwner,Set<PatchOwner>> ordering = new LinkedHashMap<>();
        currentPlans.forEach((owner, plan) -> {
            PatchOwner key = new PatchOwner.Mod(owner);
            var metadata = descriptors.get(owner).manifest().composition();
            for (String predecessor : metadata.after()) if (currentPlans.containsKey(predecessor))
                ordering.computeIfAbsent(key, ignored -> new java.util.LinkedHashSet<>()).add(new PatchOwner.Mod(predecessor));
            for (String successor : metadata.before()) if (currentPlans.containsKey(successor))
                ordering.computeIfAbsent(new PatchOwner.Mod(successor), ignored -> new java.util.LinkedHashSet<>()).add(key);
        });
        contributionReport = ModContributionReport.build(currentPlans, descriptors);
        registrationPlans = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(currentPlans));
        registrationFailures = Map.copyOf(currentFailures);
        standaloneModules = Map.copyOf(currentStandaloneModules);
        currentTitleEntries.keySet().retainAll(currentPlans.keySet());
        titleEntries = List.copyOf(currentTitleEntries.values());
        return new ModuleResolutionService.PatchPlan(registrations, dependencies, ordering);
    }

    /** Runs one fresh registration pass and returns the atomically published standalone module. */
    public synchronized Optional<com.openggf.game.GameModule> prepareStandaloneModule(String ownerModId) {
        String owner = com.openggf.game.ModKeySyntax.requireManifestId(ownerModId);
        newRegistrationPlan();
        return Optional.ofNullable(standaloneModules.get(owner));
    }

    /** Retained immutable asset snapshot owned by this runtime for a standalone session. */
    public synchronized SnapshotModAssetRoot standaloneAssetSnapshot(String ownerModId) {
        String owner = com.openggf.game.ModKeySyntax.requireManifestId(ownerModId);
        ModDescriptor descriptor = descriptors.get(owner);
        if (descriptor == null || descriptor.manifest().type() != com.openggf.mods.ModType.STANDALONE
                || runtimeDisabledOwners.contains(owner)) {
            throw new IllegalArgumentException("Standalone owner is unavailable: " + owner);
        }
        return Objects.requireNonNull(snapshots.get(owner), "standalone asset snapshot");
    }

    public synchronized ModDescriptor standaloneDescriptor(String ownerModId) {
        String owner = com.openggf.game.ModKeySyntax.requireManifestId(ownerModId);
        ModDescriptor descriptor = descriptors.get(owner);
        if (descriptor == null || descriptor.manifest().type() != com.openggf.mods.ModType.STANDALONE) {
            throw new IllegalArgumentException("Standalone owner is unavailable: " + owner);
        }
        return descriptor;
    }

    public synchronized Map<String, com.openggf.game.GameModule> standaloneModules() {
        return standaloneModules;
    }

    /** Successful private transactions published by the latest registration pass; never re-runs creator code. */
    public synchronized ModContributionReport contributionReport() { return contributionReport; }

    /** Master-title entries from the latest registration pass, in owner order. */
    public synchronized List<OwnedTitleEntry> titleEntries() {
        return titleEntries;
    }

    public synchronized Map<String, ModRegistrationPlan> registrationPlans() {
        return registrationPlans;
    }

    public synchronized Map<String, Throwable> registrationFailures() {
        return registrationFailures;
    }

    /** Sets the engine/test-owned storage root for subsequently created owner transactions. */
    public synchronized void installStorageRoot(java.nio.file.Path root) {
        if (closed) throw new IllegalStateException("Mod runtime is closed");
        storageRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    /** Installs the engine-owned runtime callback boundary before launch plans are built. */
    public synchronized void installFaultBoundary(ModFaultBoundary boundary) {
        if (closed) throw new IllegalStateException("Mod runtime is closed");
        this.faultBoundary = Objects.requireNonNull(boundary, "boundary");
    }

    public synchronized void installSaveFindingSink(java.util.function.BiConsumer<String,
            com.openggf.game.sonic2.dataselect.S2SaveFinding> sink) {
        if (closed) throw new IllegalStateException("Mod runtime is closed");
        saveFindingSink = Objects.requireNonNull(sink, "sink");
    }

    public synchronized void disableOwnersForProcess(Set<String> owners) {
        runtimeDisabledOwners.addAll(Set.copyOf(Objects.requireNonNull(owners, "owners")));
        LinkedHashMap<String, ModRegistrationPlan> retained = new LinkedHashMap<>(registrationPlans);
        runtimeDisabledOwners.forEach(retained::remove);
        registrationPlans = java.util.Collections.unmodifiableMap(retained);
        titleEntries = titleEntries.stream()
                .filter(entry -> !runtimeDisabledOwners.contains(entry.ownerModId())).toList();
        contributionReport = ModContributionReport.build(registrationPlans, descriptors);
    }

    public synchronized Set<String> runtimeDisabledOwners() {
        return Set.copyOf(runtimeDisabledOwners);
    }

    /** Read-only dynamic view used by the engine composition root during team bootstrap. */
    public com.openggf.game.CharacterAvailability characterAvailability() {
        return com.openggf.game.CharacterAvailability.dynamic(
                this::isKnownOwner, this::isOwnerEnabled);
    }

    private synchronized boolean isKnownOwner(String owner) {
        return !closed && availableOwners.contains(owner);
    }

    private synchronized boolean isOwnerEnabled(String owner) {
        return !closed && availableOwners.contains(owner) && !runtimeDisabledOwners.contains(owner);
    }

    public Map<String, Set<String>> ownerDependencies() {
        LinkedHashMap<String, Set<String>> result = new LinkedHashMap<>();
        for (String owner : owners) {
            ModDescriptor descriptor = descriptors.get(owner);
            if (descriptor == null) continue;
            java.util.LinkedHashSet<String> required = new java.util.LinkedHashSet<>();
            for (ModDependency dependency : descriptor.manifest().dependencies()) {
                if (!availableOwners.contains(dependency.id())) {
                    throw new IllegalStateException("Runtime owner " + owner
                            + " retained without dependency " + dependency.id());
                }
                required.add(dependency.id());
            }
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
        registrationPlans = Map.of();
        titleEntries = List.of();
        contributionReport = new ModContributionReport(List.of());
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
        List<SnapshotModAssetRoot> snapshotValues = new ArrayList<>(snapshots.values());
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
        SNAPSHOT_FAILED,
        NATIVE_UNSUPPORTED
    }

    public record Rejection(RejectionReason reason, String detail) {
        public Rejection {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
            if (detail.isBlank()) throw new IllegalArgumentException("detail must be nonblank");
        }
    }
}
