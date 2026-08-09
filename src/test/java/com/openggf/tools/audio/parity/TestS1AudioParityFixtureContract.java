package com.openggf.tools.audio.parity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class TestS1AudioParityFixtureContract {
    private static final String EXPECTED_SHA256 =
            "622ff642d0b0835a4f77bee568f2413f288ead3306a8bc2a93e8d8f77f24ca9c";
    private static final Set<String> EXPECTED_ENTRIES = Set.of(
            "BizState 1.0", "BizVersion.txt", "Header.txt", "Comments.txt",
            "Subtitles.txt", "SyncSettings.json", "Input Log.txt");
    private static final Path FIXTURE = Path.of(
            "src/test/resources/audio/parity/s1/s1-soundtest-ghz.bk2");

    @Test
    void controllerOnlySoundTestMovieHasPinnedContract() throws Exception {
        assertTrue(Files.isRegularFile(FIXTURE), "tracked S1 parity BK2 is required");
        assertEquals(EXPECTED_SHA256, sha256(FIXTURE));

        try (ZipFile movie = new ZipFile(FIXTURE.toFile())) {
            Set<String> actualEntries = new HashSet<>();
            movie.stream().forEach(entry -> actualEntries.add(entry.getName()));
            assertEquals(EXPECTED_ENTRIES, actualEntries);

            for (ZipEntry entry : java.util.Collections.list(movie.entries())) {
                String name = entry.getName().toLowerCase(java.util.Locale.ROOT);
                assertFalse(name.contains("savestate"), "savestate payload is forbidden");
                assertFalse(name.endsWith(".gen") || name.endsWith(".bin") || name.endsWith(".rom"),
                        "ROM payload is forbidden");
                assertTrue(entry.getSize() >= 0 && entry.getSize() <= 1_000_000,
                        "unexpected large archive entry: " + entry.getName());
            }

            String header = read(movie, "Header.txt");
            assertEquals("Version 2.11\n", read(movie, "BizVersion.txt"));
            assertTrue(header.contains("MovieVersion BizHawk v2.0.0"));
            assertTrue(header.contains("emuVersion Version 2.11"));
            assertTrue(header.contains("Core Genplus-gx"));
            assertTrue(header.contains("GameName Sonic The Hedgehog (W) (REV01) [!]"));
            assertTrue(read(movie, "SyncSettings.json").contains("gpgx.GPGX"));
            String sha1 = header.lines().filter(line -> line.startsWith("SHA1 ")).findFirst().orElseThrow();
            assertTrue(sha1.substring(5).matches("[0-9A-Fa-f]{32}"), "SHA1 header is opaque metadata");

            String input = read(movie, "Input Log.txt");
            assertEquals(992, input.lines().count());
            assertEquals(1, input.lines().filter("[Input]"::equals).count());
            assertEquals(1, input.lines().filter(line -> line.startsWith("LogKey:")).count());
            assertEquals(1, input.lines().filter("[/Input]"::equals).count());
            assertEquals(989, input.lines().filter(line -> line.startsWith("|")).count());
        }
    }

    private static String read(ZipFile movie, String name) throws IOException {
        ZipEntry entry = movie.getEntry(name);
        assertNotNull(entry);
        try (InputStream stream = movie.getInputStream(entry)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
