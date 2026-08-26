package com.openggf.audio.driver;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSmpsDriverServiceOrder {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
    }

    @Test
    void s3kServicesSfxBeforeMusicAtTheSameVint() {
        assertServiceOrder(
                SmpsSequencerConfig.DriverServiceOrder.SFX_THEN_MUSIC,
                List.of(0xA0, 0x81));
    }

    @Test
    void s1AndS2RetainMusicBeforeSfxServiceOrder() {
        assertServiceOrder(
                SmpsSequencerConfig.DriverServiceOrder.MUSIC_THEN_SFX,
                List.of(0x81, 0xA0));
    }

    @Test
    void s3kReleasesACompletedSfxBeforeSameVintMusicService() {
        SmpsDriver driver = new SmpsDriver(60.0);
        SmpsSequencer music = sequencer(driver, 0x81, false,
                SmpsSequencerConfig.DriverServiceOrder.SFX_THEN_MUSIC);
        SmpsSequencer sfx = sequencer(driver, 0xA0, true,
                SmpsSequencerConfig.DriverServiceOrder.SFX_THEN_MUSIC,
                new byte[] { (byte) 0xF2 });
        sfx.getTracks().getFirst().duration = 1;
        sfx.getTracks().getFirst().pos = 0;
        driver.addSequencer(music, false);
        driver.addSequencer(sfx, true);
        List<String> services = new ArrayList<>();
        driver.setServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                services.add(event.sequencer().source().id()
                        + ":" + event.kind());
            }
        });

        driver.read(new short[4]);

        assertEquals(List.of(
                "160:SEQUENCER_TICK",
                "160:COMPLETION_CLEANUP",
                "129:SEQUENCER_TICK"), services.subList(0, 3));
    }

    private static void assertServiceOrder(
            SmpsSequencerConfig.DriverServiceOrder order,
            List<Integer> expected) {
        SmpsDriver driver = new SmpsDriver(60.0);
        SmpsSequencer music = sequencer(driver, 0x81, false, order);
        SmpsSequencer sfx = sequencer(driver, 0xA0, true, order);
        driver.addSequencer(music, false);
        driver.addSequencer(sfx, true);
        List<Integer> serviced = new ArrayList<>();
        driver.setServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                if (event.kind() == ServiceKind.SEQUENCER_TICK) {
                    serviced.add(event.sequencer().source().id());
                }
            }
        });

        driver.read(new short[4]);

        assertEquals(expected, serviced.subList(0, 2));
    }

    private static SmpsSequencer sequencer(
            SmpsDriver driver,
            int id,
            boolean sfx,
            SmpsSequencerConfig.DriverServiceOrder order) {
        return sequencer(driver, id, sfx, order,
                new byte[] { (byte) 0x81, 20 });
    }

    private static SmpsSequencer sequencer(
            SmpsDriver driver,
            int id,
            boolean sfx,
            SmpsSequencerConfig.DriverServiceOrder order,
            byte[] program) {
        MinimalData data = new MinimalData(program);
        data.setId(id);
        SmpsSequencer sequencer = new SmpsSequencer(
                data,
                AudioTestFixtures.EMPTY_DAC,
                driver,
                AudioManager.getInstance(),
                new SmpsSequencerConfig.Builder()
                        .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW)
                        .driverServiceOrder(order)
                        .build());
        sequencer.setSampleRate(60.0);
        sequencer.setSfxMode(sfx);
        SmpsSequencer.Track track = track();
        track.duration = 20;
        sequencer.addTrack(track);
        return sequencer;
    }

    private static SmpsSequencer.Track track() {
        try {
            var constructor = SmpsSequencer.Track.class
                    .getDeclaredConstructor(int.class,
                            SmpsSequencer.TrackType.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    0, SmpsSequencer.TrackType.FM, 2);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class MinimalData extends AbstractSmpsData {
        private MinimalData(byte[] program) {
            super(program, 0);
        }

        @Override protected void parseHeader() { }
        @Override public byte[] getVoice(int id) { return new byte[25]; }
        @Override public byte[] getPsgEnvelope(int id) { return new byte[0]; }
        @Override public int read16(int offset) { return 0; }
        @Override public int getBaseNoteOffset() { return 0; }
    }
}
