package com.openggf.game.rules;

import com.openggf.game.DonorCapabilities;

/**
 * The only public cross-game policy composer.
 *
 * <p>It accepts whole host and donor policies plus the donor's capability
 * declaration, and never a category record, so a caller cannot build a mixture
 * the donation contract does not list (for example a donor's collision or camera
 * rules under a host game). Every rule group except the donated
 * {@link PlayerCapabilityRules} fields stays the host's object.
 */
public final class CrossGamePolicyComposer {

    private CrossGamePolicyComposer() {
    }

    /**
     * Returns {@code host} with only the {@code capabilities}-listed player
     * capabilities taken from {@code donor}; returns {@code host} unchanged when
     * the donor declares no capabilities.
     */
    public static GamePolicies donatePlayerCapabilities(GamePolicies host, GamePolicies donor,
            DonorCapabilities capabilities) {
        if (host == null) {
            throw new IllegalArgumentException("Host GamePolicies are required");
        }
        if (donor == null) {
            throw new IllegalArgumentException("Donor GamePolicies are required");
        }
        if (capabilities == null) {
            return host;
        }
        return new GamePolicies(CrossGameRuleComposer.compose(host.rules(), donor.rules(), capabilities));
    }
}
