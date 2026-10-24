package com.openggf.level.objects;

import com.openggf.level.objects.boss.AbstractBossInstance;
import java.util.*;

/** Live native ownership edges and stable picture-part paths; deletion releases strong references. */
final class StageplayObjectGraph {
    record ChildLink(ObjectInstance child, int ordinal) { }
    final IdentityHashMap<ObjectInstance, List<ChildLink>> children = new IdentityHashMap<>();
    final IdentityHashMap<ObjectInstance, Integer> nextChildOrdinals = new IdentityHashMap<>();
    final IdentityHashMap<ObjectInstance, String> authored = new IdentityHashMap<>();
    private final StageplayObjectDirection owner;
    private final ObjectManager objects;

    StageplayObjectGraph(StageplayObjectDirection owner) { this.owner = owner; this.objects = owner.objects; }

    void childSpawned(ObjectInstance parent, ObjectInstance child) {
        if (parent == child || child.isDestroyed()) {
            return;
        }
        if (objects.objectQuery().identityOf(child).isEmpty()
                && !(parent instanceof AbstractBossInstance boss && boss.getChildComponents().contains(child))) return;
        var list = children.computeIfAbsent(parent, k -> new ArrayList<>());
        if (list.stream().noneMatch(link -> link.child() == child)) {
            int ordinal = nextChildOrdinals.getOrDefault(parent, 0);
            nextChildOrdinals.put(parent, ordinal + 1);
            list.add(new ChildLink(child, ordinal));
        }
        String authoredId = authored.get(parent);
        if (authoredId != null) {
            owner.claimAuthored(authoredId, child);
        }
        StageplayObjectDraw.Directed directed = owner.membership.get(parent);
        if (directed != null) {
            for (ObjectInstance member : graph(child)) {
                owner.membership.put(member, directed);
            }
        }
    }

    void retired(ObjectInstance object) {
        children.remove(object);
        nextChildOrdinals.remove(object);
        authored.remove(object);
        owner.membership.remove(object);
        StageplayObjectDraw.Directed directed = owner.roots.remove(object);
        if (directed != null) {
            owner.membership.values().removeIf(owner -> owner == directed);
            directed.close();
        }
        for (var links : children.values()) links.removeIf(link -> link.child() == object);
        children.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    List<ObjectInstance> graph(ObjectInstance root) {
        List<ObjectInstance> result = new ArrayList<>();
        Set<ObjectInstance> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<ObjectInstance> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            ObjectInstance next = pending.removeFirst();
            if (next.isDestroyed() || !seen.add(next)) {
                continue;
            }
            result.add(next);
            for (ChildLink link : children.getOrDefault(next, List.of())) {
                pending.add(link.child());
            }
            if (next instanceof AbstractBossInstance boss) {
                pending.addAll(List.copyOf(boss.getChildComponents()));
            }
        }
        return result;
    }

    Map<ObjectInstance, String> structuralPaths(ObjectInstance root) {
        Map<ObjectInstance, String> paths = new IdentityHashMap<>();
        ArrayDeque<ObjectInstance> pending = new ArrayDeque<>();
        paths.put(root, "root");
        pending.add(root);
        while (!pending.isEmpty()) {
            ObjectInstance parent = pending.removeFirst();
            if (parent instanceof AbstractBossInstance boss) {
                for (var child : boss.getChildComponents()) {
                    childSpawned(parent, child);
                }
            }
            for (ChildLink link : children.getOrDefault(parent, List.of())) {
                ObjectInstance child = link.child();
                if (child.isDestroyed() || paths.containsKey(child)) {
                    continue;
                }
                paths.put(child, paths.get(parent) + "/" + link.ordinal());
                pending.add(child);
            }
        }
        return paths;
    }

}
