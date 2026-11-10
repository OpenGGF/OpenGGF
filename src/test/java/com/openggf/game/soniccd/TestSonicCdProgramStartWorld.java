package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.internal.NativeDynamicObjectPass;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.CdCapturedValueAssertions.assertCapturedValueEquals;
import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.actualField;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/** Earlier explicit DEV initial conditions and real source/World ownership; not retail boot.
 * New/changed Java checks are UNRUN by this author. Native N1ecc5f3a MDINIT.Start25-30/
 * IPX.Start32-37 own the fixed MAINVARS projection; omitted programmes/hardware are not executed.
 */
@Isolated
class TestSonicCdProgramStartWorld {
    private static CdGameDataSource usa;

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

    @Test void legacyDevelopmentEntryKeepsItsUnknownEarlierHistory() {
        var module = SonicCdGameModule.openingObjectsDevelopmentPlayer();
        var before = module.programServicesState();
        assertEquals(SonicCdProgramLoadServices.PcmKnowledge.UNKNOWN, before.loads().pcmKnowledge());
        assertEquals(CdProgramInitialization.unknown(), before.initialization());
        assertEquals(CdProgramInitialization.KnownBit.UNKNOWN, before.initialization().palClearFlagBit0());
        assertTrue(before.environment().region().isEmpty());
        assertTrue(before.completedCalls().isEmpty());
    }

    @Test void namedColdEntryDeclaresInputsWithoutMintingCompletedSetup() {
        var module = cold(0xC0); var value = module.programServicesState();
        assertEquals(SonicCdProgramLoadServices.PcmKnowledge.KNOWN_ABSENT, value.loads().pcmKnowledge());
        assertTrue(value.loads().installed().isEmpty());
        assertEquals(CdProgramInitialization.KnownBit.CLEAR, value.initialization().controllerSetupBit());
        assertEquals(CdProgramInitialization.MarkerKnowledge.UNKNOWN, value.initialization().initMarker());
        assertEquals(new CdProgramInitialization.VersionCache(0, 0), value.initialization().versionCache());
        assertEquals(CdProgramInitialization.KnownBit.UNKNOWN, value.initialization().palClearFlagBit0());
        assertEquals(0xC0, value.environment().region().orElseThrow().versionC0());
        assertTrue(value.completedCalls().isEmpty());
        assertThrows(NullPointerException.class,
                () -> SonicCdGameModule.openingObjectsDevelopmentPlayerFromColdProgramStart(null));
    }

