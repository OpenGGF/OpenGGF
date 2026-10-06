package slaytherobotnik.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Card numbers and text as the player sees them.
 *
 * <p>Card text uses placeholders ({@code {D}} damage, {@code {B}} Block, {@code {M}} magic,
 * {@code {C}} Combo; {@code {M|card}} adds a pluralised noun, highlighted when written
 * {@code {M|*Ring Bomb*}}) and {@code *Keyword*} for highlighted terms. {@link #describe} turns
 * that into display markup understood by the card renderer:
 * {@code <g>12</g>} a number raised by modifiers (green), {@code <r>4</r>} a number lowered
 * (red), {@code <k>Vulnerable</k>} a keyword (gold). Keywords such as Exhaust are added on
 * their own line from the card's keyword set.
 */
public final class Cards {
    private Cards() {
    }

    /** Per-hit base damage before Strength/Weak/Vulnerable. */
    public static int baseDamage(Combat combat, Card card, Enemy target) {
        CardDef.DamageFormula formula = card.def().damageFormula();
        if (formula != null && combat != null) {
            return formula.baseDamage(combat, card, target);
        }
        return card.damage();
    }

    /** Damage per hit the card would deal now (target may be null). */
    public static int previewDamage(Combat combat, Card card, Enemy target) {
        int base = baseDamage(combat, card, target);
        if (combat == null) {
            return base;
        }
        return combat.previewCardDamage(card, target, base);
    }

    /** Block the card would give now. */
    public static int previewBlock(Combat combat, Card card) {
        if (combat == null) {
            return card.block();
        }
        return combat.calculateBlock(combat.player(), card.block(), card);
    }

    /**
     * Display text for a card. With a combat, numbers include modifiers (against {@code target}
     * when given); without one (deck view, rewards) the printed values are shown.
     */
    public static String describe(Card card, Combat combat, Enemy target) {
        String text = card.def().text(card.upgraded());
        StringBuilder out = new StringBuilder();
        StringBuilder prefix = new StringBuilder();
        StringBuilder suffix = new StringBuilder();
        for (String keyword : card.keywords()) {
            if (keyword.equals(Keyword.UNPLAYABLE) && card.type().equals(CardType.CURSE)) {
                prefix.append("<k>Unplayable</k>. ");
                continue;
            }
            if (Keyword.printedFirst(keyword)) {
                prefix.append("<k>").append(keyword).append("</k>. ");
            } else {
                suffix.append("\n<k>").append(keyword).append("</k>.");
            }
        }
        out.append(prefix);
        int i = 0;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (ch == '{' && i + 2 < text.length() && text.charAt(i + 2) == '}') {
                out.append(number(text.charAt(i + 1), card, combat, target));
                i += 3;
            } else if (ch == '{' && i + 2 < text.length() && text.charAt(i + 2) == '|' && text.indexOf('}', i) > 0) {
                // {M|card} -> "1 card" / "2 cards"
                int end = text.indexOf('}', i);
                String noun = text.substring(i + 3, end);
                String shown = number(text.charAt(i + 1), card, combat, target);
                boolean one = shown.replaceAll("</?[gr]>", "").equals("1");
                boolean term = noun.length() > 2 && noun.startsWith("*") && noun.endsWith("*");
                String nouns = (term ? noun.substring(1, noun.length() - 1) : noun) + (one ? "" : "s");
                out.append(shown).append(' ').append(term ? "<k>" + nouns + "</k>" : nouns);
                i = end + 1;
            } else if (ch == '*') {
                int end = text.indexOf('*', i + 1);
                if (end < 0) {
                    out.append(ch);
                    i++;
                } else {
                    out.append("<k>").append(text, i + 1, end).append("</k>");
                    i = end + 1;
                }
            } else {
                out.append(ch);
                i++;
            }
        }
        out.append(suffix);
        return out.toString().trim();
    }

    /** The card's highlighted terms, each once, in the order the text first shows them. */
    public static List<String> terms(Card card) {
        String text = describe(card, null, null);
        List<String> terms = new ArrayList<>();
        int at = text.indexOf("<k>");
        while (at >= 0) {
            int end = text.indexOf("</k>", at);
            if (end < 0) {
                break;
            }
            String term = text.substring(at + 3, end);
            if (!terms.contains(term)) {
                terms.add(term);
            }
            at = text.indexOf("<k>", end);
        }
        return terms;
    }

    /**
     * What a highlighted term means: its {@link Keyword#tooltip} entry, or, for a term naming a
     * card ("Ring Bombs", "Ring Bomb+", "Dent"), that card's cost, type and text, so the
     * explanation follows the card's numbers. Empty when the term is neither.
     */
    public static String explainTerm(Catalog catalog, String term) {
        String glossary = Keyword.tooltip(term);
        if (!glossary.isEmpty()) {
            return glossary;
        }
        Card card = namedCard(catalog, term);
        if (card == null) {
            return "";
        }
        int cost = card.def().cost(card.upgraded());
        boolean noCost = cost == CardDef.COST_NONE || card.type().equals(CardType.STATUS)
                || card.type().equals(CardType.CURSE);
        String costText = noCost ? "" : (cost == CardDef.COST_X ? "X" : Integer.toString(cost)) + "-cost ";
        return costText + card.type() + ". " + plainText(card).replace('\n', ' ');
    }

    /** The heading for a term's explanation: the keyword or card it means ("Ring Bombs" -> "Ring Bomb"). */
    public static String termName(Catalog catalog, String term) {
        if (!Keyword.tooltip(term).isEmpty()) {
            return Keyword.headword(term);
        }
        Card card = namedCard(catalog, term);
        return card == null ? term : card.name();
    }

    /** The card a term names, singular or plural, upgraded when it ends in "+"; null if none. */
    private static Card namedCard(Catalog catalog, String term) {
        boolean upgraded = term.endsWith("+");
        String name = upgraded ? term.substring(0, term.length() - 1) : term;
        for (CardDef def : catalog.allCards()) {
            if (def.name().equals(name) || (def.name() + "s").equals(name)) {
                return new Card(def, upgraded);
            }
        }
        return null;
    }

    /** The same text with markup removed (for logs and tests). */
    public static String plainText(Card card) {
        return describe(card, null, null).replaceAll("</?[grk]>", "");
    }

    private static String number(char kind, Card card, Combat combat, Enemy target) {
        int printed;
        int actual;
        switch (kind) {
            case 'D' -> {
                printed = card.def().damage(card.upgraded());
                actual = combat == null ? baseDamage(null, card, target) : previewDamage(combat, card, target);
                if (combat != null && card.def().damageFormula() != null) {
                    printed = baseDamage(combat, card, target);
                }
            }
            case 'B' -> {
                printed = card.def().block(card.upgraded());
                actual = previewBlock(combat, card);
            }
            case 'M' -> {
                printed = card.magic();
                actual = printed;
            }
            case 'C' -> {
                printed = card.combo();
                actual = combat == null ? printed : combat.comboCount(card);
            }
            default -> {
                return "{" + kind + "}";
            }
        }
        if (actual > printed) {
            return "<g>" + actual + "</g>";
        }
        if (actual < printed) {
            return "<r>" + actual + "</r>";
        }
        return Integer.toString(actual);
    }
}
