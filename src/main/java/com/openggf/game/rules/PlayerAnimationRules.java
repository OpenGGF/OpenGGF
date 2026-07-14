package com.openggf.game.rules;

public record PlayerAnimationRules(
        boolean extendedEdgeBalance,
        boolean singleFacingBalanceAnimationSet,
        boolean animationChangeClearsPush,
        boolean pushUsesWalkSpecialHandler,
        boolean walkRunDelayLatchesRenderOrientation) {
}