    @Test void actualWorldRunsOrderedStartWithAnExplicitRegionIndependentFromTheUsaDisc() throws Exception {
        for (int region : new int[]{0, 0x40, 0x80, 0xC0}) {
            var h = load(cold(region)); var module = h.module(); var value = module.programServicesState();
            assertEquals(List.of(SonicCdGameModule.ProgramStartCall.CLEAR_MAIN_VARIABLES,
                    SonicCdGameModule.ProgramStartCall.LOAD_LEVEL_PCM,
                    SonicCdGameModule.ProgramStartCall.WRITE_VERSION_CACHE,
                    SonicCdGameModule.ProgramStartCall.WRITE_INIT_MARKER,
                    SonicCdGameModule.ProgramStartCall.INIT_VDP,
                    SonicCdGameModule.ProgramStartCall.INIT_CONTROLLERS,
                    SonicCdGameModule.ProgramStartCall.DECLARED_BEGIN_LEVEL_INGRESS), value.completedCalls());
            assertEquals(CdProgramInitialization.KnownBit.SET, value.initialization().controllerSetupBit());
            assertEquals(CdProgramInitialization.MarkerKnowledge.EQUALS_INIT, value.initialization().initMarker());
            assertEquals(new CdProgramInitialization.VersionCache(region, 0xFF), value.initialization().versionCache());
            assertClearExactlyOnce(module);
            var mdInit = source().fmSoundAssets().orElseThrow().source();
            assertEquals(CdReleaseVariant.USA, mdInit.variant()); assertEquals(CdNativeFile.MDINIT, mdInit.file());
            assertEquals("abd4735cb55aac797402d611b6d732276f02f1394ef39e7e1af6cd9dbd6fdf7e", mdInit.mdinitSha256());
            assertEquals(source().assets().identity().observedWholeDiscSha256(), mdInit.wholeDiscSha256());
            assertEquals(source().release().normalizedSha256(), mdInit.wholeDiscSha256());
            String[] oldNames = {"LOAD_LEVEL_PCM", "WRITE_VERSION_CACHE", "WRITE_INIT_MARKER", "INIT_VDP", "INIT_CONTROLLERS", "DECLARED_BEGIN_LEVEL_INGRESS"};
            var calls = SonicCdGameModule.ProgramStartCall.values(); assertEquals(9, calls.length);
            for (int i = 0; i < oldNames.length; i++) assertEquals(oldNames[i], calls[i].name());
            assertEquals(6, SonicCdGameModule.ProgramStartCall.CLEAR_MAIN_VARIABLES.ordinal());
            assertEquals(SonicCdProgramLoadServices.LoadPrefix.SOURCE_INSTALLED, value.loads().lastLoad().prefix());
            var driver = value.loads().installed().orElseThrow();
            var programs = source().timeWarpPrograms().orElseThrow();
            assertEquals(programs.source(), driver.driver().source());
            assertSame(programs.r1Pcm(), driver.driver().originalSource());
            assertEquals(0, driver.variables().on()); assertEquals(0, driver.variables().soundPlay());
            assertEquals(0, driver.variables().pointerOffset()); assertEquals(0, driver.variables().queue().slot0());
            assertEquals(CdReleaseVariant.USA, module.requirePlayerFrame().stageSource().variant());
            assertEquals(0, module.requirePlayerFrame().vIntClock().absoluteValue().orElseThrow());
            assertEquals(0, h.mode().getLevelManager().getFrameCounter(), "Native Start does not tick gameplay");
            assertEquals(0, module.requirePlayerFrame().state().motion().routine(), "No standing-pose prime");
            SessionManager.clear(); setup();
        }
    }

    @Test void unknownExecutionRegionRetainsTheInstalledSourcePrefixAndCannotRetryTheEntry() throws Exception {
        var module = SonicCdGameModule.openingObjectsDevelopmentPlayerFromColdProgramStart(
                new CdProgramInitialization.ExecutionEnvironment(Optional.empty()));
        var failure = assertThrows(java.io.IOException.class, () -> load(module));
        var error = assertInstanceOf(CdProgramInitialization.UnknownRead.class, failure.getCause());
        assertEquals(CdProgramInitialization.Read.EXECUTION_REGION, error.read());
        var stopped = module.programServicesState();
        assertEquals(List.of(SonicCdGameModule.ProgramStartCall.CLEAR_MAIN_VARIABLES,
                SonicCdGameModule.ProgramStartCall.LOAD_LEVEL_PCM), stopped.completedCalls());
        assertEquals(SonicCdProgramLoadServices.PcmKnowledge.LOADED, stopped.loads().pcmKnowledge());
        assertEquals(CdProgramInitialization.KnownBit.CLEAR, stopped.initialization().controllerSetupBit());
        assertEquals(CdProgramInitialization.MarkerKnowledge.DIFFERS_FROM_INIT, stopped.initialization().initMarker());
        assertEquals(new CdProgramInitialization.VersionCache(0, 0xFF), stopped.initialization().versionCache());
        assertClearExactlyOnce(module);
        assertNull(module.getGameService(NativeDynamicObjectPass.class));
        assertThrows(IllegalStateException.class, () -> module.createGame(source()));
        assertSame(stopped, module.programServicesState(), "No retry, rollback or hidden re-installation");
    }

    @Test void sourceBindingWithoutItsActualWorldRejectsBeforeTheProgramPrefix() throws Exception {
        var module = cold(0xC0); var before = module.programServicesState();
        assertThrows(IllegalStateException.class, () -> module.createGame(source()));
        assertSame(before, module.programServicesState());
    }

