package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.control.InputHandler;
import com.openggf.data.Rom;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.PlaybackSessionScope;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.OscillationManager;
import com.openggf.game.internal.BlockingLevelEntryFade;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.level.LevelManager;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Actual S1 source events, GameLoop consumption, and native title-card release. */
@RequiresRom(SonicGame.SONIC_1)
class TestGameLoopBlockingLevelEntryFadeS1 {
    private GameLoop activeLoop;

    @AfterEach
    void tearDown() {
        try {
            if (activeLoop != null) activeLoop.closePresence();
        } finally {
            activeLoop = null;
            SessionManager.clear();
        }
    }

    @ParameterizedTest(name = "SBZ act {0} event to {1}:{2}")
    @CsvSource({"1, 5, 2, 0x0B80, 0x0000, true", "2, 6, 0, 0x2140, 0x05AC, false"})
    void nativeEventFinishesItsSourcePassThenConsumedEntryHoldsUntilDestination(
            int sourceAct, int targetZone, int targetAct,
            int targetX, int targetY, boolean targetAir) throws IOException {
        Harness h = open(sourceAct, false);
        assertTrue(GameServices.module().getLevelInitProfile() instanceof BlockingLevelEntryFade);
        assertFalse(h.level.isLevelInactiveForTransition());
        assertFalse(h.level.getTransitions().hasPendingLevelExit());
        RewindSnapshottable<Object> transitions = transitionAdapter(h.level);
        Object idle = transitions.capture();

        positionForNativeEvent(h, sourceAct);
        int sourceObjects = h.level.getObjectManager().getFrameCounter();
        int sourceOscillatorFrame = OscillationManager.snapshot().lastFrame();
        List<String> sections = new ArrayList<>();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME, canonicalFrame(h, sections));

        assertEquals(sourceObjects + 1, h.level.getObjectManager().getFrameCounter(),
                "triggering player/object dispatch must finish");
        assertTrue(sections.contains("camera-scroll"), "source pass still reaches DeformBgLayer camera");
        assertTrue(sections.contains("fixed-objects"), "source pass still reaches post-camera fixed objects");
        assertNotEquals(sourceOscillatorFrame, OscillationManager.snapshot().lastFrame(),
                "request-producing frame still reaches the ordinary oscillator loop tail");
        assertTrue(h.level.getTransitions().hasPendingLevelExit(), "actual source event queued an exit");
        assertEquals(targetZone, h.level.getRequestedZone());
        assertEquals(targetAct, h.level.getRequestedAct());
        assertFalse(h.level.isLevelInactiveForTransition(), "event publication alone must not freeze its pass");
        assertTrue(h.player.isControlLocked(), "native event retains its control lock");
        assertFalse(h.player.getDead(), "SBZ2 interception must suppress pit death");
        Object queued = transitions.capture();

        // Restore the real queued edge before any live fade token exists.
        assertTrue(h.level.consumeZoneActRequest());
        h.level.setLevelInactiveForTransition(true);
        transitions.restore(queued);
        assertEquals(queued, transitions.capture());
        assertFalse(h.level.isLevelInactiveForTransition());
        h.loop.step();
        assertTrue(h.level.isLevelInactiveForTransition(), "GameLoop consumption starts blocking entry");
        assertTrue(h.mode.getFadeManager().isActive());
        assertTrue(h.mode.getFadeManager().hasPendingCompletion());
        Object blocked = transitions.capture();

        // Destructively restore only the existing coordinator adapter. A whole
        // registry/fade restore would intentionally orphan this callback/token.
        transitions.restore(idle);
        assertFalse(h.level.isLevelInactiveForTransition());
        h.level.requestZoneAndAct(0, 0, false);
        transitions.restore(blocked);
        assertEquals(blocked, transitions.capture(), "restore consumed state as well as inactive flag");
        assertTrue(h.level.isLevelInactiveForTransition());
        assertFalse(h.level.consumeZoneActRequest(), "restored blocked state must not queue a second fade");

