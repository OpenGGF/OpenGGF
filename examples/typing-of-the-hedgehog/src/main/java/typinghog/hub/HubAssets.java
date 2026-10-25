package typinghog.hub;

import java.util.function.UnaryOperator;

/**
 * The hub's own files from the jar, read once during registration (mod files cannot be read
 * later): the story, Typing School lines, original text art and the glyph sheets.
 */
public record HubAssets(String story, String school, String art, String logo, String plateFont, String hubGlyphs) {

    /** Reads every hub file through {@code reader} (path to text; "" when missing). */
    public static HubAssets read(UnaryOperator<String> reader) {
        return new HubAssets(reader.apply("hub/story.txt"), reader.apply("hub/school.txt"),
                reader.apply("hub/art.txt"), reader.apply("hub/logo.txt"), reader.apply("art/plate-font.txt"),
                reader.apply("hub/glyphs.txt"));
    }
}
