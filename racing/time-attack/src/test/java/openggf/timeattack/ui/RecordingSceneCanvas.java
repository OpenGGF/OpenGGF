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

    /**
     * True when {@code text} is visible in any face: drawn with {@link #text}, or laid out as the
     * exact pixel runs of {@code NameFont} or {@code CompactFont} (capitals) in any colour at any
     * position. Secrets must be absent in every face, so masking checks use this too.
     */
    public boolean shows(String text) {
        if (joined().contains(text)) return true;
        return drawsRuns(text, (t, sink) -> com.openggf.mods.ui.NameFont.glyphs(t, 0, 0, sink)) != null
                || drawsRuns(text.toUpperCase(java.util.Locale.ROOT),
                        (t, sink) -> com.openggf.mods.ui.CompactFont.glyphs(t, 0, 0, 1, sink)) != null;
    }

    /** The line-box top of the first place {@code text} is drawn with {@code NameFont}, or {@code Integer.MIN_VALUE}. */
    public int nameTopAnywhere(String text) {
        int[] at = drawsRuns(text, (t, sink) -> com.openggf.mods.ui.NameFont.glyphs(t, 0, 0, sink));
        return at == null ? Integer.MIN_VALUE : at[1];
    }

    private interface Layout { void runs(String text, com.openggf.mods.ui.CompactFont.RunSink sink); }

    /** The {dx, dy} offset of the first exact drawing of {@code text}, or null. */
    private int[] drawsRuns(String text, Layout layout) {
        List<int[]> runs = new ArrayList<>();
        layout.runs(text, (x, y, w, h) -> runs.add(new int[] {x, y, w, h}));
        if (runs.isEmpty()) return null;
        java.util.Map<Integer, java.util.Set<String>> byColour = new java.util.HashMap<>();
        for (int[] fill : fills) {
            byColour.computeIfAbsent(fill[4], ignored -> new java.util.HashSet<>())
                    .add(fill[0] + "," + fill[1] + "," + fill[2] + "," + fill[3]);
        }
        int[] first = runs.getFirst();
        for (int[] fill : fills) {
            if (fill[2] != first[2] || fill[3] != first[3]) continue;
            int dx = fill[0] - first[0], dy = fill[1] - first[1];
            java.util.Set<String> drawn = byColour.get(fill[4]);
            boolean all = true;
            for (int[] run : runs) {
                if (!drawn.contains((run[0] + dx) + "," + (run[1] + dy) + "," + run[2] + "," + run[3])) {
                    all = false;
                    break;
                }
            }
            if (all) return new int[] {dx, dy};
        }
        return null;
    }

    /** Every recorded line joined, for containment checks. */
    public String joined() {
        return String.join("\n", lines);
    }
}
