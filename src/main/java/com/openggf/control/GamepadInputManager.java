package com.openggf.control;

import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_A;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_B;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_START;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_X;

public class GamepadInputManager {
    private final GamepadStateSource stateSource;
    private PlayerInputState previousP1 = PlayerInputState.neutral();
    private PlayerInputState previousP2 = PlayerInputState.neutral();

    public GamepadInputManager(GamepadStateSource stateSource) {
        this.stateSource = Objects.requireNonNull(stateSource, "stateSource");
    }

    public LogicalInputSnapshot poll(InputBindings bindings) {
        if (bindings == null || !bindings.controllerEnabled()) {
            resetPreviousStates();
            return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), PlayerInputState.neutral());
        }

        List<GamepadStateSource.DeviceState> connected = connectedDevices();
        int nextPad = 0;

        PlayerInputState p1 = PlayerInputState.neutral();
        if (isAuto(bindings.controllerPlayer1()) && nextPad < connected.size()) {
            p1 = mapDevice(connected.get(nextPad++), bindings.controllerDeadzone(), previousP1);
        }

        PlayerInputState p2 = PlayerInputState.neutral();
        if (isAuto(bindings.controllerPlayer2()) && nextPad < connected.size()) {
            p2 = mapDevice(connected.get(nextPad), bindings.controllerDeadzone(), previousP2);
        }

        previousP1 = p1;
        previousP2 = p2;
        return LogicalInputSnapshot.ofPlayers(p1, p2);
    }

    private List<GamepadStateSource.DeviceState> connectedDevices() {
        List<GamepadStateSource.DeviceState> connected = new ArrayList<>();
        for (GamepadStateSource.DeviceState device : stateSource.pollDevices()) {
            if (device != null && device.connected()) {
                connected.add(device);
            }
        }
        return connected;
    }

    private PlayerInputState mapDevice(
            GamepadStateSource.DeviceState device,
            double deadzone,
            PlayerInputState previous) {
        int heldMask = directionMask(device, deadzone);
        int actionHeldMask = actionMask(device);
        boolean startHeld = device.buttonDown(GLFW_GAMEPAD_BUTTON_START);

        int pressedMask = heldMask & ~previous.heldMask();
        int actionPressedMask = actionHeldMask & ~previous.actionHeldMask();
        boolean startPressed = startHeld && !previous.startHeld();

        return PlayerInputState.of(
                heldMask,
                pressedMask,
                actionHeldMask,
                actionPressedMask,
                startHeld,
                startPressed);
    }

    private int directionMask(GamepadStateSource.DeviceState device, double deadzone) {
        int mask = 0;
        if (device.buttonDown(GLFW_GAMEPAD_BUTTON_DPAD_UP) || device.leftY() < -deadzone) {
            mask |= AbstractPlayableSprite.INPUT_UP;
        }
        if (device.buttonDown(GLFW_GAMEPAD_BUTTON_DPAD_DOWN) || device.leftY() > deadzone) {
            mask |= AbstractPlayableSprite.INPUT_DOWN;
        }
        if (device.buttonDown(GLFW_GAMEPAD_BUTTON_DPAD_LEFT) || device.leftX() < -deadzone) {
            mask |= AbstractPlayableSprite.INPUT_LEFT;
        }
        if (device.buttonDown(GLFW_GAMEPAD_BUTTON_DPAD_RIGHT) || device.leftX() > deadzone) {
            mask |= AbstractPlayableSprite.INPUT_RIGHT;
        }
        return mask;
    }

    private int actionMask(GamepadStateSource.DeviceState device) {
        int mask = 0;
        if (device.buttonDown(GLFW_GAMEPAD_BUTTON_X)) {
            mask |= InputActionMasks.ACTION_A;
        }
        if (device.buttonDown(GLFW_GAMEPAD_BUTTON_A)) {
            mask |= InputActionMasks.ACTION_B;
        }
        if (device.buttonDown(GLFW_GAMEPAD_BUTTON_B)) {
            mask |= InputActionMasks.ACTION_C;
        }
        return mask;
    }

    private boolean isAuto(String assignment) {
        return assignment != null && "auto".equalsIgnoreCase(assignment.trim());
    }

    private void resetPreviousStates() {
        previousP1 = PlayerInputState.neutral();
        previousP2 = PlayerInputState.neutral();
    }
}
