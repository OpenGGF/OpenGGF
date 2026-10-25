package typinghog.art;

/**
 * Parses the mod's editable glyph text ({@code art/plate-font.txt}) into the alphabet and
 * row-major 0/1 pixel string {@code com.openggf.mods.ui.BitmapFont.binary} takes. Each glyph
 * is a line {@code = c} naming its character, then exactly {@code height} rows of
 * {@code width} cells where {@code #} is ink. Engine-free so the checks can run without it.
 */
public record GlyphSheet(String alphabet, String bits, int width, int height) {
    public static GlyphSheet parse(String text, int width, int height) {
        StringBuilder alphabet = new StringBuilder();
        StringBuilder bits = new StringBuilder();
        String[] lines = text.split("\n", -1);
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            if (line.startsWith("#") || line.isBlank()) { i++; continue; }
            if (!line.startsWith("= ") || line.length() != 3) {
                throw new IllegalArgumentException("Expected '= c' at line " + (i + 1) + ": " + line);
            }
            char c = line.charAt(2);
            if (alphabet.indexOf(String.valueOf(c)) >= 0) throw new IllegalArgumentException("Duplicate glyph " + c);
            alphabet.append(c);
            for (int row = 0; row < height; row++) {
                String cells = i + 1 + row < lines.length ? lines[i + 1 + row] : "";
                if (cells.length() != width) {
                    throw new IllegalArgumentException("Glyph '" + c + "' row " + row + " is not " + width + " wide");
                }
                for (int x = 0; x < width; x++) {
                    char cell = cells.charAt(x);
                    if (cell != '#' && cell != '.') throw new IllegalArgumentException("Glyph '" + c + "' has '" + cell + "'");
                    bits.append(cell == '#' ? '1' : '0');
                }
            }
            i += 1 + height;
        }
        return new GlyphSheet(alphabet.toString(), bits.toString(), width, height);
    }

    /** True when every glyph cell column {@code x} is clear (used to tighten proportional spacing). */
    public boolean columnClear(char c, int x) {
        int index = alphabet.indexOf(c);
        if (index < 0) return true;
        int base = index * width * height;
        for (int row = 0; row < height; row++) if (bits.charAt(base + row * width + x) == '1') return false;
        return true;
    }
}
