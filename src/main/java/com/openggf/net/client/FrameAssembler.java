package com.openggf.net.client;

import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.ProtocolViolationException;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Reassembles fragmented WebSocket messages under hard running caps. */
final class FrameAssembler {
    private final int maxTextBytes;
    private final int maxBinaryBytes;
    private final StringBuilder text = new StringBuilder();
    private final ByteArrayOutputStream binary = new ByteArrayOutputStream();
    private int textBytes;

    FrameAssembler() {
        this(Protocol.MAX_CONTROL_BYTES, Protocol.MAX_BINARY_BYTES);
    }

    FrameAssembler(int maxTextBytes, int maxBinaryBytes) {
        this.maxTextBytes = maxTextBytes;
        this.maxBinaryBytes = maxBinaryBytes;
    }

    String onTextPart(CharSequence data, boolean last) {
        int partBytes = data.toString().getBytes(StandardCharsets.UTF_8).length;
        if ((long) textBytes + partBytes > maxTextBytes) {
            text.setLength(0);
            textBytes = 0;
            throw new ProtocolViolationException(
                    "fragmented text exceeds " + maxTextBytes);
        }
        text.append(data);
        textBytes += partBytes;
        if (!last) {
            return null;
        }
        String whole = text.toString();
        text.setLength(0);
        textBytes = 0;
        return whole;
    }

    byte[] onBinaryPart(ByteBuffer data, boolean last) {
        if (binary.size() + data.remaining() > maxBinaryBytes) {
            binary.reset();
            throw new ProtocolViolationException(
                    "fragmented binary exceeds " + maxBinaryBytes);
        }
        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);
        binary.writeBytes(bytes);
        if (!last) {
            return null;
        }
        byte[] whole = binary.toByteArray();
        binary.reset();
        return whole;
    }
}
