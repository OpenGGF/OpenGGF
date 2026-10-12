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
 * would leave the screen.
 */
public final class RecordingSceneCanvas implements SceneCanvas {
    public final List<String> lines = new ArrayList<>();
    private final int width;

    public RecordingSceneCanvas(int width) {
        this.width = width;
    }

    @Override public int width() { return width; }
    @Override public int height() { return 224; }
    @Override public void clear(int rgb) { lines.clear(); }
    @Override public void fill(int x, int y, int w, int h, int argb) { }
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

    /** Every recorded line joined, for containment checks. */
    public String joined() {
        return String.join("\n", lines);
    }
}
