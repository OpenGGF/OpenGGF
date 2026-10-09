package flappytails;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSpriteSet;

/**
 * Sonic 3 &amp; Knuckles' title-card lettering, borrowed to spell anything: "FLAPPY TAILS",
 * "GET READY", "GAME OVER".
 *
 * <p>The ROM has no title-card alphabet. Each zone's card art holds only the letters of its own
 * name, placed by the card's mappings. But {@code SceneRomArt.titleCard} returns every zone's
 * name as a finished picture, and the zone names are known, so the letters can be cut back out:
 * split each name picture at the columns with no letter pixels, check the pieces match the
 * name's letters one for one, and keep each new letter. The fourteen names cover every letter
 * the game uses except J, Q, W, X and Z; words containing those fall back to the menu font.
 *
 * <p>The card draws its white letters over a dark band, part of each letter's own tiles. A cut
 * letter keeps only the light pixels, and {@link #draw} paints one continuous band behind a
 * word so the gaps between letters stay dark too.
 */
final class CardFont {
    /** Zone ids with title cards, and the names their cards spell (spaces between words). */
    private static final String NAMES = "0:ANGEL ISLAND|1:HYDROCITY|2:MARBLE GARDEN|3:CARNIVAL NIGHT|"
            + "4:FLYING BATTERY|5:ICECAP|6:LAUNCH BASE|7:MUSHROOM HILL|8:SANDOPOLIS|9:LAVA REEF|"
            + "10:SKY SANCTUARY|11:DEATH EGG|12:THE DOOMSDAY";
    /** Pixels with this much brightness or more are letter, darker ones band. */
    private static final int LETTER_LUMA = 300;

    private final SceneImage[] letters = new SceneImage[26];
    private int bandColor = 0xFF202020;
    private int bandTop = 8;
    private int height = 24;

    /** Cuts every letter it can from {@code rom}'s title cards; never null, possibly empty. */
    static CardFont load(SceneRomArt rom) {
        CardFont font = new CardFont();
        if (rom == null) return font;
        for (String entry : NAMES.split("\\|")) {
            int colon = entry.indexOf(':');
            int zone = Integer.parseInt(entry.substring(0, colon));
            if (!rom.hasTitleCard(zone, 0)) continue;
            SceneSpriteSet card = rom.titleCard(zone, 0);
            if (card == null || card.frameCount() < 2) continue;
            font.cut(card.frame(1).image(), entry.substring(colon + 1));
        }
        return font;
    }

    /** True when every letter of {@code text} is available (spaces always are). */
    boolean canSpell(String text) {
        for (char c : text.toCharArray()) {
            if (c == ' ') continue;
            if (c < 'A' || c > 'Z' || letters[c - 'A'] == null) return false;
        }
        return true;
    }

    int height() { return height; }

    /** The width {@code text} draws at, scale 1. */
    int width(String text) {
        int w = 0;
        for (char c : text.toCharArray()) {
            w += advance(c);
        }
        return Math.max(0, w - 2);
    }

    /**
     * Draws {@code text} with its top-left at ({@code x}, {@code y}) and its band behind it.
     * {@code reveal} letters are shown (the rest not yet), for typing-on effects; pass a large
     * number for all of them.
     */
    void draw(SceneCanvas canvas, String text, float x, float y, float scale, int reveal, SceneDraw style) {
        int w = width(text);
        int bandAlpha = Math.round(((style.tint() >>> 24) & 0xFF) * ((bandColor >>> 24) & 0xFF) / 255f);
        canvas.fill(Math.round(x - 4 * scale), Math.round(y + bandTop * scale),
                Math.round((w + 8) * scale), Math.round((height - bandTop) * scale),
                (bandAlpha << 24) | (bandColor & 0xFFFFFF));
        float pen = x;
        int shown = 0;
        for (char c : text.toCharArray()) {
            if (c != ' ' && shown++ < reveal && c >= 'A' && c <= 'Z' && letters[c - 'A'] != null) {
                canvas.draw(letters[c - 'A'], pen, y, style.withScale(scale));
            }
            pen += advance(c) * scale;
        }
    }

