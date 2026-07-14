package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.ObjectArtProvider;
import com.openggf.level.Pattern;
import com.openggf.level.objects.BakedSheetReader;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies standalone {@code registerObjectArt} sheets are actually served through the proxy. */
class TestStandaloneObjectArtWiring {

    @Test
    void standaloneModuleWithRegisteredArtServesItThroughTheProxy() {
        ModFaultBoundary boundary = boundary();
        GameModule delegate = mock(GameModule.class);
        when(delegate.getObjectArtProvider()).thenReturn(null);
        Map<String, BakedSheetReader.BakedSheet> prepared = Map.of("owner:key", baked(2));

        GameModule wrapped = OwnerAwareStandaloneModule.wrap(
                "owner", delegate, boundary, Map.of(), prepared);

        ObjectArtProvider provider = wrapped.getObjectArtProvider();
        assertNotNull(provider);
        assertFalse(Proxy.isProxyClass(provider.getClass()));
        assertNotNull(provider.getRenderer("owner:key"));
        assertEquals(List.of("owner:key"), provider.getRendererKeys());
    }

    @Test
    void standaloneModuleWithoutArtKeepsNullProvider() {
        ModFaultBoundary boundary = boundary();
        GameModule delegate = mock(GameModule.class);
        when(delegate.getObjectArtProvider()).thenReturn(null);

        GameModule wrapped = OwnerAwareStandaloneModule.wrap(
                "owner", delegate, boundary, Map.of(), Map.of());

        assertNull(wrapped.getObjectArtProvider());
    }

    @Test
    void decoratedProviderIsNotReProxied() {
        ModFaultBoundary boundary = boundary();
        GameModule delegate = mock(GameModule.class);
        when(delegate.getObjectArtProvider()).thenReturn(null);
        Map<String, BakedSheetReader.BakedSheet> prepared = Map.of("owner:key", baked(3));

        GameModule wrapped = OwnerAwareStandaloneModule.wrap(
                "owner", delegate, boundary, Map.of(), prepared);

        ObjectArtProvider provider = wrapped.getObjectArtProvider();
        assertInstanceOf(com.openggf.game.ObjectArtOverlayProvider.class, provider);
        assertSame(provider, wrapped.getObjectArtProvider(), "decorated provider must be cached");
    }

    private static ModFaultBoundary boundary() {
        return new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
    }

    private static BakedSheetReader.BakedSheet baked(int color) {
        Pattern pattern = new Pattern();
        pattern.setPixel(0, 0, (byte) color);
        return new BakedSheetReader.BakedSheet(new Pattern[] {pattern}, List.of(), Optional.empty());
    }
}
