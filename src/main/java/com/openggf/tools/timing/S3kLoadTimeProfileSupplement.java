package com.openggf.tools.timing;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds selected exact native FIFO measurements without retraining an existing profile.
 * Inputs are an existing manifest, executed BK2, v2 measurement stream, and a source/fingerprint
 * inventory derived from production ROM submissions. Origin: S3K ending reunion, 2026-10-15.
 * Legacy, censored, unclassified and unrelated-source rows cannot become profile entries.
 */
public final class S3kLoadTimeProfileSupplement {
    private static final ObjectMapper JSON = new ObjectMapper();
    /**
     * The checked-in FAST manifest's own layout (two-space indent, {@code ": "}, expanded
     * arrays), so a supplement is a pure append and never rewrites existing bytes.
     */
    private static final DefaultPrettyPrinter MANIFEST_LAYOUT;
    static {
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        MANIFEST_LAYOUT = new DefaultPrettyPrinter(Separators.createDefaultInstance()
                .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                .withObjectEmptySeparator("")
                .withArrayEmptySeparator(""));
        MANIFEST_LAYOUT.indentObjectsWith(indenter);
        MANIFEST_LAYOUT.indentArraysWith(indenter);
    }

    private S3kLoadTimeProfileSupplement() {
    }

    public static String supplement(Path manifest, Path movie, Path measurements,
                                    Map<String, Long> selectedSources) throws Exception {
        String movieHash = sha256(movie);
        for (String line : Files.readAllLines(measurements, StandardCharsets.UTF_8)) {
            JsonNode row = JSON.readTree(line);
            if (row.path("measurement_schema").asInt() != 2
                    || !"load-time-measurement-v2".equals(row.path("recorder_version").asText())) {
                throw new IllegalArgumentException("supplements require exact FIFO v2 measurement provenance");
            }
            if (!movieHash.equals(row.path("movie_sha256").asText())
                    || !movie.getFileName().toString().equals(row.path("fixture").asText())) {
                throw new IllegalArgumentException("measurement does not name the executed movie");
            }
        }
        var result = S3kLoadTimeProfileGenerator.generate(List.of(measurements));
        ObjectNode original = (ObjectNode) JSON.readTree(Files.readString(manifest));
        if (original.path("formatVersion").asInt() != 1
                || !"s3k-kos-v1".equals(original.path("serviceModel").asText())
                || !original.path("entries").isArray() || !original.path("fixtures").isArray()) {
            throw new IllegalArgumentException("unsupported base profile");
        }
        ObjectNode output = original.deepCopy();
        ArrayNode entries = (ArrayNode) output.get("entries");
        ArrayNode fixtures = (ArrayNode) output.get("fixtures");
        for (var selected : selectedSources.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var observations = result.eligible().get(selected.getKey());
            if (observations == null || observations.isEmpty()) {
                throw new IllegalArgumentException("no exact classified observation for " + selected.getKey());
            }
            if (observations.stream().anyMatch(row -> row.source() != selected.getValue())) {
                throw new IllegalArgumentException("selected source disagrees with exact observation");
            }
            boolean known = false;
            for (JsonNode entry : entries) {
                if (selected.getKey().equals(entry.path("submissionFingerprint").asText())) {
                    known = true;
                    break;
                }
            }
            if (known) continue; // Preserve all existing costs, statistics and provenance.
            var costs = observations.stream().map(S3kLoadTimeProfileGenerator.Observation::serviceOpportunities)
                    .sorted().toList();
            ObjectNode entry = JSON.createObjectNode();
            entry.put("kind", "kos_decompression_queue");
            entry.put("submissionFingerprint", selected.getKey());
            entry.put("serviceFrames", costs.get((costs.size() - 1) / 2));
            entry.putArray("eligibleBoundaries").add("pre_main_loop");
            entry.put("sampleCount", costs.size());
            entry.put("minFrames", costs.getFirst());
            entry.put("maxFrames", costs.getLast());
            ArrayNode indexes = entry.putArray("fixtureIndexes");
            for (String fixture : observations.stream().map(S3kLoadTimeProfileGenerator.Observation::fixture).distinct().sorted().toList()) {
                int index = -1;
                for (int i = 0; i < fixtures.size(); i++) if (fixtures.get(i).asText().equals(fixture)) index = i;
                if (index < 0) { index = fixtures.size(); fixtures.add(fixture); }
                indexes.add(index);
            }
            entries.add(entry);
        }
        return manifestJson(output);
    }

