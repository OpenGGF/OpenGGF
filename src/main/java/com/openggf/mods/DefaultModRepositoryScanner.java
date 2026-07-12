package com.openggf.mods;

import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.io.PackedModAssetRoot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Filesystem scanner for production packed-mod repositories. */
public final class DefaultModRepositoryScanner implements ModRepositoryScanner {
    public static final String MANIFEST_PATH = "META-INF/openggf-mod.yaml";

    private final ModInputLimits limits;
    private final Runnable afterPreflight;

    public DefaultModRepositoryScanner() {
        this(ModInputLimits.production());
    }

    public DefaultModRepositoryScanner(ModInputLimits limits) {
        this(limits, () -> { });
    }

    DefaultModRepositoryScanner(ModInputLimits limits, Runnable afterPreflight) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.afterPreflight = Objects.requireNonNull(afterPreflight, "afterPreflight");
    }

    @Override
    public List<ModCatalogEntry> scan(Path normalizedModRoot) {
        Path root = requireNormalizedRoot(normalizedModRoot);
        if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            return repositoryFailure(root, "MOD_REPOSITORY_INVALID",
                    "Mod repository root is not a non-symlink directory");
        }

        final List<Path> jars;
        try (var children = Files.list(root)) {
            jars = children.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException | SecurityException e) {
            return repositoryFailure(root, "MOD_REPOSITORY_INVALID", safeMessage(e));
        }

        if (jars.size() > limits.maxModJars()) {
            return repositoryFailure(root, "REPOSITORY_JAR_LIMIT_EXCEEDED",
                    "Mod repository contains more than " + limits.maxModJars() + " jars");
        }
        Map<Path, ModFinding> preflightFailures = new HashMap<>();
        Map<Path, Long> reservedSizes = new HashMap<>();
        try {
            reserveRepositoryBytes(jars, preflightFailures, reservedSizes);
        } catch (IOException | SecurityException e) {
            return repositoryFailure(root, "REPOSITORY_VALIDATION_BYTES_EXCEEDED", safeMessage(e));
        }
        afterPreflight.run();

        List<ModCatalogEntry> entries = new ArrayList<>(jars.size());
        for (Path jar : jars) {
            ModFinding preflightFailure = preflightFailures.get(jar);
            entries.add(preflightFailure == null
                    ? scanJar(root, jar, reservedSizes.get(jar))
                    : new InvalidModEntry(jar, List.of(preflightFailure)));
        }
        markDuplicateIds(entries);
        return List.copyOf(entries);
    }

    private void reserveRepositoryBytes(List<Path> jars, Map<Path, ModFinding> perJarFailures,
                                        Map<Path, Long> reservedSizes)
            throws IOException {
        long total = 0;
        for (Path jar : jars) {
            if (Files.isSymbolicLink(jar)) {
                continue;
            }
            try {
                BasicFileAttributes attributes = Files.readAttributes(
                        jar, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isRegularFile()) {
                    perJarFailures.put(jar, error("MOD_JAR_INVALID",
                            "Mod jar is not a regular file", jar.getFileName().toString()));
                    continue;
                }
                total = Math.addExact(total, attributes.size());
                reservedSizes.put(jar, attributes.size());
            } catch (IOException | SecurityException e) {
                perJarFailures.put(jar, error("MOD_JAR_INVALID", safeMessage(e),
                        jar.getFileName().toString()));
                continue;
            } catch (ArithmeticException e) {
                throw new IOException("Mod repository byte count overflow", e);
            }
            if (total > limits.maxRepositoryValidationBytes()) {
                throw new IOException("Mod repository exceeds validation budget "
                        + limits.maxRepositoryValidationBytes());
            }
        }
    }

    private ModCatalogEntry scanJar(Path root, Path jar, Long expectedSourceBytes) {
        try (PackedModAssetRoot assets = expectedSourceBytes == null
                ? ModAssetRoot.jar(root, jar, limits)
                : ModAssetRoot.jar(root, jar, limits, expectedSourceBytes)) {
            List<String> names = assets.validatedEntryNames();
            if (!names.contains(MANIFEST_PATH)) {
                return invalid(jar, "MANIFEST_MISSING", "Required manifest is missing", MANIFEST_PATH);
            }
            byte[] manifestBytes;
            try {
                manifestBytes = assets.readBounded(MANIFEST_PATH, limits.maxMetadataBytes());
            } catch (IOException | SecurityException e) {
                return invalid(jar, "MANIFEST_INVALID", safeMessage(e), MANIFEST_PATH);
            }
            ModManifest manifest;
            try {
                manifest = new ModManifestParser(limits).parse(manifestBytes);
            } catch (ModManifestException | SecurityException e) {
                return invalid(jar, "MANIFEST_INVALID", safeMessage(e), MANIFEST_PATH);
            }
            boolean containsCode = names.stream().anyMatch(name -> name.endsWith(".class"));
            return new ModDescriptor(jar, manifest, assets.immutableSha256(), containsCode, List.of());
        } catch (IOException | SecurityException e) {
            return invalid(jar, "MOD_JAR_INVALID", safeMessage(e), jar.getFileName().toString());
        }
    }

    private static void markDuplicateIds(List<ModCatalogEntry> entries) {
        Map<String, List<Integer>> indexesById = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) instanceof ModDescriptor descriptor) {
                indexesById.computeIfAbsent(descriptor.manifest().id(), ignored -> new ArrayList<>()).add(i);
            }
        }
        for (Map.Entry<String, List<Integer>> group : indexesById.entrySet()) {
            if (group.getValue().size() < 2) {
                continue;
            }
            for (int index : group.getValue()) {
                ModDescriptor descriptor = (ModDescriptor) entries.get(index);
                List<ModFinding> findings = new ArrayList<>(descriptor.findings());
                findings.add(error("DUPLICATE_MOD_ID",
                        "Mod id is used by multiple jars: " + group.getKey(), MANIFEST_PATH));
                entries.set(index, new ModDescriptor(descriptor.jarPath(), descriptor.manifest(),
                        descriptor.sha256(), descriptor.containsCode(), findings, descriptor.retainedSource()));
            }
        }
    }

    private static InvalidModEntry invalid(Path jar, String code, String message, String assetPath) {
        return new InvalidModEntry(jar, List.of(error(code, message, assetPath)));
    }

    private static List<ModCatalogEntry> repositoryFailure(Path root, String code, String message) {
        return List.of(new RepositoryScanFailure(root, List.of(error(code, message, null))));
    }

    private static ModFinding error(String code, String message, String assetPath) {
        return new ModFinding(ModFindingSeverity.ERROR, code, message, assetPath);
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static Path requireNormalizedRoot(Path root) {
        Objects.requireNonNull(root, "normalizedModRoot");
        Path normalized = root.toAbsolutePath().normalize();
        if (!root.equals(normalized)) {
            throw new IllegalArgumentException("Mod repository root must be absolute and normalized: " + root);
        }
        return root;
    }
}
