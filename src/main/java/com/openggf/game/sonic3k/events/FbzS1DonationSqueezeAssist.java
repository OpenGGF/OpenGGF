package com.openggf.game.sonic3k.events;

import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.rules.GameRules;
import com.openggf.game.sonic3k.objects.FbzMovingSqueezeTraversal;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState.S1DonationSqueezeAssistState;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Objects;

/**
 * S1-donation-only compatibility for FBZ2's rising-car squeeze.
 *
 * <p>The locked-on route releases a {@code $0800} rightward spindash while
 * riding the authored elevator car beneath a normal Obj28 full solid. Sonic 1
 * donation deliberately removes spindash. The runtime adapter therefore lets
 * an ordinary rightward roll receive the same one-shot speed floor, but only
 * when exact live object geometry proves that the native floor clears the
 * squeeze and the donor's current speed does not.
 */
final class FbzS1DonationSqueezeAssist {
    static final int NATIVE_SPINDASH_RELEASE_SPEED = 0x0800;

    private FbzS1DonationSqueezeAssist() {
    }

    /** Applies the S1-only rule to P1 after the live object pass. */
    static void applyToMainPlayer(AbstractPlayableSprite player,
                                  FbzZoneRuntimeState runtime,
                                  ObjectManager objects) {
        if (player == null || runtime == null || objects == null
                || player.getDead() || player.isHurt()) {
            return;
        }
        GameRules rules = player.getGameRules();
        if (rules == null || rules.playerCapability() == null) return;

        var episode = FbzMovingSqueezeTraversal.findEpisode(objects, player);
        boolean exactEpisodeActive = episode.isPresent();
        boolean beforeLaunchFrontier = episode
                .map(value -> FbzMovingSqueezeTraversal.beforeLaunchFrontier(value, player))
                .orElse(false);
        boolean currentProjectionSafe = episode
                .filter(value -> player.getGSpeed() > 0)
                .map(value -> FbzMovingSqueezeTraversal
                        .project(value, player, player.getGSpeed()).clears())
                .orElse(false);
        boolean nativeFloorProjectionSafe = episode
                .map(value -> FbzMovingSqueezeTraversal.project(value, player,
                        NATIVE_SPINDASH_RELEASE_SPEED).clears())
                .orElse(false);
        boolean ordinaryRightDownRolling = !player.getAir()
                && player.getRolling() && !player.getSpindash()
                && player.isRightPressed() && player.isDownPressed();
        int currentSpeed = player.getGSpeed();
        Decision decision = resolve(runtime.s1DonationSqueezeAssistState(),
                CrossGameFeatureProvider.isActive(),
                rules.playerCapability().spindashEnabled(),
                exactEpisodeActive, ordinaryRightDownRolling,
                beforeLaunchFrontier, currentProjectionSafe,
                nativeFloorProjectionSafe, currentSpeed);
        if (decision.nextState() != runtime.s1DonationSqueezeAssistState()) {
            runtime.setS1DonationSqueezeAssistState(decision.nextState());
        }
        if (decision.groundSpeed() != currentSpeed) {
            player.setGSpeed((short) decision.groundSpeed());
        }
    }

    static boolean isFullSolidGapSafe(int gap, int defaultYRadius,
                                      int currentYRadius) {
        // Locked-on SolidObject_cont loc_1DFD6: the +4 overlap bias means
        // contact remains live while gap < default_y_radius+y_radius-4.
        return gap >= defaultYRadius + currentYRadius - 4;
    }

    static Decision resolve(S1DonationSqueezeAssistState state,
                            boolean donationActive, boolean spindashEnabled,
                            boolean exactEpisodeActive,
                            boolean ordinaryRightDownRolling,
                            boolean beforeLaunchFrontier,
                            boolean currentProjectionSafe,
                            boolean nativeFloorProjectionSafe,
                            int groundSpeed) {
        Objects.requireNonNull(state, "state");
        if (state == S1DonationSqueezeAssistState.CONSUMED) {
            return new Decision(groundSpeed, exactEpisodeActive
                    ? S1DonationSqueezeAssistState.CONSUMED
                    : S1DonationSqueezeAssistState.ARMED);
        }
        boolean eligible = donationActive && !spindashEnabled
                && exactEpisodeActive && ordinaryRightDownRolling
                && beforeLaunchFrontier
                && !currentProjectionSafe && nativeFloorProjectionSafe;
        if (!eligible || groundSpeed >= NATIVE_SPINDASH_RELEASE_SPEED) {
            return new Decision(groundSpeed, S1DonationSqueezeAssistState.ARMED);
        }
        return new Decision(NATIVE_SPINDASH_RELEASE_SPEED,
                S1DonationSqueezeAssistState.CONSUMED);
    }

    record Decision(int groundSpeed, S1DonationSqueezeAssistState nextState) {
        Decision {
            Objects.requireNonNull(nextState, "nextState");
        }
    }
}
