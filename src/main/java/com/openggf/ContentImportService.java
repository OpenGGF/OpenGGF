package com.openggf;

import com.openggf.StockMusicDomains;
import com.openggf.editor.project.EditorProjectDocument;
import com.openggf.editor.project.EditorProjectStore;
import com.openggf.game.GameId;
import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.io.PackedModAssetRoot;
import com.openggf.mods.DefaultModRepositoryScanner;
import com.openggf.mods.ModCatalogValidator;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.ModFinding;
import com.openggf.mods.ModFindingSeverity;
import com.openggf.mods.ModManifestException;
import com.openggf.mods.ModManifestParser;
import com.openggf.mods.ModState;
import com.openggf.mods.ModStateStore;
import com.openggf.mods.RepositoryScanFailure;
import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.ModLevelDefinition;
import com.openggf.mods.code.ModLevelDefinitionParser;
import com.openggf.mods.validation.ModValidationFinding;
import com.openggf.mods.validation.ModValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Host composition between editor documents and bounded mod packages.
 * Inspection never defines creator classes or enables mods.
 */
public final class ContentImportService {
    public enum Kind { PROJECT, LEVEL, MOD }

    private final Path modRoot;
    private final Supplier<Set<String>> reservedModIds;
    private final ModInputLimits limits;

    public ContentImportService(Path modRoot) {
        this(modRoot, Set.of());
    }

    public ContentImportService(Path modRoot, Set<String> reservedModIds) {
        this(modRoot, () -> reservedModIds, ModInputLimits.production());
    }

    public ContentImportService(Path modRoot, Supplier<Set<String>> reservedModIds) {
        this(modRoot, reservedModIds, ModInputLimits.production());
    }

    public ContentImportService(Path modRoot, Set<String> reservedModIds, ModInputLimits limits) {
        this(modRoot, () -> reservedModIds, limits);
    }

