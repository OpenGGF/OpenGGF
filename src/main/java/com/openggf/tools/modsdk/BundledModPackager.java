package com.openggf.tools.modsdk;

import com.openggf.io.ModInputLimits;
import com.openggf.mods.BundledModManifest;
import com.openggf.mods.ModManifest;
import com.openggf.mods.ModManifestException;
import com.openggf.mods.ModManifestParser;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Build step that turns the configured first-party mod projects into the jars bundled with an
 * engine build and writes the build-pinned {@link BundledModManifest}.
 *
 * <p>Each project uses the example layout ({@code src/main/java}, {@code src/main/resources}
 * with {@code META-INF/openggf-mod.yaml}). Sources compile with {@code javac --release 21}
 * against the engine build classpath; the result is packaged by {@code ggfmod package
 * --warnings error}, so the mod validator runs and any warning fails the build. Jars are
 * written as {@code <id>-<version>.jar}; the manifest records id, version, file, SHA-256 and
 * size, and is always written (possibly empty) so a stale list never survives a rebuild.
 *
 * <p>Usage: {@code BundledModPackager <basedir> <jar-output-dir> <manifest-output>
 * [<project>[,<project>...]]}, project paths relative to {@code basedir}. Maven runs it at
 * {@code prepare-package} with the {@code openggf.bundled.mods} property.
 *
 * <p>Origin: Time Attack bundled-mod extraction, lane B (2026-10-08).
 */
public final class BundledModPackager {
    private static final String MOD_MANIFEST = "META-INF/openggf-mod.yaml";

    private BundledModPackager() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) {
            System.err.println("Usage: BundledModPackager <basedir> <jar-output-dir> <manifest-output> "
                    + "[<project>[,<project>...]]");
            System.exit(2);
        }
        try {
            BundledModManifest manifest = packageProjects(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]),
                    projects(args.length == 4 ? args[3] : ""), System.getProperty("java.class.path"),
                    System.out);
            System.out.println("Bundled mods: " + manifest.mods().size());
        } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
            System.err.println("Bundled mod packaging failed: " + failure.getMessage());
            System.exit(1);
        }
    }

    /** Comma- or whitespace-separated project list; blank entries are ignored. */
    static List<String> projects(String configured) {
        return Arrays.stream(Objects.requireNonNull(configured, "configured").split("[,\\s]+"))
                .map(String::strip).filter(value -> !value.isEmpty()).toList();
    }

    public static BundledModManifest packageProjects(Path basedir, Path jarOutput, Path manifestOutput,
                                                     List<String> projects, String classpath,
                                                     PrintStream log) throws IOException {
        Path base = basedir.toAbsolutePath().normalize();
        Path output = jarOutput.toAbsolutePath().normalize();
        Path work = output.resolveSibling(output.getFileName() + "-work");
        recreate(output);
        deleteTree(work);
        List<BundledModManifest.Entry> entries = new ArrayList<>();
        for (String project : projects) {
            Path root = base.resolve(project).normalize();
            if (!root.startsWith(base)) {
                throw new IllegalArgumentException("Bundled mod project must stay inside " + base + ": " + project);
            }
            Path resources = root.resolve("src/main/resources");
            if (!Files.isRegularFile(resources.resolve(MOD_MANIFEST), LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Bundled mod project has no " + MOD_MANIFEST + ": " + project);
            }
            Path classes = Files.createDirectories(work.resolve(root.getFileName().toString()).resolve("classes"));
            compile(root.resolve("src/main/java"), classes, classpath);
            copyTree(resources, classes);
            ModManifest manifest;
            try {
                manifest = new ModManifestParser(ModInputLimits.production())
                        .parse(Files.readAllBytes(classes.resolve(MOD_MANIFEST)));
            } catch (ModManifestException failure) {
                throw new IllegalArgumentException("Invalid manifest in " + project + ": " + failure.getMessage(), failure);
            }
            Path jar = output.resolve(manifest.id() + "-" + manifest.version() + ".jar");
            int status = GgfModCli.run(new String[]{"package", "--input", classes.toString(),
                    "--out", jar.toString(), "--warnings", "error"}, log);
            if (status != 0) {
                throw new IllegalStateException("ggfmod package rejected " + project + " (warnings are errors)");
            }
            byte[] bytes = Files.readAllBytes(jar);
            entries.add(new BundledModManifest.Entry(manifest.id(), manifest.version(),
                    jar.getFileName().toString(), sha256(bytes), bytes.length));
            log.println("Bundled " + manifest.id() + " " + manifest.version() + " from " + project);
        }
        BundledModManifest manifest = new BundledModManifest(entries);
        writeAtomically(manifestOutput.toAbsolutePath().normalize(), manifest.toJson());
        deleteTree(work);
        return manifest;
    }

    private static void compile(Path sources, Path classes, String classpath) throws IOException {
        if (!Files.isDirectory(sources, LinkOption.NOFOLLOW_LINKS)) return;
        List<String> files;
        try (Stream<Path> walk = Files.walk(sources)) {
            files = walk.filter(path -> path.toString().endsWith(".java")).map(Path::toString).sorted().toList();
        }
        if (files.isEmpty()) return;
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) throw new IllegalStateException("Bundled mod packaging requires a JDK compiler");
        List<String> arguments = new ArrayList<>(List.of("--release", "21", "-encoding", "UTF-8",
                "-cp", classpath, "-d", classes.toString()));
        arguments.addAll(files);
        if (javac.run(null, null, null, arguments.toArray(String[]::new)) != 0) {
            throw new IllegalStateException("javac failed for " + sources);
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.sorted().toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IllegalArgumentException("Bundled mod resources must not contain links: " + path);
                }
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void recreate(Path directory) throws IOException {
        Files.createDirectories(directory);
        try (Stream<Path> children = Files.list(directory)) {
            for (Path child : children.toList()) {
                if (child.getFileName().toString().endsWith(".jar")) Files.delete(child);
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    private static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path staging = Files.createTempFile(target.getParent(), ".bundled-mods-", ".tmp");
        try {
            Files.write(staging, bytes);
            try {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
