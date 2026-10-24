package com.openggf.stageplay.route;

import com.openggf.stageplay.StageplayDocument;

import java.util.HashSet;
import java.util.Set;

/** Shared authoring bounds; search budgets are tool limits, not native behavior constants. */
public final class StageplayRouteContract {
    private StageplayRouteContract() {}

    public static StageplayDocument.SetSource source(StageplayDocument document, int frame) {
        var result = document.set();
        for (var change : document.sceneChanges())
            if (change.frame() <= frame) result = change.set();
        return result;
    }

    public static int shotEnd(StageplayDocument document, int frame) {
        return document.sceneChanges().stream()
                .filter(c -> c.frame() > frame)
                .mapToInt(StageplayDocument.SceneChange::frame)
                .min()
                .orElse(document.durationFrames());
    }

    public static String unavailableReason(
            StageplayDocument document, StageplayRouteRequest request) {
        var object = document.objectActors().stream()
                .filter(actor -> actor.id().equals(request.actorId())).findFirst();
        if (object.isPresent()) {
            if (object.get().keys().stream().noneMatch(key -> key.frame() == request.startFrame()
                    && key.visible() && key.mode() == StageplayDocument.ObjectMode.PUPPET))
                return "Object routes start at an existing visible PUPPET key; retain the native"
                        + " Free initialization earlier in the same shot";
        } else if (request.startFrame() != 0
                && document.sceneChanges().stream()
                        .noneMatch(c -> c.frame() == request.startFrame()))
            return "Player routes start at a shot-entry frame; the actor is never reset at an"
                       + " arbitrary frame";
        if ((long) request.startFrame() + request.maxFrames()
                > shotEnd(document, request.startFrame()))
            return "The route window must fit entirely within its shot";
        return "";
    }

    public static Set<String> identities(StageplayDocument document) {
        Set<String> ids = new HashSet<>();
        document.actors().forEach(a -> ids.add(a.id()));
        document.objectActors().forEach(a -> ids.add(a.id()));
        document.captions().forEach(a -> ids.add(a.id()));
        document.audio().forEach(a -> ids.add(a.id()));
        document.cameraEffects().forEach(a -> ids.add(a.id()));
        document.dialogue().forEach(a -> ids.add(a.id()));
        document.sceneChanges().forEach(a -> ids.add(a.id()));
        document.worldCommands().forEach(a -> ids.add(a.id()));
        document.puppets().forEach(p -> p.commands().forEach(c -> ids.add(c.id())));
        document.objectActors().forEach(p -> p.commands().forEach(c -> ids.add(c.id())));
        return ids;
    }

    public static String nextId(Set<String> ids, String prefix) {
        for (int index = 0; index < 100_000; index++) {
            String candidate = prefix + index;
            if (ids.add(candidate)) return candidate;
        }
        throw new IllegalArgumentException("No portable route command identity remains");
    }
}
