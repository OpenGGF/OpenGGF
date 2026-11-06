package com.openggf.game.sonic1;

import static com.openggf.game.sonic1.Sonic1RomProfile.resolve;

import com.openggf.data.RomByteReader;
import com.openggf.game.internal.HudSatLayout;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.objects.HudStaticArt;
import com.openggf.level.objects.HudStaticArtFactory;
import com.openggf.level.objects.HudStaticArtFactory.Layout;

public final class Sonic1HudStaticArtFactory {
    /** Map_HUD / Map_HUD_internal, World REV01; byte counts and five-byte pieces. */
    private static final int HUD_SAT_MAPPING_ADDR = 0x1CD6A;
    private static final Layout LAYOUT = new Layout(0, 0, 0, true);

    private Sonic1HudStaticArtFactory() {
    }

    public static HudStaticArt create(Pattern[] textPatterns, Pattern[] livesPatterns) {
        return HudStaticArtFactory.create(textPatterns, livesPatterns, LAYOUT);
    }

    static HudSatLayout loadSatLayout(RomByteReader reader) {
        return new HudSatLayout(S1SpriteDataLoader.loadMappingFrames(reader, resolve(reader, HUD_SAT_MAPPING_ADDR), 4));
    }

    static Palette createKnucklesLivesPalette(Palette basePalette) {
        if (basePalette == null) {
            return null;
        }
        Palette override = basePalette.deepCopy();
        setColor(override, 12, 255, 73, 109);
        setColor(override, 13, 219, 0, 36);
        setColor(override, 14, 109, 0, 36);
        return override;
    }

    static void copyPatternPair(Pattern[] dest, int destIndex, Pattern[] src, int srcIndex) {
        if (src == null || srcIndex < 0 || srcIndex + 1 >= src.length) {
            return;
        }
        if (destIndex < 0 || destIndex + 1 >= dest.length) {
            return;
        }
        if (dest[destIndex] == null) {
            dest[destIndex] = new Pattern();
        }
        if (dest[destIndex + 1] == null) {
            dest[destIndex + 1] = new Pattern();
        }
        dest[destIndex].copyFrom(src[srcIndex]);
        dest[destIndex + 1].copyFrom(src[srcIndex + 1]);
    }

    private static void setColor(Palette palette, int index, int r, int g, int b) {
        Palette.Color color = palette.getColor(index);
        color.r = (byte) r;
        color.g = (byte) g;
        color.b = (byte) b;
    }
}
