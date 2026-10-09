package starpost.people;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import starpost.core.Catalog;
import starpost.core.Item;

/**
 * Picture speech: how an animal says a line before the Chirp Translator. A line's authored
 * pictures win; otherwise they are read from its words, in the order they appear: items by name
 * (their icons), villagers by name (their faces), a handful of words (rings, rain, Robotnik,
 * badniks, love, sleep...), then the line's mood from its punctuation. At most
 * {@value #MAX} pictures.
 *
 * <p>Tokens: {@code item:ID}, {@code who:ID}, {@code farmer}, {@code robotnik}, and the glyphs
 * {@code heart}, {@code sad}, {@code ring}, {@code sun}, {@code rain}, {@code snow}, {@code moon},
 * {@code zzz}, {@code note}, {@code house}, {@code food}, {@code badnik}, {@code flicky},
 * {@code clock}, {@code gift}, {@code fish}, {@code sweat}, {@code anger}, {@code ?}, {@code !},
 * {@code ...}.
 */
public final class Pictures {
    public static final int MAX = 5;

    private Pictures() {
    }

    /** The pictures for a line: authored, or read from the words. */
    public static String[] of(Line line, Catalog catalog, Cast cast) {
        String[] authored = line.pictures();
        return authored != null ? authored : fromText(line.text(), catalog, cast);
    }

    public static String[] fromText(String text, Catalog catalog, Cast cast) {
        String upper = text.replace("{FARMER}", " YOU ").replace("{FARM}", " HOME ");
        TreeMap<Integer, String> found = new TreeMap<>();
        boolean[] used = new boolean[upper.length()];
        // Item names, longest first, so "RING RADISH SEEDS" is not read as a ring.
        List<Item> items = new ArrayList<>(catalog.items());
        items.sort((a, b) -> b.name().length() - a.name().length());
        for (Item item : items) {
            mark(upper, item.name(), "item:" + item.id(), found, used);
        }
        for (VillagerDef v : cast.all()) {
            mark(upper, v.name, "who:" + v.id, found, used);
        }
        int i = 0;
        while (i < upper.length()) {
            if (!Character.isLetter(upper.charAt(i)) || used[i]) {
                i++;
                continue;
            }
            int end = i;
            while (end < upper.length() && (Character.isLetter(upper.charAt(end)) || upper.charAt(end) == '\'')) {
                end++;
            }
            String token = glyphFor(upper.substring(i, end).replace("'S", ""));
            if (token != null && !found.containsValue(token)) {
                found.put(i, token);
            }
            i = end;
        }
        List<String> out = new ArrayList<>();
        for (String token : found.values()) {
            if (!out.contains(token) && out.size() < MAX - 1) {
                out.add(token);
            }
        }
        String mood = text.contains("?") ? "?" : text.contains("!") ? "!" : out.isEmpty() ? "..." : null;
        if (mood != null) {
            out.add(mood);
        }
        return out.toArray(new String[0]);
    }

    private static void mark(String text, String name, String token, TreeMap<Integer, String> found, boolean[] used) {
        int from = 0;
        while (true) {
            int at = text.indexOf(name, from);
            if (at < 0) {
                return;
            }
            boolean startOk = at == 0 || !Character.isLetter(text.charAt(at - 1));
            int end = at + name.length();
            boolean endOk = end >= text.length() || !Character.isLetter(text.charAt(end))
                    || text.startsWith("S", end) && (end + 1 >= text.length() || !Character.isLetter(text.charAt(end + 1)));
            if (startOk && endOk && !used[at]) {
                if (!found.containsValue(token)) {
                    found.put(at, token);
                }
                for (int k = at; k < Math.min(text.length(), end + 1); k++) {
                    used[k] = true;
                }
            }
            from = at + 1;
        }
    }

    /** The glyph a word stands for, or null. */
    static String glyphFor(String word) {
        return switch (word) {
            case "ROBOTNIK", "EGGMAN", "DOCTOR", "EGG" -> "robotnik";
            case "RING", "RINGS" -> "ring";
            case "RAIN", "RAINING", "RAINY", "STORM", "WET", "PUDDLE", "PUDDLES" -> "rain";
            case "SUN", "SUNNY", "SUNSHINE", "HOT", "SUMMER", "WARM" -> "sun";
            case "SNOW", "WINTER", "COLD", "ICE", "FROZEN", "FROST", "CHILLY" -> "snow";
            case "NIGHT", "MOON", "STARS", "EVENING", "DARK" -> "moon";
            case "SLEEP", "TIRED", "NAP", "BED", "YAWN", "ASLEEP" -> "zzz";
            case "LOVE", "LOVES", "THANK", "THANKS", "FRIEND", "FRIENDS", "HEART", "HAPPY", "GLAD", "KIND" -> "heart";
            case "SAD", "SORRY", "LONELY", "MISS", "CRY", "LOST", "GONE" -> "sad";
            case "SCARED", "AFRAID", "NERVOUS", "WORRIED", "WORRY", "HELP" -> "sweat";
            case "ANGRY", "MAD", "FURIOUS", "HMPH" -> "anger";
            case "BADNIK", "BADNIKS", "MOTOBUG", "MOTOBUGS", "ROBOT", "ROBOTS" -> "badnik";
            case "FLICKY", "FLICKIES", "BIRD", "BIRDS", "POST", "MAIL", "LETTER", "LETTERS" -> "flicky";
            case "SONG", "SONGS", "MUSIC", "SING", "SINGING", "DANCE", "TUNE" -> "note";
            case "HOME", "HOUSE", "STALL", "INN", "SHOP", "NEST", "HUT" -> "house";
            case "FOOD", "EAT", "EATING", "HUNGRY", "COOK", "COOKING", "DINNER", "LUNCH", "BREAKFAST", "SOUP",
                    "MEAL", "RECIPE", "KITCHEN" -> "food";
            case "TIME", "LATE", "EARLY", "CLOCK", "HOUR", "TOMORROW", "TODAY", "WAIT" -> "clock";
            case "GIFT", "GIFTS", "PRESENT", "BIRTHDAY", "PARTY" -> "gift";
            case "FISH", "FISHING", "CATCH" -> "fish";
            case "YOU", "YOUR" -> "farmer";
            default -> null;
        };
    }
}
