package com.openggf.game.presentation;

import com.openggf.data.Game;
import com.openggf.data.Rom;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.RomDataSource;
import com.openggf.game.session.WorldSession;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Pattern;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestLoadedLevelSceneSourceCapability {
    @Test
    void romlessLoadedGameCannotEnterCartridgeSceneResidency() {
        LevelManager manager = mock(LevelManager.class);
        WorldSession world = mock(WorldSession.class);
        GameDataSource source = mock(GameDataSource.class);
        when(manager.getGame()).thenReturn(mock(Game.class));
        when(manager.getWorldSession()).thenReturn(world);
        when(world.getDataSource()).thenReturn(source);
        when(source.rom()).thenReturn(Optional.empty());
        GraphicsManager graphics = mock(GraphicsManager.class);

        assertThrows(IllegalStateException.class, () -> new LoadedLevelScene(manager, graphics));
        verify(manager, never()).getCurrentLevel();
        verifyNoInteractions(graphics);
    }

    @Test
    void cartridgeResidencyUsesTheLoadedWorldCapabilityWithoutAmbientServices() throws Exception {
        LevelManager manager = mock(LevelManager.class);
        WorldSession world = mock(WorldSession.class);
        when(manager.getGame()).thenReturn(mock(Game.class));
        when(manager.getWorldSession()).thenReturn(world);
        when(world.getDataSource()).thenReturn(new RomDataSource(mock(Rom.class), "rom:local-scene"));
        when(manager.getGameModule()).thenReturn(mock(GameModule.class));
        Level terrain = mock(Level.class);
        when(manager.getCurrentLevel()).thenReturn(terrain);
        when(terrain.getPatternCount()).thenReturn(1);
        when(terrain.getPattern(0)).thenReturn(new Pattern());

        try (var services = mockStatic(GameServices.class, invocation -> {
            throw new AssertionError("Scene residency touched ambient " + invocation.getMethod().getName());
        })) {
            assertNotNull(new LoadedLevelScene(manager, mock(GraphicsManager.class)));
            verify(terrain).getPattern(0);
        }
    }
}
