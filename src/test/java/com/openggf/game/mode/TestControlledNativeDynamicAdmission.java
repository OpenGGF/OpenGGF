package com.openggf.game.mode;

import com.openggf.GameLoop;
import com.openggf.LevelFrameResult;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.*;
import com.openggf.game.internal.ControlledVBlankOnlyOwner;
import com.openggf.game.internal.NativeDynamicObjectPass;
import com.openggf.game.internal.NativeFixedLevelObjectPass;
import com.openggf.game.internal.NativePlayableSlot;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.rewind.snapshot.TimerManagerSnapshot;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.EngineTiming;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.CdStockLaunch;
import com.openggf.game.soniccd.SonicCdGameModule;
import com.openggf.game.soniccd.SonicCdPlayerInitProfile;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.rings.RingManager;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import com.openggf.timer.TimerManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual controlled entry, engine clock and lifecycle; no synthetic PLC clock substitutes for V-int. */
@Isolated
class TestControlledNativeDynamicAdmission {
    private final List<String> events = new ArrayList<>();
    private final RecordingFade fade = new RecordingFade();
    private GameplayModeContext context;
    private LevelManager originalLevel;
    private LevelManager level;
    private NativeDynamicObjectPass binding;
    private int resolutions;
    private GameplayFrameDisposition disposition = GameplayFrameDisposition.ADVANCE_NATIVE_INPUT;
    private PlcLifecyclePhase heldPhase;
    private Runnable onDecision = () -> { };

    @AfterEach void close() {
        SessionManager.clear();
        TestEnvironment.resetAll();
    }

    private void load() {
        // Reuse the real manager/session fixture used by TestControlledVBlankOnlyOwner.
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        var base = GameModuleRegistry.getCurrent();
        binding = new Owner("first");
        var controller = new Decision();
        TestEnvironment.configureGameModuleFixture(new DelegatingGameModule(base, "test:dynamic-admission") {
            @Override public GameplayFrameController gameplayFrameController() { return controller; }
            @Override public TitleCardProvider getTitleCardProvider() { return null; }
            @Override public LevelEventProvider getLevelEventProvider() { return null; }
            @Override public LevelInitProfile getLevelInitProfile() { return new RecordingProfile(); }
            @Override public <T> T getGameService(Class<T> type) {
                if (type == NativeDynamicObjectPass.class) { resolutions++; return type.cast(binding); }
                if (type == NativeFixedLevelObjectPass.class)
                    return type.cast((NativeFixedLevelObjectPass) () -> events.add("reserved"));
                if (type == PlcLifecycleService.class) return type.cast(new RecordingService());
                return super.getGameService(type);
            }
        });
        context = TestEnvironment.activeGameplayMode();
        context.attachGameplayManagers(context.getCamera(), context.getTimerManager(),
                context.getGameStateManager(), fade, context.getRng(), context.getSolidExecutionRegistry());
        originalLevel = context.getLevelManager();
        originalLevel.setFrameCounter(41);
        level = replacementLevel();
        attachLevel(level);
        var player = new NativeProbe();
        context.getSpriteManager().addSprite(player);
        context.getCamera().setFocusedSprite(player);
        context.getLevelManager().setFrameCounter(41);
        context.getSpriteManager().setFrameCounter(17);
        context.getTimerManager().restore(timerState());
        context.setHardwareTimingBoundaryObserver(boundary -> events.add("hardware:" + boundary));
        EngineTiming.vIntRunCounter().seed(99);
        assertNull(level.getObjectManager());
        assertNull(level.getRingManager());
        assertEquals(99, EngineTiming.vIntRunCounter().value());
        events.clear();
        resolutions = 0;
    }

    private static TimerManagerSnapshot timerState() {
        return new TimerManagerSnapshot(Map.of("admission", new TimerManagerSnapshot.TimerState("admission", 9)));
    }

    private void attachLevel(LevelManager replacement) {
        context.attachLevelManagers(context.getWaterSystem(), context.getParallaxManager(),
                context.getTerrainCollisionManager(), context.getCollisionSystem(),
                context.getSpriteManager(), replacement);
    }

