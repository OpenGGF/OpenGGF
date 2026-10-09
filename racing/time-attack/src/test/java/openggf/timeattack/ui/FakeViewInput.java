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

    @Override public boolean up() { return up; }
    @Override public boolean down() { return down; }
    @Override public boolean left() { return left; }
    @Override public boolean right() { return right; }
    @Override public boolean accept() { return accept; }
    @Override public boolean back() { return back; }
    @Override public boolean keyPressed(int key) { return pressed.contains(key); }
    @Override public boolean keyDown(int key) { return held.contains(key) || pressed.contains(key); }

    public void clear() {
        up = down = left = right = accept = back = false;
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

    /** Types {@code text} one key per tap on a US layout. */
    public void type(Consumer<ViewInput> view, String text) {
        for (char c : text.toCharArray()) {
            int[] key = keyFor(c);
            tapKey(view, key[0], key[1] != 0);
        }
    }

    /** The {@code SceneKeys} code and whether Shift is held for {@code c} on a US layout. */
    public static int[] keyFor(char c) {
        if (c >= 'a' && c <= 'z') return new int[] {SceneKeys.A + c - 'a', 0};
        if (c >= 'A' && c <= 'Z') return new int[] {SceneKeys.A + c - 'A', 1};
        if (c >= '0' && c <= '9') return new int[] {SceneKeys.DIGIT_0 + c - '0', 0};
        return switch (c) {
            case ' ' -> new int[] {SceneKeys.SPACE, 0};
            case '-' -> new int[] {SceneKeys.MINUS, 0};
            case '_' -> new int[] {SceneKeys.MINUS, 1};
            case '.' -> new int[] {SceneKeys.PERIOD, 0};
            case ',' -> new int[] {SceneKeys.COMMA, 0};
            case '\'' -> new int[] {SceneKeys.APOSTROPHE, 0};
            case ';' -> new int[] {SceneKeys.SEMICOLON, 0};
            case ':' -> new int[] {SceneKeys.SEMICOLON, 1};
            case '/' -> new int[] {SceneKeys.SLASH, 0};
            case '?' -> new int[] {SceneKeys.SLASH, 1};
            case '+' -> new int[] {SceneKeys.EQUAL, 1};
            case '!' -> new int[] {SceneKeys.DIGIT_1, 1};
            case '#' -> new int[] {SceneKeys.DIGIT_3, 1};
            case '[' -> new int[] {SceneKeys.LEFT_BRACKET, 0};
            case ']' -> new int[] {SceneKeys.RIGHT_BRACKET, 0};
            default -> throw new IllegalArgumentException("No key types '" + c + "'");
        };
    }
}
