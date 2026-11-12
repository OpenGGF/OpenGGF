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
        assertNull(ModEndingPresentation.sidekickPhotos(registry,List.of()));
        assertNull(ModEndingPresentation.sidekickPhotos(registry,List.of("tails")));
        assertNull(ModEndingPresentation.sidekickPhotos(registry,List.of("tails",key.persisted())));
        assertSame(photos,ModEndingPresentation.sidekickPhotos(registry,List.of(key.persisted())));
        when(definition.presentationSupplier()).thenReturn(code -> null);
        assertNull(ModEndingPresentation.sidekickPhotos(registry,List.of(key.persisted())));
        when(definition.presentationSupplier()).thenReturn(null);
        assertNull(ModEndingPresentation.sidekickPhotos(registry,List.of(key.persisted())));
    }
}
