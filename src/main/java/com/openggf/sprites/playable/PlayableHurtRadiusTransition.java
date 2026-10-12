package com.openggf.sprites.playable;

import com.openggf.game.rules.GameRules;

final class PlayableHurtRadiusTransition {
    private PlayableHurtRadiusTransition() {
    }

    static void apply(AbstractPlayableSprite sprite) {
        apply(sprite, false);
    }

    /** KillSonic/KillCharacter use the floor reset, not the sidekick hurt policy. */
    static void applyDeath(AbstractPlayableSprite sprite) {
        apply(sprite, true);
    }

    private static void apply(AbstractPlayableSprite sprite, boolean death) {
        boolean wasRolling = sprite.getRolling();
        int nativeXBeforeRadiusChange = sprite.getCentreX();
        int nativeYBeforeRadiusChange = sprite.getCentreY();
        int oldYRadius = sprite.getYRadius();
        sprite.setRolling(false);
        if (wasRolling) {
            sprite.setCentreXPreserveSubpixel((short) nativeXBeforeRadiusChange);
        }
        GameRules rules = sprite.getGameRules();
        boolean usesCurrentRadiusDelta = rules != null
                && rules.playerMovement() != null
                && rules.playerMovement().landing().landingRollClearUsesCurrentYRadiusDelta();
        boolean restoresSplitSidekickRadii = !(sprite instanceof Tails)
                || rules == null || rules.sidekickCpu() == null
                || rules.sidekickCpu().sidekickHurtRestoresRadiiWithoutRoll();
        // S1/S2 reset radii only in the ball branch. S3K Player_TouchFloor
        // restores default radii before that test, including Tails and Knuckles.
        if (death ? wasRolling || usesCurrentRadiusDelta : restoresSplitSidekickRadii) {
            sprite.applyStandingRadii(false);
        }
        if (wasRolling) {
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
