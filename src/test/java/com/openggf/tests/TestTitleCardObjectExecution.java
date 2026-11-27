package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.OscillationManager;
import com.openggf.game.OscillationSnapshot;
import com.openggf.game.PlayableEntity;
import com.openggf.game.TitleCardProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.session.EngineTiming;
import com.openggf.game.sonic1.resources.Sonic1PlcService;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.game.session.SessionManager;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies each game's level-object execution policy while the title card is
 * on screen.
 *
 * <p>ROM parity:
 * <ul>
 *   <li>S1 {@code Level_TtlCardLoop} (sonic.asm:2811-2839) calls
 *       {@code ExecuteObjects} while object RAM still contains the title-card
 *       objects. Level objects are populated later by {@code ObjPosLoad}, then
 *       receive one {@code ExecuteObjects} pass immediately before
 *       {@code Level_MainLoop}.</li>
 *   <li>S2 {@code Level_TtlCard} (s2.asm:4914-4924) calls
 *       {@code RunObjects} every iteration of the title-card wait loop.</li>
 *   <li>S3K title-card wait loop at {@code loc_62CC} (sonic3k.asm:7769-7780)
 *       calls {@code Process_Sprites} every iteration.</li>
 * </ul>
 *
 * <p>Locked arrival waits advance the VBlank clock while player physics,
 * level objects and the world clock stay parked. The release checks cover
 * the S1 object prelude and S2 leave loop before the first ordinary wait.
 *
 * <p>Each {@code @Test} method opens its own ROM (and the test is skipped
 * via {@link Assumptions} when that ROM is not available locally). This
 * keeps the three games in one class so each can be enabled or skipped
 * independently.
 */
@Isolated
class TestTitleCardObjectExecution {

    private static final int FRAMES_TO_STEP = 5;

