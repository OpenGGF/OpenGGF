package com.openggf.data.cd;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** Opt in with -Dsoniccd.media.root=<directory containing USA, Europe and Japan>. No bundled media. */
class TestSonicCdDiscMedia {
    @ParameterizedTest(name = "{0} user-supplied disc")
    @ValueSource(strings = {"USA", "Europe", "Japan"})
    void readsRegionalDirectoriesDataAndAudioWithoutLoadingVideos(String region) throws IOException {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.media.root for real-disc checks");
        Path regionDirectory = Path.of(configured).resolve(region);
        Path cue;
        try (var paths = Files.list(regionDirectory)) {
            var cues = paths.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Expected one explicit regional CUE");
            cue = cues.getFirst();
        }
        long expectedFrames = switch (region) {
            case "USA" -> 59261;
            case "Europe" -> 55098;
            default -> 55099;
        };
        try (CueDiscImage disc = CueDiscImage.open(cue)) {
            assertEquals(35, disc.tracks().size());
            assertEquals(1, disc.tracks().stream().filter(track -> track.mode().isMode1()).count());
            assertEquals(expectedFrames, disc.track(1).programFrames());
            Iso9660FileSystem fs = Iso9660FileSystem.open(disc, 1);
            assertEquals(expectedFrames - 150, fs.volumeBlocks());
            assertEquals(20, fs.root().extentBlock());
            assertEquals(6144, fs.root().size());
            assertEquals(131, fs.files().size());
            assertEquals(131, fs.listDirectory("/").size());
            assertEquals(262144, fs.entry("R11A__.MMD").size());
            assertEquals(952, fs.entry("R11A__.MMD").extentBlock());
            Path data = cue.getParent().resolve(disc.track(1).sourceFile());
            try (FileChannel channel = FileChannel.open(data, StandardOpenOption.READ)) {
                for (Iso9660FileSystem.Entry entry : fs.files()) {
                    int count = (int) Math.min(4096, entry.size());
                    long offset = Math.max(0, entry.size() - count);
                    byte[] expected = rawUserBytes(channel, entry.extentBlock() * 2048 + offset, count);
                    assertArrayEquals(expected, fs.readFile(entry.path(), offset, count), entry.path());
                }
                Iso9660FileSystem.Entry stage = fs.entry("R11A__.MMD");
                assertArrayEquals(rawUserBytes(channel, stage.extentBlock() * 2048 + 2040, 48),
                        fs.readFile(stage.path(), 2040, 48));
                Iso9660FileSystem.Entry video = fs.entry("BADEND.STM");
                assertTrue(video.size() > CueDiscImage.MAX_READ_BYTES);
                assertThrows(IOException.class, () -> fs.readFile(video.path()));
                try (InputStream stream = fs.openFile(video.path())) {
                    assertArrayEquals(rawUserBytes(channel, video.extentBlock() * 2048, 4096), stream.readNBytes(4096));
                    long skip = video.size() - 4096 - 32;
                    assertEquals(skip, stream.skip(skip));
                    assertArrayEquals(rawUserBytes(channel, video.extentBlock() * 2048 + video.size() - 32, 32),
                            stream.readNBytes(32));
                    assertEquals(-1, stream.read());
                }
            }
            for (DiscTrack track : disc.tracks().subList(1, 35)) {
                assertEquals(DiscTrack.Mode.AUDIO, track.mode());
                assertEquals(0, track.syntheticPregapFrames());
                assertTrue(track.filePregapFrames() >= 149 && track.filePregapFrames() <= 152);
                Path source = cue.getParent().resolve(track.sourceFile());
                try (FileChannel channel = FileChannel.open(source, StandardOpenOption.READ)) {
                    byte[] expected = rawBytes(channel, track.index01Frame() * 2352 - 2352, 4704);
                    assertArrayEquals(expected, disc.readAudioFrames(track.number(), -1, 2));
                    byte[] last = rawBytes(channel, channel.size() - 2352, 2352);
                    assertArrayEquals(last, disc.readAudioFrames(track.number(), track.programFrames() - 1, 1));
                }
            }
        }
    }

    private static byte[] rawUserBytes(FileChannel channel, long offset, int length) throws IOException {
        byte[] result = new byte[length];
        for (int copied = 0; copied < length;) {
            long current = offset + copied;
            int count = Math.min(length - copied, 2048 - (int) (current % 2048));
            byte[] sectorPart = rawBytes(channel, current / 2048 * 2352 + 16 + current % 2048, count);
            System.arraycopy(sectorPart, 0, result, copied, count);
            copied += count;
        }
        return result;
    }

    private static byte[] rawBytes(FileChannel channel, long offset, int length) throws IOException {
        ByteBuffer result = ByteBuffer.allocate(length);
        while (result.hasRemaining()) {
            int count = channel.read(result, offset + result.position());
            if (count <= 0) {
                throw new IOException("Truncated real-media comparison input");
            }
        }
        return result.array();
    }
}
