package com.openggf.control;

import com.openggf.InputBindingFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Handles keyboard input from GLFW.
 * Key codes are GLFW key codes (GLFW_KEY_*).
 */
@com.openggf.game.ModApi
public class InputHandler {
	// GLFW key codes can range from 0 to GLFW_KEY_LAST (348)
	private static final int MAX_KEYS = 512;
	private static final int MAX_MOUSE_BUTTONS = 16;
	private static final int MAX_SCENE_INPUT_EVENTS = 4096;
	boolean[] keys = new boolean[MAX_KEYS];
	boolean[] previousKeys = new boolean[MAX_KEYS];
	boolean[] mouseButtons = new boolean[MAX_MOUSE_BUTTONS];
	boolean[] previousMouseButtons = new boolean[MAX_MOUSE_BUTTONS];
	private final Supplier<InputBindings> inputBindingsSource;
	private InputBindings inputBindings;
	private final KeyboardInputMapper keyboardInputMapper;
	private final GamepadInputManager gamepadInputManager;
	private final LongSupplier monotonicClock;
	private final List<PhysicalInputEvent> pendingSceneEvents = new ArrayList<>();
	private final Map<Integer, PhysicalGamepad> sceneGamepads = new LinkedHashMap<>();
	private long sceneEventSequence;
	private int droppedSceneEvents;
	private LogicalInputSnapshot logicalSnapshot = LogicalInputSnapshot.neutral();
	private LogicalInputSnapshot logicalOverride;
    RecordedInputOwnership.Lease recordedInputOwner;
	private LogicalInputSnapshot physicalGamepadSnapshot = LogicalInputSnapshot.neutral();
	private LogicalInputSnapshot menuGamepadSnapshot = LogicalInputSnapshot.neutral();
	private double mouseX;
	private double mouseY;
	private boolean mouseInputSeen;
	/** Engine-internal ({@link MouseWheel#of}); not part of the creator API. */
	final MouseWheel wheel = new MouseWheel(this);
	private boolean controllerPresentation;
	// Host-only: while a window-level editor front end owns the pads, mappings see a neutral pad.
	private boolean gamepadSuppressed;
	private boolean keyboardPresentationPending;
	// Per logical player: null until that player's first intentional press, then whether it was a pad.
	private final Boolean[] playerControllerPresentation = new Boolean[2];
	final MenuRepeat menuRepeat = new MenuRepeat();
	long menuFrame;
	private final StringBuilder menuTypedText = new StringBuilder();
	private long keyboardTextCaptureFrame = Long.MIN_VALUE;
	final TextInputSession textInput = new TextInputSession(this);
	private InputHandlerInternalAccess.InputPass inputPass;
	// Synchronous Engine handoff only: never survives the next owner's invocation.
	private InputHandlerInternalAccess.InputPass handedOffPass;
	private boolean mappingKeyboard;
    private final OrderedGameplayInput orderedGameplay = new OrderedGameplayInput();
    private List<Integer> gameplayPadOwners;

    private OrderedGameplayInput.Sample admittedPhysicalSample() {
        return inputPass == null ? null : inputPass.admission;
    }

    void discardOrderedGameplayInput() {
        orderedGameplay.discardAll(admittedPhysicalSample());
    }

    LogicalInputSnapshot previewGameplayInput() {
        if (inputPass == null || !inputPass.observationOnly) throw new IllegalStateException("No zero-step input owner");
        // Start already handled by the zero-step host cannot toggle pause again
        // when its retained gameplay prefix reaches the canonical pass.
        if (logicalSnapshot.player1().startPressed()) {
            orderedGameplay.discard(control -> control == inputBindings.p1Start()
                    || control == OrderedGameplayInput.KEY_COUNT + 7, admittedPhysicalSample());
        }
        inputPass.previewGameplay = true;
        refreshLogicalSnapshot();
        return logicalSnapshot;
    }

    private boolean gameplayKey(int key) {
        return key == inputBindings.p1Up() || key == inputBindings.p1Down()
                || key == inputBindings.p1Left() || key == inputBindings.p1Right()
                || key == inputBindings.p1A() || key == inputBindings.p1B() || key == inputBindings.p1C()
                || key == inputBindings.p1Start() || key == inputBindings.p2Up() || key == inputBindings.p2Down()
                || key == inputBindings.p2Left() || key == inputBindings.p2Right()
                || key == inputBindings.p2A() || key == inputBindings.p2B() || key == inputBindings.p2C()
                || key == inputBindings.p2Start();
    }

    private void discardClaimedGameplayKeys() {
        orderedGameplay.discard(control -> control < MAX_KEYS && hiddenByTextCapture(control), admittedPhysicalSample());
    }

    void discardTextGameplayKeys() {
        orderedGameplay.discard(control -> control < MAX_KEYS && isTextCaptureKey(control), admittedPhysicalSample());
    }

