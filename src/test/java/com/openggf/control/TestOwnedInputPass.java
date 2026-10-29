package com.openggf.control;

import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** InputHandler's host-internal pass; callbacks finish before an owner starts its pass. */
class TestOwnedInputPass {
    @Test void repeatedRefreshKeepsKeyboardPadAndShortcutEdgesUntilTheExistingAcknowledgment() {
        var f = fixture();
        f.input.refreshLogicalSnapshot(); f.input.update();
        f.input.handleKeyEvent(f.action, GLFW_PRESS);
        f.pads.b = true; f.pads.details = true;
        try (var pass = InputHandlerInternalAccess.openInputPass(f.input)) {
            f.input.refreshLogicalSnapshot();
            var first = f.input.logical();
            int polls = f.pads.polls;
            f.input.refreshLogicalSnapshot();
            assertEquals(first, f.input.logical());
            assertEquals(InputActionMasks.ACTION_A | InputActionMasks.ACTION_B,
                    first.player1().actionPressedMask());
            assertTrue(MenuInput.details(f.input));
            assertEquals(polls, f.pads.polls, "re-reading one pass cannot observe or acknowledge another pad sample");
            f.input.update();
            long completed = f.input.menuFrame;
            f.input.update();
            assertEquals(completed, f.input.menuFrame, "the owner may acknowledge this pass only once");
            assertFalse(f.input.isPhysicalKeyPressed(f.action));
            f.input.refreshLogicalSnapshot();
            assertEquals(0, f.input.logical().player1().actionPressedMask());
            assertFalse(MenuInput.details(f.input));
        }
        try (var pass = InputHandlerInternalAccess.openInputPass(f.input)) {
            f.input.refreshLogicalSnapshot();
            assertEquals(0, f.input.logical().player1().actionPressedMask());
            assertEquals(InputActionMasks.ACTION_A | InputActionMasks.ACTION_B,
                    f.input.logical().player1().actionHeldMask());
            assertFalse(MenuInput.details(f.input));
            f.input.update();
        }
    }

    @Test void observationAndOwnedRefreshLeaveTheSceneDrainAndCommittedTextWithTheirOwner() {
        var f = fixture();
        f.input.pollPhysicalGamepads();
        f.input.handleKeyEvent(f.action, GLFW_PRESS);
        f.pads.b = true; f.input.pollPhysicalGamepads();
        f.input.handleKeyEvent(f.action, GLFW_RELEASE);
        f.pads.b = false; f.input.pollPhysicalGamepads();
        MenuInput.handleCharEvent(f.input, 'x');
        long frame = f.input.menuFrame;
        try (var pass = InputHandlerInternalAccess.openInputPass(f.input)) {
            f.input.refreshLogicalSnapshot(); f.input.refreshLogicalSnapshot();
            assertEquals(frame, f.input.menuFrame);
            assertEquals("x", f.input.consumeTypedText());
            var events = f.input.capturePhysicalInput().events();
            assertEquals(List.of(true, true, false, false), events.stream().map(PhysicalInputEvent::pressed).toList());
            assertTrue(f.input.capturePhysicalInput().events().isEmpty());
            // This prerequisite does not invent gameplay admission for a scene's ordered transitions.
            assertEquals(PlayerInputState.neutral(), f.input.logical().player1());
            f.input.update();
        }
    }

    @Test void aTextClaimTakenAfterFirstRefreshStillFiltersTheMappedKeyboardOnReread() {
        var f = fixture();
        f.input.handleKeyEvent(f.action, GLFW_PRESS);
        f.pads.b = true;
        try (var pass = InputHandlerInternalAccess.openInputPass(f.input)) {
            f.input.refreshLogicalSnapshot();
            f.input.captureKeyboardText();
            f.input.refreshLogicalSnapshot();
            assertEquals(InputActionMasks.ACTION_B, f.input.logical().player1().actionPressedMask());
            assertFalse(f.input.isPhysicalKeyPressed(f.action));
            assertTrue(f.input.isCapturedKeyPressed(f.action));
            assertTrue(f.input.isKeyboardTextCaptured());
            f.input.update();
        }
    }

    @Test void compositionRetractionRemainsAuthoritativeAfterAPassHasSampled() {
        var f = fixture();
        var session = InputHandlerInternalAccess.textInput(f.input);
        session.claim(this);
        f.input.handleKeyEvent(f.action, GLFW_PRESS);
        try (var pass = InputHandlerInternalAccess.openInputPass(f.input)) {
            f.input.refreshLogicalSnapshot();
            session.preedit(Composition.fromCodepoints(new int[] {'x'}, new int[] {1}, 0, 1));
            f.input.refreshLogicalSnapshot();
            assertEquals(0, f.input.logical().player1().actionPressedMask());
            assertFalse(f.input.isCapturedKeyPressed(f.action));
            assertTrue(f.input.capturePhysicalInput().events().isEmpty());
            f.input.update();
        }
    }

