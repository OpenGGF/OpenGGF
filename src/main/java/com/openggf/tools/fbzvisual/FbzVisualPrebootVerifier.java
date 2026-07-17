package com.openggf.tools.fbzvisual;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Performs the hash and workspace checks required before the engine boots. */
public final class FbzVisualPrebootVerifier {

    public static final String LOCKED_ON_S3K_SHA1 =
            "CFBF98C36C776677290A872547AC47C53D2761D6";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FbzVisualPrebootVerifier() {
    }

    public static Result verify(Inputs inputs) {
        Objects.requireNonNull(inputs, "inputs");
        Map<String, Object> provenance = baseProvenance(inputs);
        try {
            Path workspace = gitRoot(inputs.workspace());
            Path rom = regularFile(inputs.rom(), "ROM");
            Path artifact = regularFile(inputs.builtArtifact(), "built artifact");

            String romSha1 = digest(rom, "SHA-1");
            requireHash("ROM SHA-1", LOCKED_ON_S3K_SHA1, romSha1);
            String artifactSha256 = digest(artifact, "SHA-256");
            requireHash("built artifact SHA-256", inputs.expectedArtifactSha256(), artifactSha256);

            ResolvedSource schedule = resolveSource(inputs.inputScheduleSource(),
                    inputs.expectedInputScheduleSha256(), "input schedule", workspace);
            ResolvedSource savestate = resolveSource(inputs.savestateSource(),
                    inputs.expectedSavestateSha256(), "savestate", workspace);
            String effectiveConfigSha256 = sha256(MAPPER.writeValueAsBytes(
                    new TreeMap<>(HiddenGlCaptureSession.configurationContract(inputs.mode()))));

            provenance.put("commit", gitText(workspace, "rev-parse", "HEAD"));
            provenance.put("dirty_worktree_sha256", dirtyFingerprint(workspace));
            provenance.put("built_artifact", artifact.toString());
            provenance.put("built_artifact_sha256", artifactSha256);
            provenance.put("effective_config", HiddenGlCaptureSession.configurationContract(inputs.mode()));
            provenance.put("effective_config_sha256", effectiveConfigSha256);
            provenance.put("rom", rom.toString());
            provenance.put("rom_sha1", romSha1);
            provenance.put("manifest", inputs.manifest().source().toString());
            provenance.put("manifest_sha256", inputs.manifest().sha256());
            provenance.put("input_schedule_source", schedule.source());
            provenance.put("input_schedule_sha256", schedule.sha256());
            provenance.put("savestate_source", savestate.source());
            provenance.put("savestate_sha256", savestate.sha256());
            provenance.put("framebuffer_width", inputs.mode().framebufferWidth());
            provenance.put("framebuffer_height", inputs.mode().framebufferHeight());
            provenance.put("native_crop_x", inputs.mode().nativeCropX());
            provenance.put("native_crop_y", inputs.mode().nativeCropY());
            provenance.put("native_crop_width", inputs.mode().nativeCropWidth());
            provenance.put("native_crop_height", inputs.mode().nativeCropHeight());
            provenance.put("preboot_verified", true);
            return new Result(true, null, Map.copyOf(provenance));
        } catch (Exception failure) {
            provenance.put("preboot_verified", false);
            provenance.put("preboot_failure", failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage());
            return new Result(false, provenance.get("preboot_failure").toString(), Map.copyOf(provenance));
        }
    }

    private static Map<String, Object> baseProvenance(Inputs inputs) {
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("commit", "unavailable");
        base.put("dirty_worktree_sha256", "unavailable");
        base.put("built_artifact_sha256", "unavailable");
        base.put("effective_config_sha256", "unavailable");
        base.put("rom_sha1", "unavailable");
        base.put("manifest_sha256", inputs.manifest().sha256());
        base.put("input_schedule_sha256", "unavailable");
        base.put("input_schedule_source", inputs.inputScheduleSource());
        base.put("savestate_sha256", "unavailable");
        base.put("savestate_source", inputs.savestateSource());
        base.put("rng_seed", inputs.rngSeed());
        base.put("rng_state", "preboot:not-initialized");
        base.put("preboot_verified", false);
        return base;
    }

    private static ResolvedSource resolveSource(String source, String expectedSha256,
                                                 String label, Path workspace) throws IOException {
        Objects.requireNonNull(source, label + " source");
        String actual;
        String normalizedSource;
        if (source.startsWith("none:")) {
            normalizedSource = source;
            actual = sha256(source.getBytes(StandardCharsets.UTF_8));
        } else {
            Path path = Path.of(source);
            if (!path.isAbsolute()) {
                path = workspace.resolve(path);
            }
            path = regularFile(path, label);
            normalizedSource = path.toString();
            actual = digest(path, "SHA-256");
        }
        requireHash(label + " SHA-256", expectedSha256, actual);
        return new ResolvedSource(normalizedSource, actual);
    }

