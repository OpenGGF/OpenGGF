package com.openggf.tools;

import com.openggf.data.RomManager;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableCharacterRegistry;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestStandaloneHeadlessBoot {
    private EngineContext previous;

    @AfterEach void cleanup() {
        SessionManager.clear();
        if (previous != null) EngineServices.configure(previous);
    }

    @Test
    void joinsStandaloneSessionWithoutRomDetectionOrPatchResolution() throws Exception {
        previous = EngineServices.current();
        RomManager roms = mock(RomManager.class);
        when(roms.getRom()).thenThrow(new IOException("standalone has no ROM"));
        ModuleResolutionService resolver = mock(ModuleResolutionService.class);
        EngineContext services = new EngineContext(previous.configuration(), previous.graphics(),
                previous.audio(), roms, previous.profiler(), previous.debugOverlay(),
                previous.playbackDebug(), previous.romDetection(), previous.crossGameFeatures(),
                resolver);
        EngineServices.configure(services);
        GameModule module = mock(GameModule.class);
        when(module.getGameId()).thenReturn(GameId.STANDALONE);
        when(module.getIdentifier()).thenReturn("owner-game");
        when(module.getGameCode()).thenReturn("owner-game");
        when(module.getPlayableCharacterRegistry()).thenReturn(PlayableCharacterRegistry.empty());
        GameDataSource source = missingSource();

        var gameplay = HeadlessGameBoot.openStandaloneSessionForBoot(services, module, source);

        assertSame(module, gameplay.getWorldSession().rootGameModule());
        assertSame(module, gameplay.getWorldSession().resolvedGameModule());
        assertSame(source, gameplay.getWorldSession().getDataSource());
        assertTrue(source.rom().isEmpty());
        verifyNoInteractions(resolver);
        verify(roms, never()).isRomAvailable();

        var editor = SessionManager.enterEditorMode(new EditorCursorState(12, 34));
        assertSame(source, editor.getWorldSession().getDataSource());
        assertSame(source, SessionManager.resumeGameplayFromEditor()
                .getWorldSession().getDataSource());
        assertThrows(IOException.class, () -> GameServices.rom().getRom(),
                "GameServices.rom() keeps its missing-ROM checked exception semantics");
    }

    @Test
    void engineStandaloneBootSourceHasNoStockDetectionOrTitleRoutes() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/Engine.java"));
        int start = source.indexOf("void initializeStandaloneGame");
        int end = source.indexOf("GameModule resolveInitialModuleForLaunch", start);
        assertTrue(start >= 0 && end > start);
        String standaloneBoot = source.substring(start, end);

        assertFalse(standaloneBoot.contains("romManager"));
        assertFalse(standaloneBoot.contains("romDetectionService"));
        assertFalse(standaloneBoot.contains("moduleResolutionService"));
        assertFalse(standaloneBoot.contains("resolveInitialModuleForLaunch"));
        assertFalse(standaloneBoot.contains("showStartupRomError"));
        assertFalse(standaloneBoot.contains("enterConfiguredStartupMode"));
        assertTrue(standaloneBoot.contains("openStandaloneSession"));
    }

    @Test
    void standaloneReachableRomTouchesStayGuardedOrCapabilityGated() throws Exception {
        String playableArt = Files.readString(Path.of(
                "src/main/java/com/openggf/level/LevelPlayableArtInitializer.java"));
        assertTrue(playableArt.contains("superCtrl != null && !superCtrl.isRomDataPreLoaded()"));
        assertTrue(playableArt.contains("catch (Exception e)"));

        String parallax = Files.readString(Path.of(
                "src/main/java/com/openggf/level/ParallaxManager.java"));
        assertTrue(parallax.contains("getDataSource().rom().orElse(null)"));
        assertTrue(parallax.contains("if (rom != null)"));

        String lazyMappings = Files.readString(Path.of(
                "src/main/java/com/openggf/util/LazyMappingHolder.java"));
        assertTrue(lazyMappings.contains("catch (IOException | RuntimeException e)"));

        String objectServices = Files.readString(Path.of(
                "src/main/java/com/openggf/level/objects/DefaultObjectServices.java"));
        assertTrue(objectServices.contains("public Rom rom() throws IOException"));
        assertTrue(objectServices.contains("public RomByteReader romReader() throws IOException"));
    }

    private static GameDataSource missingSource() {
        return new GameDataSource() {
            @Override public Optional<com.openggf.data.Rom> rom() { return Optional.empty(); }
            @Override public java.io.InputStream openAsset(String normalizedPath) {
                return java.io.InputStream.nullInputStream();
            }
            @Override public String identity() { return "mod:owner-game:test"; }
        };
    }
}
