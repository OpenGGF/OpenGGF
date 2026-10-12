package com.openggf.level.objects;

import com.openggf.game.mutators.LevelMutatorPolicy;
import com.openggf.game.mutators.LevelMutatorPolicyAccess;
import java.util.Map;

/** Frozen load decisions retain the complete source placement list and its identities. */
final class ObjectPlacementAdmission {
    private final MutatorPlacementClassifier classifier;
    private final LevelMutatorPolicy policy;
    private final boolean ringsAllowed;
    ObjectPlacementAdmission(ObjectRegistry registry, ObjectServices services) {
        classifier = registry instanceof MutatorPlacementClassifier nativeClassifier ? nativeClassifier : null;
        policy = services == null ? LevelMutatorPolicy.STOCK : LevelMutatorPolicyAccess.policy(services.worldSession());
        ringsAllowed = LevelMutatorPolicyAccess.ringsAllowed(services);
    }
    boolean allowsNewSpawn(ObjectSpawn spawn, Map<ObjectSpawn, ObjectInstance> activeObjects,
                           ObjectPlacementController placement) {
        return spawn != null && allows(spawn) && !activeObjects.containsKey(spawn)
                && (!placement.isRemembered(spawn) || placement.isStayActive(spawn))
                && !placement.isDormant(spawn);
    }
    boolean allows(ObjectSpawn spawn) {
        if (classifier == null || spawn == null || spawn.ownerModId() != null) return true;
        var content = classifier.monitorContent(spawn);
        return (content == null || !policy.removedMonitorContents().contains(content))
                && (ringsAllowed || !classifier.isRingPlacement(spawn));
    }
}
