package com.openggf.game.rules;

@com.openggf.game.ModApi
public record PlayerAnimationRules(
        boolean extendedEdgeBalance,
        boolean singleFacingBalanceAnimationSet,
        boolean animationChangeClearsPush) {
}
