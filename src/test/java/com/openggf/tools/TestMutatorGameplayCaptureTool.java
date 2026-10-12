package com.openggf.tools;

import com.openggf.GameLoop;
import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.pipeline.UiRenderPipeline;
import com.openggf.level.LevelManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.lwjgl.glfw.GLFW.*;

class TestMutatorGameplayCaptureTool {
    @TempDir Path temporary;
    private final String originalBackend = System.getProperty(SurfacelessEglContext.PROPERTY);

    @AfterEach
    void restoreBackend() {
        if (originalBackend == null) System.clearProperty(SurfacelessEglContext.PROPERTY);
        else System.setProperty(SurfacelessEglContext.PROPERTY, originalBackend);
    }

    @Test
    void observationDriverRejectsDefaultAndUnknownDisplayRoutesBeforeCreatingASession() {
        System.clearProperty(SurfacelessEglContext.PROPERTY);
        assertThrows(IllegalArgumentException.class, MutatorGameplayCaptureTool::requireDisplayFreeContext);
        System.setProperty(SurfacelessEglContext.PROPERTY, "x11");
        assertThrows(IllegalArgumentException.class, MutatorGameplayCaptureTool::requireDisplayFreeContext);
        System.setProperty(SurfacelessEglContext.PROPERTY, SurfacelessEglContext.SURFACELESS);
        assertDoesNotThrow(MutatorGameplayCaptureTool::requireDisplayFreeContext);
    }

    @Test
    void showcaseAndPacingAreExplicitWhileDefaultRemainsCanonical() {
        var canonical = options();
        assertFalse(canonical.showcase());
        assertFalse(canonical.interactivePacing());
        var showcase = options("--showcase");
        assertTrue(showcase.showcase());
        assertFalse(showcase.interactivePacing());
        var interactive = options("--interactive-pacing");
        assertTrue(interactive.showcase());
        assertTrue(interactive.interactivePacing());
    }

    @Test
    void argumentsRejectIgnoredFeaturesAndUnboundedOrIncompatibleWorkBeforeBoot() {
        for (String flag : List.of("--stills", "--scale", "--donor-rom")) {
            var failure = assertThrows(IllegalArgumentException.class, () -> options(flag, "1"));
            assertTrue(failure.getMessage().contains(flag));
        }
        for (String[] flags : List.of(new String[]{"--frames", "0"}, new String[]{"--frames", "36001"},
                new String[]{"--settle", "-1"}, new String[]{"--input-start", "-1"},
                new String[]{"--stop-on-death", "maybe"}, new String[]{"--fps", "30"},
                new String[]{"--input"}, new String[]{"--input", "--showcase"},
                new String[]{"--interactive-pacing", "--sidekick", "tails"},
                new String[]{"--interactive-pacing", "--donor", "s1"},
                new String[]{"--interactive-pacing", "--every", "2"},
                new String[]{"--interactive-pacing", "--capture-from", "1"},
                new String[]{"--interactive-pacing", "--complete-special-stage"},
                new String[]{"--interactive-pacing", "--title-screen"}, new String[]{"--unknown"}))
            assertThrows(IllegalArgumentException.class, () -> options(flags), List.of(flags).toString());
        assertThrows(IllegalArgumentException.class, () -> MutatorGameplayCaptureTool.Options.boundedFrames(Long.MAX_VALUE));
        assertEquals(36000, MutatorGameplayCaptureTool.Options.boundedFrames(36000));
    }

    @Test
    void missingDisplayFreeBackendFailsBeforeReadingInputOrCreatingOutputs() {
        System.clearProperty(SurfacelessEglContext.PROPERTY);
        var capture = options("--interactive-pacing", "--input", temporary.resolve("absent.txt").toString());
        assertThrows(IllegalArgumentException.class, () -> MutatorGameplayCaptureTool.run(capture));
        assertFalse(Files.exists(capture.capture().outDir()));
    }

