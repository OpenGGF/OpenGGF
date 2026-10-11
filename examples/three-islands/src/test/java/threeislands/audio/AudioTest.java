package threeislands.audio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.openggf.mods.scene.SceneAudio;
import com.openggf.mods.scene.SceneContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AudioTest {
    private final SceneContext ctx = mock(SceneContext.class);
    private final SceneAudio driver = mock(SceneAudio.class);
    private Audio audio;

    @BeforeEach void setup() {
        when(ctx.audio()).thenReturn(driver);
        when(driver.playMusic(anyString(), anyInt())).thenReturn(true);
        audio = new Audio(ctx);
    }
    @Test void repeatedAreaUpdatesNeverRestartTheNativeLoop() {
        for (int i = 0; i < 10000; i++) audio.music("s1", 0x84, 0x20);
        verify(driver, times(1)).playMusic("s1", 0x84);
        verify(driver, never()).playMusic(0x20);
        assertTrue(audio.playerActive());
        verify(ctx, never()).music();
    }
    @Test void dungeonBattleAndReturnHaveOnlyTheirRequestedCues() {
        audio.music("s1", 0x81, 0x20);
        audio.music("s1", 0x83, 0x20);
        audio.music("s3k", Audio.MUS_BOSS);
        audio.jingle(Audio.MUS_ACT_CLEAR);
        audio.music("s1", 0x83, 0x20);
        var order = inOrder(driver);
        order.verify(driver).playMusic("s1", 0x81);
        order.verify(driver).playMusic("s1", 0x83);
        order.verify(driver).playMusic(Audio.MUS_BOSS);
        order.verify(driver).playMusic(Audio.MUS_ACT_CLEAR);
        order.verify(driver).playMusic("s1", 0x83);
        order.verifyNoMoreInteractions();
    }
    @Test void unsupportedHostGetsAStableFallbackWithoutRepeatedRequests() {
        when(driver.playMusic("s1", 0x84)).thenReturn(false);
        for (int i = 0; i < 120; i++) audio.music("s1", 0x84, 0x20);
        verify(driver).playMusic("s1", 0x84);
        verify(driver).playMusic(0x20);
        assertTrue(audio.failed());
    }
    @Test void failedTrackCanBeReplacedAndSceneCanClose() {
        when(driver.playMusic("s1", 0x84)).thenThrow(new IllegalArgumentException("unavailable"));
        assertDoesNotThrow(() -> audio.music("s1", 0x84, 0x20));
        assertTrue(audio.failed());
        audio.music("s2", 0x81, 0x21);
        assertFalse(audio.failed());
        audio.close();
        verify(driver).stopMusic();
        assertFalse(audio.playerActive());
    }
    @Test void fadeAllowsTheSameTrackToBeRequestedAgain() {
        audio.music("s1", 0x84);
        audio.fadeOut();
        audio.music("s1", 0x84);
        verify(driver).fadeOutMusic();
        verify(driver, times(2)).playMusic("s1", 0x84);
    }
}
