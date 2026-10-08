package com.openggf.tests;

import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.LrzSinkingRockObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jumping off {@code Obj_LRZSinkingRock} while it is still sinking.
 *
 * <p>The ROM runs Player 1 (object slot 0) before every placed object, so on the jump frame the
 * player's whole pass happens while the block still holds the previous frame's {@code y_pos}:
 * <ul>
 *   <li>{@code Sonic_Jump} (docs/skdisasm/sonic3k.asm:23288-23349) sets {@code Status_InAir},
 *       swaps {@code y_radius} {@code $13} -> {@code $E} and applies that as
 *       {@code sub.w d0,y_pos(a0)} at {@code loc_118AE} (:23347-23349), i.e. {@code y_pos + 5}.
 *       It does <b>not</b> clear {@code Status_OnObj} and it does not touch the block's own
 *       standing bit.</li>
 *   <li>The block then runs {@code loc_427E2} (:87916-87940): it advances {@code $2E(a0)} and
 *       writes the new {@code y_pos}, then calls {@code SolidObjectFull}. In
 *       {@code SolidObjectFull_1P} (:41021-41034) the object's own {@code d6} standing bit is
 *       still set and {@code Status_InAir} is now set, so the helper branches to
 *       {@code loc_1DC98}: it clears {@code Status_OnObj}, sets {@code Status_InAir}, clears
 *       {@code d6} and returns {@code d4 = 0}. {@code MvSonicOnPtfm} does not run, and neither
 *       does {@code loc_1E154}'s upward-velocity position lift (:41608-41637).</li>
 * </ul>
 *
 * <p>So the block's sink on the jump frame must not reach the player: the jump moves the player
 * down by exactly the five pixels of the radius swap, no more. The failing shape this pins is the
 * sink's {@code +1} being applied on top of it, which is what ended the cold act 1 route's exact
 * match at frame 637 (`player_y` 1322 against the fixture's 1321).
 *
 * <p>{@code d6} is per object: another solid's {@code SolidObjectFull} clears only its own
 * {@code a0} bit, never this block's, so the block's stale-rider branch must still be available
 * when the block's own checkpoint runs.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzSinkingRockJumpOffHeadless {

    /** {@code default_y_radius $13} -> jump {@code y_radius $E} at sonic3k.asm:23341-23349. */
    private static final int JUMP_RADIUS_DROP = 5;

    @AfterEach
    void cleanup() {
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @Test
    void theSinkOnTheJumpFrameIsNotCarriedIntoTheJump() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0)
                .startPosition((short) 0x04F3, (short) 0x0500)
                .startPositionIsCentre()
                .build();

        // Fall onto the block at ($4F8,$543) and ride it while $2E climbs.
        for (int i = 0; i < 25; i++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        AbstractPlayableSprite player = fixture.sprite();
        LrzSinkingRockObjectInstance rock = nearestRock(fixture, player);

        assertTrue(player.isOnObject(), "precondition: standing on the block");
        assertFalse(player.getAir(), "precondition: grounded");
        assertTrue(rock.angle() > 0 && rock.angle() < 0x40,
                "precondition: the block is still sinking, so the jump frame moves it");

        int angleBefore = rock.angle();
        int blockYBefore = rock.getCentreY();
        int playerYBefore = player.getCentreY();

        fixture.stepFrame(false, false, false, false, true);

        assertEquals(angleBefore + 1, rock.angle(),
                "loc_427F8: the standing bit from the previous frame still advances $2E");
        assertEquals(blockYBefore + 1, rock.getCentreY(),
                "precondition: the block really did sink a pixel on the jump frame");
        assertTrue(player.getAir(), "Sonic_Jump sets Status_InAir (sonic3k.asm:23327)");
        assertFalse(player.isOnObject(),
                "loc_1DC98 clears Status_OnObj when the block's checkpoint sees the rider airborne");
        assertEquals(playerYBefore + JUMP_RADIUS_DROP, player.getCentreY(),
                "loc_118AE's +5 only: loc_1DC98 returns d4=0 without MvSonicOnPtfm or loc_1E154's lift");
    }

    private static LrzSinkingRockObjectInstance nearestRock(HeadlessTestFixture fixture,
            AbstractPlayableSprite player) {
        LrzSinkingRockObjectInstance nearest = null;
        for (ObjectInstance o : fixture.runtime().getLevelManager().getObjectManager().getActiveObjects()) {
            if (o instanceof LrzSinkingRockObjectInstance rock
                    && (nearest == null
                        || Math.abs(rock.getCentreX() - player.getCentreX())
                            < Math.abs(nearest.getCentreX() - player.getCentreX()))) {
                nearest = rock;
            }
        }
        if (nearest == null) {
            throw new AssertionError("no Obj_LRZSinkingRock is live near the player");
        }
        return nearest;
    }
}
