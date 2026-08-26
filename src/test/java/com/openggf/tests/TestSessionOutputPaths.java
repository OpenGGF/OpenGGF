package com.openggf.tests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Resolves test-owned diagnostic output without changing no-session defaults. */
public final class TestSessionOutputPaths {

    private static final String TRACE_REPORTS_PROPERTY = "openggf.trace.reports";
    private static final String DIAGNOSTICS_PROPERTY = "openggf.test.diagnostics";
    private static final String ARTIFACT_ROOT_PROPERTY = "openggf.artifact.root";
    private static final String BUILD_DIRECTORY_PROPERTY = "openggf.build.directory";
    private static final Path LEGACY_TRACE_REPORTS = Path.of("target", "trace-reports");
    private static final Path LEGACY_DIAGNOSTICS = Path.of("target", "diagnostics");
    private static final Path LEGACY_ARTIFACT_ROOT = Path.of("target");
    private static final Path LEGACY_BUILD_DIRECTORY = Path.of("target");

    private TestSessionOutputPaths() {
    }

    public static Path traceReports() {
        return configuredPath(TRACE_REPORTS_PROPERTY, LEGACY_TRACE_REPORTS);
    }

    public static Path diagnostics(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        return configuredPath(DIAGNOSTICS_PROPERTY, LEGACY_DIAGNOSTICS)
                .resolve(safeComponent(namespace));
    }

    public static Path artifactRoot() {
        return configuredPath(ARTIFACT_ROOT_PROPERTY, LEGACY_ARTIFACT_ROOT);
    }

    /** Resolves Maven's compiled production classes for this test session. */
    public static Path compiledClasses() {
        return configuredPath(BUILD_DIRECTORY_PROPERTY, LEGACY_BUILD_DIRECTORY)
                .resolve("classes");
    }

    /** Resolves Maven's compiled test classes for this test session. */
    public static Path compiledTestClasses() {
        return configuredPath(BUILD_DIRECTORY_PROPERTY, LEGACY_BUILD_DIRECTORY)
                .resolve("test-classes");
    }

    /**
     * Reserves a logical report owner. The metadata sidecar is published with
     * CREATE_NEW semantics, so a repeated invocation cannot silently replace
     * an earlier report with the same owner key.
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
     * The legacy report basename is retained for those callers; the owner
     * sidecar still makes a duplicate publication fail closed.
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
        Path directory = includeProfileDirectory
                ? outputDirectory.resolve(safeComponent(profile))
                : outputDirectory;
        Files.createDirectories(directory);
        Path physicalPath = directory.resolve(filename);
        Path metadataPath = physicalPath.resolveSibling(filename + ".owner.json");
        return new ReportAllocation(logicalKey, ownerKey, physicalPath, metadataPath);
    }

    /** Publishes the owner sidecar after the report itself has been published. */
    public static void publishOwnerMetadata(ReportAllocation allocation) throws IOException {
        Objects.requireNonNull(allocation, "allocation");
        String metadata = "{\n"
                + "  \"logical_key\": \"" + json(allocation.logicalKey()) + "\",\n"
                + "  \"owner_key\": \"" + allocation.ownerKey() + "\",\n"
                + "  \"physical_path\": \"" + json(allocation.physicalPath().toString()) + "\"\n"
                + "}\n";
        publishCreateNew(allocation.metadataPath(), metadata);
    }

    /**
     * Publishes one report or context file without replacing an existing file.
     * A repeated publication of byte-identical content is idempotent; a
     * different publication remains a hard collision.
     */
    public static void publish(Path destination, String content) throws IOException {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(content, "content");
        publishCreateNew(destination, content);
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

    private static String safeComponent(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank()) {
            return "unnamed";
        }
        return safe.equals(".") || safe.equals("..") ? "_" + safe : safe;
    }

    private static void publishCreateNew(Path destination, String content) throws IOException {
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
                // A hard link publishes the complete temporary file atomically
                // and has CREATE_NEW destination semantics: unlike an atomic
                // move with ATOMIC_MOVE alone, it cannot replace a prior owner.
                Files.createLink(destination, temporary);
                Files.delete(temporary);
            } catch (FileAlreadyExistsException e) {
                // The replay finally block may publish the same logical report
                // after the normal divergence path already wrote it. Accept
                // that exact repeat, but never let a changed report replace
                // evidence from the first publication.
                if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)
                        || Files.mismatch(destination, temporary) != -1L) {
                    throw e;
                }
            } catch (UnsupportedOperationException e) {
                throw new IOException(
                        "exclusive atomic report publication is unsupported for " + destination,
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
