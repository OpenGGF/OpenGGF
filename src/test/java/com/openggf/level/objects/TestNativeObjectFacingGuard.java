package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.camera.Camera;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.render.ObjectSpriteCapture;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.graphics.GLCommand;
import com.openggf.tests.ObjectGuardSourceScanner;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Enumerates native factory bytecode, without invoking registries or factories.
 * All branches of a factory are visited, including S3K pointer-table and stock-zone
 * overrides. Constructor calls are leaves, so ordinary non-castable descendants
 * are not pulled into the declaration obligation merely because a root spawns them.
 * Dynamically spawned facing owners are checked on their initialized captured
 * appearance; the runtime child regression below covers that separate boundary.
 */
class TestNativeObjectFacingGuard {
    @Test void allNativeCastFactoriesDeclareTheirLogicalFacingOwner() throws Exception {
        var inventory = nativeInventory();
        assertTrue(inventory.opaqueFactories().isEmpty(), () -> "Native factory helper returns an ObjectInstance without an inspectable"
                + " constructor/factory recipe. Expose its native allocation or add explicit native registration metadata;"
                + " do not silently omit this cast path: " + inventory.opaqueFactories());
        var missing = inventory.missingOwners();
        assertTrue(missing.isEmpty(), () -> "Native cast facing ownership is incomplete. Implement "
                + "ObjectVisualFacing.Source.nativeFacing() at each native owner; use "
                + "ObjectVisualFacing.NonDirectional only for an entire performer without native "
                + "horizontal direction. Do not remove cast registrations or infer facing from "
                + "placement flags. Missing " + missing.size() + " owners:\n" + String.join("\n", missing));
    }

    @Test void inventoryIncludesS3kOverridesAndAuxiliaryAnimals() throws Exception {
        var owners = nativeInventory().allocations.keySet();
        for (String owner : List.of(
                "com.openggf.game.sonic1.objects.badniks.Sonic1MotobugBadnikInstance",
                "com.openggf.game.sonic3k.objects.MhzTwistedVineObjectInstance",
                "com.openggf.game.sonic3k.objects.DezMinibossInstance",
                "com.openggf.game.sonic3k.objects.badniks.SnaleBlasterBadnikInstance",
                "com.openggf.game.sonic1.objects.Sonic1AnimalsObjectInstance",
                "com.openggf.level.objects.AnimalObjectInstance")) {
            assertTrue(owners.contains(owner), "Incomplete native allocation scan: " + owner);
        }
    }

    @Test void scannerFollowsFactoryHelpersAndLambdasWithoutConstructingOrInitializingObjects() throws Exception {
        var inventory = new Inventory();
        inventory.registry(FixtureRegistry.class);
        inventory.scan();
        assertEquals(Set.of(DeclaredProbe.class.getName(), UndeclaredProbe.class.getName(),
                com.openggf.level.objects.authored.AuthoredGizmoInstance.class.getName()), inventory.allocations.keySet());
        var missing = inventory.missingOwners();
        assertEquals(1, missing.size());
        assertTrue(missing.getFirst().startsWith(UndeclaredProbe.class.getName() + " <- "));
        assertTrue(missing.getFirst().contains("FixtureFactories"));
        assertEquals(1, inventory.opaqueFactories().size(), "An opaque generic ObjectInstance helper must not disappear from coverage");
        assertEquals("borrowed", inventory.opaqueFactories().iterator().next().name());
    }

