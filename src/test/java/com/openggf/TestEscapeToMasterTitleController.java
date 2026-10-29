package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

class TestEscapeToMasterTitleController {

    @Test
    void promptStaysVisibleForAtLeastTwoSecondsAfterEscapePress() {
        InputHandler input = new InputHandler();
        EscapeToMasterTitleController controller = new EscapeToMasterTitleController(() -> false, () -> {});

        assertEquals(60, EscapeToMasterTitleController.HOLD_FRAMES);
        assertEquals(120, EscapeToMasterTitleController.PROMPT_MINIMUM_FRAMES);

        press(input, GLFW_KEY_ESCAPE);
        controller.update(GameMode.LEVEL, input);
        assertTrue(controller.visible());
        assertEquals("Hold to return to games", controller.message());

        release(input, GLFW_KEY_ESCAPE);
        for (int frame = 0; frame < EscapeToMasterTitleController.PROMPT_MINIMUM_FRAMES - 1; frame++) {
            controller.update(GameMode.LEVEL, input);
            assertTrue(controller.visible(), "prompt should remain visible through frame " + frame);
        }

        controller.update(GameMode.LEVEL, input);
        assertFalse(controller.visible());
    }

    @Test
    void holdProgressResetsWhenEscapeIsReleased() {
        InputHandler input = new InputHandler();
        EscapeToMasterTitleController controller = new EscapeToMasterTitleController(() -> false, () -> {});

        press(input, GLFW_KEY_ESCAPE);
        for (int frame = 0; frame < 40; frame++) {
            controller.update(GameMode.LEVEL, input);
            input.update();
        }
        assertEquals(40 / (double) EscapeToMasterTitleController.HOLD_FRAMES,
                controller.progress(), 0.0001);

        release(input, GLFW_KEY_ESCAPE);
        controller.update(GameMode.LEVEL, input);

        assertEquals(0.0, controller.progress(), 0.0001);
        assertTrue(controller.visible(), "prompt remains visible even though progress resets");
    }

    @Test
    void holdingEscapeForOneSecondTriggersTransitionOnce() {
        InputHandler input = new InputHandler();
        AtomicInteger transitions = new AtomicInteger();
        EscapeToMasterTitleController controller =
                new EscapeToMasterTitleController(() -> false, transitions::incrementAndGet);

        press(input, GLFW_KEY_ESCAPE);
        for (int frame = 0; frame < EscapeToMasterTitleController.HOLD_FRAMES - 1; frame++) {
            controller.update(GameMode.LEVEL, input);
            input.update();
        }
        assertEquals(0, transitions.get());
        assertEquals((EscapeToMasterTitleController.HOLD_FRAMES - 1)
                        / (double) EscapeToMasterTitleController.HOLD_FRAMES,
                controller.progress(), 0.0001);

        controller.update(GameMode.LEVEL, input);
        assertEquals(1, transitions.get());
        assertEquals(1.0, controller.progress(), 0.0001);
        assertEquals(EscapeToMasterTitleController.LEAVING_MESSAGE, controller.message());

        for (int frame = 0; frame < 20; frame++) {
            controller.update(GameMode.LEVEL, input);
            input.update();
        }
        assertEquals(1, transitions.get());
    }

    @Test
    void masterTitleOwnsEscapeSoHoldNeverShowsOrTriggers() {
        InputHandler input = new InputHandler();
        AtomicInteger transitions = new AtomicInteger();
        EscapeToMasterTitleController controller =
                new EscapeToMasterTitleController(() -> false, transitions::incrementAndGet);

        press(input, GLFW_KEY_ESCAPE);
        for (int frame = 0; frame < EscapeToMasterTitleController.HOLD_FRAMES * 2; frame++) {
            controller.update(GameMode.MASTER_TITLE_SCREEN, input);
            input.update();
        }

        assertFalse(controller.visible());
        assertEquals(0f, controller.presence());
        assertEquals(0, transitions.get());
    }

    @Test
    void returningToMasterTitleClearsHeldProgress() {
        InputHandler input = new InputHandler();
        EscapeToMasterTitleController controller = new EscapeToMasterTitleController(() -> false, () -> {});

        press(input, GLFW_KEY_ESCAPE);
        for (int frame = 0; frame < EscapeToMasterTitleController.HOLD_FRAMES - 1; frame++) {
            controller.update(GameMode.LEVEL, input);
            input.update();
        }
        controller.update(GameMode.MASTER_TITLE_SCREEN, input);

        assertFalse(controller.visible());
        assertEquals(0.0, controller.progress(), 0.0001);
    }

    @Test
    void toastSlidesInThenOutAtTheEndOfItsLinger() {
        InputHandler input = new InputHandler();
        EscapeToMasterTitleController controller = new EscapeToMasterTitleController(() -> false, () -> {});

        press(input, GLFW_KEY_ESCAPE);
        controller.update(GameMode.LEVEL, input);
        float first = controller.presence();
        assertTrue(first > 0f && first < 1f, "toast eases in rather than popping");

        release(input, GLFW_KEY_ESCAPE);
        for (int frame = 0; frame < EscapeToMasterTitleController.SLIDE_FRAMES; frame++) {
            controller.update(GameMode.LEVEL, input);
        }
        assertEquals(1f, controller.presence(), 0.0001f);

        while (controller.visible()) {
            float before = controller.presence();
            controller.update(GameMode.LEVEL, input);
            assertTrue(controller.presence() <= before, "toast only leaves once lingering");
        }
        assertEquals(0f, controller.presence());
    }

