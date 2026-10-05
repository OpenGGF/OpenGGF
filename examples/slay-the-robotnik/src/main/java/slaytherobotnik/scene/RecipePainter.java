package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import java.util.List;
import slaytherobotnik.art.CardRecipes;
import slaytherobotnik.ui.Colors;

/**
 * Draws picture recipes ({@link CardRecipes}) for card art and relic badges: ROM sprites,
 * hero poses, item monitors, text-art icons and drawn effects, around a centre point.
 */
final class RecipePainter {
    private final Shell shell;

    RecipePainter(Shell shell) {
        this.shell = shell;
    }

    /**
     * Paints {@code layers} centred on {@code cx, cy}; offsets and scales are multiplied by
     * {@code zoom}. Whole-window effects fill {@code x, y, w, h}. Backdrop layers are skipped
     * (the caller draws the backdrop).
     */
    void paint(SceneCanvas c, List<CardRecipes.Layer> layers, float cx, float cy, float zoom, int x, int y, int w,
            int h) {
        for (CardRecipes.Layer layer : layers) {
            drawLayer(c, layer, cx, cy, zoom, x, y, w, h);
        }
    }

    private void drawLayer(SceneCanvas c, CardRecipes.Layer layer, float cx, float cy, float zoom, int x, int y,
            int w, int h) {
        float lx = cx + layer.x() * zoom;
        float ly = cy + layer.y() * zoom;
        SceneDraw style = SceneDraw.plain().withScale(layer.scale() * zoom).withFlipX(layer.flip())
                .withAlpha(layer.alpha());
        long t = shell.ticks;
        switch (layer.kind()) {
            case "hero" -> {
                var set = shell.art.character(layer.key());
                SceneSprite pose = layer.anim() ? Poses.frame(set, layer.frames()[0], t)
                        : set == null ? null : set.frame(layer.frames()[0]);
                Poses.centre(c, pose, lx, ly, style);
            }
            case "rom" -> {
                int frame = layer.frames()[(int) ((t / Math.max(1, layer.ticks())) % layer.frames().length)];
                Poses.centre(c, shell.art.romFrame(layer.key(), frame), lx, ly, style);
            }
            case "icon" -> {
                SceneImage icon = shell.art.icon(layer.key());
                float s = layer.scale() * zoom;
                c.draw(icon, lx - icon.width() * s / 2f, ly - icon.height() * s / 2f,
                        SceneDraw.plain().withScale(s).withAlpha(layer.alpha()));
            }
            case "monitor" -> {
                SceneSprite box = shell.art.romFrame("monitor", 0);
                if (box != null) {
                    float s = layer.scale() * zoom;
                    HudIcons.monitor(shell, c, layer.key(), lx - (box.width() / 2f - box.originX()) * s,
                            ly - (box.height() / 2f - box.originY()) * s, s, t);
                }
            }
            case "fx" -> effect(c, layer.key(), x, y, w, h, lx, ly, layer.alpha(), t);
            default -> {
            }
        }
    }

    /** Procedural effects, drawn over the whole window ({@code x,y,w,h}) or around the layer's point. */
    void effect(SceneCanvas c, String name, int x, int y, int w, int h, float px, float py, float alpha,
            long t) {
        switch (name) {
            case "speed" -> {
                for (int i = 0; i < 6; i++) {
                    int ly = y + 3 + (i * 7 + (int) (t / 3)) % Math.max(1, h - 6);
                    int len = w / 3 + (i * 13) % (w / 3);
                    int lx = x + w - (int) ((t * 3 + i * 29) % (w + len));
                    c.fill(lx, ly, len, 1, Colors.alpha(Colors.WHITE, 0.35f * alpha));
                }
            }
            case "burst" -> {
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6 + t * 0.01;
                    for (int r = 8; r < 40; r += 3) {
                        c.fill(Math.round(px + (float) Math.cos(a) * r), Math.round(py + (float) Math.sin(a) * r),
                                1, 1, Colors.alpha(0xFFFFDA24, (1f - r / 40f) * alpha));
                    }
                }
            }
            case "sparkle", "stars" -> {
                int color = name.equals("stars") ? 0xFFFFDA24 : Colors.WHITE;
                for (int i = 0; i < 7; i++) {
                    if ((t / 6 + i * 3) % 5 == 0) {
                        continue;
                    }
                    int sx = x + 3 + (i * 37) % Math.max(1, w - 6);
                    int sy = y + 3 + (i * 23) % Math.max(1, h - 6);
                    c.fill(sx - 1, sy, 3, 1, Colors.alpha(color, alpha));
                    c.fill(sx, sy - 1, 1, 3, Colors.alpha(color, alpha));
                }
            }
            case "rings" -> {
                for (int i = 0; i < 6; i++) {
                    int rx = x + 4 + (i * 29) % Math.max(1, w - 8);
                    int ry = y + (int) ((t * (1 + i % 3) + i * 17) % (h + 8)) - 8;
                    SceneSprite ring = shell.art.romFrame("ring", (int) ((t / 6 + i) % 4));
                    if (ring != null) {
                        c.draw(ring, rx, ry, SceneDraw.plain().withScale(0.75f).withAlpha(alpha));
                    }
                }
            }
            case "fire" -> {
                for (int i = 0; i < w; i += 3) {
                    int fh = 4 + (int) ((Math.sin(t * 0.25 + i * 0.7) + 1) * 4);
                    c.fill(x + i, y + h - fh, 3, fh, Colors.alpha(i % 2 == 0 ? 0xFFFF9024 : 0xFFFFDA24, 0.8f * alpha));
                }
            }
            case "water" -> {
                for (int i = 0; i < 5; i++) {
                    int wy = y + h - 4 - i * 4;
                    int off = (int) ((t + i * 11) % 16);
                    for (int k = -16; k < w; k += 16) {
                        c.fill(x + k + off, wy, 8, 1, Colors.alpha(0xFF6CB6FF, 0.6f * alpha));
                    }
                }
            }
            case "zap" -> {
                if ((t / 4) % 3 != 0) {
                    float zx = px;
                    float zy = y;
                    for (int i = 0; i < 6; i++) {
                        float nx = px + (float) Math.sin(i * 2.1 + t) * 6;
                        float ny = zy + h / 6f;
                        int steps = 4;
                        for (int k = 0; k < steps; k++) {
                            c.fill(Math.round(zx + (nx - zx) * k / steps), Math.round(zy + (ny - zy) * k / steps), 2, 2,
                                    Colors.alpha(0xFFFFFF6C, alpha));
                        }
                        zx = nx;
                        zy = ny;
                    }
                }
            }
            default -> {
            }
        }
    }

}
