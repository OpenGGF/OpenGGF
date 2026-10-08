package com.openggf.tools.modtestkit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;

/** Stages the separate creator-test artifact. Origin: mod framework readiness, 2026-10-07. */
public final class ModTestKitPackager {
    private ModTestKitPackager() { }

    public static void prepare(Path compiledClasses, Path outputDirectory) throws IOException {
        Path classes = Objects.requireNonNull(compiledClasses, "compiled classes").toAbsolutePath().normalize();
        Path output = Objects.requireNonNull(outputDirectory, "output").toAbsolutePath().normalize();
        Path build = classes.getParent();
        if (!Files.isDirectory(classes, LinkOption.NOFOLLOW_LINKS) || build == null
                || !output.startsWith(build) || output.equals(build)
                || output.startsWith(classes) || classes.startsWith(output)) {
            throw new IllegalArgumentException("Testkit output must be a separate directory under the build root");
        }
        for (Path part = output; part != null && part.startsWith(build); part = part.getParent()) {
            if (Files.isSymbolicLink(part)) throw new IOException("Testkit output crosses a symbolic link");
        }
        Path inputs = classes.resolve("com/openggf/mods/testing");
        Path metadata = classes.resolve("version.properties");
        if (!Files.isDirectory(inputs, LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(metadata)) {
            throw new IOException("Missing compiled testkit classes or build identity metadata");
        }
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            try (var paths = Files.walk(output)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        Files.createDirectories(output);
        try (var paths = Files.walk(inputs)) {
            for (Path source : paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList()) {
                Path destination = output.resolve(classes.relativize(source));
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Path identity = output.resolve("META-INF/openggf-build.properties");
        Files.createDirectories(identity.getParent());
        Files.copy(metadata, identity);
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 2) throw new IllegalArgumentException(
                "Usage: ModTestKitPackager <compiled-classes> <testkit-classes-output>");
        prepare(Path.of(arguments[0]), Path.of(arguments[1]));
    }
}
