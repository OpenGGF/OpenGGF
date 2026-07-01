package com.openggf.game.rules;

import com.openggf.game.PhysicsFeatureSet;

public record SidekickCpuRules(
        int sidekickFollowSnapThreshold,
        int sidekickDespawnX,
        int sidekickFollowLeadOffset,
        boolean sidekickFollowNudgeBlockedByObjectControlBit0,
        boolean sidekickDelayedJumpPressUsesHistoryEdge,
        boolean sidekickPanicTreatsPinballModeAsSpindashFlag,
        boolean sidekickSpawningRequiresGroundedLeader,
        int sidekickFlyLandStatusBlockerMask,
        boolean sidekickFlyLandRequiresLeaderAlive,
        int sidekickCatchUpYOffset,
        int sidekickFlightAutoLandFrames,
        int sidekickFlightMaxXStep,
        int sidekickFlightYStep,
        int sidekickFlightLeadXOffset,
        int sidekickFlightLeadSuppressGSpeed,
        boolean sidekickRespawnEntersCatchUpFlight,
        boolean sidekickCpuUsesLevelFrameCounter,
        boolean sidekickDeathUsesDeferredDespawn) {

    public static SidekickCpuRules fromLegacy(PhysicsFeatureSet fs) {
        return new SidekickCpuRules(
                fs.sidekickFollowSnapThreshold(),
                fs.sidekickDespawnX(),
                fs.sidekickFollowLeadOffset(),
                fs.sidekickFollowNudgeBlockedByObjectControlBit0(),
                fs.sidekickDelayedJumpPressUsesHistoryEdge(),
                fs.sidekickPanicTreatsPinballModeAsSpindashFlag(),
                fs.sidekickSpawningRequiresGroundedLeader(),
                fs.sidekickFlyLandStatusBlockerMask(),
                fs.sidekickFlyLandRequiresLeaderAlive(),
                fs.sidekickCatchUpYOffset(),
                fs.sidekickFlightAutoLandFrames(),
                fs.sidekickFlightMaxXStep(),
                fs.sidekickFlightYStep(),
                fs.sidekickFlightLeadXOffset(),
                fs.sidekickFlightLeadSuppressGSpeed(),
                fs.sidekickRespawnEntersCatchUpFlight(),
                fs.sidekickCpuUsesLevelFrameCounter(),
                fs.sidekickDeathUsesDeferredDespawn());
    }
}
