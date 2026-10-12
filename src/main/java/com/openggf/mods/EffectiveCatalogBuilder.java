package com.openggf.mods;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Freezes validated discovery results and startup state into one process-lifetime catalog. */
public final class EffectiveCatalogBuilder {
    private final Predicate<VersionRange> apiSupport;
    private final Supplier<String> supportedApiDiagnostic;

    public EffectiveCatalogBuilder() {
        this(ModApiVersion::supports, ModApiVersion::supportedContractsDiagnostic);
    }

    EffectiveCatalogBuilder(Predicate<VersionRange> apiSupport,
                            Supplier<String> supportedApiDiagnostic) {
        this.apiSupport = Objects.requireNonNull(apiSupport, "apiSupport");
        this.supportedApiDiagnostic = Objects.requireNonNull(
                supportedApiDiagnostic, "supportedApiDiagnostic");
    }

    public ModCatalog build(List<? extends ModCatalogEntry> scanned, ModState startupState) {
        List<ModCatalogEntry> retained = new ArrayList<>(Objects.requireNonNull(scanned, "scanned"));
        Objects.requireNonNull(startupState, "startupState");
        Map<String, ModState.Entry> stateById = new LinkedHashMap<>();
        startupState.entries().forEach(entry -> stateById.put(entry.id(), entry));

        Map<String, List<ModDescriptor>> grouped = new LinkedHashMap<>();
        Map<String, Integer> discoveryOrder = new HashMap<>();
        for (int index = 0; index < retained.size(); index++) {
            if (retained.get(index) instanceof ModDescriptor descriptor) {
                String id = descriptor.manifest().id();
                grouped.computeIfAbsent(id, ignored -> new ArrayList<>()).add(descriptor);
                discoveryOrder.putIfAbsent(id, index);
            }
        }
        Map<String, ModDescriptor> descriptors = new LinkedHashMap<>();
        grouped.forEach((id, values) -> {
            if (values.size() == 1) descriptors.put(id, values.get(0));
        });
        ModDependencyGraph graph = new ModDependencyGraph(descriptors);
        Map<String, List<String>> cycles = graph.cycleParticipants();
        Map<String, ModEligibility> resolved = new LinkedHashMap<>();

        for (Map.Entry<String, List<ModDescriptor>> duplicate : grouped.entrySet()) {
            if (duplicate.getValue().size() > 1) {
                resolved.put(duplicate.getKey(), blocked(duplicate.getKey(), "DUPLICATE_MOD_ID",
                        "Duplicate mod id is not eligible", List.of(duplicate.getKey())));
            }
        }
        Map<String, String> cycleMessages = new HashMap<>();
        cycles.forEach((id, participants) -> resolved.put(id, blocked(id, "DEPENDENCY_CYCLE",
                cycleMessages.computeIfAbsent(participants.get(0),
                        ignored -> "Dependency cycle: " + String.join(" -> ", participants)), participants)));
        Set<String> acyclic = new LinkedHashSet<>(descriptors.keySet());
        acyclic.removeAll(cycles.keySet());
        Map<String, Integer> priority = new HashMap<>();
        stateById.forEach((id, state) -> priority.put(id, state.order()));
        // Optional neighbors must satisfy the hard prerequisite graph first.
        // Allocation is deferred: soft order also determines scarce window priority.
        boolean hasComposition = descriptors.values().stream().anyMatch(descriptor ->
                !descriptor.manifest().composition().equals(ModCompositionMetadata.EMPTY));
        Set<String> compositionOwners = new LinkedHashSet<>();
        if (hasComposition) {
            Map<String, ModEligibility> prerequisiteEligibility = new LinkedHashMap<>(resolved);
            for (String id : graph.stableOrder(acyclic, priority, discoveryOrder))
                prerequisiteEligibility.put(id, evaluate(descriptors.get(id), descriptors, stateById,
                        prerequisiteEligibility, new int[]{0}, false));
            prerequisiteEligibility.forEach((id, eligibility) -> {
                if (eligibility.status() == ModEligibility.Status.EFFECTIVE) compositionOwners.add(id);
            });
        }
        ModDependencyGraph applicationGraph = hasComposition
                ? new ModDependencyGraph(descriptors, compositionOwners) : graph;
        (hasComposition ? applicationGraph.cycleParticipants() : cycles).forEach((id, participants) -> {
            if (!cycles.containsKey(id)) resolved.put(id, blocked(id, "ORDERING_CYCLE",
                    "Composition ordering cycle: " + String.join(" -> ", participants), participants));
        });
        Map<String, java.util.SortedSet<String>> conflictNeighbors = new java.util.TreeMap<>();
        for (String owner : compositionOwners) {
            for (String conflict : descriptors.get(owner).manifest().composition().conflictsWith()) {
                if (!compositionOwners.contains(conflict)) continue;
                conflictNeighbors.computeIfAbsent(owner, ignored -> new java.util.TreeSet<>()).add(conflict);
                conflictNeighbors.computeIfAbsent(conflict, ignored -> new java.util.TreeSet<>()).add(owner);
            }
        }
        conflictNeighbors.forEach((owner, neighbors) -> {
            var participants = new java.util.TreeSet<>(neighbors); participants.add(owner);
            List<String> sorted = List.copyOf(participants);
            resolved.putIfAbsent(owner, blocked(owner, "MOD_CONFLICT",
                    "Conflicting enabled owners: " + String.join(", ", sorted), sorted));
        });
        // Stock data replacements are prepared before compiled registration. Reject
        // their exclusive collisions here so no blocked owner's audio can become live.
        Map<String, List<String>> dataSources = new java.util.TreeMap<>();
        for (String owner : compositionOwners.stream().sorted().toList()) {
            if (resolved.containsKey(owner)) continue;
            ModManifest manifest = descriptors.get(owner).manifest();
            for (String claim : new java.util.TreeSet<>(manifest.composition().exclusiveContributions())) {
                boolean unused = claim.startsWith("audio:")
                        && !manifest.audioOverrides().containsKey(Integer.parseInt(claim.substring(6)));
                boolean unknownArt = claim.startsWith("art:") && (!manifest.artOverrides().containsKey(claim.substring(4))
                        || !StockArtOverrideCatalog.contains(manifest.baseGame(), claim.substring(4)));
                if (unused || unknownArt) resolved.put(owner, blocked(owner, "MOD_COMPOSITION_REJECTED",
                        "Unused or unknown exclusive contribution claim: " + claim, List.of(owner)));
            }
            if (resolved.containsKey(owner)) continue;
            manifest.audioOverrides().keySet().forEach(key -> dataSources.computeIfAbsent(
                    manifest.baseGame() + "/audio:" + key, ignored -> new ArrayList<>()).add(owner));
            manifest.artOverrides().keySet().forEach(key -> dataSources.computeIfAbsent(
                    manifest.baseGame() + "/art:" + key, ignored -> new ArrayList<>()).add(owner));
        }
        dataSources.forEach((target, sources) -> {
            String claim = target.substring(target.indexOf('/') + 1);
            if (sources.size() > 1 && sources.stream().anyMatch(owner -> descriptors.get(owner).manifest()
                    .composition().exclusiveContributions().contains(claim))) {
                List<String> participants = sources.stream().sorted().toList();
                for (String owner : participants) resolved.put(owner, blocked(owner, "MOD_COMPOSITION_REJECTED",
                        "Exclusive contribution conflict at " + target + ": " + String.join(", ", participants), participants));
            }
        });
        acyclic.removeAll(resolved.keySet());
        int[] allocatedPatternWindows = {0};
        for (String id : applicationGraph.stableOrder(acyclic, priority, discoveryOrder)) {
            resolved.put(id, evaluate(descriptors.get(id), descriptors, stateById, resolved,
                    allocatedPatternWindows));
        }

        List<ModCatalogEntry> staticCatalog = List.copyOf(retained);
        Map<String, ModDescriptor> staticById = new LinkedHashMap<>();
        for (ModCatalogEntry entry : staticCatalog) {
            if (entry instanceof ModDescriptor descriptor) {
                staticById.putIfAbsent(descriptor.manifest().id(), descriptor);
            }
        }
        Set<String> effectiveIds = new LinkedHashSet<>();
        resolved.forEach((id, eligibility) -> {
            if (eligibility.status() == ModEligibility.Status.EFFECTIVE) effectiveIds.add(id);
        });
        List<ModDescriptor> ordered = applicationGraph.stableOrder(effectiveIds, priority, discoveryOrder).stream()
                .map(staticById::get).toList();
        return new ModCatalog(staticCatalog, new EffectiveModCatalog(ordered), resolved);
    }

