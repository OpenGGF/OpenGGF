package com.openggf.audio.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestSfxContentionObserver {

    @Test
    void observerIsDisabledByDefaultAndDoesNotChangeSnapshotIdentity() {
        // Break caught: diagnostic observation becomes live driver state or is enabled implicitly.
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer music = sequencer("music", 0x81, driver);
        SmpsSequencer sfx = sequencer("sfx", 0xA0, driver);
        driver.addSequencer(music, false);
        SmpsDriverSnapshot before = driver.captureSnapshot();

        driver.addSequencer(sfx, true);
        driver.writeFm(sfx, 0, 0xA2, 0x22);

        assertEquals(SfxContentionObserver.NONE, driver.sfxContentionObserver());
        assertEquals(before.sequencers().size() + 1,
                driver.captureSnapshot().sequencers().size());
        driver.setSfxContentionObserver(SfxContentionObserver.NONE);
        SmpsDriverSnapshot afterFirstDisabledInstall = driver.captureSnapshot();
        driver.setSfxContentionObserver(SfxContentionObserver.NONE);
        SmpsDriverSnapshot afterSecondDisabledInstall = driver.captureSnapshot();
        assertEquals(afterFirstDisabledInstall.sequencers(), afterSecondDisabledInstall.sequencers(),
                "installing the disabled observer must not participate in rewind snapshots");
        assertEquals(afterFirstDisabledInstall.continuousSfxId(), afterSecondDisabledInstall.continuousSfxId());
    }

    @Test
    void observerBookkeepingIsReleasedWithSfxLifecycle() {
        // Break caught: opt-in diagnostic identities retain dead SFX sequencers.
        SmpsDriver driver = new SmpsDriver();
        driver.setSfxContentionObserver(new SfxContentionObserver() { });
        SmpsSequencer sfx = sequencer("sfx", 0xA0, driver);
        driver.addSequencer(sfx, true);
        assertEquals(1, driver.trackedSfxAdmissionCountForTesting());
        driver.stopAllSfx();
        assertEquals(0, driver.trackedSfxAdmissionCountForTesting());
        driver.addSequencer(sequencer("again", 0xA0, driver), true);
        driver.stopAll();
        assertEquals(0, driver.trackedSfxAdmissionCountForTesting());
    }

    @Test
    void reportsOrderedAdmissionAndPerRoleDisplacementForOverlappingSfx() {
        // Break caught: same-frame SFX requests lose source order or a later normal SFX's FM3 steal.
        SmpsDriver driver = new SmpsDriver();
        List<Object> events = new ArrayList<>();
        driver.setSfxContentionObserver(new SfxContentionObserver() {
            @Override
            public void onSfxAdmitted(SfxContentionObserver.Admission admission) {
                events.add(admission);
            }

            @Override
            public void onRoleArbitrated(SfxContentionObserver.Arbitration arbitration) {
                events.add(arbitration);
            }
        });
        SmpsSequencer music = sequencer("music", 0x81, driver);
        SmpsSequencer first = realTrackSequencer("first", 0xA0, driver);
        SmpsSequencer second = realTrackSequencer("second", 0xA1, driver);
        first.setSfxPriority(0x20);
        second.setSfxPriority(0x60);
        driver.addSequencer(music, false);
        driver.addSequencer(first, true);
        first.writeFm(0, 0xA2, 0x22);
        driver.addSequencer(second, true);
        second.writeFm(0, 0xA2, 0x44);

        assertEquals(4, events.size());
        SfxContentionObserver.Admission firstAdmission =
                (SfxContentionObserver.Admission) events.get(0);
        SfxContentionObserver.Arbitration firstRole =
                (SfxContentionObserver.Arbitration) events.get(1);
        SfxContentionObserver.Admission secondAdmission =
                (SfxContentionObserver.Admission) events.get(2);
        SfxContentionObserver.Arbitration secondRole =
                (SfxContentionObserver.Arbitration) events.get(3);
        assertEquals(0xA0, firstAdmission.source().descriptor().id());
        assertEquals(0, firstAdmission.source().admissionOrdinal());
        assertEquals(SfxContentionObserver.Bus.FM, firstRole.bus());
        assertEquals(2, firstRole.channel());
        assertTrue(firstRole.acquired());
        assertNull(firstRole.previousOwner(),
                "an unlocked role remains music-owned in the presentation snapshot, not the SFX lock table");
        assertEquals(0xA1, secondAdmission.source().descriptor().id());
        assertEquals(1, secondAdmission.source().admissionOrdinal());
        assertTrue(secondRole.acquired());
        assertEquals(firstAdmission.source(), secondRole.previousOwner());
        assertEquals(secondAdmission.source(), secondRole.challenger());
        assertEquals(1, events.stream()
                .filter(SfxContentionObserver.Arbitration.class::isInstance)
                .map(SfxContentionObserver.Arbitration.class::cast)
                .filter(event -> event.challenger().equals(secondAdmission.source()))
                .count(), "one production lock decision reports the same-frame B takeover");
    }

    @Test
    void rollbackPreservesSurvivingAdmissionIdentityWithoutReusingOrdinals() {
        // Break caught: rollback reidentified a surviving sequencer or reused a reverted admission ordinal.
        SmpsDriver driver = new SmpsDriver();
        List<SfxContentionObserver.Admission> admissions = new ArrayList<>();
        driver.setSfxContentionObserver(new SfxContentionObserver() {
            @Override
            public void onSfxAdmitted(SfxContentionObserver.Admission admission) {
                admissions.add(admission);
            }
        });
        SmpsSequencer survivor = sequencer("survivor", 0xA0, driver);
        driver.addSequencer(survivor, true);
        SmpsDriver.LiveCommandMutationToken rollback = driver.captureLiveCommandMutation();
        driver.addSequencer(sequencer("discarded", 0xA1, driver), true);

        driver.rollbackLiveCommandMutation(rollback);
        driver.addSequencer(sequencer("after-rollback", 0xA2, driver), true);

        assertEquals(0, admissions.getFirst().source().admissionOrdinal());
        assertEquals(1, admissions.get(1).source().admissionOrdinal());
        assertEquals(2, admissions.get(2).source().admissionOrdinal());
        assertEquals(0xA0, admissions.getFirst().source().descriptor().id());
    }

    @Test
    void restoreReadmitsSfxInSnapshotOrderWithFreshMonotonicIdentities() {
        // Break caught: reconstructed SFX inherited -1 or HashSet-order admission identities.
        SmpsDriver driver = new SmpsDriver();
        List<SfxContentionObserver.Admission> admissions = new ArrayList<>();
        driver.setSfxContentionObserver(new SfxContentionObserver() {
            @Override
            public void onSfxAdmitted(SfxContentionObserver.Admission admission) {
                admissions.add(admission);
            }
        });
        driver.addSequencer(sequencer("first", 0xA0, driver), true);
        driver.addSequencer(sequencer("second", 0xA1, driver), true);
        SmpsDriverSnapshot snapshot = driver.captureSnapshot();
        admissions.clear();

        driver.restoreSnapshot(snapshot);

        assertEquals(List.of(0xA0, 0xA1), admissions.stream()
                .map(admission -> admission.source().descriptor().id()).toList());
        assertEquals(List.of(2L, 3L), admissions.stream()
                .map(admission -> admission.source().admissionOrdinal()).toList());
        assertTrue(admissions.stream().allMatch(admission -> admission.source().admissionOrdinal() >= 0));
        assertNotEquals(admissions.getFirst().source(), admissions.get(1).source());
    }

    private static SmpsSequencer sequencer(String name, int id, SmpsDriver driver) {
        AbstractSmpsData data = new AudioTestFixtures.StubSmpsData(name);
        data.setId(id);
        return new SmpsSequencer(data, AudioTestFixtures.EMPTY_DAC, driver,
                AudioManager.getInstance(), new SmpsSequencerConfig.Builder().build());
    }

    private static SmpsSequencer realTrackSequencer(String name, int id, SmpsDriver driver) {
        AbstractSmpsData data = new SingleFmTrackData(name);
        data.setId(id);
        return new SmpsSequencer(data, AudioTestFixtures.EMPTY_DAC, driver,
                AudioManager.getInstance(), new SmpsSequencerConfig.Builder()
                .fmChannelOrder(new int[] {2}).build());
    }

    private static final class SingleFmTrackData extends AbstractSmpsData {
        private final String name;

        private SingleFmTrackData(String name) {
            super(new byte[] {0}, 0);
            this.name = name;
        }

        @Override
        protected void parseHeader() {
            channels = 1;
            tempo = 1;
            fmPointers = new int[] {0};
            fmKeyOffsets = new int[] {0};
            fmVolumeOffsets = new int[] {0};
        }

        @Override public byte[] getVoice(int voiceId) { return new byte[0]; }
        @Override public byte[] getPsgEnvelope(int id) { return new byte[0]; }
        @Override public int read16(int offset) { return 0; }
        @Override public int getBaseNoteOffset() { return 0; }
        @Override public String toString() { return name; }
    }
}