    @Test void fullAndCourseReplayRetainTheSameActualProgramAndNeverReexecuteStart() throws Exception {
        var h = load(cold(0xC0)); var value = h.module().programServicesState();
        var registry = h.mode().getRewindRegistry(); var full = registry.capture(); var course = registry.captureCourse();
        var frame = h.module().requirePlayerFrame(); var sourceBinding = frame.stageSource(); var world = h.mode().getWorldSession();
        var initialActor = frame.captureActor(); var initialShared = frame.captureShared();
        registry.restore(full); assertSame(value, h.module().programServicesState());
        assertCapturedValueEquals(initialActor, frame.captureActor()); assertCapturedValueEquals(initialShared, frame.captureShared());
        registry.restoreCourse(course); assertSame(value, h.module().programServicesState());
        assertCapturedValueEquals(initialActor, frame.captureActor()); assertCapturedValueEquals(initialShared, frame.captureShared());
        assertClearExactlyOnce(h.module());
        step(h); var actor = h.module().requirePlayerFrame().captureActor(); var shared = h.module().requirePlayerFrame().captureShared();
        registry.restore(full);
        assertSame(value, h.module().programServicesState()); step(h);
        assertCapturedValueEquals(actor, h.module().requirePlayerFrame().captureActor());
        assertCapturedValueEquals(shared, h.module().requirePlayerFrame().captureShared());
        registry.restoreCourse(course);
        assertSame(value, h.module().programServicesState()); step(h);
        assertCapturedValueEquals(actor, h.module().requirePlayerFrame().captureActor());
        assertCapturedValueEquals(shared, h.module().requirePlayerFrame().captureShared());
        assertSame(value.loads().installed().orElseThrow().driver(),
                h.module().programServicesState().loads().installed().orElseThrow().driver());
        assertSame(frame, h.module().requirePlayerFrame()); assertSame(sourceBinding, frame.stageSource());
        assertSame(world, h.mode().getWorldSession()); assertClearExactlyOnce(h.module());
    }

    @Test void sameProgramDeathReloadPreservesTheActualPreludeWithoutRepeatingIt() throws Exception {
        var h = load(cold(0xC0)); step(h); var value = h.module().programServicesState();
        var f = h.module().requirePlayerFrame(); var captured = f.captureActor(); var old = captured.state(); var m = old.motion();
        // Explicit component death/restart fixture; the ordinary loop performs the real reload.
        var motion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(),
                m.groundVelocity(), 8, m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(),
                m.animation(), m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(), m.stick(),
                m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var t = old.timers();
        f.restoreActor(new CdPlayerFrameRuntime.ActorState(captured.owner(), new CdPlayerBody.State(motion, old.animation(),
                old.header(), new CdPlayerBody.Timers(t.hurt(), t.invincibility(), t.speedShoes(), 1), old.history(),
                old.rotationDistance(), old.objectLinks(), old.rotationAngle()), Optional.empty()));
        h.module().requirePlayerActor().rebindAfterRestore();
        step(h); step(h);
        assertNotSame(f, h.module().requirePlayerFrame()); assertTrue(f.closed());
        assertSame(value, h.module().programServicesState());
        assertSame(value.loads().installed().orElseThrow().driver(),
                h.module().programServicesState().loads().installed().orElseThrow().driver());
    }

    @Test void oneProgramServiceLifetimeCannotBeReboundIntoAnotherWorld() throws Exception {
        var h = load(cold(0xC0)); var module = h.module(); var before = module.programServicesState();
        module.closeTerrainPlayer();
        var other = SessionManager.openGameplaySession(module, module, source(), null);
        assertNotSame(h.mode().getWorldSession(), other.getWorldSession());
        assertThrows(IllegalStateException.class, () -> module.createGame(source()));
        assertSame(before, module.programServicesState());
    }

