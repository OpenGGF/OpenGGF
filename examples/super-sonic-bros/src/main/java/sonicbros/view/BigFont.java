package sonicbros.view;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Large display lettering ("GAME!", "3", "GO!", the title): a blocky 5x7 alphabet scaled up,
 * filled with a vertical gradient, outlined and drop-shadowed. Each string is rendered once and
 * cached.
 */
public final class BigFont {
    private final Map<String, SceneImage> cache = new HashMap<>();
    private final Map<String, SceneImage> small = new HashMap<>();

    /** Advance of the small font, in pixels per character. */
    public static final int SMALL_ADVANCE = 6;

    /** Width of {@code text} in the small font. */
    public int width(String text) { return text.isEmpty() ? 0 : text.length() * SMALL_ADVANCE + 1; }

    /**
     * Draws {@code text} in the small outlined 5x7 font (white letters, dark outline) tinted
     * {@code argb}; the menus and HUD use it where the engine's font is too wide.
     */
    public void draw(SceneCanvas c, String text, int x, int y, int argb) {
        if (text.isEmpty()) return;
        SceneImage img = small.computeIfAbsent(text, this::renderSmall);
        c.draw(img, x - 1, y - 1, SceneDraw.plain().withTint(argb));
    }

    /** Draws small text centred on {@code cx}. */
    public void drawCentered(SceneCanvas c, String text, int cx, int y, int argb) {
        draw(c, text, cx - width(text) / 2, y, argb);
    }

