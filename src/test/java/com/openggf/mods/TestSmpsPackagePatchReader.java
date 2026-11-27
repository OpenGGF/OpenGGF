package com.openggf.mods;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.audio.smps.PackageStockResolver;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;

import static com.openggf.mods.SmpsPackageReader.sha256;
import static com.openggf.mods.TestSmpsPackageReader.*;
import static org.junit.jupiter.api.Assertions.*;

class TestSmpsPackagePatchReader {
    @TempDir Path temp;

    @ParameterizedTest
    @CsvSource({
            "00, length",
            "0009, length",
            "8001, length",
            "00150000, truncated run header",
            "00150005000201, truncated literal run",
            "0015001400020102, out of range",
            "001500050000, out of range",
            "001500050001010006000102, separated",
            "00150005000201020006000103, separated",
            "001500050001010004000102, separated"
    })
    void rejectsMalformedDeltasDuringStructuralPreflight(String hex, String reason) throws Exception {
        byte[] delta = HexFormat.of().parseHex(hex);
        assertFailure("track.delta", reason, delta, patchManifest(delta, baseBank(), songBank()), null);
    }

    @Test
    void enforcesStoredHashAndEntryLimit() throws Exception {
        byte[] delta = {0, 21};
        var doc = patchManifest(delta, baseBank(), songBank());
        assertFailure("track.delta", "expected SHA-256", new byte[]{0, 22}, doc, null);
        byte[] oversized = new byte[98_307];
        assertFailure("track.delta", "limit", oversized, patchManifest(oversized, baseBank(), songBank()), null);
    }

    @Test
    void checksBankMetadataAgainstTheTargetLength() throws Exception {
        byte[] delta = {0, 21};
        var doc = patchManifest(delta, baseBank(), songBank());
        doc.withObject("/bank").put("headerOffset", 16);
        assertFailure("headerOffset", "integer in", delta, doc, null);
        doc.withObject("/bank").put("headerOffset", 0).withArray("relocations").add(20);
        assertFailure("relocation", "integer in", delta, doc, null);
    }

    @Test
    void requiresPatchProvenanceAndForbidsEmbeddedBankData() throws Exception {
        byte[] delta = {0, 21};
        var doc = patchManifest(delta, baseBank(), songBank());
        var invalid = doc.deepCopy();
        invalid.withObject("/provenance").putNull("derivedFromRetailSong");
        assertFailure("derivedFromRetailSong", "requires", delta, invalid, null);
        invalid = doc.deepCopy();
        invalid.withObject("/provenance").put("containsRetailSongData", true);
        assertFailure("DERIVED_FROM_RETAIL_SONG", "RETAIL_SONG_DATA", delta, invalid, null);
        invalid = doc.deepCopy();
        invalid.withObject("/provenance").putArray("warnings");
        assertFailure("DERIVED_FROM_RETAIL_SONG", "must", delta, invalid, null);
        invalid = doc.deepCopy();
        invalid.putObject("source");
        assertFailure("source", "null", delta, invalid, null);
        invalid = doc.deepCopy();
        invalid.withObject("/bank").withArray("fills").addObject();
        assertFailure("fills", "empty", delta, invalid, null);
        invalid = doc.deepCopy();
        asset(invalid, "fm/0", "FM_VOICE").put("bankOffset", 0);
        assertFailure("bankOffset", "patch", delta, invalid, null);
        invalid = doc.deepCopy();
        invalid.withObject("/requires").put("stockAssets", false);
        assertFailure("requires", "retail", delta, invalid, null);
        for (String driver : new String[]{"s1", "s2"}) {
            invalid = doc.deepCopy().put("driver", driver);
            invalid.withObject("/driverOptions").putNull("speedUpTempo");
            assertFailure("S3K", "patch", delta, invalid, null, driver);
        }
        try (var root = packed(temp, doc.toString(), songBank(), Map.of("track.delta", delta))) {
            var error = assertThrows(IOException.class, () -> reader(root, null).read("s3k"));
            assertTrue(error.getMessage().contains("Unexpected package entry track.bin"), error::getMessage);
        }
    }

