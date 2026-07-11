package com.openggf.mods.code;

import com.openggf.mods.ModDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestModClassLoaders {
    @TempDir Path temp;

    @Test
    void exposesOnlyTheOwnersDirectDeclaredDependencies() throws Exception {
        Path cJar = jar("c.jar", "example.c.C");
        Path bJar = jar("b.jar", "example.b.B");
        Path siblingJar = jar("sibling.jar", "example.sibling.Hidden");
        Path aJar = jar("a.jar", "example.a.A");

        try (ModDependencyClassLoader c = loader("c", cJar, List.of());
             ModDependencyClassLoader b = loader("b", bJar, List.of(c));
             ModDependencyClassLoader sibling = loader("sibling", siblingJar, List.of());
             ModDependencyClassLoader a = loader("a", aJar, List.of(b))) {
            assertEquals(b, a.loadClass("example.b.B").getClassLoader());
            assertEquals(c, b.loadClass("example.c.C").getClassLoader());
            assertThrows(ClassNotFoundException.class, () -> a.loadClass("example.c.C"));
            assertThrows(ClassNotFoundException.class, () -> a.loadClass("example.sibling.Hidden"));

            try (ModDependencyClassLoader aWithC = loader("a-direct", aJar, List.of(b, c))) {
                assertEquals(c, aWithC.loadClass("example.c.C").getClassLoader());
            }
        }
    }

    @Test
    void resolvesEngineClassesParentFirstAndKeepsIdenticalModNamesOwnerLocal() throws Exception {
        Path firstJar = jar("first.jar", "example.shared.Same", "com.openggf.mods.ModDescriptor");
        Path secondJar = jar("second.jar", "example.shared.Same");

        try (ModDependencyClassLoader first = loader("first", firstJar, List.of());
             ModDependencyClassLoader second = loader("second", secondJar, List.of())) {
            assertSame(ModDescriptor.class, first.loadClass(ModDescriptor.class.getName()));
            Class<?> firstSame = first.loadClass("example.shared.Same");
            Class<?> secondSame = second.loadClass("example.shared.Same");
            assertNotSame(firstSame, secondSame);
            assertSame(first, firstSame.getClassLoader());
            assertSame(second, secondSame.getClassLoader());
            assertNull(first.getResource("mod-asset.bin"));
            assertFalse(first.getResources("mod-asset.bin").hasMoreElements());
            assertNull(first.getResource("example/shared/Same.class"));
            assertFalse(first.getResources("example/shared/Same.class").hasMoreElements());
        }
    }

    private ModDependencyClassLoader loader(String id, Path jar,
                                            List<ModDependencyClassLoader> dependencies) throws IOException {
        return new ModDependencyClassLoader(id, new URL[] {jar.toUri().toURL()},
                TestModClassLoaders.class.getClassLoader(), dependencies);
    }

    private Path jar(String name, String... binaryNames) throws IOException {
        Path path = temp.resolve(name);
        try (OutputStream output = Files.newOutputStream(path); JarOutputStream jar = new JarOutputStream(output)) {
            for (String binaryName : binaryNames) {
                jar.putNextEntry(new JarEntry(binaryName.replace('.', '/') + ".class"));
                jar.write(emptyClass(binaryName.replace('.', '/')));
                jar.closeEntry();
            }
            jar.putNextEntry(new JarEntry("mod-asset.bin"));
            jar.write(new byte[] {1, 2, 3});
            jar.closeEntry();
        }
        return path;
    }

    private static byte[] emptyClass(String internalName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
