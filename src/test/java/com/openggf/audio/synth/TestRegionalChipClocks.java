package com.openggf.audio.synth;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.smps.SmpsSequencer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestRegionalChipClocks {
    @Test
    void driverRegionSelectsTheRetailMasterClockDivisors() {
        SmpsDriver driver = new SmpsDriver();

        driver.setRegion(SmpsSequencer.Region.PAL);
        assertEquals(53_203_424.0 / 7.0,
                driver.ymChipClockForTesting(), 0.000_001);
        assertEquals(3_546_893.0,
                driver.psgInputClockForTesting(), 0.000_001);

        driver.setRegion(SmpsSequencer.Region.NTSC);
        assertEquals(7_670_453.0,
                driver.ymChipClockForTesting(), 0.000_001);
        assertEquals(3_579_545.0,
                driver.psgInputClockForTesting(), 0.000_001);
    }

    @Test
    void synthSnapshotRestoresTheClockDomainAndDerivedRates() {
        SmpsDriver driver = new SmpsDriver();
        driver.setOutputSampleRate(48_000.0);
        driver.setRegion(SmpsSequencer.Region.PAL);
        VirtualSynthesizer.Snapshot pal = driver.captureSynthSnapshot();

        driver.setRegion(SmpsSequencer.Region.NTSC);
        driver.restoreSynthSnapshot(pal);

        assertEquals(53_203_424.0 / 7.0,
                driver.ymChipClockForTesting(), 0.000_001);
        assertEquals(3_546_893.0,
                driver.psgInputClockForTesting(), 0.000_001);
        assertEquals((53_203_424.0 / 7.0 / 144.0) / 48_000.0,
                driver.captureSynthSnapshot().ym().resampleRatio(),
                0.000_000_001);
    }

    @Test
    void palClockProducesDifferentHardwareSamplesFromTheSamePsgPeriod() {
        SmpsDriver ntsc = new SmpsDriver();
        SmpsDriver pal = new SmpsDriver();
        ntsc.setOutputSampleRate(48_000.0);
        pal.setOutputSampleRate(48_000.0);
        ntsc.setRegion(SmpsSequencer.Region.NTSC);
        pal.setRegion(SmpsSequencer.Region.PAL);
        for (SmpsDriver driver : new SmpsDriver[] {ntsc, pal}) {
            driver.writePsg(driver, 0x84);
            driver.writePsg(driver, 0x12);
            driver.writePsg(driver, 0x90);
        }
        short[] ntscSamples = new short[1_024];
        short[] palSamples = new short[1_024];

        ntsc.renderFrames(ntscSamples, 0, ntscSamples.length / 2);
        pal.renderFrames(palSamples, 0, palSamples.length / 2);

        assertFalse(Arrays.equals(ntscSamples, palSamples),
                "PAL PSG pitch must derive from the PAL master clock");
    }
}
