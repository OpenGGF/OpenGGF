package com.openggf.game.rules;

import com.openggf.game.ModApi;

@ModApi
public record ObjectInteractionRules(
        boolean bossHitNegatesGroundSpeed,
        boolean bossHitHalvesBounceVelocity,
        boolean sidekickDespawnUsesObjectIdMismatch,
        boolean sidekickDespawnUsesRidingInstanceLoss,
        boolean sidekickDespawnUsesInteractCodeWordChange,
        boolean sidekickNormalCpuSkipsHurtRoutine,
        boolean permanentRespawnTableLatch,
        // Touch_Loop (s2.asm:85209-85219) and KiS2 Touch_Loop
        // (docs/kis2disasm/s2.asm:88464-88474) scan collision_flags without a render gate.
        // S1 React_LoopObjects (_incObj/Sonic ReactToItem.asm:47-53) tests obRender bit7.
        // Separate from the geometry of that bit and collision-response-list admission.
        boolean touchScanRequiresObjectRenderBit,
        boolean touchResponseUsesRenderFlagYGate,
        boolean touchResponseUsesPreviousCollisionResponseList,
        boolean animalObjectPreservesObjectMoveXSubpixel,
        boolean animalObjectUsesRenderFlagDeleteBounds,
        boolean solidPushReleaseWritesWalkRunAnimationWord,
        boolean solidPushReleaseSkipsWalkRunWhenRolling,
        boolean solidPushReleaseSkipsWalkRunWhenSpindashing,
        int duckTouchBoxMappingFrame,
        int bossDuckTouchBoxMappingFrame,
        boolean bossHitEndsActiveGlide) {

    public boolean isBossDuckTouchBoxMappingFrame(int mappingFrame) {
        return bossDuckTouchBoxMappingFrame != NO_DUCK_TOUCH_BOX
                && (mappingFrame & 0xFF) == bossDuckTouchBoxMappingFrame;
    }

    public static final int NO_DUCK_TOUCH_BOX = -1;
    public static final int DUCK_TOUCH_BOX_TOP_SHIFT = 12;
    public static final int DUCK_TOUCH_BOX_HEIGHT = 20;

    public boolean isDuckTouchBoxMappingFrame(int mappingFrame) {
        return duckTouchBoxMappingFrame != NO_DUCK_TOUCH_BOX
                && (mappingFrame & 0xFF) == duckTouchBoxMappingFrame;
    }
}
