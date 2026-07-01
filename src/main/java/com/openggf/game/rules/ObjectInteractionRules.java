package com.openggf.game.rules;

import com.openggf.game.PhysicsFeatureSet;

public record ObjectInteractionRules(
        boolean bossHitNegatesGroundSpeed,
        boolean bossHitHalvesBounceVelocity,
        boolean sidekickDespawnUsesObjectIdMismatch,
        boolean sidekickNormalDespawnDelaysFreshRenderEntry,
        boolean sidekickDespawnUsesRidingInstanceLoss,
        boolean sidekickNormalCpuSkipsHurtRoutine,
        boolean permanentRespawnTableLatch,
        boolean objectsExecuteAfterPlayerPhysics,
        boolean touchResponseUsesRenderFlagYGate,
        boolean touchResponseUsesPreviousCollisionResponseList,
        boolean animalObjectPreservesObjectMoveXSubpixel,
        boolean animalObjectUsesRenderFlagDeleteBounds) {

    public static ObjectInteractionRules fromLegacy(PhysicsFeatureSet fs) {
        return new ObjectInteractionRules(
                fs.bossHitNegatesGroundSpeed(),
                fs.bossHitHalvesBounceVelocity(),
                fs.sidekickDespawnUsesObjectIdMismatch(),
                fs.sidekickNormalDespawnDelaysFreshRenderEntry(),
                fs.sidekickDespawnUsesRidingInstanceLoss(),
                fs.sidekickNormalCpuSkipsHurtRoutine(),
                fs.permanentRespawnTableLatch(),
                fs.objectsExecuteAfterPlayerPhysics(),
                fs.touchResponseUsesRenderFlagYGate(),
                fs.touchResponseUsesPreviousCollisionResponseList(),
                fs.animalObjectPreservesObjectMoveXSubpixel(),
                fs.animalObjectUsesRenderFlagDeleteBounds());
    }
}
