package com.openggf.tests;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.tests.SessionInvocationExtension.SessionInvocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Resolves test-owned diagnostic output below the current worktree's target tree. */
public final class TestSessionOutputPaths {

    private static final String TRACE_REPORTS_PROPERTY = "openggf.trace.reports";
    private static final String DIAGNOSTICS_PROPERTY = "openggf.test.diagnostics";
    private static final String ARTIFACT_ROOT_PROPERTY = "openggf.artifact.root";
    private static final Path LEGACY_TRACE_REPORTS = Path.of("target", "trace-reports");
    private static final Path LEGACY_DIAGNOSTICS = Path.of("target", "diagnostics");
    private static final Path LEGACY_ARTIFACT_ROOT = Path.of("target");

    private TestSessionOutputPaths() {
    }

    public static Path traceReports() {
        return configuredPath(TRACE_REPORTS_PROPERTY, LEGACY_TRACE_REPORTS);
    }

    /** Resolves the configured reports root and requires it to stay below the supplied build directory. */
    public static Path traceReports(Path buildDirectory) {
        return configuredPathWithin(buildDirectory, TRACE_REPORTS_PROPERTY, "trace-reports");
    }

    public static Path diagnostics(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        return configuredPath(DIAGNOSTICS_PROPERTY, LEGACY_DIAGNOSTICS)
                .resolve(safeComponent(namespace));
    }

    /** Resolves diagnostics below a supplied build directory, rejecting escaping configured roots. */
    public static Path diagnostics(Path buildDirectory, String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        return configuredPathWithin(buildDirectory, DIAGNOSTICS_PROPERTY, "diagnostics")
                .resolve(safeComponent(namespace));
    }

    public static Path artifactRoot() {
        return configuredPath(ARTIFACT_ROOT_PROPERTY, LEGACY_ARTIFACT_ROOT);
    }

    /** Resolves the configured artifact root and requires it to stay below the supplied build directory. */
    public static Path artifactRoot(Path buildDirectory) {
        return configuredPathWithin(buildDirectory, ARTIFACT_ROOT_PROPERTY, "");
    }

    /** Resolves Maven's compiled production classes below this worktree's target. */
    public static Path compiledClasses() {
        return Path.of("target", "classes");
    }

    /** Resolves Maven's compiled test classes below this worktree's target. */
    public static Path compiledTestClasses() {
        return Path.of("target", "test-classes");
    }

    /**
     * Reserves a logical report owner. Repeating the same invocation resolves
     * the same path so a later raw-Maven run replaces stale evidence.
     */
    public static ReportAllocation allocateReport(
            String profile,
            String className,
            String methodName,
            int parameterIndex,
            String invocationId,
            String laneId,
            String logicalKey,
            String suffix) throws IOException {
        return allocateReport(traceReports(), profile, className, methodName,
                parameterIndex, invocationId, laneId, logicalKey, suffix,
                true);
    }

    /**
     * Allocates a report below an explicitly selected directory. This is used
     * by diagnostic fixtures that override their report directory (often a
     * per-test temporary directory) and therefore must retain that override.
     * The legacy report basename is retained for those callers.
     */
    public static ReportAllocation allocateReport(
            Path outputDirectory,
            String profile,
            String className,
            String methodName,
            int parameterIndex,
            String invocationId,
            String laneId,
            String logicalKey,
            String suffix) throws IOException {
        return allocateReport(outputDirectory, profile, className, methodName,
                parameterIndex, invocationId, laneId, logicalKey, suffix,
                false);
    }

    private static ReportAllocation allocateReport(
            Path outputDirectory,
            String profile,
            String className,
            String methodName,
            int parameterIndex,
            String invocationId,
            String laneId,
            String logicalKey,
            String suffix,
            boolean includeProfileDirectory) throws IOException {
        ReportAllocation allocation = reportAllocation(outputDirectory, profile, className, methodName,
                parameterIndex, invocationId, laneId, logicalKey, suffix, includeProfileDirectory);
        Files.createDirectories(reportDirectory(outputDirectory, profile, includeProfileDirectory));
        return allocation;
    }

    private static ReportAllocation reportAllocation(
            Path outputDirectory, String profile, String className, String methodName,
            int parameterIndex, String invocationId, String laneId, String logicalKey,
            String suffix, boolean includeProfileDirectory) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(invocationId, "invocationId");
        Objects.requireNonNull(laneId, "laneId");
        Objects.requireNonNull(logicalKey, "logicalKey");
        Objects.requireNonNull(suffix, "suffix");
        if (suffix.isBlank() || !suffix.startsWith(".")) {
            throw new IllegalArgumentException("report suffix must start with '.'");
        }

