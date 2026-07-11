package com.openggf.game.patch;

import com.openggf.game.GameModule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Per-launch frozen registrations and owner graph, plus failures local to that launch.
 * No state is shared with another resolution attempt.
 */
public final class ResolutionContext {

    private final PatchEnablement enablement;
    private final List<RegisteredPatch> registrations;
    private final Map<PatchOwner, Set<PatchOwner>> dependencies;
    private final Map<PatchOwner, Set<PatchOwner>> dependents;
    private final Map<PatchOwner, Throwable> failures = new LinkedHashMap<>();

    private ResolutionContext(PatchEnablement enablement, List<RegisteredPatch> registrations,
            Map<PatchOwner, ? extends Set<PatchOwner>> dependencies) {
        this.registrations = List.copyOf(Objects.requireNonNull(registrations, "registrations"));
        validateUniqueRegistrations(this.registrations);
        this.dependencies = freezeGraph(dependencies);
        this.dependents = reverse(this.dependencies);
        this.enablement = freezeEnablement(
                Objects.requireNonNull(enablement, "enablement"),
                this.registrations, this.dependencies);
        validateAcyclic();
    }

    public static ResolutionContext forTests(PatchEnablement enablement,
            List<RegisteredPatch> registrations,
            Map<PatchOwner, ? extends Set<PatchOwner>> dependencies) {
        return new ResolutionContext(enablement, registrations, dependencies);
    }

    static ResolutionContext create(PatchEnablement enablement,
            List<RegisteredPatch> registrations,
            Map<PatchOwner, ? extends Set<PatchOwner>> dependencies) {
        return new ResolutionContext(enablement, registrations, dependencies);
    }

    public PatchEnablement enablement() {
        return enablement;
    }

    public List<RegisteredPatch> registrations() {
        return registrations;
    }

    public Map<PatchOwner, Set<PatchOwner>> ownerDependencies() {
        return dependencies;
    }

    public synchronized Map<PatchOwner, Throwable> failures() {
        return Map.copyOf(failures);
    }

    public synchronized Set<PatchOwner> failedOwners() {
        return Set.copyOf(failures.keySet());
    }

    synchronized boolean ownerEligible(PatchOwner owner) {
        return enablement.isEnabled(owner)
                && !failures.containsKey(owner)
                && dependencies.getOrDefault(owner, Set.of()).stream()
                        .allMatch(this::ownerEligible);
    }

    synchronized void failOwnerAndDependents(PatchOwner owner, Throwable failure) {
        ArrayDeque<PatchOwner> pending = new ArrayDeque<>();
        pending.add(owner);
        while (!pending.isEmpty()) {
            PatchOwner failed = pending.removeFirst();
            if (failures.putIfAbsent(failed, failure) == null) {
                pending.addAll(dependents.getOrDefault(failed, Set.of()));
            }
        }
    }

