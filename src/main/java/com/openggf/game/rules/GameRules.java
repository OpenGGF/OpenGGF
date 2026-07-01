package com.openggf.game.rules;

import com.openggf.game.PhysicsFeatureSet;

public record GameRules(
        PlayerMovementRules playerMovement,
        PlayerCapabilityRules playerCapability,
        CollisionRules collision,
        PlayerAnimationRules playerAnimation,
        CameraRules camera,
        RingRules ring,
        ObjectInteractionRules objectInteraction,
        SidekickCpuRules sidekickCpu,
        PowerUpRules powerUp,
        DrowningBubbleRules drowningBubble) {

    public static GameRules fromLegacy(PhysicsFeatureSet fs) {
        if (fs == null) {
            throw new IllegalArgumentException("PhysicsFeatureSet is required");
        }
        return new GameRules(
                PlayerMovementRules.fromLegacy(fs),
                PlayerCapabilityRules.fromLegacy(fs),
                CollisionRules.fromLegacy(fs),
                PlayerAnimationRules.fromLegacy(fs),
                CameraRules.fromLegacy(fs),
                RingRules.fromLegacy(fs),
                ObjectInteractionRules.fromLegacy(fs),
                SidekickCpuRules.fromLegacy(fs),
                PowerUpRules.fromLegacy(fs),
                DrowningBubbleRules.fromLegacy(fs));
    }
}