    @Test void missingPcmCapabilityRetainsOnlyTheAuthenticatedClearPrefixAndCannotRetry() throws Exception {
        var original = source();
        // Real immutable original stage/MDINIT capability, deliberately absent later PCM capability.
        var withoutPcm = new CdGameDataSource(original.release(), original.assets(), original.fmSoundAssets());
        var module = cold(0x80); var initial = module.programServicesState();
        var failure = assertThrows(java.io.IOException.class, () -> load(module, withoutPcm));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        var stopped = module.programServicesState();
        assertEquals(List.of(SonicCdGameModule.ProgramStartCall.CLEAR_MAIN_VARIABLES), stopped.completedCalls());
        assertSame(initial.loads(), stopped.loads()); assertSame(initial.environment(), stopped.environment());
        assertEquals(new CdProgramInitialization.State(CdProgramInitialization.KnownBit.CLEAR,
                CdProgramInitialization.MarkerKnowledge.DIFFERS_FROM_INIT, new CdProgramInitialization.VersionCache(0, 0xFF),
                CdProgramInitialization.KnownBit.CLEAR), stopped.initialization());
        assertClearExactlyOnce(module); assertNull(actualField(module, "playerFrame")); assertNull(actualField(module, "playerActor"));
        assertNull(actualField(module, "programStartVideo")); assertNull(module.getGameService(NativeDynamicObjectPass.class));
        assertThrows(IllegalStateException.class, () -> module.createGame(withoutPcm));
        assertSame(stopped, module.programServicesState());
    }

    @Test void genuineWarpPastAndOldProgrammeReturnCarryClearKnowledgeWithoutReexecutingItsProducer() throws Exception {
        var h = load(cold(0x80)); var module = h.module(); var world = h.mode().getWorldSession();
        var initial = module.programServicesState().initialization(); assertClearExactlyOnce(module);
        for (int call = 1; call <= 7728; call++) step(h, TestSonicCdPastLoadingWorld.route(call));
        assertEquals(SonicCdGameModule.LevelProgramPhase.PAST_PENDING, module.levelProgramState().phase());
        assertCapturedValueEquals(initial, module.programServicesState().initialization()); assertClearExactlyOnce(module);
        step(h, TestSonicCdPastLoadingWorld.route(7729));
        for (int call = 7730; call <= 8278; call++) step(h, TestSonicCdPastLoadingWorld.route(call));
        assertEquals(SonicCdGameModule.PastLoadPhase.BEGIN_LEVEL, module.pastLoadState().phase());
        assertCapturedValueEquals(initial, module.programServicesState().initialization()); assertClearExactlyOnce(module);
        var visit = (SonicCdPastLoadingVisit) actualField(module, "pastLoading");
        var placement = (CdObjectPlacementRuntime) actualField(visit, "placement");
        var old = placement.pool().state(127); assertEquals(0, old.id()); var before = placement.capture();
        // Fixed Results object-only ingress, identical to the qualified old-exit fixture.
        // No actor, palette, programme, queue, art, camera or clock is seeded.
        var next = old.moveByte(ID, 0x3A).moveByte(ROUTINE, 8).moveByte(SPRITE_FLAGS, 0)
                .moveWord(X, 288).moveWord(RESULTS_DEST_X, 288).moveWord(RESULTS_SCREEN_Y_WORD, 204)
                .moveWord(Y, 0x2468).moveWord(Y_FRACTION, 0x1357).moveByte(PRIORITY, 0).moveByte(MAP_FRAME, 0)
                .moveWord(TILE, 0x83C4).moveLong(MAPPINGS,
                        module.pastLoadState().assets().view(CdR11bAssets.Asset.MAP_SPR_RESULTS).range().nativeAddress());
        placement.pool().write(next); assertEquals(before.status(), placement.status());
        assertArrayEquals(before.savedFlags().copy(), placement.capture().savedFlags().copy());
        for (var other : before.pool().slots()) if (other.identity().wholeIndex() != 127)
            assertEquals(other, placement.pool().state(other.identity().wholeIndex()));
        step(h); assertEquals(SonicCdGameModule.PastLoadPhase.OLD_BLACK_FADE, module.pastLoadState().phase());
        for (int wait = 0; wait < 22; wait++) { step(h); assertClearExactlyOnce(module); }
        assertEquals(SonicCdGameModule.PastLoadPhase.OLD_CLEAR_WAIT, module.pastLoadState().phase());
        step(h); assertEquals(SonicCdGameModule.PastLoadPhase.OLD_RETURNED, module.pastLoadState().phase());
        step(h); assertEquals(SonicCdGameModule.PastLoadPhase.OLD_IPX_RETURNED, module.pastLoadState().phase());
        assertCapturedValueEquals(initial, module.programServicesState().initialization()); assertClearExactlyOnce(module);
        assertSame(world, h.mode().getWorldSession()); assertFalse(module.requirePlayerFrame().closed());
        var frame = module.requirePlayerFrame(); var actualServices = module.programServicesState();
        var actor = frame.captureActor(); var shared = frame.captureShared(); var registry = h.mode().getRewindRegistry();
        var full = registry.capture(); var course = registry.captureCourse();
        registry.restore(full); assertSame(actualServices, module.programServicesState()); assertClearExactlyOnce(module);
        assertCapturedValueEquals(actor, frame.captureActor()); assertCapturedValueEquals(shared, frame.captureShared());
        registry.restoreCourse(course); assertSame(actualServices, module.programServicesState()); assertClearExactlyOnce(module);
        assertCapturedValueEquals(actor, frame.captureActor()); assertCapturedValueEquals(shared, frame.captureShared());
        // No next-act World or failed restore is claimed at the retained admission boundary.
    }

