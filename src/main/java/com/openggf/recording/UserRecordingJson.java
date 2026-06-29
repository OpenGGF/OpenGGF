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
import java.time.format.DateTimeParseException;

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
            Integer schemaVersion,
            String movieName,
            BuildIdentity engineIdentity,
            RecordingLaunchContext launchContext,
            UserRecordingSidecarMetadata sidecar,
            RecordingDeterminismMetadata determinism,
            String jumpActionButton,
            Integer frameCount,
            String stopReason,
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
                    manifest.stopReason() == null ? null : manifest.stopReason().name(),
                    manifest.createdAt() == null ? null : manifest.createdAt().toString());
        }

        UserRecordingManifest toManifest() throws IOException {
            validate();
            return new UserRecordingManifest(
                    schemaVersion,
                    movieName,
                    engineIdentity,
                    launchContext,
                    sidecar,
                    determinism,
                    jumpActionButton,
                    frameCount,
                    parseStopReason(stopReason),
                    parseCreatedAt(createdAt));
        }

        private void validate() throws IOException {
            requirePresent(schemaVersion, "schemaVersion");
            if (schemaVersion != UserRecordingManifest.CURRENT_SCHEMA_VERSION) {
                throw new IOException("Unsupported manifest schema version: " + schemaVersion);
            }
            requirePresent(movieName, "movieName");
            requirePresent(engineIdentity, "engineIdentity");
            requirePresent(launchContext, "launchContext");
            requirePresent(sidecar, "sidecar");
            requirePresent(determinism, "determinism");
            requirePresent(jumpActionButton, "jumpActionButton");
            requirePresent(frameCount, "frameCount");
            requirePresent(stopReason, "stopReason");
            requirePresent(createdAt, "createdAt");
        }

        private static void requirePresent(Object value, String fieldName) throws IOException {
            if (value == null) {
                throw new IOException("Missing required manifest field: " + fieldName);
            }
        }

        private static UserRecordingStopReason parseStopReason(String value) {
            try {
                return UserRecordingStopReason.valueOf(value);
            } catch (IllegalArgumentException ex) {
                return UserRecordingStopReason.UNKNOWN;
            }
        }

        private static Instant parseCreatedAt(String value) throws IOException {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ex) {
                throw new IOException("Invalid manifest createdAt timestamp: " + value, ex);
            }
        }
    }
}
