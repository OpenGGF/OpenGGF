package com.openggf.mods.code;

import com.openggf.ModSubsystem;
import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.PlayableEntity;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.objects.ClamerObjectInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import javax.tools.ToolProvider;
import java.nio.file.*;
import java.net.URL;
import java.util.*;
import java.util.jar.*;

import static org.junit.jupiter.api.Assertions.*;

/** Matched real native Spring graph; component causality, not the CNZ physical route. */
@Isolated
class TestDevelopmentPatchRewindResolver {
    private static final String OWNER = "capture-fixture";
    @TempDir Path temp;
    @BeforeEach void setup() { TestEnvironment.resetAll(); GraphicsManager.getInstance().initHeadless(); ModSubsystem.clearProcess(); }
    @AfterEach void cleanup() { SessionManager.clear(); ModSubsystem.clearProcess(); GraphicsManager.getInstance().resetState(); }

    @Test void currentDevelopmentPublicationRestoresTheRequiredOwnedSpring() throws Exception {
        try (var prepared = DevelopmentPatchLoader.openJar(jar()); var scope = new CaptureScope()) {
            var prior = ModSubsystem.current().rewindClassResolver();
            ModSubsystem.disableCurrentSessionForDeterminism();
            var module = scope.decorate(prepared, new Sonic3kGameModule());
            assertSame(prepared.rewindClassResolver(), ModSubsystem.current().rewindClassResolver());
            for (int rebuild = 0; rebuild < 2; rebuild++) {
                var mode = SessionManager.openGameplaySession(module);
                com.openggf.game.session.GameplaySessionFactory.attachManagers(mode,
                        com.openggf.game.session.EngineServices.current());
                var field = com.openggf.level.LevelManager.class.getDeclaredField("rewindClassResolver");
                field.setAccessible(true);
                var injected = (RewindClassResolver) field.get(mode.getLevelManager());
                assertSame(prepared.rewindClassResolver(), injected);
                new Graph(module, injected).restoreRequiredSpring();
                SessionManager.closeGameplaySession();
                assertTrue(prepared.rewindClassResolver().resolve(OWNER, "capturefixture.Fixture").isPresent(),
                        "the loader remains open through world retirement");
            }
            scope.close();
            assertSame(prior, ModSubsystem.current().rewindClassResolver());
        }
    }

    @Test void acceptedOwnerRuntimeRestoresTheSameRequiredSpringGraph() throws Exception {
        Path jar = jar();
        var loader = new ModDependencyClassLoader(OWNER, new URL[]{jar.toUri().toURL()},
                getClass().getClassLoader(), List.of());
        try (var runtime = new ModRuntime(Map.of(OWNER, loader), Map.of(), Map.of(), Map.of());
             var prepared = DevelopmentPatchLoader.openJar(jar)) {
            var module = prepared.apply(new Sonic3kGameModule());
            var graph = new Graph(module, new ModClassResolver(runtime, getClass().getClassLoader()));
            graph.restoreRequiredSpring();
        }
    }

    @Test void resolverRejectsWrongMissingAndClosedOwnersEvenForAlreadyLoadedTypes() throws Exception {
        var prepared = DevelopmentPatchLoader.openJar(jar());
        var resolver = prepared.rewindClassResolver();
        assertTrue(resolver.resolve(OWNER, "capturefixture.Fixture").isEmpty(), "registration not yet accepted");
        prepared.apply(new Sonic3kGameModule());
        Class<?> owned = resolver.resolve(OWNER, "capturefixture.Fixture").orElseThrow();
        assertEquals(Optional.of(OWNER), resolver.ownerOf(owned));
        assertTrue(resolver.resolve("wrong-owner", owned.getName()).isEmpty());
        assertTrue(resolver.resolve(OWNER, "capturefixture.Missing").isEmpty());
        assertTrue(resolver.resolve("wrong-owner", ClamerObjectInstance.class.getName()).isEmpty());
        assertSame(ClamerObjectInstance.class, resolver.resolve(OWNER, ClamerObjectInstance.class.getName()).orElseThrow(),
                "accepted native owned callbacks resolve through their exact owner loader's normal delegation");
        prepared.close();
        assertTrue(resolver.resolve(OWNER, owned.getName()).isEmpty());
        assertTrue(resolver.resolve(OWNER, ClamerObjectInstance.class.getName()).isEmpty());
        assertTrue(resolver.ownerOf(owned).isEmpty());
        assertSame(ClamerObjectInstance.class, resolver.resolve(null, ClamerObjectInstance.class.getName()).orElseThrow());
    }

    @Test void failedDecorationDoesNotPublishAndScopeClosePreservesANewerResolver() throws Exception {
        try (var prepared = DevelopmentPatchLoader.openJar(jar()); var scope = new CaptureScope()) {
            var prior = ModSubsystem.current().rewindClassResolver();
            assertThrows(IllegalArgumentException.class, () -> scope.decorate(prepared,
                    new com.openggf.game.sonic2.Sonic2GameModule()));
            assertSame(prior, ModSubsystem.current().rewindClassResolver());
            scope.decorate(prepared, new Sonic3kGameModule());
            var newer = new RewindClassResolver() {
                public Optional<Class<?>> resolve(String owner, String name) { return Optional.empty(); }
                public Optional<String> ownerOf(Class<?> type) { return Optional.empty(); }
            };
            ModSubsystem.current().installRewindClassResolver(newer);
            scope.close();
            assertSame(newer, ModSubsystem.current().rewindClassResolver());
        }
    }

