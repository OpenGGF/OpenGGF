package com.openggf.audio.session;

import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.audio.synth.FmCoreSelection;
import com.openggf.audio.synth.NativeCompatiblePcmOutput;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsPhysicalPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The S3K SEGA PCM loop's byte cadence on the live presentation's hardware-frame budget.
 *
 * <p>The ROM's loop costs 248 Z80 cycles per byte ({@code zPlaySEGAPCM}; see
 * {@link TestSmpsSegaPcmTransport}). One presentation of 800 samples at 48 kHz and 60 Hz is one NTSC hardware
 * frame, 3420 * 262 = 896,040 master clocks = 59,736 Z80 cycles (master / 15). Byte {@code k} is therefore due
 * {@code k * 248} Z80 cycles after the loop is entered, and it is written in the first one-sample render whose
 * start is at or after that time: render {@code ceil(k * 248 * 800 / 59,736)}. Before the transport took the
 * exact rational synthesis rate (357,954,500 / 7,467 Hz), it rounded that rate to 47,938 Hz, a 4 ppm error that
 * moves later bytes across sample boundaries.
 */
class TestSmpsSegaPcmTransportCadence {

    private static final int SAMPLE_RATE = 48_000;
    private static final int FRAME_RATE = 60;
    private static final int SAMPLES_PER_PRESENTATION = SAMPLE_RATE / FRAME_RATE;
    private static final long Z80_CYCLES_PER_HARDWARE_FRAME = 3420L * 262 / 15;
    private static final int Z80_CYCLES_PER_BYTE = 248;
    private static final int BYTES = 24_000;

    @Test
    void bytesLandAtTheExactRationalZ80CadenceOnTheProductionBudget() {
        assertEquals(Z80_CYCLES_PER_BYTE,
                Sonic3kSmpsPhysicalPolicy.INSTANCE.segaPcmTransport().orElseThrow().z80CyclesPerByte());
        List<Integer> frames = byteRenderIndices(productionSettings(null), BYTES);
        assertEquals(BYTES, frames.size());
        for (int k = 0; k < BYTES; k++) {
            long due = ceilDiv((long) k * Z80_CYCLES_PER_BYTE * SAMPLES_PER_PRESENTATION,
                    Z80_CYCLES_PER_HARDWARE_FRAME);
            assertEquals(due, (long) frames.get(k), "byte " + k);
        }
    }

    @Test
    void wholeHertzRoundingOfTheSameRateMovesBytes() {
        // Control: the pre-correction configuration (the rounded double rate) misplaces bytes.
        SmpsPhysicalDevice.Settings rounded = new SmpsPhysicalDevice.Settings(
                SmpsHardwareFrameBudget.synthesisRate(SAMPLE_RATE, FRAME_RATE, false), false,
                FmCoreSelection.ACCURATE);
        assertNotEquals(byteRenderIndices(productionSettings(null), BYTES), byteRenderIndices(rounded, BYTES));
    }

    @Test
    void anInFlightTransportKeepsItsPhaseAcrossRestore() {
        Recording recording = session(productionSettings(null));
        recording.session.install();
        recording.session.beginSegaPcmTransport(pcm(4_000));
        short[] target = new short[2];
        for (int i = 0; i < 1_001; i++) {
            recording.render(target);
        }
        var physical = recording.session.captureSnapshot();
        var logical = recording.session.captureLogicalSnapshot();
        List<Integer> uninterrupted = recording.runToEnd(target);
        recording.session.commitRestore(recording.session.prepareRestore(physical, logical, ignored -> null));
        recording.frame = 1_001;
        List<Integer> restored = recording.runToEnd(target);
        assertEquals(uninterrupted, restored, "byte phase continues identically after a restore");
    }

    @Test
    void theTransportReachesTheNativeCompatibleStageOneHardwareFramePerPresentation() {
        Recording recording = session(productionSettings(
                NativeCompatiblePcmOutput.Config.withFilter(NativeCompatiblePcmOutput.Filter.NONE)));
        recording.session.install();
        recording.session.beginSegaPcmTransport(pcm(BYTES));
        short[] presentation = new short[SAMPLES_PER_PRESENTATION * 2];
        short[] drained = new short[2 * 4096];
        boolean audible = false;
        int presentations = 0;
        while (recording.session.segaPcmTransportActive()) {
            recording.session.renderFrames(presentation, 0, SAMPLES_PER_PRESENTATION);
            presentations++;
            int count = recording.session.drainNativeCompatiblePcm(drained, 0, drained.length / 2);
            for (int i = 0; i < count * 2 && !audible; i++) {
                audible = drained[i] != 0;
            }
        }
        assertTrue(audible, "the SEGA PCM bytes reach the stage through the chip's DAC");
        long frames = recording.session.nativeCompatibleFramesEnded();
        assertTrue(frames == presentations || frames == presentations - 1,
                presentations + " presentations ended " + frames + " NTSC frames");
    }

    private static SmpsPhysicalDevice.Settings productionSettings(NativeCompatiblePcmOutput.Config stage) {
        return SmpsHardwareFrameBudget.presentationSettings(SAMPLE_RATE, FRAME_RATE, false, false,
                FmCoreSelection.ACCURATE, stage);
    }

    private static byte[] pcm(int length) {
        byte[] pcm = new byte[length];
        for (int i = 0; i < length; i++) {
            pcm[i] = (byte) (0x80 + (i % 2 == 0 ? 40 : -40));
        }
        return pcm;
    }

    private static List<Integer> byteRenderIndices(SmpsPhysicalDevice.Settings settings, int bytes) {
        Recording recording = session(settings);
        recording.session.install();
        recording.session.beginSegaPcmTransport(pcm(bytes));
        return recording.runToEnd(new short[2]).subList(0, bytes);
    }

    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }

    private static Recording session(SmpsPhysicalDevice.Settings settings) {
        Recording recording = new Recording();
        recording.session = new SmpsDriverSession(settings, Sonic3kSmpsPhysicalPolicy.INSTANCE, recording,
                new SmpsSessionProfileFingerprint("s3k-sega-cadence", 1,
                        Sonic3kSmpsPhysicalPolicy.INSTANCE.identity(), settings),
                SmpsDriverSessionConfiguration.DEFAULT);
        return recording;
    }

    /** Records the one-sample render index of every SEGA sample byte ({@code 2Ah} data). */
    private static final class Recording implements ChipWriteObserver {
        SmpsDriverSession session;
        int frame;
        final List<Integer> byteFrames = new ArrayList<>();

        @Override
        public void onYm2612Write(int port, int register, int value) {
            if (register == 0x2A && session != null && session.segaPcmTransportActive()) {
                byteFrames.add(frame);
            }
        }

        @Override
        public void onPsgWrite(int value) {
        }

        void render(short[] target) {
            session.renderFrames(target, 0, 1);
            frame++;
        }

        List<Integer> runToEnd(short[] target) {
            byteFrames.clear();
            while (session.segaPcmTransportActive()) {
                render(target);
            }
            assertFalse(session.segaPcmTransportActive());
            return new ArrayList<>(byteFrames);
        }
    }
}
