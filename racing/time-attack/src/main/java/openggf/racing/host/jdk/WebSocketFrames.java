package openggf.racing.host.jdk;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * RFC 6455 framing for the server side of a {@code /race} connection: a blocking frame
 * reader that enforces the masking, opcode, control-frame and size rules, and the
 * unmasked server-frame encoder. Holds no static state.
 */
final class WebSocketFrames {
    static final int OP_CONTINUATION = 0x0;
    static final int OP_TEXT = 0x1;
    static final int OP_BINARY = 0x2;
    static final int OP_CLOSE = 0x8;
    static final int OP_PING = 0x9;
    static final int OP_PONG = 0xA;

    static final int CLOSE_NORMAL = 1000;
    static final int CLOSE_PROTOCOL_ERROR = 1002;
    static final int CLOSE_INVALID_PAYLOAD = 1007;
    static final int CLOSE_POLICY_VIOLATION = 1008;
    static final int CLOSE_MESSAGE_TOO_BIG = 1009;
    static final int CLOSE_INTERNAL_ERROR = 1011;

    static final int MAX_CONTROL_PAYLOAD = 125;
    private static final int MAX_CLOSE_REASON_BYTES = MAX_CONTROL_PAYLOAD - 2;

    private WebSocketFrames() {
    }

    /** A frame that violates RFC 6455 or a transport budget; the connection closes with {@link #closeCode}. */
    static final class Violation extends Exception {
        private final int closeCode;

        Violation(int closeCode, String message) {
            super(message, null, false, false);
            this.closeCode = closeCode;
        }

        int closeCode() {
            return closeCode;
        }
    }

    record Frame(boolean fin, int opcode, byte[] payload) {
        boolean control() {
            return (opcode & 0x8) != 0;
        }
    }

    /** Reads client frames one at a time; never allocates more than {@code maxFramePayload} for a payload. */
    static final class Reader {
        private final InputStream input;
        private final int maxFramePayload;

        Reader(InputStream input, int maxFramePayload) {
            this.input = input;
            this.maxFramePayload = maxFramePayload;
        }

        Frame next() throws IOException, Violation {
            int first = readByte();
            int second = readByte();
            boolean fin = (first & 0x80) != 0;
            if ((first & 0x70) != 0) {
                // No extension is ever negotiated, so RSV1-3 must be clear (RFC 6455 §5.2).
                throw new Violation(CLOSE_PROTOCOL_ERROR, "reserved bits set");
            }
            int opcode = first & 0x0F;
            if ((second & 0x80) == 0) {
                throw new Violation(CLOSE_PROTOCOL_ERROR, "client frames must be masked");
            }
            boolean control = (opcode & 0x8) != 0;
            if (control ? opcode > OP_PONG : opcode > OP_BINARY) {
                throw new Violation(CLOSE_PROTOCOL_ERROR, "reserved opcode " + opcode);
            }
            long length = second & 0x7F;
            if (length == 126) {
                length = (readByte() << 8) | readByte();
                if (length < 126) {
                    throw new Violation(CLOSE_PROTOCOL_ERROR, "non-minimal 16-bit length");
                }
            } else if (length == 127) {
                length = 0;
                for (int i = 0; i < 8; i++) {
                    length = (length << 8) | readByte();
                }
                if (length < 0) {
                    throw new Violation(CLOSE_PROTOCOL_ERROR, "64-bit length has its high bit set");
                }
                if (length < 0x10000) {
                    throw new Violation(CLOSE_PROTOCOL_ERROR, "non-minimal 64-bit length");
                }
            }
            if (control && (!fin || length > MAX_CONTROL_PAYLOAD)) {
                throw new Violation(CLOSE_PROTOCOL_ERROR,
                        "control frames must be final and at most 125 bytes");
            }
            if (length > maxFramePayload) {
                throw new Violation(CLOSE_MESSAGE_TOO_BIG,
                        "frame exceeds " + maxFramePayload + " bytes");
            }
            byte[] mask = input.readNBytes(4);
            if (mask.length != 4) {
                throw new EOFException("connection closed inside a frame header");
            }
            byte[] payload = input.readNBytes((int) length);
            if (payload.length != length) {
                throw new EOFException("connection closed inside a frame payload");
            }
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= mask[i & 3];
            }
            return new Frame(fin, opcode, payload);
        }

        private int readByte() throws IOException {
            int value = input.read();
            if (value < 0) {
                throw new EOFException("connection closed");
            }
            return value;
        }
    }

    /** One unfragmented, unmasked server frame. */
    static byte[] encode(int opcode, byte[] payload) {
        int length = payload.length;
        int header = length < 126 ? 2 : length <= 0xFFFF ? 4 : 10;
        byte[] frame = new byte[header + length];
        frame[0] = (byte) (0x80 | opcode);
        if (length < 126) {
            frame[1] = (byte) length;
        } else if (length <= 0xFFFF) {
            frame[1] = 126;
            frame[2] = (byte) (length >>> 8);
            frame[3] = (byte) length;
        } else {
            frame[1] = 127;
            long wide = length;
            for (int i = 0; i < 8; i++) {
                frame[2 + i] = (byte) (wide >>> (56 - 8 * i));
            }
        }
        System.arraycopy(payload, 0, frame, header, length);
        return frame;
    }

    /** Close payload with the reason cut on a code-point boundary to fit a control frame. */
    static byte[] closePayload(int code, String reason) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(code >>> 8);
        out.write(code);
        if (reason != null) {
            int used = 0;
            for (int i = 0; i < reason.length(); ) {
                int codePoint = reason.codePointAt(i);
                byte[] bytes = new String(Character.toChars(codePoint))
                        .getBytes(StandardCharsets.UTF_8);
                if (used + bytes.length > MAX_CLOSE_REASON_BYTES) {
                    break;
                }
                out.writeBytes(bytes);
                used += bytes.length;
                i += Character.charCount(codePoint);
            }
        }
        return out.toByteArray();
    }

    /** RFC 6455 §7.4 status codes a peer may send, as accepted by Netty's close-frame check. */
    static boolean validCloseCode(int code) {
        return (code >= 1000 && code <= 1003) || (code >= 1007 && code <= 1014)
                || (code >= 3000 && code <= 4999);
    }

    /** Strict UTF-8 decode; malformed or unmappable input is a 1007 violation. */
    static String decodeUtf8(byte[] bytes) throws Violation {
        try {
            CharBuffer chars = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return chars.toString();
        } catch (CharacterCodingException e) {
            throw new Violation(CLOSE_INVALID_PAYLOAD, "invalid UTF-8");
        }
    }
}
