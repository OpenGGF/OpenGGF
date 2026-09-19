package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.Level;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code DEZ1_ScreenEvent}, {@code DEZ2_ScreenInit} and {@code DEZ2_ScreenEvent}
 * (sonic3k.asm:118623-118705 and :118695-118735) — the Sonic 3 &amp; Knuckles Death Egg
 * foreground layout writes.
 *
 * <p>{@code ScreenEvents} (:102233) calls the foreground handler with
 * {@code a3 = Level_layout_main}, whose first {@code $40} words are line pointers with the
 * foreground and background rows interleaved: foreground row {@code n} is at offset {@code 4n}
 * and background row {@code n} at {@code 4n + 2} (constants.asm:288; the same arithmetic gives
 * HPZ's {@code $1C(a3)} = foreground row 7). So:
 *
 * <ul>
 *   <li>{@code DEZ1_ScreenEvent}: {@code movea.w $14(a3),a1; move.b #$BD,$6E(a1)} is chunk
 *       {@code $BD} at foreground row 5, column {@code $6E}.</li>
 *   <li>{@code DEZ2_ScreenEvent} routine 0 ({@code loc_594B4}): {@code movea.w $38(a3),a1;
 *       addq.w #1,a1} then three bytes is {@code $D7, $DC, $D7} at foreground row 14,
 *       columns 1-3.</li>
 *   <li>{@code DEZ2_ScreenEvent} routine 4 ({@code loc_594DA}): {@code movea.w $18(a3),a1;
 *       move.b #$BC,$6B(a1)} is chunk {@code $BC} at foreground row 6, column {@code $6B}.</li>
 * </ul>
 *
 * <p>Each handler consumes {@code Events_fg_4} with {@code clr.w}, and the act 2 handlers also
 * {@code addq.w #4,(Events_routine_fg).w}, so one raise produces exactly one write and the next
 * raise advances to the next stage.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezScreenEvents {

    private static final int ACT_1 = 0;
    private static final int ACT_2 = 1;

    private static S3kDezZoneRuntimeState dezState() {
        return S3kRuntimeStates.currentDez(GameServices.zoneRuntimeRegistry()).orElseThrow();
    }

    private static int chunkAt(Level level, int column, int row) {
        return Byte.toUnsignedInt(level.getMap().getValue(0, column, row));
    }

    @Test
    void actOneEventsFgFourWritesChunkBdIntoForegroundRowFive() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, ACT_1)
                .build();
        Level level = GameServices.level().getCurrentLevel();
        fixture.stepIdleFrames(2);
        assertNotEquals(0xBD, chunkAt(level, 0x6E, 5), "the chunk must not already be present");

        dezState().raiseEventsFg4();
        fixture.stepIdleFrames(2);

        assertEquals(0xBD, chunkAt(level, 0x6E, 5));
        assertEquals(0, dezState().eventsFg4(), "DEZ1_ScreenEvent clears Events_fg_4");
    }

    /**
     * {@code DEZ1_ScreenEvent} has no routine index: it is reached on every frame and writes
     * whenever {@code Events_fg_4} is non-zero, so a second raise writes the chunk again rather
     * than advancing a stage.
     */
    @Test
    void actOneHasNoStageIndexAndRepeatsOnEveryRaise() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, ACT_1)
                .build();
        fixture.stepIdleFrames(2);
        assertEquals(0, dezState().foregroundRoutine());

        dezState().raiseEventsFg4();
        fixture.stepIdleFrames(2);
        dezState().raiseEventsFg4();
        fixture.stepIdleFrames(2);

        assertEquals(0, dezState().foregroundRoutine(),
                "DEZ1_ScreenEvent never touches Events_routine_fg");
    }

    /**
     * {@code DEZ2_ScreenInit} does {@code move.w #4,(Events_routine_fg).w} and
     * {@code DEZ2_BackgroundInit} does {@code move.w #8,(Events_routine_bg).w}, so a direct act 2
     * load starts past the transition-only stage 0. Stage 0 is reachable only through the
     * seamless change, where {@code loc_593EC} leaves both routines at 0.
     */
    @Test
    void directActTwoLoadStartsAtForegroundRoutineFourAndBackgroundRoutineEight() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, ACT_2)
                .build();
        fixture.stepIdleFrames(2);

        assertEquals(4, dezState().foregroundRoutine());
        assertEquals(8, dezState().backgroundRoutine());
    }

    @Test
    void actTwoStageOneWritesChunkBcIntoForegroundRowSixAndAdvances() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, ACT_2)
                .build();
        Level level = GameServices.level().getCurrentLevel();
        fixture.stepIdleFrames(2);
        assertNotEquals(0xBC, chunkAt(level, 0x6B, 6), "the chunk must not already be present");

        dezState().raiseEventsFg4();
        fixture.stepIdleFrames(2);

        assertEquals(0xBC, chunkAt(level, 0x6B, 6));
        assertEquals(8, dezState().foregroundRoutine(), "addq.w #4,(Events_routine_fg).w");
        assertEquals(0, dezState().eventsFg4());
    }

    /**
     * Stage 2 ({@code loc_594F8}) is plain {@code DrawTilesAsYouMove}: a later raise writes
     * nothing and the routine stops advancing.
     */
    @Test
    void actTwoStageTwoConsumesNothingFurther() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, ACT_2)
                .build();
        fixture.stepIdleFrames(2);
        dezState().raiseEventsFg4();
        fixture.stepIdleFrames(2);
        assertEquals(8, dezState().foregroundRoutine());

        dezState().raiseEventsFg4();
        fixture.stepIdleFrames(2);

        assertEquals(8, dezState().foregroundRoutine(), "stage 2 never advances");
        assertNotEquals(0, dezState().eventsFg4(),
                "stage 2 does not clear Events_fg_4 either — it jumps straight to DrawTilesAsYouMove");
    }

    /**
     * The transition-only stage 0 writes {@code $D7, $DC, $D7} into foreground row 14 columns
     * 1-3. A direct load starts at stage 1, so driving the routine back to 0 is the only way to
     * reach it before the seamless act change lands in slice 7.
     */
    @Test
    void actTwoStageZeroWritesTheThreeTransitionChunksIntoRowFourteen() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, ACT_2)
                .build();
        Level level = GameServices.level().getCurrentLevel();
        fixture.stepIdleFrames(2);

        dezState().setForegroundRoutine(0);
        dezState().raiseEventsFg4();
        fixture.stepIdleFrames(2);

        assertEquals(0xD7, chunkAt(level, 1, 14));
        assertEquals(0xDC, chunkAt(level, 2, 14));
        assertEquals(0xD7, chunkAt(level, 3, 14));
        assertEquals(4, dezState().foregroundRoutine());
    }

    /** The seamless entry starts DEZ2_BackgroundEvent at zero and redraws 16 rows bottom-up. */
    @Test
    void actTwoTransitionBackgroundRedrawAdvancesToPlainDeformation() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, ACT_2)
                .build();
        fixture.stepIdleFrames(2);

        dezState().setBackgroundRoutine(0);
        fixture.stepIdleFrames(1);
        assertEquals(4, dezState().backgroundRoutine());
        assertEquals(0x0F, dezState().backgroundDrawRowsRemaining());

        fixture.stepIdleFrames(8);
        assertEquals(8, dezState().backgroundRoutine());
        assertTrue(dezState().backgroundDrawRowsRemaining() < 0);
    }
}
