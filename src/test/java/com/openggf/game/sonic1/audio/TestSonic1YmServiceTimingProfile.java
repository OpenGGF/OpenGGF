package com.openggf.game.sonic1.audio;

import com.openggf.audio.smps.YmServiceTimingProfile;
import com.openggf.audio.smps.YmSourceProgramTiming;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.Sonic3kYmServiceTimingProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestSonic1YmServiceTimingProfile {

    @Test
    void sourceProfileIsExactButRemainsDisabledUntilDriverIntegration() {
        assertSame(YmServiceTimingProfile.none(),
                Sonic1SmpsSequencerConfig.CONFIG.getYmServiceTimingProfile());
        assertSame(YmServiceTimingProfile.none(),
                Sonic2SmpsSequencerConfig.CONFIG.getYmServiceTimingProfile());
        assertSame(Sonic3kYmServiceTimingProfile.PROFILE,
                Sonic3kSmpsSequencerConfig.CONFIG.getYmServiceTimingProfile());

        assertEquals(4_096,
                Sonic1YmServiceTimingProfile.PROFILE.maximumWritesPerDriverService());
        assertEquals(YmServiceTimingProfile.TimingOwnership.EXCLUSIVE_SFX_FM5,
                Sonic1YmServiceTimingProfile.PROFILE.timingOwnership());
        assertEquals(30, Sonic1YmServiceTimingProfile.PROFILE.requireProgram(
                YmSourceProgramTiming.FirstPathShape.VOICE_NOTE, 0b1010).writes().size());
        assertEquals(31, Sonic1YmServiceTimingProfile.PROFILE.requireProgram(
                YmSourceProgramTiming.FirstPathShape.VOICE_PAN_NOTE, 0b1010).writes().size());
        assertThrows(IllegalArgumentException.class, () ->
                Sonic1YmServiceTimingProfile.PROFILE.requireProgram(
                        YmSourceProgramTiming.FirstPathShape.VOICE_NOTE, 0b1000));
    }
}
