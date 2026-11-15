/*
 * Translated from MAME src/devices/sound/rf5c68.cpp and rf5c68.h (the Ricoh RF5C68 / RF5C164
 * PCM controller), https://github.com/mamedev/mame commit 9cf98d883e72e2ab8d4d73a7104989b4b306dda7
 * (BSD-3-Clause; text below and in LICENSES/MAME-BSD-3-Clause.txt).
 *
 * Copyright-holders: Olivier Galibert, Aaron Giles
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Modified 2026-11 by the OpenGGF contributors: translated from C++ to Java
 * (rf5c68_device::rf5c68_w, rf5c68_r, rf5c68_mem_r, rf5c68_mem_w, sound_stream_update and the
 * rf5c164_map address decode; the pcm_channel fields become per-channel arrays). The stream,
 * memory-space, save-state and sample-end-callback plumbing is not translated. Three behaviours
 * differ from upstream; see the class comment. Added by OpenGGF, not in upstream: stepSplit, which
 * also reports the part of the channel sum from a caller's set of channels, for the host's mix.
 */
package com.openggf.game.soniccd.device.pcm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Session-owned Sega CD PCM sample device (Ricoh RF5C164, Sega part 315-5476A).
 *
 * <p>The register decode, wave-RAM banking and per-sample channel loop are translated from MAME's
 * {@code rf5c68_device} (see the header). The chip sits on the sub-CPU bus at {@code FF0000-FF3FFF}
 * on odd bytes; this class takes the chip's own byte address {@code 0000-1FFF} after that
 * translation: registers {@code 0-8} are write-only, {@code 10-1F} read back each channel's
 * current sample address (low byte at even, high byte at odd addresses), and {@code 1000-1FFF} is
 * the 4 KiB window into the 64 KiB wave RAM selected by the control register's bank field.
 *
 * <p>Where the native captures and MAME disagree, this class follows the captures. Each case is a
 * hardware-visible output difference measured from BizHawk 2.11 by {@code tools/soniccd/
 * pcm_native_run.py} (fixtures {@code controls-startup-jumps-r1} and
 * {@code route-soundtest-controls-r1}, compared by {@code TestCdPcmNativeContinuations} and
 * {@code TestCdPcmNativeMusic}; MAME's rounding fails 16 of their 20 blocks, MAME's loop step 10):
 * <ul>
 *   <li>A channel's contribution is {@code (signed sample * ENV * pan nibble) >> 5} with an
 *       arithmetic shift, so a negative product rounds down; MAME shifts the magnitude and then
 *       negates, which rounds toward zero.</li>
 *   <li>When a {@code FF} loop marker sends a channel to its loop address, that sample is played
 *       from the loop address and the address is not stepped until the following sample; MAME
 *       adds the step in the same sample.</li>
 *   <li>Reset leaves every channel's PAN at {@code FF}. This value has no document or measurement
 *       behind it: it is the expectation of {@code TestCdPcmDevice}; the game's drivers write PAN
 *       before keying a channel on, so nothing measured depends on it.</li>
 * </ul>
 * Unmapped register reads return {@code FF} (likewise test-pinned, unsourced; the drivers read
 * only {@code 10-1F}).
 *
 * <p>The chip makes one output sample per 384 chip clocks (MAME {@code rf5c68_device::device_start},
 * stream rate {@code clock() / 384}; the Mega-CD wires it to the 12.5 MHz sub-CPU clock,
 * MAME {@code src/mame/sega/megacd.cpp} {@code SEGACD_CLOCK}). This class owns no clock: callers
 * advance it explicitly at their own service boundary, before applying the access that follows.
 * Output is the clipped signed 16-bit mix at unity gain; host volume, filtering and resampling are
 * audio policy outside the chip. One simulation owner; no shared sink or background thread.
 */
public final class CdPcmDevice {
    public static final int CHANNEL_COUNT = 8;
    public static final int WAVE_RAM_BYTES = 0x10000;
    public static final int BANK_BYTES = 0x1000;