    List<RegisteredPatch> topologicallyOrderedFor(GameModule base) {
        Objects.requireNonNull(base, "base");
        Map<PatchOwner, List<RegisteredPatch>> byOwner = new LinkedHashMap<>();
        for (RegisteredPatch registration : registrations) {
            byOwner.computeIfAbsent(registration.owner(), ignored -> new ArrayList<>())
                    .add(registration);
        }
        byOwner.values().forEach(list -> list.sort(
                Comparator.comparingLong(RegisteredPatch::registrationIndex)));

        Comparator<PatchOwner> ownerOrder = Comparator
                .comparingInt((PatchOwner owner) -> enablement.orderOf(owner))
                .thenComparingLong(owner -> firstRegistration(owner, byOwner))
                .thenComparing(Object::toString);
        Map<PatchOwner, Integer> remainingDependencies = new HashMap<>();
        PriorityQueue<PatchOwner> ready = new PriorityQueue<>(ownerOrder);
        for (PatchOwner owner : byOwner.keySet()) {
            int count = (int) dependencies.getOrDefault(owner, Set.of()).stream()
                    .filter(byOwner::containsKey)
                    .count();
            remainingDependencies.put(owner, count);
            if (count == 0) {
                ready.add(owner);
            }
        }
        List<PatchOwner> orderedOwners = new ArrayList<>();
        while (!ready.isEmpty()) {
            PatchOwner owner = ready.remove();
            orderedOwners.add(owner);
            for (PatchOwner dependent : dependents.getOrDefault(owner, Set.of())) {
                if (!remainingDependencies.containsKey(dependent)) {
                    continue;
                }
                int remaining = remainingDependencies.computeIfPresent(
                        dependent, (ignored, count) -> count - 1);
                if (remaining == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (orderedOwners.size() != byOwner.size()) {
            throw new IllegalArgumentException("Owner dependency graph cannot be ordered");
        }

        List<RegisteredPatch> result = new ArrayList<>();
        for (PatchOwner owner : orderedOwners) {
            result.addAll(byOwner.getOrDefault(owner, List.of()));
        }
        return List.copyOf(result);
    }

    private void validateAcyclic() {
        Set<PatchOwner> nodes = new LinkedHashSet<>();
        nodes.addAll(dependencies.keySet());
        dependencies.values().forEach(nodes::addAll);
        Set<PatchOwner> visited = new HashSet<>();
        Set<PatchOwner> visiting = new HashSet<>();
        for (PatchOwner node : nodes) {
            validateNode(node, visited, visiting);
        }
    }

    private void validateNode(PatchOwner owner, Set<PatchOwner> visited, Set<PatchOwner> visiting) {
        if (visited.contains(owner)) {
            return;
        }
        if (!visiting.add(owner)) {
            throw new IllegalArgumentException("Owner dependency cycle includes " + owner);
        }
        for (PatchOwner dependency : dependencies.getOrDefault(owner, Set.of())) {
            validateNode(dependency, visited, visiting);
        }
        visiting.remove(owner);
        visited.add(owner);
    }

    private static long firstRegistration(PatchOwner owner,
            Map<PatchOwner, List<RegisteredPatch>> byOwner) {
        return byOwner.get(owner).stream().mapToLong(RegisteredPatch::registrationIndex)
                .min().orElse(Long.MAX_VALUE);
    }

    private static void validateUniqueRegistrations(List<RegisteredPatch> registrations) {
        Set<String> ids = new HashSet<>();
        Map<PatchOwner, Set<Long>> indexesByOwner = new HashMap<>();
        for (RegisteredPatch registration : registrations) {
            if (!ids.add(registration.namespacedId())) {
                throw new IllegalArgumentException(
                        "Duplicate registered patch id: " + registration.namespacedId());
            }
            if (!indexesByOwner.computeIfAbsent(registration.owner(), ignored -> new HashSet<>())
                    .add(registration.registrationIndex())) {
                throw new IllegalArgumentException("Duplicate registration index "
                        + registration.registrationIndex() + " for owner " + registration.owner());
            }
        }
    }

    private static PatchEnablement freezeEnablement(PatchEnablement source,
            List<RegisteredPatch> registrations,
            Map<PatchOwner, Set<PatchOwner>> dependencies) {
        Set<PatchOwner> owners = new LinkedHashSet<>();
        registrations.forEach(registration -> owners.add(registration.owner()));
        owners.addAll(dependencies.keySet());
        dependencies.values().forEach(owners::addAll);
        Map<PatchOwner, Boolean> enabled = new HashMap<>();
        Map<PatchOwner, Integer> order = new HashMap<>();
        for (PatchOwner owner : owners) {
            enabled.put(owner, source.isEnabled(owner));
            order.put(owner, source.orderOf(owner));
        }
        Map<PatchOwner, Boolean> frozenEnabled = Map.copyOf(enabled);
        Map<PatchOwner, Integer> frozenOrder = Map.copyOf(order);
        return new PatchEnablement() {
            @Override
            public boolean isEnabled(PatchOwner owner) {
                Boolean value = frozenEnabled.get(Objects.requireNonNull(owner, "owner"));
                if (value == null) {
                    throw new IllegalArgumentException("Owner absent from frozen context: " + owner);
                }
                return value;
            }

            @Override
            public int orderOf(PatchOwner owner) {
                Integer value = frozenOrder.get(Objects.requireNonNull(owner, "owner"));
                if (value == null) {
                    throw new IllegalArgumentException("Owner absent from frozen context: " + owner);
                }
                return value;
            }
        };
    }

    private static Map<PatchOwner, Set<PatchOwner>> freezeGraph(
            Map<PatchOwner, ? extends Set<PatchOwner>> graph) {
        Objects.requireNonNull(graph, "dependencies");
        Map<PatchOwner, Set<PatchOwner>> frozen = new LinkedHashMap<>();
        for (Map.Entry<PatchOwner, ? extends Set<PatchOwner>> entry : graph.entrySet()) {
            PatchOwner owner = Objects.requireNonNull(entry.getKey(), "dependency owner");
            Set<PatchOwner> required = Set.copyOf(
                    Objects.requireNonNull(entry.getValue(), "owner dependencies"));
            if (required.contains(owner)) {
                throw new IllegalArgumentException("Owner cannot depend on itself: " + owner);
            }
            frozen.put(owner, required);
        }
        return Map.copyOf(frozen);
    }

    private static Map<PatchOwner, Set<PatchOwner>> reverse(
            Map<PatchOwner, Set<PatchOwner>> graph) {
        Map<PatchOwner, Set<PatchOwner>> result = new HashMap<>();
        for (Map.Entry<PatchOwner, Set<PatchOwner>> entry : graph.entrySet()) {
            for (PatchOwner dependency : entry.getValue()) {
                result.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>())
                        .add(entry.getKey());
            }
        }
        Map<PatchOwner, Set<PatchOwner>> frozen = new HashMap<>();
        result.forEach((owner, owners) -> frozen.put(owner, Set.copyOf(owners)));
        return Map.copyOf(frozen);
    }
}
