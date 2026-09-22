package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CheckpointState;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.SSZHPZTeleporterObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicActEndObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicTrailChild;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 7: the {@code $79} pad at {@code ($1A40,$670)} and the entry of {@code Obj_SSZEndBoss}.
 *
 * <p>Every expectation is a literal from {@code loc_45A84}, {@code loc_45AB0}, {@code loc_7B2DC}
 * or {@code loc_7B308}, written out rather than read back from the production constant it is
 * checking, so a change to the constant fails here instead of agreeing with itself.
 *
 * <p><b>Declared setup.</b> The final arena's own lock wants {@code Camera_X >= $19A0} and the
 * leader below {@code $680}, which a checkpoint restart at the pad's own placement reaches; no
 * cold route gets here yet.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszMechaSpawnHeadless {

    /** The {@code $79:$00} placement slice 7 owns. */
    private static final int PAD_X = 0x1A40;
    private static final int PAD_Y = 0x0670;

    /** {@code sub_575EA}: {@code move.w #$19A0,(Camera_min_X_pos).w}. */
    private static final int FINAL_ARENA_MIN_X = 0x19A0;
    /** {@code move.w #$5C0,(Camera_min_Y_pos).w} and the same into {@code Camera_target_max_Y}. */
    private static final int FINAL_ARENA_Y = 0x05C0;
    /** {@code Events_bg+$06}. */
    private static final int EV_FINAL_ARENA = 0x06;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    /**
     * {@code sub_575EA}'s final-arena branch and {@code loc_45A84}'s gate. The pad does not test
     * the player at all: it waits for {@code Camera_Y_pos == Camera_max_Y_pos}, which only
     * becomes true once the lock's {@code $5C0} has finished easing the camera down.
     */
    @Test
    void theFinalArenaLocksAndThenThePadAllocatesMechaSonic() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszZoneRuntimeState state = requireState();

        boolean locked = false;
        for (int frame = 0; frame < 0x200 && !locked; frame++) {
            fixture.stepIdleFrames(1);
            locked = state.eventsBgByte(EV_FINAL_ARENA) != 0;
        }
        assertTrue(locked, "bset the Events_bg+$06 byte");

        var camera = GameServices.camera();
        assertEquals(FINAL_ARENA_MIN_X, camera.getMinX() & 0xFFFF,
                "move.w #$19A0,(Camera_min_X_pos).w");
        assertEquals(FINAL_ARENA_Y, camera.getMinY() & 0xFFFF,
                "move.w #$5C0,(Camera_min_Y_pos).w");

        SszMechaSonicObjectInstance boss = runToSpawn(fixture);
        assertEquals(camera.getY() & 0xFFFF, camera.getMaxY() & 0xFFFF,
                "loc_45A84 only allocates on the frame Camera_Y_pos has reached Camera_max_Y_pos");
        assertEquals(boss.getSlotIndex() & 0xFFFF, state.carriedObjectSlot(),
                "move.w a1,(_unkFAA4).w");
    }

    /**
     * {@code loc_7B2DC} and {@code loc_7B308}. Each number here is the immediate operand of one
     * instruction, and the two box words and the two positions are all derived from the camera
     * at the moment of the allocation, not from the pad.
     */
    @Test
    void theInitTakesItsBoxAndItsEntryFromTheCameraAtTheAllocation() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToSpawn(fixture);
        var camera = GameServices.camera();
        int cameraX = camera.getX() & 0xFFFF;
        int cameraY = camera.getY() & 0xFFFF;

        // The init runs on the boss's first update, which a plain AllocateObject may defer to
        // the next frame; step until it has.
        for (int frame = 0; frame < 4 && !boss.initExecutedForTest(); frame++) {
            fixture.stepIdleFrames(1);
        }
        assertTrue(boss.initExecutedForTest(), "loc_7B2DC ran");

        assertEquals(8, boss.getCollisionProperty(), "move.b #8,collision_property(a0)");
        assertEquals(4, boss.routineForTest(), "move.b #4,routine(a0) -- the act-1 branch");
        assertEquals(2, boss.mappingFrameForTest(), "move.b #2,mapping_frame(a0)");
        assertEquals(-0x800, boss.xVelForTest(), "move.w #-$800,x_vel(a0)");

        SszZoneRuntimeState state = requireState();
        assertEquals((cameraX + 0x20) & 0xFFFF, state.bossLeftX(),
                "addi.w #$20,d0 / move.w d0,(_unkFAB4).w");
        assertEquals((cameraX + 0x120) & 0xFFFF, state.bossRightX(),
                "and another addi.w #$100,d0 into (_unkFAB6).w");
        assertEquals((cameraY + 0x30) & 0xFFFF, state.bossCeilingY(),
                "addi.w #$30,d0 / move.w d0,(_unkFAB0).w");
        assertEquals((cameraX + 0x160) & 0xFFFF, boss.getX(),
                "a further addi.w #$40,d0 into x_pos(a0)");
        assertEquals((cameraY + 0xA0) & 0xFFFF, boss.getY(),
                "addi.w #$70,d0 into y_pos(a0)");

        // CreateChild6_Simple ChildObjDat_7D47A: dc.w 1 is two children, not one.
        assertEquals(2, boss.trailForTest().size(),
                "ChildObjDat_7D47A's dc.w 1 is a count of two");
    }

    /**
     * {@code loc_7B3E6}. The entry is eight pixels a frame to the left and the test that ends it
     * is {@code Camera_X_pos - $20} against {@code x_pos}, so it runs off the left of the screen
     * before it turns round. {@code $2E} is then {@code $3F} and the pause is what
     * {@code loc_7B416}'s {@code Obj_Wait} spends.
     */
    @Test
    void theEntryRunsOffTheLeftOfTheScreenAndThenWaitsSixtyThreeFrames() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToInit(fixture);
        int startX = boss.getX();

        fixture.stepIdleFrames(1);
        assertEquals((startX - 8) & 0xFFFF, boss.getX(),
                "MoveSprite2 with x_vel -$800 is eight pixels a frame");

        for (int frame = 0; frame < 0x100 && boss.routineForTest() == 4; frame++) {
            fixture.stepIdleFrames(1);
        }
        assertEquals(6, boss.routineForTest(), "move.b #6,routine(a0)");
        assertEquals(0x3F, boss.timerForTest(), "move.w #$3F,$2E(a0)");
        int cameraX = GameServices.camera().getX() & 0xFFFF;
        assertTrue(boss.getX() <= ((cameraX - 0x20) & 0xFFFF),
                "the turn is the frame Camera_X_pos - $20 caught x_pos");

        // loc_7B41C, after Obj_Wait spends $3F: the return is at Camera_Y + $40, four pixels a
        // frame to the right, and it is flipped because the ROM bsets render_flags bit 0.
        for (int frame = 0; frame < 0x50 && boss.routineForTest() == 6; frame++) {
            fixture.stepIdleFrames(1);
        }
        assertEquals(8, boss.routineForTest(), "move.b #8,routine(a0)");
        assertEquals(0x400, boss.xVelForTest(), "move.w #$400,x_vel(a0)");
        assertTrue(boss.renderFlippedForTest(), "bset #0,render_flags(a0)");
        assertEquals(((GameServices.camera().getY() & 0xFFFF) + 0x40) & 0xFFFF, boss.getY(),
                "addi.w #$40,d0 / move.w d0,y_pos(a0)");
    }

    /**
     * {@code loc_45AB0}. The pad reads the boss's {@code x_pos} back out of {@code $30(a0)} every
     * frame and arms its own {@code $20}-frame deletion the moment the boss is no longer to the
     * right of it, which known bug #42 recorded as the missing half of this pad.
     */
    @Test
    void thePadExplodesAndDeletesItselfOnceMechaSonicHasPassedIt() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToInit(fixture);
        SSZHPZTeleporterObjectInstance pad = spawnerPad();
        assertNotNull(pad, "the $79 placement at ($1A40,$670) is live");

        boolean passed = false;
        for (int frame = 0; frame < 0x100 && !passed; frame++) {
            fixture.stepIdleFrames(1);
            passed = boss.getX() <= PAD_X;
        }
        assertTrue(passed, "routine 4 carried Mecha Sonic past the pad");

        // move.w #$20,$32(a0), then subq.w #1 a frame until Delete_Current_Sprite.
        for (int frame = 0; frame < 0x30 && !pad.isDestroyed(); frame++) {
            fixture.stepIdleFrames(1);
        }
        assertTrue(pad.isDestroyed(), "the pad deleted itself after its $20 frames");
    }

    /**
     * {@code byte_7D2FC}, written out as the twenty-two literals the ROM holds. This is the whole
     * of {@code sub_7D2D8}: which frames of Mecha Sonic hurt, which can be attacked and which are
     * harmless is a property of the animation frame and of nothing else.
     */
    @Test
    void theCollisionByteIsTheMappingFrameTableAndNotAState() {
        int[] shipped = {
                0x23, 0x23, 0x09, 0x86, 0x86, 0x86, 0x86, 0x1A,
                0x23, 0x23, 0x23, 0x23, 0x23, 0x23, 0x09, 0x00,
                0x09, 0x23, 0x06, 0x23, 0x23, 0x23,
        };
        assertEquals(shipped.length, SszMechaSonicObjectInstance.COLLISION_BY_FRAME.length,
                "byte_7D2FC is twenty-two bytes");
        for (int frame = 0; frame < shipped.length; frame++) {
            assertEquals(shipped[frame], SszMechaSonicObjectInstance.COLLISION_BY_FRAME[frame],
                    "byte_7D2FC entry " + frame);
        }

        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToInit(fixture);
        fixture.stepIdleFrames(1);
        assertEquals(shipped[boss.mappingFrameForTest()], boss.getCollisionFlags(),
                "sub_7D2D8 wrote the row for the frame the boss is actually drawing");
    }

    /**
     * {@code byte_7B62E} and {@code byte_7B636}. The three attacks are a fixed eight-entry cycle
     * on {@code $3B(a0)}, not a random draw — only {@code loc_7B484}'s landing reads the RNG —
     * so the sequence a recorded run sees is a function of how many jumps have happened.
     */
    @Test
    void theAttackChoiceIsAFixedEightEntryCycleAndNotARandomDraw() {
        int[] cycle = {0, 1, 2, 0, 1, 2, 0, 1};
        int[] routines = {0x16, 0x1A, 0x1E};
        assertEquals(cycle.length, SszMechaSonicObjectInstance.ATTACK_CYCLE.length,
                "byte_7B62E is eight bytes");
        for (int index = 0; index < cycle.length; index++) {
            assertEquals(cycle[index], SszMechaSonicObjectInstance.ATTACK_CYCLE[index],
                    "byte_7B62E entry " + index);
        }
        for (int index = 0; index < routines.length; index++) {
            assertEquals(routines[index], SszMechaSonicObjectInstance.ATTACK_ROUTINES[index],
                    "byte_7B636 entry " + index);
        }
    }

    /**
     * {@code loc_7B484} and {@code loc_7B4DA}. The landing choice is the one place in this graph
     * that reads the RNG; what is asserted here is the state it leaves and the gravity it falls
     * under, because {@code loc_7B4DA} is {@code MoveSprite_LightGravity} — {@code moveq #$20,d1}
     * — where every other fall in the graph is {@code MoveSprite}'s {@code $38}.
     */
    @Test
    void theLandingDropsAtLightGravityAndTakesTheFallingRadius() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToRoutine(fixture, 0x0C, 0x300);

        assertEquals(0x1F, boss.yRadiusForTest(), "move.b #$1F,y_radius(a0)");
        assertEquals(0, boss.xVelForTest(), "clr.w x_vel(a0)");

        int before = boss.yVelForTest();
        fixture.stepIdleFrames(1);
        assertEquals(before + 0x20, boss.yVelForTest(),
                "MoveSprite_LightGravity's moveq #$20,d1, not MoveSprite's $38");

        SszMechaSonicObjectInstance landed = runToRoutine(fixture, 0x0E, 0x300);
        // loc_7B4EC is clr.w $16(a0) -- the low word of the 16.16 y_pos, not y_vel, which keeps
        // whatever the fall had reached. Asserting y_vel here instead is how this was first
        // written, and it is a different instruction.
        assertEquals(0, landed.ySubForTest(), "clr.w $16(a0)");
        assertTrue(landed.yVelForTest() > 0, "and y_vel is not what that instruction clears");
    }

    /**
     * {@code loc_7B5E8}, {@code byte_7B62E} and {@code byte_7B636}. Three jumps in a row must
     * come out as routine {@code $16}, {@code $1A} and {@code $1E} — the air dash, the ground
     * pound and the slam — because the cycle is {@code 0,1,2,…} on {@code $3B(a0)} and not a
     * draw. Asserted as the ROM's own routine numbers, not as the production table.
     */
    @Test
    void theThreeAttacksComeOutInTheCartridgesCycleOrder() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToRoutine(fixture, 0x0C, 0x300);

        int[] expected = {0x16, 0x1A, 0x1E};
        for (int index = 0; index < expected.length; index++) {
            int counterBefore = boss.attackCounterForTest();
            runToRoutine(fixture, expected[index], 0x400);
            assertEquals(index, boss.attackChoiceForTest(),
                    "byte_7B62E entry " + index + " of 0,1,2,0,1,2,0,1");
            assertEquals(counterBefore + 1, boss.attackCounterForTest(),
                    "addq.b #1,$3B(a0) once per jump");
            assertEquals(0x0F, boss.yRadiusForTest(),
                    "move.b #$F,y_radius(a0) in loc_7B5E8");
        }
    }

    /**
     * {@code loc_7B544}/{@code loc_7B5AC} and {@code loc_7D216}. The ground dash leaves at
     * {@code $820} with {@code $40(a0) = -$20} decelerating it, and it does not stop where it
     * runs out — it stops at whichever of {@code _unkFAB4}/{@code _unkFAB6} it was heading for,
     * and the caller is handed that limit as its new {@code x_pos}.
     */
    @Test
    void theGroundDashDeceleratesAndStopsOnTheBoxLimitItWasHeadingFor() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToRoutine(fixture, 0x10, 0x600);
        SszZoneRuntimeState state = requireState();

        assertEquals(0x820, Math.abs(boss.xVelForTest()), "move.w #$820,d0");
        boolean headingRight = boss.xVelForTest() > 0;

        int before = boss.xVelForTest();
        fixture.stepIdleFrames(1);
        assertEquals(before + (headingRight ? -0x20 : 0x20), boss.xVelForTest(),
                "add.w $40(a0),x_vel(a0) with $40 = -$20, negated with the direction");

        runToRoutine(fixture, 0x12, 0x400);
        assertEquals(headingRight ? state.bossRightX() : state.bossLeftX(), boss.getX(),
                "loc_7B5C2's move.w d0,x_pos(a0): d0 is the limit loc_7D216 matched");
    }

    /**
     * {@code loc_7B6C0} and {@code loc_7B70E}: the two floor arrivals that are not the landing.
     * The ground pound bounces at {@code -$900}; the slam instead arms {@code $2E = $F} and
     * creates {@code ChildObjDat_7D480}'s single child with {@code move.b #8,subtype(a1)}, which
     * is {@code byte_7D24C}'s fifth row — the only path that reaches it.
     */
    @Test
    void theGroundPoundBouncesAndTheSlamMakesTheRowFourAfterImage() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToRoutine(fixture, 0x1C, 0x800);
        assertEquals(-0x900, boss.yVelForTest(), "move.w #-$900,y_vel(a0) in loc_7B6C0");

        runToRoutine(fixture, 0x20, 0x800);
        assertEquals(0x0F, boss.timerForTest(), "move.w #$F,$2E(a0) in loc_7B70E");
        SszMechaSonicTrailChild row4 = boss.trailForTest().stream()
                .filter(child -> !child.isDestroyed())
                .filter(child -> child.subtypeForTest() == 8)
                .findFirst().orElse(null);
        assertNotNull(row4, "ChildObjDat_7D480's child with move.b #8,subtype(a1)");
        assertEquals(0x0C, row4.childDxForTest(), "dc.b $C of byte_7D24C's fifth row");
        assertEquals(0x0C, row4.childDyForTest(), "dc.b $C");
        assertEquals(0x200, row4.priorityWordForTest(), "dc.w $200");
    }

    /**
     * {@code loc_7B790} and {@code loc_7B7EC}, the tail of the slam chain, and the return to
     * {@code loc_7B462} that closes the loop. {@code ChildObjDat_7D486}'s pair goes through
     * {@code loc_7C8FE}, so those two after-images are subtypes 4 and 6 — rows 2 and 3.
     */
    @Test
    void theSlamChainEndsInAFinalDashAndHopAndComesBackToThePause() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToRoutine(fixture, 0x24, 0x800);
        assertEquals(0x640, Math.abs(boss.xVelForTest()), "move.w #$640,d0 in loc_7B790");

        runToRoutine(fixture, 0x28, 0x400);
        assertEquals(-0x640, boss.yVelForTest(), "move.w #-$640,y_vel(a0) in loc_7B7EC");
        assertEquals(0, boss.xVelForTest(), "clr.w x_vel(a0)");

        runToRoutine(fixture, 0x0A, 0x400);
        assertEquals(0x1F, boss.timerForTest(),
                "loc_7B804 falls back into loc_7B462's move.w #$1F,$2E(a0)");
    }

    /**
     * {@code loc_7C8FE}'s {@code addq.b #4,subtype(a0)}: the dash's pair are rows 2 and 3 of
     * {@code byte_7D24C}, where the init's pair are rows 0 and 1.
     */
    @Test
    void theDashAfterImagesTakeTheRowsFourAndSixSubtypes() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToRoutine(fixture, 0x10, 0x600);

        List<SszMechaSonicTrailChild> dash = boss.trailForTest().stream()
                .filter(child -> !child.isDestroyed())
                .filter(child -> child.subtypeForTest() >= 4)
                .sorted(java.util.Comparator.comparingInt(SszMechaSonicTrailChild::subtypeForTest))
                .toList();
        assertEquals(2, dash.size(), "ChildObjDat_7D486's dc.w 2-1 is two children");
        assertEquals(4, dash.get(0).subtypeForTest(), "0 + loc_7C8FE's addq.b #4");
        assertEquals(6, dash.get(1).subtypeForTest(), "2 + 4");
        assertEquals(-8, dash.get(0).childDxForTest(), "dc.b -8 of the third row");
        assertEquals(0x200, dash.get(0).priorityWordForTest(), "dc.w $200");
        assertEquals(4, dash.get(1).childDxForTest(), "dc.b 4 of the fourth row");
        assertEquals(0x300, dash.get(1).priorityWordForTest(), "dc.w $300");
    }

    /**
     * {@code sub_7D35A} into {@code off_7B838}'s act-1 entries, and {@code loc_7D056} beside
     * them. The frame counts are the ROM's own: {@code $7F} into an {@code Obj_Wait} that
     * completes on update N+1, then a fall that lands on its first dispatch because
     * {@code loc_7B858}'s {@code $1F} radius already puts his feet below the floor.
     *
     * <p>The same three numbers are in the native {@code hpz_3} segment of
     * {@code s3k-sonic-tails-complete-emeralds} — {@code loc_7B81A} on frame 1310, routine 2 on
     * 1438, routine 4 and the {@code loc_7D056} slot both on 1439 — read for comparison only.
     */
    @Test
    void theDefeatWaitsTheCartridgesFrameCountAndThenHandsTheActOver() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToInit(fixture);

        for (int hit = 8; hit > 0; hit--) {
            landOneHit(fixture, boss);
            assertEquals(hit - 1, boss.getCollisionProperty(),
                    "collision_property counts down from 8");
        }
        assertTrue(boss.defeatedForTest(), "sub_7D35A ran at collision_property zero");
        assertEquals(0, boss.routineForTest(), "clr.b routine(a0)");
        assertEquals(0x7F, boss.timerForTest(), "move.w #$7F,$2E(a0)");

        // loc_7B852 is a bare Obj_Wait: $7F, and the frame the decrement goes negative is the
        // one that calls $34. 128 dispatches, not 127.
        for (int frame = 0; frame < 0x7F + 1; frame++) {
            parkTheLeader(fixture);
            fixture.stepIdleFrames(1);
        }
        assertEquals(2, boss.routineForTest(), "loc_7B858's move.b #2,routine(a0)");
        assertEquals(0x0E, boss.mappingFrameForTest(), "move.b #$E,mapping_frame(a0)");
        assertEquals(0, boss.xVelForTest(), "clr.w x_vel(a0)");

        parkTheLeader(fixture);
        fixture.stepIdleFrames(1);
        assertEquals(4, boss.routineForTest(),
                "ObjHitFloor_DoRoutine lands him on the first dispatch of routine 2");
        assertEquals(0x23, boss.yRadiusForTest(), "move.b #$23,y_radius(a0) in loc_7B888");
        assertEquals((2 * 60) - 1, boss.timerForTest(), "move.w #(2*60)-1,$2E(a0)");
        assertTrue(requireState().mechaSonicBeaten(), "st (_unkFAA8).w");

        SszMechaSonicActEndObjectInstance handover =
                active(SszMechaSonicActEndObjectInstance.class);
        assertNotNull(handover, "jsr (AllocateObject).l / move.l #loc_7D056,(a1)");
        assertEquals((2 * 60) - 1, handover.timerForTest(),
                "loc_7D056's own move.w #(2*60)-1,$2E(a0)");
    }

    /**
     * {@code sub_868F8}. The act does not end on the timer alone: the routine pre-decrements
     * {@code $2E} and then refuses while Player 1 is in the air, so a leader still falling holds
     * the handover open frame after frame and it fires on the first grounded one.
     */
    @Test
    void theHandoverWaitsForTheLeaderToBeStandingAndNotJustForItsTimer() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToInit(fixture);
        for (int hit = 8; hit > 0; hit--) {
            landOneHit(fixture, boss);
        }
        for (int frame = 0; frame < 0x7F + 2; frame++) {
            parkTheLeader(fixture);
            fixture.stepIdleFrames(1);
        }
        SszMechaSonicActEndObjectInstance handover =
                active(SszMechaSonicActEndObjectInstance.class);
        assertNotNull(handover, "the handover object is live");

        // Hold the leader off the floor -- placed above it each frame, so the engine's own
        // physics reports air rather than the test asserting it -- well past the timer.
        for (int frame = 0; frame < (2 * 60) + 30; frame++) {
            parkTheLeaderInTheAir(fixture);
            fixture.stepIdleFrames(1);
        }
        assertTrue(fixture.sprite().getAir(), "the leader really is airborne");
        assertFalse(handover.resultsRequestedForTest(),
                "btst #Status_InAir,status(a1) / bne: an airborne leader holds it open");
        assertTrue(handover.timerForTest() < 0, "and $2E went negative long ago");

        parkTheLeader(fixture);
        fixture.stepIdleFrames(1);
        assertTrue(handover.resultsRequestedForTest(),
                "the first grounded dispatch takes it");
        assertEquals(2, handover.routineForTest(), "move.b d0,routine(a0) with d0 = 2");
    }

    /** Rewind across the entry: before the spawn, with the boss live, and after. */
    @Test
    void theEntrySurvivesACaptureRestoreAndForwardReplay() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToInit(fixture);
        fixture.stepIdleFrames(4);

        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        fixture.stepIdleFrames(1);
        CompositeSnapshot after = registry.capture();

        registry.restore(before);
        sameSnapshot(before, registry.capture(), "restore during the entry");
        SszMechaSonicObjectInstance restored = active(SszMechaSonicObjectInstance.class);
        assertNotNull(restored, "Mecha Sonic is back");
        assertEquals(boss.routineForTest(), restored.routineForTest(), "routine(a0) came back");
        assertFalse(restored.trailForTest().isEmpty(), "the trail children relinked");

        fixture.runner().primeInputState(
                new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
        fixture.stepIdleFrames(1);
        sameSnapshot(after, registry.capture(), "forward replay during the entry");
    }

    /**
     * {@code sub_7D236}'s {@code add.w d0,d0} against four-byte rows only addresses whole rows
     * because {@code CreateChild6_Simple} steps its subtypes with {@code addq.w #2,d2}. Read as
     * one-per-child instead, subtype 1 would take {@code $81C} as a priority word — not a
     * sprite-table offset at all — which is how the first draft of this was caught.
     */
    @Test
    void theTrailChildrenStepTheirSubtypesByTwoSoTheRowsLineUp() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, PAD_X, PAD_Y);
        SszMechaSonicObjectInstance boss = runToInit(fixture);
        List<SszMechaSonicTrailChild> children = boss.trailForTest();
        assertEquals(2, children.size(), "ChildObjDat_7D47A's dc.w 1 loops dbf twice");

        SszMechaSonicTrailChild first = children.get(0);
        SszMechaSonicTrailChild second = children.get(1);
        assertEquals(0, first.subtypeForTest(), "moveq #0,d2");
        assertEquals(2, second.subtypeForTest(), "addq.w #2,d2, not addq.w #1");

        assertEquals(-4, first.childDxForTest(), "dc.b -4 of byte_7D24C's first row");
        assertEquals(0x1C, first.childDyForTest(), "dc.b $1C");
        assertEquals(0x300, first.priorityWordForTest(), "dc.w $300");

        assertEquals(8, second.childDxForTest(), "dc.b 8 of the second row");
        assertEquals(0x1C, second.childDyForTest(), "dc.b $1C");
        assertEquals(0x200, second.priorityWordForTest(), "dc.w $200");
    }

    // --- helpers ------------------------------------------------------------------------------

    private static void sameSnapshot(CompositeSnapshot a, CompositeSnapshot b, String label) {
        assertEquals(a.entries().keySet(), b.entries().keySet(), label);
        for (String key : a.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)).isEmpty(),
                    () -> label + " " + key + ": "
                            + RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)));
        }
    }

    private static SszZoneRuntimeState requireState() {
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElse(null);
        assertNotNull(state, "SSZ act 1 has its runtime state");
        return state;
    }

    private static SszMechaSonicObjectInstance runToSpawn(HeadlessTestFixture fixture) {
        SszMechaSonicObjectInstance boss = null;
        for (int frame = 0; frame < 0x400 && boss == null; frame++) {
            fixture.stepIdleFrames(1);
            boss = active(SszMechaSonicObjectInstance.class);
        }
        assertNotNull(boss, "loc_45A84 allocated Obj_SSZEndBoss");
        return boss;
    }

    /**
     * Steps until the boss reaches a routine, with the leader parked out of the way and not
     * attacking, so the graph advances on its own timers rather than on a hit.
     */
    private static SszMechaSonicObjectInstance runToRoutine(HeadlessTestFixture fixture,
                                                            int routine, int budget) {
        SszMechaSonicObjectInstance boss = active(SszMechaSonicObjectInstance.class);
        if (boss == null) {
            boss = runToInit(fixture);
        }
        for (int frame = 0; frame < budget && boss.routineForTest() != routine; frame++) {
            parkTheLeader(fixture);
            fixture.stepIdleFrames(1);
        }
        assertEquals(routine, boss.routineForTest(),
                "the boss reached routine $" + Integer.toHexString(routine));
        return boss;
    }

    /** One attack through the real touch pass, waited for by the boss's own hit counter. */
    private static void landOneHit(HeadlessTestFixture fixture,
                                   SszMechaSonicObjectInstance boss) {
        int before = boss.getCollisionProperty();
        boolean landed = false;
        for (int frame = 0; frame < 0x400 && !landed; frame++) {
            AbstractPlayableSprite player = fixture.sprite();
            player.setDead(false);
            player.setHurt(false);
            player.setInvulnerableFrames(0);
            player.setInvincibleFrames(2);
            player.setCentreX((short) boss.getX());
            player.setCentreYPreserveSubpixel((short) boss.getY());
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setGSpeed((short) 0);
            player.setAir(true);
            player.setAnimationId(Sonic3kAnimationIds.ROLL.id());
            fixture.stepIdleFrames(1);
            landed = boss.getCollisionProperty() == before - 1;
        }
        assertTrue(landed, "a hit landed through the touch pass");
    }

    /** The same park, a long way above the arena floor, so the leader stays in the air. */
    private static void parkTheLeaderInTheAir(HeadlessTestFixture fixture) {
        parkTheLeader(fixture);
        AbstractPlayableSprite player = fixture.sprite();
        player.setCentreYPreserveSubpixel((short) (PAD_Y - 0x80));
        player.setYSpeed((short) 0);
        player.setAir(true);
    }

    private static void parkTheLeader(HeadlessTestFixture fixture) {
        AbstractPlayableSprite player = fixture.sprite();
        player.setDead(false);
        player.setHurt(false);
        player.setInvulnerableFrames(0);
        player.setInvincibleFrames(0);
        player.setCentreX((short) PAD_X);
        player.setCentreYPreserveSubpixel((short) PAD_Y);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setAnimationId(Sonic3kAnimationIds.WALK.id());
    }

    private static SszMechaSonicObjectInstance runToInit(HeadlessTestFixture fixture) {
        SszMechaSonicObjectInstance boss = runToSpawn(fixture);
        for (int frame = 0; frame < 4 && !boss.initExecutedForTest(); frame++) {
            fixture.stepIdleFrames(1);
        }
        assertTrue(boss.initExecutedForTest(), "loc_7B2DC ran");
        return boss;
    }

    private static SSZHPZTeleporterObjectInstance spawnerPad() {
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return null;
        }
        for (var instance : manager.getActiveObjects()) {
            if (instance instanceof SSZHPZTeleporterObjectInstance pad
                    && (pad.getX() & 0xFFFF) == PAD_X) {
                return pad;
            }
        }
        return null;
    }

    private static <T> T active(Class<T> type) {
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return null;
        }
        for (var instance : manager.getActiveObjects()) {
            if (type.isInstance(instance) && !instance.isDestroyed()) {
                return type.cast(instance);
            }
        }
        return null;
    }

    private static HeadlessTestFixture bootAtCheckpoint(int width, int x, int y) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                width == 320 ? WidescreenAspect.NATIVE_4_3.name()
                        : WidescreenAspect.WIDE_16_9.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 0)
                .withFreshLevelStartLifecycle()
                .startPosition((short) x, (short) y)
                .startPositionIsCentre()
                .build();
        if (GameServices.level().getCheckpointState() instanceof CheckpointState checkpoint) {
            checkpoint.saveCheckpoint(2, x, y, false);
        }
        return fixture;
    }
}
