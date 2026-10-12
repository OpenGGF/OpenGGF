package com.openggf.mods.scene.host;

import com.openggf.mods.scene.SceneStorage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** {@link SceneStorage} in one directory per mod under the save root. Engine-internal. */
final class FileSceneStorage implements SceneStorage {
    private static final Logger LOG = Logger.getLogger(FileSceneStorage.class.getName());
    private static final Pattern NAME = Pattern.compile("[a-z0-9._-]{1,64}");
    static final int MAX_BYTES = MAX_TEXT_BYTES;

    private final Path root;
    private final Path directory;
    private final Path legacyDirectory;

    FileSceneStorage(Path directory) {
        this(directory.toAbsolutePath().normalize().getParent(), directory, null);
    }

    FileSceneStorage(Path root, Path directory, Path legacyDirectory) {
        this.root = root.toAbsolutePath().normalize();
        this.directory = directory.toAbsolutePath().normalize();
        this.legacyDirectory = legacyDirectory == null ? null : legacyDirectory.toAbsolutePath().normalize();
        if (!this.directory.startsWith(this.root) || (this.legacyDirectory != null && !this.legacyDirectory.startsWith(this.root))) {
            throw new IllegalArgumentException("Mod storage must remain inside the engine save root");
        }
    }

    private boolean safe(Path path) {
        if (!path.startsWith(root)) return false;
        Path current = root;
        for (Path component : root.relativize(path)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) return false;
        }
        return true;
    }

    private Path file(String name) {
        if (name == null || !NAME.matcher(name).matches() || name.startsWith(".")) {
            throw new IllegalArgumentException("Invalid storage name: " + name);
        }
        return directory.resolve(name);
    }

    @Override
    public Optional<String> read(String name) {
        Optional<byte[]> bytes = readBounded(name, MAX_TEXT_BYTES);
        if (bytes.isEmpty()) return Optional.empty();
        try {
            return Optional.of(StandardCharsets.UTF_8.newDecoder()
                    .decode(java.nio.ByteBuffer.wrap(bytes.get())).toString());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Mod storage text is not UTF-8: " + name, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean write(String name, String text) {
        return writeBounded(name, (text == null ? "" : text).getBytes(StandardCharsets.UTF_8),
                MAX_TEXT_BYTES);
    }

    @Override
    public Optional<byte[]> readBytes(String name) {
        return readBounded(name, MAX_BINARY_BYTES);
    }

    @Override
    public boolean writeBytes(String name, byte[] bytes) {
        java.util.Objects.requireNonNull(bytes, "bytes");
        return writeBounded(name, bytes, MAX_BINARY_BYTES);
    }

    private Optional<byte[]> readBounded(String name, int cap) {
        Path path = file(name);
        if (!safe(path)) return Optional.empty();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) && legacyDirectory != null) {
            path = legacyDirectory.resolve(name);
        }
        if (!safe(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(cap + 1);
            return bytes.length > cap ? Optional.empty() : Optional.of(bytes);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Mod storage read failed: " + path, e);
            return Optional.empty();
        }
    }

    /** Stages the whole file beside its target, then renames it over the target. */
    private boolean writeBounded(String name, byte[] bytes, int cap) {
        Path path = file(name);
        if (!safe(path)) return false;
        if (bytes.length > cap) {
            return false;
        }
        try {
            Files.createDirectories(directory);
            if (!safe(path)) return false;
            Path temp = Files.createTempFile(directory, "." + name + ".", ".tmp");
            try {
                Files.write(temp, bytes);
                try {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Scene storage write failed: " + path, e);
            return false;
        }
    }

    @Override
    public boolean delete(String name) {
        try {
            Path path = file(name);
            return safe(path) && Files.deleteIfExists(path);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public List<String> list() {
        List<String> out = new ArrayList<>();
        if (!safe(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return out;
        }
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(p -> safe(p) && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .map(p -> p.getFileName().toString())
                    .filter(n -> NAME.matcher(n).matches() && !n.startsWith("."))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Scene storage list failed: " + directory, e);
        }
        return out;
    }
}
