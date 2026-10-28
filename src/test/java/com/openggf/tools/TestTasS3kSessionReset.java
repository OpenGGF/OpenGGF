package com.openggf.tools;

import com.openggf.GameLoop;
import com.openggf.InputBindingFactory;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.*;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.save.*;
import com.openggf.game.session.*;
import com.openggf.game.sonic3k.Sonic3kCheatFlags;
import com.openggf.game.sonic3k.Sonic3kSessionResetPolicy;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real ROM, managers, movie cursor and lifecycle; graphics are deliberately logic-only here. */
@RequiresRom(SonicGame.SONIC_3K)
class TestTasS3kSessionReset {
    @TempDir Path directory;
    private EngineContext services;
    private HeadlessGameBoot boot;
    private GameLoop loop;
    private InputHandler input;
    private TitleScreenProvider title;
    private final List<LogicalInputSnapshot> titleInputs = new ArrayList<>();
    private String previousSaveRoot;

    @BeforeEach void open() throws Exception {
        TestEnvironment.resetEngineOnly();
        services = EngineServices.current();
        services.configuration().setConfigValue(SonicConfiguration.AUDIO_ENABLED, false);
        services.configuration().setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        services.configuration().setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        services.configuration().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        services.configuration().setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        services.configuration().setConfigValue(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        title = mock(TitleScreenProvider.class);
        when(title.isActive()).thenReturn(true);
        doAnswer(call -> { titleInputs.add(((InputHandler) call.getArgument(0)).logical()); return null; })
                .when(title).update(any());
        boot = new HeadlessGameBoot(320, 224, services, new HeadlessGameBoot.NativeGlLifecycle() {
            @Override public void initialize(HeadlessGameBoot ignored) { services.graphics().initHeadless(); }
            @Override public void close(HeadlessGameBoot ignored) { }
        });
        // Observe title input at its interface; the reset policy comes from stock registration.
        boot.setModuleDecorator(module -> new DelegatingGameModule(module, "test:reset-title-observations") {
            @Override public TitleScreenProvider getTitleScreenProvider() { return title; }
        });
        loop = boot.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 7, 0);
        assertSame(Sonic3kSessionResetPolicy.INSTANCE,
                GameServices.module().getGameService(NativeSessionResetPolicy.class));
        input = new InputHandler(InputBindingFactory.supplier(services.configuration()));
        loop.setInputHandler(input);
        previousSaveRoot = System.getProperty(SavePaths.ROOT_PROPERTY);
        System.setProperty(SavePaths.ROOT_PROPERTY, directory.toString());
    }

