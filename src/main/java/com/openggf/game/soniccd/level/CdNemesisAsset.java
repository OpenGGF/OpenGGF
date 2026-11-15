package com.openggf.game.soniccd.level;

import com.openggf.data.compression.NemesisReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/** Bounded adapter to the existing decoder; a header declaration alone is never a decode result. */
public final class CdNemesisAsset {
    public record Decoded(CdAssetBytes bytes, int consumedInputBytes) { }

    private CdNemesisAsset() { }

    /**
     * Decodes one reviewed input view, including any explicitly supplied real lookahead bytes.
     * Does not synthesize EOF padding. The cursor counts bytes actually requested by NemesisReader.
     */
    public static Decoded decode(byte[] input, int expectedOutputBytes, int outputCapacity) throws IOException {
        if (expectedOutputBytes <= 0 || expectedOutputBytes % 32 != 0
                || expectedOutputBytes > outputCapacity || outputCapacity > 0x10000) {
            throw new IllegalArgumentException("Invalid bounded Nemesis output contract");
        }
        if (input.length < 2 || input.length > 0x10000) {
            throw new IOException("Nemesis input outside bounded asset extent");
        }
        int declared = (((input[0] & 0x7F) << 8) | Byte.toUnsignedInt(input[1])) * 32;
        if (declared != expectedOutputBytes) {
            throw new IOException("Nemesis header output differs from the reviewed asset contract");
        }
        try (var channel = new Input(input)) {
            byte[] decoded = NemesisReader.decompress(channel);
            if (decoded.length != expectedOutputBytes || decoded.length > outputCapacity) {
                throw new IOException("Nemesis output exceeds or differs from its bounded contract");
            }
            return new Decoded(CdAssetBytes.copyOf(decoded), channel.cursor());
        }
    }

    private static final class Input implements ReadableByteChannel {
        private final ByteBuffer input;
        private boolean open = true;

        private Input(byte[] bytes) { input = ByteBuffer.wrap(bytes).asReadOnlyBuffer(); }
        int cursor() { return input.position(); }
        @Override public boolean isOpen() { return open; }
        @Override public void close() { open = false; }
        @Override public int read(ByteBuffer destination) throws IOException {
            if (!open) { throw new IOException("Nemesis asset channel is closed"); }
            if (!destination.hasRemaining()) { return 0; }
            if (!input.hasRemaining()) { return -1; }
            int count = Math.min(destination.remaining(), input.remaining());
            destination.put(input.slice(input.position(), count));
            input.position(input.position() + count);
            return count;
        }
    }
}