    private void observeGameplayPads() {
        var observation = gamepadInputManager.observedPlayers(inputBindings);
        orderedGameplay.observePlayers(observation.players().player1(), observation.players().player2());
        if (gameplayPadOwners != null && !gameplayPadOwners.equals(observation.owners())) {
            orderedGameplay.discard(control -> control >= MAX_KEYS, admittedPhysicalSample());
        }
        gameplayPadOwners = observation.owners();
        if (gamepadSuppressed) orderedGameplay.discard(control -> control >= MAX_KEYS, admittedPhysicalSample());
        if (logicalOverride != null) discardOrderedGameplayInput();
    }

	InputHandlerInternalAccess.InputPass openInputPass(boolean hostControls) {
        return openInputPass(hostControls, false);
    }

    InputHandlerInternalAccess.InputPass openInputPass(boolean hostControls, boolean observationOnly) {
		if (inputPass != null) throw new IllegalStateException("An input pass already owns this handler");
		if (hostControls && handedOffPass != null) throw new IllegalStateException("A host handoff is already active");
		inputPass = new InputHandlerInternalAccess.InputPass(this, hostControls, observationOnly);
		if (handedOffPass != null) {
			inputPass.gamepad = handedOffPass.gamepad;
			inputPass.physicalGamepad = handedOffPass.physicalGamepad;
			inputPass.priorGamepad = handedOffPass.priorGamepad;
			handedOffPass = null;
		}
		return inputPass;
	}

	void handoffInputPass(InputHandlerInternalAccess.InputPass pass, Runnable nextOwner) {
		Objects.requireNonNull(nextOwner, "nextOwner");
		if (inputPass != pass) throw new IllegalStateException("Host pass no longer owns input");
		inputPass = null;
		handedOffPass = pass;
		try {
			nextOwner.run();
		} finally {
			// Some setup/abort paths never open a GameLoop pass. They did not
			// consume this sample, and cannot leave it for another scene/frame.
			if (handedOffPass == pass) {
				handedOffPass = null;
				rollbackInputPass(pass);
			}
		}
	}

	void closeInputPass(InputHandlerInternalAccess.InputPass pass) {
		if (inputPass == pass) {
			rollbackInputPass(pass);
			inputPass = null;
		}
	}

	private void rollbackInputPass(InputHandlerInternalAccess.InputPass pass) {
		if (!pass.acknowledged && pass.priorGamepad != null) {
			gamepadInputManager.restoreLogicalHistory(pass.priorGamepad);
		}
		if (!pass.acknowledged && pass.carry != null && retainedGameplayInput == null) {
			retainedGameplayInput = pass.carry;
		}
	}

	/**
	 * Creates a new InputHandler.
	 * Key events should be delivered via handleKeyEvent() from GLFW callback.
	 */
	public InputHandler() {
		this(InputBindingFactory.standaloneSupplier());
	}

	public InputHandler(Supplier<InputBindings> inputBindingsSource) {
		this(inputBindingsSource, new GamepadInputManager(GamepadStateSource.noop()));
	}

	/**
	 * Creates an InputHandler backed by a caller-supplied {@link GamepadStateSource},
	 * for tests that need to drive gamepad state from outside {@code com.openggf.control}.
	 */
	public InputHandler(Supplier<InputBindings> inputBindingsSource, GamepadStateSource gamepadStateSource) {
		this(inputBindingsSource, new GamepadInputManager(gamepadStateSource));
	}

	/**
	 * Caller-supplied physical devices and monotonic nanosecond clock, for deterministic
	 * timing-sensitive input tests. Live input uses {@link System#nanoTime()}.
	 */
	public InputHandler(Supplier<InputBindings> inputBindingsSource, GamepadStateSource gamepadStateSource,
			LongSupplier monotonicClock) {
		this(inputBindingsSource, new GamepadInputManager(gamepadStateSource), monotonicClock);
	}

	public static InputHandler live(Supplier<InputBindings> inputBindingsSource) {
		return new InputHandler(inputBindingsSource, new GamepadInputManager(new GlfwGamepadStateSource()));
	}

	InputHandler(Supplier<InputBindings> inputBindingsSource, GamepadInputManager gamepadInputManager) {
		this(inputBindingsSource, gamepadInputManager, System::nanoTime);
	}

	private InputHandler(Supplier<InputBindings> inputBindingsSource, GamepadInputManager gamepadInputManager,
			LongSupplier monotonicClock) {
		this.inputBindingsSource = Objects.requireNonNull(inputBindingsSource, "inputBindingsSource");
		this.gamepadInputManager = Objects.requireNonNull(gamepadInputManager, "gamepadInputManager");
		this.monotonicClock = Objects.requireNonNull(monotonicClock, "monotonicClock");
		this.inputBindings = Objects.requireNonNull(inputBindingsSource.get(), "inputBindings");
		this.keyboardInputMapper = new KeyboardInputMapper();
	}