        Pose held = pose(h.player);
        int heldObjects = h.level.getObjectManager().getFrameCounter();
        for (int frame = 0; frame < 4; frame++) {
            int fadeFrame = h.mode.getFadeManager().getFrameCount();
            float redDarkness = h.mode.getFadeManager().getFadeColor()[0];
            h.loop.step();
            assertEquals(held, pose(h.player), "held player physics/animation at fade frame " + frame);
            assertEquals(heldObjects, h.level.getObjectManager().getFrameCounter(), "held object dispatch");
            assertTrue(h.mode.getFadeManager().getFrameCount() > fadeFrame, "live fade clock progresses");
            assertTrue(h.mode.getFadeManager().getFadeColor()[0] > redDarkness, "live black fade progresses");
            assertEquals(5, h.level.getCurrentZone());
            assertEquals(sourceAct, h.level.getCurrentAct());
        }

        // Whole-run headless handoffs request their destination title through
        // this production input-scheduling boundary. An ordinary positioned
        // headless load intentionally omits it; no event/reentry flags change.
        Bk2Movie neutralMovie = neutralInputMovie();
        var playback = GameServices.playbackDebug();
        try (var playbackScope = new PlaybackSessionScope(playback, neutralMovie)) {
            playback.scheduleSessionAtNextLevelLoad(neutralMovie, 0);
            assertTrue(playback.hasScheduledLevelLoadSession());
            boolean loaded = false;
            boolean titleSeen = false;
            boolean released = false;
            for (int frame = 0; frame < 600; frame++) {
                h.loop.step();
                if (h.level.getCurrentZone() == targetZone && h.level.getCurrentAct() == targetAct) {
                    if (!loaded) {
                        loaded = true;
                        assertFalse(playback.hasScheduledLevelLoadSession(), "load activated the scheduled ordinary input");
                        assertEquals(neutralMovie.getFrameCount(), playback.getMovieFrameCount());
                        assertFalse(h.level.isLevelInactiveForTransition(), "destination load releases source hold");
                        assertEquals(targetX, h.player.getCentreX() & 0xFFFF, "ROM destination start X");
                        assertEquals(targetY, h.player.getCentreY() & 0xFFFF, "ROM destination start Y");
                    }
                    titleSeen |= h.loop.getCurrentGameMode() == GameMode.TITLE_CARD;
                    if (titleSeen && h.loop.getCurrentGameMode() == GameMode.LEVEL) {
                        released = true;
                        assertFalse(h.level.isLevelInactiveForTransition());
                        assertFalse(h.player.isControlLocked(), "destination title releases control");
                        assertEquals(targetAir, h.player.getAir(), "native player release prelude resolves spawn terrain");
                        assertEquals(0, h.level.getFrameCounter(), "prelude precedes ordinary level frames");
                        assertEquals(1, h.level.getObjectManager().getFrameCounter(), "native release object prelude ran once");
                        break;
                    }
                }
            }
            assertTrue(loaded, "destination loaded through real fade callback");
            assertTrue(titleSeen, "destination ran its real title-card lifecycle");
            assertTrue(released, "destination player/object prelude completed");
            int beforeForward = h.level.getObjectManager().getFrameCounter();
            h.loop.step();
            assertEquals(beforeForward + 1, h.level.getObjectManager().getFrameCounter(),
                    "ordinary destination execution resumes after release");
        }
    }

    @Test
    void unoptedProfileWithSameTwentyTwoFrameEntryDurationDoesNotHoldGameplay() throws IOException {
        Harness h = open(0, true);
        LevelInitProfile profile = GameServices.module().getLevelInitProfile();
        assertEquals(22, profile.preLevelFadeOutFrames());
        assertFalse(profile instanceof BlockingLevelEntryFade);
        h.player.setAir(true);
        h.player.setYSpeed((short) -0x200);
        h.level.requestZoneAndAct(0, 1, false);
        assertFalse(h.level.isLevelInactiveForTransition());
        h.loop.step();
        assertTrue(h.mode.getFadeManager().hasPendingCompletion());
        assertFalse(h.level.isLevelInactiveForTransition(), "duration does not imply blocking capability");
        int before = h.level.getObjectManager().getFrameCounter();
        Pose initial = pose(h.player);
        for (int frame = 0; frame < 4; frame++) {
            h.loop.step();
            assertFalse(h.level.isLevelInactiveForTransition());
        }
        assertEquals(before + 4, h.level.getObjectManager().getFrameCounter(),
                "ordinary callback-bearing fade keeps unopted gameplay ticking");
        assertNotEquals(initial, pose(h.player), "production physics/animation remain live");
        assertTrue(h.mode.getFadeManager().getFrameCount() >= 4);
    }

    @Test
    void ordinaryCallbackFadeDoesNotApplyTheNativeEntryHold() throws IOException {
        Harness h = open(0, false);
        assertTrue(GameServices.module().getLevelInitProfile() instanceof BlockingLevelEntryFade);
        h.player.setAir(true);
        h.player.setYSpeed((short) -0x200);
        h.loop.fadeOutTo(() -> { });
        assertTrue(h.mode.getFadeManager().hasPendingCompletion());
        int before = h.level.getObjectManager().getFrameCounter();
        Pose initial = pose(h.player);
        for (int frame = 0; frame < 4; frame++) {
            h.loop.step();
            assertFalse(h.level.isLevelInactiveForTransition());
        }
        assertEquals(before + 4, h.level.getObjectManager().getFrameCounter());
        assertNotEquals(initial, pose(h.player), "native entry capability does not freeze ordinary fades");
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void sonicTwoConsumedEntryPreservesFalseDeactivationPolicy() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        HeadlessTestFixture fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        GameplayModeContext mode = fixture.gameplayMode();
        LevelManager level = mode.getLevelManager();
        level.consumeTitleCardRequest();
        assertEquals(22, GameServices.module().getLevelInitProfile().preLevelFadeOutFrames());
        assertFalse(GameServices.module().getLevelInitProfile() instanceof BlockingLevelEntryFade);
        InputHandler input = new InputHandler();
        GameLoop loop = new GameLoop(input);
        activeLoop = loop;
        loop.setGameplayMode(mode);
        loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
        completeSourceSetup(new Harness(mode, level, fixture.sprite(), input, loop));
        level.requestZoneAndAct(0, 1, false);
        loop.step();
        assertTrue(mode.getFadeManager().hasPendingCompletion());
        assertFalse(level.isLevelInactiveForTransition());
        int before = level.getObjectManager().getFrameCounter();
        for (int frame = 0; frame < 4; frame++) {
            loop.step();
            assertFalse(level.isLevelInactiveForTransition());
        }
        assertEquals(before + 4, level.getObjectManager().getFrameCounter(),
                "S2 keeps its existing live object loop under an unblocked entry fade");
    }

    private Harness open(int act, boolean unopted) throws IOException {
        Rom rom = GameServices.rom().getRom();
        assertNotNull(rom, "@RequiresRom must install the actual S1 ROM");
        Sonic1GameModule module = new Sonic1GameModule() {
            private LevelInitProfile unoptedProfile;
            @Override public LevelInitProfile getLevelInitProfile() {
                LevelInitProfile nativeProfile = super.getLevelInitProfile();
                if (!unopted) return nativeProfile;
                if (unoptedProfile == null) {
                    // A deliberately different profile contract: delegate every
                    // native step but expose only the public LevelInitProfile.
                    unoptedProfile = (LevelInitProfile) Proxy.newProxyInstance(
                            LevelInitProfile.class.getClassLoader(), new Class<?>[]{LevelInitProfile.class},
                            (proxy, method, args) -> {
                                try { return method.invoke(nativeProfile, args); }
                                catch (InvocationTargetException error) { throw error.getCause(); }
                            });
                }
                return unoptedProfile;
            }
        };
        TestEnvironment.configureGameModuleFixture(module);
        GameServices.rom().setRom(rom);
        HeadlessTestFixture fixture = HeadlessTestFixture.builder().withZoneAndAct(5, act).build();
        GameplayModeContext mode = fixture.gameplayMode();
        LevelManager level = mode.getLevelManager();
        // A positioned interaction contract starts after the initial title.
        // Destination title cards below are consumed by GameLoop normally.
        level.consumeTitleCardRequest();
        InputHandler input = new InputHandler(); // default source is no-device
        GameLoop loop = new GameLoop(input);
        activeLoop = loop;
        loop.setGameplayMode(mode);
        loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
        Harness h = new Harness(mode, level, fixture.sprite(), input, loop);
        completeSourceSetup(h);
        return h;
    }

    private static void completeSourceSetup(Harness h) {
        for (int frame = 0; frame < 2; frame++) {
            LevelFrameResult result;
            int setups = 0;
            do {
                result = canonicalFrame(h, new ArrayList<>());
                assertTrue(++setups <= 8, "bounded initial setup");
            } while (result == LevelFrameResult.SETUP_ONLY);
            assertEquals(LevelFrameResult.GAMEPLAY_FRAME, result);
        }
    }

    private static Bk2Movie neutralInputMovie() {
        List<Bk2FrameInput> frames = new ArrayList<>();
        for (int frame = 0; frame < 32; frame++) {
            frames.add(new Bk2FrameInput(frame, 0, 0, false, 0, 0, false, ""));
        }
        // The path is a diagnostic label; no movie file or trace data is read.
        return new Bk2Movie(Path.of("synthetic-s1-entry-fade-neutral.bk2"), "",
                Map.of(), frames, 1);
    }

    private static void positionForNativeEvent(Harness h, int act) {
        Camera camera = h.mode.getCamera();
        camera.setLevelStarted(true);
        camera.setMinX((short) (act == 1 ? 0x2000 : 0x0D00));
        camera.setMaxX((short) 0x2300);
        camera.setX((short) (act == 1 ? 0x2000 : 0x0D00));
        camera.setMaxY((short) 0x0510);
        camera.setMaxYTarget((short) 0x0510);
        NativePositionOps.writeXPosResetSubpixel(h.player, act == 1 ? 0x2080 : 0x0D80);
        NativePositionOps.writeYPosResetSubpixel(h.player, act == 1 ? 0x0800 : 0x0010);
        h.player.setSubpixelRaw(0x4000, 0x6000);
        h.player.setAir(true);
        h.player.setRolling(false);
        h.player.setControlLocked(false);
        h.player.setXSpeed((short) 0x100);
        h.player.setYSpeed((short) (act == 1 ? 0x100 : 0));
        h.player.setGSpeed((short) 0);
    }

    private static LevelFrameResult canonicalFrame(Harness h, List<String> sections) {
        return h.mode.plcFrameLifecycle().runLogicalIteration(
                h.mode.getFadeManager()::update,
                frame -> LevelFrameStep.execute(LevelFrameContext.from(h.mode), frame,
                        PlcLifecyclePhase.ORDINARY_LEVEL, h.level, h.mode.getCamera(),
                        () -> h.mode.getSpriteManager().update(h.input),
                        (name, step) -> {
                            sections.add(name);
                            step.run();
                        }));
    }

    @SuppressWarnings("unchecked")
    private static RewindSnapshottable<Object> transitionAdapter(LevelManager level) {
        return (RewindSnapshottable<Object>) level.levelTransitionRewindSnapshottable();
    }

    private static Pose pose(AbstractPlayableSprite player) {
        return new Pose(player.getCentreX(), player.getXSubpixelRaw(), player.getCentreY(),
                player.getYSubpixelRaw(), player.getXSpeed(), player.getYSpeed(), player.getGSpeed(),
                player.getAir(), player.getRolling(), player.getAnimationId(), player.getMappingFrame(),
                player.getAnimationFrameIndex(), player.getAnimationTick(), player.getAnimationFrameCount());
    }

    private record Harness(GameplayModeContext mode, LevelManager level, AbstractPlayableSprite player,
                           InputHandler input, GameLoop loop) { }
    private record Pose(int x, int xSub, int y, int ySub, int vx, int vy, int inertia,
                        boolean air, boolean rolling, int animation, int mapping,
                        int animationFrame, int animationTick, int animationCount) { }
}
