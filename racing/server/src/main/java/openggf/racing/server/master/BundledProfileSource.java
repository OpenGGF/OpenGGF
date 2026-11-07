package openggf.racing.server.master;

import com.openggf.net.hub.TrackValidationProfile;
import com.openggf.net.hub.TrackValidationProfileSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Checked-in numeric track bounds consumed by the ROM-free master. */
public final class BundledProfileSource implements com.openggf.net.hub.KeyedTrackValidationProfileSource {
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
                String key = com.openggf.net.protocol.TrackKey.parse(node.path("key").asText()).format();
                TrackValidationProfile profile = new TrackValidationProfile(
                        node.path("levelWidthPx").asInt(),
                        node.path("levelHeightPx").asInt(),
                        node.path("maxSpeedPxPerFrame").asInt(),
                        node.path("maxFramesPerSecond").asInt(), node.path("wrapWidthPx").asInt(),
                        node.path("wrapHeightPx").asInt(), node.path("lapCount").asInt(), node.path("maxSwapSources").asInt());
                if (key.isBlank() || loaded.putIfAbsent(key, profile) != null) {
                    throw new IllegalStateException("invalid or duplicate track key: " + key);
                }
            }
            // Conservative placeholders; A3 replaces these with ROM exports before certification.
            for (int arena = 0; arena < 5; arena++) loaded.putIfAbsent("s3k:arena:" + arena,
                    new TrackValidationProfile(65535, 65535, 32, 60, 0, 0, 5, 12));
            profiles = Map.copyOf(loaded);
        } catch (Exception e) {
            throw new IllegalStateException("bundled track profile table unreadable", e);
        }
    }

    @Override
    public Optional<TrackValidationProfile> profileFor(com.openggf.net.protocol.TrackKey key) {
        return Optional.ofNullable(profiles.get(key.format()));
    }
}
