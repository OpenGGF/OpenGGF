package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.CheckpointState;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.SSZHPZTeleporterObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Sky Sanctuary branch of {@code Obj_SSZHPZTeleporter} ($79, sonic3k.asm:90929-91243).
 *
 * <p>Every expectation is taken from the routine or decoded from the ROM inside the test, never
 * from the engine's own copy of a table. The placement rows come from {@code SSZ1_Sprites}
 * ({@code $1F90EE}) decoded here; the slope table from {@code byte_466E8} ({@code $466E8}); the
 * light colours from {@code word_4670C} ({@code $4670C}).
 *
 * <p>Two of these cases make a declared seeded write: they set {@code Events_bg+$00} negative to
 * model a GHZ recreation that slice 5 of the bring-up plan has not built yet. The write is what
 * {@code Obj_SSZGHZBoss}'s defeat does ({@code st (Events_bg+$00).w}), it is stated in the test
 * name, and nothing else in the case depends on the boss existing.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszTeleporterPads {

    /** {@code SSZ1_Sprites}, resolved through {@code SpriteLocPtrs} index {@code $0A * 2 + 0}. */
    private static final int SSZ1_SPRITES = 0x1F90EE;
    /** {@code word_4670C}. */
    private static final int LIGHT_TABLE = 0x04670C;
    /** {@code byte_466E8}: {@code moveq #$23,d1} makes the centre sample index {@code $23 >> 1}. */
    private static final int SLOPE_TABLE = 0x0466E8;
    /** {@code move.w #$20,y_vel(a0)}. */
    private static final int GATED_SINK = 0x20;
    /** {@code loc_4577E}. */
    private static final int LAUNCH_MIN_Y = -0x100;
    private static final int LAUNCH_MAX_Y = 0x1000;
    /** {@code loc_45804}: {@code subi.w #$10,y_pos(a1)} once per {@code $2D(a0)} step. */
    private static final int RISE_STEP = 0x10;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    /** One {@code SSZ1_Sprites} record: {@code sub_1BA0C} X word, Y word, ID byte, subtype byte. */
    private record Placement(int x, int y, int id, int subtype) {}

    private static List<Placement> teleporterPlacements() throws IOException {
        RomByteReader rom = rom();
        List<Placement> found = new ArrayList<>();
        int address = SSZ1_SPRITES;
        while (true) {
            int x = rom.readU16BE(address);
            if (x == 0xFFFF) {
                break;
            }
            int id = rom.readU8(address + 4);
            if (id == 0x79) {
                found.add(new Placement(x, rom.readU16BE(address + 2) & 0xFFF,
                        id, rom.readU8(address + 5)));
            }
            address += 6;
        }
        return found;
    }

    /**
     * The five receiving pads, the three plain launch pads and the two boss-gated pads the ROM
     * places, all resolving to the teleporter class rather than a placeholder.
     */
    @Test
    void everyRomPlacedPadResolvesToTheTeleporterClass() throws IOException {
        List<Placement> placements = teleporterPlacements();
        assertEquals(10, placements.size(), "$79 records in SSZ1_Sprites");
        // loc_45744's andi.w #$3F is the whole of the SSZ lift decode.
        assertEquals(List.of(0x00, 0x00, 0xAA, 0x15, 0x00, 0x1E, 0x00, 0xF6, 0x00, 0x32),
                placements.stream().map(Placement::subtype).toList(),
                "the subtypes SSZ1_Sprites carries, in list order");

        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x100, 0xC00);
        fixture.stepIdleFrames(4);
        // The arrival pad at ($100,$C70) is the one Load_Sprites has in range from the start.
        SSZHPZTeleporterObjectInstance pad = padNear(0x100, 0xC70);
        assertNotNull(pad, "$79:$00 at ($100,$C70) is a teleporter, not a placeholder");
        assertEquals(0, activePlaceholderCount(), "no SSZ $79 placeholder is left in range");
    }

    /**
     * {@code loc_4554E}: a pad whose subtype is negative starts {@code $20} px below its placement
     * Y and stays inert while its boss flag is not yet negative.
     */
    @Test
    void aGatedPadStartsSunkAndInertWhileItsBossFlagIsClear() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x200, 0x820);
        fixture.stepIdleFrames(8);
        SSZHPZTeleporterObjectInstance pad = padNear(0x200, 0x870 + GATED_SINK);
        assertNotNull(pad, "$79:$AA at ($200,$870)");
        assertEquals(0x870 + GATED_SINK, pad.getY() & 0xFFFF,
                "move.w #$20,y_vel(a0) then y_pos = $16(a0) + $1A(a0)");

        int before = pad.getY() & 0xFFFF;
        fixture.stepIdleFrames(60);
        assertEquals(before, pad.getY() & 0xFFFF,
                "loc_45640 returns while tst.b (Events_bg+$00).w is not negative");
    }

    /**
     * {@code loc_45640} with a <em>declared seeded</em> {@code st (Events_bg+$00).w}, which is
     * exactly what {@code Obj_SSZGHZBoss}'s defeat writes: the pad then rises one pixel every
     * fourth {@code Level_frame_counter} tick until it is flush with its placement Y.
     */
    @Test
    void aGatedPadRisesOnePixelEveryFourthTickAfterASeededBossDefeat() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x200, 0x820);
        fixture.stepIdleFrames(8);
        SSZHPZTeleporterObjectInstance pad = padNear(0x200, 0x870 + GATED_SINK);
        assertNotNull(pad);

        state().setEventsBgByte(0x00, -1);
        // Four frames per pixel; $20 pixels is $80 frames, plus slack for the tick phase.
        int risenAt = -1;
        for (int frame = 0; frame < 4 * GATED_SINK + 8; frame++) {
            fixture.stepIdleFrames(1);
            if ((pad.getY() & 0xFFFF) == 0x870) {
                risenAt = frame;
                break;
            }
        }
        assertTrue(risenAt >= 0, "the pad reaches its placement Y");
        assertTrue(risenAt >= 4 * GATED_SINK - 4 && risenAt <= 4 * GATED_SINK + 4,
                "subq.w #1,y_vel(a0) on (Level_frame_counter & 3) == 0 is 4 frames per pixel, "
                        + "so $20 px takes about " + (4 * GATED_SINK) + " frames, not " + risenAt);
    }

    /**
     * {@code loc_45744}-{@code loc_45790} and {@code loc_457BE}: the lift is
     * {@code (subtype & $3F) * $10}, the launch opens the whole Y wrap, locks scrolling and clears
     * {@code Events_bg+$05}.
     */
    @Test
    void theLaunchLiftsBySixteenTimesTheLowSixSubtypeBitsAndOpensTheYWrap() {
        // $79:$15 at ($1000,$7B0): $15 * $10 = $150 px.
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x1000, 0x760);
        fixture.stepIdleFrames(4);
        SSZHPZTeleporterObjectInstance pad = padNear(0x1000, 0x7B0);
        assertNotNull(pad, "$79:$15 at ($1000,$7B0)");

        SszZoneRuntimeState state = state();
        // Declared seeded: SSZ1_ScreenInit only sets Events_bg+$05 on the no-star-post path, and
        // this case enters through a checkpoint. The launch's clr.b is what is under test.
        state.setEventsBgByte(0x05, -1);
        // Declared seeded: sub_575EA returns immediately while Events_bg+$06 (the final arena) is
        // set. Without that, clearing +$05 releases the bounds machine, which re-derives
        // Camera_max_Y from word_5779A in the same frame's ScreenEvents tail and overwrites the
        // launch's $1000 — measured here as $C60. Parking it isolates loc_4577E's own writes,
        // which is what this case is about.
        state.setEventsBgByte(0x06, -1);

        int riseStartY = Integer.MIN_VALUE;
        boolean scrollLockedDuringRise = false;
        int settleStartY = Integer.MIN_VALUE;
        int previousState = SSZHPZTeleporterObjectInstance.STATE_IDLE;
        for (int frame = 0; frame < 600; frame++) {
            fixture.stepIdleFrames(1);
            int now = pad.stateForTest();
            if (previousState != SSZHPZTeleporterObjectInstance.STATE_RISING
                    && now == SSZHPZTeleporterObjectInstance.STATE_RISING) {
                riseStartY = fixture.sprite().getCentreY() & 0xFFFF;
                scrollLockedDuringRise = GameServices.camera().getFrozen();
            }
            if (previousState == SSZHPZTeleporterObjectInstance.STATE_RISING
                    && now == SSZHPZTeleporterObjectInstance.STATE_SETTLING) {
                settleStartY = fixture.sprite().getCentreY() & 0xFFFF;
                break;
            }
            previousState = now;
        }
        assertTrue(riseStartY != Integer.MIN_VALUE, "loc_45744 reached");
        assertTrue(settleStartY != Integer.MIN_VALUE, "loc_457BE ran its $2D(a0) steps out");
        assertEquals(0x15 * RISE_STEP, riseStartY - settleStartY,
                "(subtype & $3F) * $10 pixels of lift");

        assertEquals(0, state.eventsBgByte(0x05), "loc_45790 clr.b (Events_bg+$05).w");
        var camera = GameServices.camera();
        assertEquals((short) LAUNCH_MIN_Y, camera.getMinY(), "loc_4577E min_Y -$100");
        assertEquals(LAUNCH_MAX_Y, camera.getMaxY() & 0xFFFF, "loc_4577E max_Y $1000");
        assertEquals(LAUNCH_MAX_Y, camera.getMaxYTarget() & 0xFFFF,
                "loc_4577E Camera_target_max_Y_pos");
        // loc_45790 sets Scroll_lock at the launch and loc_457BE clears it again when the last
        // $10 step is taken, so it is only observable during the rise itself.
        assertTrue(scrollLockedDuringRise, "loc_45790 st (Scroll_lock).w during the rise");
        assertFalse(camera.getFrozen(), "loc_457BE clr.b (Scroll_lock).w at the top of the rise");
    }

    /**
     * {@code sub_45866}'s zone {@code $A} branch writes one {@code word_4670C} longword — two
     * entries in table order — into palette line 2 colours {@code $C} and {@code $D}. HPZ's
     * branch writes the same table to line 3 colours 1-2 in the opposite order, so a shared
     * write would be wrong in one zone or the other.
     */
    @Test
    void theTransportLightCycleWritesTheRomColoursToPaletteLineTwo() throws IOException {
        RomByteReader rom = rom();
        int[] table = new int[20];
        for (int index = 0; index < table.length; index++) {
            table[index] = rom.readU16BE(LIGHT_TABLE + index * 2);
        }

        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x1000, 0x760);
        fixture.stepIdleFrames(4);
        SSZHPZTeleporterObjectInstance pad = padNear(0x1000, 0x7B0);
        assertNotNull(pad);

        boolean matched = false;
        for (int frame = 0; frame < 600 && !matched; frame++) {
            fixture.stepIdleFrames(1);
            if (pad.stateForTest() == SSZHPZTeleporterObjectInstance.STATE_IDLE) {
                continue;
            }
            Palette line = GameServices.level().getCurrentLevel().getPalette(2);
            for (int index = 0; index + 1 < table.length && !matched; index += 2) {
                if (sameColour(line.colors[0xC], table[index])
                        && sameColour(line.colors[0xD], table[index + 1])) {
                    matched = true;
                }
            }
        }
        assertTrue(matched,
                "Normal_palette_line_3+$18 holds a word_4670C longword pair during a transport");
    }

    /**
     * The {@code andi.w #$3F,d0} in {@code loc_45744} is only observable on a subtype whose high
     * bits are set, i.e. one of the two gated pads — the three plain pads ({@code $15}, {@code $1E},
     * {@code $32}) are all below {@code $40} and pass through the mask unchanged. This case takes
     * the {@code $AA} pad, seeds its GHZ flag (declared, as above), waits for it to surface and
     * launches from it: {@code $AA & $3F = $2A}, so the lift is {@code $2A0} and not {@code $AA0}.
     */
    @Test
    void theGatedPadLiftUsesOnlyTheLowSixSubtypeBits() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x200, 0x820);
        // Seeded before the pad's init pass: loc_4556A's tst.b (a1) / bmi skips the $20 sink
        // entirely when the flag is already negative, so the pad is flush and solid from the
        // first frame and the player can be dropped straight onto it. Events_bg+$06 parks
        // sub_575EA as in the case above.
        SszZoneRuntimeState state = state();
        state.setEventsBgByte(0x00, -1);
        state.setEventsBgByte(0x06, -1);
        fixture.stepIdleFrames(8);
        SSZHPZTeleporterObjectInstance pad = padNear(0x200, 0x870);
        assertNotNull(pad, "$79:$AA at ($200,$870)");

        int riseStartY = Integer.MIN_VALUE;
        int settleStartY = Integer.MIN_VALUE;
        int previousState = SSZHPZTeleporterObjectInstance.STATE_IDLE;
        for (int frame = 0; frame < 1200; frame++) {
            fixture.stepIdleFrames(1);
            int now = pad.stateForTest();
            if (previousState != SSZHPZTeleporterObjectInstance.STATE_RISING
                    && now == SSZHPZTeleporterObjectInstance.STATE_RISING) {
                riseStartY = fixture.sprite().getCentreY() & 0xFFFF;
            }
            if (previousState == SSZHPZTeleporterObjectInstance.STATE_RISING
                    && now == SSZHPZTeleporterObjectInstance.STATE_SETTLING) {
                settleStartY = fixture.sprite().getCentreY() & 0xFFFF;
                break;
            }
            previousState = now;
        }
        assertTrue(riseStartY != Integer.MIN_VALUE,
                "the pad surfaces and then launches the player standing on it");
        assertTrue(settleStartY != Integer.MIN_VALUE, "the rise runs its $2D(a0) steps out");
        assertEquals(0x2A * RISE_STEP, riseStartY - settleStartY,
                "($AA & $3F) * $10 = $2A0 pixels of lift, not $AA * $10");
    }

    /** {@code loc_455BA}: the pad at {@code ($1A40,$670)} is the Mecha Sonic spawner, not a pad. */
    @Test
    void theSpawnerPadTakesTheMechaSonicBranchAndIsNeverSolid() {
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x1A40, 0x620);
        fixture.stepIdleFrames(8);
        SSZHPZTeleporterObjectInstance pad = padNear(0x1A40, 0x670);
        assertNotNull(pad, "$79:$00 at ($1A40,$670)");
        assertEquals(SSZHPZTeleporterObjectInstance.STATE_MECHA_SPAWNER, pad.stateForTest(),
                "x_pos >= $1A00 && y_pos < $680 installs loc_45A72");
        // The pad at ($1500,$CF0) is below $680 in Y but left of $1A00 in X, so it is ordinary.
        assertNull(padNear(0x1500, 0xCF0), "out of Load_Sprites range from this checkpoint");
    }

    /** {@code byte_466E8} is what the engine hands the sloped-solid routine. */
    @Test
    void theSlopeTableIsTheRomTable() throws IOException {
        RomByteReader rom = rom();
        HeadlessTestFixture fixture = bootAtCheckpoint(320, 0x1000, 0x760);
        fixture.stepIdleFrames(4);
        SSZHPZTeleporterObjectInstance pad = padNear(0x1000, 0x7B0);
        assertNotNull(pad);
        byte[] slope = pad.getSlopeData();
        assertEquals(0x24, slope.length, "moveq #$23,d1 makes the index reach $23");
        for (int index = 0; index < slope.length; index++) {
            assertEquals(rom.readU8(SLOPE_TABLE + index), slope[index] & 0xFF,
                    "byte_466E8[" + index + "]");
        }
    }

    private static boolean sameColour(Palette.Color actual, int romWord) {
        Palette.Color expected = new Palette.Color();
        expected.fromSegaFormat(romWord);
        return expected.r == actual.r && expected.g == actual.g && expected.b == actual.b;
    }

    private static RomByteReader rom() throws IOException {
        return RomByteReader.fromRom(RomManager.getInstance().getRom());
    }

    private static SszZoneRuntimeState state() {
        return S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
    }

    private static SSZHPZTeleporterObjectInstance padNear(int x, int y) {
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return null;
        }
        for (ObjectInstance instance : manager.getActiveObjects()) {
            if (instance instanceof SSZHPZTeleporterObjectInstance pad && !pad.isDestroyed()
                    && (pad.getX() & 0xFFFF) == x && Math.abs((pad.getY() & 0xFFFF) - y) <= 0x40) {
                return pad;
            }
        }
        return null;
    }

    private static int activePlaceholderCount() {
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return 0;
        }
        int count = 0;
        for (ObjectInstance instance : manager.getActiveObjects()) {
            if (!instance.isDestroyed()
                    && instance.getClass().getSimpleName().equals("PlaceholderObjectInstance")
                    && instance.getName() != null && instance.getName().contains("Teleporter")) {
                count++;
            }
        }
        return count;
    }

    /**
     * Enters act 1 through the star-post path so {@code SSZ1_ScreenInit} skips the arrival
     * ({@code tst.b (Last_star_post_hit).w}) and the pads can be reached directly.
     */
    private static HeadlessTestFixture bootAtCheckpoint(int width, int x, int y) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                WidescreenAspect.NATIVE_4_3.name());
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
            checkpoint.saveCheckpoint(1, x, y, false);
        }
        return fixture;
    }
}
