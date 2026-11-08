package com.openggf.game.sonic2;

import com.openggf.game.*;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2PresentationProvider {
    @Test void providerConsumesOwnerScopedPresentationForHudResultsAndMonitor() throws Exception {
        var key=CharacterKey.mod("test-mica","mica");
        var definition=mock(CharacterDefinition.class);
        when(definition.key()).thenReturn(key);
        var loads = new java.util.concurrent.atomic.AtomicInteger();
        when(definition.presentationSupplier()).thenReturn(code -> {
            loads.incrementAndGet();
            assertEquals(key.persisted(),code);
            var hudPresentation = TestSonic2CharacterPresentation.mica();
            byte[] face = new byte[256];
            java.util.Arrays.fill(face, (byte) 5);
            return new CharacterPresentation("MICA", null,
                    java.util.Map.of(CharacterPresentation.Host.S2, new CharacterPresentation.Art(
                            hudPresentation.art().get(CharacterPresentation.Host.S2).hudLifeIcon(),
                            new CharacterPresentation.Image(16, 16, face), null, null)), java.util.Map.of());
        });
        var module=spy(GameServices.module());
        var registry = PlayableCharacterRegistry.empty().register(key, definition);
        when(module.getPlayableCharacterRegistry()).thenReturn(registry);
        try(var games=mockStatic(GameServices.class,CALLS_REAL_METHODS);
            var teams=mockStatic(ActiveGameplayTeamResolver.class,CALLS_REAL_METHODS)) {
            games.when(GameServices::module).thenReturn(module);
            teams.when(() -> ActiveGameplayTeamResolver.resolveMainCharacterCode(GameServices.configuration())).thenReturn(key.persisted());
            var provider=new Sonic2ObjectArtProvider();
            provider.loadArtForZone(0);
            var hud=provider.getHudLivesPatterns();
            for(int i=0;i<4;i++) for(int x=0;x<8;x++) for(int y=0;y<8;y++) assertEquals(3,hud[i].getPixel(x,y));
            var monitor=provider.getSheet(ObjectArtKeys.MONITOR);
            for(int i=0;i<4;i++) for(int x=0;x<8;x++) for(int y=0;y<8;y++)
                assertEquals(5,monitor.getPatterns()[Sonic2ObjectArt.MONITOR_LIFE_ICON_TILE+i].getPixel(x,y));
            assertTrue(provider.getSheet(ObjectArtKeys.RESULTS).getFrame(0).pieces().size()>8);
            assertEquals(1, loads.get(), "one presentation snapshot owns all surfaces in a zone load");
        }
    }
    @Test void stockSonicAndTailsKeepNativeHudMonitorAndResults() throws Exception {
        var rom = com.openggf.tests.TestEnvironment.currentRom();
        var module = spy(GameServices.module());
        when(module.getPlayableCharacterRegistry()).thenReturn(PlayableCharacterRegistry.empty());
        var nativeArt = new Sonic2ObjectArt(rom, com.openggf.data.RomByteReader.fromRom(rom)).loadForZone(0);
        try (var games = mockStatic(GameServices.class, CALLS_REAL_METHODS);
             var teams = mockStatic(ActiveGameplayTeamResolver.class, CALLS_REAL_METHODS)) {
            games.when(GameServices::module).thenReturn(module);
            for (String character : new String[]{"sonic", "tails"}) {
                teams.when(() -> ActiveGameplayTeamResolver.resolveMainCharacterCode(GameServices.configuration()))
                        .thenReturn(character);
                var provider = new Sonic2ObjectArtProvider();
                provider.loadArtForZone(0);
                int address = character.equals("sonic")
                        ? com.openggf.game.sonic2.constants.Sonic2Constants.ART_NEM_SONIC_LIFE_ADDR
                        : com.openggf.game.sonic2.constants.Sonic2Constants.ART_NEM_TAILS_LIFE_ADDR;
                var expected = com.openggf.util.PatternDecompressor.nemesis(rom, address);
                // The provider's initial HUD view is the native loader's Sonic bank;
                // character-specific ROM art remains with the existing native selection path.
                var nativeHud = nativeArt.getHudLivesPatterns();
                assertTiles(nativeHud, provider.getHudLivesPatterns(), 0, nativeHud.length);
                assertTiles(expected, provider.getSheet(ObjectArtKeys.MONITOR).getPatterns(),
                        Sonic2ObjectArt.MONITOR_LIFE_ICON_TILE, 4);
                var results = provider.getSheet(ObjectArtKeys.RESULTS);
                assertTiles(nativeArt.resultsSheet().getPatterns(), results.getPatterns(), 0, results.getPatterns().length);
                for (int i = 0; i < results.getFrameCount(); i++)
                    assertEquals(nativeArt.resultsSheet().getFrame(i).pieces(), results.getFrame(i).pieces());
            }
        }
    }

    private static void assertTiles(com.openggf.level.Pattern[] expected, com.openggf.level.Pattern[] actual,
                                    int offset, int count) {
        for (int i = 0; i < count; i++) for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++)
            assertEquals(expected[i].getPixel(x, y), actual[offset + i].getPixel(x, y));
    }
}