    public ContentImportService(Path modRoot, Supplier<Set<String>> reservedModIds, ModInputLimits limits) {
        this.modRoot = Objects.requireNonNull(modRoot, "modRoot").toAbsolutePath().normalize();
        this.reservedModIds = Objects.requireNonNull(reservedModIds, "reservedModIds");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /** Prepares owned content; callers close it after confirmation, cancellation, or failure. */
    public PreparedImport inspect(Path supplied) throws IOException {
        Path source = Objects.requireNonNull(supplied, "supplied").toAbsolutePath().normalize();
        if (Files.isSymbolicLink(source)) throw new IOException("Drop a regular file or directory, not a symbolic link");
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            try (var assets = ModAssetRoot.directory(source.getParent(), source, limits, DirectoryAccess.DEVELOPMENT)) {
                return PreparedImport.level(source, ModLevelDefinitionParser.read(assets, new BakedLevelRef("level.json")));
            }
        }
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Import source is not a regular file or level directory");
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".ogfproject")) return PreparedImport.project(source, new EditorProjectStore().open(source));
        if (name.equals("level.json")) {
            Path directory = source.getParent();
            try (var assets = ModAssetRoot.directory(directory.getParent(), directory, limits, DirectoryAccess.DEVELOPMENT)) {
                return PreparedImport.level(source, ModLevelDefinitionParser.read(assets, new BakedLevelRef("level.json")));
            }
        }
        if (!name.endsWith(".jar") && !name.endsWith(".zip")) {
            throw new IOException("Choose an .ogfproject, level directory, level.json, level ZIP, or mod/mutator JAR");
        }
        PackedModAssetRoot assets = ModAssetRoot.jar(source.getParent(), source, limits);
        boolean retained = false;
        try {
            List<String> names = assets.validatedEntryNames();
            boolean manifest = names.contains(DefaultModRepositoryScanner.MANIFEST_PATH);
            // A root mod manifest owns its included level assets, just as it does in a JAR.
            if (name.endsWith(".jar") || manifest) {
                PreparedImport result = prepareMod(source, assets);
                retained = true;
                return result;
            }
            List<String> levels = names.stream().filter(ContentImportService::isLevelEntry).toList();
            List<String> jars = names.stream().filter(ContentImportService::isWrappedJarEntry).toList();
            int choices = levels.size() + jars.size();
            if (choices != 1) throw new IOException(choices == 0
                    ? "ZIP must contain one level.json at its root or in one folder, or one mod/mutator JAR"
                    : "ZIP contains several importable items; drop one level or mod package at a time");
            if (!levels.isEmpty()) return PreparedImport.level(source,
                    ModLevelDefinitionParser.read(assets, new BakedLevelRef(levels.getFirst())));
            // Only the selected bounded JAR is staged. Archive paths never become filesystem paths.
            Path temporary = Files.createTempFile("openggf-import-", ".jar");
            try {
                Files.write(temporary, assets.readBounded(jars.getFirst(), limits.maxAssetBytes()));
                PackedModAssetRoot inner = ModAssetRoot.jar(temporary.getParent(), temporary, limits);
                try {
                    PreparedImport result = prepareMod(source, inner);
                    result.enclosingAssets = assets;
                    retained = true;
                    return result;
                } catch (IOException | RuntimeException failure) {
                    try { inner.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
                    throw failure;
                }
            } finally { Files.deleteIfExists(temporary); }
        } finally {
            if (!retained) assets.close();
        }
    }

    private PreparedImport prepareMod(Path source, PackedModAssetRoot assets) throws IOException {
        List<String> names = assets.validatedEntryNames();
        if (!names.contains(DefaultModRepositoryScanner.MANIFEST_PATH)) throw new IOException("Mod JAR is missing its manifest META-INF/openggf-mod.yaml");
        final com.openggf.mods.ModManifest manifest;
        try { manifest = new ModManifestParser(limits).parse(assets.readBounded(
                DefaultModRepositoryScanner.MANIFEST_PATH, limits.maxMetadataBytes())); }
        catch (ModManifestException failure) { throw new IOException(failure.getMessage(), failure); }
        boolean code = names.stream().anyMatch(entry -> entry.endsWith(".class"));
        var descriptor = new ModDescriptor(source, manifest, assets.immutableSha256(), code, List.of());
        var validated = new ModCatalogValidator(source.getParent(), limits, StockMusicDomains::containsSupported)
                .validatePacked(descriptor, assets);
        descriptor = (ModDescriptor) validated.eligibility().entries().getFirst();
        List<ModFinding> findings = new ArrayList<>(descriptor.findings());
        if (code && manifest.entrypoint() == null) findings.add(new ModFinding(ModFindingSeverity.ERROR,
                "ENTRYPOINT_MISSING", "A code-bearing mod requires a manifest entrypoint", DefaultModRepositoryScanner.MANIFEST_PATH));
        if (manifest.entrypoint() != null) {
            var report = new ModValidator().validate(assets.immutableContentPath(), manifest.entrypoint());
            for (var finding : report.findings()) findings.add(new ModFinding(
                    finding.severity() == ModValidationFinding.Severity.ERROR ? ModFindingSeverity.ERROR : ModFindingSeverity.WARNING,
                    finding.code(), finding.message(), finding.className()));
        }
        List<String> errors = findings.stream().filter(f -> f.severity() == ModFindingSeverity.ERROR)
                .map(ModFinding::message).toList();
        if (!errors.isEmpty()) throw new IOException(errors.getFirst());
        descriptor = new ModDescriptor(source, manifest, descriptor.sha256(), code, findings);
        return PreparedImport.mod(source, descriptor, assets);
    }

    /** Installs the inspected immutable bytes under their manifest id. No enablement or trust is changed. */
    public synchronized Path installMod(PreparedImport prepared) throws IOException {
        Objects.requireNonNull(prepared, "prepared").requireKind(Kind.MOD);
        checkInterrupted();
        String id = prepared.modDescriptor().manifest().id();
        if (reservedModIds.get().contains(id)) throw new IOException("A mod with id " + id + " is already installed or bundled");
        requireNonSymlinkParents(modRoot);
        Files.createDirectories(modRoot);
        BasicFileAttributes rootIdentity = Files.readAttributes(modRoot, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!rootIdentity.isDirectory() || Files.isSymbolicLink(modRoot)) throw new IOException("Mod repository is not a regular directory");
        var catalog = new DefaultModRepositoryScanner(limits).scan(modRoot);
        for (var entry : catalog) {
            if (entry instanceof RepositoryScanFailure) throw new IOException(entry.findings().getFirst().message());
            if (entry instanceof ModDescriptor existing && existing.manifest().id().equals(id)) {
                throw new IOException("A mod with id " + id + " is already installed; existing files are preserved");
            }
        }
        if (catalog.size() >= limits.maxModJars()) throw new IOException("Mod repository has reached its JAR limit");
        long repositoryBytes = Files.size(prepared.assets.immutableContentPath());
        for (var entry : catalog) {
            BasicFileAttributes attributes = Files.readAttributes(entry.jarPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isRegularFile()) repositoryBytes += attributes.size();
            if (repositoryBytes > limits.maxRepositoryValidationBytes()) throw new IOException("Import would exceed the repository validation-byte limit");
        }
        if (repositoryBytes > limits.maxRepositoryValidationBytes()) throw new IOException("Import would exceed the repository validation-byte limit");
        ModState state = readPreferencesWithoutMutation();
        if (state.entries().stream().anyMatch(entry -> entry.id().equals(id))) {
            throw new IOException("Saved preferences already exist for " + id + "; importing it could restore an old enablement choice");
        }
        Path target = modRoot.resolve(id + ".jar");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Import destination already exists: " + target.getFileName());
        Path temporary = Files.createTempFile(modRoot, ".content-import-", ".tmp");
        boolean published = false;
        try {
            MessageDigest digest = sha256();
            long copied = 0;
            try (var input = Files.newInputStream(prepared.assets.immutableContentPath());
                 var output = Files.newOutputStream(temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    checkInterrupted();
                    copied += read;
                    if (copied > limits.maxJarBytes()) throw new IOException("Mod exceeds the packed-file limit");
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }
            if (!prepared.modDescriptor().sha256().equals(HexFormat.of().formatHex(digest.digest()))) {
                throw new IOException("Inspected mod snapshot changed; drop the package again");
            }
            requireNonSymlinkParents(modRoot);
            BasicFileAttributes current = Files.readAttributes(modRoot, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!current.isDirectory() || !Objects.equals(rootIdentity.fileKey(), current.fileKey())) {
                throw new IOException("Mod repository changed during import");
            }
            // No ATOMIC_MOVE: its existing-target behavior is provider-dependent. The default
            // no-replacement move contract preserves a target created after our precheck.
            checkInterrupted();
            Files.move(temporary, target);
            published = true;
            return target;
        } finally { if (!published) Files.deleteIfExists(temporary); }
    }

    private static boolean isLevelEntry(String entry) {
        return entry.equals("level.json") || entry.matches("[^/]+/level\\.json");
    }

    private ModState readPreferencesWithoutMutation() throws IOException {
        Path source = modRoot.resolve("modstate.json");
        if (Files.notExists(source, LinkOption.NOFOLLOW_LINKS)) return ModState.EMPTY;
        BasicFileAttributes before = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || Files.isSymbolicLink(source) || before.size() > limits.maxMetadataBytes()) {
            throw new IOException("Cannot verify mod preferences: expected a bounded regular non-symlink file");
        }
        byte[] bytes;
        try (var input = Files.newInputStream(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(Math.toIntExact(limits.maxMetadataBytes() + 1));
        }
        BasicFileAttributes after = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (bytes.length > limits.maxMetadataBytes() || !after.isRegularFile()
                || !Objects.equals(before.fileKey(), after.fileKey()) || before.size() != after.size()
                || bytes.length != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !before.creationTime().equals(after.creationTime())) {
            throw new IOException("Mod preferences changed during import; try again");
        }
        // The existing strict reader quarantines invalid files. Give it only an owned copy:
        // refusing an import must leave the live preferences and existing packages untouched.
        Path isolated = Files.createTempDirectory("openggf-import-state-");
        Path copy = isolated.resolve("modstate.json");
        Path quarantined = null;
        try {
            Files.write(copy, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            var loaded = new ModStateStore(isolated.toAbsolutePath().normalize(), limits).load();
            quarantined = loaded.quarantinedPath().orElse(null);
            if (loaded.message().isPresent()) throw new IOException("Cannot verify mod preferences: " + loaded.message().orElseThrow());
            return loaded.state();
        } finally {
            Files.deleteIfExists(copy);
            if (quarantined != null && quarantined.getParent().equals(isolated)) Files.deleteIfExists(quarantined);
            Files.deleteIfExists(isolated);
        }
    }

    private static boolean isWrappedJarEntry(String entry) {
        return entry.toLowerCase(Locale.ROOT).endsWith(".jar") && entry.split("/").length <= 2;
    }

    private static void requireNonSymlinkParents(Path path) throws IOException {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw new IOException("Mod repository path contains a symbolic link");
        }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void checkInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Content installation cancelled");
    }

    /** Validated payload and owned archive lease, kept alive until the user chooses an action. */
    public static final class PreparedImport implements AutoCloseable {
        private final Kind kind;
        private final Path source;
        private final String displayName;
        private final String detail;
        private final GameId suggestedGame;
        private final EditorProjectDocument project;
        private final ModLevelDefinition level;
        private final ModDescriptor descriptor;
        private final PackedModAssetRoot assets;
        private PackedModAssetRoot enclosingAssets;
        private boolean closed;

        private PreparedImport(Kind kind, Path source, String displayName, String detail, GameId game,
                               EditorProjectDocument project, ModLevelDefinition level, ModDescriptor descriptor,
                               PackedModAssetRoot assets) {
            this.kind = kind; this.source = source; this.displayName = displayName; this.detail = detail;
            this.suggestedGame = game; this.project = project; this.level = level; this.descriptor = descriptor; this.assets = assets;
        }

        static PreparedImport project(Path source, EditorProjectDocument project) {
            return new PreparedImport(Kind.PROJECT, source, project.metadata().name(), "Open this complete editable project",
                    project.metadata().gameId(), project, null, null, null);
        }

        static PreparedImport level(Path source, ModLevelDefinition level) {
            GameId game = level.formatVersion() == 2 ? GameId.S3K : level.blockGridSide() == 16 ? GameId.S1 : GameId.S2;
            return new PreparedImport(Kind.LEVEL, source, level.zoneName(),
                    level.width() + " x " + level.height() + " blocks; import as a new editable project",
                    game, null, level, null, null);
        }

        static PreparedImport mod(Path source, ModDescriptor descriptor, PackedModAssetRoot assets) {
            String detail = descriptor.manifest().id() + " " + descriptor.manifest().version()
                    + "; install disabled, then enable" + (descriptor.containsCode() ? " and trust its code" : "") + " in Mods";
            return new PreparedImport(Kind.MOD, source, descriptor.manifest().name(), detail, null, null, null, descriptor, assets);
        }

        public Kind kind() { return kind; }
        public Path source() { return source; }
        public String displayName() { return displayName; }
        public String detail() { return detail; }
        public Optional<GameId> suggestedGame() { return Optional.ofNullable(suggestedGame); }
        public EditorProjectDocument project() { requireKind(Kind.PROJECT); return project; }
        public ModLevelDefinition level() { requireKind(Kind.LEVEL); return level; }
        public ModDescriptor modDescriptor() { requireKind(Kind.MOD); return descriptor; }
        public List<ModFinding> findings() { return descriptor == null ? List.of() : descriptor.findings(); }

        private void requireKind(Kind expected) {
            if (closed) throw new IllegalStateException("Prepared import is closed");
            if (kind != expected) throw new IllegalStateException("Import is " + kind + ", expected " + expected);
        }

        @Override public void close() throws IOException {
            if (closed) return;
            closed = true;
            IOException failure = null;
            try { if (assets != null) assets.close(); }
            catch (IOException cleanup) { failure = cleanup; }
            try { if (enclosingAssets != null) enclosingAssets.close(); }
            catch (IOException cleanup) {
                if (failure == null) failure = cleanup;
                else failure.addSuppressed(cleanup);
            }
            if (failure != null) throw failure;
        }
    }
}
