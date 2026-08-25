package com.openggf.game.sonic2.audio;

import com.openggf.audio.AudioManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TestSonic2AudioProfile {

    @Test
    void routesRetailFadeAndStopCommandIds() {
        Sonic2AudioProfile profile = new Sonic2AudioProfile();
        assertEquals(0xF9, Sonic2SmpsConstants.CMD_FADE_OUT);
        assertEquals(0xFD, Sonic2SmpsConstants.CMD_STOP_ALL);

        AudioManager fadeManager = mock(AudioManager.class);
        assertTrue(profile.handleSystemCommand(
                Sonic2SmpsConstants.CMD_FADE_OUT, fadeManager));
        verify(fadeManager).fadeOutMusic();

        AudioManager stopManager = mock(AudioManager.class);
        assertTrue(profile.handleSystemCommand(
                Sonic2SmpsConstants.CMD_STOP_ALL, stopManager));
        verify(stopManager).stopMusic();
    }
}
