package openggf.timeattack.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Records the text a view draws, with the menu font's 10-pixel advance, and fails when text
 * would leave the screen. Fills are recorded too, so {@code NameFont} text (drawn as pixel runs)
 * can be found by its colour.
 */
public final class RecordingSceneCanvas implements SceneCanvas {
    public final List<String> lines = new ArrayList<>();
    /** Every fill since the last clear: {@code {x, y, w, h, argb}}. */
    public final List<int[]> fills = new ArrayList<>();
    private final int width;

    public RecordingSceneCanvas(int width) {
        this.width = width;
    }

    @Override public int width() { return width; }
    @Override public int height() { return 224; }
    @Override public void clear(int rgb) { lines.clear(); fills.clear(); }
    @Override public void fill(int x, int y, int w, int h, int argb) { fills.add(new int[] {x, y, w, h, argb}); }
    @Override public void draw(SceneImage image, float x, float y) { }
    @Override public void draw(SceneImage image, float x, float y, SceneDraw style) { }
    @Override public void draw(SceneSprite sprite, float x, float y, SceneDraw style) { }
    @Override public void drawRegion(SceneImage image, int sx, int sy, int sw, int sh, float dx, float dy,
                                     float dw, float dh, SceneDraw style) { }

    @Override
    public void text(String text, int x, int y, int argb) {
        assertTrue(x >= 0 && x + textWidth(text) <= width, "text leaves the screen horizontally: " + text);
        assertTrue(y >= 0 && y + 10 <= 224, "text leaves the screen vertically: " + text);
        lines.add(text);
    }

    @Override public int textWidth(String text) { return text.length() * 10; }
    @Override public void clip(int x, int y, int w, int h) { }
    @Override public void unclip() { }

    /** Fills drawn in {@code argb}, such as the pixel runs of a name drawn in a player's colour. */
    public List<int[]> fillsIn(int argb) {
        return fills.stream().filter(fill -> fill[4] == argb).toList();
    }

    /**
     * True when {@code text} was drawn with {@code NameFont} in {@code argb} with its line box's
     * left edge at {@code x} (any row): every pixel run of the text is among the recorded fills.
     * A prefix of a longer drawn name matches too, since glyphs are laid out left to right.
     */
    public boolean drewName(String text, int x, int argb) {
        return nameTop(text, x, argb) != Integer.MIN_VALUE;
    }

    /** The line-box top at which {@link #drewName} finds {@code text}, or {@code Integer.MIN_VALUE}. */
    public int nameTop(String text, int x, int argb) {
        java.util.Set<String> drawn = new java.util.HashSet<>();
        for (int[] fill : fills) {
            if (fill[4] == argb) drawn.add(fill[0] + "," + fill[1] + "," + fill[2] + "," + fill[3]);
        }
        if (drawn.isEmpty()) return Integer.MIN_VALUE;
        for (int y = -16; y < 224; y++) {
            List<String> runs = new ArrayList<>();
            com.openggf.mods.ui.NameFont.glyphs(text, x, y, (rx, ry, w, h) -> runs.add(rx + "," + ry + "," + w + "," + h));
            if (!runs.isEmpty() && drawn.containsAll(runs)) return y;
        }
        return Integer.MIN_VALUE;
    }

    /** Fails when any fill leaves the screen. */
    public void assertFillsOnScreen() {
        for (int[] fill : fills) {
            assertTrue(fill[0] >= 0 && fill[1] >= 0 && fill[0] + fill[2] <= width && fill[1] + fill[3] <= 224,
                    () -> "fill leaves the screen: " + java.util.Arrays.toString(fill));
        }
    }

    /** Every recorded line joined, for containment checks. */
    public String joined() {
        return String.join("\n", lines);
    }
}