    @AfterEach
    void cleanup() {
        SessionManager.clear();
        GameModuleRegistry.reset();
        RomManager.getInstance().setRom(null);
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    @Test
    void titleOnlyWaitAndRelease_s1Ghz1() throws IOException {
        // S1's locked loop executes title-card objects, not the already-loaded
        // level objects represented by ObjectManager. Those level objects must
        // remain fresh until the gameplay handoff pass.
        File romFile = RomTestUtils.ensureSonic1RomAvailable();
        Assumptions.assumeTrue(romFile != null, "Sonic 1 ROM not available — skipping test");
        runTitleCardAdvancementCheck(SonicGame.SONIC_1, romFile, 0, 0, false,
                /* expectObjectAdvance */ false, /* expectLevelAdvance */ false,
                /* checkSonic1ReleaseHandoff */ true);
    }

    @Test
    void titleOnlyWaitAndRelease_s2Ehz1() throws IOException {
        // S2: the level's placed objects and the players are both created by
        // the s2.asm:5003-5004 ObjectsManager / InitPlayers pair, which runs
        // after the Level_TtlCard scroll-in loop at :4946-4957. The RunObjects
        // that loop dispatches therefore sees only the Obj34 title-card pieces,
        // so no level-object pass may run while the card is sliding in.
        File romFile = RomTestUtils.ensureSonic2RomAvailable();
        Assumptions.assumeTrue(romFile != null, "Sonic 2 ROM not available — skipping test");
        runTitleCardAdvancementCheck(SonicGame.SONIC_2, romFile, 0, 0, false,
                /* expectObjectAdvance */ false, /* expectLevelAdvance */ false,
                /* checkSonic1ReleaseHandoff */ false);
    }

    @Test
    void titleOnlyWaitAndRelease_s3kAiz1() throws IOException {
        // S3K's native wait dispatches title-card SSTs. The provider represents
        // those sprites, so loaded level objects stay fresh while VBlank ticks.
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        Assumptions.assumeTrue(romFile != null, "Sonic 3&K ROM not available — skipping test");
        // Preserve the fresh-AIZ intro's absent initial owner so enterTitleCard
        // below creates the one title-card owner exercised by this test.
        runTitleCardAdvancementCheck(SonicGame.SONIC_3K, romFile, 0, 0, false,
                /* expectObjectAdvance */ false, /* expectLevelAdvance */ false,
                /* checkSonic1ReleaseHandoff */ false);
    }

    /**
     * Loads the requested ROM and level, switches the GameLoop into TITLE_CARD,
     * steps N frames, and asserts the per-game expected behaviour of the
     * title-card branch. None of the three advances a level-object pass during
     * the card's locked slide-in; the per-game release handoffs are checked
     * after the stepped window.
     */
    private void runTitleCardAdvancementCheck(SonicGame game, File romFile,
                                              int zone, int act,
                                              boolean skipIntros,
                                              boolean expectObjectsToAdvance,
                                              boolean expectLevelFrameCounterToAdvance,
                                              boolean checkSonic1ReleaseHandoff) throws IOException {
        // 1. Load the requested ROM and configure the matching game module.
        Rom rom = new Rom();
        assertTrue(rom.open(romFile.getAbsolutePath()),
                "ROM file must open: " + romFile.getAbsolutePath());
        TestEnvironment.configureRomFixture(rom);

        if (skipIntros) {
            SonicConfigurationService.getInstance()
                    .setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        }

        // 2. Load a real level via the headless fixture (also creates the player).
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(zone, act)
                .build();

        LevelManager levelManager = GameServices.level();
        ObjectManager objectManager = levelManager.getObjectManager();
        assertNotNull(objectManager, "level load should produce an object manager");
        var camera = fixture.camera();
        CameraCallSpy cameraCallSpy = new CameraCallSpy();
        LockedPhaseProbe lockedPhaseProbe = new LockedPhaseProbe(camera.getX(), camera.getY());
        objectManager.addDynamicObject(lockedPhaseProbe);

        // 3. Build a GameLoop bound to the active gameplay mode, then move it
        //    into TITLE_CARD mode using the production entry point.
        InputHandler inputHandler = new InputHandler();
        GameLoop loop = new GameLoop(inputHandler);
        loop.setGameplayMode(SessionManager.getCurrentGameplayMode());

        // Force the active title card provider into a fresh non-released
        // state so the title card stays locked while we step frames.
        TitleCardProvider provider = GameServices.module().getTitleCardProvider();
        assertNotNull(provider, "game module must expose a title card provider");
        if (game == SonicGame.SONIC_3K) {
            assertFalse(provider.shouldRunLevelObjectsDuringLockedPhase(),
                    "S3K's locked card dispatches title-card SSTs, not loaded level objects");
            assertTrue(provider.shouldAdvanceVblankClockDuringLockedPhase(),
                    "S3K's locked card still advances the production VBlank clock");
        }
        provider.reset();

        // The headless fixture replaces the bootstrap EngineContext after the
        // session attached its managers. Bind the new carrier to the same live
        // ObjectManager that production services during this test.
        var carrier = EngineTiming.vIntRunCounter();
        carrier.bindObjectClock(() -> Integer.toUnsignedLong(objectManager.getVblaCounter()));
        int clockBeforeEntry = objectManager.getVblaCounter();
        loop.enterTitleCard(zone, act);
        assertEquals(clockBeforeEntry, objectManager.getVblaCounter(),
                "staging the provider alone does not own a wait");
        assertEquals(GameMode.TITLE_CARD, loop.getCurrentGameMode(),
                "loop should be in TITLE_CARD mode after enterTitleCard()");

        // 4. Snapshot both counters before stepping any frames.
        int objectFramesBefore = objectManager.getFrameCounter();
        Supplier<NemesisPlcQueueSnapshot> titlePlc = prepareTitlePlc(game);
        int patternsBeforeWait = titlePlc == null ? 0 : remainingPatterns(titlePlc.get());
        int levelFramesBefore = levelManager.getFrameCounter();
        int spriteFramesBefore = GameServices.sprites().getFrameCounter();
        ObjectInstance trackedObject = lockedPhaseProbe;
        int trackedObjectXBefore = trackedObject.getX();
        int trackedObjectYBefore = trackedObject.getY();
        String trackedObjectStateBefore = trackedObject.traceDebugDetails();
        int cameraXBefore = camera.getX();
        int cameraYBefore = camera.getY();
        int cameraMinXBefore = camera.getMinX();
        int cameraMaxXBefore = camera.getMaxX();
        int cameraMinYBefore = camera.getMinY();
        int cameraMaxYBefore = camera.getMaxY();
        OscillationSnapshot oscillationBefore = OscillationManager.snapshot();
        long rngSeedBefore = GameServices.rng().getSeed();
        camera.setUpdateObserver(cameraCallSpy);

        // 5. Step exactly FRAMES_TO_STEP frames while in TITLE_CARD mode.
        //    The provider's reset()/initialize() above guarantees the
        //    title-card animation starts from its first locked frame, so all
        //    of the requested steps should run inside the locked-phase branch.
        for (int i = 0; i < FRAMES_TO_STEP; i++) {
            assertEquals(GameMode.TITLE_CARD, loop.getCurrentGameMode(),
                    "loop should remain in TITLE_CARD mode for the duration of the test "
                            + "(frame=" + i + ", game=" + game + ")");
            int beforeWait = objectManager.getVblaCounter();
            loop.step();
            if (i == 0 && titlePlc != null) {
                assertTrue(remainingPatterns(titlePlc.get()) < patternsBeforeWait,
                        "the wait services the ROM-backed title PLC queue");
            }
            assertEquals(beforeWait + 1, objectManager.getVblaCounter(),
                    game + " locked title wait " + (i + 1) + " owns exactly one V-int");
            assertEquals(Integer.toUnsignedLong(objectManager.getVblaCounter()),
                    carrier.value(), "the live object clock owns this service");
        }

        // 6. Assert each game's production-owned counter policy.
        int objectFramesAfter = objectManager.getFrameCounter();
        int levelFramesAfter = levelManager.getFrameCounter();
        if (game != SonicGame.SONIC_3K) {
            assertEquals(FRAMES_TO_STEP, cameraCallSpy.positionUpdates,
                    "the S1/S2 wait preserves the forced camera update");
        }
        int objectDelta = objectFramesAfter - objectFramesBefore;
        int levelDelta = levelFramesAfter - levelFramesBefore;

        assertEquals(expectObjectsToAdvance ? FRAMES_TO_STEP : 0, objectDelta,
                "Unexpected ObjectManager.frameCounter delta during the title card for " + game);

        if (game == SonicGame.SONIC_3K) {
            assertEquals(trackedObjectXBefore, trackedObject.getX());
            assertEquals(trackedObjectYBefore, trackedObject.getY());
            assertEquals(trackedObjectStateBefore, trackedObject.traceDebugDetails(),
                    "loaded level-object routine/debug state must remain fresh");
            assertEquals(cameraXBefore, camera.getX());
            assertEquals(cameraYBefore, camera.getY());
            assertEquals(cameraMinXBefore, camera.getMinX());
            assertEquals(cameraMaxXBefore, camera.getMaxX());
            assertEquals(cameraMinYBefore, camera.getMinY());
            assertEquals(cameraMaxYBefore, camera.getMaxY(),
                    "locked S3K must not run camera position or boundary easing");
            assertEquals(0, cameraCallSpy.positionUpdates,
                    "locked S3K must not invoke camera.updatePosition");
            assertEquals(0, cameraCallSpy.boundaryEasingUpdates,
                    "locked S3K must not invoke camera.updateBoundaryEasing");
            assertOscillationEquals(oscillationBefore, OscillationManager.snapshot(),
                    "locked S3K must not advance or suppress global oscillation");
            assertEquals(rngSeedBefore, GameServices.rng().getSeed(),
                    "locked S3K must not consume gameplay RNG");

            OscillationSnapshot beforeRelease = null;
            int levelFrameBeforeRelease = -1;
            int guard = 600;
            while (loop.getCurrentGameMode() == GameMode.TITLE_CARD && guard-- > 0) {
                beforeRelease = OscillationManager.snapshot();
                levelFrameBeforeRelease = levelManager.getFrameCounter();
                loop.step();
            }
            assertEquals(GameMode.LEVEL, loop.getCurrentGameMode(),
                    "S3K title card should release within the test guard");
            assertNotNull(beforeRelease);
            assertEquals(levelFrameBeforeRelease, levelManager.getFrameCounter(),
                    "release must exit after the setup-only pass");
            OscillationSnapshot afterRelease = OscillationManager.snapshot();
            assertOscillationEquals(beforeRelease, afterRelease,
                    "release must not advance oscillation during the setup-only pass");
            assertEquals(0, cameraCallSpy.positionUpdates,
                    "release must not run the ordinary camera path");
            assertEquals(0, cameraCallSpy.boundaryEasingUpdates,
                    "release must not run boundary easing");

            loop.step();
            assertEquals(levelFrameBeforeRelease + 1, levelManager.getFrameCounter(),
                    "the next ordinary iteration runs exactly one level frame");
            afterRelease = OscillationManager.snapshot();
            OscillationManager.restore(beforeRelease);
            // LevelManager publishes the phase for the pass that is about to
            // consume it, so the ordinary frame keyed as
            // levelFrameBeforeRelease + 1 -- the counter value asserted just
            // above -- is the one this update models
            // (LevelManager.advanceGlobalOscillationForNextPass()).
            OscillationManager.update(levelFrameBeforeRelease + 1);
            OscillationSnapshot expectedAfterOneUpdate = OscillationManager.snapshot();
            OscillationManager.restore(afterRelease);
            assertOscillationEquals(expectedAfterOneUpdate, afterRelease,
                    "the first unlocked frame must perform exactly one ordinary oscillator update");
            assertEquals(beforeRelease.suppressedUpdates(), afterRelease.suppressedUpdates(),
                    "the VBlank-only locked path must not leak oscillator suppression");
            assertEquals(1, cameraCallSpy.positionUpdates,
                    "the next ordinary frame performs one normal camera position update");
            assertEquals(1, cameraCallSpy.boundaryEasingUpdates,
                    "the first unlocked frame performs one normal boundary-easing update");
        }

        if (expectLevelFrameCounterToAdvance) {
            // S2 only: full LevelFrameStep also advances level frame state
            // so parallax, water dynamics, and zone features stay in sync.
            assertTrue(levelDelta >= FRAMES_TO_STEP,
                    "LevelManager.frameCounter must advance by at least " + FRAMES_TO_STEP
                            + " during the title card on the LevelFrameStep path (game="
                            + game + "); was " + levelDelta);
        } else {
            // S1/S3K keep the loaded level lifecycle stopped during the
            // locked card. S1 retains its forced camera step; S3K advances
            // only the provider-owned VBlank clock.
            assertEquals(0, levelDelta,
                    "LevelManager.frameCounter must NOT advance during the title card "
                            + "on the legacy minimal path (game=" + game + ")");
        }

        if (game == SonicGame.SONIC_2) {
            // Run the rest of the card and count the level-object passes that
            // land while it is still up. The ROM window is the single
            // RunObjects at s2.asm:5006 plus the 25 iterations of the leave
            // loop at :5092-5098 -- the pass counts of Obj34_LeftPartOut (5),
            // Obj34_BottomPartOut (11) and Obj34_BackgroundOut (9) at
            // :27558-27644 -- so 26 passes between InitPlayers and
            // Level_MainLoop.
            int passesWhileCardUp = objectDelta;
            int guard = 2000;
            while (loop.getCurrentGameMode() == GameMode.TITLE_CARD && guard-- > 0) {
                passesWhileCardUp = objectManager.getFrameCounter() - objectFramesBefore;
                int beforeWait = objectManager.getVblaCounter();
                loop.step();
                assertEquals(beforeWait + 1, objectManager.getVblaCounter(),
                        "S2 arrival, upload and leave iterations each represent one V-int");
            }
            assertEquals(GameMode.LEVEL, loop.getCurrentGameMode(),
                    "S2 title card should release within the test guard");
            assertEquals(25, passesWhileCardUp,
                    "the release iteration must still execute the final locked object pass");
            assertEquals(26, objectManager.getFrameCounter() - objectFramesBefore,
                    "S2 releases after all 26 native passes without another VBlank");
            // Level_frame_counter is cleared at s2.asm:4772, and Level_MainLoop
            // increments it at :5124 -- one instruction AFTER its own
            // bsr.w WaitForVint at :5123. That wait is the console frame the
            // release iteration ends on, so the counter is still 0 when the
            // title-card flag clears and reads 1 only once Level_MainLoop's
            // first pass has resumed from the wait.
            assertEquals(0, GameServices.sprites().getFrameCounter(),
                    "the release iteration ends on Level_MainLoop's WaitForVint "
                            + "(s2.asm:5091), before its addq at :5092");
            int beforeGameplay = objectManager.getVblaCounter();
            loop.step();
            assertEquals(beforeGameplay + 1, objectManager.getVblaCounter(),
                    "the first S2 gameplay wait owns a separate V-int");
            assertEquals(1, GameServices.sprites().getFrameCounter(),
                    "Level_MainLoop's first pass past the wait performs the addq");
        }

        if (checkSonic1ReleaseHandoff) {
            AbstractPlayableSprite player = fixture.sprite();
            player.setGSpeed((short) 0);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setAngle((byte) 0);
            player.setSubpixelRaw(0, 0);
            player.setForcedInputMask(AbstractPlayableSprite.INPUT_RIGHT);
            int guard = 600;
            while (loop.getCurrentGameMode() == GameMode.TITLE_CARD && guard-- > 0) {
                int beforeWait = objectManager.getVblaCounter();
                loop.step();
                assertEquals(beforeWait + 1, objectManager.getVblaCounter(),
                        "the S1 arrival wait owns the V-int even when its tail releases control");
            }
            assertEquals(GameMode.LEVEL, loop.getCurrentGameMode(),
                    "title card should release within the test guard");
            // Level_LoadObj's ExecuteObjects (sonic.asm:2896) has no
            // WaitForVBlank of its own; id_VBlank_Levels is first set by
            // Level_MainLoop (:3031-3035). The release iteration therefore owns
            // the prelude pass alone, and the first Level_MainLoop pass belongs
            // to the following iteration.
            assertEquals(1, objectManager.getFrameCounter() - objectFramesBefore,
                    "S1 release must run the native level-object prelude and nothing else");
            assertEquals(0, GameServices.sprites().getFrameCounter(),
                    "v_framecount is cleared at sonic.asm:2915, after the prelude pass, so the "
                            + "prelude is not a Level_MainLoop row");
            assertEquals(0, player.getGSpeed(),
                    "the locked release prelude must ignore a stale forced-input mask");
            assertEquals(0, player.getXSubpixelRaw(),
                    "the locked release prelude must not move the player");

            int beforeGameplay = objectManager.getVblaCounter();
            loop.step();
            assertEquals(beforeGameplay + 1, objectManager.getVblaCounter(),
                    "the first S1 gameplay wait owns a separate V-int");
            assertEquals(2, objectManager.getFrameCounter() - objectFramesBefore,
                    "the first Level_MainLoop iteration runs exactly one more object pass");
            assertEquals(1, GameServices.sprites().getFrameCounter() - spriteFramesBefore,
                    "Level_MainLoop's addq at sonic.asm:3002 makes this the first level frame");
            assertEquals(0x0C, player.getGSpeed(),
                    "the first unlocked Level_MainLoop pass consumes held Right");
            assertEquals(0x0B00, player.getXSubpixelRaw(),
                    "only the first unlocked Level_MainLoop pass may consume held Right, with slope projection");
        }
    }

    private static Supplier<NemesisPlcQueueSnapshot> prepareTitlePlc(SonicGame game)
            throws IOException {
        if (game == SonicGame.SONIC_1) {
            var plc = GameServices.module().getGameService(Sonic1PlcService.class);
            plc.resetForMissingSnapshot();
            plc.append(1);
            plc.prepare();
            return plc::capture;
        }
        if (game == SonicGame.SONIC_2) {
            var plc = GameServices.module().getGameService(Sonic2PlcService.class);
            plc.resetForMissingSnapshot();
            plc.append(com.openggf.game.sonic2.constants.Sonic2Constants.PLC_STD2);
            plc.prepare();
            return plc::capture;
        }
        return null;
    }

    private static int remainingPatterns(NemesisPlcQueueSnapshot queue) {
        int active = queue.activeEntry() == null ? 0 : queue.activeEntry().remainingPatterns();
        return active + queue.queuedEntries().stream()
                .mapToInt(NemesisPlcQueueSnapshot.Entry::remainingPatterns).sum();
    }

    private static void assertOscillationEquals(
            OscillationSnapshot expected, OscillationSnapshot actual, String message) {
        assertArrayEquals(expected.values(), actual.values(), message);
        assertArrayEquals(expected.deltas(), actual.deltas(), message);
        assertArrayEquals(expected.activeSpeeds(), actual.activeSpeeds(), message);
        assertArrayEquals(expected.activeLimits(), actual.activeLimits(), message);
        assertEquals(expected.control(), actual.control(), message);
        assertEquals(expected.lastFrame(), actual.lastFrame(), message);
        assertEquals(expected.suppressedUpdates(), actual.suppressedUpdates(), message);
    }

    private static final class CameraCallSpy implements Camera.UpdateObserver {
        private int positionUpdates;
        private int boundaryEasingUpdates;

        @Override
        public void onUpdatePosition(boolean force) {
            positionUpdates++;
        }

        @Override
        public void onUpdateBoundaryEasing() {
            boundaryEasingUpdates++;
        }
    }

    private static final class LockedPhaseProbe extends AbstractObjectInstance {
        private int updates;

        private LockedPhaseProbe(int x, int y) {
            super(new ObjectSpawn(x, y, 0, 0, 0, false, y), "LockedPhaseProbe");
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            updates++;
        }

        @Override
        public String traceDebugDetails() {
            return "updates=" + updates;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // State-only title-card dispatch sentinel.
        }
    }
}
