package com.openggf.audio.driver;

import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSfxData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestPsgSfxAdmissionNoiseSilence {
    @ParameterizedTest
    @ValueSource(ints = {0x80, 0xA0})
    void retailPsgAdmissionSilencesNoiseForPsgOneAndTwo(int channel) {
        // zGetSFXChannelPointers.is_psg has no PSG3 condition (:2131-2136).
        assertEquals(List.of(0xFF), admitPsg(channel, Sonic3kSmpsSequencerConfig.CONFIG));
    }

    @ParameterizedTest
    @ValueSource(ints = {0x80, 0xA0})
    void defaultProfileDoesNotAddAdmissionWrites(int channel) {
        assertEquals(List.of(), admitPsg(channel, new SmpsSequencerConfig.Builder().build()));
    }

    private List<Integer> admitPsg(int channel, SmpsSequencerConfig config) {
        // One silent synthetic header pointing at F2. Separate admissions
        // avoid implying that consecutive PSG headers' stale-IX writes are modelled.
        byte[] bytes = {0, 0, 1, 1,
                (byte) 0x80, (byte) channel, 10, 0, 0, 0, (byte) 0xF2};
        List<Integer> writes = new ArrayList<>();
        SmpsDriver driver = SmpsDriverTestAccess.create(44_100, new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
            }

            @Override
            public void onPsgWrite(int value) {
                writes.add(value);
            }
        });
        try {
            SmpsSequencer sfx = new SmpsSequencer(
                    new Sonic3kSfxData(bytes, 0, 0, 0), new DacData(Map.of(), Map.of()),
                    driver, () -> { }, config);
            driver.addSequencer(sfx, true);
            return writes;
        } finally {
            SmpsDriverTestAccess.close(driver);
        }
    }
}
