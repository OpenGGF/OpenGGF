package com.openggf;

import com.openggf.io.ModInputLimits;
import com.openggf.mods.BundledModManifest;
import com.openggf.mods.BundledModSource;
import com.openggf.mods.InvalidModEntry;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.ModEligibility;
import com.openggf.mods.ModState;
import com.openggf.mods.ModStateStore;
import com.openggf.mods.NativeUnsupportedMods;
import com.openggf.mods.SemanticVersion;
import com.openggf.mods.code.ModClassLoaderFactory;
import com.openggf.mods.code.ModRuntime;
import com.openggf.mods.ui.ModManagerScreen;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Boot-level behaviour of mods bundled with the engine build: defaults, persistence, policy. */
class TestBundledModBoot {
    @TempDir Path temp;
    private final List<AutoCloseable> opened = new ArrayList<>();

    @AfterEach void cleanup() throws Exception {
        for (AutoCloseable closeable : opened.reversed()) closeable.close();
        ModSubsystem.clearProcess();
    }

    @Test void freshInstallEnablesAndTrustsTheBundledCodeModWithoutWritingModState() throws Exception {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        Bundle bundle = bundle("1.0.0");

        ModSubsystem subsystem = boot(mods, bundle);

        ModDescriptor effective = subsystem.processCatalog().effective().orderedEnabled().getFirst();
        assertEquals("time-attack", effective.manifest().id());
        assertEquals(bundle.sha256(), effective.sha256());
        assertEquals(java.util.Set.of("time-attack"), subsystem.trustedCodeOwners());
        assertEquals(new ModState.Entry("time-attack", true, 0, true, bundle.sha256()),
                subsystem.startupModState().entries().getFirst());
        assertFalse(Files.exists(mods.resolve("modstate.json")), "defaults are derived, not written");
        assertEquals(java.util.Set.of(bundle.jar()), subsystem.bundledSourcePaths());

        ModManagerScreen screen = subsystem.createManager(null).screen();
        ModManagerScreen.RowView row = screen.rows().getFirst();
        assertTrue(row.enabled());
        assertEquals(List.of("BUNDLED"), row.badges(), "bundled code needs no trust prompt");
        assertTrue(screen.detailLines().stream().anyMatch(line -> line.startsWith("Bundled with OpenGGF")));
    }

    @Test void explicitDisablePersistsWithoutTrustAcrossAnUpgradeAndCanBeReversed() throws Exception {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        ModSubsystem first = boot(mods, bundle("1.0.0"));
        ModManagerScreen screen = first.createManager(null).screen();
        screen.select(0);
        press(screen, Key.ACCEPT);
        assertFalse(screen.rows().getFirst().enabled());
        apply(screen);

        ModState persisted = new ModStateStore(mods).load().state();
        assertEquals(List.of(new ModState.Entry("time-attack", false, 0)), persisted.entries(),
                "modstate.json records the player's choice but never the manifest-derived trust");
        first.close();

        Bundle upgraded = bundle("1.1.0");
        ModSubsystem second = boot(mods, upgraded);
        assertTrue(second.processCatalog().effective().orderedEnabled().isEmpty(), "the disable survives the upgrade");
        assertEquals(ModEligibility.Status.DISABLED,
                second.processCatalog().eligibility().get("time-attack").status());
        assertEquals(new ModState.Entry("time-attack", false, 0, true, upgraded.sha256()),
                second.startupModState().entries().getFirst(), "only the new manifest's hash is trusted");

        ModManagerScreen again = second.createManager(null).screen();
        again.select(0);
        press(again, Key.ACCEPT);
        assertTrue(again.rows().getFirst().enabled(), "re-enabling needs no trust prompt");
        assertFalse(again.trustArmed());
        apply(again);
        assertEquals(List.of(new ModState.Entry("time-attack", true, 0)),
                new ModStateStore(mods).load().state().entries());
        second.close();

        ModSubsystem third = boot(mods, upgraded);
        assertEquals("time-attack", third.processCatalog().effective().orderedEnabled().getFirst().manifest().id());
    }

    @Test void userInstalledCopyOfABundledIdIsReportedAndIgnored() throws Exception {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        Files.write(mods.resolve("old-time-attack.jar"), modJar("time-attack", "0.9.0"));
        Bundle bundle = bundle("1.0.0");

        ModSubsystem subsystem = boot(mods, bundle);

        assertEquals(bundle.sha256(),
                subsystem.processCatalog().effective().orderedEnabled().getFirst().sha256());
        InvalidModEntry shadowed = subsystem.processCatalog().scanned().stream()
                .filter(InvalidModEntry.class::isInstance).map(InvalidModEntry.class::cast)
                .findFirst().orElseThrow();
        assertEquals(mods.resolve("old-time-attack.jar"), shadowed.jarPath());
        assertEquals("BUNDLED_MOD_ID_RESERVED", shadowed.findings().getLast().code());
        ModManagerScreen screen = subsystem.createManager(null).screen();
        assertEquals(List.of("ERROR"), screen.rows().getLast().badges(), "the user copy is not badged bundled");
    }

    @Test void tamperedBundledJarIsReportedAndNeverTrusted() throws Exception {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        Bundle bundle = bundle("1.0.0");
        byte[] bytes = Files.readAllBytes(bundle.jar());
        bytes[bytes.length - 30] ^= 0x01;
        Files.write(bundle.jar(), bytes);

        ModSubsystem subsystem = boot(mods, bundle);

        assertTrue(subsystem.processCatalog().effective().orderedEnabled().isEmpty());
        assertTrue(subsystem.trustedCodeOwners().isEmpty());
        assertTrue(subsystem.startupModState().entries().isEmpty());
        ModManagerScreen screen = subsystem.createManager(null).screen();
        assertEquals(List.of("BUNDLED", "ERROR"), screen.rows().getFirst().badges());
        screen.select(0);
        assertTrue(screen.detailLines().stream().anyMatch(line -> line.contains("BUNDLED_MOD_HASH_MISMATCH")));
    }

