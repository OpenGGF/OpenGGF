package com.openggf.game.sonic2;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.util.PatternDecompressor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * s2-dez-art-corruption:1: native PLC slot reuse, independently of the author's quick-kill route.
 * The 2026-10-26 audit at 4f5eb1ee2de0b6128c39728289516c079d71064e
 * reached both orders but observed the dedicated explosion sheet staying fiery.
 * Keep that historical negative evidence; this regression now requires the ROM result.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestTasS2DezArtAlias {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nativeLastWriterOwnsTheExplosionTiles(boolean animalsLast) throws Exception {
        var rom = GameServices.rom().getRom();
        var fiery = Sonic2PlcLoader.parsePlc(rom, Sonic2Constants.PLC_FIERY_EXPLOSION).entries();
        var animals = Sonic2PlcLoader.parsePlc(rom, Sonic2Constants.PLC_ANIMALS_DEZ).entries();
        assertEquals(1, fiery.size()); assertEquals(2, animals.size());
        assertEquals(0x580, fiery.getFirst().tileIndex());
        assertEquals(0x580, animals.getFirst().tileIndex(), "ROM itself establishes the alias");
        assertEquals(0x594, animals.get(1).tileIndex());
        var expectedFiery = PatternDecompressor.nemesis(rom, fiery.getFirst().romAddr());
        var expectedAnimal = PatternDecompressor.nemesis(rom, animals.getFirst().romAddr());
        assertEquals(100, expectedFiery.length);
        assertEquals(18, expectedAnimal.length);
        assertEquals(16, PatternDecompressor.nemesis(rom, animals.get(1).romAddr()).length);
        assertFalse(java.util.Arrays.equals(pixels(expectedFiery[0]), pixels(expectedAnimal[0])),
                "control art must be distinguishable");
        GameServices.graphics().initHeadless();
        var provider = new Sonic2ObjectArtProvider(rom, RomByteReader.fromRom(rom));
        provider.loadArtForZone(14); // native ROM zone index, DEZ
        provider.requestPlc(animalsLast ? Sonic2Constants.PLC_FIERY_EXPLOSION : Sonic2Constants.PLC_ANIMALS_DEZ);
        provider.requestPlc(animalsLast ? Sonic2Constants.PLC_ANIMALS_DEZ : Sonic2Constants.PLC_FIERY_EXPLOSION);
        var sheet = provider.getSheet(ObjectArtKeys.BOSS_EXPLOSION);
        assertNotNull(sheet); assertTrue(sheet.getFrameCount() > 0);
        var renderer = provider.getRenderer(ObjectArtKeys.BOSS_EXPLOSION);
        assertNotNull(renderer, "Obj58's actual render consumer must exist");
        assertArrayEquals(pixels(animalsLast ? expectedAnimal[0] : expectedFiery[0]),
                pixels(sheet.getPatterns()[0]), "native ordered publication must reach the actual Obj58 sheet");
        com.openggf.tests.reel.S2TestReelPresentation.explosionSheet(renderer);

    }
    private static byte[] pixels(Pattern pattern) {
        byte[] pixels = new byte[64];
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) pixels[y * 8 + x] = pattern.getPixel(x, y);
        return pixels;
    }
}
