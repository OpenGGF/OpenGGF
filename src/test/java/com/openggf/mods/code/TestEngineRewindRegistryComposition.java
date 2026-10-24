package com.openggf.mods.code;

import com.openggf.camera.Camera;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.EngineRewindRegistryFactory;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.graphics.FadeManager;
import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.ModManifest;
import com.openggf.mods.ModType;
import com.openggf.mods.SemanticVersion;
import com.openggf.mods.VersionRange;
import com.openggf.tests.TestEnvironment;
import com.openggf.timer.TimerManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real gameplay attachment and the caller-restricted package bridge. */
@Isolated
public class TestEngineRewindRegistryComposition {
    @TempDir Path temporary;

    private final GameDataSource source = new GameDataSource() {
        @Override public String identity() { return "shared-composition-fixture"; }
        @Override public Optional<com.openggf.data.Rom> rom() { return Optional.empty(); }
        @Override public InputStream openAsset(String path) throws IOException {
            throw new IOException("No fixture assets: " + path);
        }
    };

    @BeforeEach void reset() { TestEnvironment.resetAll(); }

    @AfterEach void clear() {
        SessionManager.clear();
        TestEnvironment.resetAll();
    }

    @Test
    void actualCompositionKeepsWorldScopeAndControllerPartitionAcrossRebuilds() {
        Ledger controller = new Ledger();
        GameplayModeContext first = openWorld(controller);
        var world = first.getWorldSession();
        RewindRegistry original = first.getRewindRegistry();
        first.getCamera().setX((short) 11);
        controller.value = 7;
        CompositeSnapshot full = original.capture();
        first.getCamera().setX((short) 22);
        controller.value = 17;
        CompositeSnapshot course = original.captureCourse();
        assertTrue(full.containsKey(controller.key()));
        assertFalse(course.containsKey(controller.key()), "Only the actual controller is excluded");

        attachCore(first);
        assertNotSame(original, first.getRewindRegistry());
        assertEquals(0, first.getCamera().getX());
        assertFullAndCourseRestore(first, controller, full, course);

        GameplayModeContext rebuilt = SessionManager.reopenGameplaySession(HardwareReadinessAdmissionPolicy.LIVE);
        attachCore(rebuilt);
        assertSame(world, rebuilt.getWorldSession());
        assertNotSame(first, rebuilt);
        assertNotSame(original, rebuilt.getRewindRegistry());
        assertEquals(0, rebuilt.getCamera().getX());
        assertFullAndCourseRestore(rebuilt, controller, full, course);
    }

    private static void assertFullAndCourseRestore(GameplayModeContext context, Ledger controller,
                                                   CompositeSnapshot full, CompositeSnapshot course) {
        context.getRewindRegistry().restore(full);
        assertEquals(11, context.getCamera().getX());
        assertEquals(7, controller.value);
        controller.value = 29;
        context.getRewindRegistry().restoreCourse(course);
        assertEquals(22, context.getCamera().getX());
        assertEquals(29, controller.value, "Course restore must retain the current mode ledger");
    }

    @Test
    void foreignWorldAndMapCopiesRejectBeforeActualCoreStateOrCallbacksChange() {
        GameplayModeContext first = openWorld(new Ledger());
        first.getCamera().setX((short) 11);
        CompositeSnapshot foreignFull = first.getRewindRegistry().capture();
        CompositeSnapshot foreignCourse = first.getRewindRegistry().captureCourse();

        Ledger controller = new Ledger();
        GameplayModeContext second = openWorld(controller);
        assertSame(first.getWorldSession().getDataSource(), second.getWorldSession().getDataSource());
        RewindRegistry registry = second.getRewindRegistry();
        CompositeSnapshot copiedFull = new CompositeSnapshot(registry.capture().entries());
        CompositeSnapshot copiedCourse = new CompositeSnapshot(registry.captureCourse().entries());
        second.getCamera().setX((short) 321);
        second.getGameStateManager().addScore(500);
        second.getRng().nextRaw();
        controller.value = 41;
        var cameraBefore = second.getCamera().capture();
        var stateBefore = second.getGameStateManager().capture();
        var rngBefore = second.getRng().capture();
        AtomicInteger callbacks = new AtomicInteger();
        registry.registerPostRestoreCallback("composition-observer", callbacks::incrementAndGet);

        for (CompositeSnapshot snapshot : List.of(foreignFull, copiedFull)) {
            assertThrows(IllegalArgumentException.class, () -> registry.restore(snapshot));
        }
        for (CompositeSnapshot snapshot : List.of(foreignCourse, copiedCourse)) {
            assertThrows(IllegalArgumentException.class, () -> registry.requireCourseLayout(snapshot));
            assertThrows(IllegalArgumentException.class, () -> registry.restoreCourse(snapshot));
        }
        assertEquals(cameraBefore, second.getCamera().capture());
        var stateAfter = second.getGameStateManager().capture();
        assertEquals(500, stateAfter.score());
        assertEquals(stateBefore.lives(), stateAfter.lives());
        assertArrayEquals(stateBefore.gotEmeralds(), stateAfter.gotEmeralds());
        assertArrayEquals(stateBefore.gotSuperEmeralds(), stateAfter.gotSuperEmeralds());
        assertEquals(rngBefore, second.getRng().capture());
        assertEquals(41, controller.value);
        assertEquals(0, callbacks.get());
    }

