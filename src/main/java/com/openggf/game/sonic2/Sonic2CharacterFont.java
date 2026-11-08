package com.openggf.game.sonic2;

import com.openggf.data.Rom;
import com.openggf.game.CharacterPresentation.Image;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.util.PatternDecompressor;
import java.io.IOException;
import java.util.*;

/** LoadTitleCard0 alphabet: E/N/O/Z are in the base bank, the remaining letters in TitleCard2. */
public final class Sonic2CharacterFont {
    private Sonic2CharacterFont() { }
    public static Map<Character, Image> title(Rom rom) throws IOException {
        var letters = PatternDecompressor.nemesis(rom, Sonic2Constants.ART_NEM_TITLE_CARD2_ADDR);
        var base = PatternDecompressor.nemesis(rom, Sonic2Constants.ART_NEM_TITLE_CARD_ADDR);
        int[] offsets = {0,4,8,12,-1,16,20,24,28,30,34,38,42,-1,-1,48,52,56,60,64,68,72,76,82,86,-1};
        int[] baseOffsets = {0,0,0,0,0,0,0,0,0,0,0,0,0,4,8,0,0,0,0,0,0,0,0,0,0,12};
        var glyphs = new LinkedHashMap<Character, Image>();
        for (int i = 0; i < 26; i++) {
            int columns = i == 8 ? 1 : i == 12 || i == 22 ? 3 : 2;
            glyphs.put((char)('A' + i), CharacterNameComposer.extract(offsets[i] < 0 ? base : letters,
                    offsets[i] < 0 ? baseOffsets[i] : offsets[i], columns, 2, 0, 0, columns * 8, 16));
        }
        return Map.copyOf(glyphs);
    }
}
