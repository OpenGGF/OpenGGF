package starpost.people;

import com.openggf.mods.scene.SceneImage;

/**
 * The picture-speech glyphs: small original shapes (no faces, by the art rule) drawn from
 * character grids in Mega Drive colours. Methods return new arrays: the mod validator forbids
 * static tables.
 */
final class Glyphs {
    private Glyphs() {
    }

    /** The grid for a glyph token, or null. */
    static String[] rows(String token) {
        return switch (token) {
            case "heart" -> new String[] {
                "..kkk...kkk..",
                ".krrrk.krrrk.",
                "krrwrrkrrrrrk",
                "krwrrrrrrrrrk",
                "krrrrrrrrrrrk",
                ".krrrrrrrrrk.",
                "..krrrrrrrk..",
                "...krrrrrk...",
                "....krrrk....",
                ".....krk.....",
                "......k......",
            };
            case "sad" -> new String[] {
                "....k....",
                "...kBk...",
                "...kBk...",
                "..kBBBk..",
                ".kBBwBBk.",
                ".kBwBBBk.",
                "kBBBBBBBk",
                "kBBBBBBnk",
                ".kBBBBnk.",
                "..kkkkk..",
            };
            case "sweat" -> new String[] {
                "...k.....",
                "..kBk....",
                ".kBwBk...",
                ".kBBBk.k.",
                "..kkk.kBk",
                "......kBk",
                ".......k.",
            };
            case "sun" -> new String[] {
                "......y......",
                "..y...y...y..",
                "...y.kkk.y...",
                "....kyyyk....",
                "...kywyyyk...",
                "yy.kyyyyyk.yy",
                "...kyyyyok...",
                "....kyyok....",
                "...y.kkk.y...",
                "..y...y...y..",
                "......y......",
            };
            case "rain" -> new String[] {
                "....kkkk.....",
                "..kkggwgkk...",
                ".kgggwwwggk..",
                "kgggggggggGk.",
                "kGGGGGGGGGGk.",
                ".kkkkkkkkkk..",
                "..B...B...B..",
                ".B...B...B...",
                "B...B...B....",
            };
            case "snow" -> new String[] {
                ".....B.....",
                "..B..B..B..",
                "...B.B.B...",
                "....BwB....",
                "BBBBwwwBBBB",
                "....BwB....",
                "...B.B.B...",
                "..B..B..B..",
                ".....B.....",
            };
            case "moon" -> new String[] {
                "...kkkk...",
                "..kyyyk...",
                ".kyyyk....",
                "kyyyk.....",
                "kyyyk.....",
                "kyyyk.....",
                "kyyyyk....",
                ".kyyyyk.kk",
                "..kyyyyyyk",
                "...kkkkkk.",
            };
            case "note" -> new String[] {
                "....kkkkkk",
                "....kbbbbk",
                "....kbkkbk",
                "....kbk.kk",
                "....kbk...",
                ".kkkkbk...",
                "kbbbbbk...",
                "kbbwbbk...",
                ".kkkkk....",
            };
            case "house" -> new String[] {
                "......kk......",
                ".....krrk.....",
                "....krrrrk....",
                "...krrRrrrk...",
                "..krrrrrrrRk..",
                ".kkkkkkkkkkkk.",
                "..kcccccccck..",
                "..kcBBkcmmck..",
                "..kcBBkcmmck..",
                "..kccccmmmck..",
                "..kkkkkkkkkk..",
            };
            case "gift" -> new String[] {
                "...kk.kk....",
                "..kyykyyk...",
                "...kkykk....",
                "kkkkkykkkkk.",
                "krrrryrrrrk.",
                "kkkkkykkkkk.",
                ".krrryrrrk..",
                ".krrryrrrk..",
                ".krrryrrrk..",
                ".kkkkkkkkk..",
            };
            case "clock" -> new String[] {
                "...kkkkk...",
                "..kwwwwwk..",
                ".kwwwkwwwk.",
                "kwwwwkwwwwk",
                "kwwwwkwwwwk",
                "kwwwwkkkwwk",
                "kwwwwwwwwwk",
                ".kwwwwwwwk.",
                "..kwwwwwk..",
                "...kkkkk...",
            };
            case "anger" -> new String[] {
                ".kk...kk.",
                "krrk.krrk",
                "krrkkkrrk",
                ".kkrrrkk.",
                "..krrrk..",
                ".kkrrrkk.",
                "krrkkkrrk",
                "krrk.krrk",
                ".kk...kk.",
            };
            case "fish" -> new String[] {
                ".......kk.....",
                "..kkkkkBBk..kk",
                ".kBBBBBBBBkkBk",
                "kBwkBBBBBBBBBk",
                ".kBBBBBBBBkkBk",
                "..kkkkkkkk..kk",
            };
            case "sparkle" -> new String[] {
                "....y....",
                "....y....",
                "...yyy...",
                "yyyywyyyy",
                "...yyy...",
                "....y....",
                "....y....",
            };
            case "no" -> new String[] {
                "kk.....kk",
                "krk...krk",
                ".krk.krk.",
                "..krkrk..",
                "...krk...",
                "..krkrk..",
                ".krk.krk.",
                "krk...krk",
                "kk.....kk",
            };
            case "yes" -> new String[] {
                "........kk",
                ".......kek",
                "......kek.",
                "kk...kek..",
                "kek.kek...",
                ".kekek....",
                "..kek.....",
                "...k......",
            };
            case "arrow" -> new String[] {
                "......k....",
                "......kk...",
                "kkkkkkkwk..",
                "kwwwwwwwwk.",
                "kwwwwwwwwwk",
                "kwwwwwwwwk.",
                "kkkkkkkwk..",
                "......kk...",
                "......k....",
            };
            case "?" -> new String[] {
                ".kkkkk.",
                "kbbbbbk",
                "kbkkkbk",
                "kkk.kbk",
                "...kbbk",
                "..kbbk.",
                "..kbk..",
                "..kkk..",
                "..kbk..",
                "..kkk..",
            };
            case "!" -> new String[] {
                "kkk",
                "krk",
                "krk",
                "krk",
                "krk",
                "krk",
                "kkk",
                "...",
                "kkk",
                "krk",
                "kkk",
            };
            case "..." -> new String[] {
                "kkk.kkk.kkk",
                "kbk.kbk.kbk",
                "kkk.kkk.kkk",
            };
            case "zzz" -> new String[] {
                "......kkkkk",
                "......kbbbk",
                ".......kbk.",
                "kkkkk.kbbbk",
                "kbbbk.kkkkk",
                "..kbk......",
                ".kbk.......",
                "kbbbk......",
                "kkkkk......",
            };
            default -> null;
        };
    }

    static SceneImage image(String[] rows) {
        int w = 0;
        for (String row : rows) {
            w = Math.max(w, row.length());
        }
        int h = rows.length;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                px[y * w + x] = colour(rows[y].charAt(x));
            }
        }
        return new SceneImage(w, h, px);
    }

    /** Mega Drive levels only (0, 24, 49, 6D, 92, B6, DB, FF). */
    static int colour(char ch) {
        return switch (ch) {
            case 'k' -> 0xFF000000;
            case 'w' -> 0xFFFFFFFF;
            case 'r' -> 0xFFFF2424;
            case 'R' -> 0xFFB60000;
            case 'y' -> 0xFFFFDB00;
            case 'o' -> 0xFFFF9200;
            case 'b' -> 0xFF2449FF;
            case 'B' -> 0xFF6DB6FF;
            case 'n' -> 0xFF0024B6;
            case 'g' -> 0xFFDBDBDB;
            case 'G' -> 0xFF929292;
            case 'c' -> 0xFFFFDBB6;
            case 'm' -> 0xFF924900;
            case 'e' -> 0xFF49DB00;
            default -> 0;
        };
    }
}