    @Test void deterministicBootNeverOpensOrExtractsBundledContent() {
        AtomicBoolean opened = new AtomicBoolean();
        Path mods = temp.resolve("mods");
        ModSubsystem.installAtBoot(new ExternalContentPolicy(Engine.externalContentBootMode(true)),
                ModSubsystem.normalBootLoader(
                () -> mods.toAbsolutePath().normalize(), ModInputLimits.production(), (game, id) -> true,
                new Boundary(), limits -> {
                    opened.set(true);
                    throw new AssertionError("bundled source opened on a deterministic boot");
                }));
        assertFalse(opened.get());
        assertTrue(ModSubsystem.current().processCatalog().scanned().isEmpty());
        assertFalse(Files.exists(mods));
    }

    @Test void nativeBuildsRejectBundledCodeWithTheExistingNotice() throws Exception {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        Bundle bundle = bundle("1.0.0");
        ModSubsystem.installAtBoot(new ExternalContentPolicy(ExternalContentMode.NORMAL),
                loader(mods, bundle), false);
        ModSubsystem subsystem = ModSubsystem.current();

        assertEquals(List.of("time-attack"), NativeUnsupportedMods.compute(subsystem.processCatalog().scanned(),
                subsystem.startupModState(), false).stream().map(d -> d.manifest().id()).toList());
        try (ModRuntime runtime = new ModClassLoaderFactory(getClass().getClassLoader()).create(
                subsystem.processCatalog().effective(), subsystem.trustedCodeOwners(), false)) {
            assertEquals(ModRuntime.RejectionReason.NATIVE_UNSUPPORTED,
                    runtime.rejectedOwners().get("time-attack").reason());
            assertFalse(runtime.owners().contains("time-attack"));
        }
        ModManagerScreen screen = subsystem.createManager(null).screen();
        assertEquals(List.of("BUNDLED", "UNSUPPORTED"), screen.rows().getFirst().badges());
    }

    private enum Key { ACCEPT, LEFT, RIGHT }

    private static void press(ModManagerScreen screen, Key key) {
        screen.update(new ModManagerScreen.MenuInput() {
            @Override public boolean menuAccept() { return key == Key.ACCEPT; }
            @Override public boolean menuLeft() { return key == Key.LEFT; }
            @Override public boolean menuRight() { return key == Key.RIGHT; }
        });
        screen.update(ModManagerScreen.MenuInput.NEUTRAL);
    }

    /** Actions row: Right enters it on Details; Left wraps to Apply. */
    private static void apply(ModManagerScreen screen) {
        press(screen, Key.RIGHT);
        press(screen, Key.LEFT);
        press(screen, Key.ACCEPT);
        assertEquals("Saved. Restart to apply mod changes", screen.statusMessage());
    }

    private ModSubsystem boot(Path mods, Bundle bundle) {
        ModSubsystem subsystem = loader(mods, bundle).get();
        opened.add(subsystem);
        return subsystem;
    }

    private java.util.function.Supplier<ModSubsystem> loader(Path mods, Bundle bundle) {
        return ModSubsystem.normalBootLoader(() -> mods.toAbsolutePath().normalize(),
                ModInputLimits.production(), (game, id) -> true, new Boundary(),
                limits -> BundledModSource.open(bundle.resources(), Optional.of(bundle.install()),
                        temp.resolve("saves/bundled-mod-cache"), limits));
    }

    private record Bundle(Path install, Path jar, String sha256, ClassLoader resources) { }

    /** One JVM distribution: an engine classpath manifest plus {@code bundled/} beside it. */
    private Bundle bundle(String version) throws IOException {
        Path install = temp.resolve("install-" + version);
        Path jar = Files.createDirectories(install.resolve("bundled")).resolve("time-attack-" + version + ".jar");
        byte[] bytes = modJar("time-attack", version);
        Files.write(jar, bytes);
        String sha = sha256(bytes);
        Path classpath = Files.createDirectories(temp.resolve("engine-" + version));
        Path manifest = classpath.resolve(BundledModManifest.RESOURCE);
        Files.createDirectories(manifest.getParent());
        Files.write(manifest, new BundledModManifest(List.of(new BundledModManifest.Entry("time-attack",
                SemanticVersion.parse(version), jar.getFileName().toString(), sha, bytes.length))).toJson());
        URLClassLoader resources = new URLClassLoader(new URL[]{classpath.toUri().toURL()}, null);
        opened.add(resources);
        return new Bundle(install, jar, sha, resources);
    }

    private static byte[] modJar(String id, String version) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/openggf-mod.yaml", ("""
                formatVersion: 1
                id: %s
                name: Time Attack
                version: %s
                authors: [OpenGGF]
                description: Bundled test mod.
                engineApiRange: "*"
                type: patch
                baseGame: s2
                entrypoint: bundled.Mod
                dependencies: []
                audioOverrides: {}
                artOverrides: {}
                """).formatted(id, version).getBytes(StandardCharsets.UTF_8));
        entries.put("bundled/Mod.class", version.getBytes(StandardCharsets.UTF_8));
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

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class Boundary implements ModSubsystem.SessionAudioBoundary {
        @Override public void install(com.openggf.audio.StreamedMusicPort port) { }
        @Override public void clear() { }
    }
}
