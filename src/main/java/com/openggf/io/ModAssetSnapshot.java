package com.openggf.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@FunctionalInterface
interface SnapshotHook {
    SnapshotHook NONE = (source, snapshot) -> { };

    default void beforeCopy(Path source) throws IOException {
    }

    default void snapshotAllocated(Path source, Path snapshot) throws IOException {
    }

    default void afterFileCopied(Path sourceEntry, Path snapshotEntry, long totalSnapshotBytes)
            throws IOException {
    }

    void afterCopy(Path source, Path snapshot) throws IOException;
}

/** Engine-owned immutable temp-disk snapshot used by all file-backed mod roots. */
final class ModAssetSnapshot implements AutoCloseable {
    private static final String PREFIX = "openggf-mod-snapshot-";
    private static final String OWNER_MARKER = ".openggf-snapshot-owner";

    private final Path root;
    private final Path content;
    private final Object rootIdentity;
    private final String ownerToken;
    private boolean closed;

    private ModAssetSnapshot(Path root, Path content, String ownerToken) throws IOException {
        this.root = root;
        this.content = content;
        this.ownerToken = ownerToken;
        this.rootIdentity = Files.readAttributes(root, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS).fileKey();
    }

    static ModAssetSnapshot file(Path source, long maxBytes, SnapshotHook hook) throws IOException {
        return create(source, false, maxBytes, hook);
    }

    static ModAssetSnapshot directory(Path source, ModInputLimits limits, SnapshotHook hook) throws IOException {
        return create(source, true, limits.maxAssetBytes(), limits, hook);
    }

    private static ModAssetSnapshot create(Path source, boolean directory, long maxFileBytes,
                                           SnapshotHook hook) throws IOException {
        return create(source, directory, maxFileBytes, null, hook);
    }

