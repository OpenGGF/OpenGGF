package com.openggf.mods.code;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.mods.DefaultModRepositoryScanner;
import com.openggf.mods.EffectiveCatalogBuilder;
import com.openggf.mods.ModCatalog;
import com.openggf.mods.ModCatalogValidator;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.ModRepositoryScanner;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModState;
import com.openggf.mods.ModStateSaveResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestModOwnedDynamicObjectRewind {
    private static final String OWNER = "dynamic-rewind";
    private static final String PROBE_CLASS = "example.dynamicrewind.DynamicProbe";
    private static final Path SOURCE = Path.of("src/test/resources/mods/dynamic-rewind-src");

    @TempDir Path temp;

    @Test
    void independentDynamicEntryRestoresThroughOwningModClassloaderWithStableIdentity() throws Exception {
        try (ModRuntime runtime = compileAndLoadMod()) {
            // A real registration pass loads and invokes the fixture's GgfMod entrypoint.
            runtime.newRegistrationPlan();
            assertFalse(runtime.registrationFailures().containsKey(OWNER));

            ModClassResolver resolver = new ModClassResolver(runtime, getClass().getClassLoader());
            Class<?> probeType = resolver.resolve(OWNER, PROBE_CLASS).orElseThrow();
            ObjectManager manager = testObjectManager();
            manager.setRewindClassResolver(resolver);

            ObjectSpawn spawn = new ObjectSpawn(96, 112, 0, 0, 0, false, 0, -1,
                    OWNER, OWNER + ":probe");
            AbstractObjectInstance original = (AbstractObjectInstance) probeType
                    .getConstructor(ObjectSpawn.class).newInstance(spawn);
            Method setValue = probeType.getMethod("setValue", int.class);
            Method value = probeType.getMethod("value");
            setValue.invoke(original, 73);

            // This is an independent dynamic entry: no layout registration and no spawnChild/adoption.
            manager.addDynamicObject(original);
            ObjectRefId originalId = manager.captureIdentityContext()
                    .requireIdentityTable().idFor(original);
            ObjectManagerSnapshot snapshot = manager.rewindSnapshottable().capture();
            assertEquals(1, snapshot.dynamicObjects().size());
            assertEquals(OWNER, snapshot.dynamicObjects().getFirst().ownerModId());
            assertEquals(originalId, snapshot.dynamicObjects().getFirst().objectId());

            manager.removeDynamicObject(original);
            assertEquals(0, manager.getActiveObjects().size());
            manager.rewindSnapshottable().restore(snapshot);

            List<AbstractObjectInstance> restored = manager.getActiveObjects().stream()
                    .filter(probeType::isInstance)
                    .map(AbstractObjectInstance.class::cast)
                    .toList();
            assertEquals(1, restored.size(), "generic recreate must not duplicate through child adoption");
            AbstractObjectInstance recreated = restored.getFirst();
            assertSame(probeType, recreated.getClass());
            assertEquals(73, value.invoke(recreated));
            assertEquals(originalId, manager.captureIdentityContext()
                    .requireIdentityTable().idFor(recreated));
        }
    }

    private ModRuntime compileAndLoadMod() throws Exception {
        Path classes = temp.resolve("classes");
        Files.createDirectories(classes);
        compileJava(SOURCE.resolve("example/dynamicrewind/DynamicProbe.java"), classes);
        Path manifest = classes.resolve("META-INF/openggf-mod.yaml");
        Files.createDirectories(manifest.getParent());
        Files.copy(SOURCE.resolve("META-INF/openggf-mod.yaml"), manifest);

        Path repository = temp.resolve("repo");
        Files.createDirectories(repository);
        Path jar = repository.resolve("dynamic-rewind.jar");
        pack(classes, jar);

        ModRepositoryScanner scanner = new DefaultModRepositoryScanner();
        var scanned = scanner.scan(repository.toAbsolutePath().normalize());
        var validated = new ModCatalogValidator(repository.toAbsolutePath().normalize(),
                ModInputLimits.production(), (game, id) -> true).validate(scanned);
        ModDescriptor descriptor = (ModDescriptor) validated.entries().getFirst();
        assertFalse(descriptor.hasErrors(), descriptor.findings()::toString);
        ModState state = new ModState(1, List.of(
                new ModState.Entry(OWNER, true, 0, true, descriptor.sha256())));
        ModCatalog catalog = new EffectiveCatalogBuilder().build(validated.entries(), state);
        ModRuntime runtime = new ModClassLoaderFactory(getClass().getClassLoader())
                .create(catalog.effective(), Set.of(OWNER));
        runtime.installFaultBoundary(new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { }));
        return runtime;
    }

    private static void compileJava(Path source, Path output) {
        List<String> arguments = new ArrayList<>(List.of("--release", "21", "-classpath",
                Path.of("target/classes").toAbsolutePath().toString(), "-d", output.toString(),
                source.toString()));
        int exit = ToolProvider.getSystemJavaCompiler().run(
                null, null, null, arguments.toArray(String[]::new));
        assertEquals(0, exit, source.toString());
    }

    private static void pack(Path classes, Path jar) throws Exception {
        try (OutputStream file = Files.newOutputStream(jar);
             JarOutputStream output = new JarOutputStream(file);
             var files = Files.walk(classes)) {
            for (Path path : files.filter(Files::isRegularFile).sorted().toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(path).toString().replace('\\', '/')));
                Files.copy(path, output);
                output.closeEntry();
            }
        }
    }

    private static ObjectManager testObjectManager() {
        ObjectManager[] holder = new ObjectManager[1];
        StubObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
        };
        holder[0] = new ObjectManager(List.of(), null, 0, null, null,
                null, null, services);
        return holder[0];
    }
}
