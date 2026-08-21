package com.openggf.audio.driver;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsLoader;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kBlueSphereSfxParity {

    @Test
    void repeatedBlueSphereAdmissionsRestartTheExactVolumeSequence() {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        AbstractSmpsData data = loader.loadSfx(Sonic3kSfx.BLUE_SPHERE.id);
        assertNotNull(data);

        SmpsDriver driver = new SmpsDriver();
        List<Integer> fm5CarrierLevels = new ArrayList<>();
        driver.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                if (port == 1 && (register == 0x49
                        || register == 0x45 || register == 0x4D)) {
                    fm5CarrierLevels.add(value);
                }
            }

            @Override
            public void onPsgWrite(int value) {
            }
        });

        admit(driver, data, loader);
        driver.read(new short[24_000]);
        List<Integer> first = List.copyOf(fm5CarrierLevels);

        driver.read(new short[246]);
        fm5CarrierLevels.clear();
        admit(driver, data, loader);
        driver.read(new short[24_000]);

        assertEquals(List.of(5, 5, 5, 10, 10, 10), first,
                "Sound_65 starts at header attenuation 5 then applies its intentional +5 delta");
        assertEquals(first, fm5CarrierLevels,
                "same-ID retrigger must not inherit the previous track's attenuation");
    }

    private static void admit(
            SmpsDriver driver,
            AbstractSmpsData data,
            Sonic3kSmpsLoader loader) {
        SmpsSequencer sequencer = new SmpsSequencer(
                data,
                loader.loadDacData(),
                driver,
                Sonic3kSmpsSequencerConfig.CONFIG);
        driver.addSequencer(sequencer, true);
    }
}