    static String manifestJson(JsonNode manifest) throws Exception {
        return JSON.writer(MANIFEST_LAYOUT).writeValueAsString(manifest) + "\n";
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 9) {
            throw new IllegalArgumentException("usage: <base-manifest> <executed.bk2> <v2-measurement.jsonl> <source-fingerprint.tsv> <frozen-setup.json> <expected-setup-sha256> <native-recorder-40hex-commit> <output-manifest> <publication.tsv>");
        }
        Path base = Path.of(args[0]), movie = Path.of(args[1]), measurements = Path.of(args[2]), inventory = Path.of(args[3]);
        Path setup = Path.of(args[4]);
        String setupHash = args[5], recorderCommit = args[6];
        if (!setupHash.matches("[0-9a-fA-F]{64}") || !recorderCommit.matches("[0-9a-fA-F]{40}")) {
            throw new IllegalArgumentException("publication requires exact setup SHA256 and native recorder commit");
        }
        if (!Files.isRegularFile(setup) || !setupHash.equalsIgnoreCase(sha256(setup))) {
            throw new IllegalArgumentException("frozen setup bytes disagree with the declared SHA256");
        }
        setupHash = setupHash.toLowerCase(java.util.Locale.ROOT);
        recorderCommit = recorderCommit.toLowerCase(java.util.Locale.ROOT);
        Map<String, Long> sources = new LinkedHashMap<>();
        for (String line : Files.readAllLines(inventory)) {
            String[] cells = line.split("\t", -1);
            if (cells.length != 2 || !cells[1].matches("sha256:[0-9a-f]{64}")
                    || sources.put(cells[1], Long.parseUnsignedLong(cells[0], 16)) != null) {
                throw new IllegalArgumentException("invalid or duplicate source/fingerprint inventory");
            }
        }
        if (sources.isEmpty()) throw new IllegalArgumentException("empty source inventory");
        String output = supplement(base, movie, measurements, sources);
        Path target = Path.of(args[7]), audit = Path.of(args[8]);
        if (Files.exists(target) || Files.exists(audit)) throw new IllegalArgumentException("refuse existing publication output");
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.createDirectories(audit.toAbsolutePath().getParent());
        StringBuilder provenance = new StringBuilder("# exact FIFO supplemental publication; canonical v5 is unchanged\n")
                .append("# measurement_schema\t2\n")
                .append("# recorder_version\tload-time-measurement-v2\n")
                .append("# recorder_commit\t").append(recorderCommit).append('\n')
                .append("# frozen_setup_sha256\t").append(setupHash).append('\n');
        for (Path input : List.of(base, movie, measurements, inventory)) {
            provenance.append("input\t").append(input.getFileName()).append("\tsha256\t").append(sha256(input)).append('\n');
        }
        provenance.append("input\t").append(setup.getFileName()).append("\tsha256\t")
                .append(setupHash).append('\n'); // Retain the verified frozen bytes' hash.
        provenance.append("output\t").append(target.getFileName()).append("\tsha256\t")
                .append(digest(output.getBytes(StandardCharsets.UTF_8))).append('\n');
        Files.writeString(target, output);
        Files.writeString(audit, provenance);
    }

    private static String sha256(Path path) throws Exception {
        return digest(Files.readAllBytes(path));
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