    private static void assertClearExactlyOnce(SonicCdGameModule module) {
        var actual = module.programServicesState();
        assertEquals(CdProgramInitialization.KnownBit.CLEAR, actual.initialization().palClearFlagBit0());
        assertFalse(CdProgramInitialization.requirePalClearFlagBit0(actual.initialization()));
        assertEquals(SonicCdGameModule.ProgramStartCall.CLEAR_MAIN_VARIABLES, actual.completedCalls().getFirst());
        assertEquals(1, actual.completedCalls().stream().filter(call -> call == SonicCdGameModule.ProgramStartCall.CLEAR_MAIN_VARIABLES).count());
    }

    private record Harness(SonicCdGameModule module, GameplayModeContext mode, GameLoop loop) { }
    private static SonicCdGameModule cold(int region) {
        return SonicCdGameModule.openingObjectsDevelopmentPlayerFromColdProgramStart(
                new CdProgramInitialization.ExecutionEnvironment(Optional.of(new CdProgramInitialization.ExecutionRegion(region))));
    }
    private static Harness load(SonicCdGameModule module) throws Exception { return load(module, source()); }
    private static Harness load(SonicCdGameModule module, CdGameDataSource source) throws Exception {
        var mode = SessionManager.openGameplaySession(module, module, source, null);
        GameplaySessionFactory.attachManagers(mode, EngineServices.current()); GameModuleRegistry.setCurrent(module);
        mode.getLevelManager().loadLevel(0, LevelLoadMode.FULL, SonicCdPlayerInitProfile.openingObjectsContext());
        var loop = new GameLoop(EngineServices.current()); loop.setGameplayMode(mode); loop.setInputHandler(new InputHandler());
        loop.setGameMode(GameMode.LEVEL);
        return new Harness(module, mode, loop);
    }
    private static void step(Harness h) { step(h, LogicalInputSnapshot.neutral()); }
    private static void step(Harness h, LogicalInputSnapshot input) {
        h.loop().getInputHandler().setLogicalOverride(input); h.loop().step();
    }
    private static CdGameDataSource source() throws Exception {
        if (usa != null) return usa;
        var root = System.getProperty("soniccd.media.root"); assertNotNull(root, "Original-USA media must be configured");
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); usa = CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a()); return usa;
        }
    }
}
