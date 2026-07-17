package com.openggf.tools.fbzvisual;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reviewed contract separating ROM initialization from accepted visible-frame state. */
final class FbzVisualEvidenceAmendment {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() { };
    private static final String APPROVED = "approved";

    private final Path source;
    private final String sha256;
    private final List<Integer> initialAnimCounters;
    private final List<Integer> firstTickAnimCounters;
    private final String acceptedStatus;
    private final Map<String, Object> acceptedExactState;
    private final Map<String, CadenceReview> cadenceReviews;

    private FbzVisualEvidenceAmendment(Path source, String sha256,
                                       List<Integer> initialAnimCounters,
                                       List<Integer> firstTickAnimCounters,
                                       String acceptedStatus,
                                       Map<String, Object> acceptedExactState,
                                       Map<String, CadenceReview> cadenceReviews) {
        this.source = source;
        this.sha256 = sha256;
        this.initialAnimCounters = List.copyOf(initialAnimCounters);
        this.firstTickAnimCounters = List.copyOf(firstTickAnimCounters);
        this.acceptedStatus = acceptedStatus;
        this.acceptedExactState = Collections.unmodifiableMap(new LinkedHashMap<>(acceptedExactState));
        this.cadenceReviews = Map.copyOf(cadenceReviews);
    }

