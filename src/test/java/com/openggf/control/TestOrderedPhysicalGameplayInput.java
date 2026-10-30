package com.openggf.control;

import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Real callback/poll -> scoped owner -> acknowledgment boundary, independent of scene drain. */
class TestOrderedPhysicalGameplayInput {
    private final SonicConfigurationService config = SonicConfigurationService.createStandalone();
    private final Pads pads = new Pads();
    private final InputHandler input;
    TestOrderedPhysicalGameplayInput() {
        config.setConfigValue(SonicConfiguration.P1_A, GLFW_KEY_SPACE);
        config.setConfigValue(SonicConfiguration.P1_B, GLFW_KEY_Z);
        config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER2, "none");
        input = new InputHandler(InputBindingFactory.supplier(config), pads);
        sample();
    }
    private void key(int code, boolean down) { input.handleKeyEvent(code, down ? GLFW_PRESS : GLFW_RELEASE); }
    private PlayerInputState sample() {
        try (var pass = InputHandlerInternalAccess.openInputPass(input)) {
            input.refreshLogicalSnapshot();
            var result = input.logical().player1();
            input.update();
            return result;
        }
    }
    @Test void callbackTapSurvivesPhysicalSceneDrainAndHostHandoff() {
        key(GLFW_KEY_SPACE, true); key(GLFW_KEY_SPACE, false);
        assertEquals(2, input.capturePhysicalInput().events().size());
        try (var host = InputHandlerInternalAccess.openHostInputPass(input)) {
            input.refreshLogicalSnapshot();
            assertFalse(input.isPhysicalKeyDown(GLFW_KEY_SPACE));
            assertEquals(0, input.logical().player1().actionHeldMask(), "host still sees physical held state");
            host.handoff(() -> {
                var pressed = sample();
                assertEquals(InputActionMasks.ACTION_A, pressed.actionHeldMask());
                assertEquals(InputActionMasks.ACTION_A, pressed.actionPressedMask());
            });
        }
        assertEquals(PlayerInputState.neutral(), sample(), "ordered release follows on the next owner pass");
        assertEquals(PlayerInputState.neutral(), sample());
    }
    @Test void repeatedTapsRetainOrderAndSimultaneousKeysShareTheFirstSample() {
        key(GLFW_KEY_SPACE, true); key(GLFW_KEY_Z, true);
        key(GLFW_KEY_SPACE, false); key(GLFW_KEY_Z, false);
        key(GLFW_KEY_SPACE, true); key(GLFW_KEY_SPACE, false);
        assertEquals(InputActionMasks.ACTION_A | InputActionMasks.ACTION_B, sample().actionPressedMask());
        assertEquals(PlayerInputState.neutral(), sample());
        assertEquals(InputActionMasks.ACTION_A, sample().actionPressedMask());
        assertEquals(PlayerInputState.neutral(), sample());
    }
    @Test void padTapBetweenLogicalSamplesSurvivesAllObservedPolls() {
        pads.b = true; input.pollPhysicalGamepads();
        pads.b = false; input.pollPhysicalGamepads();
        assertEquals(InputActionMasks.ACTION_B, sample().actionPressedMask());
        assertEquals(PlayerInputState.neutral(), sample());
    }
    @Test void textClaimAfterHostObservationDiscardsKeyboardTapButKeepsPadTap() {
        key(GLFW_KEY_SPACE, true); key(GLFW_KEY_SPACE, false);
        pads.b = true; input.pollPhysicalGamepads();
        pads.b = false; input.pollPhysicalGamepads();
        try (var host = InputHandlerInternalAccess.openHostInputPass(input)) {
            input.refreshLogicalSnapshot(); input.captureKeyboardText();
            host.handoff(() -> assertEquals(InputActionMasks.ACTION_B, sample().actionPressedMask()));
        }
        for (int i = 0; i < 4; i++) assertEquals(PlayerInputState.neutral(), sample());
    }
    @Test void textClaimWithdrawnBeforeRefreshCannotResurrectAnEarlierTap() {
        key(GLFW_KEY_SPACE, true); key(GLFW_KEY_SPACE, false);
        Object owner = new Object();
        var lease = InputHandlerInternalAccess.captureKeyboardText(input, owner);
        lease.close(); InputHandlerInternalAccess.releaseKeyboardText(input, owner);
        for (int i = 0; i < 4; i++) assertEquals(PlayerInputState.neutral(), sample());
    }
    @Test void modalHostAcknowledgmentDiscardsTheWholePendingSequence() {
        key(GLFW_KEY_SPACE, true); key(GLFW_KEY_SPACE, false);
        key(GLFW_KEY_SPACE, true); key(GLFW_KEY_SPACE, false);
        try (var host = InputHandlerInternalAccess.openHostInputPass(input)) {
            input.refreshLogicalSnapshot(); input.update();
        }
        for (int i = 0; i < 4; i++) assertEquals(PlayerInputState.neutral(), sample());
    }
    @Test void abortedConsumerDoesNotAcknowledgeItsSelectedPrefix() {
        key(GLFW_KEY_SPACE, true); key(GLFW_KEY_SPACE, false);
        try (var pass = InputHandlerInternalAccess.openInputPass(input)) {
            input.refreshLogicalSnapshot(); input.refreshLogicalSnapshot();
            assertEquals(InputActionMasks.ACTION_A, input.logical().player1().actionPressedMask());
        }
        assertEquals(InputActionMasks.ACTION_A, sample().actionPressedMask());
        assertEquals(PlayerInputState.neutral(), sample());
    }
    @Test void replayPublicationAndReleaseDiscardPendingKeyboardAndPadTaps() {
        key(GLFW_KEY_SPACE, true); key(GLFW_KEY_SPACE, false);
        pads.b = true; input.pollPhysicalGamepads(); pads.b = false; input.pollPhysicalGamepads();
        var recorded = LogicalInputSnapshot.neutral();
        try (var pass = InputHandlerInternalAccess.openInputPass(input)) {
            input.refreshLogicalSnapshot(); input.setLogicalOverride(recorded);
            input.refreshLogicalSnapshot(); assertSame(recorded, input.logical());
            input.clearLogicalOverride(); input.refreshLogicalSnapshot();
            assertEquals(PlayerInputState.neutral(), input.logical().player1());
        }
        assertEquals(PlayerInputState.neutral(), sample());
    }
    @Test void controllerClaimDiscardsPadSequenceWithoutTakingKeyboardSequence() {
        pads.b = true; input.pollPhysicalGamepads(); pads.b = false; input.pollPhysicalGamepads();
        key(GLFW_KEY_SPACE, true); key(GLFW_KEY_SPACE, false);
        InputHandlerInternalAccess.suppressGamepad(input, true);
        assertEquals(InputActionMasks.ACTION_A, sample().actionPressedMask());
        InputHandlerInternalAccess.suppressGamepad(input, false);
        assertEquals(PlayerInputState.neutral(), sample());
    }
    @Test void focusClearInvalidatesTheSequenceRatherThanAdmittingGhostPresses() {
        key(GLFW_KEY_SPACE, true); key(GLFW_KEY_SPACE, false);
        input.clearKeyState();
        assertEquals(PlayerInputState.neutral(), sample());
        key(GLFW_KEY_SPACE, true);
        assertEquals(InputActionMasks.ACTION_A, sample().actionPressedMask());
    }
    @Test void keyboardPlayersShareSimultaneousAdmissionWithoutSharingTheirControls() {
        config.setConfigValue(SonicConfiguration.P2_A, GLFW_KEY_F); sample();
        key(GLFW_KEY_SPACE, true); key(GLFW_KEY_F, true);
        key(GLFW_KEY_SPACE, false); key(GLFW_KEY_F, false);
        try (var pass = InputHandlerInternalAccess.openInputPass(input)) {
            input.refreshLogicalSnapshot();
            assertEquals(InputActionMasks.ACTION_A, input.logical().player1().actionPressedMask());
            assertEquals(InputActionMasks.ACTION_A, input.logical().player2().actionPressedMask());
            input.update();
        }
        assertEquals(PlayerInputState.neutral(), sample());
    }
    @Test void observedStickTapUsesTheExistingDeadzoneAndDirectionMapping() {
        pads.x = 1; input.pollPhysicalGamepads();
        pads.x = 0; input.pollPhysicalGamepads();
        assertEquals(com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT, sample().pressedMask());
        assertEquals(PlayerInputState.neutral(), sample());
    }
    @Test void retiringAControllerDiscardsItsPendingTapsButKeepsKeyboardInput() {
        pads.b = true; input.pollPhysicalGamepads(); pads.b = false; input.pollPhysicalGamepads();
        key(GLFW_KEY_SPACE, true); key(GLFW_KEY_SPACE, false);
        pads.connected = false; input.pollPhysicalGamepads();
        assertEquals(InputActionMasks.ACTION_A, sample().actionPressedMask());
        assertEquals(PlayerInputState.neutral(), sample());
    }
    @Test void bindingChangeRebaselinesRatherThanReinterpretingAnOldTap() {
        key(GLFW_KEY_SPACE, true); key(GLFW_KEY_SPACE, false);
        config.setConfigValue(SonicConfiguration.P1_A, GLFW_KEY_X);
        assertEquals(PlayerInputState.neutral(), sample());
        key(GLFW_KEY_X, true);
        assertEquals(InputActionMasks.ACTION_A, sample().actionPressedMask());
    }
    @Test void overflowingAnUnacknowledgedIntervalCannotInventAPartialTap() {
        for (int i = 0; i < 2050; i++) { key(GLFW_KEY_SPACE, true); key(GLFW_KEY_SPACE, false); }
        assertEquals(PlayerInputState.neutral(), sample());
        key(GLFW_KEY_SPACE, true);
        assertEquals(InputActionMasks.ACTION_A, sample().actionPressedMask());
    }
    @Test void releasedFixedMenuKeysKeepTheirUIAuthorityOverReboundGameplayActions() {
        config.setConfigValue(SonicConfiguration.P1_C, GLFW_KEY_ENTER);
        config.setConfigValue(SonicConfiguration.RIGHT, GLFW_KEY_LEFT);
        config.setConfigValue(SonicConfiguration.LEFT, GLFW_KEY_RIGHT);
        sample();
        key(GLFW_KEY_ENTER, true); key(GLFW_KEY_LEFT, true);
        key(GLFW_KEY_ENTER, false); key(GLFW_KEY_LEFT, false);
        try (var pass = InputHandlerInternalAccess.openInputPass(input)) {
            input.refreshLogicalSnapshot();
            assertTrue(MenuInput.accept(input));
            assertFalse(MenuInput.back(input), "fixed Enter must not become gameplay C/Back");
            assertTrue(MenuInput.left(input));
            assertFalse(MenuInput.right(input), "fixed Left must not become gameplay Right");
            assertFalse(MenuInput.textAccept(input), "physical text controls do not borrow gameplay admission");
            input.update();
        }
        assertEquals(PlayerInputState.neutral(), sample());
    }
    private static final class Pads implements GamepadStateSource {
        boolean b;
        boolean connected = true;
        float x;
        public List<DeviceState> pollDevices() {
            if (!connected) return List.of();
            boolean[] buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
            buttons[GLFW_GAMEPAD_BUTTON_A] = b;
            return List.of(DeviceState.connected(0, "pad", buttons, x, 0));
        }
    }
}
