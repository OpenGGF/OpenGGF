package com.openggf.net.protocol;

import com.openggf.ghost.GhostFrameCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.fail;

@Timeout(30)
class TestProtocolFuzzing {
    private static final long SEED = 0xC0FFEE_1234_5678L;

    private static void assertSafe(Runnable decode) {
        try {
            decode.run();
        } catch (ProtocolViolationException | IllegalArgumentException expected) {
            // Clean rejection is the decoder contract.
        } catch (Throwable fatal) {
            fail("decoder threw a non-protocol error: " + fatal, fatal);
        }
    }

    @Test
    void randomBytesNeverCrashBinaryDecoders() {
        Random random = new Random(SEED);
        for (int i = 0; i < 20_000; i++) {
            byte[] bytes = new byte[random.nextInt(Protocol.MAX_BINARY_BYTES + 16)];
            random.nextBytes(bytes);
            assertBinarySafe(bytes);
        }
    }

    @Test
    void randomStringsNeverCrashControlDecoder() {
        Random random = new Random(SEED);
        for (int i = 0; i < 20_000; i++) {
            byte[] bytes = new byte[random.nextInt(512)];
            random.nextBytes(bytes);
            String text = new String(bytes, StandardCharsets.UTF_8);
            assertSafe(() -> ControlCodec.decode(text));
        }
    }

    @Test
    void singleByteMutationsOfValidPacketsStaySafe() {
        byte[] frameData = new byte[GhostFrameCodec.BYTES];
        byte[] frames = GhostPackets.encodeFrames(1, 0, frameData);
        byte[] aggregate = GhostPackets.encodeAggregate(1, List.of(
                new GhostPackets.AggregateEntry(0, 1, 0, 1, frameData)));
        byte[] roster = GhostPackets.encodeRoster(List.of(
                new GhostPackets.RosterEntry(0, 1, 2, 0)));
        byte[] wrapped = GhostPackets.encodeRelayGuestBinary(7, frames);
        for (byte[] valid : new byte[][] {frames, aggregate, roster, wrapped}) {
            for (int index = 0; index < valid.length; index++) {
                for (int value = 0; value < 256; value++) {
                    byte[] mutated = valid.clone();
                    mutated[index] = (byte) value;
                    assertBinarySafe(mutated);
                }
            }
        }
    }

    private static void assertBinarySafe(byte[] bytes) {
        assertSafe(() -> GhostPackets.decodeFrames(bytes));
        assertSafe(() -> GhostPackets.decodeAggregate(bytes));
        assertSafe(() -> GhostPackets.decodeRoster(bytes));
        assertSafe(() -> GhostPackets.decodeRelayGuestBinary(bytes));
    }
}
