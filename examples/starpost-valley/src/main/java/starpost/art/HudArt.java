package starpost.art;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Sonic 1's own HUD: the yellow TIME and RINGS labels (Nem_Hud with Map_HUD; frame 3 has both in
 * red, as when time runs out or the rings are gone) and the big HUD digits (Art_Hud: 0-9 and the
 * colon, two tiles each).
 */
public final class HudArt {
    private static final int NEM_HUD = 0x39812;
    private static final int MAP_HUD = 0x1CD6A;
    private static final int ART_HUD = 0x1D2A6;

    public final SceneImage time;
    public final SceneImage rings;
    public final SceneImage timeRed;
    public final SceneImage ringsRed;
    private final SceneImage[] digits = new SceneImage[11];
    /** The last opaque row of the digit glyphs inside Art_Hud's 16-row tiles. */
    private final int digitBottom;

    HudArt(SceneRomArt s1, int[] palette) {
        SceneSpriteSet hud = s1.sprites(RomSpriteRequest.of(NEM_HUD, RomSpriteRequest.Compression.NEMESIS, MAP_HUD, 0),
                palette);
        List<SceneImage> yellow = bands(hud.frame(0).image());
        List<SceneImage> red = bands(hud.frame(3).image());
        time = yellow.get(1);
        rings = yellow.get(2);
        timeRed = red.get(1);
        ringsRed = red.get(2);
        for (int d = 0; d < digits.length; d++) {
            digits[d] = s1.tiles(ART_HUD, RomSpriteRequest.Compression.UNCOMPRESSED, d * 2, 1, 2, true, palette);
        }
        int bottom = 15;
        while (bottom > 0 && emptyRow(digits[0], bottom)) {
            bottom--;
        }
        digitBottom = bottom;
    }

    /**
     * One of Map_HUD's rows: the TIME label at x 16 with its counter on the same 16-pixel row. The
     * time counter starts $28 right of the label, so the minute digit is at 56 and the colon at 64;
     * an hour of two digits reaches back to 48, the label's own right edge.
     */
    public void timeRow(SceneCanvas canvas, boolean red, int hours, int minutes, float y) {
        SceneImage label = red ? timeRed : time;
        canvas.draw(label, 16, y, SceneDraw.plain());
        String h = Integer.toString(hours);
        number(canvas, h + ":" + (minutes < 10 ? "0" : "") + minutes, 64 - 8 * h.length(), digitY(label, y));
    }

    /**
     * The RINGS row: Map_HUD's ring counter is three digits from $30 past the label, right-aligned
     * as Hud_Rings writes it; a wallet of four or more digits grows rightwards from there.
     */
    public void ringsRow(SceneCanvas canvas, boolean red, int count, float y) {
        row(canvas, red ? ringsRed : rings, Integer.toString(count), 16, y, 0x30, 3);
    }

    /**
     * A label at ({@code x}, {@code y}) and its counter on the same row, {@code counterAt} pixels
     * further on: right-aligned within {@code digits} places as the ROM's counters are (0: left-aligned),
     * growing rightwards when the value is longer.
     */
    public void row(SceneCanvas canvas, SceneImage label, String value, float x, float y, int counterAt, int digits) {
        canvas.draw(label, x, y, SceneDraw.plain());
        float vx = x + counterAt + (value.length() < digits ? 8 * (digits - value.length()) : 0);
        number(canvas, value, vx, digitY(label, y));
    }

    /** The y for digits beside a label drawn at {@code labelY}: their glyphs end on the label's last row. */
    public float digitY(SceneImage label, float labelY) {
        return labelY + label.height() - 1 - digitBottom;
    }

    /** The label rows of a HUD frame (SCOR, TIME, RINGS), each cropped to its pixels. */
    private static List<SceneImage> bands(SceneImage frame) {
        List<SceneImage> out = new ArrayList<>();
        int y = 0;
        while (y < frame.height()) {
            while (y < frame.height() && emptyRow(frame, y)) {
                y++;
            }
            int top = y;
            while (y < frame.height() && !emptyRow(frame, y)) {
                y++;
            }
            if (y > top) {
                int left = frame.width(), right = -1;
                for (int yy = top; yy < y; yy++) {
                    for (int x = 0; x < frame.width(); x++) {
                        if (frame.pixel(x, yy) >>> 24 != 0) {
                            left = Math.min(left, x);
                            right = Math.max(right, x);
                        }
                    }
                }
                out.add(frame.crop(left, top, right - left + 1, y - top));
            }
        }
        while (out.size() < 3) {
            out.add(new SceneImage(1, 1, new int[1]));
        }
        return out;
    }

    private static boolean emptyRow(SceneImage image, int y) {
        for (int x = 0; x < image.width(); x++) {
            if (image.pixel(x, y) >>> 24 != 0) {
                return false;
            }
        }
        return true;
    }

    /** Draws digits and colons in the HUD font; returns the width drawn. */
    public int number(SceneCanvas canvas, String text, float x, float y) {
        return number(canvas, text, x, y, 1);
    }

    /** The same at an integer scale (title cards use 2). */
    public int number(SceneCanvas canvas, String text, float x, float y, int scale) {
        float cx = x;
        SceneDraw style = SceneDraw.plain().withScale(scale);
        for (char ch : text.toCharArray()) {
            int d = ch == ':' ? 10 : ch - '0';
            if (d < 0 || d > 10) {
                cx += 8 * scale;
                continue;
            }
            canvas.draw(digits[d], cx, y, style);
            cx += 8 * scale;
        }
        return Math.round(cx - x);
    }
}
