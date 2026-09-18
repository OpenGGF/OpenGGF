package com.openggf.tools.audio.completerun;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Shared input checks; callers retain request ordering and publication ownership. */
public final class CompleteRunAudioFiles {
    private CompleteRunAudioFiles() {}

    public static Path file(Path value, String label) {
        Path path = absolute(value, label);
        if (!canonical(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(label + " must be an ordinary non-symlink file");
        }
        return path;
    }

    public static Path directory(Path value, String label) {
        Path path = absolute(value, label);
        if (!canonical(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(label + " must be an ordinary non-symlink directory");
        }
        return path;
    }

    public static Path absolute(Path value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.isAbsolute() || !value.equals(value.normalize())) {
            throw new IllegalArgumentException(label + " must be an absolute normalized path");
        }
        return value;
    }

    private static boolean canonical(Path path) {
        try { return path.equals(path.toRealPath()); }
        catch (IOException missing) { return false; }
    }

    public static void requireDigest(Path path, String algorithm, String pinnedDigest, String label)
            throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
            }
            if (!pinnedDigest.equals(HexFormat.of().formatHex(digest.digest()))) {
                throw new IllegalArgumentException(label + " identity does not match the fixed profile");
            }
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
