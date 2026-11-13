package com.openggf.game.sonic2.credits;

import com.openggf.game.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSonic2SidekickPhotos {
    @Test void onlyTheFirstActiveSidekickMaySupplyPhotos() throws Exception {
        var key=CharacterKey.parsePersisted("photo-test:talon");
        var definition=mock(CharacterDefinition.class);
        var photos=new EndingSpriteSet(new StageSpriteSet(Map.of(1,new StageSpriteSet.Frame(new CharacterPresentation.Image(1,1,new byte[]{1}),0,0))),new int[16]);
        var presentation=new CharacterPresentation("Talon",null,Map.of(),Map.of(),Map.of(),
                Map.of(EndingSpriteSet.Surface.S2_PHOTOS,photos));
        when(definition.key()).thenReturn(key);
        when(definition.presentationSupplier()).thenReturn(code -> {
            assertEquals(key.persisted(),code);
            return presentation;
        });
        var registry=PlayableCharacterRegistry.empty().register(key,definition);
        var surface=EndingSpriteSet.Surface.S2_PHOTOS;
        var solo=EndingSpriteSet.Surface.S2_SOLO_PHOTOS;
        assertNull(photos(registry,"sonic",List.of(),surface,solo));
        assertNull(photos(registry,"sonic",List.of("tails"),surface,solo));
        assertNull(photos(registry,"sonic",List.of("tails",key.persisted()),surface,solo));
        assertSame(photos,photos(registry,"sonic",List.of(key.persisted()),surface,solo));
        // The photos show the search party: a lead's own S2_PHOTOS never stands in for a partner.
        assertNull(photos(registry,key.persisted(),List.of(),surface,solo));
        when(definition.presentationSupplier()).thenReturn(code -> null);
        assertNull(photos(registry,"sonic",List.of(key.persisted()),surface,solo));
        when(definition.presentationSupplier()).thenReturn(null);
        assertNull(photos(registry,"sonic",List.of(key.persisted()),surface,solo));
    }
    @Test void aSoloLeadSuppliesItsPartnersPhotosExplicitly() throws Exception {
        var key=CharacterKey.parsePersisted("photo-test:lead");
        var definition=mock(CharacterDefinition.class);
        var partner=new EndingSpriteSet(new StageSpriteSet(Map.of(1,new StageSpriteSet.Frame(new CharacterPresentation.Image(1,1,new byte[]{1}),0,0))),new int[16]);
        when(definition.key()).thenReturn(key);
        when(definition.presentationSupplier()).thenReturn(code -> new CharacterPresentation("Lead",null,Map.of(),Map.of(),Map.of(),
                Map.of(EndingSpriteSet.Surface.S2_SOLO_PHOTOS,partner)));
        var registry=PlayableCharacterRegistry.empty().register(key,definition);
        assertSame(partner,photos(registry,key.persisted(),List.of(),EndingSpriteSet.Surface.S2_PHOTOS,EndingSpriteSet.Surface.S2_SOLO_PHOTOS));
        // A configured sidekick is the search party, even a stock one whose photos stay native.
        assertNull(photos(registry,key.persisted(),List.of("tails"),EndingSpriteSet.Surface.S2_PHOTOS,EndingSpriteSet.Surface.S2_SOLO_PHOTOS));
    }
    private static EndingSpriteSet photos(PlayableCharacterRegistry registry,String main,List<String> sidekicks,
            EndingSpriteSet.Surface surface,EndingSpriteSet.Surface solo) throws Exception {
        return com.openggf.game.presentation.EndingCharacterPresentation.companion(registry,main,sidekicks,surface,solo);
    }
}
