package com.openggf.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** A validated, bounded source of files owned by one mod. */
public sealed interface ModAssetRoot extends AutoCloseable
        permits AbstractModAssetRoot {
    byte[] readBounded(String normalizedEntry, long maxBytes) throws IOException;
    String describe();
    ModInputLimits limits();

    @Override
    void close() throws IOException;

    static ModAssetRoot jar(Path declaredRoot, Path jar, ModInputLimits limits) throws IOException {
        return new JarModAssetRoot(declaredRoot, jar, limits);
    }

    static ModAssetRoot jar(Path declaredRoot, Path jar) throws IOException {
        return jar(declaredRoot, jar, ModInputLimits.production());
    }

    static ModAssetRoot directory(Path declaredRoot, Path directory, ModInputLimits limits) throws IOException {
        return new DirectoryModAssetRoot(declaredRoot, directory, limits);
    }

    static ModAssetRoot directory(Path declaredRoot, Path directory) throws IOException {
        return directory(declaredRoot, directory, ModInputLimits.production());
    }

    static ModAssetRoot forTests(String description, ModInputLimits limits) {
        return new InMemoryModAssetRoot(description, limits);
    }

    static ModAssetRoot forTests(String description) {
        return forTests(description, ModInputLimits.production());
    }

    static String requireNormalizedEntry(String entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.isEmpty() || entry.indexOf('\0') >= 0 || entry.indexOf('\\') >= 0
                || entry.startsWith("/") || hasWindowsDrivePrefix(entry) || Path.of(entry).isAbsolute()) {
            throw new IllegalArgumentException("Entry must be a normalized relative forward-slash path: " + entry);
        }
        for (String segment : entry.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid entry path segment: " + entry);
            }
        }
        return entry;
    }

    private static boolean hasWindowsDrivePrefix(String entry) {
        return entry.length() >= 2 && entry.charAt(1) == ':'
                && ((entry.charAt(0) >= 'A' && entry.charAt(0) <= 'Z')
                || (entry.charAt(0) >= 'a' && entry.charAt(0) <= 'z'));
    }
}

abstract sealed class AbstractModAssetRoot implements ModAssetRoot
        permits JarModAssetRoot, DirectoryModAssetRoot, InMemoryModAssetRoot {
    private final ModInputLimits limits;
    private boolean closed;

    AbstractModAssetRoot(ModInputLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    @Override
    public final ModInputLimits limits() {
        return limits;
    }

    final long checkedCap(long requestedMax) throws IOException {
        ensureOpen();
        if (requestedMax <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        if (requestedMax > limits.maxAssetBytes()) {
            throw new IllegalArgumentException("Requested cap exceeds root asset limit");
        }
        return Math.min(requestedMax, limits.maxAssetBytes());
    }

    final void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Mod asset root is closed: " + describe());
        }
    }

    final void markClosed() {
        closed = true;
    }

    static byte[] readFullyBounded(InputStream input, long declaredSize, long cap) throws IOException {
        if (declaredSize > cap) {
            throw new IOException("Asset declared size " + declaredSize + " exceeds limit " + cap);
        }
        int initial = declaredSize >= 0 ? Math.toIntExact(Math.min(declaredSize, 8192)) : 8192;
        ByteArrayOutputStream output = new ByteArrayOutputStream(initial);
        byte[] buffer = new byte[8192];
        long count = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            count += read;
            if (count > cap) {
                throw new IOException("Asset stream exceeds limit " + cap);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    static Path containedRealPath(Path declaredRoot, Path target) throws IOException {
        Objects.requireNonNull(declaredRoot, "declaredRoot");
        Objects.requireNonNull(target, "target");
        Path rootReal = declaredRoot.toRealPath();
        Path targetReal = target.toRealPath();
        if (!targetReal.startsWith(rootReal)) {
            throw new IOException("Path escapes declared mod root: " + target);
        }
        return targetReal;
    }

    static void registerName(String name, ModInputLimits limits, Set<String> exact,
                             Set<String> folded, long[] aggregate) throws IOException {
        try {
            ModAssetRoot.requireNormalizedEntry(name);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid mod entry name: " + name, e);
        }
        int bytes = name.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > limits.maxEntryNameBytes()) {
            throw new IOException("Entry name exceeds byte limit: " + name);
        }
        aggregate[0] = Math.addExact(aggregate[0], bytes);
        if (aggregate[0] > limits.maxAggregateEntryNameBytes()) {
            throw new IOException("Aggregate entry-name bytes exceed limit");
        }
        if (!exact.add(name)) {
            throw new IOException("Duplicate entry name: " + name);
        }
        if (!folded.add(name.toLowerCase(Locale.ROOT))) {
            throw new IOException("Case-folding entry collision: " + name);
        }
    }
}

final class JarModAssetRoot extends AbstractModAssetRoot {
    private final Path jarPath;
    private final ZipFile zip;

    JarModAssetRoot(Path declaredRoot, Path jar, ModInputLimits limits) throws IOException {
        super(limits);
        this.jarPath = containedRealPath(declaredRoot, jar);
        if (!Files.isRegularFile(jarPath) || Files.size(jarPath) > limits.maxJarBytes()) {
            throw new IOException("Invalid or oversized mod jar: " + jarPath);
        }
        ZipFile opened = new ZipFile(jarPath.toFile());
        try {
            validateCentralDirectory(opened);
            this.zip = opened;
        } catch (IOException | RuntimeException e) {
            opened.close();
            throw e;
        }
    }

    private void validateCentralDirectory(ZipFile opened) throws IOException {
        Set<String> exact = new HashSet<>();
        Set<String> folded = new HashSet<>();
        long[] nameBytes = {0};
        long totalDeclared = 0;
        int count = 0;
        Enumeration<? extends ZipEntry> entries = opened.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (++count > limits().maxJarEntries()) {
                throw new IOException("Jar entry count exceeds limit");
            }
            registerName(entry.getName(), limits(), exact, folded, nameBytes);
            long size = entry.getSize();
            if (size > limits().maxAssetBytes()) {
                throw new IOException("Jar entry exceeds asset limit: " + entry.getName());
            }
            if (size >= 0) {
                totalDeclared = Math.addExact(totalDeclared, size);
                if (totalDeclared > limits().maxModValidationBytes()) {
                    throw new IOException("Jar declared bytes exceed validation budget");
                }
            }
        }
    }

    @Override
    public byte[] readBounded(String normalizedEntry, long maxBytes) throws IOException {
        long cap = checkedCap(maxBytes);
        String name = ModAssetRoot.requireNormalizedEntry(normalizedEntry);
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("Missing mod asset: " + name);
        }
        try (InputStream input = zip.getInputStream(entry)) {
            return readFullyBounded(input, entry.getSize(), cap);
        }
    }

    @Override
    public String describe() { return jarPath.toString(); }

    @Override
    public void close() throws IOException {
        try {
            zip.close();
        } finally {
            markClosed();
        }
    }
}