    private ModEligibility evaluate(ModDescriptor descriptor, Map<String, ModDescriptor> descriptors,
                                    Map<String, ModState.Entry> stateById,
                                    Map<String, ModEligibility> resolved,
                                    int[] allocatedPatternWindows) {
        return evaluate(descriptor, descriptors, stateById, resolved, allocatedPatternWindows, true);
    }

    private ModEligibility evaluate(ModDescriptor descriptor, Map<String, ModDescriptor> descriptors,
                                    Map<String, ModState.Entry> stateById,
                                    Map<String, ModEligibility> resolved,
                                    int[] allocatedPatternWindows, boolean allocateWindows) {
        String id = descriptor.manifest().id();
        ModState.Entry state = stateById.get(id);
        ModEligibility ownBlock = ownBlock(descriptor, state);
        if (ownBlock != null) return ownBlock;
        for (ModDependency dependency : descriptor.manifest().dependencies()) {
            ModDescriptor target = descriptors.get(dependency.id());
            if (target == null) {
                ModEligibility unavailable = resolved.get(dependency.id());
                if (unavailable != null && unavailable.status() == ModEligibility.Status.BLOCKED) {
                    return blocked(id, "DEPENDENCY_BLOCKED",
                            "Required dependency is blocked: " + dependency.id(), List.of(dependency.id()));
                }
                return blocked(id, "DEPENDENCY_MISSING",
                        "Required dependency is missing: " + dependency.id(), List.of(dependency.id()));
            }
            if (!dependency.versionRange().contains(target.manifest().version())) {
                return blocked(id, "DEPENDENCY_VERSION_INCOMPATIBLE",
                        "Dependency " + dependency.id() + " version " + target.manifest().version()
                                + " does not satisfy " + dependency.versionRange(), List.of(dependency.id()));
            }
            if (!Objects.equals(descriptor.manifest().baseGame(), target.manifest().baseGame())) {
                return blocked(id, "DEPENDENCY_BASE_GAME_MISMATCH",
                        "Patch dependency targets a different base game: " + dependency.id(),
                        List.of(dependency.id()));
            }
            ModEligibility dependencyEligibility = resolved.get(dependency.id());
            if (dependencyEligibility == null) {
                throw new IllegalStateException("Dependency was not evaluated before dependent: " + dependency.id());
            }
            if (dependencyEligibility.status() == ModEligibility.Status.DISABLED) {
                return blocked(id, "DEPENDENCY_DISABLED",
                        "Required dependency is disabled: " + dependency.id(), List.of(dependency.id()));
            }
            if (dependencyEligibility.status() == ModEligibility.Status.BLOCKED) {
                String inherited = dependencyEligibility.reasons().stream()
                        .map(ModEligibility.Reason::code).distinct().sorted().reduce((a, b) -> a + ", " + b)
                        .orElse("unknown reason");
                return blocked(id, "DEPENDENCY_BLOCKED",
                        "Required dependency " + dependency.id() + " is blocked: " + inherited,
                        List.of(dependency.id()));
            }
        }
        if (state == null || !state.enabled()) {
            return new ModEligibility(id, ModEligibility.Status.DISABLED, List.of(
                    reason("DISABLED", "Mod is disabled in startup state", List.of())));
        }
        if (!allocateWindows) return new ModEligibility(id, ModEligibility.Status.EFFECTIVE, List.of());
        int requestedWindows = descriptor.manifest().patternWindows().orElse(1);
        if (allocatedPatternWindows[0] + requestedWindows > 128) {
            return blocked(id, "PATTERN_WINDOW_BUDGET_EXCEEDED",
                    "Pattern-window allocation for " + id
                            + " exceeds the process limit of 128", List.of(id));
        }
        allocatedPatternWindows[0] += requestedWindows;
        return new ModEligibility(id, ModEligibility.Status.EFFECTIVE, List.of());
    }