    @Test void runtimeFacingChecksDynamicChildrenOnceTheyHaveAVisualBothAloneAndInAContainer() {
        try (var fixture = new RuntimeFacingFixture()) {
            var parent = fixture.objects.createDynamicObject(RuntimeParent::new);
            var child = parent.child(fixture.renderer(false));
            assertTrue(fixture.direction.graph(parent).contains(child));
            var draws = new ArrayList<ObjectSpriteCapture.Draw>();
            ObjectSpriteCapture.observe(draws::add,
                    () -> child.appendRenderCommands(new ArrayList<>()));
            assertEquals(1, draws.size(), "Exercise an observed mapping draw with no populated poses");
            for (var root : List.of(child, parent)) {
                var failure = assertThrows(IllegalArgumentException.class, () -> fixture.direction.captureDynamic(root));
                assertTrue(failure.getMessage().contains("no initialized native visual"), failure.getMessage());
            }

            // Readiness must not cache a neutral fallback for an undeclared child.
            child.renderer = fixture.renderer(true);
            for (var root : List.of(child, parent)) {
                var failure = assertThrows(IllegalArgumentException.class, () -> fixture.direction.captureDynamic(root));
                assertTrue(failure.getMessage().contains(RuntimeChild.class.getName()), failure.getMessage());
                assertTrue(failure.getMessage().contains("ObjectVisualFacing.Source.nativeFacing()"), failure.getMessage());
            }
        }
    }

    private static final class RuntimeFacingFixture implements AutoCloseable {
        final GraphicsManager graphics = new GraphicsManager();
        final ObjectManager objects;
        final StageplayObjectDirection direction;

        RuntimeFacingFixture() {
            graphics.setHeadlessMode(true);
            graphics.cachePaletteTexture(new Palette(), 0);
            var services = mock(ObjectServices.class);
            when(services.graphicsManager()).thenReturn(graphics);
            when(services.solidExecutionRegistry())
                    .thenReturn(SolidExecutionRegistry.inert());
            objects = new ObjectManager(List.of(), null, 0, null, null, graphics,
                    new Camera(), services);
            when(services.objectManager()).thenReturn(objects);
            direction = ObjectDirectionAccess.forManager(objects);
        }

        PatternSpriteRenderer renderer(boolean populated) {
            var pieces = populated
                    ? List.of(new SpriteMappingPiece(0, 0, 1, 1, 0, false, false, 0))
                    : List.<SpriteMappingPiece>of();
            var renderer = new PatternSpriteRenderer(new ObjectSpriteSheet(
                    new Pattern[] {new Pattern()},
                    List.of(new SpriteMappingFrame(pieces)), 0, 1), graphics);
            renderer.ensurePatternsCached(graphics, 512);
            return renderer;
        }

        @Override public void close() { direction.close(); }
    }

    private static final class RuntimeParent extends AbstractObjectInstance implements ObjectVisualFacing.Container {
        RuntimeParent() { super(new ObjectSpawn(100, 80, 1, 0, 0, false, 80), "runtime container"); }
        RuntimeChild child(PatternSpriteRenderer renderer) {
            return spawnChild(() -> new RuntimeChild(renderer));
        }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class RuntimeChild extends AbstractObjectInstance {
        PatternSpriteRenderer renderer;
        RuntimeChild(PatternSpriteRenderer renderer) {
            super(new ObjectSpawn(120, 80, 1, 0, 0, false, 80), "undeclared runtime child");
            this.renderer = renderer;
        }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) {
            renderer.drawFrameIndex(0, getX(), getY());
        }
    }

