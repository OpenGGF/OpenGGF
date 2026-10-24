package com.openggf.level.objects;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.identity.ObjectRefId;
import java.util.*;
import static com.openggf.level.objects.StageplayObjectDirection.*;

/** Rebinds controller picture handles after native object identities have restored. */
final class StageplayObjectRewind implements RewindSnapshottable<Snapshot> {
    private final StageplayObjectDirection owner;
    private final ObjectManager objects;
    private final IdentityHashMap<ObjectInstance, StageplayObjectDraw.Directed> roots, membership;
    private final LinkedHashMap<String, StageplayObjectDirection.VisualObject> visuals;
    private final IdentityHashMap<ObjectInstance, List<StageplayObjectGraph.ChildLink>> children;
    private final IdentityHashMap<ObjectInstance, Integer> nextChildOrdinals;
    private final IdentityHashMap<ObjectInstance, String> authored;

    StageplayObjectRewind(StageplayObjectDirection owner) {
        this.owner = owner;
        objects = owner.objects;
        roots = owner.roots;
        membership = owner.membership;
        visuals = owner.visuals;
        children = owner.children;
        nextChildOrdinals = owner.nextChildOrdinals;
        authored = owner.authored;
    }

    public String key() {
        return "stageplay-object-direction";
    }

    public Snapshot capture() {
        // Native deletion drops registered identities; inline boss components are
        // owned by their parent and may never have had a manager identity.
        for (var object : List.copyOf(authored.keySet()))
            if (object.isDestroyed()) owner.retired(object);
        for (var object : List.copyOf(children.keySet()))
            if (object.isDestroyed()) owner.retired(object);
        for (var links : List.copyOf(children.values()))
            for (var link : List.copyOf(links))
                if (link.child().isDestroyed()) owner.retired(link.child());
        List<DirectedSnapshot> directed = new ArrayList<>();
        List<VisualSnapshot> visualSnapshots = new ArrayList<>();
        List<EdgeSnapshot> edges = new ArrayList<>();
        for (StageplayObjectDraw.Directed root : roots.values()) {
            if (root.root instanceof VisualObject visual) {
                visualSnapshots.add(
                        new VisualSnapshot(visual.id, root.template, root.direction));
                continue;
            }
            ObjectRefId id = objects.objectQuery().identityOf(root.root).orElseThrow();
            List<ObjectRefId> members =
                    owner.graph(root.root).stream()
                            .map(o -> objects.objectQuery().identityOf(o))
                            .flatMap(Optional::stream)
                            .toList();
            directed.add(new DirectedSnapshot(id, root.template, root.direction, members));
        }
        for (var entry : children.entrySet()) {
            var parent = objects.objectQuery().identityOf(entry.getKey());
            if (parent.isEmpty()) {
                continue;
            }
            for (StageplayObjectGraph.ChildLink link : entry.getValue()) {
                objects.objectQuery()
                        .identityOf(link.child())
                        .ifPresent(
                                id ->
                                        edges.add(
                                                new EdgeSnapshot(
                                                        parent.get(), id, link.ordinal())));
            }
        }
        directed.sort(java.util.Comparator.comparing(entry -> entry.root().toString()));
        visualSnapshots.sort(java.util.Comparator.comparing(VisualSnapshot::id));
        edges.sort(
                Comparator.comparing((EdgeSnapshot edge) -> edge.parent().toString())
                        .thenComparingInt(EdgeSnapshot::ordinal));
        Map<ObjectRefId, Integer> nextOrdinals = new HashMap<>();
        for (var entry : nextChildOrdinals.entrySet()) {
            objects.objectQuery()
                    .identityOf(entry.getKey())
                    .ifPresent(id -> nextOrdinals.put(id, entry.getValue()));
        }
        Map<ObjectRefId, String> authoredIds = new HashMap<>();
        for (var entry : authored.entrySet()) {
            objects.objectQuery()
                    .identityOf(entry.getKey())
                    .ifPresent(id -> authoredIds.put(id, entry.getValue()));
        }
        return new Snapshot(
                directed,
                visualSnapshots,
                edges,
                nextOrdinals,
                authoredIds,
                owner.nextVisualId);
    }

    public void restore(Snapshot snapshot) {
        Map<String, VisualObject> retained = new HashMap<>(visuals);
        owner.close();
        owner.nextVisualId = snapshot.nextVisualId();
        for (var entry : snapshot.authored().entrySet()) {
            objects.objectQuery()
                    .resolve(entry.getKey())
                    .ifPresent(object -> owner.claimAuthored(entry.getValue(), object));
        }
        for (var visual : snapshot.visuals()) {
            VisualObject object =
                    retained.getOrDefault(visual.id(), new VisualObject(visual.id()));
            visuals.put(visual.id(), object);
            owner.direct(object, visual.template(), visual.direction());
        }
        for (var entry : snapshot.nextChildOrdinals().entrySet()) {
            objects.objectQuery()
                    .resolve(entry.getKey())
                    .ifPresent(parent -> nextChildOrdinals.put(parent, entry.getValue()));
        }
        for (var edge : snapshot.edges()) {
            ObjectInstance parent =
                    objects.objectQuery().resolve(edge.parent()).orElse(null);
            ObjectInstance child = objects.objectQuery().resolve(edge.child()).orElse(null);
            if (parent != null && child != null) {
                children.computeIfAbsent(parent, key -> new ArrayList<>())
                        .add(new StageplayObjectGraph.ChildLink(child, edge.ordinal()));
                owner.childSpawned(parent, child);
            }
        }
        for (var entry : snapshot.directed()) {
            ObjectInstance root =
                    objects.objectQuery()
                            .resolve(entry.root())
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Native graph missing after rewind"
                                                            + " restore"));
            owner.direct(root, entry.template(), entry.direction());
            StageplayObjectDraw.Directed owner = roots.get(root);
            for (var id : entry.members()) {
                objects.objectQuery()
                        .resolve(id)
                        .ifPresent(child -> membership.put(child, owner));
            }
        }
    }

    public void resetForMissingSnapshot() {
        owner.close();
    }
}
