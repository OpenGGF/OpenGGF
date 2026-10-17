package com.openggf.audio.synth;

import com.openggf.audio.synth.blip.BlipBuf;

import java.util.Arrays;

/**
 * PSG half of {@link NativeCompatiblePcmOutput}: the Mega Drive's integrated
 * SN76489 output as the BizHawk Genesis Plus GX core produces it, driven by
 * the production session's own PSG data-bus bytes at the master-clock time
 * they reach the chip. Modelled from the documented behaviour of GPGX
 * {@code core/sound/psg.c} (BizHawk {@code 427556b5}); no text of that file is
 * copied (it carries a non-commercial licence). Citations are to that file.
 *
 * <ul>
 *   <li>Clock: one generator step per 15 * 16 = 240 master clocks
 *       ({@code PSG_MCYCLES_RATIO}, :47). Integrated part
 *       ({@code PSG_INTEGRATED}, sound.c:357): 15-bit noise shift width,
 *       feedback mask {@code 0x9}, a zero tone period behaves as 1 (:96-112).</li>
 *   <li>Levels: {@code 2800 * 10^(-2k/20)} truncated to 16 bits for
 *       attenuation {@code k < 15}, zero for 15 (:49-68), times
 *       {@code psg_preamp / 100} with BizHawk's preamp 150 on both sides
 *       (cinterface.c:844, {@code psg_config(0, 150, 0xff)}, sound.c:365).</li>
 *   <li>Output: each channel contributes its level while its generator is
 *       high. Every tone flip and every noise shift-register output change is
 *       a band-limited delta at its own master-clock time; volume changes made
 *       while a channel is high are applied as one delta at the next
 *       240-cycle boundary (:196-229, :455-569).</li>
 *   <li>Writes: a write first runs the generators to its time, then latches
 *       or updates the register; tone 3 drives the noise clock when the noise
 *       rate is {@code 11}; any noise-control write resets the shift register
 *       (:230-378). The frame end subtracts the frame length from every
 *       counter (:440-453).</li>
 * </ul>
 */
final class NativeCompatiblePsgOutput {

    private static final int RATIO = 15 * 16;
    private static final int NOISE_SHIFT_WIDTH = 15;
    private static final int NOISE_BIT_MASK = 0x9;
    private static final int ZERO_FREQ_INC = RATIO;
    private static final int PREAMP = 150;
    private static final int[] NOISE_FEEDBACK = {0, 1, 1, 0, 1, 0, 0, 1, 1, 0};
    private static final int[] CHAN_VOLUME = new int[16];

    static {
        double[] factor = {1.0, 0.794328234, 0.630957344, 0.501187233, 0.398107170, 0.316227766,
                0.251188643, 0.199526231, 0.158489319, 0.125892541, 0.1, 0.079432823, 0.063095734,
                0.050118723, 0.039810717};
        for (int i = 0; i < factor.length; i++) {
            CHAN_VOLUME[i] = (int) (2800 * factor[i]) & 0xFFFF;
        }
        CHAN_VOLUME[15] = 0;
    }

    int clocks;
    int latch;
    int noiseShiftValue;
    final int[] regs = new int[8];
    final int[] freqInc = new int[4];
    final int[] freqCounter = new int[4];
    final int[] polarity = new int[4];
    final int[] chanDeltaLeft = new int[4];
    final int[] chanDeltaRight = new int[4];
    final int[] chanOutLeft = new int[4];
    final int[] chanOutRight = new int[4];

    NativeCompatiblePsgOutput() {
        reset();
    }

    /** Power-on state (:114-139) followed by {@code psg_config(0, 150, 0xff)}: all levels zero. */
    void reset() {
        for (int i = 0; i < 4; i++) {
            regs[i * 2] = 0;
            regs[i * 2 + 1] = 0;
            freqInc[i] = i < 3 ? ZERO_FREQ_INC : 16 * RATIO;
            freqCounter[i] = 0;
            polarity[i] = -1;
            chanDeltaLeft[i] = chanDeltaRight[i] = 0;
            chanOutLeft[i] = chanOutRight[i] = 0;
        }
        latch = 3;
        noiseShiftValue = 1 << NOISE_SHIFT_WIDTH;
        clocks = 0;
    }

    private static int amplified(int volume) {
        return volume * PREAMP / 100;
    }

    private void sync(BlipBuf blip, int time) {
        if (time > clocks) {
            update(blip, time);
            clocks += ((time - clocks + RATIO - 1) / RATIO) * RATIO;
        }
    }

    void write(BlipBuf blip, int time, int data) {
        sync(blip, time);
        int index;
        if ((data & 0x80) != 0) {
            latch = index = (data >> 4) & 0x07;
        } else {
            index = latch;
        }
        switch (index) {
            case 0, 2, 4 -> {
                data = (data & 0x80) != 0
                        ? (regs[index] & 0x3f0) | (data & 0x0f)
                        : (regs[index] & 0x00f) | ((data & 0x3f) << 4);
                freqInc[index >> 1] = data != 0 ? data * RATIO : ZERO_FREQ_INC;
                if (index == 4 && (regs[6] & 0x03) == 0x03) {
                    freqInc[3] = freqInc[2];
                }
            }
            case 6 -> {
                int noiseFreq = data & 0x03;
                if (noiseFreq == 0x03) {
                    freqInc[3] = freqInc[2];
                    freqCounter[3] = freqCounter[2];
                } else {
                    freqInc[3] = (0x10 << noiseFreq) * RATIO;
                }
                if ((noiseShiftValue & 1) != 0) {
                    chanDeltaLeft[3] -= chanOutLeft[3];
                    chanDeltaRight[3] -= chanOutRight[3];
                }
                noiseShiftValue = 1 << NOISE_SHIFT_WIDTH;
            }
            default -> {
                int channel = index >> 1;
                data = CHAN_VOLUME[data & 0x0f];
                int left = amplified(data);
                int right = amplified(data);
                boolean high = channel == 3 ? (noiseShiftValue & 1) != 0 : polarity[channel] > 0;
                if (high) {
                    chanDeltaLeft[channel] += left - chanOutLeft[channel];
                    chanDeltaRight[channel] += right - chanOutRight[channel];
                }
                chanOutLeft[channel] = left;
                chanOutRight[channel] = right;
            }
        }
        regs[index] = data;
    }

