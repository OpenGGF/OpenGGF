package com.openggf.mods.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The scene API's small value types: styles, ROM sprite requests and the absent mouse. */
class TestSceneApiValues {
    @Test
    void drawStylesAreImmutableValues() {
        SceneDraw plain = SceneDraw.plain();
        SceneDraw styled = plain.withScale(2).withFlipX(true).withTint(0xFF8080FF).withAlpha(0.5f).withFlash(0x80FFFFFF);
        assertEquals(1f, plain.scaleX(), "plain() is untouched by chaining");
        assertEquals(2f, styled.scaleY());
        assertTrue(styled.flipX());
        assertFalse(styled.flipY());
        assertEquals(0x808080FF, styled.tint(), "withAlpha multiplies the tint's alpha");
        assertEquals(0x80FFFFFF, styled.flash());
        assertEquals(plain.withScale(2), plain.withScale(2, 2), "equal by value");
        assertEquals(plain.withScale(2).hashCode(), plain.withScale(2, 2).hashCode());
        assertNotEquals(plain, plain.withFlipY(true));
        assertEquals(0xFF000000 | 0x8080FF, plain.withAlpha(0.5f).withTint(0xFF8080FF).tint(),
                "a later withTint replaces the alpha too");
    }

    @Test
    void romSpriteRequestFactoriesAndValidation() {
        RomSpriteRequest compressed = RomSpriteRequest.of(0x367DCA, RomSpriteRequest.Compression.KOSINSKI_MODULED,
                0x3616C0, 1);
        assertFalse(compressed.hasDplc());
        assertEquals(-1, compressed.dplcAddress());
        RomSpriteRequest raw = RomSpriteRequest.uncompressed(0x158CAE, 0x200, 0x1000, 0);
        assertEquals(RomSpriteRequest.Compression.UNCOMPRESSED, raw.compression());
        assertEquals(0x200, raw.artSize());
        assertFalse(raw.hasDplc());
        RomSpriteRequest streamed = RomSpriteRequest.streamed(0x36732A, 0xAA0, 0x3615A8, 0x36156E,
                RomSpriteRequest.DplcLayout.OBJECT, 1);
        assertTrue(streamed.hasDplc());
        RomSpriteRequest cued = RomSpriteRequest.compressedWithDplc(0x1000, RomSpriteRequest.Compression.NEMESIS,
                0x2000, 0x3000, RomSpriteRequest.DplcLayout.PLAYER, 2);
        assertEquals(0x3000, cued.dplcAddress());
        assertEquals(RomSpriteRequest.DplcLayout.PLAYER, cued.dplcLayout());
        assertEquals(-4, cued.withTileOffset(-4).tileOffset());

        assertThrows(IllegalArgumentException.class, () -> new RomSpriteRequest(0, RomSpriteRequest.Compression.NEMESIS,
                0, 0, -1, null, 0, 0), "a null layout is an error, not OBJECT");
        assertThrows(IllegalArgumentException.class, () -> new RomSpriteRequest(0, RomSpriteRequest.Compression.NEMESIS,
                0, 0, -2, RomSpriteRequest.DplcLayout.OBJECT, 0, 0), "-1 is the only 'no DPLC'");
        assertThrows(IllegalArgumentException.class, () -> RomSpriteRequest.uncompressed(0, 0, 0, 0),
                "uncompressed art needs its size");
        assertThrows(IllegalArgumentException.class, () -> new RomSpriteRequest(0,
                RomSpriteRequest.Compression.NEMESIS, 0x40, 0, -1, RomSpriteRequest.DplcLayout.OBJECT, 0, 0),
                "compressed art has no size to pass");
        assertThrows(IllegalArgumentException.class, () -> RomSpriteRequest.of(0, RomSpriteRequest.Compression.NEMESIS,
                0, 4), "palette line 0-3");
    }

    @Test
    void theAbsentMouseIsNowhere() {
        SceneMouse none = SceneMouse.none();
        assertSame(none, SceneMouse.none());
        assertEquals(-1, none.x());
        assertFalse(none.inside());
        assertFalse(none.over(-10, -10, 100, 100), "outside the picture nothing is under the pointer");
        assertFalse(none.lastInputWasMouse());
    }
}
