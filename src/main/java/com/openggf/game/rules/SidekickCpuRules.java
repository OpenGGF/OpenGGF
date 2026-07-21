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
        boolean sidekickDeathUsesDeferredDespawn,
        boolean sidekickHurtRestoresRadiiWithoutRoll) {
    /** Constructor for the post-2.4 rule shape after the history-edge gate retired. */
    public SidekickCpuRules(int sidekickFollowSnapThreshold, int sidekickDespawnX,
            int sidekickFollowLeadOffset, boolean sidekickFollowNudgeBlockedByObjectControlBit0,
            boolean sidekickPanicTreatsPinballModeAsSpindashFlag,
            boolean sidekickSpawningRequiresGroundedLeader, int sidekickFlyLandStatusBlockerMask,
            boolean sidekickFlyLandRequiresLeaderAlive, int sidekickCatchUpYOffset,
            int sidekickFlightAutoLandFrames, int sidekickFlightMaxXStep, int sidekickFlightYStep,
            int sidekickFlightLeadXOffset, int sidekickFlightLeadSuppressGSpeed,
            boolean sidekickRespawnEntersCatchUpFlight, boolean sidekickCpuUsesLevelFrameCounter,
            boolean sidekickDeathUsesDeferredDespawn, boolean sidekickHurtRestoresRadiiWithoutRoll) {
        this(sidekickFollowSnapThreshold, sidekickDespawnX, sidekickFollowLeadOffset,
                sidekickFollowNudgeBlockedByObjectControlBit0, false,
                sidekickPanicTreatsPinballModeAsSpindashFlag,
                sidekickSpawningRequiresGroundedLeader, sidekickFlyLandStatusBlockerMask,
                sidekickFlyLandRequiresLeaderAlive, sidekickCatchUpYOffset,
                sidekickFlightAutoLandFrames, sidekickFlightMaxXStep, sidekickFlightYStep,
                sidekickFlightLeadXOffset, sidekickFlightLeadSuppressGSpeed,
                sidekickRespawnEntersCatchUpFlight, sidekickCpuUsesLevelFrameCounter,
                sidekickDeathUsesDeferredDespawn, sidekickHurtRestoresRadiiWithoutRoll);
    }
}
