package com.openggf.game.rules;

public record ObjectInteractionRules(
        boolean bossHitNegatesGroundSpeed,
        boolean bossHitHalvesBounceVelocity,
        boolean sidekickDespawnUsesObjectIdMismatch,
        boolean sidekickNormalDespawnDelaysFreshRenderEntry,
        boolean sidekickDespawnUsesRidingInstanceLoss,
        boolean sidekickDespawnUsesInteractCodeWordChange,
        boolean sidekickNormalCpuSkipsHurtRoutine,
        boolean permanentRespawnTableLatch,
        boolean objectsExecuteAfterPlayerPhysics,
        boolean touchResponseUsesRenderFlagYGate,
        boolean touchResponseUsesPreviousCollisionResponseList,
        boolean animalObjectPreservesObjectMoveXSubpixel,
        boolean animalObjectUsesRenderFlagDeleteBounds,
        boolean solidPushReleaseWritesWalkRunAnimationWord) {
}