    /** Allocation/work bound, without clock or cadence meaning. Larger work is explicitly chunked. */
    public static final int MAX_SAMPLES_PER_ADVANCE = 4096;

    private static final int CHIP_BYTES = 2 * BANK_BYTES;
    private static final int RESET_PAN = 0xFF;
    private static final int LOOP_MARKER = 0xFF;

    private final byte[] wave = new byte[WAVE_RAM_BYTES];
    // MAME pcm_channel: env, pan, start, addr (unsigned 32-bit, 11 fraction bits), step, loopst.
    private final int[] env = new int[CHANNEL_COUNT];
    private final int[] pan = new int[CHANNEL_COUNT];
    private final int[] start = new int[CHANNEL_COUNT];
    private final int[] addr = new int[CHANNEL_COUNT];
    private final int[] step = new int[CHANNEL_COUNT];
    private final int[] loopStart = new int[CHANNEL_COUNT];
    /** Bit n set = channel n keyed on (MAME pcm_channel.enable; the ON/OFF register inverted). */
    private int keyedOn;
    /** MAME m_cbank: the channel the register window addresses. */
    private int registerChannel;
    /** MAME m_wbank, as a bank number 0-15 rather than a pre-shifted byte offset. */
    private int waveBank;
    /** MAME m_enable: control register bit 7. */
    private boolean sounding;
    private int lastLeft;
    private int lastRight;
    /** Unclamped channel sums of the last {@link #stepSplit} sample: all channels, then the selected ones. */
    private int sumLeft;
    private int sumRight;
    private int selectedLeft;
    private int selectedRight;

    public CdPcmDevice() { reset(); }

    /** Power-on state: wave RAM, registers and progress clear, chip silent, PAN {@code FF}. */
    public void reset() {
        Arrays.fill(wave, (byte) 0);
        Arrays.fill(env, 0);
        Arrays.fill(pan, RESET_PAN);
        Arrays.fill(start, 0);
        Arrays.fill(addr, 0);
        Arrays.fill(step, 0);
        Arrays.fill(loopStart, 0);
        keyedOn = 0;
        registerChannel = 0;
        waveBank = 0;
        sounding = false;
        lastLeft = 0;
        lastRight = 0;
    }

    public void writeRegisterAtBoundary(CdPcmRegister register, int unsignedByte) {
        Objects.requireNonNull(register, "register");
        writeAtBoundary(register.address(), unsignedByte);
    }

    /** One byte write in the chip window. Advances no samples; unmapped addresses are ignored. */
    public void writeAtBoundary(int chipByteAddress, int unsignedByte) {
        checkChipAddress(chipByteAddress);
        CdPcmState.requireUnsigned(unsignedByte, 0xFF, "PCM data");
        if (chipByteAddress >= BANK_BYTES) {
            wave[waveOffset(chipByteAddress - BANK_BYTES)] = (byte) unsignedByte;
            return;
        }
        int ch = registerChannel;
        switch (chipByteAddress) {
            case 0 -> env[ch] = unsignedByte;
            case 1 -> pan[ch] = unsignedByte;
            case 2 -> step[ch] = (step[ch] & 0xFF00) | unsignedByte;
            case 3 -> step[ch] = (step[ch] & 0x00FF) | (unsignedByte << 8);
            case 4 -> loopStart[ch] = (loopStart[ch] & 0xFF00) | unsignedByte;
            case 5 -> loopStart[ch] = (loopStart[ch] & 0x00FF) | (unsignedByte << 8);
            case 6 -> {
                start[ch] = unsignedByte;
                if ((keyedOn & (1 << ch)) == 0) {
                    addr[ch] = startAddress(ch);
                }
            }
            case 7 -> {
                sounding = (unsignedByte & 0x80) != 0;
                if ((unsignedByte & 0x40) != 0) {
                    registerChannel = unsignedByte & 7;
                } else {
                    waveBank = unsignedByte & 0x0F;
                }
            }
            case 8 -> {
                keyedOn = ~unsignedByte & 0xFF;
                for (int c = 0; c < CHANNEL_COUNT; c++) {
                    if ((keyedOn & (1 << c)) == 0) {
                        addr[c] = startAddress(c);
                    }
                }
            }
            default -> { /* not decoded by the RF5C164 map */ }
        }
    }

