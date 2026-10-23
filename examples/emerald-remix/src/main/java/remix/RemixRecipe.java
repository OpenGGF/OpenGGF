package remix;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A parsed {@code .remix} recipe: the stock act it rebuilds, the ordered column strips that make
 * up the new layout, the stock object ids to leave out of each source act, and extra stock
 * placements authored at remixed coordinates. Acts are named {@code ehz1}/{@code ehz2}; see
 * {@code remix/ehz1.remix} for the format.
 */
record RemixRecipe(Act target, List<Strip> strips, Set<Drop> drops, List<Placement> placements) {
    /** A stock Emerald Hill act; {@code act} is zero-based. */
    record Act(String name, int zone, int act) {
        static Act parse(String name, int line) {
            String key = name.toLowerCase(Locale.ROOT);
            if (key.matches("ehz[12]")) return new Act(key, 0, key.charAt(3) - '1');
            throw new IllegalArgumentException("line " + line + ": unknown act '" + name + "' (use ehz1 or ehz2)");
        }
    }
    /** Inclusive range of 128px map columns copied from {@code source}. */
    record Strip(Act source, int firstColumn, int lastColumn) {
        int width() { return lastColumn - firstColumn + 1; }
    }
    record Drop(Act source, int objectId) { }
    /** A stock object at a position in the remixed layout; {@code flags} are the render flip bits. */
    record Placement(int x, int y, int objectId, int subtype, int flags) { }

    RemixRecipe {
        strips = List.copyOf(strips);
        drops = Set.copyOf(drops);
        placements = List.copyOf(placements);
    }

    int width() { return strips.stream().mapToInt(Strip::width).sum(); }

    /** Every act the recipe reads, the target last so its art is the one left published. */
    List<Act> sources() {
        List<Act> acts = new ArrayList<>();
        for (Strip strip : strips) if (!strip.source().equals(target) && !acts.contains(strip.source())) acts.add(strip.source());
        acts.add(target);
        return acts;
    }

    boolean drops(Act source, int objectId) { return drops.contains(new Drop(source, objectId)); }

    static RemixRecipe parse(String text) {
        Act target = null;
        List<Strip> strips = new ArrayList<>();
        Set<Drop> drops = new HashSet<>();
        List<Placement> placements = new ArrayList<>();
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            int line = i + 1;
            String body = lines[i].replaceFirst("#.*", "").trim();
            if (body.isEmpty()) continue;
            String[] words = body.split("\\s+");
            switch (words[0]) {
                case "target" -> {
                    expect(words, 2, line);
                    if (target != null) throw new IllegalArgumentException("line " + line + ": target is already set");
                    target = Act.parse(words[1], line);
                }
                case "strip" -> {
                    expect(words, 4, line);
                    int first = number(words[2], line), last = number(words[3], line);
                    if (first < 0 || last < first || last > 127)
                        throw new IllegalArgumentException("line " + line + ": columns must satisfy 0 <= first <= last <= 127");
                    strips.add(new Strip(Act.parse(words[1], line), first, last));
                }
                case "drop" -> {
                    expect(words, 3, line);
                    drops.add(new Drop(Act.parse(words[1], line), number(words[2], line)));
                }
                case "object" -> {
                    if (words.length != 5 && words.length != 6)
                        throw new IllegalArgumentException("line " + line + ": 'object' takes x, y, id, subtype and optional flags");
                    var placement = new Placement(number(words[1], line), number(words[2], line), number(words[3], line),
                            number(words[4], line), words.length == 6 ? number(words[5], line) : 0);
                    if (placement.x() < 0 || placement.x() > 0x7FFF || placement.y() < 0 || placement.y() > 0x0FFF
                            || placement.objectId() < 0 || placement.objectId() > 0xFF || placement.subtype() < 0
                            || placement.subtype() > 0xFF || placement.flags() < 0 || placement.flags() > 3)
                        throw new IllegalArgumentException("line " + line + ": object values are out of range");
                    placements.add(placement);
                }
                default -> throw new IllegalArgumentException("line " + line + ": unknown instruction '" + words[0] + "'");
            }
        }
        if (target == null) throw new IllegalArgumentException("the recipe needs a target act");
        if (strips.isEmpty()) throw new IllegalArgumentException("the recipe needs at least one strip");
        var recipe = new RemixRecipe(target, strips, drops, placements);
        if (recipe.width() > 128) throw new IllegalArgumentException("the strips total " + recipe.width() + " columns; the map holds 128");
        return recipe;
    }

    private static void expect(String[] words, int count, int line) {
        if (words.length != count) throw new IllegalArgumentException("line " + line + ": '" + words[0] + "' takes " + (count - 1) + " values");
    }

    private static int number(String word, int line) {
        try {
            return Integer.decode(word);
        } catch (NumberFormatException bad) {
            throw new IllegalArgumentException("line " + line + ": '" + word + "' is not a number");
        }
    }
}
