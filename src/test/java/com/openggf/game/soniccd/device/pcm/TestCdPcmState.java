package com.openggf.game.soniccd.device.pcm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.openggf.game.soniccd.device.pcm.CdPcmRegister.*;
import static com.openggf.game.soniccd.device.pcm.TestCdPcmDevice.*;
import static org.junit.jupiter.api.Assertions.*;

class TestCdPcmState {
    @Test
    void restoreReplaysRamFractionalProgressSelectorsRegistersAndOutputDeltasExactly() {
        var device = patternedDevice();
        device.advanceSamples(7);
        device.writeRegisterAtBoundary(CONTROL, 0xc5);
        device.writeRegisterAtBoundary(CONTROL, 0x8d);
        var checkpoint = device.captureState();
        var expected = device.advanceSamples(29);
        var expectedEnd = device.captureState();

        device.reset();
        device.writeAtBoundary(0x1000, 0xff);
        device.restoreState(checkpoint);
        assertEquals(checkpoint, device.captureState());
        assertEquals(expected, device.advanceSamples(29));
        assertEquals(expectedEnd, device.captureState());
        device.restoreState(checkpoint);
        device.writeRegisterAtBoundary(CONTROL, 0x45);
        assertEquals(step(0, 0, -checkpoint.leftOutput(), -checkpoint.rightOutput()),
                device.advanceSamples(1).steps().getFirst());
        assertEquals(13, device.captureState().selectedBank());
        assertEquals(5, device.captureState().selectedChannel());
    }

    @Test
    void disabledSnapshotRetainsPendingMuteAndDoesNotConfuseOffChannelsWithChipDisable() {
        var device = new CdPcmDevice();
        putRam(device, 0x100, 0x85);
        configure(device, 0, 32, 0x11, 0x0401, 0, 1);
        enableChannels(device, 1);
        device.advanceSamples(1);
        device.writeRegisterAtBoundary(CONTROL, 0x40);
        var checkpoint = device.captureState();
        var expected = device.advanceSamples(3);
        device.reset();
        device.restoreState(checkpoint);

        assertEquals(expected, device.advanceSamples(3));
        assertEquals(List.of(step(0, 0, -5, -5), silentStep(), silentStep()), expected.steps());
        assertEquals(0x80401, device.captureState().channels().getFirst().addressFixed11());
        assertEquals(1, device.captureState().enabledChannelMask());
    }

    @Test
    void captureConstructorAccessorsAndRestoreNeverAliasMutableRamOrChannelCollections() {
        var source = patternedDevice();
        source.advanceSamples(3);
        var original = source.captureState();
        byte[] ram = original.waveRam();
        var channels = new ArrayList<>(original.channels());
        var snapshot = new CdPcmState(channels, original.enabledChannelMask(), original.selectedChannel(),
                original.selectedBank(), original.chipEnabled(), original.leftOutput(), original.rightOutput(), ram);
        ram[0x100] = 0;
        channels.clear();
        snapshot.waveRam()[0x100] = 0;
        assertEquals(original, snapshot);
        assertEquals(original.hashCode(), snapshot.hashCode());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.channels().clear());

