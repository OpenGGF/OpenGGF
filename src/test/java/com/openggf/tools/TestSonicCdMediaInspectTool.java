package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.SyntheticProgramDisc;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class TestSonicCdMediaInspectTool {
    @TempDir Path directory;

    @Test void reportDescribesObservedAssetsAndDoesNotClaimGameplayOrCertification() throws IOException {
        Path cue = SyntheticProgramDisc.write(directory.resolve("media"), CdReleaseVariant.USA);
        try (var assets = CdProgramAssets.open(cue)) {
            var mapper = new ObjectMapper();
            byte[] first = mapper.writeValueAsBytes(SonicCdMediaInspectTool.facts(assets));
            byte[] second = mapper.writeValueAsBytes(SonicCdMediaInspectTool.facts(assets));
            assertArrayEquals(first, second);
            var report = mapper.readTree(first);
            assertEquals("observed_uncertified_media_assets", report.path("status").asText());
            assertEquals("USA", report.path("observedSourceVariant").asText());
            assertTrue(report.path("complete35TrackShape").asBoolean());
            assertEquals(35, report.path("tracks").size());
            assertEquals(102, report.path("mmdPrograms").size());
            assertEquals(127, report.path("isoFiles").size());
            assertTrue(report.path("provenance").path("originalContainer").isNull());
        }
    }

    @Test void cliRejectsInputOverwriteAndMalformedArgumentsBeforeWritingAnything() throws IOException {
        Path cue = SyntheticProgramDisc.write(directory.resolve("media"), CdReleaseVariant.JAPAN);
        byte[] original = Files.readAllBytes(cue);
        assertThrows(IOException.class, () -> SonicCdMediaInspectTool.main(new String[] {
                "--cue", cue.toString(), "--output", cue.toString()}));
        assertArrayEquals(original, Files.readAllBytes(cue));
        assertThrows(IllegalArgumentException.class, () -> SonicCdMediaInspectTool.main(new String[] {"--cue"}));
        assertThrows(IllegalArgumentException.class, () -> SonicCdMediaInspectTool.main(new String[] {"--unknown", "value"}));
        assertThrows(IllegalArgumentException.class, () -> SonicCdMediaInspectTool.main(new String[] {
                "--cue", cue.toString(), "--cue", cue.toString()}));
    }

    @ParameterizedTest
    @EnumSource(CdReleaseVariant.class)
    void cliAllowsExternalJsonUnderUnrelatedAncestorGit(CdReleaseVariant variant) throws IOException {
        Path home = Files.createDirectories(directory.resolve("home"));
        Files.createDirectory(home.resolve(".git"));
        Path cue = SyntheticProgramDisc.write(home.resolve("media"), variant);
        Path output = home.resolve("inspection.json");
        String[] arguments = {"--cue", cue.toString(), "--output", output.toString(),
                "--requested-variant", variant.name()};
        SonicCdMediaInspectTool.main(arguments);
        byte[] original = Files.readAllBytes(output);
        var report = new ObjectMapper().readTree(original);
        assertEquals(variant.name(), report.path("observedSourceVariant").asText());
        assertThrows(IOException.class, () -> SonicCdMediaInspectTool.main(arguments));
        assertArrayEquals(original, Files.readAllBytes(output));
    }

    @ParameterizedTest
    @EnumSource(CdReleaseVariant.class)
    void cliAllowsGeneratedTargetJson(CdReleaseVariant variant) throws IOException {
        Path cue = SyntheticProgramDisc.write(directory.resolve("media"), variant);
        Path checkout = Files.createDirectories(directory.resolve("checkout"));
        Files.writeString(checkout.resolve("pom.xml"), "<project/>");
        Files.createDirectories(checkout.resolve("src/main/java/com/openggf"));
        Path generated = Files.createDirectories(checkout.resolve("target/diagnostics"));
        Path output = generated.resolve("inspection.json");
        SonicCdMediaInspectTool.main(new String[] {"--cue", cue.toString(), "--output", output.toString(),
                "--requested-variant", variant.name()});
        var report = new ObjectMapper().readTree(Files.readAllBytes(output));
        assertEquals("observed_uncertified_media_assets", report.path("status").asText());
        assertEquals(variant.name(), report.path("observedSourceVariant").asText());
        assertEquals(35, report.path("tracks").size());
        assertEquals(102, report.path("mmdPrograms").size());
    }

    @Test void cliRejectsOpenGgfSourceRootAndSourceDirectories() throws IOException {
        Path cue = SyntheticProgramDisc.write(directory.resolve("media"), CdReleaseVariant.USA);
        Path checkout = Files.createDirectories(directory.resolve("checkout"));
        Files.writeString(checkout.resolve("pom.xml"), "<project/>");
        Files.createDirectories(checkout.resolve("src/main/java/com/openggf"));
        Files.writeString(checkout.resolve(".git"), "gitdir: elsewhere");
        for (Path parent : java.util.List.of(checkout, checkout.resolve("src/main/java/com/openggf"))) {
            Path output = parent.resolve("inspection.json");
            assertThrows(IOException.class, () -> SonicCdMediaInspectTool.main(new String[] {
                    "--cue", cue.toString(), "--output", output.toString()}));
            assertFalse(Files.exists(output));
        }
    }

    @Test void cliRejectsTargetSymlinkIntoSource() throws IOException {
        Path cue = SyntheticProgramDisc.write(directory.resolve("media"), CdReleaseVariant.USA);
        Path checkout = Files.createDirectories(directory.resolve("checkout"));
        Files.writeString(checkout.resolve("pom.xml"), "<project/>");
        Path source = Files.createDirectories(checkout.resolve("src/main/java/com/openggf"));
        Path target = Files.createSymbolicLink(checkout.resolve("target"), source.toAbsolutePath());
        assertThrows(IOException.class, () -> SonicCdMediaInspectTool.main(new String[] {
                "--cue", cue.toString(), "--output", target.resolve("inspection.json").toString()}));
        assertFalse(Files.exists(source.resolve("inspection.json")));
    }

    @Test void cliAllowsNearestCheckoutTargetInsideAnOuterWorktreeDirectory() throws IOException {
        Path cue = SyntheticProgramDisc.write(directory.resolve("media"), CdReleaseVariant.USA);
        Path outer = Files.createDirectories(directory.resolve("outer"));
        Path nested = Files.createDirectories(outer.resolve(".worktrees/nested"));
        for (Path checkout : java.util.List.of(outer, nested)) {
            Files.writeString(checkout.resolve("pom.xml"), "<project/>");
            Files.createDirectories(checkout.resolve("src/main/java/com/openggf"));
        }
        Path generated = Files.createDirectory(nested.resolve("target"));
        Path output = generated.resolve("inspection.json");
        SonicCdMediaInspectTool.main(new String[] {
                "--cue", cue.toString(), "--output", output.toString()});
        assertEquals("USA", new ObjectMapper().readTree(Files.readAllBytes(output))
                .path("observedSourceVariant").asText());
    }

    @Test void cliRejectsNearestCheckoutSourceInsideAnOuterTargetDirectory() throws IOException {
        Path cue = SyntheticProgramDisc.write(directory.resolve("media"), CdReleaseVariant.USA);
        Path outer = Files.createDirectories(directory.resolve("outer"));
        Path nested = Files.createDirectories(outer.resolve("target/nested"));
        for (Path checkout : java.util.List.of(outer, nested)) {
            Files.writeString(checkout.resolve("pom.xml"), "<project/>");
            Files.createDirectories(checkout.resolve("src/main/java/com/openggf"));
        }
        Path output = nested.resolve("src/inspection.json");
        assertThrows(IOException.class, () -> SonicCdMediaInspectTool.main(new String[] {
                "--cue", cue.toString(), "--output", output.toString()}));
        assertFalse(Files.exists(output));
    }

    @Test void cliRejectsSymlinkedOutputParentsIntoSourceCheckoutOrMedia() throws IOException {
        Path cue = SyntheticProgramDisc.write(directory.resolve("media"), CdReleaseVariant.USA);
        Path checkout = Files.createDirectories(directory.resolve("checkout"));
        Files.writeString(checkout.resolve("pom.xml"), "<project/>");
        Files.createDirectories(checkout.resolve("src/main/java/com/openggf"));
        for (Path destination : java.util.List.of(checkout, cue.getParent())) {
            Path link = directory.resolve(destination.getFileName() + "-link");
            Files.createSymbolicLink(link, destination.toAbsolutePath());
            Path output = link.resolve("inspection.json");
            assertThrows(IOException.class, () -> SonicCdMediaInspectTool.main(new String[] {
                    "--cue", cue.toString(), "--output", output.toString()}));
            assertFalse(Files.exists(destination.resolve("inspection.json")));
        }
    }

    @Test void realMainWritesExplicitExternalJsonAndRefusesToReplaceIt() throws IOException {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for real inspection CLI");
        Path media = Path.of(root).toRealPath();
        Path cue;
        try (var paths = Files.list(media.resolve("USA"))) {
            var cues = paths.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            cue = cues.getFirst();
        }
        Path outputDirectory = Files.createTempDirectory(media.getParent(), "inspection-test-");
        Path output = outputDirectory.resolve("inspection.json");
        try {
            String[] arguments = {"--cue", cue.toString(), "--output", output.toString(), "--requested-variant", "USA"};
            SonicCdMediaInspectTool.main(arguments);
            var report = new ObjectMapper().readTree(Files.readAllBytes(output));
            assertEquals("USA", report.path("observedSourceVariant").asText());
            assertEquals(35, report.path("tracks").size());
            assertEquals(102, report.path("mmdPrograms").size());
            assertThrows(IOException.class, () -> SonicCdMediaInspectTool.main(arguments));
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(outputDirectory);
        }
    }
}
