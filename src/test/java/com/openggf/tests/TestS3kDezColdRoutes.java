package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceCharacterState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Death Egg routes driven only by the controller input of the committed Sonic + Tails
 * complete-run movie. The independent direct-entry check and the historical
 * native-state-seeded diagnostic have distinct evidence scopes.
 *
 * <p><b>The fixture is named {@code ssz} and is Death Egg.</b> The run's segment directories are
 * shifted one zone; {@code metadata.json} says {@code zone_id 11}, which is
 * {@link Sonic3kZoneIds#ZONE_DEZ}. Identify by {@code zone_id}, never by directory name.
 *
 * <p><b>Act 2 does not begin where the act 2 rows begin.</b> {@code aux_state}'s
 * {@code zone_act_state} rows put {@code actual_act} at 1 from row <b>18670</b> — the frame the
 * act 2 level data is loaded, with the player at {@code $01E8,$072C} and the camera at
 * {@code $00C0,$068C} — while {@code apparent_act} only follows at row 19252, five hundred and
 * eighty-two frames of act 1's ending later. That incoming act change is a different
 * inlet from the engine's direct act 2 boot at {@code $0140,$03AC}.
 *
 * <p><b>What the opening actually is.</b> Rows 19509-19548 carry the player up the entrance at a
 * flat {@code $10} px a frame with {@code x} pinned at {@code $0140} and {@code Status_InAir}
 * set, decelerating from 19549, while the sidekick sits parked at {@code $7F00,$FFF9}. That is a
 * scripted ride, not free play. Comparing a direct cold load against that inlet
 * establishes no matching native prefix. The direct-entry contract below is independent
 * of the historical native-state-seeded diagnostic.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezColdRoutes {

    static final Path RUN = Path.of(
            "src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds");
    static final Path BK2 = RUN.resolve("s3k-sonic-tails-complete-emeralds.bk2");
    static final Path PHYSICS = RUN.resolve("ssz/physics.csv.gz");
    /** {@code metadata.json}'s {@code bk2_frame_offset} for the segment. */
    static final int SEGMENT_MOVIE_OFFSET = 468982;
    /** The {@code zone_act_state} row where {@code actual_act} becomes 1. */
    static final int ACT_TWO_LOAD_ROW = 18670;
    /** Native row 18670: the act 2 load state, before a single gameplay frame. */
    static final int ACT_TWO_START_X = 0x01E8;
    static final int ACT_TWO_START_Y = 0x072C;
    static final int ACT_TWO_START_CAMERA_X = 0x00C0;
    static final int ACT_TWO_START_CAMERA_Y = 0x068C;
    static final int ACT_TWO_CARRIED_RINGS = 0x7B;
    /**
     * The first frame of free play in act 2. The entrance holds {@code x} pinned at
     * {@code $0140} with both speeds zero until row 19769; 19770 is the first row with a
     * non-zero {@code x_speed}, and 19772 the first grounded one. The player stands at
     * {@code $0140,$03AC} — <b>exactly where the engine's own cold act 2 boot puts them</b> —
     * with {@code x_speed} and {@code ground_vel} at {@code -$48} and no rings.
     */
    static final int ACT_TWO_FREE_PLAY_ROW = 19772;
    static final int FREE_PLAY_X = 0x0140;
    static final int FREE_PLAY_Y = 0x03AC;
    static final int FREE_PLAY_SPEED = -0x48;
    /** Row 19772's {@code x_sub} / {@code y_sub}: the seed is a full 16.16 position. */
    static final int FREE_PLAY_X_SUB = 0x1800;
    static final int FREE_PLAY_Y_SUB = 0x5268;
    /**
     * Row 19772's {@code gameplay_frame_counter}, which is the ROM's {@code Level_frame_counter}.
     * Objects whose cycle is phased on that clock — {@code $55} {@code Obj_DEZEnergyBridge} is
     * the first this route reaches — are wrong by a fixed offset unless the seed carries it,
     * because a fresh engine level load starts the counter at zero while the native one has been
     * running since before the act change. {@code LevelManager.setFrameCounter} wants the
     * previous completed frame, which is exactly this row's value: the first stepped frame is
     * row 19773 and reads {@code $4D3A}.
     */
    static final int FREE_PLAY_LEVEL_FRAME_COUNTER = 0x4D39;
    /**
     * Declared engine setup, not trace data. {@code LevelSizes} gives DEZ2 a minimum camera X
     * of {@code 0} (sonic3k.asm's {@code LevelSizes}, the {@code DEZ2} row), and the engine
     * loads that faithfully — but the native camera stops dead at {@code $0080} from row 19812
     * while the player keeps walking left to {@code $00CA}, so the act 2 entrance sequence
     * leaves a camera lock behind it. The seeded route reproduces the lock rather than the
     * sequence that sets it; the first version of this route reported the missing lock as a
     * two-pixel camera divergence, which would have read as an engine defect.
     */
    static final int FREE_PLAY_CAMERA_MIN_X = 0x0080;
    static final int FREE_PLAY_CAMERA_X = 0x00B0;
    static final int FREE_PLAY_CAMERA_Y = 0x0332;

    /** How far each route is driven before the frontier is reported. */
    static final int ROUTE_FRAMES = 4000;
    /**
     * Measured 2026-09-18 on this branch: 527 frames of exact player x, y, camera and ring
     * parity from the first frame of act 2 free play. It was 390 until {@code $A4}
     * {@code Obj_Spikebonker} landed — the divergence there was the badnik's own destruction
     * rebound ({@code neg.w y_vel(a0)}, sonic3k.asm:21015) that a placeholder could not give —
     * and 472 until {@code $5D} {@code Obj_DEZRetractingSpring} landed, which is the
     * {@code -$A00} launch at native row 20245.
     *
     * It was 527 until {@code $55} {@code Obj_DEZEnergyBridge} landed, which is the object the
     * player falls onto at native row 20300, and until the route began carrying the ROM's
     * {@code Level_frame_counter} (see {@link #FREE_PLAY_LEVEL_FRAME_COUNTER}).
     *
     * It was 616 until {@code $A5} {@code Obj_Chainspike} landed, which is the hit at native
     * row 20389 and the 640 frames after it; {@link #ROUTE_FRAMES} had to be widened from 1200
     * to find the next divergence at all.
     *
     * <p>The first divergence is now native row 21029, and it is <b>not a Death Egg object</b>.
     * The player has been riding a shared {@code $08} platform since row 21026
     * ({@code status_byte $08}, {@code stand_on_obj $09}; {@code DEZ2_Sprites} record 13 places
     * a {@code $08} at {@code $05C0,$038F} subtype {@code $20}) and the engine's {@code x} falls
     * one pixel behind — {@code $0696} against {@code $0697}, with {@code camera_x} following —
     * while {@code y}, both speeds, the angle and the rings all still match. That is a
     * one-frame difference in the shared platform's horizontal carry, not in anything this
     * campaign has written. A ratchet, not a target: raise it when the frontier moves.
     */
    static final int SEEDED_ROUTE_FRONTIER = 1256;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    /**
     * Break-the-fixture: the three numbers the route is seeded and compared against must still
     * be what the committed segment says, so a re-recorded fixture fails loudly instead of
     * quietly moving the frontier.
     */
    @Test
    void theActTwoLoadRowIsStillWhereItWasMeasured() throws Exception {
        List<String[]> rows = readRows();
        String[] load = rows.get(ACT_TWO_LOAD_ROW);
        assertEquals(ACT_TWO_START_X, Integer.parseInt(load[9], 16), "native act 2 load x");
        assertEquals(ACT_TWO_START_Y, Integer.parseInt(load[10], 16), "native act 2 load y");
        assertEquals(ACT_TWO_START_CAMERA_X, Integer.parseInt(load[2], 16), "camera x");
        assertEquals(ACT_TWO_START_CAMERA_Y, Integer.parseInt(load[3], 16), "camera y");
        assertEquals(ACT_TWO_CARRIED_RINGS, Integer.parseInt(load[4], 16), "carried rings");

        String[] previous = rows.get(ACT_TWO_LOAD_ROW - 1);
        assertTrue(Integer.parseInt(previous[9], 16) > 0x3000,
                "the row before the load is still act 1, far to the right");

        // The seeded route now also carries the ROM's Level_frame_counter, which every
        // frame-phased object reads. Column 5 is gameplay_frame_counter.
        String[] freePlay = rows.get(ACT_TWO_FREE_PLAY_ROW);
        assertEquals(FREE_PLAY_LEVEL_FRAME_COUNTER, Integer.parseInt(freePlay[5], 16),
                "native Level_frame_counter at the first frame of free play");
        assertEquals(FREE_PLAY_LEVEL_FRAME_COUNTER + 1,
                Integer.parseInt(rows.get(ACT_TWO_FREE_PLAY_ROW + 1)[5], 16),
                "and it advances by one a row, so the seed is the previous completed frame");
    }

    /**
     * A direct cold DEZ2 load is a different inlet from the movie's seamless
     * DEZ1-to-DEZ2 load. Assert its own ROM spawn and live controller response;
     * the first incoming-movie row is comparison-only and proves no cold parity.
     */
    @Test
    void theColdActTwoEntryUsesItsOwnSpawnAndAcceptsControllerInput() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .withFreshLevelStartLifecycle()
                .build();
        var player = fixture.sprite();

        // Sonic_Start_Locations DEZ2 (ROM $1E3C74): 01 40 03 AC.
        // No position, velocity, camera, ring or level-clock writes from trace rows.
        assertEquals(0x0140, player.getCentreX() & 0xFFFF, "Direct DEZ2 ROM spawn X");
        assertEquals(0x03AC, player.getCentreY() & 0xFFFF, "Direct DEZ2 ROM spawn Y");
        assertEquals(0, player.getRingCount(), "A cold load carries no Act 1 rings");
        assertFalse(player.getDead(), "The cold inlet must be playable");
        String[] incoming = readRows().get(ACT_TWO_LOAD_ROW);
        assertNotEquals(Integer.parseInt(incoming[10], 16), player.getCentreY() & 0xFFFF,
                "The incoming act-change row is not the direct cold-load inlet");

        int startX = player.getCentreX();
        for (int frame = 0; frame < 120 && player.getCentreX() < startX + 64; frame++) {
            fixture.stepFrame(false, false, false, true, false);
            assertFalse(player.getDead(), "DEZ2 cold entry died before its short controller-response boundary");
        }
        assertTrue(player.getCentreX() >= startX + 64,
                "Direct cold entry must release control and move at least 64px; final X=" + player.getCentreX());
    }

    /**
     * The act 2 route from the first frame of free play, seeded with the native position,
     * camera and ring count at {@link #ACT_TWO_FREE_PLAY_ROW} rather than started cold. This is
     * a historical diagnostic of the act's objects. Its seeded frontier cannot qualify
     * a cold route, production bootstrap or incoming act change. The independent
     * cold-entry test above proves only its direct inlet and controller response.
     */
    @Test
    void theDiagnosticSeededActTwoRouteReachesItsRecordedFrontier() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .startPosition((short) FREE_PLAY_X, (short) FREE_PLAY_Y)
                .startPositionIsCentre()
                .withSkippedZoneIntro()
                .withRecording(BK2)
                // Row R's input column is the input the ROM read on frame R, and row R's
                // state is what that frame produced, so the seed is row R and the first
                // stepped frame takes row R+1's input.
                .withRecordingStartFrame(SEGMENT_MOVIE_OFFSET + ACT_TWO_FREE_PLAY_ROW + 1)
                .build();
        GameServices.gameState().restoreS3kEmeraldProgress(List.of(3, 3, 3, 3, 3, 3, 3), true);
        var player = fixture.sprite();
        player.setAir(false);
        player.setXSpeed((short) FREE_PLAY_SPEED);
        player.setGSpeed((short) FREE_PLAY_SPEED);
        player.setYSpeed((short) 0);
        player.setAngle((byte) 0);
        player.setSubpixelRaw(FREE_PLAY_X_SUB, FREE_PLAY_Y_SUB);
        player.setRingCount(0);
        GameServices.level().setFrameCounter(FREE_PLAY_LEVEL_FRAME_COUNTER);
        GameServices.sprites().setFrameCounter(FREE_PLAY_LEVEL_FRAME_COUNTER);
        GameServices.camera().setMinX((short) FREE_PLAY_CAMERA_MIN_X);
        GameServices.camera().setMinXCurrent((short) FREE_PLAY_CAMERA_MIN_X);
        GameServices.camera().setMinXTarget((short) FREE_PLAY_CAMERA_MIN_X);
        GameServices.camera().setX((short) FREE_PLAY_CAMERA_X);
        GameServices.camera().setY((short) FREE_PLAY_CAMERA_Y);

        List<String[]> rows = readRows();
        String firstMismatch = null;
        int survived = 0;
        for (int frame = 1; frame <= ROUTE_FRAMES; frame++) {
            fixture.stepFrameFromRecording();
            String[] row = rows.get(ACT_TWO_FREE_PLAY_ROW + frame);
            String actual = String.format("x=%04X y=%04X cam=%04X,%04X rings=%d",
                    player.getCentreX() & 0xFFFF, player.getCentreY() & 0xFFFF,
                    GameServices.camera().getX() & 0xFFFF,
                    GameServices.camera().getY() & 0xFFFF, player.getRingCount());
            String expected = String.format("x=%04X y=%04X cam=%04X,%04X rings=%d",
                    Integer.parseInt(row[9], 16), Integer.parseInt(row[10], 16),
                    Integer.parseInt(row[2], 16), Integer.parseInt(row[3], 16),
                    Integer.parseInt(row[4], 16));
            if (!actual.equals(expected)) {
                firstMismatch = "frame " + frame + " (native row "
                        + (ACT_TWO_FREE_PLAY_ROW + frame)
                        + ")\n  expected " + expected + "\n  actual   " + actual;
                break;
            }
            survived = frame;
        }
        System.out.println("DEZ act 2 seeded route frontier: " + survived + " frames\n"
                + (firstMismatch == null ? "no divergence in " + ROUTE_FRAMES + " frames"
                        : firstMismatch));
        assertTrue(survived >= SEEDED_ROUTE_FRONTIER,
                "the act 2 frontier must not move backwards from " + SEEDED_ROUTE_FRONTIER
                        + " frames:\n" + firstMismatch);
    }

    /** Original cold movie input through the first mace contact and hurt fall. */
    @Test
    void coldActOneOpeningPreservesNativeCompanionMaceContact() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .withFreshLevelStartLifecycle()
                .withRecording(BK2.toAbsolutePath())
                .withRecordingStartFrame(SEGMENT_MOVIE_OFFSET)
                .build();
        assertEquals(320, GameServices.camera().getWidth(), "original movie viewport");
        var rows = readRows();
        // A bounded contact regression, independent of the longer campaign frontier.
        // Expected rows are comparison-only; all gameplay starts from the fresh ROM inlet.
        boolean observedHurt = false;
        for (int rowIndex = 0; rowIndex < 200; rowIndex++) {
            fixture.stepFrameFromRecording();
            var companion = GameServices.sprites().getRegisteredSidekicks().getFirst();
            observedHurt |= companion.isHurt();
            var row = rows.get(rowIndex);
            int[] actual = {companion.getCentreX() & 0xFFFF, companion.getCentreY() & 0xFFFF,
                    companion.getXSpeed() & 0xFFFF, companion.getYSpeed() & 0xFFFF,
                    companion.getGSpeed() & 0xFFFF, companion.getXSubpixelRaw(),
                    companion.getYSubpixelRaw(), companion.getAir() ? 1 : 0,
                    companion.getRolling() ? 1 : 0, TraceCharacterState.routineFromSprite(companion)};
            int[] columns = {26, 27, 28, 29, 30, 35, 36, 32, 33, 37};
            int[] expected = Arrays.stream(columns)
                    .map(column -> Integer.parseInt(row[column], 16)).toArray();
            assertArrayEquals(expected, actual,
                    "native companion movement/contact at original movie row " + rowIndex);
        }
        assertTrue(observedHurt, "the recorded opening must exercise the mace's hurt contact");
    }

    /** Original cold input through the first lift's exact-boundary rejection and later landing. */
    @Test
    void coldActOneLiftLandingPreservesNativeBoundary() throws Exception {
        assertColdActOnePrefixMatchesNative(2720);
    }

    @Test
    void coldActOneConveyorJumpPreservesNativeBoundary() throws Exception {
        assertColdActOnePrefixMatchesNative(3280);
    }

    @Test
    void coldActOneFloorLauncherLandingPreservesNativeBoundary() throws Exception {
        assertColdActOnePrefixMatchesNative(5520);
    }

    @Test
    void coldActOneStaircaseJumpPreservesNativeBoundary() throws Exception {
        assertColdActOnePrefixMatchesNative(6810);
    }

    @Test
    void coldActOneConveyorWalkOffPreservesNativeBoundary() throws Exception {
        assertColdActOnePrefixMatchesNative(8770);
    }

    private void assertColdActOnePrefixMatchesNative(int comparedRows) throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .withFreshLevelStartLifecycle()
                .withRecording(BK2.toAbsolutePath())
                .withRecordingStartFrame(SEGMENT_MOVIE_OFFSET)
                .build();
        assertEquals(320, GameServices.camera().getWidth(), "original movie viewport");
        var rows = readRows();
        var player = fixture.sprite();
        // Comparison-only native rows: no position, clocks or object state are seeded.
        for (int index = 0; index < comparedRows; index++) {
            int input = fixture.stepFrameFromRecording();
            var row = rows.get(index);
            assertEquals(Integer.parseInt(row[1], 16), input, "original input " + index);
            int[] actual = {player.getCentreX() & 0xFFFF, player.getCentreY() & 0xFFFF,
                    player.getXSpeed() & 0xFFFF, player.getYSpeed() & 0xFFFF,
                    player.getGSpeed() & 0xFFFF, player.getXSubpixelRaw(), player.getYSubpixelRaw(),
                    player.getRingCount(), GameServices.camera().getX() & 0xFFFF,
                    GameServices.camera().getY() & 0xFFFF, GameServices.level().getFrameCounter() & 0xFFFF,
                    player.getAir() ? 1 : 0, player.getRolling() ? 1 : 0};
            int[] columns = {9, 10, 11, 12, 13, 18, 19, 4, 2, 3, 5, 15, 16};
            int[] expected = Arrays.stream(columns)
                    .map(column -> Integer.parseInt(row[column], 16)).toArray();
            assertArrayEquals(expected, actual, "native main movement/contact at original movie row " + index);
        }
    }

    private static List<String[]> readRows() throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (var reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(PHYSICS))))) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(line.split(",", -1));
            }
        }
        return rows;
    }
}
