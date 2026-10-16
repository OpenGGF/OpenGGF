package com.openggf.tools.timing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TestS3kExactLoadTimeProfile {
    @TempDir Path temp;
    private static final String FP = "sha256:1135c7e6b6f9e9dfa4ce84ed5bcce9bc5ad7289a14a1c51ce702b67a0773a451";

    @Test void acceptsLiteralModuleAndTopLevelFifoProof() throws Exception {
        for (int pc : new int[] {0x1B46, 0x1BEE}) {
            Path p = temp.resolve("proof-" + pc);
            Files.writeString(p, row("a".repeat(64), "exact_fifo_callbacks", pc, true, 1));
            var result = S3kLoadTimeProfileGenerator.generate(List.of(p));
            assertEquals(1, result.uniqueEligibleFingerprints());
            assertEquals(1, result.eligible().get(FP).getFirst().serviceOpportunities());
        }
    }

    @Test void unknownPcLegacyPrecisionAndInventedParentAreRejected() throws Exception {
        for (String content : List.of(row("a".repeat(64), "exact_fifo_callbacks", 0x5AE0A, true, 1),
                row("a".repeat(64), "exact_callback", 0x1B46, true, 1),
                row("a".repeat(64), "exact_fifo_callbacks", 0x1B46, true, 1).replace("\"parent_fingerprint\":null", "\"parent_fingerprint\":\"" + FP + "\""))) {
            Path p = temp.resolve("bad"); Files.writeString(p, content);
            assertThrows(IllegalArgumentException.class, () -> S3kLoadTimeProfileGenerator.generate(List.of(p)));
        }
    }

    @Test void censoredUnclassifiedAndZeroOpportunitiesNeverPublish() throws Exception {
        for (String content : List.of(row("a".repeat(64), "head_service_censored", 0, true, 1),
                row("a".repeat(64), "exact_fifo_callbacks", 0x1B46, false, 1),
                row("a".repeat(64), "exact_fifo_callbacks", 0x1B46, true, 0))) {
            Path p = temp.resolve("excluded"); Files.writeString(p, content);
            var result = S3kLoadTimeProfileGenerator.generate(List.of(p));
            assertEquals(0, result.uniqueEligibleFingerprints());
        }
    }

    @Test void legacySchemaCannotSmuggleNewSubmissionPc() throws Exception {
        Path p = temp.resolve("legacy");
        Files.writeString(p, row("a".repeat(64), "head_service_censored", 0, true, 1)
                .replace("measurement_schema\":2", "measurement_schema\":1")
                .replace("load-time-measurement-v2", "load-time-measurement-v1"));
        assertThrows(IllegalArgumentException.class, () -> S3kLoadTimeProfileGenerator.generate(List.of(p)));
    }

    @Test void supplementPreservesKnownCostsEstimatorAndProvenance() throws Exception {
        Path movie = temp.resolve("declared.bk2"); Files.writeString(movie, "input");
        Path input = temp.resolve("new.jsonl"); Files.writeString(input, row(hash(movie), "exact_fifo_callbacks", 0x1B46, true, 1));
        Path base = temp.resolve("base.json");
        Files.writeString(base, "{\"formatVersion\":1,\"profile\":\"s3k-fast\",\"serviceModel\":\"s3k-kos-v1\",\"fixtures\":[\"original\"],\"estimator\":{\"kept\":true},\"entries\":[{\"submissionFingerprint\":\"" + FP + "\",\"serviceFrames\":99,\"fixtureIndexes\":[0]}]}");
        var json = new ObjectMapper();
        var original = json.readTree(Files.readString(base));
        var result = json.readTree(S3kLoadTimeProfileSupplement.supplement(base, movie, input, Map.of(FP, 1L)));
        assertEquals(original, result);
    }

    @Test void supplementLayoutReproducesTheCheckedInFastManifestBytes() throws Exception {
        // A supplement must append rows, not reformat the hand-tuned FAST manifest.
        Path fast = Path.of("src/main/resources/load-time-profiles/s3k-fast-v1.json");
        String original = Files.readString(fast);
        assertEquals(original, S3kLoadTimeProfileSupplement.manifestJson(new ObjectMapper().readTree(original)));
    }

    @Test void supplementAppendsOnlySelectedMatchingSource() throws Exception {
        Path movie = temp.resolve("declared.bk2"); Files.writeString(movie, "input");
        Path input = temp.resolve("new.jsonl"); Files.writeString(input, row(hash(movie), "exact_fifo_callbacks", 0x1B46, true, 1));
        Path base = temp.resolve("base.json"); Files.writeString(base, "{\"formatVersion\":1,\"profile\":\"s3k-fast\",\"serviceModel\":\"s3k-kos-v1\",\"fixtures\":[\"old\"],\"entries\":[]}");
        var result = new ObjectMapper().readTree(S3kLoadTimeProfileSupplement.supplement(base, movie, input, Map.of(FP, 1L)));
        assertEquals(1, result.path("entries").size());
        assertEquals(1, result.path("entries").get(0).path("serviceFrames").asInt());
        assertEquals("old", result.path("fixtures").get(0).asText());
        assertEquals("declared.bk2", result.path("fixtures").get(1).asText());
        assertThrows(IllegalArgumentException.class, () -> S3kLoadTimeProfileSupplement.supplement(base, movie, input, Map.of(FP, 2L)));
    }

    @Test void supplementRejectsLegacyAndWrongExecutedMovie() throws Exception {
        Path movie = temp.resolve("declared.bk2"); Files.writeString(movie, "input");
        Path input = temp.resolve("bad.jsonl");
        Path base = temp.resolve("base.json"); Files.writeString(base, "{}");
        Files.writeString(input, row("a".repeat(64), "exact_fifo_callbacks", 0x1B46, true, 1));
        assertThrows(IllegalArgumentException.class, () -> S3kLoadTimeProfileSupplement.supplement(base, movie, input, Map.of(FP, 1L)));
        Files.writeString(input, row(hash(movie), "exact_fifo_callbacks", 0x1B46, true, 1).replace("measurement_schema\":2", "measurement_schema\":1"));
        assertThrows(IllegalArgumentException.class, () -> S3kLoadTimeProfileSupplement.supplement(base, movie, input, Map.of(FP, 1L)));
    }

    @Test void directLegacyPublicationFailsBeforeOpeningV1InputsOrWritingOutputs() throws Exception {
        Path input = temp.resolve("v2.jsonl");
        Files.writeString(input, row("a".repeat(64), "exact_fifo_callbacks", 0x1B46, true, 1));
        var result = S3kLoadTimeProfileGenerator.generate(List.of(input));
        var direct = assertThrows(IllegalArgumentException.class, result::publicationTsv);
        assertTrue(direct.getMessage().contains("supplemental publication path"));
        Path manifest = temp.resolve("manifest.json"), report = temp.resolve("report.md"), audit = temp.resolve("audit.tsv");
        var outer = assertThrows(IllegalArgumentException.class,
                () -> S3kLoadTimeProfileGenerator.publish(result, manifest, report, audit));
        assertTrue(outer.getMessage().contains("supplemental publication path"));
        assertFalse(Files.exists(manifest));
        assertFalse(Files.exists(report));
        assertFalse(Files.exists(audit));
        // Diagnostic parsing remains supported; neither entry grants legacy publication authority.
        assertEquals(1, result.uniqueEligibleFingerprints());
    }

    @Test void supplementalCliRetainsVerifiedSetupRecorderAndAllInputOutputHashes() throws Exception {
        String[] args = cliArguments();
        S3kLoadTimeProfileSupplement.main(args);
        String audit = Files.readString(Path.of(args[8]));
        assertTrue(audit.contains("# recorder_commit\t" + "f".repeat(40) + "\n"));
        assertTrue(audit.contains("# frozen_setup_sha256\t" + args[5] + "\n"));
        for (int argument : new int[] {0, 1, 2, 3, 4}) {
            Path input = Path.of(args[argument]);
            assertTrue(audit.contains("input\t" + input.getFileName() + "\tsha256\t" + hash(input) + "\n"));
        }
        Path output = Path.of(args[7]);
        assertTrue(audit.contains("output\t" + output.getFileName() + "\tsha256\t" + hash(output) + "\n"));
        assertEquals(1, new ObjectMapper().readTree(Files.readString(output)).path("entries").size());
    }

    @Test void missingMalformedOrChangedPublicationIdentityWritesNothing() throws Exception {
        String[] valid = cliArguments();
        for (int field : new int[] {5, 6}) {
            for (String invalid : List.of("", "not-hex", "a".repeat(field == 5 ? 63 : 39))) {
                String[] bad = valid.clone();
                bad[field] = invalid;
                assertThrows(IllegalArgumentException.class, () -> S3kLoadTimeProfileSupplement.main(bad));
                assertFalse(Files.exists(Path.of(valid[7])));
                assertFalse(Files.exists(Path.of(valid[8])));
            }
        }
        String[] absent = valid.clone();
        absent[4] = temp.resolve("missing-setup.json").toString();
        assertThrows(IllegalArgumentException.class, () -> S3kLoadTimeProfileSupplement.main(absent));
        Files.writeString(Path.of(valid[4]), "changed after freezing");
        var changed = assertThrows(IllegalArgumentException.class, () -> S3kLoadTimeProfileSupplement.main(valid));
        assertTrue(changed.getMessage().contains("frozen setup bytes disagree"));
        assertFalse(Files.exists(Path.of(valid[7])));
        assertFalse(Files.exists(Path.of(valid[8])));
        assertThrows(IllegalArgumentException.class, () -> S3kLoadTimeProfileSupplement.main(new String[6]));
    }

    private String[] cliArguments() throws Exception {
        Path movie = temp.resolve("declared.bk2");
        Files.writeString(movie, "independent authoring inputs");
        Path input = temp.resolve("new.jsonl");
        Files.writeString(input, row(hash(movie), "exact_fifo_callbacks", 0x1B46, true, 1));
        Path base = temp.resolve("base.json");
        Files.writeString(base, "{\"formatVersion\":1,\"profile\":\"s3k-fast\",\"serviceModel\":\"s3k-kos-v1\",\"fixtures\":[],\"entries\":[]}");
        Path inventory = temp.resolve("selected.tsv");
        Files.writeString(inventory, "1\t" + FP + "\n");
        Path setup = temp.resolve("frozen-setup.json");
        Files.writeString(setup, "{\"frozen_utc\":\"2026-10-14T23:00:00Z\",\"scope\":\"independently specified setup\"}");
        return new String[] {base.toString(), movie.toString(), input.toString(), inventory.toString(),
                setup.toString(), hash(setup), "f".repeat(40), temp.resolve("output.json").toString(), temp.resolve("publication.tsv").toString()};
    }

    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static String row(String movieHash, String precision, int pc, boolean classified, int cost) {
        return "{\"measurement_schema\":2,\"recorder_version\":\"load-time-measurement-v2\","
            + "\"fixture\":\"declared.bk2\",\"movie_sha256\":\"" + movieHash + "\","
            + "\"rom_sha1\":\"CFBF98C36C776677290A872547AC47C53D2761D6\",\"service_model\":\"s3k-kos-v1\","
            + "\"epoch\":0,\"raw_frame\":1,\"sequence_in_frame\":0,\"boundary\":\"pre_main_loop\","
            + "\"kind\":\"KOS_DECOMPRESSION_QUEUE\",\"ordinal\":0,\"fingerprint\":\"" + FP + "\","
            + "\"parent_fingerprint\":null,\"observation_precision\":\"" + precision + "\",\"submission_pc\":" + pc + ","
            + "\"classified\":" + classified + ",\"service_opportunities\":" + cost + ","
            + "\"source\":1,\"destination\":2,\"compressed_length\":8,\"decompressed_length\":3,"
            + "\"literal_commands\":3,\"short_copy_commands\":0,\"long_copy_commands\":0,\"copied_output_length\":0}\n";
    }
}
