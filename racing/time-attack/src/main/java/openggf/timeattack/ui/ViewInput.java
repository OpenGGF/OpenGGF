package openggf.timeattack.ui;

import com.openggf.control.ButtonPrompts;
import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.control.PhysicalInput;
import com.openggf.control.PhysicalGamepad;
import com.openggf.control.PhysicalInputEvent;

import java.util.Optional;

/**
 * One tick of menu input for the Time Attack views: repeated directions, confirm and back
 * (the scene convention: A, C, Start or Enter confirm; B or Escape go back), plus raw key state
 * and typed text for text entry. Views read this instead of the scene context so they can be driven in tests.
 */
public interface ViewInput {
    boolean up();

    boolean down();

    boolean left();

    boolean right();

    boolean accept();

    boolean back();

    /**
     * Start (any controller) or Enter went down this tick. Over the lobby level it toggles between
     * the lobby menu and the character once the player is ready.
     */
    default boolean start() {
        return false;
    }

    /** True on the tick the key went down ({@link SceneKeys} code). */
    boolean keyPressed(int key);

    /** True while the key is held ({@link SceneKeys} code). */
    boolean keyDown(int key);

    /** Text typed this tick in any script ({@link SceneContext#typedText()}); empty when none. */
    default String typedText() {
        return "";
    }

    default PhysicalInput physicalInput() { return PhysicalInput.neutral(); }

    /** Keyboard presses win a simultaneous activation; raw pad presence alone is not activation. */
    default boolean controllerAccept() {
        if (!accept() || keyPressed(SceneKeys.ENTER) || keyPressed(SceneKeys.KP_ENTER)) return false;
        PhysicalInput physical = physicalInput();
        if (physical.gamepads().isEmpty() || physical.events().stream()
                .anyMatch(event -> event.kind() == PhysicalInputEvent.Kind.KEY && event.pressed())) return false;
        int primary = physical.gamepads().getFirst().deviceId();
        return physical.events().stream().anyMatch(event -> event.deviceId() == primary && event.pressed()
                && event.kind() == PhysicalInputEvent.Kind.BUTTON
                && (event.code() == PhysicalGamepad.BUTTON_X || event.code() == PhysicalGamepad.BUTTON_B
                    || event.code() == PhysicalGamepad.BUTTON_START));
    }

    /** Read-only text access is separate from confirm, start and profile actions. */
    default boolean details() {
        return keyPressed(SceneKeys.F1) || physicalInput().events().stream().anyMatch(event ->
                event.kind()==PhysicalInputEvent.Kind.BUTTON && event.pressed()
                        && event.code()==PhysicalGamepad.BUTTON_RIGHT_THUMB);
    }

    /**
     * Player 1's label for a logical button on the keyboard ({@code controller} false) or on their
     * pad ({@link SceneContext#buttonLabel}); empty when unbound or without live input.
     */
    default Optional<String> buttonLabel(ButtonPrompts.Button button, boolean controller) {
        return Optional.empty();
    }

    /** True when the player bound {@code key} itself to {@code button}, so the binding decides what it does. */
    default boolean bound(ButtonPrompts.Button button, int key) {
        Optional<String> name = ButtonPrompts.keyName(key);
        return name.isPresent() && buttonLabel(button, false).equals(name);
    }

    /** Enter (or keypad Enter) is bound to B: it goes back and never also confirms. */
    default boolean enterGoesBack(int key) { return bound(ButtonPrompts.Button.B, key); }

    /** Escape is bound to a confirming button (A, C or Start): it confirms and never also goes back. */
    default boolean escapeConfirms() {
        return bound(ButtonPrompts.Button.A, SceneKeys.ESCAPE) || bound(ButtonPrompts.Button.C, SceneKeys.ESCAPE)
                || bound(ButtonPrompts.Button.START, SceneKeys.ESCAPE);
    }

    /**
     * This tick's input from a scene context. Enter confirms and Escape goes back as well as the
     * Genesis buttons, except where the player bound one of those keys to the opposite button:
     * then the binding decides, so one press never both confirms and goes back.
     */
    static ViewInput of(SceneContext ctx) {
        return new ViewInput() {
            @Override public boolean up() { return ctx.buttonRepeated(SceneButtons.UP); }
            @Override public boolean down() { return ctx.buttonRepeated(SceneButtons.DOWN); }
            @Override public boolean left() { return ctx.buttonRepeated(SceneButtons.LEFT); }
            @Override public boolean right() { return ctx.buttonRepeated(SceneButtons.RIGHT); }
            @Override public boolean accept() {
                return ctx.buttonPressed(SceneButtons.START | SceneButtons.A | SceneButtons.C) || enterPressed();
            }
            @Override public boolean back() {
                return ctx.buttonPressed(SceneButtons.B) || ctx.keyPressed(SceneKeys.ESCAPE) && !escapeConfirms();
            }
            @Override public boolean start() {
                return ctx.buttonPressed(SceneButtons.START) || enterPressed();
            }
            private boolean enterPressed() {
                return ctx.keyPressed(SceneKeys.ENTER) && !enterGoesBack(SceneKeys.ENTER)
                        || ctx.keyPressed(SceneKeys.KP_ENTER) && !enterGoesBack(SceneKeys.KP_ENTER);
            }
            @Override public boolean keyPressed(int key) { return ctx.keyPressed(key); }
            @Override public boolean keyDown(int key) { return ctx.keyDown(key); }
            @Override public String typedText() { return ctx.typedText(); }
            @Override public PhysicalInput physicalInput() { return ctx.physicalInput(); }
            @Override public Optional<String> buttonLabel(ButtonPrompts.Button button, boolean controller) {
                return ctx.buttonLabel(button, controller);
            }
        };
    }
}
