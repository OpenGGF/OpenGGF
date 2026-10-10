package starpost.art;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Sonic 3 &amp; Knuckles' title-card lettering as a font. Each zone's card holds only its own
 * letters, so the alphabet is gathered from the zone names whose letters separate cleanly by
 * their bright bodies (HYDROCITY, CARNIVAL NIGHT, FLYING BATTERY, ICECAP, SANDOPOLIS, SKY
 * SANCTUARY, DEATH EGG), M from MUSHROOM HILL and Z from "ZONE". W is two Vs overlapped; the ROM
 * has no J, Q or X, so those are skipped. Digits are not in the font (draw them with
 * {@link HudArt}).
 */
public final class CardFont {
    public static final int HEIGHT = 24;
    private static final int SPACE = 10;
    private final Map<Character, SceneImage> letters = new HashMap<>();

    CardFont(SceneRomArt s3k) {
        take(s3k, 1, "HYDROCITY");
        take(s3k, 3, "CARNIVALNIGHT");
        take(s3k, 4, "FLYINGBATTERY");
        take(s3k, 5, "ICECAP");
        take(s3k, 8, "SANDOPOLIS");
        take(s3k, 10, "SKYSANCTUARY");
        take(s3k, 11, "DEATHEGG");
        if (s3k.hasTitleCard(7, 0)) {
            SceneImage mushroom = s3k.titleCard(7, 0).frame(1).image();
            int[][] segments = segments(mushroom);
            if (segments.length > 0 && segments[0][1] >= 18) {
                letters.put('M', crop(mushroom, segments, 0));
            }
        }
        if (s3k.hasTitleCard(0, 0)) {
            SceneImage zone = s3k.titleCard(0, 0).frame(2).image();
            int[][] segments = segments(zone);
            if (segments.length == 4) {
                letters.putIfAbsent('Z', crop(zone, segments, 0));
            }
        }
    }

    /** Takes every letter of a zone's name when its letters can be told apart. */
    private void take(SceneRomArt s3k, int zone, String name) {
        if (!s3k.hasTitleCard(zone, 0)) {
            return;
        }
        SceneSpriteSet card = s3k.titleCard(zone, 0);
        SceneImage frame = card.frame(1).image();
        int[][] segments = segments(frame);
        if (segments.length != name.length()) {
            return;
        }
        for (int i = 0; i < name.length(); i++) {
            letters.putIfAbsent(name.charAt(i), crop(frame, segments, i));
        }
    }

    /** {start, width} of each run of columns holding the letters' bright bodies. */
    private static int[][] segments(SceneImage image) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        int x = 0;
        while (x < image.width()) {
            if (bright(image, x)) {
                int start = x;
                while (x < image.width() && bright(image, x)) {
                    x++;
                }
                out.add(new int[] {start, x - start});
            } else {
                x++;
            }
        }
        return out.toArray(new int[0][]);
    }

    private static boolean bright(SceneImage image, int x) {
        for (int y = 0; y < image.height(); y++) {
            int c = image.pixel(x, y);
            if (c >>> 24 != 0 && (c >>> 16 & 255) + (c >>> 8 & 255) + (c & 255) > 500) {
                return true;
            }
        }
        return false;
    }

    /** A letter with its drop shadow: its body plus up to two shadow columns on the right. */
    private static SceneImage crop(SceneImage image, int[][] segments, int i) {
        int start = segments[i][0];
        int limit = i + 1 < segments.length ? segments[i + 1][0] : image.width();
        int width = Math.min(limit - start, segments[i][1] + 2);
        return image.crop(start, 0, width, image.height());
    }

    public boolean has(char c) {
        return c == ' ' || c == 'W' && letters.containsKey('V') || letters.containsKey(c);
    }

    public int width(String text) {
        int w = 0;
        for (char c : text.toCharArray()) {
            w += advance(c);
        }
        return w;
    }

    private int advance(char c) {
        if (c == 'W') {
            SceneImage v = letters.get('V');
            return v == null ? SPACE : v.width() * 2 - 5;
        }
        SceneImage image = letters.get(c);
        return image == null ? SPACE : image.width();
    }

    /** Draws upper-case text; letters the ROM lacks leave a gap. Returns the width drawn. */
    public int draw(SceneCanvas canvas, String text, float x, float y, SceneDraw style) {
        float cx = x;
        for (char c : text.toCharArray()) {
            if (c == 'W' && letters.containsKey('V')) {
                SceneImage v = letters.get('V');
                canvas.draw(v, cx, y, style);
                canvas.draw(v, cx + v.width() - 5, y, style);
            } else if (letters.containsKey(c)) {
                canvas.draw(letters.get(c), cx, y, style);
            }
            cx += advance(c);
        }
        return Math.round(cx - x);
    }

    public void centred(SceneCanvas canvas, String text, int y, SceneDraw style) {
        draw(canvas, text, (canvas.width() - width(text)) / 2f, y, style);
    }
}