    /** One byte read in the chip window. Advances no samples. */
    public int readAtBoundary(int chipByteAddress) {
        checkChipAddress(chipByteAddress);
        if (chipByteAddress >= BANK_BYTES) {
            return wave[waveOffset(chipByteAddress - BANK_BYTES)] & 0xFF;
        }
        if (chipByteAddress >= 0x10 && chipByteAddress < 0x20) {
            int position = addr[(chipByteAddress & 0x0E) >> 1];
            int shift = (chipByteAddress & 1) != 0 ? 11 + 8 : 11;
            return (position >>> shift) & 0xFF;
        }
        return 0xFF;
    }

    /** Copies bytes into the selected 4 KiB wave bank at {@code bankOffset}, or rejects the whole copy. */
    public void writeBankedRamAtBoundary(int bankOffset, byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        checkBankSlice(bankOffset, bytes.length);
        System.arraycopy(bytes, 0, wave, (waveBank << 12) + bankOffset, bytes.length);
    }

    /** A copy of {@code length} bytes of the selected wave bank from {@code bankOffset}. */
    public byte[] readBankedRamAtBoundary(int bankOffset, int length) {
        checkBankSlice(bankOffset, length);
        int from = (waveBank << 12) + bankOffset;
        return Arrays.copyOfRange(wave, from, from + length);
    }

    /** Fills wave bank {@code bank} (0-15) with one byte, as a driver's clear loop would. */
    public void fillBankAtBoundary(int bank, int unsignedByte) {
        CdPcmState.requireUnsigned(bank, 15, "PCM bank");
        CdPcmState.requireUnsigned(unsignedByte, 0xFF, "PCM data");
        Arrays.fill(wave, bank << 12, (bank + 1) << 12, (byte) unsignedByte);
    }

    /**
     * Produces {@code sampleCount} output samples. Each enabled step carries a delta from the
     * previous output, even when unchanged; while the chip is disabled the output is zero and only
     * the first step that silences a nonzero output carries one.
     */
    public CdPcmOutput advanceSamples(int sampleCount) {
        if (sampleCount < 0 || sampleCount > MAX_SAMPLES_PER_ADVANCE) {
            throw new IllegalArgumentException("PCM sample count must be 0-" + MAX_SAMPLES_PER_ADVANCE);
        }
        List<CdPcmOutput.Step> steps = new ArrayList<>(sampleCount);
        for (int i = 0; i < sampleCount; i++) {
            int previousLeft = lastLeft;
            int previousRight = lastRight;
            boolean delta = produceSample(0);
            steps.add(delta
                    ? new CdPcmOutput.Step(lastLeft, lastRight, lastLeft - previousLeft, lastRight - previousRight, true)
                    : new CdPcmOutput.Step(0, 0, 0, 0, false));
        }
        return new CdPcmOutput(steps);
    }

    /** One output sample without allocation: {@code (left << 16) | (right & 0xFFFF)}. */
    public int step() {
        produceSample(0);
        return (lastLeft << 16) | (lastRight & 0xFFFF);
    }

    /**
     * {@link #step()} that also keeps the sample's channel sum before the clamp, whole and for the
     * channels in {@code channelMask} (bit n = channel n), read with {@link #sumLeft()},
     * {@link #selectedLeft()} and their right-hand pairs. The chip state and the returned sample
     * are exactly {@link #step()}'s; the sums are a host-mix view and are not chip state.
     */
    public int stepSplit(int channelMask) {
        produceSample(channelMask);
        return (lastLeft << 16) | (lastRight & 0xFFFF);
    }

    /** Last {@link #stepSplit} sample's left channel sum before the clamp (zero while the chip is disabled). */
    public int sumLeft() { return sumLeft; }

    public int sumRight() { return sumRight; }

