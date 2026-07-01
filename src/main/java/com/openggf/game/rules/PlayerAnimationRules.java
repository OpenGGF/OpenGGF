package com.openggf.game.rules;

import com.openggf.game.PhysicsFeatureSet;

public record PlayerAnimationRules(
        boolean extendedEdgeBalance,
        boolean singleFacingBalanceAnimationSet,
        boolean animationChangeClearsPush) {

    public static PlayerAnimationRules fromLegacy(PhysicsFeatureSet fs) {
        return new PlayerAnimationRules(
                fs.extendedEdgeBalance(),
                fs.singleFacingBalanceAnimationSet(),
                fs.animationChangeClearsPush());
    }
}
