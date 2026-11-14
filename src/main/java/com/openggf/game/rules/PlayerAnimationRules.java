package com.openggf.game.rules;

@com.openggf.game.ModApi
public record PlayerAnimationRules(
        boolean extendedEdgeBalance,
        boolean singleFacingBalanceAnimationSet,
        boolean animationChangeClearsPush,
        boolean walkRunDelayLatchesRenderOrientation,
        boolean angledLandingPublishesWalk,
        boolean airLandingPublishesTiltAngles,
        TailsTailPushDetection tailsTailPushDetection,
        /**
         * In the un-rolled air mode a player that is not rising returns to Walk, unless a
         * wind tunnel or the bubble animation owns it. Sonic CD's {@code ObjSonic_MdAir}
         * opens with that write (scddisasm src/Level/_Objects/Sonic/Main.asm:713-721);
         * Sonic 1's {@code Obj01_MdJump} keeps the airborne animation. Only Sonic CD sets it.
         */
        boolean fallingAirStateRestoresWalk,
        /**
         * The single-state (Sonic 1 shape) edge balance keeps the facing and picks the
         * forwards animation when the player faces the edge, the backwards one (the
         * profile's third balance id) when it faces away. Sonic CD's
         * {@code .BalanceRight/.BalanceLeft} test {@code oFlags} bit 0 and write anim 6 or
         * {@code $32} (scddisasm src/Level/_Objects/Sonic/Main.asm:916-935); Sonic 1's
         * {@code Sonic_Balance} turns the player toward the edge. Only Sonic CD sets it.
         */
        boolean balanceKeepsFacing) {
}
