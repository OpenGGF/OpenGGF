package com.openggf.game.sonic3k.audio;

import com.openggf.audio.GameSound;
import com.openggf.audio.GameMusic;
import com.openggf.audio.AudioManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TestSonic3kAudioProfile {

    @Test
    void originalS3MusicPoliciesUseTheSameNamespaceAsTheCartridgeLoader() {
        var profile = new Sonic3kAudioProfile(Sonic3kAudioResourceProfile.SONIC_3);
        assertEquals(0x12A, profile.getExtraLifeMusicId());
        assertEquals(0x12C, profile.getInvincibilityMusicId());
        assertEquals(0x131, profile.getDrowningMusicId());
        assertEquals(0x129, profile.getMusicMap().get(GameMusic.ACT_CLEAR));
        assertEquals(0x12C, profile.getSuperSonicMusicId());
        assertEquals(0x118, profile.resolveAirResetMusic(0x101, true, true, true));
        assertEquals(0x12C, profile.resolveAirResetMusic(0x101, true, false, false));
        assertEquals(0x101, profile.resolveAirResetMusic(0x101, false, false, false));
        assertTrue(profile.permitsTemporaryMusic(0x101));
        assertEquals(0x125, profile.musicForNativeRequest(0x25));
        assertEquals(-1, profile.musicForNativeRequest(0x33));
        assertEquals(-1, profile.musicForNativeRequest(0xDC));
        assertEquals(0x33, profile.getSoundMap().get(GameSound.RING_RIGHT));
    }

    @Test
    void mapsTailsFlightSoundsToNativeSfx() {
        Sonic3kAudioProfile profile = new Sonic3kAudioProfile();

        assertEquals(Sonic3kSfx.FLYING.id, profile.getSoundMap().get(GameSound.TAILS_FLYING));
        assertEquals(Sonic3kSfx.FLY_TIRED.id, profile.getSoundMap().get(GameSound.TAILS_FLY_TIRED));
    }

    @Test
    void nativeZ80CommandMapUsesShippedIdentities() {
        Sonic3kAudioProfile profile = new Sonic3kAudioProfile();
        AudioManager manager = mock(AudioManager.class);

        assertTrue(profile.handleSystemCommand(Sonic3kSmpsConstants.CMD_FADE_OUT, manager));
        verify(manager).fadeOutMusic(0x28, 6);

        AudioManager globalStop = mock(AudioManager.class);
        assertTrue(profile.handleSystemCommand(
                Sonic3kSmpsConstants.CMD_STOP, globalStop));
        verify(globalStop).retainGlobalStop(
                Sonic3kSmpsConstants.CMD_STOP);

        AudioManager stopSfx = mock(AudioManager.class);
        assertTrue(profile.handleSystemCommand(
                Sonic3kSmpsConstants.CMD_STOP_SFX, stopSfx));
        verify(stopSfx).stopSmpsSfx(
                Sonic3kSmpsConstants.CMD_STOP_SFX);
    }
}