final class DirectoryModAssetRoot extends AbstractModAssetRoot {
    private final Path root;

    DirectoryModAssetRoot(Path declaredRoot, Path directory, ModInputLimits limits) throws IOException {
        super(limits);
        this.root = containedRealPath(declaredRoot, directory);
        if (!Files.isDirectory(root)) {
            throw new IOException("Mod asset root is not a directory: " + root);
        }
        validateTree();
    }

    private void validateTree() throws IOException {
        Set<String> exact = new HashSet<>();
        Set<String> folded = new HashSet<>();
        long[] nameBytes = {0};
        long[] total = {0};
        int[] count = {0};
        try (var paths = Files.walk(root)) {
            paths.filter(path -> !path.equals(root)).forEach(path -> {
                try {
                    if (Files.isSymbolicLink(path)
                            || (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                            && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))) {
                        throw new IOException("Symbolic links and special files are not allowed in directory roots: "
                                + path);
                    }
                    Path real = path.toRealPath();
                    if (!real.startsWith(root)) {
                        throw new IOException("Directory entry escapes root: " + path);
                    }
                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        return;
                    }
                    if (++count[0] > limits().maxJarEntries()) {
                        throw new IOException("Directory entry count exceeds limit");
                    }
                    String name = root.relativize(path).toString().replace('\\', '/');
                    registerName(name, limits(), exact, folded, nameBytes);
                    long size = Files.size(real);
                    if (size > limits().maxAssetBytes()) {
                        throw new IOException("Directory asset exceeds limit: " + name);
                    }
                    total[0] = Math.addExact(total[0], size);
                    if (total[0] > limits().maxModValidationBytes()) {
                        throw new IOException("Directory bytes exceed validation budget");
                    }
                } catch (IOException e) {
                    throw new DirectoryValidationException(e);
                }
            });
        } catch (DirectoryValidationException e) {
            throw e.ioCause;
        }
    }

    @Override
    public byte[] readBounded(String normalizedEntry, long maxBytes) throws IOException {
        long cap = checkedCap(maxBytes);
        String name = ModAssetRoot.requireNormalizedEntry(normalizedEntry);
        Path unresolved = rejectSymlinkPath(name);
        Path candidate = unresolved.toRealPath();
        if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
            throw new IOException("Missing or escaping mod asset: " + name);
        }
        long size = Files.size(candidate);
        try (InputStream input = Files.newInputStream(candidate)) {
            return readFullyBounded(input, size, cap);
        }
    }

    private Path rejectSymlinkPath(String normalizedEntry) throws IOException {
        Path relative = Path.of(normalizedEntry);
        Path current = root;
        for (int i = 0; i < relative.getNameCount(); i++) {
            current = current.resolve(relative.getName(i));
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic-link paths are not readable from directory roots: "
                        + normalizedEntry);
            }
            boolean last = i == relative.getNameCount() - 1;
            if (!last && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Missing directory in mod asset path: " + normalizedEntry);
            }
            if (last && !Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Missing mod asset: " + normalizedEntry);
            }
        }
        return current;
    }

    @Override
    public String describe() { return root.toString(); }

    @Override
    public void close() { markClosed(); }

    private static final class DirectoryValidationException extends RuntimeException {
        private final IOException ioCause;
        private DirectoryValidationException(IOException cause) { super(cause); this.ioCause = cause; }
    }
}

final class InMemoryModAssetRoot extends AbstractModAssetRoot {
    private final String description;

    InMemoryModAssetRoot(String description, ModInputLimits limits) {
        super(limits);
        this.description = Objects.requireNonNull(description, "description");
    }

    @Override
    public byte[] readBounded(String normalizedEntry, long maxBytes) throws IOException {
        checkedCap(maxBytes);
        ModAssetRoot.requireNormalizedEntry(normalizedEntry);
        throw new IOException("No in-memory asset registered: " + normalizedEntry);
    }

    @Override
    public String describe() { return description; }

    @Override
    public void close() { markClosed(); }
}
