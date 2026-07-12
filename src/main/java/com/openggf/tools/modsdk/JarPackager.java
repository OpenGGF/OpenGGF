package com.openggf.tools.modsdk;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.io.DirectoryAccess;
import com.openggf.io.SnapshotModAssetRoot;

/** Deterministically packages and validates an exploded mod directory. */
public final class JarPackager {
    /** ZIP's portable epoch (1980-01-01T00:00:00Z). */
    static final long ENTRY_TIMESTAMP = 315_532_800_000L;
    private static final Set<String> WINDOWS_DEVICE_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5",
            "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5",
            "LPT6", "LPT7", "LPT8", "LPT9");

    private JarPackager() { }

    public static void packageDirectory(Path inputDirectory, Path outputJar) throws IOException {
        packageDirectory(inputDirectory,outputJar,ModInputLimits.production(),ignored->{});
    }
    static void packageDirectory(Path inputDirectory,Path outputJar,ModInputLimits limits,
                                 java.util.function.Consumer<Path> afterSnapshot)throws IOException{
        packageDirectory(inputDirectory,outputJar,limits,afterSnapshot,()->{});
    }
    static void packageDirectory(Path inputDirectory,Path outputJar,ModInputLimits limits,
                                 java.util.function.Consumer<Path> afterSnapshot,
                                 Runnable beforePublish)throws IOException{
        Path input = Objects.requireNonNull(inputDirectory, "inputDirectory")
                .toAbsolutePath().normalize();
        Path output = Objects.requireNonNull(outputJar, "outputJar").toAbsolutePath().normalize();
        if (Files.isSymbolicLink(input)
                || !Files.isDirectory(input, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Package input must be a non-symlink directory: " + input);
        }
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Package output already exists: " + output);
        }
        if (output.startsWith(input)) {
            throw new IllegalArgumentException("Package output must be outside the input directory: " + output);
        }

        Path parent = output.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Package output requires a parent directory: " + output);
        }
        Files.createDirectories(parent);
        try(SnapshotModAssetRoot snapshot=ModAssetRoot.snapshotDirectory(input.getParent(),input,
                Objects.requireNonNull(limits),DirectoryAccess.DEVELOPMENT)){
          Path retained=snapshot.immutableContentPath();Objects.requireNonNull(afterSnapshot).accept(retained);
          List<String> names=snapshot.validatedEntryNames().stream().sorted().toList();validateEntryNames(names);
          List<Path> files=names.stream().map(retained::resolve).toList();
          Path staging = Files.createTempFile(parent, ".ggfmod-package-", ".jar");
          boolean published = false;
          try {
            writeJar(files,names,staging);
            ModJarValidator.Report report = new ModJarValidator().validate(staging);
            if (!report.valid()) {
                throw new IllegalArgumentException("Mod validation failed:\n"
                        + String.join("\n", report.numberedLines()));
            }
            NoClobberPublisher.publish(staging,output,beforePublish);
            published = true;
          } finally {
            if (!published) {
                Files.deleteIfExists(staging);
            }
          }
        }
    }

    static void validateEntryNames(List<String> names) {
        Set<String> exact = new HashSet<>();
        Set<String> folded = new HashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank() || name.startsWith("/") || name.contains("\\")
                    || name.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Unsafe package entry name: " + name);
            }
            String[] segments = name.split("/", -1);
            for (String segment : segments) {
                if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                    throw new IllegalArgumentException("Unsafe package entry name: " + name);
                }
                String stem = segment.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
                if (WINDOWS_DEVICE_NAMES.contains(stem)) {
                    throw new IllegalArgumentException("Reserved package entry name: " + name);
                }
            }
            String upper = name.toUpperCase(Locale.ROOT);
            if (upper.equals("META-INF/MANIFEST.MF") || upper.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)")) {
                throw new IllegalArgumentException("Reserved package entry name: " + name);
            }
            if (!exact.add(name)) {
                throw new IllegalArgumentException("Duplicate package entry name: " + name);
            }
            if (!folded.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Case-colliding package entry name: " + name);
            }
        }
    }

    private static void writeJar(List<Path> files, List<String> names, Path staging) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(staging))) {
            byte[] buffer = new byte[8192];
            for (int index = 0; index < files.size(); index++) {
                JarEntry entry = new JarEntry(names.get(index));
                entry.setTime(ENTRY_TIMESTAMP);
                output.putNextEntry(entry);
                try (InputStream input = Files.newInputStream(files.get(index))) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                }
                output.closeEntry();
            }
        }
    }

    private static String entryName(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
