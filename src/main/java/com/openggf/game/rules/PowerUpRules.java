package com.openggf.game.rules;

/**
 * Game-wide power-up/effect rules.
 *
 * <p>{@code speedShoesTimerPrePhysicsExtraTicks} compensates for where the
 * countdown sits inside the frame. In S1 and S2 the shoes timer is decremented
 * -- and, on the decrement that reaches zero, the acceleration/top-speed/
 * deceleration values are restored -- inside {@code Sonic_Display}, which the
 * player's control routine calls <em>after</em> it has already dispatched the
 * movement modes: {@code jsr Sonic_Modes} then {@code bsr.s Sonic_Display}
 * ({@code docs/s1disasm/_incObj/01 Sonic.asm:76,80}; the decrement and restore
 * at {@code :186-191}), and {@code jsr Obj01_Modes} then
 * {@code bsr.s Sonic_Display} ({@code docs/s2disasm/s2.asm:36240,36244};
 * decrement at {@code :36310-36312}). The frame whose display step zeroes the
 * timer therefore still moved with boosted acceleration. The engine ticks its
 * timers before the movement step, so both games need one extra tick to place
 * the restore on the same frame boundary the ROM does. S3K's value is left at
 * zero here: its byte timer only decrements on every eighth level frame
 * ({@code docs/skdisasm/sonic3k.asm:22072-22078}), so its phase is set by that
 * gate rather than by this offset, and it has not been measured against a
 * trace that expires speed shoes.
 *
 * <p>{@code waterSplashFixedSlotIndex} is the absolute SST slot the water-entry
 * splash occupies, or {@code -1} when the game allocates it dynamically. Sonic 1
 * writes {@code id_Splash} straight into {@code v_splash}
 * ({@code docs/s1disasm/_incObj/01 Sonic.asm:274,299}), which is
 * {@code v_objspace+object_size*12} ({@code docs/s1disasm/_Variables.asm:71}) --
 * a fixed SST outside {@code v_lvlobjspace}, so the splash never runs
 * {@code FindFreeObj} and never consumes a level-object slot.
 *
 * <p>{@code superStarsFixedSlotIndex} is the absolute SST slot the super-form
 * stars object occupies, or {@code -1} when the game has no such object. Sonic 2
 * writes {@code ObjID_SuperSonicStars} straight into {@code SuperSonicStars}
 * ({@code docs/s2disasm/s2.asm:26209,37481}, whose own comment names
 * {@code $FFFFD040}), which is the second entry of
 * {@code LevelOnly_Object_RAM} ({@code docs/s2disasm/s2.constants.asm:1149-1155})
 * -- past {@code Object_RAM_End}, so above rather than below the dynamic pool,
 * but equally outside it. Sonic 3&K writes its super/hyper stars into
 * {@code Super_stars} ({@code docs/skdisasm/sonic3k.asm:23504}), the third entry
 * of {@code Level_object_RAM} ({@code docs/skdisasm/sonic3k.constants.asm:309-315}).
 * Sonic 1 has no super form and no such object.
 */
@com.openggf.game.ModApi
public record PowerUpRules(
        int speedShoesTimerPrePhysicsExtraTicks,
        int shieldObjectFixedSlotIndex,
        int invincibilityStarsFixedSlotIndex,
        int waterSplashFixedSlotIndex,
        int superStarsFixedSlotIndex,
        int speedShoesTimerDecimation,
        boolean fixedSkidDustAllocatesAfterDynamicObjectPass,
        boolean waterSplashUsesFixedDustObject,
        int primaryFixedDustSlotIndex,
        int secondaryFixedDustSlotIndex) {

    public int fixedDustSlotIndex(boolean secondaryPlayer) {
        return secondaryPlayer ? secondaryFixedDustSlotIndex : primaryFixedDustSlotIndex;
    }
}
