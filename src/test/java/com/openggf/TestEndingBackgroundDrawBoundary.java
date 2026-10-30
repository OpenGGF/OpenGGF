package com.openggf;

import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.AudioRequestObserver;
import com.openggf.game.sonic2.scroll.SwScrlDez;
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
import java.util.ArrayList;

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
        // Activation itself is white; this independent witness needs visible sky/character colours.
        for (int tick = 0; tick < 16; tick++) engine.advanceFrame();
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

    @Test
    void firstSkyActivationPublishesTheFirstNativeStarSampleBeforeAnyPicture() {
        var level = GameServices.level();
        var scroll = SessionManager.getCurrentGameplayMode().getParallaxManager();
        assertEquals(1, level.getFrameCounter(), "photos must not spend background clock ticks");
        assertEquals(0xC8, loop.getEndingProvider().getBackgroundVscroll());
        assertNativeStarSample(1);
        var saved = scroll.capture();
        var rows = scroll.getHScroll().clone();
        var audio = AudioManagerTestDiagnostics.shadowParitySnapshot(GameServices.audio());
        var first = draw();
        assertTrue(Arrays.stream(first.pixels()).allMatch(p -> (p & 0xFFFFFF) == 0xFFFFFF),
                "the native first sky sample still has the initial white palette");
        assertArrayEquals(first.pixels(), draw().pixels());
        assertEquals(1, level.getFrameCounter());
        assertEquals(saved, scroll.capture());
        assertArrayEquals(rows, scroll.getHScroll());
        assertEquals(audio.presentedFrames(),
                AudioManagerTestDiagnostics.shadowParitySnapshot(GameServices.audio()).presentedFrames());
    }

    @Test
    void movingBackgroundUsesUpdatedYWithNoDrawsAndOneAcknowledgedFrameStep() {
        var provider = loop.getEndingProvider();
        var level = GameServices.level();
        for (int ticks = 0; ticks < 100 && provider.getBackgroundVscroll() == 0xC8; ticks++) {
            engine.advanceFrame();
        }
        assertEquals(0xC9, provider.getBackgroundVscroll(), "reach the first real camera-scroll update");
        assertNativeStarSample(level.getFrameCounter());
        var before = draw();
        for (int ticks = 1; ticks <= 8; ticks++) {
            int frame = level.getFrameCounter();
            engine.advanceFrame(); // Consecutive changing-Y samples, without any picture.
            assertEquals(0xC9 + ticks, provider.getBackgroundVscroll());
            assertEquals(frame + 1, level.getFrameCounter());
            assertNativeStarSample(level.getFrameCounter());
        }
        var moved = draw();
        assertFalse(Arrays.equals(before.pixels(), moved.pixels()), "native sky motion reaches the framebuffer");
        loop.pause();
        int heldY = provider.getBackgroundVscroll();
        int heldFrame = level.getFrameCounter();
        engine.advanceFrame();
        assertArrayEquals(moved.pixels(), draw().pixels());
        assertArrayEquals(moved.pixels(), draw().pixels());
        assertEquals(heldFrame, level.getFrameCounter());
        assertEquals(heldY, provider.getBackgroundVscroll());
        input.handleKeyEvent(GLFW_KEY_F8, GLFW_PRESS);
        engine.advanceFrame();
        assertEquals(heldY + 1, provider.getBackgroundVscroll());
        assertEquals(heldFrame + 1, level.getFrameCounter());
        assertNativeStarSample(level.getFrameCounter());
        var stepped = draw();
        engine.advanceFrame();
        assertArrayEquals(stepped.pixels(), draw().pixels());
        assertEquals(heldY + 1, provider.getBackgroundVscroll());
        assertEquals(heldFrame + 1, level.getFrameCounter());
    }

    @Test
    void lastSkyUpdateRetiresScrollBeforeCreditsAndPicturesCannotRepeatTheMusicRequest() {
        var level = GameServices.level();
        var scroll = SessionManager.getCurrentGameplayMode().getParallaxManager();
        var provider = loop.getEndingProvider();
        var requests = new ArrayList<Integer>();
        GameServices.audio().setRequestObserver((kind, id) -> {
            if (kind == AudioRequestObserver.RequestClass.MUSIC) requests.add(id);
        });
        try {
            boolean retired = false;
            for (int ticks = 0; ticks < 5000 && provider.needsLevelBackground(); ticks++) {
                int frame = level.getFrameCounter();
                var saved = scroll.capture();
                var rows = scroll.getHScroll().clone();
                engine.advanceFrame();
                if (provider.needsLevelBackground()) {
                    assertEquals(frame + 1, level.getFrameCounter());
                } else {
                    retired = true;
                    assertEquals(frame, level.getFrameCounter(), "post-provider gate omits the terminal sky pass");
                    assertEquals(saved, scroll.capture());
                    assertArrayEquals(rows, scroll.getHScroll());
                }
            }
            assertTrue(retired, "native ending must reach its terminal credits trigger");
            assertEquals(GameMode.ENDING_CUTSCENE, loop.getCurrentGameMode(), "outgoing black fade precedes credits");
            int terminalFrame = level.getFrameCounter();
            var saved = scroll.capture();
            var rows = scroll.getHScroll().clone();
            draw(); draw();
            assertTrue(requests.isEmpty(), "pictures cannot start credits music");
            for (int ticks = 0; ticks < 100 && loop.getCurrentGameMode() != GameMode.CREDITS_TEXT; ticks++) {
                engine.advanceFrame();
                assertEquals(terminalFrame, level.getFrameCounter());
                assertArrayEquals(rows, scroll.getHScroll());
            }
            assertEquals(GameMode.CREDITS_TEXT, loop.getCurrentGameMode());
            assertFalse(provider.needsLevelBackground());
            // This deliberately early ending ingress leaves DEZ's level-entry music
            // pending; its native VBlank service can publish $87 during the fade.
            // Preserve that owner and independently require exactly one credits request.
            assertEquals(1, requests.stream().filter(id -> id == 0xBD).count(),
                    "EndgameCredits requests MusID_Credits once");
            assertEquals(0xBD, requests.getLast());
            var musicAtCredits = java.util.List.copyOf(requests);
            // Reveal the real first credit slide; this is not a forced phase/timer write.
            for (int tick = 0; tick < 32; tick++) engine.advanceFrame();
            var credits = drawCredits();
            assertTrue(Arrays.stream(credits.pixels()).anyMatch(p -> (p & 0xFFFFFF) != 0), "credit text is visible");
            assertTrue(Arrays.stream(credits.pixels()).anyMatch(p -> (p & 0xFFFFFF) == 0), "credits use a black backdrop");
            assertArrayEquals(credits.pixels(), drawCredits().pixels());
            assertEquals(terminalFrame, level.getFrameCounter());
            assertEquals(saved, scroll.capture());
            assertArrayEquals(rows, scroll.getHScroll());
            assertEquals(musicAtCredits, requests, "credit pictures and hold add no music requests");
        } finally {
            GameServices.audio().setRequestObserver(AudioRequestObserver.NONE);
        }
    }

    private void assertNativeStarSample(int passes) {
        var scroll = SessionManager.getCurrentGameplayMode().getParallaxManager();
        var handler = assertInstanceOf(SwScrlDez.class, scroll.getHandler(10));
        int y = loop.getEndingProvider().getBackgroundVscroll();
        assertEquals(y, scroll.getVscrollFactorBG() & 0xFFFF, "publish the provider's new Y, not its preceding Y");
        // SwScrl_DEZ (s2.asm:17491-17533): increment before reading. At BG Y=$C8,
        // row 10 starts at screen line 0; the next row starts at 8. These fixed
        // native strip boundaries reject stale-Y row packing as Y moves.
        assertEquals((3 * passes) & 0xFFFF, handler.getTempArray()[1]);
        assertEquals((6 * passes) & 0xFFFF, handler.getTempArray()[10]);
        assertEquals(passes & 0xFFFF, handler.getTempArray()[24]);
        int[] nativeY = {200, 208, 216, 312, 320, 328, 336, 344, 368, 384};
        int[] nativeValue = {6 * passes, 3 * passes, 4 * passes, passes,
                (passes & 0xFFFF) >>> 1, 3 * passes, 2 * passes, 4 * passes, passes, 0};
        for (int strip = 0; strip < nativeY.length; strip++) {
            int line = nativeY[strip] - y;
            if (line >= 0 && line < 224) {
                assertEquals((-nativeValue[strip]) & 0xFFFF, scroll.getHScroll()[line],
                        "native packed FG=0/BG at world Y=" + nativeY[strip]);
            }
        }
    }

    private RgbaImage drawCredits() {
        // Engine.displayFrame clears before Engine.draw; draw() alone intentionally
        // leaves poison behind. Credits need that separate real provider clear owner.
        glDisable(GL_SCISSOR_TEST);
        glClearColor(1, 0, 1, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        loop.getEndingProvider().setClearColor();
        glClear(GL_COLOR_BUFFER_BIT);
        var cleared = ScreenshotCapture.captureFramebufferRegion(0, 0, 320, 224);
        assertTrue(Arrays.stream(cleared.pixels()).allMatch(p -> (p & 0xFFFFFF) == 0),
                "the production credits clear owner must replace poison with black");
        engine.draw();
        GameServices.graphics().flushScreenSpace();
        glFinish();
        assertEquals(GL_NO_ERROR, glGetError());
        return ScreenshotCapture.captureFramebufferRegion(0, 0, 320, 224);
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
