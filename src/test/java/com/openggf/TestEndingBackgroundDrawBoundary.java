package com.openggf;

import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.rewind.LevelRewindSnapshotAdapter;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/** Real S2 ending ingress, live advance/draw owners and original-ROM DEZ scroll under EGL. */
@Isolated
@RequiresRom(SonicGame.SONIC_2)
class TestEndingBackgroundDrawBoundary {
    private GameplayCaptureSession session;
    private Engine engine;
    private GameLoop loop;
    private InputHandler input;

    @BeforeEach
    void open() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        session = new GameplayCaptureSession(settings);
        session.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(), 10, 0, settings);
        session.loop().closePresence();
        engine = new Engine();
        input = new InputHandler();
        engine.setInputHandler(input);
        loop = engine.getGameLoop();
        var bind = Engine.class.getDeclaredMethod("bindGameplayMode", GameplayModeContext.class);
        bind.setAccessible(true);
        bind.invoke(engine, SessionManager.getCurrentGameplayMode());
        var reshape = Engine.class.getDeclaredMethod("reshape", int.class, int.class);
        reshape.setAccessible(true);
        reshape.invoke(engine, 320, 224);
        GameServices.graphics().setProjectionMatrixBuffer(engine.getProjectionMatrixBuffer());
        GameServices.configuration().setConfigValue(SonicConfiguration.FRAME_STEP_KEY, GLFW_KEY_F8);
        GameServices.level().consumeTitleCardRequest();
        GameServices.fade().cancel();
        ending().startEndingFade(); // Actual native provider initialize; no phase/scroll hydration.
        assertEquals(GameMode.ENDING_CUTSCENE, loop.getCurrentGameMode());
        assertFalse(loop.getEndingProvider().needsLevelBackground());
        int initial = GameServices.level().getFrameCounter();
        engine.advanceFrame();
        assertEquals(initial, GameServices.level().getFrameCounter(), "photo-only presentation owns no DEZ scroll");
        // Four native photo cycles each hold $180 + $80 ticks, plus transition passes.
        for (int ticks = 0; ticks < 2200 && !loop.getEndingProvider().needsLevelBackground(); ticks++) {
            engine.advanceFrame();
        }
        assertTrue(loop.getEndingProvider().needsLevelBackground(), "native photo sequence must reach sky reveal");
        // Reveal real star/character colours while staying inside the $40-tick constant-BG-Y hold.
        for (int tick = 0; tick < 16; tick++) engine.advanceFrame();
    }

    @AfterEach
    void close() {
        try {
            if (loop != null) loop.closePresence();
            if (session != null) session.close();
        } finally {
            Engine.clearGlobalInstance();
        }
    }

    @Test
    void repeatedDrawsNeitherAdvanceLogicalScrollNorProduceAudio() {
        var level = GameServices.level();
        var scroll = SessionManager.getCurrentGameplayMode().getParallaxManager();
        int frame = level.getFrameCounter();
        int[] rows = scroll.getHScroll().clone();
        var audio = AudioManagerTestDiagnostics.shadowParitySnapshot(GameServices.audio());

        var first = draw();
        var second = draw();

        assertEquals(frame, level.getFrameCounter(), "pictures cannot increment the level clock");
        assertArrayEquals(rows, scroll.getHScroll(), "pictures cannot dispatch SwScrl_DEZ");
        assertArrayEquals(first.pixels(), second.pixels(), "unchanged native state must give the same picture");
        assertTrue(Arrays.stream(first.pixels()).anyMatch(p -> (p & 0xFFFFFF) != 0xFF00FF),
                "the real scene must replace the deliberately poisoned framebuffer");
        assertTrue(Arrays.stream(first.pixels()).anyMatch(p -> p != first.pixels()[0]),
                "the native sky/character picture must contain visible content");
        assertEquals(audio.presentedFrames(),
                AudioManagerTestDiagnostics.shadowParitySnapshot(GameServices.audio()).presentedFrames());
    }

    @Test
    void admittedUpdatesAdvanceBackgroundWithoutPictures() {
        var level = GameServices.level();
        var scroll = SessionManager.getCurrentGameplayMode().getParallaxManager();
        int initial = level.getFrameCounter();
        var audio = AudioManagerTestDiagnostics.shadowParitySnapshot(GameServices.audio());
        for (int tick = 1; tick <= 8; tick++) {
            engine.advanceFrame();
            assertEquals(initial + tick, level.getFrameCounter(), "one background pass per admitted update");
        }
        assertTrue(Arrays.stream(scroll.getHScroll()).anyMatch(row -> row != 0), "native star rows must progress");
        assertEquals(audio.presentedFrames() + 8,
                AudioManagerTestDiagnostics.shadowParitySnapshot(GameServices.audio()).presentedFrames());
        int advanced = level.getFrameCounter();
        draw();
        assertEquals(advanced, level.getFrameCounter());
    }

    @Test
    void focusPausedPicturesHoldAndFrameStepAdvancesExactlyOnce() {
        var level = GameServices.level();
        int initial = level.getFrameCounter();
        // Ending mode rejects user-toggle pause; retain its existing focus-pause/step gate.
        loop.pause();
        for (int picture = 0; picture < 3; picture++) {
            engine.advanceFrame();
            draw();
        }
        assertEquals(initial, level.getFrameCounter(), "paused drawings cannot run the scroll owner");
        input.handleKeyEvent(GLFW_KEY_F8, GLFW_PRESS);
        engine.advanceFrame();
        draw();
        draw();
        assertEquals(initial + 1, level.getFrameCounter());
        engine.advanceFrame(); // The acknowledged held step must not execute again.
        draw();
        assertEquals(initial + 1, level.getFrameCounter());
    }

    @Test
    void restoredLevelAndParallaxOwnersReplayWithoutDrawDrivenDrift() {
        // ENDING_CUTSCENE is deliberately not live-rewindable: the provider itself has no snapshot.
        // This witness restores only the already registered level/parallax owners, within the
        // native CHARACTER_APPEAR hold where the provider's BG Y remains $C8 throughout.
        var level = GameServices.level();
        var scroll = SessionManager.getCurrentGameplayMode().getParallaxManager();
        var adapter = LevelRewindSnapshotAdapter.create(level);
        var savedLevel = adapter.capture();
        var savedScroll = scroll.capture();
        int bgY = loop.getEndingProvider().getBackgroundVscroll();
        for (int tick = 0; tick < 4; tick++) engine.advanceFrame();
        assertEquals(bgY, loop.getEndingProvider().getBackgroundVscroll());
        int replayFrame = level.getFrameCounter();
        int[] replayRows = scroll.getHScroll().clone();
        adapter.restore(savedLevel);
        scroll.restore(savedScroll);
        draw();
        draw();
        assertEquals(savedLevel.frameCounter(), level.getFrameCounter(), "a restored clock is held until an update");
        assertEquals(savedScroll, scroll.capture(), "drawing may not consume restored logical scroll state");
        for (int tick = 0; tick < 4; tick++) {
            engine.advanceFrame();
            draw();
            draw();
        }
        assertEquals(bgY, loop.getEndingProvider().getBackgroundVscroll());
        assertEquals(replayFrame, level.getFrameCounter());
        assertArrayEquals(replayRows, scroll.getHScroll(), "replayed scroll must depend on restored ticks, not pictures");
    }

    private RgbaImage draw() {
        glDisable(GL_SCISSOR_TEST);
        glClearColor(1, 0, 1, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        engine.draw();
        GameServices.graphics().flushScreenSpace();
        glFinish();
        assertEquals(GL_NO_ERROR, glGetError());
        return ScreenshotCapture.captureFramebufferRegion(0, 0, 320, 224);
    }

    private GameLoopEndingSession ending() throws Exception {
        Field field = GameLoop.class.getDeclaredField("endingSession");
        field.setAccessible(true);
        return (GameLoopEndingSession) field.get(loop);
    }
}
