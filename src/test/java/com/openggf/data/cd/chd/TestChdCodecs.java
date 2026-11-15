package com.openggf.data.cd.chd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Codec checks beyond the small CHD fixtures, against standard encoders run on generated input
 * (no third-party content): {@code text.bin} is 40,000 bytes of words, random runs and back-references
 * from a seeded generator; {@code audio.raw} is 22,050 stereo 16-bit frames of tones, noise and silence.
 * Encoded with zstd 1.5 ({@code -19 --no-check}, {@code -1 --check}), xz ({@code --format=raw
 * --lzma1=preset=9,lc=3,lp=0,pb=2}) and flac ({@code -8 --blocksize=1176}, raw little-endian input).
 */
class TestChdCodecs {
    private static final String TEXT_SHA256 = "f7943b46974bab772e91c5676b3e0a8b35a6442d734f2e1f72a42b2ae9bef423";
    /** SHA-256 of audio.raw with every sample swapped to big-endian, the CHD's stored order. */
    private static final String AUDIO_BIG_ENDIAN_SHA256 = "c38d4c6e5593eb146a8335c07e9c7f6fa2dccf4facd376d8a168c3eeac4bbb3f";

    private static byte[] resource(String name) throws IOException {
        return Files.readAllBytes(Path.of("src/test/resources/cd/chd/codec", name));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"text-19.zst", "text-1-check.zst"})
    void zstdFramesDecode(String name) throws Exception {
        byte[] src = resource(name);
        byte[] out = new byte[40_000];
        new ZstdDecoder().decode(src, 0, src.length, out, out.length);
        assertEquals(TEXT_SHA256, sha256(out));
        assertThrows(ChdFormatException.class, () -> new ZstdDecoder().decode(src, 0, src.length, new byte[39_999], 39_999));
        assertThrows(ChdFormatException.class, () -> new ZstdDecoder().decode(src, 0, src.length - 7, out, out.length));
    }

    @Test
    void rawLzmaDecodes() throws Exception {
        byte[] src = resource("text.lzma");
        byte[] out = new byte[40_000];
        new LzmaDecoder().decode(src, 0, src.length, out);
        assertEquals(TEXT_SHA256, sha256(out));
        assertThrows(ChdFormatException.class, () -> new LzmaDecoder().decode(src, 0, src.length / 2, new byte[40_000]));
    }

    @Test
    void flacFramesDecodeToBigEndianSamples() throws Exception {
        byte[] file = resource("audio.flac");
        int frames = framesStart(file);
        byte[] out = new byte[22_050 * 4];
        int used = new FlacDecoder().decode(file, frames, file.length - frames, out, out.length);
        assertEquals(file.length - frames, used, "the decoder reports where the frames end");
        assertEquals(AUDIO_BIG_ENDIAN_SHA256, sha256(out));
        byte[] corrupt = file.clone();
        corrupt[frames + 5000] ^= 0x04;
        assertThrows(ChdFormatException.class,
                () -> new FlacDecoder().decode(corrupt, frames, corrupt.length - frames, new byte[out.length], out.length));
    }

    /** Random corruption must fail as a format error, never as an unchecked exception. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"text-19.zst", "text.lzma", "audio.flac"})
    void corruptStreamsFailCleanly(String name) throws Exception {
        byte[] src = resource(name);
        int start = name.endsWith(".flac") ? framesStart(src) : 0;
        Random random = new Random(name.hashCode());
        for (int round = 0; round < 300; round++) {
            byte[] copy = src.clone();
            copy[start + random.nextInt(copy.length - start)] ^= (byte) (1 << random.nextInt(8));
            int length = random.nextInt(4) == 0 ? random.nextInt(copy.length - start) : copy.length - start;
            try {
                if (name.endsWith(".zst")) {
                    new ZstdDecoder().decode(copy, start, length, new byte[40_000], 40_000);
                } else if (name.endsWith(".lzma")) {
                    new LzmaDecoder().decode(copy, start, length, new byte[40_000]);
                } else {
                    new FlacDecoder().decode(copy, start, length, new byte[22_050 * 4], 22_050 * 4);
                }
            } catch (ChdFormatException expected) {
                // Corruption may also decode to different bytes; only a crash is a failure.
            }
        }
    }

    @Test
    void crc16IsCcittFalse() {
        assertEquals(0x29B1, Crc16.of("123456789".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, 9));
    }

    /**
     * The uncompressed fixture stores its sectors verbatim, with parity from the fixture generator's
     * independent ECMA-130 encoder; stripping and regenerating must reproduce them, and a changed
     * user byte must change the parity.
     */
    @Test
    void eccRegenerationReproducesIndependentlyEncodedSectors() throws IOException {
        try (var disc = com.openggf.data.cd.ChdDiscImage.open(Path.of("src/test/resources/cd/chd/mixed-none.chd"))) {
            for (int frame : new int[] {0, 16, 25}) {
                byte[] stored = disc.readRawFrames(1, frame, 1);
                byte[] sector = stored.clone();
                java.util.Arrays.fill(sector, 0, 12, (byte) 0);
                java.util.Arrays.fill(sector, 0x81C, 2352, (byte) 0);
                CdEcc.regenerate(sector, 0);
                assertArrayEquals(stored, sector, "sector " + frame);
                sector[100] ^= 1;
                CdEcc.regenerate(sector, 0);
                assertFalse(java.util.Arrays.equals(java.util.Arrays.copyOfRange(stored, 0x81C, 2352),
                        java.util.Arrays.copyOfRange(sector, 0x81C, 2352)), "parity must depend on the data");
            }
        }
    }

    /** Skips "fLaC" and the metadata blocks to the first frame. */
    private static int framesStart(byte[] file) {
        assertEquals("fLaC", new String(file, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        int at = 4;
        while (true) {
            boolean last = (file[at] & 0x80) != 0;
            int length = ((file[at + 1] & 0xFF) << 16) | ((file[at + 2] & 0xFF) << 8) | (file[at + 3] & 0xFF);
            at += 4 + length;
            if (last) {
                return at;
            }
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
