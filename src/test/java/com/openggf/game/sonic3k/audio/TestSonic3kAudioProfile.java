package com.openggf.game.sonic3k.audio;

import com.openggf.audio.GameSound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSonic3kAudioProfile {

    @Test
    void mapsTailsFlightSoundsToNativeSfx() {
        Sonic3kAudioProfile profile = new Sonic3kAudioProfile();

        assertEquals(Sonic3kSfx.FLYING.id, profile.getSoundMap().get(GameSound.TAILS_FLYING));
        assertEquals(Sonic3kSfx.FLY_TIRED.id, profile.getSoundMap().get(GameSound.TAILS_FLY_TIRED));
    }
}
