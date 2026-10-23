package com.openggf.game.soniccd.device.pcm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.soniccd.device.pcm.CdPcmRegister.*;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed chip behavior, without commercial samples, clocks, native libraries or audio devices. */
class TestCdPcmDevice {
    @ParameterizedTest
    @CsvSource(textBlock = """
            0,   255, 255,      0,      0
            128, 255, 255,      0,      0
            1,     1,  17,     -1,     -1
            129,   1,  17,      0,      0
            127, 255, 255, -15181, -15181
            254, 255, 255,  15060,  15060
            130,  16,  33,      1,      2
            2,    16,  33,     -1,     -2
            254, 255,  33,   1004,   2008
            126, 255,  33,  -1005,  -2009
            254,   0, 255,      0,      0
            254, 255,   0,      0,      0
            254, 255,  15,  15060,      0
            254, 255, 240,      0,  15060
            """)
    void decodesSignedMagnitudeAndRoundsNegativeChannelProductsDown(int data, int envelope, int pan,
                                                                  int left, int right) {
        var device = new CdPcmDevice();
        putRam(device, 0x100, data);
        configure(device, 0, envelope, pan, 0, 0, 1);
        enableChannels(device, 1);

        assertEquals(List.of(step(left, right, left, right)), device.advanceSamples(1).steps());
        assertEquals(0x80000, device.captureState().channels().getFirst().addressFixed11());
    }

    @Test
    void readsBeforeAdvancingAndRetainsFractionalFrequencyAndBothRegisterBytes() {
        var device = new CdPcmDevice();
        putRam(device, 0x1200, 0x81, 0x82, 0x83, 0x84);
        configure(device, 0, 32, 0x21, 0x0801, 0, 0x12);
        enableChannels(device, 1);

        assertEquals(List.of(step(1, 2, 1, 2), step(2, 4, 1, 2), step(3, 6, 1, 2)),
                device.advanceSamples(3).steps());
        assertEquals(0x00901803, device.captureState().channels().getFirst().addressFixed11());
        assertEquals(3, device.readAtBoundary(0x10));
        assertEquals(0x12, device.readAtBoundary(0x11));

        device.writeRegisterAtBoundary(FREQUENCY_HIGH, 1);
        assertEquals(0x0101, device.captureState().channels().getFirst().frequency());
        device.writeRegisterAtBoundary(FREQUENCY_LOW, 0x7f);
        assertEquals(0x017f, device.captureState().channels().getFirst().frequency());
        device.writeRegisterAtBoundary(LOOP_HIGH, 0xab);
        device.writeRegisterAtBoundary(LOOP_LOW, 0xcd);
        assertEquals(0xabcd, device.captureState().channels().getFirst().loopAddress());
        device.writeRegisterAtBoundary(LOOP_HIGH, 0x12);
        assertEquals(0x12cd, device.captureState().channels().getFirst().loopAddress());
    }

    @Test
    void markerSamplesLoopTargetImmediatelyButDoesNotIncrementUntilTheFollowingStep() {
        var device = new CdPcmDevice();
        putRam(device, 0x100, 0x81, 0xff);
        putRam(device, 0x1234, 0x85, 0x87);
        configure(device, 0, 32, 0x11, 0x0801, 0x1234, 1);
        enableChannels(device, 1);

        assertEquals(step(1, 1, 1, 1), device.advanceSamples(1).steps().getFirst());
        assertEquals(0x80801, device.captureState().channels().getFirst().addressFixed11());
        assertEquals(step(5, 5, 4, 4), device.advanceSamples(1).steps().getFirst());
        assertEquals(0x91a000, device.captureState().channels().getFirst().addressFixed11());
        assertEquals(List.of(step(5, 5, 0, 0), step(7, 7, 2, 2)), device.advanceSamples(2).steps());
        assertEquals(0x91b002, device.captureState().channels().getFirst().addressFixed11());
    }

    @Test
    void markerAtLoopTargetStaysSilentWithoutStoppingOrSearchingForAnotherSample() {
        var device = new CdPcmDevice();
        putRam(device, 0x100, 0x83, 0xff);
        putRam(device, 0xffff, 0xff);
        configure(device, 0, 32, 0x11, 0x0800, 0xffff, 1);
        enableChannels(device, 1);

        assertEquals(List.of(step(3, 3, 3, 3), step(0, 0, -3, -3), step(0, 0, 0, 0)),
                device.advanceSamples(3).steps());
        assertEquals(0x7fff800, device.captureState().channels().getFirst().addressFixed11());
        assertEquals(1, device.captureState().enabledChannelMask());
        assertEquals(step(0, 0, 0, 0), device.advanceSamples(1).steps().getFirst());
    }

