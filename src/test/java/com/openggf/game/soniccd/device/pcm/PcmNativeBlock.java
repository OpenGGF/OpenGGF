package com.openggf.game.soniccd.device.pcm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Test-only admission of the captured integer-loop extent, never a runtime input source. */
record PcmNativeBlock(int ordinal, int callLabel, CdPcmState before, CdPcmState after,
                      List<CdPcmOutput.Step> samples, List<Integer> loopMasks) {
    static final int BYTES = 262656;
    private static final int STATE_BYTES = 224;
    private static final int RAM_BYTES = 65536;
    private static final int SAMPLES_OFFSET = 131584;

    private record NativeState(CdPcmState chip, long cycles) { }

    PcmNativeBlock {
        samples = List.copyOf(samples);
        loopMasks = List.copyOf(loopMasks);
    }

    static PcmNativeBlock parse(byte[] bytes) {
        require(bytes.length == BYTES, "full block extent");
        var input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int[] header = new int[16];
        for (int i = 0; i < header.length; i++) { header[i] = input.getInt(); }
        int length = header[4];
        require(header[0] == 0x424d4350 && header[1] == 1, "magic/ABI");
        require(header[2] > 0 && header[3] > 0 && header[3] <= 3600, "ordinal/call label");
        require(length > 0 && length <= CdPcmDevice.MAX_SAMPLES_PER_ADVANCE, "sample bound");
        require(header[5] == STATE_BYTES && header[6] == RAM_BYTES && header[7] == 32
                && header[8] == length && header[9] == 1 && header[10] == 1, "extent/phase/completion");
        for (int i = 11; i < 16; i++) { require(header[i] == 0, "reserved header word"); }
        byte[] ramBefore = Arrays.copyOfRange(bytes, 512, 66048);
        byte[] ramAfter = Arrays.copyOfRange(bytes, 66048, SAMPLES_OFFSET);
        require(Arrays.equals(ramBefore, ramAfter), "wave RAM changed inside pcm_run");
        NativeState before = state(input, 64, ramBefore);
        NativeState after = state(input, 288, ramAfter);
        require(after.cycles == ((before.cycles + 1536L * length) & 0xffffffffL), "service clock extent");
        CdPcmState pre = before.chip;
        CdPcmState post = after.chip;
        require(pre.chipEnabled() == post.chipEnabled()
                && pre.enabledChannelMask() == post.enabledChannelMask()
                && pre.selectedChannel() == post.selectedChannel() && pre.selectedBank() == post.selectedBank(),
                "register changed inside pcm_run");
        for (int i = 0; i < 8; i++) {
            var a = pre.channels().get(i);
            var b = post.channels().get(i);
            require(a.startPage() == b.startPage() && a.loopAddress() == b.loopAddress()
                    && a.frequency() == b.frequency() && a.envelope() == b.envelope() && a.pan() == b.pan(),
                    "channel register changed inside pcm_run");
            if (!pre.chipEnabled() || (pre.enabledChannelMask() & (1 << i)) == 0) {
                require(a.addressFixed11() == b.addressFixed11(), "inactive cursor changed");
            }
        }
        var samples = new ArrayList<CdPcmOutput.Step>(length);
        var loops = new ArrayList<Integer>(length);
        input.position(SAMPLES_OFFSET);
        int previousLeft = pre.leftOutput();
        int previousRight = pre.rightOutput();
        for (int i = 0; i < length; i++) {
            require(input.getInt() == i, "sample order");
            int left = input.getInt();
            int right = input.getInt();
            int leftDelta = input.getInt();
            int rightDelta = input.getInt();
            int submitted = input.getInt();
            int loopMask = input.getInt();
            require(input.getInt() == 0, "reserved sample word");
            boolean expectedSubmission = pre.chipEnabled() || (i == 0 && (previousLeft != 0 || previousRight != 0));
            require(submitted == (expectedSubmission ? 1 : 0), "delta submission");
            require(leftDelta == left - previousLeft && rightDelta == right - previousRight, "delta values");
            require(loopMask >= 0 && loopMask <= 255 && (loopMask & ~pre.enabledChannelMask()) == 0,
                    "loop mask");
            require(pre.chipEnabled() || (left == 0 && right == 0 && loopMask == 0), "disabled output");
            samples.add(new CdPcmOutput.Step(left, right, leftDelta, rightDelta, submitted == 1));
            loops.add(loopMask);
            previousLeft = left;
            previousRight = right;
        }
        require(previousLeft == post.leftOutput() && previousRight == post.rightOutput(), "final output");
        while (input.hasRemaining()) { require(input.get() == 0, "unused sample storage"); }
        return new PcmNativeBlock(header[2], header[3], pre, post, samples, loops);
    }

    private static NativeState state(ByteBuffer input, int offset, byte[] waveRam) {
        input.position(offset);
        var channels = new ArrayList<CdPcmState.Channel>(8);
        for (int i = 0; i < 8; i++) {
            long address = Integer.toUnsignedLong(input.getInt());
            long start = Integer.toUnsignedLong(input.getInt());
            require((start & 0x7ffff) == 0 && start <= (255L << 19), "native start accumulator");
            channels.add(new CdPcmState.Channel(address, (int) (start >>> 19),
                    input.getInt(), input.getInt(), input.getInt(), input.getInt()));
        }
        int enabled = input.getInt();
        require(enabled == 0 || enabled == 128, "enable byte");
        int mask = input.getInt();
        int index = input.getInt();
        int bank = input.getInt();
        int left = input.getInt();
        int right = input.getInt();
        long cycles = Integer.toUnsignedLong(input.getInt());
        require(input.getInt() == 100, "unity host volume");
        // Clock and volume are validated service/host metadata, not fields silently masked from chip state.
        return new NativeState(new CdPcmState(channels, mask, index, bank, enabled == 128,
                left, right, waveRam), cycles);
    }

    private static void require(boolean condition, String field) {
        if (!condition) { throw new IllegalArgumentException("Invalid native PCM " + field); }
    }
}
