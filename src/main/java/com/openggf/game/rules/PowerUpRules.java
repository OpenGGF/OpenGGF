package com.openggf.game.rules;

/**
 * Game-wide power-up/effect rules.
 *
 * <p>{@code waterSplashFixedSlotIndex} is the absolute SST slot the water-entry
 * splash occupies, or {@code -1} when the game allocates it dynamically. Sonic 1
 * writes {@code id_Splash} straight into {@code v_splash}
 * ({@code docs/s1disasm/_incObj/01 Sonic.asm:274,299}), which is
 * {@code v_objspace+object_size*12} ({@code docs/s1disasm/_Variables.asm:71}) --
 * a fixed SST outside {@code v_lvlobjspace}, so the splash never runs
 * {@code FindFreeObj} and never consumes a level-object slot.
 */
public record PowerUpRules(
        int speedShoesTimerPrePhysicsExtraTicks,
        int shieldObjectFixedSlotIndex,
        int invincibilityStarsFixedSlotIndex,
        int waterSplashFixedSlotIndex,
        int speedShoesTimerDecimation,
        boolean fixedSkidDustAllocatesAfterDynamicObjectPass,
        boolean waterSplashUsesFixedDustObject,
        int primaryFixedDustSlotIndex,
        int secondaryFixedDustSlotIndex) {

    public int fixedDustSlotIndex(boolean secondaryPlayer) {
        return secondaryPlayer ? secondaryFixedDustSlotIndex : primaryFixedDustSlotIndex;
    }
}