    @Test
    void runsAllEightChannelsAndClipsOnlyAfterSummingIndividuallyRoundedContributions() {
        var device = new CdPcmDevice();
        for (int channel = 0; channel < 8; channel++) {
            putRam(device, (channel + 1) << 8, channel < 4 ? 0xfe : 0x7e);
            configure(device, channel, 255, 0xff, channel + 1, 0, channel + 1);
        }
        enableChannels(device, 0xff);

        // Four +15060 and four -15061 contributions. Intermediate clipping loses this cancellation.
        assertEquals(List.of(step(-4, -4, -4, -4)), device.advanceSamples(1).steps());
        for (int channel = 0; channel < 8; channel++) {
            assertEquals(((long) (channel + 1) << 19) + channel + 1,
                    device.captureState().channels().get(channel).addressFixed11());
            assertEquals(0, device.readAtBoundary(0x10 + 2 * channel));
            assertEquals(channel + 1, device.readAtBoundary(0x11 + 2 * channel));
        }
    }

    @Test
    void saturatesBothPolaritiesAndReturnsFullWidthOutputDeltas() {
        var device = new CdPcmDevice();
        putRam(device, 0x100, 0xfe);
        for (int channel = 0; channel < 8; channel++) {
            configure(device, channel, 255, 0xff, 0, 0, 1);
        }
        enableChannels(device, 0xff);
        assertEquals(step(32767, 32767, 32767, 32767), device.advanceSamples(1).steps().getFirst());

        putRam(device, 0x100, 0x7f);
        assertEquals(step(-32768, -32768, -65535, -65535), device.advanceSamples(1).steps().getFirst());
        putRam(device, 0x100, 0xfe);
        assertEquals(step(32767, 32767, 65535, 65535), device.advanceSamples(1).steps().getFirst());
    }

    @Test
    void startWritesReloadOnlyAnOffChannelAndOffWritesReloadEveryOffPointer() {
        var device = new CdPcmDevice();
        putRam(device, 0x100, 0x81, 0x82, 0x83);
        putRam(device, 0x200, 0x89);
        configure(device, 0, 32, 0x11, 0x0800, 0, 1);
        enableChannels(device, 1);
        device.advanceSamples(2);

        device.writeRegisterAtBoundary(START, 2);
        assertEquals(0x81000, device.captureState().channels().getFirst().addressFixed11());
        assertEquals(step(3, 3, 1, 1), device.advanceSamples(1).steps().getFirst());
        device.writeRegisterAtBoundary(CHANNEL_OFF, 0xff);
        assertEquals(0x100000, device.captureState().channels().getFirst().addressFixed11());
        assertEquals(step(0, 0, -3, -3), device.advanceSamples(1).steps().getFirst());
        enableChannels(device, 1);
        assertEquals(step(9, 9, 9, 9), device.advanceSamples(1).steps().getFirst());
        device.writeRegisterAtBoundary(CHANNEL_OFF, 0xfe); // Channel 0 stays on; do not restart it.
        assertEquals(0x100800, device.captureState().channels().getFirst().addressFixed11());
        device.writeRegisterAtBoundary(CHANNEL_OFF, 0xff);
        device.writeRegisterAtBoundary(CHANNEL_OFF, 0xff);
        assertEquals(0x100000, device.captureState().channels().getFirst().addressFixed11());
        device.writeRegisterAtBoundary(START, 3);
        assertEquals(0x180000, device.captureState().channels().getFirst().addressFixed11());
    }

