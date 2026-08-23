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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCollapseDashSfxParity {

    @Test
    void collapsePsgRunsFiveTwentyFourTickBurstsThenStops() {
        Fixture fixture = fixture(Sonic3kSfx.COLLAPSE);
        assertEquals(4, fixture.sequencer.trackCount());
        SmpsSequencer.Track psg3 = fixture.sequencer.trackAt(3);
        List<Integer> burstVolumes = new ArrayList<>();

        int frames = runToCompletion(fixture, () -> {
            if (psg3.active && psg3.duration == 0x18) {
                burstVolumes.add(psg3.volumeOffset);
            }
        });

        assertEquals(List.of(0, 3, 6, 9, 12), burstVolumes,
                "Sound_59 loops the tied PSG3 note exactly five times");
        assertEquals(0x0F, psg3.volumeOffset,
                "the terminal EC +3 reaches native silence before F2");
        assertEquals(122, frames,
                "request/load frame plus five 24-tick bursts match the Z80 lifecycle");
    }

    @Test
    void collapsePsgChipWritesBeginLikeTheNativeDriver() {
        Fixture fixture = fixture(Sonic3kSfx.COLLAPSE);

        // The first engine service is the native request/admission frame. Its
        // defensive PSG takeover writes are intentionally outside the SFX
        // track-update stream compared below.
        fixture.driver.read(new short[735 * 2]);
        fixture.observer.takePsgWrites();

        fixture.driver.read(new short[735 * 2]);
        assertEquals(List.of(0xDF, 0xE7, 0xC8, 0x04, 0xF0),
                fixture.observer.takePsgWrites());

    }

    @Test
    void dashPsgChipWritesBeginLikeTheNativeDriver() {
        Fixture fixture = fixture(Sonic3kSfx.DASH);

        fixture.driver.read(new short[735 * 2]);
        fixture.observer.takePsgWrites();
        for (int frame = 1; frame <= 6; frame++) {
            fixture.driver.read(new short[735 * 2]);
            fixture.observer.takePsgWrites();
        }
        fixture.driver.read(new short[735 * 2]);
        assertEquals(List.of(0xDF, 0xE7, 0xC6, 0x01, 0xF0),
                fixture.observer.takePsgWrites());
    }

    @Test
    void collapseEffectivePsgStateMatchesEveryNativeFrame() {
        assertEquals(
                "d85bbd997725b5804d5990cb222f13a1c367ce2e76b628ab5ec61c515d81c584",
                effectivePsgDigest(Sonic3kSfx.COLLAPSE, 124));
    }

    @Test
    void dashEffectivePsgStateMatchesEveryNativeFrame() {
        assertEquals(
                "0b7d78978c85bc7c021789c333594b96f905bbf2e64f1b2b3921751f2af1e093",
                effectivePsgDigest(Sonic3kSfx.DASH, 89));
    }

    @Test
    void dashPsgRestsSixTicksThenRunsOneSeventyNineTickNote() {
        Fixture fixture = fixture(Sonic3kSfx.DASH);
        assertEquals(2, fixture.sequencer.trackCount());
        SmpsSequencer.Track psg3 = fixture.sequencer.trackAt(1);
        List<Integer> startedDurations = new ArrayList<>();
        int[] priorDuration = {psg3.duration};

        int frames = runToCompletion(fixture, () -> {
            if (psg3.active && psg3.duration > priorDuration[0]) {
                startedDurations.add(psg3.duration);
            }
            priorDuration[0] = psg3.duration;
        });

        assertEquals(List.of(0x06, 0x4F), startedDurations,
                "Sound_B6 preserves its rest then its one modulated PSG note");
        assertTrue(psg3.envHold,
                "native tone 1D reaches its hold byte before track end");
        assertEquals(87, frames,
                "request/load frame plus 6 rest ticks and 79 note ticks match the Z80 lifecycle");
    }

    private static int runToCompletion(Fixture fixture, Runnable afterFrame) {
        for (int frame = 1; frame <= 512; frame++) {
            fixture.driver.read(new short[735 * 2]);
            afterFrame.run();
            if (fixture.sequencer.isComplete()) {
                return frame;
            }
        }
        throw new AssertionError("SFX remained active beyond 512 driver frames");
    }

    private static Fixture fixture(Sonic3kSfx sfx) {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        AbstractSmpsData data = loader.loadSfx(sfx.id);
        RecordingObserver observer = new RecordingObserver();
        SmpsDriver driver = new SmpsDriver(44_100.0, observer);
        SmpsSequencer sequencer = new SmpsSequencer(
                data, loader.loadDacData(), driver,
                Sonic3kSmpsSequencerConfig.CONFIG);
        driver.addSequencer(sequencer, true);
        return new Fixture(driver, sequencer, observer);
    }

    private static String effectivePsgDigest(
            Sonic3kSfx sfx, int frames) {
        Fixture fixture = fixture(sfx);
        StringBuilder rows = new StringBuilder();
        for (int frame = 0; frame < frames; frame++) {
            fixture.driver.read(new short[735 * 2]);
            rows.append(fixture.observer.effectiveState(frame));
        }
        return sha256(rows);
    }

    private static String sha256(StringBuilder rows) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rows.toString().getBytes(StandardCharsets.US_ASCII));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private record Fixture(
            SmpsDriver driver,
            SmpsSequencer sequencer,
            RecordingObserver observer) {
    }

    private static final class RecordingObserver implements ChipWriteObserver {
        private final List<Integer> psgWrites = new ArrayList<>();
        private int latchedChannel = -1;
        private boolean latchedVolume;
        private int tone2Period;
        private int noise;
        private int tone2Volume = 0x0F;
        private int noiseVolume = 0x0F;

        @Override
        public void onYm2612Write(int port, int register, int value) {
        }

        @Override
        public void onPsgWrite(int value) {
            value &= 0xFF;
            psgWrites.add(value);
            if ((value & 0x80) != 0) {
                latchedChannel = (value >> 5) & 3;
                latchedVolume = (value & 0x10) != 0;
                if (latchedVolume) {
                    if (latchedChannel == 2) {
                        tone2Volume = value & 0x0F;
                    } else if (latchedChannel == 3) {
                        noiseVolume = value & 0x0F;
                    }
                } else if (latchedChannel == 2) {
                    tone2Period = (tone2Period & 0x3F0) | (value & 0x0F);
                } else if (latchedChannel == 3) {
                    noise = value & 7;
                }
            } else if (!latchedVolume && latchedChannel == 2) {
                tone2Period = (tone2Period & 0x0F) | ((value & 0x3F) << 4);
            }
        }

        private List<Integer> takePsgWrites() {
            List<Integer> result = List.copyOf(psgWrites);
            psgWrites.clear();
            return result;
        }

        private String effectiveState(int frame) {
            return frame + ":" + tone2Period + ":" + noise + ":"
                    + tone2Volume + ":" + noiseVolume + "\n";
        }

    }
}
