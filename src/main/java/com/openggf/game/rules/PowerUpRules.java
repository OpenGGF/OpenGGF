package com.openggf.game.rules;

@com.openggf.game.ModApi
public record PowerUpRules(
        int speedShoesTimerPrePhysicsExtraTicks,
        int shieldObjectFixedSlotIndex,
        int invincibilityStarsFixedSlotIndex,
        int speedShoesTimerDecimation,
        boolean fixedSkidDustAllocatesAfterDynamicObjectPass,
        boolean waterSplashUsesFixedDustObject,
        int primaryFixedDustSlotIndex,
        int secondaryFixedDustSlotIndex) {

    public int fixedDustSlotIndex(boolean secondaryPlayer) {
        return secondaryPlayer ? secondaryFixedDustSlotIndex : primaryFixedDustSlotIndex;
    }
}
