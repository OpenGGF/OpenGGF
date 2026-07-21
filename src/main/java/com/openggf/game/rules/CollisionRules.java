package com.openggf.game.rules;

import com.openggf.game.CollisionModel;

@com.openggf.game.ModApi
public record CollisionRules(
        CollisionModel collisionModel,
        boolean groundWallCollisionEnabled,
        boolean groundWallPushRequiresFacingIntoWall,
        boolean repeatedObjectRideGroundWallResponseDeferred,
        boolean topSolidLandingAllowsZeroDist,
        boolean airBottomSolidHitClearsGroundSpeed,
        boolean airRightWallHitContinuesIntoCeilingSeparation,
        boolean airLeftWallHitContinuesIntoCeilingSeparation,
        AirCollisionRules air,
        boolean fullSolidBottomOverlapUsesCurrentYRadiusOnly,
        boolean solidObjectOffscreenGate,
        boolean solidObjectRequiresSidekickOnScreen,
        boolean sidekickPushBypassUsesGraceStatus,
        boolean sidekickSuppressesFastLeaderTinyFollowNudge,
        boolean sidekickClearsStalePushVelocityBeforeGroundMove,
        boolean solidObjectTopBranchAlwaysLiftsOnUpwardVelocity,
        boolean rightWallDeepProbePreservesPenetration,
        boolean solidObjectBarelyPokingResolvesAsSide,
        boolean solidObjectKeepsOnObjWhenJumpedOffSameFrame,
        boolean advanceWaterLevelBeforePlayerPhysics,
        int defaultCollisionLayoutYMask) {
    public CollisionRules {
        airBottomSolidHitClearsGroundSpeed = air.bottomSolidHitClearsGroundSpeed();
        airRightWallHitContinuesIntoCeilingSeparation =
                air.rightWallHitContinuesIntoCeilingSeparation();
        airLeftWallHitContinuesIntoCeilingSeparation =
                air.leftWallHitContinuesIntoCeilingSeparation();
    }

    /** Constructor for the post-2.4 nested air-rule shape. */
    public CollisionRules(CollisionModel collisionModel,
            boolean groundWallCollisionEnabled, boolean groundWallPushRequiresFacingIntoWall,
            boolean repeatedObjectRideGroundWallResponseDeferred, boolean topSolidLandingAllowsZeroDist,
            AirCollisionRules air, boolean fullSolidBottomOverlapUsesCurrentYRadiusOnly,
            boolean solidObjectOffscreenGate, boolean solidObjectRequiresSidekickOnScreen,
            boolean sidekickPushBypassUsesGraceStatus, boolean sidekickSuppressesFastLeaderTinyFollowNudge,
            boolean sidekickClearsStalePushVelocityBeforeGroundMove,
            boolean solidObjectTopBranchAlwaysLiftsOnUpwardVelocity,
            boolean rightWallDeepProbePreservesPenetration, boolean solidObjectBarelyPokingResolvesAsSide,
            boolean solidObjectKeepsOnObjWhenJumpedOffSameFrame,
            boolean advanceWaterLevelBeforePlayerPhysics, int defaultCollisionLayoutYMask) {
        this(collisionModel, groundWallCollisionEnabled, groundWallPushRequiresFacingIntoWall,
                repeatedObjectRideGroundWallResponseDeferred, topSolidLandingAllowsZeroDist,
                air.bottomSolidHitClearsGroundSpeed(),
                air.rightWallHitContinuesIntoCeilingSeparation(),
                air.leftWallHitContinuesIntoCeilingSeparation(), air,
                fullSolidBottomOverlapUsesCurrentYRadiusOnly, solidObjectOffscreenGate,
                solidObjectRequiresSidekickOnScreen, sidekickPushBypassUsesGraceStatus,
                sidekickSuppressesFastLeaderTinyFollowNudge,
                sidekickClearsStalePushVelocityBeforeGroundMove,
                solidObjectTopBranchAlwaysLiftsOnUpwardVelocity,
                rightWallDeepProbePreservesPenetration, solidObjectBarelyPokingResolvesAsSide,
                solidObjectKeepsOnObjWhenJumpedOffSameFrame,
                advanceWaterLevelBeforePlayerPhysics, defaultCollisionLayoutYMask);
    }
    /** Compatibility constructor for API 1.1 rules, which used the original 11-bit layout mask. */
    public CollisionRules(CollisionModel collisionModel,
                          boolean groundWallCollisionEnabled,
                          boolean groundWallPushRequiresFacingIntoWall,
                          boolean repeatedObjectRideGroundWallResponseDeferred,
                          boolean topSolidLandingAllowsZeroDist,
                          boolean airBottomSolidHitClearsGroundSpeed,
                          boolean airRightWallHitContinuesIntoCeilingSeparation,
                          boolean airLeftWallHitContinuesIntoCeilingSeparation,
                          boolean fullSolidBottomOverlapUsesCurrentYRadiusOnly,
                          boolean solidObjectOffscreenGate,
                          boolean solidObjectRequiresSidekickOnScreen,
                          boolean sidekickPushBypassUsesGraceStatus,
                          boolean sidekickSuppressesFastLeaderTinyFollowNudge,
                          boolean sidekickClearsStalePushVelocityBeforeGroundMove,
                          boolean solidObjectTopBranchAlwaysLiftsOnUpwardVelocity,
                          boolean rightWallDeepProbePreservesPenetration,
                          boolean solidObjectBarelyPokingResolvesAsSide,
                          boolean solidObjectKeepsOnObjWhenJumpedOffSameFrame,
                          boolean advanceWaterLevelBeforePlayerPhysics) {
        this(collisionModel, groundWallCollisionEnabled, groundWallPushRequiresFacingIntoWall,
                repeatedObjectRideGroundWallResponseDeferred, topSolidLandingAllowsZeroDist,
                airBottomSolidHitClearsGroundSpeed,
                airRightWallHitContinuesIntoCeilingSeparation,
                airLeftWallHitContinuesIntoCeilingSeparation,
                new AirCollisionRules(airBottomSolidHitClearsGroundSpeed, false,
                        airRightWallHitContinuesIntoCeilingSeparation,
                        airLeftWallHitContinuesIntoCeilingSeparation, false),
                fullSolidBottomOverlapUsesCurrentYRadiusOnly,
                solidObjectOffscreenGate, solidObjectRequiresSidekickOnScreen, sidekickPushBypassUsesGraceStatus,
                sidekickSuppressesFastLeaderTinyFollowNudge, sidekickClearsStalePushVelocityBeforeGroundMove,
                solidObjectTopBranchAlwaysLiftsOnUpwardVelocity, rightWallDeepProbePreservesPenetration,
                solidObjectBarelyPokingResolvesAsSide, solidObjectKeepsOnObjWhenJumpedOffSameFrame,
                advanceWaterLevelBeforePlayerPhysics, 0x07FF);
    }

    /** Binary-compatible constructor for the Mod API 2.4 rule shape. */
    public CollisionRules(CollisionModel collisionModel,
            boolean groundWallCollisionEnabled, boolean groundWallPushRequiresFacingIntoWall,
            boolean repeatedObjectRideGroundWallResponseDeferred, boolean topSolidLandingAllowsZeroDist,
            boolean airBottomSolidHitClearsGroundSpeed,
            boolean airRightWallHitContinuesIntoCeilingSeparation,
            boolean airLeftWallHitContinuesIntoCeilingSeparation,
            boolean fullSolidBottomOverlapUsesCurrentYRadiusOnly,
            boolean solidObjectOffscreenGate, boolean solidObjectRequiresSidekickOnScreen,
            boolean sidekickPushBypassUsesGraceStatus, boolean sidekickSuppressesFastLeaderTinyFollowNudge,
            boolean sidekickClearsStalePushVelocityBeforeGroundMove,
            boolean solidObjectTopBranchAlwaysLiftsOnUpwardVelocity,
            boolean rightWallDeepProbePreservesPenetration, boolean solidObjectBarelyPokingResolvesAsSide,
            boolean solidObjectKeepsOnObjWhenJumpedOffSameFrame,
            boolean advanceWaterLevelBeforePlayerPhysics, int defaultCollisionLayoutYMask) {
        this(collisionModel, groundWallCollisionEnabled, groundWallPushRequiresFacingIntoWall,
                repeatedObjectRideGroundWallResponseDeferred, topSolidLandingAllowsZeroDist,
                airBottomSolidHitClearsGroundSpeed,
                airRightWallHitContinuesIntoCeilingSeparation,
                airLeftWallHitContinuesIntoCeilingSeparation,
                new AirCollisionRules(airBottomSolidHitClearsGroundSpeed, false,
                        airRightWallHitContinuesIntoCeilingSeparation,
                        airLeftWallHitContinuesIntoCeilingSeparation, false),
                fullSolidBottomOverlapUsesCurrentYRadiusOnly, solidObjectOffscreenGate,
                solidObjectRequiresSidekickOnScreen, sidekickPushBypassUsesGraceStatus,
                sidekickSuppressesFastLeaderTinyFollowNudge,
                sidekickClearsStalePushVelocityBeforeGroundMove,
                solidObjectTopBranchAlwaysLiftsOnUpwardVelocity,
                rightWallDeepProbePreservesPenetration, solidObjectBarelyPokingResolvesAsSide,
                solidObjectKeepsOnObjWhenJumpedOffSameFrame,
                advanceWaterLevelBeforePlayerPhysics, defaultCollisionLayoutYMask);
    }
}
