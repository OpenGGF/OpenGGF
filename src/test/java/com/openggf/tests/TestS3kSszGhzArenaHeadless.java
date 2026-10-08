package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CheckpointState;
import com.openggf.game.sonic3k.objects.bosses.SszBossExplosionController;
import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.sonic3k.objects.bosses.SszGhzBossChainLinkChild;
import com.openggf.game.sonic3k.objects.bosses.SszGhzBossObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszGhzBossShieldChild;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicHeadChild;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 5: the Green Hill recreation's arena, from {@code sub_575EA}'s {@code loc_57686} band
 * through {@code loc_576E8}'s allocation of {@code Obj_SSZGHZBoss}.
 *
 * <p>Every value is from the routine. The lock needs Player 1's {@code y_pos} in
 * {@code [$440,$880)} with {@code Events_bg+$00} zero, then {@code y_pos >= $7C0},
 * {@code Camera_X_pos == $160} and the player not in the air ({@code btst #1,status}); it writes
 * {@code Camera_max_X = $160}, {@code Camera_min_Y = Camera_target_max_Y = $7C0} and
 * {@code st (Events_bg+$01).w}. The spawn is a separate gate at {@code loc_576E8}:
 * {@code Camera_Y_pos == $7C0} exactly, and only then {@code AllocateObject},
 * {@code st (Events_bg+$05).w} and {@code Events_bg+$00 = $7F00}.
 *
 * <p><b>Declared setup.</b> The arena sits behind the bridge and a long route, and
 * {@code SSZ1_ScreenInit} drags a leader with no star post back to the arrival column, so the
 * approach is written as a checkpoint restart at {@code ($200,$7C8)}. Measured in a capture, the
 * leader settles on the arena floor at {@code ($200,$86C)} with the camera at {@code ($160,$7C0)} —
 * the exact configuration the two gates ask for, reached by the act's own physics rather than by
 * writing camera values.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszGhzArenaHeadless {

    private static final int APPROACH_X = 0x0200;
    private static final int APPROACH_Y = 0x07C8;
    /** {@code loc_57686}. */
    private static final int PRE_LOCK_MIN_X = 0x0160;
    private static final int PRE_LOCK_MAX_X = 0x19A0;
    private static final int LOCK_MAX_X = 0x0160;
    private static final int ARENA_Y = 0x07C0;
    /** {@code move.w #$7F00,(Events_bg+$00).w}. */
    private static final int BOSS_FIGHTING_WORD = 0x7F00;
    private static final int EV_GHZ_BOSS = 0x00;
    private static final int EV_GHZ_LOCK = 0x01;
    private static final int EV_EVENT_OWNS_BOUNDS = 0x05;
    /** The lock pins {@code Camera_max_X_pos} to {@code $160} and the player holds it there. */
    private static final int ARENA_CAMERA_X = 0x0160;
    /** {@code addi.w #$110,d0} against that camera. */
    private static final int SHIP_SPAWN_X = ARENA_CAMERA_X + 0x110;
    /** {@code subi.w #$40,d0} against {@code Camera_Y_pos == $7C0}. */
    private static final int SHIP_SPAWN_Y = ARENA_Y - 0x40;
    /** {@code addi.w #$A0,d0} in {@code loc_7A32C}: where the chain is dropped. */
    private static final int CHAIN_DROP_X = ARENA_CAMERA_X + 0xA0;
    /**
     * One init execution, then {@code $1F + 1} {@code Obj_Wait} executions — the last of which
     * goes negative and tail-calls {@code loc_7A294} to install {@code loc_7A29C} — and then the
     * first {@code off_7A2B4} dispatch. Thirty-three frames after the init.
     */
    private static final int FRAMES_FROM_INIT_TO_FIRST_DISPATCH = 0x1F + 2;
    /** {@code word_7A628} rows, as {@code sub_7A614} copies them. */
    private static final int[] FLASH_NORMAL_ROW = {0x0008, 0x0866, 0x0222};
    /** The shipped {@code addi.w #2*2,d0} row, one word short of the {@code FixBugs} one. */
    private static final int[] FLASH_SHIPPED_ROW = {0x0222, 0x0888, 0x0CCC};
    private static final int[] FLASH_FIXED_ROW = {0x0888, 0x0CCC, 0x0EEE};
    /** {@code word_7A622}: byte offsets into {@code Normal_palette}, i.e. line 0 colours. */
    private static final int[] FLASH_COLORS = {0x0E / 2, 0x1C / 2, 0x1E / 2};

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 352, 400, 528, 800})
    void theArenaLocksAndThenAllocatesTheBossWhenTheCameraSettles(int width) {
        HeadlessTestFixture fixture = bootAtCheckpoint(width, APPROACH_X, APPROACH_Y);
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        assertEquals(0, state.eventsBgByte(EV_GHZ_LOCK), "the lock has not fired at the first pass");

        var camera = GameServices.camera();
        boolean locked = false;
        for (int frame = 0; frame < 400 && !locked; frame++) {
            fixture.stepIdleFrames(1);
            locked = state.eventsBgByte(EV_GHZ_LOCK) != 0;
        }
        assertTrue(locked, "st (Events_bg+$01).w: the leader reached $7C0 with Camera_X at $160");
        assertEquals(LOCK_MAX_X, camera.getMaxX() & 0xFFFF, "move.w #$160,(Camera_max_X_pos).w");
        assertEquals(ARENA_Y, camera.getMinY() & 0xFFFF, "move.w #$7C0,(Camera_min_Y_pos).w");
        assertEquals(ARENA_Y, camera.getMaxYTarget() & 0xFFFF,
                "and (Camera_target_max_Y_pos).w");

        boolean spawned = false;
        for (int frame = 0; frame < 1400 && !spawned; frame++) {
            fixture.stepIdleFrames(1);
            // tst.b, not tst.w: Events_bg+$01 is the lock byte and sits in the same word.
            spawned = state.eventsBgByte(EV_GHZ_BOSS) != 0;
        }
        assertTrue(spawned, "loc_576E8 allocated Obj_SSZGHZBoss");
        assertEquals(ARENA_Y, camera.getY() & 0xFFFF,
                "loc_576E8 gates on Camera_Y_pos == $7C0 exactly, which the $1000 -> $7C0 ease "
                        + "at 2 px a frame takes about 1050 frames to reach");
        assertEquals(BOSS_FIGHTING_WORD, state.eventsBgWord(EV_GHZ_BOSS),
                "move.w #$7F00,(Events_bg+$00).w");
        assertEquals(0, state.eventsBgByte(EV_GHZ_LOCK),
                "the same word write clears Events_bg+$01");
        assertEquals(-1, state.eventsBgByte(EV_EVENT_OWNS_BOUNDS),
                "st (Events_bg+$05).w freezes sub_575EA until a $79 launch clears it");
        // The pre-lock bounds are not restored while the fight runs.
        assertEquals(LOCK_MAX_X, camera.getMaxX() & 0xFFFF, "the arena stays closed");
        assertTrue((camera.getMinX() & 0xFFFF) == PRE_LOCK_MIN_X
                        || (camera.getMinX() & 0xFFFF) == 0,
                "Camera_min_X is whatever the last pre-lock pass left, not "
                        + PRE_LOCK_MAX_X);
    }

    /**
     * The boss the allocation produces, through its own routine table. Every expectation is a
     * literal from {@code off_7A2B4} and the routine bodies, or a frame count derived from one:
     * the ship appears at {@code ($270,$780)} — {@code (Camera_X + $110, Camera_Y - $40)} against
     * the arena's pinned {@code ($160,$7C0)} — does nothing for the {@code $1F}-frame
     * {@code Obj_Wait} <em>and the {@code loc_7A294} frame after it</em>, falls at exactly one
     * pixel a frame with the Mecha Sonic head attached, then runs in with the
     * {@code ChildObjDat_7A69E} emitter and drops the six-link {@code ChildObjDat_7A684} chain the
     * frame the ship reaches {@code Camera_X + $A0}.
     */
    @Test
    void theBossFallsInRunsAndDropsItsSixLinkChain() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        var camera = GameServices.camera();
        SszGhzBossObjectInstance boss = runToSpawn(fixture);
        assertEquals(ARENA_CAMERA_X, nativeFramedCameraX(),
                "the lock writes Camera_max_X_pos $160; at 320 px that is camera.getX() itself");
        assertEquals(SHIP_SPAWN_X, boss.getX() & 0xFFFF, "move.w Camera_X_pos + $110, x_pos(a0)");
        assertEquals(SHIP_SPAWN_Y, boss.getY() & 0xFFFF, "move.w Camera_Y_pos - $40, y_pos(a0)");
        assertEquals(0, boss.routineForTest(), "the dispatch has not started yet");
        assertEquals(8, boss.hitsRemainingForTest(), "move.b #8,collision_property(a0)");

        // The init block is one execution of its own: it ends jmp (PalLoad_Line1), and the
        // move.l #Obj_Wait,(a0) at its top only changes what the NEXT frame runs.
        for (int frame = 0; frame < 4 && !boss.initExecutedForTest(); frame++) {
            fixture.stepIdleFrames(1);
        }
        assertTrue(boss.initExecutedForTest(), "Obj_SSZGHZBoss's own body ran");
        assertTrue(S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow()
                .bossFlag(), "move.b #1,(Boss_flag).w in the init");
        assertEquals(0x1F, boss.waitTimerForTest(),
                "move.w #$1F,$2E(a0): the init frame does not also consume a wait frame");
        int spawnY = boss.getY() & 0xFFFF;

        int framesFromInit = 0;
        while (boss.routineForTest() == 0 && framesFromInit < 0x60) {
            fixture.stepIdleFrames(1);
            framesFromInit++;
        }
        assertEquals(FRAMES_FROM_INIT_TO_FIRST_DISPATCH, framesFromInit,
                "Obj_Wait goes negative on the 32nd decrement and tail-calls loc_7A294 through "
                        + "loc_84892, which installs loc_7A29C and returns inside that same "
                        + "frame; off_7A2B4 is first dispatched on the next one");
        assertEquals(2, boss.routineForTest(),
                "loc_7A2C0 runs SetUp_ObjAttributes, whose addq.b #2,routine(a0) is the only "
                        + "thing that advances the table");
        assertEquals(spawnY, boss.getY() & 0xFFFF,
                "nothing in the wait or in loc_7A2C0 moves the ship");
        assertEquals(1, countActive(SszMechaSonicHeadChild.class),
                "Child1_MakeMechaHead: one head, at (0,-$20)");

        // move.w #$100,y_vel(a0) with MoveSprite2 and no gravity: one pixel a frame, exactly.
        int fallFrames = 0x20;
        fixture.stepIdleFrames(fallFrames);
        assertEquals(2, boss.routineForTest(), "still inside the $67-frame fall");
        assertEquals(spawnY + fallFrames, boss.getY() & 0xFFFF,
                "y_vel $100 is 1.0 px/frame, so $20 frames of routine 2 is exactly $20 pixels");

        boolean runningIn = false;
        for (int frame = 0; frame < 0x80 && !runningIn; frame++) {
            fixture.stepIdleFrames(1);
            runningIn = boss.routineForTest() != 2;
        }
        assertEquals(4, boss.routineForTest(),
                "loc_7A2FC takes routine 4 after the $67-frame fall — not 6, not 8");
        assertEquals(1, countActive(SszGhzBossShieldChild.class),
                "ChildObjDat_7A69E: the $1E-offset emitter");
        assertFalse(boss.isChainPhaseActive(),
                "bset #6,$38(a0) has not happened yet, so the emitter is drawable");

        boolean chained = false;
        for (int frame = 0; frame < 600 && !chained; frame++) {
            fixture.stepIdleFrames(1);
            chained = !boss.chainForTest().isEmpty();
        }
        assertTrue(chained, "loc_7A32C dropped the chain when Camera_X + $A0 reached the ship");
        assertEquals(CHAIN_DROP_X, boss.getX() & 0xFFFF,
                "x_vel -$100 is exactly 1 px a frame from an even $270, so the first frame "
                        + "Camera_X + $A0 is not below x_pos lands the ship exactly on $200");
        assertEquals(6, boss.routineForTest(), "move.b #6,routine(a0) in the same branch");
        assertEquals(List.of(0, 2, 4, 6, 8, 0x0A),
                boss.chainForTest().stream().map(SszGhzBossChainLinkChild::subtypeForTest)
                        .toList(),
                "ChildObjDat_7A684 is six entries and CreateChild9_TreeList numbers them by 2");
        assertTrue(boss.isChainPhaseActive(), "bset #6,$38(a0) hides the emitter while it runs");
    }

    /**
     * The same two gates at 320 and at 800. {@code loc_7A244}'s {@code Camera_X + $110} and
     * {@code loc_7A32C}'s {@code Camera_X + $A0} are offsets from the ROM's 320-pixel left edge,
     * and the arena lock does not fix that edge: it writes {@code Camera_max_X_pos = $160}, and
     * the engine's wider viewport centres the same focus point, so {@code camera.getX()} is
     * {@code $160} at 320 px and {@code $70} at 800 px. Read raw the ship spawned at {@code $180}
     * and dropped its chain at {@code $110} on a wide screen. Both go through
     * {@code nativeFramedCameraX} now, and this asserts the framed camera, the spawn and the drop
     * are the same at both widths.
     */
    @ParameterizedTest
    @ValueSource(ints = {320, 800})
    void theShipSpawnsAndDropsTheChainAtTheSameWorldPositionAtEveryWidth(int width) {
        HeadlessTestFixture fixture = bootAtCheckpoint(width, APPROACH_X, APPROACH_Y);
        SszGhzBossObjectInstance boss = runToSpawn(fixture);
        assertEquals(ARENA_CAMERA_X, nativeFramedCameraX(),
                "Camera_max_X_pos $160 fixes the ROM's 320-pixel frame, which is $160 at every "
                        + "viewport even though the engine's own camera.getX() is $70 at 800 px");
        assertEquals(SHIP_SPAWN_X, boss.getX() & 0xFFFF, "x_pos = Camera_X_pos + $110 = $270");
        assertEquals(SHIP_SPAWN_Y, boss.getY() & 0xFFFF, "y_pos = Camera_Y_pos - $40 = $780");

        for (int frame = 0; frame < 900 && boss.chainForTest().isEmpty(); frame++) {
            fixture.stepIdleFrames(1);
        }
        assertEquals(6, boss.chainForTest().size(), "the chain dropped at this width too");
        assertEquals(CHAIN_DROP_X, boss.getX() & 0xFFFF,
                "the drop happens at exactly $200 at " + width + " px too");
    }

    /**
     * {@code loc_7A496}: the root link owns the angle and turns the ship around.
     *
     * <p>The reversal test is {@code subi.b #$40,d0 / cmpi.b #-$80,d0 / bhs}, so the step is
     * negated on every angle in {@code [$40,$BF]}. Walked from zero that is a pendulum whose
     * extremes are {@code $40} and {@code $C0} and whose sweep runs through zero: the interior of
     * {@code [$41,$BF]} is never visited at all. The ship's turn comes from the same branch's
     * {@code sls}/{@code not.b} pair, so it fires at exactly one of the two ends.
     */
    @Test
    void theRootLinkSweepsThroughZeroAndTurnsTheShipAtOneEnd() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszGhzBossObjectInstance boss = runToSpawn(fixture);
        SszGhzBossChainLinkChild root = runToSwingingRoot(fixture, boss);
        assertEquals(1, Math.abs(root.stepForTest()), "move.b #1,$3A(a0) in loc_7A446");

        java.util.Set<Integer> visited = new java.util.HashSet<>();
        java.util.List<Integer> flips = new java.util.ArrayList<>();
        boolean flippedBefore = boss.isRenderFlippedForTest();
        int turns = 0;
        int lastStep = root.stepForTest();
        for (int frame = 0; frame < 1200; frame++) {
            fixture.stepIdleFrames(1);
            if (boss.isDestroyed()) {
                break;
            }
            visited.add(root.angleForTest());
            if (root.stepForTest() != lastStep) {
                flips.add(root.angleForTest());
                lastStep = root.stepForTest();
            }
            if (boss.isRenderFlippedForTest() != flippedBefore) {
                flippedBefore = boss.isRenderFlippedForTest();
                turns++;
            }
        }
        assertTrue(visited.contains(0x00) && visited.contains(0xFF),
                "the surviving sweep runs through zero: " + visited.size() + " angles seen");
        for (int angle = 0x41; angle <= 0xBE; angle++) {
            assertFalse(visited.contains(angle), "angle " + Integer.toHexString(angle)
                    + " is inside [$41,$BE], which the reversal window never lets the sweep "
                    + "reach");
        }
        assertTrue(visited.contains(0x40) && visited.contains(0xBF) && visited.contains(0xC0),
                "the sweep turns at $40 and at $BF, so it holds $BF and $C0 for a frame each");
        assertFalse(flips.isEmpty(), "neg.b $3A(a0) fired");
        for (int angle : flips) {
            assertTrue(angle == 0x3F || angle == 0xC0,
                    "the step is negated at $40 and at $BF, so the angle after the same frame's "
                            + "add is $3F or $C0, not " + Integer.toHexString(angle));
        }
        assertTrue(turns >= 2, "bchg #0,render_flags(a0) in loc_7A3A8 turned the ship " + turns
                + " times; the 8 <-> $A loop must run more than once");
    }

    /**
     * The chain's geometry. {@code MoveSprite_CircularSimple} is
     * {@code (±$100 << 16) >> d2} in 16.16, so {@code $3A = 4} is a 16-pixel arm and the ball's
     * {@code $3A = 3} a 32-pixel one; and because the routine reads and writes
     * {@code move.l x_pos}, each link carries its parent's sub-pixel fraction rather than
     * truncating it.
     */
    @Test
    void theOrbitRadiiAreSixteenAndThirtyTwoPixelsAndCarrySubPixels() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszGhzBossObjectInstance boss = runToSpawn(fixture);
        runToSwingingRoot(fixture, boss);
        var links = boss.chainForTest();
        boolean sawFraction = false;
        for (int frame = 0; frame < 120; frame++) {
            fixture.stepIdleFrames(1);
            for (int index = 1; index < links.size(); index++) {
                SszGhzBossChainLinkChild parent = links.get(index - 1);
                SszGhzBossChainLinkChild link = links.get(index);
                double dx = (link.xFixedForTest() - parent.xFixedForTest()) / 65536.0;
                double dy = (link.yFixedForTest() - parent.yFixedForTest()) / 65536.0;
                double expected = link.subtypeForTest() == 0x0A ? 32.0 : 16.0;
                assertEquals(expected, Math.hypot(dx, dy), 0.6,
                        "link " + index + " (subtype " + link.subtypeForTest()
                                + ") orbits its parent at $3A = "
                                + (link.subtypeForTest() == 0x0A ? 3 : 4));
                sawFraction |= (link.xFixedForTest() & 0xFFFF) != 0
                        || (link.yFixedForTest() & 0xFFFF) != 0;
            }
        }
        assertTrue(sawFraction,
                "move.l x_pos(a1),d2 / move.l d2,x_pos(a0): the chain carries sub-pixels");
        // Refresh_ChildPosition is move.w into x_pos, so the root never makes a fraction; every
        // fraction in the chain is made below it. Asserted where it can disagree: the link behind
        // the root carries one even though its parent does not.
        assertEquals(0, links.getFirst().xFixedForTest() & 0xFFFF, "the root's fraction stays 0");
        assertTrue((links.get(1).xFixedForTest() & 0xFFFF) != 0
                        || (links.get(1).yFixedForTest() & 0xFFFF) != 0,
                "the first orbiting link makes a fraction its move.w parent never had");
    }

    /**
     * The emitter. Its flicker is {@code btst #0,(V_int_run_count+3).w} — a skipped frame rather
     * than an animation — and {@code word_7A65A}'s collision byte is zero, so the only reason it
     * reaches {@code Add_SpriteToCollisionResponseList} at all is that {@code loc_7A568} puts
     * every drawn child there.
     */
    @Test
    void theEmitterFlickersEveryOtherFrameAndCarriesNoCollisionByte() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszGhzBossObjectInstance boss = runToSpawn(fixture);
        for (int frame = 0; frame < 0x200 && boss.routineForTest() != 4; frame++) {
            fixture.stepIdleFrames(1);
        }
        assertEquals(4, boss.routineForTest(), "the run-in is where the emitter is alive");
        SszGhzBossShieldChild emitter = active(SszGhzBossShieldChild.class);
        assertNotNull(emitter, "ChildObjDat_7A69E allocated it");

        boolean[] seen = new boolean[8];
        for (int frame = 0; frame < seen.length; frame++) {
            fixture.stepIdleFrames(1);
            seen[frame] = emitter.isVisibleForTest();
        }
        for (int frame = 1; frame < seen.length; frame++) {
            assertTrue(seen[frame] != seen[frame - 1],
                    "btst #0,(V_int_run_count+3).w skips exactly every other frame: "
                            + java.util.Arrays.toString(seen));
        }
        // word_7A65A's last byte is the collision_flags SetUp_ObjAttributes3 writes, and it is 0.
        // Standing on the emitter does not prove that: it sits $1E in front of the ship, well
        // inside the ship's own $F-index box, so the player is hurt by the ship either way. What
        // can disagree is the object graph: of everything this fight allocates, exactly one thing
        // publishes a collision byte, and it is the ball.
        var touching = new java.util.ArrayList<String>();
        for (var instance : GameServices.level().getObjectManager().getActiveObjects()) {
            if (instance instanceof SszGhzBossShieldChild
                    || instance instanceof SszGhzBossChainLinkChild
                    || instance instanceof SszMechaSonicHeadChild) {
                if (instance instanceof com.openggf.level.objects.TouchResponseProvider provider
                        && provider.getCollisionFlags() != 0) {
                    touching.add(instance.getClass().getSimpleName() + "="
                            + Integer.toHexString(provider.getCollisionFlags()));
                }
            }
        }
        assertEquals(List.of(), touching,
                "before the chain drops, none of the ship's children has a collision byte: "
                        + "word_7A65A ends in 0 and Child1_MakeMechaHead's row does too");
    }

    /**
     * The ball is the only thing in the fight that touches the player, and it does so through the
     * engine's own collision pass rather than a direct call. {@code ObjDat3_7A678} is
     * {@code dc.b 8,8,0,$8F} and only {@code loc_7A514} ends
     * {@code Child_DrawTouch_Sprite_FlickerMove}; the root and the four middle links end
     * {@code Child_Draw_Sprite_FlickerMove}, which never reaches
     * {@code Add_SpriteToCollisionResponseList}.
     */
    @Test
    void onlyTheBallHasCollisionAndItHurtsThePlayerThroughTheTouchPass() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszGhzBossObjectInstance boss = runToSpawn(fixture);
        runToSwingingRoot(fixture, boss);
        var links = boss.chainForTest();
        for (SszGhzBossChainLinkChild link : links) {
            int flags = ((com.openggf.level.objects.TouchResponseProvider) link)
                    .getCollisionFlags();
            if (link.subtypeForTest() == 0x0A) {
                assertEquals(0x8F, flags, "ObjDat3_7A678: dc.b 8,8,0,$8F");
            } else {
                assertEquals(0, flags, "ObjDat3_7A660/_7A66C end their rows with 0");
            }
        }

        SszGhzBossChainLinkChild ball = links.getLast();
        AbstractPlayableSprite player = fixture.sprite();
        int ringsBefore = 20;
        boolean hurt = false;
        for (int frame = 0; frame < 60 && !hurt; frame++) {
            GameServices.level().getLevelGamestate().setRings(ringsBefore);
            player.setDead(false);
            player.setObjectControlled(false);
            player.setControlLocked(false);
            player.setHurt(false);
            player.setInvulnerableFrames(0);
            player.setInvincibleFrames(0);
            player.setCentreX((short) ball.getX());
            player.setCentreYPreserveSubpixel((short) ball.getY());
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setGSpeed((short) 0);
            player.setAir(false);
            player.setAnimationId(Sonic3kAnimationIds.WALK.id());
            fixture.stepIdleFrames(1);
            hurt = player.isHurt() || GameServices.level().getLevelGamestate().getRings() == 0;
        }
        assertTrue(hurt, "standing on the ball with $8F (category HURT) must cost the player "
                + "rings through ObjectTouchResponseController, with no direct call");
    }

    /**
     * {@code sub_7A5A0}'s three-word hit flash, driven by a real attack rather than by calling
     * {@code onPlayerAttack}. {@code sub_7A614} copies three words out of {@code word_7A628} into
     * {@code Normal_palette+$0E/$1C/$1E} — line 0 colours 7, 14 and 15, which is the line
     * {@code make_art_tile(ArtTile_RobotnikShip,0,0)} draws the ship on — and picks the row from
     * bit 0 of the {@code $20} counter.
     *
     * <p>{@code FixBugs = 0}, so the even frames take {@code addi.w #2*2,d0}. That is a byte
     * offset of 4 into a six-word table, i.e. one word short of the second row, and it writes
     * {@code $222,$888,$CCC} — overlapping the normal row's last colour. The {@code FixBugs}
     * branch is {@code 2*3} and would write {@code $888,$CCC,$EEE}; this asserts the shipped row
     * and asserts the fixed row is <em>not</em> what appears.
     */
    @Test
    void oneRealHitFlashesThreePaletteWordsFromTheShippedRow() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszGhzBossObjectInstance boss = runToSpawn(fixture);
        for (int frame = 0; frame < 0x200 && boss.routineForTest() < 4; frame++) {
            fixture.stepIdleFrames(1);
        }
        assertEquals(0xC0 | 0x0F, boss.getCollisionFlags(),
                "ObjDat_SSZGHZBoss's dc.b $1C,$20,$A,$F gives collision_flags $F, whose size "
                        + "index is $F. The $C0 is the engine's own BOSS category, not the ROM's: "
                        + "$0F's top two bits are %00, which Touch_ChkValue sends to Touch_Enemy, "
                        + "and the boss-ness comes from boss_hitcount2 being non-zero");
        assertEquals(8, boss.getCollisionProperty(), "move.b #8,collision_property(a0)");
        // Measured: line 0 colours 7, 14 and 15 already hold word_7A628's FIRST row before any
        // hit. So "the odd frames wrote the normal row" is true with no palette write at all, and
        // it is not asserted below; what is asserted is the alternation, which cannot be true
        // without both writes landing.
        assertArrayEquals(FLASH_NORMAL_ROW, flashColors(),
                "word_7A628's first row is the ship's own colours, which is why sub_7A614 needs "
                        + "no restore when the window closes");

        pinAttackingAt(fixture, boss.getX(), boss.getY());
        fixture.stepIdleFrames(1);
        assertEquals(7, boss.getCollisionProperty(),
                "one ordinary attack through the touch pass decrements collision_property once");
        assertEquals(0, boss.getCollisionFlags(),
                "the touch response zeroes collision_flags and stashes it in $25(a0)");
        assertEquals(0x20 - 1, boss.hitWindowForTest(),
                "move.b #$20,$20(a0) arms the window and sub_7A5A0 decrements it only after "
                        + "sub_7A614 has run, so the first window frame reads an even $20 and "
                        + "writes the flash row, leaving $1F behind");

        // The window alternates rows every frame. An alternation is the assertion: "the normal
        // row appeared" would be true of a boss that wrote nothing at all.
        int shippedFrames = 0;
        int alternations = 0;
        boolean previousWasShipped = false;
        for (int frame = 0; frame < 0x20 && boss.getCollisionFlags() == 0; frame++) {
            int[] live = flashColors();
            boolean shipped = java.util.Arrays.equals(live, FLASH_SHIPPED_ROW);
            boolean normal = java.util.Arrays.equals(live, FLASH_NORMAL_ROW);
            assertTrue(shipped || normal,
                    "every window frame writes one of word_7A628's two rows, not "
                            + java.util.Arrays.toString(live));
            assertFalse(java.util.Arrays.equals(live, FLASH_FIXED_ROW),
                    "FixBugs = 0: the 2*3 row must never appear");
            if (shipped) {
                shippedFrames++;
            }
            if (frame > 0 && previousWasShipped && normal) {
                alternations++;
            }
            previousWasShipped = shipped;
            pinNonAttackingAt(fixture, APPROACH_X, APPROACH_Y);
            fixture.stepIdleFrames(1);
        }
        assertTrue(shippedFrames >= 0x0E,
                "half of the $20 window's frames take addi.w #2*2,d0 and write $222,$888,$CCC; "
                        + "saw " + shippedFrames);
        assertTrue(alternations >= 0x0C,
                "and every one of them is followed by the d0 = 0 row, which is the parity "
                        + "btst #0,$20(a0) selects; saw " + alternations + " alternations");
        assertEquals(0xC0 | 0x0F, boss.getCollisionFlags(),
                "move.b $25(a0),collision_flags(a0) at the end of the $20 window");
    }

    /**
     * {@code PalLoad_Line1 Pal_SSZGHZMisc} on entry and {@code loc_7A414}'s copy back on the way
     * out. The ROM saves {@code Normal_palette_line_2} into {@code Target_palette_line_2} in the
     * init and restores it from there; {@code Normal_palette_line_2} is
     * {@code Normal_palette+$20}, so both are palette line index 1. Asserted as a round trip
     * against the line the act itself loaded, which is what the save and restore mean.
     */
    @Test
    void theFightOwnsPaletteLineOneAndGivesItBack() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        int[] beforeTheFight = paletteLine(1);
        SszGhzBossObjectInstance boss = runToSpawn(fixture);
        for (int frame = 0; frame < 8 && !boss.initExecutedForTest(); frame++) {
            fixture.stepIdleFrames(1);
        }
        fixture.stepIdleFrames(2);
        int[] duringTheFight = paletteLine(1);
        assertFalse(java.util.Arrays.equals(beforeTheFight, duringTheFight),
                "PalLoad_Line1 Pal_SSZGHZMisc replaced the act's own line 1");

        for (int frame = 0; frame < 0x200 && boss.routineForTest() < 4; frame++) {
            fixture.stepIdleFrames(1);
        }
        defeatThroughTheTouchPass(fixture, boss);
        for (int frame = 0; frame < 400 && !boss.hasEscaped(); frame++) {
            pinNonAttackingAt(fixture, APPROACH_X, APPROACH_Y);
            fixture.stepIdleFrames(1);
        }
        assertTrue(boss.hasEscaped(), "loc_7A3F8 ran");
        fixture.stepIdleFrames(2);
        assertArrayEquals(beforeTheFight, paletteLine(1),
                "loc_7A414 copies Target_palette_line_2 — the line saved in the init — back over "
                        + "Normal_palette_line_2");
    }

    /**
     * {@code sub_7A5A0}'s zero-hits branch and the escape that follows, with all eight hits
     * delivered through {@code ObjectTouchResponseController}.
     *
     * <p>The flag the rest of the act reads is not written at the killing hit: {@code loc_7A5EC}
     * only installs {@code Wait_FadeToLevelMusic} with {@code $34 = loc_7A3CE} and jumps to
     * {@code BossDefeated}, which sets {@code $2E = $3F} and awards 1,000 displayed points. Those {@code $3F}
     * frames, then {@code loc_85674}'s {@code (2*60)-1} escape frames, then {@code loc_7A3F8}
     * writes {@code st (Events_bg+$00).w}.
     */
    @Test
    void eightHitsSendTheShipAwayAndOnlyThenIsTheFlagNegative() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        SszGhzBossObjectInstance boss = runToSpawn(fixture);
        for (int frame = 0; frame < 0x200 && boss.routineForTest() < 4; frame++) {
            fixture.stepIdleFrames(1);
        }
        assertEquals(4, boss.routineForTest(), "the ship is flying before the first hit");
        assertEquals(0x7F, state.eventsBgByte(EV_GHZ_BOSS) & 0xFF, "still $7F00's high byte");
        int scoreBefore = GameServices.gameState().getScore();

        defeatThroughTheTouchPass(fixture, boss);
        assertEquals(0, boss.hitsRemainingForTest(), "collision_property reached zero");
        assertEquals(0, boss.getCollisionFlags(), "a defeated ship is no longer hittable");
        assertEquals(0x3F, boss.waitTimerForTest(),
                "BossDefeated (sonic3k.asm:180822) is move.w #$3F,$2E(a0), and the killing frame "
                        + "decrements nothing: loc_7A29C has already dispatched this slot before "
                        + "sub_7A5A0 installs Wait_FadeToLevelMusic, so the first decrement "
                        + "belongs to the next object pass");
        assertEquals(scoreBefore + 1000,
                GameServices.gameState().getScore(),
                "moveq #100,d0 / jsr (HUD_AddToScore) in the same routine");
        assertTrue(state.eventsBgByte(EV_GHZ_BOSS) > 0,
                "loc_7A5EC writes no flag: the killing hit only starts the escape");
        assertTrue(boss.hasStatusBit7(),
                "Touch_Enemy's .checkhurtenemy sets status bit 7 on the ship when "
                        + "boss_hitcount2 reaches zero (sonic3k.asm:20922)");

        var explosionRenderer = GameServices.level().getObjectRenderManager().getBossExplosionRenderer();
        assertNotNull(explosionRenderer, "the shared ROM explosion sheet is loaded");
        assertTrue(explosionRenderer.isReady(), "late-loaded explosion patterns are cached for rendering");
        assertEquals(1, countActive(SszBossExplosionController.class),
                "the killing dispatch allocates Child6_CreateBossExplosion subtype 4");
        int framesToBeaten = 0;
        while (state.eventsBgByte(EV_GHZ_BOSS) >= 0 && framesToBeaten < 400) {
            pinNonAttackingAt(fixture, APPROACH_X, APPROACH_Y);
            fixture.stepIdleFrames(1);
            framesToBeaten++;
            if (framesToBeaten == 1) {
                assertTrue(countActive(S3kBossExplosionChild.class) > 0,
                        "zeroed $2E expires on the first controller dispatch");
            }
            if (framesToBeaten == 20) {
                var registry = fixture.gameplayMode().getRewindRegistry();
                CompositeSnapshot before = registry.capture();
                fixture.stepIdleFrames(1);
                CompositeSnapshot after = registry.capture();
                var manager = GameServices.level().getObjectManager();
                for (var object : List.copyOf(manager.getActiveObjects())) {
                    if (object instanceof SszBossExplosionController
                            || object instanceof S3kBossExplosionChild) {
                        manager.removeDynamicObject(object);
                    }
                }
                registry.restore(before);
                sameSnapshot(before, registry.capture(), "restore defeat explosions");
                fixture.runner().primeInputState(
                        new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
                fixture.stepIdleFrames(1);
                sameSnapshot(after, registry.capture(), "forward replay defeat explosions");
                framesToBeaten++;
            }
        }
        assertTrue(state.eventsBgByte(EV_GHZ_BOSS) < 0,
                "loc_7A3F8 st (Events_bg+$00).w after the escape");
        assertEquals(0x40 + 0x78, framesToBeaten,
                "$3F decrements plus the one that goes negative is $40 Wait_FadeToLevelMusic "
                        + "frames, then loc_85674's (2*60)-1 = $77 escape frames plus the frame "
                        + "loc_7A3E6 goes negative on: $B8 = 184. The "
                        + "s3k-sonic-tails-complete-emeralds hpz segment puts the ship on "
                        + "Wait_FadeToLevelMusic at native frame 4412 and frees its slot at "
                        + "4596 — the same 184. Comparison only");
        assertEquals(0, countActive(SszMechaSonicHeadChild.class),
                "st (_unkFA89).w deletes the head with the ship");
        assertEquals(0, countActive(SszGhzBossObjectInstance.class), "Go_Delete_Sprite");
        assertEquals(0, countActive(SszGhzBossChainLinkChild.class),
                "Obj_FlickerMove culls each scattered link once it is off screen");
        assertEquals(0, countActive(SszGhzBossShieldChild.class),
                "loc_7A568 took loc_7A59A the frame after the eighth hit");
        fixture.stepIdleFrames(2);
        assertEquals(0, countActive(SszBossExplosionController.class),
                "Obj_WaitForParent retires after the ship slot is freed");
        assertFalse(state.bossFlag(), "clr.b (Boss_flag).w is loc_7A3F8's first instruction");
    }

    /**
     * What the killing hit does to the children, which is not in {@code Obj_SSZGHZBoss} at all.
     *
     * <p>{@code Touch_Enemy}'s {@code .checkhurtenemy} ends
     * {@code subq.b #1,boss_hitcount2(a1) / bne.s .bossnotdefeated / bset #7,status(a1)}
     * (sonic3k.asm:20922). Both chain dispatchers and {@code loc_7A568} test that bit on
     * {@code parent3}: the emitter takes {@code loc_7A59A} and deletes, and every link takes
     * {@code loc_849D8}, which installs {@code Obj_FlickerMove}, clears {@code collision_flags}
     * and fills {@code x_vel}/{@code y_vel} from {@code Obj_VelocityIndex + subtype*2}. Because
     * each link's {@code parent3} is the link in front of it and {@code CreateChild9_TreeList}
     * allocates them into ascending slots in that same order, one object pass walks the whole
     * chain: all six convert on the killing-hit frame, which is what the
     * {@code s3k-sonic-tails-complete-emeralds} {@code hpz} segment's aux rows show at frame
     * 4412. Comparison only.
     */
    @Test
    void theKillingHitScattersTheWholeChainAtOnceAndTakesTheBallsHitbox() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszGhzBossObjectInstance boss = runToSpawn(fixture);
        runToSwingingRoot(fixture, boss);
        var links = boss.chainForTest();
        SszGhzBossChainLinkChild ball = links.getLast();
        assertEquals(0x8F, ((com.openggf.level.objects.TouchResponseProvider) ball)
                .getCollisionFlags(), "the ball is armed before the last hit");
        assertEquals(1, countActive(SszGhzBossShieldChild.class), "so is the emitter");

        defeatThroughTheTouchPass(fixture, boss);
        assertTrue(boss.hasStatusBit7(), "bset #7,status(a1) on the killing hit");

        // All six on the killing-hit frame, not one a frame. parent3 threads down the list, but
        // CreateChild9_TreeList allocates the links into ascending slots in that same order, so
        // one object pass walks the whole chain: the root sees the ship's bit and sets its own,
        // and every later link sees its parent's bit already up. Measured in the
        // s3k-sonic-tails-complete-emeralds `hpz` segment, whose aux rows put all six links on
        // Obj_FlickerMove ($85102) on frame 4412, the same frame the ship takes
        // Wait_FadeToLevelMusic ($85668). Comparison only; nothing here is hydrated from it.
        int convertedOnTheHitFrame = 0;
        for (SszGhzBossChainLinkChild link : links) {
            if (link.routineForTest() == -1) {
                convertedOnTheHitFrame++;
            }
        }
        assertEquals(SszGhzBossObjectInstance.CHAIN_LINKS, convertedOnTheHitFrame,
                "loc_849D8 reaches every link in one object pass, because the tree list's slots "
                        + "ascend along the chain");
        assertEquals(0, ((com.openggf.level.objects.TouchResponseProvider) ball)
                        .getCollisionFlags(),
                "loc_849D8's clr.b collision_flags(a0): a scattered ball hurts nobody, and it "
                        + "stops the frame it converts rather than when the ship finally leaves");
        assertEquals(0, countActive(SszGhzBossShieldChild.class),
                "loc_7A568 took loc_7A59A -> Delete_Current_Sprite");
    }

    /**
     * Rewind spot: mid-fight, with the ball and chain out. The links are the only SSZ graph so far
     * where the parent of a child is another child — {@code CreateChild9_TreeList} threads
     * {@code parent3} down the list — so a restore has to relink six objects, not one, and the
     * root link's angle has to come back with them.
     */
    @Test
    void theFightSurvivesACaptureRestoreAndForwardReplay() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszGhzBossObjectInstance boss = runToSpawn(fixture);
        boolean chained = false;
        for (int frame = 0; frame < 900 && !chained; frame++) {
            fixture.stepIdleFrames(1);
            chained = boss.chainForTest().size() == SszGhzBossObjectInstance.CHAIN_LINKS;
        }
        assertTrue(chained, "the spot is taken with the chain out");
        // Let the links finish their word_7A642 drops so the swing is live at the capture.
        fixture.stepIdleFrames(0x50);

        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        fixture.stepIdleFrames(1);
        CompositeSnapshot after = registry.capture();

        registry.restore(before);
        sameSnapshot(before, registry.capture(), "restore mid-fight");
        SszGhzBossObjectInstance restored = active(SszGhzBossObjectInstance.class);
        assertNotNull(restored, "the ship is back");
        var links = restored.chainForTest();
        assertEquals(SszGhzBossObjectInstance.CHAIN_LINKS, links.size(),
                "the whole tree list came back");

        fixture.runner().primeInputState(
                new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
        fixture.stepIdleFrames(1);
        sameSnapshot(after, registry.capture(), "forward replay mid-fight");
    }

    private static void sameSnapshot(CompositeSnapshot a, CompositeSnapshot b, String label) {
        assertEquals(a.entries().keySet(), b.entries().keySet(), label);
        for (String key : a.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)).isEmpty(),
                    () -> label + " " + key + ": "
                            + RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)));
        }
    }

    private static SszGhzBossObjectInstance runToSpawn(HeadlessTestFixture fixture) {
        SszGhzBossObjectInstance boss = null;
        for (int frame = 0; frame < 1600 && boss == null; frame++) {
            fixture.stepIdleFrames(1);
            boss = active(SszGhzBossObjectInstance.class);
        }
        assertNotNull(boss, "loc_576E8 allocated Obj_SSZGHZBoss");
        return boss;
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

    private static int countActive(Class<?> type) {
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return 0;
        }
        int count = 0;
        for (var instance : manager.getActiveObjects()) {
            if (type.isInstance(instance) && !instance.isDestroyed()) {
                count++;
            }
        }
        return count;
    }

    /** Eight ordinary attacks through the collision pass, one per hit window. */
    private static void defeatThroughTheTouchPass(HeadlessTestFixture fixture,
                                                  SszGhzBossObjectInstance boss) {
        for (int hit = 0; hit < 8; hit++) {
            int before = boss.getCollisionProperty();
            boolean landed = false;
            for (int frame = 0; frame < 0x60 && !landed; frame++) {
                pinAttackingAt(fixture, boss.getX(), boss.getY());
                fixture.stepIdleFrames(1);
                landed = boss.getCollisionProperty() == before - 1;
            }
            assertTrue(landed, "hit " + (hit + 1) + " did not land through the touch pass");
            if (hit == 7) {
                return;
            }
            // sub_7A5A0 counts $20(a0) down before move.b $25(a0),collision_flags(a0).
            for (int frame = 0; frame < 0x40 && boss.getCollisionFlags() == 0; frame++) {
                pinNonAttackingAt(fixture, APPROACH_X, APPROACH_Y);
                fixture.stepIdleFrames(1);
            }
            assertTrue(boss.getCollisionFlags() != 0, "the $20 window never reopened the box");
        }
    }

    private static void pinAttackingAt(HeadlessTestFixture fixture, int x, int y) {
        AbstractPlayableSprite player = fixture.sprite();
        GameServices.level().getLevelGamestate().setRings(1);
        player.setDead(false);
        player.setObjectRoutineOverride(null);
        player.setObjectControlled(false);
        player.setControlLocked(false);
        player.setHurt(false);
        player.setInvulnerableFrames(0);
        player.setInvincibleFrames(2);
        player.setCentreX((short) x);
        player.setCentreYPreserveSubpixel((short) y);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setAir(true);
        player.setAnimationId(Sonic3kAnimationIds.ROLL.id());
    }

    private static void pinNonAttackingAt(HeadlessTestFixture fixture, int x, int y) {
        AbstractPlayableSprite player = fixture.sprite();
        player.setDead(false);
        player.setObjectRoutineOverride(null);
        player.setObjectControlled(false);
        player.setControlLocked(false);
        player.setHurt(false);
        player.setInvulnerableFrames(0);
        player.setInvincibleFrames(0);
        player.setCentreX((short) x);
        player.setCentreYPreserveSubpixel((short) y);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setAir(false);
        player.setAnimationId(Sonic3kAnimationIds.WALK.id());
    }

    /** Steps until the chain has paid out and the root link owns the angle. */
    private static SszGhzBossChainLinkChild runToSwingingRoot(HeadlessTestFixture fixture,
                                                              SszGhzBossObjectInstance boss) {
        for (int frame = 0; frame < 900 && boss.chainForTest().size() != 6; frame++) {
            fixture.stepIdleFrames(1);
        }
        assertEquals(6, boss.chainForTest().size(), "the chain dropped");
        SszGhzBossChainLinkChild root = boss.chainForTest().getFirst();
        // word_7A642's longest drop is the ball's $3C, two pixels a frame.
        for (int frame = 0; frame < 0x60 && root.routineForTest() != 6; frame++) {
            fixture.stepIdleFrames(1);
        }
        assertEquals(6, root.routineForTest(),
                "loc_7A482 hands the root to loc_7A496 once the ball sets $38 bit 2");
        return root;
    }

    /** {@code Camera_X_pos} as the ROM's 320-pixel frame sees it; see the boss's own copy. */
    private static int nativeFramedCameraX() {
        var camera = GameServices.camera();
        int focusExcess = Math.max(0,
                com.openggf.camera.DeadzoneGeometry.rightEdge(camera.getWidth())
                        - com.openggf.camera.DeadzoneGeometry.rightEdge(320));
        return (camera.getX() + focusExcess) & 0xFFFF;
    }

    /** {@code Normal_palette+$0E/$1C/$1E} as {@code sub_7A614} leaves them. */
    private static int[] flashColors() {
        var palette = GameServices.level().getCurrentLevel().getPalette(0);
        int[] out = new int[FLASH_COLORS.length];
        for (int index = 0; index < out.length; index++) {
            out[index] = PaletteWriteSupport.segaWordFromColor(
                    palette.getColor(FLASH_COLORS[index])) & 0x0EEE;
        }
        return out;
    }

    private static int[] paletteLine(int line) {
        var palette = GameServices.level().getCurrentLevel().getPalette(line);
        int[] out = new int[16];
        for (int index = 0; index < out.length; index++) {
            out[index] = PaletteWriteSupport.segaWordFromColor(palette.getColor(index)) & 0x0EEE;
        }
        return out;
    }

    private static HeadlessTestFixture bootAtCheckpoint(int width, int x, int y) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                java.util.Arrays.stream(WidescreenAspect.values())
                        .filter(aspect -> aspect.pixelWidth() == width).findFirst().orElseThrow().name());
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
        assertEquals(width, GameServices.camera().getWidth(), "resolved viewport width");
        return fixture;
    }
}
