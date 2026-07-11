package com.openggf.tools.modsdk;

import com.openggf.game.ModApi;
import com.openggf.mods.code.ModApiSurfaceInventory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.DocumentationTool;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Generates Javadoc for exactly the engine's supported, annotated mod API inventory. */
public final class ModApiJavadocTool {
    private ModApiJavadocTool() { }

    /** Returns the canonical inventory in stable binary-name order. */
    public static List<Class<?>> annotatedTypes() {
        return requireAnnotatedInventory(ModApiSurfaceInventory.annotatedTypes());
    }

    /** Returns the canonical inventory names in stable binary-name order. */
    public static List<String> annotatedTypeNames() {
        return annotatedTypes().stream().map(Class::getName).toList();
    }

    /**
     * Validates an exact caller-supplied inventory and freezes it in stable order.
     * Unannotated types and duplicate binary names are rejected rather than silently
     * broadening or narrowing the documented compatibility surface.
     */
    public static List<Class<?>> requireAnnotatedInventory(Collection<Class<?>> inventory) {
        Objects.requireNonNull(inventory, "inventory");
        Map<String, Class<?>> byName = new LinkedHashMap<>();
        for (Class<?> type : inventory) {
            Objects.requireNonNull(type, "inventory type");
            if (!type.isAnnotationPresent(ModApi.class)) {
                throw new IllegalArgumentException("Javadoc inventory type lacks @ModApi: "
                        + type.getName());
            }
            if (byName.putIfAbsent(type.getName(), type) != null) {
                throw new IllegalArgumentException("Duplicate Javadoc inventory type: "
                        + type.getName());
            }
        }
        return byName.values().stream().sorted(Comparator.comparing(Class::getName)).toList();
    }

    /** Generates Javadoc for exactly {@code inventory}; no package scanning is performed. */
    public static void generate(Path sourceRoot, Path outputDirectory,
                                Collection<Class<?>> inventory) throws IOException {
        Path sources = normalizedDirectory(sourceRoot, "source root", true);
        Path output = Objects.requireNonNull(outputDirectory, "outputDirectory")
                .toAbsolutePath().normalize();
        if (Files.isDirectory(output)) {
            try (var entries = Files.list(output)) {
                if (entries.findAny().isPresent()) {
                    throw new IllegalArgumentException(
                            "Javadoc output directory must be empty: " + output);
                }
            }
        } else if (Files.exists(output)) {
            throw new IllegalArgumentException(
                    "Javadoc output path is not a directory: " + output);
        }
        Files.createDirectories(output);

        List<Class<?>> exact = requireAnnotatedInventory(inventory);
        if (exact.isEmpty()) {
            throw new IllegalArgumentException("Javadoc inventory must not be empty");
        }
        List<Path> sourceFiles = sourceFiles(sources, exact);
        DocumentationTool tool = ToolProvider.getSystemDocumentationTool();
        if (tool == null) {
            throw new IllegalStateException("A full JDK with the DocumentationTool is required");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StringWriter toolOutput = new StringWriter();
        try (StandardJavaFileManager files = tool.getStandardFileManager(
                diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromPaths(sourceFiles);
            List<String> options = List.of(
                    "-encoding", StandardCharsets.UTF_8.name(),
                    "-sourcepath", sources.toString(),
                    "-classpath", System.getProperty("java.class.path", ""),
                    "--release", Integer.toString(Runtime.version().feature()),
                    "-quiet");
            Set<String> canonicalNames = exact.stream()
                    .map(Class::getCanonicalName)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Boolean succeeded;
            ExactModApiDoclet.configure(canonicalNames, output);
            try {
                succeeded = tool.getTask(toolOutput, files, diagnostics,
                        ExactModApiDoclet.class, options, units).call();
            } finally {
                ExactModApiDoclet.clearConfiguration();
            }
            if (!Boolean.TRUE.equals(succeeded)) {
                throw new IllegalStateException(failureMessage(toolOutput, diagnostics));
            }
        }
    }

    /** Generates the canonical supported inventory. */
    public static void generate(Path sourceRoot, Path outputDirectory) throws IOException {
        generate(sourceRoot, outputDirectory, annotatedTypes());
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: ModApiJavadocTool <source-root> <output-directory>");
        }
        generate(Path.of(args[0]), Path.of(args[1]));
    }

    static List<Path> sourceFilesForTests(Path sourceRoot, Collection<Class<?>> inventory) {
        Path sources = normalizedDirectory(sourceRoot, "source root", true);
        return sourceFiles(sources, requireAnnotatedInventory(inventory));
    }

    private static List<Path> sourceFiles(Path sourceRoot, List<Class<?>> inventory) {
        Map<String, Path> bySourcePath = new java.util.TreeMap<>();
        for (Class<?> type : inventory) {
            String sourceFile = sourceFileAttribute(type);
            Path packagePath = type.getPackageName().isEmpty()
                    ? Path.of("") : Path.of(type.getPackageName().replace('.', '/'));
            Path source = sourceRoot.resolve(packagePath).resolve(sourceFile).normalize();
            if (!source.startsWith(sourceRoot) || !Files.isRegularFile(source)) {
                throw new IllegalArgumentException("Missing source for annotated type "
                        + type.getName() + ": " + source);
            }
            bySourcePath.put(source.toString(), source);
        }
        return List.copyOf(bySourcePath.values());
    }

    private static String sourceFileAttribute(Class<?> type) {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing classfile for annotated type: "
                        + type.getName());
            }
            String[] source = new String[1];
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public void visitSource(String value, String debug) {
                    source[0] = value;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            if (source[0] == null || source[0].isBlank()
                    || source[0].indexOf('/') >= 0 || source[0].indexOf('\\') >= 0) {
                throw new IllegalArgumentException("Classfile has no safe SourceFile attribute: "
                        + type.getName());
            }
            return source[0];
        } catch (IOException failure) {
            throw new IllegalArgumentException("Cannot read classfile for annotated type: "
                    + type.getName(), failure);
        }
    }

    private static Path normalizedDirectory(Path path, String label, boolean mustExist) {
        Path normalized = Objects.requireNonNull(path, label).toAbsolutePath().normalize();
        if (mustExist && !Files.isDirectory(normalized)) {
            throw new IllegalArgumentException(label + " is not a directory: " + normalized);
        }
        return normalized;
    }

    private static String failureMessage(StringWriter output,
            DiagnosticCollector<JavaFileObject> diagnostics) {
        List<String> details = new ArrayList<>();
        diagnostics.getDiagnostics().stream()
                .sorted(Comparator.comparing((Diagnostic<? extends JavaFileObject> diagnostic) ->
                                diagnostic.getSource() == null ? "" : diagnostic.getSource().toUri().toString())
                        .thenComparingLong(Diagnostic::getLineNumber)
                        .thenComparing(diagnostic -> diagnostic.getKind().name())
                        .thenComparing(diagnostic -> diagnostic.getMessage(Locale.ROOT)))
                .forEach(diagnostic -> details.add(formatDiagnostic(diagnostic)));
        String rawOutput = output.toString().strip();
        if (!rawOutput.isEmpty()) {
            details.add("tool-output: " + rawOutput);
        }
        return "Mod API Javadoc generation failed"
                + (details.isEmpty() ? "" : ":\n" + String.join("\n", details));
    }

    private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        String source = diagnostic.getSource() == null
                ? "<unknown>" : diagnostic.getSource().toUri().toString();
        return diagnostic.getKind() + " " + source + ":" + diagnostic.getLineNumber()
                + ":" + diagnostic.getColumnNumber() + " "
                + diagnostic.getMessage(Locale.ROOT);
    }
}
