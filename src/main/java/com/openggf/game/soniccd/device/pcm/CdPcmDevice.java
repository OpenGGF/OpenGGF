package com.openggf.game.soniccd.device.pcm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 * Session-owned finite Sega CD PCM sample device (315-5476A / RF5C164 compatible).
 *
 * Semantics reference: Genesis Plus GX 051d430d3d1b54625f9900c8f152d7f232e06daf,
 * core/cd_hw/pcm.c, pcm_reset, pcm_run, pcm_write and pcm_read; pcm.h, chan_t / pcm_t.
 * This is an independent integer implementation of those register/sample rules. It runs no
 * SP/SPX/PCM driver, CPU, sub-CPU timer, frame clock, DMA controller, resampler or host worker.
 *
 * All accesses are explicitly at the caller's service boundary. GPGX pcm_read/pcm_write first
 * round positive elapsed sub-CPU cycles up to samples at 384 * 4 cycles per sample, then access
 * the chip. That synchronization belongs to a future service adapter, which must advance this
 * device BEFORE applying the access and retain its own rational clock remainder. These methods
 * do not infer elapsed time, grant readiness or make a register write advance a live clock.
 *
 * The chip byte window is 0000-1FFF, before CPU bus translation. The GPGX wave-RAM readback
 * behavior is retained, including its source TODO about physical read capability; this source
 * model is not independent hardware proof. Output is the native integer mix at unity gain.
 * GPGX config.pcm_volume, blip synthesis and output-rate conversion are external audio policy.
 * Instances require one simulation owner; no shared sink or background thread is installed.
 */
public final class CdPcmDevice {
    public static final int CHANNEL_COUNT = 8;
    public static final int WAVE_RAM_BYTES = 0x10000;
    public static final int BANK_BYTES = 0x1000;

    /** Allocation/work bound, without clock or cadence meaning. Larger work is explicitly chunked. */
    public static final int MAX_SAMPLES_PER_ADVANCE = 4096;

    private final byte[] waveRam = new byte[WAVE_RAM_BYTES];
    private final Channel[] channels = new Channel[CHANNEL_COUNT];
    private int enabledChannelMask;
    private int selectedChannel;
    private int selectedBank;
    private boolean chipEnabled;
    private int leftOutput;
    private int rightOutput;

    public CdPcmDevice() { reset(); }

    /** Reset the complete device, including external RAM, default FF panning and previous output. */
    public void reset() {
        Arrays.fill(waveRam, (byte) 0);
        for (int index = 0; index < CHANNEL_COUNT; index++) { channels[index] = new Channel(); }
        enabledChannelMask = selectedChannel = selectedBank = leftOutput = rightOutput = 0;
        chipEnabled = false;
    }

    public void writeRegisterAtBoundary(CdPcmRegister register, int unsignedByte) {
        Objects.requireNonNull(register, "register");
        writeAtBoundary(register.address(), unsignedByte);
    }

    /**
     * Apply one already-synchronized chip-byte access. Reserved internal writes are ignored;
     * out-of-window addresses and non-byte data are rejected, not silently wrapped.
     */
    public void writeAtBoundary(int chipByteAddress, int unsignedByte) {
        requireChipAddress(chipByteAddress);
        CdPcmState.requireUnsigned(unsignedByte, 0xff, "Write data");
        if (chipByteAddress >= BANK_BYTES) {
            waveRam[(selectedBank << 12) | (chipByteAddress & 0xfff)] = (byte) unsignedByte;
            return;
        }
        Channel channel = channels[selectedChannel];
        switch (chipByteAddress) {
            case 0 -> channel.envelope = unsignedByte;
            case 1 -> channel.pan = unsignedByte;
            case 2 -> channel.frequency = (channel.frequency & 0xff00) | unsignedByte;
            case 3 -> channel.frequency = (channel.frequency & 0x00ff) | (unsignedByte << 8);
            case 4 -> channel.loopAddress = (channel.loopAddress & 0xff00) | unsignedByte;
            case 5 -> channel.loopAddress = (channel.loopAddress & 0x00ff) | (unsignedByte << 8);
            case 6 -> {
                channel.startPage = unsignedByte;
                if ((enabledChannelMask & (1 << selectedChannel)) == 0) {
                    channel.addressFixed11 = (long) unsignedByte << 19;
                }
            }
            case 7 -> {
                if ((unsignedByte & 0x40) != 0) { selectedChannel = unsignedByte & 7; }
                else { selectedBank = unsignedByte & 15; }
                chipEnabled = (unsignedByte & 0x80) != 0;
            }
            case 8 -> {
                enabledChannelMask = (~unsignedByte) & 0xff;
                for (int index = 0; index < CHANNEL_COUNT; index++) {
                    if ((unsignedByte & (1 << index)) != 0) {
                        channels[index].addressFixed11 = (long) channels[index].startPage << 19;
                    }
                }
            }
            default -> { /* Reserved internal address: no register side effects. */ }
        }
    }

    /**
     * Banked RAM or the low/high integer address bytes at 10-1F; other internal reads return FF.
     * Fractional progress and accumulator overflow bits are not exposed in the address readback.
     */
    public int readAtBoundary(int chipByteAddress) {
        requireChipAddress(chipByteAddress);
        if (chipByteAddress >= BANK_BYTES) {
            return waveRam[(selectedBank << 12) | (chipByteAddress & 0xfff)] & 0xff;
        }
        if (chipByteAddress >= 0x10 && chipByteAddress < 0x20) {
            long address = channels[(chipByteAddress >>> 1) & 7].addressFixed11;
            return (int) (address >>> ((chipByteAddress & 1) == 0 ? 11 : 19)) & 0xff;
        }
        return 0xff;
    }

