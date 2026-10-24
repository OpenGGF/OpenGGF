package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameResult;
import com.openggf.LevelFrameStep;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.internal.NativeDynamicObjectPass;
import com.openggf.game.mode.ControlledFrameRuntime;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.objects.CdRingGlobals;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.graphics.PatternAtlas;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.SpritePresentation;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.render.PlayerResidentBanks;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Actual media and ordinary World/load/loop ownership acceptance. */
@Isolated
@DisplayName("Actual private opening World lifecycle")
class TestSonicCdOpeningWorldLoad {
    private static final EnumMap<CdReleaseVariant, CdGameDataSource> SOURCES = new EnumMap<>(CdReleaseVariant.class);

    @BeforeEach void setup() {
        TestEnvironment.resetAll();
        var c = GameServices.configuration();
        c.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        c.setSessionOverride(SonicConfiguration.SCREEN_WIDTH, 320);
        c.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT, 224);
        c.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        c.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        c.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        c.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        c.resolveDisplayAspect();
        GameServices.graphics().initHeadless();
        GameServices.audio().setBackend(new NullAudioBackend());
    }
    @AfterEach void cleanup() { SessionManager.clear(); TestEnvironment.resetAll(); }

    // Catches the new mode accidentally replacing either existing regional entry.
    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void unrunDefaultStaticAndExplicitTerrainEntryKeepTheirThreeRegionContracts(CdReleaseVariant variant) throws Exception {
        var source = source(variant);
        var stock = load(new SonicCdGameModule(), source, false);
        assertTrue(stock.mode().getSpriteManager().getAllSprites().isEmpty());
        assertNull(stock.module().getGameService(NativeDynamicObjectPass.class));
        assertNull(stock.module().getGameService(PlcLifecycleService.class));
        assertEquals(LevelFrameResult.HELD, ControlledFrameRuntime.step(stock.mode(), new InputHandler(), LogicalInputSnapshot.neutral()));
        var terrain = load(SonicCdGameModule.terrainOnlyDevelopmentPlayer(), source, true);
        assertNull(terrain.module().getGameService(NativeDynamicObjectPass.class));
        assertSame(terrain.module().requirePlayerFrame(), terrain.module().getGameService(PlcLifecycleService.class));
        step(terrain, LogicalInputSnapshot.neutral());
        assertEquals(2, terrain.module().requirePlayerActor().nativeState().motion().routine());
        assertEquals(1, terrain.mode().getSpriteManager().getAllSprites().size());
    }

    // Catches a declaration-only source check or a regional rejection after source replacement.
    @Test void unrunOpeningRejectsJapanAndEuropeBeforeModuleBindingChanges() throws Exception {
        var usa = source(CdReleaseVariant.USA);
        for (var other : List.of(CdReleaseVariant.JAPAN, CdReleaseVariant.EUROPE)) {
            var module = SonicCdGameModule.openingObjectsDevelopmentPlayer();
            var foreign = source(other);
            assertThrows(UnsupportedOperationException.class, () -> module.createGame(foreign));
            assertDoesNotThrow(() -> module.createGame(usa));
            assertNull(module.getGameService(PlcLifecycleService.class), "binding assets alone cannot install an owner");
        }
    }

    // Catches service duplication, a generic manager, missing live child visits,
    // an offscreen-enemy omission or opening factory using the terrain Frame fallback.
    @Test void unrunFirstOrdinaryWorldCycleRunsTheActualSharedOpeningPass() throws Exception {
        var h = opening();
        var objects = objects(h);
        var frame = h.module().requirePlayerFrame();
        assertEquals(source(CdReleaseVariant.USA).identity(), h.mode().getWorldSession().getDataSource().identity());
        assertSame(((CdGameDataSource) h.mode().getWorldSession().getDataSource()).assets(), frame.assets());
        assertSame(objects, h.module().getGameService(PlcLifecycleService.class));
        assertSame(objects, NativeDynamicObjectPass.forModule(h.module()));
        assertNull(h.mode().getLevelManager().getObjectManager());
        assertNull(h.mode().getLevelManager().getRingManager());
        assertEquals(1, h.mode().getSpriteManager().getAllSprites().size());
        assertTrue(h.mode().getSpriteManager().getRegisteredSidekicks().isEmpty());
        assertEquals(new CdRingGlobals(0, 3, 0, 0, 0, 0, 0, 0), objects.ringGlobals());
        assertEquals(0, objects.cpuSat().spriteCount());
        assertTrue(objects.displayedFrame().tiles().isEmpty());
        assertEquals(com.openggf.game.soniccd.player.CdPlayerBody.DynamicSlots.UNIMPLEMENTED, frame.environment().dynamicSlots());
        step(h, LogicalInputSnapshot.neutral());
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, objects.phase());
        assertEquals(1, frame.captureShared().vIntClock().deltaSinceAdmittedCut());
        assertEquals(1, h.mode().getLevelManager().getFrameCounter());
        var pool = objects.placement().pool();
        assertEquals(List.of(0x10, 0x10, 0x3D), List.of(pool.state(32).id(), pool.state(33).id(), pool.state(34).id()));
        assertEquals(List.of(0x110, 0x210, 0x230), List.of(pool.state(35).centreX(), pool.state(36).centreX(), pool.state(37).centreX()));
        assertEquals(0x26F, pool.state(34).centreX());
        assertEquals(List.of(32, 35), objects.cpuSat().provenance().stream().map(p -> p.slotRef().wholeIndex()).toList());
        assertTrue(objects.displayedFrame().tiles().isEmpty());
        assertEquals(1, objects.ringGlobals().ringAnimationFrame());
        step(h, LogicalInputSnapshot.neutral());
        assertFalse(objects.displayedFrame().tiles().isEmpty());
        assertEquals(0x26E, pool.state(34).centreX(), "Offscreen native patrol runs again");
        h.module().requirePlayerActor().requireCompletedNativeFrame();
        assertTrue(objects.displayedFrame().patternVersions().keySet().stream().allMatch(PatternAtlasRange.OBJECTS::contains));
        assertObjectRangeExactlyOnce();
    }

    @Test void unrunOpeningRejectsRosterAndConfigurationChangesBeforeService() throws Exception {
        var h = opening();
        step(h, LogicalInputSnapshot.neutral());
        var before = state(h);
        var c = GameServices.configuration();
        c.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        assertThrows(UnsupportedOperationException.class, () -> step(h, LogicalInputSnapshot.neutral()));
        c.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        assertState(before, h);
        c.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
        assertThrows(UnsupportedOperationException.class, () -> step(h, LogicalInputSnapshot.neutral()));
        c.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        assertState(before, h);
        c.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, true);
        assertThrows(UnsupportedOperationException.class, () -> step(h, LogicalInputSnapshot.neutral()));
        c.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        assertState(before, h);
        Sprite extra = mock(Sprite.class);
        when(extra.getCode()).thenReturn("foreign");
        h.mode().getSpriteManager().addSprite(extra);
        assertThrows(UnsupportedOperationException.class, h.module()::validateTerrainPlayerRequest);
        h.mode().getSpriteManager().removeSprite("foreign");
        assertState(before, h);
    }

    @Test void unrunSharedRewindRejectsPendingInputWithoutCallingTerminalRuntimeCapture() throws Exception {
        var h = opening();
        var frame = h.module().requirePlayerFrame();
        var runtime = objects(h);
        var before = state(h);
        frame.prepareInput(0); // Constructed protocol probe, not route evidence.
        assertThrows(IllegalStateException.class, () -> h.module().rewindAdapters().getFirst().capture());
        assertFalse(frame.closed());
        assertFalse(runtime.closed());
        frame.clearPendingInput();
        assertState(before, h);
        step(h, LogicalInputSnapshot.neutral());
    }

    // Catches running the tail/body/dynamics on pause, and a second conversion on PP lag.
    @Test void unrunPauseAndSemanticLagServiceHoldAllObjectPartitions() throws Exception {
        var h = opening();
        step(h, LogicalInputSnapshot.neutral());
        var objects = objects(h);
        var prior = objects.captureClosedCut();
        var beforeActor = h.module().requirePlayerFrame().captureActor();
        int levelFrame = h.mode().getLevelManager().getFrameCounter();
        step(h, input(0, 0, true));
        assertTrue(h.module().requirePlayerFrame().paused());
        assertEquals(levelFrame, h.mode().getLevelManager().getFrameCounter());
        assertEquals(beforeActor, h.module().requirePlayerFrame().captureActor());
        assertEquals(prior.placement().pool().slots(), objects.placement().capture().pool().slots());
        assertEquals(prior.ringGlobals(), objects.ringGlobals());
        assertEquals(prior.lastSoundRequests(), objects.lastSoundRequests(), "Pause retains the last-loop ledger");
        var picture = objects.displayedFrame();
        var nativeFrame = h.module().requirePlayerFrame();
        nativeFrame.prepareInput(0); // Explicit semantic held cut, not a GameLoop lag-row generator.
        h.mode().plcFrameLifecycle().runLogicalIteration(frame -> { }, h.mode().getFadeManager()::update,
                frame -> { LevelFrameStep.serviceVBlankOnly(LevelFrameContext.from(h.mode()), frame, PlcLifecyclePhase.LAG); return null; });
        assertSame(picture, objects.displayedFrame());
        assertEquals(prior.lastSoundRequests(), objects.lastSoundRequests(), "Lag does not clear or replay sounds");
        assertEquals(prior.ringGlobals(), objects.ringGlobals());
        assertEquals(prior.placement().pool().slots(), objects.placement().capture().pool().slots());
        assertNotNull(objects.captureClosedCut());
    }

    // Catches duplicate actor draw, gameplay work on redraw, or rendering using mutable
    // Frame art rather than frozen publication. This is a logic-headless purity check.
    @Test void unrunRepeatedOverlayDrawDoesNotAdvanceAnySourceOwner() throws Exception {
        var h = opening();
        for (int i = 0; i < 4; i++) step(h, LogicalInputSnapshot.neutral());
        var before = state(h);
        h.module().gameplayFrameController().drawOverlay();
        h.module().gameplayFrameController().drawOverlay();
        assertState(before, h);
        var actor = h.module().requirePlayerActor();
        actor.draw(); // Shared mode explicitly emits no private player SAT.
        assertState(before, h);
    }

    // Catches dropping queue/display/sounds/global state from course snapshots, or
    // replaying a tick on restore. Exact Body, CPU bytes, native bank and pixel versions matter.
    @Test void unrunFullAndCourseRestoreReplayEveryOwnedPartitionOnTheSameWorld() throws Exception {
        var h = opening();
        for (int i = 0; i < 4; i++) step(h, LogicalInputSnapshot.neutral());
        var registry = h.mode().getRewindRegistry();
        var full = registry.capture();
        var course = registry.captureCourse();
        assertTrue(course.containsKey("soniccd-opening-player-shared"));
        assertFalse(course.containsKey("soniccd-terrain-player-shared"));
        var before = state(h);
        var route = List.of(input(8, 0, false), input(8, 0, false), input(8, 2, false),
                LogicalInputSnapshot.neutral(), input(0, 0, true), LogicalInputSnapshot.neutral(), input(0, 0, true));
        for (var row : route) step(h, row);
        var after = state(h);
        registry.restore(full);
        assertState(before, h);
        for (var row : route) step(h, row);
        assertState(after, h);
        registry.restoreCourse(course);
        assertState(before, h);
        for (var row : route) step(h, row);
        assertState(after, h);
    }

    // Catches cross-world restore or reuse of an old source visit after fresh assembly.
    @Test void unrunForeignWorldAndOldVisitCapturesRejectWithoutReopeningRetiredOwners() throws Exception {
        var a = opening();
        step(a, LogicalInputSnapshot.neutral());
        var full = a.mode().getRewindRegistry().capture();
        var course = a.mode().getRewindRegistry().captureCourse();
        var retired = objects(a);
        var b = opening();
        var before = state(b);
        assertTrue(retired.closed());
        assertThrows(IllegalArgumentException.class, () -> b.mode().getRewindRegistry().restore(full));
        assertThrows(IllegalArgumentException.class, () -> b.mode().getRewindRegistry().restoreCourse(course));
        assertState(before, b);
        var previous = objects(b);
        var oldOwned = previous.captureClosedCut();
        b.mode().getLevelManager().loadLevel(0, LevelLoadMode.FULL, SonicCdPlayerInitProfile.openingObjectsContext());
        assertTrue(previous.closed());
        assertThrows(IllegalArgumentException.class, () -> objects(b).validateRestore(oldOwned));
        assertEquals(0, objects(b).cpuSat().spriteCount());
        assertTrue(objects(b).displayedFrame().tiles().isEmpty());
    }

    // Catches duplicate atlas registration on retained headless reset, clearing a
    // foreign governed range, or rebuilding background before rebinding source assets.
    @Test void unrunRetainedResetKeepsTerrainAndAtlasGovernanceButCreatesFreshNativeOwners() throws Exception {
        var h = opening();
        for (int i = 0; i < 4; i++) step(h, LogicalInputSnapshot.neutral());
        h.module().gameplayFrameController().drawOverlay();
        var old = objects(h);
        var oldFrame = h.module().requirePlayerFrame();
        var level = h.mode().getLevelManager().getCurrentLevel();
        var atlas = GameServices.graphics().getPatternAtlas();
        atlas.registerRange(PatternAtlasRange.MENU_AND_DATA_SELECT);
        var foreign = new PatternAtlas.PatternRange(PatternAtlasRange.MENU_AND_DATA_SELECT.base(),
                PatternAtlasRange.MENU_AND_DATA_SELECT.size(), PatternAtlasRange.MENU_AND_DATA_SELECT.category());
        assertDoesNotThrow(TestEnvironment::resetPerTest);
        assertSame(level, h.mode().getLevelManager().getCurrentLevel());
        assertSame(atlas, GameServices.graphics().getPatternAtlas());
        assertTrue(atlas.registeredRangesForTesting().contains(foreign));
        assertObjectRangeExactlyOnce();
        assertTrue(old.closed());
        assertTrue(oldFrame.closed());
        assertNotSame(old, objects(h));
        assertEquals(new CdRingGlobals(0, 3, 0, 0, 0, 0, 0, 0), objects(h).ringGlobals());
        assertEquals(0, objects(h).placement().pool().state(32).id());
        assertEquals(0, objects(h).cpuSat().spriteCount());
        assertTrue(objects(h).displayedFrame().tiles().isEmpty());
        assertEquals(0x70B, h.mode().getCamera().getY());
        assertEquals(0, h.module().requirePlayerFrame().state().history().index());
        step(h, LogicalInputSnapshot.neutral());
        assertEquals(0x26F, objects(h).placement().pool().state(34).centreX());
    }

    // Catches incomplete teardown, cleanup that removes an unrelated actor, or
    // returning a stale closed runtime after cancellation/failure.
    @Test void unrunCancellationAndAdvancedFailureRetireOnlyOwnedObjects() throws Exception {
        var h = opening();
        step(h, LogicalInputSnapshot.neutral());
        var old = objects(h);
        h.module().getLevelInitProfile().cancelPendingLevelLoadWork();
        h.module().getLevelInitProfile().cancelPendingLevelLoadWork();
        assertTrue(old.closed());
        assertNull(h.module().getGameService(PlcLifecycleService.class));
        assertNull(h.module().getGameService(NativeDynamicObjectPass.class));
        h.mode().getLevelManager().loadLevel(0, LevelLoadMode.FULL, SonicCdPlayerInitProfile.openingObjectsContext());
        step(h, LogicalInputSnapshot.neutral());
        var broken = objects(h);
        var brokenFrame = h.module().requirePlayerFrame();
        broken.placement().pool().write(broken.placement().pool().state(34).moveByte(CdObjectState.ByteField.ID, 0x7E));
        assertThrows(UnsupportedOperationException.class, () -> step(h, LogicalInputSnapshot.neutral()));
        assertTrue(broken.closed());
        assertTrue(brokenFrame.closed());
        assertThrows(IllegalStateException.class, h.module().gameplayFrameController()::drawOverlay);
        // This sentinel observes cleanup identity only, never simulates gameplay.
        h.mode().getSpriteManager().removeSprite("sonic");
        Sprite foreign = mock(Sprite.class);
        when(foreign.getCode()).thenReturn("sonic");
        h.mode().getSpriteManager().addSprite(foreign);
        h.module().closeTerrainPlayer();
        assertSame(foreign, h.mode().getSpriteManager().getSprite("sonic"));
    }

    private record Harness(SonicCdGameModule module, GameplayModeContext mode, GameLoop loop) { }
    private static Harness opening() throws Exception { return load(SonicCdGameModule.openingObjectsDevelopmentPlayer(), source(CdReleaseVariant.USA), true); }
    private static Harness load(SonicCdGameModule module, CdGameDataSource source, boolean player) throws Exception {
        var mode = SessionManager.openGameplaySession(module, module, source, null);
        GameplaySessionFactory.attachManagers(mode, EngineServices.current());
        GameModuleRegistry.setCurrent(module);
        var loop = new GameLoop(EngineServices.current());
        loop.setGameplayMode(mode); loop.setInputHandler(new InputHandler()); loop.setGameMode(GameMode.LEVEL);
        mode.getLevelManager().loadLevel(0, LevelLoadMode.FULL,
                player ? (module.openingObjectsAdmission() ? SonicCdPlayerInitProfile.openingObjectsContext()
                        : SonicCdPlayerInitProfile.terrainPlayerContext()) : SonicCdLevelInitProfile.staticStageContext());
        return new Harness(module, mode, loop);
    }
    private static CdOpeningObjectRuntime objects(Harness h) {
        return assertInstanceOf(CdOpeningObjectRuntime.class, h.module().getGameService(NativeDynamicObjectPass.class));
    }
    private static void step(Harness h, LogicalInputSnapshot row) {
        h.loop().getInputHandler().setLogicalOverride(row); h.loop().step();
    }
    private static LogicalInputSnapshot input(int directions, int actions, boolean start) {
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(directions, 0, actions, 0, start, false), PlayerInputState.neutral());
    }
    private record State(CdPlayerFrameRuntime.ActorState actor, CdPlayerFrameRuntime.SharedState shared,
                         CdOpeningObjectRuntime.Snapshot objects, CdObjectDrawQueue.CpuSat cpu,
                         SpritePresentation.Frame picture, Object camera, Object scroll, int levelFrame,
                         PlayerResidentBanks.Snapshot resident) { }
    private static State state(Harness h) {
        var frame = h.module().requirePlayerFrame(); var objects = objects(h);
        return new State(frame.captureActor(), frame.captureShared(), objects.captureClosedCut(), objects.cpuSat(), objects.displayedFrame(),
                h.mode().getCamera().capture(), h.module().getScrollHandlerProvider().getHandler(0).captureRewindState(),
                h.mode().getLevelManager().getFrameCounter(), PlayerResidentBanks.capture(h.module().requirePlayerActor().getSpriteRenderer()));
    }
    private static void assertState(State expected, Harness h) {
        var actual = state(h);
        assertEquals(expected.actor(), actual.actor());
        assertEquals(expected.shared().environment(), actual.shared().environment());
        assertEquals(expected.shared().vIntClock(), actual.shared().vIntClock());
        assertEquals(expected.shared().paused(), actual.shared().paused());
        assertEquals(expected.shared().artState().lastFrame(), actual.shared().artState().lastFrame());
        assertEquals(expected.shared().artState().dirty(), actual.shared().artState().dirty());
        assertArrayEquals(expected.shared().artState().cpuBuffer().copy(), actual.shared().artState().cpuBuffer().copy());
        assertArrayEquals(expected.shared().artState().residentBank().copy(), actual.shared().artState().residentBank().copy());
        assertEquals(expected.objects().placement().pool().slots(), actual.objects().placement().pool().slots());
        assertEquals(expected.objects().placement().status(), actual.objects().placement().status());
        assertArrayEquals(expected.objects().placement().savedFlags().copy(), actual.objects().placement().savedFlags().copy());
        assertEquals(expected.objects().ringGlobals(), actual.objects().ringGlobals());
        assertEquals(expected.objects().lastSoundRequests(), actual.objects().lastSoundRequests());
        assertArrayEquals(expected.cpu().bytes(), actual.cpu().bytes());
        assertEquals(expected.cpu().spriteCount(), actual.cpu().spriteCount());
        assertEquals(expected.cpu().provenance(), actual.cpu().provenance());
        assertEquals(expected.picture(), actual.picture());
        assertEquals(expected.camera(), actual.camera()); assertEquals(expected.scroll(), actual.scroll());
        assertEquals(expected.levelFrame(), actual.levelFrame());
        var a = expected.resident().tiles(); var b = actual.resident().tiles();
        assertEquals(24, b.length);
        for (int i = 0; i < 24; i++) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) assertEquals(a[i].getPixel(x, y), b[i].getPixel(x, y));
    }
    private static void assertObjectRangeExactlyOnce() {
        assertEquals(1, GameServices.graphics().getPatternAtlas().registeredRangesForTesting().stream()
                .filter(r -> r.base() == PatternAtlasRange.OBJECTS.base() && r.size() == PatternAtlasRange.OBJECTS.size()).count());
    }
    private static CdGameDataSource source(CdReleaseVariant variant) throws IOException {
        if (SOURCES.containsKey(variant)) return SOURCES.get(variant);
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "UNRUN actual-media opt-in: set soniccd.media.root");
        String folder = switch (variant) { case USA -> "USA"; case JAPAN -> "Japan"; case EUROPE -> "Europe"; };
        try (var files = Files.list(Path.of(root).resolve(folder))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured media must identify exactly one actual regional CUE");
            var loaded = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a()); SOURCES.put(variant, loaded); return loaded;
        }
    }
}
