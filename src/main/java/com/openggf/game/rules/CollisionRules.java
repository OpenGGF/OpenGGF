package com.openggf.game.rules;

import com.openggf.game.CollisionModel;
import com.openggf.game.PhysicsFeatureSet;

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

    public static CollisionRules fromLegacy(PhysicsFeatureSet fs) {
        return new CollisionRules(
                fs.collisionModel(),
                fs.groundWallCollisionEnabled(),
                fs.groundWallPushRequiresFacingIntoWall(),
                fs.repeatedObjectRideGroundWallResponseDeferred(),
                fs.topSolidLandingAllowsZeroDist(),
                fs.airBottomSolidHitClearsGroundSpeed(),
                fs.airRightWallHitContinuesIntoCeilingSeparation(),
                fs.airLeftWallHitContinuesIntoCeilingSeparation(),
                fs.fullSolidBottomOverlapUsesCurrentYRadiusOnly(),
                fs.solidObjectOffscreenGate(),
                fs.solidObjectRequiresSidekickOnScreen(),
                fs.sidekickPushBypassUsesGraceStatus(),
                fs.sidekickSuppressesFastLeaderTinyFollowNudge(),
                fs.sidekickClearsStalePushVelocityBeforeGroundMove(),
                fs.solidObjectTopBranchAlwaysLiftsOnUpwardVelocity(),
                fs.rightWallDeepProbePreservesPenetration(),
                fs.solidObjectBarelyPokingResolvesAsSide(),
                fs.solidObjectKeepsOnObjWhenJumpedOffSameFrame(),
                fs.advanceWaterLevelBeforePlayerPhysics());
    }
}
