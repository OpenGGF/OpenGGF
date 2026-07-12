package com.openggf.tools.modsdk;

import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.io.SnapshotModAssetRoot;
import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.ModLevelDefinitionParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

/** Validates and publishes the exact Task 14 editor-export directory contract. */
public final class LevelConverter {
    private final Runnable afterValidation;
    public LevelConverter(){this(()->{});}
    LevelConverter(Runnable afterValidation){this.afterValidation=Objects.requireNonNull(afterValidation);}
    public Result convert(Path exportDirectory, Path outputDirectory) throws IOException {
        Path source = requireDirectory(exportDirectory, "editor export");
        Path target = Objects.requireNonNull(outputDirectory, "outputDirectory").toAbsolutePath().normalize();
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Level output already exists: " + target);
        }
        try (SnapshotModAssetRoot root = ModAssetRoot.snapshotDirectory(source.getParent(), source,
                ModInputLimits.production(), DirectoryAccess.DEVELOPMENT)) {
            ModLevelDefinitionParser.read(root, new BakedLevelRef("level.json"));
            Path retained=root.immutableContentPath();
            validateExactInventory(retained);afterValidation.run();

            Path parent = Objects.requireNonNull(target.getParent(), "level output parent");
            Files.createDirectories(parent);
            Path staging = Files.createTempDirectory(parent, target.getFileName() + ".tmp-");
            int count = 0;
            try {
            try(var paths=Files.walk(retained)){
            for (Path path : paths.sorted().toList()) {
                if (path.equals(retained)) continue;
                if (Files.isSymbolicLink(path)) throw new IOException("Level export contains a symlink: " + path);
                Path relative = retained.relativize(path);
                Path destination = staging.resolve(relative).normalize();
                if (!destination.startsWith(staging)) throw new IOException("Level export path escapes output");
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(destination);
                else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination); count++;
                } else throw new IOException("Unsupported level export entry: " + path);
            }
            }
            Files.move(staging, target);
            return new Result(target, count);
            } catch (IOException | RuntimeException failure) {
            deleteTree(staging, failure);
            throw failure;
            }
        }
    }

    private static void validateExactInventory(Path source) throws IOException {
        Set<String> required = Set.of("level.json", "patterns.bin", "chunks.bin", "blocks.bin",
                "fg-map.bin", "solid-heights.bin", "solid-widths.bin", "solid-angles.bin",
                "collision-primary.bin", "collision-secondary.bin", "palettes.bin");
        Set<String> allowed = new java.util.HashSet<>(required); allowed.add("bg-map.bin");
        java.util.HashSet<String> actual = new java.util.HashSet<>();
        try (var paths=Files.walk(source)) {
            for (Path path:paths.filter(p -> !p.equals(source)).toList()) {
                if (Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Level export must contain only root-level contract files");
                actual.add(source.relativize(path).toString().replace('\\','/'));
            }
        }
        if (!actual.containsAll(required) || !allowed.containsAll(actual))
            throw new IOException("Level export inventory does not match the exact JSON/binary contract");
    }

    private static Path requireDirectory(Path value, String label) {
        Path path = Objects.requireNonNull(value, label).toAbsolutePath().normalize();
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)
                || path.getParent() == null) throw new IllegalArgumentException(label + " must be a non-symlink directory");
        return path;
    }

    private static void deleteTree(Path root, Throwable failure) {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    public record Result(Path output, int filesCopied) {}
}
