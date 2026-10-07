package survivors;

/** Bounded, derived presentation data owned by one module, never gameplay or rewind state.
 * No static arrays: mod reloads and separate sessions must not share mutable storage. */
final class MenuArt {
    // Immutable font geometry, built once per module. Cover each lit pixel exactly once:
    // overlapping rectangles would darken translucent text and its shadow.
    final int[][] font = fontRectangles(Draw.GLYPHS, 5, 7);

    final int[][] ringShapes = ringShapes();

    private static int[][] ringShapes() {
        int[][] shapes = new int[3][];
        for (int tier = 1; tier <= 3; tier++) {
            int radius = 7 + tier * 3, side = radius * 2 + 1, inner = radius - 3;
            char[] pixels = new char[side * side];
            for (int y = -radius; y <= radius; y++) {
                for (int x = -radius; x <= radius; x++) {
                    int distance = x * x + y * y;
                    pixels[(y + radius) * side + x + radius] =
                            distance <= radius * radius && distance >= inner * inner ? '1' : '0';
                }
            }
            shapes[tier - 1] = fontRectangles(new String(pixels), side, side)[0];
        }
        return shapes;
    }

    private static int[][] fontRectangles(String pixels, int width, int height) {
        int[][] glyphs = new int[pixels.length() / (width * height)][];
        for (int glyph = 0; glyph < glyphs.length; glyph++) {
            boolean[] remaining = new boolean[width * height];
            for (int p = 0; p < remaining.length; p++) {
                remaining[p] = pixels.charAt(glyph * remaining.length + p) == '1';
            }
            int[] rectangles = new int[remaining.length * 4];
            int count = 0;
            while (true) {
                int bestX = 0, bestY = 0, bestW = 0, bestH = 0;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int span = width - x;
                        for (int h = 1; y + h <= height; h++) {
                            int w = 0;
                            while (w < span && remaining[(y + h - 1) * width + x + w]) w++;
                            span = w;
                            if (w == 0) break;
                            if (w * h > bestW * bestH) {
                                bestX = x; bestY = y; bestW = w; bestH = h;
                            }
                        }
                    }
                }
                if (bestW == 0) break;
                rectangles[count++] = bestX;
                rectangles[count++] = bestY;
                rectangles[count++] = bestW;
                rectangles[count++] = bestH;
                for (int y = bestY; y < bestY + bestH; y++) {
                    for (int x = bestX; x < bestX + bestW; x++) remaining[y * width + x] = false;
                }
            }
            glyphs[glyph] = java.util.Arrays.copyOf(rectangles, count);
        }
        return glyphs;
    }

    record CardText(String level, String first, String second) { }
    final CardText[][] cards = cardText();

    private static CardText[][] cardText() {
        CardText[][] cards = new CardText[Upgrades.COUNT][];
        for (int id = 0; id < cards.length; id++) {
            cards[id] = new CardText[Upgrades.maxLevel(id) + 1];
            for (int next = 1; next < cards[id].length; next++) {
                String[] lines = Upgrades.describe(id, next);
                cards[id][next] = new CardText(next == 1 ? "NEW!" : "LV " + (next - 1) + ">" + next,
                        lines[0], lines[1]);
            }
        }
        return cards;
    }

}
