package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesHpzInstance;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.sonic3k.objects.HpzKnucklesBossMusicObjectInstance;
import com.openggf.game.sonic3k.objects.HpzKnucklesDustObjectInstance;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Hidden Palace Knuckles fight replayed from a declared mid-route seed against native
 * BizHawk observations of the Sonic + Tails complete run.
 *
 * <p><b>Seed</b> (local setup, not route evidence): the state at {@code hpz22_2} row
 * {@code $287F} (movie frame 444437). Player 1 centre {@code ($112F,$42C)}, subpixels
 * {@code ($1000,$5000)}, x/ground speed {@code $600}, and its 64-entry position record ending
 * at that row; Tails {@code ($10D5,$430)}, subpixels {@code ($E700,$D800)}, speed {@code $600},
 * in its normal follow routine; {@code Ring_count} {@code $27}; {@code Level_frame_counter}
 * {@code $A77}; camera {@code ($108F,$396)} with {@code Camera_max_Y_pos} already eased to
 * {@code $394}. The level's first frame (movie frame 444436) places Knuckles and runs the
 * initial player assembly before the seed is written. {@code CutsceneKnux_HPZ} started at row
 * {@code $2863}, when the camera first reached {@code $FE0}; its {@code loc_85CA4} wait word has
 * run 28 of its 120 frames by the seed.
 *
 * <p><b>Expected values</b> come from {@code native/probe-fight/observations.csv} (slot 48,
 * {@code loc_63DE0}; routine byte, {@code collision_property}, position) and the
 * {@code _unkFAB8} column of {@code probe-fight}, {@code probe-altar} and {@code probe-ending}.
 * The comparison stops before row {@code $331E}: from there the ROM keeps the stale
 * {@code Ctrl_1_logical} jump bit under {@code Ctrl_1_locked} (engine input latch, see the HPZ
 * plan) and a lag frame at row {@code $33DD} delays the recording by one frame.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHpzKnucklesFightNativeSequence {
    private static final Path BK2 = Path.of(
            "src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/s3k-sonic-tails-complete-emeralds.bk2");
    /** Movie frame of row {@code $287E}; the seed is written after it. */
    private static final int FIRST_MOVIE_FRAME = 444436;
    private static final int FIRST_ROW = 0x2880;

    /** Player 1 {@code x_pos} for rows {@code $2840}-{@code $287F}; Y {@code $42C}, right held, status 0. */
    private static final int[] P1_HISTORY_X = {
            0x0FBC, 0x0FC1, 0x0FC6, 0x0FCC, 0x0FD1, 0x0FD6, 0x0FDC, 0x0FE1, 0x0FE7, 0x0FEC, 0x0FF2, 0x0FF8,
            0x0FFD, 0x1003, 0x1009, 0x100F, 0x1015, 0x101B, 0x1021, 0x1027, 0x102D, 0x1033, 0x1039, 0x103F,
            0x1045, 0x104B, 0x1051, 0x1057, 0x105D, 0x1063, 0x1069, 0x106F, 0x1075, 0x107B, 0x1081, 0x1087,
            0x108D, 0x1093, 0x1099, 0x109F, 0x10A5, 0x10AB, 0x10B1, 0x10B7, 0x10BD, 0x10C3, 0x10C9, 0x10CF,
            0x10D5, 0x10DB, 0x10E1, 0x10E7, 0x10ED, 0x10F3, 0x10F9, 0x10FF, 0x1105, 0x110B, 0x1111, 0x1117,
            0x111D, 0x1123, 0x1129, 0x112F};

    /** Native slot-48 transitions: row, routine byte, hit points, X, Y. */
    private static final int[][] KNUCKLES_TIMELINE = {
            {0x28A5, 0x02, 8, 0x11F0, 0x42C},
            {0x28B2, 0x16, 7, 0x11F0, 0x42C},
            {0x28CF, 0x08, 7, 0x1211, 0x42C},
            {0x28E0, 0x0A, 7, 0x1211, 0x42C},
            {0x28FC, 0x0C, 7, 0x1211, 0x3D7},
            {0x2925, 0x0E, 7, 0x116D, 0x3EC},
            {0x293C, 0x10, 7, 0x116D, 0x42C},
            {0x294F, 0x00, 7, 0x116D, 0x42C},
            {0x2950, 0x02, 7, 0x116D, 0x42C},
            {0x2969, 0x16, 6, 0x116D, 0x42C},
            {0x2986, 0x08, 6, 0x1116, 0x42C},
            {0x2997, 0x0A, 6, 0x1116, 0x42C},
            {0x29B3, 0x0C, 6, 0x1116, 0x3D7},
            // off_640CC: Tails beside the glide hits Knuckles (loc_6429E).
            {0x29CD, 0x16, 5, 0x117E, 0x3E4},
            {0x29F9, 0x08, 5, 0x1202, 0x42C},
            {0x2A0A, 0x0A, 5, 0x1202, 0x42C},
            {0x2A26, 0x0C, 5, 0x1202, 0x3D6},
            {0x2A3D, 0x16, 4, 0x11A6, 0x3E2},
            {0x2A69, 0x12, 4, 0x1122, 0x42C},
            {0x2AA5, 0x14, 4, 0x1122, 0x430},
            {0x2AC9, 0x00, 4, 0x1212, 0x42C},
            {0x2AE1, 0x02, 4, 0x1212, 0x42C},
            {0x2AE9, 0x16, 3, 0x1212, 0x42C},
            {0x2B06, 0x08, 3, 0x1212, 0x42C},
            {0x2B17, 0x0A, 3, 0x1212, 0x42C},
            {0x2B33, 0x0C, 3, 0x1212, 0x3D6},
            {0x2B4C, 0x16, 2, 0x11AE, 0x3E3},
            {0x2B78, 0x08, 2, 0x1211, 0x42C},
            {0x2B89, 0x0A, 2, 0x1211, 0x42C},
            {0x2BA5, 0x0C, 2, 0x1211, 0x3D7},
            {0x2BBE, 0x16, 1, 0x11AD, 0x3E3},
            {0x2BEA, 0x08, 1, 0x1210, 0x42C},
            {0x2BFB, 0x0A, 1, 0x1210, 0x42C},
            {0x2C17, 0x0C, 1, 0x1210, 0x3D7},
            {0x2C2F, 0x18, 0, 0x11B0, 0x3E3},
            {0x2C5B, 0x1A, 0, 0x112C, 0x42C},
            {0x2CA8, 0x1C, 0, 0x112C, 0x42C},
            {0x2D28, 0x1E, 0, 0x112C, 0x42C},
            {0x2D38, 0x20, 0, 0x112C, 0x42C},
            {0x2D40, 0x22, 0, 0x112C, 0x42C},
            {0x2DAE, 0x24, 0, 0x1620, 0x3AC},
    };

    /** Native {@code _unkFAB8} changes: row, value. */
    private static final int[][] FAB8_TIMELINE = {
            {0x28DC, 0x01}, {0x2DAE, 0x00}, {0x2F2E, 0x01}, {0x3054, 0x03},
            {0x32B5, 0x0F}, {0x32B6, 0x1F}, {0x331D, 0x3F},
    };
    private static final int LAST_ROW = 0x331D;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @Test
    void seededFightFollowsTheNativeRoutineHitAndFlagTimeline() throws Exception {
        HeadlessTestFixture fixture = seededFixture();

        List<String> knuckles = new ArrayList<>();
        List<String> flags = new ArrayList<>();
        String lastRoutineAndHp = null;
        List<String> dust = new ArrayList<>();
        boolean dustPresent = false;
        int lastFlags = 0;
        for (int row = FIRST_ROW; row <= LAST_ROW; row++) {
            fixture.stepFrameFromRecording();
            assertFalse(fixture.sprite().getDead(), "Sonic survives row " + Integer.toHexString(row));
            CutsceneKnucklesHpzInstance k = GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(CutsceneKnucklesHpzInstance.class::isInstance)
                    .map(CutsceneKnucklesHpzInstance.class::cast)
                    .filter(o -> !o.isDestroyed())
                    .findFirst().orElse(null);
            if (k != null && row <= KNUCKLES_TIMELINE[KNUCKLES_TIMELINE.length - 1][0]) {
                String routineAndHp = k.routineForTest() + "/" + k.collisionPropertyForTest();
                if (lastRoutineAndHp != null && !routineAndHp.equals(lastRoutineAndHp)) {
                    knuckles.add(event(row, k.routineForTest(), k.collisionPropertyForTest(), k.getX(), k.getY()));
                }
                lastRoutineAndHp = routineAndHp;
            }
            if (dustPresent != dustActive()) {
                dustPresent = !dustPresent;
                dust.add(String.format("%04X %s", row, dustPresent ? "created" : "deleted"));
            }
            HpzZoneRuntimeState hpz = S3kRuntimeStates.currentHpz(GameServices.zoneRuntimeRegistry()).orElseThrow();
            if (hpz.knucklesCutsceneFlags() != lastFlags) {
                if (row == 0x2F2E) {
                    // loc_64D1A: hpz22_2 camera_x is $1580 on row $2F2E.
                    assertEquals(0x1580, GameServices.camera().getX() & 0xFFFF, "loc_64D1A pan end");
                }
                lastFlags = hpz.knucklesCutsceneFlags();
                flags.add(String.format("%04X=%02X", row, lastFlags));
            }
        }

        List<String> expectedKnuckles = new ArrayList<>();
        for (int[] e : KNUCKLES_TIMELINE) {
            expectedKnuckles.add(event(e[0], e[1], e[2], e[3], e[4]));
        }
        List<String> expectedFlags = new ArrayList<>();
        for (int[] e : FAB8_TIMELINE) {
            expectedFlags.add(String.format("%04X=%02X", e[0], e[1]));
        }
        assertEquals(expectedKnuckles, knuckles);
        assertEquals(expectedFlags, flags);
        // loc_641C4 ($2A69) sets $2E = 59; loc_64184 creates ChildObjDat_665FC when it reaches 43
        // (16 passes later), and loc_64222 ($2AA5) sets $38 bit 0, which the later-slot dust
        // reads on the same pass.
        assertEquals(List.of("2A79 created", "2AA5 deleted"), dust);
    }

    /**
     * Rewind spot on the spin-dash dust ({@code loc_64C24}): capture while the dust is live,
     * run past {@code loc_64222}'s delete, then restore and replay the same inputs twice.
     */
    @Test
    void spinDashDustRestoresAndReplaysIdentically() throws Exception {
        HeadlessTestFixture fixture = seededFixture();
        int row = FIRST_ROW;
        for (; row <= 0x2A80; row++) {
            fixture.stepFrameFromRecording();
        }
        assertTrue(dustActive(), "ChildObjDat_665FC dust is live at row $2A80");
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot start = registry.capture();
        int[] masks = new int[60];
        for (int i = 0; i < masks.length; i++) {
            masks[i] = fixture.peekRecordingInputAt(i);
        }
        for (int mask : masks) {
            step(fixture, mask);
        }
        assertFalse(dustActive(), "loc_64222 deleted the dust");
        CompositeSnapshot expected = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(start);
            assertTrue(dustActive(), "restore recreates the dust");
            for (int mask : masks) {
                step(fixture, mask);
            }
            TestS3kHpzKnucklesFightHeadless.assertSnapshotsEqual(expected, registry.capture(),
                    "dust replay " + cycle);
        }
    }

    private static void step(HeadlessTestFixture fixture, int mask) {
        fixture.stepFrame((mask & AbstractPlayableSprite.INPUT_UP) != 0,
                (mask & AbstractPlayableSprite.INPUT_DOWN) != 0,
                (mask & AbstractPlayableSprite.INPUT_LEFT) != 0,
                (mask & AbstractPlayableSprite.INPUT_RIGHT) != 0,
                (mask & AbstractPlayableSprite.INPUT_JUMP) != 0);
    }

    private static boolean dustActive() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(o -> o instanceof HpzKnucklesDustObjectInstance && !o.isDestroyed());
    }

    private static HeadlessTestFixture seededFixture() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HPZ, 1)
                .startPosition((short) 0x112F, (short) 0x42C)
                .startPositionIsCentre()
                .withRecording(BK2)
                .withRecordingStartFrame(FIRST_MOVIE_FRAME)
                .build();
        fixture.stepFrameFromRecording();
        seed(fixture);
        return fixture;
    }

    private static String event(int row, int routine, int hp, int x, int y) {
        return String.format("%04X rt=%02X hp=%d (%04X,%04X)", row, routine, hp, x & 0xFFFF, y & 0xFFFF);
    }

    private static void seed(HeadlessTestFixture fixture) {
        AbstractPlayableSprite sonic = fixture.sprite();
        AbstractPlayableSprite tails = (AbstractPlayableSprite) GameServices.sprites().getRegisteredSidekicks().get(0);
        NativePositionOps.writeXPosResetSubpixel(sonic, 0x112F);
        NativePositionOps.writeYPosResetSubpixel(sonic, 0x42C);
        sonic.setSubpixelRaw(0x1000, 0x5000);
        sonic.setXSpeed((short) 0x600);
        sonic.setGSpeed((short) 0x600);
        short[] xs = new short[64];
        short[] ys = new short[64];
        short[] inputs = new short[64];
        byte[] status = new byte[64];
        for (int i = 0; i < 64; i++) {
            xs[i] = (short) P1_HISTORY_X[i];
            ys[i] = 0x42C;
            inputs[i] = AbstractPlayableSprite.INPUT_RIGHT;
        }
        sonic.hydrateRecordedHistory(xs, ys, inputs, status, 63);
        sonic.setRingCount(0x27);

        NativePositionOps.writeXPosResetSubpixel(tails, 0x10D5);
        NativePositionOps.writeYPosResetSubpixel(tails, 0x430);
        tails.setSubpixelRaw(0xE700, 0xD800);
        tails.setXSpeed((short) 0x600);
        tails.setGSpeed((short) 0x600);
        tails.getCpuController().setInitialState(SidekickCpuController.State.NORMAL);

        GameServices.sprites().setFrameCounter(0x0A77);
        GameServices.level().setFrameCounter(0x0A77);
        var camera = GameServices.camera();
        camera.setX((short) 0x108F);
        camera.setY((short) 0x396);
        camera.setMaxYCurrent((short) 0x394);
        GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(HpzKnucklesBossMusicObjectInstance.class::isInstance)
                .map(HpzKnucklesBossMusicObjectInstance.class::cast)
                .findFirst().orElseThrow()
                .setWaitWordForTest(120 - (0x287F - 0x2863));
    }
}
