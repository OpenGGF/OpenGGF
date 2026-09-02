package com.openggf.tools.audio.parity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestOverrideResumeReferenceBundle {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temp;

    @Test
    void opensTheExactFourMemberCommitObject() throws Exception {
        Path parity = validBundle();

        OverrideResumeReferenceBundle bundle = OverrideResumeReferenceBundle.open(parity);

        assertEquals("s1", bundle.s1().reference().path("game").asText());
        assertEquals("s2", bundle.s2().metadata().path("game").asText());
        assertEquals(4, bundle.memberInventory().size());
    }

    @Test
    void absentBundlePreservesTheAuthenticatedAuthorityLimitation() throws Exception {
        Path parity = Files.createDirectories(temp.resolve("absent"));

        OverrideResumeReferenceBundle.ReferenceUnavailableException failure = assertThrows(
                OverrideResumeReferenceBundle.ReferenceUnavailableException.class,
                () -> OverrideResumeReferenceBundle.open(parity));

        assertEquals("FRESH_AUTHENTICATED_NATIVE_GPGX_AUTHORITY_UNAVAILABLE", failure.code());
    }

    @Test
    void rejectsMissingExtraSymlinkedAndHashMismatchedMembers() throws Exception {
        Path parity = validBundle();
        Path bundle = parity.resolve(OverrideResumeReferenceBundle.BUNDLE_NAME);
        Path s1 = bundle.resolve("s1");
        Path metadata = s1.resolve("s1-override-resume-metadata.v1.json");
        Path reference = s1.resolve("s1-override-resume-reference.v1.jsonl.gz");

        Files.delete(metadata);
        assertThrows(OverrideResumeReferenceBundle.InvalidBundleException.class,
                () -> OverrideResumeReferenceBundle.open(parity));
        writeGame(bundle, "s1");

        Files.writeString(bundle.resolve("extra"), "extra\n");
        assertThrows(OverrideResumeReferenceBundle.InvalidBundleException.class,
                () -> OverrideResumeReferenceBundle.open(parity));
        Files.delete(bundle.resolve("extra"));

        Path outside = temp.resolve("outside.gz");
        Files.copy(reference, outside);
        Files.delete(reference);
        Files.createSymbolicLink(reference, outside);
        assertThrows(OverrideResumeReferenceBundle.InvalidBundleException.class,
                () -> OverrideResumeReferenceBundle.open(parity));
        Files.delete(reference);
        Files.copy(outside, reference);

        byte[] changed = Files.readAllBytes(reference);
        changed[changed.length - 1] ^= 1;
        Files.write(reference, changed);
        assertThrows(OverrideResumeReferenceBundle.InvalidBundleException.class,
                () -> OverrideResumeReferenceBundle.open(parity));
    }

    @Test
    void rejectsSchemaInventoryAndLogicalDigestMismatches() throws Exception {
        Path parity = validBundle();
        Path bundle = parity.resolve(OverrideResumeReferenceBundle.BUNDLE_NAME);
        Path metadata = bundle.resolve("s2/s2-override-resume-metadata.v1.json");
        Map<String, Object> value = JSON.readValue(Files.readAllBytes(metadata), Map.class);

        value.put("schema", "wrong");
        Files.write(metadata, JSON.writeValueAsBytes(value));
        assertThrows(OverrideResumeReferenceBundle.InvalidBundleException.class,
                () -> OverrideResumeReferenceBundle.open(parity));

        writeGame(bundle, "s2");
        value = JSON.readValue(Files.readAllBytes(metadata), Map.class);
        value.put("bundle_member_inventory", List.of("wrong"));
        Files.write(metadata, JSON.writeValueAsBytes(value));
        assertThrows(OverrideResumeReferenceBundle.InvalidBundleException.class,
                () -> OverrideResumeReferenceBundle.open(parity));

        writeGame(bundle, "s2");
        value = JSON.readValue(Files.readAllBytes(metadata), Map.class);
        value.put("logical_sha256", "0".repeat(64));
        Files.write(metadata, JSON.writeValueAsBytes(value));
        assertThrows(OverrideResumeReferenceBundle.InvalidBundleException.class,
                () -> OverrideResumeReferenceBundle.open(parity));
    }

    private Path validBundle() throws Exception {
        Path parity = Files.createDirectories(temp.resolve("parity"));
        Path bundle = Files.createDirectories(parity.resolve(
                OverrideResumeReferenceBundle.BUNDLE_NAME));
        writeGame(bundle, "s1");
        writeGame(bundle, "s2");
        return parity;
    }

    private static void writeGame(Path bundle, String game) throws Exception {
        Path directory = Files.createDirectories(bundle.resolve(game));
        String referenceName = game + "-override-resume-reference.v1.jsonl.gz";
        String metadataName = game + "-override-resume-metadata.v1.json";
        byte[] logical = JSON.writeValueAsBytes(Map.of(
                "schema", "openggf.override-resume-first-divergence-reference.v1",
                "game", game, "boundary", Map.of(), "pcm", Map.of()));
        logical = (new String(logical, StandardCharsets.UTF_8) + "\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] stored = gzip(logical);
        Files.write(directory.resolve(referenceName), stored);
        Map<String, Object> metadata = Map.ofEntries(
                Map.entry("schema", "openggf.override-resume-first-divergence-metadata.v1"),
                Map.entry("game", game),
                Map.entry("raw_sha256", List.of("1".repeat(64), "1".repeat(64))),
                Map.entry("raw_byte_count", 1),
                Map.entry("attestation_sha256", List.of("2".repeat(64), "2".repeat(64))),
                Map.entry("record_count", 1),
                Map.entry("logical_byte_count", logical.length),
                Map.entry("logical_sha256", digest(logical)),
                Map.entry("stored_byte_count", stored.length),
                Map.entry("stored_sha256", digest(stored)),
                Map.entry("bundle_relative_root", "src/test/resources/audio/parity/"
                        + OverrideResumeReferenceBundle.BUNDLE_NAME),
                Map.entry("bundle_member_inventory", OverrideResumeReferenceBundle.EXACT_INVENTORY),
                Map.entry("publication_protocol", "linux-atomic-bundle-rename-noreplace-v1"),
                Map.entry("namespace_lock_precondition", "cooperative namespace-stable root"));
        Files.write(directory.resolve(metadataName),
                (JSON.writeValueAsString(metadata) + "\n")
                        .getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] gzip(byte[] logical) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(logical);
        }
        return output.toByteArray();
    }

    private static String digest(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