    private static Inventory nativeInventory() throws Exception {
        Path root = ObjectGuardSourceScanner.findSourceRoot();
        assertNotNull(root, "Production sources are required to discover every native registry");
        var inventory = new Inventory();
        int registries = 0;
        for (Path source : ObjectGuardSourceScanner.sortedJavaSources(root.resolve("com/openggf/game"))) {
            if (!source.getFileName().toString().endsWith("Registry.java")) continue;
            Class<?> type = load(ObjectGuardSourceScanner.className(root, source));
            if (AbstractObjectRegistry.class.isAssignableFrom(type) && !Modifier.isAbstract(type.getModifiers())) {
                inventory.registry(type);
                registries++;
            }
        }
        assertTrue(registries >= 3, "Native registry discovery lost one of the three supported games");
        inventory.scan();
        return inventory;
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name.replace('/', '.'), false, TestNativeObjectFacingGuard.class.getClassLoader());
    }

    private record Method(String owner, String name, String descriptor) { }

    private static final class Inventory {
        private final Map<String, Set<String>> allocations = new TreeMap<>();
        private final ArrayDeque<Method> pending = new ArrayDeque<>();
        private final Set<Method> visited = new HashSet<>();
        private final Set<String> registryOwners = new HashSet<>();
        private final Set<Method> returningObjects = new HashSet<>();
        private final Set<Method> recipes = new HashSet<>();
        private final Map<Method, Set<Method>> calls = new java.util.HashMap<>();

        void registry(Class<?> registry) {
            for (Class<?> owner = registry; owner != null && AbstractObjectRegistry.class.isAssignableFrom(owner);
                 owner = owner.getSuperclass()) {
                String name = owner.getName().replace('.', '/');
                registryOwners.add(name);
                for (String root : List.of("<init>", "<clinit>", "registerDefaultFactories", "create", "releasedAnimalFactory")) {
                    pending.add(new Method(name, root, null));
                }
            }
        }

        List<String> missingOwners() throws ClassNotFoundException {
            var missing = new ArrayList<String>();
            for (var entry : allocations.entrySet()) {
                if (!ObjectVisualFacing.Source.class.isAssignableFrom(load(entry.getKey()))) {
                    missing.add(entry.getKey() + " <- " + String.join(", ", entry.getValue()));
                }
            }
            return missing;
        }

        void scan() throws IOException {
            while (!pending.isEmpty()) {
                Method method = pending.removeFirst();
                if (visited.add(method)) scan(method);
            }
        }

        Set<Method> opaqueFactories() {
            var reachable = new HashSet<>(recipes);
            boolean changed;
            do {
                changed = false;
                for (var entry : calls.entrySet()) {
                    if (entry.getValue().stream().anyMatch(reachable::contains)) changed |= reachable.add(entry.getKey());
                }
            } while (changed);
            var opaque = new HashSet<>(returningObjects);
            opaque.removeAll(reachable);
            return opaque;
        }

        private void call(Method caller, Method target) {
            pending.add(target);
            calls.computeIfAbsent(caller, ignored -> new HashSet<>()).add(target);
        }

        private boolean allocation(String owner, String site) {
            try {
                Class<?> type = load(owner);
                if (ObjectInstance.class.isAssignableFrom(type)) {
                    if (!PlaceholderObjectInstance.class.isAssignableFrom(type))
                        allocations.computeIfAbsent(type.getName(), ignored -> new TreeSet<>()).add(site);
                    return true;
                }
                return false;
            } catch (ClassNotFoundException failure) {
                throw new IllegalStateException("Cannot inspect native factory allocation " + owner + " at " + site, failure);
            }
        }

        private boolean factoryReturn(String descriptor) {
            Type result = Type.getReturnType(descriptor);
            if (result.getSort() != Type.OBJECT || !result.getInternalName().startsWith("com/openggf/")) return false;
            try {
                Class<?> type = load(result.getClassName());
                return ObjectInstance.class.isAssignableFrom(type) || ObjectFactory.class.isAssignableFrom(type)
                        || DestructionEffects.AnimalFactory.class.isAssignableFrom(type)
                        || type == DestructionEffects.DestructionConfig.class
                        || type == ObjectRegistryCatalog.AuxiliaryFactory.class;
            } catch (ClassNotFoundException failure) {
                throw new IllegalStateException("Cannot inspect native factory return type " + result, failure);
            }
        }

        private void scan(Method selected) throws IOException {
            if (!selected.owner().startsWith("com/openggf/")) return;
            try (InputStream input = TestNativeObjectFacingGuard.class.getClassLoader()
                    .getResourceAsStream(selected.owner() + ".class")) {
                if (input == null) throw new IOException("Missing native factory bytecode: " + selected);
                final boolean[] found = {false};
                final String[] parent = {null};
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                        parent[0] = superName;
                    }

                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        if (!name.equals(selected.name())
                                || selected.descriptor() != null && !descriptor.equals(selected.descriptor())) return null;
                        found[0] = true;
                        Method current = new Method(selected.owner(), name, descriptor);
                        Type result = Type.getReturnType(descriptor);
                        if (!registryOwners.contains(selected.owner()) && result.getSort() == Type.OBJECT
                                && result.getInternalName().startsWith("com/openggf/")
                                && (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                            try {
                                if (ObjectInstance.class.isAssignableFrom(load(result.getClassName()))) returningObjects.add(current);
                            } catch (ClassNotFoundException failure) {
                                throw new IllegalStateException("Cannot inspect native factory return " + current, failure);
                            }
                        }
                        return new MethodVisitor(Opcodes.ASM9) {
                            private int line;
                            private String site() { return selected.owner().replace('/', '.') + "." + name + ":" + line; }

                            @Override public void visitLineNumber(int value, Label start) { line = value; }

                            @Override public void visitTypeInsn(int opcode, String type) {
                                if (opcode == Opcodes.NEW && allocation(type, site())) recipes.add(current);
                            }

                            @Override public void visitInvokeDynamicInsn(String invokedName, String invokedDescriptor, Handle bootstrap, Object... arguments) {
                                // LambdaMetafactory carries constructors and factory bodies as
                                // handles. Scanning every handle also covers nested Suppliers.
                                for (Object argument : arguments) if (argument instanceof Handle handle) {
                                    if (handle.getTag() == Opcodes.H_NEWINVOKESPECIAL) {
                                        if (allocation(handle.getOwner(), site())) recipes.add(current);
                                    }
                                    else if (handle.getTag() >= Opcodes.H_INVOKEVIRTUAL && handle.getTag() <= Opcodes.H_INVOKEINTERFACE) {
                                        call(current, new Method(handle.getOwner(), handle.getName(), handle.getDesc()));
                                    }
                                }
                            }

                            @Override public void visitMethodInsn(int opcode, String owner, String invokedName, String invokedDescriptor, boolean isInterface) {
                                if (invokedName.equals("<init>")) return;
                                if (registryOwners.contains(owner) || factoryReturn(invokedDescriptor)) {
                                    call(current, new Method(owner, invokedName, invokedDescriptor));
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_FRAMES);
                if (!found[0] && parent[0] != null) call(selected, new Method(parent[0], selected.name(), selected.descriptor()));
            }
        }
    }

    private static final class FixtureRegistry extends AbstractObjectRegistry {
        FixtureRegistry() { throw new AssertionError("A completeness scan must never construct a registry"); }
        @Override protected void registerDefaultFactories() {
            registerFactory(1, FixtureFactories::declared);
            registerFactory(2, FixtureFactories.hiddenFactory());
            registerFactory(3, FixtureFactories::borrowed);
        }
        @Override public String getPrimaryName(int id) { return "fixture"; }
    }

    private static final class FixtureFactories {
        static ObjectInstance declared(ObjectSpawn spawn, ObjectRegistry registry) { return new DeclaredProbe(spawn); }
        static ObjectFactory hiddenFactory() { return (spawn, registry) -> new UndeclaredProbe(spawn); }
        static ObjectInstance borrowed(ObjectSpawn spawn, ObjectRegistry registry) { return null; }
    }

    private static final class DeclaredProbe extends AbstractObjectInstance implements ObjectVisualFacing.NonDirectional {
        DeclaredProbe(ObjectSpawn spawn) { super(spawn, "declared"); throw new AssertionError("Must not construct a probe"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class UndeclaredProbe extends AbstractObjectInstance {
        UndeclaredProbe(ObjectSpawn spawn) { super(spawn, "undeclared"); throw new AssertionError("Must not construct a probe"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