	/**
	 * Handle a key event from GLFW.
	 *
	 * @param key    The GLFW key code
	 * @param action GLFW_PRESS, GLFW_RELEASE, or GLFW_REPEAT
	 */
	public void handleKeyEvent(int key, int action) {
		if (!textInput.key(key, action)) return;
		if (key >= 0 && key < MAX_KEYS) {
			boolean wasDown = keys[key];
			if (action == GLFW_PRESS || action == GLFW_REPEAT) {
				if (action == GLFW_PRESS && !keys[key]) {
					noteKeyboardInput();
				}
				keys[key] = true;
			} else if (action == GLFW_RELEASE) {
				keys[key] = false;
			}
			if (keys[key] != wasDown && action != GLFW_REPEAT) {
				appendSceneEvent(monotonicClock.getAsLong(), PhysicalInputEvent.Kind.KEY,
                        PhysicalInputEvent.KEYBOARD_DEVICE, key, keys[key] ? 1 : 0);
                if (gameplayKey(key)) orderedGameplay.observe(key, keys[key]);
                if (hiddenByTextCapture(key) || logicalOverride != null) {
                    orderedGameplay.discard(control -> control == key, admittedPhysicalSample());
                }
			}
		}
	}

	/**
	 * Samples full gamepad state at the outer event-loop rate, independently of simulation
	 * ticks and Genesis mappings. GLFW supplies state polling rather than hardware event
	 * timestamps, so transitions are timestamped when this poll observes them.
	 */
	public void pollPhysicalGamepads() {
		List<GamepadStateSource.DeviceState> devices = gamepadInputManager.pollPhysicalDevices();
		trackSceneGamepads(devices, monotonicClock.getAsLong());
        observeGameplayPads();
	}

	/**
	 * Captures physical held state and drains pending transitions once for the scene host.
	 * Logical replay overrides do not hide this independent physical channel. Unconsumed
	 * transitions are discarded by {@link #update()} at tick end, so old menus or sessions
	 * cannot inject actions when a scene subsequently opens.
	 */
	public PhysicalInput capturePhysicalInput() {
		List<Integer> heldKeys = new ArrayList<>();
		for (int key = 0; key < keys.length; key++) {
			if (keys[key] && !textInput.hidesKey(key)) heldKeys.add(key);
		}
		PhysicalInput captured = new PhysicalInput(monotonicClock.getAsLong(), heldKeys,
				List.copyOf(sceneGamepads.values()), pendingSceneEvents, droppedSceneEvents);
		pendingSceneEvents.clear();
		droppedSceneEvents = 0;
		return captured;
	}

	private void appendSceneEvent(long timestamp, PhysicalInputEvent.Kind kind, int deviceId, int code, float value) {
		long sequence = ++sceneEventSequence;
		if (pendingSceneEvents.size() < MAX_SCENE_INPUT_EVENTS) {
			pendingSceneEvents.add(new PhysicalInputEvent(sequence, timestamp, kind, deviceId, code, value));
		} else if (droppedSceneEvents < Integer.MAX_VALUE) {
			droppedSceneEvents++;
		}
	}

	private void trackSceneGamepads(List<GamepadStateSource.DeviceState> devices, long timestamp) {
		Map<Integer, PhysicalGamepad> current = new LinkedHashMap<>();
		for (GamepadStateSource.DeviceState device : devices) {
			PhysicalGamepad next = new PhysicalGamepad(device.joystickId(), device.name(), device.buttons(), device.axes());
			PhysicalGamepad previous = sceneGamepads.get(next.deviceId());
			// A newly connected held device establishes a baseline, not an attack.
			if (previous != null) appendGamepadChanges(previous, next, timestamp);
			current.put(next.deviceId(), next);
		}
		for (PhysicalGamepad previous : sceneGamepads.values()) {
			if (!current.containsKey(previous.deviceId())) {
				appendGamepadChanges(previous, null, timestamp);
			}
		}
		sceneGamepads.clear();
		sceneGamepads.putAll(current);
	}

	private void appendGamepadChanges(PhysicalGamepad previous, PhysicalGamepad next, long timestamp) {
		for (int button = 0; button <= PhysicalGamepad.BUTTON_DPAD_LEFT; button++) {
			boolean down = next != null && next.buttonDown(button);
			if (down != previous.buttonDown(button)) {
				appendSceneEvent(timestamp, PhysicalInputEvent.Kind.BUTTON, previous.deviceId(), button, down ? 1 : 0);
			}
		}
		for (int axis = 0; axis <= PhysicalGamepad.AXIS_RIGHT_TRIGGER; axis++) {
			float value = next != null ? next.axis(axis) : PhysicalGamepad.neutralAxis(axis);
			if (Float.compare(value, previous.axis(axis)) != 0) {
				appendSceneEvent(timestamp, PhysicalInputEvent.Kind.AXIS, previous.deviceId(), axis, value);
			}
		}
	}

	public void handleMouseMove(double x, double y) {
		mouseX = x;
		mouseY = y;
		mouseInputSeen = true;
	}

