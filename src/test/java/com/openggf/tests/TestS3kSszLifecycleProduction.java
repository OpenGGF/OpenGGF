package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.SszArrivalControllerObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 4: what a Sky Sanctuary act-1 death and checkpoint restart does to the act's own state.
 *
 * <p>Two ROM clears own the answer and they are in different routines. {@code LevelSetup}
 * (sonic3k.asm:102185) runs {@code clr.l (Events_bg+$00/$04/$08/$0C).w} on <em>every</em> load, so
 * a beaten boss is not remembered across a respawn — nothing skips the arenas, the star posts
 * simply sit past them. And {@code Level:} runs {@code clearRAM _unkFA80,$80} (sonic3k.asm:7623),
 * which covers {@code _unkFA82} (the EggRobo pairing word), {@code _unkFA8A}, {@code _unkFAA2},
 * {@code _unkFAA4} and {@code _unkFAB0..B8}: none of those has a clear of its own anywhere in the
 * SSZ code, and that block clear is why they start at zero. The engine expresses both by building
 * a fresh {@link SszZoneRuntimeState} per load, which is only faithful if the load really does
 * replace it — so the test drives a real {@code applyPitDeath} through {@code GameLoop} rather
 * than constructing a state.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszLifecycleProduction {

    /** {@code SSZ1_Sprites}. */
    private static final int SSZ1_SPRITES = 0x1F90EE;
    /** {@code $34:$03} at {@code ($14C0,$E8)}: the star post past the Metropolis arena. */
    private static final int STAR_POST_X = 0x14C0;
    private static final int STAR_POST_Y = 0x00E8;
    private static final int STAR_POST_INDEX = 3;
    /** {@code sub_575EA}'s Metropolis pre-lock: {@code Camera_min_X} when the GHZ word is zero. */
    private static final int MTZ_PRELOCK_MIN_X = 0x0160;
    private static final int MTZ_PRELOCK_MAX_X = 0x1660;
    /** The native segment that is itself a {@code $34:$03} restart. Comparison-only. */
    private static final String NATIVE_PHYSICS =
            "src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/hpz_2/"
                    + "physics.csv.gz";

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    /** The three {@code $34} rows the inventory claims, decoded from the ROM. */
    @Test
    void theRomPlacesThreeStarPostsWhereTheInventorySaysItDoes() throws IOException {
        RomByteReader rom = RomByteReader.fromRom(RomManager.getInstance().getRom());
        List<int[]> posts = new ArrayList<>();
        int address = SSZ1_SPRITES;
        while (rom.readU16BE(address) != 0xFFFF) {
            if (rom.readU8(address + 4) == 0x34) {
                posts.add(new int[] {rom.readU16BE(address), rom.readU16BE(address + 2) & 0xFFF,
                        rom.readU8(address + 5)});
            }
            address += 6;
        }
        assertEquals(3, posts.size(), "SSZ1 places three star posts");
        assertArrayEquals3(new int[] {0x0640, 0x05E8, 0x02}, posts.get(0));
        assertArrayEquals3(new int[] {STAR_POST_X, STAR_POST_Y, STAR_POST_INDEX}, posts.get(1));
        assertArrayEquals3(new int[] {0x1880, 0x0968, 0x04}, posts.get(2));
        // Each post sits past an arena rather than inside one: the Green Hill lock is at
        // Camera_X $160 with the player below $880, Metropolis at $1660 above $440.
        assertTrue(posts.get(1)[1] < 0x0440,
                "$34:$03 is above the Metropolis lock's P1-Y threshold, so sub_575EA takes that "
                        + "branch on a restart there");
    }

    /**
     * A real death and checkpoint reload at {@code $34:$03}. Both clears are exercised from a
     * seeded pre-death state: {@code Events_bg+$00} negative is what a beaten Green Hill boss
     * looks like ({@code st (Events_bg+$00).w} at {@code Obj_SSZGHZBoss}'s defeat), and a set
     * {@code _unkFA82} bit is what a passed EggRobo fly-by looks like. Neither survives.
     */
    @Test
    void theDeathReloadClearsTheEventFlagsAndThePairingWordAndSkipsTheArrival() {
        HeadlessTestFixture fixture = bootRestartingAtTheStarPost();
        var checkpoint = GameServices.level().getCheckpointState();
        assertNotNull(checkpoint, "the act has a checkpoint state");
        assertTrue(checkpoint.isActive(), "the declared restart is live");
        assertEquals(STAR_POST_INDEX, checkpoint.getLastCheckpointIndex(),
                "the star post's own subtype is its index");

        InputHandler input = new InputHandler();
        var neutral = new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, "");
        input.setLogicalOverride(
                com.openggf.debug.playback.RecordedInputSnapshots.fromBk2(neutral, neutral));
        GameLoop loop = new GameLoop(input);
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        try {
            for (int frame = 0; frame < 30; frame++) {
                fixture.gameplayMode().getFadeManager().update();
                loop.step();
            }
            var objects = GameServices.level().getObjectManager();
            SszZoneRuntimeState before = state();
            // Declared pre-death stimulus, both of which a naive "keep the zone state" would carry.
            before.setEventsBgByte(0x00, 0xFF);
            before.markEggRoboFlyByPassed(6);
            assertTrue(before.eventsBgByte(0x00) < 0, "a beaten Green Hill boss before the death");
            assertTrue(before.eggRoboFlyByPassed(6), "a passed fly-by before the death");

            assertTrue(GameServices.sprites().getMainPlayable().applyPitDeath(), "the leader dies");
            int largestOutgoingFrame = 0;
            boolean loaded = false;
            for (int frame = 0; frame < 1200 && !loaded; frame++) {
                fixture.gameplayMode().getFadeManager().update();
                loop.step();
                var rewind = fixture.gameplayMode().getRewindController();
                if (GameServices.level().getObjectManager() != objects) {
                    loaded = true;
                    assertEquals(Sonic3kZoneIds.ZONE_SSZ, GameServices.level().getCurrentZone());
                    assertEquals(0, GameServices.level().getCurrentAct());
                    assertEquals(STAR_POST_INDEX,
                            GameServices.level().getCheckpointState().getLastCheckpointIndex(),
                            "the restart is at the star post, not the act start");

                    SszZoneRuntimeState after = state();
                    assertNotSame(before, after, "the load builds a new zone runtime state");
                    for (int offset = 0; offset < SszZoneRuntimeState.EVENTS_BG_BYTES; offset++) {
                        assertEquals(0, after.eventsBgByte(offset),
                                "LevelSetup clr.l (Events_bg+$" + Integer.toHexString(offset)
                                        + ").w: a beaten boss is not remembered");
                    }
                    assertEquals(0, after.eggRoboFlyByBits(),
                            "clearRAM _unkFA80,$80 takes the EggRobo pairing word with it");
                    assertEquals(0, after.carriedObjectSlot(),
                            "and _unkFAA4, which lives in the same block");

                    assertNull(arrivalController(),
                            "SSZ1_ScreenInit's forced block is behind tst.b Last_star_post_hit");
                    assertEquals(0, after.eventsBgByte(0x05),
                            "no Events_bg+$05, so sub_575EA is live from the first frame");
                    assertTrue(largestOutgoingFrame > 10,
                            "the pre-death run built a timeline to isolate");
                    assertTrue(rewind == null || rewind.currentFrame() < largestOutgoingFrame,
                            "the reload resets the timeline instead of continuing the old one");
                } else if (rewind != null) {
                    largestOutgoingFrame = Math.max(largestOutgoingFrame, rewind.currentFrame());
                }
            }
            assertTrue(loaded, "GameLoop reloaded the act");

            // Let the title card and fade finish, then read the bounds sub_575EA publishes.
            for (int frame = 0; frame < 500; frame++) {
                fixture.gameplayMode().getFadeManager().update();
                loop.step();
                var title = GameServices.module().getTitleCardProvider();
                if (loop.getCurrentGameMode() == GameMode.LEVEL
                        && (title == null || title.isComplete())
                        && !fixture.gameplayMode().getFadeManager().isActive()
                        && !GameServices.sprites().getMainPlayable().isControlLocked()) {
                    break;
                }
            }
            assertFalse(GameServices.sprites().getMainPlayable().getDead(), "the leader is back");
            var camera = GameServices.camera();
            assertEquals(MTZ_PRELOCK_MIN_X, camera.getMinX() & 0xFFFF,
                    "sub_575EA's Metropolis branch: min_X is $160 because the Green Hill word is "
                            + "zero after the load, not 0 as it would be if the boss were "
                            + "remembered");
            assertEquals(MTZ_PRELOCK_MAX_X, camera.getMaxX() & 0xFFFF,
                    "and max_X is the arena's left edge");
            assertTrue(camera.isVerticalWrapEnabled(), "Levels_1000_High keeps the act wrapping");
            assertEquals(0x1000, camera.getVerticalWrapRange(), "over $1000");
        } finally {
            loop.closePresence();
        }
    }

    /**
     * Native corroboration for the restart this class declares, comparison-only. The
     * {@code s3k-sonic-tails-complete-emeralds/hpz_2} segment ({@code zone_id 10}, movie offset
     * 460334) <em>is</em> a {@code $34:$03} restart: its metadata declares
     * {@code start_x 0x14C0 / start_y 0x00E8}, the placement's own coordinates, and its row 0 —
     * recorded after one frame — reads the settled standing position and the camera that follows
     * it. The engine's first frame from the same declared restart has to agree.
     *
     * <p>It also corrects the fixture table in this campaign's plan, which describes {@code hpz_2}
     * as carrying a "second death": the segment has exactly one {@code player_routine 00} span.
     */
    @Test
    void theNativeStarPostRestartRowAgreesWithTheEnginesFirstFrame() throws Exception {
        List<String[]> rows = readNativeRows();
        assertEquals(4352, rows.size(), "hpz_2 row count");
        String[] first = rows.get(0);
        int nativePlayerX = Integer.parseInt(first[9], 16);
        int nativePlayerY = Integer.parseInt(first[10], 16);
        int nativeCameraX = Integer.parseInt(first[2], 16);
        int nativeCameraY = Integer.parseInt(first[3], 16);
        assertEquals(STAR_POST_X, nativePlayerX, "the segment really starts at $34:$03's X");

        int deathSpans = 0;
        boolean inDeath = false;
        for (String[] row : rows) {
            boolean dead = "00".equals(row[20]);
            if (dead && !inDeath) {
                deathSpans++;
            }
            inDeath = dead;
        }
        assertEquals(1, deathSpans,
                "hpz_2 carries one death/reload, not the two the plan's fixture table claims");

        HeadlessTestFixture fixture = bootRestartingAtTheStarPost();
        var player = GameServices.sprites().getMainPlayable();
        var camera = GameServices.camera();
        assertEquals(nativePlayerX, player.getCentreX() & 0xFFFF, "Player_1 x_pos");
        assertEquals(nativePlayerY, player.getCentreY() & 0xFFFF, "Player_1 y_pos");
        assertEquals(nativeCameraX, camera.getX() & 0xFFFF, "Camera_X_pos");
        assertEquals(nativeCameraY, camera.getY() & 0xFFFF, "Camera_Y_pos");
        assertFalse(player.isObjectControlled(),
                "SSZ1_ScreenInit's object_control 3 is on the no-starpost path only");
    }

    private static List<String[]> readNativeRows() throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (var in = new java.io.BufferedReader(new java.io.InputStreamReader(
                new java.util.zip.GZIPInputStream(java.nio.file.Files.newInputStream(
                        java.nio.file.Path.of(NATIVE_PHYSICS)))))) {
            in.readLine();
            for (String line; (line = in.readLine()) != null; ) {
                rows.add(line.split(","));
            }
        }
        return rows;
    }

    private static void assertArrayEquals3(int[] expected, int[] actual) {
        assertEquals(expected[0], actual[0], "x");
        assertEquals(expected[1], actual[1], "y");
        assertEquals(expected[2], actual[2], "subtype");
    }

    private static SszZoneRuntimeState state() {
        return S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
    }

    private static SszArrivalControllerObjectInstance arrivalController() {
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return null;
        }
        for (ObjectInstance instance : manager.getActiveObjects()) {
            if (instance instanceof SszArrivalControllerObjectInstance controller
                    && !controller.isDestroyed()) {
                return controller;
            }
        }
        return null;
    }

    /**
     * <b>Declared setup, not a route.</b> {@code SSZ1_ScreenInit} reads
     * {@code Last_star_post_hit} on the load's first pass and, with it clear, hands Player 1 to
     * {@code Obj_57C1E} under {@code object_control 3} — so a leader placed near {@code $34:$03}
     * with no checkpoint is dragged back to the arrival column before he can touch anything. The
     * restart is therefore written the way the ROM writes it and the way
     * {@code GameplayCaptureTool --star-post} does: {@code Last_star_post_hit} and
     * {@code Saved_X/Y} at the post's own placement, index {@code $03}.
     */
    private static HeadlessTestFixture bootRestartingAtTheStarPost() {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withSkippedZoneIntro()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 0)
                .startPosition((short) STAR_POST_X, (short) STAR_POST_Y)
                .startPositionIsCentre()
                .withFreshLevelStartLifecycle()
                .build();
        if (GameServices.level().getCheckpointState()
                instanceof com.openggf.game.CheckpointState state) {
            state.saveCheckpoint(STAR_POST_INDEX, STAR_POST_X, STAR_POST_Y, false);
        }
        fixture.stepIdleFrames(1);
        return fixture;
    }
}
