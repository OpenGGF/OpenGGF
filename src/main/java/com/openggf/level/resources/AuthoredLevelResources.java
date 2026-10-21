package com.openggf.level.resources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/** Bounded authored-format intake shared by editor, runtime scenes and offline tools. */
public final class AuthoredLevelResources {
    public static final int MAX_ENCODED_BYTES = 64 * 1024 * 1024;

    private AuthoredLevelResources() { }

    /** Asset owners must acquire bytes through their own typed LoadOp before calling this decoder. */
    public static AuthoredLevelDecoder.Decoded decode(String format, byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length > MAX_ENCODED_BYTES) throw new IOException("Authored level exceeds the 64 MiB storage limit");
        try {
            // Only engine-packaged providers participate; a creator's context classloader is not authority.
            var providers = ServiceLoader.load(AuthoredLevelDecoder.class, AuthoredLevelDecoder.class.getClassLoader());
            var decoded = selectDecoder(format, providers).decode(bytes);
            if (decoded == null) throw new IOException("Authored level decoder returned no terrain");
            return decoded;
        } catch (ServiceConfigurationError failure) {
            throw new IOException("Cannot load authored level format provider", failure);
        }
    }

    /** Local hosts enforce their asset-root confinement before supplying this path. */
    public static AuthoredLevelDecoder.Decoded read(String format, Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) throw new IOException("Authored level must be a regular file");
        if (Files.size(file) > MAX_ENCODED_BYTES) throw new IOException("Authored level exceeds the 64 MiB storage limit");
        try (var input = Files.newInputStream(file)) {
            return decode(format, input.readNBytes(MAX_ENCODED_BYTES + 1));
        }
    }

    static AuthoredLevelDecoder selectDecoder(String format, Iterable<AuthoredLevelDecoder> providers) throws IOException {
        Objects.requireNonNull(format, "format");
        AuthoredLevelDecoder selected = null;
        for (var provider : providers) {
            if (!format.equals(provider.format())) continue;
            if (selected != null) throw new IOException("Multiple authored level decoders for " + format);
            selected = provider;
        }
        if (selected == null) throw new IOException("No authored level decoder for " + format);
        return selected;
    }
}
