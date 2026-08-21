package com.openggf.audio.smps;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.driver.SmpsDriverServiceObserver;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestS3kPalDriverCadence {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
    }

    @Test
    void palRepeatIsDriverGlobalAcrossLateSfxAdmission() {
        SmpsDriver driver = new SmpsDriver(50.0);
        driver.setRegion(SmpsSequencer.Region.PAL);
        SmpsSequencer music = sequencer(driver, false, 0);
        SmpsSequencer.Track musicTrack = envelopeTrack(0);
        music.addTrack(musicTrack);
        driver.addSequencer(music, false);

        driver.read(new short[4]); // two PAL VInts

        SmpsSequencer sfx = sequencer(driver, true, 1);
        SmpsSequencer.Track sfxTrack = envelopeTrack(3);
        sfx.addTrack(sfxTrack);
        driver.addSequencer(sfx, true);
        driver.read(new short[6]); // through PAL VInt five

        assertEquals(5, musicTrack.envPos);
        assertEquals(3, sfxTrack.envPos);
        assertEquals(0, driver.captureSnapshot().palFullUpdateCounter());

        driver.read(new short[2]); // sixth VInt: normal + repeated full update

        assertEquals(7, musicTrack.envPos);
        assertEquals(5, sfxTrack.envPos);
        assertEquals(5, driver.captureSnapshot().palFullUpdateCounter());
    }

    @Test
    void lateAdmissionJoinsTheExistingDriverVintPhase() {
        SmpsDriver driver = new SmpsDriver(100.0);
        driver.setRegion(SmpsSequencer.Region.PAL);
        SmpsSequencer music = sequencer(driver, false, 0);
        SmpsSequencer.Track musicTrack = envelopeTrack(0);
        music.addTrack(musicTrack);
        driver.addSequencer(music, false);

        driver.read(new short[2]); // one sample, halfway to the first VInt

        SmpsSequencer sfx = sequencer(driver, true, 1);
        SmpsSequencer.Track sfxTrack = envelopeTrack(3);
        sfx.addTrack(sfxTrack);
        driver.addSequencer(sfx, true);
        driver.read(new short[2]);

        assertEquals(1, musicTrack.envPos);
        assertEquals(1, sfxTrack.envPos);
    }

    @Test
    void palDriverCounterRoundTripsAtZeroBeforeRepeat() {
        SmpsDriver source = new SmpsDriver(50.0);
        source.setRegion(SmpsSequencer.Region.PAL);
        SmpsSequencer music = sequencer(source, false, 0);
        music.addTrack(envelopeTrack(0));
        source.addSequencer(music, false);
        source.read(new short[10]);
        SmpsDriverSnapshot snapshot = source.captureSnapshot();
        SmpsDriver restored = new SmpsDriver(50.0);

        restored.restoreSnapshot(snapshot);
        restored.read(new short[2]);

        assertEquals(0, snapshot.palFullUpdateCounter());
        assertEquals(5,
                restored.captureSnapshot().palFullUpdateCounter());
    }

    @Test
    void repeatedFullUpdateReleasesSfxBeforeRepeatedMusicService() {
        SmpsDriver driver = new SmpsDriver(50.0);
        driver.setRegion(SmpsSequencer.Region.PAL);
        SmpsSequencer music = sequencer(driver, false, 0x81);
        music.addTrack(envelopeTrack(0));
        driver.addSequencer(music, false);
        driver.read(new short[10]);

        SmpsSequencer sfx = sequencer(
                driver, true, 0xA0, new byte[] { (byte) 0xF2 });
        SmpsSequencer.Track sfxTrack = new SmpsSequencer.Track(
                0, SmpsSequencer.TrackType.FM, 2);
        sfxTrack.duration = 2;
        sfxTrack.pos = 0;
        sfx.addTrack(sfxTrack);
        driver.addSequencer(sfx, true);
        List<String> services = new ArrayList<>();
        driver.setServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                services.add(event.sequencer().source().id()
                        + ":" + event.kind());
            }
        });

        driver.read(new short[2]);

        assertEquals(List.of(
                "160:SEQUENCER_TICK",
                "129:SEQUENCER_TICK",
                "160:SEQUENCER_TICK",
                "160:COMPLETION_CLEANUP",
                "129:SEQUENCER_TICK"), services);
    }

    private static SmpsSequencer sequencer(
            SmpsDriver driver, boolean sfx, int id) {
        return sequencer(driver, sfx, id,
                new byte[] { (byte) 0x81, 4 });
    }

    private static SmpsSequencer sequencer(
            SmpsDriver driver, boolean sfx, int id, byte[] program) {
        MinimalData data = new MinimalData(program);
        data.setId(id);
        SmpsSequencer sequencer = new SmpsSequencer(
                data,
                AudioTestFixtures.EMPTY_DAC,
                driver,
                AudioManager.getInstance(),
                new SmpsSequencerConfig.Builder()
                        .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW)
                        .palServicePolicy(
                                SmpsSequencerConfig.PalServicePolicy
                                        .FULL_DRIVER_REPEAT_EVERY_SIXTH)
                        .driverServiceOrder(
                                SmpsSequencerConfig.DriverServiceOrder
                                        .SFX_THEN_MUSIC)
                        .build());
        sequencer.setSampleRate(driver.getOutputSampleRate());
        sequencer.setSfxMode(sfx);
        return sequencer;
    }

    private static SmpsSequencer.Track envelopeTrack(int channel) {
        SmpsSequencer.Track track = new SmpsSequencer.Track(
                0, SmpsSequencer.TrackType.PSG, channel);
        track.duration = 20;
        track.note = 0x81;
        track.envData = new byte[20];
        return track;
    }

    private static final class MinimalData extends AbstractSmpsData {
        private MinimalData(byte[] program) {
            super(program, 0);
            tempo = 0;
        }

        @Override
        protected void parseHeader() {
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
}
