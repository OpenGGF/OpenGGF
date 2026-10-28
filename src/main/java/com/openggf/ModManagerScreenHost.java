package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.MenuChrome;
import com.openggf.game.MenuStyle;
import java.util.List;
import com.openggf.game.MenuFeedback;
import com.openggf.mods.ui.ModManagerScreen;

import java.util.Objects;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/** Root composition adapter between engine input/rendering and the neutral mod UI. */
public final class ModManagerScreenHost implements MasterTitleScreen.ModManagerView {
    private final ModManagerScreen screen;
    private Pointer pointer = Pointer.NONE;

    /** One frame of the mouse in the manager's 320x224 page; x is negative without a mouse. */
    private record Pointer(int x, int y, boolean moved, boolean click, boolean backClick, int wheel) {
        static final Pointer NONE = new Pointer(-1, -1, false, false, false, 0);
    }

    public ModManagerScreenHost(ModManagerScreen screen) {
        this.screen = Objects.requireNonNull(screen, "screen");
    }

    @Override
    public void update(InputHandler input) {
        Pointer frame = pointer;
        pointer = Pointer.NONE;
        screen.update(menuInput(Objects.requireNonNull(input, "input"), frame),
                event -> MenuFeedback.emit(MenuFeedback.Cue.valueOf(event.name())));
    }

    @Override
    public void pointer(int x, int y, boolean moved, boolean click, boolean backClick, int wheel) {
        pointer = new Pointer(x, y, moved, click, backClick, wheel);
    }

    @Override public void render() { screen.render(); }
    @Override public void setGameFilter(String gameId) { screen.setGameFilter(gameId); }
    /** The neutral screen this host adapts; for same-package composition tests. */
    ModManagerScreen screen() { return screen; }
    @Override public boolean consumeCloseRequested() { return screen.consumeCloseRequested(); }
    @Override public void suppressInputUntilNeutral() { screen.suppressInputUntilNeutral(); }
    @Override public boolean consumeActivationRequested() { return screen.consumeActivationRequested(); }
    @Override public void activationFailed(String message) { screen.activationFailed(message); }

    static ModManagerScreen.MenuInput menuInput(InputHandler input) {
        return menuInput(input, Pointer.NONE);
    }

    private static ModManagerScreen.MenuInput menuInput(InputHandler input, Pointer pointer) {
        var logical = input.logical();
        boolean escape = input.isKeyPressed(GLFW_KEY_ESCAPE);
        // Mod UI retains this object as its previous frame; never close over mutable input.
        boolean up = MenuInput.up(input);
        boolean down = MenuInput.down(input);
        boolean left = MenuInput.left(input);
        boolean right = MenuInput.right(input);
        boolean accept = MenuInput.accept(input);
        boolean back = MenuInput.back(input);
        String confirmLabel = MenuInput.confirmLabel(input);
        String backLabel = MenuInput.backLabel(input);
        String directionLabel = MenuInput.directionLabel(input);
        boolean controllerInput = MenuInput.controller(input);

        return new ModManagerScreen.MenuInput() {
            @Override public boolean menuUp() { return up; }
            @Override public boolean menuDown() { return down; }
            @Override public boolean menuLeft() { return left; }
            @Override public boolean menuRight() { return right; }
            @Override public boolean menuAccept() { return accept; }
            @Override public boolean menuBack() { return back; }
            @Override public boolean startHeld() { return logical.player1().startHeld(); }
            @Override public boolean escape() { return escape; }
            @Override public String confirmLabel() { return confirmLabel; }
            @Override public String backLabel() { return backLabel; }
            @Override public String directionLabel() { return directionLabel; }
            @Override public boolean controller() { return controllerInput; }
            @Override public int pointerX() { return pointer.x(); }
            @Override public int pointerY() { return pointer.y(); }
            @Override public boolean pointerMoved() { return pointer.moved(); }
            @Override public boolean pointerClick() { return pointer.click(); }
            @Override public boolean pointerBack() { return pointer.backClick(); }
            @Override public int wheel() { return pointer.wheel(); }
        };
    }

    private static final int W = 320;

    private static float[] color(ModManagerScreen.Tone tone) {
        return switch (tone) {
            case GOOD -> MenuChrome.GREEN;
            case WARNING -> MenuChrome.AMBER;
            case ERROR -> MenuChrome.RED;
            case INFO -> MenuChrome.CYAN;
            case MUTED -> MenuChrome.MUTED;
        };
    }