    @Test void recordedPublicationAndReleaseRemainAuthoritativeWithinAnOwnedPass() {
        var f = fixture();
        var recorded = LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral());
        try (var pass = InputHandlerInternalAccess.openInputPass(f.input)) {
            f.input.refreshLogicalSnapshot();
            InputHandlerInternalAccess.retainGameplayInput(f.input,
                    PlayerInputState.of(0, 0, 0, InputActionMasks.ACTION_C, false, false), PlayerInputState.neutral());
            f.input.setLogicalOverride(recorded);
            f.input.refreshLogicalSnapshot();
            assertSame(recorded, f.input.logical());
            assertTrue(InputHandlerInternalAccess.clearLogicalOverrideIfSame(f.input, recorded));
            f.input.refreshLogicalSnapshot();
            assertEquals(LogicalInputSnapshot.neutral(), f.input.logical(), "publication discarded live carry");
            f.input.update();
        }
    }

    @Test void recordedOverrideDiscardsPadPressEvenWhenReleasedWithinTheSamePass() {
        var f = fixture();
        f.pads.b = true;
        var recorded = LogicalInputSnapshot.neutral();
        f.input.setLogicalOverride(recorded);
        try (var pass = InputHandlerInternalAccess.openInputPass(f.input)) {
            f.input.refreshLogicalSnapshot();
            assertSame(recorded, f.input.logical());
            assertTrue(InputHandlerInternalAccess.clearLogicalOverrideIfSame(f.input, recorded));
            f.input.refreshLogicalSnapshot();
            assertEquals(InputActionMasks.ACTION_B, f.input.logical().player1().actionHeldMask());
            assertEquals(0, f.input.logical().player1().actionPressedMask());
            f.input.update();
        }
    }

    @Test void abortedPassCannotRestorePadPressDiscardedByRecordedOverride() {
        var f = fixture();
        f.pads.b = true; f.pads.details = true;
        f.input.setLogicalOverride(LogicalInputSnapshot.neutral());
        try (var pass = InputHandlerInternalAccess.openInputPass(f.input)) {
            f.input.refreshLogicalSnapshot();
            assertTrue(MenuInput.details(f.input));
            assertFalse(f.input.capturePhysicalInput().gamepads().isEmpty());
        }
        f.input.clearLogicalOverride();
        try (var pass = InputHandlerInternalAccess.openInputPass(f.input)) {
            f.input.refreshLogicalSnapshot();
            assertEquals(InputActionMasks.ACTION_B, f.input.logical().player1().actionHeldMask());
            assertEquals(0, f.input.logical().player1().actionPressedMask());
            assertTrue(MenuInput.details(f.input), "the independent host shortcut was not acknowledged");
            f.input.update();
        }
    }

    @Test void slowMotionCarryIsStableForThePassAndRetiresAtItsExistingAcknowledgment() {
        var f = fixture();
        var tap = PlayerInputState.of(0, 0, 0, InputActionMasks.ACTION_A, false, false);
        InputHandlerInternalAccess.retainGameplayInput(f.input, tap, PlayerInputState.neutral());
        try (var pass = InputHandlerInternalAccess.openInputPass(f.input)) {
            f.input.refreshLogicalSnapshot(); f.input.refreshLogicalSnapshot();
            assertEquals(InputActionMasks.ACTION_A, f.input.logical().player1().actionPressedMask());
            f.input.update();
        }
        try (var pass = InputHandlerInternalAccess.openInputPass(f.input)) {
            f.input.refreshLogicalSnapshot();
            assertEquals(0, f.input.logical().player1().actionPressedMask());
            f.input.update();
        }
    }

    @Test void closingWithoutAcknowledgmentDoesNotPretendThatADeferredOwnerConsumedInput() {
        var f = fixture();
        f.input.captureKeyboardText();
        MenuInput.handleCharEvent(f.input, 'x');
        long frame = f.input.menuFrame;
        var first = InputHandlerInternalAccess.openInputPass(f.input);
        assertThrows(IllegalStateException.class, () -> InputHandlerInternalAccess.openInputPass(f.input));
        first.close();
        try (var second = InputHandlerInternalAccess.openInputPass(f.input)) {
            first.close();
            assertEquals(frame, f.input.menuFrame);
            assertEquals("x", f.input.consumeTypedText());
            assertTrue(f.input.isKeyboardTextCaptured());
            f.input.update();
        }
    }

    @Test void anAbortedPassDoesNotAcknowledgePadHistoryWhileLeavingKeyboardEdgesPending() {
        var f = fixture(); f.pads.b = true;
        f.input.handleKeyEvent(f.action, GLFW_PRESS);
        try (var pass = InputHandlerInternalAccess.openInputPass(f.input)) {
            f.input.refreshLogicalSnapshot();
        }
        try (var pass = InputHandlerInternalAccess.openInputPass(f.input)) {
            f.input.refreshLogicalSnapshot();
            assertEquals(InputActionMasks.ACTION_A | InputActionMasks.ACTION_B,
                    f.input.logical().player1().actionPressedMask());
            f.input.update();
        }
    }

    @Test void chromeSuppressionCanChangeWithoutRepollingOrRevivingThePadMapping() {
        var f = fixture(); f.pads.b = true;
        try (var pass = InputHandlerInternalAccess.openInputPass(f.input)) {
            f.input.refreshLogicalSnapshot();
            InputHandlerInternalAccess.suppressGamepad(f.input, true);
            f.input.refreshLogicalSnapshot();
            assertEquals(PlayerInputState.neutral(), f.input.logical().player1());
            assertFalse(MenuInput.textAccept(f.input));
            assertFalse(f.input.capturePhysicalInput().gamepads().isEmpty());
            f.input.update();
        }
    }

    @Test void unscopedRefreshKeepsTheExistingPublicAndToolContract() {
        var f = fixture(); f.pads.b = true;
        f.input.refreshLogicalSnapshot();
        assertEquals(InputActionMasks.ACTION_B, f.input.logical().player1().actionPressedMask());
        f.input.refreshLogicalSnapshot();
        assertEquals(0, f.input.logical().player1().actionPressedMask());
    }

    @Test void keyboardShortcutAliasesKeepTheirFirstMappingWhileTheHostStillSeesThePadShortcut() {
        var f = fixture();
        f.config.setConfigValue(SonicConfiguration.P1_A, GLFW_KEY_F6);
        f.config.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, GLFW_KEY_F6);
        f.input.refreshLogicalSnapshot(); f.input.update();
        f.pads.step = true;
        try (var pass = InputHandlerInternalAccess.openInputPass(f.input)) {
            f.input.refreshLogicalSnapshot();
            var first = f.input.logical();
            assertTrue(f.input.isKeyPressed(GLFW_KEY_F6), "the host sees this pass's physical pad shortcut");
            f.input.refreshLogicalSnapshot();
            assertEquals(first, f.input.logical(), "rereading cannot turn a pad shortcut into another mapped action");
            f.input.update();
        }
    }

    @Test void unacknowledgedCarryReturnsToTheExistingOneRefreshBridgeUnlessReplayDiscardsIt() {
        var f = fixture();
        InputHandlerInternalAccess.retainGameplayInput(f.input,
                PlayerInputState.of(0, 0, 0, InputActionMasks.ACTION_A, false, false), PlayerInputState.neutral());
        try (var pass = InputHandlerInternalAccess.openInputPass(f.input)) {
            f.input.refreshLogicalSnapshot();
        }
        try (var pass = InputHandlerInternalAccess.openInputPass(f.input)) {
            f.input.refreshLogicalSnapshot();
            assertEquals(InputActionMasks.ACTION_A, f.input.logical().player1().actionPressedMask());
            f.input.setLogicalOverride(LogicalInputSnapshot.neutral());
        }
        f.input.clearLogicalOverride();
        f.input.refreshLogicalSnapshot();
        assertEquals(0, f.input.logical().player1().actionPressedMask());
    }

    private static Fixture fixture() {
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.P1_A, GLFW_KEY_SPACE);
        config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER2, "none");
        var pads = new Pads();
        return new Fixture(new InputHandler(InputBindingFactory.supplier(config), pads), pads, GLFW_KEY_SPACE, config);
    }
    private record Fixture(InputHandler input, Pads pads, int action, SonicConfigurationService config) { }
    private static final class Pads implements GamepadStateSource {
        boolean b, details, step; int polls;
        public List<DeviceState> pollDevices() {
            polls++;
            var buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
            buttons[GLFW_GAMEPAD_BUTTON_A] = b; buttons[GLFW_GAMEPAD_BUTTON_Y] = details;
            buttons[GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER] = step;
            return List.of(DeviceState.connected(0, "pad", buttons, 0, 0));
        }
    }
}
