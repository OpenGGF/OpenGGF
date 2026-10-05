package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import java.util.List;
import slaytherobotnik.art.CardRecipes;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardColor;
import slaytherobotnik.core.CardType;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;

/**
 * Card illustrations. Each card's art is a recipe in {@code art/cards.txt} (see
 * {@link CardRecipes}): ROM sprites of the heroes, badniks and items plus drawn effects,
 * layered over a backdrop. Cards without a recipe get a pose of their character that matches
 * the card type. Recipes are laid out for the large card's art window; small cards show the
 * middle of the same picture.
 */
final class CardArt {
    /** Width of the large card's art window, which recipe offsets are measured in. */
    private static final int RECIPE_W = 76;

    private final Shell shell;

    CardArt(Shell shell) {
        this.shell = shell;
    }

    static String characterFor(Card card) {
        return switch (card.color()) {
            case CardColor.ORANGE -> "tails";
            case CardColor.RED -> "knuckles";
            case CardColor.BLUE -> "sonic";
            default -> null;
        };
    }

    private static int background(Card card) {
        return switch (card.type()) {
            case CardType.ATTACK -> 0xFF902424;
            case CardType.SKILL -> 0xFF244890;
            case CardType.POWER -> 0xFF906C00;
            case CardType.CURSE -> 0xFF482448;
            default -> 0xFF303030;
        };
    }

    void draw(SceneCanvas c, Card card, int x, int y, int w, int h) {
        List<CardRecipes.Layer> recipe = shell.art.cards().recipe(card.id());
        int bg = background(card);
        if (recipe != null) {
            for (CardRecipes.Layer layer : recipe) {
                if (layer.kind().equals("bg")) {
                    bg = 0xFF000000 | Integer.parseInt(layer.key(), 16);
                }
            }
        }
        Gfx.gradient(c, x, y, w, h, Colors.mix(bg, Colors.WHITE, 0.25f), Colors.mix(bg, Colors.BLACK, 0.45f));
        c.clip(x, y, w, h);
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        if (recipe != null) {
            // Small cards show the middle of the large picture at a smaller scale.
            float zoom = w < RECIPE_W ? 0.5f : 1f;
            for (CardRecipes.Layer layer : recipe) {
                drawLayer(c, layer, cx, cy, zoom, x, y, w, h);
            }
        } else {
            fallback(c, card, x, y, w, h, cx, cy);
        }
        c.unclip();
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
            case "fx" -> effect(c, layer.key(), x, y, w, h, lx, ly, layer.alpha(), t);
            default -> {
            }
        }
    }

    /** Procedural effects, drawn over the whole window or around the layer's point. */
    private void effect(SceneCanvas c, String name, int x, int y, int w, int h, float px, float py, float alpha,
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

    /** No recipe: the card's character in a pose for its type. */
    private void fallback(SceneCanvas c, Card card, int x, int y, int w, int h, float cx, float cy) {
        String who = characterFor(card);
        if (card.type().equals(CardType.ATTACK)) {
            effect(c, "speed", x, y, w, h, cx, cy, 1f, shell.ticks);
        }
        if (who != null) {
            int anim = switch (card.type()) {
                case CardType.ATTACK -> Poses.ROLL;
                case CardType.POWER -> Poses.VICTORY;
                default -> Poses.LOOK_UP;
            };
            Poses.centre(c, Poses.still(shell.art.character(who), anim), cx, cy + 1, SceneDraw.plain());
        } else if (card.type().equals(CardType.CURSE)) {
            c.draw(shell.art.icon("intent_strong_debuff"), cx - 6, cy - 6);
        } else {
            c.draw(shell.art.icon("intent_unknown"), cx - 6, cy - 6);
        }
    }
}
