package com.openggf.mods;

import com.openggf.game.sonic1.audio.Sonic1Music;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TestModCatalogValidator {
    private static final ModCatalogValidator.StockMusicDomain STOCK_MUSIC_DOMAIN = (baseGame, id) -> switch (baseGame) {
        case "s1" -> Sonic1Music.titleMap().containsKey(id);
        case "s2" -> Sonic2Music.titleMap().containsKey(id);
        case "s3k" -> Sonic3kMusic.titleMap().containsKey(id);
        default -> false;
    };

    @TempDir Path temp;

    @Test
    void validPackedAudioRegistersTracksAndMissingManifestIsOptionalWithoutOverrides() throws Exception {
        ModDescriptor valid = descriptor("valid", "s1", Map.of(0x81, "music"), Map.of(
                "audio/audio-manifest.yaml", bytes(audio("music", "audio/music.ogg", false)),
                "audio/music.ogg", new byte[] {1, 2}));
        ModDescriptor quiet = descriptor("quiet", "s2", Map.of(), Map.of());

        ModCatalogValidator.ValidationResult result = validator().validate(List.of(valid, quiet));

        assertTrue(result.entries().stream().filter(ModDescriptor.class::isInstance)
                .map(ModDescriptor.class::cast).noneMatch(ModDescriptor::hasErrors));
        assertTrue(result.registry().find(new TrackKey("valid", "music")).isPresent());
        assertEquals(1, result.registry().tracks().size());
        assertThrows(UnsupportedOperationException.class, () -> result.entries().clear());
    }

    @Test
    void staticFailuresAreOwnedStructuredAndDoNotStopFollowingJars() throws Exception {
        ModDescriptor missingManifest = descriptor("missing-manifest", "s1", Map.of(0x81, "music"), Map.of());
        ModDescriptor missingAsset = descriptor("missing-asset", "s1", Map.of(0x81, "music"), Map.of(
                "audio/audio-manifest.yaml", bytes(audio("music", "audio/missing.ogg", false))));
        ModDescriptor withSfx = descriptor("with-sfx", "s1", Map.of(), Map.of(
                "audio/audio-manifest.yaml", bytes(audio("music", "audio/music.ogg", true)),
                "audio/music.ogg", new byte[] {1}, "audio/hit.wav", new byte[] {2}));
        ModDescriptor valid = descriptor("following", "s1", Map.of(0x81, "music"), Map.of(
                "audio/audio-manifest.yaml", bytes(audio("music", "audio/music.ogg", false)),
                "audio/music.ogg", new byte[] {1}));

        ModCatalogValidator.ValidationResult result = validator().validate(
                List.of(missingManifest, missingAsset, withSfx, valid));

        assertTrue(codes(result, "missing-manifest").contains("AUDIO_MANIFEST_MISSING"));
        assertTrue(codes(result, "missing-asset").contains("AUDIO_ASSET_MISSING"));
        assertTrue(codes(result, "with-sfx").contains("SFX_UNSUPPORTED_PHASE1"));
        assertTrue(result.registry().find(new TrackKey("following", "music")).isPresent());
        assertTrue(result.registry().find(new TrackKey("missing-asset", "music")).isEmpty());
    }

    @Test
    void standaloneSfxIsEligibleWhilePatchSfxRemainsDeferred() throws Exception {
        Map<String, byte[]> entries = Map.of(
                "audio/audio-manifest.yaml", bytes(audio("music", "audio/music.ogg", true)),
                "audio/music.ogg", new byte[] {1}, "audio/hit.wav", new byte[] {2});
        ModDescriptor standalone = descriptor("standalone-sfx", ModType.STANDALONE, null,
                Map.of(), entries);
        ModDescriptor patch = descriptor("patch-sfx", "s1", Map.of(), entries);

        ModCatalogValidator.ValidationResult result = validator().validate(List.of(standalone, patch));

        assertFalse(descriptor(result, "standalone-sfx").hasErrors());
        assertTrue(result.sfxRegistry().find(new SfxKey("standalone-sfx", "hit")).isPresent());
        assertTrue(codes(result, "patch-sfx").contains("SFX_UNSUPPORTED_PHASE1"));
        assertTrue(result.sfxRegistry().find(new SfxKey("patch-sfx", "hit")).isEmpty());
    }

    @Test
    void overrideDomainStandaloneDigestAndCollisionRulesAreEnforced() throws Exception {
        ModDescriptor first = descriptor("first", "s1", Map.of(0x81, "music"), audioEntries("music"));
        ModDescriptor second = descriptor("second", "s1", Map.of(0x81, "music"), audioEntries("music"));
        ModDescriptor wrongDomain = descriptor("wrong-domain", "s1", Map.of(Integer.MAX_VALUE, "music"), audioEntries("music"));
        ModDescriptor changed = descriptor("changed", "s2", Map.of(), Map.of());
        writeJar(changed.jarPath(), Map.of(
                "META-INF/openggf-mod.yaml", new byte[] {1}, "extra.bin", new byte[] {9}));

        ModCatalogValidator.ValidationResult result = validator().validate(
                List.of(first, second, wrongDomain, changed));

        assertEquals(List.of("AUDIO_OVERRIDE_CONFLICT"), warningCodes(result, "first"));
        String message = descriptor(result, "first").findings().stream()
                .filter(f -> f.code().equals("AUDIO_OVERRIDE_CONFLICT")).findFirst().orElseThrow().message();
        assertTrue(message.contains("first") && message.contains("second"));
        assertTrue(codes(result, "wrong-domain").contains("AUDIO_OVERRIDE_ID_INVALID"));
        assertTrue(codes(result, "changed").contains("MOD_JAR_CHANGED"));
    }

    @Test
    void invalidAndRepositoryEntriesPassThroughUnchanged() throws Exception {
        InvalidModEntry invalid = new InvalidModEntry(temp.resolve("bad.jar"), List.of(
                new ModFinding(ModFindingSeverity.ERROR, "BAD_JAR", "bad", null)));
        RepositoryScanFailure failure = new RepositoryScanFailure(temp, List.of(
                new ModFinding(ModFindingSeverity.ERROR, "BAD_ROOT", "bad", null)));
        List<ModCatalogEntry> entries = validator().validate(List.of(invalid, failure)).entries();
        assertSame(invalid, entries.get(0));
        assertSame(failure, entries.get(1));
    }

    @Test
    void repositoryCountAndAggregateBudgetsFailBeforeAnyJarValidation() throws Exception {
        ModDescriptor one = descriptor("budget-one", "s1", Map.of(), Map.of());
        ModDescriptor two = descriptor("budget-two", "s1", Map.of(), Map.of());
        ModInputLimits count = ModInputLimits.loweringBuilder().maxModJars(1).build();
        ModCatalogValidator.ValidationResult countResult = validator(count).validate(List.of(one, two));
        assertTrue(codes(countResult, "budget-one").contains("REPOSITORY_JAR_LIMIT_EXCEEDED"));
        assertTrue(codes(countResult, "budget-two").contains("REPOSITORY_JAR_LIMIT_EXCEEDED"));
        assertTrue(countResult.registry().tracks().isEmpty());

        long total = Files.size(one.jarPath()) + Files.size(two.jarPath());
        ModInputLimits aggregate = ModInputLimits.loweringBuilder()
                .maxRepositoryValidationBytes(total - 1).build();
        ModCatalogValidator.ValidationResult aggregateResult = validator(aggregate).validate(List.of(one, two));
        assertTrue(codes(aggregateResult, "budget-one").contains("REPOSITORY_VALIDATION_BYTES_EXCEEDED"));
        assertTrue(codes(aggregateResult, "budget-two").contains("REPOSITORY_VALIDATION_BYTES_EXCEEDED"));
        assertTrue(aggregateResult.registry().tracks().isEmpty());
    }

    @Test
    void preflightSizeIsBoundThroughPackedOpenAndFailureDoesNotStopFollowingJar() throws Exception {
        ModDescriptor changed = descriptor("size-changed", "s1", Map.of(), Map.of());
        ModDescriptor following = descriptor("size-following", "s1", Map.of(), Map.of());
        ModCatalogValidator.PreflightHook mutation = sizes -> Files.write(
                changed.jarPath(), new byte[] {7}, java.nio.file.StandardOpenOption.APPEND);
        ModCatalogValidator validator = new ModCatalogValidator(temp.toAbsolutePath().normalize(),
                ModInputLimits.production(), STOCK_MUSIC_DOMAIN, mutation);

        ModCatalogValidator.ValidationResult result = validator.validate(List.of(changed, following));

        assertTrue(codes(result, "size-changed").contains("MOD_JAR_CHANGED"));
        assertFalse(descriptor(result, "size-following").hasErrors());
    }

    @Test
    void titleDomainsStandaloneReferencesAndCollisionScopesAreExact() throws Exception {
        ModDescriptor s1 = descriptor("s1-valid", "s1", Map.of(0x82, "music"), audioEntries("music"));
        ModDescriptor s2 = descriptor("s2-valid", "s2", Map.of(0x82, "music"), audioEntries("music"));
        ModDescriptor s3k = descriptor("s3k-valid", "s3k", Map.of(0x01, "music"), audioEntries("music"));
        ModDescriptor crossGame = descriptor("cross-game", "s1", Map.of(0x01, "music"), audioEntries("music"));
        ModDescriptor sfxId = descriptor("sfx-id", "s1", Map.of(0xA0, "music"), audioEntries("music"));
        ModDescriptor missingTrack = descriptor("missing-track", "s1", Map.of(0x83, "undeclared"), audioEntries("music"));
        ModDescriptor standalone = descriptor("standalone", ModType.STANDALONE, null,
                Map.of(0x81, "music"), audioEntries("music"));

        ModCatalogValidator.ValidationResult result = validator().validate(List.of(
                s1, s2, s3k, crossGame, sfxId, missingTrack, standalone));

        assertFalse(descriptor(result, "s1-valid").hasErrors());
        assertFalse(descriptor(result, "s2-valid").hasErrors());
        assertFalse(descriptor(result, "s3k-valid").hasErrors());
        assertTrue(codes(result, "cross-game").contains("AUDIO_OVERRIDE_ID_INVALID"));
        assertTrue(codes(result, "sfx-id").contains("AUDIO_OVERRIDE_ID_INVALID"));
        assertTrue(codes(result, "missing-track").contains("AUDIO_OVERRIDE_TRACK_MISSING"));
        assertTrue(codes(result, "standalone").contains("STANDALONE_AUDIO_OVERRIDE"));
        assertTrue(warningCodes(result, "s1-valid").isEmpty(),
                "same numeric id in different base games is not a conflict");
        assertFalse(STOCK_MUSIC_DOMAIN.contains("unknown", 0x81));
    }

    @Test
    void malformedAudioIsOwnedAndEverySameBaseColliderIsWarned() throws Exception {
        ModDescriptor malformed = descriptor("malformed-audio", "s1", Map.of(), Map.of(
                "audio/audio-manifest.yaml", bytes("not: [valid")));
        ModDescriptor first = descriptor("collide-a", "s1", Map.of(0x81, "music"), audioEntries("music"));
        ModDescriptor second = descriptor("collide-b", "s1", Map.of(0x81, "music"), audioEntries("music"));
        ModDescriptor third = descriptor("collide-c", "s1", Map.of(0x81, "music"), audioEntries("music"));
        ModCatalogValidator.ValidationResult result = validator().validate(List.of(malformed, first, second, third));
        assertTrue(codes(result, "malformed-audio").contains("AUDIO_MANIFEST_INVALID"));
        for (String owner : List.of("collide-a", "collide-b", "collide-c")) {
            ModFinding warning = descriptor(result, owner).findings().stream()
                    .filter(f -> f.code().equals("AUDIO_OVERRIDE_CONFLICT")).findFirst().orElseThrow();
            assertTrue(warning.message().contains("collide-a"));
            assertTrue(warning.message().contains("collide-b"));
            assertTrue(warning.message().contains("collide-c"));
        }
    }

    @Test
    void hookGlobalFailureUsesSameAnnotatedDescriptorsPlusOneBannerPolicy() throws Exception {
        ModDescriptor one = descriptor("hook-one", "s1", Map.of(), Map.of());
        ModDescriptor two = descriptor("hook-two", "s2", Map.of(), Map.of());
        ModCatalogValidator validator = new ModCatalogValidator(temp.toAbsolutePath().normalize(),
                ModInputLimits.production(), STOCK_MUSIC_DOMAIN,
                ignored -> { throw new IOException("hook failure"); });

        ModCatalogValidator.ValidationResult result = validator.validate(List.of(one, two));

        assertEquals(3, result.entries().size());
        assertEquals(2, result.entries().stream().filter(ModDescriptor.class::isInstance).count());
        assertEquals(1, result.entries().stream().filter(RepositoryScanFailure.class::isInstance).count());
        assertTrue(codes(result, "hook-one").contains("REPOSITORY_PREFLIGHT_FAILED"));
        assertTrue(codes(result, "hook-two").contains("REPOSITORY_PREFLIGHT_FAILED"));
        assertTrue(result.registry().tracks().isEmpty());
    }

    private ModCatalogValidator validator() {
        return validator(ModInputLimits.production());
    }

    private ModCatalogValidator validator(ModInputLimits limits) {
        return new ModCatalogValidator(temp.toAbsolutePath().normalize(), limits, STOCK_MUSIC_DOMAIN);
    }

    private ModDescriptor descriptor(String id, String base, Map<Integer, String> overrides,
                                     Map<String, byte[]> extra) throws Exception {
        return descriptor(id, ModType.PATCH, base, overrides, extra);
    }

    private ModDescriptor descriptor(String id, ModType type, String base,
                                     Map<Integer, String> overrides,
                                     Map<String, byte[]> extra) throws Exception {
        Path jar = temp.resolve(id + ".jar");
        LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/openggf-mod.yaml", new byte[] {1});
        entries.putAll(extra);
        writeJar(jar, entries);
        String digest;
        try (var root = ModAssetRoot.jar(temp.toAbsolutePath().normalize(), jar)) {
            digest = root.immutableSha256();
        }
        ModManifest manifest = new ModManifest(1, id, id, new SemanticVersion(1, 0, 0),
                List.of("Author"), "Description", VersionRange.parse("*"), type, base,
                null, List.of(), overrides, Map.of(), null, OptionalInt.empty());
        return new ModDescriptor(jar, manifest, digest, false, List.of());
    }

    private static Map<String, byte[]> audioEntries(String id) {
        return Map.of("audio/audio-manifest.yaml", bytes(audio(id, "audio/" + id + ".ogg", false)),
                "audio/" + id + ".ogg", new byte[] {1});
    }

    private static String audio(String id, String path, boolean sfx) {
        return "formatVersion: 1\ntracks:\n  - id: " + id + "\n    assetPath: " + path
                + "\n    loop: false\n    loopStartFrame: 0\n    gain: 1.0\n    tempoEffects: false\n"
                + (sfx ? "sfx:\n  - id: hit\n    assetPath: audio/hit.wav\n    gain: 1.0\n" : "sfx: []\n");
    }

    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static void writeJar(Path path, Map<String, byte[]> entries) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey())); output.write(entry.getValue()); output.closeEntry();
            }
        }
    }
    private static ModDescriptor descriptor(ModCatalogValidator.ValidationResult result,String id){return result.entries().stream().filter(ModDescriptor.class::isInstance).map(ModDescriptor.class::cast).filter(d->d.manifest().id().equals(id)).findFirst().orElseThrow();}
    private static List<String> codes(ModCatalogValidator.ValidationResult result,String id){return descriptor(result,id).findings().stream().filter(f->f.severity()==ModFindingSeverity.ERROR).map(ModFinding::code).toList();}
    private static List<String> warningCodes(ModCatalogValidator.ValidationResult result,String id){return descriptor(result,id).findings().stream().filter(f->f.severity()==ModFindingSeverity.WARNING).map(ModFinding::code).toList();}
}
