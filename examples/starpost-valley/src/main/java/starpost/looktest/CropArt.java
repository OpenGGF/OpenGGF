package starpost.looktest;

import com.openggf.mods.scene.SceneImage;

/**
 * Original crop art: small pixel pictures in Mega Drive colours, one of the few kinds of creator
 * art the design allows (no faces except the pumpkin's moustache, which is Robotnik's). Each
 * picture is drawn with its bottom row on the soil.
 */
public final class CropArt {
    public static final int RADISH = 0;
    public static final int SUNFLOWER = 1;
    public static final int MELON = 2;
    public static final int PUMPKIN = 3;
    public static String name(int crop) {
        return switch (crop) {
            case RADISH -> "RING RADISH";
            case SUNFLOWER -> "GREEN HILL SUNFLOWER";
            case MELON -> "EMERALD MELON";
            default -> "EGGMAN PUMPKIN";
        };
    }
    /** Growth stages: 0 seed, 1 sprout, 2 young, 3 ripe. */
    public static final int STAGES = 4;

    private final SceneImage[][] art = new SceneImage[4][STAGES];

    public CropArt(Tone tone, SceneImage romSunflower) {
        SceneImage seed = picture(tone, seed());
        SceneImage sprout = picture(tone, sprout());
        SceneImage young = picture(tone, young());
        for (int crop = 0; crop < 4; crop++) {
            art[crop][0] = seed;
            art[crop][1] = sprout;
            art[crop][2] = young;
        }
        art[RADISH][3] = picture(tone, radishRipe());
        art[SUNFLOWER][2] = picture(tone, sunflowerBud());
        art[SUNFLOWER][3] = romSunflower != null ? tone.apply(romSunflower) : picture(tone, sunflowerRipe());
        art[MELON][3] = picture(tone, melonRipe());
        art[PUMPKIN][3] = picture(tone, pumpkinRipe());
    }

    public SceneImage stage(int crop, int stage) {
        return art[crop][Math.max(0, Math.min(STAGES - 1, stage))];
    }

    private static SceneImage picture(Tone tone, String[] rows) {
        int w = 0, h = rows.length;
        for (String row : rows) {
            w = Math.max(w, row.length());
        }
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                int c = colour(rows[y].charAt(x));
                px[y * w + x] = c == 0 ? 0 : tone.apply(c);
            }
        }
        return new SceneImage(w, h, px);
    }

    private static int colour(char c) {
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
            default -> 0;
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
