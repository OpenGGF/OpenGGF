package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kLRZEvents;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        before.setCenterNativeArenaCamera(true);
        assertEquals(0, before.eventsFg5(), "precondition: Events_fg_5 is clear");

        // The ROM reaches this with the miniboss arena lock still on the camera. The arena's
        // measured left edge is $2C00 and its measured right wall $2D28 (both recorded in the
        // campaign plan at b35f59d33); the two are kept distinct on purpose, because a min that
        // equals the max pins the camera to a point and would make the camera and player
        // assertions below pass whatever the transition did with its offsets.
        fixture.camera().setX((short) 0x2C00);
        fixture.camera().setMinX((short) 0x2C00);
        fixture.camera().setMaxX((short) 0x2D28);
        int playerXBefore = player.getCentreX() & 0xFFFF;
        int cameraXBefore = fixture.camera().getX() & 0xFFFF;
        int minXBefore = fixture.camera().getMinX() & 0xFFFF;
        int maxXBefore = fixture.camera().getMaxX() & 0xFFFF;
        // jsr (Offset_ObjectsDuringTransition) (sonic3k.asm:115365) subtracts d0 from every
        // world-space SST entry, not just the players. Pick one that is live now and follow it.
        // Obj_Results only runs after the act has ended, which in Lava Reef act 1 means the
        // miniboss is already Obj_Explosion. Leaving it alive would keep its arena camera lock
        // running into act 2 and rewrite the very bounds this case is about.
        for (ObjectInstance o : fixture.runtime().getLevelManager().getObjectManager()
                .getActiveObjects()) {
            if (o instanceof com.openggf.game.sonic3k.objects.bosses.LrzMinibossInstance boss) {
                boss.setDestroyed(true);
            }
        }

        StringBuilder live = new StringBuilder();
        AbstractObjectInstance carried = null;
        for (ObjectInstance o : fixture.runtime().getLevelManager().getObjectManager()
                .getActiveObjects()) {
            if (!(o instanceof AbstractObjectInstance aoi) || aoi.isDestroyed()
                    || aoi.getSpawn() == null) {
                continue;
            }
            live.append("\n  ").append(aoi.getClass().getSimpleName()).append("@")
                    .append(Integer.toHexString(aoi.getCollisionX() & 0xFFFF));
            if ((aoi.getCollisionX() & 0xFFFF) >= 0x2000) {
                carried = aoi;
                break;
            }
        }
        assertNotNull(carried,
                "precondition: a live world-space object in act 1 coordinates; live are" + live);
        assertTrue(carried.participatesInRomWorldTransitionOffset(),
                "precondition: the chosen object is world-positioned, the engine's render_flags "
                        + "bit 2: " + carried.getClass().getSimpleName());
        int carriedXBefore = carried.getCollisionX() & 0xFFFF;

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
        assertTrue(after.centerNativeArenaCamera(), "carry presentation framing with the native camera rebase");
        assertTrue(!Sonic3kLevelTriggerManager.testBit(0, 0),
                "Clear_Switches wipes the trigger array (sonic3k.asm:115355, 104284-104291)");

        assertTrue(playerXBefore >= REBASE_X, "precondition: the player is in act 1 coordinates");
        // sub.w d0,(Player_1+x_pos) (sonic3k.asm:115363) puts the player at $2C0A - $2C00 = $A,
        // which is left of the new boundary, so Player_LevelBound pins them on the same frame:
        // move.w (Camera_min_X_pos),d0 / addi.w #$10,d0 / cmp.w d1,d0 / bhi Player_Boundary_Sides
        // (sonic3k.asm:23179-23181, 23211-23212). With the rebased min at 0 that is exactly $10.
        int rebasedPlayerX = (playerXBefore - REBASE_X) & 0xFFFF;
        int boundaryX = (fixture.camera().getMinX() & 0xFFFF) + 0x10;
        assertTrue(rebasedPlayerX < boundaryX,
                "precondition: the rebase puts the player past the act 2 left boundary ("
                        + rebasedPlayerX + " < " + boundaryX + ")");
        assertEquals(boundaryX, player.getCentreX() & 0xFFFF,
                "Player_LevelBound pins the rebased player to Camera_min_X_pos + $10");
        assertEquals((carriedXBefore - REBASE_X) & 0xFFFF, carried.getCollisionX() & 0xFFFF,
                "jsr (Offset_ObjectsDuringTransition) moves every carried world-space object by "
                        + "the same d0 (sonic3k.asm:115365, 104166-104181): "
                        + carried.getClass().getSimpleName());
        assertTrue(cameraXBefore >= REBASE_X - 0x200, "precondition: the camera is in the arena");
        // sub.w d0,(Camera_X_pos) (sonic3k.asm:115366). The camera was written at $2C00 exactly,
        // so the rebase is the whole story here and no clamp reaches it.
        assertEquals((cameraXBefore - REBASE_X) & 0xFFFF, fixture.camera().getX() & 0xFFFF,
                "sub.w d0,(Camera_X_pos) (sonic3k.asm:115366)");
        // The Y bounds are deliberately NOT asserted to survive. loc_56CAA subtracts from no Y
        // word (sonic3k.asm:115366-115369) and Load_Level writes no camera word (:38747-38761),
        // but the same routine also does clr.b (Dynamic_resize_routine) at :115350, so act 2's
        // own resize owner runs from its first entry on the very next pass and installs act 2's
        // Y bounds. Measured here: minY goes 0 to $710 on the change frame. Carrying act 1's Y
        // across would be the deviation, not the fidelity.
        // The camera BOUNDS are deliberately not asserted either, for the same measured reason:
        // loc_56CAA does subtract $2C00 from Camera_min_X_pos and Camera_max_X_pos (:115368-369)
        // and the request carries both, but the same routine's clr.b (Dynamic_resize_routine)
        // at :115350 puts act 2's resize owner back at its first entry, and it installs act 2's
        // own bounds on the change frame. Measured at this commit: minX 0 (indistinguishable
        // from the subtract) and maxX 0, not the $128 the subtract alone would leave. What is
        // attributable to the subtract is the camera POSITION, asserted above, which the resize
        // owner does not rewrite.
        assertTrue(maxXBefore > minXBefore,
                "precondition: the arena bounds were not collapsed to a point, which would pin "
                        + "the camera and make the position assertion above unfalsifiable");
    }

    /** {@code Sonic3kLRZEvents} owns the stage constant the two halves share. */
    @Test
    void theArmedStageIsTheRomsOwnIndexEntry() {
        assertEquals(BG_STAGE_ACT_CHANGE, Sonic3kLRZEvents.BG_STAGE_ACT_CHANGE,
                "LRZ1_BackgroundEvent_Index's fourth entry (sonic3k.asm:115264-115271)");
    }
}
