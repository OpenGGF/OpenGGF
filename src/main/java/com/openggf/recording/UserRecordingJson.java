package com.openggf.recording;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.version.BuildIdentity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public final class UserRecordingJson {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private UserRecordingJson() {
    }

    public static String writeManifest(UserRecordingManifest manifest) throws JsonProcessingException {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ManifestDto.from(manifest));
    }

    public static void writeManifest(Path path, UserRecordingManifest manifest) throws IOException {
        Files.writeString(path, writeManifest(manifest), StandardCharsets.UTF_8);
    }

    public static UserRecordingManifest readManifest(String json) throws IOException {
        return MAPPER.readValue(json, ManifestDto.class).toManifest();
    }

    public static UserRecordingManifest readManifest(Path path) throws IOException {
        return readManifest(Files.readString(path, StandardCharsets.UTF_8));
    }

    private record ManifestDto(
            int schemaVersion,
            String movieName,
            BuildIdentity engineIdentity,
            RecordingLaunchContext launchContext,
            UserRecordingSidecarMetadata sidecar,
            RecordingDeterminismMetadata determinism,
            String jumpActionButton,
            int frameCount,
            UserRecordingStopReason stopReason,
            String createdAt
    ) {
        static ManifestDto from(UserRecordingManifest manifest) {
            return new ManifestDto(
                    manifest.schemaVersion(),
                    manifest.movieName(),
                    manifest.engineIdentity(),
                    manifest.launchContext(),
                    manifest.sidecar(),
                    manifest.determinism(),
                    manifest.jumpActionButton(),
                    manifest.frameCount(),
                    manifest.stopReason(),
                    manifest.createdAt() == null ? null : manifest.createdAt().toString());
        }

        UserRecordingManifest toManifest() {
            return new UserRecordingManifest(
                    schemaVersion,
                    movieName,
                    engineIdentity,
                    launchContext,
                    sidecar,
                    determinism,
                    jumpActionButton,
                    frameCount,
                    stopReason,
                    createdAt == null ? null : Instant.parse(createdAt));
        }
    }
}
