package com.openggf.game.soniccd.media;

import com.openggf.data.cd.CueDiscImage;
import com.openggf.data.cd.Iso9660FileSystem;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdProgramCatalogue;
import com.openggf.game.soniccd.CdReleaseVariant;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Opt in with -Dsoniccd.media.root=<directory containing USA, Japan, Europe>. */
class TestSonicCdProgramMedia {
    @ParameterizedTest(name = "{0} user program assets")
    @ValueSource(strings = {"USA", "Japan", "Europe"})
    void allStageHeadersNativeFilesAndCatalogueComponentsMatchDirectUserBytes(String region) throws IOException {
        String configured = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(configured != null && !configured.isBlank(), "Set soniccd.media.root for real-media checks");
        Path directory = Path.of(configured).resolve(region);
        Path cue;
        try (var entries = Files.list(directory)) {
            var cues = entries.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            cue = cues.getFirst();
        }
        CdReleaseVariant variant = CdReleaseVariant.valueOf(region.toUpperCase(Locale.ROOT));
        try (var assets = CdProgramAssets.open(cue, Optional.of(variant), Optional.empty());
             var disc = CueDiscImage.open(cue)) {
            assertEquals(35, assets.identity().tracks().size());
            // Independently streamed from this supplied inventory, not retail revision certification.
            String expectedIdentity = switch (region) {
                case "USA" -> "a6b026a2381f926485cebb2da0bded29319cfd515ef7956a3ca79b18645bdb2b";
                case "Japan" -> "3b82f0701dcc225158020225eba56687df03467ad97435e2cb6a5a2734238109";
                default -> "91fed9c846a688e8d8e8a130923ef8a7af6bcc130d19c6f179aaf69a2e9cf168";
            };
            assertEquals(expectedIdentity, assets.identity().normalizedSha256());
            assertTrue(assets.hasExpectedTrackShape());
            assertEquals(34, assets.identity().tracks().stream().filter(track -> track.mode().name().equals("AUDIO")).count());
            Iso9660FileSystem direct = Iso9660FileSystem.open(disc, 1);
            CdProgramCatalogue catalogue = assets.catalogue().orElseThrow();
            try (FileChannel raw = FileChannel.open(directory.resolve(disc.track(1).sourceFile()), StandardOpenOption.READ)) {
                for (CdNativeFile file : catalogue.files()) {
                    var entry = assets.entry(file);
                    assertEquals(direct.entry(file.isoName()), entry);
                    int count = (int) Math.min(4096, entry.size() - 1);
                    long offset = entry.size() - count;
                    assertArrayEquals(rawUserBytes(raw, entry.extentBlock() * 2048 + offset, count),
                            assets.read(file, offset, count), file.name());
                    try (var stream = assets.openFile(file)) {
                        assertArrayEquals(rawUserBytes(raw, entry.extentBlock() * 2048, count), stream.readNBytes(count));
                        assertEquals(entry.size() - count - 1, stream.skip(entry.size() - count - 1));
                        assertArrayEquals(rawUserBytes(raw, entry.extentBlock() * 2048 + entry.size() - 1, 1), stream.readNBytes(1));
                        assertEquals(-1, stream.read());
                    }
                    if (file.isoName().contains(".MMD;")) {
                        var image = assets.mmd(file);
                        byte[] expected = rawUserBytes(raw, entry.extentBlock() * 2048, (int) entry.size());
                        assertArrayEquals(expected, image.readFile(0, image.fileBytes()), file.name());
                        ByteBuffer header = ByteBuffer.wrap(expected);
                        assertEquals(Byte.toUnsignedInt(header.get(0)), image.header().flags());
                        assertEquals(Byte.toUnsignedInt(header.get(1)), image.header().reserved());
                        assertEquals(Integer.toUnsignedLong(header.getInt(2)), image.header().origin());
                        assertEquals(Short.toUnsignedInt(header.getShort(6)), image.header().sizeCounter());
                        assertEquals(Integer.toUnsignedLong(header.getInt(8)), image.header().entry());
                        assertEquals(Integer.toUnsignedLong(header.getInt(12)), image.header().horizontalInterrupt());
                        assertEquals(Integer.toUnsignedLong(header.getInt(16)), image.header().verticalInterrupt());
                    }
                }
                assertEquals(70, catalogue.stages().size());
                for (var stage : catalogue.stages()) {
                    var image = assets.mmd(stage.file());
                    assertEquals(262144, image.fileBytes());
                    assertEquals(0, image.header().origin());
                    assertEquals(0x200100, image.header().entry());
                    assertEquals(List.of(stage.file(), stage.pcmDriver()), assets.stage(stage.stage())
                            .stream().map(CdProgramAssets.Component::file).toList());
                }
                assertEquals(8192, assets.mmd(CdNativeFile.MDINIT).header().declaredCopyBytes());
                assertEquals(32768, assets.mmd(CdNativeFile.TITLEMAIN).header().declaredCopyBytes());
                assertTrue(assets.mmd(CdNativeFile.TITLEMAIN).unresolvedCopyBytes() > 0);
                for (var command : catalogue.commands()) {
                    if (catalogue.disposition(command) == CdProgramCatalogue.CommandDisposition.INVALID) { continue; }
                    List<CdNativeFile> expected = new ArrayList<>(catalogue.nativeLoadFiles(command));
                    catalogue.programForCommand(command).ifPresent(program -> expected.addAll(program.streamFiles()));
                    var components = assets.components(command);
                    assertEquals(expected, components.stream().map(CdProgramAssets.Component::file).toList(), command.name());
                    for (var component : components) {
                        int count = (int) Math.min(32, component.entry().size());
                        long offset = component.entry().size() - count;
                        try (var stream = assets.slice(component.file(), offset, count).openStream()) {
                            assertArrayEquals(rawUserBytes(raw, component.entry().extentBlock() * 2048 + offset, count),
                                    stream.readAllBytes(), command + " " + component.file());
                        }
                    }
                }
            }
        }
    }

    private static byte[] rawUserBytes(FileChannel channel, long offset, int length) throws IOException {
        byte[] bytes = new byte[length];
        int copied = 0;
        while (copied < length) {
            long current = offset + copied;
            int count = Math.min(length - copied, 2048 - (int) (current % 2048));
            ByteBuffer part = ByteBuffer.wrap(bytes, copied, count).slice();
            long position = current / 2048 * 2352 + 16 + current % 2048;
            while (part.hasRemaining()) {
                if (channel.read(part, position + part.position()) <= 0) { throw new IOException("Truncated direct user input"); }
            }
            copied += count;
        }
        return bytes;
    }
}
