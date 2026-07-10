package com.openggf.net.master;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Filesystem storage for bounded, content-addressed input recordings. */
public final class RecordingBlobStore {
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private final Path dir;

    public RecordingBlobStore(Path dir) {
        this.dir = dir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.dir);
        } catch (IOException failure) {
            throw new IllegalStateException("unable to create recording store", failure);
        }
    }

    public void put(String hashHex, byte[] bytes) {
        try {
            Files.write(pathFor(hashHex), bytes);
        } catch (IOException failure) {
            throw new IllegalStateException("unable to store recording", failure);
        }
    }

    public boolean putIfWithinLimit(String hashHex, byte[] bytes, long maxTotalBytes) {
        Path target = pathFor(hashHex);
        if (Files.isRegularFile(target)) {
            return true;
        }
        if (totalBytes() + bytes.length > maxTotalBytes) {
            return false;
        }
        put(hashHex, bytes);
        return true;
    }

    public long totalBytes() {
        try (Stream<Path> files = Files.list(dir)) {
            long total = 0;
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                total = Math.addExact(total, Files.size(path));
            }
            return total;
        } catch (IOException | ArithmeticException failure) {
            throw new IllegalStateException("unable to measure recording store", failure);
        }
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

    public int deleteOlderThan(long cutoffMillis) {
        int deleted = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                if (HASH.matcher(path.getFileName().toString()).matches()
                        && Files.getLastModifiedTime(path).toMillis() < cutoffMillis
                        && Files.deleteIfExists(path)) {
                    deleted++;
                }
            }
            return deleted;
        } catch (IOException failure) {
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