    @Test
    void chipDisableFreezesProgressAndDefersOneMuteDeltaUntilPositiveSampleWork() {
        var device = new CdPcmDevice();
        putRam(device, 0x100, 0x81, 0x82, 0x83);
        configure(device, 0, 32, 0x11, 0x0800, 0, 1);
        enableChannels(device, 1);
        device.advanceSamples(1);
        device.writeRegisterAtBoundary(CONTROL, 0x40);
        var disabled = device.captureState();
        assertFalse(disabled.chipEnabled());
        assertEquals(1, disabled.leftOutput());
        assertEquals(1, disabled.enabledChannelMask());
        assertTrue(device.advanceSamples(0).steps().isEmpty());
        assertEquals(disabled, device.captureState());

        assertEquals(List.of(step(0, 0, -1, -1), silentStep(), silentStep()), device.advanceSamples(3).steps());
        assertEquals(0x80800, device.captureState().channels().getFirst().addressFixed11());
        assertEquals(List.of(silentStep()), device.advanceSamples(1).steps());
        device.writeRegisterAtBoundary(START, 2); // Still on in the channel mask while the chip is off.
        assertEquals(0x80800, device.captureState().channels().getFirst().addressFixed11());
        device.writeRegisterAtBoundary(CONTROL, 0xc0);
        assertEquals(step(2, 2, 2, 2), device.advanceSamples(1).steps().getFirst());
    }

    @Test
    void silentEnvelopeAndPanDoNotStopAddressingButAnOffChannelDoes() {
        var device = new CdPcmDevice();
        configure(device, 0, 0, 0xff, 0x0800, 0, 1);
        configure(device, 1, 255, 0, 0x0800, 0, 2);
        configure(device, 2, 255, 0xff, 0x0800, 0, 3);
        enableChannels(device, 3);

        assertEquals(List.of(step(0, 0, 0, 0), step(0, 0, 0, 0)), device.advanceSamples(2).steps());
        var channels = device.captureState().channels();
        assertEquals(0x81000, channels.get(0).addressFixed11());
        assertEquals(0x101000, channels.get(1).addressFixed11());
        assertEquals(0x180000, channels.get(2).addressFixed11());
    }

    @Test
    void controlSelectsBankOrChannelIndependentlyAndIgnoresUnusedBits() {
        var device = new CdPcmDevice();
        device.writeRegisterAtBoundary(CONTROL, 0x8a);
        device.writeRegisterAtBoundary(CONTROL, 0xff);
        assertEquals(10, device.captureState().selectedBank());
        assertEquals(7, device.captureState().selectedChannel());
        assertTrue(device.captureState().chipEnabled());
        device.writeRegisterAtBoundary(ENVELOPE, 73);
        device.writeRegisterAtBoundary(CONTROL, 0xbf);
        assertEquals(15, device.captureState().selectedBank());
        assertEquals(7, device.captureState().selectedChannel());
        assertEquals(73, device.captureState().channels().get(7).envelope());
        assertEquals(0, device.captureState().channels().get(0).envelope());

        for (int ignored : new int[] {0, 8, 16, 32, 56}) {
            device.writeRegisterAtBoundary(CONTROL, 0xc3);
            var expected = device.captureState();
            device.writeRegisterAtBoundary(CONTROL, 0xc3 | ignored);
            assertEquals(expected, device.captureState());
        }
        device.writeRegisterAtBoundary(CONTROL, 0x8a);
        var bankTen = device.captureState();
        device.writeRegisterAtBoundary(CONTROL, 0xba);
        assertEquals(bankTen, device.captureState());
        device.writeRegisterAtBoundary(CONTROL, 0x7b);
        assertFalse(device.captureState().chipEnabled());
        assertEquals(3, device.captureState().selectedChannel());
        assertEquals(10, device.captureState().selectedBank());
    }

    @Test
    void bankedRamReadWriteRetainsAllSixteenBanksIncludingUnsignedBytesAndEdges() {
        var device = new CdPcmDevice();
        for (int bank = 0; bank < 16; bank++) {
            device.writeRegisterAtBoundary(CONTROL, bank);
            device.writeAtBoundary(0x1000, 0x80 + bank);
            device.writeAtBoundary(0x1fff, 0xf0 + bank);
            device.writeBankedRamAtBoundary(0x10, new byte[] {(byte) bank, (byte) 0xff});
        }
        for (int bank = 0; bank < 16; bank++) {
            device.writeRegisterAtBoundary(CONTROL, bank);
            assertEquals(0x80 + bank, device.readAtBoundary(0x1000));
            assertEquals(0xf0 + bank, device.readAtBoundary(0x1fff));
            assertArrayEquals(new byte[] {(byte) bank, (byte) 0xff}, device.readBankedRamAtBoundary(0x10, 2));
            assertEquals(0, device.readAtBoundary(0x1001));
        }
        assertFalse(device.captureState().chipEnabled());
        assertEquals(0, device.captureState().enabledChannelMask());
    }

