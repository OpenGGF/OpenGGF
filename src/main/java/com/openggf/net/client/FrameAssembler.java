package com.openggf.net.client;

import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.ProtocolViolationException;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/** Reassembles fragmented WebSocket messages under hard running caps. */
final class FrameAssembler {
    private final StringBuilder text = new StringBuilder();
    private final ByteArrayOutputStream binary = new ByteArrayOutputStream();

    String onTextPart(CharSequence data, boolean last) {
        if (text.length() + data.length() > Protocol.MAX_CONTROL_BYTES) {
            text.setLength(0);
            throw new ProtocolViolationException(
                    "fragmented text exceeds " + Protocol.MAX_CONTROL_BYTES);
        }
        text.append(data);
        if (!last) {
            return null;
        }
        String whole = text.toString();
        text.setLength(0);
        return whole;
    }

    byte[] onBinaryPart(ByteBuffer data, boolean last) {
        if (binary.size() + data.remaining() > Protocol.MAX_BINARY_BYTES) {
            binary.reset();
            throw new ProtocolViolationException(
                    "fragmented binary exceeds " + Protocol.MAX_BINARY_BYTES);
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
