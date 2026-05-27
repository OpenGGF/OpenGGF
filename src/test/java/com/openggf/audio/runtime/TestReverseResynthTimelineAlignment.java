package com.openggf.audio.runtime;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioKeyframeStore;
import com.openggf.audio.rewind.AudioTimelineEntry;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestReverseResynthTimelineAlignment {

    private static final DacData EMPTY_DAC = new DacData(
            Collections.emptyMap(), Collections.emptyMap(), 288);
    private static final SmpsSequencerConfig DEFAULT_CONFIG = new SmpsSequencerConfig.Builder().build();

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void firstFrameAfterKeyframeReplaysCommandsBeforeRendering() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new DeterministicNullBackend());

        AudioKeyframeStore store = new AudioKeyframeStore();
        store.capture(0L, audio);

        PcmHistoryRing ring = new PcmHistoryRing(8);
        ring.invalidateAt(2);
        ring.write(new short[] {0, 0, 0, 0}, 2);
        PcmHistoryRing.ReverseCursor cursor = ring.createReverseCursor();

        StubSmpsData sfx = new StubSmpsData();
        sfx.setId(0x99);
        ReverseAudioSession session = new ReverseAudioSession(
                ring,
                store.frozenView(),
                0L,
                List.of(new AudioTimelineEntry(1, 0,
                        new AudioCommand.PlaySfx(
                                0x99, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null))),
                120, 60,
                SmpsSequencer.Region.NTSC,
                2,
                0,
                SmpsDriverSnapshot.liveReferences(),
                () -> new com.openggf.audio.driver.SmpsDriver(120),
                new AudioCommandDataResolver() {
                    @Override
                    public MusicData resolveMusic(AudioCommand.PlayMusic command) {
                        return null;
                    }

                    @Override
                    public SfxData resolveSfx(AudioCommand.PlaySfx command) {
                        return new SfxData(sfx, EMPTY_DAC, DEFAULT_CONFIG);
                    }
                },
                null,
                DEFAULT_CONFIG,
                false,
                false,
                false);
        ReverseResynthesizer worker = new ReverseResynthesizer(session);

        assertTrue(worker.runOneIterationForPrefill(cursor));
        assertNotNull(worker.workerStateForTest().sfxStream,
                "SFX at frame 1 must be active while rendering audio immediately after frame-0 keyframe");
    }

    private static final class DeterministicNullBackend extends NullAudioBackend {
        @Override
        public boolean supportsDeterministicRuntimePresentation() {
            return true;
        }

        @Override
        public int outputSampleRate() {
            return 120;
        }
    }

    private static final class StubSmpsData extends AbstractSmpsData {
        private StubSmpsData() {
            super(new byte[0], 0);
        }

        @Override
        protected void parseHeader() {
        }

        @Override
        public byte[] getVoice(int voiceId) {
            return new byte[0];
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
}
