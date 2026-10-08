package com.openggf.control;

import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestScenePhysicalInput {
    private final AtomicLong clock = new AtomicLong(100);
    private final MutablePads pads = new MutablePads();
    private final SonicConfigurationService configuration = SonicConfigurationService.createStandalone();

    private InputHandler input() {
        return new InputHandler(InputBindingFactory.supplier(configuration), pads, clock::get);
    }

    @Test
    void callbackTapSurvivesBetweenTicksAndKeepsArrivalTimes() {
        InputHandler input = input();
        input.handleKeyEvent(GLFW_KEY_A, GLFW_PRESS);
        clock.set(170);
        input.handleKeyEvent(GLFW_KEY_A, GLFW_RELEASE);
        clock.set(300);

        PhysicalInput snapshot = input.capturePhysicalInput();

        assertEquals(300, snapshot.timestampNanos());
        assertFalse(snapshot.keyDown(GLFW_KEY_A));
        assertEquals(2, snapshot.events().size());
        assertEquals(100, snapshot.events().get(0).timestampNanos());
        assertEquals(170, snapshot.events().get(1).timestampNanos());
        assertTrue(snapshot.events().get(0).pressed());
        assertTrue(snapshot.events().get(1).released());
        assertEquals(PhysicalInputEvent.Kind.KEY, snapshot.events().get(0).kind());
        assertEquals(PhysicalInputEvent.KEYBOARD_DEVICE, snapshot.events().get(0).deviceId());
        assertTrue(snapshot.events().get(0).sequence() < snapshot.events().get(1).sequence());
        assertTrue(input.capturePhysicalInput().events().isEmpty());
    }

    @Test
    void allFiveSimultaneousFretsRemainSeparateAndRepeatIsNotAnotherAttack() {
        InputHandler input = input();
        int[] frets = {GLFW_KEY_A, GLFW_KEY_S, GLFW_KEY_D, GLFW_KEY_F, GLFW_KEY_G};
        for (int key : frets) input.handleKeyEvent(key, GLFW_PRESS);
        input.handleKeyEvent(GLFW_KEY_A, GLFW_REPEAT);

        PhysicalInput snapshot = input.capturePhysicalInput();

        assertEquals(5, snapshot.events().size());
        for (int i = 0; i < frets.length; i++) {
            assertTrue(snapshot.keyDown(frets[i]));
            assertEquals(frets[i], snapshot.events().get(i).code());
            if (i > 0) assertTrue(snapshot.events().get(i).sequence() > snapshot.events().get(i - 1).sequence());
        }
        input.update();
        input.clearKeyState();
        input.capturePhysicalInput();
        input.handleKeyEvent(GLFW_KEY_A, GLFW_REPEAT);
        assertTrue(input.capturePhysicalInput().events().isEmpty(), "a held-key repeat after focus reset is not an attack");
    }

    @Test
    void focusLossProducesReleasesAndUnconsumedEventsDoNotLeakIntoNextTick() {
        InputHandler input = input();
        input.handleKeyEvent(GLFW_KEY_A, GLFW_PRESS);
        input.capturePhysicalInput();
        clock.set(250);
        input.clearKeyState();
        PhysicalInput cleared = input.capturePhysicalInput();
        assertEquals(1, cleared.events().size());
        assertTrue(cleared.events().getFirst().released());
        assertEquals(250, cleared.events().getFirst().timestampNanos());
        assertFalse(cleared.keyDown(GLFW_KEY_A));

        input.handleKeyEvent(GLFW_KEY_S, GLFW_PRESS);
        input.update();
        assertTrue(input.capturePhysicalInput().events().isEmpty());
        assertTrue(input.capturePhysicalInput().keyDown(GLFW_KEY_S));
        assertEquals(1, cleared.events().size(), "captured snapshots stay immutable after updates");
    }

    @Test
    void physicalPadsExposeEveryButtonAndAxisEvenWhenGenesisControllersAreDisabled() {
        configuration.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, false);
        InputHandler input = input();
        boolean[] buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
        buttons[GLFW_GAMEPAD_BUTTON_Y] = true;
        buttons[GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER] = true;
        float[] axes = {-0.4f, 0.5f, -0.6f, 0.7f, 0.8f, 0.9f};
        pads.devices = List.of(GamepadStateSource.DeviceState.connected(3, "Six-axis pad", buttons, axes));
        input.pollPhysicalGamepads();

        PhysicalInput snapshot = input.capturePhysicalInput();

        assertTrue(snapshot.buttonDown(3, PhysicalGamepad.BUTTON_Y));
        assertTrue(snapshot.buttonDown(3, PhysicalGamepad.BUTTON_RIGHT_BUMPER));
        assertEquals(0.7f, snapshot.axis(3, PhysicalGamepad.AXIS_RIGHT_Y));
        assertEquals(0.8f, snapshot.axis(3, PhysicalGamepad.AXIS_LEFT_TRIGGER));
        assertEquals(0.9f, snapshot.axis(3, PhysicalGamepad.AXIS_RIGHT_TRIGGER));
        assertTrue(snapshot.events().isEmpty(), "connecting an already-held pad establishes a baseline");
        assertFalse(input.logical().player1().startHeld());
    }

    @Test
    void padTapBetweenLogicalUpdatesIsCapturedAndDisconnectReleasesHeldControls() {
        InputHandler input = input();
        pads.devices = List.of(pad(0, false));
        input.pollPhysicalGamepads();
        clock.set(150);
        pads.devices = List.of(pad(0, true));
        input.pollPhysicalGamepads();
        clock.set(190);
        pads.devices = List.of(pad(0, false));
        input.pollPhysicalGamepads();
        PhysicalInput tap = input.capturePhysicalInput();
        assertEquals(2, tap.events().size());
        assertTrue(tap.events().get(0).pressed());
        assertEquals(150, tap.events().get(0).timestampNanos());
        assertTrue(tap.events().get(1).released());
        assertEquals(190, tap.events().get(1).timestampNanos());

        pads.devices = List.of(pad(0, true));
        input.pollPhysicalGamepads();
        input.capturePhysicalInput();
        clock.set(260);
        pads.devices = List.of();
        input.pollPhysicalGamepads();
        PhysicalInput disconnected = input.capturePhysicalInput();
        assertTrue(disconnected.gamepads().isEmpty());
        assertEquals(1, disconnected.events().size());
        assertTrue(disconnected.events().getFirst().released());
        assertEquals(260, disconnected.events().getFirst().timestampNanos());
    }

    @Test
    void whammyAxisChangesKeepObservationTimeAndDisconnectReturnsTriggerToRest() {
        InputHandler input = input();
        float[] axes = {0, 0, 0, 0, -1, -1};
        pads.devices = List.of(GamepadStateSource.DeviceState.connected(0, "pad", new boolean[15], axes));
        input.pollPhysicalGamepads();
        clock.set(180);
        axes[PhysicalGamepad.AXIS_RIGHT_TRIGGER] = 0.75f;
        pads.devices = List.of(GamepadStateSource.DeviceState.connected(0, "pad", new boolean[15], axes));
        input.pollPhysicalGamepads();
        PhysicalInput changed = input.capturePhysicalInput();
        assertEquals(1, changed.events().size());
        PhysicalInputEvent event = changed.events().getFirst();
        assertEquals(PhysicalInputEvent.Kind.AXIS, event.kind());
        assertEquals(PhysicalGamepad.AXIS_RIGHT_TRIGGER, event.code());
        assertEquals(0.75f, event.value());
        assertEquals(180, event.timestampNanos());
        assertFalse(event.pressed());
        assertFalse(event.released());

        pads.devices = List.of();
        input.pollPhysicalGamepads();
        PhysicalInput disconnected = input.capturePhysicalInput();
        assertEquals(-1f, disconnected.events().getFirst().value());
        assertEquals(-1f, disconnected.axis(0, PhysicalGamepad.AXIS_RIGHT_TRIGGER));
    }

    @Test
    void rawSamplingAndLogicalPollingShareFreshPhysicalStateWithoutStaleReuse() {
        configuration.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        configuration.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        InputHandler input = input();
        pads.devices = List.of(pad(0, false));
        input.pollPhysicalGamepads();
        input.refreshLogicalSnapshot();
        assertEquals(1, pads.polls);

        pads.devices = List.of(pad(0, true));
        input.refreshLogicalSnapshot();
        assertEquals(2, pads.polls);
        assertEquals(InputActionMasks.ACTION_A, input.logical().player1().actionPressedMask());
        assertEquals(1, input.capturePhysicalInput().events().size());
    }

    @Test
    void logicalReplayOverrideDoesNotRewritePhysicalTimingInput() {
        configuration.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        InputHandler input = input();
        pads.devices = List.of(pad(0, false));
        input.pollPhysicalGamepads();
        input.setLogicalOverride(LogicalInputSnapshot.neutral());
        clock.set(160);
        input.handleKeyEvent(GLFW_KEY_A, GLFW_PRESS);
        pads.devices = List.of(pad(0, true));
        input.pollPhysicalGamepads();
        input.refreshLogicalSnapshot();

        assertEquals(PlayerInputState.neutral(), input.logical().player1());
        PhysicalInput physical = input.capturePhysicalInput();
        assertTrue(physical.keyDown(GLFW_KEY_A));
        assertTrue(physical.buttonDown(0, PhysicalGamepad.BUTTON_X));
        assertEquals(2, physical.events().size());
        assertTrue(physical.events().stream().allMatch(event -> event.timestampNanos() == 160));
    }

    @Test
    void boundedQueueReportsLostEventsAndCaptureResetsOverflow() {
        InputHandler input = input();
        for (int i = 0; i < 2050; i++) {
            input.handleKeyEvent(GLFW_KEY_A, GLFW_PRESS);
            input.handleKeyEvent(GLFW_KEY_A, GLFW_RELEASE);
        }
        PhysicalInput snapshot = input.capturePhysicalInput();
        assertEquals(4096, snapshot.events().size());
        assertEquals(4, snapshot.droppedEvents());
        assertFalse(snapshot.keyDown(GLFW_KEY_A));
        assertEquals(0, input.capturePhysicalInput().droppedEvents());
    }

    @Test
    void snapshotAndDeviceArraysDoNotExposeMutableInputStorage() {
        boolean[] buttons = new boolean[15];
        float[] axes = new float[6];
        buttons[0] = true;
        axes[2] = 0.5f;
        PhysicalGamepad pad = new PhysicalGamepad(0, "pad", buttons, axes);
        buttons[0] = false;
        axes[2] = 0;
        pad.buttons()[0] = false;
        pad.axes()[2] = 0;
        assertTrue(pad.buttonDown(0));
        assertEquals(0.5f, pad.axis(2));
        PhysicalInput snapshot = new PhysicalInput(0, List.of(GLFW_KEY_A), List.of(pad), List.of(), 0);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.keysDown().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.gamepads().clear());
    }

    private static GamepadStateSource.DeviceState pad(int id, boolean down) {
        boolean[] buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
        buttons[GLFW_GAMEPAD_BUTTON_X] = down;
        return GamepadStateSource.DeviceState.connected(id, "pad", buttons, 0, 0);
    }

    private static final class MutablePads implements GamepadStateSource {
        List<DeviceState> devices = List.of();
        int polls;

        @Override
        public List<DeviceState> pollDevices() {
            polls++;
            return devices;
        }
    }
}
