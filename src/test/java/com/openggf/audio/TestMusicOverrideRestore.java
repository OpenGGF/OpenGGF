package com.openggf.audio;

import com.openggf.audio.presentation.AudioPresentationSnapshot;
import com.openggf.audio.presentation.PresentationVoiceSnapshot;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The 1-up jingle is the only music that interrupts the current song and
 * restores it, and the sound driver saves exactly one song for it.
 *
 * <p>The ROM owns a single save slot: S1's {@code Sound_PlayBGM} backs up
 * {@code v_1up_ram} and sets {@code f_1up_playing}; S3K's {@code zPlayMusic}
 * copies {@code zTracksStart} to {@code zTracksSaveStart} and sets
 * {@code zFadeToPrevFlag}. S1/S2 abandon that save when another song is loaded;
 * S3K instead leaves an ordinary music request queued while the jingle owns
 * {@code zFadeToPrevFlag}. Invincibility and Super are ordinary music that
 * restore the level music by re-issuing it, not by unwinding a saved song.
 *
 * <p>The claims are made against the presentation registry the mixer actually
 * renders: {@code activeMusic} is the voice being heard and the override stack
 * degenerates to that single save slot.
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
        AudioManagerTestDiagnostics.registerFallbackSfxAsset(audio,
                "sfx/jump.wav", new byte[SAMPLE_RATE], SAMPLE_RATE);
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
        assertNothingSaved();

        play(EXTRA_LIFE_MUSIC);
        assertEquals(EXTRA_LIFE_MUSIC, activeMusicId());
        assertSaved(LEVEL_MUSIC);

        restore();
        assertEquals(LEVEL_MUSIC, activeMusicId());
        assertNothingSaved();
        assertEquals(false, snapshot().sfxBlocked(),
                "restoring a non-SMPS fallback must release the 1-up SFX gate");
    }

    @Test
    void extraLifeFromSilenceStillOwnsAndReleasesItsGate() {
        play(EXTRA_LIFE_MUSIC);
        play(LEVEL_MUSIC);
        assertEquals(EXTRA_LIFE_MUSIC, activeMusicId());

        restore();
        assertEquals(null, snapshot().activeMusic());
        assertEquals(false, snapshot().sfxBlocked());

        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(LEVEL_MUSIC, activeMusicId());
    }

    /**
     * Invincibility and Super are ordinary music, not overrides. The ROM plays
     * them through the same {@code zPlayMusic_DoFade} path as any other song
     * and restores the level music by re-issuing it when the power-up ends
     * ({@code Sonic_ChkInvin}), so nothing is saved here.
     */
    @Test
    void invincibilityAndSuperReplaceTheZoneMusicWithoutSavingIt() {
        play(LEVEL_MUSIC);

        play(INVINCIBILITY_MUSIC);
        assertEquals(INVINCIBILITY_MUSIC, activeMusicId());
        assertNothingSaved();

        play(SUPER_MUSIC);
        assertEquals(SUPER_MUSIC, activeMusicId());
        assertNothingSaved();
    }

    /**
     * Ordinary music requested while the jingle plays waits behind it.
     *
     * <p>ROM: while {@code zFadeToPrevFlag} holds the 1-up id,
     * {@code zUpdateMusic} leaves an ordinary {@code zMusicNumber} queued and
     * continues updating the jingle. Its E4 first restores the saved tracks;
     * the next driver service may then consume the queued replacement. This is
     * observable when invincibility expires during the jingle: the expiry's
     * level-music request must not cut the jingle short or strand its SFX gate.
     */
    @Test
    void invincibilityExpiryWaitsForTheExtraLifeJingle() {
        play(INVINCIBILITY_MUSIC);
        play(EXTRA_LIFE_MUSIC);
        assertSaved(INVINCIBILITY_MUSIC);

        play(LEVEL_MUSIC);
        assertEquals(EXTRA_LIFE_MUSIC, activeMusicId(),
                "the queued level song must not cut off the 1-up jingle");
        assertEquals(LEVEL_MUSIC,
                snapshot().pendingMusic().music().musicId());
        assertEquals(true, snapshot().sfxBlocked(),
                "the 1-up SFX gate remains active until its own restore");

        AudioLogicalSnapshot queued = audio.captureLogicalSnapshot();
        play(SUPER_MUSIC);
        assertEquals(SUPER_MUSIC,
                snapshot().pendingMusic().music().musicId(),
                "S3K's single input cell keeps only the latest song request");
        audio.restoreLogicalSnapshot(queued);
        assertEquals(LEVEL_MUSIC,
                snapshot().pendingMusic().music().musicId(),
                "rewind must restore the pending input separately from the save slot");

        restore();
        assertEquals(INVINCIBILITY_MUSIC, activeMusicId(),
                "E4 first restores the saved song before consuming the queue");
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(LEVEL_MUSIC, activeMusicId(),
                "the next driver service consumes the queued expiry request");
        assertNothingSaved();
        assertEquals(false, snapshot().sfxBlocked(),
                "finishing the 1-up must release later sound effects");
    }

    /** S3K clears a queued system command instead of applying it to the jingle. */
    @Test
    void fadeDuringExtraLifeIsDiscardedAndReleasesTheSfxGate() {
        play(INVINCIBILITY_MUSIC);
        play(EXTRA_LIFE_MUSIC);
        play(LEVEL_MUSIC);

        audio.fadeOutMusic(0x28, 6);
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(EXTRA_LIFE_MUSIC, activeMusicId(),
                "a fade command must not fade the active 1-up jingle");

        restore();
        assertEquals(INVINCIBILITY_MUSIC, activeMusicId(),
                "the fade overwrites and clears the queued level song");
        assertNothingSaved();
        assertEquals(false, snapshot().sfxBlocked(),
                "the 1-up restore must release its SFX gate");

        audio.playSfx("JUMP");
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(true, hasSampleAsset("sfx/jump.wav"),
                "the discarded fade must not suppress later jump SFX");
    }

    @Test
    void stopDuringExtraLifeIsDiscardedAndClearsThePendingInput() {
        play(INVINCIBILITY_MUSIC);
        play(EXTRA_LIFE_MUSIC);
        play(LEVEL_MUSIC);

        audio.stopMusic();
        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(EXTRA_LIFE_MUSIC, activeMusicId());
        assertEquals(null, snapshot().pendingMusic());

        restore();
        assertEquals(INVINCIBILITY_MUSIC, activeMusicId());
        assertEquals(false, snapshot().sfxBlocked());
    }

    /**
     * Re-collecting a 1-up while the jingle plays reloads it without saving a
     * second time, so the zone music underneath survives.
     *
     * <p>ROM: {@code zPlayMusic} jumps straight to {@code zBGMLoad} when
     * {@code zFadeToPrevFlag} already holds the 1-up id.
     */
    @Test
    void retriggeredJingleKeepsTheOriginalSavedSong() {
        play(LEVEL_MUSIC);
        play(EXTRA_LIFE_MUSIC);
        long voiceId = snapshot().activeMusic().voiceId();
        play(EXTRA_LIFE_MUSIC);
        assertEquals(voiceId, snapshot().activeMusic().voiceId(),
                "a later 1-up input is cleared without restarting the jingle");
        assertSaved(LEVEL_MUSIC);

        restore();
        assertEquals(LEVEL_MUSIC, activeMusicId());
    }

    /**
     * The save is a single slot, never a stack: whatever the sequence, at most
     * one song is ever waiting to be restored.
     */
    @Test
    void theSaveSlotNeverHoldsMoreThanOneSong() {
        play(LEVEL_MUSIC);
        play(EXTRA_LIFE_MUSIC);
        play(SUPER_MUSIC);
        play(EXTRA_LIFE_MUSIC);
        play(INVINCIBILITY_MUSIC);
        play(EXTRA_LIFE_MUSIC);
        assertSaved(LEVEL_MUSIC);

        restore();
        assertEquals(LEVEL_MUSIC, activeMusicId());
        assertNothingSaved();
    }

    /** Ordinary music still replaces the foreground when no jingle owns it. */
    @Test
    void ordinaryMusicReplacesTheForegroundOutsideTheJingle() {
        play(SUPER_MUSIC);
        play(LEVEL_MUSIC);
        assertEquals(LEVEL_MUSIC, activeMusicId());
        assertNothingSaved();
    }

    /** S1/S2 retain their immediate ordinary-music replacement behavior. */
    @Test
    void defaultProfileAbandonsTheJingleForOrdinaryMusic() {
        audio.setAudioProfile(new ImmediateOverrideProfile());
        play(LEVEL_MUSIC);
        play(EXTRA_LIFE_MUSIC);
        play(SUPER_MUSIC);

        assertEquals(SUPER_MUSIC, activeMusicId());
        assertNothingSaved();
        assertEquals(null, snapshot().pendingMusic());
    }

    private void play(int musicId) {
        audio.playMusic(musicId);
        audio.presentFrame(PresentationMode.SILENT);
    }

    private void restore() {
        audio.restoreMusic();
        audio.presentFrame(PresentationMode.SILENT);
    }

    private int activeMusicId() {
        AudioPresentationSnapshot.MusicSlotSnapshot active =
                snapshot().activeMusic();
        assertNotNull(active, "no music is playing at all");
        return active.musicId();
    }

    /** Asserts the single song the 1-up jingle saved for restoration. */
    private void assertSaved(int musicId) {
        assertSavedSongs(musicId);
    }

    private void assertNothingSaved() {
        assertSavedSongs();
    }

    private void assertSavedSongs(int... musicIds) {
        int[] actual = snapshot().overrideStack().stream()
                .mapToInt(AudioPresentationSnapshot.MusicSlotSnapshot::musicId)
                .toArray();
        assertEquals(Arrays.toString(musicIds), Arrays.toString(actual),
                "song saved for the 1-up jingle to restore");
    }

    private AudioPresentationSnapshot snapshot() {
        return audio.captureLogicalSnapshot().presentation();
    }

    private boolean hasSampleAsset(String assetId) {
        return snapshot().voices().stream()
                .filter(PresentationVoiceSnapshot.Sample.class::isInstance)
                .map(PresentationVoiceSnapshot.Sample.class::cast)
                .anyMatch(sample -> sample.assetId().equals(assetId));
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
        @Override public MusicDuringOverridePolicy getMusicDuringOverridePolicy() {
            return new Sonic3kAudioProfile().getMusicDuringOverridePolicy();
        }
        @Override public int getDrowningMusicId() { return -1; }
        @Override public Map<GameSound, Integer> getSoundMap() { return Map.of(); }
        @Override public Map<GameMusic, Integer> getMusicMap() { return Map.of(); }
    }

    private record ImmediateOverrideProfile() implements GameAudioProfile {
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