    @Test
    void allPublicConstructorsRemainUnscopedAndKeepTheirControllerSemantics() {
        Ledger controller = new Ledger();
        List<RewindRegistry> registries = List.of(new RewindRegistry(),
                new RewindRegistry(null), new RewindRegistry(null, controller));
        for (int i = 0; i < registries.size(); i++) {
            RewindRegistry registry = registries.get(i);
            registry.register(controller);
            controller.value = 7;
            CompositeSnapshot full = new CompositeSnapshot(registry.capture().entries());
            CompositeSnapshot course = new CompositeSnapshot(registry.captureCourse().entries());
            controller.value = 99;
            registry.restore(full);
            assertEquals(7, controller.value);
            controller.value = 99;
            registry.restoreCourse(course);
            assertEquals(i == 2 ? 99 : 7, controller.value);
        }
    }

    @Test
    void sameLoaderWrongCallerCannotMintScopeDirectlyOrThroughHiddenReference() {
        assertSame(EngineRewindRegistryFactory.class.getClassLoader(), getClass().getClassLoader());
        assertThrows(SecurityException.class,
                () -> EngineRewindRegistryFactory.create(new Object(), null, null));
        TestOwnerBoundGamePatch.RegistryCreation reference = EngineRewindRegistryFactory::create;
        assertThrows(SecurityException.class, () -> reference.create(new Object(), null, null));
        // Caller admission must precede argument validation.
        assertThrows(SecurityException.class, () -> EngineRewindRegistryFactory.create(null, null, null));
        assertThrows(SecurityException.class, () -> reference.create(null, null, null));
    }

    @Test
    void foreignLoaderCannotMintScopeDirectlyOrThroughHiddenReference() throws Exception {
        for (Class<? extends GgfMod> entrypoint : List.of(DirectCaller.class, ReferenceCaller.class)) {
            String name = entrypoint.getName();
            String resource = name.replace('.', '/') + ".class";
            Path jar = temporary.resolve(entrypoint.getSimpleName() + ".jar");
            try (var archive = new JarOutputStream(Files.newOutputStream(jar));
                 var bytes = getClass().getClassLoader().getResourceAsStream(resource)) {
                archive.putNextEntry(new JarEntry(resource));
                archive.write(Objects.requireNonNull(bytes, resource).readAllBytes());
                archive.closeEntry();
            }
            ClassLoader engineParent = new ClassLoader(getClass().getClassLoader()) {
                @Override protected Class<?> loadClass(String className, boolean resolve)
                        throws ClassNotFoundException {
                    if (className.equals(name)) throw new ClassNotFoundException(className);
                    return super.loadClass(className, resolve);
                }
            };
            String owner = "scope-probe";
            Path assets = Files.createDirectory(temporary.resolve(entrypoint.getSimpleName() + "-assets"));
            var snapshot = ModAssetRoot.snapshotDirectory(temporary, assets,
                    ModInputLimits.production(), DirectoryAccess.TEST);
            var manifest = new ModManifest(1, owner, "Scope probe", SemanticVersion.parse("1.0.0"),
                    List.of("test"), "test", VersionRange.parse("*"), ModType.PATCH, "s2", name,
                    List.of(), Map.of(), Map.of(), null, OptionalInt.empty());
            var descriptor = new ModDescriptor(assets, manifest, "0".repeat(64), true, List.of());
            var loader = new ModDependencyClassLoader(owner, new URL[]{jar.toUri().toURL()}, engineParent, List.of());
            // Reuse the existing registration fixture API: the real runtime invokes the entrypoint.
            try (var runtime = new ModRuntime(Map.of(owner, loader), Map.of(owner, snapshot),
                    Map.of(owner, descriptor), Map.of())) {
                assertSame(loader, runtime.loadOwned(owner, name).getClassLoader());
                runtime.newRegistrationPlan();
                assertEquals(Set.of(owner), runtime.registrationFailures().keySet());
                assertInstanceOf(SecurityException.class, runtime.registrationFailures().get(owner));
                assertTrue(runtime.registrationPlans().isEmpty());
            }
        }
    }

    public static final class DirectCaller implements GgfMod {
        @Override public void register(ModContext context) {
            EngineRewindRegistryFactory.create(new Object(), null, null);
        }
    }

    public static final class ReferenceCaller implements GgfMod {
        @Override public void register(ModContext context) {
            TestOwnerBoundGamePatch.RegistryCreation reference = EngineRewindRegistryFactory::create;
            reference.create(new Object(), null, null);
        }
    }

    private GameplayModeContext openWorld(Ledger controller) {
        var module = new DelegatingGameModule(new Sonic2GameModule(), "rewind-composition") {
            @Override public GameplayFrameController gameplayFrameController() { return controller; }
            @Override public List<RewindSnapshottable<?>> rewindAdapters() { return List.of(controller); }
        };
        GameplayModeContext context = SessionManager.openGameplaySession(module, module, source, null,
                HardwareReadinessAdmissionPolicy.LIVE);
        attachCore(context);
        return context;
    }

    private static void attachCore(GameplayModeContext context) {
        context.attachGameplayManagers(new Camera(), new TimerManager(), new GameStateManager(),
                new FadeManager(), new GameRng(GameRng.Flavour.S1_S2), new DefaultSolidExecutionRegistry());
    }

    private static final class Ledger implements GameplayFrameController, RewindSnapshottable<Integer> {
        private int value;
        @Override public String key() { return "composition-ledger"; }
        @Override public Integer capture() { return value; }
        @Override public void restore(Integer snapshot) { value = snapshot; }
        @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) { return true; }
        @Override public void afterTick(CourseControl course, boolean advanced) { }
    }
}
