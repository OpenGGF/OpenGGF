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
}
