package com.openggf.game.rules;

@com.openggf.game.ModApi
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
        boolean solidPushReleaseWritesWalkRunAnimationWord,
        boolean solidPushReleaseSkipsWalkRunWhenRolling,
        boolean solidPushReleaseSkipsWalkRunWhenSpindashing,
        int duckTouchBoxMappingFrame) {

    public static final int NO_DUCK_TOUCH_BOX = -1;
    public static final int DUCK_TOUCH_BOX_TOP_SHIFT = 12;
    public static final int DUCK_TOUCH_BOX_HEIGHT = 20;

    public boolean isDuckTouchBoxMappingFrame(int mappingFrame) {
        return duckTouchBoxMappingFrame != NO_DUCK_TOUCH_BOX
                && (mappingFrame & 0xFF) == duckTouchBoxMappingFrame;
    }
}
