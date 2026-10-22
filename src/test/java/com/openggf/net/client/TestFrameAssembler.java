package com.openggf.net.client;

import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.ProtocolViolationException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class TestFrameAssembler {
    @Test
    void reassemblesFragmentedTextAndBinary() {
        FrameAssembler assembler = new FrameAssembler();
        assertNull(assembler.onTextPart("hel", false));
        assertEquals("hello", assembler.onTextPart("lo", true));
        assertNull(assembler.onBinaryPart(ByteBuffer.wrap(new byte[] {1, 2}), false));
        assertArrayEquals(new byte[] {1, 2, 3},
                assembler.onBinaryPart(ByteBuffer.wrap(new byte[] {3}), true));
    }

    @Test
    void oversizedFragmentedTextThrowsBeforeBuffering() {
        FrameAssembler assembler = new FrameAssembler();
        String fragment = "x".repeat(Protocol.MAX_CONTROL_BYTES / 2 + 1);
        assertNull(assembler.onTextPart(fragment, false));
        assertThrows(ProtocolViolationException.class,
                () -> assembler.onTextPart(fragment, false));
    }

    @Test
    void oversizedFragmentedBinaryThrowsBeforeBuffering() {
        FrameAssembler assembler = new FrameAssembler();
        byte[] fragment = new byte[Protocol.MAX_BINARY_BYTES / 2 + 1];
        assertNull(assembler.onBinaryPart(ByteBuffer.wrap(fragment), false));
        assertThrows(ProtocolViolationException.class,
                () -> assembler.onBinaryPart(ByteBuffer.wrap(fragment), false));
    }

    @Test
    void recoversCleanlyAfterOversizeThrow() {
        FrameAssembler assembler = new FrameAssembler();
        assertThrows(ProtocolViolationException.class,
                () -> assembler.onTextPart("x".repeat(Protocol.MAX_CONTROL_BYTES + 1), true));
        assertEquals("ok", assembler.onTextPart("ok", true));
    }

    @Test
    void completeBinaryMessagesOwnTheirBytesForHeapAndDirectReadOnlySlices() {
        for (boolean direct : new boolean[]{false, true}) {
            ByteBuffer source = direct ? ByteBuffer.allocateDirect(9) : ByteBuffer.allocate(9);
            for (int i = 0; i < 9; i++) source.put((byte) i);
            source.flip().position(2).limit(7);
            ByteBuffer part = source.slice().asReadOnlyBuffer();
            part.position(1);
            byte[] result = new FrameAssembler().onBinaryPart(part, true);
            assertArrayEquals(new byte[]{3, 4, 5, 6}, result);
            assertEquals(part.limit(), part.position());
            source.put(3, (byte) 99);
            assertEquals(3, result[0]);
            result[1] = 88;
            assertEquals(4, source.get(4));
        }
    }

    @Test
    void binaryCapRecoveryAndMixedFragmentationNeverReuseReturnedStorage() {
        FrameAssembler assembler = new FrameAssembler(8, 4);
        assertNull(assembler.onBinaryPart(ByteBuffer.wrap(new byte[]{1, 2, 3}), false));
        ByteBuffer oversized = ByteBuffer.wrap(new byte[]{4, 5});
        assertThrows(ProtocolViolationException.class, () -> assembler.onBinaryPart(oversized, true));
        assertEquals(0, oversized.position(), "reject before consuming or buffering");
        byte[] complete = assembler.onBinaryPart(ByteBuffer.wrap(new byte[]{6, 7, 8, 9}), true);
        assertNull(assembler.onBinaryPart(ByteBuffer.wrap(new byte[0]), false));
        assertNull(assembler.onBinaryPart(ByteBuffer.wrap(new byte[]{10, 11}), false));
        byte[] fragmented = assembler.onBinaryPart(ByteBuffer.wrap(new byte[]{12, 13}), true);
        assertArrayEquals(new byte[]{6, 7, 8, 9}, complete);
        assertArrayEquals(new byte[]{10, 11, 12, 13}, fragmented);
        complete[0] = 99;
        fragmented[0] = 98;
        assertArrayEquals(new byte[]{14}, assembler.onBinaryPart(ByteBuffer.wrap(new byte[]{14}), true));
    }
}
