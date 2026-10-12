package openggf.timeattack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestAttemptInputRecording {
    @Test
    void checkedDecoderRejectsTrailingBytesInvalidMasksAndOversizedPayloads() throws Exception {
        AttemptInputRecording recording=new AttemptInputRecording(start());recording.appendFrame(0x08,false);
        byte[] encoded=recording.encode();
        assertThrows(java.io.IOException.class,()->AttemptInputRecording.decodeChecked(java.util.Arrays.copyOf(encoded,encoded.length+1)));
        encoded[encoded.length-1]=(byte)0x80;
        assertThrows(java.io.IOException.class,()->AttemptInputRecording.decodeChecked(encoded));
        assertThrows(java.io.IOException.class,()->AttemptInputRecording.decodeChecked(new byte[AttemptInputRecording.MAX_ENCODED_BYTES+1]));
    }

    @Test
    void checkedDecoderRejectsNoncanonicalUtfBeforeHashingCanThrowUncheckedIo() throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();
        try(var output=new java.io.DataOutputStream(bytes)) {
            output.writeUTF("s3k");output.writeByte(0);output.writeByte(0);output.writeUTF("sonic");
            // DataInputStream accepts raw NUL bytes, but canonical modified UTF needs twice the bytes.
            // Re-encoding this decoded value would exceed writeUTF's unsigned-short limit.
            output.writeShort(40000);output.write(new byte[40000]);output.writeInt(0);
        }
        assertTrue(bytes.size()<AttemptInputRecording.MAX_ENCODED_BYTES);
        assertThrows(java.io.IOException.class,()->AttemptInputRecording.decodeChecked(bytes.toByteArray()));
    }
    private static AttemptStartDescriptor start() {
        return new AttemptStartDescriptor("s3k", 0, 0, "sonic", "0.6:cafe1234");
    }

    @Test
    void recordsIdleFramesFromSpawn() {
        AttemptInputRecording rec = new AttemptInputRecording(start());
        rec.appendFrame(0, false);            // spawn frame, idle
        rec.appendFrame(0, false);
        rec.appendFrame(0x08, false);         // first input (RIGHT) at frame 2
        assertEquals(3, rec.frameCount());
        assertEquals(0, rec.heldMaskAt(0));
        assertEquals(0x08, rec.heldMaskAt(2));
    }

    @Test
    void foldsStartHeldIntoBit5() {
        AttemptInputRecording rec = new AttemptInputRecording(start());
        rec.appendFrame(0x10, true);
        assertEquals(0x30, rec.heldMaskAt(0));
    }

    @Test
    void encodeDecodeRoundTripsAndHashIsStable() {
        AttemptInputRecording rec = new AttemptInputRecording(start());
        rec.appendFrame(0, false);
        rec.appendFrame(0x0C, false);
        byte[] encoded = rec.encode();
        AttemptInputRecording back = AttemptInputRecording.decode(encoded);
        assertEquals(rec.frameCount(), back.frameCount());
        assertEquals(start(), back.start());
        assertArrayEquals(rec.sha256(), back.sha256());
        assertEquals(32, rec.sha256().length);
    }

    @Test
    void hashChangesWhenAnyMaskChanges() {
        AttemptInputRecording a = new AttemptInputRecording(start());
        a.appendFrame(0x08, false);
        AttemptInputRecording b = new AttemptInputRecording(start());
        b.appendFrame(0x04, false);
        assertFalse(java.util.Arrays.equals(a.sha256(), b.sha256()));
    }

    @Test
    void decodeRejectsHostileFrameLength() {
        AttemptInputRecording rec = new AttemptInputRecording(start());
        rec.appendFrame(0x08, false);
        byte[] encoded = rec.encode();
        // The frame-count int is the 4 bytes immediately before the single mask byte.
        int lengthOffset = encoded.length - 1 - 4;
        encoded[lengthOffset] = (byte) 0x7F; // claim ~2 billion frames
        assertThrows(java.io.UncheckedIOException.class, () -> AttemptInputRecording.decode(encoded));
    }
}
