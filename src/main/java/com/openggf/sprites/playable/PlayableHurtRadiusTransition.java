package com.openggf.sprites.playable;

import com.openggf.game.rules.GameRules;

final class PlayableHurtRadiusTransition {
    private PlayableHurtRadiusTransition() {
    }

    static void apply(AbstractPlayableSprite sprite) {
        boolean wasRolling = sprite.getRolling();
        int nativeXBeforeRadiusChange = sprite.getCentreX();
        int nativeYBeforeRadiusChange = sprite.getCentreY();
        int oldYRadius = sprite.getYRadius();
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
            boolean usesCurrentRadiusDelta = rules != null
                    && rules.playerMovement() != null
                    && rules.playerMovement().landing().landingRollClearUsesCurrentYRadiusDelta();
            if (usesCurrentRadiusDelta) {
                int radiusDelta = oldYRadius - sprite.getStandYRadius();
                var gameState = sprite.currentGameStateOrNull();
                if (gameState != null && gameState.isReverseGravityActive()) {
                    radiusDelta = -radiusDelta;
                }
                int anglePlusQuarterTurn = ((sprite.getAngle() & 0xFF) + 0x40) & 0xFF;
                if ((anglePlusQuarterTurn & 0x80) != 0) {
                    radiusDelta = -radiusDelta;
                }
                sprite.setCentreYPreserveSubpixel((short) (nativeYBeforeRadiusChange + radiusDelta));
            } else {
                sprite.setY((short) (sprite.getY() - sprite.getRollHeightAdjustment()));
            }
        }
    }
}