        var restored = new CdPcmDevice();
        restored.restoreState(snapshot);
        source.reset();
        assertEquals(original, restored.captureState());
        restored.writeRegisterAtBoundary(CONTROL, 0x80);
        restored.writeAtBoundary(0x1100, 0);
        assertEquals(0x81, snapshot.waveRam()[0x100] & 255);
        assertNotEquals(original, restored.captureState());
    }

    @Test
    void unsigned32BitProgressWrapsWithoutLosingTheIndependentSixteenBitSampleAddress() {
        var device = new CdPcmDevice();
        byte[] ram = new byte[0x10000];
        ram[0xffff] = (byte) 0x82;
        ram[0x1f] = (byte) 0x83;
        var channels = new ArrayList<>(device.captureState().channels());
        channels.set(0, new CdPcmState.Channel(0xfffffff0L, 0xff, 0x4321, 0xffff, 32, 0x11));
        device.restoreState(new CdPcmState(channels, 1, 0, 0, true, 0, 0, ram));

        assertEquals(step(2, 2, 2, 2), device.advanceSamples(1).steps().getFirst());
        assertEquals(0xffef, device.captureState().channels().getFirst().addressFixed11());
        assertEquals(0x1f, device.readAtBoundary(0x10));
        assertEquals(0, device.readAtBoundary(0x11));
        assertEquals(step(3, 3, 1, 1), device.advanceSamples(1).steps().getFirst());
        assertEquals(0x1ffee, device.captureState().channels().getFirst().addressFixed11());
    }

    @Test
    void rejectsMalformedSnapshotsRatherThanMaskingNonNativeState() {
        var valid = new CdPcmDevice().captureState();
        assertThrows(IllegalArgumentException.class, () -> state(valid, List.of(), 0, 0, 0, 0, 0, valid.waveRam()));
        assertThrows(IllegalArgumentException.class,
                () -> state(valid, Collections.nCopies(9, valid.channels().getFirst()), 0, 0, 0, 0, 0, valid.waveRam()));
        for (int mask : new int[] {-1, 256, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> state(valid, valid.channels(), mask, 0, 0, 0, 0, valid.waveRam()));
        }
        for (int channel : new int[] {-1, 8, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> state(valid, valid.channels(), 0, channel, 0, 0, 0, valid.waveRam()));
        }
        for (int bank : new int[] {-1, 16, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> state(valid, valid.channels(), 0, 0, bank, 0, 0, valid.waveRam()));
        }
        for (int output : new int[] {-32769, 32768, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> state(valid, valid.channels(), 0, 0, 0, output, 0, valid.waveRam()));
            assertThrows(IllegalArgumentException.class,
                    () -> state(valid, valid.channels(), 0, 0, 0, 0, output, valid.waveRam()));
        }
        assertThrows(IllegalArgumentException.class,
                () -> state(valid, valid.channels(), 0, 0, 0, 0, 0, new byte[0xffff]));
        assertThrows(IllegalArgumentException.class,
                () -> state(valid, valid.channels(), 0, 0, 0, 0, 0, new byte[0x10001]));
        assertThrows(NullPointerException.class, () -> state(valid, null, 0, 0, 0, 0, 0, valid.waveRam()));
        assertThrows(NullPointerException.class, () -> state(valid, valid.channels(), 0, 0, 0, 0, 0, null));
        var nullChannel = new ArrayList<>(valid.channels());
        nullChannel.set(0, null);
        assertThrows(NullPointerException.class, () -> state(valid, nullChannel, 0, 0, 0, 0, 0, valid.waveRam()));
    }

    @Test
    void rejectsNonNativeChannelFieldsAndInvalidOutputEvents() {
        for (long address : new long[] {-1, 0x100000000L, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new CdPcmState.Channel(address, 0, 0, 0, 0, 0));
        }
        for (int value : new int[] {-1, 256, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new CdPcmState.Channel(0, value, 0, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdPcmState.Channel(0, 0, 0, 0, value, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdPcmState.Channel(0, 0, 0, 0, 0, value));
        }
        for (int value : new int[] {-1, 65536, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new CdPcmState.Channel(0, 0, value, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdPcmState.Channel(0, 0, 0, value, 0, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> new CdPcmOutput.Step(32768, 0, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new CdPcmOutput.Step(0, -32769, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new CdPcmOutput.Step(0, 0, 65536, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new CdPcmOutput.Step(0, 0, 0, -65536, true));
        assertThrows(IllegalArgumentException.class, () -> new CdPcmOutput.Step(0, 0, 1, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new CdPcmOutput(Collections.nCopies(CdPcmDevice.MAX_SAMPLES_PER_ADVANCE + 1, silentStep())));
        assertThrows(NullPointerException.class, () -> new CdPcmOutput(null));
        assertThrows(NullPointerException.class, () -> new CdPcmOutput(Collections.singletonList(null)));
        var mutable = new ArrayList<>(List.of(step(1, 2, 1, 2)));
        var output = new CdPcmOutput(mutable);
        mutable.clear();
        assertEquals(List.of(step(1, 2, 1, 2)), output.steps());
    }

    private static CdPcmState state(CdPcmState original, List<CdPcmState.Channel> channels, int mask,
                                    int channel, int bank, int left, int right, byte[] ram) {
        return new CdPcmState(channels, mask, channel, bank, original.chipEnabled(), left, right, ram);
    }
}
