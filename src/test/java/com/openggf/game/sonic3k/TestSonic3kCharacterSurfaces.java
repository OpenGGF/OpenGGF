package com.openggf.game.sonic3k;

import com.openggf.game.CharacterPresentation;
import com.openggf.game.CharacterPresentation.*;
import com.openggf.game.GameServices;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.util.PatternDecompressor;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kCharacterSurfaces {
    static CharacterPresentation mica() {
        byte[] pixels = new byte[256];
        java.util.Arrays.fill(pixels, (byte) 7);
        return new CharacterPresentation("MICA", null,
                Map.of(Host.S3K, new Art(new Image(16,16,pixels), null, null, null)), Map.of());
    }
    @Test void hudUsesNativeMicaLettersAndIndependentFace() throws Exception {
        var rom = GameServices.rom().getRom();
        var nativeTiles = PatternDecompressor.nemesis(rom, Sonic3kConstants.ART_NEM_SONIC_LIFE_ICON_ADDR);
        var tiles = Sonic3kCharacterPresentation.hud(rom, nativeTiles, mica());
        var expected = CharacterNameComposer.compose("MICA", Sonic3kCharacterFont.hud(rom), Map.of(), 0).image();
        assertArrayEquals(expected.pixels(), CharacterNameComposer.extract(tiles,4,4,2,0,0,expected.width(),expected.height()).pixels());
        assertArrayEquals(mica().art().get(Host.S3K).hudLifeIcon().pixels(), CharacterNameComposer.extract(tiles,0,2,2,0,0,16,16).pixels());
        for (int address : new int[]{Sonic3kConstants.ART_NEM_SONIC_LIFE_ICON_ADDR,
                Sonic3kConstants.ART_NEM_TAILS_LIFE_ICON_ADDR, Sonic3kConstants.ART_NEM_KNUCKLES_LIFE_ICON_ADDR}) {
            var stock = PatternDecompressor.nemesis(rom,address);
            assertSame(stock, Sonic3kCharacterPresentation.hud(rom,stock,null));
        }
    }
    @Test void actResultsReplaceAllNativeNameFramesWithoutTouchingGotThrough() throws Exception {
        var rom = GameServices.rom().getRom();
        var objectArt = new com.openggf.game.sonic3k.Sonic3kObjectArt(null,
                com.openggf.data.RomByteReader.fromRom(rom));
        var mappings = objectArt.loadResultsMappings();
        var stock = new com.openggf.level.Pattern[12];
        var custom = Sonic3kCharacterPresentation.results(rom, stock, mappings, mica(), 0);
        assertSame(mappings.get(0x11), custom.mappings().get(0x11)); // GOT THROUGH
        assertSame(mappings.get(0x10), custom.mappings().get(0x10)); // ACT
        assertSame(stock, Sonic3kCharacterPresentation.results(rom, stock, mappings, null, 0).patterns());
        for (int frame = 0x13; frame <= 0x16; frame++) {
            var pieces = custom.mappings().get(frame).pieces();
            assertEquals(stock.length, pieces.getFirst().tileIndex());
            assertEquals(16, pieces.getFirst().heightTiles() * 8);
            var expected = CharacterNameComposer.compose("MICA", resultsFont(rom), Map.of(), 0).image();
            assertArrayEquals(expected.pixels(), CharacterNameComposer.extract(custom.patterns(),
                    stock.length, pieces.size(), 2, 0, 0, expected.width(), expected.height()).pixels());
        }
    }
    @Test void blueSphereConsumerUsesCustomNameWithNativeTileCompensation() throws Exception {
        try (var adapter = org.mockito.Mockito.mockStatic(Sonic3kCharacterPresentation.class,
                org.mockito.Mockito.CALLS_REAL_METHODS)) {
            adapter.when(Sonic3kCharacterPresentation::active).thenReturn(mica());
            for (var character : com.openggf.game.PlayerCharacter.values()) {
                var screen = new com.openggf.game.sonic3k.specialstage.S3kSpecialStageResultsScreen(
                        0, false, 1, 0, 0, character);
                var field = screen.getClass().getDeclaredField("spriteSheet");
                field.setAccessible(true);
                var sheet = (com.openggf.level.objects.ObjectSpriteSheet) field.get(screen);
                assertNotNull(sheet);
                var piece = sheet.getFrame(0x13).pieces().getFirst();
                var expected = CharacterNameComposer.compose("MICA",
                        resultsFont(GameServices.rom().getRom()), Map.of(), 0).image();
                assertArrayEquals(expected.pixels(), CharacterNameComposer.extract(sheet.getPatterns(),
                        piece.tileIndex() - 0x87, sheet.getFrame(0x13).pieces().size(),
                        piece.heightTiles(), 0, 0, expected.width(), expected.height()).pixels());
                adapter.verify(Sonic3kCharacterPresentation::active, org.mockito.Mockito.atLeastOnce());
            }
        }
    }
    @Test void oneUpFaceIsIndependentOfHudAndPreservesNativeFallback() throws Exception {
        var rom = GameServices.rom().getRom();
        byte[] face = new byte[256];
        java.util.Arrays.fill(face, (byte) 3);
        var presentation = new CharacterPresentation("MICA", null, Map.of(Host.S3K,
                new Art(mica().art().get(Host.S3K).hudLifeIcon(), new Image(16,16,face), null, null)), Map.of());
        for (int address : new int[]{Sonic3kConstants.ART_NEM_SONIC_LIFE_ICON_ADDR,
                Sonic3kConstants.ART_NEM_TAILS_LIFE_ICON_ADDR, Sonic3kConstants.ART_NEM_KNUCKLES_LIFE_ICON_ADDR}) {
            var stock = PatternDecompressor.nemesis(rom, address);
            assertSame(stock, Sonic3kCharacterPresentation.oneUp(stock, null));
            assertSame(stock, Sonic3kCharacterPresentation.oneUp(stock, mica()));
            var custom = Sonic3kCharacterPresentation.oneUp(stock, presentation);
            assertArrayEquals(face, CharacterNameComposer.extract(custom,0,2,2,0,0,16,16).pixels());
            for (int tile = 4; tile < stock.length; tile++) assertSame(stock[tile], custom[tile]);
        }
    }
    @Test void providerPublishesHudAndIndependentMonitorTiles() throws Exception {
        byte[] face = new byte[256];
        java.util.Arrays.fill(face, (byte) 3);
        var presentation = new CharacterPresentation("MICA", null, Map.of(Host.S3K,
                new Art(mica().art().get(Host.S3K).hudLifeIcon(), new Image(16,16,face), null, null)), Map.of());
        try (var adapter = org.mockito.Mockito.mockStatic(Sonic3kCharacterPresentation.class,
                org.mockito.Mockito.CALLS_REAL_METHODS)) {
            adapter.when(Sonic3kCharacterPresentation::active).thenReturn(presentation);
            var provider = new Sonic3kObjectArtProvider();
            for (String method : new String[]{"loadHudArt", "loadMonitorArt"}) {
                var load = provider.getClass().getDeclaredMethod(method);
                load.setAccessible(true);
                load.invoke(provider);
            }
            assertArrayEquals(presentation.art().get(Host.S3K).hudLifeIcon().pixels(),
                    CharacterNameComposer.extract(provider.getHudLivesPatterns(),0,2,2,0,0,16,16).pixels());
            var monitor = provider.getSheet(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.MONITOR);
            assertArrayEquals(face, CharacterNameComposer.extract(monitor.getPatterns(),0x310,2,2,0,0,16,16).pixels());
        }
    }
    private static Map<Character, Image> resultsFont(com.openggf.data.Rom rom) throws Exception {
        var font = new java.util.LinkedHashMap<>(Sonic3kCharacterFont.results(rom));
        Sonic3kCharacterFont.title(rom).forEach(font::putIfAbsent);
        return font;
    }
}