    private ModEligibility ownBlock(ModDescriptor descriptor, ModState.Entry state) {
        String id = descriptor.manifest().id();
        if (descriptor.hasErrors()) return blocked(id, "DESCRIPTOR_INVALID",
                "Discovery or static validation reported an error", List.of(id));
        ModManifest manifest = descriptor.manifest();
        if (!apiSupport.test(manifest.engineApiRange())) {
            return blocked(id, "ENGINE_API_INCOMPATIBLE",
                    "Requires engine API " + manifest.engineApiRange() + "; supported contracts "
                            + supportedApiDiagnostic.get(),
                    List.of());
        }
        if (descriptor.containsCode()
                && (state == null || !state.trustsSha256(descriptor.sha256()))) {
            return blocked(id, "CODE_TRUST_REQUIRED",
                    "contains code — trust required (press accept twice)", List.of());
        }
        if (manifest.insertAfter() != null
                && !StockProgressionAnchors.contains(manifest.baseGame(), manifest.insertAfter())) {
            return blocked(id, "INSERT_AFTER_STOCK_ANCHOR_INVALID",
                    "insertAfter is not a results-driven stock boundary for " + manifest.baseGame(),
                    List.of());
        }
        return null;
    }

    private static ModEligibility blocked(String id, String code, String message, List<String> related) {
        return new ModEligibility(id, ModEligibility.Status.BLOCKED, List.of(reason(code, message, related)));
    }

    private static ModEligibility.Reason reason(String code, String message, List<String> related) {
        return new ModEligibility.Reason(code, message, related);
    }

}