    /** Exercises the actual capture-local owner without constructing a GL window. */
    private static final class CaptureScope implements AutoCloseable {
        final Object delegate;
        final java.lang.reflect.Method decorate, close;
        CaptureScope() throws Exception {
            Class<?> type = Class.forName("com.openggf.tools.GameplayCaptureSession$DevelopmentResolverScope");
            var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
            delegate = constructor.newInstance();
            decorate = type.getDeclaredMethod("decorate", DevelopmentPatchLoader.PreparedDecorator.class,
                    GameModule.class, com.openggf.game.patch.GameplayLaunchRequest.class);
            close = type.getDeclaredMethod("close"); decorate.setAccessible(true); close.setAccessible(true);
        }
        GameModule decorate(DevelopmentPatchLoader.PreparedDecorator prepared, GameModule base) throws Exception {
            try {
                return (GameModule) decorate.invoke(delegate, prepared, base,
                        com.openggf.game.patch.GameplayLaunchRequest.fromConfig(
                                com.openggf.game.GameServices.configuration(), base.getGameId().code()));
            } catch (java.lang.reflect.InvocationTargetException failure) {
                if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
                throw new AssertionError(failure.getCause());
            }
        }
        public void close() throws Exception { close.invoke(delegate); }
    }

    private static final class Graph {
        final ObjectManager manager;
        final RewindClassResolver resolver;
        Graph(GameModule module, RewindClassResolver resolver) {
            this.resolver = resolver;
            Camera camera = new Camera(); camera.setX((short)0); camera.setY((short)0);
            ObjectManager[] holder = new ObjectManager[1];
            var services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GameModule gameModule() { return module; }
                @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> null, List::of); }
            };
            var spawn = new ObjectSpawn(0x100, 0x80, 0xA3, 0, 0, false, 0, 165,
                    OWNER, OWNER+":clamer");
            manager = new ObjectManager(List.of(spawn), module.createObjectRegistry(), 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            holder[0] = manager;
            manager.setRewindClassResolver(resolver);
            manager.reset(0);
            manager.update(0, null, List.of(), 0, false);
            manager.update(0, null, List.of(), 1, false);
        }
        void restoreRequiredSpring() {
            var snapshot = manager.rewindSnapshottable().capture();
            var spring = snapshot.dynamicObjects().stream()
                    .filter(e -> e.className().endsWith("$ClamerSpringChild")).findFirst().orElseThrow();
            assertEquals(OWNER, spring.ownerModId());
            System.out.println("owned Spring before restore: owner="+spring.ownerModId()+" type="+spring.className()
                    +" id="+spring.objectId()+" resolver="+resolver.resolve(spring.ownerModId(),spring.className()).isPresent());
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            assertDoesNotThrow(() -> manager.rewindSnapshottable().restore(snapshot));
            var restored = manager.rewindSnapshottable().capture();
            assertEquals(snapshot.dynamicObjects().size(), restored.dynamicObjects().size());
            var parent = manager.activeObjectsOfType(ClamerObjectInstance.class).getFirst();
            assertNotNull(parent);
            try {
                var required = ClamerObjectInstance.class.getDeclaredField("springChildSlot"); required.setAccessible(true);
                Object child = required.get(parent); assertNotNull(child);
                var backlink = child.getClass().getDeclaredField("parent"); backlink.setAccessible(true);
                assertSame(parent, backlink.get(child), "restored required Spring belongs to the restored parent");
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            assertEquals(spring.objectId(), restored.dynamicObjects().stream()
                    .filter(e -> e.className().equals(spring.className())).findFirst().orElseThrow().objectId());
        }
    }

    private Path jar() throws Exception {
        Path source=temp.resolve("Fixture.java"), classes=temp.resolve("classes");Files.createDirectories(classes);
        Files.writeString(source,"""
                package capturefixture;
                import com.openggf.mods.code.*;
                import com.openggf.game.sonic3k.objects.ClamerObjectInstance;
                public class Fixture implements GgfMod {
                  public void register(ModContext c) {
                    c.registerObject("clamer", (spawn, registry) -> new ClamerObjectInstance(spawn));
                  }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","25","-classpath",
                System.getProperty("java.class.path"),"-d",classes.toString(),source.toString()));
        Path jar=temp.resolve("fixture.jar");
        try(var out=new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("META-INF/openggf-mod.yaml"));
            out.write("""
                    formatVersion: 1
                    id: capture-fixture
                    name: Capture fixture
                    version: 1.0.0
                    authors: [OpenGGF]
                    description: Owned native graph resolver fixture
                    engineApiRange: ">=0.7.0 <0.8.0"
                    type: patch
                    baseGame: s3k
                    entrypoint: capturefixture.Fixture
                    dependencies: []
                    audioOverrides: {}
                    artOverrides: {}
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8));out.closeEntry();
            try(var paths=Files.walk(classes)) { for(var p:paths.filter(Files::isRegularFile).toList()) {
                out.putNextEntry(new JarEntry(classes.relativize(p).toString()));Files.copy(p,out);out.closeEntry();
            }}
        }
        return jar;
    }
}
