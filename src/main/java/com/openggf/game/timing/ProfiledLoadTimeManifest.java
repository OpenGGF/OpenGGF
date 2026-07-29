package com.openggf.game.timing;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Session-owned exact profiled-load lookup with warn-once immediate fallback. */
public final class ProfiledLoadTimeManifest implements LoadTimeProfile {
    private static final ObjectMapper MAPPER = new ObjectMapper(
            new JsonFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));

    private final String serviceModel;
    private final Map<Key, LoadTimeDecision> decisions;
    private final Consumer<String> warningSink;
    private final Set<Key> warnedMissing = new HashSet<>();

    private ProfiledLoadTimeManifest(
            String serviceModel,
            Map<Key, LoadTimeDecision> decisions,
            Consumer<String> warningSink) {
        this.serviceModel = serviceModel;
        this.decisions = Map.copyOf(decisions);
        this.warningSink = warningSink;
    }

    public static ProfiledLoadTimeManifest load(
            InputStream input, Consumer<String> warningSink) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(warningSink, "warningSink");
        JsonNode root;
        try (JsonParser parser = MAPPER.getFactory().createParser(input)) {
            root = MAPPER.readTree(parser);
        }
        if (requiredInt(root, "formatVersion") != 1) {
            throw new IllegalArgumentException("unsupported load-time manifest version");
        }
        requiredText(root, "profile");
        String serviceModel = requiredText(root, "serviceModel");
        JsonNode fixtures = requiredArray(root, "fixtures");
        for (JsonNode fixture : fixtures) {
            if (!fixture.isTextual()) {
                throw new IllegalArgumentException("fixture must be a string");
            }
        }

        Map<Key, LoadTimeDecision> decisions = new HashMap<>();
        for (JsonNode entry : requiredArray(root, "entries")) {
            HardwareWorkKind kind = HardwareWorkKind.fromWireName(
                    requiredText(entry, "kind"));
            String fingerprint = requiredText(entry, "submissionFingerprint");
            int frames = requiredInt(entry, "serviceFrames");
            int sampleCount = requiredInt(entry, "sampleCount");
            int minFrames = requiredInt(entry, "minFrames");
            int maxFrames = requiredInt(entry, "maxFrames");
            if (sampleCount <= 0 || minFrames < 0
                    || maxFrames < minFrames
                    || frames < minFrames || frames > maxFrames) {
                throw new IllegalArgumentException("invalid load-time entry statistics");
            }
            EnumSet<HardwareServiceBoundary> boundaries =
                    EnumSet.noneOf(HardwareServiceBoundary.class);
            for (JsonNode boundary : requiredArray(entry, "eligibleBoundaries")) {
                boundaries.add(HardwareServiceBoundary.fromWireName(boundary.asText()));
            }
            for (JsonNode fixtureIndex : requiredArray(entry, "fixtureIndexes")) {
                if (!fixtureIndex.canConvertToInt()
                        || fixtureIndex.intValue() < 0
                        || fixtureIndex.intValue() >= fixtures.size()) {
                    throw new IllegalArgumentException("invalid fixture index");
                }
            }
            Key key = new Key(kind, fingerprint);
            LoadTimeDecision previous = decisions.put(
                    key,
                    new LoadTimeDecision(
                            frames,
                            boundaries,
                            LoadTimeDecisionSource.MEASURED,
                            serviceModel));
            if (previous != null) {
                throw new IllegalArgumentException(
                        "duplicate load-time manifest key: " + key);
            }
        }
        return new ProfiledLoadTimeManifest(serviceModel, decisions, warningSink);
    }

    @Override
    public LoadTimeDecision assign(
            HardwareWorkSubmission submission, HardwareWorkHandle handle) {
        Objects.requireNonNull(handle, "handle");
        Key key = new Key(handle.kind(), handle.submissionFingerprint());
        LoadTimeDecision decision = decisions.get(key);
        if (decision != null) {
            return decision;
        }
        if (warnedMissing.add(key)) {
            warningSink.accept(
                    "No " + serviceModel + " load-time profile for "
                            + handle.kind() + " " + handle.submissionFingerprint()
                            + "; using immediate readiness");
        }
        return LoadTimeProfile.IMMEDIATE.assign(submission, handle);
    }

    private static String requiredText(JsonNode parent, String name) {
        JsonNode node = parent.get(name);
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalArgumentException(name + " must be a nonblank string");
        }
        return node.textValue();
    }

    private static int requiredInt(JsonNode parent, String name) {
        JsonNode node = parent.get(name);
        if (node == null || !node.canConvertToInt()) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        return node.intValue();
    }

    private static JsonNode requiredArray(JsonNode parent, String name) {
        JsonNode node = parent.get(name);
        if (node == null || !node.isArray()) {
            throw new IllegalArgumentException(name + " must be an array");
        }
        return node;
    }

    private record Key(HardwareWorkKind kind, String fingerprint) {
        private Key {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(fingerprint, "fingerprint");
        }
    }
}
