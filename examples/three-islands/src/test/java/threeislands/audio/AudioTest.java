package threeislands.audio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.openggf.mods.scene.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Audible transition regressions, including asynchronous completion during driver cues. */
class AudioTest {
    private final SceneContext ctx = mock(SceneContext.class);
    private final SceneAudio driver = mock(SceneAudio.class);
    private final SceneMusic music = mock(SceneMusic.class);
    private final SceneMusicPreparation preparation = mock(SceneMusicPreparation.class);
    private final SceneMusicPreparation part = mock(SceneMusicPreparation.class);
    private final ScenePreparedMusic song = mock(ScenePreparedMusic.class);
    private final SceneMusicPlayer player = mock(SceneMusicPlayer.class);
    private Audio audio;

    @BeforeEach
    void setup() {
        when(ctx.audio()).thenReturn(driver);
        when(ctx.music()).thenReturn(music);
        when(music.prepareAsync(anyString(), anyInt(), anyInt())).thenReturn(preparation);
        when(preparation.state()).thenReturn(SceneMusicPreparation.State.PREPARING);
        when(preparation.prepared()).thenReturn(song);
        when(music.preparePartAsync(eq(song), anyList())).thenReturn(part);
        when(part.state()).thenReturn(SceneMusicPreparation.State.PREPARING);
        when(music.start(eq(song), anyList(), eq(0))).thenReturn(player);
        audio = new Audio(ctx);
    }

    private void area() { audio.music("s1", 0x81, 0x20); }
    private void finishPreparation() {
        when(preparation.state()).thenReturn(SceneMusicPreparation.State.READY);
        when(part.state()).thenReturn(SceneMusicPreparation.State.READY);
        audio.tick();
    }

    @Test
    void firstArrivalHasNoUnrelatedPlaceholderAndRepeatedAreaRequestsDoNotRestart() {
        audio.music("s3k", Audio.MUS_TITLE);
        clearInvocations(driver);
        area();
        audio.tick();
        verify(driver, never()).playMusic(anyInt());
        assertFalse(audio.playerActive());
        finishPreparation();
        for (int i = 0; i < 120; i++) { area(); audio.tick(); }
        verify(music, times(1)).prepareAsync("s1", 0x81, 3600);
        verify(music, times(1)).start(eq(song), anyList(), eq(0));
        assertEquals("s1", audio.playingGame());
    }

    @Test
    void battleAndVictoryReturnImmediatelyToPreparedAreaWithoutFallbackOrRendering() {
        area(); finishPreparation();
        audio.music("s3k", Audio.MUS_MINIBOSS);
        verify(player).stop();
        audio.jingle(Audio.MUS_ACT_CLEAR);
        area();
        assertTrue(audio.playerActive());
        verify(music, times(1)).prepareAsync(anyString(), anyInt(), anyInt());
        verify(music, times(1)).preparePartAsync(eq(song), anyList());
        verify(music, times(2)).start(eq(song), anyList(), eq(0));
        verify(driver, never()).playMusic(0x20);
    }

    @Test
    void pendingSynthesisFinishesDuringBattleWithoutStealingItsMusic() {
        area();
        audio.music("s3k", Audio.MUS_BOSS);
        finishPreparation();
        verify(preparation, never()).cancel();
        verify(music, never()).start(eq(song), anyList(), eq(0));
        assertEquals(Audio.MUS_BOSS, audio.playingId());
        area();
        assertTrue(audio.playerActive());
    }

    @Test
    void pendingPartSurvivesJingleAndReturnsWithoutAnotherJob() {
        area();
        when(preparation.state()).thenReturn(SceneMusicPreparation.State.READY);
        audio.tick();
        audio.jingle(Audio.MUS_EMERALD);
        when(part.state()).thenReturn(SceneMusicPreparation.State.READY);
        audio.tick();
        assertEquals(Audio.MUS_EMERALD, audio.playingId());
        area();
        verify(part, never()).cancel();
        verify(music, times(1)).preparePartAsync(eq(song), anyList());
        assertTrue(audio.playerActive());
    }

    @Test
    void changingAreasCancelsOldPreparationAndCannotStartItLater() {
        area();
        SceneMusicPreparation next = mock(SceneMusicPreparation.class);
        when(next.state()).thenReturn(SceneMusicPreparation.State.PREPARING);
        when(music.prepareAsync("s2", 0x81, 3600)).thenReturn(next);
        audio.music("s2", 0x81, 0x21);
        verify(preparation).cancel();
        finishPreparation();
        assertFalse(audio.playerActive());
        verify(music, never()).start(eq(song), anyList(), eq(0));
    }

    @Test
    void failedPreparationUsesStableFallbackOnlyAfterFailure() {
        area();
        when(preparation.state()).thenReturn(SceneMusicPreparation.State.FAILED);
        audio.tick();
        for (int i = 0; i < 120; i++) { area(); audio.tick(); }
        assertTrue(audio.failed());
        verify(driver, times(1)).playMusic(0x20);
        verify(music, times(1)).prepareAsync(anyString(), anyInt(), anyInt());
    }

    @Test
    void failureDuringBattleDoesNotReplaceTheBattleCue() {
        area();
        audio.music("s3k", Audio.MUS_BOSS);
        when(preparation.state()).thenReturn(SceneMusicPreparation.State.FAILED);
        audio.tick();
        assertEquals(Audio.MUS_BOSS, audio.playingId());
        verify(driver, never()).playMusic(0x20);
        area();
        verify(driver).playMusic(0x20);
    }

    @Test
    void songLoopAndFadeDoNotReprepareOrPlayPlaceholder() {
        area(); finishPreparation();
        when(player.finished()).thenReturn(true);
        audio.tick();
        when(player.finished()).thenReturn(false);
        audio.fadeOut();
        audio.tick();
        assertFalse(audio.playerActive());
        area();
        verify(music, times(1)).prepareAsync(anyString(), anyInt(), anyInt());
        verify(music, times(3)).start(eq(song), anyList(), eq(0));
        verify(driver, never()).playMusic(0x20);
    }

    @Test
    void unavailableCachedPlaybackFallsBackWithoutFaultingTheScene() {
        area(); finishPreparation();
        audio.music("s3k", Audio.MUS_BOSS);
        when(music.start(eq(song), anyList(), eq(0))).thenThrow(new IllegalStateException("unavailable"));
        assertDoesNotThrow(this::area);
        assertTrue(audio.failed());
        verify(driver).playMusic(0x20);
    }

    @Test
    void closeCancelsPendingWork() {
        area();
        audio.close();
        verify(preparation).cancel();
        finishPreparation();
        verify(music, never()).start(eq(song), anyList(), eq(0));
    }
}
