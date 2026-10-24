package com.openggf.level.objects;

import java.util.*;

/** Session-policy admission and authored native ownership; factories remain ObjectManager-owned. */
final class StageplayObjectAdmission {
    private final StageplayObjectDirection owner;
    private final ObjectManager objects;
    private final StageplayObjectLoadPolicy loadPolicy;
    private final IdentityHashMap<ObjectInstance, String> authored;
    private int explicitNativeAdmission;
    private String authoredAdmissionId;
    private record BadnikKey(int zone, int act, int objectId) { }
    private final Map<BadnikKey, Boolean> badniks = new HashMap<>();

    StageplayObjectAdmission(StageplayObjectDirection owner, StageplayObjectLoadPolicy loadPolicy) {
        this.owner = owner; this.objects = owner.objects; this.loadPolicy = loadPolicy; this.authored = owner.authored;
    }

    boolean allowsObject(ObjectInstance object) {
        return explicitNativeAdmission > 0
                || authored.containsKey(object)
                || objects.stageplayAuthoredExecutionParent() != null
                || objects.isStageplayPlayerAttachmentOrRing(object)
                || !loadPolicy.clearObjects && (!loadPolicy.clearBadniks || !isBadnik(object));
    }

    void noteNativeAdmission(ObjectInstance object) {
        String id =
                explicitNativeAdmission > 0
                        ? authoredAdmissionId
                        : objects.stageplayAuthoredExecutionParent();
        if (id != null) {
            claimAuthored(id, object);
        }
    }

    String authoredId(ObjectInstance object) {
        return authored.get(object);
    }

    void claimAuthored(ObjectInstance root) {
        claimAuthored("stageplay-cast", root);
    }

    void claimAuthored(String actorId, ObjectInstance root) {
        Objects.requireNonNull(actorId, "authored actor identity");
        for (ObjectInstance member : owner.graph(root)) {
            authored.put(member, actorId);
        }
    }

    <T> T withAuthoredAdmission(String actorId, java.util.function.Supplier<T> action) {
        String previous = authoredAdmissionId;
        authoredAdmissionId = Objects.requireNonNull(actorId);
        explicitNativeAdmission++;
        try {
            return action.get();
        } finally {
            explicitNativeAdmission--;
            authoredAdmissionId = previous;
        }
    }

    boolean isBadnik(ObjectInstance object) {
        if (object instanceof AbstractBadnikInstance
                || object.headPresentationRole()
                        == com.openggf.game.mutators.ObjectHeadRole.BADNIK) {
            return true;
        }
        return isBadnik(object.getSpawn());
    }

    boolean isBadnik(ObjectSpawn spawn) {
        var level = objects.services().levelManager();
        var registry = objects.objectRegistry();
        if (level == null
                || registry == null
                || spawn == null
                || spawn.objectId() < 0
                || spawn.objectId() > 255) {
            return false;
        }
        int zone = level.getCurrentZone(), act = level.getCurrentAct();
        if (zone < 0 || act < 0) {
            return false;
        }
        return badniks.computeIfAbsent(new BadnikKey(zone, act, spawn.objectId()), key -> ObjectRegistryCatalog.entry(
                                registry,
                                new ObjectRegistryCatalog.Context(zone, act),
                                key.objectId())
                        .semantics()
                        .role()
                == ObjectRegistryCatalog.Role.BADNIK);
    }

    <T> T explicitNativeAdmission(java.util.function.Supplier<T> action) {
        return withAuthoredAdmission("stageplay-cast", action);
    }

}
