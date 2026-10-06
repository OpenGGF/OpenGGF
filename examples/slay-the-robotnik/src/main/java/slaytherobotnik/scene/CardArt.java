package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
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
    /** Card pictures are stills: every animated layer shows its first frame. */
    private static final long STILL = 0;

    /** Width of the large card's art window, which recipe offsets are measured in. */
    private static final int RECIPE_W = 76;

    private final Shell shell;
    private final RecipePainter painter;

    CardArt(Shell shell) {
        this.shell = shell;
        this.painter = new RecipePainter(shell);
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
            painter.paint(c, recipe, cx, cy, zoom, x, y, w, h, STILL);
        } else {
            fallback(c, card, x, y, w, h, cx, cy);
        }
        c.unclip();
    }

    /** No recipe: the card's character in a pose for its type. */
    private void fallback(SceneCanvas c, Card card, int x, int y, int w, int h, float cx, float cy) {
        String who = characterFor(card);
        if (card.type().equals(CardType.ATTACK)) {
            painter.effect(c, "speed", x, y, w, h, cx, cy, 1f, STILL);
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
