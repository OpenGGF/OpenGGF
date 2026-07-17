package com.openggf.tools.fbzvisual;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Fail-closed provenance receipt for one FBZ checkpoint/mode capture. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FbzVisualCaptureReceipt(
        @JsonProperty("schema_version") int schemaVersion,
        String status,
        String checkpoint,
        @JsonProperty("mode_key") String modeKey,
        @JsonProperty("rejection_reason") String rejectionReason,
        Map<String, Object> provenance,
        @JsonProperty("pre_state") Map<String, Object> preState,
        @JsonProperty("post_state") Map<String, Object> postState,
        @JsonProperty("full_png_sha256") String fullPngSha256,
        @JsonProperty("native_crop_png_sha256") String nativeCropPngSha256) {

    private static final Set<String> REQUIRED_PROVENANCE = Set.of(
            "commit",
            "dirty_worktree_sha256",
            "built_artifact_sha256",
            "effective_config_sha256",
            "rom_sha1",
            "manifest_sha256",
            "input_schedule_sha256",
            "input_schedule_source",
            "savestate_sha256",
            "savestate_source",
            "rng_seed",
            "rng_state",
            "preboot_verified");

    public FbzVisualCaptureReceipt {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Unsupported FBZ receipt schema: " + schemaVersion);
        }
        status = requireText(status, "status");
        checkpoint = requireText(checkpoint, "checkpoint");
        modeKey = requireText(modeKey, "modeKey");
        provenance = immutableMap(provenance, "provenance");
        validateProvenance(provenance, status);
        preState = immutableMap(preState, "preState");
        postState = immutableMap(postState, "postState");
        if ("accepted".equals(status)) {
            fullPngSha256 = requireText(fullPngSha256, "fullPngSha256");
            nativeCropPngSha256 = requireText(nativeCropPngSha256, "nativeCropPngSha256");
            if (rejectionReason != null) {
                throw new IllegalArgumentException("Accepted FBZ receipt has a rejection reason");
            }
        } else if ("rejected".equals(status)) {
            rejectionReason = requireText(rejectionReason, "rejectionReason");
            fullPngSha256 = null;
            nativeCropPngSha256 = null;
        } else {
            throw new IllegalArgumentException("Unknown FBZ receipt status: " + status);
        }
    }

    public static FbzVisualCaptureReceipt accepted(
            String checkpoint,
            String modeKey,
            Map<String, Object> provenance,
            Map<String, Object> preState,
            Map<String, Object> postState,
            String fullPngSha256,
            String nativeCropPngSha256) {
        return new FbzVisualCaptureReceipt(1, "accepted", checkpoint, modeKey, null,
                provenance, preState, postState, fullPngSha256, nativeCropPngSha256);
    }

    public static FbzVisualCaptureReceipt rejected(
            String checkpoint,
            String modeKey,
            String reason,
            Map<String, Object> provenance) {
        return new FbzVisualCaptureReceipt(1, "rejected", checkpoint, modeKey, reason,
                provenance, Map.of(), Map.of(), null, null);
    }

    public FbzVisualCaptureReceipt rejectedAfterPublicationFailure(String reason) {
        return rejected(checkpoint, modeKey, reason, provenance);
    }

    private static void validateProvenance(Map<String, Object> provenance, String status) {
        for (String key : REQUIRED_PROVENANCE) {
            Object value = provenance.get(key);
            if (value == null || value instanceof String text && text.isBlank()) {
                throw new IllegalArgumentException("Missing required FBZ provenance: " + key);
            }
        }
        if ("accepted".equals(status) && !Boolean.TRUE.equals(provenance.get("preboot_verified"))) {
            throw new IllegalArgumentException("FBZ provenance preboot verification did not pass");
        }
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source, String name) {
        Objects.requireNonNull(source, name);
        return Map.copyOf(new LinkedHashMap<>(source));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing FBZ receipt " + name);
        }
        return value;
    }
}
