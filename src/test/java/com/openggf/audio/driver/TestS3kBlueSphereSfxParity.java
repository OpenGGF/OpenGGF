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
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kBlueSphereSfxParity {

    @Test
    void admissionPreparesFm5OneVintBeforeTheFirstSfxService() {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        AbstractSmpsData data = loader.loadSfx(Sonic3kSfx.BLUE_SPHERE.id);
        SmpsDriver driver = new SmpsDriver();
        List<String> writes = new ArrayList<>();
        driver.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                writes.add("%d:%02X:%02X".formatted(port, register, value));
            }

            @Override
            public void onPsgWrite(int value) {
            }
        });

        admit(driver, data, loader);
        assertTrue(writes.contains("0:28:05"),
                "admission keys off FM5 immediately");
        writes.clear();

        driver.read(new short[735 * 2]);
        assertTrue(writes.isEmpty(),
                "the admission VInt has already run before zPlaySound");

        driver.read(new short[735 * 2]);
        assertTrue(writes.containsAll(List.of(
                        "1:81:FF", "1:85:FF", "1:89:FF", "1:8D:FF")),
                "the following VInt executes cfSetVoice and starts the SFX");
    }

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

        SmpsSequencer sequencer = admit(driver, data, loader);
        driver.read(new short[24_000]);
        List<Integer> first = List.copyOf(fm5CarrierLevels);
        assertEquals(-0x30, sequencer.trackAt(0).modCurrentDelta,
                "Sound_65's $D0 modulation delta is sign-extended by zDoModulation");

        driver.read(new short[246]);
        fm5CarrierLevels.clear();
        admit(driver, data, loader);
        driver.read(new short[24_000]);

        assertEquals(List.of(5, 5, 5, 10, 10, 10), first,
                "Sound_65 starts at header attenuation 5 then applies its intentional +5 delta");
        assertEquals(first, fm5CarrierLevels,
                "same-ID retrigger must not inherit the previous track's attenuation");
    }

    @Test
    void firstBlueSphereNoteWritesOnlyTheFinalModulatedFrequency() {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        AbstractSmpsData data = loader.loadSfx(Sonic3kSfx.BLUE_SPHERE.id);
        SmpsDriver driver = new SmpsDriver();
        List<String> writes = new ArrayList<>();
        driver.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                if (port == 1 && (register == 0xB5
                        || register == 0xA5 || register == 0xA1)) {
                    writes.add("%02X:%02X".formatted(register, value));
                }
            }

            @Override
            public void onPsgWrite(int value) {
            }
        });

        admit(driver, data, loader);
        driver.read(new short[735 * 2]);
        driver.read(new short[735 * 2]);

        assertEquals(List.of("B5:C0", "A5:23", "A1:3F"), writes,
                "zUpdateFMorPSGTrack applies modulation before its sole "
                        + "zFMSendFreq write");
    }

    private static SmpsSequencer admit(
            SmpsDriver driver,
            AbstractSmpsData data,
            Sonic3kSmpsLoader loader) {
        SmpsSequencer sequencer = new SmpsSequencer(
                data,
                loader.loadDacData(),
                driver,
                Sonic3kSmpsSequencerConfig.CONFIG);
        driver.addSequencer(sequencer, true);
        return sequencer;
    }
}
