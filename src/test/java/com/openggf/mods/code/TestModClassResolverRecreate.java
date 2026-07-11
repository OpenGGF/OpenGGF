package com.openggf.mods.code;

import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.DynamicObjectRecreateContext;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindClassResolver;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestModClassResolverRecreate {
    private static final String SHARED_NAME = "example.shared.DynamicChild";
    @TempDir Path temp;

    @Test
    void ownerResolverRecreatesUnregisteredChildAndSeparatesIdenticalNames() throws Exception {
        try (RuntimeFixture fixture = runtimeWithIdenticalChildren()) {
            ModClassResolver resolver = new ModClassResolver(
                    fixture.runtime(), getClass().getClassLoader());
            Class<?> first = resolver.resolve("owner-a", SHARED_NAME).orElseThrow();
            Class<?> second = resolver.resolve("owner-b", SHARED_NAME).orElseThrow();
            assertNotSame(first, second);
            assertEquals("owner-a", resolver.ownerOf(first).orElseThrow());
            assertEquals("owner-b", resolver.ownerOf(second).orElseThrow());

            ObjectManager manager = testObjectManager();
            DynamicObjectRecreateContext context = new DynamicObjectRecreateContext(manager, resolver);
            ObjectManagerSnapshot.DynamicObjectEntry firstEntry = entry("owner-a", SHARED_NAME);
            ObjectManagerSnapshot.DynamicObjectEntry secondEntry = entry("owner-b", SHARED_NAME);

            Object firstRestored = ObjectRewindDynamicCodecs.genericRecreate(firstEntry, context);
            Object secondRestored = ObjectRewindDynamicCodecs.genericRecreate(secondEntry, context);
            assertNotNull(firstRestored);
            assertNotNull(secondRestored);
            assertSame(first, firstRestored.getClass());
            assertSame(second, secondRestored.getClass());
        }
    }

    @Test
    void missingOwnerAndClosedRuntimeFailWithoutEngineFallback() throws Exception {
        try (RuntimeFixture fixture = runtimeWithIdenticalChildren()) {
            ModClassResolver resolver = new ModClassResolver(
                    fixture.runtime(), getClass().getClassLoader());
            assertTrue(resolver.resolve("missing", RecreateBase.class.getName()).isEmpty());
            fixture.runtime().close();
            assertTrue(resolver.resolve("owner-a", SHARED_NAME).isEmpty());
        }
    }

    @Test
    void engineOnlyResolverPreservesOwnerlessBehaviorAndRejectsOwnedEntries() throws Exception {
        assertSame(RecreateBase.class,
                RewindClassResolver.ENGINE_ONLY.resolve(null, RecreateBase.class.getName()).orElseThrow());
        assertTrue(RewindClassResolver.ENGINE_ONLY.ownerOf(RecreateBase.class).isEmpty());
        assertTrue(RewindClassResolver.ENGINE_ONLY.resolve(
                "owner", RecreateBase.class.getName()).isEmpty());
    }

    @Test
    void realObjectManagerCaptureAndRestoreCarriesOwnerForLoaderOnlyChild() throws Exception {
        try (RuntimeFixture fixture = runtimeWithIdenticalChildren()) {
            ModClassResolver resolver = new ModClassResolver(
                    fixture.runtime(), getClass().getClassLoader());
            ObjectManager manager = testObjectManager();
            manager.setRewindClassResolver(resolver);
            AbstractObjectInstance original = (AbstractObjectInstance) resolver
                    .resolve("owner-a", SHARED_NAME).orElseThrow()
                    .getDeclaredConstructor().newInstance();
            manager.addDynamicObject(original);

            ObjectManagerSnapshot snapshot = manager.rewindSnapshottable().capture();
            assertEquals("owner-a", snapshot.dynamicObjects().getFirst().ownerModId());
            manager.removeDynamicObject(original);
            manager.rewindSnapshottable().restore(snapshot);

            AbstractObjectInstance restored = manager.getActiveObjects().stream()
                    .filter(value -> value.getClass().getName().equals(SHARED_NAME))
                    .map(AbstractObjectInstance.class::cast).findFirst().orElse(null);
            assertNotNull(restored);
            assertSame(fixture.firstLoader(), restored.getClass().getClassLoader());
        }
    }

    @Test
    void entryIdentityIncludesOwnerEvenWhenBinaryNamesMatch() {
        ObjectManagerSnapshot.DynamicObjectEntry first = entry("owner-a", SHARED_NAME);
        ObjectManagerSnapshot.DynamicObjectEntry second = entry("owner-b", SHARED_NAME);

        assertFalse(first.equals(second));
    }

    private RuntimeFixture runtimeWithIdenticalChildren() throws Exception {
        Path firstJar = childJar("first.jar", SHARED_NAME);
        Path secondJar = childJar("second.jar", SHARED_NAME);
        ModDependencyClassLoader first = new ModDependencyClassLoader("owner-a",
                new URL[] { firstJar.toUri().toURL() }, getClass().getClassLoader(), List.of());
        ModDependencyClassLoader second = new ModDependencyClassLoader("owner-b",
                new URL[] { secondJar.toUri().toURL() }, getClass().getClassLoader(), List.of());
        ModRuntime runtime = new ModRuntime(Map.of("owner-a", first, "owner-b", second),
                Map.of(), Map.of(), Map.of());
        return new RuntimeFixture(runtime, first, second);
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

    private Path childJar(String filename, String binaryName) throws Exception {
        Path jar = temp.resolve(filename);
        String internalName = binaryName.replace('.', '/');
        try (OutputStream file = Files.newOutputStream(jar);
             JarOutputStream output = new JarOutputStream(file)) {
            output.putNextEntry(new JarEntry(internalName + ".class"));
            output.write(childClass(internalName));
            output.closeEntry();
        }
        return jar;
    }

    private static byte[] childClass(String internalName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null,
                RecreateBase.class.getName().replace('.', '/'), null);
        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                RecreateBase.class.getName().replace('.', '/'), "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ObjectManagerSnapshot.DynamicObjectEntry entry(String owner, String className) {
        return new ObjectManagerSnapshot.DynamicObjectEntry(className,
                new ObjectSpawn(0, 0, 0, 0, 0, false, 0), 0,
                null, null, null, owner);
    }

    public static class RecreateBase extends AbstractObjectInstance implements RewindRecreatable {
        public RecreateBase() {
            super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "ResolverFixture");
        }

        @Override
        public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            try {
                return (AbstractObjectInstance) getClass().getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        }

        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private record RuntimeFixture(ModRuntime runtime,
                                  ModDependencyClassLoader firstLoader,
                                  ModDependencyClassLoader secondLoader)
            implements AutoCloseable {
        @Override public void close() throws Exception {
            runtime.close();
        }
    }
}
