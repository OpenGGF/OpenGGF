package com.openggf.net.hub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Checked-in numeric track bounds consumed by the ROM-free master. */
public final class BundledProfileSource implements TrackValidationProfileSource {
    private static final String RESOURCE = "/net/track-validation-profiles.json";
    private final Map<String, TrackValidationProfile> profiles;

    public BundledProfileSource() {
        Map<String, TrackValidationProfile> loaded = new HashMap<>();
        try (InputStream input = BundledProfileSource.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing resource " + RESOURCE);
            }
            JsonNode root = new ObjectMapper().readTree(input);
            JsonNode entries = root.get("profiles");
            if (entries == null || !entries.isArray()) {
                throw new IllegalStateException("profile table has no profiles array");
            }
            for (JsonNode node : entries) {
                String key = node.path("key").asText();
                TrackValidationProfile profile = new TrackValidationProfile(
                        node.path("levelWidthPx").asInt(),
                        node.path("levelHeightPx").asInt(),
                        node.path("maxSpeedPxPerFrame").asInt(),
                        node.path("maxFramesPerSecond").asInt());
                if (key.isBlank() || loaded.putIfAbsent(key, profile) != null) {
                    throw new IllegalStateException("invalid or duplicate track key: " + key);
                }
            }
            profiles = Map.copyOf(loaded);
        } catch (Exception e) {
            throw new IllegalStateException("bundled track profile table unreadable", e);
        }
    }

    @Override
    public Optional<TrackValidationProfile> profileFor(String gameId, int zone, int act) {
        return Optional.ofNullable(profiles.get(gameId + ":" + zone + ":" + act));
    }
}
