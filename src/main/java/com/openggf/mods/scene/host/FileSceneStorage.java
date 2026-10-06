package com.openggf.mods.scene.host;

import com.openggf.mods.scene.SceneStorage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    static final int MAX_BYTES = 1 << 20;

    private final Path directory;

    FileSceneStorage(Path directory) {
        this.directory = directory;
    }

    private Path file(String name) {
        if (name == null || !NAME.matcher(name).matches() || name.startsWith(".")) {
            throw new IllegalArgumentException("Invalid storage name: " + name);
        }
        return directory.resolve(name);
    }

    @Override
    public Optional<String> read(String name) {
        Path path = file(name);
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES) {
                return Optional.empty();
            }
            return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Scene storage read failed: " + path, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean write(String name, String text) {
        Path path = file(name);
        byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            return false;
        }
        try {
            Files.createDirectories(directory);
            Path temp = directory.resolve("." + name + ".tmp");
            Files.write(temp, bytes);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Scene storage write failed: " + path, e);
            return false;
        }
    }

    @Override
    public boolean delete(String name) {
        try {
            return Files.deleteIfExists(file(name));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public List<String> list() {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return out;
        }
        try (Stream<Path> files = Files.list(directory)) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> NAME.matcher(n).matches() && !n.startsWith("."))
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Scene storage list failed: " + directory, e);
        }
        return out;
    }
}