    @Test
    void busAccessesNeverAdvanceSamplesAndReservedRegistersIgnoreWritesOrReadAsFf() {
        var device = new CdPcmDevice();
        configure(device, 0, 32, 0x11, 0x0800, 0, 0x12);
        enableChannels(device, 1);
        var before = device.captureState();
        for (int address : new int[] {9, 0x10, 0x1f, 0x20, 0xfff}) {
            device.writeAtBoundary(address, 0xa5);
        }
        for (int address : new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0xf, 0x20, 0xfff}) {
            assertEquals(0xff, device.readAtBoundary(address));
        }
        assertEquals(0, device.readAtBoundary(0x10));
        assertEquals(0x12, device.readAtBoundary(0x11));
        assertEquals(before, device.captureState());
        device.writeRegisterAtBoundary(ENVELOPE, 64);
        assertEquals(before.channels().getFirst().addressFixed11(),
                device.captureState().channels().getFirst().addressFixed11());
        assertEquals(0, device.captureState().leftOutput());
    }

    @Test
    void sampleAddressWrapsAtSixteenBitsIndependentlyOfTheHostRamBank() {
        var device = new CdPcmDevice();
        putRam(device, 0xffff, 0x82);
        putRam(device, 0, 0x83);
        configure(device, 0, 32, 0x11, 0x0800, 0, 0xff);
        var state = device.captureState();
        var channels = new ArrayList<>(state.channels());
        channels.set(0, new CdPcmState.Channel(0x7fff800, 0xff, 0, 0x0800, 32, 0x11));
        device.restoreState(new CdPcmState(channels, 1, 0, 5, true, 0, 0, state.waveRam()));

        assertEquals(List.of(step(2, 2, 2, 2), step(3, 3, 1, 1)), device.advanceSamples(2).steps());
        assertEquals(0x8000800, device.captureState().channels().getFirst().addressFixed11());
        assertEquals(1, device.readAtBoundary(0x10));
        assertEquals(0, device.readAtBoundary(0x11));
        assertEquals(5, device.captureState().selectedBank());
    }

    @Test
    void chunkingAndZeroWorkPreserveTheExactSamplesDeltasAndChannelProgress() {
        var whole = patternedDevice();
        var chunked = patternedDevice();
        var expected = whole.advanceSamples(37);
        var actual = new ArrayList<CdPcmOutput.Step>();
        for (int count : new int[] {1, 0, 7, 4, 19, 6}) {
            actual.addAll(chunked.advanceSamples(count).steps());
        }
        assertEquals(expected.steps(), actual);
        assertEquals(whole.captureState(), chunked.captureState());
        assertThrows(UnsupportedOperationException.class, () -> expected.steps().clear());
        whole.advanceSamples(1);
        assertEquals(37, expected.steps().size());
    }

    @Test
    void resetClearsRamRegistersProgressAndPreviousOutputAndRestoresDefaultPanning() {
        var device = patternedDevice();
        device.advanceSamples(11);
        device.writeRegisterAtBoundary(CONTROL, 0x8f);
        device.writeAtBoundary(0x1fff, 0xff);
        device.reset();

        assertEquals(new CdPcmDevice().captureState(), device.captureState());
        assertArrayEquals(new byte[0x10000], device.captureState().waveRam());
        for (var channel : device.captureState().channels()) {
            assertEquals(0xff, channel.pan());
        }
        assertEquals(List.of(silentStep()), device.advanceSamples(1).steps());
        device.writeRegisterAtBoundary(CONTROL, 0x80);
        assertEquals(List.of(step(0, 0, 0, 0)), device.advanceSamples(1).steps());
    }

    @Test
    void rejectsNonByteDataInvalidAddressesAndUnboundedWorkBeforeChangingAnyState() {
        var device = patternedDevice();
        device.advanceSamples(3);
        var before = device.captureState();
        for (int address : new int[] {-1, Integer.MIN_VALUE, 0x2000, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> device.writeAtBoundary(address, 0));
            assertThrows(IllegalArgumentException.class, () -> device.readAtBoundary(address));
        }
        for (int data : new int[] {-1, Integer.MIN_VALUE, 256, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> device.writeAtBoundary(7, data));
            assertThrows(IllegalArgumentException.class, () -> device.writeAtBoundary(0x1000, data));
            assertThrows(IllegalArgumentException.class, () -> device.writeRegisterAtBoundary(CONTROL, data));
        }
        for (int count : new int[] {-1, Integer.MIN_VALUE, CdPcmDevice.MAX_SAMPLES_PER_ADVANCE + 1,
                Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> device.advanceSamples(count));
        }
        assertThrows(NullPointerException.class, () -> device.writeRegisterAtBoundary(null, 0));
        assertThrows(NullPointerException.class, () -> device.restoreState(null));
        assertEquals(before, device.captureState());
        assertEquals(CdPcmDevice.MAX_SAMPLES_PER_ADVANCE,
                device.advanceSamples(CdPcmDevice.MAX_SAMPLES_PER_ADVANCE).steps().size());
    }

    @Test
    void boundedBankCopiesRejectCrossingOrOverflowingSlicesWithoutPartialWrites() {
        var device = new CdPcmDevice();
        byte[] source = {(byte) 0xfe};
        device.writeBankedRamAtBoundary(0xfff, source);
        source[0] = 1;
        byte[] read = device.readBankedRamAtBoundary(0xfff, 1);
        assertArrayEquals(new byte[] {(byte) 0xfe}, read);
        read[0] = 2;
        assertEquals(0xfe, device.readAtBoundary(0x1fff));
        device.writeBankedRamAtBoundary(0x1000, new byte[0]);
        assertArrayEquals(new byte[0], device.readBankedRamAtBoundary(0x1000, 0));
        var before = device.captureState();

        assertThrows(IllegalArgumentException.class, () -> device.writeBankedRamAtBoundary(-1, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> device.writeBankedRamAtBoundary(0xfff, new byte[2]));
        assertThrows(IllegalArgumentException.class, () -> device.writeBankedRamAtBoundary(0, new byte[0x1001]));
        assertThrows(IllegalArgumentException.class,
                () -> device.writeBankedRamAtBoundary(Integer.MAX_VALUE, new byte[1]));
        assertThrows(NullPointerException.class, () -> device.writeBankedRamAtBoundary(0, null));
        for (int[] range : new int[][] {{-1, 0}, {0, -1}, {0x1001, 0}, {0xfff, 2},
                {0, Integer.MAX_VALUE}, {Integer.MAX_VALUE, 1}}) {
            assertThrows(IllegalArgumentException.class, () -> device.readBankedRamAtBoundary(range[0], range[1]));
        }
        assertEquals(before, device.captureState());
    }

    static CdPcmDevice patternedDevice() {
        var device = new CdPcmDevice();
        for (int channel = 0; channel < 8; channel++) {
            int base = (channel + 1) << 8;
            putRam(device, base, 0x81 + channel, 0x02 + channel, 0x85 + channel, 0xff);
            configure(device, channel, 29 + channel * 17, 0xf1 + channel,
                    0x0401 + channel * 0x101, base + 1, channel + 1);
        }
        enableChannels(device, 0xff);
        return device;
    }

    static void configure(CdPcmDevice device, int channel, int envelope, int pan, int frequency,
                          int loopAddress, int startPage) {
        device.writeRegisterAtBoundary(CONTROL, 0xc0 | channel);
        device.writeRegisterAtBoundary(ENVELOPE, envelope);
        device.writeRegisterAtBoundary(PAN, pan);
        device.writeRegisterAtBoundary(FREQUENCY_LOW, frequency & 255);
        device.writeRegisterAtBoundary(FREQUENCY_HIGH, frequency >>> 8);
        device.writeRegisterAtBoundary(LOOP_LOW, loopAddress & 255);
        device.writeRegisterAtBoundary(LOOP_HIGH, loopAddress >>> 8);
        device.writeRegisterAtBoundary(START, startPage);
    }

    static void putRam(CdPcmDevice device, int absoluteAddress, int... data) {
        for (int value : data) {
            device.writeRegisterAtBoundary(CONTROL, 0x80 | (absoluteAddress >>> 12));
            device.writeAtBoundary(0x1000 | (absoluteAddress & 0xfff), value);
            absoluteAddress++;
        }
    }

    static void enableChannels(CdPcmDevice device, int mask) {
        device.writeRegisterAtBoundary(CHANNEL_OFF, (~mask) & 255);
    }

    static CdPcmOutput.Step step(int left, int right, int leftDelta, int rightDelta) {
        return new CdPcmOutput.Step(left, right, leftDelta, rightDelta, true);
    }

    static CdPcmOutput.Step silentStep() {
        return new CdPcmOutput.Step(0, 0, 0, 0, false);
    }
}
