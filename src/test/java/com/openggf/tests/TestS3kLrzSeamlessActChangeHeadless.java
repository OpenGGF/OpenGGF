package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kLRZEvents;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Lava Reef seamless act change, {@code $900} to {@code $901}, end to end in production.
 *
 * <p>{@code Obj_Results} (sonic3k.asm:62615-62622) ends its sprite-creation routine with
 * {@code addq.b #2,routine(a0)} and then, only when {@code Apparent_act} is zero and
 * {@code Apparent_zone} is neither Angel Island nor Ice Cap, {@code st (Events_fg_5)}. Lava Reef
 * act 1 satisfies that gate, and the engine's shared results path already publishes it through
 * {@code signalActTransition()}.
 *
 * <p>{@code LRZ1_BackgroundEvent} stage 0 ({@code loc_56BD2}, sonic3k.asm:115274-115293) consumes
 * the word: it clears it, queues the act-2 secondary resources and PLC {@code $30}, and sets
 * {@code Events_routine_bg} to {@code $C} -- still leaving through {@code loc_56D16}, so that
 * frame draws normally. Stage {@code $C} ({@code loc_56CAA}, sonic3k.asm:115347-115374) then waits
 * on {@code Kos_modules_left} and, on one frame, writes {@code $901}, clears the level variables,
 * runs {@code Clear_Switches}, reloads the level, subtracts {@code $2C00} from both players, every
 * live object, the camera and its two X bounds, and returns {@code Events_routine_bg} to zero.
 *
 * <p>Both halves are asserted here because neither is safe alone: a stage 0 that advanced to
 * {@code $C} with no stage {@code $C} behind it would leave the act 1 background pointing at a
 * stage that never draws again.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzSeamlessActChangeHeadless {

    /** {@code move.w #$2C00,d0} (sonic3k.asm:115361). */
    private static final int REBASE_X = 0x2C00;
    /** {@code move.w #$C,(Events_routine_bg)} (sonic3k.asm:115292). */
    private static final int BG_STAGE_ACT_CHANGE = 0x0C;

    @AfterEach
    void cleanup() {
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @Test
    void theResultsSignalCarriesTheActChangeThroughToActTwo() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0)
                .startPosition((short) 0x2C00, (short) 0x0600)
                .startPositionIsCentre()
                .withSkippedZoneIntro()
                .build();
        AbstractPlayableSprite player = fixture.sprite();
        for (int i = 0; i < 30; i++) {
            fixture.stepFrame(false, false, false, false, false);
        }

        Sonic3kLevelEventManager events = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider());
        LrzZoneRuntimeState before =
                S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        assertEquals(0, before.actIndex(), "precondition: act 1");
        assertEquals(0, before.eventsFg5(), "precondition: Events_fg_5 is clear");

        // The ROM reaches this with the miniboss arena lock still on the camera: the measured
        // pair is ($2C00,$710), and loc_56CAA's word subtract takes those bounds to act 2's own.
        // Starting from a zero min would make the subtract underflow, which is not the ROM's case.
        fixture.camera().setX((short) 0x2C00);
        fixture.camera().setMinX((short) 0x2C00);
        fixture.camera().setMaxX((short) 0x2C00);
        int playerXBefore = player.getCentreX() & 0xFFFF;
        int cameraXBefore = fixture.camera().getX() & 0xFFFF;
        int minXBefore = fixture.camera().getMinX() & 0xFFFF;
        int maxXBefore = fixture.camera().getMaxX() & 0xFFFF;
        // Something the change must clear: Clear_Switches wipes the trigger array.
        Sonic3kLevelTriggerManager.setBit(0, 0);
        assertTrue(Sonic3kLevelTriggerManager.testBit(0, 0), "precondition: a trigger bit is set");

        // The production publication: Obj_Results' st (Events_fg_5) for a Lava Reef act 1 tally.
        events.signalActTransition();
        assertNotEquals(0,
                S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow()
                        .eventsFg5(),
                "setEventsFg5ForActTransition has no Lava Reef branch");

        boolean sawArmedStage = false;
        for (int frame = 0; frame < 240; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            LrzZoneRuntimeState now =
                    S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow();
            if (now.actIndex() == 1) {
                break;
            }
            if (now.backgroundRoutine() == BG_STAGE_ACT_CHANGE) {
                sawArmedStage = true;
                assertEquals(0, now.eventsFg5(),
                        "loc_56BD2 clears Events_fg_5 as it arms the change");
            }
        }

        LrzZoneRuntimeState after =
                S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        assertTrue(sawArmedStage,
                "loc_56BD2 must park Events_routine_bg on $C while the queued art lands");
        assertEquals(1, after.actIndex(),
                "loc_56CAA writes $901 into Current_zone_and_act (sonic3k.asm:115349)");
        assertEquals(Sonic3kZoneIds.ZONE_LRZ, after.zoneIndex(), "still Lava Reef");
        assertEquals(0, after.backgroundRoutine(),
                "clr.w (Events_routine_bg): stage 0 is LRZ2's from here (sonic3k.asm:115374)");
        // clr.b (LRZ_rocks_routine) (sonic3k.asm:115356) is not assertable after the fact: act 2's
        // own rock-sprite renderer re-arms the routine on the very frame the change lands, as the
        // ROM's does. What is assertable is that act 1's state did not survive the swap at all.
        assertNotSame(before, after, "loc_56CAA's Load_Level installs the act 2 state");
        assertTrue(!Sonic3kLevelTriggerManager.testBit(0, 0),
                "Clear_Switches wipes the trigger array (sonic3k.asm:115355, 104284-104291)");

        // sub.w d0,(Player_1+x_pos) (sonic3k.asm:115363). The exact word is asserted on the
        // camera below, which no terrain touches; the player's own x is read after the target
        // act's first pass, and $2C00 lands them within a few pixels of act 2's left edge where
        // the level's own boundary owns the final value. What this asserts is that the player was
        // carried out of act 1's coordinates at all.
        assertTrue(playerXBefore >= REBASE_X, "precondition: the player is in act 1 coordinates");
        assertTrue((player.getCentreX() & 0xFFFF) < 0x0200,
                "the player is in act 2's coordinates, not act 1's: x is "
                        + Integer.toHexString(player.getCentreX() & 0xFFFF));
        // sub.w d0,(Camera_X_pos) (sonic3k.asm:115366): like the player's own x, the value read
        // after the target act's first pass is the camera's follow of a player that has already
        // been clamped by act 2's left boundary. The two bounds below are the direct writes.
        assertTrue(cameraXBefore >= REBASE_X - 0x200, "precondition: the camera is in the arena");
        assertTrue((short) fixture.camera().getX() < 0x0200,
                "the camera is in act 2's coordinates: x is " + (short) fixture.camera().getX());
        String seen = " (minX=" + (short) fixture.camera().getMinX()
                + " maxX=" + (short) fixture.camera().getMaxX()
                + " camX=" + (short) fixture.camera().getX()
                + " px=" + (short) player.getCentreX() + ")";
        assertEquals((minXBefore - REBASE_X) & 0xFFFF, fixture.camera().getMinX() & 0xFFFF,
                "sub.w d0,(Camera_min_X_pos) (sonic3k.asm:115368)" + seen);
        assertEquals((maxXBefore - REBASE_X) & 0xFFFF, fixture.camera().getMaxX() & 0xFFFF,
                "sub.w d0,(Camera_max_X_pos) (sonic3k.asm:115369)" + seen);
    }

    /** {@code Sonic3kLRZEvents} owns the stage constant the two halves share. */
    @Test
    void theArmedStageIsTheRomsOwnIndexEntry() {
        assertEquals(BG_STAGE_ACT_CHANGE, Sonic3kLRZEvents.BG_STAGE_ACT_CHANGE,
                "LRZ1_BackgroundEvent_Index's fourth entry (sonic3k.asm:115264-115271)");
    }
}
