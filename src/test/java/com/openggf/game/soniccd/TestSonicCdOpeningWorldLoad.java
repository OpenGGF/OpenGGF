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
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane;
import com.openggf.game.soniccd.scroll.CdR11aForegroundScroll;
import com.openggf.level.LevelSpritePresentation;
import com.openggf.level.NativeBackgroundTestAccess;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.objects.CdOpeningObjectRuntime;
import com.openggf.game.soniccd.objects.CdRingGlobals;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
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
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
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
        var terrainPlc = terrain.module().getGameService(PlcLifecycleService.class);
        assertNotNull(terrainPlc);
        assertNotSame(terrain.module().requirePlayerFrame(), terrainPlc);
        assertSame(terrainPlc, terrain.module().getGameService(PlcLifecycleService.class));
        assertEquals(0, terrain.module().requirePlayerFrame().state().motion().routine());
        assertEquals(0x10000, scroll(terrain).background().cloudRam().unsignedLong(0), "one load-time call before player Init");
        step(terrain, LogicalInputSnapshot.neutral());
        assertEquals(0x20000, scroll(terrain).background().cloudRam().unsignedLong(0), "first gameplay call is separate");
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
        assertNotNull(h.module().getGameService(PlcLifecycleService.class));
        assertSame(objects, NativeDynamicObjectPass.forModule(h.module()));
        assertNull(h.mode().getLevelManager().getObjectManager());
        assertNull(h.mode().getLevelManager().getRingManager());
        assertEquals(1, h.mode().getSpriteManager().getAllSprites().size());
        assertTrue(h.mode().getSpriteManager().getRegisteredSidekicks().isEmpty());
        assertEquals(new CdRingGlobals(0, 3, 0, 0, 0, 0, 0, 0), objects.ringGlobals());
        // Declared DEV post-LevelStart values; updateHUDTime 0 is the DEV HUD omission (native Level/Main.asm:304 writes 1).
        assertEquals(new CdLevelGlobals(0, 0, 0, 0, 0x03, 0, 2, 0, 0), objects.levelGlobals());
        assertEquals(0, objects.cpuSat().spriteCount());
        assertTrue(objects.displayedFrame().tiles().isEmpty());
        assertEquals(com.openggf.game.soniccd.player.CdPlayerBody.DynamicSlots.UNIMPLEMENTED, frame.environment().dynamicSlots());
        assertEquals(0, frame.state().motion().routine());
        assertEquals(0x070B0000, scroll(h).state().cameraY16_16());
        assertEquals(0x070B0000, scroll(h).background().cameraBgY(), "d0 upper bits are zero, not cameraY16_16");
        assertEquals(0x10000, scroll(h).background().cloudRam().unsignedLong(0));
        step(h, LogicalInputSnapshot.neutral());
        assertEquals(0x20000, scroll(h).background().cloudRam().unsignedLong(0));
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
        assertEquals(new CdLevelGlobals(0, 0, 0, 0, 0x03, 0, 2, 0, 0), objects.levelGlobals(), "no admitted writer yet");
        h.module().requirePlayerActor().requireCompletedNativeFrame();
        assertTrue(objects.displayedFrame().patternVersions().keySet().stream().allMatch(PatternAtlasRange.OBJECTS::contains));
        assertObjectRangeExactlyOnce();
    }

    // Source Level/Main.asm:400-421: all object slots run before the dead-camera branch.
    // Constructed actor/slot ingress tests the real World contract, not a natural death route.
    @ParameterizedTest @CsvSource({"2,1899,0,2", "4,2040,-1024,0"})
    void deathFinishesDynamicSlotsFreezesCameraAndReplaysTheCompletedWorldCut(
            int routine, int y, int yVelocity, int drawBucket) throws Exception {
        var h = opening();
        step(h, LogicalInputSnapshot.neutral());
        var frame = h.module().requirePlayerFrame();
        var runtime = objects(h);
        var captured = frame.captureActor();
        var old = captured.state();
        var m = old.motion();
        var motion = new CdPlayerMotionState(64 << 16, y << 16, 0, yVelocity, 0, routine,
                0, routine == 4 ? 2 : 0, 4, 9, 19, routine == 4 ? 0x1A : 0, m.previousAnimation(),
                0, 0, 0, 0, 0, m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var actor = new CdPlayerBody.State(motion, old.animation(), old.header(),
                new CdPlayerBody.Timers(0, 0, 0, old.timers().reset()), old.history(), old.rotationDistance(), old.objectLinks());
        frame.restoreActor(new CdPlayerFrameRuntime.ActorState(captured.owner(), actor, Optional.empty()));
        h.module().requirePlayerActor().rebindAfterRestore();
        var pool = runtime.placement().pool();
        pool.clearDynamicSlots();
        pool.write(pool.state(40).moveByte(CdObjectState.ByteField.ID, 0x3D)
                .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, 0x84)
                .moveByte(CdObjectState.ByteField.COLLISION_TYPE, 0xAB)
                .moveWord(CdObjectState.WordField.X, 64)
                .moveWord(CdObjectState.WordField.Y, routine == 2 ? 0x759 : y));
        int cameraX = h.mode().getCamera().getX(), cameraY = h.mode().getCamera().getY();
        var rings = runtime.ringGlobals();
        var raw = scroll(h).background();
        replaceBackground(h, flags(raw, 0xAA5A, 0x00C3, 0x007E));
        var background = scroll(h).background(); var beforeScroll = scroll(h);
        var expectedPlane = nextPlane(h, beforeScroll);
        var before = h.mode().getRewindRegistry().capture();

        step(h, LogicalInputSnapshot.neutral());
        assertBackground(background, scroll(h).background());
        assertPlane(expectedPlane.state(), scroll(h).plane());
        assertCopies(consumedCopies(beforeScroll, expectedPlane.flags()), scroll(h).copies());
        assertEquals(0xAA5A, scroll(h).background().scrollFlagsBg(), "death omits BG arithmetic, not V-int drawing");
        assertEquals(6, frame.state().motion().routine());
        assertEquals(0, frame.state().header().priority());
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase());
        assertFalse(frame.closed());
        assertEquals(cameraX, h.mode().getCamera().getX());
        assertEquals(cameraY, h.mode().getCamera().getY());
        assertEquals(cameraY & 0xFFFF, frame.environment().movement().bounds().bottom());
        assertEquals(4, pool.state(40).unsignedByte(CdObjectState.ByteField.ROUTINE),
                "the later Mosqui initializes and runs after Sonic is killed");
        assertEquals(rings.advanceGlobalRingAnimation(), runtime.ringGlobals(), "ordinary tail runs once");
        var playerPieces = runtime.cpuSat().provenance().stream()
                .filter(piece -> piece.slotRef().wholeIndex() == 0).toList();
        assertFalse(playerPieces.isEmpty());
        assertTrue(playerPieces.stream().allMatch(piece -> piece.queueBucket() == drawBucket),
                "Main enqueues at its pre-touch cut; Hurt draws after its kill");
        var completed = state(h);
        h.mode().getRewindRegistry().restore(before);
        step(h, LogicalInputSnapshot.neutral());
        assertState(completed, h);
    }

    @Test void scrollPublicationPairsThePreviousCompletedLoopWithTheSharedCpuSat() throws Exception {
        var h = opening();
        var presentation = (SonicCdPlayerPresentation) h.module().getScrollHandlerProvider();
        assertNull(presentation.captureScrollPublication().prepared());
        assertNull(presentation.captureScrollPublication().published());
        step(h, LogicalInputSnapshot.neutral());
        step(h, LogicalInputSnapshot.neutral());
        boolean movedHorizontally = false;
        boolean planeCutDistinguished = false;
        for (int i = 0; i < 91; i++) {
            var prior = presentation.captureScrollPublication().prepared();
            var sourceCut = scroll(h); var priorBackground = sourceCut.background();
            var expectedPlane = nextPlane(h, sourceCut);
            step(h, input(8, i >= 60 && i < 84 ? 2 : 0, false));
            var next = presentation.captureScrollPublication();
            assertSame(prior, next.published(), "V-int latches the exact prior completed scroll with CPU SAT");
            assertScrollFrame(h, next.published(), priorBackground);
            assertScrollFrame(h, next.prepared(), scroll(h).background());
            assertPlane(expectedPlane.state(), next.plane());
            assertPlane(next.plane(), presentation.publishedPlane());
            var beforeDraw = state(h); drawAndAssertNativeBackground(h); assertNormalEnsureKeepsNativeBackground(h);
            assertState(beforeDraw, h);
            assertCopies(consumedCopies(sourceCut, expectedPlane.flags()), scroll(h).copies());
            planeCutDistinguished |= !Arrays.equals(next.plane().nameTable().copy(), nextPlane(h, scroll(h)).state().nameTable().copy());
            assertEquals(h.mode().getCamera().getX(), next.prepared().cameraX());
            assertEquals(h.mode().getCamera().getY(), next.prepared().cameraY());
            movedHorizontally |= next.published().cameraX() != next.prepared().cameraX();
        }
        assertTrue(movedHorizontally, "The ordinary route must distinguish current and published camera");
        assertTrue(planeCutDistinguished, "The route must distinguish selected plane N from the next CPU cut N+1");
    }

    @Test void coldFullRestoreRetainsUnseededScrollAndReplaysTheFirstCompletedLoop() throws Exception {
        var h = opening();
        var presentation = (SonicCdPlayerPresentation) h.module().getScrollHandlerProvider();
        var cold = h.mode().getRewindRegistry().capture();
        step(h, LogicalInputSnapshot.neutral());
        var after = state(h);
        h.mode().getRewindRegistry().restore(cold);
        assertNull(presentation.captureScrollPublication().prepared());
        assertNull(presentation.captureScrollPublication().published());
        step(h, LogicalInputSnapshot.neutral());
        assertState(after, h);
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
        var presentation = (SonicCdPlayerPresentation) h.module().getScrollHandlerProvider();
        var priorScroll = presentation.captureScrollPublication();
        var priorBackground = scroll(h).background();
        int levelFrame = h.mode().getLevelManager().getFrameCounter();
        step(h, input(0, 0, true));
        assertTrue(h.module().requirePlayerFrame().paused());
        assertBackground(priorBackground, scroll(h).background());
        assertSame(priorScroll.prepared(), presentation.captureScrollPublication().prepared());
        assertSame(priorScroll.prepared(), presentation.captureScrollPublication().published());
        assertEquals(levelFrame, h.mode().getLevelManager().getFrameCounter());
        assertEquals(beforeActor, h.module().requirePlayerFrame().captureActor());
        assertEquals(prior.placement().pool().slots(), objects.placement().capture().pool().slots());
        assertEquals(prior.ringGlobals(), objects.ringGlobals());
        assertEquals(prior.lastSoundRequests(), objects.lastSoundRequests(), "Pause retains the last-loop ledger");
        var picture = objects.displayedFrame();
        var pausedScroll = presentation.captureScrollPublication();
        var nativeFrame = h.module().requirePlayerFrame();
        nativeFrame.prepareInput(0); // Explicit semantic held cut, not a GameLoop lag-row generator.
        h.mode().plcFrameLifecycle().runLogicalIteration(frame -> { }, h.mode().getFadeManager()::update,
                frame -> { LevelFrameStep.serviceVBlankOnly(LevelFrameContext.from(h.mode()), frame, PlcLifecyclePhase.LAG); return null; });
        assertSame(picture, objects.displayedFrame());
        assertBackground(priorBackground, scroll(h).background());
        assertPublication(pausedScroll, presentation.captureScrollPublication());
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
        var bg = scroll(h).background();
        assertEquals(0x50000, bg.cloudRam().unsignedLong(0), "load plus four ordinary calls");
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
        var after = state(h); drawAndAssertNativeBackground(h);
        registry.restore(full);
        assertState(before, h); drawAndAssertNativeBackground(h);
        for (var row : route) step(h, row);
        assertState(after, h);
        registry.restoreCourse(course);
        assertState(before, h); drawAndAssertNativeBackground(h);
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
        var foreignScroll = scroll(a);
        var foreignPublication = ((SonicCdPlayerPresentation) a.module().getScrollHandlerProvider()).captureScrollPublication();
        var b = opening();
        var before = state(b);
        var targetPresentation = (SonicCdPlayerPresentation) b.module().getScrollHandlerProvider();
        assertThrows(IllegalArgumentException.class, () -> targetPresentation.restoreScrollPublication(foreignPublication));
        assertState(before, b);
        assertTrue(retired.closed());
        assertThrows(IllegalArgumentException.class, () -> b.module().getScrollHandlerProvider().getHandler(0)
                .restoreRewindState(foreignScroll)); // Direct rejection covers both FG and BG before writes.
        assertThrows(IllegalArgumentException.class, () -> b.mode().getRewindRegistry().restore(full));
        assertThrows(IllegalArgumentException.class, () -> b.mode().getRewindRegistry().restoreCourse(course));
        assertState(before, b);
        var previous = objects(b);
        var retiredPlc = b.module().getGameService(PlcLifecycleService.class);
        var oldOwned = previous.captureClosedCut();
        b.mode().getLevelManager().loadLevel(0, LevelLoadMode.FULL, SonicCdPlayerInitProfile.openingObjectsContext());
        assertTrue(previous.closed());
        assertThrows(IllegalArgumentException.class, () -> objects(b).validateRestore(oldOwned));
        assertEquals(0, objects(b).cpuSat().spriteCount());
        assertTrue(objects(b).displayedFrame().tiles().isEmpty());
        var fresh = state(b);
        assertThrows(IllegalStateException.class, () -> retiredPlc.serviceVBlank(PlcLifecyclePhase.LAG));
        assertThrows(IllegalStateException.class, () -> retiredPlc.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL));
        assertState(fresh, b); // A stale service cannot advance or close the new visit.
        assertFalse(objects(b).closed());
        step(b, LogicalInputSnapshot.neutral());
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
        assertEquals(new CdLevelGlobals(0, 0, 0, 0, 0x03, 0, 2, 0, 0), objects(h).levelGlobals());
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

    @Test void normalControllerRestartKeepsCarriedStateAndBeginsExactlyOneNewRewindEpoch() throws Exception {
        GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_DETERMINISM_AUDIT, false);
        var h = opening(); var old = objects(h); var oldFrame = h.module().requirePlayerFrame();
        GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        var oldActor = h.module().requirePlayerActor();
        var oldPlc = h.module().getGameService(PlcLifecycleService.class);
        var level = h.mode().getLevelManager(); long generation = level.getCompletedProductionLoadGeneration();
        var boundaries = new java.util.ArrayList<com.openggf.game.rewind.RewindBoundary>();
        com.openggf.level.LevelRewindBoundaryAccess.bind(level, boundary -> {
            boundaries.add(boundary);
            if (h.mode().getLevelManager() == level) h.mode().markRewindBoundary(boundary);
        });
        var log = com.openggf.tools.InputScriptCompiler.compileToInputLog(
                "2 -;108 R;500 -;77 R;230 -;100 R;120 -");
        var movie = new com.openggf.debug.playback.Bk2MovieLoader().parseInputLogText(
                Path.of("restart-controller-recipe"), log.lines().toList());
        for (int call = 1; call <= 1111; call++) {
            var row = movie.getFrame(call - 1);
            // A held Start across restart distinguishes carried rawHeld from a cold zero latch.
            step(h, input(row.p1InputMask(), row.p1ActionMask(), call >= 1110 || row.p1StartPressed()));
        }
        assertEquals(2, old.ringGlobals().lives()); assertEquals(1, oldFrame.state().timers().reset());
        assertEquals(8, oldFrame.state().motion().routine()); assertEquals(0, old.levelGlobals().levelRestart());
        var previous = state(h); var beforeRings = old.ringGlobals(); var beforeSpill = old.spillGlobals();
        step(h, input(0, 0, true)); // Row1112: source Restart zero call; old token must finish on old owners.
        assertEquals(1, old.levelGlobals().levelRestart());
        assertEquals(new CdLevelGlobals(1, 0, 0, 0, 1, 0, 0, 8, 0), old.levelGlobals());
        assertEquals(0, oldFrame.state().timers().reset()); assertFalse(old.closed());
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, old.phase());
        oldActor.requireCompletedNativeFrame();
        var cut = state(h); var registry = h.mode().getRewindRegistry();
        var oldFull = registry.capture(); var oldCourse = registry.captureCourse();
        assertArrayEquals(previous.cpu().bytes(), cut.cpu().bytes());
        assertEquals(previous.cpu().spriteCount(), cut.cpu().spriteCount());
        assertEquals(previous.cpu().provenance(), cut.cpu().provenance());
        assertEquals(previous.camera(), cut.camera()); assertScroll(previous.scroll(), cut.scroll());
        assertEquals(previous.publication().prepared(), cut.publication().prepared());
        assertEquals(beforeRings, old.ringGlobals()); assertEquals(beforeSpill, old.spillGlobals());
        assertTrue(boundaries.isEmpty()); assertEquals(generation, level.getCompletedProductionLoadGeneration());
        assertNotNull(h.mode().getRewindController());
        assertEquals(1112, h.mode().getRewindController().currentFrame());
        long audioAtCut = GameServices.audio().commandTimeline().currentFrame();

        step(h, input(0, 0, true)); // Row1113: rebuild BEFORE new admission, then run the new Init row.
        var next = objects(h); var nextFrame = h.module().requirePlayerFrame();
        assertNotSame(old, next); assertNotSame(oldFrame, nextFrame);
        assertNotSame(oldActor, h.module().requirePlayerActor());
        assertNotSame(oldPlc, h.module().getGameService(PlcLifecycleService.class));
        assertTrue(old.closed()); assertTrue(oldFrame.closed()); assertFalse(next.closed());
        assertEquals(generation + 1, level.getCompletedProductionLoadGeneration());
        assertEquals(List.of(com.openggf.game.rewind.RewindBoundary.LEVEL_LOAD), boundaries);
        assertEquals(1, h.mode().getRewindController().currentFrame());
        assertEquals(1, level.getFrameCounter()); assertEquals(1, nextFrame.environment().globals().levelFrames());
        assertEquals(java.util.OptionalLong.of(0), nextFrame.vIntClock().absoluteSeed());
        assertEquals(1, nextFrame.vIntClock().deltaSinceAdmittedCut());
        assertEquals(audioAtCut + 1, GameServices.audio().commandTimeline().currentFrame());
        assertFalse(nextFrame.paused(), "Held Start must not become a fresh pause edge after reconstruction");
        assertEquals(0x80, nextFrame.environment().controls().rawHeld());
        assertEquals(0, nextFrame.environment().controls().rawTap());
        assertEquals(2, next.ringGlobals().lives()); assertEquals(0, next.ringGlobals().rings());
        assertEquals(new CdLevelGlobals(0, 0, 0, 0, 3, 0, 2, 0, 0), next.levelGlobals());
        assertEquals(cut.objects().scoreGlobals().score(), next.scoreGlobals().score());
        assertEquals(cut.objects().scoreGlobals().nextLifeScore(), next.scoreGlobals().nextLifeScore());
        assertArrayEquals(cut.actor().state().history().bytes().copy(), nextFrame.state().history().bytes().copy());
        assertEquals(0, nextFrame.state().history().index());
        assertArrayEquals(cut.shared().artState().cpuBuffer().copy(), nextFrame.artState().cpuBuffer().copy());
        assertArrayEquals(cut.shared().artState().residentBank().copy(), nextFrame.artState().residentBank().copy());
        assertArrayEquals(new byte[0x480], next.powerupArtState().cpuBuffer().copy());
        assertArrayEquals(cut.objects().powerupArtState().residentBank().copy(), next.powerupArtState().residentBank().copy());
        assertEquals(64, nextFrame.state().motion().centreX16_16() >> 16);
        assertEquals(0x76B, nextFrame.state().motion().centreY16_16() >> 16);
        assertEquals(0x70B, h.mode().getCamera().getY());
        assertEquals(List.of(0x10, 0x10, 0x3D), List.of(next.placement().pool().state(32).id(),
                next.placement().pool().state(33).id(), next.placement().pool().state(34).id()));
        assertEquals(0x26F, next.placement().pool().state(34).centreX());
        assertTrue(next.displayedFrame().tiles().isEmpty()); assertObjectRangeExactlyOnce();

        var first = state(h);
        assertThrows(IllegalStateException.class, () -> oldPlc.serviceVBlank(PlcLifecyclePhase.LAG));
        assertThrows(IllegalStateException.class, () -> oldPlc.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL));
        assertThrows(IllegalArgumentException.class, () -> registry.restore(oldFull));
        assertThrows(IllegalArgumentException.class, () -> registry.restoreCourse(oldCourse));
        assertState(first, h); assertFalse(next.closed());

        // Inspect the new epoch's real frame-zero capture, before SpawnObjects rewrites bank headers/BSETs.
        var rewind = h.mode().getRewindController(); rewind.seekTo(0);
        assertEquals(0, level.getFrameCounter()); assertEquals(0, nextFrame.environment().globals().levelFrames());
        assertArrayEquals(cut.objects().placement().savedFlags().copy(), next.placement().capture().savedFlags().copy());
        assertEquals(0, next.placement().pool().state(32).id());
        rewind.seekTo(1); assertState(first, h);

        var full = registry.capture(); var course = registry.captureCourse();
        var rows = List.of(LogicalInputSnapshot.neutral(), LogicalInputSnapshot.neutral(), input(8, 0, false));
        for (var row : rows) step(h, row);
        var after = state(h);
        registry.restore(full); assertState(first, h);
        for (var row : rows) step(h, row); assertState(after, h);
        registry.restoreCourse(course); assertState(first, h);
        for (var row : rows) step(h, row); assertState(after, h);
        assertEquals(List.of(com.openggf.game.rewind.RewindBoundary.LEVEL_LOAD), boundaries);
        assertObjectRangeExactlyOnce();
    }

    @Test void restartPreflightClosesItsOldVisitWithoutDeletingAnUnknownRosterMember() throws Exception {
        var h = opening(); step(h, LogicalInputSnapshot.neutral()); constructedRestartCut(h);
        var old = objects(h); var frame = h.module().requirePlayerFrame();
        long generation = h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        int levelFrames = h.mode().getLevelManager().getFrameCounter();
        var clock = frame.vIntClock();
        Sprite foreign = mock(Sprite.class); when(foreign.getCode()).thenReturn("foreign");
        h.mode().getSpriteManager().addSprite(foreign);
        assertThrows(UnsupportedOperationException.class, () -> step(h, LogicalInputSnapshot.neutral()));
        assertTrue(old.closed()); assertTrue(frame.closed());
        assertSame(foreign, h.mode().getSpriteManager().getSprite("foreign"));
        assertEquals(generation, h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        assertEquals(levelFrames, h.mode().getLevelManager().getFrameCounter()); assertEquals(clock, frame.vIntClock());
        assertFalse(h.module().restartStaged());
    }

    @Test void admittedRestartLoadFailureClosesBothVisitsAndNeverPublishesALoadBoundary() throws Exception {
        var h = opening(); step(h, LogicalInputSnapshot.neutral()); constructedRestartCut(h);
        var old = objects(h); var oldFrame = h.module().requirePlayerFrame();
        var level = h.mode().getLevelManager(); long generation = level.getCompletedProductionLoadGeneration();
        var boundaries = new java.util.ArrayList<com.openggf.game.rewind.RewindBoundary>();
        com.openggf.level.LevelRewindBoundaryAccess.bind(level, boundary -> {
            boundaries.add(boundary); h.mode().markRewindBoundary(boundary);
        });
        try (var failure = org.mockito.Mockito.mockStatic(SonicCdStaticPresentation.class,
                org.mockito.Mockito.CALLS_REAL_METHODS)) {
            failure.when(() -> SonicCdStaticPresentation.initializeCamera(
                    org.mockito.ArgumentMatchers.any(SonicCdLevel.class),
                    org.mockito.ArgumentMatchers.any(com.openggf.camera.Camera.class)))
                    .thenThrow(new IllegalStateException("constructed post-Admit camera failure"));
            assertThrows(RuntimeException.class, () -> step(h, LogicalInputSnapshot.neutral()));
        }
        assertTrue(old.closed()); assertTrue(oldFrame.closed()); assertFalse(h.module().restartStaged());
        assertNull(h.module().getGameService(PlcLifecycleService.class));
        assertNull(h.module().getGameService(NativeDynamicObjectPass.class));
        assertEquals(generation, level.getCompletedProductionLoadGeneration()); assertTrue(boundaries.isEmpty());
        assertThrows(IllegalStateException.class, h.module()::requirePlayerFrame);
        level.loadLevel(0, LevelLoadMode.FULL, SonicCdPlayerInitProfile.openingObjectsContext());
        step(h, LogicalInputSnapshot.neutral());
        assertEquals(generation + 1, level.getCompletedProductionLoadGeneration());
        assertEquals(List.of(com.openggf.game.rewind.RewindBoundary.LEVEL_LOAD), boundaries);
        assertEquals(3, objects(h).ringGlobals().lives()); assertFalse(objects(h).closed());
    }

    // Catches ticking clouds from renderer resolution, publication, diagnostics or restore.
    @Test void loadScrollRunsBeforeInitAndEveryProjectionKeepsTheSingleCompletedBackgroundState() throws Exception {
        var h = opening(); var f = h.module().requirePlayerFrame(); var m = f.state().motion();
        assertEquals(0, m.routine()); assertEquals(0, m.flags()); assertEquals(0, m.groundVelocity());
        assertEquals(0, m.xVelocity()); assertEquals(0, m.yVelocity());
        assertEquals(0, m.playerControl()); assertEquals(0, m.animation());
        var load = scroll(h); var bg = load.background();
        assertEquals(0x070B0000, load.state().cameraY16_16());
        assertEquals(0x070B0000, bg.cameraBgY()); assertEquals(0x070B070B, bg.vscroll());
        assertEquals(0x10000, bg.cloudRam().unsignedLong(0));
        assertEquals(0xE000, bg.cloudRam().unsignedLong(4));
        assertArrayEquals(new byte[436], bg.cloudRam().slice(76, 436).copy());
        assertArrayEquals(new byte[] {0x10,0x10,0x10,0,0x10,0,0,0}, bg.crossingRam().copy());
        assertEquals(0xAA00, bg.scrollFlagsBg());
        assertEquals(0xFFFF, bg.deformationRam().unsignedWord(0));
        assertEquals(0, bg.cameraBgY() & 0xFFFF, "Init consumes d0=$0000070B");
        var registry = h.mode().getRewindRegistry(); var cold = registry.capture();
        var handler = h.module().getScrollHandlerProvider().getHandler(0);
        handler.update(new int[256], 0x7FFF, -123, 999, 0);
        h.module().gameplayFrameController().drawOverlay();
        LevelSpritePresentation.captureScroll(h.mode().getLevelManager());
        registry.restore(cold);
        assertBackground(bg, scroll(h).background());
        assertEquals(0, f.state().motion().routine());
        step(h, LogicalInputSnapshot.neutral());
        assertEquals(2, f.state().motion().routine());
        assertEquals(0x20000, scroll(h).background().cloudRam().unsignedLong(0));
        var first = scroll(h).background();
        h.module().gameplayFrameController().drawOverlay();
        handler.update(new int[224], -1, -1, -1, 0);
        assertBackground(first, scroll(h).background());
        step(h, LogicalInputSnapshot.neutral());
        assertEquals(0x30000, scroll(h).background().cloudRam().unsignedLong(0));
    }

    // Full RAM projection, including native unwritten tails and signed packed-word offset semantics.
    @Test void projectionUsesEveryStoredPackedWordAndPreservesCameraFractionsAndCallerAliases() throws Exception {
        var h = opening(); var old = scroll(h); var b = old.background();
        byte[] ram = new byte[1024]; var bytes = ByteBuffer.wrap(ram);
        int[] pattern = {0x80007FFF, 0x7FFF8000, 0xFFFF0001, 0x0001FFFF};
        for (int i = 0; i < 256; i++) bytes.putInt(pattern[i & 3]);
        var image = CdAssetBytes.copyOf(ram);
        var bg = new CdR11aBackgroundScroll.State(0xFEDC1234, 0x070B5678,
                0x00109ABC, 0x070BDEF0, 0x00207654, 0x070B3210, b.crossingRam(),
                0xABCD, 0x1234, 0xFEDC, b.cloudRam(), b.deformationRam(), image, 0x070B9ABC);
        replaceBackground(h, bg); Arrays.fill(ram, (byte) 0);
        var handler = h.module().getScrollHandlerProvider().getHandler(0);
        for (int length : new int[] {0, 1, 224, 240, 256}) {
            int[] output = new int[length]; Arrays.fill(output, 0xDEADBEEF);
            handler.update(output, 0x7777, 0x6666, 0x5555, 0);
            for (int i = 0; i < length; i++) assertEquals(pattern[i & 3], output[i]);
            assertEquals(length == 0 ? Integer.MAX_VALUE : length == 1 ? 65535 : -65535,
                    handler.getMinScrollOffset());
            assertEquals(length == 0 ? Integer.MIN_VALUE : 65535, handler.getMaxScrollOffset());
            assertEquals((short) 0x9ABC, handler.getVscrollFactorBG());
            assertEquals((short) 0xFEDC, handler.getBgCameraX());
            assertEquals(512, handler.getBgPeriodWidth(), "physical native Plane B period");
            assertBackground(bg, scroll(h).background());
        }
        int[] oversized = new int[257]; Arrays.fill(oversized, 0xDEADBEEF);
        assertThrows(IllegalArgumentException.class, () -> handler.update(oversized, 0, 0, 0, 0));
        assertTrue(Arrays.stream(oversized).allMatch(word -> word == 0xDEADBEEF));
        assertBackground(bg, scroll(h).background());
        handler.restoreRewindState(new SonicCdPlayerPresentation.ScrollCapture(old.owner(), old.state(), bg, old.plane(), old.copies()));
        assertBackground(bg, scroll(h).background());
    }

    // Bounded dirty-slot Init cut, not a controller ramp route: Init leaves these velocity/control words alone.
    @Test void worldComposesCompletedForegroundWithActualXVelocityRatherThanGroundInertia() throws Exception {
        var h = opening(); var frame = h.module().requirePlayerFrame(); var a = frame.captureActor(); var s = a.state();
        var m = s.motion();
        var motion = new CdPlayerMotionState(0x02A60000, 0x01600000, 0x600, 0, 0x123,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0);
        frame.restoreActor(new CdPlayerFrameRuntime.ActorState(a.owner(), new CdPlayerBody.State(motion,
                s.animation(), s.header(), s.timers(), s.history(), s.rotationDistance(), s.objectLinks()), Optional.empty()));
        var prior = scroll(h); var b = prior.background();
        var fg = new CdR11aForegroundScroll.State(0x200, 0x01001234, 0x10, 0x10,
                0, 0, 0, 0x710, 0, 0, 0);
        var bg = new CdR11aBackgroundScroll.State(0x00101234, 0x01005678,
                0x00205678, 0x01009ABC, 0x00309ABC, 0x0100DEF0, b.crossingRam(),
                0, 0, 0, b.cloudRam(), b.deformationRam(), b.hscrollRam(), 0x01000100);
        h.module().getScrollHandlerProvider().getHandler(0).restoreRewindState(
                new SonicCdPlayerPresentation.ScrollCapture(prior.owner(), fg, bg, prior.plane(), prior.copies()));
        ((SonicCdPlayerPresentation) h.module().getScrollHandlerProvider()).afterRestore();
        step(h, LogicalInputSnapshot.neutral());
        assertEquals(0x600, frame.state().motion().xVelocity()); assertEquals(0x123, frame.state().motion().groundVelocity());
        var next = scroll(h);
        assertEquals(0x206, next.state().cameraX()); assertEquals(0x600, next.state().scrollXDiff());
        assertEquals(0x00191234, next.background().cameraBgX());
        assertEquals(0x002D7678, next.background().cameraBg2X());
        assertEquals(0x00375ABC, next.background().cameraBg3X());
        assertEquals(0x01005678, next.background().cameraBgY());
        assertEquals(0x20000, next.background().cloudRam().unsignedLong(0));
    }

    // DrawObject culls against pre-scroll FG even for BG-relative flags; DrawObjects selects completed BG1/BG3.
    @Test void actualRuntimeSatUsesCompletedBackgroundCamerasAndRetainsNativeForegroundCulling() throws Exception {
        var h = opening(); step(h, LogicalInputSnapshot.neutral());
        var registry = h.mode().getRewindRegistry(); var before = registry.capture();
        step(h, LogicalInputSnapshot.neutral());
        var foreground = objects(h).cpuSat(); int first = pieceFor(foreground, 35);
        int foregroundX = satX(foreground, first);
        for (int mode : new int[] {8, 12}) {
            registry.restore(before);
            var p = objects(h).placement().pool(); var ring = p.state(35);
            p.write(ring.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,
                    (ring.unsignedByte(CdObjectState.ByteField.SPRITE_FLAGS) & ~12) | mode));
            var b = scroll(h).background();
            replaceBackground(h, new CdR11aBackgroundScroll.State(0x01001234, b.cameraBgY(),
                    b.cameraBg2X(), b.cameraBg2Y(), 0x0040ABCD, b.cameraBg3Y(), b.crossingRam(),
                    b.scrollFlagsBg(), b.scrollFlagsBg2(), b.scrollFlagsBg3(), b.cloudRam(),
                    b.deformationRam(), b.hscrollRam(), b.vscroll()));
            step(h, LogicalInputSnapshot.neutral());
            var cpu = objects(h).cpuSat(); int at = pieceFor(cpu, 35);
            int expectedX = (foregroundX - (mode == 8 ? 0x100 : 0x40)) & 0x1FF;
            assertEquals(expectedX == 0 ? 1 : expectedX, satX(cpu, at));
            assertEquals(foreground.provenance().get(first).mappingPieceOrdinal(), cpu.provenance().get(at).mappingPieceOrdinal());
            assertEquals(0x01001234, scroll(h).background().cameraBgX());
            assertEquals(0x0040ABCD, scroll(h).background().cameraBg3X());
        }
    }

    @ParameterizedTest @CsvSource({"257,65536", "1,131072"})
    void worldScrollLockReadsOnlyItsAuthoritativeHighByte(int lock, long expectedCloud) throws Exception {
        var h = opening(); var old = scroll(h); var f = old.state();
        h.module().getScrollHandlerProvider().getHandler(0).restoreRewindState(new SonicCdPlayerPresentation.ScrollCapture(
                old.owner(), new CdR11aForegroundScroll.State(f.cameraX(), f.cameraY16_16(), f.horizontalCrossed(),
                f.verticalCrossed(), f.scrollXDiff(), f.scrollYDiff(), f.scrollFlags(), f.destBottomBound(),
                f.destLeftBound(), f.bottomBoundShift(), lock), old.background(), old.plane(), old.copies()));
        var sourceCut = scroll(h); var expectedPlane = nextPlane(h, sourceCut);
        step(h, LogicalInputSnapshot.neutral());
        assertPlane(expectedPlane.state(), scroll(h).plane());
        assertCopies(consumedCopies(sourceCut, expectedPlane.flags()), scroll(h).copies());
        assertEquals(expectedCloud, scroll(h).background().cloudRam().unsignedLong(0));
        if ((lock >>> 8) != 0) assertBackground(old.background(), scroll(h).background());
        assertFalse(objects(h).closed()); assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, objects(h).phase());
    }

    @Test void restartClearsHscrollAndCloudsThenRunsExactlyOneLoadScroll() throws Exception {
        var h = opening(); step(h, LogicalInputSnapshot.neutral());
        var freshPlane = initialPlane(h).state();
        var oldCapture = scroll(h); var b = oldCapture.background();
        byte[] cloud = new byte[512], deform = new byte[512], horizontal = new byte[1024];
        Arrays.fill(cloud, (byte) 0x77); Arrays.fill(deform, (byte) 0x5A); Arrays.fill(horizontal, (byte) 0x6B);
        replaceBackground(h, new CdR11aBackgroundScroll.State(0x12345678, 0x070B9ABC,
                0x3456789A, 0x070BBCDE, 0x56789ABC, 0x070BDEF0, CdAssetBytes.copyOf(new byte[] {1,2,3,4,5,6,7,8}),
                0x1234, 0x5678, 0x9ABC, CdAssetBytes.copyOf(cloud), CdAssetBytes.copyOf(deform),
                CdAssetBytes.copyOf(horizontal), b.vscroll()));
        var dirtyCut = scroll(h); byte[] dirtyPlane = new byte[4096]; Arrays.fill(dirtyPlane, (byte) 0x6D);
        h.module().getScrollHandlerProvider().getHandler(0).restoreRewindState(new SonicCdPlayerPresentation.ScrollCapture(
                dirtyCut.owner(), dirtyCut.state(), dirtyCut.background(),
                new CdR11aBackgroundPlane.State(CdAssetBytes.copyOf(dirtyPlane)),
                new SonicCdPlayerPresentation.DrawCopies(0x12345678, 0x76543210, copied(dirtyCut.background()),
                        0xBEEF, new CdR11aBackgroundPlane.FlagWords(0xFACE, 0xCAFE, 0xBABE))));
        constructedRestartCut(h); // The zero-call restart branch performs no scroll.
        assertArrayEquals(horizontal, scroll(h).background().hscrollRam().copy());
        h.module().restartOpeningWorld(); // Inspect the load cut before its first gameplay Init.
        var next = scroll(h); var bg = next.background();
        assertPlane(freshPlane, next.plane()); assertCopies(clearedCopies(), next.copies());
        assertPlane(next.plane(), ((SonicCdPlayerPresentation) h.module().getScrollHandlerProvider()).publishedPlane());
        assertEquals(0, h.module().requirePlayerFrame().state().motion().routine());
        assertEquals(0, bg.cameraBgX()); assertEquals(0, bg.cameraBg2X()); assertEquals(0, bg.cameraBg3X());
        assertEquals(0x070B0000, bg.cameraBgY()); assertEquals(0x070B0000, bg.cameraBg2Y());
        assertEquals(0x070B0000, bg.cameraBg3Y()); assertEquals(0xAA00, bg.scrollFlagsBg());
        assertEquals(0, bg.scrollFlagsBg2()); assertEquals(0, bg.scrollFlagsBg3());
        assertArrayEquals(new byte[] {0x10,0x10,0x10,0,0x10,0,0,0}, bg.crossingRam().copy());
        assertEquals(0x10000, bg.cloudRam().unsignedLong(0));
        assertArrayEquals(new byte[436], bg.cloudRam().slice(76, 436).copy());
        assertEquals(0xFFFF, bg.deformationRam().unsignedWord(0));
        // PP Scroll ApplyBGHScroll: at $70B, 117 regular + 112 water = 229 longs; 27 remain untouched.
        assertArrayEquals(new byte[229 * 4], bg.hscrollRam().slice(0, 229 * 4).copy());
        assertArrayEquals(new byte[27 * 4], bg.hscrollRam().slice(229 * 4, 27 * 4).copy(),
                "native ClearScreen clears the old tail before the load-time scroll");
        var handler = h.module().getScrollHandlerProvider().getHandler(0);
        assertThrows(IllegalArgumentException.class, () -> handler.restoreRewindState(oldCapture));
        assertScroll(next, scroll(h)); // Old visit rejection precedes every current tuple write.
        assertBackground(bg, scroll(h).background());
        drawAndAssertNativeBackground(h);
        var registry = h.mode().getRewindRegistry(); var full = registry.capture(); var course = registry.captureCourse();
        step(h, LogicalInputSnapshot.neutral()); var replay = state(h);
        registry.restore(full); assertScroll(next, scroll(h)); drawAndAssertNativeBackground(h);
        step(h, LogicalInputSnapshot.neutral()); assertState(replay, h); drawAndAssertNativeBackground(h);
        registry.restoreCourse(course); assertScroll(next, scroll(h)); drawAndAssertNativeBackground(h);
        step(h, LogicalInputSnapshot.neutral()); assertState(replay, h); drawAndAssertNativeBackground(h);
        assertEquals(0x20000, scroll(h).background().cloudRam().unsignedLong(0));
        drawAndAssertNativeBackground(h); assertNormalEnsureKeepsNativeBackground(h);
    }

    @Test void actualBackgroundBoundaryFailureClosesTheExistingSourceSiblingsWithoutRetry() throws Exception {
        var h = opening(); var frame = h.module().requirePlayerFrame(); var runtime = objects(h);
        var a = frame.captureActor(); var s = a.state(); var m = s.motion();
        var motion = new CdPlayerMotionState(64 << 16, 0x858 << 16, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        frame.restoreActor(new CdPlayerFrameRuntime.ActorState(a.owner(), new CdPlayerBody.State(motion,
                s.animation(), s.header(), s.timers(), s.history(), s.rotationDistance(), s.objectLinks()), Optional.empty()));
        var old = scroll(h);
        h.module().getScrollHandlerProvider().getHandler(0).restoreRewindState(new SonicCdPlayerPresentation.ScrollCapture(
                old.owner(), new CdR11aForegroundScroll.State(0, 0x07F80000, 0x10, 0x10,
                0, 0, 0, 0x710, 0, 0, 0), old.background(), old.plane(), old.copies()));
        ((SonicCdPlayerPresentation) h.module().getScrollHandlerProvider()).afterRestore();
        var failure = assertThrows(CdR11aBackgroundScroll.InvalidScroll.class, () -> step(h, LogicalInputSnapshot.neutral()));
        assertEquals(CdR11aBackgroundScroll.Failure.DEFORMATION_READ, failure.failure());
        assertEquals(512, failure.offset()); assertTrue(frame.closed()); assertTrue(runtime.closed());
        assertThrows(IllegalStateException.class, () -> step(h, LogicalInputSnapshot.neutral()));
    }

    // Explicit raw-storage component ingress: no HUD, title or secondary-player behavior is asserted.
    @Test void reservedImagesArePartOfFullAndCourseWorldRestoreAndForwardReplay() throws Exception {
        var h = opening();
        for (int i = 0; i < 3; i++) step(h, LogicalInputSnapshot.neutral());
        var runtime = objects(h);
        for (int whole = 1; whole < 32; whole++)
            assertArrayEquals(new byte[64], runtime.reservedState(whole).copyBytes(), "declared DEV ClearObjects bank");
        // Keep actual secondary whole1 clear. Populate every byte of otherwise inactive reserved images2..31.
        for (int whole = 2; whole < 32; whole++) {
            byte[] bytes = new byte[64];
            for (int offset = 1; offset < 64; offset++) bytes[offset] = (byte) (whole * 7 + offset * 3);
            runtime.writeReservedImage(runtime.reservedState(whole).withBytes(bytes));
        }
        var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var course = registry.captureCourse(); var before = state(h);
        runtime.writeReservedImage(runtime.reservedState(31).moveByteAt(63,
                runtime.reservedState(31).byteAt(63) ^ 0xFF));
        assertThrows(AssertionError.class, () -> assertState(before, h), "reserved bytes must participate in the complete-state oracle");
        registry.restore(full); assertState(before, h);
        var rows = new java.util.ArrayList<State>();
        for (int i = 0; i < 7; i++) { step(h, LogicalInputSnapshot.neutral()); rows.add(state(h)); }
        for (int whole = 2; whole < 32; whole++)
            runtime.writeReservedImage(runtime.reservedState(whole).moveByteAt(63, 0));
        registry.restore(full); assertState(before, h);
        for (var row : rows) { step(h, LogicalInputSnapshot.neutral()); assertState(row, h); }
        registry.restoreCourse(course); assertState(before, h);
        for (var row : rows) { step(h, LogicalInputSnapshot.neutral()); assertState(row, h); }
    }

    @Test void ordinaryRampApproachPassesOwnedSpringsTagsVerticalDoorAndMonitorAndReplays() throws Exception {
        var h = opening();
        for (int call = 0; call < 323; call++) step(h, input(8, 0, false));
        var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var course = registry.captureCourse(); var before = state(h);
        var expected = new java.util.ArrayList<State>();
        for (int call = 323; call < 400; call++) {
            step(h, input(8, 0, false));
            if (call == 323) {
                var monitor = objects(h).placement().pool().state(59);
                // Actual row74 admission: native Init replaces bit7 before its first Main.
                assertEquals(0x19, monitor.id()); assertEquals(2, monitor.routine());
                assertEquals(4, monitor.unsignedByte(CdObjectState.ByteField.SUBTYPE));
                assertEquals(45, monitor.unsignedByte(CdObjectState.ByteField.SAVED_FLAGS_ID));
                assertEquals(0x46, monitor.unsignedByte(CdObjectState.ByteField.COLLISION_TYPE));
                assertEquals(0x20AA4E, monitor.longBits(CdObjectState.LongField.MAPPINGS));
                assertEquals(0x850, monitor.centreX()); assertEquals(0x2D8, monitor.centreY());
            }
            expected.add(state(h));
        }
        registry.restore(full); assertState(before, h);
        for (var row : expected) { step(h, input(8, 0, false)); assertState(row, h); }
        registry.restoreCourse(course); assertState(before, h);
        for (var row : expected) { step(h, input(8, 0, false)); assertState(row, h); }
    }

    @Test void ordinaryRightRouteRunsTheOwnedHorizontalDoorAt518AndReplaysItsCompletedPass() throws Exception {
        var h = opening(); var runtime = objects(h);
        for (int call = 0; call < 517; call++) step(h, input(8, 0, false));
        var registry = h.mode().getRewindRegistry(); var full = registry.capture(); var course = registry.captureCourse();
        var before = state(h);
        step(h, input(8, 0, false));
        var door = runtime.placement().pool().state(37);
        assertEquals(0x0D, door.id()); assertEquals(2, door.routine());
        assertEquals(1, door.unsignedByte(CdObjectState.ByteField.SUBTYPE));
        assertEquals(0x96F, door.centreX()); assertEquals(0x318, door.centreY());
        assertEquals(0x23F038, door.longBits(CdObjectState.LongField.MAPPINGS));
        assertArrayEquals(new byte[64], runtime.reservedState(1).copyBytes());
        assertFalse(runtime.closed()); assertFalse(h.module().requirePlayerFrame().closed());
        var after = state(h);
        registry.restore(full); assertState(before, h); step(h, input(8, 0, false)); assertState(after, h);
        registry.restoreCourse(course); assertState(before, h); step(h, input(8, 0, false)); assertState(after, h);
    }

    @Test void ordinaryRightRouteRunsPataInitAndFinalPlc9ServiceAcross540And541WithFullCourseReplay() throws Exception {
        var h=opening();var runtime=objects(h);var pool=runtime.placement().pool();
        for(int call=0;call<539;call++) step(h,input(8,0,false));
        var registry=h.mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
        var before=state(h);
        step(h,input(8,0,false));
        var pata=pool.state(52);assertEquals(0x3E,pata.id());assertEquals(2,pata.routine());
        assertEquals(2471,pata.centreX());assertEquals(636,pata.centreY());
        assertEquals(2472,pata.word(CdObjectState.WordField.PATA_BATA_ANCHOR_X));
        // Pinned Objects (Act1).bin record88:09A8 027C BE FF 40 00; subtypeFF chooses damaged mappings.
        assertEquals(0xFF,pata.subtype());
        assertEquals(0x20B550,pata.longBits(CdObjectState.LongField.MAPPINGS));
        assertEquals(0x0200,runtime.sectionArtState().sectionWord());
        assertEquals(2,runtime.sectionArtState().remainingTilesWord());
        assertEquals(0x237D9DL,runtime.sectionArtState().queue().headSource());
        assertFalse(runtime.closed());var at540=state(h);
        step(h,input(8,0,false));assertEquals(635,pool.state(52).centreY());
        assertEquals(0,runtime.sectionArtState().remainingTilesWord());assertEquals(0,runtime.sectionArtState().queue().headSource());
        var tam=h.module().requirePlayerFrame().assets().openingObjects().sectionArtSources()
                .source(com.openggf.game.soniccd.level.CdR11aAsset.ART_TAMABBOH).decode().bytes();
        for(int tile=0;tile<59;tile++) assertArrayEquals(tam.slice(tile*32,32).copy(),
                com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(runtime.sectionArtState().residency(),0x428+tile).copy());
        var at541=state(h);
        registry.restore(full);assertState(before,h);step(h,input(8,0,false));assertState(at540,h);step(h,input(8,0,false));assertState(at541,h);
        registry.restoreCourse(course);assertState(before,h);step(h,input(8,0,false));assertState(at540,h);step(h,input(8,0,false));assertState(at541,h);
    }
    @Test void ordinaryRightRouteCompletesBoulderInitThenStopsAtTheLaterDiagonalSpringInCall561() throws Exception {
        var h=opening();var runtime=objects(h);var frame=h.module().requirePlayerFrame();var pool=runtime.placement().pool();
        for(int call=0;call<560;call++)step(h,input(8,0,false));
        assertEquals(0,pool.state(46).id());
        assertEquals(0x3E,pool.state(52).id());assertEquals(2,pool.state(52).routine());
        assertEquals(2466,pool.state(52).centreX());assertEquals(628,pool.state(52).centreY());
        assertEquals(0,runtime.sectionArtState().remainingTilesWord());assertEquals(0,runtime.sectionArtState().queue().headSource());
        var failure=assertThrows(com.openggf.game.soniccd.objects.CdSpring.UnsupportedSpring.class,()->step(h,input(8,0,false)));
        assertEquals(com.openggf.game.soniccd.objects.CdSpring.UnsupportedBoundary.DIAGONAL_TILE_SELECTOR,failure.boundary());
        assertEquals(47,failure.wholeIndex());assertEquals(0x0F,failure.value());
        assertEquals("DIAGONAL_TILE_SELECTOR at Spring whole slot 47, reached value $f",failure.getMessage());
        // Native record94 0A0D04A01B004000 has completed its real Init before the later physical stop.
        var rock=pool.state(46);assertEquals(0x1B,rock.id());assertEquals(2,rock.routine());
        assertEquals(2573,rock.centreX());assertEquals(1184,rock.centreY());assertEquals(4,rock.spriteFlags());
        assertEquals(0x209654,rock.longBits(CdObjectState.LongField.MAPPINGS));assertEquals(0x0374,rock.word(CdObjectState.WordField.TILE));
        assertEquals(4,rock.unsignedByte(CdObjectState.ByteField.PRIORITY));assertEquals(16,rock.unsignedByte(CdObjectState.ByteField.WIDTH));
        assertEquals(16,rock.unsignedByte(CdObjectState.ByteField.Y_RADIUS));assertEquals(0,rock.unsignedByte(CdObjectState.ByteField.MAP_FRAME));
        assertEquals(0,rock.unsignedByte(CdObjectState.ByteField.COLLISION_TYPE));assertEquals(0,rock.flags());
        var spring=pool.state(47);assertEquals(0x0A,spring.id());assertEquals(0x14,spring.routine());
        assertEquals(2576,spring.centreX());assertEquals(1038,spring.centreY());assertEquals(4,spring.spriteFlags());
        assertEquals(145239552,frame.state().motion().centreX16_16());assertEquals(-337408,frame.state().motion().centreY16_16());
        assertEquals(628,pool.state(52).centreY(),"later Pata slot52 remains unvisited after Spring47; call561 is NOT complete");
        assertEquals(0x0200,runtime.sectionArtState().sectionWord());
        assertTrue(runtime.closed());assertTrue(frame.closed());
    }

    // ClearScreen/InitLevelDrawBG: this is post-initial-draw, before any represented V-int.
    @Test void coldPlaneUsesTheNativeClearSeedForAll560BytesNotCoveredBy442InitialBlocks() throws Exception {
        var h = opening(); var cut = scroll(h); var initialized = initialPlane(h);
        assertEquals(0x70B, cut.background().cameraBgYWord());
        assertEquals(442, initialized.blocks().size()); assertEquals(16, initialized.sections().size());
        assertEquals(112, initialized.sections().getFirst().tableIndex());
        assertEquals(127, initialized.sections().getLast().tableIndex());
        assertEquals(7, initialized.sections().stream().filter(section -> section.selector() == 4).count());
        assertEquals(new CdR11aBackgroundPlane.FlagWords(0xAA00, 0, 0), initialized.flags());
        assertEquals(0x0400, cut.state().scrollFlags(), "Main:247 forces only live FG high-byte bit2");
        assertCopies(clearedCopies(), cut.copies()); assertPlane(initialized.state(), cut.plane());
        var presentation = (SonicCdPlayerPresentation) h.module().getScrollHandlerProvider();
        assertPlane(cut.plane(), presentation.publishedPlane());
        assertNull(presentation.captureScrollPublication().prepared()); assertNull(presentation.captureScrollPublication().published());
        assertEquals(0, h.module().requirePlayerFrame().captureShared().vIntClock().deltaSinceAdmittedCut());
        byte[] marker = new byte[4096]; Arrays.fill(marker, (byte) 0x6D);
        var marked = CdR11aBackgroundPlane.bind(h.module().requirePlayerFrame().assets()).initialize(
                new CdR11aBackgroundPlane.State(CdAssetBytes.copyOf(marker)), live(cut.background()), flagWords(cut.background()));
        int untouched = 0; byte[] zeroDraw = initialized.state().nameTable().copy(), markerDraw = marked.state().nameTable().copy();
        for (int i = 0; i < 4096; i++) if (zeroDraw[i] != markerDraw[i]) {
            untouched++; assertEquals(0, cut.plane().nameTable().unsignedByte(i)); assertEquals(0x6D, markerDraw[i] & 255);
        }
        assertEquals(560, untouched, "initial drawing cannot stand in for the native clear");
        var before = state(h); h.module().gameplayFrameController().drawOverlay(); assertState(before, h);
    }

    @Test void firstSelectorEightServices39BlocksAndConsumesOnlyTheFreshCopiedBgHighByte() throws Exception {
        var h = opening(); var before = scroll(h); var expected = nextPlane(h, before);
        assertEquals(39, expected.blocks().size()); assertEquals(17, expected.sections().size());
        assertEquals(127, expected.sections().getFirst().tableIndex());
        assertEquals(7, expected.sections().stream().filter(section -> section.phase() == CdR11aBackgroundPlane.Phase.INCREMENTAL_COLUMN && section.drawn()).count());
        step(h, input(0, 0, true)); // Genuine first selector8 NORMAL_PAUSE, no CPU LevelScroll afterward.
        assertTrue(h.module().requirePlayerFrame().paused());
        var after = scroll(h); assertBackground(before.background(), after.background());
        assertPlane(expected.state(), after.plane()); assertCopies(consumedCopies(before, expected.flags()), after.copies());
        assertEquals(0, after.copies().bgFlags().bg1()); assertEquals(0xAA00, after.background().scrollFlagsBg());
        assertEquals(0x0400, after.copies().fgFlags(), "native FG streaming is outside this Plane B unit");
        assertEquals(0x10000, after.background().cloudRam().unsignedLong(0));
        assertEquals(1, h.module().requirePlayerFrame().captureShared().vIntClock().deltaSinceAdmittedCut());
        assertTrue(objects(h).displayedFrame().tiles().isEmpty());
    }

    @Test void pauseRecopiesEveryFractionAndRawFlagWordWhileLagLeavesPlaneAndCopiesUntouched() throws Exception {
        var h = opening(); var old = scroll(h); var b = old.background(); var f = old.state();
        var bg = new CdR11aBackgroundScroll.State(0x00121234, 0x070B5678, 0x00239ABC, 0x070BDEF0,
                0x00347654, 0x070B3210, b.crossingRam(), 0xAA5A, 0x00C3, 0x007E,
                b.cloudRam(), b.deformationRam(), b.hscrollRam(), b.vscroll());
        var fg = new CdR11aForegroundScroll.State(f.cameraX(), 0x070B7654, f.horizontalCrossed(), f.verticalCrossed(),
                f.scrollXDiff(), f.scrollYDiff(), 0x915A, f.destBottomBound(), f.destLeftBound(), f.bottomBoundShift(), f.scrollLock());
        var handler = h.module().getScrollHandlerProvider().getHandler(0);
        handler.restoreRewindState(new SonicCdPlayerPresentation.ScrollCapture(old.owner(), fg, bg, old.plane(), old.copies()));
        ((SonicCdPlayerPresentation) h.module().getScrollHandlerProvider()).afterRestore();
        var sourceCut = scroll(h); var expected = nextPlane(h, sourceCut); assertEquals(39, expected.blocks().size());
        step(h, input(0, 0, true)); var first = scroll(h);
        assertBackground(bg, first.background()); assertEquals(fg, first.state());
        assertPlane(expected.state(), first.plane()); assertCopies(consumedCopies(sourceCut, expected.flags()), first.copies());
        assertEquals(new CdR11aBackgroundPlane.FlagWords(0x005A, 0x00C3, 0x007E), first.copies().bgFlags());
        assertEquals(0x00121234, first.copies().bg().bg1().xLong()); assertEquals(0x070B5678, first.copies().bg().bg1().yLong());
        assertEquals(0x00239ABC, first.copies().bg().bg2().xLong()); assertEquals(0x070BDEF0, first.copies().bg().bg2().yLong());
        assertEquals(0x00347654, first.copies().bg().bg3().xLong()); assertEquals(0x070B3210, first.copies().bg().bg3().yLong());
        assertEquals(0x070B7654, first.copies().fgY16_16()); assertEquals(0x915A, first.copies().fgFlags());
        byte[] poison = new byte[4096]; Arrays.fill(poison, (byte) 0x6D);
        handler.restoreRewindState(new SonicCdPlayerPresentation.ScrollCapture(first.owner(), first.state(), first.background(),
                new CdR11aBackgroundPlane.State(CdAssetBytes.copyOf(poison)), first.copies()));
        var secondSource = scroll(h); var repeated = nextPlane(h, secondSource);
        assertEquals(39, repeated.blocks().size(), "expected from live AA5A, not previously consumed 005A");
        step(h, LogicalInputSnapshot.neutral()); var second = scroll(h);
        assertBackground(bg, second.background()); assertPlane(repeated.state(), second.plane());
        assertCopies(consumedCopies(secondSource, repeated.flags()), second.copies());
        assertFalse(Arrays.equals(poison, second.plane().nameTable().copy()), "pause must really repeat the plane writes");
        var presentation = (SonicCdPlayerPresentation) h.module().getScrollHandlerProvider();
        var publication = presentation.captureScrollPublication(); var picture = objects(h).displayedFrame();
        var frame = h.module().requirePlayerFrame(); frame.prepareInput(0);
        h.mode().plcFrameLifecycle().runLogicalIteration(token -> { }, h.mode().getFadeManager()::update,
                token -> { LevelFrameStep.serviceVBlankOnly(LevelFrameContext.from(h.mode()), token, PlcLifecyclePhase.LAG); return null; });
        assertScroll(second, scroll(h)); assertPublication(publication, presentation.captureScrollPublication());
        assertSame(picture, objects(h).displayedFrame()); assertPlane(publication.plane(), presentation.publishedPlane());
    }


    // Literal raw-word goldens, independent of layout/chunk transforms and residency admission.
    @Test void nativeBackgroundDescriptorsKeepZeroAndAllRawAttributeBits() {
        byte[] words = new byte[4096];
        ByteBuffer.wrap(words).putShort((short) 0xE804).putShort((short) 0xFFFF);
        var plane = new CdR11aBackgroundPlane.State(CdAssetBytes.copyOf(words));
        var encoded = SonicCdPlayerPresentation.nativeBackgroundDescriptors(plane);
        assertEquals(8192, encoded.length);
        assertArrayEquals(new byte[] {4, (byte) 0xB8, 0, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, 0, (byte) 0xFF, 0, 0, 0, (byte) 0xFF},
                Arrays.copyOf(encoded, 12));
        assertArrayEquals(new byte[] {0, 0, 0, (byte) 0xFF}, Arrays.copyOfRange(encoded, 8188, 8192));
        assertArrayEquals(words, plane.nameTable().copy());
        assertThrows(NullPointerException.class, () -> SonicCdPlayerPresentation.nativeBackgroundDescriptors(null));
    }

    @Test void nativeBackgroundDescriptorsRoundTripEveryWordWithoutAliasingTheImmutablePlane() {
        for (int first = 0; first < 65536; first += 2048) {
            byte[] raw = new byte[4096]; var words = ByteBuffer.wrap(raw);
            for (int cell = 0; cell < 2048; cell++) words.putShort((short) (first + cell));
            var plane = new CdR11aBackgroundPlane.State(CdAssetBytes.copyOf(raw));
            var before = plane.nameTable().copy();
            Arrays.fill(raw, (byte) 0x55);
            var encoded = SonicCdPlayerPresentation.nativeBackgroundDescriptors(plane);
            assertEquals(8192, encoded.length);
            for (int cell = 0; cell < 2048; cell++) {
                int at = cell * 4, g = encoded[at + 1] & 255;
                int decoded = (encoded[at] & 255) | ((g & 7) << 8) | ((g & 0x18) << 10)
                        | ((g & 0x60) << 6) | ((g & 0x80) << 8);
                assertEquals(first + cell, decoded, "raw physical cell " + cell);
                assertEquals(0, encoded[at + 2]); assertEquals(255, encoded[at + 3] & 255);
            }
            var again = SonicCdPlayerPresentation.nativeBackgroundDescriptors(plane);
            assertNotSame(encoded, again); assertArrayEquals(encoded, again);
            Arrays.fill(encoded, (byte) 0);
            assertArrayEquals(before, plane.nameTable().copy());
            assertArrayEquals(oracleNativeDescriptors(plane), again);
        }
    }

    @Test void coldNativeBackgroundDrawReconcilesTheActualCacheWithoutPublishingOrSteppingSource() throws Exception {
        var h = opening(); var presentation = (SonicCdPlayerPresentation) h.module().getScrollHandlerProvider();
        var before = state(h); var tm = h.mode().getLevelManager().getTilemapManager();
        assertNull(before.publication().prepared()); assertNull(before.publication().published());
        drawAndAssertNativeBackground(h); assertState(before, h);
        tm.setBgTilemapBaseX(512); tm.setCurrentBgPeriodWidth(1024); tm.setBgLoopBandBaseY(256);
        tm.setBackgroundTilemapDirty(true);
        h.module().getScrollHandlerProvider().getHandler(0).update(new int[256], 999, -999, 777, 0);
        drawAndAssertNativeBackground(h); drawAndAssertNativeBackground(h);
        assertState(before, h);
        assertNull(presentation.captureScrollPublication().prepared());
        assertNull(presentation.captureScrollPublication().published());
        assertEquals(0, h.module().requirePlayerFrame().vIntClock().deltaSinceAdmittedCut());
        assertNormalEnsureKeepsNativeBackground(h);
    }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void terrainNativeBackgroundUsesActualCpuPatternsWithoutRequiringGpuResidency(CdReleaseVariant variant) throws Exception {
        var h = load(SonicCdGameModule.terrainOnlyDevelopmentPlayer(), source(variant), true);
        var frame = h.module().requirePlayerFrame(); var body = frame.captureActor(); var shared = frame.captureShared();
        var before = scroll(h); var camera = h.mode().getCamera().capture();
        var atlas = GameServices.graphics().getPatternAtlas();
        assertTrue(GameServices.graphics().isHeadlessMode()); assertFalse(GameServices.graphics().isGlInitialized());
        assertFalse(atlas.isInitialized(), "CPU projection must not initialize an atlas");
        int id = before.plane().nameTable().unsignedWord(0) & 0x7FF;
        GameServices.graphics().uncachePattern(id); // Public residency removal is harmless for a CPU-only projection.
        assertNull(atlas.getEntry(id));
        drawAndAssertNativeBackground(h); drawAndAssertNativeBackground(h); assertNormalEnsureKeepsNativeBackground(h);
        assertNull(atlas.getEntry(id), "Do not manufacture GPU residency while projecting headless descriptors");
        assertSame(atlas, GameServices.graphics().getPatternAtlas()); assertFalse(atlas.isInitialized());
        assertEquals(body, frame.captureActor()); assertEquals(shared.environment(), frame.captureShared().environment());
        assertEquals(shared.vIntClock(), frame.captureShared().vIntClock()); assertEquals(camera, h.mode().getCamera().capture());
        assertScroll(before, scroll(h)); assertEquals(0, frame.state().motion().routine());
    }

    @ParameterizedTest @CsvSource({"true,scroll", "true,cinematic", "true,capture",
            "false,scroll", "false,cinematic", "false,capture"})
    void nativeBackgroundNestedAdmissionRejectsBeforeCacheMutationAndClosesItsOwnVisit(
            boolean openingEntry, String enclosing) throws Exception {
        var h = openingEntry ? opening() : load(SonicCdGameModule.terrainOnlyDevelopmentPlayer(), source(CdReleaseVariant.USA), true);
        drawAndAssertNativeBackground(h);
        var level = h.mode().getLevelManager(); var graphics = GameServices.graphics();
        var frame = h.module().requirePlayerFrame(); var body = frame.state(); var clock = frame.vIntClock();
        var runtime = openingEntry ? objects(h) : null;
        var bytes = NativeBackgroundTestAccess.descriptors(level);
        var geometry = NativeBackgroundTestAccess.geometry(level); var flags = NativeBackgroundTestAccess.flags(level);
        Runnable reject = () -> assertThrows(IllegalStateException.class, h.module().gameplayFrameController()::drawOverlay);
        switch (enclosing) {
            case "scroll" -> LevelSpritePresentation.withScroll(level, LevelSpritePresentation.captureScroll(level), reject);
            case "cinematic" -> LevelSpritePresentation.withCinematicCamera(level, h.mode().getCamera().getX(),
                    h.mode().getCamera().getY(), reject);
            case "capture" -> graphics.executeCapturedCommands(reject, h.mode().getCamera().getX(),
                    h.mode().getCamera().getY(), 320, 224);
            default -> fail("unknown fixture scope");
        }
        assertTrue(frame.closed()); if (runtime != null) assertTrue(runtime.closed());
        assertEquals(body, frame.state()); assertEquals(clock, frame.vIntClock());
        assertArrayEquals(bytes, NativeBackgroundTestAccess.descriptors(level));
        assertArrayEquals(geometry, NativeBackgroundTestAccess.geometry(level));
        assertArrayEquals(flags, NativeBackgroundTestAccess.flags(level));
        assertThrows(IllegalStateException.class, h.module().gameplayFrameController()::drawOverlay);
        assertArrayEquals(bytes, NativeBackgroundTestAccess.descriptors(level));
    }

    @ParameterizedTest @CsvSource({"true", "false"})
    void nativeBackgroundRejectsAChangedRuntimePatternInsteadOfAcceptingArrayCapacity(boolean openingEntry) throws Exception {
        var h = openingEntry ? opening() : load(SonicCdGameModule.terrainOnlyDevelopmentPlayer(), source(CdReleaseVariant.USA), true);
        drawAndAssertNativeBackground(h);
        var frame = h.module().requirePlayerFrame(); var runtime = openingEntry ? objects(h) : null;
        var level = h.mode().getLevelManager(); var before = NativeBackgroundTestAccess.descriptors(level);
        var selected = ((SonicCdPlayerPresentation) h.module().getScrollHandlerProvider()).publishedPlane();
        int id = selected.nameTable().unsignedWord(0) & 0x7FF;
        var pattern = level.getCurrentLevel().getPattern(id); byte original = pattern.getPixel(0, 0);
        pattern.setPixel(0, 0, (byte) ((original + 1) & 15)); // Legitimate public mutable-Level operation.
        var body = frame.state(); var clock = frame.vIntClock();
        var failure = assertThrows(IllegalStateException.class, h.module().gameplayFrameController()::drawOverlay);
        assertTrue(failure.getMessage().contains("resident base-level pattern"), failure.getMessage());
        assertTrue(frame.closed()); if (runtime != null) assertTrue(runtime.closed());
        assertEquals(body, frame.state()); assertEquals(clock, frame.vIntClock());
        assertArrayEquals(before, NativeBackgroundTestAccess.descriptors(level));
        assertThrows(IllegalStateException.class, h.module().gameplayFrameController()::drawOverlay);
    }

    private static byte[] oracleNativeDescriptors(CdR11aBackgroundPlane.State plane) {
        byte[] out = new byte[8192];
        for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) {
            int cell = y * 64 + x, word = plane.nameTable().unsignedWord(cell * 2);
            int pattern = word % 2048, palette = (word / 8192) % 4;
            out[cell * 4] = (byte) (pattern % 256);
            out[cell * 4 + 1] = (byte) (pattern / 256 + palette * 8
                    + ((word / 2048) % 2) * 32 + ((word / 4096) % 2) * 64 + (word / 32768) * 128);
            out[cell * 4 + 3] = (byte) 255;
        }
        return out;
    }

    private static void assertNativeBackground(Harness h) {
        var presentation = (SonicCdPlayerPresentation) h.module().getScrollHandlerProvider();
        var level = h.mode().getLevelManager(); var tm = level.getTilemapManager();
        assertArrayEquals(oracleNativeDescriptors(presentation.publishedPlane()), NativeBackgroundTestAccess.descriptors(level));
        assertArrayEquals(new int[] {64, 32, 0, 0, 512, 32, 0, -1}, NativeBackgroundTestAccess.geometry(level));
        assertArrayEquals(new boolean[] {false, true, false}, NativeBackgroundTestAccess.flags(level));
        assertEquals(512, h.module().getScrollHandlerProvider().getHandler(0).getBgPeriodWidth());
        assertEquals(512, GameServices.parallax().getBgPeriodWidth());
        assertEquals(8192, tm.getBackgroundTilemapData().length);
    }

    private static void drawAndAssertNativeBackground(Harness h) {
        h.module().gameplayFrameController().drawOverlay(); assertNativeBackground(h);
    }

    private static void assertNormalEnsureKeepsNativeBackground(Harness h) {
        var tm = h.mode().getLevelManager().getTilemapManager();
        tm.ensureBackgroundTilemapData((layer, x, y) -> {
            throw new AssertionError("A clean retained native plane must not query layout geometry");
        }, null, 0, GameServices.parallax(), h.mode().getLevelManager().isVerticalWrapEnabled());
        assertNativeBackground(h);
    }

    /** Explicit component ingress for failure paths; the separate natural test supplies only controllers. */
    private static void constructedRestartCut(Harness h) {
        var frame = h.module().requirePlayerFrame(); var captured = frame.captureActor();
        var old = captured.state(); var m = old.motion();
        var motion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(),
                m.groundVelocity(), 8, m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(),
                m.animation(), m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(), m.stick(),
                m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var timers = old.timers();
        var state = new CdPlayerBody.State(motion, old.animation(), old.header(), new CdPlayerBody.Timers(
                timers.hurt(), timers.invincibility(), timers.speedShoes(), 1), old.history(), old.rotationDistance(), old.objectLinks());
        frame.restoreActor(new CdPlayerFrameRuntime.ActorState(captured.owner(), state, Optional.empty()));
        h.module().requirePlayerActor().rebindAfterRestore();
        step(h, LogicalInputSnapshot.neutral());
        assertEquals(1, objects(h).levelGlobals().levelRestart());
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, objects(h).phase());
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
                         PlayerResidentBanks.Snapshot resident, SonicCdPlayerPresentation.ScrollPublication publication) { }
    private static State state(Harness h) {
        var frame = h.module().requirePlayerFrame(); var objects = objects(h);
        return new State(frame.captureActor(), frame.captureShared(), objects.captureClosedCut(), objects.cpuSat(), objects.displayedFrame(),
                h.mode().getCamera().capture(), h.module().getScrollHandlerProvider().getHandler(0).captureRewindState(),
                h.mode().getLevelManager().getFrameCounter(), PlayerResidentBanks.capture(h.module().requirePlayerActor().getSpriteRenderer()),
                ((SonicCdPlayerPresentation) h.module().getScrollHandlerProvider()).captureScrollPublication());
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
        assertReservedImages(expected.objects().reserved(), actual.objects().reserved());
        assertEquals(expected.objects().placement().status(), actual.objects().placement().status());
        assertArrayEquals(expected.objects().placement().savedFlags().copy(), actual.objects().placement().savedFlags().copy());
        assertEquals(expected.objects().ringGlobals(), actual.objects().ringGlobals());
        assertEquals(expected.objects().spillGlobals(), actual.objects().spillGlobals());
        assertEquals(expected.objects().devGlobals(), actual.objects().devGlobals());
        assertEquals(expected.objects().contactGlobals(), actual.objects().contactGlobals());
        assertEquals(expected.objects().scoreGlobals(), actual.objects().scoreGlobals());
        assertEquals(expected.objects().levelGlobals(), actual.objects().levelGlobals());
        assertEquals(expected.objects().powerupArtState(), actual.objects().powerupArtState());
        assertEquals(expected.objects().sectionArtState(), actual.objects().sectionArtState());
        assertEquals(expected.objects().lastSoundRequests(), actual.objects().lastSoundRequests());
        assertArrayEquals(expected.cpu().bytes(), actual.cpu().bytes());
        assertEquals(expected.cpu().spriteCount(), actual.cpu().spriteCount());
        assertEquals(expected.cpu().provenance(), actual.cpu().provenance());
        assertEquals(expected.picture(), actual.picture());
        assertEquals(expected.camera(), actual.camera()); assertScroll(expected.scroll(), actual.scroll());
        assertEquals(expected.levelFrame(), actual.levelFrame());
        assertPublication(expected.publication(), actual.publication());
        var a = expected.resident().tiles(); var b = actual.resident().tiles();
        assertEquals(24, b.length);
        for (int i = 0; i < 24; i++) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) assertEquals(a[i].getPixel(x, y), b[i].getPixel(x, y));
    }
    private static SonicCdPlayerPresentation.ScrollCapture scroll(Harness h) {
        return assertInstanceOf(SonicCdPlayerPresentation.ScrollCapture.class,
                h.module().getScrollHandlerProvider().getHandler(0).captureRewindState());
    }
    private static void replaceBackground(Harness h, CdR11aBackgroundScroll.State background) {
        var old = scroll(h);
        h.module().getScrollHandlerProvider().getHandler(0).restoreRewindState(
                new SonicCdPlayerPresentation.ScrollCapture(old.owner(), old.state(), background, old.plane(), old.copies()));
    }
    private static void assertScroll(Object expected, Object actual) {
        var a = assertInstanceOf(SonicCdPlayerPresentation.ScrollCapture.class, expected);
        var b = assertInstanceOf(SonicCdPlayerPresentation.ScrollCapture.class, actual);
        assertSame(a.owner(), b.owner()); assertEquals(a.state(), b.state()); assertBackground(a.background(), b.background());
        assertPlane(a.plane(), b.plane()); assertCopies(a.copies(), b.copies());
    }
    private static void assertReservedImages(
            com.openggf.game.soniccd.objects.CdReservedObjectBank.Snapshot expected,
            com.openggf.game.soniccd.objects.CdReservedObjectBank.Snapshot actual) {
        assertEquals(31, expected.slots().size()); assertEquals(31, actual.slots().size());
        for (int i = 0; i < 31; i++) {
            assertEquals(i + 1, expected.slots().get(i).identity().wholeIndex());
            assertEquals(expected.slots().get(i).identity(), actual.slots().get(i).identity());
            assertArrayEquals(expected.slots().get(i).copyBytes(), actual.slots().get(i).copyBytes(),
                    "complete reserved whole " + (i + 1));
        }
    }

    private static void assertPlane(com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.State expected,
                                    com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane.State actual) {
        assertEquals(4096, expected.nameTable().length()); assertEquals(4096, actual.nameTable().length());
        assertArrayEquals(expected.nameTable().copy(), actual.nameTable().copy());
    }
    private static void assertCopies(SonicCdPlayerPresentation.DrawCopies expected,
                                     SonicCdPlayerPresentation.DrawCopies actual) {
        assertEquals(expected.fgX16_16(), actual.fgX16_16()); assertEquals(expected.fgY16_16(), actual.fgY16_16());
        assertEquals(expected.bg().bg1(), actual.bg().bg1()); assertEquals(expected.bg().bg2(), actual.bg().bg2());
        assertEquals(expected.bg().bg3(), actual.bg().bg3()); assertEquals(expected.fgFlags(), actual.fgFlags());
        assertEquals(expected.bgFlags().bg1(), actual.bgFlags().bg1());
        assertEquals(expected.bgFlags().bg2(), actual.bgFlags().bg2()); assertEquals(expected.bgFlags().bg3(), actual.bgFlags().bg3());
    }
    private static void assertPublication(SonicCdPlayerPresentation.ScrollPublication expected,
                                          SonicCdPlayerPresentation.ScrollPublication actual) {
        assertSame(expected.owner(), actual.owner());
        assertEquals(expected.prepared(), actual.prepared()); assertEquals(expected.published(), actual.published());
        assertPlane(expected.plane(), actual.plane());
    }
    private static void assertBackground(CdR11aBackgroundScroll.State a, CdR11aBackgroundScroll.State b) {
        assertEquals(a.cameraBgX(), b.cameraBgX()); assertEquals(a.cameraBgY(), b.cameraBgY());
        assertEquals(a.cameraBg2X(), b.cameraBg2X()); assertEquals(a.cameraBg2Y(), b.cameraBg2Y());
        assertEquals(a.cameraBg3X(), b.cameraBg3X()); assertEquals(a.cameraBg3Y(), b.cameraBg3Y());
        assertEquals(a.scrollFlagsBg(), b.scrollFlagsBg()); assertEquals(a.scrollFlagsBg2(), b.scrollFlagsBg2());
        assertEquals(a.scrollFlagsBg3(), b.scrollFlagsBg3()); assertEquals(a.vscroll(), b.vscroll());
        assertArrayEquals(a.crossingRam().copy(), b.crossingRam().copy());
        assertArrayEquals(a.cloudRam().copy(), b.cloudRam().copy());
        assertArrayEquals(a.deformationRam().copy(), b.deformationRam().copy());
        assertArrayEquals(a.hscrollRam().copy(), b.hscrollRam().copy());
    }
    private static void assertScrollFrame(Harness h, LevelSpritePresentation.ScrollFrame frame,
                                          CdR11aBackgroundScroll.State expected) {
        LevelSpritePresentation.withScroll(h.mode().getLevelManager(), frame, () -> {
            var actual = LevelSpritePresentation.backgroundScrollForDraw(h.mode().getLevelManager());
            assertNotNull(actual); assertEquals((float) (short) expected.vscroll(), actual.y());
            var horizontal = actual.horizontal(); assertEquals(224, horizontal.length);
            for (int i = 0; i < horizontal.length; i++) {
                assertEquals((int) expected.hscrollRam().unsignedLong(i * 4), horizontal[i]);
            }
        });
    }
    private static CdR11aBackgroundPlane.LiveCameras live(CdR11aBackgroundScroll.State b) {
        return new CdR11aBackgroundPlane.LiveCameras(new CdR11aBackgroundPlane.Camera(b.cameraBgX(), b.cameraBgY()),
                new CdR11aBackgroundPlane.Camera(b.cameraBg2X(), b.cameraBg2Y()), new CdR11aBackgroundPlane.Camera(b.cameraBg3X(), b.cameraBg3Y()));
    }
    private static CdR11aBackgroundPlane.CopiedCameras copied(CdR11aBackgroundScroll.State b) {
        var live = live(b); return new CdR11aBackgroundPlane.CopiedCameras(live.bg1(), live.bg2(), live.bg3());
    }
    private static CdR11aBackgroundPlane.FlagWords flagWords(CdR11aBackgroundScroll.State b) {
        return new CdR11aBackgroundPlane.FlagWords(b.scrollFlagsBg(), b.scrollFlagsBg2(), b.scrollFlagsBg3());
    }
    private static CdR11aBackgroundScroll.State flags(CdR11aBackgroundScroll.State b, int one, int two, int three) {
        return new CdR11aBackgroundScroll.State(b.cameraBgX(), b.cameraBgY(), b.cameraBg2X(), b.cameraBg2Y(),
                b.cameraBg3X(), b.cameraBg3Y(), b.crossingRam(), one, two, three, b.cloudRam(), b.deformationRam(), b.hscrollRam(), b.vscroll());
    }
    private static SonicCdPlayerPresentation.DrawCopies clearedCopies() {
        var zero = new CdR11aBackgroundPlane.Camera(0, 0);
        return new SonicCdPlayerPresentation.DrawCopies(0, 0, new CdR11aBackgroundPlane.CopiedCameras(zero, zero, zero),
                0, new CdR11aBackgroundPlane.FlagWords(0, 0, 0));
    }
    private static SonicCdPlayerPresentation.DrawCopies consumedCopies(SonicCdPlayerPresentation.ScrollCapture cut,
                                                                      CdR11aBackgroundPlane.FlagWords remaining) {
        return new SonicCdPlayerPresentation.DrawCopies(cut.state().cameraX() << 16, cut.state().cameraY16_16(),
                copied(cut.background()), cut.state().scrollFlags(), remaining);
    }
    private static CdR11aBackgroundPlane.Result initialPlane(Harness h) {
        var cut = scroll(h);
        return CdR11aBackgroundPlane.bind(h.module().requirePlayerFrame().assets()).initialize(
                new CdR11aBackgroundPlane.State(CdAssetBytes.copyOf(new byte[4096])), live(cut.background()), flagWords(cut.background()));
    }
    private static CdR11aBackgroundPlane.Result nextPlane(Harness h, SonicCdPlayerPresentation.ScrollCapture cut) {
        return CdR11aBackgroundPlane.bind(h.module().requirePlayerFrame().assets()).incremental(cut.plane(),
                live(cut.background()), copied(cut.background()), flagWords(cut.background()));
    }
    private static int pieceFor(CdObjectDrawQueue.CpuSat cpu, int slot) {
        for (int i = 0; i < cpu.provenance().size(); i++) {
            if (cpu.provenance().get(i).slotRef().wholeIndex() == slot) return i;
        }
        fail("Expected a real queued piece for physical slot " + slot); return -1;
    }
    private static int satX(CdObjectDrawQueue.CpuSat cpu, int piece) {
        return ByteBuffer.wrap(cpu.bytes()).getShort(piece * 8 + 6) & 0xFFFF;
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
