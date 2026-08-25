package com.openggf.audio;

import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCrossGameMusicOverridePolicies {

    @Test
    void sonic1UsesRetailOverrideAndFadeRules() {
        GameAudioProfile profile = new Sonic1AudioProfile();

        assertEquals(GameAudioProfile.MusicOverrideRetriggerPolicy.IGNORE,
                profile.getMusicOverrideRetriggerPolicy());
        assertEquals(GameAudioProfile.SystemCommandDuringOverridePolicy.APPLY,
                profile.getSystemCommandDuringOverridePolicy());
        assertTrue(profile.getSequencerConfig().blocksSfxDuringFadeOut());
    }

    @Test
    void sonic2UsesRetailOverrideAndFadeRules() {
        GameAudioProfile profile = new Sonic2AudioProfile();

        assertEquals(GameAudioProfile.MusicOverrideRetriggerPolicy.RESTART,
                profile.getMusicOverrideRetriggerPolicy());
        assertEquals(GameAudioProfile.SystemCommandDuringOverridePolicy.APPLY,
                profile.getSystemCommandDuringOverridePolicy());
        assertFalse(profile.getSequencerConfig().blocksSfxDuringFadeOut());
    }

    @Test
    void sonic3kUsesRetailOverrideAndFadeRules() {
        GameAudioProfile profile = new Sonic3kAudioProfile();

        assertEquals(GameAudioProfile.MusicOverrideRetriggerPolicy.IGNORE,
                profile.getMusicOverrideRetriggerPolicy());
        assertEquals(GameAudioProfile.SystemCommandDuringOverridePolicy.DISCARD,
                profile.getSystemCommandDuringOverridePolicy());
        assertFalse(profile.getSequencerConfig().blocksSfxDuringFadeOut());
    }
}
