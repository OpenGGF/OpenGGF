package com.openggf.game.sonic3k;

import com.openggf.game.GameModuleRegistry;
import com.openggf.level.Palette;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.SuperStateController;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestSonic3kFormPresentation {

    @BeforeEach
    void setUp() {
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        TestEnvironment.activeGameplayMode();
    }

    @Test
    void onlySonicAdvancedFormsUseSuperSonicRendererResources() throws Exception {
        Sonic3kPlayerArt art = spy(new Sonic3kPlayerArt(mock(com.openggf.data.RomByteReader.class)));
        SpriteArtSet normalSonic = mock(SpriteArtSet.class);
        SpriteArtSet superSonic = mock(SpriteArtSet.class);
        SpriteArtSet normalTails = mock(SpriteArtSet.class);
        SpriteArtSet normalKnuckles = mock(SpriteArtSet.class);
        doReturn(normalSonic).when(art).loadSonic();
        doReturn(superSonic).when(art).loadSuperSonicArtSet();
        doReturn(normalTails).when(art).loadTails();
        doReturn(normalKnuckles).when(art).loadKnuckles();

        SpriteArtSet sonic = art.loadFormArtSet("sonic", S3kFormTier.HYPER);
        SpriteArtSet tails = art.loadFormArtSet("tails", S3kFormTier.SUPER_TAILS);
        SpriteArtSet knuckles = art.loadFormArtSet("knuckles", S3kFormTier.HYPER);

        assertSame(superSonic, sonic);
        assertSame(normalTails, tails);
        assertSame(normalKnuckles, knuckles);
    }

    @Test
    void tailsPatchWritesOnlyRomColorSlotsAndUsesSameWordsUnderwater() throws Exception {
        Tails tails = new Tails("tails", (short) 0, (short) 0);
        Sonic3kSuperStateController controller = new Sonic3kSuperStateController(tails);
        Palette palette = new Palette();
        for (int i = 0; i < Palette.PALETTE_SIZE; i++) {
            palette.getColor(i).r = 1;
            palette.getColor(i).g = 2;
            palette.getColor(i).b = 3;
        }
        byte[] patch = {0x00, (byte) 0xAE, 0x00, (byte) 0x8E, 0x04, 0x6A};

        Class<?> targetType = Class.forName(
                "com.openggf.sprites.playable.SuperStateController$PaletteTarget");
        var targetConstructor = targetType.getDeclaredConstructor(Palette.class, int.class);
        targetConstructor.setAccessible(true);
        Object target = targetConstructor.newInstance(palette, 0);
        var apply = Sonic3kSuperStateController.class.getDeclaredMethod(
                "applySurfacePalettePatch", targetType, com.openggf.level.Level.class, byte[].class);
        apply.setAccessible(true);
        apply.invoke(controller, target, null, patch);

        assertEquals(1, palette.getColor(10).r);
        assertEquals(2, palette.getColor(10).g);
        assertEquals(3, palette.getColor(10).b);
        assertColorMatchesWord(palette.getColor(8), patch, 0);
        assertColorMatchesWord(palette.getColor(9), patch, 2);
        assertColorMatchesWord(palette.getColor(11), patch, 4);

        setField(controller, "activeFormTier", S3kFormTier.SUPER_TAILS);
        setField(controller, "activePaletteData", patch);
        var underwater = Sonic3kSuperStateController.class.getDeclaredMethod(
                "underwaterPaletteTable", int.class, int.class, boolean.class);
        underwater.setAccessible(true);
        assertArrayEquals(patch, (byte[]) underwater.invoke(controller, 0, 0, false));
    }

    private static void assertColorMatchesWord(Palette.Color actual, byte[] data, int offset) {
        Palette.Color expected = new Palette.Color();
        expected.fromSegaFormat(data, offset);
        assertEquals(expected.r, actual.r);
        assertEquals(expected.g, actual.g);
        assertEquals(expected.b, actual.b);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = Sonic3kSuperStateController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
