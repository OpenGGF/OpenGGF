package com.openggf.sprites.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.openggf.audio.AudioBackend;
import com.openggf.audio.AudioManager;
import com.openggf.audio.ChannelType;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.rewind.AudioBackendLogicalSnapshot;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
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
    private RecordingAudioBackend audioBackend;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        AudioManager.getInstance().resetState();
        audioBackend = new RecordingAudioBackend();
        AudioManager.getInstance().setBackend(audioBackend);
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
        mode.attachLevelManagers(
                new WaterSystem(),
                new ParallaxManager(),
                mock(TerrainCollisionManager.class),
                mock(CollisionSystem.class),
                spriteManager,
                mock(LevelManager.class));
        mode.attachSharedRegistries(
                new ZoneRuntimeRegistry(),
                new PaletteOwnershipRegistry(),
                new AnimatedTileChannelGraph(),
                new SpecialRenderEffectRegistry(),
                new AdvancedRenderModeController(),
                mock(ZoneLayoutMutationPipeline.class));
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        AudioManager.getInstance().resetState();
    }

    @Test
    void giveEmeraldsDebugKeyPlaysEmeraldChimeWhenEmeraldsAreGranted() {
        InputHandler input = new InputHandler();
        int giveEmeraldsKey = EngineServices.current().configuration().getInt(SonicConfiguration.GIVE_EMERALDS_KEY);
        input.handleKeyEvent(giveEmeraldsKey, GLFW.GLFW_PRESS);

        GameServices.sprites().update(input);

        assertEquals(7, GameServices.gameState().getEmeraldCount());
        assertEquals(Sonic2Music.GOT_EMERALD.id, audioBackend.lastMusicId);
        assertEquals(1, audioBackend.musicPlayCount);
    }

    private static final class RecordingAudioBackend implements AudioBackend {
        private int lastMusicId = -1;
        private int musicPlayCount;

        @Override
        public void init() {
        }

        @Override
        public void setAudioProfile(GameAudioProfile profile) {
        }

        @Override
        public void playMusic(int musicId) {
            lastMusicId = musicId;
            musicPlayCount++;
        }

        @Override
        public void playSmps(AbstractSmpsData data, DacData dacData) {
        }

        @Override
        public void playSfxSmps(AbstractSmpsData data, DacData dacData) {
        }

        @Override
        public void playSfxSmps(AbstractSmpsData data, DacData dacData, float pitch) {
        }

        @Override
        public void playSfx(String sfxName) {
        }

        @Override
        public void playSfx(String sfxName, float pitch) {
        }

        @Override
        public void stopPlayback() {
        }

        @Override
        public void stopAllSfx() {
        }

        @Override
        public void fadeOutMusic(int steps, int delay) {
        }

        @Override
        public void toggleMute(ChannelType type, int channel) {
        }

        @Override
        public void toggleSolo(ChannelType type, int channel) {
        }

        @Override
        public boolean isMuted(ChannelType type, int channel) {
            return false;
        }

        @Override
        public boolean isSoloed(ChannelType type, int channel) {
            return false;
        }

        @Override
        public void setSpeedShoes(boolean enabled) {
        }

        @Override
        public void restoreMusic() {
        }

        @Override
        public void endMusicOverride(int musicId) {
        }

        @Override
        public void update() {
        }

        @Override
        public void destroy() {
        }

        @Override
        public void pause() {
        }

        @Override
        public void resume() {
        }

        @Override
        public AudioBackendLogicalSnapshot captureLogicalSnapshot() {
            return AudioBackendLogicalSnapshot.empty();
        }
    }
}