    @AfterEach void close() {
        try {
            PlaybackDebugManager.getInstance().endSession();
            SessionSaveRequests.flushPendingSaves();
            if (boot != null) boot.close();
        } finally {
            if (services != null) services.audio().destroy();
            if (previousSaveRoot == null) System.clearProperty(SavePaths.ROOT_PROPERTY);
            else System.setProperty(SavePaths.ROOT_PROPERTY, previousSaveRoot);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void warmAndColdRebuildWorldsAndPreserveDurableSaveWithoutAnImplicitWrite(boolean cold) throws Exception {
        installSaveSession();
        SessionSaveRequests.requestCurrentSessionSave(SaveReason.EXISTING_SLOT_LOAD);
        Path savePath = directory.resolve("s3k/slot1.json");
        byte[] saved = Files.readAllBytes(savePath);
        var outgoing = SessionManager.getCurrentGameplayMode();
        var oldWorld = outgoing.getWorldSession();
        var oldSource = oldWorld.getDataSource();
        var oldLevel = outgoing.getLevelManager();
        var oldObjects = oldLevel.getObjectManager();
        var oldState = outgoing.getGameStateManager();
        oldState.addScore(12340);
        oldState.restoreSaveProgress(9, 2, List.of(0, 2), List.of());
        for (int i = 0; i < 3; i++) oldState.consumeCurrentSpecialStageIndexAndAdvance();
        oldState.setReverseGravityActive(true);
        oldState.setGamePaused(true);
        oldLevel.getLevelGamestate().setRings(37);
        oldLevel.getLevelGamestate().setTimerFrames(1234);
        oldLevel.setFrameCounter(2222);
        ((CheckpointState) oldLevel.getCheckpointState()).saveCheckpoint(1, 0x120, 0x300, false);
        assertTrue(oldLevel.getCheckpointState().isActive(), "outgoing checkpoint is real and active");
        outgoing.getRng().setSeed(0x12345678L);
        oldObjects.initVblaCounter(0x12345678);
        var cheats = oldWorld.getGameModule().getGameService(Sonic3kCheatFlags.class);
        cheats.enableLevelSelectAndSlowMotion();
        cheats.enableDebugCheat();
        AtomicInteger closes = new AtomicInteger();
        outgoing.setHardwareTimingReplayCloseHook(closes::incrementAndGet);
        var backend = services.audio().getBackend();
        assertInstanceOf(NullAudioBackend.class, backend);
        assertFalse(outgoing.getSpriteManager().getAllSprites().isEmpty(), "real Knuckles roster reached");

        startMovie(cold ? "|P.|........|" : "|.r|........|", "|..|........|");
        loop.step();

        var replacement = SessionManager.getCurrentGameplayMode();
        assertNotSame(outgoing, replacement);
        assertNotSame(oldWorld, replacement.getWorldSession());
        assertSame(oldSource, replacement.getWorldSession().getDataSource(), "pin the same immutable ROM source");
        assertNull(replacement.getWorldSession().getSaveSessionContext(), "title owns no active Save_pointer");
        assertNull(replacement.getWorldSession().getCurrentLevel());
        assertNull(outgoing.getLevelManager());
        assertNull(outgoing.getRewindRegistry(), "old timeline cannot be installed into the replacement owner");
        assertFalse(outgoing.dynamicArtLifecycle().isRunActive());
        assertEquals(1, closes.get());
        outgoing.destroy();
        assertEquals(1, closes.get(), "already retired owners close once");
        assertTrue(replacement.getSpriteManager().getAllSprites().isEmpty(), "title has no active gameplay roster");
        assertEquals(GameMode.TITLE_SCREEN, loop.getCurrentGameMode());
        var checkpoint = replacement.getLevelManager().getCheckpointState();
        assertTrue(checkpoint == null || !checkpoint.isActive(), "title entry cleared active starpost ownership");
        assertFalse(replacement.getGameStateManager().isReverseGravityActive());
        assertFalse(replacement.getGameStateManager().isGamePaused());
        assertEquals(0, replacement.getRng().getSeed(), "RNG_seed lies below CrossResetRAM");
        assertEquals(cold ? 0 : 0x12345678L, EngineTiming.vIntRunCounter(services).value());
        assertEquals(cold ? 0 : 12340, replacement.getGameStateManager().getScore());
        assertEquals(cold ? 0 : 9, replacement.getGameStateManager().getLives());
        assertEquals(cold ? 0 : 2, replacement.getGameStateManager().getContinues());
        assertEquals(cold ? 0 : 2, replacement.getGameStateManager().getEmeraldCount());
        assertEquals(cold ? 0 : 3, replacement.getGameStateManager().getCurrentSpecialStageIndex());
        assertEquals(cold ? 0 : 37, replacement.getLevelManager().getLevelGamestate().getRings());
        assertEquals(cold ? 0 : 1234, replacement.getLevelManager().getLevelGamestate().getTimerFrames());
        assertEquals(cold ? 0 : 2222, replacement.getLevelManager().getFrameCounter());
        assertEquals(!cold, cheats.isLevelSelectEnabled());
        assertEquals(!cold, cheats.isDebugCheatEntered());
        assertSame(backend, services.audio().getBackend(), "reset rebuilds audio voices without opening a device");
        SessionSaveRequests.flushPendingSaves();
        assertArrayEquals(saved, Files.readAllBytes(savePath), "reset must not persist unsaved runtime mutations");
        assertEquals(1, services.playbackDebug().getCursorFrame());
        verify(title).initialize();
    }

    @Test void consecutiveResetPowerStartRowsHaveOneCursorOwnerAndFreshControllerEdges() throws Exception {
        startMovie("|.r|.......S|", "|.r|.......S|", "|..|.......S|", "|..|........|",
                "|P.|.......S|", "|..|.......S|", "|..|........|");
        var initial = SessionManager.getCurrentGameplayMode();
        loop.step();
        var first = SessionManager.getCurrentGameplayMode();
        assertNotSame(initial, first);
        assertEquals(1, services.playbackDebug().getCursorFrame());
        loop.step();
        assertNotSame(first, SessionManager.getCurrentGameplayMode());
        assertEquals(2, services.playbackDebug().getCursorFrame());
        loop.step();
        assertEquals(3, services.playbackDebug().getCursorFrame());
        assertTrue(titleInputs.getFirst().player1().startPressed(), "reset cleared the old held Start sample");
        loop.step();
        assertEquals(4, services.playbackDebug().getCursorFrame());
        assertFalse(titleInputs.getLast().player1().startHeld());
        loop.step();
        assertEquals(5, services.playbackDebug().getCursorFrame());
        loop.step();
        assertEquals(6, services.playbackDebug().getCursorFrame());
        assertTrue(titleInputs.getLast().player1().startPressed());
        loop.step();
        assertEquals(6, services.playbackDebug().getCursorFrame(), "last row completes without a phantom extra row");
        assertFalse(input.hasLogicalOverride());
        verify(title, times(3)).initialize();
    }

    @Test void aHeldActionIsConsumedOnceByTheNativeMenuAfterReset() throws Exception {
        startMovie("|.r|........|", "|..|....A...|", "|..|....A...|", "|..|........|");
        loop.step();
        loop.step();
        loop.step();
        assertEquals(1, titleInputs.get(0).player1().actionPressedMask());
        assertEquals(0, titleInputs.get(1).player1().actionPressedMask());
        assertFalse(input.hasLogicalOverride());
    }

    @Test void ordinaryMovieKeepsItsLevelCursorAndRestoreForwardReplayAfterResetUsesFreshRegistry() throws Exception {
        settleOrdinaryLevel();
        startMovie("|..|........|", "|..|........|", "|..|........|");
        var original = SessionManager.getCurrentGameplayMode();
        int before = services.playbackDebug().getCursorFrame();
        loop.step();
        assertSame(original, SessionManager.getCurrentGameplayMode());
        assertEquals(before + 1, services.playbackDebug().getCursorFrame());

        startMovie("|.r|........|", "|..|........|");
        loop.step();
        services.playbackDebug().endSession();
        var replacement = SessionManager.getCurrentGameplayMode();
        var team = GameplayTeamBootstrap.registerActiveTeam(replacement.getWorldSession().getGameModule(),
                replacement.getSpriteManager(), services.configuration());
        replacement.getCamera().setFocusedSprite(team.mainSprite());
        replacement.getLevelManager().loadZoneAndAct(4, 0); // ordinary FBZ load, not a claimed MHZ reset skip
        loop.setGameMode(GameMode.LEVEL);
        settleOrdinaryLevel();
        assertFalse(replacement.getLevelManager().getCheckpointState().isActive(),
                "the next ordinary level cannot revive the retired checkpoint");
        var registry = replacement.getRewindRegistry();
        var snapshot = registry.capture();
        int x = team.mainSprite().getCentreX();
        int frame = replacement.getLevelManager().getFrameCounter();
        long clock = EngineTiming.vIntRunCounter(services).value();
        loop.step();
        loop.step();
        int futureX = team.mainSprite().getCentreX();
        int futureFrame = replacement.getLevelManager().getFrameCounter();
        long futureClock = EngineTiming.vIntRunCounter(services).value();
        assertEquals(frame + 2, futureFrame, "forward control executed two real level frames");
        assertEquals((clock + 2) & 0xFFFFFFFFL, futureClock, "the new visible object clock advances after reset");
        registry.restore(snapshot);
        assertEquals(frame, replacement.getLevelManager().getFrameCounter());
        assertEquals(x, replacement.getSpriteManager().getMainPlayable().getCentreX());
        loop.step();
        loop.step();
        assertEquals(futureFrame, replacement.getLevelManager().getFrameCounter());
        assertEquals(futureX, replacement.getSpriteManager().getMainPlayable().getCentreX());
        assertEquals(futureClock, EngineTiming.vIntRunCounter(services).value());
        assertNativePauseAfterReset(replacement);
    }

    private void assertNativePauseAfterReset(GameplayModeContext mode) {
        var registry = mode.getRewindRegistry();
        int frame = mode.getLevelManager().getFrameCounter();
        int x = mode.getSpriteManager().getMainPlayable().getCentreX();
        int y = mode.getSpriteManager().getMainPlayable().getCentreY();
        long rng = mode.getRng().getSeed();
        long clock = EngineTiming.vIntRunCounter(services).value();
        for (int i = 0; i < 3; i++) stepStart(true, i == 0);
        assertTrue(mode.getGameStateManager().isGamePaused());
        assertEquals((clock + 3) & 0xFFFFFFFFL, EngineTiming.vIntRunCounter(services).value());
        assertEquals(frame, mode.getLevelManager().getFrameCounter());
        assertEquals(x, mode.getSpriteManager().getMainPlayable().getCentreX());
        assertEquals(y, mode.getSpriteManager().getMainPlayable().getCentreY());
        assertEquals(rng, mode.getRng().getSeed());
        var paused = registry.capture();
        stepStart(false, false);
        stepStart(true, true);
        assertFalse(mode.getGameStateManager().isGamePaused());
        assertEquals(frame + 1, mode.getLevelManager().getFrameCounter());
        assertEquals((clock + 5) & 0xFFFFFFFFL, EngineTiming.vIntRunCounter(services).value());
        var resumed = registry.capture();
        registry.restore(paused);
        assertTrue(mode.getGameStateManager().isGamePaused(), "the replacement registry owns native pause");
        stepStart(false, false);
        stepStart(true, true);
        var replay = registry.capture();
        assertEquals(resumed.entries().keySet(), replay.entries().keySet());
        for (String key : resumed.entries().keySet()) {
            var diff = com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                    key, resumed.get(key), replay.get(key));
            assertTrue(diff.isEmpty(), () -> key + ": " + diff);
        }
    }

    private void stepStart(boolean held, boolean pressed) {
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                com.openggf.control.PlayerInputState.of(0, 0, 0, 0, held, pressed),
                com.openggf.control.PlayerInputState.neutral()));
        try (var owned = com.openggf.control.RecordedInputOwnership.acquire(input)) {
            loop.step();
        }
    }

    @Test void resetDuringRealSpecialStageEntryRetiresThePendingVisitInsteadOfReturningOrAwarding() throws Exception {
        var progress = SessionManager.getCurrentGameplayMode().getGameStateManager();
        loop.enterSpecialStage();
        var outgoing = SessionManager.getCurrentGameplayMode();
        assertTrue(outgoing.getFadeManager().isActive(), "native special entry fade was reached");
        startMovie("|.r|........|", "|..|........|");
        loop.step();
        services.playbackDebug().endSession();
        var replacement = SessionManager.getCurrentGameplayMode();
        assertEquals(0, replacement.getGameStateManager().getEmeraldCount());
        for (int i = 0; i < 90; i++) loop.step();
        assertEquals(GameMode.TITLE_SCREEN, loop.getCurrentGameMode(), "retired fade cannot enter the old special stage");
        assertEquals(0, replacement.getGameStateManager().getEmeraldCount());
        assertNull(outgoing.getGameStateManager());
        assertNotSame(progress, replacement.getGameStateManager());
    }

    private void installSaveSession() throws Exception {
        var world = SessionManager.getCurrentWorldSession();
        var save = SaveSessionContext.forSlot("s3k", 1, new SelectedTeam("knuckles", List.of()), 7, 0);
        var mode = SessionManager.openGameplaySession(world.rootGameModule(), world.getGameModule(),
                world.getDataSource(), save);
        GameplaySessionFactory.attachManagers(mode, services);
        var team = GameplayTeamBootstrap.registerActiveTeam(world.getGameModule(), mode.getSpriteManager(), services.configuration());
        mode.getCamera().setFocusedSprite(team.mainSprite());
        loop.setGameplayMode(mode);
        mode.getLevelManager().loadZoneAndAct(7, 0);
        loop.setGameMode(GameMode.LEVEL);
    }

    private void startMovie(String... rows) throws Exception {
        var lines = new ArrayList<>(List.of("[Input]",
                "LogKey:#Power|Reset|#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|"));
        lines.addAll(List.of(rows));
        lines.add("[/Input]");
        services.playbackDebug().startSession(new Bk2MovieLoader().parseInputLogText(directory.resolve("reset.txt"), lines), 0);
    }

    private void settleOrdinaryLevel() {
        var level = SessionManager.getCurrentGameplayMode().getLevelManager();
        for (int i = 0; i < 1200; i++) {
            var card = loop.getTitleCardProvider();
            if (loop.getCurrentGameMode() == GameMode.LEVEL
                    && !level.hasPendingFreshLevelTransitionBoundary()
                    && !SessionManager.getCurrentGameplayMode().getFadeManager().isActive()
                    && (card == null || !card.isOverlayActive()) && level.getFrameCounter() > 0) return;
            loop.step();
        }
        fail("ordinary native level entry did not finish");
    }
}