    private LevelManager replacementLevel() {
        var replacement = spy(originalLevel);
        doReturn(true).when(replacement).objectsExecuteAfterPlayerPhysics();
        doAnswer(call -> { events.add("camera"); return call.callRealMethod(); })
                .when(replacement).advanceCameraDrivenScrollForFrame();
        return replacement;
    }

    private LevelFrameResult step(Runnable beforeGameplay) {
        return ControlledFrameRuntime.step(context, new InputHandler(), LogicalInputSnapshot.neutral(),
                true, beforeGameplay);
    }

    private LevelFrameResult step() { return step(() -> events.add("before-gameplay")); }

    private void assertInitialRejection() {
        var camera = context.getCamera().capture();
        var timers = context.getTimerManager().capture();
        long vint = EngineTiming.vIntRunCounter().value();
        assertThrows(IllegalStateException.class, this::step);
        // ControlledFrameRuntime's represented V-int used to precede execute's guard.
        // VIntRunCounter.serviceRepresentedVBlank (89-94) increments this real owner
        // whenever the fixture's null ObjectManager supplies NO_OBJECT_CLOCK.
        assertAll(
                () -> assertEquals(vint, EngineTiming.vIntRunCounter().value(), "real represented V-int"),
                () -> assertEquals(41, level.getFrameCounter(), "level clock"),
                () -> assertEquals(17, context.getSpriteManager().getFrameCounter(), "player dispatch"),
                () -> assertEquals(timers, context.getTimerManager().capture(), "ordinary timers"),
                () -> assertEquals(camera, context.getCamera().capture(), "camera"),
                () -> assertEquals(List.of("decision"), events,
                        "no beforeGameplay, fade/lifecycle/native service, object phase or camera"),
                () -> assertEquals(1, resolutions));
    }

    @Test void legacyOrderRejectsBeforeRepresentedVIntAndGameplayEffects() {
        load();
        doReturn(false).when(level).objectsExecuteAfterPlayerPhysics();
        // Latching this real lifecycle would service PLC even before execute's claim.
        try (var blocking = context.plcFrameLifecycle().beginNativeBlockingFade()) {
            assertInitialRejection();
        }
    }

    @Test void competingRingManagerRejectsBeforeRepresentedVIntAndGameplayEffects() {
        load();
        doReturn(mock(RingManager.class)).when(level).getRingManager();
        assertInitialRejection();
    }

    @Test void competingObjectManagerRejectsBeforeGameplayEffects() {
        load();
        var objects = new ObjectManager(List.of(), null, -1, null, null);
        objects.initVblaCounter(99);
        doReturn(objects).when(level).getObjectManager();
        assertInitialRejection();
        assertEquals(99, objects.getVblaCounter());
    }

