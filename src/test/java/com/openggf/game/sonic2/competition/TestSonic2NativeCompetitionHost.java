package com.openggf.game.sonic2.competition;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameResult;
import com.openggf.LevelFrameStep;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.CharacterKey;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.competition.NativePlayerDeathPass;
import com.openggf.game.internal.PlayerDplcDmaSink;
import com.openggf.game.nativemenu.NativeMenuController;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.snapshot.CameraSnapshot;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.objects.SignpostObjectInstance;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.sonic2.bumpers.CNZBumperDataLoader;
import com.openggf.game.sonic2.bumpers.CNZBumperType;
import com.openggf.game.sonic2.bumpers.CNZNativeBumperTable;
import com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceVram;
import com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceVramService;
import com.openggf.game.sonic2.competition.lifecycle.Sonic2CompetitionLifecycle;
import com.openggf.game.sonic2.objects.Sonic2GameOverCardObjectInstance;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.Level;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real ROM/native-world assembly and positioned signpost interactions on owned EGL.
 * No native field is drawn or qualified here, and the waypoint setup is not a route replay.
 */
@Isolated
@EnabledOnOs(OS.LINUX)
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2NativeCompetitionHost {
    private static final String GL_PROPERTY = "openggf.headless.gl";
    private static final String OBJECT_STREAMING = "native_competition_object_streaming";
    private static final String RING_STREAMING = "native_competition_ring_streaming";
    private static final String NATIVE_BUMPERS = "sonic2_native_cnz_bumpers";
    private static final LogicalInputSnapshot NEUTRAL = LogicalInputSnapshot.neutral();

    @Test
    void sixRealActsKeepIndependentHumansCanonicalTimersRewindAndTheActLedger() throws Exception {
        assertNull(System.getenv("DISPLAY"), "launch this EGL test with DISPLAY unset");
        assertNull(System.getenv("WAYLAND_DISPLAY"), "launch this EGL test with WAYLAND_DISPLAY unset");
        String previousGl = System.getProperty(GL_PROPERTY);
        var services = EngineServices.current();
        var configuration = services.configuration();
        var previousConfiguration = new EnumMap<SonicConfiguration, Object>(SonicConfiguration.class);
        for (var key : SonicConfiguration.values())
            previousConfiguration.put(key, configuration.getConfigValue(key));
        try {
            System.setProperty(GL_PROPERTY, "surfaceless-egl");
            configuration.setConfigValue(SonicConfiguration.AUDIO_ENABLED, false);
            configuration.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
            configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            configuration.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            configuration.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
            configuration.resolveDisplayAspect();
            try (var boot = new HeadlessGameBoot(320, 224, services)) {
                var loop = boot.boot(RomTestUtils.ensureSonic2RomAvailable().toPath().toAbsolutePath(), 0, 0);
                assertInstanceOf(NullAudioBackend.class, services.audio().getBackend());
                var rootModule = SessionManager.getCurrentWorldSession().rootGameModule();
                var ordinaryActor = SessionManager.getCurrentGameplayMode().getSpriteManager().getSprite("sonic");
                var ordinaryMode = SessionManager.getCurrentGameplayMode();
                var unboundSession = new Sonic2CompetitionSession();
                startThroughMenu(unboundSession, 0);
                var unboundBefore = unboundSession.capture();
                var unbound = new Sonic2NativeCompetitionHost(rootModule, services,
                        loop::setGameplayMode, new UnqualifiedViews(), new UnavailableNativeState());
                assertThrows(IllegalStateException.class, () -> unbound.loadAct(unboundSession, 0, 0));
                assertSame(ordinaryMode, SessionManager.getCurrentGameplayMode(),
                        "missing native wait input must fail before retiring the active ordinary world");
                assertEquals(value(unboundBefore), value(unboundSession.capture()));
                assertNull(unboundSession.player(0));
                assertNull(unboundSession.player(1));
                assertNull(unbound.profile());
                assertNull(unbound.nativeArt());
                int[][] zones = {{0, 0, 135, 158}, {11, 1, 130, 148}, {12, 2, 187, 168}};
                for (int[] zone : zones) {
                    String savedCharacter = zone[0] == 11 ? "knuckles" : "tails";
                    configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, savedCharacter);
                    var session = new Sonic2CompetitionSession();
                    var views = new UnqualifiedViews();
                    var host = new Sonic2NativeCompetitionHost(rootModule, services,
                            loop::setGameplayMode, views, new UnavailableNativeState());
                    var input = new CountingInput();
                    var driver = new Driver(host, session, input);
                    var screen = new Sonic2CompetitionScreen(session, host, null, services.roms().getRom());
                    var menus = new NativeMenuController();
                    assertTrue(menus.open(screen));
                    assertFalse(screen.supported(), "the controller fixture cannot qualify missing native views/special stages");
                    startThroughMenu(session, zone[1]);
                    assertFalse(host.supportsZone(zone[0]));
                    var entrySample = LogicalInputSnapshot.ofPlayers(
                            PlayerInputState.of(AbstractPlayableSprite.INPUT_UP | AbstractPlayableSprite.INPUT_RIGHT,
                                    AbstractPlayableSprite.INPUT_RIGHT, 0, 0, false, false),
                            PlayerInputState.of(AbstractPlayableSprite.INPUT_DOWN | AbstractPlayableSprite.INPUT_LEFT,
                                    AbstractPlayableSprite.INPUT_LEFT, 0, 0, false, false));
                    input.setLogicalOverride(entrySample);
                    host.loadAct(session, zone[0], 0);
                    assertEquals(25, driver.nativeSamples, "each real title wait must sample the shared input once");
                    assertEquals(0, input.updates, "the native wait sample port never advances outer input");
                    assertEquals(Sonic2CompetitionPause.convertLogical(entrySample.player1()),
                            host.nativePause().player1ControllerWord());
                    assertEquals(Sonic2CompetitionPause.convertLogical(entrySample.player2()),
                            host.nativePause().player2ControllerWord());
                    assertEquals(Sonic2CompetitionPause.Continuation.ENTRY, host.nativePause().continuation());
                    assertEquals(savedCharacter, configuration.getString(SonicConfiguration.MAIN_CHARACTER_CODE),
                            "native P1 selection must not rewrite the saved campaign character");
                    var firstContext = host.context();
                    var stableArtCoordinator = firstContext.runtimeArtCoordinator();
                    var firstArtOwner = nativeVramOwner(host, session);
                    assertNotSame(ordinaryActor, session.player(0), "native assembly supplies a fresh human body");
                    assertNativeAssembly(host, session, zone[0], 0, zone[2]);
                    verifyIndependentHumansAndTimers(driver);
                    if (zone[0] == 0) verifyNativePauseAndContinuation(driver, menus, screen, views);
                    assertEquals(savedCharacter, configuration.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
                    if (zone[0] == 0) verifyRegistryRestoreAndForwardReplay(driver);
                    if (zone[0] == 0) verifySpeedWordsAcrossDestructiveRestore(driver);
                    verifyNativeRingCollection(driver);
                    if (zone[0] == 0) verifyNativeP2DeathAndRespawn(driver);
                    if (zone[0] == 0) earnP1BadnikScoreForOverallTie(driver);
                    if (zone[0] == 12) verifyNativeP2BumpersAndRewind(driver);

                    // Replay both arrival orders from the same actual pre-goal registry capture,
                    // then replay the chosen branch for the act-two handoff.
                    finishAtPhysicalSignpost(driver, zone[0] == 11 ? 1 : 0, zone[0] == 0);
                    var actOneResult = session.actResult(zone[1], 0);
                    assertNotNull(actOneResult);
                    if (zone[0] == 0) {
                        assertOverallTiePrerequisites(session);
                        assertTrue(session.resultMetrics(0).timerFrames() < session.resultMetrics(1).timerFrames());
                        assertEquals(new Sonic2CompetitionResults.Wins(2, 2), actOneResult,
                                "real badnik/time wins and real current/total ring wins must earn an overall tie");
                    }
                    int lives = session.gameState(0).getLives();
                    var sonic = session.player(0);
                    var tails = session.player(1);
                    for (int slot = 0; slot < 2; slot++) {
                        var actor = session.player(slot);
                        actor.setXSpeed((short) (0x123 + slot));
                        actor.setYSpeed((short) (-0x234 - slot));
                        actor.setGSpeed((short) (0x345 + slot));
                        actor.setAngle((byte) (0x36 + slot));
                    }
                    advanceActThroughCompletedResultsFrame(driver, menus, screen);
                    for (int slot = 0; slot < 2; slot++) {
                        var actor = session.player(slot);
                        assertEquals(0, actor.getXSpeed(), "act load clears native x_vel before the initial pass");
                        assertEquals(0, actor.getYSpeed(), "act load clears native y_vel before the initial pass");
                        assertEquals(0, actor.getGSpeed(), "act load clears native inertia before the initial pass");
                        assertEquals(0, actor.getAngle(), "act load clears the native floor angle");
                    }
                    assertEquals(savedCharacter, configuration.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
                    assertSame(firstContext, host.context(), "act two must retain the same world and policy ledger");
                    assertSame(stableArtCoordinator, host.context().runtimeArtCoordinator(),
                            "the context retains its original DPLC/runtime-art port across acts");
                    var secondArtOwner = nativeVramOwner(host, session);
                    assertNotSame(firstArtOwner, secondArtOwner, "each act must select a fresh physical VRAM owner");
                    assertNotSame(firstArtOwner.plcService(), secondArtOwner.plcService(),
                            "each native owner must retain the fresh PLC created for its actual act");
                    assertSame(sonic, session.player(0));
                    assertSame(tails, session.player(1));
                    assertEquals(actOneResult, session.actResult(zone[1], 0));
                    assertEquals(lives, session.gameState(0).getLives());
                    assertEquals(0, session.levelState(0).getTimerFrames());
                    assertEquals(0, session.levelState(1).getTimerFrames());
                    assertNativeAssembly(host, session, zone[0], 1, zone[3]);
                    var freshActTwo = host.context().getRewindRegistry().capture();
                    verifyIndependentHumansAndTimers(driver);
                    verifyNativeRingCollection(driver);
                    if (zone[0] == 12) {
                        verifyNativeP2BumpersAndRewind(driver);
                        var beforeTimeout = host.context().getRewindRegistry().capture();
                        WorldState before = state(driver);
                        verifyCanonicalTimeOverRequests(driver);
                        host.context().getRewindRegistry().restore(beforeTimeout);
                        assertRestoredRoster(driver);
                        assertEquals(before, state(driver), "actual timeout/death must restore before the goal branch");
                    }
                    finishAtPhysicalSignpost(driver, zone[0] == 11 ? 1 : 0);
                    assertNotNull(session.actResult(zone[1], 1));
                    verifyFreshActSimultaneousArrival(driver, freshActTwo);
                    assertFalse(host.supportsZone(zone[0]), "logic checks cannot qualify missing native views/results");
                    assertTrue(views.preparations > 0);
                    assertEquals(0, views.draws);
                }
                configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
                boot.reboot(RomTestUtils.ensureSonic2RomAvailable().toPath().toAbsolutePath(), 0, 0);
                var ordinary = SessionManager.getCurrentGameplayMode();
                assertNull(NativeCompetitionRoster.from(ordinary.getWorldSession()));
                assertEquals(CharacterKey.TAILS, ordinary.getSpriteManager().getMainPlayable().characterKey(),
                        "ordinary campaign assembly must still honor the saved main character");
                assertEquals("tails", configuration.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
            }
        } finally {
            TestEnvironment.resetAll();
            previousConfiguration.forEach(configuration::setConfigValue);
            configuration.resolveDisplayAspect();
            if (previousGl == null) System.clearProperty(GL_PROPERTY);
            else System.setProperty(GL_PROPERTY, previousGl);
        }
    }

    private static void startThroughMenu(Sonic2CompetitionSession session, int selection) {
        if ((selection & 1) != 0) session.updateMenu(directions(0, AbstractPlayableSprite.INPUT_RIGHT), true);
        if ((selection & 2) != 0) session.updateMenu(directions(0, AbstractPlayableSprite.INPUT_DOWN), true);
        session.updateMenu(startPress(), true);
        assertEquals(Sonic2CompetitionSession.Phase.ACT, session.phase());
    }

    private static void advanceActThroughCompletedResultsFrame(Driver driver, NativeMenuController menus,
                                                               Sonic2CompetitionScreen screen) {
        var session = driver.session;
        var host = driver.host;
        var mode = host.context();
        var outgoingObjects = mode.getObjectManager();
        int outgoingPasses = outgoingObjects.getFrameCounter();
        var input = driver.input;
        int initialUpdates = input.updates;
        int initialSamples = driver.nativeSamples;
        input.setLogicalOverride(startPress());
        screen.resultsPlcReady();
        mode.plcFrameLifecycle().runLogicalIteration(() -> { }, frame -> {
            assertTrue(frame.claim(PlcLifecyclePhase.TWO_PLAYER_RESULTS));
            assertEquals(NativeMenuScreen.Exit.NONE, menus.update(input, false));
            assertEquals(initialUpdates + 1, input.updates, "the actual controller advances input exactly once");
            assertEquals(Sonic2CompetitionSession.Phase.ACT, session.phase());
            assertEquals(1, session.act());
            assertEquals(0, host.profile().act(), "screen update must only queue the next native act");
            assertSame(outgoingObjects, mode.getObjectManager());
            assertEquals(outgoingPasses, outgoingObjects.getFrameCounter(), "the results token cannot run entry objects");
            for (int slot = 0; slot < 2; slot++)
                assertEquals(0x123 + slot, session.player(slot).getXSpeed(), "actor initialization is also deferred");
            frame.prepareAfterLoop(PlcLifecyclePhase.TWO_PLAYER_RESULTS);
            return null;
        });
        // runLogicalIteration finished the outgoing token. The real afterFrame
        // owner can now run all 26 entry walks on this same context's lifecycle.
        input.setLogicalOverride(NEUTRAL);
        assertTrue(menus.completeFrame());
        assertEquals(1, host.profile().act());
        assertSame(mode, host.context());
        assertEquals(26, mode.getObjectManager().getFrameCounter());
        assertFalse(menus.completeFrame(), "the controller consumes completion once");
        assertEquals(26, mode.getObjectManager().getFrameCounter());
        assertEquals(initialUpdates + 1, input.updates, "afterFrame does not advance the outgoing input again");
        assertEquals(initialSamples + 25, driver.nativeSamples);
        assertEquals(0, host.nativePause().player1ControllerWord());
        assertEquals(0, host.nativePause().player2ControllerWord());
    }

    private static final class CountingInput extends InputHandler {
        private int updates;
        @Override public void update() { updates++; super.update(); }
    }

    private static LogicalInputSnapshot startPress() {
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(),
                PlayerInputState.of(0, 0, 0, 0, true, true));
    }

    private static LogicalInputSnapshot directions(int p1, int p2) {
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(p1, p1, 0, 0, false, false),
                PlayerInputState.of(p2, p2, 0, 0, false, false));
    }

    private static void assertNativeAssembly(Sonic2NativeCompetitionHost host, Sonic2CompetitionSession session,
            int zone, int act, int expectedObjects) {
        assertEquals(zone, host.profile().romZone());
        assertEquals(act, host.profile().act());
        assertEquals(expectedObjects, host.profile().objects().size());
        assertEquals(0, host.context().getLevelManager().getFrameCounter(), "title entry holds native level cadence");
        assertEquals(0, host.context().getSpriteManager().getFrameCounter());
        assertEquals(26, host.context().getObjectManager().getFrameCounter(), "leading plus 25 real RunObjects walks");
        Level level = host.context().getLevelManager().getCurrentLevel();
        assertEquals(zone, level.getZoneIndex());
        assertEquals(host.profile().objects(), level.getObjects());
        assertEquals(host.profile().rings(), level.getRings());
        assertEquals(256, level.getBlockCount());
        assertEquals(768, level.getChunkCount());
        assertArrayEquals(host.nativeArt().baseTerrain().layoutBytes(), level.getMap().getData());
        byte[] rawBlocks = host.nativeArt().baseTerrain().chunkBytes();
        for (int block = 0; block < level.getBlockCount(); block++) {
            int[] expected = new int[64];
            for (int i = 0; i < expected.length; i++) {
                int address = block * 128 + i * 2;
                expected[i] = (rawBlocks[address] & 255) << 8 | rawBlocks[address + 1] & 255;
            }
            assertArrayEquals(expected, level.getBlock(block).saveState());
        }
        if (zone == 0) assertEquals(0x4280, level.getChunk(0x1788 / 8).getPatternDesc(0, 0).get());
        if (zone == 12) {
            assertEquals(act == 0 ? 0x1802A : 0x18492, host.profile().objectAddress());
            assertEquals(0x03A8, level.getChunk(0x1760 / 8).getPatternDesc(0, 0).get());
        }
        assertEquals(CharacterKey.SONIC, session.player(0).characterKey());
        assertEquals(CharacterKey.TAILS, session.player(1).characterKey());
        assertFalse(session.player(0).isCpuControlled());
        assertFalse(session.player(1).isCpuControlled());
        assertNotSame(session.player(0), session.player(1));
        assertNotSame(session.camera(0), session.camera(1));
        assertNotSame(session.gameState(0), session.gameState(1));
        assertNotSame(session.levelState(0), session.levelState(1));
        assertSame(session.player(0), host.context().getSpriteManager().getSprite("sonic"));
        assertSame(session.player(1), host.context().getSpriteManager().getSprite("tails"));
        assertSame(session.levelState(0), host.context().getLevelManager().getLevelGamestate());
        assertSame(session.player(0), host.context().getCamera().getFocusedSprite());
        for (int slot = 0; slot < 2; slot++) {
            var actor = session.player(slot);
            assertNativeSpeedWords(actor, 0x600, 0xC, 0x80);
            assertNotNull(actor.getAnimationProfile(), "ROM animation logic must be installed for each human");
            assertNotNull(actor.getAnimationSet());
            assertNotNull(actor.getSpindashDustController());
            assertEquals(132 + slot, actor.getSpindashDustController().fixedSlotIndex(),
                    "native dust retains Sonic/Tails' separate fixed SST owners");
        }
        assertNotNull(session.player(1).getTailsTailsController());
        nativeVramOwner(host, session);
    }

    private static Sonic2InterlaceVramService nativeVramOwner(Sonic2NativeCompetitionHost host,
                                                            Sonic2CompetitionSession session) {
        var mode = host.context();
        var module = mode.getWorldSession().getGameModule();
        var owner = module.getGameService(Sonic2InterlaceVramService.class);
        assertNotNull(owner, "the runtime module must expose its selected physical VRAM owner");
        assertInstanceOf(PlayerDplcDmaSink.class, mode.runtimeArtCoordinator());
        assertNotSame(owner, mode.runtimeArtCoordinator(), "the context needs a stable forwarder across act owners");
        assertSame(owner.plcService(), module.getGameService(Sonic2PlcService.class));
        assertSame(owner.plcLifecycle(), module.getGameService(PlcLifecycleService.class));
        var snapshot = owner.capture();
        assertSame(session.player(0), snapshot.sonic(), "Sonic DMA must bind the actual current P1");
        assertSame(session.player(1), snapshot.tails(), "Tails DMA must bind the actual current human P2");
        assertSame(owner.current(), snapshot.vram());
        assertEquals(owner.plcService().capture(), snapshot.plc());
        return owner;
    }

    private static void verifyNativePauseAndContinuation(Driver driver, NativeMenuController menus,
                                                         Sonic2CompetitionScreen screen, UnqualifiedViews views) throws Exception {
        var registry = driver.host.context().getRewindRegistry();
        var original = registry.capture();
        WorldState originalState = state(driver);
        boolean previousSlowMotion = driver.slowMotion.get();
        int updatesBefore = driver.input.updates;
        try {
            assertNativePauseFlags(driver, false);
            WorldState beforeStart = state(driver);
            assertEquals(LevelFrameResult.GAMEPLAY_FRAME, driver.tick(startPress()),
                    "current P2 Start belongs to this ordinary body, before the next PauseGame entry");
            assertOneNativeBody(beforeStart, state(driver));
            assertNativePauseFlags(driver, false);
            assertEquals(0x8080, driver.host.nativePause().player2ControllerWord());
            WorldState afterStartBody = state(driver);
            assertEquals(LevelFrameResult.PAUSED, driver.tick(NEUTRAL));
            assertOneNativePauseWait(afterStartBody, state(driver));
            assertNativePauseFlags(driver, true);
            var insidePause = registry.capture();
            WorldState paused = state(driver);
            for (int wait = 0; wait < 2; wait++) {
                WorldState beforeWait = state(driver);
                assertEquals(LevelFrameResult.PAUSED, driver.tick(NEUTRAL));
                assertOneNativePauseWait(beforeWait, state(driver));
                assertEquals(paused.body(), state(driver).body(),
                        "actual pause waits freeze both actors, cameras, banks, loaders and body clocks");
                assertNativePauseFlags(driver, true);
            }
            WorldState beforeResumeWait = state(driver);
            assertEquals(LevelFrameResult.PAUSED, driver.tick(startPress()),
                    "the current P2 resume sample still belongs to a pause wait");
            assertOneNativePauseWait(beforeResumeWait, state(driver));
            assertNativePauseFlags(driver, false);
            assertEquals(Sonic2CompetitionPause.Continuation.ORDINARY_WAIT, driver.host.nativePause().continuation());
            var afterResume = registry.capture();
            WorldState resumed = state(driver);
            assertEquals(LevelFrameResult.GAMEPLAY_FRAME, driver.tick(NEUTRAL));
            WorldState expectedForward = state(driver);
            assertOneNativeBody(resumed, expectedForward);

            registry.restore(insidePause);
            assertRestoredRoster(driver);
            assertEquals(paused, state(driver));
            assertNativePauseFlags(driver, true);
            for (int wait = 0; wait < 2; wait++) {
                WorldState beforeWait = state(driver);
                assertEquals(LevelFrameResult.PAUSED, driver.tick(NEUTRAL));
                assertOneNativePauseWait(beforeWait, state(driver));
            }
            beforeResumeWait = state(driver);
            assertEquals(LevelFrameResult.PAUSED, driver.tick(startPress()));
            assertOneNativePauseWait(beforeResumeWait, state(driver));
            assertEquals(LevelFrameResult.GAMEPLAY_FRAME, driver.tick(NEUTRAL));
            assertEquals(expectedForward, state(driver), "restoring inside Pause_Loop must reproduce resume and body");
            registry.restore(afterResume);
            assertRestoredRoster(driver);
            assertEquals(resumed, state(driver));
            assertNativePauseFlags(driver, false);
            assertEquals(LevelFrameResult.GAMEPLAY_FRAME, driver.tick(NEUTRAL));
            assertEquals(expectedForward, state(driver), "the captured ordinary-wait continuation must replay once");
            assertEquals(updatesBefore, driver.input.updates, "host waits never advance the shared outer input");

            assertEquals(LevelFrameResult.GAMEPLAY_FRAME, driver.tick(startPress()));
            assertEquals(LevelFrameResult.PAUSED, driver.tick(NEUTRAL));
            var slowPause = registry.capture();
            WorldState beforeSlow = state(driver);
            driver.slowMotion.set(true);
            for (var action : List.of(
                    PlayerInputState.of(0, 0, InputActionMasks.ACTION_B, 0, false, false),
                    PlayerInputState.of(0, 0, InputActionMasks.ACTION_C, InputActionMasks.ACTION_C, false, false))) {
                registry.restore(slowPause);
                assertRestoredRoster(driver);
                assertEquals(LevelFrameResult.PAUSED,
                        driver.tick(LogicalInputSnapshot.ofPlayers(action, PlayerInputState.neutral())));
                assertOneNativePauseWait(beforeSlow, state(driver));
                assertNativePauseFlags(driver, true);
                assertEquals(Sonic2CompetitionPause.Continuation.ORDINARY_WAIT, driver.host.nativePause().continuation());
                WorldState beforeSingleBody = state(driver);
                assertEquals(LevelFrameResult.GAMEPLAY_FRAME, driver.tick(NEUTRAL));
                WorldState singleBody = state(driver);
                assertOneNativeBody(beforeSingleBody, singleBody);
                assertNativePauseFlags(driver, true);
                assertEquals(LevelFrameResult.PAUSED, driver.tick(NEUTRAL));
                assertOneNativePauseWait(singleBody, state(driver));
                assertNativePauseFlags(driver, true);
            }
            assertEquals(updatesBefore, driver.input.updates);

            // A is observed by the actual screen on its pause wait. Its title
            // command must wait for the following distinct ordinary world body.
            registry.restore(slowPause);
            var titlePress = LogicalInputSnapshot.ofPlayers(
                    PlayerInputState.of(0, 0, InputActionMasks.ACTION_A, InputActionMasks.ACTION_A, false, false),
                    PlayerInputState.neutral());
            assertEquals(NativeMenuScreen.Exit.NONE,
                    driver.screenTick(menus, titlePress, PlcLifecyclePhase.NORMAL_PAUSE));
            assertOneNativePauseWait(beforeSlow, state(driver));
            assertEquals(NativeMenuScreen.Exit.NONE, screen.consumeExit());
            assertFalse(driver.host.consumePendingTitleExit());
            assertEquals(Sonic2CompetitionPause.Exit.TITLE_SCREEN, driver.host.nativePause().pendingExit());
            assertEquals(Sonic2CompetitionPause.Continuation.ORDINARY_WAIT, driver.host.nativePause().continuation());
            assertNativePauseFlags(driver, false);
            var titleResume = registry.capture();
            WorldState beforeTitleBody = state(driver);

            // Capture the real consumable command between body completion and
            // screen consumption. A view fault after that body must not roll
            // back its completed pause continuation or consumable title flag.
            views.failNextPrepare = true;
            var viewFailure = assertThrows(IllegalStateException.class, () -> driver.tick(NEUTRAL));
            assertEquals("owned native view prepare fault", viewFailure.getMessage());
            assertFalse(views.failNextPrepare);
            WorldState afterViewFailure = state(driver);
            assertOneNativeBody(beforeTitleBody, afterViewFailure);
            assertEquals(Sonic2CompetitionPause.Continuation.EXITED, driver.host.nativePause().continuation());
            assertEquals(Sonic2CompetitionPause.Exit.TITLE_SCREEN, driver.host.nativePause().pendingExit());
            assertNativePauseFlags(driver, false);
            var pendingTitle = registry.capture();
            assertTrue(driver.host.consumePendingTitleExit());
            assertFalse(driver.host.consumePendingTitleExit());
            registry.restore(pendingTitle);
            assertTrue(driver.host.consumePendingTitleExit(), "restore must restore the unconsumed real title command");
            assertFalse(driver.host.consumePendingTitleExit());

            registry.restore(titleResume);
            assertEquals(LevelFrameResult.GAMEPLAY_FRAME, driver.tick(NEUTRAL));
            assertEquals(afterViewFailure, state(driver), "the completed body must replay identically without the view fault");
            assertTrue(driver.host.consumePendingTitleExit());
            assertFalse(driver.host.consumePendingTitleExit());

            registry.restore(titleResume);
            var titleExit = driver.screenTick(menus, NEUTRAL, PlcLifecyclePhase.ORDINARY_LEVEL);
            assertEquals(NativeMenuScreen.Destination.TITLE_SCREEN, titleExit.destination());
            assertOneNativeBody(beforeTitleBody, state(driver));
            assertEquals(NativeMenuScreen.Exit.NONE, screen.consumeExit());
            assertFalse(driver.host.consumePendingTitleExit(), "the actual screen consumes the command once");
            WorldState titleCompleted = state(driver);

            registry.restore(slowPause);
            assertRestoredRoster(driver);
            assertEquals(Sonic2CompetitionSession.Phase.ACT, driver.session.phase());
            assertEquals(beforeSlow, state(driver), "restore before the title branch must retain the live native act");
            assertNativePauseFlags(driver, true);
            assertEquals(NativeMenuScreen.Exit.NONE,
                    driver.screenTick(menus, titlePress, PlcLifecyclePhase.NORMAL_PAUSE));
            assertOneNativePauseWait(beforeSlow, state(driver));
            assertEquals(titleExit, driver.screenTick(menus, NEUTRAL, PlcLifecyclePhase.ORDINARY_LEVEL));
            assertEquals(titleCompleted, state(driver), "the real screen's restored title branch must reproduce forward state");
            assertEquals(NativeMenuScreen.Exit.NONE, screen.consumeExit());
            assertFalse(driver.host.consumePendingTitleExit());
        } finally {
            views.failNextPrepare = false;
            driver.slowMotion.set(previousSlowMotion);
            registry.restore(original);
            driver.input.setLogicalOverride(NEUTRAL);
        }
        assertRestoredRoster(driver);
        assertEquals(originalState, state(driver), "pause branch probes must restore the original six-act world");
        assertNativePauseFlags(driver, false);
    }

    private static void assertNativePauseFlags(Driver driver, boolean paused) {
        assertEquals(paused, driver.host.nativePause().gamePaused());
        for (int slot = 0; slot < 2; slot++) {
            assertEquals(paused, driver.session.gameState(slot).isGamePaused(), "both native game banks mirror Game_paused");
            assertFalse(driver.session.levelState(slot).isTimerPaused(), "pause never sets the separate finish timer latch");
        }
    }

    private static void assertOneNativeBody(WorldState before, WorldState after) {
        assertEquals(before.levelFrame() + 1, after.levelFrame());
        assertEquals(before.spriteFrame() + 1, after.spriteFrame());
        assertEquals(before.objectPass() + 1, after.objectPass());
        assertEquals(before.objectClock() + 1, after.objectClock());
        assertEquals(before.firstBank().metrics().timerFrames() + 1, after.firstBank().metrics().timerFrames());
        assertEquals(before.secondBank().metrics().timerFrames() + 1, after.secondBank().metrics().timerFrames());
    }

    private static void assertOneNativePauseWait(WorldState before, WorldState after) {
        assertEquals(before.body(), after.body(), "the native pause wait cannot execute a gameplay body");
        assertEquals(before.objectClock() + 1, after.objectClock(),
                "VintRet advances the native VInt clock once even while dispatch is paused");
    }

    private static void verifyIndependentHumansAndTimers(Driver driver) {
        driver.ticks(24, NEUTRAL);
        var session = driver.session;
        int sonicX = session.player(0).getCentreX();
        int tailsX = session.player(1).getCentreX();
        long one = session.levelState(0).getTimerFrames();
        long two = session.levelState(1).getTimerFrames();
        driver.ticks(64, directions(0, AbstractPlayableSprite.INPUT_RIGHT));
        assertEquals(sonicX, session.player(0).getCentreX(), "neutral P1 must not borrow P2's input");
        assertTrue(session.player(1).getCentreX() > tailsX + 16, "the human P2 must move from its own controller");
        assertEquals(0, session.player(0).getLogicalInputState() & AbstractPlayableSprite.INPUT_RIGHT);
        assertNotEquals(0, session.player(1).getLogicalInputState() & AbstractPlayableSprite.INPUT_RIGHT);
        assertEquals(one + 64, session.levelState(0).getTimerFrames(), "canonical timer advances once, not per view");
        assertEquals(two + 64, session.levelState(1).getTimerFrames());
        int sonicBeforeOwnInput = session.player(0).getCentreX();
        driver.ticks(32, directions(AbstractPlayableSprite.INPUT_RIGHT, 0));
        assertTrue(session.player(0).getCentreX() > sonicBeforeOwnInput + 8,
                "native P1 must also move from its own controller, including with saved Tails/Knuckles preferences");
        assertNotEquals(0, session.player(0).getLogicalInputState() & AbstractPlayableSprite.INPUT_RIGHT);
        assertEquals(0, session.player(1).getLogicalInputState() & AbstractPlayableSprite.INPUT_RIGHT,
                "neutral P2 must not borrow P1's input; its existing momentum may continue");
        assertEquals(one + 96, session.levelState(0).getTimerFrames());
        assertEquals(two + 96, session.levelState(1).getTimerFrames());
        assertFalse(session.player(0).getDead());
        assertFalse(session.player(1).getDead());
    }

    private static void verifyRegistryRestoreAndForwardReplay(Driver driver) throws Exception {
        var mode = driver.host.context();
        var snapshot = mode.getRewindRegistry().capture();
        WorldState before = state(driver);
        driver.ticks(32, directions(AbstractPlayableSprite.INPUT_RIGHT, AbstractPlayableSprite.INPUT_LEFT));
        driver.ticks(16, NEUTRAL);
        WorldState expected = state(driver);
        mode.getRewindRegistry().restore(snapshot);
        assertRestoredRoster(driver);
        assertEquals(before, state(driver), "registry restoration must restore both humans/cameras/banks/loaders");
        driver.ticks(32, directions(AbstractPlayableSprite.INPUT_RIGHT, AbstractPlayableSprite.INPUT_LEFT));
        driver.ticks(16, NEUTRAL);
        assertEquals(expected, state(driver), "restored world must reproduce the same forward input segment");
    }

    private static void assertNativeSpeedWords(AbstractPlayableSprite actor, int max, int accel, int decel) {
        assertEquals(max, com.openggf.sprites.playable.PlayableSpriteInternalAccess.nativeMaximumSpeedWord(actor));
        assertEquals(accel, com.openggf.sprites.playable.PlayableSpriteInternalAccess.nativeAccelerationWord(actor));
        assertEquals(decel, com.openggf.sprites.playable.PlayableSpriteInternalAccess.nativeDecelerationWord(actor));
        assertEquals(max, actor.getMax()); assertEquals(accel, actor.getRunAccel()); assertEquals(decel, actor.getRunDecel());
    }

    private static void verifySpeedWordsAcrossDestructiveRestore(Driver driver) {
        var mode = driver.host.context();
        var registry = mode.getRewindRegistry();
        var original = registry.capture();
        try {
            var outsider = new com.openggf.sprites.playable.Sonic("probe", (short) 100, (short) 200);
            com.openggf.sprites.playable.PlayableSpriteInternalAccess.publishNativeSpeedWords(outsider, 0, 0, 0);
            assertEquals(-1, com.openggf.sprites.playable.PlayableSpriteInternalAccess.nativeMaximumSpeedWord(outsider),
                    "a non-roster actor cannot acquire this Host's authority");
            for (int slot = 0; slot < 2; slot++) {
                var actor = driver.session.player(slot);
                actor.setHurt(false); actor.setDead(false);
                assertFalse(actor.isSuperSonic(), "this writer replay has a normal native control precondition");
                actor.updateWaterState(actor.getCentreY() - 1);
                actor.giveSpeedShoes(8);
                assertEquals(8, actor.captureRewindState().playerExtra().speedShoesRemainingTicks());
                assertNativeSpeedWords(actor, 0xC00, 0x18, 0x80);
            }
            var before = registry.capture();
            List<com.openggf.level.objects.PerObjectRewindSnapshot> expected = null;
            for (int pass = 0; pass < 2; pass++) {
                var abandonedOne = driver.session.player(0);
                var abandonedTwo = driver.session.player(1);
                if (pass != 0) {
                    mode.getSpriteManager().removeSprite(abandonedOne.getCode());
                    mode.getSpriteManager().removeSprite(abandonedTwo.getCode());
                    registry.restore(before);
                    assertNotSame(abandonedOne, driver.session.player(0));
                    assertNotSame(abandonedTwo, driver.session.player(1));
                    assertRestoredRoster(driver);
                }
                for (int slot = 0; slot < 2; slot++) {
                    var actor = driver.session.player(slot);
                    assertNativeSpeedWords(actor, 0xC00, 0x18, 0x80);
                    actor.updateWaterState(actor.getCentreY());
                    assertNativeSpeedWords(actor, 0x600, 0xC, 0x80);
                    assertTrue(actor.hasSpeedShoes());
                    actor.updateWaterState(actor.getCentreY() - 1);
                    assertNativeSpeedWords(actor, 0x300, 6, 0x40);
                    for (int tick = 0; tick < 8; tick++) actor.currentTimerManager().updateDisplayPhaseTimersFor(actor);
                    assertNativeSpeedWords(actor, 0x600, 0xC, 0x80);
                    assertTrue(actor.isInWater()); assertEquals(0x10, actor.getEffectiveGravity());
                    assertFalse(actor.hasSpeedShoes());
                    if (pass != 0) assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                            "player", expected.get(slot), actor.captureRewindState()),
                            "destructive registered restore must reproduce the full forward player state");
                }
                if (pass == 0) expected = List.of(driver.session.player(0).captureRewindState(),
                        driver.session.player(1).captureRewindState());
            }
        } finally { registry.restore(original); }
        assertRestoredRoster(driver);
    }

    private static void verifyNativeRingCollection(Driver driver) {
        var session = driver.session;
        var rings = driver.host.context().getLevelManager().getRingManager();
        var placements = driver.host.profile().rings();
        int left = Math.max(1, session.camera(1).getX() - 8);
        int right = left + 320 + 16;
        int playerY = session.player(1).getCentreY();
        String initialInterest = "zone=" + driver.host.profile().romZone() + "/act=" + driver.host.profile().act()
                + " P2camera=" + session.camera(1).getX() + " window=[" + left + "," + right + ")"
                + " placements=" + placements.size()
                + " collected=" + placements.stream().filter(rings::isCollected).count();
        var ring = placements.stream()
                .filter(spawn -> spawn.x() >= left && spawn.x() < right && !rings.isCollected(spawn))
                .min(Comparator.comparingInt(spawn -> Math.abs(spawn.y() - playerY)))
                .orElse(null);
        int first = session.levelState(0).getRings();
        int firstTotal = session.playerState(0).totalRings();
        int second = session.levelState(1).getRings();
        int secondTotal = session.playerState(1).totalRings();
        int ringClearY = placements.stream().mapToInt(spawn -> spawn.y()).min().orElse(0x100) - 0x40;
        int firstClearY = ring == null ? ringClearY : ring.y() - 0x80;
        if (ring == null) {
            int playerX = session.player(1).getCentreX();
            ring = placements.stream().filter(spawn -> !rings.isCollected(spawn))
                    .min(Comparator.comparingInt(spawn -> Math.abs(spawn.x() - playerX)))
                    .orElse(null);
            assertNotNull(ring, "expected an uncollected actual ROM ring: " + initialInterest);
            int targetCamera = Math.clamp(ring.x() - 0xA0,
                    driver.host.profile().bounds().minX(), driver.host.profile().bounds().maxX());
            int camera = session.camera(1).getX();
            int firstX = session.player(0).getCentreX();
            // Walk the existing dual loader through every native column. Keep
            // both bodies clear of all rings; only P2's camera is positioned.
            while (camera != targetCamera) {
                camera += Math.clamp(targetCamera - camera, -0x100, 0x100);
                position(session.player(0), firstX, ringClearY);
                position(session.player(1), camera + 0xA0, ringClearY);
                session.camera(1).setX((short) camera);
                driver.ticks(1, NEUTRAL);
            }
            int admittedLeft = Math.max(1, session.camera(1).getX() - 8);
            assertTrue(ring.x() >= admittedLeft && ring.x() < admittedLeft + 320 + 16,
                    "the selected ring must enter actual P2 interest: " + initialInterest
                            + " target=" + ring + " currentP2camera=" + session.camera(1).getX());
            assertEquals(first, session.levelState(0).getRings(), initialInterest);
            assertEquals(firstTotal, session.playerState(0).totalRings(), initialInterest);
            assertEquals(second, session.levelState(1).getRings(), initialInterest);
            assertEquals(secondTotal, session.playerState(1).totalRings(), initialInterest);
        }
        // Position a real human on a real loaded placement, then let the canonical
        // playable/ring pass perform collection. No ring/domain collection helper runs.
        position(session.player(0), Math.max(0x20, ring.x() - 0x40), firstClearY);
        position(session.player(1), ring.x(), ring.y());
        driver.ticks(1, NEUTRAL);
        assertTrue(rings.isCollected(ring), "the production RingManager must consume the actual placement: "
                + initialInterest + " target=" + ring + " currentP2camera=" + session.camera(1).getX()
                + " collectedNow=" + placements.stream().filter(rings::isCollected).count());
        assertTrue(session.levelState(1).getRings() > second);
        assertTrue(session.playerState(1).totalRings() > secondTotal);
        assertEquals(first, session.levelState(0).getRings(), "P2 collection must not credit P1's ring bank");
        assertEquals(firstTotal, session.playerState(0).totalRings());
    }

    /** Positioned death-row interaction, not a claim to have traversed an authored pit route. */
    private static void verifyNativeP2DeathAndRespawn(Driver driver) throws Exception {
        var mode = driver.host.context();
        var registry = mode.getRewindRegistry();
        var original = registry.capture();
        WorldState originalState = state(driver);
        var session = driver.session;
        var lifecycle = nativeLifecycle(driver.host);
        var checkpoint = lifecycle.checkpoint(1);
        assertEquals(0, checkpoint.lastStarPost(), "this death uses the actual initial respawn bank");
        assertNull(checkpoint.primaryEnvironment(), "P2 has no native camera/water checkpoint bank");
        assertEquals(driver.host.profile().tailsStart().x(), checkpoint.x());
        assertEquals(driver.host.profile().tailsStart().y(), checkpoint.y());
        var firstBank = bank(session, 0);
        var secondBank = bank(session, 1);
        assertTrue(secondBank.metrics().rings() > 0, "P2 must retain its actual prior ring pickup before death");
        try {
            var second = session.player(1);
            assertSame(lifecycle, NativePlayerDeathPass.from(second));
            session.camera(1).setX((short) (session.camera(1).getX() + 0x100));
            assertTrue(second.applyPitDeath(), "the real death owner must initiate native routine 6");
            assertEquals(6, lifecycle.nativeRoutineByte(1));
            int deathMargin = (short) EngineServices.current().roms().getRom().read16BitAddr(0x1CC88);
            NativePositionOps.writeYPosPreserveSubpixel(second, session.camera(1).getMaxY() + deathMargin + 1);
            assertEquals(LevelFrameResult.GAMEPLAY_FRAME, driver.tick(NEUTRAL));
            assertEquals(10, lifecycle.nativeRoutineByte(1));
            assertFalse(second.getDead());
            assertEquals(checkpoint.x(), second.getCentreX());
            assertEquals(checkpoint.y(), second.getCentreY());
            assertEquals(secondBank.lives() - 1, session.gameState(1).getLives());
            assertEquals(0, session.levelState(1).getRings());
            assertEquals(secondBank.metrics().totalRings(), session.playerState(1).totalRings());
            assertFalse(session.levelState(1).isTimerPaused());
            var cameraWords = lifecycle.capture().slots().get(1);
            assertTrue(cameraWords.cameraXDiff() != 0 || cameraWords.cameraYDiff() != 0,
                    "routine A must observe actual scrolling rather than an invented settle counter");
            var respawning = registry.capture();
            WorldState respawningState = state(driver);
            var forward = new ArrayList<WorldState>();
            for (int pass = 0; pass < 256 && lifecycle.nativeRoutineByte(1) != 2; pass++) {
                int x = second.getCentreX(), y = second.getCentreY();
                int subX = second.getXSubpixelRaw(), subY = second.getYSubpixelRaw();
                assertEquals(LevelFrameResult.GAMEPLAY_FRAME, driver.tick(NEUTRAL));
                assertEquals(x, second.getCentreX(), "the actual routine-A dispatch cannot execute ordinary movement");
                assertEquals(y, second.getCentreY());
                assertEquals(subX, second.getXSubpixelRaw());
                assertEquals(subY, second.getYSubpixelRaw());
                forward.add(state(driver));
            }
            assertEquals(2, lifecycle.nativeRoutineByte(1), "actual camera scrolling must release routine A");
            cameraWords = lifecycle.capture().slots().get(1);
            assertEquals(0, cameraWords.cameraXDiff());
            assertEquals(0, cameraWords.cameraYDiff());
            assertEquals(LevelFrameResult.GAMEPLAY_FRAME, driver.tick(NEUTRAL));
            forward.add(state(driver));
            assertEquals(firstBank.lives(), session.gameState(0).getLives());
            assertEquals(firstBank.metrics().score(), session.gameState(0).getScore());
            assertEquals(firstBank.metrics().rings(), session.levelState(0).getRings());
            assertEquals(firstBank.metrics().totalRings(), session.playerState(0).totalRings());
            assertEquals(firstBank.metrics().monitors(), session.playerState(0).monitorsBroken());
            assertEquals(firstBank.metrics().timerFrames() + 1 + forward.size(), session.levelState(0).getTimerFrames());
            assertEquals(secondBank.metrics().timerFrames() + 1 + forward.size(), session.levelState(1).getTimerFrames());
            assertEquals(checkpoint, lifecycle.checkpoint(1));
            assertFalse(mode.getLevelManager().isRespawnRequestedForRewind(), "P2 death cannot reload the shared world");

            registry.restore(respawning);
            assertRestoredRoster(driver);
            assertEquals(respawningState, state(driver));
            for (var expected : forward) {
                assertEquals(LevelFrameResult.GAMEPLAY_FRAME, driver.tick(NEUTRAL));
                assertEquals(expected, state(driver), "native camera-wait respawn must reproduce full forward state");
            }
        } finally {
            registry.restore(original);
        }
        assertRestoredRoster(driver);
        assertEquals(originalState, state(driver), "restore must retain P2's earned ring lead for the actual tie branch");
    }

    private static Sonic2CompetitionLifecycle nativeLifecycle(Sonic2NativeCompetitionHost host) {
        var lifecycle = host.context().getWorldSession().getGameModule().getGameService(Sonic2CompetitionLifecycle.class);
        assertNotNull(lifecycle, "the actual native world must own one lifecycle service");
        assertSame(lifecycle, Sonic2CompetitionLifecycle.from(host.context().getLevelManager()));
        return lifecycle;
    }

    private static void earnP1BadnikScoreForOverallTie(Driver driver) {
        var session = driver.session;
        var sonic = session.player(0);
        // Ring collection left P1 above the real EHZ ground. Let terrain earn
        // the grounded state, then controller A must earn the actual jump/roll.
        for (int tick = 0; tick < 180 && (sonic.getAir() || sonic.getYSpeed() != 0); tick++) {
            driver.ticks(1, NEUTRAL);
            assertFalse(sonic.getDead());
            assertFalse(session.player(1).getDead());
        }
        assertFalse(sonic.getAir(), "P1 must land through real terrain before jumping");
        assertEquals(0, sonic.getYSpeed());
        var objects = driver.host.context().getObjectManager();
        var rings = driver.host.context().getLevelManager().getRingManager();
        AbstractBadnikInstance badnik = null;
        for (int tick = 0; tick < 120 && badnik == null; tick++) {
            badnik = objects.getActiveObjects().stream()
                    .filter(AbstractBadnikInstance.class::isInstance).map(AbstractBadnikInstance.class::cast)
                    // Moving badniks publish their current coordinates through
                    // getSpawn; the decoded ROM record's identity stays stable.
                    .filter(body -> !body.isDestroyed() && driver.host.profile().objects().stream().anyMatch(placement ->
                            placement.layoutIndex() == body.getSpawn().layoutIndex()
                                    && placement.objectId() == body.getSpawn().objectId()))
                    .filter(body -> Math.abs(session.player(1).getCentreX() - body.getX()) > 0x40)
                    .filter(body -> driver.host.profile().rings().stream().noneMatch(ring -> !rings.isCollected(ring)
                            && Math.abs(ring.x() - body.getX()) < 0x20 && Math.abs(ring.y() - body.getY()) < 0x20))
                    .min(Comparator.comparingInt(body -> Math.abs(body.getX() - sonic.getCentreX())))
                    .orElse(null);
            if (badnik == null) driver.ticks(1, NEUTRAL);
        }
        assertNotNull(badnik, "expected a live ROM badnik clear of P2 and uncollected rings");
        if (!badnik.isOnScreenForTouch()) {
            // Native streaming can retain a body beyond P1's display. Bring
            // its real column into P1's camera, preserving P2's independent
            // interest. This positioned setup does not change the ROM body.
            int ringClearY = driver.host.profile().rings().stream().mapToInt(ring -> ring.y()).min()
                    .orElse(0x100) - 0x40;
            int targetCamera = Math.clamp(badnik.getX() - 0xA0,
                    driver.host.profile().bounds().minX(), driver.host.profile().bounds().maxX());
            int camera = session.camera(0).getX();
            while (camera != targetCamera) {
                camera += Math.clamp(targetCamera - camera, -0x100, 0x100);
                position(sonic, camera + 0xA0, ringClearY);
                session.camera(0).setX((short) camera);
                driver.ticks(1, NEUTRAL);
            }
            // Land beside the body, clear of the actual ring columns. Only
            // after terrain establishes normal control do we earn a jump.
            int landingX = badnik.getX() - 0x40;
            int landingMin = Math.max(0x20, badnik.getX() - 0x80);
            while (landingX >= landingMin) {
                final int candidateX = landingX;
                if (driver.host.profile().rings().stream().noneMatch(ring -> !rings.isCollected(ring)
                        && Math.abs(ring.x() - candidateX) < 0x18)) break;
                landingX--;
            }
            assertTrue(landingX >= landingMin, "expected a ring-clear terrain column beside the real badnik");
            position(sonic, landingX, ringClearY);
            for (int tick = 0; tick < 180 && (sonic.getAir() || sonic.getYSpeed() != 0); tick++) {
                driver.ticks(1, NEUTRAL);
                assertFalse(sonic.getDead());
                assertFalse(session.player(1).getDead());
            }
        }
        assertFalse(sonic.getAir(), "P1 must land through terrain after the positioned camera setup");
        assertEquals(0, sonic.getYSpeed());
        assertFalse(sonic.isHurt(), badnikTouchState(driver, badnik));
        assertTrue(badnik.isOnScreenForTouch(), badnikTouchState(driver, badnik));
        assertFalse(badnik.isDestroyed(), badnikTouchState(driver, badnik));
        int firstScore = session.gameState(0).getScore();
        int secondScore = session.gameState(1).getScore();
        int firstRings = session.levelState(0).getRings();
        int firstTotal = session.playerState(0).totalRings();
        int secondRings = session.levelState(1).getRings();
        int secondTotal = session.playerState(1).totalRings();
        assertEquals(firstScore, secondScore, "the score category must still be unearned before the badnik");
        assertTrue(secondRings > firstRings);
        assertTrue(secondTotal > firstTotal);
        driver.ticks(1, LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, InputActionMasks.ACTION_A, InputActionMasks.ACTION_A, false, false),
                PlayerInputState.neutral()));
        assertTrue(sonic.getAir(), "the real P1 action input must jump");
        assertTrue(sonic.getRolling(), "the attack state must be earned by the real jump");
        assertEquals(Sonic2AnimationIds.ROLL.id(), sonic.getAnimationId());

        assertTrue(Math.abs(session.player(1).getCentreX() - badnik.getX()) > 0x40,
                "P2 must remain clear of the positioned badnik interaction");
        int badnikSlot = badnik.getSlotIndex();
        // Only place the centre on a real body. Preserve the jump's actor
        // flags, animation and velocities; one canonical touch owns the kill.
        NativePositionOps.writeXPosResetSubpixel(sonic, badnik.getX());
        NativePositionOps.writeYPosResetSubpixel(sonic, badnik.getY());
        String beforeTouch = badnikTouchState(driver, badnik);
        driver.ticks(1, NEUTRAL);
        assertTrue(badnik.isDestroyed(), "canonical attacking touch must destroy the actual ROM badnik\n"
                + "before: " + beforeTouch + "\nafter: " + badnikTouchState(driver, badnik));
        assertTrue(objects.getActiveObjects().stream().anyMatch(object ->
                        object instanceof com.openggf.level.objects.ExplosionObjectInstance explosion
                                && explosion.getSlotIndex() == badnikSlot),
                "the real destruction path must replace the badnik in its native SST slot");
        assertTrue(session.gameState(0).getScore() > firstScore);
        assertEquals(secondScore, session.gameState(1).getScore(), "P1's actual kill must not credit P2");
        assertEquals(firstRings, session.levelState(0).getRings());
        assertEquals(firstTotal, session.playerState(0).totalRings());
        assertEquals(secondRings, session.levelState(1).getRings());
        assertEquals(secondTotal, session.playerState(1).totalRings());
        assertFalse(sonic.getDead());
        assertFalse(session.player(1).getDead());
        assertOverallTiePrerequisites(session);
    }

    private static String badnikTouchState(Driver driver, AbstractBadnikInstance badnik) {
        var player = driver.session.player(0);
        var objects = driver.host.context().getObjectManager();
        var rules = driver.host.context().getWorldSession().getGameModule().getRules().objectInteraction();
        var nearby = objects.getActiveObjects().stream()
                .filter(object -> object instanceof com.openggf.level.objects.TouchResponseProvider
                        && Math.abs(object.getCollisionX() - player.getCentreX()) < 0x40
                        && Math.abs(object.getCollisionY() - player.getCentreY()) < 0x40)
                .map(object -> object.getClass().getSimpleName() + "@" + object.getCollisionX() + ","
                        + object.getCollisionY() + "/flags="
                        + ((com.openggf.level.objects.TouchResponseProvider) object).getCollisionFlags()
                        + "/skip=" + object.isSkipTouchThisFrame())
                .toList();
        return "P1=" + player.getCentreX() + "," + player.getCentreY()
                + " velocity=" + player.getXSpeed() + "," + player.getYSpeed()
                + " air=" + player.getAir() + " roll=" + player.getRolling()
                + " animation=" + player.getAnimationId() + " hurt=" + player.isHurt()
                + " dead=" + player.getDead() + " objectControl=" + player.isObjectControlled()
                + " controlLocked=" + player.isControlLocked() + " debug=" + player.isDebugMode()
                + " invulnerable=" + player.getInvulnerableFrames()
                + " touchSuppressed=" + player.isTouchResponseSuppressedByObjectControl()
                + " camera=" + driver.session.camera(0).getX() + "," + driver.session.camera(0).getY()
                + " body=" + badnik.getClass().getSimpleName() + "/slot=" + badnik.getSlotIndex()
                + "/layout=" + badnik.getSpawn().layoutIndex() + "/id=" + badnik.getSpawn().objectId()
                + " centre=" + badnik.getX() + "," + badnik.getY()
                + " touch=" + badnik.getCollisionX() + "," + badnik.getCollisionY()
                + " preTouch=" + badnik.getPreUpdateCollisionX() + "," + badnik.getPreUpdateCollisionY()
                + " flags=" + badnik.getCollisionFlags() + "/pre=" + badnik.getPreUpdateCollisionFlags()
                + " onScreen=" + badnik.isOnScreenForTouch() + " skip=" + badnik.isSkipTouchThisFrame()
                + " destroyed=" + badnik.isDestroyed()
                + " previousList=" + rules.touchResponseUsesPreviousCollisionResponseList()
                + " previousHasBody=" + com.openggf.level.objects.ObjectManagerInternalAccess
                        .previousCollisionResponseObjects(objects).contains(badnik)
                + " nearby=" + nearby;
    }

    private static void assertOverallTiePrerequisites(Sonic2CompetitionSession session) {
        assertTrue(session.gameState(0).getScore() > session.gameState(1).getScore());
        assertTrue(session.levelState(1).getRings() > session.levelState(0).getRings());
        assertTrue(session.playerState(1).totalRings() > session.playerState(0).totalRings());
        assertEquals(0, session.playerState(0).monitorsBroken());
        assertEquals(0, session.playerState(1).monitorsBroken());
    }

    private static void verifyNativeP2BumpersAndRewind(Driver driver) throws Exception {
        var session = driver.session;
        var registry = driver.host.context().getRewindRegistry();
        var original = registry.capture();
        WorldState originalState = state(driver);
        assertNotNull(original.get(NATIVE_BUMPERS), "CNZ must capture its actual native dual-cursor owner");
        var table = new CNZBumperDataLoader().loadNativeCompetition(
                EngineServices.current().roms().getRom(), driver.host.profile().act());
        int clearP1X = session.player(0).getCentreX();
        // Approach each first ROM id $A/$8 entry from its outer side. Real
        // terrain resolves before TouchResponse and selects the oblique face.
        for (int id : new int[]{10, 8}) {
            CNZNativeBumperTable.Entry bumper = null;
            for (int index = 0; index < table.entryCount() && bumper == null; index++) {
                if (table.entry(index).id() == id) bumper = table.entry(index);
            }
            assertNotNull(bumper, "expected the native CNZ handler in this act's raw ROM table");
            int targetCamera = Math.max(0, bumper.x() - 0xA0);
            int camera = session.camera(1).getX();
            while (camera != targetCamera) {
                camera += Integer.signum(targetCamera - camera) * Math.min(0x100, Math.abs(targetCamera - camera));
                position(session.player(0), clearP1X, 0x100);
                position(session.player(1), camera + 0xA0, 0x100);
                session.camera(1).setX((short) camera);
                driver.tick(NEUTRAL);
            }
            assertTrue(Math.abs(clearP1X - bumper.x()) > 0x40, "keep P1 clear of this native bumper");
            position(session.player(0), clearP1X, 0x100);
            var type = CNZBumperType.fromId((id & 0xE) >> 1);
            assertNotNull(type);
            // Check_CNZ_bumpers uses an 18px player box against this 8px
            // half-width. Keep the actual terrain pass:
            // Tails_DoLevelCollision (s2.asm:40809-40819) pushes out of a negative wall probe
            // before its later native bumper endpoint reads the resulting centre.
            int contactDistance = type.getHalfWidth() + 0x12 / 2 - 1;
            position(session.player(1), bumper.x() + (id == 8 ? contactDistance : -contactDistance), bumper.y());
            var before = registry.capture();
            WorldState beforeState = state(driver);
            var firstBank = session.playerState(0).metrics();
            int firstLives = session.gameState(0).getLives();
            String beforeContact = bumperContactState(driver, bumper);
            driver.tick(NEUTRAL);
            String contact = "before: " + beforeContact + "\nafter: " + bumperContactState(driver, bumper)
                    + "\ncursorsBefore=" + before.get(NATIVE_BUMPERS);
            var second = session.player(1);
            int sideDistance = (short) (id == 8 ? second.getCentreX() - bumper.x()
                    : bumper.x() - second.getCentreX());
            int downDistance = (short) (second.getCentreY() - bumper.y());
            assertTrue(sideDistance >= 0 && sideDistance < 8,
                    "actual terrain must select the native oblique face, below the axis threshold\n" + contact);
            assertTrue(downDistance >= 0 && downDistance < 0x40,
                    "the contact must select the lower oblique face rather than either vertical tip\n" + contact);
            assertEquals(second.getCentreX() - second.getPrePhysicsCentreX(), nativeWallPush(second),
                    "the actual captured wall probes must account for the canonical centre displacement\n" + contact);
            assertNotEquals(0, nativeWallPush(second), "the real terrain pass must run before bumper touch\n" + contact);
            // loc_17790 / loc_177E6 choose $F8 / $88 on this lower face.
            // With zero horizontal input/speed, CalcAngle is $40; both source
            // differences exceed $38, so loc_175EA retains that surface angle.
            // Read CalcSine's actual REV01 Sine_Data words (s2.asm:4012-4024),
            // including its extended cosine quarter, instead of an output fit.
            int surfaceAngle = id == 8 ? 0xF8 : 0x88;
            var rom = EngineServices.current().roms().getRom();
            int sine = (short) rom.read16BitAddr(0x33CE + surfaceAngle * 2L);
            int cosine = (short) rom.read16BitAddr(0x33CE + (surfaceAngle + 0x40) * 2L);
            assertEquals((-0xA00 * cosine) >> 8, second.getXSpeed(),
                    "native ROM cosine must determine the complete P2 horizontal impulse\n" + contact);
            assertEquals((-0xA00 * sine) >> 8, second.getYSpeed(),
                    "native ROM sine must determine the complete P2 vertical impulse\n" + contact);
            assertTrue(session.player(1).getAir());
            assertFalse(second.getRollingJump());
            assertFalse(second.getPushing());
            assertFalse(second.isJumping());
            assertEquals(0, session.player(0).getXSpeed(), "P2's bumper impulse must not reach P1");
            assertEquals(firstBank.score(), session.gameState(0).getScore());
            assertEquals(firstBank.rings(), session.levelState(0).getRings());
            assertEquals(firstBank.totalRings(), session.playerState(0).totalRings());
            assertEquals(firstBank.monitors(), session.playerState(0).monitorsBroken());
            assertEquals(firstLives, session.gameState(0).getLives());
            WorldState expected = state(driver);
            registry.restore(before);
            assertRestoredRoster(driver);
            assertEquals(beforeState, state(driver), "restore must retain both native bumper cursor pairs");
            driver.tick(NEUTRAL);
            assertEquals(expected, state(driver), "actual P2 bumper collision must replay from the captured world");
        }
        // The goal loader later starts from its original camera interest, so
        // restore the real pre-probe world instead of warping P2's cursors back.
        registry.restore(original);
        assertRestoredRoster(driver);
        assertEquals(originalState, state(driver));
    }

    private static int nativeWallPush(AbstractPlayableSprite player) {
        int displacement = 0;
        for (var sensor : player.getPushSensors()) {
            var result = sensor.getCurrentResult();
            if (result != null && result.distance() < 0) {
                displacement += switch (result.direction()) {
                    case LEFT -> -result.distance();
                    case RIGHT -> result.distance();
                    default -> 0;
                };
            }
        }
        return displacement;
    }

    private static String bumperContactState(Driver driver, CNZNativeBumperTable.Entry bumper) {
        var player = driver.session.player(1);
        var wallProbes = java.util.Arrays.stream(player.getPushSensors()).map(sensor -> {
            var result = sensor.getCurrentResult();
            return result == null ? "none" : result.direction() + "/distance=" + result.distance()
                    + "/tile=" + result.tileId() + "/angle=" + result.angle();
        }).toList();
        return "zone=" + driver.host.profile().romZone() + "/act=" + driver.host.profile().act()
                + " ROMbumper=" + bumper + " P2=" + player.getCentreX() + "," + player.getCentreY()
                + " prePhysics=" + player.getPrePhysicsCentreX() + "," + player.getPrePhysicsCentreY()
                + " velocity=" + player.getXSpeed() + "," + player.getYSpeed()
                + " groundSpeed=" + player.getGSpeed() + " angle=" + player.getAngle()
                + " air=" + player.getAir() + " rollingJump=" + player.getRollingJump()
                + " jumping=" + player.isJumping() + " hurt=" + player.isHurt()
                + " dead=" + player.getDead() + " objectControl=" + player.isObjectControlled()
                + " touchSuppressed=" + player.isTouchResponseSuppressedByObjectControl()
                + " P2camera=" + driver.session.camera(1).getX() + "," + driver.session.camera(1).getY()
                + " wallProbes=" + wallProbes;
    }

    private static void verifyCanonicalTimeOverRequests(Driver driver) throws Exception {
        var session = driver.session;
        for (int slot = 0; slot < 2; slot++) session.levelState(slot).setTimerFrames(35999);
        driver.ticks(1, NEUTRAL);
        for (int slot = 0; slot < 2; slot++) {
            assertTrue(session.timeOver(slot));
            assertTrue(session.player(slot).getDead(), "the native host must execute the actual time-over kill");
            assertTrue(session.levelState(slot).isTimerPaused());
            assertEquals(35999, session.levelState(slot).getTimerFrames());
        }
        assertEquals(0, session.consumeTimeOverRequests(), "the host must consume each timer request in its native tick");
        assertEquals(Sonic2CompetitionSession.Phase.ACT, session.phase());
        assertNull(session.actResult(2, 1), "time over cannot manufacture an Obj39 dismissal or finish");
        driver.ticks(1, NEUTRAL);
        assertEquals(0, session.consumeTimeOverRequests(), "the held timeout flags must not reissue the kill request");
        assertEquals(Sonic2CompetitionSession.Phase.ACT, session.phase());
        assertNull(session.actResult(2, 1));

        var mode = driver.host.context();
        var objects = mode.getObjectManager();
        var lifecycle = nativeLifecycle(driver.host);
        // Let the real dead-fall and PLC/card routines reach Obj39's wait.
        // Neither actor position, card timer nor parent owner is rewritten.
        for (int pass = 0; pass < 600; pass++) {
            var current = NativeSstSlotAccess.currentOwner(objects, 2);
            if (current instanceof Sonic2GameOverCardObjectInstance card && card.getRoutine() == 4) break;
            driver.ticks(1, NEUTRAL);
            assertEquals(Sonic2CompetitionSession.Phase.ACT, session.phase());
        }
        var word = assertInstanceOf(Sonic2GameOverCardObjectInstance.class,
                NativeSstSlotAccess.currentOwner(objects, 2));
        var over = assertInstanceOf(Sonic2GameOverCardObjectInstance.class,
                NativeSstSlotAccess.currentOwner(objects, 3));
        assertEquals(4, word.getRoutine(), "actual Obj39 must finish its PLC hold and conjoining passes");
        assertEquals(0x120, word.getVdpX());
        assertEquals(2, word.getMappingFrame());
        assertEquals(3, over.getMappingFrame());
        assertEquals(0xB000, word.getNativeParentWord(), "both REV01 timeout flags retain P1's shared card owner");
        assertEquals(0, over.getNativeParentWord());
        assertEquals(8, lifecycle.nativeRoutineByte(0));
        assertEquals(8, lifecycle.nativeRoutineByte(1));
        assertEquals(Sonic2GameOverCardObjectInstance.WAIT_FRAMES, word.getWaitTimer());
        assertEquals(Sonic2GameOverCardObjectInstance.WAIT_FRAMES, over.getWaitTimer());
        assertFalse(word.isDismissed());
        assertFalse(over.isDismissed());
        driver.ticks(1, NEUTRAL);
        assertSame(word, NativeSstSlotAccess.currentOwner(objects, 2), "P2's timeout cannot replace the surviving P1 prefix owner");
        assertEquals(Sonic2GameOverCardObjectInstance.WAIT_FRAMES - 1, word.getWaitTimer());
        assertEquals(Sonic2GameOverCardObjectInstance.WAIT_FRAMES, over.getWaitTimer(), "odd OVER never consumes the wait clock");
        assertEquals(Sonic2CompetitionSession.Phase.ACT, session.phase());
        assertNull(session.actResult(2, 1));

        var registry = mode.getRewindRegistry();
        var beforeDismiss = registry.capture();
        WorldState before = state(driver);
        var dismiss = LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(),
                PlayerInputState.of(0, 0, InputActionMasks.ACTION_B, InputActionMasks.ACTION_B, false, false));
        driver.ticks(1, dismiss);
        assertTrue(word.isDismissed(), "actual P2 ABC input must execute the surviving left Obj39 routine");
        assertFalse(over.isDismissed());
        assertEquals(Sonic2CompetitionSession.Phase.ACT_RESULTS, session.phase());
        assertNotNull(session.actResult(2, 1), "the actual card callback must publish the native timeout result");
        assertEquals(Sonic2CompetitionResults.compare(session.resultMetrics(0), session.resultMetrics(1)),
                session.actResult(2, 1));
        assertFalse(mode.getLevelManager().isRespawnRequestedForRewind(), "native Obj39 cannot request a campaign reload");
        WorldState expected = state(driver);
        registry.restore(beforeDismiss);
        assertRestoredRoster(driver);
        assertEquals(before, state(driver));
        var restoredWord = assertInstanceOf(Sonic2GameOverCardObjectInstance.class,
                NativeSstSlotAccess.currentOwner(objects, 2));
        assertFalse(restoredWord.isDismissed());
        assertEquals(0xB000, restoredWord.getNativeParentWord());
        assertEquals(Sonic2GameOverCardObjectInstance.WAIT_FRAMES - 1, restoredWord.getWaitTimer());
        driver.ticks(1, dismiss);
        assertTrue(restoredWord.isDismissed());
        assertEquals(expected, state(driver), "actual timeout-card dismissal must restore and reproduce forward results");
    }

    private static void finishAtPhysicalSignpost(Driver driver, int first) throws Exception {
        finishAtPhysicalSignpost(driver, first, false);
    }

    private static void finishAtPhysicalSignpost(Driver driver, int first, boolean preserveEarnedRingLead)
            throws Exception {
        approachPhysicalSignpost(driver, preserveEarnedRingLead);
        var registry = driver.host.context().getRewindRegistry();
        var beforeArrival = registry.capture();
        WorldState before = state(driver);
        crossAndSpinPhysicalSignpost(driver, first);
        WorldState chosen = state(driver);

        registry.restore(beforeArrival);
        assertRestoredRoster(driver);
        assertEquals(before, state(driver), "the actual pre-arrival world must restore before reversing finish order");
        crossAndSpinPhysicalSignpost(driver, 1 - first);

        registry.restore(beforeArrival);
        assertRestoredRoster(driver);
        assertEquals(before, state(driver));
        crossAndSpinPhysicalSignpost(driver, first);
        assertEquals(chosen, state(driver),
                "replaying the chosen actual goal branch must reproduce actors/cameras/banks/loaders/results");
    }

    private static void verifyFreshActSimultaneousArrival(Driver driver, CompositeSnapshot freshAct) throws Exception {
        var registry = driver.host.context().getRewindRegistry();
        var chosenFinish = registry.capture();
        WorldState chosen = state(driver);
        registry.restore(freshAct);
        assertRestoredRoster(driver);
        assertEquals(0, driver.session.levelState(0).getTimerFrames());
        assertEquals(0, driver.session.levelState(1).getTimerFrames());
        approachPhysicalSignpost(driver);
        crossAndSpinPhysicalSignpost(driver, 0, true);
        // Both bodies entered together, but Obj0D samples P2 only when P1 did
        // not arrive. The two real timer banks therefore cannot earn an equal
        // time from this setup; do not seed counters to manufacture a tie.
        assertEquals(driver.session.resultMetrics(0).timerFrames() + 1,
                driver.session.resultMetrics(1).timerFrames());
        registry.restore(chosenFinish);
        assertRestoredRoster(driver);
        assertEquals(chosen, state(driver), "the simultaneous branch must not replace the selected finish/results");
    }

    private static void assertRestoredRoster(Driver driver) {
        var mode = driver.host.context();
        assertSame(driver.session.player(0), mode.getSpriteManager().getSprite("sonic"));
        assertSame(driver.session.player(1), mode.getSpriteManager().getSprite("tails"));
        assertSame(driver.session.player(0), driver.session.camera(0).getFocusedSprite());
        assertSame(driver.session.player(1), driver.session.camera(1).getFocusedSprite());
    }

    private static void approachPhysicalSignpost(Driver driver) {
        approachPhysicalSignpost(driver, false);
    }

    private static void approachPhysicalSignpost(Driver driver, boolean preserveEarnedRingLead) {
        var session = driver.session;
        var goal = driver.host.profile().signposts().getFirst();
        int ringClearY = driver.host.profile().rings().stream().mapToInt(ring -> ring.y()).min().orElse(0x100) - 0x40;
        int targetCamera = Math.min(driver.host.profile().bounds().maxX(), goal.x() - 0xA0);
        int[] cameras = {Math.max(0, session.camera(0).getX()), Math.max(0, session.camera(1).getX())};
        while (cameras[0] != targetCamera || cameras[1] != targetCamera) {
            for (int slot = 0; slot < 2; slot++) {
                cameras[slot] += Math.clamp(targetCamera - cameras[slot], -0x100, 0x100);
                assertTrue(Math.abs(cameras[slot] - session.camera(slot).getX()) <= 0x100,
                        "each independent camera must enter the next native column without a warp");
                // The tie's P1 score/roll was earned earlier. Keep that positioned
                // loader setup clear of rings while P2 retains its earned lead.
                int y = preserveEarnedRingLead && slot == 0 ? ringClearY : 0x100;
                position(session.player(slot), Math.min(cameras[slot] + 0xA0, goal.x() - 0x20), y);
                session.camera(slot).setX((short) cameras[slot]);
            }
            driver.tick(NEUTRAL);
        }
        assertTrue(driver.host.context().getObjectManager().getActiveObjects().stream()
                .anyMatch(SignpostObjectInstance.class::isInstance), "native streaming must construct the real goal");
        assertEquals(-1, session.firstFinisher());
        assertFalse(session.levelState(0).isTimerPaused());
        assertFalse(session.levelState(1).isTimerPaused());
    }

    private static void crossAndSpinPhysicalSignpost(Driver driver, int first) {
        crossAndSpinPhysicalSignpost(driver, first, false);
    }

    private static void crossAndSpinPhysicalSignpost(Driver driver, int first, boolean simultaneous) {
        var session = driver.session;
        var goal = driver.host.profile().signposts().getFirst();
        position(session.player(first), goal.x() + 8, goal.y() - 0x40);
        position(session.player(1 - first), simultaneous ? goal.x() + 8 : goal.x() - 0x20, goal.y() - 0x40);
        driver.ticks(1, NEUTRAL);
        assertEquals(first, session.firstFinisher());
        assertTrue(session.levelState(first).isTimerPaused());
        assertFalse(session.levelState(1 - first).isTimerPaused());
        position(session.player(1 - first), goal.x() + 8, goal.y() - 0x40);
        driver.ticks(1, NEUTRAL);
        assertTrue(session.levelState(0).isTimerPaused());
        assertTrue(session.levelState(1).isTimerPaused());
        assertEquals(Sonic2CompetitionSession.Phase.ACT, session.phase(), "the real spin still owns completion");
        // Hold the positioned interaction above terrain while the real Obj0D spin executes.
        // This is bounded object/session coverage, not a claim to have traversed the act.
        for (int tick = 0; tick < 300 && session.phase() == Sonic2CompetitionSession.Phase.ACT; tick++) {
            for (int slot = 0; slot < 2; slot++) position(session.player(slot), goal.x() + 8, goal.y() - 0x40);
            driver.tick(NEUTRAL);
        }
        assertEquals(Sonic2CompetitionSession.Phase.ACT_RESULTS, session.phase(), "Obj0D must complete its native spin");
        assertEquals(first, session.firstFinisher());
        assertNotNull(session.resultMetrics(0));
        assertNotNull(session.resultMetrics(1));
        assertTrue(session.resultMetrics(first).timerFrames() < session.resultMetrics(1 - first).timerFrames(),
                "the first native arrival must freeze its own timer before the second arrival");
    }

    private static void position(AbstractPlayableSprite player, int x, int y) {
        assertFalse(player.getDead(), "positioned interaction must not silently revive an actor");
        NativePositionOps.writeXPosResetSubpixel(player, x);
        NativePositionOps.writeYPosResetSubpixel(player, y);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setAir(true);
        player.setOnObject(false);
    }

    private record WorldState(List<Integer> sonic, List<Integer> tails,
            CameraSnapshot firstCamera, CameraSnapshot secondCamera,
            Bank firstBank, Bank secondBank, int levelFrame, int spriteFrame,
            int objectClock, int objectPass, Object objectLoader, Object ringLoader, Object bumperLoader,
            NativeArtState nativeArt, Object nativePause, Object lifecycle,
            Sonic2CompetitionSession.Phase phase, int firstFinisher, int loserTimeLeft,
            List<Sonic2CompetitionResults.Wins> ledger,
            Sonic2CompetitionResults.Metrics firstResult, Sonic2CompetitionResults.Metrics secondResult) {
        /** Pause advances VInt/art/controller state while every gameplay body owner remains frozen. */
        private WorldState body() {
            return new WorldState(sonic, tails, firstCamera, secondCamera, firstBank, secondBank,
                    levelFrame, spriteFrame, 0, objectPass, objectLoader, ringLoader, bumperLoader,
                    null, null, lifecycle, phase, firstFinisher, loserTimeLeft, ledger, firstResult, secondResult);
        }
    }

    private record Bank(Sonic2CompetitionResults.Metrics metrics, int lives, int continues,
            int nextLife, Sonic2CompetitionPlayerState.Checkpoint checkpoint, boolean paused, boolean timeOver) { }

    private record NativeArtState(Sonic2InterlaceVramService owner, Sonic2InterlaceVram vram,
            NemesisPlcQueueSnapshot plc, List<Sonic2InterlaceVramService.DmaTransfer> pending,
            int sonicLast, int tailsLast, int tailLast) { }

    private static WorldState state(Driver driver) throws Exception {
        var session = driver.session;
        var mode = driver.host.context();
        var snapshot = mode.getRewindRegistry().capture();
        Object objectState = snapshot.get(OBJECT_STREAMING), ringState = snapshot.get(RING_STREAMING);
        assertNotNull(objectState, "native object cursor adapter must participate in actual registry capture");
        assertNotNull(ringState, "native ring cursor adapter must participate in actual registry capture");
        Object bumperState = snapshot.get(NATIVE_BUMPERS);
        if (driver.host.profile().romZone() == 12) assertNotNull(bumperState);
        else assertNull(bumperState, "other native zones do not own CNZ bumper state");
        assertNotNull(snapshot.get(Sonic2InterlaceVramService.REWIND_KEY),
                "the stable early art binding must participate in actual registry capture");
        var owner = nativeVramOwner(driver.host, session);
        var art = owner.capture();
        var nativeArt = new NativeArtState(owner, art.vram(), art.plc(), art.pending(),
                art.sonicLast(), art.tailsLast(), art.tailLast());
        var pause = snapshot.get(driver.host.nativePause().key());
        assertNotNull(pause, "the actual pause continuation and consumable exit must be captured");
        var lifecycle = nativeLifecycle(driver.host);
        var lifeState = snapshot.get(lifecycle.key());
        assertNotNull(lifeState, "the actual lifecycle and signed camera-difference words must be captured");
        return new WorldState(actor(session.player(0)), actor(session.player(1)),
                session.camera(0).capture(), session.camera(1).capture(), bank(session, 0), bank(session, 1),
                mode.getLevelManager().getFrameCounter(), mode.getSpriteManager().getFrameCounter(),
                mode.getObjectManager().getVblaCounter(), mode.getObjectManager().getFrameCounter(),
                value(objectState), value(ringState), value(bumperState), nativeArt, value(pause), value(lifeState),
                session.phase(), session.firstFinisher(), session.loserTimeLeft(), session.capture().ledger(),
                session.resultMetrics(0), session.resultMetrics(1));
    }

    private static Bank bank(Sonic2CompetitionSession session, int slot) {
        var bank = session.playerState(slot);
        return new Bank(bank.metrics(), session.gameState(slot).getLives(), session.gameState(slot).getContinues(),
                bank.nextExtraLifeScore(), bank.checkpoint(), bank.isTimerPaused(), bank.isTimeOver());
    }

    private static List<Integer> actor(AbstractPlayableSprite player) {
        var result = new ArrayList<Integer>(210);
        Collections.addAll(result, (int) player.getCentreX(), (int) player.getCentreY(),
                player.getXSubpixelRaw(), player.getYSubpixelRaw(), (int) player.getXSpeed(),
                (int) player.getYSpeed(), (int) player.getGSpeed(), (int) player.getAngle(),
                player.getAir() ? 1 : 0, player.getRolling() ? 1 : 0, player.getDead() ? 1 : 0,
                player.getMappingFrame(), player.getAnimationId(), player.getLogicalInputState());
        for (int i = 0; i < 64; i++) {
            result.add((int) player.getCentreX(i));
            result.add((int) player.getCentreY(i));
            result.add((int) player.getInputHistory(i));
        }
        return List.copyOf(result);
    }

    /** Native cursor snapshots contain nested records and arrays; compare their retained values. */
    private static Object value(Object snapshot) throws Exception {
        if (snapshot == null) return null;
        var values = new ArrayList<Object>();
        if (snapshot.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(snapshot); i++) values.add(value(Array.get(snapshot, i)));
            return Collections.unmodifiableList(values);
        }
        if (snapshot.getClass().isRecord()) {
            for (var component : snapshot.getClass().getRecordComponents()) {
                var accessor = component.getAccessor();
                accessor.setAccessible(true);
                values.add(value(accessor.invoke(snapshot)));
            }
            return Collections.unmodifiableList(values);
        }
        return snapshot;
    }

    private static final class Driver {
        private final Sonic2NativeCompetitionHost host;
        private final Sonic2CompetitionSession session;
        private final CountingInput input;
        private final AtomicBoolean slowMotion = new AtomicBoolean(false);
        private int nativeSamples;

        private Driver(Sonic2NativeCompetitionHost host, Sonic2CompetitionSession session, CountingInput input) {
            this.host = host;
            this.session = session;
            this.input = input;
            host.bindNativeWaitInput(() -> {
                nativeSamples++;
                return input.logical();
            }, slowMotion::get);
        }

        private LevelFrameResult tick(LogicalInputSnapshot snapshot) {
            input.setLogicalOverride(snapshot);
            var mode = host.context();
            LevelFrameStep.updateTimers(LevelFrameContext.from(mode));
            return mode.plcFrameLifecycle().runLogicalIteration(mode::updateFade, frame -> {
                var result = new LevelFrameResult[1];
                NativeCompetitionIterationAccess.runAdmitted(frame, () -> result[0] = host.step(input, frame));
                return result[0];
            });
        }

        private void ticks(int count, LogicalInputSnapshot input) {
            int completed = 0;
            for (int attempts = 0; completed < count && attempts < count + 2; attempts++) {
                if (tick(input) == LevelFrameResult.GAMEPLAY_FRAME) completed++;
            }
            assertEquals(count, completed, "only the single initial setup pass may omit a gameplay tick");
        }

        private NativeMenuScreen.Exit screenTick(NativeMenuController menus, LogicalInputSnapshot snapshot,
                                                PlcLifecyclePhase expectedOwner) {
            input.setLogicalOverride(snapshot);
            int updatesBefore = input.updates;
            var mode = host.context();
            LevelFrameStep.updateTimers(LevelFrameContext.from(mode));
            var exit = mode.plcFrameLifecycle().runLogicalIteration(mode::updateFade, frame -> {
                var result = new NativeMenuScreen.Exit[1];
                NativeCompetitionIterationAccess.runAdmitted(frame, () -> result[0] = menus.update(input, false));
                assertTrue(frame.isOwnedBy(expectedOwner), "the real screen must claim the native wait's phase");
                return result[0];
            });
            assertEquals(updatesBefore + 1, input.updates, "only the actual menu controller advances input");
            assertTrue(menus.completeFrame());
            assertEquals(updatesBefore + 1, input.updates);
            return exit;
        }
    }

    private static final class UnqualifiedViews implements Sonic2NativeCompetitionHost.Views {
        private int preparations, draws;
        private int entryVInt;
        private int lastEntryPass;
        private boolean failNextPrepare;
        @Override public void prepare(GameplayModeContext context, Sonic2CompetitionSession session,
                Sonic2CompetitionLevelProfile profile, Sonic2CompetitionNativeArt art) {
            assertSame(SessionManager.getCurrentGameplayMode(), context);
            assertNotNull(profile);
            assertNotNull(art);
            if (failNextPrepare) {
                failNextPrepare = false;
                throw new IllegalStateException("owned native view prepare fault");
            }
            var objects = context.getObjectManager();
            var lifecycle = context.getWorldSession().getGameModule().getGameService(Sonic2CompetitionLifecycle.class);
            assertNotNull(lifecycle);
            if (objects.getFrameCounter() == 0) {
                entryVInt = objects.getVblaCounter();
                lastEntryPass = 0;
                for (int slot = 0; slot < 2; slot++) {
                    assertEquals(0, lifecycle.nativeRoutineByte(slot), "the initial view observes actual native Init");
                    var player = session.player(slot);
                    assertEquals(0, player.getXSpeed());
                    assertEquals(0, player.getYSpeed());
                    assertEquals(0, player.getGSpeed());
                    assertEquals(0, player.getAngle());
                }
            } else if (context.getLevelManager().getFrameCounter() == 0) {
                if (objects.getFrameCounter() != lastEntryPass) {
                    assertEquals(lastEntryPass + 1, objects.getFrameCounter(),
                            "the actual view must observe the leading pass and each of the 25 native waits");
                    lastEntryPass = objects.getFrameCounter();
                }
                assertEquals(2, lifecycle.nativeRoutineByte(0), "the leading RunObjects pass completes native P1 Init");
                assertEquals(2, lifecycle.nativeRoutineByte(1), "all title passes retain actual native human control routines");
                assertEquals(entryVInt + objects.getFrameCounter() - 1, objects.getVblaCounter(),
                        "the leading object pass has no VInt and each leave pass has one");
                assertEquals(0, session.levelState(0).getTimerFrames());
                assertEquals(0, session.levelState(1).getTimerFrames());
            }
            preparations++;
        }
        @Override public void draw(GameplayModeContext context, Sonic2CompetitionSession session) { draws++; }
    }

    private static final class UnavailableNativeState implements Sonic2NativeCompetitionHost.NativeState {
        @Override public boolean swapActorWorldState(GameplayModeContext context, Sonic2CompetitionSession session) {
            return false;
        }
        @Override public void freezeObjects(GameplayModeContext context, int ticks) {
            throw new UnsupportedOperationException("native atomic swap/freeze port is deliberately unavailable");
        }
    }
}
