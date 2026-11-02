package com.openggf.game.sonic3k.objects;

import com.openggf.audio.AudioManager;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSongFadeRestoreMusic {
    @Test
    void restoreSelectsApparentActAtExpiryAfterSignedWaitUnderflow() {
        Harness services = new Harness();
        SongFadeTransitionInstance fade = SongFadeTransitionInstance.toLevelMusic(Sonic3kMusic.AIZ2.id);
        fade.setServices(services);
        for (int i = 0; i < 120; i++) fade.update(i, null);
        assertTrue(services.played.isEmpty());
        services.apparentMusic = Sonic3kMusic.AIZ1.id;
        fade.update(120, null);
        assertEquals(List.of(Sonic3kMusic.AIZ1.id), services.played);
        assertTrue(fade.isDestroyed());
        verify(services.audio, times(1)).fadeOutMusic();
    }

    @Test
    void nativeRestoreRechecksP1InvincibilityAtExpiry() {
        Harness services = new Harness();
        SongFadeTransitionInstance fade = SongFadeTransitionInstance.createNativeLevelMusicFade(Sonic3kMusic.MHZ2.id);
        fade.setServices(services);
        for (int i = 0; i < 120; i++) fade.update(i, null);
        services.player.setInvincibleFrames(60);
        fade.update(120, null);
        assertEquals(List.of(Sonic3kMusic.INVINCIBILITY.id), services.played);
        verify(services.audio, times(1)).fadeOutMusic(0x28, 6);
    }

    @Test
    void expiredInvincibilityDoesNotOverrideRestoreAndFixedTransitionsStayFixed() {
        Harness services = new Harness();
        services.player.setInvincibleFrames(60);
        SongFadeTransitionInstance restore = SongFadeTransitionInstance.toCurrentLevelMusic();
        restore.setServices(services);
        for (int i = 0; i < 120; i++) restore.update(i, null);
        services.player.setInvincibleFrames(0);
        restore.update(120, null);
        assertEquals(List.of(Sonic3kMusic.AIZ2.id), services.played);
        services.player.setInvincibleFrames(60);
        SongFadeTransitionInstance fixed = SongFadeTransitionInstance.transitionTo(Sonic3kMusic.KNUCKLES.id);
        fixed.setServices(services);
        for (int i = 0; i < 90; i++) fixed.update(i, null);
        assertEquals(1, services.played.size());
        fixed.update(90, null);
        assertEquals(List.of(Sonic3kMusic.AIZ2.id, Sonic3kMusic.KNUCKLES.id), services.played);
    }

    @Test
    void poweredFormProjectsNativeInvincibilityBitWithoutTimer() {
        var player = mock(com.openggf.sprites.playable.AbstractPlayableSprite.class);
        when(player.isSuperSonic()).thenReturn(true);
        assertEquals(Sonic3kMusic.INVINCIBILITY.id,
                com.openggf.game.sonic3k.audio.S3kLevelMusicRestore.resolve(Sonic3kMusic.AIZ1.id, player));
        when(player.isSuperSonic()).thenReturn(false);
        assertEquals(Sonic3kMusic.AIZ1.id,
                com.openggf.game.sonic3k.audio.S3kLevelMusicRestore.resolve(Sonic3kMusic.AIZ1.id, player));
    }

    private static final class Harness extends TestObjectServices {
        final List<Integer> played = new ArrayList<>();
        final AudioManager audio = mock(AudioManager.class);
        final TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        int apparentMusic = Sonic3kMusic.AIZ2.id;
        Harness() { withAudioManager(audio); }
        @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> player, List::of); }
        @Override public int getApparentLevelMusicId() { return apparentMusic; }
        @Override public int getCurrentLevelMusicId() { return Sonic3kMusic.MHZ2.id; }
        @Override public void playMusic(int musicId) { played.add(musicId); }
    }
}