	/** The wheel reaches mouse-input tracking through {@link MouseWheel#scroll}. */
	void noteMouseInput() {
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
	 * @param keyCode The GLFW key code to check, or a negative value for an
	 *        unbound binding, which is never down
	 * @return Whether the key is pressed or not
	 */
	public boolean isKeyDown(int keyCode) {
		// The twin of the guard in isKeyPressed, and load-bearing for the same
		// reason. An unbound binding is -1, and so is rewindKey() when live
		// rewind is unbound, so without this the pad-substitution tail below
		// reports every unbound binding as held for as long as the pad's rewind
		// bumper is -- and rewindHeld is not gated on LIVE_REWIND_ENABLED.
		// P1_B, P1_C, P2_B and P2_C ship unbound and are read here through
		// KeyboardInputMapper, so a held bumper handed both players a phantom
		// B and C with no matching press edge, held disagreeing with pressed.
		if (keyCode < 0) {
			return false;
		}
		var admitted = admittedPhysicalSample();
        boolean down = mappingKeyboard && admitted != null && gameplayKey(keyCode)
                ? admitted.down(keyCode) : keyCode < MAX_KEYS && keys[keyCode];
        if (down && !hiddenByTextCapture(keyCode)) {
			return true;
		}
		return keyCode == inputBindings.rewindKey() && !gamepadSuppressed && mappedRewindHeld();
	}

	/**
	 * Returns the configured keyboard rewind key or the primary pad's rewind bumper,
	 * independently of whether developer live rewind is enabled. Unbinding the key
	 * leaves the bumper available. Movie/trace-owned frames suppress this live input;
	 * the caller owns rewind permission, allowances and press-edge detection.
	 */
	public boolean isRewindHeld() {
		return logicalOverride == null && (isPhysicalKeyDown(inputBindings.rewindKey())
				|| !gamepadSuppressed && gamepadInputManager.isRewindHeld());
	}

	/** Returns raw keyboard state, ignoring any trace/replay logical override. */
	public boolean isPhysicalKeyDown(int keyCode) {
		return keyCode >= 0 && keyCode < MAX_KEYS && keys[keyCode] && !hiddenByTextCapture(keyCode);
	}

	/**
	 * Claims the keyboard's text and editing keys for typed input: printable keys, Enter, Tab,
	 * Backspace, Insert/Delete, the arrows, Home/End/Page keys and the keypad. While claimed,
	 * host shortcuts, keyboard bindings and keyboard pause read those keys as up; Escape, function
	 * keys, modifiers and every gamepad stay with the host. A claim lasts through the next frame,
	 * so the owner renews it once per frame and it lapses on its own when the owner stops.
	 * Typed characters keep arriving through {@link #consumeTypedText()}; the owner reads claimed
	 * keys with {@link #isCapturedKeyDown(int)} and {@link #isCapturedKeyPressed(int)}.
	 */
	public void captureKeyboardText() {
		keyboardTextCaptureFrame = menuFrame;
		textInput.captureLegacy();
        discardTextGameplayKeys();
	}

	TextInputSession.Lease captureKeyboardText(Object owner) {
        keyboardTextCaptureFrame = menuFrame;
        var lease = textInput.captureLegacy(owner);
        discardTextGameplayKeys();
        return lease;
    }

    void prepareKeyboardTextConsumer(Object owner) { textInput.prepareHostConsumer(owner); }
    void releaseKeyboardText(Object owner) { textInput.releaseHostCapture(owner); }

	/** True while a {@link #captureKeyboardText()} claim is in force. */
	public boolean isKeyboardTextCaptured() {
		return keyboardTextCaptureFrame >= menuFrame - 1;
	}

	/** Raw held state for the claim owner, ignoring the claim; false for an out-of-range code. */
	public boolean isCapturedKeyDown(int keyCode) {
		return keyCode >= 0 && keyCode < MAX_KEYS && keys[keyCode] && !textInput.hidesKey(keyCode);
	}

	/** Raw press edge for the claim owner, ignoring the claim; false for an out-of-range code. */
	public boolean isCapturedKeyPressed(int keyCode) {
		return keyCode >= 0 && keyCode < MAX_KEYS && keys[keyCode] && !previousKeys[keyCode] && !textInput.hidesKey(keyCode);
	}

	/**
	 * Characters typed through the operating system's text input since this frame began, in
	 * order, honouring the user's keyboard layout and Shift; control characters are omitted.
	 * Consuming empties the frame's text, so one owner should read it once per frame.
	 */
	public String consumeTypedText() {
		return consumeMenuText();
	}

	private boolean hiddenByTextCapture(int keyCode) {
		return textInput.hidesKey(keyCode) || isKeyboardTextCaptured() && isTextCaptureKey(keyCode);
	}

	/** Printable keys (Space through the GLFW world keys), Enter through End, and the keypad. */
	static boolean isTextCaptureKey(int keyCode) {
		return (keyCode >= GLFW_KEY_SPACE && keyCode <= GLFW_KEY_WORLD_2)
				|| (keyCode >= GLFW_KEY_ENTER && keyCode <= GLFW_KEY_END)
				|| (keyCode >= GLFW_KEY_KP_0 && keyCode <= GLFW_KEY_KP_EQUAL);
	}

	public boolean isPhysicalShiftDown() {
		return isPhysicalKeyDown(GLFW_KEY_LEFT_SHIFT)
				|| isPhysicalKeyDown(GLFW_KEY_RIGHT_SHIFT);
	}

	public boolean isPhysicalControlDown() {
		return isPhysicalKeyDown(GLFW_KEY_LEFT_CONTROL)
				|| isPhysicalKeyDown(GLFW_KEY_RIGHT_CONTROL);
	}

	public boolean isPhysicalAltDown() {
		return isPhysicalKeyDown(GLFW_KEY_LEFT_ALT)
				|| isPhysicalKeyDown(GLFW_KEY_RIGHT_ALT);
	}

	public boolean isPhysicalSuperDown() {
		return isPhysicalKeyDown(GLFW_KEY_LEFT_SUPER)
				|| isPhysicalKeyDown(GLFW_KEY_RIGHT_SUPER);
	}

	/**
	 * Held state for a directional menu-cursor key, keyboard OR gamepad (D-pad/stick),
	 * for callers that drive their own hold-repeat timer (e.g. level-select screens).
	 * {@code directionMask} is one of {@code AbstractPlayableSprite.INPUT_UP/_DOWN/_LEFT/_RIGHT}.
	 * For a single edge-triggered press instead, use {@link #logical()}'s
	 * {@code menuUp()}/{@code menuDown()}/{@code menuLeft()}/{@code menuRight()}.
	 */
	public boolean isDirectionHeld(int keyCode, int directionMask) {
        // Native hold-repeat timers must not start ahead of their logical prefix.
        var sample = admittedPhysicalSample();
        boolean keyboardHeld = sample != null && gameplayKey(keyCode)
                ? logicalOverride == null && sample.down(keyCode) && !hiddenByTextCapture(keyCode)
                : isKeyDown(keyCode);
        return keyboardHeld || (logical().player1().heldMask() & directionMask) != 0;
	}

	/**
	 * Checks whether a specific key was just pressed this frame.
	 *
	 * @param keyCode The GLFW key code to check, or a negative value for an
	 *        unbound binding, which is never pressed
	 * @return Whether the key was just pressed
	 */
	public boolean isKeyPressed(int keyCode) {
		// An explicitly empty binding resolves to -1, and so do the unbound
		// debugModeKey()/frameStepKey() bindings -- so without this an unbound
		// shortcut matches a pad-substitution branch below and fires from a held
		// gamepad button. Unbinding debug mode fired every other unbound binding
		// with it, including all nine playback keys, which ship unbound.
		if (keyCode < 0) {
			return false;
		}
		if (keyCode == inputBindings.debugModeKey()) {
			if (logicalOverride != null) {
				return logicalOverride.debugModeTogglePressed();
			}
			return isRawKeyPressed(keyCode) || gamepadEdgesVisible() && mappedDebugPressed();
		}
		if (keyCode == inputBindings.frameStepKey()) {
			return isRawKeyPressed(keyCode) || gamepadEdgesVisible() && mappedFrameStepPressed();
		}
		return isRawKeyPressed(keyCode);
	}

	/**
	 * Edge-triggered: true only on the frame the gamepad Back/Select/View button on
	 * the primary connected pad transitions to held. Scoped to the main-menu
	 * options-panel Tab toggle ({@code MasterTitleScreen} / {@code LaunchConfigPanel}) —
	 * unlike {@link #isKeyPressed(int)}'s debug-mode/frame-step wiring, this is not a
	 * blanket substitute for every keyboard use of Tab (editor toggle, special stage
	 * entry, art viewer), which are unrelated screens/modes.
	 */
	public boolean isGamepadBackButtonPressed() {
		return logicalOverride == null && gamepadEdgesVisible() && gamepadInputManager.isBackButtonPressed();
	}

    // Native menus combine legacy configured keys with logical controls. In a
    // consuming pass both must use the admitted prefix, never the newer raw key.
    // Keep general shortcuts and physical/text reads on their existing channel.
    boolean nativeMenuKeyPressed(int key) {
        var sample = admittedPhysicalSample();
        if (sample != null && gameplayKey(key)) {
            return logicalOverride == null && sample.pressed(key) && !hiddenByTextCapture(key);
        }
        return isKeyPressed(key);
    }

    // Fixed-key menus are a distinct owner from gameplay bindings. A queued
    // Enter -> C tap must still confirm, not become Back after physical release.
    // Replay owns gameplay admission, while fixed UI keys keep physical authority.
    boolean menuKeyDown(int key) {
        var sample = admittedPhysicalSample();
        return logicalOverride == null && sample != null && gameplayKey(key) ? sample.down(key) && !hiddenByTextCapture(key)
                : key >= 0 && key < MAX_KEYS && keys[key] && !textInput.hidesKey(key);
    }

    boolean menuKeyPressed(int key) {
        var sample = admittedPhysicalSample();
        return logicalOverride == null && sample != null && gameplayKey(key) ? sample.pressed(key) && !hiddenByTextCapture(key)
                : isRawKeyPressed(key);
    }

	boolean isRawKeyPressed(int keyCode) {
		if (keyCode >= 0 && keyCode < MAX_KEYS) {
			if (hiddenByTextCapture(keyCode)) return false;
            var admitted = admittedPhysicalSample();
            if (mappingKeyboard && admitted != null) return admitted.pressed(keyCode);
            return keys[keyCode] && !previousKeys[keyCode];
		}
		return false;
	}

	/** Physical keyboard edge, without the debug/rewind/frame-step gamepad substitutions. */
	public boolean isPhysicalKeyPressed(int keyCode) {
		return isRawKeyPressed(keyCode);
	}

	/**
	 * Returns true when at least one key transitioned from not-pressed to
	 * pressed during the current frame. Mirrors {@link #isKeyPressed(int)}
	 * but checks all keys at once. Used by full-screen prompts that
	 * accept any input to dismiss.
	 */
	public boolean isAnyKeyJustPressed() {
		for (int i = 0; i < MAX_KEYS; i++) {
			if (keys[i] && !previousKeys[i] && !hiddenByTextCapture(i)) {
				return true;
			}
		}
		return false;
	}

	public boolean isShiftDown() {
		if (logicalOverride != null) {
			return logicalOverride.debugShiftDown();
		}
		return isKeyDown(GLFW_KEY_LEFT_SHIFT) || isKeyDown(GLFW_KEY_RIGHT_SHIFT);
	}

	public boolean isControlDown() {
		if (logicalOverride != null) {
			return logicalOverride.debugControlDown();
		}
		return isKeyDown(GLFW_KEY_LEFT_CONTROL) || isKeyDown(GLFW_KEY_RIGHT_CONTROL);
	}

	public boolean isAltDown() {
		if (logicalOverride != null) {
			return logicalOverride.debugAltDown();
		}
		return isKeyDown(GLFW_KEY_LEFT_ALT) || isKeyDown(GLFW_KEY_RIGHT_ALT);
	}

	public boolean isSuperDown() {
		if (logicalOverride != null) {
			return logicalOverride.debugSuperDown();
		}
		return isKeyDown(GLFW_KEY_LEFT_SUPER) || isKeyDown(GLFW_KEY_RIGHT_SUPER);
	}

	public boolean isAnyModifierDown() {
		return isShiftDown() || isControlDown() || isAltDown() || isSuperDown();
	}

	public boolean isKeyPressedWithoutModifiers(int keyCode) {
		return isKeyPressed(keyCode) && !isAnyModifierDown();
	}

	/**
	 * Drops all key state. A key is otherwise cleared only by an observed
	 * GLFW_RELEASE, and the release for a window-switch modifier goes to the
	 * window that took focus, so the modifier would latch for the rest of the
	 * process and disable every shortcut requiring no modifier held.
	 *
	 * <p>{@code previousKeys} is cleared too, or the first press after the clear
	 * is not seen as a rising edge. Mouse state is untouched — a focus change
	 * does not strand a mouse button the way it strands a modifier.
	 */
	public void clearKeyState() {
		long timestamp = monotonicClock.getAsLong();
		for (int key = 0; key < keys.length; key++) {
			if (keys[key]) {
				appendSceneEvent(timestamp, PhysicalInputEvent.Kind.KEY, PhysicalInputEvent.KEYBOARD_DEVICE, key, 0);
			}
		}
		java.util.Arrays.fill(keys, false);
		java.util.Arrays.fill(previousKeys, false);
        for (int key = 0; key < MAX_KEYS; key++) orderedGameplay.observe(key, false);
        discardOrderedGameplayInput();
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

    void publishNativeStagePlayers(PlayerInputState p1, PlayerInputState p2) {
        logicalSnapshot = NativeStageLogicalSample.withPlayers(logicalSnapshot, p1, p2);
    }

	public void setLogicalOverride(LogicalInputSnapshot override) {
		retainedGameplayInput = null;
		if (inputPass != null) inputPass.carry = null;
		logicalOverride = override != null ? override : LogicalInputSnapshot.neutral();
		logicalSnapshot = logicalOverride;
        discardOrderedGameplayInput();
    }

	public void clearLogicalOverride() {
		logicalOverride = null;
	}

    boolean clearLogicalOverrideIfSame(LogicalInputSnapshot expected) {
        if (expected == null || logicalOverride != expected) {
            return false;
        }
        clearLogicalOverride();
        return true;
    }

	public boolean hasLogicalOverride() {
		return logicalOverride != null;
	}

	public void refreshLogicalSnapshot() {
        boolean firstSample = inputPass == null || inputPass.gamepad == null;
        boolean rebound = false;
        if (firstSample) {
            var nextBindings = Objects.requireNonNull(inputBindingsSource.get(), "inputBindings");
            if (!nextBindings.equals(inputBindings)) {
                rebound = true;
                for (int key = 0; key < MAX_KEYS; key++) orderedGameplay.observe(key, keys[key]);
                discardOrderedGameplayInput();
            }
            inputBindings = nextBindings;
        }
		if (firstSample && inputPass != null) inputPass.priorGamepad = gamepadInputManager.logicalHistory();
		PlayerInputState keyboardP1;
		PlayerInputState keyboardP2;
		// Preserve the first refresh's shortcut-substitution baseline even if a
		// keyboard binding aliases a pad debug/step/rewind shortcut. Claims remain live.
		mappingKeyboard = true;
		try {
			keyboardP1 = logicalOverride == null
					? keyboardInputMapper.mapPlayer1(this, inputBindings) : PlayerInputState.neutral();
			keyboardP2 = logicalOverride == null
					? keyboardInputMapper.mapPlayer2(this, inputBindings) : PlayerInputState.neutral();
		} finally {
			mappingKeyboard = false;
		}
		LogicalInputSnapshot gamepadSnapshot;
		if (firstSample) {
			gamepadSnapshot = gamepadInputManager.poll(inputBindings);
			if (inputPass != null) {
				inputPass.gamepad = gamepadSnapshot;
				inputPass.physicalGamepad = gamepadSnapshot;
			}
			trackSceneGamepads(gamepadInputManager.physicalDevices(), monotonicClock.getAsLong());
            observeGameplayPads();
            if (rebound) discardOrderedGameplayInput();
            if (gamepadInputManager.hasPresentationPress() && !keyboardPresentationPending) {
				controllerPresentation = true;
			}
			keyboardPresentationPending = false;
		} else {
			gamepadSnapshot = inputPass.gamepad;
		}
		discardClaimedGameplayKeys();
        if (inputPass != null && !inputPass.hostControls && (!inputPass.observationOnly || inputPass.previewGameplay)) {
            if (inputPass.admission == null) inputPass.admission = orderedGameplay.sample();
            // Claims/publications stay live even after the host cached its pad sample.
            mappingKeyboard = true;
            try {
                keyboardP1 = logicalOverride == null ? keyboardInputMapper.mapPlayer1(this, inputBindings) : PlayerInputState.neutral();
                keyboardP2 = logicalOverride == null ? keyboardInputMapper.mapPlayer2(this, inputBindings) : PlayerInputState.neutral();
            } finally { mappingKeyboard = false; }
            gamepadSnapshot = LogicalInputSnapshot.ofPlayers(inputPass.admission.player(0), inputPass.admission.player(1));
        }
        // Presentation (prompt family) tracking above still runs; only the mapping goes neutral.
		if (gamepadSuppressed) gamepadSnapshot = LogicalInputSnapshot.neutral();
		menuGamepadSnapshot = gamepadSnapshot;
		physicalGamepadSnapshot = gamepadSuppressed ? LogicalInputSnapshot.neutral()
				: inputPass == null ? gamepadSnapshot : inputPass.physicalGamepad;
		notePlayerDevice(0, keyboardP1, gamepadSnapshot.player1());
		notePlayerDevice(1, keyboardP2, gamepadSnapshot.player2());
		if (logicalOverride != null) {
            discardOrderedGameplayInput();
            if (inputPass != null) {
				// Recorded input owns gameplay: a hidden live pad edge must not reappear
				// when that owner releases or an unacknowledged pass is rolled back.
				// Preserve the independent physical and host-shortcut channels.
				inputPass.gamepad = LogicalInputSnapshot.ofPlayers(
						withoutPresses(inputPass.gamepad.player1()), withoutPresses(inputPass.gamepad.player2()));
				inputPass.priorGamepad = inputPass.priorGamepad.withPlayerStatesFrom(
						gamepadInputManager.logicalHistory());
			}
			logicalSnapshot = logicalOverride;
			return;
		}
		PlayerInputState p1 = keyboardP1.merge(gamepadSnapshot.player1());
		PlayerInputState p2 = keyboardP2.merge(gamepadSnapshot.player2());
		logicalSnapshot = LogicalInputSnapshot.ofPlayers(p1, p2);
		if (retainedGameplayInput != null && (inputPass == null || !inputPass.hostControls)) {
			if (inputPass != null) inputPass.carry = retainedGameplayInput;
			else logicalSnapshot = retainedGameplayInput.apply(logicalSnapshot);
			retainedGameplayInput = null;
		}
		if (inputPass != null && inputPass.carry != null) {
			logicalSnapshot = inputPass.carry.apply(logicalSnapshot);
		}
	}

	private RetainedGameplayInput retainedGameplayInput;

	/** Internal host bridge: player edges for the next live refresh, never external replay authority. */
	void retainGameplayInputForNextRefresh(PlayerInputState player1, PlayerInputState player2) {
		retainedGameplayInput = new RetainedGameplayInput(player1, player2);
	}

	void discardRetainedGameplayInput() {
		retainedGameplayInput = null;
		if (inputPass != null) inputPass.carry = null;
	}

	LogicalInputSnapshot menuWithoutMappedKeyboard() {
		// An override already owns logical input; it contains no live keyboard mapping.
		return logicalOverride != null ? logicalOverride : menuGamepadSnapshot;
	}

	private boolean gamepadEdgesVisible() {
		return !gamepadSuppressed && (inputPass == null || !inputPass.acknowledged);
	}

	private boolean mappedDebugPressed() {
		return mappingKeyboard && inputPass != null ? inputPass.priorGamepad.debugPressed()
				: gamepadInputManager.isDebugModeTogglePressed();
	}

	private boolean mappedFrameStepPressed() {
		return mappingKeyboard && inputPass != null ? inputPass.priorGamepad.stepPressed()
				: gamepadInputManager.isFrameStepTogglePressed();
	}

	private boolean mappedRewindHeld() {
		return mappingKeyboard && inputPass != null ? inputPass.priorGamepad.rewind()
				: gamepadInputManager.isRewindHeld();
	}

	boolean menuDetailsPressed() { return gamepadEdgesVisible() && gamepadInputManager.isDebugModeTogglePressed(); }

	void setGamepadSuppressed(boolean suppressed) {
        gamepadSuppressed = suppressed;
        if (suppressed) orderedGameplay.discard(control -> control >= MAX_KEYS, admittedPhysicalSample());
    }

	ControllerPromptStyle menuControllerStyle() { return gamepadInputManager.presentationStyle(); }

	boolean usesControllerPresentation() {
		return controllerPresentation;
	}

	private void notePlayerDevice(int player, PlayerInputState keyboard, PlayerInputState pad) {
		// Keyboard edges win a same-frame tie, matching the session-wide presentation rule.
		if (intentional(keyboard)) playerControllerPresentation[player] = false;
		else if (intentional(pad)) playerControllerPresentation[player] = true;
	}

	private static boolean intentional(PlayerInputState state) {
		return state.pressedMask() != 0 || state.actionPressedMask() != 0 || state.startPressed();
	}

	/** Last intentional device of one logical player, or the session-wide choice before their first press. */
	boolean playerUsesControllerPresentation(int player) {
		Boolean known = playerControllerPresentation[player];
		return known != null ? known : controllerPresentation;
	}

	InputBindings currentBindings() { return inputBindings; }

	ControllerPromptStyle playerControllerStyle(int player) { return gamepadInputManager.playerStyle(player); }

	boolean playerPadIsPrimary(int player) { return gamepadInputManager.playerPadIsPrimary(player); }

	void noteKeyboardInput() {
		controllerPresentation = false;
		keyboardPresentationPending = true;
	}

	void appendMenuCodepoint(int codepoint) {
		if (textInput.acceptsCommit() && Character.isValidCodePoint(codepoint)
				&& (codepoint < 0xD800 || codepoint > 0xDFFF) && !Character.isISOControl(codepoint)
				&& menuTypedText.length() < 4096) {
			menuTypedText.appendCodePoint(codepoint);
			noteKeyboardInput();
		}
	}

	void discardMenuText() { menuTypedText.setLength(0); }
	void discardCompositionKeyEvents() {
        discardClaimedGameplayKeys();
        java.util.BitSet unpublishedPresses = new java.util.BitSet(MAX_KEYS);
        pendingSceneEvents.removeIf(event -> {
            if (event.kind() != PhysicalInputEvent.Kind.KEY) return false;
            if (event.pressed()) { unpublishedPresses.set(event.code()); return true; }
            // A release whose press was already published still belongs to the physical consumer.
            boolean paired = unpublishedPresses.get(event.code());
            unpublishedPresses.clear(event.code());
            return paired;
        });
	}

	String consumeMenuText() {
		String text = menuTypedText.toString();
		menuTypedText.setLength(0);
		return text;
	}

	LogicalInputSnapshot physicalMenuGamepad() { return physicalGamepadSnapshot; }

	public LogicalInputSnapshot logical() {
		return logicalSnapshot;
	}

	public boolean menuAcceptExcludingBackAction() {
		PlayerInputState p1 = logical().player1();
		return (p1.actionPressedMask() & (InputActionMasks.ACTION_A | InputActionMasks.ACTION_B)) != 0
				|| p1.startPressed();
	}

	/**
	 * Updates the input handler state. Should be called at the end of the game loop.
	 */
	public void update() {
        if (inputPass == null || inputPass.hostControls) discardOrderedGameplayInput();
        else if (!inputPass.acknowledged && !inputPass.observationOnly) {
            if (inputPass.admission == null) discardOrderedGameplayInput();
            else orderedGameplay.acknowledge(inputPass.admission);
        }
        if (inputPass != null) {
			if (inputPass.acknowledged) return;
			inputPass.acknowledged = true;
			inputPass.carry = null;
            if (inputPass.admission != null) {
                inputPass.admission.previous.clear();
                inputPass.admission.previous.or(inputPass.admission.held);
            }
            if (inputPass.gamepad != null) {
				inputPass.gamepad = LogicalInputSnapshot.ofPlayers(
						withoutPresses(inputPass.gamepad.player1()), withoutPresses(inputPass.gamepad.player2()));
				inputPass.physicalGamepad = LogicalInputSnapshot.ofPlayers(
						withoutPresses(inputPass.physicalGamepad.player1()), withoutPresses(inputPass.physicalGamepad.player2()));
			}
		}
		textInput.endFrame();
		menuFrame++;
		pendingSceneEvents.clear();
		droppedSceneEvents = 0;
		menuTypedText.setLength(0);
		System.arraycopy(keys, 0, previousKeys, 0, MAX_KEYS);
		System.arraycopy(mouseButtons, 0, previousMouseButtons, 0, MAX_MOUSE_BUTTONS);
	}

	private static PlayerInputState withoutPresses(PlayerInputState sample) {
		return PlayerInputState.of(sample.heldMask(), 0, sample.actionHeldMask(), 0, sample.startHeld(), false);
	}

}
