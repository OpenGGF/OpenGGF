package openggf.racing.host.jdk;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** RFC 6455 frame encoding and the reader's size and shape rules, without sockets. */
class TestWebSocketFrames {

    @Test
    void serverFramesUseTheMinimalUnmaskedLengthForm() {
        assertHeader(125, 2, 125);
        assertHeader(126, 4, 126);
        assertHeader(0xFFFF, 4, 126);
        assertHeader(0x10000, 10, 127);
        byte[] text = WebSocketFrames.encode(WebSocketFrames.OP_TEXT, "hi".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(new byte[] {(byte) 0x81, 2, 'h', 'i'}, text);
    }

    private static void assertHeader(int length, int headerBytes, int lengthCode) {
        byte[] frame = WebSocketFrames.encode(WebSocketFrames.OP_BINARY, new byte[length]);
        assertEquals(headerBytes + length, frame.length);
        assertEquals((byte) 0x82, frame[0], "FIN + binary");
        assertEquals(lengthCode, frame[1] & 0xFF, "server frames are never masked");
    }

    @Test
    void readerUnmasksAndEnforcesTheFrameCap() throws Exception {
        byte[] mask = {1, 2, 3, 4};
        byte[] payload = "abcde".getBytes(StandardCharsets.UTF_8);
        byte[] wire = new byte[2 + 4 + payload.length];
        wire[0] = (byte) 0x81;
        wire[1] = (byte) (0x80 | payload.length);
        System.arraycopy(mask, 0, wire, 2, 4);
        for (int i = 0; i < payload.length; i++) {
            wire[6 + i] = (byte) (payload[i] ^ mask[i & 3]);
        }
        WebSocketFrames.Frame frame = new WebSocketFrames.Reader(
                new ByteArrayInputStream(wire), 16).next();
        assertTrue(frame.fin());
        assertEquals(WebSocketFrames.OP_TEXT, frame.opcode());
        assertArrayEquals(payload, frame.payload());

        WebSocketFrames.Violation tooBig = assertThrows(WebSocketFrames.Violation.class,
                () -> new WebSocketFrames.Reader(new ByteArrayInputStream(wire), 4).next());
        assertEquals(WebSocketFrames.CLOSE_MESSAGE_TOO_BIG, tooBig.closeCode());
        assertThrows(EOFException.class, () -> new WebSocketFrames.Reader(
                new ByteArrayInputStream(new byte[] {(byte) 0x81}), 16).next());
    }

    @Test
    void closeReasonIsCutOnACodePointBoundary() throws Exception {
        String reason = "é".repeat(200);
        byte[] payload = WebSocketFrames.closePayload(1000, reason);
        assertTrue(payload.length <= WebSocketFrames.MAX_CONTROL_PAYLOAD);
        assertEquals(1000, ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF));
        byte[] text = new byte[payload.length - 2];
        System.arraycopy(payload, 2, text, 0, text.length);
        assertEquals("é".repeat(61), WebSocketFrames.decodeUtf8(text));
        assertEquals(2, WebSocketFrames.closePayload(1000, null).length);
    }

    @Test
    void closeStatusAndUtf8RulesMatchRfc6455() {
        assertTrue(WebSocketFrames.validCloseCode(1000));
        assertTrue(WebSocketFrames.validCloseCode(1011));
        assertTrue(WebSocketFrames.validCloseCode(4999));
        assertFalse(WebSocketFrames.validCloseCode(1004));
        assertFalse(WebSocketFrames.validCloseCode(1005));
        assertFalse(WebSocketFrames.validCloseCode(1006));
        assertFalse(WebSocketFrames.validCloseCode(2999));
        WebSocketFrames.Violation invalid = assertThrows(WebSocketFrames.Violation.class,
                () -> WebSocketFrames.decodeUtf8(new byte[] {(byte) 0xC0, (byte) 0x80}));
        assertEquals(WebSocketFrames.CLOSE_INVALID_PAYLOAD, invalid.closeCode());
    }
}