    private static Path regularFile(Path path, String label) throws IOException {
        Path normalized = Objects.requireNonNull(path, label).toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IOException("Missing " + label + ": " + normalized);
        }
        return normalized;
    }

    private static Path gitRoot(Path workspace) throws IOException, InterruptedException {
        Path candidate = Objects.requireNonNull(workspace, "workspace").toAbsolutePath().normalize();
        return Path.of(gitText(candidate, "rev-parse", "--show-toplevel"))
                .toAbsolutePath().normalize();
    }

    private static String dirtyFingerprint(Path workspace) throws IOException, InterruptedException {
        MessageDigest digest = messageDigest("SHA-256");
        update(digest, gitBytes(workspace, "status", "--porcelain=v1", "-z", "--untracked-files=all"));
        update(digest, gitBytes(workspace, "diff", "--binary", "HEAD", "--", "."));
        byte[] untracked = gitBytes(workspace, "ls-files", "--others", "--exclude-standard", "-z");
        update(digest, untracked);
        for (byte[] rawPath : splitNull(untracked)) {
            if (rawPath.length == 0) continue;
            Path file = workspace.resolve(new String(rawPath, StandardCharsets.UTF_8)).normalize();
            if (Files.isRegularFile(file)) {
                update(digest, Files.readAllBytes(file));
            }
        }
        return HexFormat.of().withUpperCase().formatHex(digest.digest());
    }

    private static byte[][] splitNull(byte[] source) {
        ByteArrayOutputStream current = new ByteArrayOutputStream();
        java.util.List<byte[]> parts = new java.util.ArrayList<>();
        for (byte value : source) {
            if (value == 0) {
                parts.add(current.toByteArray());
                current.reset();
            } else {
                current.write(value);
            }
        }
        if (current.size() != 0) parts.add(current.toByteArray());
        return parts.toArray(byte[][]::new);
    }

    private static String gitText(Path workspace, String... arguments)
            throws IOException, InterruptedException {
        return new String(gitBytes(workspace, arguments), StandardCharsets.UTF_8).trim();
    }

    private static byte[] gitBytes(Path workspace, String... arguments)
            throws IOException, InterruptedException {
        String[] command = new String[arguments.length + 1];
        command[0] = "git";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        Process process = new ProcessBuilder(command).directory(workspace.toFile())
                .redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        int status = process.waitFor();
        if (status != 0) {
            throw new IOException("git " + String.join(" ", arguments) + " failed (" + status
                    + "): " + new String(output, StandardCharsets.UTF_8).trim());
        }
        return output;
    }

    private static String digest(Path path, String algorithm) throws IOException {
        MessageDigest digest = messageDigest(algorithm);
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) >= 0;) {
                if (read != 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().withUpperCase().formatHex(digest.digest());
    }

    public static String sha256(byte[] bytes) {
        return HexFormat.of().withUpperCase().formatHex(
                messageDigest("SHA-256").digest(Objects.requireNonNull(bytes, "bytes")));
    }

    private static MessageDigest messageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(algorithm + " unavailable", impossible);
        }
    }

    private static void update(MessageDigest digest, byte[] bytes) {
        digest.update((byte) 0);
        digest.update(bytes);
    }

    private static void requireHash(String label, String expected, String actual) throws IOException {
        if (expected == null || expected.isBlank()) {
            throw new IOException("Missing expected " + label);
        }
        if (!actual.equalsIgnoreCase(expected)) {
            throw new IOException(label + " mismatch: expected " + expected + ", got " + actual);
        }
    }

    public record Inputs(
            Path workspace,
            Path rom,
            Path builtArtifact,
            String expectedArtifactSha256,
            FbzVisualManifest manifest,
            FbzVisualCaptureMode mode,
            String inputScheduleSource,
            String expectedInputScheduleSha256,
            String savestateSource,
            String expectedSavestateSha256,
            long rngSeed) {
        public Inputs {
            Objects.requireNonNull(workspace, "workspace");
            Objects.requireNonNull(rom, "rom");
            Objects.requireNonNull(builtArtifact, "builtArtifact");
            Objects.requireNonNull(manifest, "manifest");
            Objects.requireNonNull(mode, "mode");
        }
    }

    public record Result(boolean verified, String rejectionReason, Map<String, Object> provenance) {
    }

    private record ResolvedSource(String source, String sha256) {
    }
}
