package com.openggf.mods;

import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.io.PackedModAssetRoot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * First-party mods shipped with this engine build, enabled and trusted by default.
 *
 * <p>The trust anchor is the {@link BundledModManifest} on the engine's own classpath, written
 * by the build. Jar bytes come from resources embedded in the universal JVM jar, extracted to a
 * bounded cache under the save root and re-verified on every boot, or else from the
 * {@code bundled/} directory of the application install directory. A jar is offered to the
 * catalog only when its immutable snapshot matches the manifest's SHA-256, size, id and version;
 * it is then validated exactly like any other mod. Anything else becomes an error entry and is
 * never trusted. Manifest ids are reserved: a user-installed jar with the same id is reported
 * and ignored.
 *
 * <p>All work happens in {@link Locator#open}, which {@code ModSubsystem.normalBootLoader} only
 * calls on normal boots, so deterministic and certifying boots neither scan nor extract.
 * The source owns the retained snapshots for the process lifetime; closing is idempotent, and
 * the compiled-mod runtime (closed first at shutdown) may close the ones it loaded earlier.
 */
public final class BundledModSource implements AutoCloseable {
    /** Optional absolute override for the application install directory. */
    public static final String INSTALL_DIR_PROPERTY = "openggf.installDir";
    /** Directory beside the engine jar that carries bundled jars in JVM distributions. */
    public static final String BUNDLED_DIRECTORY = "bundled";
    /** Cache directory, under the save root, for jars extracted from the universal jar. */
    public static final String CACHE_DIRECTORY = "bundled-mod-cache";

    public static final BundledModSource EMPTY = new BundledModSource(
            List.of(), Map.of(), Set.of(), Set.of(), List.of());

    private static final Logger LOG = Logger.getLogger(BundledModSource.class.getName());
    private static final Pattern CACHE_JAR = Pattern.compile("[0-9a-f]{64}\\.jar");
    private static final String STAGING_PREFIX = ".extract-";
    private static final int MAX_CACHE_LISTING = 256;

    private final List<ModCatalogEntry> entries;
    private final Map<String, String> trustedSha256ById;
    private final Set<String> reservedIds;
    private final Set<Path> sourcePaths;
    private final List<PackedModAssetRoot> snapshots;

    private BundledModSource(List<ModCatalogEntry> entries, Map<String, String> trustedSha256ById,
                             Set<String> reservedIds, Set<Path> sourcePaths,
                             List<PackedModAssetRoot> snapshots) {
        this.entries = List.copyOf(entries);
        this.trustedSha256ById = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(trustedSha256ById));
        this.reservedIds = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(reservedIds));
        this.sourcePaths = Set.copyOf(sourcePaths);
        this.snapshots = List.copyOf(snapshots);
    }

    /** Opens the bundled source; implementations perform all discovery and extraction. */
    @FunctionalInterface
    public interface Locator {
        Locator NONE = limits -> EMPTY;

        BundledModSource open(ModInputLimits limits);

        /** The engine's own classpath manifest, embedded jars, install directory and save-root cache. */
        static Locator production() {
            return limits -> BundledModSource.open(BundledModSource.class.getClassLoader(),
                    installDirectory(), com.openggf.game.save.SavePaths.root()
                            .resolve(CACHE_DIRECTORY).toAbsolutePath().normalize(), limits);
        }
    }

    /**
     * The application install directory: {@value #INSTALL_DIR_PROPERTY} when set, otherwise the
     * directory holding the engine jar (or, for an exploded class directory such as
     * {@code target/classes}, that directory's parent). Never the working directory.
     */
    public static Optional<Path> installDirectory() {
        String configured = System.getProperty(INSTALL_DIR_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return Optional.of(Path.of(configured).toAbsolutePath().normalize());
        }
        try {
            var codeSource = BundledModSource.class.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) return Optional.empty();
            Path location = Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
            return Optional.ofNullable(location.getParent());
        } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException
                 | SecurityException unavailable) {
            return Optional.empty();
        }
    }

    /**
     * Resolves every manifest entry. {@code resources} supplies the manifest and embedded jars;
     * {@code installDirectory} supplies {@code bundled/} jars when no embedded copy exists.
     */
    public static BundledModSource open(ClassLoader resources, Optional<Path> installDirectory,
                                        Path cacheDirectory, ModInputLimits limits) {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(installDirectory, "installDirectory");
        Objects.requireNonNull(cacheDirectory, "cacheDirectory");
        Objects.requireNonNull(limits, "limits");
        Path cache = cacheDirectory.toAbsolutePath().normalize();
        BundledModManifest manifest;
        try (InputStream input = resources.getResourceAsStream(BundledModManifest.RESOURCE)) {
            if (input == null) return EMPTY;
            manifest = BundledModManifest.parse(input.readNBytes(BundledModManifest.MAX_MANIFEST_BYTES + 1));
        } catch (IOException | IllegalArgumentException failure) {
            LOG.log(Level.WARNING, "Bundled mod manifest is unreadable; no bundled mods load", failure);
            return new BundledModSource(List.of(new RepositoryScanFailure(Path.of(BundledModManifest.RESOURCE),
                    List.of(error("BUNDLED_MANIFEST_INVALID",
                            "Bundled mod manifest is invalid: " + safeMessage(failure), null)))),
                    Map.of(), Set.of(), Set.of(), List.of());
        }
        List<ModCatalogEntry> entries = new ArrayList<>();
        Map<String, String> trusted = new LinkedHashMap<>();
        Set<String> reserved = new LinkedHashSet<>();
        Set<Path> paths = new LinkedHashSet<>();
        List<PackedModAssetRoot> snapshots = new ArrayList<>();
        boolean anyEmbedded = false;
        try {
            for (BundledModManifest.Entry entry : manifest.mods()) {
                reserved.add(entry.id());
                URL embedded = resources.getResource(BundledModManifest.EMBEDDED_PREFIX + entry.file());
                anyEmbedded |= embedded != null;
                Path expected = embedded != null ? cache.resolve(entry.sha256() + ".jar")
                        : installDirectory.map(dir -> dir.resolve(BUNDLED_DIRECTORY).resolve(entry.file()))
                        .orElse(cache.resolve(entry.file()));
                paths.add(expected);
                if (embedded == null && installDirectory.isEmpty()) {
                    entries.add(invalid(expected, "BUNDLED_MOD_MISSING",
                            "Bundled mod " + entry.id() + " is not embedded and no install directory is known"));
                    continue;
                }
                Resolved resolved = resolve(entry, embedded, expected, cache, limits);
                entries.add(resolved.entry());
                if (resolved.snapshot() != null) {
                    snapshots.add(resolved.snapshot());
                    trusted.put(entry.id(), entry.sha256());
                }
            }
            if (anyEmbedded || Files.isDirectory(cache, LinkOption.NOFOLLOW_LINKS)) {
                pruneCache(cache, manifest);
            }
        } catch (RuntimeException failure) {
            snapshots.forEach(BundledModSource::closeQuietly);
            throw failure;
        }
        return new BundledModSource(entries, trusted, reserved, paths, snapshots);
    }

    private record Resolved(ModCatalogEntry entry, PackedModAssetRoot snapshot) { }

    private static Resolved resolve(BundledModManifest.Entry entry, URL embedded, Path expected,
                                    Path cache, ModInputLimits limits) {
        try {
            if (embedded != null) {
                extract(embedded, entry, cache, expected);
            } else if (!Files.exists(expected, LinkOption.NOFOLLOW_LINKS)) {
                return new Resolved(invalid(expected, "BUNDLED_MOD_MISSING",
                        "Bundled mod " + entry.id() + " is missing: " + expected.getFileName()), null);
            }
            if (Files.isSymbolicLink(expected) || !Files.isRegularFile(expected, LinkOption.NOFOLLOW_LINKS)) {
                return new Resolved(invalid(expected, "BUNDLED_MOD_INVALID",
                        "Bundled mod " + entry.id() + " is not a regular file"), null);
            }
            // Classify tampering before the jar is parsed, so a damaged archive reads as a
            // hash mismatch rather than as an ordinary invalid jar.
            if (Files.size(expected) != entry.size() || !sha256(expected).equals(entry.sha256())) {
                return new Resolved(invalid(expected, "BUNDLED_MOD_HASH_MISMATCH",
                        "Bundled mod " + entry.id() + " does not match the build manifest hash; it was not loaded"),
                        null);
            }
        } catch (IntegrityFailure failure) {
            return new Resolved(invalid(expected, "BUNDLED_MOD_HASH_MISMATCH", failure.getMessage()), null);
        } catch (IOException | SecurityException failure) {
            return new Resolved(invalid(expected, "BUNDLED_MOD_INVALID",
                    "Bundled mod " + entry.id() + " could not be read: " + safeMessage(failure)), null);
        }
        PackedModAssetRoot snapshot;
        try {
            snapshot = ModAssetRoot.jar(expected.getParent(), expected, limits, entry.size());
        } catch (IOException | SecurityException failure) {
            return new Resolved(invalid(expected, "BUNDLED_MOD_INVALID",
                    "Bundled mod " + entry.id() + " is not a valid mod jar: " + safeMessage(failure)), null);
        }
        boolean retained = false;
        try {
            // The snapshot's bytes are the ones validated and class-loaded, so they are the ones checked.
            if (!snapshot.immutableSha256().equals(entry.sha256())) {
                return new Resolved(invalid(expected, "BUNDLED_MOD_HASH_MISMATCH",
                        "Bundled mod " + entry.id() + " does not match the build manifest hash; it was not loaded"), null);
            }
            List<String> names = snapshot.validatedEntryNames();
            if (!names.contains(DefaultModRepositoryScanner.MANIFEST_PATH)) {
                return new Resolved(invalid(expected, "MANIFEST_MISSING", "Required manifest is missing"), null);
            }
            ModManifest manifest = new ModManifestParser(limits).parse(snapshot.readBounded(
                    DefaultModRepositoryScanner.MANIFEST_PATH, limits.maxMetadataBytes()));
            if (!manifest.id().equals(entry.id()) || !manifest.version().equals(entry.version())) {
                return new Resolved(invalid(expected, "BUNDLED_MOD_IDENTITY_MISMATCH",
                        "Bundled jar declares " + manifest.id() + " " + manifest.version()
                                + " but the build manifest names " + entry.id() + " " + entry.version()), null);
            }
            boolean containsCode = names.stream().anyMatch(name -> name.endsWith(".class"));
            ModDescriptor descriptor = new ModDescriptor(expected, manifest, entry.sha256(),
                    containsCode, List.of(), snapshot);
            retained = true;
            return new Resolved(descriptor, snapshot);
        } catch (ModManifestException failure) {
            return new Resolved(invalid(expected, "MANIFEST_INVALID", safeMessage(failure)), null);
        } catch (IOException | SecurityException failure) {
            return new Resolved(invalid(expected, "BUNDLED_MOD_INVALID",
                    "Bundled mod " + entry.id() + " could not be read: " + safeMessage(failure)), null);
        } finally {
            if (!retained) closeQuietly(snapshot);
        }
    }

    /** Ensures {@code target} holds the embedded jar's exact bytes, re-extracting when it does not. */
    private static void extract(URL resource, BundledModManifest.Entry entry, Path cache, Path target)
            throws IOException {
        if (Files.isSymbolicLink(cache)) {
            throw new IOException("Bundled mod cache must not be a symbolic link: " + cache);
        }
        Files.createDirectories(cache);
        if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(target)
                && Files.size(target) == entry.size() && sha256(target).equals(entry.sha256())) {
            return;
        }
        Path staging = Files.createTempFile(cache, STAGING_PREFIX, ".tmp");
        try {
            MessageDigest digest = digest();
            long written = 0;
            try (InputStream input = resource.openStream(); OutputStream output = Files.newOutputStream(staging)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    written += read;
                    if (written > entry.size()) {
                        throw new IntegrityFailure("Embedded bundled mod " + entry.id()
                                + " is larger than the build manifest records; it was not loaded");
                    }
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }
            if (written != entry.size() || !HexFormat.of().formatHex(digest.digest()).equals(entry.sha256())) {
                throw new IntegrityFailure("Embedded bundled mod " + entry.id()
                        + " does not match the build manifest hash; it was not loaded");
            }
            try {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    /** Deletes engine-named cache files the current manifest no longer references. */
    private static void pruneCache(Path cache, BundledModManifest manifest) {
        if (Files.isSymbolicLink(cache) || !Files.isDirectory(cache, LinkOption.NOFOLLOW_LINKS)) return;
        Set<String> keep = new java.util.HashSet<>();
        manifest.mods().forEach(entry -> keep.add(entry.sha256() + ".jar"));
        try (var children = Files.list(cache)) {
            children.limit(MAX_CACHE_LISTING).forEach(child -> {
                String name = child.getFileName().toString();
                boolean engineOwned = CACHE_JAR.matcher(name).matches()
                        || (name.startsWith(STAGING_PREFIX) && name.endsWith(".tmp"));
                if (!engineOwned || keep.contains(name) || Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) return;
                try {
                    Files.deleteIfExists(child);
                } catch (IOException | SecurityException failure) {
                    LOG.log(Level.FINE, "Could not prune bundled mod cache entry " + child, failure);
                }
            });
        } catch (IOException | SecurityException failure) {
            LOG.log(Level.FINE, "Could not list bundled mod cache " + cache, failure);
        }
    }

    /**
     * Places bundled entries before user entries. A user-installed jar whose manifest id is
     * reserved by the bundled manifest becomes an error entry, so the bundled copy wins and the
     * duplicate is reported instead of blocking both.
     */
    public List<ModCatalogEntry> withUserEntries(List<? extends ModCatalogEntry> userEntries) {
        Objects.requireNonNull(userEntries, "userEntries");
        List<ModCatalogEntry> merged = new ArrayList<>(entries.size() + userEntries.size());
        merged.addAll(entries);
        for (ModCatalogEntry entry : userEntries) {
            if (entry instanceof ModDescriptor descriptor && reservedIds.contains(descriptor.manifest().id())) {
                List<ModFinding> findings = new ArrayList<>(descriptor.findings());
                findings.add(error("BUNDLED_MOD_ID_RESERVED", "Mod id " + descriptor.manifest().id()
                        + " belongs to a mod bundled with OpenGGF; this installed copy is ignored. "
                        + "Remove it from mods/", DefaultModRepositoryScanner.MANIFEST_PATH));
                merged.add(new InvalidModEntry(descriptor.jarPath(), findings));
            } else {
                merged.add(entry);
            }
        }
        return List.copyOf(merged);
    }

    /**
     * Applies bundled defaults to persisted startup state: a verified bundled mod without a
     * persisted entry is enabled and placed before user mods, so user overrides still win; an
     * existing entry keeps the player's enabled choice and order. Trust for the verified hash is
     * derived here on every boot and never persisted (see {@link PendingModStateEditor}).
     */
    public ModState applyStartupDefaults(ModState persisted, List<? extends ModCatalogEntry> validated) {
        Objects.requireNonNull(persisted, "persisted");
        Objects.requireNonNull(validated, "validated");
        Map<String, String> present = new LinkedHashMap<>();
        for (ModCatalogEntry entry : validated) {
            if (entry instanceof ModDescriptor descriptor
                    && descriptor.sha256().equals(trustedSha256ById.get(descriptor.manifest().id()))) {
                present.put(descriptor.manifest().id(), descriptor.sha256());
            }
        }
        if (present.isEmpty()) return persisted;
        Set<String> persistedIds = new java.util.HashSet<>();
        persisted.entries().forEach(entry -> persistedIds.add(entry.id()));
        List<String> added = present.keySet().stream().filter(id -> !persistedIds.contains(id)).toList();
        List<ModState.Entry> result = new ArrayList<>(persisted.entries().size() + added.size());
        for (int index = 0; index < added.size(); index++) {
            String id = added.get(index);
            result.add(new ModState.Entry(id, true, index, true, present.get(id)));
        }
        for (ModState.Entry entry : persisted.entries()) {
            String sha = present.get(entry.id());
            result.add(new ModState.Entry(entry.id(), entry.enabled(), entry.order() + added.size(),
                    sha != null || entry.trusted(), sha != null ? sha : entry.trustedJarSha256()));
        }
        return new ModState(ModState.CURRENT_FORMAT_VERSION, result);
    }

    /** Discovered bundled entries: verified descriptors, error entries, or a manifest failure. */
    public List<ModCatalogEntry> entries() { return entries; }

    /** Verified bundled ids and the exact hashes the build manifest trusts for them. */
    public Map<String, String> trustedSha256ById() { return trustedSha256ById; }

    /** Every id the build manifest lists, whether or not its jar verified. */
    public Set<String> reservedIds() { return reservedIds; }

    /** Catalog source paths of bundled entries, for presentation. */
    public Set<Path> sourcePaths() { return sourcePaths; }

    @Override public void close() {
        snapshots.forEach(BundledModSource::closeQuietly);
    }

    private static void closeQuietly(PackedModAssetRoot snapshot) {
        try {
            snapshot.close();
        } catch (IOException ignored) {
            // The snapshot's temp tree is engine-owned; a later boot does not reuse it.
        }
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static InvalidModEntry invalid(Path path, String code, String message) {
        return new InvalidModEntry(path, List.of(error(code, message, null)));
    }

    private static ModFinding error(String code, String message, String assetPath) {
        return new ModFinding(ModFindingSeverity.ERROR, code, message, assetPath);
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static final class IntegrityFailure extends IOException {
        IntegrityFailure(String message) { super(message); }
    }
}