    private int advance(char c) {
        if (c == ' ') return 10;
        SceneImage letter = c >= 'A' && c <= 'Z' ? letters[c - 'A'] : null;
        return letter == null ? 12 : letter.width() + 2;
    }

    private void cut(SceneImage name, String spelled) {
        String chars = spelled.replace(" ", "");
        int w = name.width();
        int h = name.height();
        boolean[] lit = new boolean[w];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (letterPixel(name.pixel(x, y))) {
                    lit[x] = true;
                    break;
                }
            }
        }
        java.util.List<int[]> runs = new java.util.ArrayList<>();      // [start, end) columns with letter pixels
        for (int x = 0; x < w; ) {
            if (!lit[x]) {
                x++;
                continue;
            }
            int start = x;
            while (x < w && lit[x]) x++;
            runs.add(new int[] {start, x});
        }
        int[] cuts = assign(runs, chars, 0, 0);
        if (cuts == null) return;
        measureBand(name);
        height = h;
        for (int i = 0; i < chars.length(); i++) {
            int index = chars.charAt(i) - 'A';
            if (index < 0 || index >= 26 || letters[index] != null) continue;
            int lw = cuts[2 * i + 1] - cuts[2 * i];
            int[] pixels = new int[lw * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < lw; x++) {
                    int argb = name.pixel(cuts[2 * i] + x, y);
                    pixels[y * lw + x] = letterPixel(argb) ? argb : 0;
                }
            }
            letters[index] = new SceneImage(lw, h, pixels);
        }
    }

    /**
     * Shares the letters from {@code letter} on among the runs from {@code run} on. A run holds one
     * letter, or several drawn touching (the card's M and A in MARBLE): their known widths must
     * add up to the run's, and at most one letter in the run may still be unknown, its width
     * whatever is left. Returns each letter's start and end column, or null if no sharing fits.
     */
    private int[] assign(java.util.List<int[]> runs, String chars, int run, int letter) {
        if (run == runs.size()) return letter == chars.length() ? new int[2 * chars.length()] : null;
        int start = runs.get(run)[0];
        int width = runs.get(run)[1] - start;
        for (int take = 1; letter + take <= chars.length() && take <= 3; take++) {
            int known = 0;
            int unknown = -1;
            boolean fits = true;
            for (int i = letter; i < letter + take; i++) {
                SceneImage glyph = letters[chars.charAt(i) - 'A'];
                if (glyph != null) {
                    known += glyph.width();
                } else if (unknown < 0) {
                    unknown = i;
                } else {
                    fits = false;
                }
            }
            if (!fits) continue;
            int rest = width - known;
            boolean exact = unknown < 0 ? (take == 1 || Math.abs(rest) <= 1) : rest >= 4 && rest <= 20;
            if (take == 1 || exact) {
                int[] tail = assign(runs, chars, run + 1, letter + take);
                if (tail == null) continue;
                int pen = start;
                for (int i = letter; i < letter + take; i++) {
                    SceneImage glyph = letters[chars.charAt(i) - 'A'];
                    int lw = take == 1 ? width : i == unknown ? rest : glyph.width();
                    tail[2 * i] = pen;
                    tail[2 * i + 1] = pen + lw;
                    pen += lw;
                }
                return tail;
            }
        }
        return null;
    }

    /** The band: the darkest common colour and the first row it covers. */
    private void measureBand(SceneImage name) {
        for (int y = 0; y < name.height(); y++) {
            for (int x = 0; x < name.width(); x++) {
                int argb = name.pixel(x, y);
                if ((argb >>> 24) != 0 && !letterPixel(argb)) {
                    bandColor = argb;
                    bandTop = y;
                    return;
                }
            }
        }
    }

    private static boolean letterPixel(int argb) {
        if ((argb >>> 24) == 0) return false;
        int luma = ((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF);
        return luma >= LETTER_LUMA;
    }
}