    @Test void validOwnerResolvesOnceAndSurvivesBindingChangeUntilBothPhasesFinish() {
        load();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, step(() -> {
            events.add("before-gameplay");
            binding = new Owner("next");
        }));
        assertEquals(1, resolutions);
        assertEquals(List.of("first:spawn", "player", "reserved", "first:dynamic", "camera"), objectEvents());
        assertEquals(100, EngineTiming.vIntRunCounter().value());
        assertEquals(42, level.getFrameCounter());
        assertEquals(8, context.getTimerManager().getTimerForCode("admission").getTicks());
        events.clear();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, step());
        assertEquals(2, resolutions, "admission is local to one ordinary cycle");
        assertEquals(List.of("next:spawn", "player", "reserved", "next:dynamic", "camera"), objectEvents());
    }

    @Test void resolvedAbsenceDoesNotBecomeASecondLookupAfterBeforeGameplay() {
        load();
        binding = null;
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, step(() -> binding = new Owner("next")));
        assertEquals(1, resolutions, "resolved absence differs from no admission yet");
        assertEquals(List.of("player", "reserved", "camera"), objectEvents());
        events.clear();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, step());
        assertEquals(2, resolutions);
        assertEquals(List.of("next:spawn", "player", "reserved", "next:dynamic", "camera"), objectEvents());
    }

    @Test void controllerChangesAreAdmittedBeforeTheOrdinaryClock() {
        load();
        onDecision = () -> doReturn(false).when(level).objectsExecuteAfterPlayerPhysics();
        assertInitialRejection();
    }

    @Test void beforeGameplayCannotCarryABoundOwnerIntoAReplacementManager() {
        load();
        assertThrows(IllegalStateException.class, () -> step(() -> attachLevel(replacementLevel())));
        assertEquals(99, EngineTiming.vIntRunCounter().value());
        assertEquals(timerState(), context.getTimerManager().capture());
        assertEquals(List.of("decision"), events);
        assertEquals(1, resolutions);
    }

    @Test void fadeReplacementCannotRunTheOldBoundOwnerOrIncomingTimers() {
        load();
        fade.completion = () -> attachLevel(replacementLevel());
        assertThrows(IllegalStateException.class, this::step);
        // Fade is after the represented V-int. There is no rollback of valid earlier work.
        assertEquals(100, EngineTiming.vIntRunCounter().value());
        assertEquals(41, context.getLevelManager().getFrameCounter());
        assertEquals(timerState(), context.getTimerManager().capture());
        assertEquals(List.of("decision", "before-gameplay", "fade"), events);
        assertEquals(1, resolutions);
    }

    @Test void unboundFadeReplacementKeepsTheFreshManagersAndOrdinaryPath() {
        load();
        binding = null;
        var freshTimers = new TimerManager();
        freshTimers.restore(timerState());
        var oldTimers = context.getTimerManager();
        fade.completion = () -> {
            attachLevel(replacementLevel());
            context.attachGameplayManagers(context.getCamera(), freshTimers,
                    context.getGameStateManager(), fade, context.getRng(), context.getSolidExecutionRegistry());
        };
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, step());
        assertEquals(2, resolutions, "replacement manager gets its own admission, including absence");
        assertEquals(9, oldTimers.getTimerForCode("admission").getTicks());
        assertEquals(8, freshTimers.getTimerForCode("admission").getTicks());
        assertEquals(List.of("player", "reserved", "camera"), objectEvents());
    }

    @Test void holdPauseAndLagDoNotResolveOrValidateAnOrdinaryOwner() {
        load();
        doReturn(false).when(level).objectsExecuteAfterPlayerPhysics();
        disposition = GameplayFrameDisposition.HOLD;
        assertEquals(LevelFrameResult.HELD, step());
        assertEquals(99, EngineTiming.vIntRunCounter().value());
        assertEquals(List.of("decision", "after:false"), events);
        for (var phase : List.of(PlcLifecyclePhase.NORMAL_PAUSE, PlcLifecyclePhase.LAG)) {
            events.clear();
            heldPhase = phase;
            assertEquals(LevelFrameResult.HELD, step());
            assertTrue(events.contains("service:" + phase));
            assertTrue(objectEvents().isEmpty());
        }
        assertEquals(101, EngineTiming.vIntRunCounter().value());
        assertEquals(41, level.getFrameCounter());
        assertEquals(timerState(), context.getTimerManager().capture());
        assertEquals(0, resolutions);
    }

    @Test void setupDoesNotResolveAnOrdinaryOwner() {
        load();
        // Existing one-shot setup contract, not a new CD setup phase.
        doReturn(true).when(level).consumePendingInitialProcessSpritesPass();
        assertEquals(LevelFrameResult.SETUP_ONLY, step());
        assertEquals(99, EngineTiming.vIntRunCounter().value());
        assertEquals(41, level.getFrameCounter());
        assertEquals(timerState(), context.getTimerManager().capture());
        assertTrue(events.isEmpty());
        assertEquals(0, resolutions);
    }

    @Test void actualUnboundCdControllerStillMovesRightWithoutInventingSetup() throws Exception {
        // Same authenticated production decoder/load recipe as TestSonicCdTerrainPlayerLoad.
        // CD rejects policy decorators; use its actual unwrapped controller for this control.
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Supply soniccd.media.root for retail CD media");
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
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            cue = cues.getFirst();
        }
        var source = CdStockLaunch.prepare(cue, CdStockLaunch.Request.r11a());
        var module = SonicCdGameModule.terrainOnlyDevelopmentPlayer();
        var mode = SessionManager.openGameplaySession(module, module, source, null);
        GameplaySessionFactory.attachManagers(mode, EngineServices.current());
        var loop = new GameLoop(EngineServices.current());
        loop.setGameplayMode(mode);
        loop.setInputHandler(new InputHandler());
        loop.setGameMode(GameMode.LEVEL);
        mode.getLevelManager().loadLevel(0, LevelLoadMode.FULL, SonicCdPlayerInitProfile.terrainPlayerContext());
        assertNull(module.getGameService(NativeDynamicObjectPass.class));
        assertFalse(mode.getLevelManager().hasPendingInitialProcessSpritesPass());
        assertFalse(ControlledFrameRuntime.prepareSetup(mode));
        var player = (AbstractPlayableSprite) mode.getSpriteManager().getSprite("sonic");
        var input = loop.getInputHandler();
        for (int row = 0; row < 3; row++) assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                ControlledFrameRuntime.step(mode, input, LogicalInputSnapshot.neutral(), true));
        int beforeX = player.getCentreX();
        long beforeVInt = EngineTiming.vIntRunCounter().value();
        var right = LogicalInputSnapshot.ofPlayers(PlayerInputState.of(AbstractPlayableSprite.INPUT_RIGHT,
                0, 0, 0, false, false), PlayerInputState.neutral());
        for (int row = 0; row < 12; row++) assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                ControlledFrameRuntime.step(mode, input, right, true));
        assertTrue(player.getCentreX() > beforeX);
        assertEquals(beforeVInt + 12, EngineTiming.vIntRunCounter().value());
        assertEquals(15, mode.getLevelManager().getFrameCounter());
        assertNull(mode.getLevelManager().getObjectManager());
        assertNull(mode.getLevelManager().getRingManager());
    }

    private List<String> objectEvents() {
        return events.stream().filter(event -> event.endsWith(":spawn") || event.endsWith(":dynamic")
                || List.of("player", "reserved", "camera").contains(event)).toList();
    }

    private final class Decision implements GameplayFrameController, ControlledVBlankOnlyOwner {
        @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) {
            throw new AssertionError("disposition owns the row");
        }
        @Override public GameplayFrameDisposition beforeFrame(CourseControl course, LogicalInputSnapshot input) {
            events.add("decision"); onDecision.run(); return disposition;
        }
        @Override public PlcLifecyclePhase heldVBlankPhase() { return heldPhase; }
        @Override public void afterTick(CourseControl course, boolean advanced) { events.add("after:" + advanced); }
    }

    private final class Owner implements NativeDynamicObjectPass {
        private final String name;
        Owner(String name) { this.name = name; }
        @Override public void spawnBeforeReservedSlots() { events.add(name + ":spawn"); }
        @Override public void runDynamicSlotsAfterReservedSlots() { events.add(name + ":dynamic"); }
    }

    private final class RecordingService implements PlcLifecycleService {
        @Override public void serviceVBlank(PlcLifecyclePhase phase) { events.add("service:" + phase); }
        @Override public boolean hasPreparationBoundary(PlcLifecyclePhase phase) { return false; }
        @Override public void prepareAfterLoop(PlcLifecyclePhase phase) { events.add("prepare:" + phase); }
    }

    private final class RecordingProfile implements LevelInitProfile {
        @Override public void serviceLevelLoadVBlank() { events.add("game-vblank"); }
        @Override public List<InitStep> levelLoadSteps(LevelLoadContext context) { return List.of(); }
        @Override public List<InitStep> levelTeardownSteps() { return List.of(); }
        @Override public List<InitStep> perTestResetSteps() { return List.of(); }
        @Override public List<StaticFixup> postTeardownFixups() { return List.of(); }
    }

    private final class RecordingFade extends FadeManager {
        Runnable completion;
        @Override public void update() {
            events.add("fade");
            super.update();
            var callback = completion;
            completion = null;
            if (callback != null) callback.run();
        }
    }

    private final class NativeProbe extends AbstractPlayableSprite implements NativePlayableSlot {
        NativeProbe() { super("main", (short) 0, (short) 0); }
        @Override public void runPlayableSlot(LevelManager manager, int frames) { events.add("player"); }
        @Override protected void defineSpeeds() { }
        @Override protected void createSensorLines() {
            groundSensors = new Sensor[0]; ceilingSensors = new Sensor[0]; pushSensors = new Sensor[0];
        }
        @Override public void draw() { fail("controlled step must not render"); }
    }
}
