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
}
