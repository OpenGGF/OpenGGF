package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardColor;
import slaytherobotnik.core.CardType;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;

/**
 * Card illustrations, composed from ROM sprites over a coloured backdrop. Each card can name
 * a recipe ({@link #recipe}); cards without one get a pose of their character that matches
 * the card type (a spin for Attacks, a ready stance for Skills, a victory pose for Powers).
 */
final class CardArt {
    /** What to draw in a card's art window. {@code anim} is a character animation id (see {@link Poses}). */
    record Recipe(String character, int anim, int frameIndex, int background, boolean flip) {
    }

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

    Recipe recipe(Card card) {
        String who = characterFor(card);
        int bg = switch (card.type()) {
            case CardType.ATTACK -> 0xFF902424;
            case CardType.SKILL -> 0xFF244890;
            case CardType.POWER -> 0xFF906C00;
            case CardType.CURSE -> 0xFF482448;
            default -> 0xFF303030;
        };
        if (who == null) {
            return new Recipe(null, -1, 0, bg, false);
        }
        int anim = switch (card.type()) {
            case CardType.ATTACK -> Poses.ROLL;
            case CardType.POWER -> Poses.VICTORY;
            default -> Poses.LOOK_UP;
        };
        return new Recipe(who, anim, 0, bg, false);
    }

    void draw(SceneCanvas c, Card card, int x, int y, int w, int h) {
        Recipe r = recipe(card);
        Gfx.gradient(c, x, y, w, h, Colors.mix(r.background(), Colors.WHITE, 0.25f),
                Colors.mix(r.background(), Colors.BLACK, 0.45f));
        // Speed lines for attacks.
        if (card.type().equals(CardType.ATTACK)) {
            for (int i = 0; i < 5; i++) {
                int ly = y + 4 + (i * 9 + (int) (shell.ticks / 3)) % Math.max(1, h - 8);
                c.fill(x + 2, ly, w / 2, 1, 0x40FFFFFF);
            }
        }
        c.clip(x, y, w, h);
        if (r.character() != null) {
            SceneSprite pose = Poses.still(shell.art.character(r.character()), r.anim());
            Poses.centre(c, pose, x + w / 2f, y + h / 2f + 1, SceneDraw.plain().withFlipX(r.flip()));
        } else if (card.type().equals(CardType.CURSE)) {
            c.draw(shell.art.icon("intent_strong_debuff"), x + w / 2f - 6, y + h / 2f - 6);
        } else {
            c.draw(shell.art.icon("intent_unknown"), x + w / 2f - 6, y + h / 2f - 6);
        }
        c.unclip();
    }
}
