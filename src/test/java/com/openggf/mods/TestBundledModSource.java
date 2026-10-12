package com.openggf.mods;

import com.openggf.io.ModInputLimits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TestBundledModSource {
    @TempDir Path temp;
    private final List<AutoCloseable> opened = new ArrayList<>();

    @AfterEach void closeOpened() throws Exception {
        for (AutoCloseable closeable : opened) closeable.close();
    }

    @Test void manifestRoundTripsAndRejectsLooseInput() {
        var manifest = new BundledModManifest(List.of(new BundledModManifest.Entry("time-attack",
                SemanticVersion.parse("1.2.3"), "time-attack-1.2.3.jar", "a".repeat(64), 1234)));
        assertEquals(manifest, BundledModManifest.parse(manifest.toJson()));
        assertEquals(BundledModManifest.EMPTY,
                BundledModManifest.parse("{\"formatVersion\":1,\"mods\":[]}".getBytes(StandardCharsets.UTF_8)));
        String entry = "{\"id\":\"a\",\"version\":\"1.0.0\",\"file\":\"a.jar\",\"sha256\":\"" + "b".repeat(64)
                + "\",\"size\":5}";
        for (String bad : List.of(
                "{\"formatVersion\":2,\"mods\":[]}",
                "{\"formatVersion\":1}",
                "{\"formatVersion\":1,\"mods\":[],\"extra\":true}",
                "{\"formatVersion\":1,\"mods\":[" + entry.replace("\"size\":5", "\"size\":5,\"trusted\":true") + "]}",
                "{\"formatVersion\":1,\"mods\":[" + entry + "," + entry + "]}",
                "{\"formatVersion\":1,\"mods\":[" + entry.replace("a.jar", "../a.jar") + "]}",
                "{\"formatVersion\":1,\"mods\":[" + entry.replace("a.jar", "A.jar") + "]}",
                "{\"formatVersion\":1,\"mods\":[" + entry.replace("b".repeat(64), "B".repeat(64)) + "]}",
                "{\"formatVersion\":1,\"mods\":[" + entry.replace("\"size\":5", "\"size\":0") + "]}",
                "{\"formatVersion\":1,\"mods\":[" + entry.replace("1.0.0", "1.0") + "]}",
                "{\"formatVersion\":1,\"mods\":[]} trailing",
                "{\"formatVersion\":1,\"formatVersion\":1,\"mods\":[]}")) {
            assertThrows(IllegalArgumentException.class,
                    () -> BundledModManifest.parse(bad.getBytes(StandardCharsets.UTF_8)), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> BundledModManifest.parse(
                new byte[BundledModManifest.MAX_MANIFEST_BYTES + 1]));
    }

    @Test void noManifestResourceMeansNoBundledModsAndNoCache() throws Exception {
        Path cache = temp.resolve("cache");
        BundledModSource source = open(resources(Map.of()), Optional.of(temp), cache);
        assertSame(BundledModSource.EMPTY, source);
        assertFalse(Files.exists(cache));
    }

    @Test void installDirectoryJarMatchingTheManifestIsATrustedDescriptor() throws Exception {
        Path install = Files.createDirectories(temp.resolve("install"));
        byte[] jar = modJar("time-attack", "1.0.0", true);
        Path file = Files.createDirectories(install.resolve("bundled")).resolve("time-attack-1.0.0.jar");
        Files.write(file, jar);
        BundledModSource source = open(resources(Map.of(BundledModManifest.RESOURCE,
                manifest(entry("time-attack", "1.0.0", "time-attack-1.0.0.jar", jar)))),
                Optional.of(install), temp.resolve("cache"));

        ModDescriptor descriptor = assertInstanceOf(ModDescriptor.class, source.entries().getFirst());
        assertEquals("time-attack", descriptor.manifest().id());
        assertEquals(sha256(jar), descriptor.sha256());
        assertTrue(descriptor.containsCode());
        assertNotNull(descriptor.retainedSource(), "validation and class loading use the verified snapshot");
        assertEquals(sha256(jar), descriptor.retainedSource().immutableSha256());
        assertEquals(Map.of("time-attack", sha256(jar)), source.trustedSha256ById());
        assertEquals(java.util.Set.of(file), source.sourcePaths());
        assertFalse(Files.exists(temp.resolve("cache")), "install-directory jars are not copied to the cache");
    }

    @Test void tamperedMissingResizedOrMislabelledInstallJarsAreReportedAndNeverTrusted() throws Exception {
        Path install = Files.createDirectories(temp.resolve("install"));
        Path bundled = Files.createDirectories(install.resolve("bundled"));
        byte[] good = modJar("tampered", "1.0.0", true);
        byte[] tampered = good.clone();
        tampered[tampered.length - 30] ^= 0x01;
        Files.write(bundled.resolve("tampered.jar"), tampered);
        byte[] resized = modJar("resized", "1.0.0", true);
        Files.write(bundled.resolve("resized.jar"), java.util.Arrays.copyOf(resized, resized.length + 1));
        byte[] mislabelled = modJar("someone-else", "1.0.0", true);
        Files.write(bundled.resolve("mislabelled.jar"), mislabelled);
        byte[] wrongVersion = modJar("versioned", "2.0.0", false);
        Files.write(bundled.resolve("versioned.jar"), wrongVersion);

        BundledModSource source = open(resources(Map.of(BundledModManifest.RESOURCE, manifest(
                        entry("tampered", "1.0.0", "tampered.jar", good),
                        entry("absent", "1.0.0", "absent.jar", good),
                        entry("resized", "1.0.0", "resized.jar", resized),
                        entry("mislabelled", "1.0.0", "mislabelled.jar", mislabelled),
                        entry("versioned", "1.0.0", "versioned.jar", wrongVersion)))),
                Optional.of(install), temp.resolve("cache"));

        assertEquals(List.of("BUNDLED_MOD_HASH_MISMATCH", "BUNDLED_MOD_MISSING", "BUNDLED_MOD_HASH_MISMATCH",
                        "BUNDLED_MOD_IDENTITY_MISMATCH", "BUNDLED_MOD_IDENTITY_MISMATCH"),
                source.entries().stream().map(entry -> assertInstanceOf(InvalidModEntry.class, entry)
                        .findings().getFirst().code()).toList());
        assertTrue(source.trustedSha256ById().isEmpty());
        assertEquals(java.util.Set.of("tampered", "absent", "resized", "mislabelled", "versioned"),
                source.reservedIds());
        assertEquals(5, source.sourcePaths().size(), "failed bundled entries still render as bundled");
    }

    @Test void embeddedJarIsExtractedToTheCacheAndReverifiedOnEveryOpen() throws Exception {
        byte[] jar = modJar("time-attack", "1.0.0", true);
        String sha = sha256(jar);
        ClassLoader resources = resources(Map.of(
                BundledModManifest.RESOURCE, manifest(entry("time-attack", "1.0.0", "ta.jar", jar)),
                BundledModManifest.EMBEDDED_PREFIX + "ta.jar", jar));
        Path cache = temp.resolve("saves/bundled-mod-cache");
        Files.createDirectories(cache);
        Path stale = Files.write(cache.resolve("c".repeat(64) + ".jar"), new byte[]{1});
        Path staleStaging = Files.write(cache.resolve(".extract-123.tmp"), new byte[]{1});
        Path foreign = Files.write(cache.resolve("notes.txt"), new byte[]{1});

        BundledModSource first = open(resources, Optional.empty(), cache);
        Path cached = cache.resolve(sha + ".jar");
        assertArrayEquals(jar, Files.readAllBytes(cached));
        assertEquals(Map.of("time-attack", sha), first.trustedSha256ById());
        assertEquals(cached, first.entries().getFirst().sourcePath());
        assertFalse(Files.exists(stale), "jars from earlier builds are pruned");
        assertFalse(Files.exists(staleStaging));
        assertTrue(Files.exists(foreign), "files the engine did not name are left alone");

        var stamp = Files.getLastModifiedTime(cached);
        BundledModSource second = open(resources, Optional.empty(), cache);
        assertEquals(stamp, Files.getLastModifiedTime(cached), "a verified cache entry is reused");
        assertEquals(Map.of("time-attack", sha), second.trustedSha256ById());

        byte[] corrupted = jar.clone();
        corrupted[corrupted.length - 30] ^= 0x01;
        Files.write(cached, corrupted);
        BundledModSource repaired = open(resources, Optional.empty(), cache);
        assertArrayEquals(jar, Files.readAllBytes(cached), "a tampered cache entry is re-extracted");
        assertEquals(Map.of("time-attack", sha), repaired.trustedSha256ById());
    }

    @Test void embeddedJarThatDoesNotMatchTheManifestIsRejectedWithoutPoisoningTheCache() throws Exception {
        byte[] jar = modJar("time-attack", "1.0.0", true);
        byte[] other = modJar("time-attack", "1.0.0", false);
        Path cache = temp.resolve("cache");
        BundledModSource source = open(resources(Map.of(
                BundledModManifest.RESOURCE, manifest(entry("time-attack", "1.0.0", "ta.jar", jar)),
                BundledModManifest.EMBEDDED_PREFIX + "ta.jar", other)), Optional.empty(), cache);
        InvalidModEntry invalid = assertInstanceOf(InvalidModEntry.class, source.entries().getFirst());
        assertEquals("BUNDLED_MOD_HASH_MISMATCH", invalid.findings().getFirst().code());
        assertTrue(source.trustedSha256ById().isEmpty());
        try (var files = Files.list(cache)) {
            assertEquals(List.of(), files.toList(), "neither the bad jar nor its staging file is kept");
        }
    }

    @Test void embeddedCopyWinsOverTheInstallDirectory() throws Exception {
        byte[] jar = modJar("time-attack", "1.0.0", true);
        Path install = Files.createDirectories(temp.resolve("install/bundled")).getParent();
        Files.write(install.resolve("bundled/ta.jar"), new byte[]{0});
        Path cache = temp.resolve("cache");
        BundledModSource source = open(resources(Map.of(
                BundledModManifest.RESOURCE, manifest(entry("time-attack", "1.0.0", "ta.jar", jar)),
                BundledModManifest.EMBEDDED_PREFIX + "ta.jar", jar)), Optional.of(install), cache);
        assertEquals(cache.resolve(sha256(jar) + ".jar"), source.entries().getFirst().sourcePath());
        assertEquals(Map.of("time-attack", sha256(jar)), source.trustedSha256ById());
    }

    @Test void symlinkedCacheDirectoryIsRefused() throws Exception {
        byte[] jar = modJar("time-attack", "1.0.0", true);
        Path real = Files.createDirectories(temp.resolve("elsewhere"));
        Path cache = Files.createSymbolicLink(temp.resolve("cache"), real);
        BundledModSource source = open(resources(Map.of(
                BundledModManifest.RESOURCE, manifest(entry("time-attack", "1.0.0", "ta.jar", jar)),
                BundledModManifest.EMBEDDED_PREFIX + "ta.jar", jar)), Optional.empty(), cache);
        assertEquals("BUNDLED_MOD_INVALID", ((InvalidModEntry) source.entries().getFirst())
                .findings().getFirst().code());
        try (var files = Files.list(real)) {
            assertEquals(List.of(), files.toList());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{\"formatVersion\":9,\"mods\":[]}"})
    void malformedManifestIsAReportedRepositoryFailureAndLoadsNothing(String text) throws Exception {
        BundledModSource source = open(resources(Map.of(BundledModManifest.RESOURCE,
                text.getBytes(StandardCharsets.UTF_8))), Optional.of(temp), temp.resolve("cache"));
        RepositoryScanFailure failure = assertInstanceOf(RepositoryScanFailure.class, source.entries().getFirst());
        assertEquals("BUNDLED_MANIFEST_INVALID", failure.findings().getFirst().code());
        assertTrue(source.trustedSha256ById().isEmpty());
    }

    @Test void reservedIdsShadowUserInstalledCopiesWhichAreReportedInstead() throws Exception {
        Path install = Files.createDirectories(temp.resolve("install/bundled")).getParent();
        byte[] jar = modJar("time-attack", "1.0.0", true);
        Files.write(install.resolve("bundled/ta.jar"), jar);
        BundledModSource source = open(resources(Map.of(BundledModManifest.RESOURCE,
                manifest(entry("time-attack", "1.0.0", "ta.jar", jar)))), Optional.of(install), temp.resolve("c"));
        ModDescriptor userCopy = descriptor("time-attack", temp.resolve("mods/old-ta.jar"));
        ModDescriptor other = descriptor("music", temp.resolve("mods/music.jar"));

        List<ModCatalogEntry> merged = source.withUserEntries(List.of(userCopy, other));

        assertEquals(3, merged.size());
        assertSame(source.entries().getFirst(), merged.getFirst(), "bundled entries come first");
        InvalidModEntry shadowed = assertInstanceOf(InvalidModEntry.class, merged.get(1));
        assertEquals(userCopy.jarPath(), shadowed.jarPath());
        assertEquals("BUNDLED_MOD_ID_RESERVED", shadowed.findings().getLast().code());
        assertSame(other, merged.get(2));
        ModCatalog catalog = new EffectiveCatalogBuilder().build(merged,
                source.applyStartupDefaults(ModState.EMPTY, merged));
        assertEquals(ModEligibility.Status.EFFECTIVE, catalog.eligibility().get("time-attack").status(),
                "the shadowed copy does not turn the bundled id into a blocked duplicate");
    }

    @Test void startupDefaultsEnableAndTrustOnlyVerifiedBundledModsAndKeepThePlayersChoice() throws Exception {
        Path install = Files.createDirectories(temp.resolve("install/bundled")).getParent();
        byte[] jar = modJar("time-attack", "1.0.0", true);
        Files.write(install.resolve("bundled/ta.jar"), jar);
        byte[] broken = modJar("broken", "1.0.0", true);
        BundledModSource source = open(resources(Map.of(BundledModManifest.RESOURCE, manifest(
                entry("time-attack", "1.0.0", "ta.jar", jar),
                entry("broken", "1.0.0", "broken.jar", broken)))), Optional.of(install), temp.resolve("c"));
        List<ModCatalogEntry> scanned = source.withUserEntries(List.of(descriptor("user-mod", temp.resolve("u.jar"))));
        String sha = sha256(jar);

        ModState fresh = source.applyStartupDefaults(new ModState(1, List.of(
                new ModState.Entry("user-mod", true, 0))), scanned);
        assertEquals(List.of(new ModState.Entry("time-attack", true, 0, true, sha),
                new ModState.Entry("user-mod", true, 1)), fresh.entries(),
                "a new bundled mod is enabled, trusted and ordered before user mods");

        ModState disabled = source.applyStartupDefaults(new ModState(1, List.of(
                new ModState.Entry("user-mod", true, 0),
                new ModState.Entry("time-attack", false, 1))), scanned);
        assertEquals(new ModState.Entry("time-attack", false, 1, true, sha), disabled.entries().get(1),
                "an explicit disable survives; trust comes from the manifest, not the saved entry");
        assertTrue(disabled.entries().stream().noneMatch(entry -> entry.id().equals("broken")),
                "an unverified bundled entry receives no default");
    }

    @Test void installDirectoryHonoursTheOverrideAndOtherwiseUsesTheEngineCodeSource() {
        String previous = System.getProperty(BundledModSource.INSTALL_DIR_PROPERTY);
        try {
            System.setProperty(BundledModSource.INSTALL_DIR_PROPERTY, temp.toString());
            assertEquals(Optional.of(temp.toAbsolutePath().normalize()), BundledModSource.installDirectory());
            System.clearProperty(BundledModSource.INSTALL_DIR_PROPERTY);
            Path codeSource = Path.of(BundledModSource.class.getProtectionDomain().getCodeSource().getLocation()
                    .getPath()).toAbsolutePath().normalize();
            assertEquals(Optional.of(codeSource.getParent()), BundledModSource.installDirectory(),
                    "the directory holding the engine jar, or the parent of an exploded class directory");
            assertNotEquals(Optional.of(Path.of("").toAbsolutePath()), BundledModSource.installDirectory());
        } finally {
            if (previous == null) System.clearProperty(BundledModSource.INSTALL_DIR_PROPERTY);
            else System.setProperty(BundledModSource.INSTALL_DIR_PROPERTY, previous);
        }
    }

    private BundledModSource open(ClassLoader resources, Optional<Path> install, Path cache) {
        BundledModSource source = BundledModSource.open(resources, install, cache, ModInputLimits.production());
        opened.add(source);
        return source;
    }

    private ClassLoader resources(Map<String, byte[]> files) throws IOException {
        Path root = Files.createTempDirectory(temp, "classpath");
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            Path target = root.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.write(target, file.getValue());
        }
        URLClassLoader loader = new URLClassLoader(new URL[]{root.toUri().toURL()}, null);
        opened.add(loader);
        return loader;
    }

    static byte[] manifest(BundledModManifest.Entry... entries) {
        return new BundledModManifest(List.of(entries)).toJson();
    }

    static BundledModManifest.Entry entry(String id, String version, String file, byte[] jar) {
        return new BundledModManifest.Entry(id, SemanticVersion.parse(version), file, sha256(jar), jar.length);
    }

    static byte[] modJar(String id, String version, boolean code) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(DefaultModRepositoryScanner.MANIFEST_PATH, ("""
                formatVersion: 1
                id: %s
                name: Bundled %s
                version: %s
                authors: [OpenGGF]
                description: Bundled test mod.
                engineApiRange: "*"
                type: patch
                baseGame: s2
                %s
                dependencies: []
                audioOverrides: {}
                artOverrides: {}
                """).formatted(id, id, version, code ? "entrypoint: bundled.Mod" : "")
                .getBytes(StandardCharsets.UTF_8));
        if (code) entries.put("bundled/Mod.class", new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
        var bytes = new java.io.ByteArrayOutputStream();
        try (JarOutputStream output = new JarOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                JarEntry jarEntry = new JarEntry(entry.getKey());
                jarEntry.setTime(315_532_800_000L);
                output.putNextEntry(jarEntry);
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static ModDescriptor descriptor(String id, Path path) {
        ModManifest manifest = new ModManifest(1, id, id, SemanticVersion.parse("1.0.0"), List.of("A"), "d",
                VersionRange.parse("*"), ModType.PATCH, "s2", null, List.of(), Map.of(), Map.of(), null,
                java.util.OptionalInt.empty());
        return new ModDescriptor(path, manifest, "d".repeat(64), false, List.of());
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
