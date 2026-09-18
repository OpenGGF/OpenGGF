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
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicHeadChild;
import com.openggf.game.sonic3k.objects.bosses.SszMtzBossLaserChild;
import com.openggf.game.sonic3k.objects.bosses.SszMtzBossObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMtzBossOrbChild;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 6: the Metropolis recreation, from {@code sub_575EA}'s {@code loc_5770C} band through
 * {@code loc_5775C}'s allocation of {@code Obj_SSZMTZBoss} and the whole of that object.
 *
 * <p>Every expectation below is a literal from the routine or a frame count derived from one.
 * Where a native number is quoted it comes from the {@code hpz} segment of the
 * {@code s3k-sonic-tails-complete-emeralds} run, whose {@code aux_state} rows carry each slot's
 * code-pointer address, and it is <b>comparison only</b>: nothing here is hydrated from a trace.
 *
 * <p><b>Declared setup.</b> The upper arena's floor is {@code y = $42C} and the native run's
 * player moves between about {@code $1700} and {@code $1755} across the fight, so the approach is
 * written as a checkpoint restart at {@code ($1700,$420)} — the same shape as
 * {@code TestS3kSszGhzArenaHeadless.bootAtCheckpoint}'s {@code ($200,$7C8)}.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszMtzArenaHeadless {

    private static final int APPROACH_X = 0x1700;
    private static final int APPROACH_Y = 0x0420;
    /** {@code loc_5770C}. */
    private static final int PRE_LOCK_MAX_X = 0x1660;
    private static final int PRE_LOCK_MIN_X_WITHOUT_GHZ = 0x0160;
    private static final int ARENA_CAMERA_X = 0x1660;
    private static final int ARENA_Y = 0x0380;
    /** {@code move.w #$7F00,(Events_bg+$02).w}. */
    private static final int BOSS_FIGHTING_WORD = 0x7F00;
    private static final int EV_GHZ_BOSS = 0x00;
    private static final int EV_MTZ_BOSS = 0x02;
    private static final int EV_MTZ_LOCK = 0x03;
    private static final int EV_EVENT_OWNS_BOUNDS = 0x05;
    /**
     * One init execution, then {@code $1F + 1} {@code Obj_Wait} executions — the last of which
     * goes negative and tail-calls {@code loc_7A712} — and then the first {@code off_7A728}
     * dispatch. Thirty-three frames, which is what the native rows show between 6156 and 6189.
     */
    private static final int FRAMES_FROM_INIT_TO_FIRST_DISPATCH = 0x1F + 2;
    /** {@code word_7AD7E} rows, as {@code sub_7AD6A} copies them. */
    private static final int[] FLASH_NORMAL_ROW = {0x0008, 0x0866, 0x0222};
    /** The shipped {@code addi.w #2*2,d0} row, one word short of the {@code FixBugs} one. */
    private static final int[] FLASH_SHIPPED_ROW = {0x0222, 0x0888, 0x0CCC};
    private static final int[] FLASH_FIXED_ROW = {0x0888, 0x0CCC, 0x0EEE};
    /** {@code word_7AD78}: byte offsets into {@code Normal_palette}, i.e. line-0 colours. */
    private static final int[] FLASH_COLORS = {0x0E / 2, 0x1C / 2, 0x1E / 2};

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    /**
     * {@code loc_5770C} through {@code loc_5775C}, at both widths. The lock's
     * {@code cmpi.w #$1660,(Camera_X_pos).w} is an equality test written against the ROM's
     * 320-pixel frame, so it can only be reached at a wider viewport through
     * {@code Sonic3kSSZEvents.nativeFramedCameraX} — that reframing is what this asserts by
     * running the same approach at 800.
     */
    @ParameterizedTest
    @ValueSource(ints = {320, 800})
    void theUpperArenaLocksAndThenAllocatesTheBossWhenTheCameraSettles(int width) {
        HeadlessTestFixture fixture = bootAtCheckpoint(width, APPROACH_X, APPROACH_Y);
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        assertEquals(0, state.eventsBgByte(EV_MTZ_LOCK), "the lock has not fired at the first pass");

        var camera = GameServices.camera();
        boolean locked = false;
        for (int frame = 0; frame < 600 && !locked; frame++) {
            fixture.stepIdleFrames(1);
            locked = state.eventsBgByte(EV_MTZ_LOCK) != 0;
        }
        assertTrue(locked, "st (Events_bg+$03).w: the leader reached $420 with Camera_X at $1660");
        assertEquals(ARENA_CAMERA_X, camera.getMinX() & 0xFFFF,
                "move.w #$1660,(Camera_min_X_pos).w");
        assertEquals(ARENA_Y, camera.getMinY() & 0xFFFF, "move.w #$380,(Camera_min_Y_pos).w");
        assertEquals(ARENA_Y, camera.getMaxYTarget() & 0xFFFF,
                "and (Camera_target_max_Y_pos).w from the same d0");

        boolean spawned = false;
        for (int frame = 0; frame < 2000 && !spawned; frame++) {
            fixture.stepIdleFrames(1);
            spawned = state.eventsBgByte(EV_MTZ_BOSS) != 0;
        }
        assertTrue(spawned, "loc_5775C allocated Obj_SSZMTZBoss");
        assertEquals(ARENA_Y, camera.getY() & 0xFFFF,
                "loc_5775C gates on Camera_Y_pos == $380 exactly, which the ease at two pixels a "
                        + "frame has to land on");
        assertEquals(BOSS_FIGHTING_WORD, state.eventsBgWord(EV_MTZ_BOSS),
                "move.w #$7F00,(Events_bg+$02).w");
        assertEquals(0, state.eventsBgByte(EV_MTZ_LOCK),
                "the same word write clears Events_bg+$03 beside it");
        assertEquals(-1, state.eventsBgByte(EV_EVENT_OWNS_BOUNDS),
                "st (Events_bg+$05).w freezes sub_575EA until a $79 launch clears it");
        assertEquals(PRE_LOCK_MAX_X, camera.getMaxX() & 0xFFFF, "the arena stays closed");
    }

    /**
     * {@code loc_5772C}'s {@code moveq #0,d1 / tst.w (Events_bg+$00).w / move.w #$160,d1}: the
     * Metropolis lock reads the <em>Green Hill</em> fight's word to decide its own left limit.
     * With Green Hill unbeaten the act's left edge is {@code $160}; once that word is non-zero it
     * opens to {@code 0}.
     */
    @Test
    void thePreLockLeftLimitIsTheGreenHillWordsToChoose() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        var camera = GameServices.camera();
        fixture.stepIdleFrames(2);
        assertEquals(PRE_LOCK_MIN_X_WITHOUT_GHZ, camera.getMinX() & 0xFFFF,
                "Events_bg+$00 is zero, so d1 keeps its move.w #$160");

        state.setEventsBgWord(EV_GHZ_BOSS, BOSS_FIGHTING_WORD);
        state.setEventsBgByte(EV_MTZ_LOCK, 0);
        fixture.stepIdleFrames(2);
        assertEquals(0, camera.getMinX() & 0xFFFF,
                "tst.w (Events_bg+$00).w / bne.s loc_5772C leaves d1 at moveq #0");
    }

    /**
     * The entry. {@code loc_7A72C} writes {@code ($1700,$300)} as absolute world coordinates —
     * unlike the Green Hill ship's {@code Camera_X + $110} — so the spawn is the same at any
     * viewport and nothing here needs the camera. The native rows put the boss slot's appearance
     * at 6156 and its {@code loc_7A71A} at 6189: init + 33. Comparison only.
     */
    @Test
    void theShipEntersAtSeventeenHundredAndDispatchesThirtyThreeFramesAfterItsInit() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszMtzBossObjectInstance boss = runToSpawn(fixture);
        assertEquals(0, boss.phaseForTest(), "$26(a0) starts at zero");
        assertEquals(8, boss.hitsRemainingForTest(), "move.b #8,collision_property(a0)");

        for (int frame = 0; frame < 4 && !boss.initExecutedForTest(); frame++) {
            fixture.stepIdleFrames(1);
        }
        assertTrue(boss.initExecutedForTest(), "Obj_SSZMTZBoss's own body ran");
        assertTrue(S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow()
                .bossFlag(), "move.b #1,(Boss_flag).w in the init");
        assertEquals(0x1F, boss.waitTimerForTest(),
                "move.w #$1F,$2E(a0): the init frame does not also consume a wait frame");

        int framesFromInit = 0;
        while (!boss.dispatcherInstalledForTest() && framesFromInit < 0x60) {
            fixture.stepIdleFrames(1);
            framesFromInit++;
        }
        // The handover frame itself; off_7A728 first runs on the one after it.
        fixture.stepIdleFrames(1);
        framesFromInit++;
        assertEquals(FRAMES_FROM_INIT_TO_FIRST_DISPATCH, framesFromInit,
                "Obj_Wait goes negative on the 32nd decrement and tail-calls loc_7A712 through "
                        + "loc_84892, which installs loc_7A71A and returns inside that same "
                        + "frame; off_7A728 is first dispatched on the next one. The "
                        + "s3k-sonic-tails-complete-emeralds hpz segment puts the slot's "
                        + "appearance at 6156 and loc_7A71A at 6189. Comparison only");
        assertEquals(SszMtzBossObjectInstance.SPAWN_X, boss.getX() & 0xFFFF,
                "move.w #$1700,x_pos(a0)");
        assertEquals(SszMtzBossObjectInstance.SPAWN_Y, boss.getY() & 0xFFFF,
                "move.w #$300,y_pos(a0)");
        assertEquals(SszMtzBossObjectInstance.ARM_REST, boss.armXForTest(),
                "move.b #$27,$38(a0)");
        assertEquals(SszMtzBossObjectInstance.ARM_REST, boss.armYForTest(),
                "and move.b #$27,$3A(a0)");
        assertEquals(SszMtzBossObjectInstance.ARM_CYCLES, boss.armCyclesForTest(),
                "move.b #7,$3C(a0)");
        assertEquals(SszMtzBossObjectInstance.COLLISION_SIZE_INITIAL, boss.collisionSizeForTest(),
                "move.b #$11,collision_flags(a0) — not the $F the hit window restores");

        // loc_7A72C allocates the head and the orb slot on this one execution, and the orb slot's
        // own routine 0 loops seven times on its first, which is one frame later. The native rows
        // put the head and all seven orbs on 6190, one frame after 6189. Comparison only.
        assertEquals(1, countActive(SszMechaSonicHeadChild.class), "Child1_MakeMechaHead");
        fixture.stepIdleFrames(1);
        assertEquals(SszMtzBossOrbChild.ORB_COUNT, countActive(SszMtzBossOrbChild.class),
                "loc_7ADA2's movea.l a0,a1 makes the allocated slot the first orb, then "
                        + "dbf allocates six more in the same execution");
        assertEquals(SszMtzBossOrbChild.ORB_COUNT, boss.orbsForTest().size(), "and the ship keeps them");
    }

    /**
     * {@code loc_7A800}: {@code Boss_MoveObject} with {@code y_vel $100} is exactly one pixel a
     * frame and nothing else moves the ship, so {@code $300} to {@code $420} is {@code $120}
     * frames. Routine 0 writes only {@code y_pos}; the hover at {@code loc_7A8DA} does not run
     * until routine 2, so the descent is straight.
     */
    @Test
    void theDescentIsOnePixelAFrameAndTurnsToFaceThePlayerAtFourTwoZero() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszMtzBossObjectInstance boss = runToFirstDispatch(fixture);
        assertEquals(SszMtzBossObjectInstance.STATE_DESCEND, boss.phaseForTest());
        int startY = boss.getY() & 0xFFFF;
        fixture.stepIdleFrames(0x40);
        assertEquals(SszMtzBossObjectInstance.STATE_DESCEND, boss.phaseForTest(),
                "still inside the descent");
        assertEquals(startY + 0x40, boss.getY() & 0xFFFF,
                "y_vel $100 is 1.0 px a frame and routine 0 has no hover term");
        assertEquals(SszMtzBossObjectInstance.SPAWN_X, boss.getX() & 0xFFFF,
                "routine 0 never runs loc_7A8DA's move.w (SSZ_MTZ_boss_X_pos).w,x_pos(a0)");

        int descendFrames = 0x40;
        while (boss.phaseForTest() == SszMtzBossObjectInstance.STATE_DESCEND
                && descendFrames < 0x200) {
            fixture.stepIdleFrames(1);
            descendFrames++;
        }
        assertEquals(SszMtzBossObjectInstance.DESCENT_FLOOR_Y
                        - SszMtzBossObjectInstance.SPAWN_Y, descendFrames,
                "$420 - $300 = $120 pixels at one a frame");
        assertEquals(SszMtzBossObjectInstance.STATE_PATROL, boss.phaseForTest(),
                "addq.b #2,$26(a0)");
        // move.w (Player_1+x_pos).w,d0 / cmp.w (SSZ_MTZ_boss_X_pos).w,d0 / blo.s loc_7A84A: the
        // ship faces the leader. The approach pins the player at $1700, which is the ship's own
        // X, so the compare is not below and the bset #7 / bset #0 pair runs.
        assertTrue(boss.isRenderFlippedForTest(),
                "the player is at $1700 and so is the ship, so cmp/blo does not branch and "
                        + "bset #0,render_flags(a0) runs");
    }

    /**
     * {@code loc_7A874} and {@code sub_7A85A}. The patrol is one pixel a frame between
     * {@code $1680} and {@code $1780}, and from routine 2 onwards {@code y_pos} is
     * {@code SSZ_MTZ_boss_Y_pos} plus {@code asr.w #6} of a {@code +/-$100} sine — a four-pixel
     * hover, with {@code $1D} stepping four a frame so the cycle is 64 frames long.
     */
    @Test
    void thePatrolTurnsAtItsTwoLimitsAndHoversFourPixels() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszMtzBossObjectInstance boss = runToPhase(fixture, SszMtzBossObjectInstance.STATE_PATROL,
                0x200);
        List<Integer> xs = new ArrayList<>();
        List<Integer> offsets = new ArrayList<>();
        int previousX = boss.getX() & 0xFFFF;
        for (int frame = 0; frame < 0x300; frame++) {
            fixture.stepIdleFrames(1);
            if (boss.phaseForTest() != SszMtzBossObjectInstance.STATE_PATROL) {
                break;
            }
            int x = boss.getX() & 0xFFFF;
            assertEquals(1, Math.abs(x - previousX),
                    "x_vel is +/-$100, so the patrol moves exactly one pixel a frame");
            previousX = x;
            xs.add(x);
            offsets.add((boss.getY() & 0xFFFF) - SszMtzBossObjectInstance.DESCENT_FLOOR_Y);
        }
        assertFalse(xs.isEmpty(), "the patrol ran");
        assertTrue(xs.stream().allMatch(x -> x >= SszMtzBossObjectInstance.PATROL_MIN_X - 1
                        && x <= SszMtzBossObjectInstance.PATROL_MAX_X),
                "the patrol stays inside [$1680,$1780]: " + Integer.toHexString(
                        xs.stream().mapToInt(Integer::intValue).min().orElse(0)) + ".."
                        + Integer.toHexString(
                        xs.stream().mapToInt(Integer::intValue).max().orElse(0)));
        int lowest = offsets.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int highest = offsets.stream().mapToInt(Integer::intValue).max().orElseThrow();
        assertEquals(-4, lowest, "asr.w #6 of -$100 is -4");
        assertEquals(4, highest, "asr.w #6 of $100 is 4");
    }

    /**
     * {@code bset #6,$2E(a0) / beq.s loc_7A8DA} in both arms of {@code loc_7A874}. {@code bset}
     * sets Z from the bit's <em>old</em> value, so the first turn-around takes the branch and
     * leaves the patrol running; only the second reaches {@code addq.b #2,$26(a0)} and
     * {@code move.w #-$100,(SSZ_MTZ_boss_Y_vel).w}. A port that advanced on the first turn passes
     * every other test in this file, so the reversal count is asserted on its own.
     */
    @Test
    void theShipLeavesThePatrolOnItsSecondTurnAroundAndNotItsFirst() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszMtzBossObjectInstance boss = runToPhase(fixture, SszMtzBossObjectInstance.STATE_PATROL,
                0x200);
        int previousX = boss.getX() & 0xFFFF;
        fixture.stepIdleFrames(1);
        int direction = Integer.signum((boss.getX() & 0xFFFF) - previousX);
        assertTrue(direction != 0, "the patrol is moving");

        int reversals = 0;
        int lowest = boss.getX() & 0xFFFF;
        int highest = lowest;
        int exitX = -1;
        for (int frame = 0; frame < 0x400; frame++) {
            previousX = boss.getX() & 0xFFFF;
            fixture.stepIdleFrames(1);
            int x = boss.getX() & 0xFFFF;
            lowest = Math.min(lowest, x);
            highest = Math.max(highest, x);
            if (boss.phaseForTest() != SszMtzBossObjectInstance.STATE_PATROL) {
                exitX = x;
                break;
            }
            int step = Integer.signum(x - previousX);
            if (step != 0 && step != direction) {
                direction = step;
                reversals++;
            }
        }
        assertTrue(exitX >= 0, "the ship eventually left the patrol");
        assertEquals(SszMtzBossObjectInstance.STATE_RISE, boss.phaseForTest(),
                "addq.b #2,$26(a0) from loc_7A874 lands on loc_7A8F4");
        // The second turn advances $26 on the same frame it flips x_vel, and Boss_MoveObject has
        // already run with the old velocity, so the reversal after it is never seen inside the
        // patrol: one observed reversal is two turn-arounds. What settles the rule is that BOTH
        // limits are reached. A port that advanced on the first bset would leave at the first
        // limit it met and never see the other.
        assertEquals(1, reversals,
                "the first bset #6 found the bit clear and took beq.s loc_7A8DA; the second found "
                        + "it set and left before its own reversal could show");
        assertEquals(SszMtzBossObjectInstance.PATROL_MAX_X, highest,
                "cmpi.w #$1780,(SSZ_MTZ_boss_X_pos).w / blo.s turns on the first frame at $1780");
        assertEquals(SszMtzBossObjectInstance.PATROL_MIN_X - 1, lowest,
                "cmpi.w #$1680,(SSZ_MTZ_boss_X_pos).w / bhs.s turns on the first frame below "
                        + "$1680, which is $167F");
        assertTrue(exitX == SszMtzBossObjectInstance.PATROL_MIN_X - 1
                        || exitX == SszMtzBossObjectInstance.PATROL_MAX_X,
                "and it leaves at whichever of the two limits it met second, not in between: "
                        + Integer.toHexString(exitX));
    }

    /**
     * {@code sub_7AF5A}. The orbit's depth is the horizontal angle's cosine scaled by the ship's
     * {@code $38} arm, and its sign and magnitude choose one of four priority words and one of
     * three mapping frames. That sort is the whole front/back illusion, and it is the piece a
     * reading is most likely to get wrong, so it is asserted as the exact table.
     */
    @Test
    void theOrbRingSortsItselfFrontAndBackFromTheCosineOfItsAngle() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszMtzBossObjectInstance boss = runToFirstDispatch(fixture);
        fixture.stepIdleFrames(2);
        List<SszMtzBossOrbChild> orbs = boss.orbsForTest();
        assertEquals(SszMtzBossOrbChild.ORB_COUNT, orbs.size(), "seven orbs");
        assertEquals(List.of(0x24, 0x6C, 0xB4, 0xFC, 0x48, 0x90, 0xD8),
                orbs.stream().map(orb -> SszMtzBossOrbChild.START_ANGLE[orb.indexForTest()])
                        .toList(),
                "byte_7AE14 seeds each orb's $2E in slot order");

        // sub_7AF5A written out as the literal table it is. None of the four rows reuses a
        // production constant, so swapping two buckets or a sign in sortByDepth() fails here.
        int[] bandCounts = new int[4];
        int deepestFront = Integer.MIN_VALUE;
        int deepestBack = Integer.MAX_VALUE;
        boolean sawZeroCrossing = false;
        for (int frame = 0; frame < 0x80; frame++) {
            fixture.stepIdleFrames(1);
            for (SszMtzBossOrbChild orb : orbs) {
                if (orb.isDestroyed()
                        || orb.routineForTest() != SszMtzBossOrbChild.ROUTINE_ORBITING) {
                    continue;
                }
                int depth = orb.depthForTest();
                int mappingFrame = orb.mappingFrameForTest();
                int priority = orb.priorityWordForTest();
                if (depth >= 0x0C) {
                    bandCounts[0]++;
                    assertEquals(0, mappingFrame, "d0 >= $C: move.b #0,mapping_frame(a0)");
                    assertEquals(0x0080, priority, "and move.w #$80,priority(a0)");
                } else if (depth >= 0) {
                    bandCounts[1]++;
                    assertEquals(1, mappingFrame, "0 <= d0 < $C: move.b #1,mapping_frame(a0)");
                    assertEquals(0x0100, priority, "and move.w #$100,priority(a0)");
                } else if (depth >= -0x0C) {
                    bandCounts[2]++;
                    assertEquals(1, mappingFrame, "-$C <= d0 < 0: move.b #1,mapping_frame(a0)");
                    assertEquals(0x0300, priority, "and move.w #$300,priority(a0)");
                } else {
                    bandCounts[3]++;
                    assertEquals(2, mappingFrame, "d0 < -$C: move.b #2,mapping_frame(a0)");
                    assertEquals(0x0380, priority, "and move.w #$380,priority(a0)");
                }
                deepestFront = Math.max(deepestFront, depth);
                deepestBack = Math.min(deepestBack, depth);
                sawZeroCrossing |= Math.abs(depth) <= 1;
            }
        }
        for (int band = 0; band < bandCounts.length; band++) {
            assertTrue(bandCounts[band] > 0,
                    "row " + band + " of sub_7AF5A was never exercised, so this table is only "
                            + "partly asserted: " + java.util.Arrays.toString(bandCounts));
        }
        // $3A(a0) is the arm length times the cosine of $2E, so its extremes over a revolution
        // are +/- the ship's $38 -- $27 while the ring is at rest. Nothing in the sort produces
        // that number, so it can disagree with the orbit.
        assertEquals(SszMtzBossObjectInstance.ARM_REST, boss.armXForTest(),
                "the ring is at rest, so $38(a0) is still the init's $27");
        assertEquals(SszMtzBossObjectInstance.ARM_REST, deepestFront,
                "muls.w d1,d4 / swap: the depth peaks at the arm length itself");
        assertEquals(-SszMtzBossObjectInstance.ARM_REST, deepestBack,
                "and bottoms out at its negative, because the second GetSineCosine's d1 is the "
                        + "cosine of a full circle");
        assertTrue(sawZeroCrossing, "and passes through the ship's own plane on the way");

        // $2E steps four a frame through a 256-step circle, and byte_7AE14's seven seeds are
        // spread around it, so the ring is never all on one side at once.
        boolean mixedAtOnce = false;
        for (int frame = 0; frame < 0x40 && !mixedAtOnce; frame++) {
            fixture.stepIdleFrames(1);
            long front = orbs.stream().filter(orb -> !orb.isDestroyed())
                    .filter(orb -> orb.depthForTest() >= 0).count();
            mixedAtOnce = front > 0 && front < orbs.size();
        }
        assertTrue(mixedAtOnce, "byte_7AE14's seven seeds spread the ring around the ship");
    }

    /**
     * {@code sub_7AC06}'s {@code cmpi.b #$1F,$1C(a0)} branch. A hit arms the {@code $20} window,
     * and on that same frame the ship sets {@code $39(a0)} — which the first orb to update takes
     * — spends one of its seven {@code $3C} arm cycles, takes {@code $26 = $A} with
     * {@code y_vel -$180}, and stops moving sideways whichever branch it took.
     */
    @Test
    void aHitLaunchesExactlyOneOrbAndSpendsOneArmCycle() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszMtzBossObjectInstance boss = runToPhase(fixture, SszMtzBossObjectInstance.STATE_PATROL,
                0x200);
        assertEquals(0, boss.liveOrbsForTest(), "nothing is off its orbit yet");

        landOneHit(fixture, boss);
        assertEquals(7, boss.hitsRemainingForTest(), "collision_property went 8 -> 7");
        assertEquals(SszMtzBossObjectInstance.STATE_HIT_RISE, boss.phaseForTest(),
                "move.b #$A,$26(a0)");
        assertEquals(SszMtzBossObjectInstance.ARM_CYCLES - 1, boss.armCyclesForTest(),
                "subq.b #1,$3C(a0)");
        assertEquals(0x1F, boss.hitWindowForTest(),
                "sub_7ACF2 sets $1C to $20 and then decrements it in the same execution");

        fixture.stepIdleFrames(1);
        long launched = boss.orbsForTest().stream().filter(orb -> !orb.isDestroyed())
                .filter(orb -> orb.routineForTest() != SszMtzBossOrbChild.ROUTINE_ORBITING)
                .count();
        assertEquals(1, launched,
                "st $39(a0) is one flag and the first orb to see it clears it, so a hit launches "
                        + "one orb however many are in the ring");
        assertEquals(1, boss.liveOrbsForTest(), "addi.b #1,$30(a1)");
    }

    /**
     * {@code sub_7ACF2}'s palette window, and the restore that is not what the init wrote.
     * {@code word_7AD7E} is byte for byte {@code word_7A628}, and the shipped
     * {@code addi.w #2*2,d0} lands one word short of the second row, so the even counter values
     * write {@code $222,$888,$CCC} rather than the {@code FixBugs} {@code $888,$CCC,$EEE}.
     */
    @Test
    void theHitWindowFlashesTheShippedRowAndReopensTheBoxAtTheSmallerSize() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszMtzBossObjectInstance boss = runToPhase(fixture, SszMtzBossObjectInstance.STATE_PATROL,
                0x200);
        landOneHit(fixture, boss);

        boolean sawNormal = false;
        boolean sawShipped = false;
        for (int frame = 0; frame < 0x20 && boss.hitWindowForTest() != 0; frame++) {
            int[] colors = flashColors();
            if (java.util.Arrays.equals(colors, FLASH_NORMAL_ROW)) {
                sawNormal = true;
            } else if (java.util.Arrays.equals(colors, FLASH_SHIPPED_ROW)) {
                sawShipped = true;
            }
            assertFalse(java.util.Arrays.equals(colors, FLASH_FIXED_ROW),
                    "FixBugs = 0: the bright row is never written");
            pinNonAttackingAt(fixture, APPROACH_X, APPROACH_Y);
            fixture.stepIdleFrames(1);
        }
        assertTrue(sawNormal, "btst #0,$1C(a0) leaves d0 zero on the odd values: word_7AD7E row 0");
        assertTrue(sawShipped, "and the even values take the shipped addi.w #2*2 row");
        assertEquals(0, boss.hitWindowForTest(), "$1C ran out");
        assertEquals(SszMtzBossObjectInstance.COLLISION_SIZE_RESTORED, boss.collisionSizeForTest(),
                "move.b #$F,collision_flags(a0) restores a smaller box than the $11 the init "
                        + "wrote; the ship's hitbox is not the same after its first hit");
    }

    /**
     * {@code loc_7A7C4}. Six bytes go over {@code _unkFA82.._unkFA87}, and the first two of them
     * are the EggRobo fly-by pairing word — so starting this fight rewrites a word an unrelated
     * badnik family reads for the rest of the act.
     */
    @Test
    void theSetupOverwritesTheEggRoboPairingWord() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        state.markEggRoboFlyByPassed(3);
        state.markEggRoboFlyByPassed(5);
        assertTrue(state.eggRoboFlyByPassed(3), "seeded");

        runToFirstDispatch(fixture);
        assertEquals(SszMtzBossObjectInstance.PAIRING_WORD_AFTER_SETUP, state.eggRoboFlyByBits(),
                "move.b #$10,(a2)+ / move.b #0,(a2)+ is the word $1000");
        assertFalse(state.eggRoboFlyByPassed(3),
                "a group that had passed reads as not passed once the fight starts");
        assertFalse(state.eggRoboFlyByPassed(5), "and so does the other one");
        assertTrue(state.eggRoboFlyByPassed(12),
                "bit 12 is what the $10 high byte sets, whether or not that fly-by ever ran");
    }

    /**
     * The seventh hit spends the last {@code $3C} arm cycle, so {@code loc_7A9D4} takes its
     * {@code loc_7AA02} branch instead: {@code $26 = $E}, and the ship starts the laser pass.
     * {@code sub_7AB56} then fires {@code ChildObjDat_7AB80}'s two children three times,
     * {@code $1E} frames apart, and the second of each pair waits eight frames on the nose before
     * it moves.
     */
    @Test
    void theLastArmCycleStartsTheLaserPassAndFiresThreePairs() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszMtzBossObjectInstance boss = runToPhase(fixture, SszMtzBossObjectInstance.STATE_PATROL,
                0x200);
        while (boss.armCyclesForTest() > 0 && boss.hitsRemainingForTest() > 1) {
            landOneHit(fixture, boss);
            waitOutHitWindow(fixture, boss);
            clearTheRing(fixture, boss);
        }
        assertEquals(0, boss.armCyclesForTest(),
                "move.b #7,$3C(a0) and one subq.b per hit: seven hits spend it");
        assertEquals(1, boss.hitsRemainingForTest(),
                "collision_property 8 against $3C 7 means the eighth hit is the killing one, so "
                        + "the laser pass runs exactly once in the whole fight");

        boolean laser = false;
        for (int frame = 0; frame < 0x400 && !laser; frame++) {
            pinNonAttackingAt(fixture, APPROACH_X, APPROACH_Y);
            fixture.stepIdleFrames(1);
            laser = boss.phaseForTest() == SszMtzBossObjectInstance.STATE_LASER;
        }
        assertTrue(laser, "loc_7AA02 wrote move.b #$E,$26(a0)");

        // A shot lives well past the next one's interval, so "the count went from 0 to 2" misses
        // the later pairs. Track the children by identity and record the frame each new one is
        // first seen instead.
        java.util.Set<SszMtzBossLaserChild> seen =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<Integer> firedOnFrame = new ArrayList<>();
        List<Integer> recoilOnFire = new ArrayList<>();
        int newestPairSize = 0;
        for (int frame = 0; frame < 0x400; frame++) {
            pinNonAttackingAt(fixture, APPROACH_X, APPROACH_Y);
            fixture.stepIdleFrames(1);
            List<SszMtzBossLaserChild> fresh = allActive(SszMtzBossLaserChild.class).stream()
                    .filter(seen::add).toList();
            if (fresh.isEmpty()) {
                continue;
            }
            firedOnFrame.add(frame);
            recoilOnFire.add(boss.laserRecoilForTest());
            newestPairSize = fresh.size();
            assertEquals(SszMtzBossLaserChild.LASER_PAIR, fresh.size(),
                    "ChildObjDat_7AB80 is dc.w 2-1, so a shot is always a pair");
            assertEquals(List.of(false, true),
                    fresh.stream().map(SszMtzBossLaserChild::isTrailingForTest).sorted().toList(),
                    "the two children are subtype 0 and subtype 1");
            for (SszMtzBossLaserChild shot : fresh) {
                assertEquals(shot.isTrailingForTest() ? SszMtzBossLaserChild.DELAY_TRAIL - 1
                                : SszMtzBossLaserChild.DELAY_LEAD - 1,
                        shot.delayForTest(),
                        "moveq #8,d0 for subtype 1 and moveq #0,d0 for subtype 0, both after "
                                + "loc_7ABC2's first subq.w #1");
                assertEquals(shot.isTrailingForTest() ? SszMtzBossLaserChild.FRAME_TRAIL
                                : SszMtzBossLaserChild.FRAME_LEAD,
                        shot.mappingFrameForTest(),
                        "subtype 1 overrides ObjDat3_7ABFA's frame $D with move.b #$C");
            }
            if (firedOnFrame.size() == SszMtzBossObjectInstance.LASER_SHOTS) {
                break;
            }
        }
        assertEquals(SszMtzBossObjectInstance.LASER_SHOTS, firedOnFrame.size(),
                "move.b #3,$31(a0): three shots, and then the timer runs on with nothing left");
        assertEquals(SszMtzBossLaserChild.LASER_PAIR, newestPairSize, "the last shot was a pair");
        assertEquals(0, boss.laserShotsLeftForTest(), "$31(a0) is spent");
        for (int recoil : recoilOnFire) {
            assertEquals(SszMtzBossObjectInstance.LASER_RECOIL_FRAMES, recoil,
                    "move.b #$10,$33(a0) on the frame the shot is created");
        }
        // loc_7AA44 tests $33 first and returns before the $32 dispatch, so sub_7AB56 is not
        // called at all during the recoil: the $1E the shot rearmed does not start counting for
        // another $10 frames. Had the recoil been modelled as a draw-only pause the gap would be
        // $1E.
        for (int shot = 1; shot < firedOnFrame.size(); shot++) {
            assertEquals(SszMtzBossObjectInstance.LASER_INTERVAL
                            + SszMtzBossObjectInstance.LASER_RECOIL_FRAMES,
                    firedOnFrame.get(shot) - firedOnFrame.get(shot - 1),
                    "move.w #$1E,(SSZ_MTZ_boss_laser_timer).w rearms the interval, and "
                            + "move.b #$10,$33(a0) freezes it for that long first");
        }
    }

    /**
     * The defeat, end to end, and the {@code $79:$F6} pad it releases. {@code loc_7AD3A} installs
     * {@code Wait_FadeToLevelMusic} on the killing frame without decrementing it —
     * {@code loc_7A71A} has already run this slot for the frame — so the fade wait is
     * {@code $3F + 1 = 64} executions, which is exactly the {@code 6715 - 6651} the native rows
     * show. {@code loc_7AC92}'s own {@code (2*60)-1} then carries the ship off screen before
     * {@code loc_7ACA4} writes the beaten byte.
     */
    @Test
    void theDefeatTakesTheCartridgesFrameCountAndOpensTheGatedPad() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        SszMtzBossObjectInstance boss = runToPhase(fixture,
                SszMtzBossObjectInstance.STATE_PATROL, 0x200);
        int scoreBefore = GameServices.gameState().getScore();
        while (boss.hitsRemainingForTest() > 0) {
            landOneHit(fixture, boss);
            if (boss.hitsRemainingForTest() > 0) {
                waitOutHitWindow(fixture, boss);
                clearTheRing(fixture, boss);
            }
        }
        assertEquals(0, boss.hitsRemainingForTest(), "eight hits");
        assertTrue(boss.isEscapingForTest(), "loc_7AD3A installed Wait_FadeToLevelMusic");
        assertEquals(0x3F, boss.waitTimerForTest(),
                "BossDefeated's move.w #$3F,$2E(a0), undecremented: the killing frame's dispatch "
                        + "had already run when loc_7AD3A replaced (a0)");
        assertEquals(scoreBefore + 100, GameServices.gameState().getScore(),
                "moveq #100,d0 / jsr (HUD_AddToScore)");
        assertTrue(state.eventsBgByte(EV_MTZ_BOSS) > 0,
                "loc_7AD3A writes no Events_bg flag: the killing hit only starts the escape");

        int framesToBeaten = 0;
        while (state.eventsBgByte(EV_MTZ_BOSS) >= 0 && framesToBeaten < 400) {
            pinNonAttackingAt(fixture, APPROACH_X, APPROACH_Y);
            fixture.stepIdleFrames(1);
            framesToBeaten++;
        }
        assertTrue(state.eventsBgByte(EV_MTZ_BOSS) < 0, "st (Events_bg+$02).w in loc_7ACA4");
        assertEquals(0x40 + 0x78, framesToBeaten,
                "$3F decrements plus the one that goes negative is $40 Wait_FadeToLevelMusic "
                        + "frames; the hpz segment's 6715 - 6651 = 64 is that number. Then "
                        + "loc_85674's (2*60)-1 = $77 escape frames plus the frame loc_7AC92 "
                        + "goes negative on. Comparison only");
        assertEquals(0, countActive(SszMtzBossObjectInstance.class), "Go_Delete_Sprite");
        assertEquals(0, countActive(SszMechaSonicHeadChild.class),
                "st (_unkFA89).w deletes the head with the ship");
        assertFalse(state.bossFlag(), "clr.b (Boss_flag).w is loc_7ACA4's first instruction");
        assertEquals(0, countActive(SszMtzBossOrbChild.class),
                "the engine deletes an orbiting orb with its ship. That is a divergence, not the "
                        + "ROM: sub_7B0C2 is only reached from loc_7AFA4 and loc_7B02A, so "
                        + "_unkFA88 never pops an orb that is still on the ring and the "
                        + "cartridge leaves it orbiting a freed slot. Recorded in "
                        + "docs/status/s3k-known-bugs.md");
    }

    /**
     * Rewind spot: mid-fight, with the ring turning and one orb off it. The orbs are the first SSZ
     * graph where a boss owns a list of children that each hold the boss back, and the ring's
     * three angles are per-orb state a restore has to carry.
     */
    @Test
    void theFightSurvivesACaptureRestoreAndForwardReplay() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, APPROACH_X, APPROACH_Y);
        SszMtzBossObjectInstance boss = runToPhase(fixture,
                SszMtzBossObjectInstance.STATE_PATROL, 0x200);
        landOneHit(fixture, boss);
        fixture.stepIdleFrames(0x14);
        assertEquals(1, boss.liveOrbsForTest(), "the spot is taken with one orb off the ring");

        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        fixture.stepIdleFrames(1);
        CompositeSnapshot after = registry.capture();

        registry.restore(before);
        sameSnapshot(before, registry.capture(), "restore mid-fight");
        SszMtzBossObjectInstance restored = active(SszMtzBossObjectInstance.class);
        assertNotNull(restored, "the ship is back");
        assertEquals(SszMtzBossOrbChild.ORB_COUNT, restored.orbsForTest().size(),
                "all seven orbs relinked");
        assertEquals(1, restored.liveOrbsForTest(), "$30(a0) came back with them");

        fixture.runner().primeInputState(
                new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
        fixture.stepIdleFrames(1);
        sameSnapshot(after, registry.capture(), "forward replay mid-fight");
    }

    // --- helpers ----------------------------------------------------------------------------

    private static void sameSnapshot(CompositeSnapshot a, CompositeSnapshot b, String label) {
        assertEquals(a.entries().keySet(), b.entries().keySet(), label);
        for (String key : a.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)).isEmpty(),
                    () -> label + " " + key + ": "
                            + RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)));
        }
    }

    /** One attack through the collision pass, waited for by the ship's own hit counter. */
    private static void landOneHit(HeadlessTestFixture fixture, SszMtzBossObjectInstance boss) {
        int before = boss.getCollisionProperty();
        boolean landed = false;
        for (int frame = 0; frame < 0x200 && !landed; frame++) {
            pinAttackingAt(fixture, boss.getX(), boss.getY());
            fixture.stepIdleFrames(1);
            landed = boss.getCollisionProperty() == before - 1;
        }
        assertTrue(landed, "a hit landed through the touch pass");
    }

    private static void waitOutHitWindow(HeadlessTestFixture fixture,
                                         SszMtzBossObjectInstance boss) {
        for (int frame = 0; frame < 0x40 && boss.getCollisionFlags() == 0; frame++) {
            pinNonAttackingAt(fixture, APPROACH_X, APPROACH_Y);
            fixture.stepIdleFrames(1);
        }
        assertTrue(boss.getCollisionFlags() != 0, "the $20 window reopened the box");
    }

    /**
     * {@code loc_7A98A}'s {@code tst.b $30(a0)}: the ship will not leave its hit reaction while a
     * single orb is still off the ring, and nothing in the ROM pops one on its own — the player
     * has to. This is the fight, so a test that wants the next phase has to play it.
     */
    private static void clearTheRing(HeadlessTestFixture fixture, SszMtzBossObjectInstance boss) {
        for (int frame = 0; frame < 0x600 && boss.liveOrbsForTest() > 0; frame++) {
            SszMtzBossOrbChild target = boss.orbsForTest().stream()
                    .filter(orb -> !orb.isDestroyed())
                    .filter(orb -> orb.routineForTest() == SszMtzBossOrbChild.ROUTINE_BOUNCING)
                    .findFirst().orElse(null);
            if (target != null) {
                pinAttackingAt(fixture, target.getX(), target.getY());
            } else {
                pinNonAttackingAt(fixture, APPROACH_X, APPROACH_Y);
            }
            fixture.stepIdleFrames(1);
        }
        assertEquals(0, boss.liveOrbsForTest(),
                "subi.b #1,$30(a1) in loc_7B116 brought the count back to zero");
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

    /** {@code Normal_palette+$0E/$1C/$1E} as {@code sub_7AD6A} leaves them. */
    private static int[] flashColors() {
        var palette = GameServices.level().getCurrentLevel().getPalette(0);
        int[] out = new int[FLASH_COLORS.length];
        for (int index = 0; index < out.length; index++) {
            out[index] = PaletteWriteSupport.segaWordFromColor(
                    palette.getColor(FLASH_COLORS[index])) & 0x0EEE;
        }
        return out;
    }

    private static SszMtzBossObjectInstance runToSpawn(HeadlessTestFixture fixture) {
        SszMtzBossObjectInstance boss = null;
        for (int frame = 0; frame < 2600 && boss == null; frame++) {
            fixture.stepIdleFrames(1);
            boss = active(SszMtzBossObjectInstance.class);
        }
        assertNotNull(boss, "loc_5775C allocated Obj_SSZMTZBoss");
        return boss;
    }

    private static SszMtzBossObjectInstance runToFirstDispatch(HeadlessTestFixture fixture) {
        SszMtzBossObjectInstance boss = runToSpawn(fixture);
        for (int frame = 0; frame < 0x60 && !boss.dispatcherInstalledForTest(); frame++) {
            fixture.stepIdleFrames(1);
        }
        assertTrue(boss.dispatcherInstalledForTest(), "loc_7A712 installed loc_7A71A");
        fixture.stepIdleFrames(1);
        return boss;
    }

    private static SszMtzBossObjectInstance runToPhase(HeadlessTestFixture fixture, int phase,
                                                       int budget) {
        SszMtzBossObjectInstance boss = runToFirstDispatch(fixture);
        for (int frame = 0; frame < budget && boss.phaseForTest() != phase; frame++) {
            fixture.stepIdleFrames(1);
        }
        assertEquals(phase, boss.phaseForTest(),
                "the ship reached $26 = " + Integer.toHexString(phase));
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

    private static <T> List<T> allActive(Class<T> type) {
        List<T> out = new ArrayList<>();
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return out;
        }
        for (var instance : manager.getActiveObjects()) {
            if (type.isInstance(instance) && !instance.isDestroyed()) {
                out.add(type.cast(instance));
            }
        }
        return out;
    }

    private static int countActive(Class<?> type) {
        return allActive(type).size();
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
