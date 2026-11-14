package com.openggf.game.presentation;

import com.openggf.game.CharacterPresentation;
import com.openggf.game.CharacterPresentation.Image;
import com.openggf.level.Pattern;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TestCharacterNameComposer {
    @Test void cropsAcrossNativeTileBoundariesAndRetilesWithoutChangingPixels() {
        Pattern[] tiles = new Pattern[8];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = new Pattern();
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
                tiles[i].setPixel(x, y, (byte)((i + x + y) % 16));
        }
        Image crop = CharacterNameComposer.extract(tiles, 0, 4, 2, 5, 3, 19, 11);
        byte[] pixels = crop.pixels();
        for (int y = 0; y < 11; y++) for (int x = 0; x < 19; x++)
            assertEquals(tiles[(x + 5) / 8 * 2 + (y + 3) / 8].getPixel((x + 5) % 8, (y + 3) % 8),
                    pixels[y * 19 + x]);
        var retiled = CharacterNameComposer.tiles(crop);
        assertArrayEquals(pixels, CharacterNameComposer.extract(retiled, 0, 3, 2, 0, 0, 19, 11).pixels());
    }
    @Test void nativeGlyphsWinAndCoverageIdentifiesOnlyUsedSupplements() {
        Image nativeGlyph = new Image(1, 1, new byte[]{1});
        Image customGlyph = new Image(1, 1, new byte[]{2});
        var result = CharacterNameComposer.compose("MICA", Map.of('I',nativeGlyph,'C',nativeGlyph),
                Map.of('M',customGlyph,'A',customGlyph,'I',customGlyph), 0);
        assertTrue(result.complete());
        assertEquals(Set.of('I','C'), result.nativeGlyphs());
        assertEquals(Set.of('M','A'), result.supplementaryGlyphs());
        assertArrayEquals(new byte[]{2,1,1,2}, result.image().pixels());
        var incomplete = CharacterNameComposer.compose("MICA", Map.of('I',nativeGlyph), Map.of(), 0);
        assertEquals(Set.of('M','C','A'), incomplete.missingGlyphs());
        assertNull(incomplete.image());
    }
    @Test void boundedWordsRemoveOnlyPaddingAndKeepNativePrecedence() {
        var nativeGlyph = new Image(4, 2, new byte[]{0, 1, 2, 0, 0, 3, 4, 0});
        var supplement = new Image(2, 2, new byte[]{5, 6, 7, 8});
        var font = Map.of('A', nativeGlyph);
        var normal = CharacterNameComposer.compose("AB", font, Map.of('A', supplement, 'B', supplement), 0);
        assertArrayEquals(normal.image().pixels(), CharacterNameComposer.composeToWidth(
                "AB", font, Map.of('A', supplement, 'B', supplement), 6).image().pixels());
        var fitted = CharacterNameComposer.composeToWidth("AB", font, Map.of('A', supplement, 'B', supplement), 4);
        assertEquals(4, fitted.image().width());
        assertArrayEquals(new byte[]{1, 2, 5, 6, 3, 4, 7, 8}, fitted.image().pixels());
        assertEquals(Set.of('A'), fitted.nativeGlyphs());
        assertEquals(Set.of('B'), fitted.supplementaryGlyphs());
        assertEquals(4, CharacterNameComposer.composeToWidth("AB", font,
                Map.of('B', supplement), 3).image().width(), "Never discard ink to fit");
        assertFalse(CharacterNameComposer.composeToWidth("AC", font, Map.of(), 4).complete());
    }

    @Test void presentationCannotRetainMutableCreatorArraysOrMaps() {
        byte[] pixels = {1};
        Image image = new Image(1,1,pixels);
        pixels[0] = 2;
        image.pixels()[0] = 3;
        assertArrayEquals(new byte[]{1}, image.pixels());
        var slots = new java.util.HashMap<CharacterPresentation.Host, CharacterPresentation.Art>();
        var value = new CharacterPresentation("MICA", null, slots, Map.of());
        slots.put(CharacterPresentation.Host.S1, new CharacterPresentation.Art(null,null,null,null));
        assertTrue(value.art().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new Image(1,1,new byte[]{16}));
        assertThrows(IllegalArgumentException.class, () -> new CharacterPresentation.Art(image,null,null,null));
    }
}
