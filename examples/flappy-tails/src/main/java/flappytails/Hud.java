package flappytails;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSpriteSet;
import com.openggf.mods.ui.CompactFont;

/**
 * The heads-up display and the small drawing kit every screen shares: outlined text, panels,
 * the score in the game's own HUD digits, and medals.
 *
 * <p>The score uses {@code ArtUnc_HUDDigits}, the uncompressed 8x16 digits the S3K HUD counts
 * rings and time with: two tiles per digit, stored top tile first, coloured with Sonic and
 * Tails' palette line as the HUD draws them. Drawn twice size, they are the Flappy score.
 */
final class Hud {
    /** {@code ArtUnc_HUDDigits} in the combined ROM, from the disassembly's listing. */
    private static final int HUD_DIGITS = 0x00E18A;
    static final int WHITE = 0xFFFFFF;
    static final int GOLD = 0xFFD84A;
    static final int ORANGE = 0xFF9A2E;
    static final int RED = 0xFF5040;
    static final int CYAN = 0x70E0FF;
    static final int GREY = 0xA8B0C8;
    static final int INK = 0x101030;

    /** The medal tiers: one per zone of the tour cleared. */
    static final int MEDAL_EVERY = Zone.GATES;

    private final SceneImage digits;
    final SceneSpriteSet ring;

    Hud(SceneRomArt rom, int[] palette, SceneSpriteSet ring) {
        SceneImage loaded = null;
        if (rom != null) {
            try {
                loaded = rom.tiles(HUD_DIGITS, RomSpriteRequest.Compression.UNCOMPRESSED, 0, 10, 2, true, palette);
            } catch (IllegalArgumentException unreadable) {
                loaded = null;
            }
        }
        this.digits = loaded;
        this.ring = ring;
    }

    // ---- text ----

    static int width(String text, int scale) {
        return CompactFont.width(text, scale);
    }

    /** Text with a one-pixel dark outline, fading with its colour's alpha. */
    static void outlined(SceneCanvas canvas, String text, int x, int y, int scale, int argb) {
        int alpha = argb >>> 24;
        if (alpha == 0) return;
        CompactFont.outlined(canvas, text, x, y, scale, argb, alpha << 24 | INK);
    }

    static void centred(SceneCanvas canvas, String text, int y, int scale, int rgb) {
        outlined(canvas, text, (canvas.width() - width(text, scale)) / 2, y, scale, 0xFF000000 | rgb);
    }

    /** A translucent navy panel with a light rim, the frame for menus and results. */
    static void panel(SceneCanvas canvas, int x, int y, int w, int h, float alpha) {
        int a = Math.round(alpha * 255);
        canvas.fill(x, y, w, h, Math.round(alpha * 200) << 24 | 0x0C1440);
        canvas.fill(x, y, w, 1, a << 24 | 0xF0F0FF);
        canvas.fill(x, y + h - 1, w, 1, a << 24 | 0x6070B0);
        canvas.fill(x, y, 1, h, a << 24 | 0xF0F0FF);
        canvas.fill(x + w - 1, y, 1, h, a << 24 | 0x6070B0);
    }

    // ---- the score ----

    /** The score centred on {@code centreX}, in HUD digits at {@code scale}; menu font if the ROM has none. */
    void score(SceneCanvas canvas, int value, float centreX, float y, float scale) {
        String text = Integer.toString(Math.max(0, value));
        if (digits == null) {
            int s = Math.max(1, Math.round(scale * 2));
            outlined(canvas, text, Math.round(centreX - width(text, s) / 2f), Math.round(y), s, 0xFFFFFFFF);
            return;
        }
        float w = text.length() * 8 * scale;
        float x = centreX - w / 2;
        for (int i = 0; i < text.length(); i++) {
            int d = text.charAt(i) - '0';
            canvas.drawRegion(digits, d * 8, 0, 8, 16, x + i * 8 * scale, y, 8 * scale, 16 * scale, SceneDraw.plain());
        }
    }

    /** A ring icon and a count, as the stock HUD shows rings. */
    void rings(SceneCanvas canvas, int count, int x, int y, boolean warn, long ticks) {
        if (ring != null) {
            canvas.draw(ring.frame((int) ((ticks / 8) % 4)), x + 8, y + 8, SceneDraw.plain());
        }
        int color = warn && (ticks / 8) % 2 == 0 ? RED : WHITE;
        outlined(canvas, Integer.toString(count), x + 20, y + 4, 2, 0xFF000000 | color);
    }

    // ---- medals ----

    /** The medal earned for {@code score}: 0 none, 1 bronze ... 5 super. */
    static int medal(int score) {
        return Math.min(5, score / MEDAL_EVERY);
    }

    static String medalName(int tier) {
        return switch (tier) {
            case 1 -> "BRONZE";
            case 2 -> "SILVER";
            case 3 -> "GOLD";
            case 4 -> "PLATINUM";
            case 5 -> "SUPER";
            default -> "NO MEDAL";
        };
    }

    static int medalColor(int tier, long ticks) {
        return switch (tier) {
            case 1 -> 0xE09050;
            case 2 -> 0xE0E8F8;
            case 3 -> 0xFFD040;
            case 4 -> 0xB8F8F0;
            case 5 -> rainbow(ticks);
            default -> 0x606880;
        };
    }

    /**
     * A medal: the ROM's ring, three times size, washed in the tier's colour, glinting.
     * {@code withFlash} mixes towards a colour and keeps the ring's shading; {@code withTint}
     * multiplies, and a yellow ring multiplied by silver is not silver.
     */
    void medal(SceneCanvas canvas, int tier, float x, float y, long ticks, float scale) {
        if (ring == null) {
            int r = Math.round(12 * scale);
            canvas.fill(Math.round(x - r), Math.round(y - r), 2 * r, 2 * r, 0xFF000000 | medalColor(tier, ticks));
            return;
        }
        int rgb = medalColor(tier, ticks);
        SceneDraw style = SceneDraw.plain().withScale(3 * scale).withFlash(0xA0000000 | rgb);
        if (tier == 0) style = style.withAlpha(0.45f);
        canvas.draw(ring.frame(0), x, y, style);
        if (tier > 0 && (ticks / 6) % 12 < 4) {
            canvas.draw(ring.frame(4 + (int) ((ticks / 6) % 4)), x + 10 * scale, y - 10 * scale, SceneDraw.plain().withScale(scale));
        }
    }

    static int rainbow(long ticks) {
        double h = (ticks % 90) / 90.0 * 6;
        int i = (int) h;
        double f = h - i;
        int up = (int) Math.round(255 * f);
        int down = 255 - up;
        return switch (i) {
            case 0 -> 0xFF0000 | up << 8;
            case 1 -> down << 16 | 0x00FF00;
            case 2 -> 0x00FF00 | up;
            case 3 -> down << 8 | 0x0000FF;
            case 4 -> up << 16 | 0x0000FF;
            default -> 0xFF0000 | down;
        };
    }
}
