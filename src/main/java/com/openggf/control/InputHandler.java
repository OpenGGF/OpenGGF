package com.openggf.control;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Handles keyboard input from GLFW.
 * Key codes are GLFW key codes (GLFW_KEY_*).
 */
public class InputHandler {
	// GLFW key codes can range from 0 to GLFW_KEY_LAST (348)
	private static final int MAX_KEYS = 512;
	private static final int MAX_MOUSE_BUTTONS = 16;
	boolean[] keys = new boolean[MAX_KEYS];
	boolean[] previousKeys = new boolean[MAX_KEYS];
	boolean[] mouseButtons = new boolean[MAX_MOUSE_BUTTONS];
	boolean[] previousMouseButtons = new boolean[MAX_MOUSE_BUTTONS];
	private double mouseX;
	private double mouseY;
	private boolean mouseInputSeen;

	/**
	 * Creates a new InputHandler.
	 * Key events should be delivered via handleKeyEvent() from GLFW callback.
	 */
	public InputHandler() {
	}

	/**
	 * Handle a key event from GLFW.
	 *
	 * @param key    The GLFW key code
	 * @param action GLFW_PRESS, GLFW_RELEASE, or GLFW_REPEAT
	 */
	public void handleKeyEvent(int key, int action) {
		if (key >= 0 && key < MAX_KEYS) {
			if (action == GLFW_PRESS || action == GLFW_REPEAT) {
				keys[key] = true;
			} else if (action == GLFW_RELEASE) {
				keys[key] = false;
			}
		}
	}

	public void handleMouseMove(double x, double y) {
		mouseX = x;
		mouseY = y;
		mouseInputSeen = true;
	}

	public void handleMouseButton(int button, int action) {
		mouseInputSeen = true;
		if (button >= 0 && button < MAX_MOUSE_BUTTONS) {
			if (action == GLFW_PRESS || action == GLFW_REPEAT) {
				mouseButtons[button] = true;
			} else if (action == GLFW_RELEASE) {
				mouseButtons[button] = false;
			}
		}
	}

	/**
	 * Checks whether a specific key is down.
	 *
	 * @param keyCode The GLFW key code to check
	 * @return Whether the key is pressed or not
	 */
	public boolean isKeyDown(int keyCode) {
		if (keyCode >= 0 && keyCode < MAX_KEYS) {
			return keys[keyCode];
		}
		return false;
	}

	/**
	 * Checks whether a specific key was just pressed this frame.
	 *
	 * @param keyCode The GLFW key code to check
	 * @return Whether the key was just pressed
	 */
	public boolean isKeyPressed(int keyCode) {
		if (keyCode >= 0 && keyCode < MAX_KEYS) {
			return keys[keyCode] && !previousKeys[keyCode];
		}
		return false;
	}

	/**
	 * Returns true when at least one key transitioned from not-pressed to
	 * pressed during the current frame. Mirrors {@link #isKeyPressed(int)}
	 * but checks all keys at once. Used by full-screen prompts that
	 * accept any input to dismiss.
	 */
	public boolean isAnyKeyJustPressed() {
		for (int i = 0; i < MAX_KEYS; i++) {
			if (keys[i] && !previousKeys[i]) {
				return true;
			}
		}
		return false;
	}

	public boolean isShiftDown() {
		return isKeyDown(GLFW_KEY_LEFT_SHIFT) || isKeyDown(GLFW_KEY_RIGHT_SHIFT);
	}

	public boolean isControlDown() {
		return isKeyDown(GLFW_KEY_LEFT_CONTROL) || isKeyDown(GLFW_KEY_RIGHT_CONTROL);
	}

	public boolean isAltDown() {
		return isKeyDown(GLFW_KEY_LEFT_ALT) || isKeyDown(GLFW_KEY_RIGHT_ALT);
	}

	public boolean isAnyModifierDown() {
		return isShiftDown() || isControlDown() || isAltDown();
	}

	public boolean isKeyPressedWithoutModifiers(int keyCode) {
		return isKeyPressed(keyCode) && !isAnyModifierDown();
	}

	public double getMouseX() {
		return mouseX;
	}

	public double getMouseY() {
		return mouseY;
	}

	public boolean isMouseButtonDown(int button) {
		if (button >= 0 && button < MAX_MOUSE_BUTTONS) {
			return mouseButtons[button];
		}
		return false;
	}

	public boolean isMouseButtonPressed(int button) {
		if (button >= 0 && button < MAX_MOUSE_BUTTONS) {
			return mouseButtons[button] && !previousMouseButtons[button];
		}
		return false;
	}

	public boolean hasMouseInputSeen() {
		return mouseInputSeen;
	}

	/**
	 * Updates the input handler state. Should be called at the end of the game loop.
	 */
	public void update() {
		System.arraycopy(keys, 0, previousKeys, 0, MAX_KEYS);
		System.arraycopy(mouseButtons, 0, previousMouseButtons, 0, MAX_MOUSE_BUTTONS);
	}
}
