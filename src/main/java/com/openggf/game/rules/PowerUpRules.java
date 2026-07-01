package com.openggf.game.rules;

import com.openggf.game.PhysicsFeatureSet;

public record PowerUpRules(
        int speedShoesTimerPrePhysicsExtraTicks,
        int shieldObjectFixedSlotIndex,
        int invincibilityStarsFixedSlotIndex,
        int speedShoesTimerDecimation,
        boolean fixedSkidDustAllocatesAfterDynamicObjectPass,
        boolean waterSplashUsesFixedDustObject,
        int primaryFixedDustSlotIndex,
        int secondaryFixedDustSlotIndex) {

    public static PowerUpRules fromLegacy(PhysicsFeatureSet fs) {
        return new PowerUpRules(
                fs.speedShoesTimerPrePhysicsExtraTicks(),
                fs.shieldObjectFixedSlotIndex(),
                fs.invincibilityStarsFixedSlotIndex(),
                fs.speedShoesTimerDecimation(),
                fs.fixedSkidDustAllocatesAfterDynamicObjectPass(),
                fs.waterSplashUsesFixedDustObject(),
                fs.fixedDustSlotIndex(false),
                fs.fixedDustSlotIndex(true));
    }

    public int fixedDustSlotIndex(boolean secondaryPlayer) {
        return secondaryPlayer ? secondaryFixedDustSlotIndex : primaryFixedDustSlotIndex;
    }
}