    @Test
    void titleCardModePreservesReturnToTitleProgress() {
        InputHandler input = new InputHandler();
        EscapeToMasterTitleController controller = new EscapeToMasterTitleController(() -> false, () -> {});

        press(input, GLFW_KEY_ESCAPE);
        for (int frame = 0; frame < 40; frame++) {
            controller.update(GameMode.LEVEL, input);
            input.update();
        }

        controller.update(GameMode.TITLE_CARD, input);

        assertTrue(controller.visible());
        assertEquals(41 / (double) EscapeToMasterTitleController.HOLD_FRAMES,
                controller.progress(), 0.0001);
        assertEquals(EscapeToMasterTitleController.MESSAGE, controller.message());
    }

    @Test
    void ineligibleModesClearPromptAndProgress() {
        InputHandler input = new InputHandler();
        EscapeToMasterTitleController controller = new EscapeToMasterTitleController(() -> false, () -> {});

        press(input, GLFW_KEY_ESCAPE);
        controller.update(GameMode.LEVEL, input);
        assertTrue(controller.visible());

        controller.update(GameMode.EDITOR, input);

        assertFalse(controller.visible());
        assertEquals(0.0, controller.progress(), 0.0001);
    }

    @Test
    void activeFadeBlocksTransitionStart() {
        InputHandler input = new InputHandler();
        AtomicInteger transitions = new AtomicInteger();
        EscapeToMasterTitleController controller =
                new EscapeToMasterTitleController(() -> true, transitions::incrementAndGet);

        press(input, GLFW_KEY_ESCAPE);
        for (int frame = 0; frame < EscapeToMasterTitleController.HOLD_FRAMES + 10; frame++) {
            controller.update(GameMode.LEVEL, input);
            input.update();
        }

        assertEquals(0, transitions.get());
        assertEquals(1.0, controller.progress(), 0.0001);
    }

    @Test
    void modOwnedTapShowsNoToastSoTheModsOwnEscapeMenuIsUncovered() {
        InputHandler input = new InputHandler();
        AtomicInteger transitions = new AtomicInteger();
        EscapeToMasterTitleController controller =
                new EscapeToMasterTitleController(() -> false, transitions::incrementAndGet, () -> true);

        press(input, GLFW_KEY_ESCAPE);
        controller.update(GameMode.MOD_SCENE, input);
        assertTrue(input.isKeyPressed(GLFW_KEY_ESCAPE), "the tap still reaches the scene");
        input.update();
        release(input, GLFW_KEY_ESCAPE);
        for (int frame = 0; frame < EscapeToMasterTitleController.PROMPT_MINIMUM_FRAMES; frame++) {
            controller.update(GameMode.MOD_SCENE, input);
            assertFalse(controller.visible(), "no toast after a mod-owned tap, frame " + frame);
            input.update();
        }
        assertEquals(0, transitions.get());
    }

    @Test
    void modOwnedHoldShowsTheToastAfterAQuarterSecondAndStillLeavesAfterOne() {
        InputHandler input = new InputHandler();
        AtomicInteger transitions = new AtomicInteger();
        EscapeToMasterTitleController controller =
                new EscapeToMasterTitleController(() -> false, transitions::incrementAndGet, () -> true);

        press(input, GLFW_KEY_ESCAPE);
        for (int frame = 1; frame < EscapeToMasterTitleController.MOD_TAP_FRAMES; frame++) {
            controller.update(GameMode.LEVEL, input);
            assertFalse(controller.visible(), "still a possible tap at frame " + frame);
            input.update();
        }
        controller.update(GameMode.LEVEL, input);
        assertTrue(controller.visible(), "a deliberate hold shows the toast");
        input.update();
        for (int frame = EscapeToMasterTitleController.MOD_TAP_FRAMES;
             frame < EscapeToMasterTitleController.HOLD_FRAMES; frame++) {
            controller.update(GameMode.LEVEL, input);
            input.update();
        }
        assertEquals(1, transitions.get(), "the full hold takes the same time from the press");
    }

    @Test
    void modOwnedHoldReleasedEarlyLingersLikeATap() {
        InputHandler input = new InputHandler();
        EscapeToMasterTitleController controller =
                new EscapeToMasterTitleController(() -> false, () -> {}, () -> true);

        press(input, GLFW_KEY_ESCAPE);
        for (int frame = 0; frame < EscapeToMasterTitleController.MOD_TAP_FRAMES + 5; frame++) {
            controller.update(GameMode.LEVEL, input);
            input.update();
        }
        release(input, GLFW_KEY_ESCAPE);
        controller.update(GameMode.LEVEL, input);

        assertTrue(controller.visible(), "the toast eases out instead of vanishing");
        assertEquals(0.0, controller.progress(), 0.0001);
    }

    private static void press(InputHandler input, int key) {
        input.handleKeyEvent(key, GLFW_PRESS);
    }

    private static void release(InputHandler input, int key) {
        input.handleKeyEvent(key, GLFW_RELEASE);
    }
}