    void endFrame(BlipBuf blip, int frameClocks) {
        sync(blip, frameClocks);
        clocks -= frameClocks;
        for (int i = 0; i < 4; i++) {
            freqCounter[i] -= frameClocks;
        }
    }

    private void update(BlipBuf blip, int time) {
        for (int i = 0; i < 4; i++) {
            if ((chanDeltaLeft[i] | chanDeltaRight[i]) != 0) {
                blip.addDelta(clocks, chanDeltaLeft[i], chanDeltaRight[i]);
                chanDeltaLeft[i] = 0;
                chanDeltaRight[i] = 0;
            }
            int timestamp = freqCounter[i];
            int pol = polarity[i];
            if (i < 3) {
                while (timestamp < time) {
                    pol = -pol;
                    blip.addDelta(timestamp, pol * chanOutLeft[i], pol * chanOutRight[i]);
                    timestamp += freqInc[i];
                }
            } else {
                int shiftValue = noiseShiftValue;
                while (timestamp < time) {
                    pol = -pol;
                    if (pol > 0) {
                        int shiftOutput = shiftValue & 0x01;
                        if ((regs[6] & 0x04) != 0) {
                            shiftValue = (shiftValue >> 1)
                                    | (NOISE_FEEDBACK[shiftValue & NOISE_BIT_MASK] << NOISE_SHIFT_WIDTH);
                        } else {
                            shiftValue = (shiftValue >> 1) | (shiftOutput << NOISE_SHIFT_WIDTH);
                        }
                        shiftOutput = (shiftValue & 0x1) - shiftOutput;
                        blip.addDelta(timestamp, shiftOutput * chanOutLeft[3], shiftOutput * chanOutRight[3]);
                    }
                    timestamp += freqInc[3];
                }
                noiseShiftValue = shiftValue;
            }
            freqCounter[i] = timestamp;
            polarity[i] = pol;
        }
    }

    /** Complete immutable state. */
    record State(int clocks, int latch, int noiseShiftValue, int[] regs, int[] freqInc, int[] freqCounter,
            int[] polarity, int[] chanDeltaLeft, int[] chanDeltaRight, int[] chanOutLeft, int[] chanOutRight) {
        State {
            regs = regs.clone();
            freqInc = freqInc.clone();
            freqCounter = freqCounter.clone();
            polarity = polarity.clone();
            chanDeltaLeft = chanDeltaLeft.clone();
            chanDeltaRight = chanDeltaRight.clone();
            chanOutLeft = chanOutLeft.clone();
            chanOutRight = chanOutRight.clone();
        }

        @Override
        public boolean equals(Object candidate) {
            return candidate instanceof State o && clocks == o.clocks && latch == o.latch
                    && noiseShiftValue == o.noiseShiftValue && Arrays.equals(regs, o.regs)
                    && Arrays.equals(freqInc, o.freqInc) && Arrays.equals(freqCounter, o.freqCounter)
                    && Arrays.equals(polarity, o.polarity) && Arrays.equals(chanDeltaLeft, o.chanDeltaLeft)
                    && Arrays.equals(chanDeltaRight, o.chanDeltaRight) && Arrays.equals(chanOutLeft, o.chanOutLeft)
                    && Arrays.equals(chanOutRight, o.chanOutRight);
        }

        @Override
        public int hashCode() {
            int result = 31 * clocks + latch;
            result = 31 * result + noiseShiftValue;
            for (int[] array : new int[][] {regs, freqInc, freqCounter, polarity, chanDeltaLeft, chanDeltaRight,
                    chanOutLeft, chanOutRight}) {
                result = 31 * result + Arrays.hashCode(array);
            }
            return result;
        }
    }

    State captureState() {
        return new State(clocks, latch, noiseShiftValue, regs, freqInc, freqCounter, polarity, chanDeltaLeft,
                chanDeltaRight, chanOutLeft, chanOutRight);
    }

    void restoreState(State state) {
        clocks = state.clocks();
        latch = state.latch();
        noiseShiftValue = state.noiseShiftValue();
        System.arraycopy(state.regs, 0, regs, 0, 8);
        System.arraycopy(state.freqInc, 0, freqInc, 0, 4);
        System.arraycopy(state.freqCounter, 0, freqCounter, 0, 4);
        System.arraycopy(state.polarity, 0, polarity, 0, 4);
        System.arraycopy(state.chanDeltaLeft, 0, chanDeltaLeft, 0, 4);
        System.arraycopy(state.chanDeltaRight, 0, chanDeltaRight, 0, 4);
        System.arraycopy(state.chanOutLeft, 0, chanOutLeft, 0, 4);
        System.arraycopy(state.chanOutRight, 0, chanOutRight, 0, 4);
    }

    void copyFrom(NativeCompatiblePsgOutput source) {
        restoreState(source.captureState());
    }
}
