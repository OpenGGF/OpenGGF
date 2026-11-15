package com.openggf.game.soniccd.device.pcm;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of a {@link CdPcmDevice}: every RF5C164 register value, each channel's sample
 * address, the previous output (so deltas replay exactly) and a private copy of the 64 KiB wave
 * RAM. It holds no clock: a service that schedules samples keeps its own remainder.
 *
 * <p>The fields are the chip's programmable registers as the Mega-CD exposes them (ENV, PAN, FD,
 * LS, ST, ON/OFF and the control register's enable, channel and bank fields; MAME
 * {@code rf5c68_device::rf5c68_w}) plus the internal sample address. Construction rejects values
 * the chip cannot hold.
 */
public record CdPcmState(List<Channel> channels, int enabledChannelMask, int selectedChannel,
                         int selectedBank, boolean chipEnabled, int leftOutput, int rightOutput, byte[] waveRam) {

    public CdPcmState {
        Objects.requireNonNull(channels, "channels");
        Objects.requireNonNull(waveRam, "waveRam");
        channels = List.copyOf(channels);
        if (channels.size() != CdPcmDevice.CHANNEL_COUNT) {
            throw new IllegalArgumentException("PCM state needs " + CdPcmDevice.CHANNEL_COUNT + " channels");
        }
        requireUnsigned(enabledChannelMask, 0xFF, "channel mask");
        requireUnsigned(selectedChannel, CdPcmDevice.CHANNEL_COUNT - 1, "selected channel");
        requireUnsigned(selectedBank, 15, "selected bank");
        requireOutput(leftOutput);
        requireOutput(rightOutput);
        if (waveRam.length != CdPcmDevice.WAVE_RAM_BYTES) {
            throw new IllegalArgumentException("PCM wave RAM must be 64 KiB");
        }
        waveRam = waveRam.clone();
    }

    /** A copy of the wave RAM; the snapshot's own bytes cannot be changed through it. */
    @Override
    public byte[] waveRam() {
        return waveRam.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CdPcmState that
                && enabledChannelMask == that.enabledChannelMask
                && selectedChannel == that.selectedChannel
                && selectedBank == that.selectedBank
                && chipEnabled == that.chipEnabled
                && leftOutput == that.leftOutput
                && rightOutput == that.rightOutput
                && channels.equals(that.channels)
                && Arrays.equals(waveRam, that.waveRam);
    }

    @Override
    public int hashCode() {
        int hash = Objects.hash(channels, enabledChannelMask, selectedChannel, selectedBank, chipEnabled,
                leftOutput, rightOutput);
        return 31 * hash + Arrays.hashCode(waveRam);
    }

    @Override
    public String toString() {
        return "CdPcmState[channels=" + channels + ", enabledChannelMask=" + enabledChannelMask
                + ", selectedChannel=" + selectedChannel + ", selectedBank=" + selectedBank
                + ", chipEnabled=" + chipEnabled + ", leftOutput=" + leftOutput + ", rightOutput=" + rightOutput
                + ", waveRam=64 KiB]";
    }

    /**
     * One channel. {@code addressFixed11} is the unsigned 32-bit sample address with 11 fraction
     * bits (the RAM byte read is bits 11-26); {@code startPage} is ST (the high byte of the start
     * address), {@code loopAddress} is LS, {@code frequency} is FD (5.11 fixed point), and
     * {@code envelope}/{@code pan} are ENV and PAN (left level in the low nibble).
     */
    public record Channel(long addressFixed11, int startPage, int loopAddress, int frequency,
                          int envelope, int pan) {
        public Channel {
            if (addressFixed11 < 0 || addressFixed11 > 0xFFFF_FFFFL) {
                throw new IllegalArgumentException("PCM sample address must be unsigned 32-bit");
            }
            requireUnsigned(startPage, 0xFF, "ST");
            requireUnsigned(loopAddress, 0xFFFF, "LS");
            requireUnsigned(frequency, 0xFFFF, "FD");
            requireUnsigned(envelope, 0xFF, "ENV");
            requireUnsigned(pan, 0xFF, "PAN");
        }
    }

    static void requireUnsigned(int value, int maximum, String field) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException(field + " must be 0-" + maximum + ": " + value);
        }
    }

    static void requireOutput(int value) {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException("PCM output must be signed 16-bit: " + value);
        }
    }
}
