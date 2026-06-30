package com.openggf.sprites.playable;

import com.openggf.tests.TestEnvironment;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.EngineServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.openggf.audio.AudioBackend;
import com.openggf.audio.AudioManager;
import com.openggf.audio.ChannelType;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.game.GameRng;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic1.audio.Sonic1Music;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.game.session.EngineContext;
import com.openggf.game.GameServices;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestDrowningControllerMusicSelection {

    @AfterEach
    void tearDown() {
        AudioManager audioManager = AudioManager.getInstance();
        audioManager.setBackend(new NullAudioBackend());
        audioManager.resetState();
        SessionManager.clear();
    }

    static Stream<Arguments> drowningMusicProvider() {
        return Stream.of(
                Arguments.of(new Sonic1AudioProfile(), Sonic1Music.DROWNING.id, "Sonic 1"),
                Arguments.of(new Sonic2AudioProfile(), Sonic2Music.UNDERWATER.id, "Sonic 2"),
                Arguments.of(new Sonic3kAudioProfile(), Sonic3kMusic.DROWNING.id, "Sonic 3K")
        );
    }

    @ParameterizedTest(name = "{2} drowning music")
    @MethodSource("drowningMusicProvider")
    void drowningMusicMatchesProfile(GameAudioProfile profile, int expectedMusicId, String label) {
        AudioManager audioManager = AudioManager.getInstance();
        CapturingBackend backend = new CapturingBackend();
        audioManager.setBackend(backend);
        audioManager.setAudioProfile(profile);
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
        LevelManager levelManager = GameServices.level();
        levelManager.resetState();

        DrowningController controller = new DrowningController(new Sonic("test", (short) 0, (short) 0));

        // Air starts at 30 and drowning music triggers when air event runs at exactly 12.
        // That is 19 air events: 30..12 inclusive.
        int updatesToTriggerMusic = (30 - 12 + 1) * 60;
        for (int i = 0; i < updatesToTriggerMusic; i++) {
            controller.update();
        }

        assertEquals(1, backend.musicPlayCalls,
                "Drowning music should be triggered exactly once");
        assertEquals(expectedMusicId, backend.lastMusicId,
                "Incorrect drowning music ID selected");
        assertTrue(controller.isDrowningMusicPlaying(),
                "Controller should flag drowning music as active");
    }

    @Test
    void s3kFixedCountdownAirEventTriggersDrowningMusicWithoutGenericBubbleUpdate() {
        AudioManager audioManager = AudioManager.getInstance();
        CapturingBackend backend = new CapturingBackend();
        audioManager.setBackend(backend);
        audioManager.setAudioProfile(new Sonic3kAudioProfile());
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
        GameServices.level().resetState();

        DrowningController controller = new DrowningController(new Sonic("test", (short) 0, (short) 0));

        for (int i = 0; i < 19; i++) {
            controller.performFixedCountdownAirEvent(true);
        }

        assertEquals(1, backend.musicPlayCalls,
                "fixed Obj_AirCountdown should still trigger drowning music at air_left=12");
        assertEquals(Sonic3kMusic.DROWNING.id, backend.lastMusicId);
        assertTrue(controller.isDrowningMusicPlaying());
    }

    @Test
    void genericCountdownProcessesAirEventBeforePendingBubbleTimer() throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
        GameServices.level().resetState();

        long seed = 0x13579BDFL;
        GameRng rng = GameServices.rng();
        rng.setSeed(seed);

        DrowningController controller = new DrowningController(new Sonic("test", (short) 0, (short) 0));
        setPrivateInt(controller, "frameTimer", 1);
        setPrivateInt(controller, "bubbleFlags", 1);
        setPrivateInt(controller, "bubblesRemainingInBurst", 1);
        setPrivateInt(controller, "nextBubbleTimer", 0);

        boolean drowned = controller.update();

        GameRng expected = new GameRng(rng.flavour(), seed);
        expected.nextBits(1);      // Obj0A_Countdown: choose one- or two-bubble burst.
        expected.nextBits(0x0F);   // Obj0A_MakeBubbleNow: seed the new mouth-bubble timer.
        assertFalse(drowned);
        assertEquals(29, getPrivateInt(controller, "remainingAir"));
        assertEquals(expected.getSeed(), rng.getSeed(),
                "same-frame air events must not consume the stale pending mouth-bubble RNG first");
    }

    @Test
    void s2CountdownResetStartsFromRomSidecarZeroTimer() throws Exception {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
        GameServices.level().resetState();

        Sonic sonic = new Sonic("test", (short) 0, (short) 0);
        sonic.setPhysicsFeatureSet(PhysicsFeatureSet.SONIC_2);
        DrowningController controller = new DrowningController(sonic);
        controller.reset();

        assertEquals(0, getPrivateInt(controller, "frameTimer"));
        assertFalse(controller.update());
        assertEquals(29, getPrivateInt(controller, "remainingAir"),
                "S2 Obj0A_Countdown starts from zero and runs its first air event immediately");
        assertEquals(60, getPrivateInt(controller, "frameTimer"));
    }

    @Test
    void s3kGenericCountdownFallbackKeepsFullSecondReset() throws Exception {
        Sonic sonic = new Sonic("test", (short) 0, (short) 0);
        sonic.setPhysicsFeatureSet(PhysicsFeatureSet.SONIC_3K);
        DrowningController controller = new DrowningController(sonic);
        controller.reset();

        assertEquals(60, getPrivateInt(controller, "frameTimer"));
        assertFalse(controller.update());
        assertEquals(30, getPrivateInt(controller, "remainingAir"));
        assertEquals(59, getPrivateInt(controller, "frameTimer"));
    }

    @Test
    void bubbleArtResolutionUsesRendererPresenceBeforeGpuCacheReadiness() throws Exception {
        AudioManager audioManager = AudioManager.getInstance();
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.currentAudioManager()).thenReturn(audioManager);
        DrowningController controller = new DrowningController(player);

        LevelManager levelManager = mock(LevelManager.class);
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(levelManager.getObjectRenderManager()).thenReturn(renderManager);
        when(renderManager.getRenderer(ObjectArtKeys.LZ_BUBBLES)).thenReturn(null);
        when(renderManager.getRenderer(ObjectArtKeys.BUBBLES)).thenReturn(renderer);
        when(renderer.isReady()).thenReturn(false);

        Method method = DrowningController.class.getDeclaredMethod("resolveBubbleConfig", LevelManager.class);
        method.setAccessible(true);
        method.invoke(controller, levelManager);

        assertEquals(ObjectArtKeys.BUBBLES, getPrivateString(controller, "bubbleArtKey"),
                "Obj0A allocation should not depend on GPU pattern-cache readiness");
        verify(renderer, never()).isReady();
    }

    private static void setPrivateInt(DrowningController controller, String fieldName, int value) throws Exception {
        Field field = DrowningController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(controller, value);
    }

    private static int getPrivateInt(DrowningController controller, String fieldName) throws Exception {
        Field field = DrowningController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(controller);
    }

    private static String getPrivateString(DrowningController controller, String fieldName) throws Exception {
        Field field = DrowningController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(controller);
    }

    private static final class CapturingBackend implements AudioBackend {
        int musicPlayCalls = 0;
        int lastMusicId = -1;

        @Override
        public void init() {
        }

        @Override
        public void setAudioProfile(GameAudioProfile profile) {
        }

        @Override
        public void playMusic(int musicId) {
            musicPlayCalls++;
            lastMusicId = musicId;
        }

        @Override
        public void playSmps(AbstractSmpsData data, DacData dacData) {
            musicPlayCalls++;
            if (data != null) {
                lastMusicId = data.getId();
            }
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
        public void setSpeedMultiplier(int multiplier) {
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
    }
}
