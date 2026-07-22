package com.openggf.mods;

import com.openggf.io.ModInputLimits;
import com.openggf.mods.code.BakedSheetRef;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestModManifestParser {
    @Test
    void canonicalPatchManifestRetainsDisplayDependencyAndOverrideMetadata() throws Exception {
        ModManifest manifest = parser().parse(canonicalPatch().getBytes(StandardCharsets.UTF_8));

        assertEquals(1, manifest.formatVersion());
        assertEquals("example-music", manifest.id());
        assertEquals("Example Music Pack", manifest.name());
        assertEquals(new SemanticVersion(1, 0, 0), manifest.version());
        assertEquals(List.of("Example Author", "Second Author"), manifest.authors());
        assertEquals("Replaces one stock track.", manifest.description());
        assertTrue(manifest.engineApiRange().contains(ModApiVersion.CURRENT));
        assertEquals(ModType.PATCH, manifest.type());
        assertEquals("s2", manifest.baseGame());
        assertNull(manifest.entrypoint());
        assertEquals(List.of(new ModDependency("shared-library", VersionRange.parse(">=1.2.0 <2.0.0"))),
                manifest.dependencies());
        assertEquals(Map.of(12, "boss-remix"), manifest.audioOverrides());
        assertEquals(Map.of(), manifest.artOverrides());
        assertNull(manifest.insertAfter());
        assertEquals(OptionalInt.empty(), manifest.patternWindows());
        assertThrows(UnsupportedOperationException.class, () -> manifest.authors().add("mutate"));
        assertThrows(UnsupportedOperationException.class, () -> manifest.audioOverrides().put(1, "mutate"));
    }

    @Test
    void standaloneForbidsBaseGameAndPatchRequiresOneCanonicalBase() throws Exception {
        String standalone = canonicalPatch()
                .replace("type: patch\nbaseGame: s2\n", "type: standalone\n");
        ModManifest parsed = parser().parse(bytes(standalone));
        assertEquals(ModType.STANDALONE, parsed.type());
        assertNull(parsed.baseGame());

        rejects(canonicalPatch().replace("baseGame: s2\n", ""));
        rejects(standalone.replace("type: standalone\n", "type: standalone\nbaseGame: s1\n"));
        for (String base : List.of("S2", "s4", "", "s2,s3k")) {
            rejects(canonicalPatch().replace("baseGame: s2", "baseGame: " + base));
        }
    }

    @Test
    void optionalManifestV1PhaseTwoFieldsRetainTheirStrictV1Shapes() throws Exception {
        String yaml = canonicalPatch()
                + "entrypoint: com.example.ExampleMod$Nested\n"
                + "insertAfter: cpz2\n"
                + "patternWindows: 16\n";
        yaml = yaml.replace("artOverrides: {}", "artOverrides:\n  stock-art: assets/art.bin");
        ModManifest manifest = parser().parse(bytes(yaml));

        assertEquals("com.example.ExampleMod$Nested", manifest.entrypoint());
        assertEquals("cpz2", manifest.insertAfter());
        assertEquals(OptionalInt.of(16), manifest.patternWindows());
        assertEquals(Map.of("stock-art", "assets/art.bin"), manifest.artOverrides());
        assertEquals(Map.of("stock-art", new BakedSheetRef("assets/art.bin")),
                manifest.artOverrides().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> new BakedSheetRef(entry.getValue()))));

        rejects(yaml.replace("patternWindows: 16", "patternWindows: 0"));
        rejects(yaml.replace("patternWindows: 16", "patternWindows: 17"));
        assertEquals(OptionalInt.of(1), parser().parse(bytes(
                yaml.replace("patternWindows: 16", "patternWindows: 1"))).patternWindows());
        rejects(yaml.replace("com.example.ExampleMod$Nested", "not a class"));
        rejects(yaml.replace("insertAfter: cpz2", "insertAfter: CPZ2"));
        rejects(yaml.replace("assets/art.bin", "../art.bin"));
    }

    @Test
    void requiredCollectionsAreExplicitMapsAndDependenciesAreObjectsOnly() {
        rejects(canonicalPatch().replace("dependencies:\n  - id: shared-library\n    versionRange: \">=1.2.0 <2.0.0\"",
                "dependencies: [shared-library]"));
        rejects(canonicalPatch().replace("audioOverrides:\n  12: boss-remix", "audioOverrides: []"));
        rejects(canonicalPatch().replace("artOverrides: {}", "artOverrides: []"));
        rejects(canonicalPatch().replace("audioOverrides:\n  12: boss-remix", "audioOverrides:\n  - 12: boss-remix"));
        rejects(canonicalPatch().replace("dependencies:\n  - id: shared-library\n    versionRange: \">=1.2.0 <2.0.0\"",
                "dependencies:\n  - id: shared-library"));
    }

    @Test
    void invalidFormatIdentityTypeAndScalarBoundsAreRejected() {
        rejects(canonicalPatch().replace("formatVersion: 1", "formatVersion: 2"));
        rejects(canonicalPatch().replace("formatVersion: 1", "formatVersion: \"1\""));
        for (String id : List.of("Example", "-bad", "bad_id", "a".repeat(65))) {
            rejects(canonicalPatch().replace("id: example-music", "id: " + id));
        }
        rejects(canonicalPatch().replace("type: patch", "type: PATCH"));
        rejects(canonicalPatch().replace("type: patch", "type: music"));
        rejects(canonicalPatch().replace("name: Example Music Pack", "name: '   '"));
        rejects(canonicalPatch().replace("description: Replaces one stock track.", "description: ''"));
        rejects(canonicalPatch().replace("  - Example Author\n  - Second Author", "[]"));
        rejects(canonicalPatch().replace("  - Example Author\n  - Second Author",
                java.util.stream.IntStream.range(0, 33).mapToObj(i -> "  - Author " + i)
                        .collect(java.util.stream.Collectors.joining("\n"))));
    }

    @Test
    void thirtyTwoAuthorsAreAcceptedAtTheInclusiveBoundary() throws Exception {
        String authors = java.util.stream.IntStream.range(0, 32)
                .mapToObj(i -> "  - Author " + i)
                .collect(java.util.stream.Collectors.joining("\n"));
        ModManifest manifest = parser().parse(bytes(canonicalPatch().replace(
                "  - Example Author\n  - Second Author", authors)));
        assertEquals(32, manifest.authors().size());
    }

    @Test
    void publicManifestRecordCannotBypassCanonicalIdentityAndMapValidation() throws Exception {
        ModManifest valid = parser().parse(bytes(canonicalPatch()));
        assertThrows(IllegalArgumentException.class, () -> new ModManifest(
                1, "Invalid", valid.name(), valid.version(), valid.authors(), valid.description(),
                valid.engineApiRange(), valid.type(), valid.baseGame(), valid.entrypoint(),
                valid.dependencies(), valid.audioOverrides(), valid.artOverrides(),
                valid.insertAfter(), valid.patternWindows()));
        assertThrows(IllegalArgumentException.class, () -> new ModManifest(
                1, valid.id(), valid.name(), valid.version(), List.of(" "), valid.description(),
                valid.engineApiRange(), valid.type(), valid.baseGame(), valid.entrypoint(),
                valid.dependencies(), Map.of(-1, "boss-remix"), valid.artOverrides(),
                valid.insertAfter(), valid.patternWindows()));
        ModDependency dependency = valid.dependencies().getFirst();
        assertThrows(IllegalArgumentException.class, () -> new ModManifest(
                1, valid.id(), valid.name(), valid.version(), valid.authors(), valid.description(),
                valid.engineApiRange(), valid.type(), valid.baseGame(), valid.entrypoint(),
                List.of(dependency, dependency), valid.audioOverrides(), valid.artOverrides(),
                valid.insertAfter(), valid.patternWindows()));
        assertThrows(IllegalArgumentException.class, () -> new ModManifest(
                1, valid.id(), valid.name(), valid.version(), valid.authors(), valid.description(),
                valid.engineApiRange(), valid.type(), valid.baseGame(), "a." + "b".repeat(65_536),
                valid.dependencies(), valid.audioOverrides(), valid.artOverrides(),
                valid.insertAfter(), valid.patternWindows()));
        assertThrows(IllegalArgumentException.class, () -> new ModManifest(
                1, valid.id(), valid.name(), valid.version(), valid.authors(), valid.description(),
                valid.engineApiRange(), valid.type(), valid.baseGame(), valid.entrypoint(),
                valid.dependencies(), valid.audioOverrides(), Map.of("stock", "assets/" + "é".repeat(253)),
                valid.insertAfter(), valid.patternWindows()));
    }

    @Test
    void invalidVersionsDependenciesAndOverrideKeysAreRejected() {
        rejects(canonicalPatch().replace("version: 1.0.0", "version: 01.0.0"));
        rejects(canonicalPatch().replace("version: 1.0.0", "version: 1.0.0-beta"));
        rejects(canonicalPatch().replace("engineApiRange: \">=0.7.0 <0.8.0\"",
                "engineApiRange: \">=2.0.0 <1.0.0\""));
        rejects(canonicalPatch().replace("id: shared-library", "id: Shared"));
        rejects(canonicalPatch().replace("versionRange: \">=1.2.0 <2.0.0\"", "versionRange: ^1.2.0"));
        rejects(canonicalPatch().replace("  12: boss-remix", "  -1: boss-remix"));
        rejects(canonicalPatch().replace("  12: boss-remix", "  01: boss-remix"));
        rejects(canonicalPatch().replace("  12: boss-remix", "  2147483648: boss-remix"));
        rejects(canonicalPatch().replace("boss-remix", "Boss-Remix"));
    }

    @Test
    void unknownDuplicateNullAliasMergeTagAndTrailingDocumentsAreRejected() {
        rejects(canonicalPatch() + "unknown: true\n");
        rejects(canonicalPatch() + "id: duplicate\n");
        rejects(canonicalPatch().replace("description: Replaces one stock track.", "description: null"));
        rejects(canonicalPatch().replace("name: Example Music Pack", "name: &display Example Music Pack")
                .replace("description: Replaces one stock track.", "description: *display"));
        rejects(canonicalPatch().replace("authors:\n  - Example Author\n  - Second Author",
                "authors: &author-list\n  - Example Author\n  - Second Author"));
        rejects(canonicalPatch().replace("audioOverrides:\n  12: boss-remix",
                "audioOverrides: &override-map\n  12: boss-remix"));
        rejects(canonicalPatch().replace("dependencies:", "defaults: &defaults\n  id: shared-library\n  versionRange: '*'\ndependencies:")
                .replace("  - id: shared-library\n    versionRange: \">=1.2.0 <2.0.0\"", "  - <<: *defaults"));
        rejects(canonicalPatch().replace("name: Example Music Pack", "name: !!str Example Music Pack"));
        rejects(canonicalPatch() + "---\nid: second\n");
        rejects(canonicalPatch().replace("    versionRange: \">=1.2.0 <2.0.0\"",
                "    versionRange: '*'\n    extra: no"));
        rejects(canonicalPatch().replace("artOverrides: {}", "artOverrides:\n  <<: assets/art.bin"));
    }

    @Test
    void loweredHostileInputLimitsApplyBeforeAndDuringParsing() throws Exception {
        ModInputLimits byteLimit = ModInputLimits.loweringBuilder().maxMetadataBytes(128).build();
        assertThrows(ModManifestException.class, () -> new ModManifestParser(byteLimit).parse(bytes(canonicalPatch())));

        ModInputLimits stringLimit = ModInputLimits.loweringBuilder().maxStringChars(64).build();
        String longDescription = canonicalPatch().replace("Replaces one stock track.", "x".repeat(65));
        assertThrows(ModManifestException.class, () -> new ModManifestParser(stringLimit).parse(bytes(longDescription)));

        ModInputLimits numericLimit = ModInputLimits.loweringBuilder().maxNumericDigits(2).build();
        String threeDigitKey = canonicalPatch().replace("  12: boss-remix", "  123: boss-remix");
        assertThrows(ModManifestException.class, () -> new ModManifestParser(numericLimit).parse(bytes(threeDigitKey)));
        for (String numeric : List.of("123.4", "1e123", "0xABCD", "1_234")) {
            String numericFormat = canonicalPatch().replace("formatVersion: 1", "formatVersion: " + numeric);
            ModManifestException error = assertThrows(ModManifestException.class,
                    () -> new ModManifestParser(numericLimit).parse(bytes(numericFormat)));
            assertTrue(error.getMessage().contains("numeric token"), numeric);
        }

        ModInputLimits collectionLimit = ModInputLimits.loweringBuilder().maxCollectionEntries(12).build();
        String manyDependencies = canonicalPatch().replace(
                "  - id: shared-library\n    versionRange: \">=1.2.0 <2.0.0\"",
                java.util.stream.IntStream.range(0, 13)
                        .mapToObj(i -> "  - id: dep-" + i + "\n    versionRange: '*'" )
                        .collect(java.util.stream.Collectors.joining("\n")));
        assertThrows(ModManifestException.class,
                () -> new ModManifestParser(collectionLimit).parse(bytes(manyDependencies)));

        ModInputLimits codePointLimit = ModInputLimits.loweringBuilder().maxDocumentCodePoints(256).build();
        assertThrows(ModManifestException.class,
                () -> new ModManifestParser(codePointLimit).parse(bytes(canonicalPatch())));

        ModInputLimits depthLimit = ModInputLimits.loweringBuilder().maxYamlDepth(2).build();
        assertThrows(ModManifestException.class,
                () -> new ModManifestParser(depthLimit).parse(bytes(canonicalPatch())));

        ModInputLimits pathLimit = ModInputLimits.loweringBuilder().maxEntryNameBytes(24).build();
        String multibytePath = canonicalPatch().replace(
                "artOverrides: {}", "artOverrides:\n  stock: assets/" + "é".repeat(9));
        assertThrows(ModManifestException.class,
                () -> new ModManifestParser(pathLimit).parse(bytes(multibytePath)));
    }

    @Test
    void parserSourceBuildsConstrainedFactoryRatherThanUnconstrainedYamlMapper() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/openggf/mods/ModManifestParser.java"));
        assertTrue(source.contains("StreamReadConstraints"));
        assertTrue(source.contains("LoaderOptions"));
        assertTrue(source.contains("STRICT_DUPLICATE_DETECTION"));
        assertTrue(!source.contains("new YAMLMapper()"));
    }

    private static ModManifestParser parser() {
        return new ModManifestParser(ModInputLimits.production());
    }

    private static void rejects(String yaml) {
        assertThrows(ModManifestException.class, () -> parser().parse(bytes(yaml)), yaml);
    }

    private static byte[] bytes(String yaml) {
        return yaml.getBytes(StandardCharsets.UTF_8);
    }

    private static String canonicalPatch() {
        return """
                formatVersion: 1
                id: example-music
                name: Example Music Pack
                version: 1.0.0
                authors:
                  - Example Author
                  - Second Author
                description: Replaces one stock track.
                engineApiRange: ">=0.7.0 <0.8.0"
                type: patch
                baseGame: s2
                dependencies:
                  - id: shared-library
                    versionRange: ">=1.2.0 <2.0.0"
                audioOverrides:
                  12: boss-remix
                artOverrides: {}
                """;
    }
}
