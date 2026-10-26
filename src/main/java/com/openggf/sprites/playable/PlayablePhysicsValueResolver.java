package com.openggf.sprites.playable;

import com.openggf.game.mutators.PlayerFluidContextAccess;
import com.openggf.game.PhysicsModifiers;

final class PlayablePhysicsValueResolver {
    private PlayablePhysicsValueResolver() {
    }

    // Local creator fluid keeps its existing dynamic profile calculation; it is not a native writer.
    private static boolean localFluid(AbstractPlayableSprite player) {
        return PlayerFluidContextAccess.speedConstantsUnderwater(player, false);
    }

    static int nativeAcceleration(AbstractPlayableSprite player) {
        return localFluid(player) ? -1 : PlayableSpriteInternalAccess.nativeAccelerationWord(player);
    }

    static short runAcceleration(AbstractPlayableSprite player, PhysicsModifiers modifiers) {
        int nativeWord = nativeAcceleration(player);
        if (nativeWord >= 0) return (short) nativeWord;
        boolean water = PlayerFluidContextAccess.speedConstantsUnderwater(player, player.waterPhysicsActive);
        boolean shoes = player.hasSpeedShoes() && !player.isSuperSonic();
        return modifiers != null
                ? modifiers.effectiveAccel(player.runAccel, water, shoes)
                : runAcceleration(player.runAccel, water, shoes);
    }

    static short runDeceleration(AbstractPlayableSprite player, PhysicsModifiers modifiers) {
        int nativeWord = localFluid(player) ? -1 : PlayableSpriteInternalAccess.nativeDecelerationWord(player);
        if (nativeWord >= 0) return (short) nativeWord;
        boolean water = PlayerFluidContextAccess.speedConstantsUnderwater(player, player.waterPhysicsActive);
        boolean shoes = player.hasSpeedShoes() && !player.isSuperSonic();
        return modifiers != null
                ? modifiers.effectiveDecel(player.runDecel, water, shoes)
                : runDeceleration(player.runDecel, water);
    }

    static short maximumSpeed(AbstractPlayableSprite player, PhysicsModifiers modifiers) {
        int nativeWord = localFluid(player) ? -1 : PlayableSpriteInternalAccess.nativeMaximumSpeedWord(player);
        if (nativeWord >= 0) return (short) nativeWord;
        boolean water = PlayerFluidContextAccess.speedConstantsUnderwater(player, player.waterPhysicsActive);
        boolean shoes = player.hasSpeedShoes() && !player.isSuperSonic();
        return modifiers != null
                ? modifiers.effectiveMax(player.max, water, shoes)
                : maximumSpeed(player.max, water, shoes);
    }

    static short effectiveRunAcceleration(AbstractPlayableSprite player) {
        int nativeWord = nativeAcceleration(player);
        return nativeWord >= 0 ? (short) nativeWord : runAcceleration(player.runAccel,
                PlayerFluidContextAccess.speedConstantsUnderwater(player, player.waterPhysicsActive), player.speedShoes);
    }

    static short effectiveRunDeceleration(AbstractPlayableSprite player) {
        int nativeWord = localFluid(player) ? -1 : PlayableSpriteInternalAccess.nativeDecelerationWord(player);
        return nativeWord >= 0 ? (short) nativeWord : runDeceleration(player.runDecel,
                PlayerFluidContextAccess.speedConstantsUnderwater(player, player.waterPhysicsActive));
    }

    static short effectiveMaximumSpeed(AbstractPlayableSprite player) {
        int nativeWord = localFluid(player) ? -1 : PlayableSpriteInternalAccess.nativeMaximumSpeedWord(player);
        return nativeWord >= 0 ? (short) nativeWord : maximumSpeed(player.max,
                PlayerFluidContextAccess.speedConstantsUnderwater(player, player.waterPhysicsActive), player.speedShoes);
    }

    static short runAcceleration(short base, boolean waterActive, boolean speedShoes) {
        return waterActive ? (short) (base / 2) : speedShoes ? (short) (base * 2) : base;
    }

    static short runDeceleration(short base, boolean waterActive) {
        return waterActive ? (short) (base / 2) : base;
    }

    static short friction(short base, boolean waterActive, boolean speedShoes) {
        return waterActive ? (short) (base / 2) : speedShoes ? (short) (base * 2) : base;
    }

    static short maximumSpeed(short base, boolean waterActive, boolean speedShoes) {
        return waterActive ? (short) (base / 2) : speedShoes ? (short) (base * 2) : base;
    }

    static short jumpForce(short base, boolean inWater) {
        return inWater ? (short) 0x380 : base;
    }

    static short gravity(boolean inWater) {
        return inWater ? (short) 0x10 : (short) 0x38;
    }

    static short airDragThreshold(boolean inWater) {
        return inWater ? (short) -0x200 : (short) -0x400;
    }
}
