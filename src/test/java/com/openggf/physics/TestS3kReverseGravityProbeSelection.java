package com.openggf.physics;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Step 2a-2: {@code sub_11FD6} and {@code sub_11FEE} swap which probe is the floor.
 *
 * <p>{@code sub_11FD6} (sonic3k.asm:24127-24137) is the wrapper every "check the floor"
 * site in the three characters' {@code DoLevelCollision} routines calls; with
 * {@code Reverse_gravity_flag} ($FFFFF7C6) set it runs {@code Sonic_CheckCeiling}
 * instead. {@code sub_11FEE} (:24141-24151) is its opposite. This asserts the selector
 * those two wrappers become, which is the branch the ROM states.
 *
 * <p><strong>Scope.</strong> This is a seam test, not the behavioural proof. It shows
 * the wrappers pick the opposite sensor array; it does not show an inverted player
 * landing on a real ceiling, which needs a controlled flat-ceiling fixture that does not
 * exist yet. See the Death Egg plan's slice 2 evidence log for why the two obvious
 * candidates did not serve: the Death Egg act 1 spawn stands on a solid object rather
 * than terrain, and at the Angel Island act 1 spawn the ground sensors' own stride
 * disagrees with {@code ObjectTerrainUtils.checkFloorDist} about whether the ledge under
 * the player is solid.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kReverseGravityProbeSelection {

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void uprightTheFloorWrapperScansTheGroundSensorsAndTheCeilingWrapperTheCeilingSensors() {
        AbstractPlayableSprite sprite = spriteWithFlag(false);
        CollisionSystem collision = GameServices.collision();

        assertSame(sprite.getGroundSensors(), collision.floorProbeSensors(sprite),
                "sub_11FD6 is Sonic_CheckFloor while the flag is clear");
        assertSame(sprite.getCeilingSensors(), collision.ceilingProbeSensors(sprite),
                "sub_11FEE is Sonic_CheckCeiling while the flag is clear");
    }

    @Test
    void reverseGravityExchangesTheTwoWrappers() {
        AbstractPlayableSprite sprite = spriteWithFlag(true);
        CollisionSystem collision = GameServices.collision();

        assertSame(sprite.getCeilingSensors(), collision.floorProbeSensors(sprite),
                "sub_11FD6 branches to Sonic_CheckCeiling with the flag set");
        assertSame(sprite.getGroundSensors(), collision.ceilingProbeSensors(sprite),
                "sub_11FEE branches to Sonic_CheckFloor with the flag set");
        assertNotSame(collision.floorProbeSensors(sprite), collision.ceilingProbeSensors(sprite),
                "the two wrappers must still be opposites, not both the same array");
    }

    private AbstractPlayableSprite spriteWithFlag(boolean reverseGravity) {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .build();
        GameServices.gameState().setReverseGravityActive(reverseGravity);
        return fixture.sprite();
    }
}
