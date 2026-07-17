package com.openggf.game.sonic3k.events;

import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.rules.GameRules;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState.S1DonationUpperLoopAssistState;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Objects;

/**
 * S1-donation-only traversal assist for FBZ2's upper loop.
 *
 * <p>The complete-run route releases a {@code -$0800} leftward spindash at
 * {@code $0A4D/$0769}. Sonic 1 donation deliberately removes spindash, so an
 * ordinary left run through that same authored approach receives the
 * equivalent one-shot speed floor. This state is independent of the later
 * lower-loop assist because the route requires both releases.
 */
final class FbzS1DonationUpperLoopAssist {
    private static final int APPROACH_MIN_X = 0x0A40;
    private static final int APPROACH_MAX_X = 0x0A70;
    private static final int APPROACH_MIN_Y = 0x0740;
    private static final int APPROACH_MAX_Y = 0x0780;
    private static final int REARM_MARGIN = 0x20;
    private static final int NATIVE_SPINDASH_RELEASE_SPEED = -0x0800;

    private FbzS1DonationUpperLoopAssist() {
    }

    /** Applies the compatibility rule to P1 only; the zone event owns authority. */
    static void applyToMainPlayer(AbstractPlayableSprite player, FbzZoneRuntimeState runtime) {
        if (player == null || runtime == null || player.getDead() || player.isHurt()) {
            return;
        }
        GameRules rules = player.getGameRules();
        if (rules == null || rules.playerCapability() == null) {
            return;
        }
        int currentSpeed = player.getGSpeed();
        Decision decision = resolve(
                runtime.s1DonationUpperLoopAssistState(),
                CrossGameFeatureProvider.isActive(),
                rules.playerCapability().spindashEnabled(),
                player.getCentreX() & 0xFFFF, player.getCentreY() & 0xFFFF,
                player.getAir(), player.isLeftPressed(), currentSpeed);
        if (decision.nextState() != runtime.s1DonationUpperLoopAssistState()) {
            runtime.setS1DonationUpperLoopAssistState(decision.nextState());
        }
        if (decision.groundSpeed() != currentSpeed) {
            player.setGSpeed((short) decision.groundSpeed());
        }
    }

    static Decision resolve(S1DonationUpperLoopAssistState state,
                            boolean donationActive, boolean spindashEnabled,
                            int playerX, int playerY,
                            boolean airborne, boolean leftPressed, int groundSpeed) {
        Objects.requireNonNull(state, "state");
        if (state == S1DonationUpperLoopAssistState.CONSUMED) {
            boolean insideRearmEnvelope = playerX >= APPROACH_MIN_X - REARM_MARGIN
                    && playerX <= APPROACH_MAX_X + REARM_MARGIN
                    && playerY >= APPROACH_MIN_Y - REARM_MARGIN
                    && playerY <= APPROACH_MAX_Y + REARM_MARGIN;
            return new Decision(groundSpeed, insideRearmEnvelope
                    ? S1DonationUpperLoopAssistState.CONSUMED
                    : S1DonationUpperLoopAssistState.ARMED);
        }
        if (!isEligibleApproach(donationActive, spindashEnabled,
                playerX, playerY, airborne, leftPressed, groundSpeed)) {
            return new Decision(groundSpeed, S1DonationUpperLoopAssistState.ARMED);
        }
        int resolvedSpeed = groundSpeed <= NATIVE_SPINDASH_RELEASE_SPEED
                ? groundSpeed : NATIVE_SPINDASH_RELEASE_SPEED;
        return new Decision(resolvedSpeed, S1DonationUpperLoopAssistState.CONSUMED);
    }

    static int resolveGroundSpeed(boolean donationActive, boolean spindashEnabled,
                                  int playerX, int playerY,
                                  boolean airborne, boolean leftPressed, int groundSpeed) {
        if (!isEligibleApproach(donationActive, spindashEnabled,
                playerX, playerY, airborne, leftPressed, groundSpeed)
                || groundSpeed <= NATIVE_SPINDASH_RELEASE_SPEED) {
            return groundSpeed;
        }
        return NATIVE_SPINDASH_RELEASE_SPEED;
    }

    private static boolean isEligibleApproach(boolean donationActive, boolean spindashEnabled,
                                              int playerX, int playerY,
                                              boolean airborne, boolean leftPressed,
                                              int groundSpeed) {
        return donationActive && !spindashEnabled
                && !airborne && leftPressed
                && playerX >= APPROACH_MIN_X && playerX <= APPROACH_MAX_X
                && playerY >= APPROACH_MIN_Y && playerY <= APPROACH_MAX_Y
                // S1 donation compatibility: this is explicitly run-to-activate.
                // The exact authored envelope supplies the spatial authority;
                // any genuine leftward ground motion supplies the capability
                // replacement without encoding a route-tuned speed cutoff.
                && groundSpeed < 0;
    }

    record Decision(int groundSpeed, S1DonationUpperLoopAssistState nextState) {
        Decision {
            Objects.requireNonNull(nextState, "nextState");
        }
    }
}
