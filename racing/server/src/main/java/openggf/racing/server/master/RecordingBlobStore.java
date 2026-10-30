package openggf.racing.server.master;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Filesystem storage for bounded, content-addressed input recordings. Writes and
 * the retention sweep are serialized: the upload handler and the hourly sweep run
 * on different threads, and an unserialized sweep could delete a blob whose
 * retention a concurrent reuse had just refreshed.
 */
public final class RecordingBlobStore {
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final String STAGING_PREFIX = ".recording-";
    private static final String STAGING_SUFFIX = ".tmp";
    private final Path dir;
    private long storedBytes;

    public RecordingBlobStore(Path dir) {
        this.dir = dir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.dir);
            removeInterruptedPublications();
            storedBytes = scanTotalBytes();
        } catch (IOException failure) {
            throw new IllegalStateException("unable to create recording store", failure);
        }
    }

    public synchronized void put(String hashHex, byte[] bytes) {
        Path target = pathFor(hashHex);
        Path staging = null;
        try {
            long previous = 0;
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalStateException("recording path is not a regular file");
                }
                previous = Files.size(target);
            }
            long updatedBytes = Math.addExact(
                    Math.subtractExact(storedBytes, previous), bytes.length);
            staging = Files.createTempFile(dir, STAGING_PREFIX, STAGING_SUFFIX);
            Files.write(staging, bytes);
            movePublished(staging, target);
            staging = null;
            storedBytes = updatedBytes;
        } catch (IOException | ArithmeticException failure) {
            throw new IllegalStateException("unable to store recording", failure);
        } finally {
            if (staging != null) {
                try {
                    Files.deleteIfExists(staging);
                } catch (IOException ignored) {
                    // The next store startup removes interrupted publication artifacts.
                }
            }
        }
    }

    public synchronized boolean putIfWithinLimit(String hashHex, byte[] bytes, long maxTotalBytes) {
        Path target = pathFor(hashHex);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("recording path is not a regular file");
            }
            try {
                long existingSize = Files.size(target);
                if (existingSize == bytes.length && contentsEqual(target, bytes)) {
                    Files.getFileAttributeView(target, BasicFileAttributeView.class,
                                    LinkOption.NOFOLLOW_LINKS)
                            .setTimes(FileTime.fromMillis(System.currentTimeMillis()), null, null);
                    return true;
                }
                // External storage damage can also change a blob's size and make
                // the cached quota stale. Reconcile the rare repair path before
                // deciding whether the validated replacement fits.
                storedBytes = scanTotalBytes();
                long updatedBytes = Math.addExact(
                        Math.subtractExact(storedBytes, existingSize), bytes.length);
                if (maxTotalBytes < 0 || updatedBytes > maxTotalBytes) {
                    return false;
                }
                // The upload route already proved that bytes match hashHex. Replace a
                // stale or corrupt content-addressed entry instead of attributing the
                // storage fault to the player when the verifier reads it later.
                put(hashHex, bytes);
                return true;
            } catch (IOException | ArithmeticException failure) {
                throw new IllegalStateException("unable to validate stored recording", failure);
            }
        }
        if (maxTotalBytes < 0 || bytes.length > maxTotalBytes
                || storedBytes > maxTotalBytes - bytes.length) {
            return false;
        }
        put(hashHex, bytes);
        return true;
    }

    public synchronized long totalBytes() {
        return storedBytes;
    }

    private static boolean contentsEqual(Path path, byte[] expected) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            int offset = 0;
            while (offset < expected.length) {
                int read = input.read(expected, offset, expected.length - offset);
                if (read < 0) {
                    return false;
                }
                offset += read;
            }
            return input.read() < 0;
        }
    }

    private long scanTotalBytes() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            long total = 0;
            for (Path path : files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    && HASH.matcher(path.getFileName().toString()).matches()).toList()) {
                total = Math.addExact(total, Files.size(path));
            }
            return total;
        }
    }

    private void removeInterruptedPublications() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            for (Path path : files.filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith(STAGING_PREFIX) && name.endsWith(STAGING_SUFFIX);
            }).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void movePublished(Path staging, Path target) throws IOException {
        Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    public Optional<byte[]> get(String hashHex) {
        Path path = pathFor(hashHex);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException failure) {
            throw new IllegalStateException("unable to read recording", failure);
        }
    }

    public synchronized int deleteOlderThan(long cutoffMillis) {
        int deleted = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path path : files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList()) {
                if (HASH.matcher(path.getFileName().toString()).matches()
                        && Files.getLastModifiedTime(path).toMillis() < cutoffMillis) {
                    long size = Files.size(path);
                    if (!Files.deleteIfExists(path)) continue;
                    storedBytes = Math.subtractExact(storedBytes, size);
                    deleted++;
                }
            }
            return deleted;
        } catch (IOException | ArithmeticException failure) {
            throw new IllegalStateException("unable to collect recording store", failure);
        }
    }

    private Path pathFor(String hashHex) {
        if (hashHex == null || !HASH.matcher(hashHex).matches()) {
            throw new IllegalArgumentException("invalid recording hash");
        }
        return dir.resolve(hashHex);
    }
}
