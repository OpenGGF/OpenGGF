package starpost.art;

import com.openggf.mods.scene.SceneImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Original crop art: small pixel pictures in Mega Drive colours, one of the few kinds of creator
 * art the design allows. Each crop has five stages (0 seed, 1 sprout, 2 young, 3 mature, 4 ripe)
 * drawn with the bottom row on the soil. Crops without a picture of their own use the generic
 * ripe plant in their fruit colours ({@link #fruit}).
 */
public final class CropArt {
    private final Tone tone;
    private final Map<String, SceneImage[]> art = new HashMap<>();
    private final SceneImage seed;
    private final SceneImage sprout;
    private final SceneImage young;
    private final SceneImage husk;

    public CropArt(Tone tone) {
        this.tone = tone;
        seed = picture(seed(), 0, 0);
        sprout = picture(sprout(), 0, 0);
        young = picture(young(), 0, 0);
        husk = picture(husk(), 0, 0);
        own("ring_radish", picture(radishRipe(), 0, 0));
        own("sunflower", picture(sunflowerRipe(), 0, 0));
        art.get("sunflower")[3] = picture(sunflowerBud(), 0, 0);
        own("emerald_melon", picture(melonRipe(), 0, 0));
        own("eggman_pumpkin", picture(pumpkinRipe(), 0, 0));
    }

    private void own(String crop, SceneImage ripe) {
        art.put(crop, new SceneImage[] {seed, sprout, young, picture(mature(), fruit(crop)[0], fruit(crop)[1]), ripe});
    }

    /** A crop's picture at a stage; dead crops are a dry husk. */
    public SceneImage stage(String crop, int stage, boolean dead) {
        if (dead) {
            return husk;
        }
        SceneImage[] stages = art.computeIfAbsent(crop, c -> new SceneImage[] {seed, sprout, young,
                picture(mature(), fruit(c)[0], fruit(c)[1]), picture(genericRipe(), fruit(c)[0], fruit(c)[1])});
        return stages[Math.max(0, Math.min(4, stage))];
    }

    /** The light and dark fruit colours of a crop without its own picture. */
    static int[] fruit(String crop) {
        return switch (crop) {
            case "palm_bean", "spring_yard_hops" -> new int[] {0xFF92FF00, 0xFF49B600};
            case "checker_cauliflower" -> new int[] {0xFFFFFFFF, 0xFFB6B6B6};
            case "spring_tulip", "motobug_tomato", "ruby_berry" -> new int[] {0xFFFF2400, 0xFFB60000};
            case "spin_spud" -> new int[] {0xFFDB9249, 0xFF924900};
            case "fire_pepper" -> new int[] {0xFFFF6D00, 0xFFDB2400};
            case "bluesphere_berry" -> new int[] {0xFF2492FF, 0xFF0024B6};
            case "starpost_corn", "sunflower", "ring_radish" -> new int[] {0xFFFFDB00, 0xFFDB9200};
            case "egg_plant", "marble_grape" -> new int[] {0xFFB66DFF, 0xFF6D24B6};
            case "totem_choke" -> new int[] {0xFF92DB6D, 0xFF6D24B6};
            case "scrap_amaranth" -> new int[] {0xFFDB2449, 0xFF6D0024};
            case "emerald_melon" -> new int[] {0xFF24DB92, 0xFF00926D};
            case "eggman_pumpkin" -> new int[] {0xFFFF9200, 0xFFB64900};
            default -> new int[] {0xFFFFDB00, 0xFFDB9200};
        };
    }

    private SceneImage picture(String[] rows, int fruitLight, int fruitDark) {
        int w = 0, h = rows.length;
        for (String row : rows) {
            w = Math.max(w, row.length());
        }
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                char ch = rows[y].charAt(x);
                int c = ch == 'F' ? fruitLight : ch == 'f' ? fruitDark : colour(ch);
                px[y * w + x] = c == 0 ? 0 : tone.apply(c);
            }
        }
        return new SceneImage(w, h, px);
    }

    static int colour(char c) {
        return switch (c) {
            case 'k' -> 0xFF240000;   // Green Hill's darkest brown, the outline colour
            case 'L' -> 0xFFB6FF92;
            case 'B' -> 0xFF492400;
            case 'b' -> 0xFF924900;
            case 'g' -> 0xFF006D00;
            case 'G' -> 0xFF49B600;
            case 'l' -> 0xFF92FF00;
            case 'y' -> 0xFFFFDB00;
            case 'Y' -> 0xFFFFFFB6;
            case 'o' -> 0xFFFF9200;
            case 'O' -> 0xFFB64900;
            case 'c' -> 0xFFFFB649;
            case 'm' -> 0xFF6D2400;
            case 'w' -> 0xFFFFFFFF;
            case 'e' -> 0xFF24DB92;
            case 'E' -> 0xFF00926D;
            case 'd' -> 0xFF926D49;   // dry husk
            case 'D' -> 0xFF6D4924;
            default -> 0;
        };
    }

    private static String[] mature() {
        return new String[] {
            "...l...ll...l...",
            "..lGl.lGGl.lGl..",
            ".lGGGlgGGglGGGl.",
            ".gGFGGGgGGGGFGg.",
            "..gGGGgGGgGGGg..",
            "...gGGGgGgGGg...",
            "....ggGGGGgg....",
            "......gGGg......",
            ".......Gg.......",
            ".......Gg.......",
            ".......gg.......",
        };
    }

    private static String[] genericRipe() {
        return new String[] {
            "......l..l......",
            ".....lGllGl.....",
            "..l..lGGGGl..l..",
            ".lGl.gGFfGg.lGl.",
            ".lGGlgFFFfglGGl.",
            "..gGFFFfFfFFGg..",
            "..kFFfFFfFFfFk..",
            "...kfFfFFfFfk...",
            "....kkfFfFkk....",
            "......kGgk......",
            ".......Gg.......",
            ".......Gg.......",
            ".......gg.......",
        };
    }

    private static String[] husk() {
        return new String[] {
            "....d....d......",
            "...dDd..dDd.....",
            "....dDddDd..d...",
            "......dDd..dD...",
            ".......D..dD....",
            ".......DddD.....",
            ".......D........",
            ".......D........",
        };
    }

    private static String[] seed() {
        return new String[] {
            "................",
            ".......w........",
            ".....bBbBb......",
            "....BbbbbbB.....",
        };
    }

    private static String[] sprout() {
        return new String[] {
            ".....l....l.....",
            "....lGl..lGl....",
            ".....gGllGg.....",
            ".......Gg.......",
            ".......Gg.......",
            ".......gg.......",
        };
    }

    private static String[] young() {
        return new String[] {
            "......l..l......",
            ".....lGllGl.....",
            "..l..lGGGGl..l..",
            ".lGl.gGGGGg.lGl.",
            ".lGGlgGGGGglGGl.",
            "..gGGGgGGgGGGg..",
            "...ggGGggGGgg...",
            ".....ggGGgg.....",
            ".......Gg.......",
            ".......Gg.......",
            ".......gg.......",
        };
    }

    private static String[] radishRipe() {
        return new String[] {
            ".......ll.......",
            "......lGGl......",
            "..ll..lGGl..ll..",
            ".lGGl.lGGl.lGGl.",
            ".lGGGlgGGglGGGl.",
            "..lGGGgGGgGGGl..",
            "...gGGGgGgGGg...",
            "....gGGgGGGg....",
            ".....ggGGgg.....",
            "......gGGg......",
            "......kggk......",
            "....kyYYYYyk....",
            "...kyYwwYYYyk...",
            "..kyYw....Yyyk..",
            "..kyY......yyk..",
            "..kyy......yyk..",
            "..kyy......yOk..",
            "..kyyY....yOOk..",
            "...kyyYYYyOOk...",
            "....kyyyyOOk....",
            ".....kkkkkk.....",
        };
    }

    private static String[] sunflowerBud() {
        return new String[] {
            "......gGGg......",
            ".....gGllGg.....",
            ".....gGllGg.....",
            "......gGGg......",
            ".......Gg.......",
            "..lGg..Gg.......",
            ".lGGGg.Gg..gGl..",
            "..gGGGgGg.gGGGl.",
            "....ggGGggGGGg..",
            ".......GgGgg....",
            ".......Gg.......",
            ".......Gg.......",
            "......gGGg......",
        };
    }

    private static String[] sunflowerRipe() {
        return new String[] {
            ".....y.yy.y.....",
            "...yyYyYYyYyy...",
            "..yYYyYYYYyYYy..",
            ".yYYyBBBBBByYYy.",
            ".yyyBbBbBbBByyy.",
            "yYYyBBbBbBbByYYy",
            "yYYyBbBbBbBByYYy",
            ".yyyBBbBbBbByyy.",
            ".yYYyBBBBBByYYy.",
            "..yYYyYYYYyYYy..",
            "...yyYyYYyYyy...",
            ".....y.Gy.y.....",
            ".......Gg.......",
            "..lGg..Gg.......",
            ".lGGGg.Gg..gGl..",
            "..gGGGgGg.gGGGl.",
            "....ggGGggGGGg..",
            ".......GgGgg....",
            ".......Gg.......",
            ".......Gg.......",
            "......gGGg......",
        };
    }

    private static String[] melonRipe() {
        return new String[] {
            "......lGl...........",
            ".....lGGGl..........",
            "......gGg...........",
            ".....kkkkkkkkkk.....",
            "...kkeLeeEeeEeekk...",
            "..keLLeeEeeEeeEeek..",
            ".keLeeEeeEeeEeeEeek.",
            ".keeeEeeEeeEeeEeEek.",
            ".kEeeEeeEeeEeeEeEEk.",
            ".kEEeEEeEEeEEeEEEEk.",
            "..kEEEEEEEEEEEEEEk..",
            "...kkkkkkkkkkkkkk...",
        };
    }

    private static String[] pumpkinRipe() {
        return new String[] {
            ".........gG.........",
            ".........Gg.........",
            ".....kkkkGgkkkk.....",
            "...kkoOoooooooOokk..",
            "..koOooYoooooYooOok.",
            ".koOooYkYoooYkYooOok",
            ".koOoooYoooooYoooOok",
            ".koOoooooooooooooOok",
            ".koOommmmooommmmoOok",
            ".koOmmmmmmmmmmmmmOok",
            ".koOoommooooommooOok",
            ".koOooooommmoooooOok",
            "..koOooooooooooooOk.",
            "...kkOOoooooooOOkk..",
            ".....kkkkkkkkkkk....",
        };
    }
}
