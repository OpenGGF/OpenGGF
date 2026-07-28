package com.openggf.sprites.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.animation.AnimatedTileChannelGraph;
import com.openggf.game.mutation.ZoneLayoutMutationPipeline;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.render.AdvancedRenderModeController;
import com.openggf.game.render.SpecialRenderEffectRegistry;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.timer.TimerManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class TestSpriteManagerDebugEmeraldGrant {
    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
        AudioManager.getInstance().setAudioProfile(new Sonic2AudioProfile());

        GameplayModeContext mode = TestEnvironment.activeGameplayMode();
        SpriteManager spriteManager = new SpriteManager();
        GameStateManager gameStateManager = new GameStateManager();
        mode.attachGameplayManagers(
                new Camera(),
                new TimerManager(),
                gameStateManager,
                new FadeManager(),
                new GameRng(GameRng.Flavour.S1_S2),
                new DefaultSolidExecutionRegistry(),
                null,
                AudioManager.getInstance());
        TerrainCollisionManager terrain = mock(TerrainCollisionManager.class);
        mode.attachLevelManagers(
                new WaterSystem(),
                new ParallaxManager(),
                terrain,
                new CollisionSystem(terrain),
                spriteManager,
                mock(LevelManager.class));
        mode.attachSharedRegistries(
                new ZoneRuntimeRegistry(),
                new PaletteOwnershipRegistry(),
                new AnimatedTileChannelGraph(),
                new SpecialRenderEffectRegistry(),
                new AdvancedRenderModeController(),
                new ZoneLayoutMutationPipeline());
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        AudioManager.getInstance().resetState();
    }

    @Test
    void giveEmeraldsDebugKeyPlaysEmeraldChimeWhenEmeraldsAreGranted() {
        EngineServices.current().configuration().setConfigValue(SonicConfiguration.DEBUG_VIEW_ENABLED, true);
        InputHandler input = new InputHandler();
        int giveEmeraldsKey = EngineServices.current().configuration().getInt(SonicConfiguration.GIVE_EMERALDS_KEY);
        input.handleKeyEvent(giveEmeraldsKey, GLFW.GLFW_PRESS);

        GameServices.sprites().update(input);

        assertEquals(7, GameServices.gameState().getEmeraldCount());
        var commands = musicCommands();
        assertEquals(1, commands.size());
        assertEquals(Sonic2Music.GOT_EMERALD.id, commands.getFirst().musicId());
    }

    @Test
    void giveEmeraldsDebugKeyIsIgnoredWhenDebugViewIsDisabled() {
        EngineServices.current().configuration().setConfigValue(SonicConfiguration.DEBUG_VIEW_ENABLED, false);
        InputHandler input = new InputHandler();
        int giveEmeraldsKey = EngineServices.current().configuration().getInt(SonicConfiguration.GIVE_EMERALDS_KEY);
        input.handleKeyEvent(giveEmeraldsKey, GLFW.GLFW_PRESS);

        GameServices.sprites().update(input);

        assertEquals(0, GameServices.gameState().getEmeraldCount());
        assertEquals(0, musicCommands().size());
    }

    private static java.util.List<AudioCommand.PlayMusic> musicCommands() {
        return AudioManager.getInstance().commandTimeline().entries().stream()
                .map(entry -> entry.command())
                .filter(AudioCommand.PlayMusic.class::isInstance)
                .map(AudioCommand.PlayMusic.class::cast)
                .toList();
    }
}
