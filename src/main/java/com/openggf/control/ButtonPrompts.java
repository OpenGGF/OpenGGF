package com.openggf.control;

import com.openggf.configuration.GlfwKeyNameResolver;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_LAST;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;

/**
 * Display names for a local player's buttons, derived from the live bindings rather than
 * fixed menu keys. Each logical player keeps their own last intentional device: a keyboard
 * player sees their bound key (P1 A defaults to "Space", P2 A to "Right Shift"), a pad player
 * sees the physical button that produces the action in that pad's family ("X", "Square",
 * "West"). Before a player's first press the session-wide last intentional device decides.
 * Replay overrides never change the remembered device.
 */
@com.openggf.game.ModApi
public final class ButtonPrompts {
    /** Logical Genesis buttons plus the shared rewind control. */
    @com.openggf.game.ModApi
    public enum Button { A, B, C, START, REWIND }

    private ButtonPrompts() {
    }

    /**
     * Short label such as "Space", "Right Shift", "Cross" or "LB" for player 0 (P1) or 1 (P2).
     * Empty when neither that player's keyboard binding nor an assigned pad can produce the button.
     * REWIND uses the shared rewind key, or the left bumper when the player's pad is the primary pad.
     */
    public static Optional<String> label(InputHandler input, int player, Button button) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(button, "button");
        if (player != 0 && player != 1) throw new IllegalArgumentException("Player must be 0 or 1");
        Optional<String> key = keyLabel(input.currentBindings(), player, button);
        ControllerPromptStyle style = input.playerControllerStyle(player);
        Optional<String> pad = style == null || button == Button.REWIND && !input.playerPadIsPrimary(player)
                ? Optional.empty() : Optional.of(style.button(switch (button) {
                    case A -> GamepadInputManager.ACTION_A_BUTTON;
                    case B -> GamepadInputManager.ACTION_B_BUTTON;
                    case C -> GamepadInputManager.ACTION_C_BUTTON;
                    case START -> GamepadInputManager.START_BUTTON;
                    case REWIND -> GamepadInputManager.REWIND_BUTTON;
                }));
        if (pad.isPresent() && input.playerUsesControllerPresentation(player)) return pad;
        return key.isPresent() ? key : pad;
    }

    /** Readable GLFW key name ("Right Shift", "Keypad Enter", "F5"); empty for an unbound key. */
    public static Optional<String> keyName(int glfwKey) {
        if (glfwKey < GLFW_KEY_SPACE || glfwKey > GLFW_KEY_LAST) return Optional.empty();
        String raw = GlfwKeyNameResolver.nameOf(glfwKey);
        if (raw.equals(Integer.toString(glfwKey)) && raw.length() > 1) return Optional.of("Key " + glfwKey);
        var words = new StringBuilder();
        for (String word : raw.split("_")) {
            if (word.isEmpty()) continue;
            if (!words.isEmpty()) words.append(' ');
            if (word.equals("KP")) words.append("Keypad");
            else words.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return Optional.of(words.toString());
    }

    private static Optional<String> keyLabel(InputBindings bindings, int player, Button button) {
        int key = switch (button) {
            case A -> player == 0 ? bindings.p1A() : bindings.p2A();
            case B -> player == 0 ? bindings.p1B() : bindings.p2B();
            case C -> player == 0 ? bindings.p1C() : bindings.p2C();
            case START -> player == 0 ? bindings.p1Start() : bindings.p2Start();
            case REWIND -> bindings.rewindKey();
        };
        return keyName(key);
    }
}
