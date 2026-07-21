package com.openggf.game.rules;

@com.openggf.game.ModApi
public record PlayerAnimationRules(
        boolean extendedEdgeBalance,
        boolean singleFacingBalanceAnimationSet,
        boolean animationChangeClearsPush,
        boolean walkRunDelayLatchesRenderOrientation,
        boolean angledLandingPublishesWalk) {
    /** Binary-compatible constructor for the Mod API 2.4 rule shape. */
    public PlayerAnimationRules(boolean extendedEdgeBalance,
            boolean singleFacingBalanceAnimationSet, boolean animationChangeClearsPush) {
        this(extendedEdgeBalance, singleFacingBalanceAnimationSet,
                animationChangeClearsPush, false, false);
    }
}