        String ownerKey = digest(String.join("\n", profile, className, methodName,
                Integer.toString(parameterIndex), invocationId, laneId, logicalKey));
        String filename = includeProfileDirectory
                ? safeComponent(logicalKey) + "-" + safeComponent(laneId)
                    + "-" + ownerKey.substring(0, 16) + suffix
                : safeComponent(logicalKey) + "_report" + suffix;
        Path directory = reportDirectory(outputDirectory, profile, includeProfileDirectory);
        Path physicalPath = directory.resolve(filename);
        Path metadataPath = physicalPath.resolveSibling(filename + ".owner.json");
        return new ReportAllocation(logicalKey, ownerKey, physicalPath, metadataPath);
    }

    private static Path reportDirectory(Path outputDirectory, String profile, boolean includeProfileDirectory) {
        return includeProfileDirectory ? outputDirectory.resolve(safeComponent(profile)) : outputDirectory;
    }

    /** Finds JSON reports whose complete sidecar owner matches this invocation and lane. */
    public static List<ReportAllocation> findOwnedReports(
            String profile, SessionInvocation invocation, String laneId) throws IOException {
        return findOwnedReports(traceReports(), profile, invocation, laneId, true);
    }

    /** Explicit output directories retain the allocator's legacy basename convention. */
    public static List<ReportAllocation> findOwnedReports(
            Path outputDirectory, String profile, SessionInvocation invocation, String laneId)
            throws IOException {
        return findOwnedReports(outputDirectory, profile, invocation, laneId, false);
    }

    private static List<ReportAllocation> findOwnedReports(
            Path outputDirectory, String profile, SessionInvocation invocation, String laneId,
            boolean includeProfileDirectory) throws IOException {
        Path root = outputDirectory.toAbsolutePath().normalize();
        Path directory = reportDirectory(root, profile, includeProfileDirectory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || hasSymbolicAncestor(directory)) {
            return List.of();
        }
        ObjectMapper mapper = new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        List<ReportAllocation> owned = new ArrayList<>();
        try (var sidecars = Files.newDirectoryStream(directory, "*.json.owner.json")) {
            for (Path sidecar : sidecars) {
                ReportAllocation allocation = verifiedAllocation(sidecar, root, profile, invocation,
                        laneId, includeProfileDirectory, mapper);
                if (allocation != null) owned.add(allocation);
            }
        }
        return List.copyOf(owned);
    }

    private static ReportAllocation verifiedAllocation(
            Path sidecar, Path root, String profile, SessionInvocation invocation, String laneId,
            boolean includeProfileDirectory, ObjectMapper mapper) {
        if (!Files.isRegularFile(sidecar, LinkOption.NOFOLLOW_LINKS)) return null;
        try {
            JsonNode owner = mapper.readTree(Files.readString(sidecar, StandardCharsets.UTF_8));
            if (owner == null || !owner.isObject() || owner.size() != 3
                    || !owner.path("logical_key").isTextual() || owner.path("logical_key").textValue().isBlank()
                    || !owner.path("owner_key").isTextual() || !owner.path("physical_path").isTextual()) {
                return null;
            }
            ReportAllocation expected = reportAllocation(root, profile, invocation.className(),
                    invocation.methodName(), invocation.parameterIndex(), invocation.invocationId(),
                    laneId, owner.path("logical_key").textValue(), ".json", includeProfileDirectory);
            // Only canonical allocator paths are returned. The sidecar's path
            // participates in verification and is never used as a deletion target.
            if (!sidecar.equals(expected.metadataPath())
                    || !owner.path("owner_key").textValue().equals(expected.ownerKey())
                    || !Path.of(owner.path("physical_path").textValue()).toAbsolutePath().normalize()
                            .equals(expected.physicalPath())
                    || !Files.isRegularFile(expected.physicalPath(), LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            return expected;
        } catch (IOException | IllegalArgumentException untrustedOwner) {
            // Malformed, unreadable and invalid-path sidecars do not establish ownership.
            return null;
        }
    }

    private static boolean hasSymbolicAncestor(Path path) {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) return true;
        }
        return false;
    }

    /** Publishes the owner sidecar after the report itself has been published. */
    public static void publishOwnerMetadata(ReportAllocation allocation) throws IOException {
        Objects.requireNonNull(allocation, "allocation");
        String metadata = "{\n"
                + "  \"logical_key\": \"" + json(allocation.logicalKey()) + "\",\n"
                + "  \"owner_key\": \"" + allocation.ownerKey() + "\",\n"
                + "  \"physical_path\": \"" + json(allocation.physicalPath().toString()) + "\"\n"
                + "}\n";
        publishAtomically(allocation.metadataPath(), metadata);
    }

    /**
     * Atomically publishes one report or context file, replacing stale output
     * from a previous raw-Maven invocation of the same test.
     */
    public static void publish(Path destination, String content) throws IOException {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(content, "content");
        publishAtomically(destination, content);
    }

    public record ReportAllocation(
            String logicalKey,
            String ownerKey,
            Path physicalPath,
            Path metadataPath) {
    }

    private static Path configuredPath(String property, Path legacyDefault) {
        String configured = System.getProperty(property);
        return configured == null || configured.isBlank()
                ? legacyDefault
                : Path.of(configured);
    }

    private static Path configuredPathWithin(Path buildDirectory, String property,
                                             String defaultRelativePath) {
        Objects.requireNonNull(buildDirectory, "buildDirectory");
        Path normalizedBuildDirectory = buildDirectory.toAbsolutePath().normalize();
        String configured = System.getProperty(property);
        Path candidate = configured == null || configured.isBlank()
                ? normalizedBuildDirectory.resolve(defaultRelativePath)
                : Path.of(configured).isAbsolute()
                        ? Path.of(configured)
                        : normalizedBuildDirectory.resolve(configured);
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedBuildDirectory)) {
            throw new IllegalArgumentException(property + " escapes build directory "
                    + normalizedBuildDirectory + ": " + configured);
        }
        return normalizedCandidate;
    }

    private static String safeComponent(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank()) {
            return "unnamed";
        }
        return safe.equals(".") || safe.equals("..") ? "_" + safe : safe;
    }

    private static void publishAtomically(Path destination, String content) throws IOException {
        Path parent = destination.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = destination.resolveSibling(
                destination.getFileName() + ".tmp-" + Long.toUnsignedString(System.nanoTime()));
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (UnsupportedOperationException e) {
                throw new IOException(
                        "atomic report publication is unsupported for " + destination,
                        e);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("JDK must provide SHA-256", e);
        }
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
