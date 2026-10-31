package com.openggf.tools.modsdk;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class TestDiagnosticReports {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private JsonNode report(int exit, String... args) throws Exception {
        return report(exit, new ModJarValidator()::validate, args);
    }

    private JsonNode report(int exit, Function<Path, ModJarValidator.Report> validator, String... args) throws Exception {
        var bytes = new ByteArrayOutputStream();
        assertEquals(exit, GgfModCli.run(args, new PrintStream(bytes, true, StandardCharsets.UTF_8), validator), bytes::toString);
        JsonNode result = mapper.readTree(bytes.toByteArray());
        assertEquals(1, result.path("formatVersion").asInt());
        assertTrue(result.has("valid"));
        assertTrue(result.has("warnings"));
        assertTrue(result.has("warningPolicy"));
        assertTrue(result.path("findings").isArray());
        return result;
    }

    @Test void missingMalformedPathsFlagsAndOperationalFailuresStayJson() throws Exception {
        for (String[] args : List.of(
                new String[]{"validate", temp.resolve("missing.jar").toString(), "--format", "json"},
                new String[]{"validate", "bad\0path", "--format", "json"},
                new String[]{"validate", "--format", "json"},
                new String[]{"validate", "x.jar", "--format", "json", "--warnings", "bogus"},
                new String[]{"validate", "x.jar", "--format", "json", "--format", "text"},
                new String[]{"package", "--format", "json"},
                new String[]{"package", "--input", "bad\0path", "--out", "x.jar", "--format", "json"},
                new String[]{"package", "--input", temp.resolve("missing").toString(), "--out", temp.resolve("x.jar").toString(), "--format", "json"},
                new String[]{"package", "--format", "json", "--unknown"})) {
            JsonNode result = report(1, args);
            assertFalse(result.path("valid").asBoolean());
            assertEquals("ERROR", result.path("findings").get(0).path("severity").asText());
            assertFalse(result.path("findings").get(0).path("guidance").path("nextAction").asText().isBlank());
        }
        var result = report(1, ignored -> { throw new IllegalStateException("failure \"quoted\"\n\\path\t"); },
                "validate", "mod.jar", "--format", "json");
        assertEquals("VALIDATION_FAILED", result.path("findings").get(0).path("code").asText());
        assertEquals("failure \"quoted\"\n\\path\t", result.path("findings").get(0).path("message").asText());
    }

    @Test void additiveGuidancePreservesEveryOriginalFieldAndUnknownCodesRemainHonest() throws Exception {
        String location = "example/\"Entry\\Class\n.class";
        var findings = List.of(new ModJarValidator.Finding(ModJarValidator.Severity.WARNING,
                "FUTURE_CODE", location, "member\t", "Original \"message\"\n"));
        var result = report(0, ignored -> new ModJarValidator.Report(findings), "validate", "x.jar", "--format", "json");
        var finding = result.path("findings").get(0);
        var original = mapper.valueToTree(findings.get(0));
        original.fields().forEachRemaining(field -> assertEquals(field.getValue(), finding.get(field.getKey())));
        assertFalse(finding.path("guidance").has("supportedReplacement"));
        assertTrue(finding.path("guidance").path("nextAction").asText().contains("no code-specific remedy"));
        assertEquals(result, report(0, ignored -> new ModJarValidator.Report(findings), "validate", "x.jar", "--format", "json"));
        var strict = report(1, ignored -> new ModJarValidator.Report(findings), "validate", "x.jar", "--format", "json", "--warnings", "error");
        assertTrue(strict.path("valid").asBoolean());
        assertEquals(1, strict.path("warnings").asInt());
    }

    @Test void packageFilesystemIoFailureRemainsAJsonReport() throws Exception {
        Path input = Files.createDirectory(temp.resolve("input"));
        Path blocker = Files.writeString(temp.resolve("file"), "not a directory");
        var failed = report(1, "package", "--input", input.toString(), "--out",
                blocker.resolve("out.jar").toString(), "--format", "json");
        assertFalse(failed.path("valid").asBoolean());
        assertEquals("COMMAND_FAILED", failed.path("findings").get(0).path("code").asText());
        assertEquals("not a directory", Files.readString(blocker));
    }

    @Test void successfulPackageAndValidateEmitOnlyDeterministicReports() throws Exception {
        Path input = Files.createDirectories(temp.resolve("valid/META-INF")).getParent();
        Files.writeString(input.resolve("META-INF/openggf-mod.yaml"), """
                formatVersion: 1
                id: example
                name: Example
                version: 1.0.0
                authors: [Author]
                description: Fixture
                engineApiRange: ">=0.7.0 <0.8.0"
                type: patch
                baseGame: s2
                dependencies: []
                audioOverrides: {}
                artOverrides: {}
                """);
        Path jar = temp.resolve("success.jar");
        var packaged = report(0, "package", "--input", input.toString(), "--out", jar.toString(), "--format", "json");
        assertTrue(Files.isRegularFile(jar));
        assertTrue(packaged.path("valid").asBoolean());
        assertEquals(0, packaged.path("findings").size());
        assertEquals(packaged, report(0, "validate", jar.toString(), "--format", "json"));
    }

    @Test void rejectedPackageDoesNotPublishAndOperationalFailurePreservesExistingOutput() throws Exception {
        Path input = Files.createDirectory(temp.resolve("empty"));
        Path jar = temp.resolve("rejected.jar");
        var result = report(1, "package", "--input", input.toString(), "--out", jar.toString(), "--format", "json");
        assertEquals("MANIFEST_MISSING", result.path("findings").get(0).path("code").asText());
        assertFalse(Files.exists(jar));
        Files.writeString(jar, "existing");
        var failed = report(1, "package", "--input", input.toString(), "--out", jar.toString(), "--format", "json");
        assertEquals("COMMAND_FAILED", failed.path("findings").get(0).path("code").asText());
        assertEquals("existing", Files.readString(jar));
        try (var files = Files.list(temp)) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith(".ggfmod-package-")));
        }
    }
}
