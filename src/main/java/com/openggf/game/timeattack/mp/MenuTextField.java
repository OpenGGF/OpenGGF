package com.openggf.game.timeattack.mp;

import com.openggf.control.InputHandler;

import java.util.Objects;

import static org.lwjgl.glfw.GLFW.*;

/** Minimal polled text field for address and chat entry. */
public final class MenuTextField {
    private final int maxLength;
    private final String allowedExtraChars;
    private final StringBuilder text = new StringBuilder();

    public MenuTextField(int maxLength, String allowedExtraChars) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("maxLength must be non-negative");
        }
        this.maxLength = maxLength;
        this.allowedExtraChars = Objects.requireNonNull(allowedExtraChars,
                "allowedExtraChars");
    }

    public void feedChar(char value) {
        if (text.length() >= maxLength) {
            return;
        }
        if (Character.isLetterOrDigit(value)
                || allowedExtraChars.indexOf(value) >= 0) {
            text.append(value);
        }
    }

    public void backspace() {
        if (!text.isEmpty()) {
            text.setLength(text.length() - 1);
        }
    }

    public String text() {
        return text.toString();
    }

    public void setText(String value) {
        text.setLength(0);
        Objects.requireNonNull(value, "value");
        for (int i = 0; i < value.length(); i++) {
            feedChar(value.charAt(i));
        }
    }

    public void poll(InputHandler input) {
        Objects.requireNonNull(input, "input");
        if (input.isKeyPressed(GLFW_KEY_BACKSPACE)) {
            backspace();
            return;
        }
        Character typed = typedCharacter(input);
        if (typed != null) {
            feedChar(typed);
        }
    }

    private static Character typedCharacter(InputHandler input) {
        boolean shift = input.isShiftDown();
        for (int key = GLFW_KEY_A; key <= GLFW_KEY_Z; key++) {
            if (input.isKeyPressed(key)) {
                char base = (char) ('a' + key - GLFW_KEY_A);
                return shift ? Character.toUpperCase(base) : base;
            }
        }
        if (shift && input.isKeyPressed(GLFW_KEY_1)) return '!';
        for (int key = GLFW_KEY_0; key <= GLFW_KEY_9; key++) {
            if (input.isKeyPressed(key)) {
                return (char) ('0' + key - GLFW_KEY_0);
            }
        }
        if (input.isKeyPressed(GLFW_KEY_SPACE)) return ' ';
        if (input.isKeyPressed(GLFW_KEY_MINUS)) return shift ? '_' : '-';
        if (input.isKeyPressed(GLFW_KEY_PERIOD)) return '.';
        if (input.isKeyPressed(GLFW_KEY_COMMA)) return ',';
        if (input.isKeyPressed(GLFW_KEY_APOSTROPHE)) return '\'';
        if (input.isKeyPressed(GLFW_KEY_SEMICOLON)) return shift ? ':' : ';';
        if (input.isKeyPressed(GLFW_KEY_SLASH)) return shift ? '?' : '/';
        if (input.isKeyPressed(GLFW_KEY_EQUAL) && shift) return '+';
        return null;
    }
}
