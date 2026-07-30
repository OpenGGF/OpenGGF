package com.openggf.audio;

import com.openggf.audio.presentation.AudioPresentationSnapshot;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Override music (1-up jingle, invincibility, Super) interrupts the zone music
 * rather than replacing it, and the interrupted song must come back.
 *
 * <p>The claims are made against the presentation registry the mixer actually
 * renders: {@code activeMusic} is the voice being heard and
 * {@code overrideStack} holds the interrupted songs. A regression that destroys
 * the zone music instead of pushing it shows up here as a never-restored
 * {@code activeMusic}, which is what it sounds like in game once the jingle's
 * own voice completes — silence for the rest of the act.
 */
class TestMusicOverrideRestore {

    private static final int SAMPLE_RATE = 48_000;
    private static final int LEVEL_MUSIC = 0x81;
    private static final int SUPER_MUSIC = 0x86;
    private static final int INVINCIBILITY_MUSIC = 0x87;
    private static final int EXTRA_LIFE_MUSIC = 0x88;

    private AudioManager audio;

    @BeforeEach
    void setUp() {
        audio = AudioManager.getInstance();
        audio.endCaptureMode();
        audio.resetState();
        SonicConfigurationService.getInstance().resetToDefaults();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(new OverrideProfile());
        // Fallback-WAV music: a durable looping sample voice, so a restored
        // song is still a live voice rather than one swept as complete.
        for (int musicId : new int[] {LEVEL_MUSIC, SUPER_MUSIC,
                INVINCIBILITY_MUSIC, EXTRA_LIFE_MUSIC}) {
            AudioManagerTestDiagnostics.registerFallbackSfxAsset(audio,
                    "music/" + Integer.toHexString(musicId).toUpperCase() + ".wav",
                    new byte[SAMPLE_RATE], SAMPLE_RATE);
        }
    }

    @AfterEach
    void tearDown() {
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    /**
     * The jingle's "fade in to previous song" (SMPS E4) arrives as
     * {@code restoreMusic()}. With nothing pushed there is nothing to fade back
     * in and the rest of the act plays in silence.
     */
    @Test
    void extraLifeJingleRestoresZoneMusic() {
        play(LEVEL_MUSIC);
        assertOverrideStack();

        play(EXTRA_LIFE_MUSIC);
        assertEquals(EXTRA_LIFE_MUSIC, activeMusicId());
        assertOverrideStack(LEVEL_MUSIC);

        restore();
        assertEquals(LEVEL_MUSIC, activeMusicId());
        assertOverrideStack();
    }

    /** A 1-up during invincibility resumes invincibility, then the zone. */
    @Test
    void extraLifeDuringInvincibilityResumesInvincibilityThenZoneMusic() {
        play(LEVEL_MUSIC);
        play(INVINCIBILITY_MUSIC);
        play(EXTRA_LIFE_MUSIC);
        assertOverrideStack(LEVEL_MUSIC, INVINCIBILITY_MUSIC);

        restore();
        assertEquals(INVINCIBILITY_MUSIC, activeMusicId());

        endOverride(INVINCIBILITY_MUSIC);
        assertEquals(LEVEL_MUSIC, activeMusicId());
        assertOverrideStack();
    }

    /**
     * Re-collecting invincibility while it is already playing restarts the
     * theme without stacking a second copy of itself over the zone music.
     */
    @Test
    void retriggeredInvincibilityDoesNotStackOverItself() {
        play(LEVEL_MUSIC);
        play(INVINCIBILITY_MUSIC);
        play(INVINCIBILITY_MUSIC);
        assertOverrideStack(LEVEL_MUSIC);

        endOverride(INVINCIBILITY_MUSIC);
        assertEquals(LEVEL_MUSIC, activeMusicId());
    }

    /**
     * Super running out during the 1-up jingle drops the saved Super theme from
     * under the jingle; the jingle still ends on the zone music.
     */
    @Test
    void superEndingDuringExtraLifeJingleStillRestoresZoneMusic() {
        play(LEVEL_MUSIC);
        play(SUPER_MUSIC);
        play(EXTRA_LIFE_MUSIC);
        assertOverrideStack(LEVEL_MUSIC, SUPER_MUSIC);

        endOverride(SUPER_MUSIC);
        assertEquals(EXTRA_LIFE_MUSIC, activeMusicId());
        assertOverrideStack(LEVEL_MUSIC);

        restore();
        assertEquals(LEVEL_MUSIC, activeMusicId());
        assertOverrideStack();
    }

    /** Ordinary music replaces the foreground and drops any saved overrides. */
    @Test
    void ordinaryMusicReplacesTheForegroundInsteadOfStacking() {
        play(LEVEL_MUSIC);
        play(INVINCIBILITY_MUSIC);
        play(LEVEL_MUSIC);
        assertEquals(LEVEL_MUSIC, activeMusicId());
        assertOverrideStack();
    }

    private void play(int musicId) {
        audio.playMusic(musicId);
        audio.presentFrame(PresentationMode.SILENT);
    }

    private void restore() {
        audio.restoreMusic();
        audio.presentFrame(PresentationMode.SILENT);
    }

    private void endOverride(int musicId) {
        audio.endMusicOverride(musicId);
        audio.presentFrame(PresentationMode.SILENT);
    }

    private int activeMusicId() {
        AudioPresentationSnapshot.MusicSlotSnapshot active =
                snapshot().activeMusic();
        assertNotNull(active, "no music is playing at all");
        return active.musicId();
    }

    /** Asserts the interrupted songs waiting to be restored, outermost first. */
    private void assertOverrideStack(int... musicIds) {
        int[] actual = snapshot().overrideStack().stream()
                .mapToInt(AudioPresentationSnapshot.MusicSlotSnapshot::musicId)
                .toArray();
        assertEquals(Arrays.toString(musicIds), Arrays.toString(actual),
                "interrupted music waiting to be restored");
    }

    private AudioPresentationSnapshot snapshot() {
        return audio.captureLogicalSnapshot().presentation();
    }

    private record OverrideProfile() implements GameAudioProfile {
        @Override public SmpsLoader createSmpsLoader(Rom rom) { return null; }
        @Override public SmpsSequencerConfig getSequencerConfig() {
            return new SmpsSequencerConfig.Builder().build();
        }
        @Override public int getSpeedShoesOnCommandId() { return -1; }
        @Override public int getSpeedShoesOffCommandId() { return -1; }
        @Override public int getInvincibilityMusicId() { return INVINCIBILITY_MUSIC; }
        @Override public int getExtraLifeMusicId() { return EXTRA_LIFE_MUSIC; }
        @Override public int getSuperSonicMusicId() { return SUPER_MUSIC; }
        @Override public int getDrowningMusicId() { return -1; }
        @Override public Map<GameSound, Integer> getSoundMap() { return Map.of(); }
        @Override public Map<GameMusic, Integer> getMusicMap() { return Map.of(); }
    }
}
