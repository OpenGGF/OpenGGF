package com.openggf.mods;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Complete manifest dependency graph with SCC detection and stable topological ordering. */
final class ModDependencyGraph {
    private final Map<String, List<String>> dependencies;

    ModDependencyGraph(Map<String, ModDescriptor> descriptors) {
        dependencies = new LinkedHashMap<>();
        descriptors.forEach((id, descriptor) -> dependencies.put(id,
                descriptor.manifest().dependencies().stream().map(ModDependency::id)
                        .filter(descriptors::containsKey).toList()));
    }

    Map<String, List<String>> cycleParticipants() {
        List<String> finished = finishOrder();
        Map<String, List<String>> reverse = reverseEdges();
        Set<String> assigned = new java.util.HashSet<>();
        Map<String, List<String>> cycles = new LinkedHashMap<>();
        for (int index = finished.size() - 1; index >= 0; index--) {
            String root = finished.get(index);
            if (!assigned.add(root)) continue;
            List<String> component = new ArrayList<>();
            ArrayDeque<String> work = new ArrayDeque<>();
            work.push(root);
            while (!work.isEmpty()) {
                String id = work.pop();
                component.add(id);
                for (String next : reverse.getOrDefault(id, List.of())) {
                    if (assigned.add(next)) work.push(next);
                }
            }
            boolean selfCycle = component.size() == 1
                    && dependencies.getOrDefault(root, List.of()).contains(root);
            if (component.size() > 1 || selfCycle) {
                component.sort(Comparator.naturalOrder());
                List<String> participants = List.copyOf(component);
                participants.forEach(member -> cycles.put(member, participants));
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(cycles));
    }

    List<String> stableOrder(Set<String> included, Map<String, Integer> priority,
                             Map<String, Integer> discoveryOrder) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        included.forEach(id -> indegree.put(id, 0));
        for (String id : included) {
            for (String dependency : dependencies.getOrDefault(id, List.of())) {
                if (!included.contains(dependency)) continue;
                indegree.merge(id, 1, Integer::sum);
                dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(id);
            }
        }
        Comparator<String> comparator = Comparator
                .comparingInt((String id) -> priority.getOrDefault(id, Integer.MAX_VALUE))
                .thenComparingInt(id -> discoveryOrder.getOrDefault(id, Integer.MAX_VALUE))
                .thenComparing(Comparator.naturalOrder());
        PriorityQueue<String> ready = new PriorityQueue<>(comparator);
        indegree.forEach((id, degree) -> { if (degree == 0) ready.add(id); });
        List<String> ordered = new ArrayList<>(included.size());
        while (!ready.isEmpty()) {
            String id = ready.remove();
            ordered.add(id);
            for (String dependent : dependents.getOrDefault(id, List.of())) {
                int remaining = indegree.merge(dependent, -1, Integer::sum);
                if (remaining == 0) ready.add(dependent);
            }
        }
        if (ordered.size() != included.size()) {
            throw new IllegalStateException("Cycle members must be excluded before topological ordering");
        }
        return List.copyOf(ordered);
    }

    private List<String> finishOrder() {
        Set<String> visited = new java.util.HashSet<>();
        List<String> finished = new ArrayList<>(dependencies.size());
        for (String root : dependencies.keySet()) {
            if (!visited.add(root)) continue;
            ArrayDeque<TraversalFrame> work = new ArrayDeque<>();
            work.push(new TraversalFrame(root));
            while (!work.isEmpty()) {
                TraversalFrame frame = work.peek();
                List<String> adjacent = dependencies.getOrDefault(frame.id, List.of());
                if (frame.nextIndex < adjacent.size()) {
                    String next = adjacent.get(frame.nextIndex++);
                    if (visited.add(next)) work.push(new TraversalFrame(next));
                } else {
                    finished.add(frame.id);
                    work.pop();
                }
            }
        }
        return finished;
    }

    private Map<String, List<String>> reverseEdges() {
        Map<String, List<String>> reverse = new LinkedHashMap<>();
        dependencies.keySet().forEach(id -> reverse.put(id, new ArrayList<>()));
        dependencies.forEach((id, adjacent) -> adjacent.forEach(dependency -> reverse.get(dependency).add(id)));
        return reverse;
    }

    private static final class TraversalFrame {
        private final String id;
        private int nextIndex;

        private TraversalFrame(String id) {
            this.id = id;
        }
    }
}