    public static ModManagerScreen.TextSink textSink(PixelFont font) {
        Objects.requireNonNull(font, "font");
        int[] ticks = new int[1];
        return new ModManagerScreen.TextSink() {
            @Override public void begin() { font.beginMegaBatch(); }
            @Override public void draw(String text, int x, int y, float scale,
                                       float r, float g, float b, float a) {
                font.drawText(text, x, y, scale, r, g, b, a);
            }
            @Override public void end() { font.endMegaBatch(); }
            @Override public void page(String title, String subtitle) { MenuChrome.page(font, W, title, null, subtitle); }
            @Override public void panel(int x, int y, int width, int height) {
                MenuChrome.fill(font, x, y, width, height, MenuChrome.NAVY, .96f);
                MenuChrome.outline(font, x, y, width, height, MenuChrome.EDGE, 1f, 1);
            }
            @Override public void focus(int x, int y, int width, int height) {
                MenuChrome.focus(font, x + 2, y + 2, width - 4, height - 4, ++ticks[0]);
            }
            @Override public int primaryTextY(int rowTop, int rowHeight) { return MenuStyle.textY(rowTop, rowHeight, 1); }
            @Override public void footer(String first, String second) {
                MenuChrome.footer(font, W, null, List.of(MenuChrome.hint("?", first + " " + second)), false);
            }
            @Override public void footer(List<ModManagerScreen.Hint> hints, boolean controller) {
                MenuChrome.footer(font, W, null, hints.stream().map(h -> MenuChrome.hint(h.key(), h.label())).toList(),
                        controller);
            }
            @Override public void toggle(int x, int y, boolean on, boolean enabled) {
                MenuChrome.toggle(font, x, y, on, enabled);
            }
            @Override public int gameBadge(int x, int y, String gameId) { return MenuChrome.gameBadge(font, x, y, gameId); }
            @Override public int tag(int x, int y, String text, ModManagerScreen.Tone tone) {
                return MenuChrome.tag(font, x, y, text, color(tone));
            }
            @Override public int tagWidth(String text) { return MenuChrome.tagWidth(text); }
            @Override public int width(String text, float scale) { return font.measureWidth(text, scale); }
            @Override public void status(String text, int x, int y, ModManagerScreen.Tone tone) {
                MenuChrome.Icon icon = switch (tone) {
                    case GOOD -> MenuChrome.Icon.CHECK;
                    case WARNING, ERROR -> MenuChrome.Icon.WARNING;
                    case INFO, MUTED -> MenuChrome.Icon.INFO;
                };
                float[] c = color(tone);
                MenuChrome.icon(font, icon, x, y, c);
                MenuChrome.compact(font, MenuChrome.fitCompact(text, W - x - 20), x + 11, y, tone == ModManagerScreen.Tone.GOOD
                        ? MenuChrome.MUTED : c);
            }
            @Override public void button(int x, int y, int width, int height, String label, boolean focused, boolean primary) {
                MenuChrome.button(font, x, y, width, height, label, focused, primary, ticks[0]);
            }
            @Override public void lead(String value, int x, int y, boolean lock, ModManagerScreen.Tone tone) {
                MenuChrome.icon(font, lock ? MenuChrome.Icon.LOCK : MenuChrome.Icon.WARNING, x, y, color(tone));
                MenuChrome.compact(font, value, x + 11, y, color(tone));
            }
            @Override public void readingLine(String value, int x, int y, ModManagerScreen.ReadingStyle style,
                                               ModManagerScreen.Tone tone) {
                switch (style) {
                    case TITLE -> MenuChrome.compact(font, value, x, y, MenuChrome.GOLD);
                    case SECTION -> MenuChrome.section(font, value, x, y, 296);
                    case FIELD -> {
                        int split = value.indexOf(": ");
                        if (split < 0) MenuChrome.compact(font, value, x, y, MenuChrome.TEXT);
                        else {
                            String label = value.substring(0, split + 2);
                            MenuChrome.compact(font, label, x, y, MenuChrome.DIM);
                            MenuChrome.compact(font, value.substring(split + 2), x + MenuChrome.compactWidth(label), y, MenuChrome.TEXT);
                        }
                    }
                    case FINDING -> {
                        MenuChrome.icon(font, tone == ModManagerScreen.Tone.INFO ? MenuChrome.Icon.INFO
                                : MenuChrome.Icon.WARNING, x, y, color(tone));
                        MenuChrome.compact(font, value, x + 11, y, color(tone));
                    }
                    case CONTINUATION -> MenuChrome.compact(font, value, x + 11, y, MenuChrome.MUTED);
                    case BODY -> MenuChrome.compact(font, value, x, y, MenuChrome.MUTED);
                }
            }
            @Override public void scrollbar(int x, int top, int height, int first, int visible, int total) {
                MenuChrome.scrollbar(font, x, top, height, first, visible, total);
            }
        };
    }
}