    static FbzVisualEvidenceAmendment load(Path path, String expectedSha256) throws IOException {
        Objects.requireNonNull(path, "path");
        byte[] bytes = Files.readAllBytes(path.toAbsolutePath().normalize());
        String actualHash = FbzVisualPrebootVerifier.sha256(bytes);
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            throw new IllegalArgumentException("FBZ evidence amendment SHA-256 is required");
        }
        if (!actualHash.equalsIgnoreCase(expectedSha256)) {
            throw new IllegalStateException("FBZ evidence amendment hash mismatch: expected "
                    + expectedSha256 + ", got " + actualHash);
        }
        JsonNode root = MAPPER.readTree(bytes);
        JsonNode invariant = root.path("rom_initialization_invariant");
        List<Integer> counters = parseCounterBytes(invariant.path("anim_counters"), "initialization");
        List<Integer> firstTickCounters = parseCounterBytes(
                invariant.path("first_animation_tick").path("anim_counters"), "first animation tick");
        JsonNode accepted = root.path("accepted_first_visible_frame");
        String status = accepted.path("status").asText("");
        JsonNode exactStateNode = accepted.get("exact_state");
        Map<String, Object> exactState = exactStateNode == null || exactStateNode.isNull()
                ? Map.of() : MAPPER.convertValue(exactStateNode, OBJECT_MAP);
        if (APPROVED.equals(status) && exactState.isEmpty()) {
            throw new IllegalStateException("Approved FBZ visible-frame amendment lacks exact_state");
        }
        Map<String, CadenceReview> cadenceStatuses = new LinkedHashMap<>();
        JsonNode cadenceSeries = root.path("proposed_aniplc_replacements").path("series");
        if (cadenceSeries.isArray()) {
            for (JsonNode series : cadenceSeries) {
                String checkpoint = series.path("checkpoint").asText("");
                if (!checkpoint.isBlank()) {
                    cadenceStatuses.put(checkpoint, new CadenceReview(
                            series.path("review_status").asText(""),
                            series.path("series").asText(""),
                            parseVisibleRegion(series.get("reviewed_visible_region"))));
                }
            }
        }
        return new FbzVisualEvidenceAmendment(path.toAbsolutePath().normalize(), actualHash,
                counters, firstTickCounters, status, exactState, cadenceStatuses);
    }

    private static VisibleRegion parseVisibleRegion(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (!node.isObject()) {
            throw new IllegalStateException("FBZ reviewed_visible_region must be an object");
        }
        return new VisibleRegion(requiredPositiveOrZero(node, "x", true),
                requiredPositiveOrZero(node, "y", true),
                requiredPositiveOrZero(node, "width", false),
                requiredPositiveOrZero(node, "height", false));
    }

    private static int requiredPositiveOrZero(JsonNode node, String key, boolean zeroAllowed) {
        JsonNode value = node.get(key);
        if (value == null || !value.canConvertToInt()) {
            throw new IllegalStateException("FBZ reviewed_visible_region lacks integer " + key);
        }
        int integer = value.intValue();
        if (integer < (zeroAllowed ? 0 : 1)) {
            throw new IllegalStateException("FBZ reviewed_visible_region has invalid " + key);
        }
        return integer;
    }

    private static List<Integer> parseCounterBytes(JsonNode countersNode, String phase) {
        if (!countersNode.isArray() || countersNode.size() != 16) {
            throw new IllegalStateException("FBZ amendment must define all 16 " + phase
                    + " Anim_Counters bytes");
        }
        List<Integer> counters = new ArrayList<>(16);
        for (JsonNode counter : countersNode) {
            if (!counter.canConvertToInt()) {
                throw new IllegalStateException("FBZ " + phase + " Anim_Counters must be integers");
            }
            int value = counter.intValue();
            if (value < 0 || value > 0xFF) {
                throw new IllegalStateException("FBZ " + phase
                        + " Anim_Counters byte out of range: " + value);
            }
            counters.add(value);
        }
        return List.copyOf(counters);
    }

    void verifyInitialization(Map<String, Object> state) {
        List<Integer> actual = rawAnimCounters(state);
        if (!initialAnimCounters.equals(actual)) {
            throw new IllegalStateException("FBZ ROM initialization Anim_Counters mismatch: expected "
                    + initialAnimCounters + ", got " + actual);
        }
    }

    void verifyFirstAnimationTick(Map<String, Object> state) {
        List<Integer> actual = rawAnimCounters(state);
        if (!firstTickAnimCounters.equals(actual)) {
            throw new IllegalStateException("FBZ first animation tick Anim_Counters mismatch: expected "
                    + firstTickAnimCounters + ", got " + actual);
        }
    }

    int acceptedLevelFrameCounter() {
        requireApproved();
        Object frame = acceptedExactState.get("level_frame_counter");
        if (!(frame instanceof Number number) || number.intValue() < 1) {
            throw new IllegalStateException("Approved FBZ visible-frame amendment lacks a positive "
                    + "level_frame_counter");
        }
        return number.intValue();
    }

    VisibleRegion requireApprovedCadenceSeries(String checkpoint) {
        CadenceReview review = cadenceReviews.get(checkpoint);
        String status = review == null ? "missing" : review.status();
        if (!APPROVED.equals(status) || review.region() == null || review.series().isBlank()) {
            throw new IllegalStateException("FBZ cadence visible region is not independently approved for "
                    + checkpoint + ": " + status);
        }
        return review.region();
    }

    String cadenceSeriesName(String checkpoint) {
        requireApprovedCadenceSeries(checkpoint);
        return cadenceReviews.get(checkpoint).series();
    }

    void verifyAcceptedVisibleFrame(Map<String, Object> state) {
        requireApproved();
        for (Map.Entry<String, Object> expected : acceptedExactState.entrySet()) {
            Object actual = state.get(expected.getKey());
            if (!equivalent(expected.getValue(), actual)) {
                throw new IllegalStateException("FBZ accepted visible-frame " + expected.getKey()
                        + " mismatch: expected " + expected.getValue() + ", got " + actual);
            }
        }
    }

    private void requireApproved() {
        if (!APPROVED.equals(acceptedStatus)) {
            throw new IllegalStateException("FBZ first gameplay-visible frame is not independently approved: "
                    + acceptedStatus);
        }
    }

    Map<String, Object> provenance() {
        return Map.of(
                "evidence_amendment", source.toString(),
                "evidence_amendment_sha256", sha256,
                "accepted_first_visible_frame_status", acceptedStatus);
    }

    private static List<Integer> rawAnimCounters(Map<String, Object> state) {
        Object raw = state.get("raw_anim_counters");
        if (!(raw instanceof List<?> list) || list.size() != 16) {
            throw new IllegalStateException("FBZ state must capture all 16 raw_anim_counters bytes");
        }
        List<Integer> values = new ArrayList<>(16);
        for (Object value : list) {
            if (!(value instanceof Number number)) {
                throw new IllegalStateException("FBZ raw_anim_counters contains a non-numeric byte");
            }
            values.add(number.intValue());
        }
        return values;
    }

    private static boolean equivalent(Object expected, Object actual) {
        if (expected instanceof Number left && actual instanceof Number right) {
            return Double.compare(left.doubleValue(), right.doubleValue()) == 0;
        }
        return Objects.equals(expected, actual);
    }

    record VisibleRegion(int x, int y, int width, int height) {
        void requireInside(int imageWidth, int imageHeight) {
            if (x < 0 || y < 0 || width <= 0 || height <= 0
                    || x + width > imageWidth || y + height > imageHeight) {
                throw new IllegalStateException("FBZ reviewed visible region lies outside "
                        + imageWidth + "x" + imageHeight);
            }
        }
    }

    private record CadenceReview(String status, String series, VisibleRegion region) {
    }
}
