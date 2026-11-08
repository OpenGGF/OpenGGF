package com.openggf.game.sonic2.specialstage;

import com.openggf.game.CharacterPresentation;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.level.Pattern;
import java.util.LinkedHashMap;
import java.util.Map;

/** Name row from the half-pipe's own SONIC/MILES and overseas TAILS art. */
public final class Sonic2SpecialStageHudPresentation {
    private Sonic2SpecialStageHudPresentation() { }

    public static Map<Character, CharacterPresentation.Image> font(Pattern[] hud, Pattern[] tails) {
        var glyphs = new LinkedHashMap<Character, CharacterPresentation.Image>();
        collect(glyphs, hud, 0, "SONIC", new int[]{1, 9, 17, 25, 29}, new int[]{7, 7, 7, 3, 7});
        collect(glyphs, hud, 5, "MILES", new int[]{1, 10, 14, 21, 29}, new int[]{8, 3, 6, 7, 7});
        collect(glyphs, tails, 0, "TAILS", new int[]{4, 11, 19, 23, 29}, new int[]{7, 7, 3, 6, 7});
        return Map.copyOf(glyphs);
    }

    private static void collect(Map<Character, CharacterPresentation.Image> glyphs, Pattern[] art,
                                int first, String letters, int[] starts, int[] widths) {
        // These words are packed across tile boundaries, not one tile per letter.
        // Keep each letter's native ink/shadow and one clean leading spacing column.
        // In TAILS, the column before A still holds T's shadow on the first three rows.
        for (int i = 0; i < letters.length(); i++) {
            var ink = CharacterNameComposer.extract(art, first, 5, 1, starts[i], 0, widths[i], 8);
            int advance = widths[i] + 1;
            byte[] pixels = new byte[advance * 8];
            byte[] source = ink.pixels();
            for (int y = 0; y < 8; y++)
                System.arraycopy(source, y * widths[i], pixels, y * advance + 1, widths[i]);
            glyphs.putIfAbsent(letters.charAt(i), new CharacterPresentation.Image(advance, 8, pixels));
        }
    }

    public static Pattern[] apply(Pattern[] hud, Pattern[] tails, CharacterPresentation presentation) {
        if (presentation == null) return hud;
        String name = presentation.hudName() == null ? presentation.displayName() : presentation.hudName();
        if (name == null) return hud;
        // Stock labels retain their original word art and palette, including overseas TAILS.
        if (name.equalsIgnoreCase("SONIC") || name.equalsIgnoreCase("MILES")
                || name.equalsIgnoreCase("TAILS")) return hud;
        var composition = CharacterNameComposer.compose(name, font(hud, tails),
                presentation.supplementaryGlyphs().getOrDefault(new CharacterPresentation.Font(
                        CharacterPresentation.Host.S2, CharacterPresentation.Style.HUD), Map.of()), 0);
        if (!composition.complete() || composition.image().width() > 40 || composition.image().height() > 8)
            return hud;
        Pattern[] result = hud.clone();
        // Map_obj5E_0008 (docs/s2disasm/mappings/sprite/obj5E.asm:7-12): five name tiles.
        for (int i = 0; i < 5; i++) result[i] = new Pattern();
        Pattern[] nameTiles = CharacterNameComposer.tiles(composition.image());
        System.arraycopy(nameTiles, 0, result, 0, nameTiles.length);
        return result;
    }
}
