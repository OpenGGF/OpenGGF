package com.openggf.control;

import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_A;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_B;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_BACK;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_START;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_X;
import static org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_Y;

public class GamepadInputManager {
    private final GamepadStateSource stateSource;
    private PlayerInputState previousP1 = PlayerInputState.neutral();
    private PlayerInputState previousP2 = PlayerInputState.neutral();
    private boolean previousDebugModeButtonHeld;
    private boolean debugModeTogglePressed;
    private boolean rewindHeld;
    private boolean previousBackButtonHeld;
    private boolean backButtonPressed;
    private boolean previousFrameStepButtonHeld;
    private boolean frameStepTogglePressed;
    // Presentation history is per physical device, independent of gameplay mappings
    // and replay overrides. Connecting a pad establishes a baseline, not an action.
    private final Map<Integer, Long> presentationHeld = new HashMap<>();
    private boolean presentationPress;
    private String presentationName = "";

    private ControllerPromptStyle presentationStyle = ControllerPromptStyle.forName("");
    // Pads currently assigned to logical players, for per-player button prompts.
    private final ControllerPromptStyle[] playerStyles = new ControllerPromptStyle[2];
    private final boolean[] playerPrimary = new boolean[2];

    /** Physical buttons that produce the logical Genesis actions; prompts and mapping share them. */
    static final int ACTION_A_BUTTON = GLFW_GAMEPAD_BUTTON_X;
    static final int ACTION_B_BUTTON = GLFW_GAMEPAD_BUTTON_A;
    static final int ACTION_C_BUTTON = GLFW_GAMEPAD_BUTTON_B;
    static final int START_BUTTON = GLFW_GAMEPAD_BUTTON_START;
    static final int REWIND_BUTTON = GLFW_GAMEPAD_BUTTON_LEFT_BUMPER;

    ControllerPromptStyle presentationStyle() { return presentationStyle; }

    /** Prompt family of the pad assigned to a logical player at the last poll, or null. */
    ControllerPromptStyle playerStyle(int player) { return playerStyles[player]; }

    /** Whether that player's pad is the primary pad, which owns the shared rewind bumper. */
    boolean playerPadIsPrimary(int player) { return playerPrimary[player]; }

    public GamepadInputManager(GamepadStateSource stateSource) {
        this.stateSource = Objects.requireNonNull(stateSource, "stateSource");
    }

