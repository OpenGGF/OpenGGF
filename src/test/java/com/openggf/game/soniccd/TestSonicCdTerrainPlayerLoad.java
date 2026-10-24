package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.LevelFrameResult;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.InitStep;
import com.openggf.game.InitialProcessSpritesLifecycle;
import com.openggf.game.LevelAssemblyKind;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.mode.ControlledFrameRuntime;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayInputFilterAccess;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.game.soniccd.player.CdPlayableSonic;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.resources.CdPlayerVisualAdapter;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.level.Pattern;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.render.PlayerResidentBanks;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Actual authenticated regional lifecycle and same-world replay through the normal load owner. */
@Isolated
class TestSonicCdTerrainPlayerLoad {
    @BeforeEach void prepareEngine() {
        TestEnvironment.resetAll();
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH, 320);
        config.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT, 224);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        config.resolveDisplayAspect();
        GameServices.graphics().initHeadless();
        GameServices.audio().setBackend(new NullAudioBackend());
    }

    @AfterEach void releaseEngine() {
        SessionManager.clear();
        TestEnvironment.resetAll();
    }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void realLifecycleStartsWithInitOnlyThenTerrainContactAndActualInput(CdReleaseVariant variant) throws Exception {
        var source = source(variant);
        var h = load(source);
        var module = h.module();
        var mode = h.mode();
        var actor = module.requirePlayerActor();
        var frame = module.requirePlayerFrame();
        assertSame(source, mode.getWorldSession().getDataSource());
        assertTrue(source.rom().isEmpty());
        assertSame(module.getPlayableCharacterRegistry(), mode.getWorldSession().getPlayableCharacterRegistry());
        assertEquals(List.of(CharacterKey.SONIC), List.copyOf(module.getPlayableCharacterRegistry().definitions().keySet()));
        assertSame(actor, mode.getSpriteManager().getSprite("sonic"));
        assertEquals(1, mode.getSpriteManager().getAllSprites().size());
        assertTrue(mode.getSpriteManager().getRegisteredSidekicks().isEmpty());
        var level = assertInstanceOf(SonicCdLevel.class, mode.getLevelManager().getCurrentLevel());
        assertSame(source.assets(), level.assets());
        assertFalse(level.nativePlacements().records().isEmpty(), "native placements are retained, not asserted empty");
        assertNull(mode.getLevelManager().getObjectManager());
        assertNull(mode.getLevelManager().getRingManager());
        assertEquals(GameMode.LEVEL, h.loop().getCurrentGameMode());
        assertSame(frame, module.getGameService(PlcLifecycleService.class));
        assertEquals(InitialProcessSpritesLifecycle.NONE, module.getLevelInitProfile().initialProcessSpritesLifecycle());
        assertEquals(0, module.getLevelInitProfile().freshMainPlayablePreludeFrames());
        assertEquals(0, module.getLevelInitProfile().skippedPresentationPlayableFrames());
        assertFalse(ControlledFrameRuntime.prepareSetup(mode));
        assertEquals(0, frame.state().motion().routine());
        assertEquals(64, actor.getCentreX());
        assertEquals(1899, actor.getCentreY());
        assertEquals(0, frame.state().header().tile());
        assertTrue(frame.display().isEmpty());
        assertEquals(0, mode.getLevelManager().getFrameCounter());
        assertEquals(0, frame.captureShared().vIntClock().deltaSinceAdmittedCut());
        assertTrue(frame.captureShared().vIntClock().absoluteSeed().isEmpty());
        assertArrayEquals(new byte[0x300], frame.artState().residentBank().copy());
        assertResidentMatches(frame, actor);

        step(h, LogicalInputSnapshot.neutral());
        assertEquals(2, frame.state().motion().routine());
        assertEquals(1899, actor.getCentreY(), "Init must not pre-land");
        assertEquals(0, frame.state().history().index());
        assertTrue(frame.display().isEmpty(), "Init has no Main/display/history/animation tail");
        assertEquals(0x780, frame.state().header().tile());
        assertEquals(0, frame.artState().lastFrame());
        assertArrayEquals(new byte[0x300], frame.artState().residentBank().copy());
        assertEquals(1, mode.getLevelManager().getFrameCounter());

        step(h, LogicalInputSnapshot.neutral());
        assertEquals(1898, actor.getCentreY());
        assertEquals(0xFC, actor.getAngle() & 255, "the real regional terrain supplies the paired-ground angle");
        assertEquals(4, frame.state().history().index());
        assertTrue(frame.display().isPresent());
        assertEquals(0, frame.artState().lastFrame(), "Main publishes the next service's cue");
        assertArrayEquals(new byte[0x300], frame.artState().residentBank().copy());
        int publishedMap = frame.state().animation().mapFrame();
        assertNotEquals(0, publishedMap);
        step(h, LogicalInputSnapshot.neutral());
        assertEquals(publishedMap, frame.artState().lastFrame());
        assertResidentMatches(frame, actor);
        int beforeX = actor.getCentreX();
        for (int i = 0; i < 12; i++) step(h, input(AbstractPlayableSprite.INPUT_RIGHT, 0, false));
        assertTrue(actor.getCentreX() > beforeX, "production input reaches native movement through GameLoop");
        assertEquals(0, mode.getLevelManager().getLevelGamestate().getTimerFrames());
        assertTrue(mode.getLevelManager().getLevelGamestate().isTimerPaused());
        assertTrue(mode.hardwareTiming().pendingHandles().isEmpty());
    }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void startHeldPauseServicesRawInputAndArtWithoutBodyOrLevelClock(CdReleaseVariant variant) throws Exception {
        var h = load(source(variant));
        for (int i = 0; i < 3; i++) step(h, LogicalInputSnapshot.neutral());
        var frame = h.module().requirePlayerFrame();
        var actor = h.module().requirePlayerActor();
        // Keep A held across entry; a new A/B/C tap while paused is the actual
        // regional reset predicate, not an admitted way to exercise pause input.
        step(h, input(0, InputActionMasks.ACTION_A, false));
        var beforeBody = frame.captureActor();
        var beforeCamera = h.mode().getCamera().capture();
        int beforeLevel = h.mode().getLevelManager().getFrameCounter();
        long beforeVInt = frame.captureShared().vIntClock().deltaSinceAdmittedCut();
        assertEquals(LevelFrameResult.HELD, controlledStep(h, input(0, InputActionMasks.ACTION_A, true)));
        assertTrue(frame.paused());
        assertEquals(0xC0, frame.environment().controls().rawHeld());
        assertEquals(0x80, frame.environment().controls().rawTap());
        assertEquals(beforeBody, frame.captureActor());
        assertEquals(beforeCamera, h.mode().getCamera().capture());
        assertEquals(beforeLevel, h.mode().getLevelManager().getFrameCounter());
        assertEquals(beforeVInt + 1, frame.captureShared().vIntClock().deltaSinceAdmittedCut());
        assertEquals(LevelFrameResult.HELD, controlledStep(h, input(0, InputActionMasks.ACTION_A, true)));
        assertEquals(0, frame.environment().controls().rawTap(), "held Start must not toggle twice");
        assertEquals(beforeBody, frame.captureActor());
        assertEquals(LevelFrameResult.HELD, controlledStep(h, LogicalInputSnapshot.neutral()));
        step(h, input(0, 0, true));
        assertFalse(frame.paused());
        assertEquals(beforeLevel + 1, h.mode().getLevelManager().getFrameCounter());
        assertEquals(beforeVInt + 4, frame.captureShared().vIntClock().deltaSinceAdmittedCut());
        assertEquals(0x80, frame.environment().controls().rawTap());
        assertNotEquals(beforeBody, frame.captureActor());
        assertResidentMatches(frame, actor);
    }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void fullAndCourseRestoreAllOwnersAndReplayTheSameProductionInputs(CdReleaseVariant variant) throws Exception {
        var h = load(source(variant));
        var registry = h.mode().getRewindRegistry();
        var cold = registry.capture();
        step(h, LogicalInputSnapshot.neutral());
        registry.restore(cold);
        assertEquals(0, h.module().requirePlayerActor().nativeState().motion().routine());
        assertEquals(0, h.mode().getLevelManager().getFrameCounter());
        for (int i = 0; i < 4; i++) step(h, LogicalInputSnapshot.neutral());
        // Constructed restore stress, not a claimed reachable cold-native state:
        // the fixed $2E0 transfer must retain a nonzero 24th tile across replay.
        var frame = h.module().requirePlayerFrame();
        var shared = frame.captureShared();
        byte[] resident = shared.artState().residentBank().copy();
        java.util.Arrays.fill(resident, 0x2E0, 0x300, (byte) 0x5A);
        frame.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(), shared.environment(),
                new CdPlayerArtRuntime.State(shared.artState().cpuBuffer().copy(), resident,
                        shared.artState().lastFrame(), shared.artState().dirty()), shared.paused(), shared.vIntClock()));
        registry.restore(registry.capture()); // Existing post-restore callback publishes the complete bank.
        var checkpoint = state(h);
        var full = registry.capture();
        var course = registry.captureCourse();
        assertTrue(course.containsKey("soniccd-terrain-player-shared"), "course includes shared art/input/pause/clock");
        var inputs = List.of(input(AbstractPlayableSprite.INPUT_RIGHT, 0, false),
                input(AbstractPlayableSprite.INPUT_RIGHT, InputActionMasks.ACTION_B, false),
                input(AbstractPlayableSprite.INPUT_RIGHT, InputActionMasks.ACTION_B | InputActionMasks.ACTION_C, false),
                LogicalInputSnapshot.neutral(), input(0, 0, true), input(0, 0, true),
                LogicalInputSnapshot.neutral(), input(0, 0, true), LogicalInputSnapshot.neutral());
        for (var input : inputs) step(h, input);
        var expected = state(h);
        registry.restore(full);
        assertState(checkpoint, h);
        for (var input : inputs) step(h, input);
        assertState(expected, h);
        registry.restoreCourse(course);
        assertState(checkpoint, h);
        for (var input : inputs) step(h, input);
        assertState(expected, h);
    }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void foreignWorldSnapshotsRejectBeforeAnyTargetOwnerChanges(CdReleaseVariant variant) throws Exception {
        var source = source(variant);
        var first = load(source);
        step(first, LogicalInputSnapshot.neutral());
        var full = first.mode().getRewindRegistry().capture();
        var course = first.mode().getRewindRegistry().captureCourse();
        var target = load(source);
        assertNotSame(first.mode().getWorldSession(), target.mode().getWorldSession());
        var before = state(target);
        var level = target.mode().getLevelManager().getCurrentLevel();
        var tiles = target.mode().getLevelManager().getTilemapManager();
        assertThrows(IllegalArgumentException.class, () -> target.mode().getRewindRegistry().restore(full));
        assertThrows(IllegalArgumentException.class, () -> target.mode().getRewindRegistry().restoreCourse(course));
        assertState(before, target);
        assertSame(level, target.mode().getLevelManager().getCurrentLevel());
        assertSame(tiles, target.mode().getLevelManager().getTilemapManager());
    }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void unsupportedRequestsAndFactoriesRejectWithoutReplacingTheAdmittedPlayer(CdReleaseVariant variant) throws Exception {
        var source = source(variant); // Media preparation is outside every assertThrows.
        var h = load(source);
        var before = state(h);
        var level = h.mode().getLevelManager().getCurrentLevel();
        for (var cause : LevelLoadCause.values()) {
            if (cause == LevelLoadCause.FULL_LEVEL_ASSEMBLY) continue;
            var request = SonicCdPlayerInitProfile.terrainPlayerContext();
            request.setLoadCause(cause);
            assertThrows(IOException.class, () -> h.mode().getLevelManager().loadLevel(0, LevelLoadMode.FULL, request));
            assertState(before, h);
        }
        var title = SonicCdPlayerInitProfile.terrainPlayerContext();
        title.setShowTitleCard(true);
        assertThrows(IOException.class, () -> h.mode().getLevelManager().loadLevel(0, LevelLoadMode.FULL, title));
        var restoration = SonicCdPlayerInitProfile.terrainPlayerContext();
        restoration.setAssemblyKind(LevelAssemblyKind.STATE_RESTORATION);
        assertThrows(IOException.class, () -> h.mode().getLevelManager().loadLevel(0, LevelLoadMode.FULL, restoration));
        var preview = SonicCdPlayerInitProfile.terrainPlayerContext();
        assertThrows(IOException.class, () -> h.mode().getLevelManager().loadLevel(0, LevelLoadMode.PREVIEW_CAPTURE, preview));
        var factory = h.module().getPlayableCharacterRegistry().find(CharacterKey.SONIC).orElseThrow().spriteFactory();
        assertThrows(IllegalStateException.class, () -> factory.create("sonic", 64, 1899));
        assertTrue(h.module().getPlayableCharacterRegistry().find(CharacterKey.TAILS).isEmpty());
        assertSame(level, h.mode().getLevelManager().getCurrentLevel());
        assertState(before, h);
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, true);
        assertThrows(UnsupportedOperationException.class, h.module()::validateTerrainPlayerRequest);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        assertThrows(UnsupportedOperationException.class, h.module()::validateTerrainPlayerRequest);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        GameplayInputFilterAccess.install(h.mode(), value -> value);
        assertThrows(UnsupportedOperationException.class, h.module()::validateTerrainPlayerRequest);
        GameplayInputFilterAccess.install(h.mode(), com.openggf.game.GameplayInputFilter.IDENTITY);
        assertState(before, h);
    }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void cancellationFreshReloadAndOwnedCleanupDoNotRetireForeignSprites(CdReleaseVariant variant) throws Exception {
        var h = load(source(variant));
        step(h, LogicalInputSnapshot.neutral());
        var previous = h.module().requirePlayerFrame();
        var previousActor = h.module().requirePlayerActor();
        h.mode().getLevelManager().loadLevel(0, LevelLoadMode.FULL, SonicCdPlayerInitProfile.terrainPlayerContext());
        assertTrue(previous.closed());
        assertNotSame(previousActor, h.module().requirePlayerActor());
        assertEquals(0, h.module().requirePlayerFrame().state().history().index());
        assertEquals(0, h.module().requirePlayerFrame().state().motion().routine());
        assertEquals(0, h.mode().getLevelManager().getFrameCounter());
        var frame = h.module().requirePlayerFrame();
        h.module().getLevelInitProfile().cancelPendingLevelLoadWork();
        h.module().getLevelInitProfile().cancelPendingLevelLoadWork();
        assertTrue(frame.closed());
        assertNull(h.module().getGameService(PlcLifecycleService.class));
        assertTrue(h.mode().getSpriteManager().getAllSprites().isEmpty());
        assertThrows(IllegalStateException.class, () -> frame.prepareInput(0));

        var steps = h.module().getLevelInitProfile().levelLoadSteps(SonicCdPlayerInitProfile.terrainPlayerContext());
        for (int i = 0; i < steps.size() - 1; i++) steps.get(i).execute();
        // A test-owned foreign sentinel checks rejection before factory/registration;
        // it does not substitute any gameplay, source, terrain, actor or renderer.
        Sprite foreign = mock(Sprite.class);
        when(foreign.getCode()).thenReturn("foreign");
        h.mode().getSpriteManager().addSprite(foreign);
        assertThrows(UnsupportedOperationException.class, () -> steps.getLast().execute());
        h.module().getLevelInitProfile().cancelPendingLevelLoadWork();
        assertSame(foreign, h.mode().getSpriteManager().getSprite("foreign"));
        assertNull(h.module().getGameService(PlcLifecycleService.class));
        h.mode().getSpriteManager().removeSprite("foreign");
        h.mode().getLevelManager().loadLevel(0, LevelLoadMode.FULL, SonicCdPlayerInitProfile.terrainPlayerContext());
        var retired = h.module().requirePlayerFrame();
        h.mode().getSpriteManager().removeSprite("sonic");
        when(foreign.getCode()).thenReturn("sonic");
        h.mode().getSpriteManager().addSprite(foreign);
        h.module().closeTerrainPlayer();
        h.module().closeTerrainPlayer();
        assertTrue(retired.closed());
        assertSame(foreign, h.mode().getSpriteManager().getSprite("sonic"), "cleanup removes only the owned actor identity");
        h.mode().getSpriteManager().removeSprite("sonic");
        for (InitStep step : h.module().getLevelInitProfile().levelTeardownSteps()) step.execute();
        assertNull(h.mode().getLevelManager().getCurrentLevel());
    }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void retainedLevelResetRebindsBackgroundBeforeRebuildingAColdPlayer(CdReleaseVariant variant) throws Exception {
        var source = source(variant);
        var h = load(source);
        for (int i = 0; i < 4; i++) step(h, LogicalInputSnapshot.neutral());
        for (int i = 0; i < 8; i++) step(h, input(AbstractPlayableSprite.INPUT_RIGHT, 0, false));
        var mode = h.mode();
        var world = mode.getWorldSession();
        var registry = world.getPlayableCharacterRegistry();
        var level = assertInstanceOf(SonicCdLevel.class, mode.getLevelManager().getCurrentLevel());
        var tilemap = mode.getLevelManager().getTilemapManager();
        var pattern = level.getPattern(1);
        var palette = level.getPalette(0);
        var oldFrame = h.module().requirePlayerFrame();
        var oldActor = h.module().requirePlayerActor();
        var oldOwner = oldFrame.captureShared().owner();
        assertTrue(oldFrame.state().history().index() > 0);
        assertFalse(java.util.Arrays.equals(new byte[0x300], oldFrame.artState().residentBank().copy()),
                "reset must retire a populated native bank, not only an already cold actor");
        assertSame(h.module(), GameModuleRegistry.getCurrent(), "the real reset consumer selects this profile");

        assertDoesNotThrow(TestEnvironment::resetPerTest);

        assertSame(mode, SessionManager.getCurrentGameplayMode());
        assertSame(world, mode.getWorldSession());
        assertSame(source, world.getDataSource());
        assertSame(registry, world.getPlayableCharacterRegistry());
        assertSame(level, mode.getLevelManager().getCurrentLevel());
        assertSame(level, world.getCurrentLevel());
        assertSame(source.assets(), level.assets());
        assertSame(tilemap, mode.getLevelManager().getTilemapManager());
        assertSame(pattern, level.getPattern(1));
        assertSame(palette, level.getPalette(0));
        assertTrue(oldFrame.closed());
        assertFalse(mode.getSpriteManager().getAllSprites().contains(oldActor));
        assertThrows(IllegalStateException.class, () -> oldFrame.prepareInput(0));
        var frame = h.module().requirePlayerFrame();
        var actor = h.module().requirePlayerActor();
        assertNotSame(oldFrame, frame);
        assertNotSame(oldActor, actor);
        assertNotSame(oldOwner, frame.captureShared().owner());
        assertSame(source.assets(), frame.assets());
        assertSame(actor, mode.getSpriteManager().getSprite("sonic"));
        assertSame(actor, mode.getCamera().getFocusedSprite());
        assertEquals(1, mode.getSpriteManager().getAllSprites().size());
        assertTrue(mode.getSpriteManager().getRegisteredSidekicks().isEmpty());
        assertEquals(0, mode.getLevelManager().getFrameCounter());
        assertEquals(0, mode.getCamera().getX());
        assertEquals(0x70B, mode.getCamera().getY());
        assertEquals(0, frame.state().motion().routine());
        assertEquals(64, actor.getCentreX());
        assertEquals(1899, actor.getCentreY());
        assertEquals(0, frame.state().history().index());
        assertArrayEquals(new byte[0x100], frame.state().history().bytes().copy());
        assertEquals(0, frame.state().header().tile());
        assertEquals(0, frame.state().animation().mapFrame());
        assertTrue(frame.display().isEmpty());
        assertFalse(frame.paused());
        assertEquals(0, frame.captureShared().vIntClock().deltaSinceAdmittedCut());
        assertTrue(frame.captureShared().vIntClock().absoluteSeed().isEmpty());
        assertEquals(0, frame.artState().lastFrame());
        assertFalse(frame.artState().dirty());
        assertArrayEquals(new byte[0x300], frame.artState().cpuBuffer().copy());
        assertArrayEquals(new byte[0x300], frame.artState().residentBank().copy());
        assertResidentMatches(frame, actor);

        step(h, LogicalInputSnapshot.neutral());
        assertEquals(1, mode.getLevelManager().getFrameCounter());
        assertEquals(1, frame.captureShared().vIntClock().deltaSinceAdmittedCut());
        assertEquals(2, frame.state().motion().routine());
        assertEquals(64, actor.getCentreX());
        assertEquals(1899, actor.getCentreY(), "the first reset frame runs Init only, without pre-landing");
        assertEquals(0x780, frame.state().header().tile());
        assertEquals(0, frame.state().history().index());
        assertArrayEquals(new byte[0x100], frame.state().history().bytes().copy());
        assertTrue(frame.display().isEmpty());
        assertEquals(0, frame.artState().lastFrame());
        assertArrayEquals(new byte[0x300], frame.artState().residentBank().copy());
        assertResidentMatches(frame, actor);
    }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void defaultConstructionStillLoadsTheUnchangedStaticHoldEntry(CdReleaseVariant variant) throws Exception {
        var source = source(variant);
        var module = new SonicCdGameModule();
        var mode = open(module, source);
        mode.getLevelManager().loadLevel(0, LevelLoadMode.FULL, SonicCdLevelInitProfile.staticStageContext());
        assertFalse(module.terrainPlayerAdmission());
        assertTrue(module.getPlayableCharacterRegistry().definitions().isEmpty());
        assertTrue(module.rewindAdapters().isEmpty());
        assertNull(module.getGameService(PlcLifecycleService.class));
        assertNull(mode.getCamera().getFocusedSprite());
        assertTrue(mode.getSpriteManager().getAllSprites().isEmpty());
        assertEquals(LevelFrameResult.HELD, ControlledFrameRuntime.step(mode, new InputHandler(), LogicalInputSnapshot.neutral()));
        assertEquals(0, mode.getLevelManager().getFrameCounter());
        assertEquals(0, mode.getLevelManager().getLevelGamestate().getTimerFrames());
    }

    private record Harness(SonicCdGameModule module, GameplayModeContext mode, GameLoop loop) { }

    private static GameplayModeContext open(SonicCdGameModule module, CdGameDataSource source) {
        var mode = SessionManager.openGameplaySession(module, module, source, null);
        GameplaySessionFactory.attachManagers(mode, EngineServices.current());
        return mode;
    }

    private static Harness load(CdGameDataSource source) throws Exception {
        var module = SonicCdGameModule.terrainOnlyDevelopmentPlayer();
        var mode = open(module, source);
        var loop = new GameLoop(EngineServices.current());
        loop.setGameplayMode(mode);
        loop.setInputHandler(new InputHandler());
        loop.setGameMode(GameMode.LEVEL);
        mode.getLevelManager().loadLevel(0, LevelLoadMode.FULL, SonicCdPlayerInitProfile.terrainPlayerContext());
        return new Harness(module, mode, loop);
    }

    private static LogicalInputSnapshot input(int directions, int actions, boolean start) {
        // Native taps must come from sampled held history, even with no host pressed bits.
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(directions, 0, actions, 0, start, false),
                PlayerInputState.neutral());
    }

    private static void step(Harness h, LogicalInputSnapshot input) {
        h.loop().getInputHandler().setLogicalOverride(input);
        h.loop().step();
    }

    private static LevelFrameResult controlledStep(Harness h, LogicalInputSnapshot input) {
        h.loop().getInputHandler().setLogicalOverride(input);
        return ControlledFrameRuntime.step(h.mode(), h.loop().getInputHandler(), input);
    }

    private record State(CdPlayerFrameRuntime.ActorState actor, CdPlayerFrameRuntime.SharedState shared,
                         Object camera, Object scroll, int levelFrame, List<CdPlayerFrameRuntime.Sound> sounds,
                         Pattern[] resident) { }

    private static State state(Harness h) {
        var actor = h.module().requirePlayerActor();
        var frame = h.module().requirePlayerFrame();
        return new State(frame.captureActor(), frame.captureShared(), h.mode().getCamera().capture(),
                h.module().getScrollHandlerProvider().getHandler(0).captureRewindState(),
                h.mode().getLevelManager().getFrameCounter(), actor.lastSoundRequests(),
                PlayerResidentBanks.capture(actor.getSpriteRenderer()).tiles());
    }

    private static void assertState(State expected, Harness h) {
        var actual = state(h);
        assertEquals(expected.actor(), actual.actor());
        assertSame(expected.shared().owner(), actual.shared().owner());
        assertEquals(expected.shared().environment(), actual.shared().environment());
        assertEquals(expected.shared().paused(), actual.shared().paused());
        assertEquals(expected.shared().vIntClock(), actual.shared().vIntClock());
        assertEquals(expected.shared().artState().lastFrame(), actual.shared().artState().lastFrame());
        assertEquals(expected.shared().artState().dirty(), actual.shared().artState().dirty());
        assertArrayEquals(expected.shared().artState().cpuBuffer().copy(), actual.shared().artState().cpuBuffer().copy());
        assertArrayEquals(expected.shared().artState().residentBank().copy(), actual.shared().artState().residentBank().copy());
        assertEquals(expected.camera(), actual.camera());
        assertScroll(expected.scroll(), actual.scroll());
        assertEquals(expected.levelFrame(), actual.levelFrame());
        assertEquals(expected.sounds(), actual.sounds());
        assertPatterns(expected.resident(), actual.resident());
        assertResidentMatches(h.module().requirePlayerFrame(), h.module().requirePlayerActor());
    }

    private static void assertResidentMatches(CdPlayerFrameRuntime frame, CdPlayableSonic actor) {
        assertPatterns(CdPlayerVisualAdapter.residentPatterns(frame.artState()),
                PlayerResidentBanks.capture(actor.getSpriteRenderer()).tiles());
    }

    private static void assertPatterns(Pattern[] expected, Pattern[] actual) {
        assertEquals(24, expected.length);
        assertEquals(24, actual.length);
        for (int i = 0; i < 24; i++) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            assertEquals(expected[i].getPixel(x, y), actual[i].getPixel(x, y), "resident tile " + i + "/" + x + "/" + y);
        }
    }

    private static CdGameDataSource source(CdReleaseVariant variant) throws IOException {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Supply soniccd.media.root for authenticated retail media");
        String directory = switch (variant) { case USA -> "USA"; case JAPAN -> "Japan"; case EUROPE -> "Europe"; };
        try (var paths = Files.list(Path.of(root).resolve(directory))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            return CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
    }
    private static void assertScroll(Object expected, Object actual) {
        var a = assertInstanceOf(SonicCdPlayerPresentation.ScrollCapture.class, expected);
        var b = assertInstanceOf(SonicCdPlayerPresentation.ScrollCapture.class, actual);
        assertSame(a.owner(), b.owner());
        assertEquals(a.state(), b.state());
        var x = a.background(); var y = b.background();
        assertEquals(x.cameraBgX(), y.cameraBgX()); assertEquals(x.cameraBgY(), y.cameraBgY());
        assertEquals(x.cameraBg2X(), y.cameraBg2X()); assertEquals(x.cameraBg2Y(), y.cameraBg2Y());
        assertEquals(x.cameraBg3X(), y.cameraBg3X()); assertEquals(x.cameraBg3Y(), y.cameraBg3Y());
        assertEquals(x.scrollFlagsBg(), y.scrollFlagsBg()); assertEquals(x.scrollFlagsBg2(), y.scrollFlagsBg2());
        assertEquals(x.scrollFlagsBg3(), y.scrollFlagsBg3()); assertEquals(x.vscroll(), y.vscroll());
        assertArrayEquals(x.crossingRam().copy(), y.crossingRam().copy());
        assertArrayEquals(x.cloudRam().copy(), y.cloudRam().copy());
        assertArrayEquals(x.deformationRam().copy(), y.deformationRam().copy());
        assertArrayEquals(x.hscrollRam().copy(), y.hscrollRam().copy());
    }

}
