package com.openggf.sprites.playable;

import com.openggf.game.rules.GameRules;

final class PlayableHurtRadiusTransition {
    private PlayableHurtRadiusTransition() {
    }

    static void apply(AbstractPlayableSprite sprite) {
        boolean wasRolling = sprite.getRolling();
        int nativeXBeforeRadiusChange = sprite.getCentreX();
        sprite.setRolling(false);
        if (wasRolling) {
            sprite.setCentreXPreserveSubpixel((short) nativeXBeforeRadiusChange);
        }
        GameRules rules = sprite.getGameRules();
        boolean restoresSplitSidekickRadii = !(sprite instanceof Tails)
                || rules == null || rules.sidekickCpu() == null
                || rules.sidekickCpu().sidekickHurtRestoresRadiiWithoutRoll();
        if (restoresSplitSidekickRadii) {
            sprite.applyStandingRadii(false);
        }
        if (wasRolling) {
            sprite.setY((short) (sprite.getY() - sprite.getRollHeightAdjustment()));
        }
    }
}
