package com.openggf.audio.driver;

import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.AudioManager;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestSmpsDriverSnapshot {

    @Test
    void captureAndRestoreRoundTripsSequencersLocksLatchesAndContinuousSfxState() {
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer music = newSequencer("music", 0x81, driver);
        SmpsSequencer sfx = newSequencer("sfx", 0xBC, driver);

        driver.addSequencer(music, false);
        driver.addSequencer(sfx, true);
        driver.startContinuousSfx(0xBC, 3);
        assertTrue(driver.extendContinuousSfx(0xBC, 3));
        driver.decrementContSfxLoopCnt();
        driver.writeFm(sfx, 0, 0xA0, 0x22);
        driver.writePsg(sfx, 0x80 | (2 << 5) | 0x04);
        driver.writePsg(sfx, 0x06);

        SmpsDriverSnapshot snapshot = driver.captureSnapshot();

        driver.stopAll();
        driver.restoreSnapshot(snapshot);

        SmpsDriverSnapshot restored = driver.captureSnapshot();
        assertEquals(2, restored.sequencers().size());
        assertFalse(restored.sequencers().get(0).sfx());
        assertTrue(restored.sequencers().get(1).sfx());
        assertEquals(1, restored.fmLockSequencerIds()[0]);
        assertEquals(1, restored.psgLockSequencerIds()[2]);
        assertEquals(2, restored.sequencers().get(1).snapshot().psgLatchChannel());
        assertEquals(0xBC, restored.continuousSfxId());
        assertTrue(restored.continuousSfxFlag());
        assertEquals(2, restored.contSfxLoopCnt());
    }

    private static SmpsSequencer newSequencer(String name, int id, SmpsDriver driver) {
        AbstractSmpsData data = new AudioTestFixtures.StubSmpsData(name);
        data.setId(id);
        return new SmpsSequencer(
                data,
                AudioTestFixtures.EMPTY_DAC,
                driver,
                AudioManager.getInstance(),
                new SmpsSequencerConfig.Builder().build());
    }
}
