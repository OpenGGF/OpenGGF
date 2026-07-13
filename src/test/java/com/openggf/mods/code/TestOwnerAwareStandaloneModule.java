package com.openggf.mods.code;

import com.openggf.game.GameDataSource;
import com.openggf.game.GameModule;
import com.openggf.game.ZoneRegistry;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestOwnerAwareStandaloneModule {
    @Test
    void moduleProviderAndGameCallbacksRunInsideOwnerBoundary() throws Exception {
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), findings,
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        GameModule delegate = mock(GameModule.class);
        when(delegate.getIdentifier()).thenReturn("owner");
        when(delegate.getGameCode()).thenReturn("owner");
        when(delegate.getGameId()).thenReturn(com.openggf.game.GameId.STANDALONE);
        ZoneRegistry zones = mock(ZoneRegistry.class);
        when(delegate.getZoneRegistry()).thenReturn(zones);
        when(zones.getZoneName(0)).thenThrow(new IllegalStateException("provider"));
        com.openggf.data.Game game = mock(com.openggf.data.Game.class);
        GameDataSource source = new GameDataSource() {
            @Override public Optional<com.openggf.data.Rom> rom() { return Optional.empty(); }
            @Override public java.io.InputStream openAsset(String path) {
                return java.io.InputStream.nullInputStream();
            }
            @Override public String identity() { return "mod:owner:test"; }
        };
        when(delegate.createGame(source)).thenReturn(game);
        when(game.getMusicId(0)).thenThrow(new java.io.IOException("game"));

        GameModule wrapped = OwnerAwareStandaloneModule.wrap("owner", delegate, boundary, Map.of());

        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> wrapped.getZoneRegistry().getZoneName(0));
        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> wrapped.createGame(source).getMusicId(0));
        assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor("owner").getFirst().code());
    }

    @Test
    void directModuleFailureAbortsOwner() {
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        GameModule delegate = mock(GameModule.class);
        when(delegate.getZoneRegistry()).thenThrow(new IllegalStateException("module"));
        GameModule wrapped = OwnerAwareStandaloneModule.wrap("owner", delegate, boundary, Map.of());

        assertThrows(ModFaultBoundary.CallbackAborted.class, wrapped::getZoneRegistry);
    }

    @Test
    void gameWrapperOverridesEveryAbstractGameCallback() {
        Class<?> wrapper = java.util.Arrays.stream(OwnerAwareStandaloneModule.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("OwnerAwareStandaloneGame"))
                .findFirst().orElseThrow();

        java.util.Arrays.stream(com.openggf.data.Game.class.getDeclaredMethods())
                .filter(method -> Modifier.isAbstract(method.getModifiers()))
                .forEach(method -> assertDoesNotThrow(() ->
                        wrapper.getDeclaredMethod(method.getName(), method.getParameterTypes()),
                        () -> "Missing owner boundary override for " + method));
    }

    @Test
    void functionalFactoriesAndDynamicServicesRemainInsideBoundary() {
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        GameModule delegate = mock(GameModule.class);
        when(delegate.getInvincibilityStarsFactory()).thenReturn(player -> {
            throw new IllegalStateException("factory");
        });
        DynamicService service = mock(DynamicService.class);
        when(delegate.getGameService(DynamicService.class)).thenReturn(service);
        doThrow(new IllegalStateException("service")).when(service).invoke();
        GameModule wrapped = OwnerAwareStandaloneModule.wrap("owner", delegate, boundary, Map.of());

        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> wrapped.getInvincibilityStarsFactory().apply(null));
        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> wrapped.getGameService(DynamicService.class).invoke());
    }

    @Test
    void standaloneLevelMusicMustBeNonNullNamespacedAndOwnedByTheModule() {
        assertEquals(com.openggf.game.MusicReference.namespaced("owner", "level"),
                wrappedMusic(com.openggf.game.MusicReference.namespaced("owner", "level"))
                        .getLevelMusicReference(0, 0));
        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> wrappedMusic(com.openggf.game.MusicReference.stock(1))
                        .getLevelMusicReference(0, 0));
        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> wrappedMusic(com.openggf.game.MusicReference.namespaced("other", "level"))
                        .getLevelMusicReference(0, 0));
        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> wrappedMusic(null).getLevelMusicReference(0, 0));
    }

    private static GameModule wrappedMusic(com.openggf.game.MusicReference music) {
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        GameModule delegate = mock(GameModule.class);
        when(delegate.getLevelMusicReference(0, 0)).thenReturn(music);
        return OwnerAwareStandaloneModule.wrap("owner", delegate, boundary, Map.of());
    }

    private interface DynamicService { void invoke(); }
}
