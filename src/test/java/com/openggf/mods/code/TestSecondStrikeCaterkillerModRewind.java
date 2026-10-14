package com.openggf.mods.code;

import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.objects.badniks.CaterkillerJrBodyInstance;
import com.openggf.game.sonic3k.objects.badniks.CaterkillerJrHeadInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.io.ModInputLimits;
import com.openggf.level.objects.*;
import com.openggf.mods.*;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Real creator factory origin and production loader for tagged native head/body reconstruction. */
class TestSecondStrikeCaterkillerModRewind {
    private static final String OWNER = "second-strike";
    @TempDir Path temporary;

    @Test void registeredDerivativeKeepsContentCallbacksUnderAnotherCreatorsProducerScope() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        try (var runtime = compileAndLoad()) {
            GameModule module = new Sonic3kGameModule();
            for (var registration : runtime.newRegistrationPlan().registrations()) module = registration.patch().apply(module, null);
            var boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                    ignored -> new ModStateSaveResult.Saved(), ignored -> { });
            var producers = new ModObjectKeyRegistry(List.of(new ModObjectKeyRegistry.Registration("producer", "producer:actor",
                    (spawn, registry) -> new Producer(spawn))));
            var registry = new ModDecoratedObjectRegistry(module.createObjectRegistry(), producers, Map.of(), boundary);
            ObjectManager[] holder = new ObjectManager[1];
            var services = new StubObjectServices() { @Override public ObjectManager objectManager() { return holder[0]; } };
            var manager = new ObjectManager(List.of(), registry, 0, null, null, GraphicsManager.getInstance(), null, services);
            holder[0] = manager; manager.setRewindClassResolver(new ModClassResolver(runtime, getClass().getClassLoader()));
            var spawn = new ObjectSpawn(400, 300, 0x8D, 3, 1, true, 0x812C, 42, OWNER, OWNER + ":guard");
            var source = (AbstractObjectInstance) manager.createDynamicObject(() -> registry.create(spawn));
            var producer = manager.createDynamicObject(() -> registry.create(new ObjectSpawn(800, 300, 0, 0, 0, false, 300, -1,
                    "producer", "producer:actor")));
            var child = ObjectCallbackDispatch.call(manager, producer, () -> ObjectCallbackDispatch.createRegisteredDerivative(manager, source, 448, 300));
            assertNotNull(child); assertEquals(source.getClass(), child.getClass());
            assertEquals(OWNER, ObjectCallbackDispatch.registeredContentOwner(manager, child));
            assertEquals(0x8D, child.getSpawn().objectId()); assertEquals(3, child.getSpawn().subtype());
            assertEquals(1, child.getSpawn().renderFlags()); assertEquals(0x812C, child.getSpawn().rawYWord());
            assertEquals(-1, child.getSpawn().layoutIndex()); assertFalse(child.getSpawn().respawnTracked());
            manager.removeDynamicObject(producer);
            var saved = manager.rewindSnapshottable().capture();
            assertEquals(2, saved.dynamicObjects().size()); saved.dynamicObjects().forEach(entry -> assertEquals(OWNER, entry.ownerModId()));
            var identity = manager.objectQuery().identityOf(child).orElseThrow();
            manager.removeDynamicObject(child); manager.rewindSnapshottable().restore(saved);
            var restored = (AbstractObjectInstance) manager.objectQuery().resolve(identity).orElseThrow();
            assertNotSame(child, restored); assertEquals(saved.dynamicObjects(), manager.rewindSnapshottable().capture().dynamicObjects());
            manager.reserveAllButNFreeSlots(0);
            assertNull(ObjectCallbackDispatch.createRegisteredDerivative(manager, restored, 500, 300), "full SST admits no derivative");
            manager.removeDynamicObject(restored);
            assertNull(ObjectCallbackDispatch.createRegisteredDerivative(manager, restored, 500, 300), "retired lifetime is never factory authority");
            var live = manager.objectQuery().resolve(saved.dynamicObjects().getFirst().objectId()).orElseThrow();
            assertThrows(ModFaultBoundary.CallbackAborted.class, () -> ObjectCallbackDispatch.run(manager, live, () -> {
                throw new IllegalStateException("injected creator callback failure");
            }));
            assertFalse(boundary.isOwnerAvailable(OWNER)); assertTrue(boundary.isOwnerAvailable("producer"));
        } finally { GraphicsManager.getInstance().resetState(); }
    }

    private static final class Producer extends AbstractObjectInstance {
        Producer(ObjectSpawn spawn) { super(spawn, "producer callback fixture"); }
        @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
    }

    @Test void ownedNativeGraphsRestoreThroughProductionRuntimeWithoutDroppingChildrenOrOwnership() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0, 0, 1024, 768, 0);
        try (var runtime = compileAndLoad()) {
            var plan = runtime.newRegistrationPlan();
            assertTrue(runtime.registrationFailures().isEmpty(), runtime.registrationFailures()::toString);
            GameModule module = new Sonic3kGameModule();
            for (var registration : plan.registrations()) module = registration.patch().apply(module, null);
            GameModule effectiveModule = module;
            var registry = module.createObjectRegistry();
            assertTrue(registry.hasObjectKey(OWNER + ":caterkiller"));
            var resolver = new ModClassResolver(runtime, getClass().getClassLoader());
            assertSame(CaterkillerJrHeadInstance.class, resolver.resolve(OWNER, CaterkillerJrHeadInstance.class.getName()).orElseThrow());
            assertSame(CaterkillerJrBodyInstance.class, resolver.resolve(OWNER, CaterkillerJrBodyInstance.class.getName()).orElseThrow());
            var player = new TestablePlayableSprite("sonic", (short) 900, (short) 300);
            var camera = new Camera() {
                @Override public short getX() { return 0; }
                @Override public short getY() { return 0; }
                @Override public short getWidth() { return 1024; }
                @Override public short getHeight() { return 768; }
            };
            ObjectManager[] holder = new ObjectManager[1];
            var services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GameModule gameModule() { return effectiveModule; }
                @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> player, List::of); }
            };
            var manager = new ObjectManager(List.of(), registry, 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            holder[0] = manager;
            manager.setRewindClassResolver(resolver);
            var a = (CaterkillerJrHeadInstance) manager.createDynamicObject(() -> registry.create(ownedSpawn(1)));
            var b = (CaterkillerJrHeadInstance) manager.createDynamicObject(() -> registry.create(ownedSpawn(2)));
            // Dispatch through the host runner, so both native child allocations
            // inherit the creator callback origin rather than only tagged spawn data.
            manager.update(0, player, List.of(), 0, false);
            manager.update(0, player, List.of(), 1, false);
            var aId = id(manager, a); var bId = id(manager, b);
            var aIds = children(a).stream().map(child -> id(manager, child)).toList();
            var bIds = children(b).stream().map(child -> id(manager, child)).toList();
            assertEquals(12, aIds.size()); assertEquals(12, bIds.size());
            var captured = manager.rewindSnapshottable().capture();
            assertEquals(26, captured.dynamicObjects().size());
            captured.dynamicObjects().forEach(entry -> {
                assertEquals(OWNER, entry.ownerModId(), "native classes inherit the registered creator callback owner");
                assertEquals(OWNER, entry.spawn().ownerModId());
                assertEquals(OWNER + ":caterkiller", entry.spawn().objectKey());
                assertSame(entry.className().endsWith("HeadInstance") ? CaterkillerJrHeadInstance.class : CaterkillerJrBodyInstance.class,
                        resolver.resolve(entry.ownerModId(), entry.className()).orElseThrow());
            });
            for (int frame = 2; frame < 162; frame++) manager.update(0, player, List.of(), frame, false);
            var expected = manager.rewindSnapshottable().capture();
            manager.removeDynamicObject(a); manager.removeDynamicObject(b);
            manager.update(0, player, List.of(), 162, false);
            manager.rewindSnapshottable().restore(captured);
            var restoredA = headById(manager, aId); var restoredB = headById(manager, bId);
            assertNotSame(a, restoredA); assertNotSame(b, restoredB);
            assertEquals(aIds, children(restoredA).stream().map(child -> id(manager, child)).toList());
            assertEquals(bIds, children(restoredB).stream().map(child -> id(manager, child)).toList());
            for (var child : children(restoredA)) assertSame(restoredA, field(child, "ownerRef"));
            for (var child : children(restoredB)) assertSame(restoredB, field(child, "ownerRef"));
            assertEquals(captured.dynamicObjects(), manager.rewindSnapshottable().capture().dynamicObjects());
            for (int frame = 2; frame < 162; frame++) manager.update(0, player, List.of(), frame, false);
            assertEquals(expected.dynamicObjects(), manager.rewindSnapshottable().capture().dynamicObjects(),
                    "production callbacks, captured owner recipe and both body graphs replay identically");
            manager.removeDynamicObject(restoredA);
            assertTrue(children(restoredB).stream().noneMatch(ObjectInstance::isDestroyed));
            // Frame publishers belong to the stable post-execution boundary.
            // Complete the host cleanup/touch publication before capturing removal.
            manager.update(0, player, List.of(), 163, false);
            assertEquals(12, children(restoredB).size());
            assertDoesNotThrow(() -> manager.rewindSnapshottable().capture());
            manager.rewindSnapshottable().restore(captured);
            assertEquals(26, manager.rewindSnapshottable().capture().dynamicObjects().size());
        } finally {
            AbstractObjectInstance.resetCameraBoundsForTests();
            GraphicsManager.getInstance().resetState();
        }
    }

    private ModRuntime compileAndLoad() throws Exception {
        Path classes = Files.createDirectory(temporary.resolve("classes"));
        Path source = temporary.resolve("CaterkillerMod.java");
        Files.writeString(source, """
                package caterkillertest;
                import com.openggf.mods.code.GgfMod;
                import com.openggf.mods.code.ModContext;
                import secondstrike.enemies.SecondStrikeCaterkiller;
                import secondstrike.enemies.SecondStrikeEnemies;
                public final class CaterkillerMod implements GgfMod {
                    @Override public void register(ModContext context) { SecondStrikeCaterkiller.register(context); SecondStrikeEnemies.register(context); }
                }
                """);
        SecondStrikeSourceFixture.compileClasses(classes, source, SecondStrikeSourceFixture.ENEMIES);
        Path manifest = classes.resolve("META-INF/openggf-mod.yaml");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, """
                formatVersion: 1
                id: second-strike
                name: Caterkiller Native Graph Fixture
                version: 1.0.0
                authors: [OpenGGF Tests]
                description: Owned native Caterkiller factory rewind proof.
                engineApiRange: ">=0.7.0 <0.8.0"
                type: patch
                baseGame: s3k
                entrypoint: caterkillertest.CaterkillerMod
                dependencies: []
                audioOverrides: {}
                artOverrides: {}
                """);
        Path repository = Files.createDirectory(temporary.resolve("repository"));
        try (var output = new JarOutputStream(Files.newOutputStream(repository.resolve("caterkiller.jar")));
             var files = Files.walk(classes)) {
            for (Path path : files.filter(Files::isRegularFile).sorted().toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(path).toString().replace('\\', '/')));
                Files.copy(path, output); output.closeEntry();
            }
        }
        var validated = new ModCatalogValidator(repository, ModInputLimits.production(), (game, key) -> true)
                .validate(new DefaultModRepositoryScanner().scan(repository));
        var descriptor = assertInstanceOf(ModDescriptor.class, validated.entries().getFirst());
        assertFalse(descriptor.hasErrors(), descriptor.findings()::toString);
        var state = new ModState(1, List.of(new ModState.Entry(OWNER, true, 0, true, descriptor.sha256())));
        var catalog = new EffectiveCatalogBuilder().build(validated.entries(), state);
        var runtime = new ModClassLoaderFactory(getClass().getClassLoader()).create(catalog.effective(), Set.of(OWNER));
        assertTrue(runtime.rejectedOwners().isEmpty(), runtime.rejectedOwners()::toString);
        runtime.installFaultBoundary(new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { }));
        return runtime;
    }

    private static ObjectSpawn ownedSpawn(int layout) {
        return new ObjectSpawn(0x180, 0x130, 0, 0, 0, false, 0x130, layout, OWNER, OWNER + ":caterkiller");
    }
    private static ObjectRefId id(ObjectManager manager, ObjectInstance object) {
        return manager.captureIdentityContext().requireIdentityTable().idFor(object);
    }
    private static CaterkillerJrHeadInstance headById(ObjectManager manager, ObjectRefId id) {
        return manager.getActiveObjects().stream().filter(CaterkillerJrHeadInstance.class::isInstance)
                .filter(head -> id.equals(id(manager, head))).map(CaterkillerJrHeadInstance.class::cast).findFirst().orElseThrow();
    }
    @SuppressWarnings("unchecked") private static List<CaterkillerJrBodyInstance> children(CaterkillerJrHeadInstance head) {
        return (List<CaterkillerJrBodyInstance>) field(head, "bodySegments");
    }
    private static Object field(Object object, String name) {
        try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