    private SceneImage renderSmall(String text) {
        int w = text.length() * SMALL_ADVANCE + 2;
        int h = 9;
        boolean[] mask = new boolean[w * h];
        for (int i = 0; i < text.length(); i++) {
            String g = glyph(Character.toUpperCase(text.charAt(i)));
            for (int gy = 0; gy < 7; gy++) {
                for (int gx = 0; gx < 5; gx++) {
                    if (g.charAt(gy * 5 + gx) == '#') mask[(gy + 1) * w + 1 + i * SMALL_ADVANCE + gx] = true;
                }
            }
        }
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (mask[y * w + x]) { px[y * w + x] = 0xFFFFFFFF; continue; }
                boolean near = false;
                for (int dy = -1; dy <= 1 && !near; dy++) {
                    for (int dx = -1; dx <= 1 && !near; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx >= 0 && ny >= 0 && nx < w && ny < h && mask[ny * w + nx]) near = true;
                    }
                }
                if (near) px[y * w + x] = 0xFF000000;
            }
        }
        return new SceneImage(w, h, px);
    }

    /** 7 rows of 5 columns per glyph, '#' set. */
    static String glyph(char c) {
        return switch (c) {
            case 'A' -> ".###.#...##...#######...##...##...#";
            case 'B' -> "####.#...##...#####.#...##...#####.";
            case 'C' -> ".###.#...##....#....#....#...#.###.";
            case 'D' -> "####.#...##...##...##...##...#####.";
            case 'E' -> "######....#....####.#....#....#####";
            case 'F' -> "######....#....####.#....#....#....";
            case 'G' -> ".###.#...##....#.####...##...#.####";
            case 'H' -> "#...##...##...#######...##...##...#";
            case 'I' -> ".###...#....#....#....#....#...###.";
            case 'J' -> "..###...#....#....#....#.#..#..##..";
            case 'K' -> "#...##..#.#.#..##...#.#..#..#.#...#";
            case 'L' -> "#....#....#....#....#....#....#####";
            case 'M' -> "#...###.###.#.##.#.##...##...##...#";
            case 'N' -> "#...###..##.#.##..###...##...##...#";
            case 'O' -> ".###.#...##...##...##...##...#.###.";
            case 'P' -> "####.#...##...#####.#....#....#....";
            case 'Q' -> ".###.#...##...##...##.#.##..#..##.#";
            case 'R' -> "####.#...##...#####.#.#..#..#.#...#";
            case 'S' -> ".#####....#.....###.....#....#####.";
            case 'T' -> "#####..#....#....#....#....#....#..";
            case 'U' -> "#...##...##...##...##...##...#.###.";
            case 'V' -> "#...##...##...##...##...#.#.#...#..";
            case 'W' -> "#...##...##...##.#.##.#.##.#.#.#.#.";
            case 'X' -> "#...##...#.#.#...#...#.#.#...##...#";
            case 'Y' -> "#...##...#.#.#...#....#....#....#..";
            case 'Z' -> "#####....#...#...#...#...#....#####";
            case '0' -> ".###.#...##..###.#.###..##...#.###.";
            case '1' -> "..#...##....#....#....#....#...###.";
            case '2' -> ".###.#...#....#...#...#...#...#####";
            case '3' -> "####.....#....#.###.....#....#####.";
            case '4' -> "...#...##..#.#.#..#.#####...#....#.";
            case '5' -> "######....####.....#....##...#.###.";
            case '6' -> ".###.#....#....####.#...##...#.###.";
            case '7' -> "#####....#...#...#...#....#....#...";
            case '8' -> ".###.#...##...#.###.#...##...#.###.";
            case '9' -> ".###.#...##...#.####....#....#.###.";
            case '!' -> "..#....#....#....#....#.........#..";
            case '?' -> ".###.#...#....#...#...#.........#..";
            case '.' -> "................................#..";
            case ',' -> "...........................#...#...";
            case '%' -> "##..###..#...#...#...#...#..###..##";
            case ':' -> ".......#....#.........#....#.......";
            case '-' -> "................###................";
            case '+' -> ".......#....#..#####..#....#.......";
            case '\'' -> "..#....#...#.......................";
            case '/' -> "....#....#...#...#...#...#....#....";
            case '<' -> "...#...#...#...#.....#.....#.....#.";
            case '>' -> ".#.....#.....#.....#...#...#...#...";
            case '(' -> "...#...#...#....#....#.....#.....#.";
            case ')' -> ".#.....#.....#....#....#...#...#...";
            case '&' -> ".##..#..#.#.#...#...#.#.##..#..##.#";
            case '^' -> "..#...#.#.#...#....................";
            case '#' -> ".#.#.#####.#.#..#.#.#####.#.#......";
            case '*' -> ".....#.#.#.###.#####.###.#.#.#.....";
            case '=' -> "..........#####.....#####..........";
            case '_' -> "..............................#####";
            default -> "...................................";
        };
    }

    /**
     * Renders {@code text} at {@code scale} pixels per glyph pixel with a {@code top} to
     * {@code bottom} gradient and a dark outline.
     */
    public SceneImage text(String text, int scale, int top, int bottom) {
        String key = text + "|" + scale + "|" + top + "|" + bottom;
        SceneImage found = cache.get(key);
        if (found != null) return found;
        int glyphW = 5 * scale;
        int glyphH = 7 * scale;
        int gap = scale;
        int pad = Math.max(2, scale / 2 + 1);
        int width = Math.max(1, text.length() * (glyphW + gap) - gap) + pad * 2 + 2;
        int height = glyphH + pad * 2 + 2;
        boolean[] mask = new boolean[width * height];
        for (int i = 0; i < text.length(); i++) {
            String g = glyph(Character.toUpperCase(text.charAt(i)));
            int ox = pad + i * (glyphW + gap);
            for (int gy = 0; gy < 7; gy++) {
                for (int gx = 0; gx < 5; gx++) {
                    int idx = gy * 5 + gx;
                    if (idx >= g.length() || g.charAt(idx) != '#') continue;
                    for (int sy = 0; sy < scale; sy++) {
                        for (int sx = 0; sx < scale; sx++) mask[(pad + gy * scale + sy) * width + ox + gx * scale + sx] = true;
                    }
                }
            }
        }
        int[] px = new int[width * height];
        int outline = Math.max(1, scale / 2);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask[y * width + x]) {
                    double t = (y - pad) / (double) Math.max(1, glyphH - 1);
                    int c = lerp(top, bottom, Math.max(0, Math.min(1, t)));
                    // A light band near the top of each glyph row gives the lettering some shine.
                    if (((y - pad) % (scale * 7)) < Math.max(1, scale / 2)) c = lerp(c, 0xFFFFFFFF, 0.45);
                    px[y * width + x] = c;
                } else {
                    boolean near = false;
                    for (int dy = -outline; dy <= outline && !near; dy++) {
                        for (int dx = -outline; dx <= outline && !near; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx >= 0 && ny >= 0 && nx < width && ny < height && mask[ny * width + nx]) near = true;
                        }
                    }
                    if (near) px[y * width + x] = 0xFF101020;
                    else if (y > 0 && x > 0 && mask[(y - 1) * width + x - 1]) px[y * width + x] = 0x90000000;
                }
            }
        }
        // Drop shadow down-right.
        int[] shadowed = px.clone();
        int sh = Math.max(1, scale / 2 + 1);
        for (int y = height - 1; y >= sh; y--) {
            for (int x = width - 1; x >= sh; x--) {
                if ((shadowed[y * width + x] >>> 24) == 0 && (px[(y - sh) * width + x - sh] >>> 24) == 0xFF) shadowed[y * width + x] = 0x80000000;
            }
        }
        SceneImage img = new SceneImage(width, height, shadowed);
        cache.put(key, img);
        return img;
    }

    static int lerp(int a, int b, double t) {
        int aa = (a >>> 24) & 255, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24) & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }
}
