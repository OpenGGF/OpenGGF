package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Controller-only cold arrival and both eight-hit fights through the accepted pre-ending stop. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszAct2FinalFight {
    private static final Path INPUT = Path.of("src/test/resources/routes/s3k/ssz2-final-defeat.bk2");
    // Authored input milestones, not native timings or runtime behavior gates.
    private static final int FIRST_DEFEAT = 3294;
    private static final int SUPER_ENTRY = 4016;
    private static final int LOW_HEALTH = 7023;
    private static final int FINAL_DEFEAT = 8156;
    private static final int COLD_STOP = 8703;

    @org.junit.jupiter.api.io.TempDir Path saveRoot;
    private final String previousSaveRoot = System.getProperty(com.openggf.game.save.SavePaths.ROOT_PROPERTY);

    @AfterEach void reset() {
        com.openggf.game.save.SessionSaveRequests.flushPendingSaves();
        if (previousSaveRoot == null) System.clearProperty(com.openggf.game.save.SavePaths.ROOT_PROPERTY);
        else System.setProperty(com.openggf.game.save.SavePaths.ROOT_PROPERTY, previousSaveRoot);
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @ParameterizedTest @ValueSource(ints = {320, 800})
    void bothFightsReachTheColdStopAndLowHealthAndDefeatReplay(int width) throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                (width == 320 ? WidescreenAspect.NATIVE_4_3 : WidescreenAspect.SUPER_32_9).name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        CrossGameFeatureProvider.getInstance().resetState(); SessionManager.clear();
        com.openggf.game.save.SessionSaveRequests.flushPendingSaves();
        System.setProperty(com.openggf.game.save.SavePaths.ROOT_PROPERTY, saveRoot.toString());
        var module = new com.openggf.game.sonic3k.Sonic3kGameModule();
        com.openggf.game.GameModuleRegistry.setCurrent(module);
        SessionManager.openGameplaySession(module, com.openggf.game.save.SaveSessionContext.forSlot(
                "s3k", 1, new com.openggf.game.save.SelectedTeam("knuckles", java.util.List.of()), 10, 1));
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 1)
                .withFreshLevelStartLifecycle().build();
        // Declared entry progress/clock. Positions, velocities, collisions and boss health
        // come only from production gameplay; no physics trace is opened by this test.
        GameServices.gameState().restoreS3kEmeraldProgress(java.util.Collections.nCopies(7, 3), true);
        GameServices.level().getObjectManager().initVblaCounter(410766);
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(INPUT);
        assertEquals(COLD_STOP + 1, movie.getFrameCount());
        fixture.runner().primeInputState(movie.getFrame(0));
        int hitsSeen = 0, previousHealth = 8;
        for (int frame = 1; frame <= FIRST_DEFEAT; frame++) {
            step(fixture, movie.getFrame(frame));
            assertFalse(fixture.sprite().getDead(), "cold route died at " + frame);
            var bosses = GameServices.level().getObjectManager().activeObjectsOfType(SszMechaSonicObjectInstance.class);
            if (!bosses.isEmpty()) {
                int health = bosses.getFirst().getCollisionProperty();
                if (health < previousHealth) {
                    assertEquals(previousHealth - 1, health, "each collision deals one hit");
                    hitsSeen++; previousHealth = health;
                }
            }
        }
        assertEquals(8, hitsSeen);
        assertTrue(boss().defeatedForTest());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var defeated = registry.capture();
        int[] expected = null;
        byte[] expectedRuntime = null;
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) {
                registry.restore(defeated);
                fixture.runner().primeInputState(movie.getFrame(FIRST_DEFEAT));
            }
            for (int frame = FIRST_DEFEAT + 1; frame <= SUPER_ENTRY; frame++) step(fixture, movie.getFrame(frame));
            var player = GameServices.sprites().getMainPlayable();
            assertFalse(player.getDead());
            assertFalse(boss().defeatedForTest(), "loc_7BBE0 returns with eight Super-phase hits");
            assertEquals(8, boss().getCollisionProperty());
            assertEquals(2, boss().routineForTest());
            assertFalse(player.isObjectControlled(), "forced run must return control");
            int[] observed = {boss().getX(), boss().getY(), boss().routineForTest(), boss().timerForTest(),
                    boss().xVelForTest(), boss().yVelForTest(), player.getCentreX(), player.getCentreY(),
                    GameServices.camera().getX(), GameServices.camera().getY()};
            byte[] runtime = ((SszZoneRuntimeState) GameServices.zoneRuntimeState()).captureBytes();
            if (replay == 0) { expected = observed; expectedRuntime = runtime; }
            else { assertArrayEquals(expected, observed); assertArrayEquals(expectedRuntime, runtime); }
        }
        var lowHealth = registry.capture();
        var finalDefeat = registry.capture();
        hitsSeen = 0; previousHealth = 8;
        for (int frame = SUPER_ENTRY + 1; frame <= COLD_STOP; frame++) {
            step(fixture, movie.getFrame(frame));
            assertFalse(fixture.sprite().getDead(), "Super route died at " + frame);
            int health = boss().getCollisionProperty();
            if (health < previousHealth) {
                assertEquals(previousHealth - 1, health, "each real Super collision deals one hit");
                hitsSeen++; previousHealth = health;
            }
            if (frame == LOW_HEALTH) {
                assertEquals(2, health);
                lowHealth = registry.capture();
            }
            if (frame == FINAL_DEFEAT) {
                assertEquals(0, health);
                assertTrue(boss().defeatedForTest());
                finalDefeat = registry.capture();
            }
        }
        assertEquals(8, hitsSeen);
        assertColdStop();
        assertSavedClear();
        String endState = observedState();
        for (int checkpoint = 0; checkpoint < 2; checkpoint++) {
            int start = checkpoint == 0 ? LOW_HEALTH : FINAL_DEFEAT;
            registry.restore(checkpoint == 0 ? lowHealth : finalDefeat);
            fixture.runner().primeInputState(movie.getFrame(start));
            for (int frame = start + 1; frame <= COLD_STOP; frame++) {
                step(fixture, movie.getFrame(frame));
                assertFalse(fixture.sprite().getDead(), "restored route died at " + frame);
            }
            assertColdStop();
            assertEquals(endState, observedState(), "low-health/final-defeat world replay");
            assertSavedClear();
        }
    }

    private void assertSavedClear() throws java.io.IOException {
        com.openggf.game.save.SessionSaveRequests.flushPendingSaves();
        var payload = new com.openggf.game.save.SaveManager(saveRoot).readSlotSummary("s3k", 1).payload();
        assertEquals(true, payload.get("clear"), "loc_7BCB0 SaveGame marks Knuckles' slot complete");
        assertEquals(12, payload.get("progressCode"));
        assertEquals(2, payload.get("clearState"), "engine clear-state projection with all Super emeralds");
        assertEquals("knuckles", payload.get("mainCharacter"));
        assertEquals(java.util.Collections.nCopies(7, 3), payload.get("emeraldStates"));
    }

    private static void assertColdStop() {
        var state = (SszZoneRuntimeState) GameServices.zoneRuntimeState();
        assertTrue(state.act2EndingActive(), "loc_7BCB0 published completion");
        assertTrue(state.endingRunning(), "stage4 applied its floor change");
        assertEquals(8, state.foregroundRoutine(), "excluded ending owner has not signalled the camera");
        assertEquals(0, boss().timerForTest(), "loc_7BCFC pre-allocation stop");
        assertTrue(GameServices.sprites().getMainPlayable().isObjectControlled());
        assertEquals(0, boss().getCollisionProperty());
    }

    private static String observedState() {
        var player = GameServices.sprites().getMainPlayable();
        var objects = GameServices.level().getObjectManager();
        var result = new StringBuilder().append(player.getCentreX()).append(',')
                .append(player.getCentreY()).append(',').append(GameServices.camera().getX())
                .append(',').append(GameServices.camera().getY()).append(',').append(objects.getVblaCounter());
        objects.getActiveObjects().stream().filter(o -> !o.isDestroyed())
                .map(o -> o.getClass().getName() + ":" + o.getX() + "," + o.getY())
                .sorted().forEach(value -> result.append('|').append(value));
        for (int line = 0; line < 4; line++) for (int color = 0; color < 16; color++) {
            result.append('|').append(com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                    GameServices.level().getCurrentLevel().getPalette(line).getColor(color)));
        }
        return result.append('|').append(java.util.HexFormat.of().formatHex(
                ((SszZoneRuntimeState) GameServices.zoneRuntimeState()).captureBytes())).toString();
    }

    private static SszMechaSonicObjectInstance boss() {
        return GameServices.level().getObjectManager().activeObjectsOfType(SszMechaSonicObjectInstance.class).getFirst();
    }

    private static void step(HeadlessTestFixture fixture, Bk2FrameInput input) {
        int mask = input.p1InputMask();
        fixture.stepFrame((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0,
                (mask & 8) != 0, (mask & 16) != 0);
        for (var mecha : GameServices.level().getObjectManager()
                .activeObjectsOfType(SszMechaSonicObjectInstance.class)) {
            assertTrue(mecha.isHighPriority(), "ObjSlot_MechaSonic keeps art_tile bit15 through power-down");
            assertEquals(0, mecha.getTileOcclusionPaletteMask(), "the island must not mask Mecha");
        }
    }
}