    @Test
    void movieP2AndDebugChannelsCannotBeSilentlyLost() {
        var p2 = new Bk2FrameInput(42, 0, 0, false, 0, 1, false, "");
        var debug = new Bk2FrameInput(43, 0, 0, false, 0, 0, false, true, false, false, false, false, "");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> MutatorGameplayCaptureTool.PresentationInput.validate(p2)).getMessage().contains("42"));
        assertThrows(IllegalArgumentException.class, () -> MutatorGameplayCaptureTool.PresentationInput.validate(debug));
    }

    @Test
    void heldInputProducesOnePressAndReleaseAndCleanupRunsWhenTheBodyFails() {
        InputHandler input = spy(input());
        int[] keys = keys();
        var rightA = new Bk2FrameInput(0, AbstractPlayableSprite.INPUT_RIGHT, 1, false, "");
        var error = new IllegalStateException("capture failed");
        assertSame(error, assertThrows(IllegalStateException.class, () -> {
            try (var driver = new MutatorGameplayCaptureTool.PresentationInput(input, keys)) {
                driver.accept(rightA);
                driver.accept(rightA);
                assertTrue(input.isPhysicalKeyDown(keys[3]));
                assertTrue(input.isPhysicalKeyDown(keys[4]));
                assertFalse(input.hasLogicalOverride());
                throw error;
            }
        }));
        verify(input).handleKeyEvent(keys[3], GLFW_PRESS);
        verify(input).handleKeyEvent(keys[3], GLFW_RELEASE);
        verify(input).handleKeyEvent(keys[4], GLFW_PRESS);
        verify(input).handleKeyEvent(keys[4], GLFW_RELEASE);
        assertFalse(input.isPhysicalKeyDown(keys[3]));
        assertFalse(input.isPhysicalKeyDown(keys[4]));
        verify(input, never()).setLogicalOverride(any());
    }

    @Test
    void cleanupAttemptsEveryHeldKeyEvenIfAReleaseFails() {
        InputHandler input = spy(input());
        int[] keys = keys();
        var driver = new MutatorGameplayCaptureTool.PresentationInput(input, keys);
        driver.accept(new Bk2FrameInput(0, AbstractPlayableSprite.INPUT_RIGHT, 1, false, ""));
        doThrow(new IllegalStateException("release failed")).when(input).handleKeyEvent(keys[3], GLFW_RELEASE);
        assertThrows(IllegalStateException.class, driver::close);
        verify(input).handleKeyEvent(keys[4], GLFW_RELEASE);
        assertFalse(input.isPhysicalKeyDown(keys[4]));
    }

    @Test
    void presentationStepUsesNativeOwnerAndUpdatesTheFadeOnlyWhenUnowned() {
        InputHandler input = input();
        var loop = mock(GameLoop.class);
        when(loop.getInputHandler()).thenReturn(input);
        var level = mock(LevelManager.class);
        var graphics = mock(GraphicsManager.class);
        var ui = mock(UiRenderPipeline.class);
        when(graphics.getUiRenderPipeline()).thenReturn(ui);
        try (var services = mockStatic(GameServices.class);
             var driver = new MutatorGameplayCaptureTool.PresentationInput(input, keys())) {
            services.when(GameServices::level).thenReturn(level);
            services.when(GameServices::graphics).thenReturn(graphics);
            driver.step(loop, null, false);
            var ordered = inOrder(level, ui, loop);
            ordered.verify(level).skipPendingInitialTitleCardPresentation();
            ordered.verify(ui).updateFade();
            ordered.verify(loop).stepPresentationFrame();
            when(loop.ownsGameplayFadeLifecycle()).thenReturn(true);
            driver.step(loop, null, true);
            verify(ui, times(1)).updateFade();
            verify(loop, times(2)).stepPresentationFrame();
            verify(loop, never()).step();
            input.setLogicalOverride(LogicalInputSnapshot.neutral());
            assertThrows(IllegalStateException.class, () -> driver.step(loop, null, false));
            input.clearLogicalOverride();
            when(loop.externalFrameOrInputOwnerActive()).thenReturn(true);
            assertThrows(IllegalStateException.class, () -> driver.step(loop, null, false));
            verify(loop, times(2)).stepPresentationFrame();
        }
    }

    @Test
    void ambiguousOrInvalidKeyBindingsAreRejectedBeforeInputInjection() {
        var duplicate = keys(); duplicate[0] = duplicate[1];
        assertThrows(IllegalArgumentException.class,
                () -> new MutatorGameplayCaptureTool.PresentationInput(input(), duplicate));
        var absent = keys(); absent[0] = -2;
        assertThrows(IllegalArgumentException.class,
                () -> new MutatorGameplayCaptureTool.PresentationInput(input(), absent));
    }

    @Test
    void defaultUnboundBCDoNotBlockNativeAAndCannotSilentlyLoseRequestedActions() {
        var config = SonicConfigurationService.createStandalone(temporary);
        var bindings = InputBindingFactory.fromConfig(config);
        InputHandler input = new InputHandler(InputBindingFactory.supplier(config));
        try (var driver = new MutatorGameplayCaptureTool.PresentationInput(input, new int[] {
                bindings.p1Up(), bindings.p1Down(), bindings.p1Left(), bindings.p1Right(),
                bindings.p1A(), bindings.p1B(), bindings.p1C(), bindings.p1Start() })) {
            driver.accept(new Bk2FrameInput(0, 0, 1, false, ""));
            assertTrue(input.isPhysicalKeyDown(bindings.p1A()));
            driver.accept(null);
            assertFalse(input.isPhysicalKeyDown(bindings.p1A()));
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> driver.accept(new Bk2FrameInput(1, 0, 2, false, ""))).getMessage().contains("B is unbound"));
            assertThrows(IllegalArgumentException.class,
                    () -> driver.accept(new Bk2FrameInput(2, 0, 4, false, "")));
        }
    }

    private MutatorGameplayCaptureTool.Options options(String... extra) {
        List<String> args = new ArrayList<>(List.of("--zone", "aiz", "--act", "1", "--frames", "8",
                "--out-dir", temporary.resolve("capture").toString(), "--no-video"));
        args.addAll(List.of(extra));
        return MutatorGameplayCaptureTool.Options.parse(args.toArray(String[]::new));
    }

    private InputHandler input() {
        return new InputHandler(InputBindingFactory.supplier(SonicConfigurationService.createStandalone(temporary)));
    }

    private int[] keys() { return new int[] { GLFW_KEY_UP, GLFW_KEY_DOWN, GLFW_KEY_LEFT, GLFW_KEY_RIGHT,
            GLFW_KEY_Z, GLFW_KEY_X, GLFW_KEY_C, GLFW_KEY_ENTER }; }
}