    private static ModAssetSnapshot create(Path source, boolean directory, long maxFileBytes,
                                           ModInputLimits directoryLimits, SnapshotHook hook) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(hook, "hook");
        Path root = createPrivateTempDirectory();
        String token = UUID.randomUUID().toString();
        try {
            Files.writeString(root.resolve(OWNER_MARKER), token,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Path content = root.resolve(directory ? "tree" : "asset.jar");
            hook.snapshotAllocated(source, content);
            hook.beforeCopy(source);
            if (directory) {
                copyDirectoryVerified(source, content, directoryLimits, hook);
            } else {
                copyFileVerified(source, content, maxFileBytes, null, hook);
            }
            hook.afterCopy(source, content);
            return new ModAssetSnapshot(root, content, token);
        } catch (IOException | RuntimeException e) {
            try {
                deleteCreatedTree(root);
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    Path content() {
        return content;
    }

    private static Path createPrivateTempDirectory() throws IOException {
        Path root = Files.createTempDirectory(PREFIX);
        try {
            Files.setPosixFilePermissions(root, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            // Windows temp ACLs are inherited from the user's private temp directory.
        }
        return root;
    }

    private static void copyDirectoryVerified(Path source, Path destination, ModInputLimits limits,
                                              SnapshotHook hook) throws IOException {
        if (Files.isSymbolicLink(source)
                || !Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Directory snapshot source must be a non-symlink directory: " + source);
        }
        DirectoryCopyBudget budget = new DirectoryCopyBudget(limits);
        Files.createDirectory(destination);
        try (var paths = Files.walk(source)) {
            paths.filter(path -> !path.equals(source)).forEach(path -> {
                try {
                    if (Files.isSymbolicLink(path)) {
                        throw new IOException("Symbolic links are not allowed in directory snapshots: " + path);
                    }
                    String normalizedName = normalizedRelativeName(source.relativize(path));
                    BasicFileAttributes before = attributes(path);
                    budget.checkEntry(normalizedName, before);
                    Path target = destination.resolve(source.relativize(path).toString());
                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        Files.createDirectory(target);
                    } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        copyFileVerified(path, target, limits.maxAssetBytes(), budget, hook);
                    } else {
                        throw new IOException("Special files are not allowed in directory snapshots: " + path);
                    }
                } catch (IOException e) {
                    throw new SnapshotCopyException(e);
                }
            });
        } catch (SnapshotCopyException e) {
            throw e.ioCause;
        }
    }

    private static void copyFileVerified(Path source, Path destination, long maxBytes,
                                         DirectoryCopyBudget budget, SnapshotHook hook) throws IOException {
        BasicFileAttributes before = attributes(source);
        if (!before.isRegularFile() || before.size() > maxBytes) {
            throw new IOException("Snapshot source must be a bounded regular non-symlink file: " + source);
        }
        try (FileChannel input = FileChannel.open(source,
                StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
             FileChannel output = FileChannel.open(destination,
                     StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            long copiedBytes = 0;
            while (input.read(buffer) != -1) {
                buffer.flip();
                copiedBytes = Math.addExact(copiedBytes, buffer.remaining());
                if (copiedBytes > maxBytes) {
                    throw new IOException("Snapshot source exceeds byte limit " + maxBytes + ": " + source);
                }
                if (budget != null) {
                    budget.reserveActualBytes(buffer.remaining());
                }
                while (buffer.hasRemaining()) {
                    output.write(buffer);
                }
                buffer.clear();
            }
        }
        BasicFileAttributes after = attributes(source);
        BasicFileAttributes copied = attributes(destination);
        if (!sameIdentityAndSize(before, after)
                || before.size() != copied.size()
                || !copied.isRegularFile()) {
            throw new IOException("Snapshot source changed while it was being copied: " + source);
        }
        if (budget != null) {
            hook.afterFileCopied(source, destination, budget.actualBytes());
        }
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    static String normalizedRelativeName(Path relativePath) throws IOException {
        List<String> components = new ArrayList<>();
        for (Path component : relativePath) {
            components.add(component.toString());
        }
        return normalizedRelativeNameComponents(components);
    }

    static String normalizedRelativeNameComponents(Iterable<String> components) throws IOException {
        StringBuilder normalized = new StringBuilder();
        for (String segment : components) {
            if (segment.indexOf('\\') >= 0) {
                throw new IOException("Backslashes are not allowed in mod asset path components: " + segment);
            }
            if (!normalized.isEmpty()) {
                normalized.append('/');
            }
            normalized.append(segment);
        }
        try {
            return ModAssetRoot.requireNormalizedEntry(normalized.toString());
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid directory snapshot entry: " + normalized, e);
        }
    }

    private static boolean sameIdentityAndSize(BasicFileAttributes before, BasicFileAttributes after) {
        Object beforeKey = before.fileKey();
        Object afterKey = after.fileKey();
        boolean identityMatches = beforeKey != null && afterKey != null
                ? beforeKey.equals(afterKey)
                : before.lastModifiedTime().equals(after.lastModifiedTime());
        return identityMatches && before.size() == after.size()
                && before.lastModifiedTime().equals(after.lastModifiedTime());
    }

    private static final class DirectoryCopyBudget {
        private final ModInputLimits limits;
        private final Set<String> exactNames = new HashSet<>();
        private final Set<String> foldedNames = new HashSet<>();
        private int entries;
        private long nameBytes;
        private long actualBytes;

        private DirectoryCopyBudget(ModInputLimits limits) {
            this.limits = Objects.requireNonNull(limits, "limits");
        }

        private void checkEntry(String normalizedName, BasicFileAttributes attributes) throws IOException {
            try {
                ModAssetRoot.requireNormalizedEntry(normalizedName);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid directory snapshot entry: " + normalizedName, e);
            }
            if (++entries > limits.maxJarEntries()) {
                throw new IOException("Directory snapshot entry count exceeds limit");
            }
            int currentNameBytes = normalizedName.getBytes(StandardCharsets.UTF_8).length;
            if (currentNameBytes > limits.maxEntryNameBytes()) {
                throw new IOException("Directory snapshot entry name exceeds limit: " + normalizedName);
            }
            nameBytes = Math.addExact(nameBytes, currentNameBytes);
            if (nameBytes > limits.maxAggregateEntryNameBytes()) {
                throw new IOException("Directory snapshot aggregate entry names exceed limit");
            }
            if (!exactNames.add(normalizedName)
                    || !foldedNames.add(normalizedName.toLowerCase(Locale.ROOT))) {
                throw new IOException("Duplicate or case-folding directory snapshot entry: " + normalizedName);
            }
            if (attributes.isRegularFile()) {
                if (attributes.size() > limits.maxAssetBytes()) {
                    throw new IOException("Directory snapshot file exceeds limit: " + normalizedName);
                }
                if (Math.addExact(actualBytes, attributes.size()) > limits.maxModValidationBytes()) {
                    throw new IOException("Directory snapshot aggregate bytes exceed limit");
                }
            }
        }

        private void reserveActualBytes(int bytes) throws IOException {
            long updated = Math.addExact(actualBytes, bytes);
            if (updated > limits.maxModValidationBytes()) {
                throw new IOException("Directory snapshot aggregate bytes exceed limit");
            }
            actualBytes = updated;
        }

        private long actualBytes() {
            return actualBytes;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            closed = true;
            return;
        }
        BasicFileAttributes current = attributes(root);
        Object currentIdentity = current.fileKey();
        boolean identityMatches = rootIdentity != null && currentIdentity != null
                ? rootIdentity.equals(currentIdentity)
                : current.isDirectory() && !Files.isSymbolicLink(root);
        Path marker = root.resolve(OWNER_MARKER);
        if (!identityMatches || Files.isSymbolicLink(root)
                || !root.getFileName().toString().startsWith(PREFIX)
                || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                || !ownerToken.equals(Files.readString(marker))) {
            throw new IOException("Refusing to delete unverified mod snapshot: " + root);
        }
        deleteCreatedTree(root);
        closed = true;
    }

    private static void deleteCreatedTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class SnapshotCopyException extends RuntimeException {
        private final IOException ioCause;
        private SnapshotCopyException(IOException cause) { super(cause); this.ioCause = cause; }
    }
}
