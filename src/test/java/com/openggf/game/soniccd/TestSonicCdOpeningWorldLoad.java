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
    @Test void ordinaryRightRouteCompletesAntonCheckpointCollapseAndPlatform21Through1200WithReplay() throws Exception {
        var h=opening();var runtime=objects(h);var frame=h.module().requirePlayerFrame();var pool=runtime.placement().pool();
        for(int call=0;call<560;call++)step(h,input(8,0,false));
        assertEquals(0,pool.state(46).id());
        assertEquals(0x3E,pool.state(52).id());assertEquals(2,pool.state(52).routine());
        assertEquals(2466,pool.state(52).centreX());assertEquals(628,pool.state(52).centreY());
        assertEquals(0,runtime.sectionArtState().remainingTilesWord());assertEquals(0,runtime.sectionArtState().queue().headSource());
        var registry=h.mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
        var before=state(h);
        step(h,input(8,0,false)); // Completed ordinary call561, including native Spikes Init/Main/Draw/tail.
        // Native record94 0A0D04A01B004000 has completed its real Init before the later physical stop.
        var rock=pool.state(46);assertEquals(0x1B,rock.id());assertEquals(2,rock.routine());
        assertEquals(2573,rock.centreX());assertEquals(1184,rock.centreY());assertEquals(4,rock.spriteFlags());
        assertEquals(0x209654,rock.longBits(CdObjectState.LongField.MAPPINGS));assertEquals(0x0374,rock.word(CdObjectState.WordField.TILE));
        assertEquals(4,rock.unsignedByte(CdObjectState.ByteField.PRIORITY));assertEquals(16,rock.unsignedByte(CdObjectState.ByteField.WIDTH));
        assertEquals(16,rock.unsignedByte(CdObjectState.ByteField.Y_RADIUS));assertEquals(0,rock.unsignedByte(CdObjectState.ByteField.MAP_FRAME));
        assertEquals(0,rock.unsignedByte(CdObjectState.ByteField.COLLISION_TYPE));assertEquals(0,rock.flags());
        var spring=pool.state(47);assertEquals(0x0A,spring.id());assertEquals(0x14,spring.routine());
        assertEquals(2576,spring.centreX());assertEquals(1038,spring.centreY());assertEquals(4,spring.spriteFlags());
        assertEquals(0x209BF0,spring.longBits(CdObjectState.LongField.MAPPINGS));assertEquals(0x2490,spring.word(CdObjectState.WordField.TILE));
        assertEquals(0xF600,spring.word(CdObjectState.WordField.SPRING_SPEED));
        assertEquals(24,spring.unsignedByte(CdObjectState.ByteField.WIDTH));assertEquals(12,spring.unsignedByte(CdObjectState.ByteField.Y_RADIUS));
        assertEquals(0,spring.unsignedByte(CdObjectState.ByteField.MAP_FRAME));
        assertEquals(145239552,frame.state().motion().centreX16_16());assertEquals(-337408,frame.state().motion().centreY16_16());
        // Physical52 and54 now complete before Spikes61; equal integer Y alone would miss Pata motion.
        assertEquals(628,pool.state(52).centreY());assertEquals(0x8000,pool.state(52).word(CdObjectState.WordField.X_FRACTION));
        assertEquals(0x5FFF,pool.state(52).word(CdObjectState.WordField.Y_FRACTION));
        assertEquals(0x10,pool.state(54).id());assertEquals(2,pool.state(54).routine());
        var spikes=pool.state(61);assertEquals(0x26,spikes.id());assertEquals(2,spikes.routine());
        assertEquals(2643,spikes.centreX());assertEquals(1038,spikes.centreY());assertEquals(4,spikes.spriteFlags());
        assertEquals(0x20C1F0,spikes.longBits(CdObjectState.LongField.MAPPINGS));assertEquals(0x0320,spikes.word(CdObjectState.WordField.TILE));
        assertEquals(3,spikes.unsignedByte(CdObjectState.ByteField.PRIORITY));assertEquals(16,spikes.unsignedByte(CdObjectState.ByteField.WIDTH));
        assertEquals(8,spikes.unsignedByte(CdObjectState.ByteField.Y_RADIUS));assertEquals(0,spikes.unsignedByte(CdObjectState.ByteField.COLLISION_TYPE));
        assertEquals(0,spikes.flags());
        assertEquals(0x0200,runtime.sectionArtState().sectionWord());assertEquals(0,runtime.sectionArtState().remainingTilesWord());
        assertFalse(runtime.closed());assertFalse(frame.closed());var at561=state(h);
        registry.restore(full);assertState(before,h);step(h,input(8,0,false));assertState(at561,h);
        registry.restoreCourse(course);assertState(before,h);step(h,input(8,0,false));assertState(at561,h);
        for(int call=562;call<=581;call++)step(h,input(8,0,false));
        assertFalse(runtime.closed());assertFalse(frame.closed());
        var full581=registry.capture();var course581=registry.captureCourse();var at581=state(h);
        var savedBeforeInit=runtime.checkpointState();
        step(h,input(8,0,false)); // Completed582: actual Checkpoint Init returns, later35 child Main runs this pass.
        var door=pool.state(32);assertEquals(0x35,door.id());assertEquals(2,door.routine());
        assertEquals(0x20CE9A,door.longBits(CdObjectState.LongField.MAPPINGS));assertEquals(0x8328,door.word(CdObjectState.WordField.TILE));
        var diagonal=pool.state(33);assertEquals(0x0A,diagonal.id());assertEquals(0x14,diagonal.routine());
        assertEquals(0x209BF0,diagonal.longBits(CdObjectState.LongField.MAPPINGS));assertEquals(2688,diagonal.centreX());assertEquals(1104,diagonal.centreY());
        var checkpoint=pool.state(34);assertEquals(0x13,checkpoint.id());assertEquals(2,checkpoint.routine());
        assertEquals(2760,checkpoint.centreX());assertEquals(744,checkpoint.centreY());assertEquals(0x23F120,checkpoint.longBits(CdObjectState.LongField.MAPPINGS));
        assertArrayEquals(java.util.HexFormat.of().parseHex("130406cb0023f1200ac8000002e8000000000000000018000408000000000000e300000002000000020000000000000000000000000000000000000000000000"),checkpoint.copyBytes());
        var ball=pool.state(35);assertEquals(0x13,ball.id());assertEquals(4,ball.routine());
        assertEquals(0xFFFFD880,ball.longBits(CdObjectState.LongField.CHECKPOINT_PARENT));
        assertEquals(2760,ball.centreX());assertEquals(712,ball.centreY());assertEquals(0,ball.unsignedByte(CdObjectState.ByteField.CHECKPOINT_ACTIVE));
        assertEquals(0,runtime.levelGlobals().checkpoint());assertEquals(0,runtime.levelGlobals().spawnMode());
        assertEquals(savedBeforeInit,runtime.checkpointState()); // Natural route initializes this pole, not an invented contact/save.
        assertEquals(153497088,frame.state().motion().centreX16_16());assertEquals(10844672,frame.state().motion().centreY16_16());
        assertEquals(0x0200,runtime.sectionArtState().sectionWord());assertEquals(0,runtime.sectionArtState().remainingTilesWord());
        assertEquals(0,runtime.sectionArtState().queue().headSource());assertFalse(runtime.closed());assertFalse(frame.closed());
        var at582=state(h);
        registry.restore(full581);assertState(at581,h);step(h,input(8,0,false));assertState(at582,h);assertEquals(savedBeforeInit,runtime.checkpointState());
        registry.restoreCourse(course581);assertState(at581,h);step(h,input(8,0,false));assertState(at582,h);assertEquals(savedBeforeInit,runtime.checkpointState());
        for(int call=583;call<=602;call++)step(h,input(8,0,false));
        var full602=registry.capture();var course602=registry.captureCourse();var at602=state(h);
        step(h,input(8,0,false));assertFalse(runtime.closed());assertFalse(frame.closed());var at603=state(h);
        registry.restore(full602);assertState(at602,h);step(h,input(8,0,false));assertState(at603,h);assertEquals(savedBeforeInit,runtime.checkpointState());
        registry.restoreCourse(course602);assertState(at602,h);step(h,input(8,0,false));assertState(at603,h);assertEquals(savedBeforeInit,runtime.checkpointState());
        var full603=registry.capture();
        var actualCourse603=registry.captureCourse();
        step(h,input(8,0,false));
        assertArrayEquals(java.util.HexFormat.of().parseHex("3f0424090020b3aa0b10000004260000000000000000131804180000010000002900003a00000000ff00ffff80000b1000000000000000000000000000000000"),pool.state(55).copyBytes());
        assertEquals(2,pool.state(56).routine());assertEquals(8,pool.state(57).routine());assertEquals(2,pool.state(58).routine());
        assertFalse(runtime.closed());assertFalse(frame.closed());var at604=state(h);
        registry.restore(full603);assertState(at603,h);step(h,input(8,0,false));assertState(at604,h);
        registry.restoreCourse(actualCourse603);assertState(at603,h);step(h,input(8,0,false));assertState(at604,h);
        int contactCall=-1;CdOpeningObjectRuntime.CheckpointOwnerState naturalSaved=null;
        for(int call=605;call<=656;call++) {
            var beforeCall=state(h);var actualFull=registry.capture();var actualCourse=registry.captureCourse();
            int oldCheckpoint=runtime.levelGlobals().checkpoint();
            step(h,input(8,0,false));assertFalse(runtime.closed());assertFalse(frame.closed());
            var afterCall=state(h);
            if(call==609)assertArrayEquals(java.util.HexFormat.of().parseHex("3f0424090020b3aa0b100000042b0000000000000000131804180000010000002900003a02000000ff00ffff80000b1000000000000000000000000000000000"),pool.state(55).copyBytes());
            if(call==610)assertArrayEquals(java.util.HexFormat.of().parseHex("3f0424090020b3aa0b100000042c0000000000000000131804180000010000002900003a04000000ff00ffff80000b1000000000000000000000000000000000"),pool.state(55).copyBytes());
            boolean contact=oldCheckpoint!=runtime.levelGlobals().checkpoint();
            if(contact) {
                assertEquals(-1,contactCall);contactCall=call;naturalSaved=runtime.checkpointState();
                assertEquals(2,runtime.levelGlobals().checkpoint());assertEquals(1,runtime.levelGlobals().spawnMode());
                assertEquals(0,pool.state(34).unsignedByte(CdObjectState.ByteField.COLLISION_TYPE));
                assertEquals(1,pool.state(34).unsignedByte(CdObjectState.ByteField.CHECKPOINT_ACTIVE));
                byte[] known=new byte[31];Arrays.fill(known,(byte)1);assertArrayEquals(known,naturalSaved.saved().knownMask().copy());
                assertEquals(1,naturalSaved.saved().read(com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.SPAWN_MODE));
                assertEquals(frame.state().motion().centreX16_16()>>>16,naturalSaved.saved().read(com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.PRIMARY_X));
                assertEquals(frame.state().motion().centreY16_16()>>>16,naturalSaved.saved().read(com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.PRIMARY_Y));
                assertEquals(0,naturalSaved.saved().read(com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.TIME_LONG));
                assertEquals(1,runtime.lastSoundRequests().stream().filter(v->v instanceof CdOpeningObjectRuntime.CheckpointFm fm
                        && fm.sound()==com.openggf.game.soniccd.objects.CdCheckpoint.FmSound.FM_CHECKPOINT_AE).count());
                System.out.println("NATURAL_CHECKPOINT_CONTACT call="+call+" saved="+naturalSaved.saved()+" actor="+frame.state().motion());
            }
            if(contact||call==609||call==610||call==611||call==656) {
                registry.restore(actualFull);assertState(beforeCall,h);step(h,input(8,0,false));assertState(afterCall,h);
                if(naturalSaved!=null)assertEquals(naturalSaved,runtime.checkpointState());
                registry.restoreCourse(actualCourse);assertState(beforeCall,h);step(h,input(8,0,false));assertState(afterCall,h);
                if(naturalSaved!=null)assertEquals(naturalSaved,runtime.checkpointState());
            }
        }
        assertEquals(649,contactCall,"Actual controller-only contact/save; no staged pole/player");
        assertNotNull(naturalSaved);assertEquals(naturalSaved,runtime.checkpointState());
        // Controller only, no object/player/art ingress. Native Init completes the former657 frontier.
        for(int call=657;call<=741;call++) {
            var beforeCall=state(h);var fullCall=registry.capture();var courseCall=registry.captureCourse();
            step(h,input(8,0,false));assertFalse(runtime.closed());assertFalse(frame.closed());
            assertEquals(naturalSaved,runtime.checkpointState(),"Earlier natural save must survive later native platform work");
            if(call==657) {
                assertArrayEquals(java.util.HexFormat.of().parseHex("200444be0020c5ec0cb0000003100000000000000000123003300000000000000000004402000000c00000000000000000000000000000000000000000000000"),pool.state(40).copyBytes());
                assertArrayEquals(java.util.HexFormat.of().parseHex("200444be0020c5200cf8000002200000000000000000222803280000000000000000004502000000000000000000000000000000000000000000000000000000"),pool.state(45).copyBytes());
                assertEquals(183030784,frame.state().motion().centreX16_16());assertEquals(48438784,frame.state().motion().centreY16_16());
                assertEquals(1534,frame.state().motion().xVelocity());assertEquals(-300,frame.state().motion().yVelocity());
            }
            long a3=runtime.lastSoundRequests().stream().filter(CdOpeningObjectRuntime.CollapsePlatformFm.class::isInstance).count();
            assertEquals(call==723?1L:0L,a3,"Actual one native breakup request at its observed controller cut");
            if(call==723) {
                assertEquals(0,pool.state(40).id());
                int[] slots={35,55,56,57,58,62},xs={3208,3224,3240,3256,3272,3288},delays={9,20,32,44,56,68};
                for(int i=0;i<slots.length;i++) {
                    var child=pool.state(slots[i]);assertEquals(0x20,child.id());assertEquals(4,child.routine());
                    assertEquals(xs[i],child.centreX());assertEquals(784,child.centreY());
                    assertEquals(0x20C7AC,child.longBits(CdObjectState.LongField.MAPPINGS));
                    assertEquals(0x44BE,child.word(CdObjectState.WordField.TILE));
                    assertEquals(delays[i],child.word(CdObjectState.WordField.COLLAPSE_DELAY));
                    assertEquals(0x20000,child.longBits(CdObjectState.LongField.COLLAPSE_FALL_DELTA));
                    assertEquals(0xFF,child.unsignedByte(CdObjectState.ByteField.COLLAPSE_SOLID_MARKER));
                    assertEquals(8,child.unsignedByte(CdObjectState.ByteField.WIDTH));assertEquals(18,child.unsignedByte(CdObjectState.ByteField.Y_RADIUS));
                }
                assertEquals(209266432,frame.state().motion().centreX16_16());assertEquals(48959232,frame.state().motion().centreY16_16());
            }
            if(call==732) {
                var child=pool.state(35);assertEquals(6,child.routine());assertEquals(0,child.word(CdObjectState.WordField.COLLAPSE_DELAY));
                assertEquals(784<<16,child.longBits(CdObjectState.LongField.Y_POSITION),"Delay transition does not run Fall in that visit");
                assertEquals(0x20000,child.longBits(CdObjectState.LongField.COLLAPSE_FALL_DELTA));
            }
            if(call==741) {
                assertEquals(811<<16,pool.state(35).longBits(CdObjectState.LongField.Y_POSITION));
                assertEquals(0x44000,pool.state(35).longBits(CdObjectState.LongField.COLLAPSE_FALL_DELTA));
            }
            var afterCall=state(h);
            if(call==657||call==723||call==732||call==741) {
                registry.restore(fullCall);assertState(beforeCall,h);step(h,input(8,0,false));assertState(afterCall,h);
                registry.restoreCourse(courseCall);assertState(beforeCall,h);step(h,input(8,0,false));assertState(afterCall,h);
            }
        }
        assertEquals(0,pool.state(75).id(),"This closed cut precedes the next native SpawnObjects");
        // Native ObjPlatform Init returns before Main (Platform/Main.asm:30-81); the ordinary
        // wrapper still runs DrawObject (same source:7-13). These are literal test oracles,
        // corroborated by Root's completed USA controller cuts, never production-derived expected bytes.
        byte[] platformInit=java.util.HexFormat.of().parseHex(
                "210444be0020ccc80ef00000015c00000000000000000800021000000000000000000000020000004000000000020000000000000000015c0ef0015c00000000");
        var level=h.mode().getLevelManager();var loadedLevel=level.getCurrentLevel();
        long generation=level.getCompletedProductionLoadGeneration();var actor=h.module().requirePlayerActor();
        for(int call=742;call<=1200;call++) {
            // Eligible full/course cuts: post741 -> Init742, post742 -> first Main743,
            // and the later pre-terminal1199 -> completed1200. No failed-call continuation.
            boolean replay=call==742||call==743||call==1200;
            var beforeCall=replay?state(h):null;
            var fullCall=replay?registry.capture():null;var courseCall=replay?registry.captureCourse():null;
            int levelFrame=level.getFrameCounter();var clock=frame.vIntClock();
            step(h,input(8,0,false));
            assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT,runtime.phase());
            assertFalse(runtime.closed());assertFalse(frame.closed());
            assertEquals(levelFrame+1,level.getFrameCounter());
            assertEquals(clock.absoluteSeed(),frame.vIntClock().absoluteSeed());
            assertEquals(clock.deltaSinceAdmittedCut()+1,frame.vIntClock().deltaSinceAdmittedCut());
            assertEquals(naturalSaved,runtime.checkpointState(),"Natural call649 save survives every later Right call");

            var platform=pool.state(75);
            assertEquals(0x21,platform.id());assertEquals(2,platform.routine());assertEquals(0x40,platform.subtype());
            // Subtype4X is stationary without contact, not an unimplemented oscillator.
            // Top is unconditional in Main; its own no-contact path clears Solid type again.
            assertEquals(3824,platform.centreX());assertEquals(348,platform.centreY());
            assertEquals(0,platform.unsignedByte(CdObjectState.ByteField.PLATFORM_PHASE));
            assertEquals(0,platform.unsignedByte(CdObjectState.ByteField.PLATFORM_SUBSTATE));
            assertEquals(0,platform.unsignedByte(CdObjectState.ByteField.PLATFORM_SAG));
            assertEquals(0,platform.unsignedByte(CdObjectState.ByteField.PLATFORM_TIMER));
            assertEquals(0,platform.unsignedByte(CdObjectState.ByteField.SOLID_TYPE));
            if(call==742||call==743)assertArrayEquals(platformInit,platform.copyBytes());
            if(call==742) {
                assertEquals(0x20CCC8,platform.longBits(CdObjectState.LongField.MAPPINGS));
                assertEquals(0x44BE,platform.word(CdObjectState.WordField.TILE));
                assertEquals(2,platform.unsignedByte(CdObjectState.ByteField.PRIORITY));
                assertEquals(0x10,platform.unsignedByte(CdObjectState.ByteField.WIDTH));
                assertEquals(8,platform.unsignedByte(CdObjectState.ByteField.Y_RADIUS));
                assertEquals(0,platform.unsignedByte(CdObjectState.ByteField.X_RADIUS));
                assertEquals(0,platform.unsignedByte(CdObjectState.ByteField.MAP_FRAME));
                assertEquals(2,platform.unsignedByte(CdObjectState.ByteField.PLATFORM_RANGE));
                assertEquals(348,platform.word(CdObjectState.WordField.PLATFORM_INITIAL_Y));
                assertEquals(3824,platform.word(CdObjectState.WordField.PLATFORM_ANCHOR_X));
                assertEquals(348,platform.word(CdObjectState.WordField.PLATFORM_ANCHOR_Y));
                assertEquals(4,platform.spriteFlags(),"Native Draw leaves bit7 clear at this offscreen Init cut");
                assertEquals(0x032F4000,pool.state(35).longBits(CdObjectState.LongField.Y_POSITION));
                assertEquals(0x48000,pool.state(35).longBits(CdObjectState.LongField.COLLAPSE_FALL_DELTA));
                assertEquals(216737536,frame.state().motion().centreX16_16());
                assertEquals(48973568,frame.state().motion().centreY16_16());
                assertEquals(1536,frame.state().motion().xVelocity());assertEquals(0,frame.state().motion().yVelocity());
                assertEquals(1536,frame.state().motion().groundVelocity());assertEquals(2,frame.state().motion().routine());
                assertEquals(2,frame.state().motion().flags());assertEquals(132,frame.state().motion().spriteFlags());
                assertEquals(0,frame.state().objectLinks().pushObject());assertEquals(0,frame.state().objectLinks().standObject());
                assertEquals(0x0200,runtime.sectionArtState().sectionWord());assertEquals(0,runtime.sectionArtState().remainingTilesWord());
                assertEquals(0,runtime.sectionArtState().queue().headSource());
            }
            if(call==743) {
                assertEquals(217130752,frame.state().motion().centreX16_16());
                assertEquals(48973568,frame.state().motion().centreY16_16());
                assertEquals(1536,frame.state().motion().xVelocity());assertEquals(56,frame.state().motion().yVelocity());
                assertEquals(1536,frame.state().motion().groundVelocity());assertEquals(2,frame.state().motion().routine());
                assertEquals(2,frame.state().motion().flags());
            }
            if(replay) {
                var afterCall=state(h);
                registry.restore(fullCall);assertState(beforeCall,h);assertEquals(naturalSaved,runtime.checkpointState());
                step(h,input(8,0,false));assertState(afterCall,h);assertEquals(naturalSaved,runtime.checkpointState());
                assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT,runtime.phase());
                assertFalse(runtime.closed());assertFalse(frame.closed());
                registry.restoreCourse(courseCall);assertState(beforeCall,h);assertEquals(naturalSaved,runtime.checkpointState());
                step(h,input(8,0,false));assertState(afterCall,h);assertEquals(naturalSaved,runtime.checkpointState());
                assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT,runtime.phase());
                assertFalse(runtime.closed());assertFalse(frame.closed());
            }
            assertSame(level,h.mode().getLevelManager());assertSame(loadedLevel,level.getCurrentLevel());
            assertEquals(generation,level.getCompletedProductionLoadGeneration(),"No load/restart epoch within this bounded route");
            assertSame(runtime,objects(h));assertSame(frame,h.module().requirePlayerFrame());
            assertSame(actor,h.module().requirePlayerActor());assertSame(pool,runtime.placement().pool());
        }
        // Completed1200 is a controller pushing obstacle, not an unsupported boundary or act completion.
        var terminal=frame.state();var motion=terminal.motion();
        assertEquals(222748416,motion.centreX16_16());assertEquals(48994048,motion.centreY16_16());
        assertEquals(0,motion.xVelocity());assertEquals(0,motion.yVelocity());assertEquals(0,motion.groundVelocity());
        assertEquals(2,motion.routine());assertEquals(32,motion.flags());assertEquals(132,motion.spriteFlags());
        assertEquals(0,terminal.timers().hurt());
        assertEquals(0,frame.environment().movement().globals().primaryAngle());
        assertEquals(0,frame.environment().movement().globals().secondaryAngle());
        assertEquals(53,terminal.objectLinks().pushObject(),"Raw whole-slot link only; no inferred object ID/class");
        assertEquals(0,terminal.objectLinks().standObject());
        assertEquals(1,terminal.animation().scriptIndex());assertEquals(6,terminal.animation().timer());
        assertEquals(100,terminal.animation().mapFrame());
        assertEquals(0x0200,runtime.sectionArtState().sectionWord());assertEquals(0,runtime.sectionArtState().remainingTilesWord());
        assertEquals(0,runtime.sectionArtState().queue().headSource());assertEquals(List.of(),runtime.lastSoundRequests());
        assertEquals(2,runtime.levelGlobals().checkpoint());assertEquals(1,runtime.levelGlobals().spawnMode());
        assertEquals(naturalSaved,runtime.checkpointState());
        byte[] known=new byte[31];Arrays.fill(known,(byte)1);assertArrayEquals(known,naturalSaved.saved().knownMask().copy());
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT,runtime.phase());
        assertFalse(runtime.closed());assertFalse(frame.closed());
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
        assertEquals(expected.objects().checkpointState(), actual.objects().checkpointState(), "Live/saved/knowledge must restore BEFORE contact replay too");
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

    /**
     * SOURCE-ONLY/UNRUN: Root's observed controller cuts end at fresh6; fresh7..14 is the
     * source-derived Source12 oracle. Loaded0 means the completed new Init at global1947,
     * not a fabricated frame-counter-zero cut. DEV HUD0 is explicit, not a retail timer claim.
     */
    @Test void controllerJumpRouteRestartsFromNaturalCheckpointAndServicesAnimalsWithFreshEpochReplay() throws Exception {
        GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_DETERMINISM_AUDIT, false);
        final String controllerScript = "1200 R;32 R+B;1200 R";
        var log = com.openggf.tools.InputScriptCompiler.compileToInputLog(controllerScript);
        var movie = new com.openggf.debug.playback.Bk2MovieLoader().parseInputLogText(
                Path.of("natural-checkpoint-animals-controller"), log.lines().toList());
        assertEquals(2432, movie.getFrameCount());
        assertEquals(0, movie.getFrame(1199).p1ActionMask());
        assertEquals(2, movie.getFrame(1200).p1ActionMask());
        assertEquals(2, movie.getFrame(1231).p1ActionMask());
        assertEquals(0, movie.getFrame(1232).p1ActionMask());

        var h = opening();
        var old = objects(h); var oldFrame = h.module().requirePlayerFrame();
        var oldActor = h.module().requirePlayerActor();
        var oldPlc = h.module().getGameService(PlcLifecycleService.class);
        var world = h.mode().getWorldSession(); var level = h.mode().getLevelManager();
        var oldLevel = level.getCurrentLevel();
        long generation = level.getCompletedProductionLoadGeneration();
        var registry = h.mode().getRewindRegistry();
        CdOpeningObjectRuntime.CheckpointOwnerState naturalSaved = null;
        int firstDead = -1, firstGone = -1, firstRestart = -1;
        for (int call = 1; call <= 1946; call++) {
            int previousCheckpoint = old.levelGlobals().checkpoint();
            var row = movie.getFrame(call - 1);
            step(h, input(row.p1InputMask(), row.p1ActionMask(), row.p1StartPressed()));
            assertSame(world, h.mode().getWorldSession());
            assertSame(level, h.mode().getLevelManager()); assertSame(oldLevel, level.getCurrentLevel());
            assertSame(old, objects(h)); assertSame(oldFrame, h.module().requirePlayerFrame());
            assertSame(oldActor, h.module().requirePlayerActor());
            assertSame(oldPlc, h.module().getGameService(PlcLifecycleService.class));
            assertEquals(generation, level.getCompletedProductionLoadGeneration());
            assertFalse(old.closed()); assertFalse(oldFrame.closed());
            assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, old.phase());
            if (previousCheckpoint != old.levelGlobals().checkpoint()) {
                assertEquals(649, call); assertEquals(0, previousCheckpoint);
                assertNull(naturalSaved);
                naturalSaved = old.checkpointState(); // Actual contact, before hurt/death; no SaveData call.
                assertEquals(2, old.levelGlobals().checkpoint()); assertEquals(1, old.levelGlobals().spawnMode());
                byte[] allSavedKnown = new byte[31]; Arrays.fill(allSavedKnown, (byte) 1);
                assertArrayEquals(allSavedKnown, naturalSaved.saved().knownMask().copy());
                assertEquals(1, naturalSaved.saved().read(
                        com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.SPAWN_MODE));
                assertEquals(oldFrame.state().motion().centreX16_16() >>> 16, naturalSaved.saved().read(
                        com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.PRIMARY_X));
                assertEquals(oldFrame.state().motion().centreY16_16() >>> 16, naturalSaved.saved().read(
                        com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.PRIMARY_Y));
                assertEquals(0, naturalSaved.saved().read(
                        com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.TIME_LONG));
                assertEquals(List.of(new CdOpeningObjectRuntime.CheckpointFm(
                                com.openggf.game.soniccd.objects.CdCheckpoint.FmSound.FM_CHECKPOINT_AE)),
                        old.lastSoundRequests().stream().filter(CdOpeningObjectRuntime.CheckpointFm.class::isInstance).toList());
            }
            if (naturalSaved != null) assertNaturalRestartCheckpoint(naturalSaved, h);
            if (call == 1200) {
                var actor = oldFrame.state();
                assertEquals(222748416, actor.motion().centreX16_16());
                assertEquals(48994048, actor.motion().centreY16_16());
                assertEquals(2, actor.motion().routine()); assertEquals(53, actor.objectLinks().pushObject());
                assertEquals(2, old.levelGlobals().checkpoint()); assertEquals(1, old.levelGlobals().spawnMode());
            }
            int routine = oldFrame.state().motion().routine();
            if (routine == 6 && firstDead < 0) firstDead = call;
            if (routine == 8 && firstGone < 0) firstGone = call;
            if (old.levelGlobals().levelRestart() != 0 && firstRestart < 0) firstRestart = call;
            if (call == 1802) {
                // ObjSonic_HurtChkLand -> KillPlayer: Sonic/Main.asm:2372-2376;
                // KillPlayer (PP Player Object Collision.asm:279-294) writes routine6, -$700, FM_HURT.
                assertEquals(6, routine); assertEquals(-1792, oldFrame.state().motion().yVelocity());
                assertEquals(List.of(new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(
                                com.openggf.game.soniccd.player.CdPlayerTouchResponse.FmSound.FM_HURT))),
                        old.lastSoundRequests().stream().filter(CdOpeningObjectRuntime.PlayerSound.class::isInstance).toList());
            }
            if (call == 1886) {
                // DeadChkGone (Sonic/Main.asm:2416-2444) precedes later live GameOver Init
                // (Game Over/Main.asm:27-49): routine8/reset60/life decrement, then SCMD_FADEPCM.
                assertEquals(8, routine); assertEquals(60, oldFrame.state().timers().reset());
                assertEquals(2, old.ringGlobals().lives());
                assertEquals(List.of(new CdOpeningObjectRuntime.GameOverSubCpu(
                                com.openggf.game.soniccd.objects.CdGameOver.SubCpuCommand.SCMD_FADEPCM)),
                        old.lastSoundRequests().stream().filter(CdOpeningObjectRuntime.GameOverSubCpu.class::isInstance).toList());
            }
            if (call == 1945) assertEquals(1, oldFrame.state().timers().reset());
            assertEquals(call == 1946 ? 1 : 0, old.levelGlobals().levelRestart());
        }
        assertNotNull(naturalSaved);
        assertEquals(1802, firstDead); assertEquals(1886, firstGone); assertEquals(1946, firstRestart);
        assertEquals(8, oldFrame.state().motion().routine()); assertEquals(0, oldFrame.state().timers().reset());
        assertEquals(2, old.levelGlobals().checkpoint()); assertEquals(1, old.levelGlobals().spawnMode());
        // ObjSonic_Restart (Sonic/Main.asm:2459-2491): ordered typed requests only, no driver/audio execution.
        assertEquals(List.of(
                        new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.DriverRamWrite(0x1C3E, 1)),
                        new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.BodySound(CdPlayerBody.Sound.SCMD_FADECDA))),
                old.lastSoundRequests().stream().filter(CdOpeningObjectRuntime.PlayerSound.class::isInstance).toList());
        oldActor.requireCompletedNativeFrame();
        var old1946 = state(h); var oldFull = registry.capture(); var oldCourse = registry.captureCourse();

        var loadRow = movie.getFrame(1946);
        step(h, input(loadRow.p1InputMask(), loadRow.p1ActionMask(), loadRow.p1StartPressed())); // Global1947.
        assertSame(world, h.mode().getWorldSession()); assertSame(level, h.mode().getLevelManager());
        assertNotSame(oldLevel, level.getCurrentLevel());
        assertEquals(generation + 1, level.getCompletedProductionLoadGeneration());
        var fresh = objects(h); var freshFrame = h.module().requirePlayerFrame();
        var freshActor = h.module().requirePlayerActor();
        assertNotSame(old, fresh); assertNotSame(oldFrame, freshFrame); assertNotSame(oldActor, freshActor);
        assertNotSame(oldPlc, h.module().getGameService(PlcLifecycleService.class));
        assertTrue(old.closed()); assertTrue(oldFrame.closed()); assertFalse(fresh.closed()); assertFalse(freshFrame.closed());
        assertSame(registry, h.mode().getRewindRegistry());
        assertEquals(1, level.getFrameCounter()); assertEquals(1, freshFrame.environment().globals().levelFrames());
        assertEquals(java.util.OptionalLong.of(0), freshFrame.vIntClock().absoluteSeed());
        assertEquals(1, freshFrame.vIntClock().deltaSinceAdmittedCut());
        var loadedMotion = freshFrame.state().motion();
        assertEquals(179765248, loadedMotion.centreX16_16()); assertEquals(48955392, loadedMotion.centreY16_16());
        assertEquals(0, loadedMotion.centreX16_16() & 0xFFFF); assertEquals(0, loadedMotion.centreY16_16() & 0xFFFF);
        assertEquals(2, loadedMotion.routine());
        assertEquals(new CdPlayerBody.ObjectLinks(0, 0), freshFrame.state().objectLinks());
        assertEquals(0, freshFrame.state().timers().hurt()); assertEquals(0, freshFrame.state().timers().reset());
        assertEquals(0, fresh.ringGlobals().rings()); assertEquals(2, fresh.ringGlobals().lives());
        assertEquals(7, fresh.ringGlobals().ringAnimationTimer()); assertEquals(1, fresh.ringGlobals().ringAnimationFrame());
        assertEquals(2, fresh.levelGlobals().checkpoint()); assertEquals(0, fresh.levelGlobals().spawnMode());
        assertEquals(0, fresh.levelGlobals().levelRestart()); assertEquals(0, fresh.levelGlobals().updateHudTime());
        assertEquals(3, fresh.levelGlobals().plcLoadFlags()); // Existing DEV title-loaded skip branch.
        assertEquals(2583, h.mode().getCamera().getX()); assertEquals(651, h.mode().getCamera().getY());
        assertSame(naturalSaved.saved(), fresh.checkpointState().saved());
        assertArrayEquals(naturalSaved.saved().knownMask().copy(), fresh.checkpointState().saved().knownMask().copy());
        assertEquals(naturalSaved.saved(), fresh.checkpointState().saved());
        // LoadCheckpointData (Load Saved Data.asm:49-67): restore raw0, frames=$3B,
        // then SUBQ.B seconds wraps to $FF. Main.SkipClear (:285-301) retains it before clearing spawn.
        assertEquals(0, fresh.checkpointState().saved().read(
                com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.TIME_LONG));
        assertEquals(0x0000FF3B, com.openggf.game.soniccd.objects.CdCheckpointLiveAux.read(
                fresh.checkpointState().live(), com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.TIME_LONG));
        assertEquals(naturalSaved.live().knownMask(), fresh.checkpointState().live().knownMask());

        var plan = freshFrame.assets().loadPlan();
        assertEquals(4, plan.sections().get(1).initialPlcId());
        var actualLoadList = plan.plcs().get(4).entries();
        assertEquals(6, actualLoadList.size());
        assertEquals(List.of(
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_BOULDER,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_ANIMALS,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_PATA_BATA,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_ANTON,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_TAMABBOH,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_GOAL_POST),
                actualLoadList.stream().map(com.openggf.game.soniccd.level.CdR11aLoadPlan.PlcEntry::source).toList());
        // LoadSectionArt (Section Art Load.asm:7-22), SectionInitPLCs:21-26 and PLC_Cam2_Full
        // (Data (Act 1 Present).asm:102-115), never canonical PLC2 forced over saved camera/section.
        var loadedArt = fresh.sectionArtState();
        assertEquals(0x0200, loadedArt.sectionWord()); assertEquals(19, loadedArt.remainingTilesWord());
        assertEquals(0x6E80, loadedArt.queue().headDestination());
        assertEquals(actualLoadList.getFirst().sourceRange().nativeAddress(),
                loadedArt.decoder().orElseThrow().nativeAddress());
        for (int row = 1; row < actualLoadList.size(); row++) {
            assertEquals(actualLoadList.get(row).sourceRange().nativeAddress(), loadedArt.queue().bytes().unsignedLong(row * 6));
            assertEquals(actualLoadList.get(row).vramAddress(), loadedArt.queue().bytes().unsignedWord(row * 6 + 4));
        }
        for (int offset = actualLoadList.size() * 6; offset < 96; offset++)
            assertEquals(0, loadedArt.queue().bytes().unsignedByte(offset));
        assertEquals(old1946.objects().sectionArtState().residency(), loadedArt.residency(),
                "Load/prepare carry the existing current bytes and knowledge; no Animals preload");
        assertArrayEquals(loadedArt.decoder().orElseThrow().codeTable().copy(), loadedArt.codeTable().copy());

        var loaded0 = state(h);
        // Exact existing Frame owner rejection, before any restore callback/partition mutation.
        var rejectedFull = assertThrows(IllegalArgumentException.class, () -> registry.restore(oldFull));
        assertEquals("Snapshot belongs to another CD runtime/source owner", rejectedFull.getMessage());
        assertState(loaded0, h); assertNaturalRestartCheckpoint(loaded0.objects().checkpointState(), h);
        var rejectedCourse = assertThrows(IllegalArgumentException.class, () -> registry.restoreCourse(oldCourse));
        assertEquals("Snapshot belongs to another CD runtime/source owner", rejectedCourse.getMessage());
        assertState(loaded0, h); assertNaturalRestartCheckpoint(loaded0.objects().checkpointState(), h);

        State[] freshStates = new State[15]; freshStates[0] = loaded0;
        int[] cutCalls = {0, 1, 7, 10};
        var freshFull = new java.util.ArrayList<com.openggf.game.rewind.CompositeSnapshot>();
        var freshCourse = new java.util.ArrayList<com.openggf.game.rewind.CompositeSnapshot>();
        freshFull.add(registry.capture()); freshCourse.add(registry.captureCourse());
        for (int call = 1; call <= 6; call++) {
            var row = movie.getFrame(1946 + call); // Fresh1..6 = global1948..1953.
            step(h, input(row.p1InputMask(), row.p1ActionMask(), row.p1StartPressed()));
            assertSame(fresh, objects(h)); assertSame(freshFrame, h.module().requirePlayerFrame());
            assertEquals(generation + 1, level.getCompletedProductionLoadGeneration());
            assertEquals(call + 1, level.getFrameCounter());
            assertEquals(call + 1, freshFrame.environment().globals().levelFrames());
            assertEquals(call + 1, freshFrame.vIntClock().deltaSinceAdmittedCut());
            assertEquals(19 - call * 3, fresh.sectionArtState().remainingTilesWord());
            assertEquals(0x0200, fresh.sectionArtState().sectionWord());
            assertNaturalRestartCheckpoint(loaded0.objects().checkpointState(), h);
            freshStates[call] = state(h);
            if (call == 1) { freshFull.add(registry.capture()); freshCourse.add(registry.captureCourse()); }
        }
        assertEquals(0x2365C5L, fresh.sectionArtState().queue().headSource());
        assertEquals(0x70C0, fresh.sectionArtState().queue().headDestination());

        // The old eleven-source runtime must reach this real call and fail in ordinary-tail
        // UNKNOWN_SOURCE $2381C0/$7100 AFTER its final Boulder service/pop. Do not catch/resume it.
        // Every Source12 lookup/count assertion is deliberately below this production step.
        var animalsReadyRow = movie.getFrame(1953);
        step(h, input(animalsReadyRow.p1InputMask(), animalsReadyRow.p1ActionMask(), animalsReadyRow.p1StartPressed()));
        assertEquals(8, level.getFrameCounter()); assertEquals(8, freshFrame.environment().globals().levelFrames());
        assertEquals(8, freshFrame.vIntClock().deltaSinceAdmittedCut());
        var animalsReady = fresh.sectionArtState();
        for (int row = 1; row < actualLoadList.size() - 1; row++) {
            assertEquals(actualLoadList.get(row + 1).sourceRange().nativeAddress(), animalsReady.queue().bytes().unsignedLong(row * 6));
            assertEquals(actualLoadList.get(row + 1).vramAddress(), animalsReady.queue().bytes().unsignedWord(row * 6 + 4));
        }
        assertEquals(20, animalsReady.remainingTilesWord()); assertEquals(3, animalsReady.processBudgetWord());
        assertEquals(0x0200, animalsReady.sectionWord()); assertEquals(0x7100, animalsReady.queue().headDestination());
        assertEquals(0x238219L, animalsReady.queue().headSource());
        var preparedAnimals = animalsReady.decoder().orElseThrow();
        assertEquals(0x2381C0L, preparedAnimals.nativeAddress()); assertEquals(0x0014, preparedAnimals.headerWord());
        assertFalse(preparedAnimals.xorMode()); assertEquals(89, preparedAnimals.inputOffset());
        assertEquals(20, preparedAnimals.remainingTiles()); assertEquals(0x6CD5, preparedAnimals.readWord());
        assertEquals(16, preparedAnimals.shiftWord());
        assertEquals(com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer.NORMAL, animalsReady.savedDecoder().writer());

        // Read-only access to the EXISTING retained caches, no reopened openingObjects() owner and no new API.
        var objectsField = CdOpeningObjectRuntime.class.getDeclaredField("objects"); objectsField.setAccessible(true);
        var retainedAssets = assertInstanceOf(com.openggf.game.soniccd.level.CdR11aObjectAssets.class, objectsField.get(fresh));
        var frontierField = CdOpeningObjectRuntime.class.getDeclaredField("frontierArt"); frontierField.setAccessible(true);
        var frontier = assertInstanceOf(com.openggf.game.soniccd.level.CdR11aObjectAssets.FrontierArtBank.class,
                frontierField.get(fresh));
        assertSame(frontier, retainedAssets.frontierArtBank());
        var animalBinding = frontier.binding(com.openggf.game.soniccd.level.CdR11aAsset.ART_ANIMALS);
        var sourceBank = retainedAssets.sectionArtSources();
        assertSame(sourceBank, retainedAssets.sectionArtSources()); assertEquals(17, sourceBank.sources().size());
        assertEquals(List.of(
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_PATA_BATA,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_ANTON,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_TAMABBOH,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_MOSQUI,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_3D_PLANT,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_3D_RAMP_BOOST,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_BOULDER,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_DIAGONAL_SPRING,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_SPIKES,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_CHECKPOINT,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_PLATFORM,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_ANIMALS,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_GOAL_POST,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_SONIC_HOLE,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_TUNNEL_WATERFALL,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_SPINNING_DISC,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_LOG_SHADOW),
                sourceBank.sources().stream().map(com.openggf.game.soniccd.level.CdR11aObjectAssets.SectionArtSource::source).toList());
        var animalSource = sourceBank.source(com.openggf.game.soniccd.level.CdR11aAsset.ART_ANIMALS);
        assertSame(sourceBank.sources().get(11), animalSource);
        assertSame(animalSource, sourceBank.source(actualLoadList.get(1)));
        assertEquals(2, animalSource.canonicalPlcId()); assertEquals(1, animalSource.canonicalRowIndex());
        assertEquals(plan.plcs().get(2).entries().get(1), animalSource.canonicalPlc());
        assertSame(animalBinding, frontier.binding(com.openggf.game.soniccd.level.CdR11aAsset.ART_ANIMALS));
        assertSame(animalBinding.decode(), animalSource.decode());
        assertEquals(446, animalSource.decode().sourceBlobRange().length());
        assertEquals(actualLoadList.get(1).sourceRange(), animalSource.decode().sourceBlobRange());
        assertEquals(animalSource.decode().sourceBlobRange(), animalSource.readerInputRange());
        assertEquals(445, animalSource.decode().readerInputRange().length());
        assertEquals(445, animalSource.decode().consumedInputBytes());
        assertEquals(446, animalSource.readerInputRange().length()); assertEquals(446, animalSource.readerBytes().length());
        assertEquals(0x0014, animalSource.readerBytes().unsignedWord(0));
        assertEquals("63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab",
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(animalSource.readerBytes().copy())));
        assertEquals(0, animalSource.readerBytes().unsignedByte(445), "Last actual Animals byte, not adjacent $80");
        assertEquals(640, animalBinding.bytes().length()); assertEquals(20, animalBinding.tileCount());
        assertEquals("ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1",
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(animalBinding.bytes().copy())));
        assertArrayEquals(animalSource.readerBytes().copy(), preparedAnimals.sourceBytes().copy());
        assertArrayEquals(preparedAnimals.codeTable().copy(), animalsReady.codeTable().copy());
        var boulder = sourceBank.source(com.openggf.game.soniccd.level.CdR11aAsset.ART_BOULDER);
        assertArrayEquals(boulder.decode().bytes().slice(18 * 32, 32).copy(),
                com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(animalsReady.residency(), 0x386).copy());
        var previousAnimalRegion = freshStates[6].objects().sectionArtState().residency().region(
                com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.AT_6E00);
        var readyAnimalRegion = animalsReady.residency().region(
                com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.AT_6E00);
        for (int offset = 0x300; offset < 0x580; offset++)
            assertEquals(previousAnimalRegion.knownByte(offset), readyAnimalRegion.knownByte(offset),
                    "CPU ProcessPLCs preparation publishes no Animals pixels");
        freshStates[7] = state(h); freshFull.add(registry.capture()); freshCourse.add(registry.captureCourse());

        // UNRUN positive oracle: ProcessPLCs (Functions (General).asm:664-695) prepares;
        // DecompPLCSlow/Main/Pop (:721-784) services 3/3/3/3/3/3/2 tiles, then CPU prepares the next head.
        for (int call = 8; call <= 14; call++) {
            var row = movie.getFrame(1946 + call);
            step(h, input(row.p1InputMask(), row.p1ActionMask(), row.p1StartPressed()));
            assertSame(fresh, objects(h)); assertSame(freshFrame, h.module().requirePlayerFrame());
            assertSame(freshActor, h.module().requirePlayerActor()); assertSame(world, h.mode().getWorldSession());
            assertEquals(generation + 1, level.getCompletedProductionLoadGeneration());
            assertFalse(fresh.closed()); assertFalse(freshFrame.closed());
            assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, fresh.phase());
            assertEquals(0x0200, fresh.sectionArtState().sectionWord());
            assertNaturalRestartCheckpoint(loaded0.objects().checkpointState(), h);
            assertEquals(call + 1, level.getFrameCounter());
            assertEquals(call + 1, freshFrame.environment().globals().levelFrames());
            assertEquals(call + 1, freshFrame.vIntClock().deltaSinceAdmittedCut());
            int completedTiles = Math.min((call - 7) * 3, 20);
            assertNaturalAnimalsPrefix(animalsReady, animalBinding, completedTiles, h);
            var art = fresh.sectionArtState(); var decoder = art.decoder().orElseThrow();
            if (call < 14) {
                assertEquals(20 - completedTiles, art.remainingTilesWord());
                assertEquals(20 - completedTiles, decoder.remainingTiles());
                assertEquals(0x2381C0L, decoder.nativeAddress()); assertEquals(0x0014, decoder.headerWord());
                assertEquals(0, art.processBudgetWord()); assertEquals(0x7100 + (call - 7) * 96, art.queue().headDestination());
                assertEquals(decoder.sourcePointer(), art.queue().headSource());
                assertArrayEquals(animalsReady.codeTable().copy(), art.codeTable().copy());
                assertEquals(com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer.NORMAL, art.savedDecoder().writer());
            } else {
                var nextSource = sourceBank.source(actualLoadList.get(2));
                assertEquals(com.openggf.game.soniccd.level.CdR11aAsset.ART_PATA_BATA, nextSource.source());
                assertEquals(50, art.remainingTilesWord()); assertEquals(50, decoder.remainingTiles());
                assertEquals(nextSource.readerInputRange().nativeAddress(), decoder.nativeAddress());
                assertArrayEquals(nextSource.readerBytes().copy(), decoder.sourceBytes().copy());
                assertEquals(0x7600, art.queue().headDestination()); assertEquals(2, art.processBudgetWord());
                assertEquals(decoder.sourcePointer(), art.queue().headSource());
                byte[] beforePop = freshStates[13].objects().sectionArtState().queue().bytes().copy();
                byte[] popped = beforePop.clone();
                for (int offset = 0; offset < 88; offset++) popped[offset] = beforePop[offset + 6];
                assertArrayEquals(Arrays.copyOfRange(popped, 4, 96), Arrays.copyOfRange(art.queue().bytes().copy(), 4, 96),
                        "FixBugs=0 88-byte POP; final eight descriptor bytes retain their prior values");
            }
            freshStates[call] = state(h);
            if (call == 10) { freshFull.add(registry.capture()); freshCourse.add(registry.captureCourse()); }
        }
        var forwardState = state(h);
        assertEquals(4, freshFull.size()); assertEquals(4, freshCourse.size());
        // Byte equality alone cannot prove a write/call count: carried bytes may already equal the cold seed.
        // Actual program, cursor/count, queue/table, ordinary services and authenticated binding own this proof.
        for (int cut = 0; cut < cutCalls.length; cut++) {
            for (boolean course : List.of(false, true)) {
                if (course) registry.restoreCourse(freshCourse.get(cut)); else registry.restore(freshFull.get(cut));
                assertState(freshStates[cutCalls[cut]], h); // Complete aggregate BEFORE the next ordinary forward step.
                assertNaturalRestartCheckpoint(freshStates[cutCalls[cut]].objects().checkpointState(), h);
                for (int call = cutCalls[cut] + 1; call <= 14; call++) {
                    var row = movie.getFrame(1946 + call);
                    step(h, input(row.p1InputMask(), row.p1ActionMask(), row.p1StartPressed()));
                    assertState(freshStates[call], h);
                    assertNaturalRestartCheckpoint(freshStates[call].objects().checkpointState(), h);
                }
                assertState(forwardState, h);
                assertNaturalRestartCheckpoint(forwardState.objects().checkpointState(), h);
                assertSame(naturalSaved.saved(), fresh.checkpointState().saved());
                assertSame(fresh, objects(h)); assertSame(freshFrame, h.module().requirePlayerFrame());
                assertSame(freshActor, h.module().requirePlayerActor()); assertSame(world, h.mode().getWorldSession());
                assertEquals(generation + 1, level.getCompletedProductionLoadGeneration());
            }
        }
        assertObjectRangeExactlyOnce();
        // Bounded stop at fresh14/global1961. No goal-post, full-USA, native parity/audio/title/HUD claim.
    }

    private static void assertNaturalRestartCheckpoint(CdOpeningObjectRuntime.CheckpointOwnerState expected, Harness h) {
        var actual = objects(h).checkpointState();
        assertEquals(expected, actual); assertSame(expected.saved(), actual.saved());
        assertArrayEquals(expected.saved().knownMask().copy(), actual.saved().knownMask().copy());
        for (var field : com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.values()) {
            assertEquals(expected.saved().read(field), actual.saved().read(field), field.name());
            for (int part = 0; part < field.width(); part++)
                assertEquals(expected.saved().knownByte(field.nativeAddress() + part),
                        actual.saved().knownByte(field.nativeAddress() + part), field.name());
        }
        assertEquals(expected.live(), actual.live());
        assertEquals(expected.live().knownMask(), actual.live().knownMask());
        for (var field : com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.values())
            for (int part = 0; part < field.width(); part++)
                assertEquals(expected.live().knownByte(field.nativeAddress() + part),
                        actual.live().knownByte(field.nativeAddress() + part), field.name());
        assertEquals(expected.saved().read(com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.TIME_LONG),
                actual.saved().read(com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.TIME_LONG));
        assertEquals(com.openggf.game.soniccd.objects.CdCheckpointLiveAux.read(expected.live(),
                        com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.TIME_LONG),
                com.openggf.game.soniccd.objects.CdCheckpointLiveAux.read(actual.live(),
                        com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.TIME_LONG));
    }

    private static void assertNaturalAnimalsPrefix(
            com.openggf.game.soniccd.resources.CdR11aSectionArt.State ready,
            com.openggf.game.soniccd.level.CdR11aObjectAssets.ArtBinding binding, int completedTiles, Harness h) {
        var current = objects(h).sectionArtState().residency();
        for (var region : com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.values()) {
            var before = ready.residency().region(region); var after = current.region(region);
            var beforeMask = before.knownMask(); var afterMask = after.knownMask();
            for (int offset = 0; offset < region.byteLength(); offset++) {
                boolean committed = region == com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.AT_6E00
                        && offset >= 0x300 && offset < 0x300 + completedTiles * 32;
                var expected = committed ? java.util.OptionalInt.of(binding.bytes().unsignedByte(offset - 0x300))
                        : before.knownByte(offset);
                assertEquals(expected, after.knownByte(offset), region.name() + " byte " + offset);
                assertEquals(committed ? 1 : beforeMask.unsignedByte(offset), afterMask.unsignedByte(offset));
            }
        }
        for (int tile = 0; tile < completedTiles; tile++)
            assertArrayEquals(binding.bytes().slice(tile * 32, 32).copy(),
                    com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(current, 0x388 + tile).copy());
    }

    /**
     * SOURCE-ONLY / UNRUN. Root observed source12 through global2005/fresh58; attempted
     * global2006/fresh59 failed UNKNOWN_SOURCE $2320DA/$9EE0 after Tamabboh's last
     * service/POP. Reproduce that route anew: never resume the failed call.
     * Fresh60..67 are conditional source-model oracles, not observed timing/parity.
     */
    @Test void controllerJumpRouteRestartsNaturallyAndServicesGoalPostWithFreshEpochReplay() throws Exception {
        GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_DETERMINISM_AUDIT, false);
        final String controllerScript = "1200 R;32 R+B;1200 R";
        var log = com.openggf.tools.InputScriptCompiler.compileToInputLog(controllerScript);
        var movie = new com.openggf.debug.playback.Bk2MovieLoader().parseInputLogText(
                Path.of("natural-checkpoint-goalpost-controller"), log.lines().toList());
        assertEquals(2432, movie.getFrameCount());
        assertEquals(0, movie.getFrame(1199).p1ActionMask());
        assertEquals(2, movie.getFrame(1200).p1ActionMask());
        assertEquals(2, movie.getFrame(1231).p1ActionMask());
        assertEquals(0, movie.getFrame(1232).p1ActionMask());

        var h = opening();
        var old = objects(h); var oldFrame = h.module().requirePlayerFrame();
        var coldSectionArt = old.sectionArtState();
        var oldActor = h.module().requirePlayerActor();
        var oldPlc = h.module().getGameService(PlcLifecycleService.class);
        var world = h.mode().getWorldSession(); var level = h.mode().getLevelManager();
        var oldLevel = level.getCurrentLevel();
        long generation = level.getCompletedProductionLoadGeneration();
        var registry = h.mode().getRewindRegistry();
        CdOpeningObjectRuntime.CheckpointOwnerState naturalSaved = null;
        int firstDead = -1, firstGone = -1, firstRestart = -1;
        for (int call = 1; call <= 1946; call++) {
            int previousCheckpoint = old.levelGlobals().checkpoint();
            var row = movie.getFrame(call - 1);
            step(h, input(row.p1InputMask(), row.p1ActionMask(), row.p1StartPressed()));
            assertSame(world, h.mode().getWorldSession());
            assertSame(level, h.mode().getLevelManager()); assertSame(oldLevel, level.getCurrentLevel());
            assertSame(old, objects(h)); assertSame(oldFrame, h.module().requirePlayerFrame());
            assertSame(oldActor, h.module().requirePlayerActor());
            assertSame(oldPlc, h.module().getGameService(PlcLifecycleService.class));
            assertEquals(generation, level.getCompletedProductionLoadGeneration());
            assertFalse(old.closed()); assertFalse(oldFrame.closed());
            assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, old.phase());
            if (previousCheckpoint != old.levelGlobals().checkpoint()) {
                assertEquals(649, call); assertEquals(0, previousCheckpoint);
                assertNull(naturalSaved);
                naturalSaved = old.checkpointState(); // Actual contact, before hurt/death; no SaveData call.
                assertEquals(2, old.levelGlobals().checkpoint()); assertEquals(1, old.levelGlobals().spawnMode());
                byte[] allSavedKnown = new byte[31]; Arrays.fill(allSavedKnown, (byte) 1);
                assertArrayEquals(allSavedKnown, naturalSaved.saved().knownMask().copy());
                assertEquals(1, naturalSaved.saved().read(
                        com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.SPAWN_MODE));
                assertEquals(oldFrame.state().motion().centreX16_16() >>> 16, naturalSaved.saved().read(
                        com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.PRIMARY_X));
                assertEquals(oldFrame.state().motion().centreY16_16() >>> 16, naturalSaved.saved().read(
                        com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.PRIMARY_Y));
                assertEquals(0, naturalSaved.saved().read(
                        com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.TIME_LONG));
                assertEquals(List.of(new CdOpeningObjectRuntime.CheckpointFm(
                                com.openggf.game.soniccd.objects.CdCheckpoint.FmSound.FM_CHECKPOINT_AE)),
                        old.lastSoundRequests().stream().filter(CdOpeningObjectRuntime.CheckpointFm.class::isInstance).toList());
            }
            if (naturalSaved != null) assertNaturalRestartCheckpoint(naturalSaved, h);
            if (call == 1200) {
                var actor = oldFrame.state();
                assertEquals(222748416, actor.motion().centreX16_16());
                assertEquals(48994048, actor.motion().centreY16_16());
                assertEquals(2, actor.motion().routine()); assertEquals(53, actor.objectLinks().pushObject());
                assertEquals(2, old.levelGlobals().checkpoint()); assertEquals(1, old.levelGlobals().spawnMode());
            }
            int routine = oldFrame.state().motion().routine();
            if (routine == 6 && firstDead < 0) firstDead = call;
            if (routine == 8 && firstGone < 0) firstGone = call;
            if (old.levelGlobals().levelRestart() != 0 && firstRestart < 0) firstRestart = call;
            if (call == 1802) {
                // ObjSonic_HurtChkLand -> KillPlayer: Sonic/Main.asm:2372-2376;
                // KillPlayer (PP Player Object Collision.asm:279-294) writes routine6, -$700, FM_HURT.
                assertEquals(6, routine); assertEquals(-1792, oldFrame.state().motion().yVelocity());
                assertEquals(List.of(new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(
                                com.openggf.game.soniccd.player.CdPlayerTouchResponse.FmSound.FM_HURT))),
                        old.lastSoundRequests().stream().filter(CdOpeningObjectRuntime.PlayerSound.class::isInstance).toList());
            }
            if (call == 1886) {
                // DeadChkGone (Sonic/Main.asm:2416-2444) precedes later live GameOver Init
                // (Game Over/Main.asm:27-49): routine8/reset60/life decrement, then SCMD_FADEPCM.
                assertEquals(8, routine); assertEquals(60, oldFrame.state().timers().reset());
                assertEquals(2, old.ringGlobals().lives());
                assertEquals(List.of(new CdOpeningObjectRuntime.GameOverSubCpu(
                                com.openggf.game.soniccd.objects.CdGameOver.SubCpuCommand.SCMD_FADEPCM)),
                        old.lastSoundRequests().stream().filter(CdOpeningObjectRuntime.GameOverSubCpu.class::isInstance).toList());
            }
            if (call == 1945) assertEquals(1, oldFrame.state().timers().reset());
            assertEquals(call == 1946 ? 1 : 0, old.levelGlobals().levelRestart());
        }
        assertNotNull(naturalSaved);
        assertEquals(1802, firstDead); assertEquals(1886, firstGone); assertEquals(1946, firstRestart);
        assertEquals(8, oldFrame.state().motion().routine()); assertEquals(0, oldFrame.state().timers().reset());
        assertEquals(2, old.levelGlobals().checkpoint()); assertEquals(1, old.levelGlobals().spawnMode());
        // ObjSonic_Restart (Sonic/Main.asm:2459-2491): ordered typed requests only, no driver/audio execution.
        assertEquals(List.of(
                        new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.DriverRamWrite(0x1C3E, 1)),
                        new CdOpeningObjectRuntime.PlayerSound(new CdPlayerFrameRuntime.BodySound(CdPlayerBody.Sound.SCMD_FADECDA))),
                old.lastSoundRequests().stream().filter(CdOpeningObjectRuntime.PlayerSound.class::isInstance).toList());
        oldActor.requireCompletedNativeFrame();
        var old1946 = state(h); var oldFull = registry.capture(); var oldCourse = registry.captureCourse();

        var loadRow = movie.getFrame(1946);
        step(h, input(loadRow.p1InputMask(), loadRow.p1ActionMask(), loadRow.p1StartPressed())); // Global1947.
        assertSame(world, h.mode().getWorldSession()); assertSame(level, h.mode().getLevelManager());
        assertNotSame(oldLevel, level.getCurrentLevel());
        assertEquals(generation + 1, level.getCompletedProductionLoadGeneration());
        var fresh = objects(h); var freshFrame = h.module().requirePlayerFrame();
        var freshActor = h.module().requirePlayerActor();
        assertNotSame(old, fresh); assertNotSame(oldFrame, freshFrame); assertNotSame(oldActor, freshActor);
        assertNotSame(oldPlc, h.module().getGameService(PlcLifecycleService.class));
        assertTrue(old.closed()); assertTrue(oldFrame.closed()); assertFalse(fresh.closed()); assertFalse(freshFrame.closed());
        assertSame(registry, h.mode().getRewindRegistry());
        assertEquals(1, level.getFrameCounter()); assertEquals(1, freshFrame.environment().globals().levelFrames());
        assertEquals(java.util.OptionalLong.of(0), freshFrame.vIntClock().absoluteSeed());
        assertEquals(1, freshFrame.vIntClock().deltaSinceAdmittedCut());
        var loadedMotion = freshFrame.state().motion();
        assertEquals(179765248, loadedMotion.centreX16_16()); assertEquals(48955392, loadedMotion.centreY16_16());
        assertEquals(0, loadedMotion.centreX16_16() & 0xFFFF); assertEquals(0, loadedMotion.centreY16_16() & 0xFFFF);
        assertEquals(2, loadedMotion.routine());
        assertEquals(new CdPlayerBody.ObjectLinks(0, 0), freshFrame.state().objectLinks());
        assertEquals(0, freshFrame.state().timers().hurt()); assertEquals(0, freshFrame.state().timers().reset());
        assertEquals(0, fresh.ringGlobals().rings()); assertEquals(2, fresh.ringGlobals().lives());
        assertEquals(7, fresh.ringGlobals().ringAnimationTimer()); assertEquals(1, fresh.ringGlobals().ringAnimationFrame());
        assertEquals(2, fresh.levelGlobals().checkpoint()); assertEquals(0, fresh.levelGlobals().spawnMode());
        assertEquals(0, fresh.levelGlobals().levelRestart()); assertEquals(0, fresh.levelGlobals().updateHudTime());
        assertEquals(3, fresh.levelGlobals().plcLoadFlags()); // Existing DEV title-loaded skip branch.
        assertEquals(2583, h.mode().getCamera().getX()); assertEquals(651, h.mode().getCamera().getY());
        assertSame(naturalSaved.saved(), fresh.checkpointState().saved());
        assertArrayEquals(naturalSaved.saved().knownMask().copy(), fresh.checkpointState().saved().knownMask().copy());
        assertEquals(naturalSaved.saved(), fresh.checkpointState().saved());
        // LoadCheckpointData (Load Saved Data.asm:49-67): restore raw0, frames=$3B,
        // then SUBQ.B seconds wraps to $FF. Main.SkipClear (:285-301) retains it before clearing spawn.
        assertEquals(0, fresh.checkpointState().saved().read(
                com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.TIME_LONG));
        assertEquals(0x0000FF3B, com.openggf.game.soniccd.objects.CdCheckpointLiveAux.read(
                fresh.checkpointState().live(), com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.TIME_LONG));
        assertEquals(naturalSaved.live().knownMask(), fresh.checkpointState().live().knownMask());

        var plan = freshFrame.assets().loadPlan();
        assertEquals(4, plan.sections().get(1).initialPlcId());
        var actualLoadList = plan.plcs().get(4).entries();
        assertEquals(6, actualLoadList.size());
        assertEquals(List.of(
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_BOULDER,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_ANIMALS,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_PATA_BATA,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_ANTON,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_TAMABBOH,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_GOAL_POST),
                actualLoadList.stream().map(com.openggf.game.soniccd.level.CdR11aLoadPlan.PlcEntry::source).toList());
        // LoadSectionArt (Section Art Load.asm:7-22), SectionInitPLCs:21-26 and PLC_Cam2_Full
        // (Data (Act 1 Present).asm:102-115), never canonical PLC2 forced over saved camera/section.
        var loadedArt = fresh.sectionArtState();
        assertEquals(0x0200, loadedArt.sectionWord()); assertEquals(19, loadedArt.remainingTilesWord());
        assertEquals(0x6E80, loadedArt.queue().headDestination());
        assertEquals(actualLoadList.getFirst().sourceRange().nativeAddress(),
                loadedArt.decoder().orElseThrow().nativeAddress());
        for (int row = 1; row < actualLoadList.size(); row++) {
            assertEquals(actualLoadList.get(row).sourceRange().nativeAddress(), loadedArt.queue().bytes().unsignedLong(row * 6));
            assertEquals(actualLoadList.get(row).vramAddress(), loadedArt.queue().bytes().unsignedWord(row * 6 + 4));
        }
        assertEquals(old1946.objects().sectionArtState().residency(), loadedArt.residency(),
                "Load/prepare carry the existing current bytes and knowledge; no Animals preload");
        assertArrayEquals(loadedArt.decoder().orElseThrow().codeTable().copy(), loadedArt.codeTable().copy());

        var loaded0 = state(h);
        // Exact existing Frame owner rejection, before any restore callback/partition mutation.
        var rejectedFull = assertThrows(IllegalArgumentException.class, () -> registry.restore(oldFull));
        assertEquals("Snapshot belongs to another CD runtime/source owner", rejectedFull.getMessage());
        assertState(loaded0, h); assertNaturalRestartCheckpoint(loaded0.objects().checkpointState(), h);
        var rejectedCourse = assertThrows(IllegalArgumentException.class, () -> registry.restoreCourse(oldCourse));
        assertEquals("Snapshot belongs to another CD runtime/source owner", rejectedCourse.getMessage());
        assertState(loaded0, h); assertNaturalRestartCheckpoint(loaded0.objects().checkpointState(), h);
        // Read only the actual fresh runtime's retained owner/cache. No openingObjects() reopen,
        // new bank/decode API, component ingress, or GoalPost/source13 lookup before fresh59.
        var objectsField = CdOpeningObjectRuntime.class.getDeclaredField("objects");
        objectsField.setAccessible(true);
        var retainedAssets = assertInstanceOf(com.openggf.game.soniccd.level.CdR11aObjectAssets.class,
                objectsField.get(fresh));
        var sourceBank = retainedAssets.sectionArtSources();
        var firstTwelve = List.copyOf(sourceBank.sources().subList(0, 12));
        var freshLevel = level.getCurrentLevel();
        var freshPlc = h.module().getGameService(PlcLifecycleService.class);
        State[] freshStates = new State[68];
        freshStates[0] = loaded0;
        int[] cutCalls = {0, 58, 59, 63, 66};
        var freshFull = new java.util.ArrayList<com.openggf.game.rewind.CompositeSnapshot>();
        var freshCourse = new java.util.ArrayList<com.openggf.game.rewind.CompositeSnapshot>();
        freshFull.add(registry.capture());
        freshCourse.add(registry.captureCourse());

        // ProcessPLCs prepares only; subsequent slow services choose3. These cuts are
        // source-derived assertions, not observations invented between Root's supplied cuts.
        // Actual PLC4 order: PLC_Cam2_Full (Data (Act 1 Present).asm:102-115).
        int[] preparedCalls = {0, 7, 14, 31, 39};
        int[] tileCounts = {19, 20, 50, 22, 59};
        int[] completedCalls = {7, 14, 31, 39, 59};
        long[] sourceAddresses = {0x2364C2L, 0x2381C0L, 0x237378L, 0x236E46L, 0x237950L};
        int[] destinations = {0x6E80, 0x7100, 0x7600, 0x8120, 0x8500};
        int producer = 0;
        var producerReady = loadedArt;
        for (int call = 1; call <= 58; call++) {
            var beforeArt = freshStates[call - 1].objects().sectionArtState();
            var row = movie.getFrame(1946 + call);
            step(h, input(row.p1InputMask(), row.p1ActionMask(), row.p1StartPressed()));
            assertSame(fresh, objects(h)); assertSame(freshFrame, h.module().requirePlayerFrame());
            assertSame(freshActor, h.module().requirePlayerActor());
            assertSame(freshPlc, h.module().getGameService(PlcLifecycleService.class));
            assertSame(world, h.mode().getWorldSession()); assertSame(level, h.mode().getLevelManager());
            assertSame(freshLevel, level.getCurrentLevel());
            assertEquals(generation + 1, level.getCompletedProductionLoadGeneration());
            assertFalse(fresh.closed()); assertFalse(freshFrame.closed());
            assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, fresh.phase());
            assertEquals(call + 1, level.getFrameCounter());
            assertEquals(call + 1, freshFrame.environment().globals().levelFrames());
            assertEquals(call + 1, freshFrame.vIntClock().deltaSinceAdmittedCut());
            assertEquals(0x0200, fresh.sectionArtState().sectionWord());
            assertNaturalRestartCheckpoint(loaded0.objects().checkpointState(), h);
            int completed = Math.min((call - preparedCalls[producer]) * 3, tileCounts[producer]);
            assertNaturalSectionPrefix(producerReady,
                    sourceBank.source(actualLoadList.get(producer)).decode().bytes(),
                    destinations[producer], completed, h);
            var art = fresh.sectionArtState();
            if (call == completedCalls[producer]) {
                byte[] popped = naturalSlowPop(beforeArt.queue().bytes().copy());
                assertArrayEquals(Arrays.copyOfRange(popped, 4, 96),
                        Arrays.copyOfRange(art.queue().bytes().copy(), 4, 96));
                int lastBatch = tileCounts[producer] % 3;
                assertEquals(4 - (lastBatch == 0 ? 3 : lastBatch), art.processBudgetWord());
                producer++;
                assertEquals(tileCounts[producer], art.remainingTilesWord());
                assertEquals(tileCounts[producer], art.decoder().orElseThrow().remainingTiles());
                assertEquals(sourceAddresses[producer], art.decoder().orElseThrow().nativeAddress());
                assertEquals(destinations[producer], art.queue().headDestination());
                assertEquals(art.decoder().orElseThrow().sourcePointer(), art.queue().headSource());
                producerReady = art;
            } else {
                assertEquals(tileCounts[producer] - completed, art.remainingTilesWord());
                assertEquals(art.remainingTilesWord(), art.decoder().orElseThrow().remainingTiles());
                assertEquals(sourceAddresses[producer], art.decoder().orElseThrow().nativeAddress());
                assertEquals(0, art.processBudgetWord());
                assertEquals(destinations[producer] + (call - preparedCalls[producer]) * 96,
                        art.queue().headDestination());
                assertEquals(art.decoder().orElseThrow().sourcePointer(), art.queue().headSource());
                assertArrayEquals(producerReady.codeTable().copy(), art.codeTable().copy());
            }
            freshStates[call] = state(h);
            if (call == 58) {
                freshFull.add(registry.capture());
                freshCourse.add(registry.captureCourse());
            }
        }
        assertEquals(4, producer);
        var tamabbohReady = producerReady;
        var beforePrepare = freshStates[58].objects().sectionArtState();
        assertEquals(2, beforePrepare.remainingTilesWord());
        assertEquals(0, beforePrepare.processBudgetWord());
        assertEquals(0x237950L, beforePrepare.decoder().orElseThrow().nativeAddress());

        // OLD12 RED must be this ordinary production step: no count13 assertion,
        // GoalPost lookup, catch/resume, new capture, or fitted replacement before it.
        var prepareRow = movie.getFrame(2005); // Global2006 = fresh59.
        step(h, input(prepareRow.p1InputMask(), prepareRow.p1ActionMask(), prepareRow.p1StartPressed()));

        var goalReady = fresh.sectionArtState();
        assertSame(fresh, objects(h)); assertSame(freshFrame, h.module().requirePlayerFrame());
        assertSame(freshActor, h.module().requirePlayerActor());
        assertSame(freshPlc, h.module().getGameService(PlcLifecycleService.class));
        assertSame(world, h.mode().getWorldSession()); assertSame(level, h.mode().getLevelManager());
        assertSame(freshLevel, level.getCurrentLevel());
        assertEquals(generation + 1, level.getCompletedProductionLoadGeneration());
        assertFalse(fresh.closed()); assertFalse(freshFrame.closed());
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, fresh.phase());
        assertEquals(60, level.getFrameCounter());
        assertEquals(60, freshFrame.environment().globals().levelFrames());
        assertEquals(60, freshFrame.vIntClock().deltaSinceAdmittedCut());
        assertNaturalRestartCheckpoint(loaded0.objects().checkpointState(), h);
        // The only fresh59 pixel effects are the final two Tamabboh tiles, before POP/prepare.
        // Across ALL regions this also proves no GoalPost bytes/masks or neighbor writes.
        assertNaturalSectionPrefix(tamabbohReady,
                sourceBank.source(actualLoadList.get(4)).decode().bytes(), 0x8500, 59, h);
        byte[] tamabbohPopped = naturalSlowPop(beforePrepare.queue().bytes().copy());
        assertArrayEquals(Arrays.copyOfRange(tamabbohPopped, 4, 96),
                Arrays.copyOfRange(goalReady.queue().bytes().copy(), 4, 96));
        assertEquals(0x0200, goalReady.sectionWord());
        assertEquals(0x9EE0, goalReady.queue().headDestination());
        assertEquals(0x232132L, goalReady.queue().headSource());
        assertEquals(2, goalReady.processBudgetWord());
        assertGoalPostDecoderCut(goalReady, 88, 0x12D7, 16, 0, 0, 0, 24);
        assertEquals(new com.openggf.game.soniccd.resources.CdR11aSectionArt.SavedDecoder(
                        com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer.XOR,
                        0, 0, 0, 0x12D7, 16), goalReady.savedDecoder());

        // Only AFTER actual fresh59 succeeds: append13, exact prior order/identities,
        // canonical2/row6 versus actually selected4/row5, and one retained decode.
        assertSame(sourceBank, retainedAssets.sectionArtSources());
        assertEquals(17, sourceBank.sources().size());
        assertEquals(List.of(
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_PATA_BATA,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_ANTON,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_TAMABBOH,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_MOSQUI,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_3D_PLANT,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_3D_RAMP_BOOST,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_BOULDER,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_DIAGONAL_SPRING,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_SPIKES,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_CHECKPOINT,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_PLATFORM,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_ANIMALS,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_GOAL_POST,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_SONIC_HOLE,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_TUNNEL_WATERFALL,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_SPINNING_DISC,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_LOG_SHADOW),
                sourceBank.sources().stream().map(com.openggf.game.soniccd.level.CdR11aObjectAssets.SectionArtSource::source).toList());
        for (int index = 0; index < 12; index++) {
            var prior = firstTwelve.get(index);
            assertSame(prior, sourceBank.sources().get(index));
            assertSame(prior, sourceBank.source(prior.source()));
            assertSame(prior.decode(), sourceBank.source(prior.source()).decode());
            assertSame(prior.readerBytes(), sourceBank.source(prior.source()).readerBytes());
        }
        var goalSource = sourceBank.source(com.openggf.game.soniccd.level.CdR11aAsset.ART_GOAL_POST);
        assertSame(sourceBank.sources().get(12), goalSource);
        assertSame(goalSource, sourceBank.source(actualLoadList.get(5)));
        assertSame(goalSource, sourceBank.source(plan.plcs().get(2).entries().get(6)));
        assertSame(goalSource.decode(),
                sourceBank.source(com.openggf.game.soniccd.level.CdR11aAsset.ART_GOAL_POST).decode());
        assertEquals(2, goalSource.canonicalPlcId()); assertEquals(6, goalSource.canonicalRowIndex());
        assertEquals(plan.plcs().get(2).entries().get(6), goalSource.canonicalPlc());
        assertEquals(0x2320DAL, actualLoadList.get(5).sourceRange().nativeAddress());
        assertEquals(0x9EE0, actualLoadList.get(5).vramAddress());
        var goalDecode = goalSource.decode();
        assertEquals(com.openggf.game.soniccd.level.CdR11aAsset.ART_GOAL_POST, goalDecode.asset());
        assertEquals(actualLoadList.get(5).sourceRange(), goalDecode.sourceBlobRange());
        assertEquals(448, goalDecode.sourceBlobRange().length());
        assertEquals(0x320DA, goalDecode.sourceBlobRange().fileOffset());
        assertEquals(goalDecode.sourceBlobRange(), goalDecode.readerInputRange());
        assertEquals(448, goalDecode.consumedInputBytes());
        assertEquals(449, goalSource.readerInputRange().length());
        assertEquals(449, goalSource.readerBytes().length());
        assertEquals(goalDecode.sourceBlobRange().file(), goalSource.readerInputRange().file());
        assertEquals(goalDecode.sourceBlobRange().domain(), goalSource.readerInputRange().domain());
        assertEquals(goalDecode.sourceBlobRange().fileOffset(), goalSource.readerInputRange().fileOffset());
        assertEquals(0x8018, goalSource.readerBytes().unsignedWord(0));
        assertEquals(0xC8, goalSource.readerBytes().unsignedByte(447));
        assertEquals(0x80, goalSource.readerBytes().unsignedByte(448), "Real adjacent Signpost byte, no padding");
        assertEquals("24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f",
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(goalSource.readerBytes().slice(0, 448).copy())));
        assertEquals("1ba1c7e9a34a071c8786cc2eb94eaf2adf0e58b480ef8d62f6501bb7f3006250",
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(goalSource.readerBytes().copy())));
        assertEquals(24, goalSource.tileCount()); assertEquals(768, goalDecode.bytes().length());
        assertEquals("68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a",
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(goalDecode.bytes().copy())));
        assertArrayEquals(goalSource.readerBytes().copy(), goalReady.decoder().orElseThrow().sourceBytes().copy());
        // The cold DEV branch ends Platform57 at9EE0 (Runtime:699-713). A source13
        // registration must not seed GoalPost; carry/ordinary preparation must retain UNKNOWN.
        var coldGoalRegion = coldSectionArt.residency().region(
                com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.AT_9000);
        var preparedGoalRegion = goalReady.residency().region(
                com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.AT_9000);
        for (int offset = 0xEE0; offset < 0x11E0; offset++) {
            assertEquals(java.util.OptionalInt.empty(), coldGoalRegion.knownByte(offset));
            assertEquals(0, coldGoalRegion.knownMask().unsignedByte(offset));
            assertEquals(coldGoalRegion.knownByte(offset), preparedGoalRegion.knownByte(offset));
            assertEquals(coldGoalRegion.knownMask().unsignedByte(offset),
                    preparedGoalRegion.knownMask().unsignedByte(offset));
        }
        assertTrue(Arrays.stream(com.openggf.game.soniccd.resources.CdObjectPresentation.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("usaR11aCurrentCameraDevPreloadedArt")
                        && method.getParameterCount() == 29));
        assertTrue(Arrays.stream(com.openggf.game.soniccd.resources.CdObjectPresentation.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("usaR11aCurrentCameraDevPreloadedArt")
                        && method.getParameterCount() == 30));
        freshStates[59] = state(h);
        freshFull.add(registry.capture()); freshCourse.add(registry.captureCourse());

        // ALL UNRUN: fixed report's native source-model table, not a zero-seed table hash.
        // DecompPLCSlow/Main/Pop (Functions (General).asm:721-785), FixBugs=0:
        // each nonfinal service saves low RAM fields; the final POP skips that store.
        int[][] vectors = {
                {124, 0x0FFB, 15, 0x0000, 0, 0x00000000},
                {182, 0x00C7, 16, 0x0000, 0, 0x00000000},
                {236, 0x0FC1, 11, 0x0000, 0, 0xEEE5EBBE},
                {291, 0x0F44, 13, 0x0000, 0, 0xBFBEB6FB},
                {349, 0xF7BB, 16, 0x0000, 0, 0xEE000000},
                {384, 0x0049, 13, 0x0006, 0, 0x00000000},
                {399, 0x16BA, 14, 0x0000, 0, 0x98766789},
                {449, 0xC880, 11, 0x0000, 9, 0x11111111}
        };
        int[] remaining = {21, 18, 15, 12, 9, 6, 3, 0};
        for (int call = 60; call <= 67; call++) {
            var beforeArt = freshStates[call - 1].objects().sectionArtState();
            var row = movie.getFrame(1946 + call);
            step(h, input(row.p1InputMask(), row.p1ActionMask(), row.p1StartPressed()));
            assertSame(fresh, objects(h)); assertSame(freshFrame, h.module().requirePlayerFrame());
            assertSame(freshActor, h.module().requirePlayerActor());
            assertSame(freshPlc, h.module().getGameService(PlcLifecycleService.class));
            assertSame(world, h.mode().getWorldSession()); assertSame(level, h.mode().getLevelManager());
            assertSame(freshLevel, level.getCurrentLevel());
            assertEquals(generation + 1, level.getCompletedProductionLoadGeneration());
            assertFalse(fresh.closed()); assertFalse(freshFrame.closed());
            assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, fresh.phase());
            assertEquals(call + 1, level.getFrameCounter());
            assertEquals(call + 1, freshFrame.environment().globals().levelFrames());
            assertEquals(call + 1, freshFrame.vIntClock().deltaSinceAdmittedCut());
            assertNaturalRestartCheckpoint(loaded0.objects().checkpointState(), h);
            var art = fresh.sectionArtState();
            int service = call - 59;
            int[] vector = vectors[service - 1];
            assertEquals(0x0200, art.sectionWord());
            assertGoalPostDecoderCut(art, vector[0], vector[1], vector[2], vector[3],
                    vector[4], vector[5], remaining[service - 1]);
            assertArrayEquals(goalReady.codeTable().copy(), art.codeTable().copy());
            assertNaturalSectionPrefix(goalReady, goalDecode.bytes(), 0x9EE0, service * 3, h);
            if (service < 8) {
                assertEquals(0, art.processBudgetWord());
                assertEquals(0x9EE0 + service * 96, art.queue().headDestination());
                assertEquals(art.decoder().orElseThrow().sourcePointer(), art.queue().headSource());
                assertEquals(new com.openggf.game.soniccd.resources.CdR11aSectionArt.SavedDecoder(
                                com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer.XOR,
                                vector[3], vector[4], vector[5], vector[1], vector[2]), art.savedDecoder());
            } else {
                assertEquals(1, art.processBudgetWord(), "Final tile branches to POP before budget decrement");
                assertEquals(freshStates[66].objects().sectionArtState().savedDecoder(), art.savedDecoder());
                assertEquals(new com.openggf.game.soniccd.resources.CdR11aSectionArt.SavedDecoder(
                                com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer.XOR,
                                0, 0, 0x98766789, 0x16BA, 14), art.savedDecoder());
                assertArrayEquals(naturalSlowPop(beforeArt.queue().bytes().copy()), art.queue().bytes().copy(),
                        "Actual pre-POP queue, preadvanceA1E0, copy88 and unchanged final8; no full96-zero assumption");
                assertArrayEquals(Arrays.copyOfRange(beforeArt.queue().bytes().copy(), 88, 96),
                        Arrays.copyOfRange(art.queue().bytes().copy(), 88, 96));
            }
            freshStates[call] = state(h); // Only after a successful complete ordinary call.
            if (call == 63 || call == 66) {
                freshFull.add(registry.capture()); freshCourse.add(registry.captureCourse());
            }
        }
        var forwardState = freshStates[67];
        assertEquals(cutCalls.length, freshFull.size()); assertEquals(cutCalls.length, freshCourse.size());
        // Save immutable publication/sound history BEFORE replay; do not draw or play requests.
        var pictureHistory = new java.util.ArrayList<SpritePresentation.Frame>();
        var soundHistory = new java.util.ArrayList<List<?>>();
        for (var completed : freshStates) {
            var picture = completed.picture();
            pictureHistory.add(new SpritePresentation.Frame(
                    picture.tiles(), picture.primitives(), picture.patternVersions()));
            soundHistory.add(List.copyOf(completed.objects().lastSoundRequests()));
        }
        assertGoalPostReplayState(forwardState, h);
        for (int cut = 0; cut < cutCalls.length; cut++) {
            for (boolean course : List.of(false, true)) {
                if (course) registry.restoreCourse(freshCourse.get(cut)); else registry.restore(freshFull.get(cut));
                // Complete actor/history, globals, queue/table/saved decoder, current pixels/masks,
                // checkpoint live/saved/knowledge/time, CPU SAT and prior picture BEFORE forwarding.
                assertGoalPostReplayState(freshStates[cutCalls[cut]], h);
                assertSame(fresh, objects(h)); assertSame(freshFrame, h.module().requirePlayerFrame());
                assertSame(freshActor, h.module().requirePlayerActor());
                assertSame(freshPlc, h.module().getGameService(PlcLifecycleService.class));
                assertSame(world, h.mode().getWorldSession()); assertSame(level, h.mode().getLevelManager());
                assertSame(freshLevel, level.getCurrentLevel());
                assertEquals(generation + 1, level.getCompletedProductionLoadGeneration());
                for (int call = cutCalls[cut] + 1; call <= 67; call++) {
                    var row = movie.getFrame(1946 + call);
                    step(h, input(row.p1InputMask(), row.p1ActionMask(), row.p1StartPressed()));
                    assertGoalPostReplayState(freshStates[call], h);
                    assertSame(fresh, objects(h)); assertSame(freshFrame, h.module().requirePlayerFrame());
                    assertSame(freshActor, h.module().requirePlayerActor());
                    assertSame(freshPlc, h.module().getGameService(PlcLifecycleService.class));
                    assertSame(world, h.mode().getWorldSession()); assertSame(level, h.mode().getLevelManager());
                    assertSame(freshLevel, level.getCurrentLevel());
                    assertEquals(generation + 1, level.getCompletedProductionLoadGeneration());
                }
                assertGoalPostReplayState(forwardState, h);
                assertSame(naturalSaved.saved(), fresh.checkpointState().saved());
                assertSame(sourceBank, retainedAssets.sectionArtSources());
                assertSame(goalDecode, sourceBank.source(actualLoadList.get(5)).decode());
                assertTrue(old.closed()); assertTrue(oldFrame.closed());
                for (int call = 0; call <= 67; call++) {
                    assertEquals(pictureHistory.get(call), freshStates[call].picture());
                    assertEquals(soundHistory.get(call), freshStates[call].objects().lastSoundRequests());
                }
            }
        }
        // Conditional bounded stop at fresh67/global2014 only if every ordinary call succeeds.
        // No whole-act, next frontier, GoalPost actor/picture, native parity, audio or HUD claim.
    }

    private static void assertNaturalSectionPrefix(
            com.openggf.game.soniccd.resources.CdR11aSectionArt.State ready,
            CdAssetBytes decoded, int destination, int completedTiles, Harness h) {
        var current = objects(h).sectionArtState().residency();
        for (var region : com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.values()) {
            var before = ready.residency().region(region); var after = current.region(region);
            var beforeMask = before.knownMask(); var afterMask = after.knownMask();
            for (int offset = 0; offset < region.byteLength(); offset++) {
                int address = region.destinationWord() + offset;
                boolean committed = address >= destination && address < destination + completedTiles * 32;
                var expected = committed ? java.util.OptionalInt.of(decoded.unsignedByte(address - destination))
                        : before.knownByte(offset);
                assertEquals(expected, after.knownByte(offset), region.name() + " byte " + offset);
                assertEquals(committed ? 1 : beforeMask.unsignedByte(offset), afterMask.unsignedByte(offset),
                        region.name() + " mask " + offset);
            }
        }
        for (int tile = 0; tile < completedTiles; tile++)
            assertArrayEquals(decoded.slice(tile * 32, 32).copy(),
                    com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(
                            current, destination / 32 + tile).copy());
        // With destination9EE0 this covers all24 GoalPost tiles in AT_9000 and preserves
        // Platform57 [97C0,9EE0), trailing17 [A1E0,A400), and every other physical region.
    }

    private static void assertGoalPostDecoderCut(
            com.openggf.game.soniccd.resources.CdR11aSectionArt.State art,
            int inputOffset, int readWord, int shiftWord, int repeatWord, int pixelNibble,
            int previousRow, int remainingTiles) {
        var decoder = art.decoder().orElseThrow();
        assertEquals(0x2320DAL, decoder.nativeAddress()); assertEquals(0x8018, decoder.headerWord());
        assertTrue(decoder.xorMode()); assertFalse(art.stopped());
        assertEquals(inputOffset, decoder.inputOffset());
        assertEquals(0x2320DAL + inputOffset, decoder.sourcePointer());
        assertEquals(449, decoder.sourceBytes().length());
        assertEquals(readWord, decoder.readWord()); assertEquals(shiftWord, decoder.shiftWord());
        assertEquals(repeatWord, decoder.repeatWord()); assertEquals(pixelNibble, decoder.pixelNibble());
        assertEquals(previousRow, decoder.previousRow());
        assertEquals(remainingTiles, decoder.remainingTiles()); assertEquals(remainingTiles, art.remainingTilesWord());
        assertArrayEquals(decoder.codeTable().copy(), art.codeTable().copy());
        assertEquals(com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer.XOR, art.savedDecoder().writer());
    }

    /** Detached byte oracle only; never applied as queue/decoder/gameplay ingress. */
    private static byte[] naturalSlowPop(byte[] before) {
        assertEquals(96, before.length);
        byte[] advanced = before.clone();
        int destination = (((Byte.toUnsignedInt(before[4]) << 8) | Byte.toUnsignedInt(before[5])) + 96) & 0xFFFF;
        advanced[4] = (byte) (destination >>> 8); advanced[5] = (byte) destination;
        byte[] popped = advanced.clone();
        // FixBugs=0 DecompPLC_Pop (Functions (General).asm:773-785) copies88, retains final8.
        for (int offset = 0; offset < 88; offset++) popped[offset] = advanced[offset + 6];
        return popped;
    }

    private static void assertGoalPostReplayState(State expected, Harness h) {
        assertState(expected, h);
        assertNaturalRestartCheckpoint(expected.objects().checkpointState(), h);
        var actual = state(h);
        assertSame(expected.shared().owner(), actual.shared().owner());
        assertEquals(expected.objects().displayedFrame(), actual.objects().displayedFrame());
        assertEquals(expected.objects().lastSoundRequests(), actual.objects().lastSoundRequests());
        assertFalse(objects(h).closed()); assertFalse(h.module().requirePlayerFrame().closed());
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, objects(h).phase());
    }

    /**
     * SOURCE-ONLY / UNRUN. ROOT-observed controller boundaries are assertions to be
     * executed afresh, not results of this authoring pass. Keep the original CP649,
     * third-jump/FM3002, first-ID21 discovery and pre3210 closed-row proof.
     * Hole's real service and Pata handoff precede the separately attempted ID22
     * dynamic-slot frontier. No failed-call resume or comparison-data ingress.
     */
    @Test void controllerThreeJumpRouteRunsTamabbohServicesQueuedArtAndStopsAtSpinningDiscSlot46() throws Exception {
        GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_DETERMINISM_AUDIT, false);
        final String controllerScript = "1200 R;32 R+B;537 R;32 R+B;1200 R;32 R+B;1200 R";
        var log = com.openggf.tools.InputScriptCompiler.compileToInputLog(controllerScript);
        var movie = new com.openggf.debug.playback.Bk2MovieLoader().parseInputLogText(
                Path.of("three-jump-natural-checkpoint-controller"), log.lines().toList());
        assertEquals(4233, movie.getFrameCount());
        for (int firstHeldCall : new int[]{1201, 1770, 3002}) {
            assertEquals(0, movie.getFrame(firstHeldCall - 2).p1ActionMask());
            assertEquals(2, movie.getFrame(firstHeldCall - 1).p1ActionMask());
            assertEquals(2, movie.getFrame(firstHeldCall + 30).p1ActionMask());
            assertEquals(0, movie.getFrame(firstHeldCall + 31).p1ActionMask());
        }

        var h = opening();
        // Retain all diagnostic owners BEFORE the eventual terminal call.
        var runtime = objects(h); var frame = h.module().requirePlayerFrame();
        var actorOwner = h.module().requirePlayerActor();
        var plcOwner = h.module().getGameService(PlcLifecycleService.class);
        var world = h.mode().getWorldSession(); var level = h.mode().getLevelManager();
        var loadedLevel = level.getCurrentLevel(); var registry = h.mode().getRewindRegistry();
        var pool = runtime.placement().pool();
        long generation = level.getCompletedProductionLoadGeneration();
        assertEquals(1L, generation, "One original USA World/load generation");
        int initialLevelFrame = level.getFrameCounter();
        int initialNativeFrame = frame.environment().globals().levelFrames();
        var initialClock = frame.vIntClock();
        var boundaries = new java.util.ArrayList<com.openggf.game.rewind.RewindBoundary>();
        h.mode().setRewindBoundaryReporter(boundaries::add);
        java.util.function.IntConsumer assertClosedAtCall = call -> {
            assertSame(world, h.mode().getWorldSession());
            assertSame(level, h.mode().getLevelManager()); assertSame(loadedLevel, level.getCurrentLevel());
            assertSame(runtime, objects(h)); assertSame(frame, h.module().requirePlayerFrame());
            assertSame(actorOwner, h.module().requirePlayerActor());
            assertSame(plcOwner, h.module().getGameService(PlcLifecycleService.class));
            assertSame(registry, h.mode().getRewindRegistry());
            assertEquals(generation, level.getCompletedProductionLoadGeneration());
            assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, runtime.phase());
            assertFalse(runtime.closed()); assertFalse(frame.closed());
            assertEquals(initialLevelFrame + call, level.getFrameCounter());
            assertEquals((initialNativeFrame + call) & 0xFFFF, frame.environment().globals().levelFrames());
            assertEquals(initialClock.absoluteSeed(), frame.vIntClock().absoluteSeed());
            assertEquals(initialClock.deltaSinceAdmittedCut() + call, frame.vIntClock().deltaSinceAdmittedCut());
            assertEquals(0, runtime.levelGlobals().levelRestart());
            assertTrue(boundaries.isEmpty(), "No load/reset boundary or fresh epoch in this recipe");
        };

        var assets = frame.assets(); var objectAssets = assets.openingObjects();
        var sourceBank = objectAssets.sectionArtSources(); var plan = assets.loadPlan();
        assertEquals(17, sourceBank.sources().size(), "Thirteen retained producers plus four reviewed appends");
        assertSame(sourceBank, objectAssets.sectionArtSources());
        assertEquals(List.of(
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_PATA_BATA,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_ANTON,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_TAMABBOH,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_MOSQUI,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_3D_PLANT,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_3D_RAMP_BOOST,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_BOULDER,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_DIAGONAL_SPRING,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_SPIKES,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_CHECKPOINT,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_PLATFORM,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_ANIMALS,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_GOAL_POST,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_SONIC_HOLE,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_TUNNEL_WATERFALL,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_SPINNING_DISC,
                        com.openggf.game.soniccd.level.CdR11aAsset.ART_LOG_SHADOW),
                sourceBank.sources().stream().map(com.openggf.game.soniccd.level.CdR11aObjectAssets.SectionArtSource::source).toList());
        assertEquals(9, com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.values().length);
        assertEquals(452, Arrays.stream(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.values())
                .mapToInt(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region::tileCount).sum());
        // Native UpdateSectionArt, Section Art Load.asm:26-46, reads UPDATE, not INIT.
        // SectionRanges/SectionUpdatePLCs/SectionInitPLCs (Section Data (Act 1 Present).asm:7-26):
        // selector4 -> update10, whereas a load at the same range would select initial5.
        var selectedSection = plan.sections().get(2);
        assertEquals(0x0F80, plan.sections().get(1).rightThresholdWord());
        assertEquals(0x1980, selectedSection.rightThresholdWord());
        assertEquals(5, selectedSection.initialPlcId()); assertEquals(10, selectedSection.updatePlcId());
        var selectedList = plan.plcs().get(selectedSection.updatePlcId());
        assertEquals(10, selectedList.nativeId());
        var selectedRows = selectedList.entries(); assertEquals(7, selectedRows.size());
        // PLC_Cam3_Incr (Data (Act 1 Present).asm:192-207), independent of canonical metadata.
        assertEquals(com.openggf.game.soniccd.level.CdR11aAsset.ART_BOULDER, selectedRows.get(0).source());
        assertEquals(0x6E80, selectedRows.get(0).vramAddress());
        assertEquals(com.openggf.game.soniccd.level.CdR11aAsset.ART_SONIC_HOLE, selectedRows.get(1).source());
        assertEquals(0x23AC08L, selectedRows.get(1).sourceRange().nativeAddress());
        assertEquals(0x7400, selectedRows.get(1).vramAddress());
        // openingObjects() creates a new owner on each call. Identity assertions below
        // concern this ONE cached owner; the actual runtime's independent reader is compared by bytes.
        var holeSource = sourceBank.source(selectedRows.get(1));
        var pataSource = sourceBank.source(selectedRows.get(2));
        assertSame(sourceBank.sources().get(13), holeSource);
        assertSame(holeSource, sourceBank.source(selectedRows.get(1)));
        assertSame(holeSource.decode(), sourceBank.source(selectedRows.get(1)).decode());
        assertSame(holeSource.readerBytes(), sourceBank.source(selectedRows.get(1)).readerBytes());
        assertEquals(5, holeSource.canonicalPlcId()); assertEquals(2, holeSource.canonicalRowIndex());
        assertEquals(plan.plcs().get(5).entries().get(2), holeSource.canonicalPlc());
        assertEquals(114, holeSource.decode().sourceBlobRange().length());
        assertEquals(113, holeSource.decode().readerInputRange().length());
        assertEquals(113, holeSource.decode().consumedInputBytes());
        assertEquals(114, holeSource.readerInputRange().length()); assertEquals(114, holeSource.readerBytes().length());
        assertEquals(14, holeSource.tileCount()); assertEquals(448, holeSource.decode().bytes().length());
        assertEquals(0x800E, holeSource.readerBytes().unsignedWord(0));
        assertEquals(0, holeSource.readerBytes().unsignedByte(113), "Authentic final source byte, not Plant padding");
        assertEquals("97622d18562de1a4b39b7d676a914fd7c266f11b9578b31cf91fa8fbecbe5c53",
                threeJumpSha256(holeSource.readerBytes().copy()));
        assertEquals("14e61d20ac8d71bc5c4ea75bab1ccca138b11b01a5f9e6974d3f126b5ba58788",
                threeJumpSha256(holeSource.readerBytes().slice(0, 113).copy()));
        var holeBytes = holeSource.decode().bytes();
        assertEquals("8038f15c0f2952ab7ce2c2e9bb2823a14888aab2e5d9d77146ed01ade5845399",
                threeJumpSha256(holeBytes.copy()));
        assertEquals(715, pataSource.readerBytes().length()); assertEquals(0x8032, pataSource.readerBytes().unsignedWord(0));
        int[] appendRows = {2, 4, 5, 6};
        for (int append = 0; append < appendRows.length; append++) {
            var source = sourceBank.sources().get(13 + append);
            assertEquals(5, source.canonicalPlcId()); assertEquals(appendRows[append], source.canonicalRowIndex());
            assertEquals(plan.plcs().get(5).entries().get(appendRows[append]), source.canonicalPlc());
            assertSame(source, sourceBank.source(selectedRows.get(append == 0 ? 1 : append + 2)));
        }
        var boulderSource = sourceBank.source(selectedRows.get(0));
        var boulderBytes = boulderSource.decode().bytes();
        assertEquals(19, boulderSource.tileCount()); assertEquals(608, boulderBytes.length());
        assertSame(boulderSource.decode(), sourceBank.source(selectedRows.get(0)).decode());
        var selectedDescriptors = ByteBuffer.allocate(96);
        for (var row : selectedRows) {
            selectedDescriptors.putInt((int) row.sourceRange().nativeAddress());
            selectedDescriptors.putShort((short) row.vramAddress());
        }
        byte[] selectedQueueBytes = selectedDescriptors.array(); // Detached comparison bytes only.

        CdOpeningObjectRuntime.CheckpointOwnerState naturalCheckpoint = null;
        com.openggf.game.soniccd.resources.CdR11aSectionArt.State boulderReady = null;
        int checkpointCall = -1, firstID21Contact = -1, firstID21Slot = -1;
        int[] cutCalls = {3001, -1, 3209};
        State[] cutStates = new State[3];
        var fullCuts = new com.openggf.game.rewind.CompositeSnapshot[3];
        var courseCuts = new com.openggf.game.rewind.CompositeSnapshot[3];
        State afterThirdJump = null;
        State[] replayRows = new State[3259]; // Only reached closed rows; never a failed3259 capture.
        List<?>[] precedingBodyRequests = new List<?>[3259];
        for (int call = 1; call <= 3210; call++) {
            int previousCheckpoint = runtime.levelGlobals().checkpoint();
            var row = movie.getFrame(call - 1);
            step(h, input(row.p1InputMask(), row.p1ActionMask(), row.p1StartPressed()));
            assertClosedAtCall.accept(call);
            if (previousCheckpoint != runtime.levelGlobals().checkpoint()) {
                assertEquals(-1, checkpointCall); assertEquals(649, call);
                assertEquals(0, previousCheckpoint); checkpointCall = call;
                naturalCheckpoint = runtime.checkpointState(); // Real pole contact; no checkpoint ingress.
                assertEquals(2, runtime.levelGlobals().checkpoint());
                assertEquals(1, runtime.levelGlobals().spawnMode());
                byte[] all31Known = new byte[31]; Arrays.fill(all31Known, (byte) 1);
                assertArrayEquals(all31Known, naturalCheckpoint.saved().knownMask().copy());
                assertEquals(1, naturalCheckpoint.saved().read(
                        com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.SPAWN_MODE));
                assertEquals(frame.state().motion().centreX16_16() >>> 16, naturalCheckpoint.saved().read(
                        com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.PRIMARY_X));
                assertEquals(frame.state().motion().centreY16_16() >>> 16, naturalCheckpoint.saved().read(
                        com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.PRIMARY_Y));
                assertEquals(0, naturalCheckpoint.saved().read(
                        com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.TIME_LONG));
                assertEquals(List.of(new CdOpeningObjectRuntime.CheckpointFm(
                                com.openggf.game.soniccd.objects.CdCheckpoint.FmSound.FM_CHECKPOINT_AE)),
                        runtime.lastSoundRequests().stream().filter(CdOpeningObjectRuntime.CheckpointFm.class::isInstance).toList());
            }
            if (naturalCheckpoint != null) {
                assertSame(naturalCheckpoint.saved(), runtime.checkpointState().saved());
                assertEquals(naturalCheckpoint.saved(), runtime.checkpointState().saved());
                assertArrayEquals(naturalCheckpoint.saved().knownMask().copy(),
                        runtime.checkpointState().saved().knownMask().copy());
            }
            if (call == 1201 || call == 1770 || call == 3002) {
                assertEquals(1L, runtime.lastSoundRequests().stream()
                        .filter(request -> request instanceof CdOpeningObjectRuntime.PlayerSound player
                                && player.sound() instanceof CdPlayerFrameRuntime.MovementSound movement
                                && movement.sound() == com.openggf.game.soniccd.player.CdPlayerMovement.Sound.FM_JUMP).count());
            }
            if (call == 1770) assertEquals(-1664, frame.state().motion().yVelocity());
            if (call == 1802) {
                assertEquals(2, frame.state().motion().routine(), "Second jump survives the old short-recipe kill cut");
                assertEquals(2, frame.state().motion().flags() & 2, "Actually airborne and alive");
            }

            var player = frame.state(); int stand = player.objectLinks().standObject();
            if (firstID21Contact < 0 && (player.motion().flags() & 8) != 0 && stand >= 32 && stand < 128
                    && pool.state(stand).id() == 0x21 && (pool.state(stand).flags() & 8) != 0) {
                assertThreeJumpNativeTop(player, pool.state(stand), stand);
                firstID21Contact = call; firstID21Slot = stand;
                System.out.println("THREE_JUMP_FIRST_ID21_CONTACT ordinaryCall=" + call + " slot=" + stand
                        + " raw=" + java.util.HexFormat.of().formatHex(pool.state(stand).copyBytes()));
            }

            var art = runtime.sectionArtState();
            if (call == 3001) {
                // Supplied ROOTMEASURED closed3001. Raw ID19 is checked, not classified from push link71.
                assertEquals(244782336, player.motion().centreX16_16());
                assertEquals(44815360, player.motion().centreY16_16());
                assertEquals(2, player.motion().routine()); assertEquals(32, player.motion().flags());
                assertEquals(0, player.motion().xVelocity()); assertEquals(0, player.motion().yVelocity());
                assertEquals(0, player.motion().groundVelocity());
                assertEquals(71, player.objectLinks().pushObject()); assertEquals(0, stand);
                assertArrayEquals(java.util.HexFormat.of().parseHex(
                        "198405a80020aa4e0eb0000002af00000000000000000e0e030f0208020200004600205602000000020000000000000000000000000000000000000000000000"),
                        pool.state(71).copyBytes());
                assertEquals(3575, h.mode().getCamera().getX()); assertEquals(587, h.mode().getCamera().getY());
                assertEquals(0x0200, art.sectionWord());
                assertEquals(0, art.remainingTilesWord()); assertEquals(0L, art.queue().headSource());
            }
            if (call == 3002) {
                // ObjSonic.CheckJump (Main.asm:1779-1861): flat grounded push releases into AIR/ROLL,
                // clears pushing, sets playerJump and emits the same native FM_JUMP just asserted.
                assertEquals(0, cutStates[0].actor().state().motion().angle());
                assertEquals(0, cutStates[0].actor().state().motion().flags() & 6);
                assertEquals(0x20, cutStates[0].actor().state().motion().flags() & 0x20);
                assertEquals(2, player.motion().routine()); assertEquals(6, player.motion().flags() & 0x26);
                assertEquals(1, player.motion().playerJump()); assertEquals(-0x680, player.motion().yVelocity());
                afterThirdJump = state(h);
            }
            if (call > 3001 && art.sectionWord() == 0x0400 && boulderReady == null) {
                boulderReady = art; // Actual completed CPU prepare; no reconstruction of a private State.
                assertEquals(19, art.remainingTilesWord()); assertEquals(0, art.processBudgetWord());
                assertEquals(0x6E80, art.queue().headDestination());
                assertEquals(selectedRows.get(0).sourceRange().nativeAddress(), art.decoder().orElseThrow().nativeAddress());
                assertEquals(0x8013, art.decoder().orElseThrow().headerWord());
                assertArrayEquals(Arrays.copyOfRange(selectedQueueBytes, 6, 96),
                        Arrays.copyOfRange(art.queue().bytes().copy(), 6, 96));
            }
            boolean partialBoulder = call > 3001 && cutCalls[1] < 0 && art.sectionWord() == 0x0400
                    && art.remainingTilesWord() > 1 && art.remainingTilesWord() < 19
                    && art.decoder().isPresent()
                    && art.decoder().orElseThrow().nativeAddress() == selectedRows.get(0).sourceRange().nativeAddress();
            if (partialBoulder) {
                assertNotNull(boulderReady); cutCalls[1] = call;
                assertThreeJumpBoulderPrefix(boulderReady, art, boulderBytes, 19 - art.remainingTilesWord());
                System.out.println("THREE_JUMP_PARTIAL_BOULDER_CUT ordinaryCall=" + call
                        + " remaining=" + art.remainingTilesWord());
            }
            for (int cut = 0; cut < cutCalls.length; cut++) {
                if (call == cutCalls[cut]) {
                    assertNotNull(naturalCheckpoint);
                    cutStates[cut] = state(h);
                    assertNaturalRestartCheckpoint(cutStates[cut].objects().checkpointState(), h);
                    fullCuts[cut] = registry.capture(); courseCuts[cut] = registry.captureCourse();
                }
            }
            if (call == 3002 || (cutCalls[1] > 0 && call >= cutCalls[1])) replayRows[call] = state(h);
            precedingBodyRequests[call] = List.copyOf(actorOwner.lastSoundRequests());
        }
        assertEquals(649, checkpointCall); assertNotNull(naturalCheckpoint);
        assertTrue(firstID21Contact > 0 && firstID21Contact <= 3210,
                "Record the real first contact; no fitted first-visit or first-standing timeline");
        assertTrue(firstID21Slot >= 32 && firstID21Slot < 128);
        assertTrue(cutCalls[1] > 3001 && cutCalls[1] < 3210, "A real partial-Boulder closed cut is required");
        assertNotNull(afterThirdJump); assertNotNull(boulderReady);
        for (int cut = 0; cut < cutCalls.length; cut++) {
            assertNotNull(cutStates[cut]); assertNotNull(fullCuts[cut]); assertNotNull(courseCuts[cut]);
        }

        // Supplied ROOTMEASURED closed3210, with a genuine ID21 rather than a stale stand byte.
        assertThreeJumpNativeTop(frame.state(), pool.state(36), 36);
        var platform3210 = pool.state(36);
        assertEquals(0x21, platform3210.id()); assertEquals(2, platform3210.routine());
        assertEquals(0x15, platform3210.subtype()); assertEquals(0, platform3210.subtype2());
        assertEquals(0, platform3210.savedFlagsId());
        assertEquals(0x20CCC8, platform3210.longBits(CdObjectState.LongField.MAPPINGS));
        assertEquals(0x44BE, platform3210.word(CdObjectState.WordField.TILE));
        assertEquals(4139, platform3210.centreX()); assertEquals(157, platform3210.centreY());
        assertArrayEquals(java.util.HexFormat.of().parseHex(
                "218444be0020ccc8102b0000009d00000000000000000800022001000000000000000800020000001500bd0005030000000000000000009b105b009b00000000"),
                platform3210.copyBytes());
        var at3210 = state(h); var beforeLastTile = runtime.sectionArtState();
        assertEquals(0x0400, beforeLastTile.sectionWord());
        assertEquals(1, beforeLastTile.remainingTilesWord()); assertEquals(0, beforeLastTile.processBudgetWord());
        assertEquals(0x2365C5L, beforeLastTile.queue().headSource());
        assertEquals(0x70C0, beforeLastTile.queue().headDestination());
        byte[] queue3210 = new byte[96];
        byte[] queuePrefix = java.util.HexFormat.of().parseHex(
                "002365c570c00023ac087400002373787600002368127c800023676c812000235ec08300002379508500");
        System.arraycopy(queuePrefix, 0, queue3210, 0, queuePrefix.length);
        assertArrayEquals(queue3210, beforeLastTile.queue().bytes().copy());
        assertThreeJumpBoulderPrefix(boulderReady, beforeLastTile, boulderBytes, 18);
        assertNaturalRestartCheckpoint(at3210.objects().checkpointState(), h);

        // Independent component-only native114 completion, not a runtime final-Hole
        // decoder assertion after Pata has replaced it. No component State enters gameplay.
        var expectedHoleTable = threeJumpExpectedCodeTable(beforeLastTile.codeTable(), holeSource.readerBytes());
        assertEquals("db7573fbc01333b4efb150f8c7b81e85ab7b9358cebd314c3c53da1771b9a52c",
                threeJumpSha256(threeJumpExpectedCodeTable(CdAssetBytes.copyOf(new byte[512]),
                        holeSource.readerBytes()).copy())); // Conditional zero-seed oracle ONLY.
        var componentHole = com.openggf.game.soniccd.resources.CdNemesisPlcDecoder.prepare(
                holeSource.readerBytes(), 0x23AC08L, beforeLastTile.codeTable());
        assertArrayEquals(expectedHoleTable.copy(), componentHole.codeTable().copy());
        byte[] componentHoleOutput = new byte[448];
        for (int tile = 0; tile < 14; tile++) {
            var decoded = com.openggf.game.soniccd.resources.CdNemesisPlcDecoder.decodeTile(componentHole);
            System.arraycopy(decoded.bytes().copy(), 0, componentHoleOutput, tile * 32, 32);
            componentHole = decoded.state();
        }
        assertArrayEquals(holeBytes.copy(), componentHoleOutput);
        assertEquals("8038f15c0f2952ab7ce2c2e9bb2823a14888aab2e5d9d77146ed01ade5845399",
                threeJumpSha256(componentHoleOutput));
        assertEquals(114, componentHole.inputOffset()); assertEquals(0x23AC7AL, componentHole.sourcePointer());
        assertEquals(0, componentHole.remainingTiles()); assertEquals(0xA000, componentHole.readWord());
        assertEquals(12, componentHole.shiftWord()); assertEquals(0, componentHole.repeatWord());
        assertEquals(0, componentHole.pixelNibble()); assertEquals(0x00001110, componentHole.previousRow());

        int[] laterCutCalls = {3210, 3211, 3213, 3215, 3216, 3220, 3221, 3222, 3233, 3243, 3247, 3253, 3257, 3258};
        State[] laterCutStates = new State[laterCutCalls.length];
        var laterFullCuts = new com.openggf.game.rewind.CompositeSnapshot[laterCutCalls.length];
        var laterCourseCuts = new com.openggf.game.rewind.CompositeSnapshot[laterCutCalls.length];
        laterCutStates[0] = at3210;
        laterFullCuts[0] = registry.capture(); laterCourseCuts[0] = registry.captureCourse();
        com.openggf.game.soniccd.resources.CdR11aSectionArt.State holeReady = null, pataReady = null;
        CdAssetBytes expectedPataTable = null;
        // Native low-field vectors independently pinned in the mandatory Hole report.
        int[][] holeSlowCuts = {
                {11, 48, 0x7460, 0x2D25, 10, 1, 0, 0x10111111},
                {8, 65, 0x74C0, 0x9F00, 13, 0, 0, 0x11111000},
                {5, 84, 0x7520, 0xD9F3, 9, 0, 0, 0x00000111},
                {2, 104, 0x7580, 0x1D78, 14, 0, 0, 0x10000000}
        };
        for (int call = 3211; call <= 3221; call++) {
            var row = movie.getFrame(call - 1);
            step(h, input(row.p1InputMask(), row.p1ActionMask(), row.p1StartPressed()));
            assertClosedAtCall.accept(call);
            assertSame(naturalCheckpoint.saved(), runtime.checkpointState().saved());
            assertNaturalRestartCheckpoint(naturalCheckpoint, h);
            var art = runtime.sectionArtState();
            assertFalse(art.stopped()); assertEquals(0x0400, art.sectionWord());
            assertEquals(beforeLastTile.hintWord(), art.hintWord()); assertEquals(beforeLastTile.padWord(), art.padWord());
            if (call == 3211) {
                holeReady = art;
                assertThreeJumpDecoder(art, holeSource, 0x800E, 14, 33, 0x00AD, 16, 0, 0, 0);
                assertEquals(3, art.processBudgetWord()); assertEquals(0x7400, art.queue().headDestination());
                assertArrayEquals(threeJumpQueueHead(naturalSlowPop(beforeLastTile.queue().bytes().copy()),
                                0x23AC29L, 0x7400), art.queue().bytes().copy());
                assertArrayEquals(expectedHoleTable.copy(), art.codeTable().copy());
                assertThreeJumpSavedDecoder(art);
                // Boulder tile19 and native88 POP completed before CPU Hole prepare.
                // This all-region comparison also proves that preparing Hole wrote no Hole or Pata tile.
                assertThreeJumpBoulderPrefix(boulderReady, art, boulderBytes, 19);
                assertArrayEquals(boulderBytes.slice(18 * 32, 32).copy(),
                        com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(
                                art.residency(), 0x70C0 / 32).copy());
            } else if (call <= 3215) {
                assertNotNull(holeReady);
                int[] vector = holeSlowCuts[call - 3212];
                assertThreeJumpDecoder(art, holeSource, 0x800E, vector[0], vector[1],
                        vector[3], vector[4], vector[5], vector[6], vector[7]);
                assertSame(holeReady.decoder().orElseThrow().sourceBytes(), art.decoder().orElseThrow().sourceBytes());
                assertEquals(0, art.processBudgetWord()); assertEquals(vector[2], art.queue().headDestination());
                assertArrayEquals(threeJumpQueueHead(holeReady.queue().bytes().copy(),
                                0x23AC08L + vector[1], vector[2]), art.queue().bytes().copy());
                assertArrayEquals(expectedHoleTable.copy(), art.codeTable().copy());
                assertThreeJumpSavedDecoder(art);
                assertThreeJumpTransferPrefix(holeReady, art, holeBytes, 0x7400, 14 - vector[0]);
            } else if (call == 3216) {
                assertNotNull(holeReady);
                var beforeHolePop = replayRows[3215].objects().sectionArtState();
                // VInt writes only the final two Hole tiles then POPs. The ordinary
                // body/tail prepares Pata; the runtime decoder now belongs to Pata.
                assertThreeJumpTransferPrefix(holeReady, art, holeBytes, 0x7400, 14);
                assertThreeJumpHoleCurrent(art, holeBytes);
                assertArrayEquals(threeJumpQueueHead(naturalSlowPop(beforeHolePop.queue().bytes().copy()),
                                0x2373CFL, 0x7600), art.queue().bytes().copy());
                assertEquals(2, art.processBudgetWord());
                assertThreeJumpDecoder(art, pataSource, 0x8032, 50, 87,
                        pataSource.readerBytes().unsignedWord(85), 16, 0, 0, 0);
                expectedPataTable = threeJumpExpectedCodeTable(expectedHoleTable, pataSource.readerBytes());
                assertArrayEquals(expectedPataTable.copy(), art.codeTable().copy());
                assertThreeJumpSavedDecoder(art);
                pataReady = art; // Actual completed handoff; no state construction, restore or cold seed.
            } else {
                assertNotNull(pataReady); assertNotNull(expectedPataTable);
                int completed = (call - 3216) * 3;
                var decoder = art.decoder().orElseThrow();
                assertEquals(0x237378L, decoder.nativeAddress()); assertEquals(0x8032, decoder.headerWord());
                assertTrue(decoder.xorMode());
                assertEquals(50 - completed, art.remainingTilesWord()); assertEquals(50 - completed, decoder.remainingTiles());
                assertEquals(0, art.processBudgetWord()); assertEquals(0x7600 + completed * 32, art.queue().headDestination());
                assertArrayEquals(threeJumpQueueHead(pataReady.queue().bytes().copy(),
                                decoder.sourcePointer(), 0x7600 + completed * 32), art.queue().bytes().copy());
                assertArrayEquals(pataSource.readerBytes().copy(), decoder.sourceBytes().copy());
                assertSame(pataReady.decoder().orElseThrow().sourceBytes(), decoder.sourceBytes());
                assertArrayEquals(expectedPataTable.copy(), art.codeTable().copy());
                assertThreeJumpSavedDecoder(art);
                // Includes every known byte/mask outside the Pata prefix: all Hole14,
                // Waterfall/Disc/Shadow destinations and the remaining Pata tail retain their actual prior state.
                assertThreeJumpTransferPrefix(pataReady, art, pataSource.decode().bytes(), 0x7600, completed);
                assertThreeJumpHoleCurrent(art, holeBytes);
            }
            replayRows[call] = state(h);
            precedingBodyRequests[call] = List.copyOf(actorOwner.lastSoundRequests());
            for (int cut = 1; cut < laterCutCalls.length; cut++) {
                if (call == laterCutCalls[cut]) {
                    laterCutStates[cut] = replayRows[call];
                    assertNaturalRestartCheckpoint(laterCutStates[cut].objects().checkpointState(), h);
                    laterFullCuts[cut] = registry.capture(); laterCourseCuts[cut] = registry.captureCourse();
                }
            }
        }
        assertNotNull(holeReady); assertNotNull(pataReady); assertNotNull(expectedPataTable);
        var at3221 = replayRows[3221];
        assertThreeJumpDecoder(at3221.objects().sectionArtState(), pataSource, 0x8032, 35, 272,
                0x0000, 9, 4, 0, 0);
        assertEquals(0x237488L, at3221.objects().sectionArtState().queue().headSource());
        assertEquals(0x77E0, at3221.objects().sectionArtState().queue().headDestination());
        assertEquals(277274880, frame.state().motion().centreX16_16());
        assertEquals(9040896, frame.state().motion().centreY16_16());
        assertEquals(1536, frame.state().motion().xVelocity()); assertEquals(448, frame.state().motion().yVelocity());
        assertEquals(1536, frame.state().motion().groundVelocity()); assertEquals(2, frame.state().motion().routine());
        assertEquals(2, frame.state().motion().flags()); assertEquals(0, frame.state().objectLinks().standObject());

        // Source-only promotion: Root OBSERVED job57473, 05:44:09Z, ordinary cuts1..3258
        // in generation1; no execution by this author. Continue the SAME controller/World.
        // The actual cached NemesisRead products are comparison bytes, never gameplay input.
        var waterfallSource = sourceBank.source(selectedRows.get(3));
        var discSource = sourceBank.source(selectedRows.get(4));
        var shadowSource = sourceBank.source(selectedRows.get(5));
        var tamSource = sourceBank.source(selectedRows.get(6));
        assertSame(sourceBank.sources().get(2), tamSource);
        assertEquals(9, tamSource.canonicalPlcId()); assertEquals(2, tamSource.canonicalRowIndex());
        assertEquals(plan.plcs().get(9).entries().get(2), tamSource.canonicalPlc());
        int[] canonicalPlcs = {9,9,9,2,2,2,2,1,1,0,1,2,2,5,5,5,5};
        int[] canonicalRows = {0,1,2,2,4,5,0,5,0,1,6,1,6,2,4,5,6};
        for (int index = 0; index < 17; index++) {
            var source = sourceBank.sources().get(index);
            assertEquals(canonicalPlcs[index], source.canonicalPlcId());
            assertEquals(canonicalRows[index], source.canonicalRowIndex());
            assertEquals(plan.plcs().get(canonicalPlcs[index]).entries().get(canonicalRows[index]), source.canonicalPlc());
            assertSame(source, sourceBank.source(source.source()));
            assertSame(source.decode(), sourceBank.source(source.source()).decode());
            assertSame(source.readerBytes(), sourceBank.source(source.source()).readerBytes());
        }
        // Mandatory A's independently measured immutable outputs; no production-State decode replay.
        var neighbours = List.of(waterfallSource, discSource, shadowSource);
        int[] nativeLengths = {328,167,24}, javaLengths = {327,167,23};
        int[] neighbourTiles = {29,12,16}, neighbourHeaders = {0x801D,0x800C,0x0010};
        String[] readerHashes = {
                "08d33432bf01cd85abc8653cba9b9901bd7f305932234685bc348ce014192d8e",
                "2ffa2baba308902cc6db0c11ac238ffe47c08fd1737d0fa6fd64967adc649c6d",
                "025a909ae3b46a5005c04784773e032147799851c0fe4a105a9c0ee9e3279b19"};
        String[] outputHashes = {
                "f3cc6438669479cd3bb36fa0f6cb176ed514865e9ec06d19ee0a5b80d993a8c6",
                "97ff27ee8b9ef0f8903cf420bafd651f954e4dd1f3468bd71e829dc932f24e86",
                "fa208fd33608e8a21ed13a7c9a92cdbbd6a936acd1a377f4ac10e9d333113866"};
        for (int index = 0; index < neighbours.size(); index++) {
            var source = neighbours.get(index);
            assertEquals(nativeLengths[index], source.readerBytes().length());
            assertEquals(nativeLengths[index], source.readerInputRange().length());
            assertEquals(javaLengths[index], source.decode().readerInputRange().length());
            assertEquals(javaLengths[index], source.decode().consumedInputBytes());
            assertEquals(neighbourTiles[index], source.tileCount());
            assertEquals(neighbourHeaders[index], source.readerBytes().unsignedWord(0));
            assertEquals(readerHashes[index], threeJumpSha256(source.readerBytes().copy()));
            assertEquals(outputHashes[index], threeJumpSha256(source.decode().bytes().copy()));
        }
        assertEquals(0x80, discSource.readerBytes().unsignedByte(166),
                "Authenticated borrowed Waterfall header byte, not invented padding");
        assertEquals(1143, tamSource.readerBytes().length());
        assertEquals(1142, tamSource.decode().consumedInputBytes()); assertEquals(59, tamSource.tileCount());
        assertEquals(0x803B, tamSource.readerBytes().unsignedWord(0));
        var queuedSources = List.of(pataSource, waterfallSource, discSource, shadowSource, tamSource);
        int[] preparedCalls = {3216,3233,3243,3247,3253};
        int[] queuedTiles = {50,29,12,16,59}, destinations = {0x7600,0x7C80,0x8120,0x8300,0x8500};
        int[] headers = {0x8032,0x801D,0x800C,0x0010,0x803B};
        int[] preparedOffsets = {87,39,48,8,91};
        long[] bases = {0x237378L,0x236812L,0x23676CL,0x235EC0L,0x237950L};
        var stageReady = new com.openggf.game.soniccd.resources.CdR11aSectionArt.State[5];
        stageReady[0] = pataReady;
        var stageTables = new CdAssetBytes[5]; stageTables[0] = expectedPataTable;
        var banks = (CdOpeningObjectRuntime.LogicalBanks) threeJumpReadField(runtime, "banks");
        var frozenAliases = new com.openggf.game.soniccd.resources.CdObjectPresentation.PatternSource[5];
        frozenAliases[0] = threeJumpCurrentAliases(replayRows[3216], objectAssets, banks, pataReady.residency());
        var old3221Placement = at3221.objects().placement();
        assertEquals(3968, old3221Placement.status().previousChunkWord());
        assertEquals(207, old3221Placement.status().rightRecordIndex());
        assertEquals(171, old3221Placement.status().leftRecordIndex());
        assertEquals(101, old3221Placement.status().rightSavedId());
        assertEquals(89, old3221Placement.status().leftSavedId());
        assertArrayEquals(new byte[64], pool.state(40).copyBytes());
        int period = frame.environment().movement().gates().timeZone() & 0x7F;
        assertEquals(1, period);
        var tamPlacementExpected = threeJumpExpectedPlacement(old3221Placement,
                runtime.placement().placements().records(), period, 4096, 211, 181);
        assertArrayEquals(java.util.HexFormat.of().parseHex(
                "22000000000000001210000002110000000000000000000000000000000000000000006500000000000000000000000000000000000000000000000000000000"),
                tamPlacementExpected.slots()[40 - 32]);
        var comparisonTerrain = new com.openggf.game.soniccd.player.CdPlayerTerrain(assets.terrain());
        int stage = 0;
        for (int call = 3222; call <= 3258; call++) {
            var priorArt = runtime.sectionArtState();
            var row = movie.getFrame(call - 1);
            step(h, input(row.p1InputMask(), row.p1ActionMask(), row.p1StartPressed()));
            assertClosedAtCall.accept(call);
            assertSame(naturalCheckpoint.saved(), runtime.checkpointState().saved());
            assertNaturalRestartCheckpoint(naturalCheckpoint, h);
            var art = runtime.sectionArtState();
            assertFalse(art.stopped()); assertEquals(0x0400, art.sectionWord());
            assertEquals(0, art.hintWord()); assertEquals(0, art.padWord());
            boolean prepared = stage + 1 < preparedCalls.length && call == preparedCalls[stage + 1];
            if (prepared) {
                // Final prior tiles commit, native88 POP, then ordinary CPU prepares only its successor.
                assertThreeJumpTransferPrefix(stageReady[stage], art, queuedSources.get(stage).decode().bytes(),
                        destinations[stage], queuedTiles[stage]);
                stage++;
                stageReady[stage] = art;
                stageTables[stage] = threeJumpExpectedCodeTable(priorArt.codeTable(), queuedSources.get(stage).readerBytes());
                assertArrayEquals(threeJumpQueueHead(naturalSlowPop(priorArt.queue().bytes().copy()),
                        bases[stage] + preparedOffsets[stage], destinations[stage]), art.queue().bytes().copy());
                int priorRemainder = priorArt.remainingTilesWord();
                assertTrue(priorRemainder > 0 && priorRemainder <= 3);
                assertEquals(4 - priorRemainder, art.processBudgetWord(),
                        "FixBugs=0 final tile skips the final budget decrement");
                assertEquals(preparedOffsets[stage], art.decoder().orElseThrow().inputOffset());
                assertEquals(16, art.decoder().orElseThrow().shiftWord());
                assertEquals(0, art.decoder().orElseThrow().repeatWord());
                assertEquals(0, art.decoder().orElseThrow().pixelNibble());
                assertEquals(0, art.decoder().orElseThrow().previousRow());
                assertEquals(queuedSources.get(stage).readerBytes().unsignedWord(preparedOffsets[stage] - 2),
                        art.decoder().orElseThrow().readWord());
            } else {
                int completed = (call - preparedCalls[stage]) * 3;
                assertEquals(0, art.processBudgetWord());
                assertThreeJumpTransferPrefix(stageReady[stage], art, queuedSources.get(stage).decode().bytes(),
                        destinations[stage], completed);
                assertArrayEquals(threeJumpQueueHead(stageReady[stage].queue().bytes().copy(),
                        art.decoder().orElseThrow().sourcePointer(), destinations[stage] + completed * 32),
                        art.queue().bytes().copy());
            }
            int completed = (call - preparedCalls[stage]) * 3;
            var decoder = art.decoder().orElseThrow(); var source = queuedSources.get(stage);
            assertEquals(bases[stage], decoder.nativeAddress());
            assertEquals(headers[stage], decoder.headerWord());
            assertEquals(stage != 3, decoder.xorMode(), "Shadow16 is NORMAL; the other reached sources are XOR");
            assertEquals(queuedTiles[stage] - completed, art.remainingTilesWord());
            assertEquals(art.remainingTilesWord(), decoder.remainingTiles());
            assertEquals(bases[stage] + decoder.inputOffset(), decoder.sourcePointer());
            assertEquals(decoder.sourcePointer(), art.queue().headSource());
            assertEquals(destinations[stage] + completed * 32, art.queue().headDestination());
            assertArrayEquals(source.readerBytes().copy(), decoder.sourceBytes().copy());
            assertSame(stageReady[stage].decoder().orElseThrow().sourceBytes(), decoder.sourceBytes());
            assertArrayEquals(stageTables[stage].copy(), art.codeTable().copy());
            assertArrayEquals(stageTables[stage].copy(), decoder.codeTable().copy());
            assertEquals(new com.openggf.game.soniccd.resources.CdR11aSectionArt.SavedDecoder(
                    stage == 3 ? com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer.NORMAL
                            : com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer.XOR,
                    decoder.repeatWord(), decoder.pixelNibble(), decoder.previousRow(), decoder.readWord(), decoder.shiftWord()),
                    art.savedDecoder());
            assertThreeJumpHoleCurrent(art, holeBytes);
            if (call == 3222) {
                assertThreeJumpDecoder(art, pataSource, 0x8032, 32, 307, 0x00CF, 14, 0, 0, 0);
                assertEquals(2323627L, art.queue().headSource());
                // ObjTamabboh_Init/Position (N Main.asm:29-72), detached comparison ONLY.
                // No CdTamabboh.run, callback replay, pool write or source ingress.
                var initial = at3221.objects().placement().pool().slots().get(40 - 32)
                        .moveByte(CdObjectState.ByteField.ID, 0x22)
                        .moveWord(CdObjectState.WordField.X, 4624).moveWord(CdObjectState.WordField.Y, 529)
                        .moveByte(CdObjectState.ByteField.SAVED_FLAGS_ID, 101)
                        .moveByte(CdObjectState.ByteField.ROUTINE, 2)
                        .moveByte(CdObjectState.ByteField.SPRITE_FLAGS, 4)
                        .moveByte(CdObjectState.ByteField.PRIORITY, 4)
                        .moveByte(CdObjectState.ByteField.COLLISION_TYPE, 0x2C)
                        .moveByte(CdObjectState.ByteField.X_RADIUS, 16)
                        .moveByte(CdObjectState.ByteField.WIDTH, 16)
                        .moveByte(CdObjectState.ByteField.Y_RADIUS, 15)
                        .moveWord(CdObjectState.WordField.TAMABBOH_ANCHOR_X, 4624)
                        .moveWord(CdObjectState.WordField.TILE, 0x2428)
                        .moveLong(CdObjectState.LongField.MAPPINGS, 0x20B968)
                        .moveLong(CdObjectState.LongField.TAMABBOH_ANIMATION_SCRIPT, 0x20B942)
                        .moveLong(CdObjectState.LongField.TAMABBOH_X_DELTA, -0xA000)
                        .addLong(CdObjectState.LongField.Y_POSITION, 0x10000);
                var floor = com.openggf.game.soniccd.objects.CdObjectCollisionCheck.objGetFloorDist(comparisonTerrain, initial);
                if (floor.distance() < 0) initial = initial.addByte(CdObjectState.ByteField.ROUTINE, 2);
                byte[] actual = pool.state(40).copyBytes(), expected = initial.copyBytes();
                actual[1] &= 0x7F; // Only native DrawObject's camera-owned on-screen bit is outside Init/Position.
                assertArrayEquals(expected, actual);
                assertEquals(0xFFDA00, pool.state(40).identity().nativeAddress24());
                var status = runtime.placement().status();
                assertEquals(4096, status.previousChunkWord());
                assertEquals(211, status.rightRecordIndex()); assertEquals(181, status.leftRecordIndex());
                assertEquals(104, status.rightSavedId()); assertEquals(91, status.leftSavedId());
            }
            if (call == 3223) {
                assertEquals(29, art.remainingTilesWord()); assertEquals(2323678L, art.queue().headSource());
            }
            if (call == 3233) assertEquals(2320441L, art.queue().headSource());
            if (call == 3243) assertEquals(2320284L, art.queue().headSource());
            if (call == 3247) assertEquals(2318024L, art.queue().headSource());
            if (call == 3253) {
                assertEquals(2324907L, art.queue().headSource()); assertEquals(3, runtime.ringGlobals().rings());
                assertTrue(runtime.lastSoundRequests().contains(new CdOpeningObjectRuntime.RingFm(
                        com.openggf.game.soniccd.objects.CdRing.FmSound.FM_RING)));
                // FM_RING's native sound ID95 is not a physical slot. Native touch then Ring_Collect
                // advances the actual selected routine2 image to6, clears collision and sets priority1.
                var beforeRings = replayRows[call-1].objects().placement().pool().slots();
                int collected = 0;
                for (var beforeRing : beforeRings) {
                    var afterRing = pool.state(beforeRing.identity().wholeIndex());
                    if (beforeRing.id() == 0x10 && beforeRing.routine() == 2
                            && afterRing.id() == 0x10 && afterRing.routine() == 6) {
                        assertEquals(0, afterRing.unsignedByte(CdObjectState.ByteField.COLLISION_TYPE));
                        assertEquals(1, afterRing.unsignedByte(CdObjectState.ByteField.PRIORITY));
                        collected++;
                    }
                }
                assertEquals(1, collected, "One actual selected Ring, retaining its physical identity");
            }
            if (call == 3254) {
                assertEquals(56, art.remainingTilesWord()); assertEquals(2324936L, art.queue().headSource());
            }
            replayRows[call] = state(h);
            precedingBodyRequests[call] = List.copyOf(actorOwner.lastSoundRequests());
            var bodyPrefix = actorOwner.lastSoundRequests().stream().map(CdOpeningObjectRuntime.PlayerSound::new).toList();
            assertEquals(bodyPrefix, runtime.lastSoundRequests().subList(0, bodyPrefix.size()),
                    "Entire actual preceding Body request prefix, followed by owned dynamic requests");
            var aliases = threeJumpCurrentAliases(replayRows[call], objectAssets, banks, art.residency());
            assertThreeJumpCurrentAliases(aliases, art.residency(), banks);
            if (prepared) frozenAliases[stage] = aliases;
            for (int held = 0; held <= stage; held++)
                assertThreeJumpCurrentAliases(frozenAliases[held], stageReady[held].residency(), banks);
            for (int cut = 1; cut < laterCutCalls.length; cut++) {
                if (call == laterCutCalls[cut]) {
                    laterCutStates[cut] = replayRows[call];
                    laterFullCuts[cut] = registry.capture(); laterCourseCuts[cut] = registry.captureCourse();
                }
            }
        }
        assertEquals(4, stage);
        var at3258 = replayRows[3258]; var art3258 = at3258.objects().sectionArtState();
        assertThreeJumpDecoder(art3258, tamSource, 0x803B, 44, 379, 43007, 12, 0, 0, 0);
        assertEquals(2325195L, art3258.queue().headSource()); assertEquals(34528, art3258.queue().headDestination());
        byte[] queue3258 = new byte[96];
        System.arraycopy(java.util.HexFormat.of().parseHex("00237acb86e0"), 0, queue3258, 0, 6);
        assertArrayEquals(queue3258, art3258.queue().bytes().copy());
        assertEquals(0, art3258.processBudgetWord()); assertEquals(0, art3258.hintWord()); assertEquals(0, art3258.padWord());
        assertFalse(art3258.stopped()); assertArrayEquals(stageTables[4].copy(), art3258.codeTable().copy());
        assertThreeJumpTransferPrefix(stageReady[4], art3258, tamSource.decode().bytes(), 0x8500, 15);
        assertArrayEquals(java.util.HexFormat.of().parseHex(
                "220424280020b96811f98000020900000000000000000f1004100102000018002c0000650400000000001210ffff60000020b942000000000000000000000000"),
                pool.state(40).copyBytes());
        assertEquals(4, pool.state(40).routine()); assertEquals(0, pool.state(40).subtype());
        assertEquals(0x11F98000, pool.state(40).longBits(CdObjectState.LongField.X_POSITION));
        assertEquals(521, pool.state(40).centreY());
        assertThreeJumpActor(frame.state(), 291823872, 22832128, 2520);
        assertEquals(3, runtime.ringGlobals().rings()); assertEquals(3, runtime.ringGlobals().lives());
        assertEquals(129, runtime.ringGlobals().updateHudRings());
        assertEquals(6, runtime.ringGlobals().ringAnimationTimer()); assertEquals(0, runtime.ringGlobals().ringAnimationFrame());
        assertEquals(10, runtime.scoreGlobals().score()); assertEquals(5000, runtime.scoreGlobals().nextLifeScore());
        assertEquals(1, runtime.scoreGlobals().updateHudScore());
        assertTrue(runtime.lastSoundRequests().isEmpty()); assertTrue(actorOwner.lastSoundRequests().isEmpty());
        assertEquals(0, runtime.devGlobals().goodFuture()); assertEquals(0, frame.environment().globals().debugMode());
        assertEquals(0, naturalCheckpoint.saved().read(
                com.openggf.game.soniccd.objects.CdCheckpointSaveData.SavedField.TIME_LONG));
        assertEquals(0, com.openggf.game.soniccd.objects.CdCheckpointLiveAux.read(
                runtime.checkpointState().live(), com.openggf.game.soniccd.objects.CdCheckpointLiveAux.Field.TIME_LONG),
                "Actual retained DEV time owner remains zero");
        assertArrayEquals(new byte[64], pool.state(46).copyBytes());

        int[] allCutCalls = Arrays.copyOf(cutCalls, cutCalls.length + laterCutCalls.length);
        System.arraycopy(laterCutCalls, 0, allCutCalls, cutCalls.length, laterCutCalls.length);
        State[] allCutStates = Arrays.copyOf(cutStates, allCutCalls.length);
        System.arraycopy(laterCutStates, 0, allCutStates, cutStates.length, laterCutStates.length);
        var allFullCuts = Arrays.copyOf(fullCuts, allCutCalls.length);
        System.arraycopy(laterFullCuts, 0, allFullCuts, fullCuts.length, laterFullCuts.length);
        var allCourseCuts = Arrays.copyOf(courseCuts, allCutCalls.length);
        System.arraycopy(laterCourseCuts, 0, allCourseCuts, courseCuts.length, laterCourseCuts.length);
        for (int cut = 0; cut < allCutCalls.length; cut++) {
            assertNotNull(allCutStates[cut]); assertNotNull(allFullCuts[cut]); assertNotNull(allCourseCuts[cut]);
        }

        // Preserve detached picture/sound expectations as well as every complete State partition.
        var expectedPictures = Arrays.stream(allCutStates).map(cut -> new SpritePresentation.Frame(
                cut.picture().tiles(), cut.picture().primitives(), cut.picture().patternVersions())).toList();
        var expectedSounds = Arrays.stream(allCutStates).map(cut -> List.copyOf(cut.objects().lastSoundRequests())).toList();
        var endPicture = new SpritePresentation.Frame(at3210.picture().tiles(), at3210.picture().primitives(),
                at3210.picture().patternVersions());
        var endSounds = List.copyOf(at3210.objects().lastSoundRequests());
        for (int cut = 0; cut < allCutCalls.length; cut++) {
            int throughCall = cut == 0 ? 3002 : 3258;
            State expectedForward = cut == 0 ? afterThirdJump : at3258;
            for (boolean course : List.of(false, true)) {
                if (course) registry.restoreCourse(allCourseCuts[cut]); else registry.restore(allFullCuts[cut]);
                assertClosedAtCall.accept(allCutCalls[cut]);
                // BEFORE forwarding: real actor/history, all checkpoint live/saved bytes and masks,
                // current art/queue/table/RAM/residency, pictures, clocks, sound ledger and owner.
                assertThreeJumpExtendedReplayCut(allCutStates[cut], h);
                assertEquals(precedingBodyRequests[allCutCalls[cut]], actorOwner.lastSoundRequests(),
                        "Complete actor-owned preceding Body requests BEFORE forward");
                assertSame(naturalCheckpoint.saved(), runtime.checkpointState().saved());
                for (int call = allCutCalls[cut] + 1; call <= throughCall; call++) {
                    var row = movie.getFrame(call - 1);
                    step(h, input(row.p1InputMask(), row.p1ActionMask(), row.p1StartPressed()));
                    assertClosedAtCall.accept(call);
                    assertNotNull(replayRows[call]);
                    assertThreeJumpExtendedReplayCut(replayRows[call], h); // EVERY reached closed row, including all knowledge/sounds.
                    assertEquals(precedingBodyRequests[call], actorOwner.lastSoundRequests());
                    assertSame(naturalCheckpoint.saved(), runtime.checkpointState().saved());
                }
                assertThreeJumpExtendedReplayCut(expectedForward, h);
                assertEquals(precedingBodyRequests[throughCall], actorOwner.lastSoundRequests());
                assertSame(naturalCheckpoint.saved(), runtime.checkpointState().saved());
                assertSame(sourceBank, objectAssets.sectionArtSources());
                assertSame(boulderSource.decode(), sourceBank.source(selectedRows.get(0)).decode());
                for (int held = 0; held < allCutStates.length; held++) {
                    assertEquals(expectedPictures.get(held), allCutStates[held].picture());
                    assertEquals(expectedSounds.get(held), allCutStates[held].objects().lastSoundRequests());
                }
                assertEquals(endPicture, at3210.picture()); assertEquals(endSounds, at3210.objects().lastSoundRequests());
            }
        }
        assertClosedAtCall.accept(3258); assertThreeJumpExtendedReplayCut(at3258, h);
        assertEquals(precedingBodyRequests[3258], actorOwner.lastSoundRequests());
        assertSame(naturalCheckpoint.saved(), runtime.checkpointState().saved());

        // ONLY AFTER every valid full/course restore and every intermediate row through3258:
        // attempt ordinary3259 ONCE. The 3258 cut itself restores without forwarding.
        // Root's generic ID9/slot46 DYNAMIC_SLOTS fault is before SpinningDisc Init.
        var beforeTerminalArt = runtime.sectionArtState();
        var beforeTerminalClock = frame.vIntClock();
        int beforeTerminalNativeFrame = frame.environment().globals().levelFrames();
        int beforeTerminalEngineFrame = level.getFrameCounter();
        var beforeTerminalRings = runtime.ringGlobals(); var beforeTerminalSpill = runtime.spillGlobals();
        var beforeTerminalScore = runtime.scoreGlobals(); var beforeTerminalDev = runtime.devGlobals();
        var placementOwner = runtime.placement();
        var oldPlacement = at3258.objects().placement(); var oldStatus = oldPlacement.status();
        assertEquals(4224, oldStatus.previousChunkWord());
        assertEquals(213, oldStatus.rightRecordIndex()); assertEquals(186, oldStatus.leftRecordIndex());
        assertEquals(106, oldStatus.rightSavedId()); assertEquals(93, oldStatus.leftSavedId());
        assertArrayEquals(new byte[64], pool.state(46).copyBytes());
        var placementExpected = threeJumpExpectedPlacement(oldPlacement, placementOwner.placements().records(),
                period, 4352, 225, 191);
        // Select the actual decoded native record by all its fields, not by the cursor's ordinal.
        var discRows = placementOwner.placements().records().stream().filter(row ->
                row.objectId() == 9 && row.centreX() == 4944 && row.centreY() == 904
                        && row.packedY() == 904 && row.subtype() == 0 && row.subtype2() == 0
                        && !row.hasSavedFlag() && (row.periodFlags() & (0x20 << period)) != 0).toList();
        assertEquals(1, discRows.size());
        assertTrue(discRows.get(0).index() >= oldStatus.rightRecordIndex() && discRows.get(0).index() < 225);
        var cameraOwner = h.mode().getCamera();
        int beforeCameraX = cameraOwner.getX(), beforeCameraY = cameraOwner.getY();
        // Retain the real owners before failure. Reflection is READ-ONLY diagnostic access
        // to already existing immutable fields, never a capture/restore/owner forgery or runtime input.
        var presentationOwner = (SonicCdPlayerPresentation) h.module().getScrollHandlerProvider();
        var beforeForeground = threeJumpReadField(presentationOwner, "scrollState");
        var beforeBackground = threeJumpReadField(presentationOwner, "backgroundState");
        var beforePreparedScroll = threeJumpReadField(presentationOwner, "preparedScroll");
        var queueOwner = (CdObjectDrawQueue) threeJumpReadField(runtime, "queue");
        var beforeCpu = threeJumpReadField(queueOwner, "cpuSat");
        assertSame(at3258.cpu(), beforeCpu);
        byte[] beforeTam40 = pool.state(40).copyBytes();
        var aliases3258 = threeJumpCurrentAliases(at3258, objectAssets, banks, beforeTerminalArt.residency());
        var next = movie.getFrame(3258);
        var stopped = assertThrows(UnsupportedOperationException.class,
                () -> step(h, input(next.p1InputMask(), next.p1ActionMask(), next.p1StartPressed())));
        assertEquals(UnsupportedOperationException.class, stopped.getClass());
        assertEquals("Unowned CD object ID $9 at live physical slot 46", stopped.getMessage());
        // Retained owner scalar/raw reads ONLY after the fault: no module require, state(h),
        // aggregate/closed capture, restore, callback replay, retry or failed-row resume.
        var terminalArt = runtime.sectionArtState();
        assertFalse(terminalArt.stopped(), "This is a dynamic object boundary, not a decoder fault");
        assertEquals(0x0400, terminalArt.sectionWord()); assertEquals(0, terminalArt.processBudgetWord());
        assertEquals(0, terminalArt.hintWord()); assertEquals(0, terminalArt.padWord());
        assertThreeJumpDecoder(terminalArt, tamSource, 0x803B, 41, 452, 37059, 11, 1, 0, 585616510);
        assertEquals(2325268L, terminalArt.queue().headSource());
        assertEquals(34624, terminalArt.queue().headDestination());
        byte[] queue3259 = new byte[96];
        System.arraycopy(java.util.HexFormat.of().parseHex("00237b148740"), 0, queue3259, 0, 6);
        assertArrayEquals(queue3259, terminalArt.queue().bytes().copy());
        assertSame(beforeTerminalArt.decoder().orElseThrow().sourceBytes(), terminalArt.decoder().orElseThrow().sourceBytes());
        assertArrayEquals(beforeTerminalArt.codeTable().copy(), terminalArt.codeTable().copy());
        assertArrayEquals(stageTables[4].copy(), terminalArt.codeTable().copy());
        assertEquals(new com.openggf.game.soniccd.resources.CdR11aSectionArt.SavedDecoder(
                        com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer.XOR,
                        1, 0, 585616510, 37059, 11), terminalArt.savedDecoder());
        assertThreeJumpSavedDecoder(terminalArt);
        assertThreeJumpTransferPrefix(stageReady[4], terminalArt, tamSource.decode().bytes(), 0x8500, 18);
        assertThreeJumpHoleCurrent(terminalArt, holeBytes);
        assertThreeJumpCurrentAliases(threeJumpCurrentAliases(at3258, objectAssets, banks, terminalArt.residency()),
                terminalArt.residency(), banks);
        assertThreeJumpCurrentAliases(aliases3258, beforeTerminalArt.residency(), banks);
        // Native Main:94-111 completed before its Draw -> anchor/despawn wrapper (:15-17).
        // The whole literal includes map/tile, animation, anchor, motion delta, saved ID and all nonwrites.
        var slot40 = pool.state(40);
        assertArrayEquals(java.util.HexFormat.of().parseHex(
                "220424280020b96811f8e000020900000000000000000f1004100102000017002c0000650400000000001210ffff60000020b942000000000000000000000000"),
                slot40.copyBytes());
        assertEquals(-0xA000, slot40.longBits(CdObjectState.LongField.X_POSITION)
                - ByteBuffer.wrap(beforeTam40).getInt(8));
        assertEquals(0x11F8E000, slot40.longBits(CdObjectState.LongField.X_POSITION));
        assertEquals(ByteBuffer.wrap(beforeTam40).getInt(12), slot40.longBits(CdObjectState.LongField.Y_POSITION));
        assertEquals(521, slot40.centreY()); assertEquals(4, slot40.routine());
        assertEquals(0, slot40.subtype()); assertEquals(24, Byte.toUnsignedInt(beforeTam40[0x1E]));
        assertEquals(23, slot40.unsignedByte(CdObjectState.ByteField.ANIMATION_TIME));
        assertEquals(0, com.openggf.game.soniccd.objects.CdObjectCollisionCheck
                .objGetFloorDist(comparisonTerrain, slot40).distance(),
                "Shared pure terrain comparison on the reached image, not another body/native execution");
        assertNull(threeJumpReadField(runtime, "activeTamabbohInvocation"), "Synchronous source invocation retires in finally");
        // ObjectIndex (N Palmtree Panic/Object Index (Act 1 Present).asm:16) names ObjSpinningDisc.
        // Full slot46 is placement-only; neither Init nor any Disc body ran.
        var slot46 = pool.state(46);
        assertEquals(0xFFDB80, slot46.identity().nativeAddress24());
        assertArrayEquals(java.util.HexFormat.of().parseHex(
                "09000000000000001350000003880000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"),
                slot46.copyBytes());
        assertEquals(9, slot46.id()); assertEquals(0, slot46.routine());
        assertEquals(4944, slot46.centreX()); assertEquals(904, slot46.centreY());
        assertEquals(0, slot46.subtype()); assertEquals(0, slot46.subtype2()); assertEquals(0, slot46.savedFlagsId());
        var afterStatus = placementOwner.status();
        assertEquals(4352, afterStatus.previousChunkWord());
        assertEquals(225, afterStatus.rightRecordIndex()); assertEquals(191, afterStatus.leftRecordIndex());
        assertEquals(111, afterStatus.rightSavedId()); assertEquals(93, afterStatus.leftSavedId());
        assertEquals(oldStatus.routine(), afterStatus.routine());
        assertEquals(oldStatus.nullRightByteOffset(), afterStatus.nullRightByteOffset());
        assertEquals(oldStatus.nullLeftByteOffset(), afterStatus.nullLeftByteOffset());
        // Ring Init in the completed earlier prefix can publish later children before the fault.
        // Model only those source-ordered stores; none of these later images ran their own body.
        var nativePrefixExpected = threeJumpExpectedRingInitPrefix(placementExpected, objectAssets, period, 46);
        for (int whole = 46; whole < 128; whole++)
            assertArrayEquals(nativePrefixExpected.slots()[whole - 32], pool.state(whole).copyBytes(),
                    "Placement/earlier Ring Init only, unvisited slot " + whole);
        boolean earlierDynamicChanged = false;
        byte[] expectedSavedFlags = nativePrefixExpected.savedFlags().clone();
        for (int whole = 32; whole < 46; whole++) {
            byte[] placed = placementExpected.slots()[whole - 32]; var actual = pool.state(whole);
            earlierDynamicChanged |= !Arrays.equals(placed, actual.copyBytes());
            int savedId = Byte.toUnsignedInt(placed[0x23]);
            if (placed[0] != 0 && actual.id() == 0 && savedId != 0)
                expectedSavedFlags[2 + savedId * 3 + period] &= (byte) 0x7F;
        }
        assertTrue(earlierDynamicChanged, "The genuine completed Tamabboh/prefix must survive");
        assertEquals(afterStatus.rightSavedId(), Byte.toUnsignedInt(expectedSavedFlags[0]));
        assertEquals(afterStatus.leftSavedId(), Byte.toUnsignedInt(expectedSavedFlags[1]));
        for (int offset = 2; offset < expectedSavedFlags.length; offset++)
            assertEquals(Byte.toUnsignedInt(expectedSavedFlags[offset]),
                    placementOwner.savedFlag((offset - 2) / 3, (offset - 2) % 3), "Full saved-object bank byte " + offset);
        assertThreeJumpActor(frame.state(), 292217088, 23477248, 2576);
        assertEquals(beforeTerminalRings, runtime.ringGlobals(), "Ordinary Ring tail was not reached");
        assertEquals(beforeTerminalSpill, runtime.spillGlobals(), "Ordinary spill tail was not reached");
        assertEquals(beforeTerminalScore, runtime.scoreGlobals()); assertEquals(beforeTerminalDev, runtime.devGlobals());
        assertEquals(3, runtime.ringGlobals().rings()); assertEquals(3, runtime.ringGlobals().lives());
        assertEquals(129, runtime.ringGlobals().updateHudRings());
        assertEquals(6, runtime.ringGlobals().ringAnimationTimer()); assertEquals(0, runtime.ringGlobals().ringAnimationFrame());
        assertEquals(10, runtime.scoreGlobals().score()); assertEquals(5000, runtime.scoreGlobals().nextLifeScore());
        assertEquals(1, runtime.scoreGlobals().updateHudScore());
        assertTrue(actorOwner.lastSoundRequests().isEmpty(), "Actual complete preceding Body request stream at3259");
        assertTrue(runtime.lastSoundRequests().isEmpty(), "Actual entire reached Body/dynamic request stream at3259; not a native audio claim");
        assertEquals(2, runtime.levelGlobals().checkpoint()); assertEquals(1, runtime.levelGlobals().spawnMode());
        assertEquals(0, runtime.levelGlobals().levelRestart());
        assertSame(naturalCheckpoint.saved(), runtime.checkpointState().saved());
        assertEquals(naturalCheckpoint.saved(), runtime.checkpointState().saved());
        assertArrayEquals(naturalCheckpoint.saved().knownMask().copy(), runtime.checkpointState().saved().knownMask().copy());
        assertEquals(at3258.objects().checkpointState().live(), runtime.checkpointState().live());
        assertEquals(at3258.objects().checkpointState().live().knownMask(), runtime.checkpointState().live().knownMask());
        assertEquals(beforeTerminalClock.absoluteSeed(), frame.vIntClock().absoluteSeed());
        assertEquals((beforeTerminalClock.deltaSinceAdmittedCut() + 1) & 0xFFFF_FFFFL, frame.vIntClock().deltaSinceAdmittedCut());
        assertEquals((beforeTerminalNativeFrame + 1) & 0xFFFF, frame.environment().globals().levelFrames());
        assertEquals(beforeTerminalEngineFrame + 1, level.getFrameCounter());
        assertEquals(beforeCameraX, cameraOwner.getX()); assertEquals(beforeCameraY, cameraOwner.getY());
        assertSame(beforeForeground, threeJumpReadField(presentationOwner, "scrollState"), "No CPU Scroll");
        assertSame(beforeBackground, threeJumpReadField(presentationOwner, "backgroundState"), "No background Scroll");
        assertSame(beforePreparedScroll, threeJumpReadField(presentationOwner, "preparedScroll"), "No ordinary prepared-scroll tail");
        assertSame(beforePreparedScroll, threeJumpReadField(presentationOwner, "publishedScroll"),
                "Represented VInt selected the previous completed scroll before the dynamic failure");
        assertSame(beforeCpu, threeJumpReadField(queueOwner, "cpuSat"), "No new CPU SAT build/commit");
        assertEquals(generation, level.getCompletedProductionLoadGeneration()); assertTrue(boundaries.isEmpty());
        assertSame(world, h.mode().getWorldSession()); assertSame(level, h.mode().getLevelManager());
        assertSame(loadedLevel, level.getCurrentLevel());
        assertEquals(CdOpeningObjectRuntime.Phase.FAILED, runtime.phase());
        assertTrue(runtime.closed()); assertTrue(frame.closed());
        // Stop: no completed3259, no selected440 fault, no Tam59 completion,
        // native execution, whole-act completion, playback, rendering or parity claim.
    }

    private static void assertThreeJumpReplayCut(State expected, Harness h) {
        assertState(expected, h);
        assertNaturalRestartCheckpoint(expected.objects().checkpointState(), h);
        var actual = state(h);
        assertSame(expected.actor().owner(), actual.actor().owner());
        assertSame(expected.shared().owner(), actual.shared().owner());
        assertEquals(expected.objects().displayedFrame(), actual.objects().displayedFrame());
        assertEquals(expected.objects().lastSoundRequests(), actual.objects().lastSoundRequests());
        assertEquals(CdOpeningObjectRuntime.Phase.CLOSED_CUT, objects(h).phase());
        assertFalse(objects(h).closed()); assertFalse(h.module().requirePlayerFrame().closed());
    }

    private static void assertThreeJumpNativeTop(CdPlayerBody.State actor, CdObjectState platform, int wholeIndex) {
        // ObjPlatform/Main.asm:7-13,89-118,145-186: native Main/Top then actual DrawObject.
        // DrawObject (Object Functions.asm:137-155) owns visibility marking/camera culling.
        // TopSolidObject/SolidObject (.PlayerAbove)/StandOnObject
        // (Object Solid.asm:174-175,188-235,344-430,65-146) clear solidity upon standing,
        // set BOTH stand flags and the WHOLE slot link, ground Sonic and stop vertical speed.
        var motion = actor.motion();
        assertEquals(0x21, platform.id()); assertEquals(2, platform.routine());
        assertEquals(wholeIndex, actor.objectLinks().standObject());
        assertEquals(8, motion.flags() & 8); assertEquals(8, platform.flags() & 8);
        assertEquals(0, motion.flags() & 2); assertEquals(0, motion.yVelocity());
        assertEquals(2, motion.routine());
        assertEquals(0, platform.unsignedByte(CdObjectState.ByteField.SOLID_TYPE));
        int radius = (byte) platform.unsignedByte(CdObjectState.ByteField.WIDTH) + 9 + 1;
        int transport = (Math.abs((int) platform.signedWord(CdObjectState.WordField.X_VELOCITY)) + 255) / 256;
        int horizontalDistance = (short) ((motion.centreX16_16() >>> 16) - platform.centreX());
        assertTrue(Math.abs(horizontalDistance) <= radius + transport,
                "Native width+9+1 bound allows the subsequent platform transport");
        if ((platform.subtype() >>> 4) == 1 && platform.signedWord(CdObjectState.WordField.Y_VELOCITY) == 0) {
            int topY = (platform.centreY() - (byte) platform.unsignedByte(CdObjectState.ByteField.Y_RADIUS)
                    - (byte) motion.yRadius()) & 0xFFFF;
            assertEquals(topY, motion.centreY16_16() >>> 16, "Native top face with real current radii");
        }
    }

    private static void assertThreeJumpBoulderPrefix(
            com.openggf.game.soniccd.resources.CdR11aSectionArt.State ready,
            com.openggf.game.soniccd.resources.CdR11aSectionArt.State current,
            CdAssetBytes decoded, int completedTiles) {
        assertTrue(completedTiles >= 0 && completedTiles <= 19);
        for (var region : com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.values()) {
            var before = ready.residency().region(region); var after = current.residency().region(region);
            for (int offset = 0; offset < region.byteLength(); offset++) {
                int address = region.destinationWord() + offset;
                boolean written = address >= 0x6E80 && address < 0x6E80 + completedTiles * 32;
                var expected = written ? java.util.OptionalInt.of(decoded.unsignedByte(address - 0x6E80))
                        : before.knownByte(offset);
                assertEquals(expected, after.knownByte(offset), region.name() + " byte " + offset);
                assertEquals(written ? 1 : before.knownMask().unsignedByte(offset),
                        after.knownMask().unsignedByte(offset), region.name() + " mask " + offset);
            }
        }
    }

    /** Pure comparison bytes only; never supplied to a PLC/runtime/restore owner. */
    private static byte[] threeJumpQueueHead(byte[] before, long source, int destination) {
        byte[] expected = before.clone();
        ByteBuffer.wrap(expected).putInt((int) source).putShort((short) destination);
        return expected;
    }

    private static String threeJumpSha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /**
     * Test oracle ONLY: expand the valid source's prefix-code descriptors over the
     * incoming comparison table, retaining every unwritten entry. No decoder State,
     * runtime input, table ingress or authenticated-media certificate is constructed.
     * NemDec_BuildCodeTable/NemBCT_ShortCode (Functions (General).asm:533-579).
     */
    private static CdAssetBytes threeJumpExpectedCodeTable(CdAssetBytes incoming, CdAssetBytes source) {
        assertEquals(512, incoming.length());
        byte[] expected = incoming.copy();
        int cursor = 2, palette = -1;
        while (true) {
            int descriptor = source.unsignedByte(cursor++);
            if (descriptor == 0xFF) break;
            if ((descriptor & 0x80) != 0) { palette = descriptor & 15; continue; }
            assertTrue(palette >= 0);
            int length = descriptor & 15; assertTrue(length >= 1 && length <= 8);
            int code = source.unsignedByte(cursor++), copies = 1 << (8 - length);
            int first = code * copies; assertTrue(first + copies <= 256);
            for (int entry = first; entry < first + copies; entry++) {
                expected[entry * 2] = (byte) length;
                expected[entry * 2 + 1] = (byte) ((descriptor & 0x70) | palette);
            }
        }
        return CdAssetBytes.copyOf(expected);
    }

    private static void assertThreeJumpDecoder(
            com.openggf.game.soniccd.resources.CdR11aSectionArt.State art,
            com.openggf.game.soniccd.level.CdR11aObjectAssets.SectionArtSource source,
            int header, int remaining, int inputOffset, int read, int shift, int repeat, int pixel, int previous) {
        var decoder = art.decoder().orElseThrow();
        assertEquals(source.canonicalPlc().sourceRange().nativeAddress(), decoder.nativeAddress());
        assertArrayEquals(source.readerBytes().copy(), decoder.sourceBytes().copy());
        assertEquals(header, decoder.headerWord()); assertTrue(decoder.xorMode());
        assertEquals(remaining, art.remainingTilesWord()); assertEquals(remaining, decoder.remainingTiles());
        assertEquals(inputOffset, decoder.inputOffset());
        assertEquals(decoder.nativeAddress() + inputOffset, decoder.sourcePointer());
        assertEquals(decoder.sourcePointer(), art.queue().headSource());
        assertEquals(read, decoder.readWord()); assertEquals(shift, decoder.shiftWord());
        assertEquals(repeat, decoder.repeatWord()); assertEquals(pixel, decoder.pixelNibble());
        assertEquals(previous, decoder.previousRow());
        assertArrayEquals(art.codeTable().copy(), decoder.codeTable().copy());
    }

    private static void assertThreeJumpSavedDecoder(
            com.openggf.game.soniccd.resources.CdR11aSectionArt.State art) {
        var decoder = art.decoder().orElseThrow();
        assertEquals(new com.openggf.game.soniccd.resources.CdR11aSectionArt.SavedDecoder(
                        com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer.XOR,
                        decoder.repeatWord(), decoder.pixelNibble(), decoder.previousRow(), decoder.readWord(), decoder.shiftWord()),
                art.savedDecoder());
    }

    /** Every supplied byte AND mask, plus absence for unknown bytes; never read unknown backing storage. */
    private static void assertThreeJumpTransferPrefix(
            com.openggf.game.soniccd.resources.CdR11aSectionArt.State before,
            com.openggf.game.soniccd.resources.CdR11aSectionArt.State current,
            CdAssetBytes decoded, int destination, int completedTiles) {
        assertTrue(completedTiles >= 0 && completedTiles * 32 <= decoded.length());
        for (var region : com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.Region.values()) {
            var original = before.residency().region(region); var actual = current.residency().region(region);
            byte[] expectedMask = original.knownMask().copy();
            for (int offset = 0; offset < region.byteLength(); offset++) {
                int address = region.destinationWord() + offset;
                boolean written = address >= destination && address < destination + completedTiles * 32;
                if (written) expectedMask[offset] = 1;
                assertEquals(written ? java.util.OptionalInt.of(decoded.unsignedByte(address - destination))
                                : original.knownByte(offset), actual.knownByte(offset), region + " byte " + offset);
            }
            assertArrayEquals(expectedMask, actual.knownMask().copy(), region + " entire known mask");
        }
        for (int tile = 0; tile < completedTiles; tile++)
            assertArrayEquals(decoded.slice(tile * 32, 32).copy(),
                    com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(
                            current.residency(), destination / 32 + tile).copy());
    }

    private static void assertThreeJumpHoleCurrent(
            com.openggf.game.soniccd.resources.CdR11aSectionArt.State current, CdAssetBytes expected) throws Exception {
        byte[] actual = new byte[448];
        for (int tile = 0; tile < 14; tile++)
            System.arraycopy(com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(
                    current.residency(), 0x7400 / 32 + tile).copy(), 0, actual, tile * 32, 32);
        assertArrayEquals(expected.copy(), actual);
        assertEquals("8038f15c0f2952ab7ce2c2e9bb2823a14888aab2e5d9d77146ed01ade5845399", threeJumpSha256(actual));
    }

    private static void assertThreeJumpExtendedReplayCut(State expected, Harness h) {
        assertThreeJumpReplayCut(expected, h); // Includes all actor/object/history/scroll/picture/sound partitions.
        var restored = objects(h).sectionArtState();
        var captured = expected.objects().sectionArtState();
        if (captured.decoder().isPresent())
            assertSame(captured.decoder().orElseThrow().sourceBytes(), restored.decoder().orElseThrow().sourceBytes(),
                    "Same actual runtime owner must retain its captured finite source identity");
    }

    /** Detached expected bytes only. This value has no production input, pool, capture or restore port. */
    private record ThreeJumpPlacementExpected(byte[][] slots, byte[] savedFlags) { }

    /**
     * Comparison-only native forward placement (Object Spawner.asm:176-227,240-319).
     * Bounds come from the supplied before/after cuts; all record fields are real decoded
     * source data. Retain dirty slot fractions/nonstores and the full saved bank.
     * No Init/body/Draw or production input/restore is performed.
     */
    private static ThreeJumpPlacementExpected threeJumpExpectedPlacement(
            com.openggf.game.soniccd.objects.CdObjectPlacementRuntime.Snapshot before,
            List<com.openggf.game.soniccd.level.CdObjectPlacements.Placement> rows, int period,
            int nextChunk, int rightAfter, int leftAfter) {
        byte[][] slots = before.pool().slots().stream().map(CdObjectState::copyBytes).toArray(byte[][]::new);
        byte[] flags = before.savedFlags().copy();
        int savedId = 0;
        for (int index = before.status().rightRecordIndex(); index < rightAfter; index++) {
            var row = rows.get(index);
            assertTrue(row.centreX() >= before.status().previousChunkWord() + 0x280
                    && row.centreX() < nextChunk + 0x280);
            if (row.hasSavedFlag()) {
                savedId = Byte.toUnsignedInt(flags[0]);
                flags[0]++;
            }
            if ((row.periodFlags() & (0x20 << period)) == 0) continue;
            if (row.hasSavedFlag()) {
                int at = 2 + savedId * 3 + period;
                boolean loaded = (flags[at] & 0x80) != 0;
                flags[at] |= (byte) 0x80;
                if (loaded) continue;
            }
            int slot = 0;
            while (slot < slots.length && slots[slot][0] != 0) slot++;
            assertTrue(slot < slots.length, "Supplied bounded forward cut has no allocation failure");
            byte[] image = slots[slot];
            ByteBuffer.wrap(image).putShort(8, (short) row.centreX()).putShort(12, (short) row.centreY());
            int flips = row.packedY() >>> 14;
            image[1] = (byte) flips; image[0x22] = (byte) flips;
            if (row.hasSavedFlag()) image[0x23] = (byte) savedId;
            image[0] = (byte) row.objectId(); image[0x28] = (byte) row.subtype(); image[0x29] = (byte) row.subtype2();
        }
        for (int index = before.status().leftRecordIndex(); index < leftAfter; index++) {
            assertTrue(rows.get(index).centreX() >= before.status().previousChunkWord() - 0x80
                    && rows.get(index).centreX() < nextChunk - 0x80);
            if (rows.get(index).hasSavedFlag()) flags[1]++;
        }
        return new ThreeJumpPlacementExpected(slots, flags);
    }
    /** Comparison-only ObjRing_Init (Ring/Main.asm:38-210), no live writes or body replay.
     * This actual unwarped cut has no new pickup; only earlier parents may initialize later children.
     * Native FindNext starts after its parent each time, retains dirty child bytes and clears LOADED
     * before the allocation attempt. Main/Draw on earlier slots do not run in this projection.
     */
    private static ThreeJumpPlacementExpected threeJumpExpectedRingInitPrefix(
            ThreeJumpPlacementExpected placed,
            com.openggf.game.soniccd.level.CdR11aObjectAssets objects, int period, int failedWhole) throws Exception {
        assertEquals(1, period, "Bounded unwarped prefix, not the shipped warping D2-clobber path");
        byte[][] slots = Arrays.stream(placed.slots()).map(byte[]::clone).toArray(byte[][]::new);
        byte[] flags = placed.savedFlags().clone();
        int mapping = objects.view(com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.RING_MAPPINGS).range().nativeAddress();
        int tile = objects.initialArtBank().binding(com.openggf.game.soniccd.level.CdR11aAsset.ART_RINGS).baseWord();
        for (int whole = 32; whole < failedWhole; whole++) {
            int at = whole - 32; byte[] parent = slots[at];
            if (parent[0] != 0x10 || parent[0x24] != 0) continue;
            int saved = Byte.toUnsignedInt(parent[0x23]), subtype = Byte.toUnsignedInt(parent[0x28]);
            int minusOne = subtype & 7; if (minusOne == 7) minusOne = 6;
            var delta = objects.ringDelta(subtype >>> 4);
            int x = Short.toUnsignedInt(ByteBuffer.wrap(parent).getShort(8));
            int y = Short.toUnsignedInt(ByteBuffer.wrap(parent).getShort(12));
            for (int member = 1; member <= minusOne + 1; member++) {
                boolean collected = false;
                for (int p = period; p >= 0; p--)
                    collected |= (Byte.toUnsignedInt(flags[2 + saved * 3 + p]) & (1 << (member - 1))) != 0;
                if (!collected) {
                    flags[2 + saved * 3 + period] &= (byte) 0x7F;
                    int target = at;
                    if (member != 1) {
                        target = at + 1;
                        while (target < slots.length && slots[target][0] != 0) target++;
                        if (target == slots.length) break;
                    }
                    byte[] image = slots[target]; image[0] = 0x10; image[0x24] = 2;
                    ByteBuffer.wrap(image).putShort(8, (short) x);
                    int anchor = Short.toUnsignedInt(ByteBuffer.wrap(slots[at]).getShort(8));
                    ByteBuffer.wrap(image).putShort(0x32, (short) anchor).putShort(12, (short) y)
                            .putInt(4, mapping).putShort(2, (short) tile);
                    image[0x18] = 2; image[1] = 4; image[0x20] = 0x47; image[0x19] = 8; image[0x16] = 8;
                    image[0x23] = slots[at][0x23]; image[0x34] = (byte) member;
                }
                x = (x + delta.x()) & 0xFFFF; y = (y + delta.y()) & 0xFFFF;
            }
            boolean firstCollected = false;
            for (int p = period; p >= 0; p--) firstCollected |= (flags[2 + saved * 3 + p] & 1) != 0;
            if (firstCollected) Arrays.fill(parent, (byte) 0);
        }
        return new ThreeJumpPlacementExpected(slots, flags);
    }

    /** Source-derived terminal actor literals, never an actor/Frame ingress. */
    private static void assertThreeJumpActor(CdPlayerBody.State actor, int x16, int y16, int yVelocity) {
        assertEquals(x16, actor.motion().centreX16_16()); assertEquals(y16, actor.motion().centreY16_16());
        assertEquals(1536, actor.motion().xVelocity()); assertEquals(1536, actor.motion().groundVelocity());
        assertEquals(yVelocity, actor.motion().yVelocity()); assertEquals(2, actor.motion().routine());
        assertEquals(2, actor.motion().flags()); assertEquals(0, actor.objectLinks().standObject());
        assertEquals(0, actor.objectLinks().pushObject()); assertEquals(0, actor.timers().hurt());
    }

    /** Read actual retained diagnostic fields; no setter or capture/restore owner is constructed. */
    private static Object threeJumpReadField(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    /**
     * Existing C29 component projection ONLY, using a closed cut's player/powerup values
     * and explicitly selected immutable current bytes. This never enters production.
     * Root's later separate current440 migration is outside this source packet.
     */
    private static com.openggf.game.soniccd.resources.CdObjectPresentation.PatternSource threeJumpCurrentAliases(
            State cut, com.openggf.game.soniccd.level.CdR11aObjectAssets objects,
            CdOpeningObjectRuntime.LogicalBanks banks,
            com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State residency) throws Exception {
        return com.openggf.game.soniccd.resources.CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(
                cut.shared().artState(), cut.objects().powerupArtState(), objects,
                objects.frontierArtBank(), objects.contactArtBank(), objects.floatBlockArtBank(),
                objects.springArtBank(), objects.tunnelDoorArtBank(), objects.monitorTimeArtBank(), residency,
                banks.player(), banks.ring(), banks.mosqui(), banks.plant(), banks.ramp(), banks.explosion(),
                banks.points(), banks.flower(), banks.powerup(), banks.floatBlock(), banks.spring(),
                banks.tunnelDoorVertical(), banks.monitorTime(), banks.boulder(), banks.diagonalSpring(),
                banks.spikes(), banks.checkpoint(), banks.anton(), banks.collapsePlatform());
    }

    /**
     * Preserve every current alias in the four affected existing windows, including nonwrites.
     * Waterfall's last2 Mosqui, Disc's first12 Anton, Shadow's7 Anton+5 complement+4 Plant
     * and Tam's actual15/18 Plant aliases are backed by the complete physical/mask comparisons.
     * Complementary storage has no invented picture binding. Native440 is never selected here.
     */
    private static void assertThreeJumpCurrentAliases(
            com.openggf.game.soniccd.resources.CdObjectPresentation.PatternSource actual,
            com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.State residency,
            CdOpeningObjectRuntime.LogicalBanks banks) {
        int[] first = {0x7400 / 32,0x8120 / 32,0x8480 / 32,0x8820 / 32};
        int[] count = {70,22,28,63};
        int[] logical = {banks.mosqui(),banks.anton(),banks.plant(),banks.ramp()};
        for (int range = 0; range < first.length; range++) for (int index = 0; index < count[range]; index++) {
            int nativeId = first[range] + index;
            var tile = actual.resolve(nativeId);
            assertEquals(logical[range] + index, tile.logicalPatternId(), "Retained alias " + nativeId);
            assertEquals(com.openggf.game.soniccd.resources.CdObjectPresentation.patternVersion(
                    com.openggf.game.soniccd.resources.CdR11aCameraArtResidency.readTile(residency, nativeId)),
                    tile.version(), "Exact current/frozen alias bytes " + nativeId);
        }
    }




}

// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
