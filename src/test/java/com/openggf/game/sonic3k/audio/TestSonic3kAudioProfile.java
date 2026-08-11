package com.openggf.game.sonic3k.audio;

import com.openggf.audio.GameSound;
import com.openggf.audio.AudioManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TestSonic3kAudioProfile {

    @Test
    void mapsTailsFlightSoundsToNativeSfx() {
        Sonic3kAudioProfile profile = new Sonic3kAudioProfile();

        assertEquals(Sonic3kSfx.FLYING.id, profile.getSoundMap().get(GameSound.TAILS_FLYING));
        assertEquals(Sonic3kSfx.FLY_TIRED.id, profile.getSoundMap().get(GameSound.TAILS_FLY_TIRED));
    }

    @Test
    void engineApiCommandsRemainInterpretedBeforeTheNativeZ80CommandMap() {
        Sonic3kAudioProfile profile = new Sonic3kAudioProfile();
        AudioManager manager = mock(AudioManager.class);

        assertTrue(profile.handleSystemCommand(Sonic3kSmpsConstants.CMD_FADE_OUT, manager));
        verify(manager).fadeOutMusic(0x28, 6);

        AudioManager nativeCommandManager = mock(AudioManager.class);
        assertFalse(profile.handleSystemCommand(0xe1, nativeCommandManager));
        verifyNoInteractions(nativeCommandManager);
    }
}
