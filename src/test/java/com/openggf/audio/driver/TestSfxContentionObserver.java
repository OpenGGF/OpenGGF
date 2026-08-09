package com.openggf.audio.driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        SmpsSequencer first = sequencer("first", 0xA0, driver);
        SmpsSequencer second = sequencer("second", 0xA1, driver);
        first.setSfxPriority(0x20);
        second.setSfxPriority(0x60);
        driver.addSequencer(music, false);
        driver.addSequencer(first, true);
        driver.writeFm(first, 0, 0xA2, 0x22);
        driver.addSequencer(second, true);
        driver.writeFm(second, 0, 0xA2, 0x44);

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
    }

    private static SmpsSequencer sequencer(String name, int id, SmpsDriver driver) {
        AbstractSmpsData data = new AudioTestFixtures.StubSmpsData(name);
        data.setId(id);
        return new SmpsSequencer(data, AudioTestFixtures.EMPTY_DAC, driver,
                AudioManager.getInstance(), new SmpsSequencerConfig.Builder().build());
    }
}
