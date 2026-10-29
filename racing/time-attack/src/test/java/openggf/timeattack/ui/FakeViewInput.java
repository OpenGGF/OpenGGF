package openggf.timeattack.ui;

import com.openggf.mods.scene.SceneKeys;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Scripted {@link ViewInput} for view tests: one tap is one tick with the input, then one idle tick. */
public final class FakeViewInput implements ViewInput {
    public boolean up;
    public boolean down;
    public boolean left;
    public boolean right;
    public boolean accept;
    public boolean back;
    public final Set<Integer> pressed = new HashSet<>();
    public final Set<Integer> held = new HashSet<>();
    public String typed = "";
    public com.openggf.control.PhysicalInput physical = com.openggf.control.PhysicalInput.neutral();

    @Override public boolean up() { return up; }
    @Override public boolean down() { return down; }
    @Override public boolean left() { return left; }
    @Override public boolean right() { return right; }
    @Override public boolean accept() { return accept; }
    @Override public boolean back() { return back; }
    @Override public boolean keyPressed(int key) { return pressed.contains(key); }
    @Override public boolean keyDown(int key) { return held.contains(key) || pressed.contains(key); }
    @Override public String typedText() { return typed; }
    @Override public com.openggf.control.PhysicalInput physicalInput() { return physical; }
    /** Host prompt labels by {@code BUTTON:key} or {@code BUTTON:pad}, as a live scene context reports them. */
    public final java.util.Map<String, String> labels = new java.util.HashMap<>();
    @Override public java.util.Optional<String> buttonLabel(com.openggf.control.ButtonPrompts.Button button,
                                                           boolean controller) {
        return java.util.Optional.ofNullable(labels.get(button.name() + (controller ? ":pad" : ":key")));
    }
    /** Labels an Xbox pad and the default keyboard produce (Genesis A is the west X, B the south A). */
    public FakeViewInput xboxAndDefaultKeyboard() {
        labels.put("A:pad", "X"); labels.put("B:pad", "A"); labels.put("C:pad", "B"); labels.put("START:pad", "Start");
        labels.put("A:key", "Space"); labels.put("START:key", "Backspace");
        return this;
    }
    /** One unbound key press (a physical key event and nothing else), so pages name the keyboard. */
    public void keyboardPress(java.util.function.Consumer<ViewInput> view) {
        clear();
        physical = new com.openggf.control.PhysicalInput(1, java.util.List.of(), java.util.List.of(), java.util.List.of(
                new com.openggf.control.PhysicalInputEvent(1, 1, com.openggf.control.PhysicalInputEvent.Kind.KEY,
                        com.openggf.control.PhysicalInputEvent.KEYBOARD_DEVICE, com.openggf.mods.scene.SceneKeys.LEFT_BRACKET, 1)), 0);
        view.accept(this);
        physical = com.openggf.control.PhysicalInput.neutral();
    }

    /** One controller button press, so pages name the pad. */
    public void padPress(java.util.function.Consumer<ViewInput> view) {
        clear();
        physical = new com.openggf.control.PhysicalInput(1, java.util.List.of(), java.util.List.of(), java.util.List.of(
                new com.openggf.control.PhysicalInputEvent(1, 1, com.openggf.control.PhysicalInputEvent.Kind.BUTTON, 0,
                        com.openggf.control.PhysicalGamepad.BUTTON_DPAD_DOWN, 1)), 0);
        view.accept(this);
        physical = com.openggf.control.PhysicalInput.neutral();
    }

    public void clear() {
        up = down = left = right = accept = back = false;
        typed = "";
        physical = com.openggf.control.PhysicalInput.neutral();
        pressed.clear();
        held.clear();
    }

    /** Runs one tick with nothing pressed. */
    public void idle(Consumer<ViewInput> view) {
        clear();
        view.accept(this);
    }

    public void tapUp(Consumer<ViewInput> view) { clear(); up = true; view.accept(this); idle(view); }
    public void tapDown(Consumer<ViewInput> view) { clear(); down = true; view.accept(this); idle(view); }
    public void tapLeft(Consumer<ViewInput> view) { clear(); left = true; view.accept(this); idle(view); }
    public void tapRight(Consumer<ViewInput> view) { clear(); right = true; view.accept(this); idle(view); }

    /** Enter: the menu accept and the Enter key, as a scene reports them. */
    public void tapEnter(Consumer<ViewInput> view) {
        clear();
        accept = true;
        pressed.add(SceneKeys.ENTER);
        view.accept(this);
        idle(view);
    }

    /** Escape: the menu back and the Escape key, as a scene reports them. */
    public void tapEscape(Consumer<ViewInput> view) {
        clear();
        back = true;
        pressed.add(SceneKeys.ESCAPE);
        view.accept(this);
        idle(view);
    }

    public void tapKey(Consumer<ViewInput> view, int key) {
        tapKey(view, key, false);
    }

    public void tapKey(Consumer<ViewInput> view, int key, boolean shift) {
        clear();
        pressed.add(key);
        if (shift) held.add(SceneKeys.LEFT_SHIFT);
        view.accept(this);
        idle(view);
    }

    /** Types {@code text} one code point per tick, as the platform delivers characters, each followed by an idle tick. */
    public void type(Consumer<ViewInput> view, String text) {
        text.codePoints().forEach(codePoint -> {
            clear();
            typed = new String(Character.toChars(codePoint));
            view.accept(this);
            idle(view);
        });
    }

    /** Delivers {@code text} in one tick, as an input method commits a whole word. */
    public void commit(Consumer<ViewInput> view, String text) {
        clear();
        typed = text;
        view.accept(this);
        idle(view);
    }
}
