package com.openggf.audio.driver;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.presentation.ResolvedSmpsSfxSource;
import com.openggf.audio.presentation.SmpsAssetKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestSmpsGlobalSfxPriority {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
    }

    @Test
    void s1AndS2LatchRejectsLowerPriorityEvenOnAFreeChannel() {
        SmpsDriver driver = new SmpsDriver();
        driver.addSequencer(sfx(driver, 0xA0, 0x70,
                SmpsSequencerConfig.SfxPriorityPolicy.GLOBAL_LATCH), true);

        SmpsRequestAdmissionPolicy.AdmissionResult lower =
                driver.evaluateSfxRequest(0xA1, 0x60, false, false);
        SmpsRequestAdmissionPolicy.AdmissionResult equal =
                driver.evaluateSfxRequest(0xA2, 0x70, false, false);

        assertFalse(lower.accepted());
        assertEquals(SmpsRequestAdmissionPolicy.RejectionReason.PRIORITY,
                lower.reason());
        assertEquals(0x70, lower.priorityBefore());
        assertTrue(equal.accepted());
        assertEquals(0x70, equal.priorityAfter());
    }

    @Test
    void bitSevenPriorityIsAcceptedWithoutReplacingTheStoredLatch() {
        SmpsDriver driver = new SmpsDriver();
        driver.addSequencer(sfx(driver, 0xA0, 0x70,
                SmpsSequencerConfig.SfxPriorityPolicy.GLOBAL_LATCH), true);

        driver.addSequencer(sfx(driver, 0xD0, 0x80,
                SmpsSequencerConfig.SfxPriorityPolicy.GLOBAL_LATCH), true);

        assertEquals(0x70, driver.sfxPriorityLatchForTesting());
        assertFalse(driver.evaluateSfxRequest(
                0xA1, 0x60, false, false).accepted());
    }

    @Test
    void s3kHasNoGlobalPriorityLatch() {
        SmpsDriver driver = new SmpsDriver();
        driver.addSequencer(sfx(driver, 0xA0, 0x70,
                SmpsSequencerConfig.SfxPriorityPolicy.NONE), true);

        assertTrue(driver.evaluateSfxRequest(
                0xA1, 0x01, false, false).accepted());
        assertEquals(SmpsRequestAdmissionPolicy.NO_PRIORITY,
                driver.sfxPriorityLatchForTesting());
    }

    @Test
    void shippedTrackStopClearsTheSingleLatchAndSnapshotPreservesIt() {
        SmpsDriver driver = new SmpsDriver(60.0);
        SmpsSequencer high = sfx(driver, 0xA0, 0x70,
                SmpsSequencerConfig.SfxPriorityPolicy.GLOBAL_LATCH);
        SmpsSequencer.Track track = track();
        track.duration = 1;
        high.addTrack(track);
        high.setSampleRate(60.0);
        driver.addSequencer(high, true);

        SmpsDriver restored = new SmpsDriver(60.0);
        restored.restoreSnapshot(driver.captureSnapshot());
        assertEquals(0x70, restored.sfxPriorityLatchForTesting());

        high.repeatDriverService();

        assertEquals(0, driver.sfxPriorityLatchForTesting());
        assertTrue(driver.evaluateSfxRequest(
                0xA1, 0x01, false, false).accepted());
    }

    @Test
    void admissionRollbackAndStaleProofPreserveTheLatchAtomically() {
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer preparedLow = sfx(driver, 0xA0, 0x60,
                SmpsSequencerConfig.SfxPriorityPolicy.GLOBAL_LATCH);
        PreparedSfxAdmission stale = driver.prepareNewSfxAdmission(
                preparedLow, 0, 0);

        SmpsSequencer high = sfx(driver, 0xA1, 0x70,
                SmpsSequencerConfig.SfxPriorityPolicy.GLOBAL_LATCH);
        PreparedSfxAdmission highAdmission = driver.prepareNewSfxAdmission(
                high, 0, 0);
        SfxAdmissionMutationJournal journal =
                SfxAdmissionMutationJournal.capture(
                        driver, highAdmission, null, null);
        high.beginSfxAdmission();
        driver.commitSfxAdmissionUnderJournal(highAdmission);
        assertEquals(0x70, driver.sfxPriorityLatchForTesting());

        assertThrows(IllegalStateException.class,
                () -> driver.commitSfxAdmission(stale));
        journal.restore();

        assertEquals(SmpsRequestAdmissionPolicy.NO_PRIORITY,
                driver.sfxPriorityLatchForTesting());
    }

    @Test
    void presentationRejectsAFreeChannelRequestBeforeConstruction() {
        SmpsDriver driver = new SmpsDriver();
        driver.addSequencer(sfx(driver, 0xA0, 0x70,
                SmpsSequencerConfig.SfxPriorityPolicy.GLOBAL_LATCH), true);
        AudioPresentationSourceFactory factory =
                new AudioPresentationSourceFactory(
                        () -> true,
                        new SmpsCoordFlagHandlerOwner(
                                new SmpsCoordFlagRuntimeState()));
        ResolvedSmpsSfxSource lower = new ResolvedSmpsSfxSource(
                1,
                new SmpsAssetKey("s1", SmpsAssetKey.Route.BASE_ID,
                        0xA1, null),
                65_536, 0x60, 0, 1, 800);

        var admission = factory.evaluateAdmission(lower, driver);

        assertFalse(admission.result().accepted());
        assertEquals(SmpsRequestAdmissionPolicy.RejectionReason.PRIORITY,
                admission.result().reason());
        assertEquals(0x70, admission.context().priorityBefore());
    }

    @Test
    void admissionOwnsTheChannelBeforeTheFirstSfxChipWrite() {
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer music = sfx(driver, 0x81, 0,
                SmpsSequencerConfig.SfxPriorityPolicy.GLOBAL_LATCH);
        SmpsSequencer.Track musicTrack = track();
        musicTrack.duration = 20;
        music.addTrack(musicTrack);
        driver.addSequencer(music, false);

        SmpsSequencer request = sfx(driver, 0xA0, 0x70,
                SmpsSequencerConfig.SfxPriorityPolicy.GLOBAL_LATCH);
        SmpsSequencer.Track requestTrack = track();
        requestTrack.duration = 20;
        request.addTrack(requestTrack);
        driver.addSequencer(request, true);

        assertTrue(musicTrack.overridden);
        assertEquals(request, fmLock(driver, 2));
    }

    private static SmpsSequencer sfx(
            SmpsDriver driver,
            int id,
            int priority,
            SmpsSequencerConfig.SfxPriorityPolicy priorityPolicy) {
        MinimalData data = new MinimalData();
        data.setId(id);
        SmpsSequencer sequencer = new SmpsSequencer(
                data,
                AudioTestFixtures.EMPTY_DAC,
                driver,
                AudioManager.getInstance(),
                new SmpsSequencerConfig.Builder()
                        .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW)
                        .sfxPriorityPolicy(priorityPolicy)
                        .build());
        sequencer.setSfxPriority(priority);
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

    private static SmpsSequencer fmLock(SmpsDriver driver, int channel) {
        try {
            var field = SmpsDriver.class.getDeclaredField("fmLocks");
            field.setAccessible(true);
            return ((SmpsSequencer[]) field.get(driver))[channel];
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class MinimalData extends AbstractSmpsData {
        private MinimalData() {
            super(new byte[] { (byte) 0xF2 }, 0);
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
