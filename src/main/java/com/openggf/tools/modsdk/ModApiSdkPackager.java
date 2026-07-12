package com.openggf.tools.modsdk;

import com.openggf.mods.code.ModApiSurfaceInventory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Prepares the creator-tool classifier and exact API Javadocs. */
public final class ModApiSdkPackager {
    private ModApiSdkPackager() { }

    public static void prepare(Path compiledClasses, Path sourceRoot,
                               Path sdkClassesOutput, Path javadocOutput) throws IOException {
        prepare(compiledClasses, sourceRoot, sdkClassesOutput, javadocOutput,
                ModApiSurfaceInventory.annotatedTypes());
    }

    static void prepare(Path compiledClasses, Path sourceRoot,
                        Path sdkClassesOutput, Path javadocOutput,
                        Collection<Class<?>> inventory) throws IOException {
        Path classes = requireDirectory(compiledClasses, "compiled classes");
        Path sources = requireDirectory(sourceRoot, "source root");
        Path buildRoot = Objects.requireNonNull(classes.getParent(),
                "compiled classes must have a build-root parent");
        Path sdkOutput = safeOutput(sdkClassesOutput, buildRoot, classes, sources,
                "SDK classes output");
        Path docsOutput = safeOutput(javadocOutput, buildRoot, classes, sources,
                "Javadoc output");
        if (sdkOutput.equals(docsOutput) || sdkOutput.startsWith(docsOutput)
                || docsOutput.startsWith(sdkOutput)) {
            throw new IllegalArgumentException(
                    "SDK classes and Javadoc outputs must be separate directory trees");
        }
        List<Class<?>> exact = ModApiJavadocTool.requireAnnotatedInventory(inventory);
        if (exact.isEmpty()) {
            throw new IllegalArgumentException("SDK inventory must not be empty");
        }

        resetDirectory(sdkOutput);
        resetDirectory(docsOutput);
        copyTree(classes, sdkOutput, Path.of("com/openggf/tools/modsdk"));
        copyTree(classes, sdkOutput, Path.of("META-INF/openggf-mod-sdk/templates"));
        copySdkServices(classes, sdkOutput);
        ModApiJavadocTool.generate(sources, docsOutput, exact);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: ModApiSdkPackager "
                    + "<compiled-classes> <source-root> <sdk-classes-output> <javadoc-output>");
        }
        prepare(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
    }

    private static Path requireDirectory(Path path, String label) {
        Path normalized = Objects.requireNonNull(path, label).toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException(label + " is not a directory: " + normalized);
        }
        return normalized;
    }

    private static Path safeOutput(Path path, Path buildRoot, Path classes, Path sources,
                                   String label) {
        Path normalized = Objects.requireNonNull(path, label).toAbsolutePath().normalize();
        if (!normalized.startsWith(buildRoot) || normalized.equals(buildRoot)
                || normalized.getParent() == null || normalized.equals(classes) || normalized.equals(sources)
                || normalized.startsWith(classes) || classes.startsWith(normalized)
                || normalized.startsWith(sources) || sources.startsWith(normalized)) {
            throw new IllegalArgumentException("Unsafe " + label + " outside build root "
                    + buildRoot + ": " + normalized);
        }
        return normalized;
    }

    private static void resetDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(directory);
    }

    private static void copyTree(Path classes, Path output, Path relativeRoot) throws IOException {
        Path sourceRoot = classes.resolve(relativeRoot).normalize();
        if (!sourceRoot.startsWith(classes) || !Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Missing SDK classifier input: " + sourceRoot);
        }
        try (var paths = Files.walk(sourceRoot)) {
            for (Path source : paths.filter(Files::isRegularFile).toList()) {
                Path relative = classes.relativize(source);
                Path destination = output.resolve(relative).normalize();
                if (!destination.startsWith(output)) {
                    throw new IllegalArgumentException("Unsafe SDK destination: " + destination);
                }
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

    private static void copySdkServices(Path classes, Path output) throws IOException {
        Path services = classes.resolve("META-INF/services");
        if (!Files.isDirectory(services)) {
            return;
        }
        try (var paths = Files.walk(services)) {
            for (Path source : paths.filter(Files::isRegularFile).toList()) {
                List<String> providers = Files.readAllLines(source).stream()
                        .map(line -> line.replaceFirst("#.*$", "").trim())
                        .filter(line -> !line.isEmpty()).toList();
                if (providers.isEmpty()
                        || providers.stream().anyMatch(provider -> !provider.startsWith(
                        "com.openggf.tools.modsdk."))) {
                    continue;
                }
                Path destination = output.resolve(classes.relativize(source)).normalize();
                if (!destination.startsWith(output)) {
                    throw new IllegalArgumentException("Unsafe SDK service destination: " + destination);
                }
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }
}
