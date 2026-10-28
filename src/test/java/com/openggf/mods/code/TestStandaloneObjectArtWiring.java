package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.internal.RuntimeObjectArtQueue;
import com.openggf.level.Pattern;
import com.openggf.level.objects.BakedSheetReader;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

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

    @Test
    void rawProviderRetainsIdentityAndHealthyCallbackResult() {
        GameModule delegate = mock(GameModule.class);
        ObjectArtProvider art = mock(ObjectArtProvider.class);
        when(delegate.getObjectArtProvider()).thenReturn(art);
        when(art.isReady()).thenReturn(true);
        GameModule wrapped = OwnerAwareStandaloneModule.wrap("owner", delegate, boundary(), Map.of());

        ObjectArtProvider provider = wrapped.getObjectArtProvider();

        assertSame(provider, wrapped.getObjectArtProvider());
        assertTrue(provider.isReady());
    }

    @Test
    void rawProviderRetainsOwnerBoundRuntimeArtCapability() {
        GameModule delegate = mock(GameModule.class);
        ObjectArtProvider art = mock(ObjectArtProvider.class,
                withSettings().extraInterfaces(RuntimeObjectArtQueue.class));
        var failure = new IllegalStateException("runtime art callback failed");
        doThrow(failure).when((RuntimeObjectArtQueue) art).processRuntimeArtQueue();
        when(delegate.getObjectArtProvider()).thenReturn(art);
        ModFaultBoundary boundary = boundary();
        GameModule wrapped = OwnerAwareStandaloneModule.wrap("owner", delegate, boundary, Map.of());

        RuntimeObjectArtQueue queue = assertInstanceOf(RuntimeObjectArtQueue.class,
                wrapped.getObjectArtProvider());
        var aborted = assertThrows(ModFaultBoundary.CallbackAborted.class, queue::processRuntimeArtQueue);

        assertEquals("owner", aborted.owner());
        assertSame(failure, aborted.getCause());
        assertFalse(boundary.isOwnerAvailable("owner"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fallbackSheetFailureRetainsItsOwnerWithOrWithoutPreparedArt(boolean preparedSheets) {
        GameModule delegate = mock(GameModule.class);
        ObjectArtProvider art = mock(ObjectArtProvider.class);
        var failure = new IllegalStateException("fallback sheet callback failed");
        when(art.getSheet("owner:missing")).thenThrow(failure);
        when(delegate.getObjectArtProvider()).thenReturn(art);
        ModFaultBoundary boundary = boundary();
        Map<String, BakedSheetReader.BakedSheet> prepared = preparedSheets
                ? Map.of("owner:key", baked(2)) : Map.of();
        GameModule wrapped = OwnerAwareStandaloneModule.wrap(
                "owner", delegate, boundary, Map.of(), prepared);

        var aborted = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> wrapped.getObjectArtProvider().getSheet("owner:missing"));

        assertEquals("owner", aborted.owner());
        assertSame(failure, aborted.getCause());
        assertFalse(boundary.isOwnerAvailable("owner"));
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
