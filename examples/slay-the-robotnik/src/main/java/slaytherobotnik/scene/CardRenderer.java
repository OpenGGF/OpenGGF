package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import java.util.List;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardColor;
import slaytherobotnik.core.CardDef;
import slaytherobotnik.core.CardRarity;
import slaytherobotnik.core.CardType;
import slaytherobotnik.core.Cards;
import slaytherobotnik.core.Combat;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;
import slaytherobotnik.ui.SmallFont;

/**
 * Draws cards in two sizes: a {@link #SMALL_W}x{@link #SMALL_H} hand card (cost, art, type
 * stripe) and a {@link #BIG_W}x{@link #BIG_H} card with the full name and text, used for
 * previews, rewards, the shop and the deck.
 */
public final class CardRenderer {
    public static final int SMALL_W = 46;
    public static final int SMALL_H = 60;
    public static final int BIG_W = 88;
    public static final int BIG_H = 124;

    private final Shell shell;
    private final CardArt art;

    CardRenderer(Shell shell) {
        this.shell = shell;
        this.art = new CardArt(shell);
    }

    private static int frameColor(Card card) {
        return Colors.opaque(CardColor.rgb(card.color()));
    }

    private static int rarityTrim(Card card) {
        return switch (card.rarity()) {
            case CardRarity.UNCOMMON -> 0xFF6CB6FF;
            case CardRarity.RARE -> 0xFFFFDA24;
            case CardRarity.CURSE -> 0xFF9048B6;
            default -> 0xFFB6B6B6;
        };
    }

    /** Cost shown on the gem: the combat cost when a combat is given. */
    private static String costText(Card card, Combat combat) {
        int printed = card.def().cost(card.upgraded());
        if (printed == CardDef.COST_NONE) {
            return null;
        }
        if (printed == CardDef.COST_X) {
            return "X";
        }
        int cost = combat == null ? card.baseCost() : combat.effectiveCost(card);
        if (card.freeToPlayOnce()) {
            cost = 0;
        }
        return Integer.toString(cost);
    }

    private static int costColor(Card card, Combat combat) {
        if (combat == null) {
            return Colors.WHITE;
        }
        int printed = card.def().cost(card.upgraded());
        int now = combat.effectiveCost(card);
        if (card.freeToPlayOnce() || (printed >= 0 && now < printed)) {
            return Colors.TEXT_GOOD;
        }
        if (printed >= 0 && now > printed) {
            return Colors.TEXT_BAD;
        }
        return Colors.WHITE;
    }

    private void costGem(SceneCanvas c, Card card, Combat combat, int x, int y, int size, boolean playable) {
        String cost = costText(card, combat);
        if (cost == null) {
            return;
        }
        int fill = playable ? 0xFF24B66C : 0xFF485048;
        c.fill(x + 1, y, size - 2, size, Colors.BLACK);
        c.fill(x, y + 1, size, size - 2, Colors.BLACK);
        c.fill(x + 1, y + 1, size - 2, size - 2, fill);
        c.fill(x + 2, y + 1, size - 4, 1, Colors.alpha(Colors.WHITE, 0.5f));
        SmallFont f = shell.font;
        f.drawOutlined(c, cost, x + (size - f.width(cost)) / 2, y + (size - SmallFont.HEIGHT) / 2,
                costColor(card, combat), 1);
    }

    private void frame(SceneCanvas c, Card card, int x, int y, int w, int h, boolean dim) {
        int color = frameColor(card);
        int body = Colors.mix(color, Colors.BLACK, 0.55f);
        int edge = Colors.mix(color, Colors.WHITE, 0.25f);
        c.fill(x + 1, y, w - 2, h, Colors.BLACK);
        c.fill(x, y + 1, w, h - 2, Colors.BLACK);
        c.fill(x + 1, y + 1, w - 2, h - 2, color);
        c.fill(x + 3, y + 3, w - 6, h - 6, body);
        c.fill(x + 2, y + 1, w - 4, 1, edge);
        c.fill(x + 1, y + 2, 1, h - 4, edge);
        Gfx.outline(c, x + 2, y + 2, w - 4, h - 4, Colors.alpha(rarityTrim(card), 0.85f));
        if (dim) {
            c.fill(x, y, w, h, 0x70000000);
        }
    }

