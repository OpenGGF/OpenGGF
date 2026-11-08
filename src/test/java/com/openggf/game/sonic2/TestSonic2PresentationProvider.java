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
        when(definition.presentationSupplier()).thenReturn(code -> {
            assertEquals(key.persisted(),code);
            return TestSonic2CharacterPresentation.mica();
        });
        var module=mock(GameModule.class);
        when(module.getPlayableCharacterRegistry()).thenReturn(PlayableCharacterRegistry.empty().register(key,definition));
        try(var games=mockStatic(GameServices.class,CALLS_REAL_METHODS);
            var teams=mockStatic(ActiveGameplayTeamResolver.class,CALLS_REAL_METHODS)) {
            games.when(GameServices::currentOrBootstrapGameModule).thenReturn(module);
            teams.when(() -> ActiveGameplayTeamResolver.resolveMainCharacterCode(GameServices.configuration())).thenReturn(key.persisted());
            var provider=new Sonic2ObjectArtProvider();
            provider.loadArtForZone(0);
            var hud=provider.getHudLivesPatterns();
            for(int i=0;i<4;i++) for(int x=0;x<8;x++) for(int y=0;y<8;y++) assertEquals(3,hud[i].getPixel(x,y));
            var monitor=provider.getSheet(ObjectArtKeys.MONITOR);
            for(int i=0;i<4;i++) for(int x=0;x<8;x++) for(int y=0;y<8;y++)
                assertEquals(3,monitor.getPatterns()[Sonic2ObjectArt.MONITOR_LIFE_ICON_TILE+i].getPixel(x,y));
            assertTrue(provider.getSheet(ObjectArtKeys.RESULTS).getFrame(0).pieces().size()>8);
        }
    }
}
