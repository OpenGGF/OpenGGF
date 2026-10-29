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

/** Earlier explicit DEV initial conditions and real source/World ownership; not retail boot. */
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
        assertEquals(0xC0, value.environment().region().orElseThrow().versionC0());
        assertTrue(value.completedCalls().isEmpty());
        assertThrows(NullPointerException.class,
                () -> SonicCdGameModule.openingObjectsDevelopmentPlayerFromColdProgramStart(null));
    }

    @Test void actualWorldRunsOrderedStartWithAnExplicitRegionIndependentFromTheUsaDisc() throws Exception {
        for (int region : new int[]{0, 0x40, 0x80, 0xC0}) {
            var h = load(cold(region)); var module = h.module(); var value = module.programServicesState();
            assertEquals(List.of(SonicCdGameModule.ProgramStartCall.LOAD_LEVEL_PCM,
                    SonicCdGameModule.ProgramStartCall.WRITE_VERSION_CACHE,
                    SonicCdGameModule.ProgramStartCall.WRITE_INIT_MARKER,
                    SonicCdGameModule.ProgramStartCall.INIT_VDP,
                    SonicCdGameModule.ProgramStartCall.INIT_CONTROLLERS,
                    SonicCdGameModule.ProgramStartCall.DECLARED_BEGIN_LEVEL_INGRESS), value.completedCalls());
            assertEquals(CdProgramInitialization.KnownBit.SET, value.initialization().controllerSetupBit());
            assertEquals(CdProgramInitialization.MarkerKnowledge.EQUALS_INIT, value.initialization().initMarker());
            assertEquals(new CdProgramInitialization.VersionCache(region, 0xFF), value.initialization().versionCache());
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
        assertEquals(List.of(SonicCdGameModule.ProgramStartCall.LOAD_LEVEL_PCM), stopped.completedCalls());
        assertEquals(SonicCdProgramLoadServices.PcmKnowledge.LOADED, stopped.loads().pcmKnowledge());
        assertEquals(CdProgramInitialization.KnownBit.CLEAR, stopped.initialization().controllerSetupBit());
        assertEquals(CdProgramInitialization.MarkerKnowledge.UNKNOWN, stopped.initialization().initMarker());
        assertEquals(new CdProgramInitialization.VersionCache(0, 0), stopped.initialization().versionCache());
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
        step(h); var actor = h.module().requirePlayerFrame().captureActor(); var shared = h.module().requirePlayerFrame().captureShared();
        registry.restore(full);
        assertSame(value, h.module().programServicesState()); step(h);
        assertEquals(actor, h.module().requirePlayerFrame().captureActor());
        assertEquals(shared, h.module().requirePlayerFrame().captureShared());
        registry.restoreCourse(course);
        assertSame(value, h.module().programServicesState()); step(h);
        assertEquals(actor, h.module().requirePlayerFrame().captureActor());
        assertEquals(shared, h.module().requirePlayerFrame().captureShared());
        assertSame(value.loads().installed().orElseThrow().driver(),
                h.module().programServicesState().loads().installed().orElseThrow().driver());
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

    private record Harness(SonicCdGameModule module, GameplayModeContext mode, GameLoop loop) { }
    private static SonicCdGameModule cold(int region) {
        return SonicCdGameModule.openingObjectsDevelopmentPlayerFromColdProgramStart(
                new CdProgramInitialization.ExecutionEnvironment(Optional.of(new CdProgramInitialization.ExecutionRegion(region))));
    }
    private static Harness load(SonicCdGameModule module) throws Exception {
        var mode = SessionManager.openGameplaySession(module, module, source(), null);
        GameplaySessionFactory.attachManagers(mode, EngineServices.current()); GameModuleRegistry.setCurrent(module);
        var loop = new GameLoop(EngineServices.current()); loop.setGameplayMode(mode); loop.setInputHandler(new InputHandler());
        loop.setGameMode(GameMode.LEVEL);
        mode.getLevelManager().loadLevel(0, LevelLoadMode.FULL, SonicCdPlayerInitProfile.openingObjectsContext());
        return new Harness(module, mode, loop);
    }
    private static void step(Harness h) {
        h.loop().getInputHandler().setLogicalOverride(LogicalInputSnapshot.neutral()); h.loop().step();
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
