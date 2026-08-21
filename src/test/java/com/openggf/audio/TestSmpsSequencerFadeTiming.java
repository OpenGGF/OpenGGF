package com.openggf.audio;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.synth.VirtualSynthesizer;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic1.audio.Sonic1SmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSmpsSequencerFadeTiming {
    @Test
    void ordinaryMusicLoadsUseShippedPerGameSfxPolicy() {
        assertEquals(GameAudioProfile.OrdinaryMusicSfxPolicy.PRESERVE_ACTIVE,
                new Sonic1AudioProfile().getOrdinaryMusicSfxPolicy());
        assertEquals(GameAudioProfile.OrdinaryMusicSfxPolicy.STOP_ALL,
                new Sonic2AudioProfile().getOrdinaryMusicSfxPolicy());
        assertEquals(GameAudioProfile.OrdinaryMusicSfxPolicy.STOP_ALL,
                new Sonic3kAudioProfile().getOrdinaryMusicSfxPolicy());
    }

    @Test
    void fadeInCompletionUsesFrameClockInsteadOfMusicTempoTicks() {
        SmpsSequencerConfig config = new SmpsSequencerConfig.Builder()
                .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW2)
                .tempoModBase(0x100)
                .fadeInSteps(2)
                .fadeInDelay(1)
                .build();
        SmpsSequencer sequencer = new SmpsSequencer(
                new MinimalMusicData(1),
                AudioTestFixtures.EMPTY_DAC,
                new VirtualSynthesizer(),
                AudioManager.getInstance(),
                config);
        sequencer.setSampleRate(60.0);

        int[] callbacks = {0};
        sequencer.setOnFadeComplete(() -> callbacks[0]++);
        sequencer.triggerFadeIn();

        sequencer.advanceBatch(4);

        assertEquals(1, callbacks[0],
                "Fade-in completion should follow configured frame delay, not wait for a music tempo tick");
    }

    @Test
    void s3kAllowsSfxWhenPreviousMusicRestoreFadeStarts() {
        assertFalse(new Sonic3kAudioProfile().blocksSfxDuringMusicRestoreFadeIn(),
                "S3K clears zFadeToPrevFlag when zFadeInToPrevious starts, before fade-in completes");
    }

    @Test
    void sonic2KeepsSfxBlockedUntilRestoreFadeCompletes() {
        assertTrue(new Sonic2AudioProfile().blocksSfxDuringMusicRestoreFadeIn(),
                "S2 gates SFX on 1upPlaying OR FadeInFlag");
        assertEquals(
                SmpsSequencerConfig.MusicOverridePriorityPolicy
                        .PRESERVE_SAVED_LATCH,
                Sonic2SmpsSequencerConfig.CONFIG
                        .getMusicOverridePriorityPolicy());
        assertEquals(
                SmpsSequencerConfig.MusicOverrideRestorePolicy
                        .DRIVER_FADE_IN,
                Sonic2SmpsSequencerConfig.CONFIG
                        .getMusicOverrideRestorePolicy());
    }

    @Test
    void sonic1ClearsPriorityBeforeSaveAndRunsTheRestoreFade() {
        assertEquals(
                SmpsSequencerConfig.MusicOverridePriorityPolicy
                        .CLEAR_BEFORE_SAVE,
                Sonic1SmpsSequencerConfig.CONFIG
                        .getMusicOverridePriorityPolicy());
        assertEquals(
                SmpsSequencerConfig.MusicOverrideRestorePolicy
                        .DRIVER_FADE_IN,
                Sonic1SmpsSequencerConfig.CONFIG
                        .getMusicOverrideRestorePolicy());
    }

    @Test
    void sonic2FadeClearsSpeedShoesAtRequestBoundary() {
        SmpsSequencer sequencer = sequencer(
                Sonic2SmpsSequencerConfig.CONFIG);
        sequencer.initializeSpeedShoes(true);

        sequencer.triggerFadeOut(0x28, 3);

        assertFalse(sequencer.captureSnapshot().speedShoes());
    }

    @Test
    void s3kRestoreFadeAttenuatesFmButLeavesPsgVolumeUnchanged() {
        assertEquals(SmpsSequencerConfig.MusicOverrideRestorePolicy.DRIVER_FADE_IN,
                Sonic3kSmpsSequencerConfig.CONFIG
                        .getMusicOverrideRestorePolicy());
        assertEquals(SmpsSequencerConfig.FadeInChannelPolicy.FM_ONLY,
                Sonic3kSmpsSequencerConfig.CONFIG.getFadeInChannelPolicy());
        SmpsSequencer sequencer = new SmpsSequencer(
                new TwoTrackMusicData(), AudioTestFixtures.EMPTY_DAC,
                new VirtualSynthesizer(), AudioManager.getInstance(),
                new SmpsSequencerConfig.Builder()
                        .fmChannelOrder(new int[] {0})
                        .fadeInChannelPolicy(
                                SmpsSequencerConfig.FadeInChannelPolicy
                                        .FM_ONLY)
                        .fadeInSteps(0x40)
                        .build());
        SmpsSequencer.Track fm = sequencer.trackAt(0);
        SmpsSequencer.Track psg = sequencer.trackAt(1);
        fm.volumeOffset = 3;
        psg.volumeOffset = 4;
        sequencer.triggerFadeIn();

        assertEquals(0x43, fm.volumeOffset);
        assertEquals(4, psg.volumeOffset);
    }

    private static SmpsSequencer sequencer(SmpsSequencerConfig config) {
        SmpsSequencer sequencer = new SmpsSequencer(
                new MinimalMusicData(1), AudioTestFixtures.EMPTY_DAC,
                new VirtualSynthesizer(), AudioManager.getInstance(), config);
        sequencer.setSampleRate(60.0);
        return sequencer;
    }

    private static final class MinimalMusicData extends AbstractSmpsData {
        private final int configuredTempo;

        private MinimalMusicData(int tempo) {
            super(new byte[0], 0);
            this.configuredTempo = tempo;
            this.tempo = tempo;
        }

        @Override
        protected void parseHeader() {
        }

        @Override
        public int getTempo() {
            return configuredTempo;
        }

        @Override
        public byte[] getVoice(int voiceId) {
            return new byte[25];
        }

        @Override
        public byte[] getPsgEnvelope(int id) {
            return new byte[0];
        }

        @Override
        public int read16(int offset) {
            return 0;
        }

        @Override
        public int getBaseNoteOffset() {
            return 0;
        }
    }

    private static final class TwoTrackMusicData extends AbstractSmpsData {
        private TwoTrackMusicData() {
            super(new byte[] {0, (byte) 0x80}, 0);
            tempo = 1;
            channels = 1;
            psgChannels = 1;
            fmPointers = new int[] {1};
            fmKeyOffsets = new int[] {0};
            fmVolumeOffsets = new int[] {0};
            psgPointers = new int[] {1};
            psgKeyOffsets = new int[] {0};
            psgVolumeOffsets = new int[] {0};
            psgModEnvs = new int[] {0};
            psgInstruments = new int[] {0};
        }

        @Override protected void parseHeader() { }
        @Override public byte[] getVoice(int voiceId) { return new byte[25]; }
        @Override public byte[] getPsgEnvelope(int id) { return new byte[0]; }
        @Override public int read16(int offset) { return 0; }
        @Override public int getBaseNoteOffset() { return 0; }
    }
}
