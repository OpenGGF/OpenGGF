package com.openggf.level;

import com.openggf.game.Game;
import com.openggf.game.ZoneKey;
import com.openggf.game.ZoneRegistry;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModFaultBoundary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TestLevelMusicCoordinator {
    private static final int MOD_LEVEL_INDEX = 0x400;

    @Test
    void additiveMusicUsesRegistryIdentityForEntryAndCurrentTrack() throws Exception {
        Game game = mock(Game.class);
        ZoneRegistry registry = modRegistry(0x23);
        LevelDescriptor selected = registry.getLevelDataForZone(1).getFirst();
        var transitions = new LevelTransitionCoordinator();

        assertEquals(OptionalInt.of(0x23),
                LevelMusicCoordinator.prepare(game, registry, transitions, MOD_LEVEL_INDEX));
        assertEquals(OptionalInt.of(0x23),
                LevelMusicCoordinator.prepareCurrent(game, registry, transitions, selected));
        assertEquals(0x23, LevelMusicCoordinator.currentMusicId(game, registry,
                List.of(List.of(LevelData.S3K_ANGEL_ISLAND_1), List.of(selected)),
                1, 0, Logger.getAnonymousLogger()));
        verifyNoInteractions(game);
    }

    @Test
    void namespacedTrackStillProducesAPublicationRequestWithoutAStockMusicId() throws Exception {
        Game game = mock(Game.class);
        assertEquals(OptionalInt.of(-1), LevelMusicCoordinator.prepare(
                game, modRegistry(-1), new LevelTransitionCoordinator(), MOD_LEVEL_INDEX));
        verifyNoInteractions(game);
    }

    @Test
    void stockMusicKeepsGameLookupAndSuppressionRemainsOneShot() throws Exception {
        Game game = mock(Game.class);
        int stockIndex = LevelData.S3K_ANGEL_ISLAND_1.levelIndex();
        when(game.getMusicId(stockIndex)).thenReturn(0x15);
        ZoneRegistry registry = modRegistry(0x23);
        var transitions = new LevelTransitionCoordinator();
        transitions.setSuppressNextMusicChange(true);

        assertEquals(OptionalInt.empty(),
                LevelMusicCoordinator.prepare(game, registry, transitions, MOD_LEVEL_INDEX));
        verifyNoInteractions(game);
        assertEquals(OptionalInt.of(0x15),
                LevelMusicCoordinator.prepare(game, registry, transitions, stockIndex));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void creatorAbortEscapesMusicLookupWithItsOriginalIdentity(boolean deferred) throws Exception {
        var boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        var aborted = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> boundary.run("owner", () -> { throw new IllegalStateException("music callback failed"); }));
        Exception failure = deferred ? new DeferredLevelLoadException(aborted) : aborted;
        Game game = mock(Game.class);
        int stockIndex = LevelData.S3K_ANGEL_ISLAND_1.levelIndex();
        when(game.getMusicId(stockIndex)).thenThrow(failure);

        assertSame(aborted, assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> LevelMusicCoordinator.currentMusicId(game, null,
                        List.of(List.of(LevelData.S3K_ANGEL_ISLAND_1)),
                        0, 0, Logger.getAnonymousLogger())));
    }

    @Test
    void ordinaryMusicLookupFailureStillReturnsNoTrack() throws Exception {
        Game game = mock(Game.class);
        int stockIndex = LevelData.S3K_ANGEL_ISLAND_1.levelIndex();
        when(game.getMusicId(stockIndex)).thenThrow(new IOException("music unavailable"));

        assertEquals(-1, LevelMusicCoordinator.currentMusicId(game, null,
                List.of(List.of(LevelData.S3K_ANGEL_ISLAND_1)),
                0, 0, Logger.getAnonymousLogger()));
    }

    private static ZoneRegistry modRegistry(int musicId) {
        ZoneRegistry registry = mock(ZoneRegistry.class);
        when(registry.getZoneCount()).thenReturn(2);
        when(registry.zoneKey(0)).thenReturn(ZoneKey.stock(0));
        when(registry.zoneKey(1)).thenReturn(new ZoneKey.Mod("sample", "sky"));
        LevelDescriptor descriptor = mock(LevelDescriptor.class);
        when(descriptor.levelIndex()).thenReturn(MOD_LEVEL_INDEX);
        when(registry.getLevelDataForZone(1)).thenReturn(List.of(descriptor));
        when(registry.getMusicId(1, 0)).thenReturn(musicId);
        return registry;
    }
}