    public LogicalInputSnapshot poll(InputBindings bindings) {
        if (bindings == null || !bindings.controllerEnabled()) {
            resetPreviousStates();
            return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), PlayerInputState.neutral());
        }

        List<GamepadStateSource.DeviceState> connected = connectedDevices();
        trackPresentation(connected, bindings.controllerDeadzone());
        int nextPad = 0;
        java.util.Arrays.fill(playerStyles, null);
        java.util.Arrays.fill(playerPrimary, false);

        PlayerInputState p1 = PlayerInputState.neutral();
        if (isAuto(bindings.controllerPlayer1()) && nextPad < connected.size()) {
            assign(0, connected.get(nextPad), nextPad == 0);
            p1 = mapDevice(connected.get(nextPad++), bindings.controllerDeadzone(), previousP1);
        }

        PlayerInputState p2 = PlayerInputState.neutral();
        if (isAuto(bindings.controllerPlayer2()) && nextPad < connected.size()) {
            assign(1, connected.get(nextPad), nextPad == 0);
            p2 = mapDevice(connected.get(nextPad), bindings.controllerDeadzone(), previousP2);
        }

        previousP1 = p1;
        previousP2 = p2;

        GamepadStateSource.DeviceState primary = connected.isEmpty() ? null : connected.get(0);
        boolean debugModeHeld = primary != null && primary.buttonDown(GLFW_GAMEPAD_BUTTON_Y);
        debugModeTogglePressed = debugModeHeld && !previousDebugModeButtonHeld;
        previousDebugModeButtonHeld = debugModeHeld;
        rewindHeld = primary != null && primary.buttonDown(REWIND_BUTTON);

        boolean backButtonHeld = primary != null && primary.buttonDown(GLFW_GAMEPAD_BUTTON_BACK);
        backButtonPressed = backButtonHeld && !previousBackButtonHeld;
        previousBackButtonHeld = backButtonHeld;

        boolean frameStepButtonHeld = primary != null && primary.buttonDown(GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER);
        frameStepTogglePressed = frameStepButtonHeld && !previousFrameStepButtonHeld;
        previousFrameStepButtonHeld = frameStepButtonHeld;

        return LogicalInputSnapshot.ofPlayers(p1, p2);
    }

    /**
     * Edge-triggered: true only on the frame the North face button (Y / Triangle)
     * transitions to held on the primary connected pad. Mirrors the Debug Movement
     * toggle keyboard binding ({@code debug.keys.debugMode}).
     */
    public boolean isDebugModeTogglePressed() {
        return debugModeTogglePressed;
    }

    /**
     * Held state of the left bumper (L1) on the primary connected pad. Mirrors the
     * live-rewind hold-key keyboard binding ({@code rewind.liveKey}).
     */
    public boolean isRewindHeld() {
        return rewindHeld;
    }

    /**
     * Edge-triggered: true only on the frame the Back/Select/View button transitions
     * to held on the primary connected pad. Mirrors the main-menu options-panel Tab
     * toggle ({@link com.openggf.game.MasterTitleScreen} / {@link com.openggf.game.LaunchConfigPanel}).
     */
    public boolean isBackButtonPressed() {
        return backButtonPressed;
    }

    /**
     * Edge-triggered: true only on the frame the right bumper (RB/R1) transitions to
     * held on the primary connected pad. Mirrors the frame-advance keyboard binding
     * ({@code debug.keys.frameStep}).
     */
    public boolean isFrameStepTogglePressed() {
        return frameStepTogglePressed;
    }

    boolean hasPresentationPress() {
        return presentationPress;
    }

    private void trackPresentation(List<GamepadStateSource.DeviceState> connected, double deadzone) {
        presentationPress = false;
        Set<Integer> connectedIds = new HashSet<>();
        for (GamepadStateSource.DeviceState device : connected) {
            int id = device.joystickId();
            connectedIds.add(id);
            Long previous = presentationHeld.get(id);
            long held = 0;
            boolean[] buttons = device.buttons();
            for (int i = 0; i < Math.min(buttons.length, 32); i++) {
                if (buttons[i]) {
                    held |= 1L << i;
                }
            }
            // Hysteresis requires returning toward centre before the same stick
            // direction can count again, avoiding prompt flicker at the deadzone.
            float[] axes = { -device.leftY(), device.leftY(), -device.leftX(), device.leftX() };
            for (int i = 0; i < axes.length; i++) {
                long bit = 1L << (32 + i);
                double threshold = previous != null && (previous & bit) != 0 ? deadzone * 0.5 : deadzone;
                if (axes[i] > threshold) {
                    held |= bit;
                }
            }
            if (previous != null && (held & ~previous) != 0) {
                presentationPress = true;
                if (!presentationName.equals(device.name())) {
                    presentationName = device.name();
                    presentationStyle = ControllerPromptStyle.forName(presentationName);
                }
            }
            presentationHeld.put(id, held);
        }
        presentationHeld.keySet().retainAll(connectedIds);
    }

    private void assign(int player, GamepadStateSource.DeviceState device, boolean primary) {
        playerStyles[player] = ControllerPromptStyle.forName(device.name());
        playerPrimary[player] = primary;
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
        boolean startHeld = device.buttonDown(START_BUTTON);

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
        if (device.buttonDown(ACTION_A_BUTTON)) {
            mask |= InputActionMasks.ACTION_A;
        }
        if (device.buttonDown(ACTION_B_BUTTON)) {
            mask |= InputActionMasks.ACTION_B;
        }
        if (device.buttonDown(ACTION_C_BUTTON)) {
            mask |= InputActionMasks.ACTION_C;
        }
        return mask;
    }

    private boolean isAuto(String assignment) {
        return assignment != null && "auto".equalsIgnoreCase(assignment.trim());
    }

    private void resetPreviousStates() {
        java.util.Arrays.fill(playerStyles, null);
        java.util.Arrays.fill(playerPrimary, false);
        presentationHeld.clear();
        presentationPress = false;
        previousP1 = PlayerInputState.neutral();
        previousP2 = PlayerInputState.neutral();
        previousDebugModeButtonHeld = false;
        debugModeTogglePressed = false;
        rewindHeld = false;
        previousBackButtonHeld = false;
        backButtonPressed = false;
        previousFrameStepButtonHeld = false;
        frameStepTogglePressed = false;
    }
}