    @Test
    void resolvesAndChecksBothHashesAndRejectsUnchangedLiterals() throws Exception {
        byte[] base = baseBank();
        byte[] modified = songBank();
        modified[5] = 1;
        byte[] delta = {0, 21, 0, 5, 0, 1, 1};
        var doc = patchManifest(delta, base, modified);
        try (var root = packed(temp, doc.toString(), null, Map.of("track.delta", delta))) {
            assertNull(reader(root, null).read("s3k"), "Catalog preflight has no ROM");
            var result = reader(root, locator -> {
                assertEquals(new PackageStockResolver.Locator("bank", 0, 42), locator);
                return base;
            }).read("s3k");
            assertArrayEquals(modified, ((Sonic3kSmpsData) result.data()).getBankData());
            assertArrayEquals(baseBank(), base, "Patching must leave the verified base untouched");
        }
        byte[] otherBase = new byte[base.length];
        assertFailure("s3k song 42", sha256(base) + ", actual " + sha256(otherBase), delta, doc, loc -> otherBase);
        assertFailure("s3k song 42", "missing", delta, doc, loc -> null);
        var assembledMismatch = doc.deepCopy();
        assembledMismatch.withObject("/bank").put("assembledSha256", sha256(songBank()));
        assertFailure("Assembled bank", sha256(songBank()) + ", actual " + sha256(modified),
                delta, assembledMismatch, loc -> base);
        byte[] unchanged = {0, 21, 0, 5, 0, 1, 0};
        assertFailure("track.delta", "unchanged", unchanged, patchManifest(unchanged, base, songBank()), loc -> base);
    }

    @Test
    void requiresExplicitCoverageOfExtensionBytesIncludingZeros() throws Exception {
        byte[] base = songBank();
        byte[] extended = {0, 23, 0, 21, 0, 2, 0, 1};
        byte[] expected = Arrays.copyOf(base, 23);
        expected[22] = 1;
        assertArrayEquals(expected, RetailBankDelta.apply(extended, base));
        for (String hex : new String[]{"0017", "00170016000101", "00170015000100"}) {
            byte[] missing = HexFormat.of().parseHex(hex);
            var error = assertThrows(IOException.class, () -> RetailBankDelta.apply(missing, base));
            assertTrue(error.getMessage().contains("track.delta is missing extension bytes"), error::getMessage);
        }
    }

    private void assertFailure(String subject, String reason, byte[] delta, ObjectNode doc, PackageStockResolver stock)
            throws Exception {
        assertFailure(subject, reason, delta, doc, stock, "s3k");
    }

    private void assertFailure(String subject, String reason, byte[] delta, ObjectNode doc, PackageStockResolver stock,
                               String driver) throws Exception {
        try (var root = packed(temp, doc.toString(), null, Map.of("track.delta", delta))) {
            var error = assertThrows(IOException.class, () -> reader(root, stock).read(driver));
            assertTrue(error.getMessage().contains(subject), error::getMessage);
            assertTrue(error.getMessage().contains(reason), error::getMessage);
        }
    }

    private static byte[] baseBank() {
        return Arrays.copyOf(songBank(), SmpsPackageReader.BANK_LIMIT);
    }

    static ObjectNode patchManifest(byte[] delta, byte[] base, byte[] assembled) throws IOException {
        var doc = manifest(assembled);
        stock(doc);
        var provenance = doc.withObject("/provenance");
        provenance.putObject("derivedFromRetailSong").put("game", "s3k").put("songId", 42)
                .put("bankSha256", sha256(base));
        provenance.withArray("warnings").add("DERIVED_FROM_RETAIL_SONG");
        doc.withObject("/bank").put("form", "patch").put("path", "track.delta").put("sha256", sha256(delta));
        return doc;
    }
}