    /** Last {@link #stepSplit} sample's left sum of the selected channels alone, before any clamp. */
    public int selectedLeft() { return selectedLeft; }

    public int selectedRight() { return selectedRight; }

    public CdPcmState captureState() {
        List<CdPcmState.Channel> channels = new ArrayList<>(CHANNEL_COUNT);
        for (int c = 0; c < CHANNEL_COUNT; c++) {
            channels.add(new CdPcmState.Channel(Integer.toUnsignedLong(addr[c]), start[c], loopStart[c],
                    step[c], env[c], pan[c]));
        }
        return new CdPcmState(channels, keyedOn, registerChannel, waveBank, sounding, lastLeft, lastRight, wave);
    }

    public void restoreState(CdPcmState state) {
        Objects.requireNonNull(state, "state");
        for (int c = 0; c < CHANNEL_COUNT; c++) {
            CdPcmState.Channel channel = state.channels().get(c);
            addr[c] = (int) channel.addressFixed11();
            start[c] = channel.startPage();
            loopStart[c] = channel.loopAddress();
            step[c] = channel.frequency();
            env[c] = channel.envelope();
            pan[c] = channel.pan();
        }
        keyedOn = state.enabledChannelMask();
        registerChannel = state.selectedChannel();
        waveBank = state.selectedBank();
        sounding = state.chipEnabled();
        lastLeft = state.leftOutput();
        lastRight = state.rightOutput();
        System.arraycopy(state.waveRam(), 0, wave, 0, WAVE_RAM_BYTES);
    }

    /**
     * MAME {@code sound_stream_update} for one sample: each keyed-on channel reads its sample, takes
     * its loop on a marker, steps its address and adds its contribution; the sum is clamped once.
     * Returns whether this sample carries an output delta.
     */
    private boolean produceSample(int selectedMask) {
        selectedLeft = 0;
        selectedRight = 0;
        if (!sounding) {
            boolean muting = lastLeft != 0 || lastRight != 0;
            lastLeft = 0;
            lastRight = 0;
            sumLeft = 0;
            sumRight = 0;
            return muting;
        }
        int left = 0;
        int right = 0;
        for (int c = 0; c < CHANNEL_COUNT; c++) {
            if ((keyedOn & (1 << c)) == 0) {
                continue;
            }
            int sample = wave[(addr[c] >>> 11) & 0xFFFF] & 0xFF;
            if (sample == LOOP_MARKER) {
                addr[c] = loopStart[c] << 11;
                sample = wave[loopStart[c]] & 0xFF;
                if (sample == LOOP_MARKER) {
                    continue;               // a marker at the loop address: silent, address held
                }
            } else {
                addr[c] += step[c];         // unsigned 32-bit accumulator; wraps
            }
            int signed = (sample & 0x80) != 0 ? sample & 0x7F : -(sample & 0x7F);
            int level = signed * env[c];
            int channelLeft = (level * (pan[c] & 0x0F)) >> 5;
            int channelRight = (level * ((pan[c] >>> 4) & 0x0F)) >> 5;
            left += channelLeft;
            right += channelRight;
            if ((selectedMask & (1 << c)) != 0) {
                selectedLeft += channelLeft;
                selectedRight += channelRight;
            }
        }
        sumLeft = left;
        sumRight = right;
        lastLeft = Math.clamp(left, -32768, 32767);
        lastRight = Math.clamp(right, -32768, 32767);
        return true;
    }

    private int startAddress(int channel) {
        return start[channel] << (8 + 11);
    }

    private int waveOffset(int bankOffset) {
        return (waveBank << 12) | bankOffset;
    }

    private static void checkChipAddress(int address) {
        if (address < 0 || address >= CHIP_BYTES) {
            throw new IllegalArgumentException("PCM chip address outside 0000-1FFF: " + address);
        }
    }

    private static void checkBankSlice(int offset, int length) {
        if (offset < 0 || offset > BANK_BYTES || length < 0 || length > BANK_BYTES - offset) {
            throw new IllegalArgumentException("PCM bank slice outside one 4 KiB bank");
        }
    }
}
