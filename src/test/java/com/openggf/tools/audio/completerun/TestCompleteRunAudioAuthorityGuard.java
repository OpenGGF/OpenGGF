package com.openggf.tools.audio.completerun;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestCompleteRunAudioAuthorityGuard {
    private static final Path MAIN_ROOT =
            Path.of("src/main/java/com/openggf");
    private static final List<Path> COMPLETE_RUN_ROOTS = List.of(
            Path.of("src/main/java/com/openggf/tools/audio/completerun"),
            Path.of("src/test/java/com/openggf/tools/audio/completerun"));
    private static final String TOOLING_PACKAGE =
            "com.openggf.tools.audio.completerun";
    private static final Pattern REFERENCE_AUTHORITY_PARAMETER =
            Pattern.compile("(?i)\\b(reference|expected|oracle|sidecar)"
                    + "[a-z0-9_]*\\b");

    @TempDir
    Path temporaryDirectory;

    @Test
    void productionOutsideToolsCannotImportCompleteRunCaptureAuthority()
            throws IOException {
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(MAIN_ROOT)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.startsWith(
                            MAIN_ROOT.resolve("tools")))
                    .forEach(path -> inspectForbiddenReference(
                            path, violations));
        }

        assertEquals(List.of(), violations,
                "production behavior must not depend on complete-run tooling");
    }

    @Test
    void openGgfProducersCannotAcceptReferenceReadersOrArtifacts()
            throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path root : COMPLETE_RUN_ROOTS) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var files = Files.walk(root)) {
                files.filter(path -> {
                            String name = path.getFileName().toString();
                            return name.endsWith(".java")
                                    && name.contains("OpenGgf");
                        })
                        .forEach(path -> inspectProducerAuthority(
                                path, violations));
            }
        }

        assertEquals(List.of(), violations,
                "OpenGGF producers may accept ROM/run/output identity, never"
                        + " reference capture authority");
    }

    @Test
    void producerGuardReadsConstructorParametersNotComments()
            throws IOException {
        Path producer = temporaryDirectory.resolve(
                "CompleteRunOpenGgfCapture.java");
        Files.writeString(producer, """
                final class CompleteRunOpenGgfCapture {
                    // A reference capture is comparison-only.
                    CompleteRunOpenGgfCapture(java.nio.file.Path output) { }
                }
                """);
        List<String> violations = new ArrayList<>();
        inspectProducerAuthority(producer, violations);
        assertEquals(List.of(), violations);

        Files.writeString(producer, """
                final class CompleteRunOpenGgfCapture {
                    CompleteRunOpenGgfCapture(
                            java.nio.file.Path referenceCapturePath) { }
                }
                """);
        inspectProducerAuthority(producer, violations);
        assertEquals(1, violations.size());
    }

    private static void inspectForbiddenReference(
            Path path, List<String> violations) {
        try {
            String source = Files.readString(path);
            if (source.contains(TOOLING_PACKAGE)) {
                violations.add(path.toString());
            }
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void inspectProducerAuthority(
            Path path, List<String> violations) {
        try {
            String source = Files.readString(path);
            String name = path.getFileName().toString();
            String simpleName = name.substring(0,
                    name.length() - ".java".length());
            Pattern constructor = Pattern.compile(
                    "\\b" + Pattern.quote(simpleName)
                            + "\\s*\\(([^)]*)\\)",
                    Pattern.DOTALL);
            var constructors = constructor.matcher(source);
            while (constructors.find()) {
                var parameter = REFERENCE_AUTHORITY_PARAMETER.matcher(
                        constructors.group(1));
                if (parameter.find()) {
                    violations.add(path + ":"
                            + parameter.group().toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
