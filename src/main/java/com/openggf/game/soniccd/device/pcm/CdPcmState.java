package com.openggf.game.soniccd.device.pcm;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Complete immutable sample-device state, including a deep copy of all 64 KiB of wave RAM and
 * the previous digital output needed to replay deltas. A service snapshot must separately retain
 * its clock remainder and pending operations; this sample-only device has neither.
 *
 * Channel fields follow GPGX 051d430d, core/cd_hw/pcm.h, chan_t and pcm_t. Unlike its host-context
 * serialization, this value has no pointers, native padding, blip buffer or host-rate configuration.
 */
public record CdPcmState(List<Channel> channels, int enabledChannelMask, int selectedChannel,
                         int selectedBank, boolean chipEnabled, int leftOutput, int rightOutput, byte[] waveRam) {
    public CdPcmState {
        Objects.requireNonNull(channels, "channels");
        Objects.requireNonNull(waveRam, "waveRam");
        if (channels.size() != CdPcmDevice.CHANNEL_COUNT) {
            throw new IllegalArgumentException("PCM state requires exactly eight channels");
        }
        requireUnsigned(enabledChannelMask, 0xff, "Enabled channel mask");
        requireUnsigned(selectedChannel, 7, "Selected channel");
        requireUnsigned(selectedBank, 15, "Selected bank");
        requireOutput(leftOutput);
        requireOutput(rightOutput);
        if (waveRam.length != CdPcmDevice.WAVE_RAM_BYTES) {
            throw new IllegalArgumentException("PCM state requires exactly 64 KiB of wave RAM");
        }
        channels = List.copyOf(channels);
        waveRam = waveRam.clone();
    }

    @Override public byte[] waveRam() { return waveRam.clone(); }

    /** Array content participates in value equality, including RAM outside the selected bank. */
    @Override public boolean equals(Object other) {
        return other instanceof CdPcmState state
                && enabledChannelMask == state.enabledChannelMask && selectedChannel == state.selectedChannel
                && selectedBank == state.selectedBank && chipEnabled == state.chipEnabled
                && leftOutput == state.leftOutput && rightOutput == state.rightOutput
                && channels.equals(state.channels) && Arrays.equals(waveRam, state.waveRam);
    }

    @Override public int hashCode() {
        return 31 * Objects.hash(channels, enabledChannelMask, selectedChannel, selectedBank,
                chipEnabled, leftOutput, rightOutput) + Arrays.hashCode(waveRam);
    }

    /**
     * The full unsigned 32-bit progress accumulator is retained, not truncated to the 16-bit RAM
     * lookup address. {@code startPage} is the unsigned ST byte; frequency is unsigned 5.11 fixed
     * point and loopAddress is an unsigned 16-bit byte address.
     */
    public record Channel(long addressFixed11, int startPage, int loopAddress, int frequency,
                           int envelope, int pan) {
        public Channel {
            if (addressFixed11 < 0 || addressFixed11 > 0xffffffffL) {
                throw new IllegalArgumentException("PCM progress must fit an unsigned 32-bit accumulator");
            }
            requireUnsigned(startPage, 0xff, "Start page");
            requireUnsigned(loopAddress, 0xffff, "Loop address");
            requireUnsigned(frequency, 0xffff, "Frequency");
            requireUnsigned(envelope, 0xff, "Envelope");
            requireUnsigned(pan, 0xff, "Panning");
        }
    }

    static void requireUnsigned(int value, int maximum, String field) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException(field + " is outside its unsigned native field");
        }
    }

    static void requireOutput(int value) {
        if (value < -32768 || value > 32767) {
            throw new IllegalArgumentException("PCM output must fit a signed 16-bit sample");
        }
    }
}