    /** A hand card. {@code playable} brightens the cost gem; {@code dim} greys it out. */
    public void drawSmall(SceneCanvas c, Card card, Combat combat, int x, int y, boolean playable, boolean dim) {
        frame(c, card, x, y, SMALL_W, SMALL_H, false);
        art.draw(c, card, x + 4, y + 9, SMALL_W - 8, 28);
        int stripe = switch (card.type()) {
            case CardType.ATTACK -> 0xFFDA4824;
            case CardType.SKILL -> 0xFF2490DA;
            case CardType.POWER -> 0xFFDAB624;
            default -> 0xFF6C6C6C;
        };
        c.fill(x + 4, y + 38, SMALL_W - 8, 2, stripe);
        SmallFont f = shell.font;
        List<String> nameLines = f.wrap(card.name().toUpperCase(), SMALL_W - 6);
        if (nameLines.size() > 2) {
            nameLines = List.of(abbreviate(card.name().toUpperCase(), SMALL_W - 6));
        }
        int ny = nameLines.size() == 1 ? y + 46 : y + 42;
        for (String line : nameLines) {
            f.drawShadowed(c, line, x + (SMALL_W - f.width(line)) / 2, ny,
                    card.upgraded() ? Colors.TEXT_GOOD : Colors.TEXT);
            ny += SmallFont.LINE;
        }
        costGem(c, card, combat, x - 2, y - 2, 11, playable);
        if (dim) {
            c.fill(x, y, SMALL_W, SMALL_H, 0x60000000);
        }
    }

    private String abbreviate(String name, int width) {
        SmallFont f = shell.font;
        if (f.width(name) <= width) {
            return name;
        }
        String[] words = name.replace("+", "").split(" ");
        String last = words[words.length - 1];
        if (f.width(last) <= width) {
            return last + (name.endsWith("+") ? "+" : "");
        }
        String s = name;
        while (s.length() > 1 && f.width(s) > width) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** A full-size card with text; numbers reflect {@code combat} and {@code target} when given. */
    public void drawBig(SceneCanvas c, Card card, Combat combat, Enemy target, int x, int y, boolean dim) {
        frame(c, card, x, y, BIG_W, BIG_H, false);
        SmallFont f = shell.font;
        // Name banner.
        int bannerColor = Colors.mix(frameColor(card), Colors.BLACK, 0.25f);
        c.fill(x + 4, y + 4, BIG_W - 8, 10, Colors.BLACK);
        c.fill(x + 5, y + 5, BIG_W - 10, 8, bannerColor);
        String name = card.name().toUpperCase();
        int nameWidth = f.width(name);
        int nameX = x + Math.max(14, (BIG_W - nameWidth) / 2);
        f.drawShadowed(c, name, Math.min(nameX, x + BIG_W - 5 - nameWidth), y + 7,
                card.upgraded() ? Colors.TEXT_GOOD : Colors.WHITE);
        // Art window.
        c.fill(x + 5, y + 16, BIG_W - 10, 44, Colors.BLACK);
        art.draw(c, card, x + 6, y + 17, BIG_W - 12, 42);
        // Type ribbon.
        String type = card.type().toUpperCase();
        int tw = f.width(type) + 8;
        c.fill(x + (BIG_W - tw) / 2, y + 59, tw, 8, Colors.BLACK);
        c.fill(x + (BIG_W - tw) / 2 + 1, y + 60, tw - 2, 6, Colors.mix(frameColor(card), Colors.BLACK, 0.4f));
        f.draw(c, type, x + (BIG_W - f.width(type)) / 2, y + 60, Colors.TEXT_DIM);
        // Description.
        // The font draws lowercase as capitals, so the markup tags can stay lowercase.
        String text = Cards.describe(card, combat, target);
        List<String> lines = f.wrap(text, BIG_W - 12);
        int textTop = y + 71;
        int textHeight = lines.size() * SmallFont.LINE;
        int ty = textTop + Math.max(0, (BIG_H - 75 - textHeight) / 2);
        f.drawParagraphCentered(c, lines, x + BIG_W / 2, ty, Colors.TEXT);
        costGem(c, card, combat, x - 3, y - 3, 14, combat == null || combat.canPlay(card));
        if (dim) {
            c.fill(x, y, BIG_W, BIG_H, 0x70000000);
        }
    }

    /** The art window alone (for previews in lists). */
    public void drawArt(SceneCanvas c, Card card, int x, int y, int w, int h) {
        art.draw(c, card, x, y, w, h);
    }
}
