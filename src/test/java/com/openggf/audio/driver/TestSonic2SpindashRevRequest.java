package com.openggf.audio.driver;

import com.openggf.audio.AudioManager;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.data.Rom;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSonic2SpindashRevRequest {
    private static final int SPINDASH_REV = 0xE0;

    @Test
    void repeatedRevRequestsUseTheDriversBoundedSemitoneLadder()
            throws Exception {
        Fixture fixture = fixture();

        for (int index = 0; index < 14; index++) {
            SmpsSequencer rev = fixture.newRev();
            fixture.driver.addSequencer(rev, true);
            assertEquals(-2 + Math.min(index, 11),
                    rev.trackAt(0).keyOffset);
        }
    }

    @Test
    void sixtyDriverServicesResetTheNextRevToItsBaseTranspose()
            throws Exception {
        Fixture fixture = fixture();
        fixture.driver.addSequencer(fixture.newRev(), true);
        SmpsSequencer second = fixture.newRev();
        fixture.driver.addSequencer(second, true);
        assertEquals(-1, second.trackAt(0).keyOffset);

        fixture.driver.read(new short[60 * 2]);

        SmpsSequencer reset = fixture.newRev();
        fixture.driver.addSequencer(reset, true);
        assertEquals(-2, reset.trackAt(0).keyOffset);
    }

    @Test
    void rewindSnapshotPreservesTheDriverOwnedLadderState() throws Exception {
        Fixture fixture = fixture();
        fixture.driver.addSequencer(fixture.newRev(), true);
        fixture.driver.addSequencer(fixture.newRev(), true);

        SmpsDriver restored = new SmpsDriver(60.0);
        restored.restoreSnapshot(fixture.driver.captureSnapshot());
        SmpsSequencer third = new SmpsSequencer(
                fixture.data, fixture.dac, restored,
                AudioManager.getInstance(), Sonic2SmpsSequencerConfig.CONFIG);
        third.setSfxPriority(0x70);
        third.setSampleRate(60.0);
        restored.addSequencer(third, true);

        assertEquals(0, third.trackAt(0).keyOffset);
    }

    private static Fixture fixture() throws Exception {
        String romPath = System.getProperty("sonic2.rom.path");
        if (romPath == null || romPath.isBlank()) {
            throw new IllegalStateException("sonic2.rom.path is required");
        }
        Rom rom = new Rom();
        if (!rom.open(romPath)) {
            throw new IllegalStateException("failed to open Sonic 2 ROM");
        }
        Sonic2SmpsLoader loader = new Sonic2SmpsLoader(rom);
        return new Fixture(new SmpsDriver(60.0),
                loader.loadSfx(SPINDASH_REV), loader.loadDacData());
    }

    private record Fixture(
            SmpsDriver driver, AbstractSmpsData data, DacData dac) {
        private SmpsSequencer newRev() {
            SmpsSequencer sequencer = new SmpsSequencer(
                    data, dac, driver, AudioManager.getInstance(),
                    Sonic2SmpsSequencerConfig.CONFIG);
            sequencer.setSfxPriority(0x70);
            sequencer.setSampleRate(60.0);
            return sequencer;
        }
    }
}