    /**
     * Bounded contiguous write into the selected 4 KiB bank. A crossing transfer must be split
     * explicitly by its owner; CDC DMA addressing/ownership is not implemented by this helper.
     */
    public void writeBankedRamAtBoundary(int bankOffset, byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        requireBankSlice(bankOffset, bytes.length);
        System.arraycopy(bytes, 0, waveRam, (selectedBank << 12) + bankOffset, bytes.length);
    }

    /** Return an owned copy; reads never service a clock or expose mutable device memory. */
    public byte[] readBankedRamAtBoundary(int bankOffset, int length) {
        requireBankSlice(bankOffset, length);
        int start = (selectedBank << 12) + bankOffset;
        return Arrays.copyOfRange(waveRam, start, start + length);
    }

    /**
     * Request semantic sample work. Zero is a strict no-op (as at GPGX's positive-clock service
     * checks); it does not call the low-level pcm_run(0) disabled-output special case. Register
     * writes, including chip disable, change no output until positive work is requested.
     * Each enabled step visits channels 0 through 7, shifts their individual signed products,
     * sums them in 32 bits, and clips once. Disabled steps freeze channels and mute output once.
     */
    public CdPcmOutput advanceSamples(int sampleCount) {
        if (sampleCount < 0 || sampleCount > MAX_SAMPLES_PER_ADVANCE) {
            throw new IllegalArgumentException("PCM work must contain 0 through " + MAX_SAMPLES_PER_ADVANCE + " samples");
        }
        var steps = new ArrayList<CdPcmOutput.Step>(sampleCount);
        for (int sample = 0; sample < sampleCount; sample++) {
            int left = 0;
            int right = 0;
            boolean hasDelta = chipEnabled || (leftOutput | rightOutput) != 0;
            if (chipEnabled) {
                for (int index = 0; index < CHANNEL_COUNT; index++) {
                    if ((enabledChannelMask & (1 << index)) == 0) { continue; }
                    Channel channel = channels[index];
                    int encoded = waveRam[(int) (channel.addressFixed11 >>> 11) & 0xffff] & 0xff;
                    if (encoded == 0xff) {
                        // A marker replaces the entire cursor, including its fractional bits.
                        channel.addressFixed11 = (long) channel.loopAddress << 11;
                        encoded = waveRam[channel.loopAddress] & 0xff;
                    } else {
                        channel.addressFixed11 = (channel.addressFixed11 + channel.frequency) & 0xffffffffL;
                    }
                    if (encoded == 0xff) { continue; }
                    int magnitude = encoded & 0x7f;
                    int signed = (encoded & 0x80) != 0 ? magnitude : -magnitude;
                    // Bounds: |product| <= 127 * 255 * 15; eight shifted terms fit an int.
                    // Arithmetic shift preserves the pinned core's rounding for negative samples.
                    left += (signed * channel.envelope * (channel.pan & 15)) >> 5;
                    right += (signed * channel.envelope * (channel.pan >>> 4)) >> 5;
                }
                left = Math.clamp(left, -32768, 32767);
                right = Math.clamp(right, -32768, 32767);
            }
            steps.add(new CdPcmOutput.Step(left, right, left - leftOutput, right - rightOutput, hasDelta));
            leftOutput = left;
            rightOutput = right;
        }
        return new CdPcmOutput(steps);
    }

    public CdPcmState captureState() {
        var captured = new ArrayList<CdPcmState.Channel>(CHANNEL_COUNT);
        for (Channel channel : channels) {
            captured.add(new CdPcmState.Channel(channel.addressFixed11, channel.startPage, channel.loopAddress,
                    channel.frequency, channel.envelope, channel.pan));
        }
        return new CdPcmState(captured, enabledChannelMask, selectedChannel, selectedBank, chipEnabled,
                leftOutput, rightOutput, waveRam);
    }

    /** Restore copies device memory; the immutable snapshot remains reusable and independently owned. */
    public void restoreState(CdPcmState state) {
        Objects.requireNonNull(state, "state");
        byte[] restoredRam = state.waveRam();
        for (int index = 0; index < CHANNEL_COUNT; index++) {
            var saved = state.channels().get(index);
            Channel channel = channels[index];
            channel.addressFixed11 = saved.addressFixed11();
            channel.startPage = saved.startPage();
            channel.loopAddress = saved.loopAddress();
            channel.frequency = saved.frequency();
            channel.envelope = saved.envelope();
            channel.pan = saved.pan();
        }
        System.arraycopy(restoredRam, 0, waveRam, 0, WAVE_RAM_BYTES);
        enabledChannelMask = state.enabledChannelMask();
        selectedChannel = state.selectedChannel();
        selectedBank = state.selectedBank();
        chipEnabled = state.chipEnabled();
        leftOutput = state.leftOutput();
        rightOutput = state.rightOutput();
    }

    private static void requireChipAddress(int address) {
        if (address < 0 || address >= 2 * BANK_BYTES) {
            throw new IllegalArgumentException("PCM chip byte address must be within 0000-1FFF");
        }
    }

    private static void requireBankSlice(int offset, int length) {
        if (offset < 0 || offset > BANK_BYTES || length < 0 || length > BANK_BYTES - offset) {
            throw new IllegalArgumentException("PCM RAM copy must fit within one 4 KiB bank");
        }
    }

    private static final class Channel {
        long addressFixed11;
        int startPage;
        int loopAddress;
        int frequency;
        int envelope;
        int pan = 0xff;
    }
}
