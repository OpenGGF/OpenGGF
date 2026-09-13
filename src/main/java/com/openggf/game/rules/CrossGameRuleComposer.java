package com.openggf.game.rules;

import com.openggf.game.DonorCapabilities;

/**
 * Composes typed cross-game rules by keeping runtime behavior owned by the host
 * game while importing only explicitly donated player capabilities.
 */
public final class CrossGameRuleComposer {

    private CrossGameRuleComposer() {
    }

    /**
     * Copies {@code base} with its player-movement and object-interaction rules
     * replaced. Game patches use this to express shipped rule differences
     * (for example the lock-on program's landing form) without positional
     * construction outside the owned rule factories.
     */
    public static GameRules withPlayerRules(GameRules base, PlayerMovementRules playerMovement,
            ObjectInteractionRules objectInteraction) {
        if (base == null) {
            throw new IllegalArgumentException("Base GameRules are required");
        }
        if (playerMovement == null || objectInteraction == null) {
            throw new IllegalArgumentException("Replacement rules are required");
        }
        return new GameRules(
                playerMovement,
                base.playerCapability(),
                base.collision(),
                base.playerAnimation(),
                base.camera(),
                base.ring(),
                objectInteraction,
                base.sidekickCpu(),
                base.powerUp(),
                base.drowningBubble(),
                base.dynamicArtDmaService());
    }

    public static GameRules compose(GameRules host, GameRules donor, DonorCapabilities donorCapabilities) {
        if (host == null) {
            throw new IllegalArgumentException("Host GameRules are required");
        }
        if (donor == null) {
            throw new IllegalArgumentException("Donor GameRules are required");
        }
        if (donorCapabilities == null) {
            return host;
        }

        PlayerCapabilityRules hostCapability = host.playerCapability();
        PlayerCapabilityRules donorCapability = donor.playerCapability();
        short[] spindashSpeedTable = donorCapabilities.hasSpindash()
                ? donorCapability.spindashSpeedTable()
                : null;
        PlayerCapabilityRules hybridCapability = new PlayerCapabilityRules(
                donorCapabilities.hasSpindash(),
                spindashSpeedTable,
                donorCapabilities.hasElementalShields(),
                donorCapabilities.hasInstaShield(),
                donorCapabilities.hasTailsFlight(),
                hostCapability.jumpRepressClearsRollJumpBeforeAbility(),
                donorCapabilities.hasElementalShields(),
                hostCapability.superSpindashSpeedTable());

        return new GameRules(
                host.playerMovement(),
                hybridCapability,
                host.collision(),
                host.playerAnimation(),
                host.camera(),
                host.ring(),
                host.objectInteraction(),
                host.sidekickCpu(),
                host.powerUp(),
                host.drowningBubble(),
                host.dynamicArtDmaService());
    }
}
