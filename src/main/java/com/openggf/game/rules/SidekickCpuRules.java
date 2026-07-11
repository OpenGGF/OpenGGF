package com.openggf.game.rules;

@com.openggf.game.ModApi
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
}
