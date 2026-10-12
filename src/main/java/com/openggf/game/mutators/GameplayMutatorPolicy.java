package com.openggf.game.mutators;

/**
 * Bounded engine-owned values consumed at native scatter, defeat and presentation
 * owners. A zero ringfall cap disables that optional cap. Full inventory removes the
 * native 32-ring scatter ceiling; the held-ring counter still bounds it. The vertical
 * defeat cap is a positive 8.8 velocity magnitude, not a limit on unrelated horizontal motion.
 */
public record GameplayMutatorPolicy(int ringfallPercent, int ringfallCap, boolean ringfallFullInventory,
                                    int defeatReboundPercent, int defeatVerticalCap,
                                    int gameplaySpeedPercent, boolean audioFollowsSpeed) {
    public static final GameplayMutatorPolicy STOCK =
            new GameplayMutatorPolicy(100, 0, false, 100, 0xC00, 100, false);

    public GameplayMutatorPolicy {
        if (ringfallPercent < 10 || ringfallPercent > 100 || ringfallCap < 0 || ringfallCap > 32
                || defeatReboundPercent < 100 || defeatReboundPercent > 300
                || defeatVerticalCap < 0x100 || defeatVerticalCap > 0x2000
                || gameplaySpeedPercent < 25 || gameplaySpeedPercent > 400) {
            throw new IllegalArgumentException("Gameplay mutator policy exceeds native host bounds");
        }
    }

    /** Native 32-ring scatter ceiling. */
    public GameplayMutatorPolicy(int ringfallPercent, int ringfallCap,
                                 int defeatReboundPercent, int defeatVerticalCap,
                                 int gameplaySpeedPercent, boolean audioFollowsSpeed) {
        this(ringfallPercent, ringfallCap, false, defeatReboundPercent, defeatVerticalCap,
                gameplaySpeedPercent, audioFollowsSpeed);
    }
}
