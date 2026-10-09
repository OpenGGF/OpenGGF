package com.openggf.game.rules;

/**
 * Game-wide power-up/effect rules.
 * Mods that leave music playing during invincibility can disable its expiry music request.
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
 * {@code Super_stars} ({@code docs/skdisasm/sonic3k.asm:23539}), the third entry
 * of {@code Level_object_RAM} ({@code docs/skdisasm/sonic3k.constants.asm:316-322}).
 * Sonic 1 has no super form and no such object.
 */
@com.openggf.game.ModApi
public record PowerUpRules(
        int shieldObjectFixedSlotIndex,
        int invincibilityStarsFixedSlotIndex,
        int waterSplashFixedSlotIndex,
        int superStarsFixedSlotIndex,
        int speedShoesTimerDecimation,
        boolean fixedSkidDustAllocatesAfterDynamicObjectPass,
        boolean waterSplashUsesFixedDustObject,
        int primaryFixedDustSlotIndex,
        int secondaryFixedDustSlotIndex,
        boolean restoreLevelMusicAfterInvincibility) {

    public int fixedDustSlotIndex(boolean secondaryPlayer) {
        return secondaryPlayer ? secondaryFixedDustSlotIndex : primaryFixedDustSlotIndex;
    }
}
