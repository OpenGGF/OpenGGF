package com.openggf.tools;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/** Bounded bytecode regression controls for the 2026-10-08 member contract.
 * Input: an owned temporary output directory. No mod code is executed. Fixtures
 * exercise inherited symbolic owners, dormant callbacks, method handles, arrays,
 * nested dynamic constants, overloads, and invalid fields/methods/constructors.
 */
public final class NativeModMemberContractTest implements Opcodes {
    private static final String PREFIX = "com/openggf/nativefixture/";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected: <temporary-directory>");
        Path root = Path.of(args[0]);
        Files.createDirectories(root);
        Path engine = root.resolve("engine.jar");
        Map<String, byte[]> input = Map.of("Base", host("Base", "java/lang/Object"),
                "Child", host("Child", PREFIX + "Base"), "Dormant", host("Dormant", "java/lang/Object"),
                "Signature", host("Signature", "java/lang/Object"),
                "Unreferenced", host("Unreferenced", PREFIX + "Base"));
        try (var jar = new JarOutputStream(Files.newOutputStream(engine))) {
            for (var entry : input.entrySet()) {
                jar.putNextEntry(new JarEntry(PREFIX + entry.getKey() + ".class"));
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
        Path mods = root.resolve("mods");
        Files.createDirectories(mods);
        writeMod(mods, "valid");
        Path output = root.resolve("valid");
        generate(engine, mods, output);
        var contract = Files.readAllLines(output.resolve("members.tsv"));
        for (String expected : new String[] {
                "F\tcom.openggf.nativefixture.Base\tTOKEN\tLjava/lang/Object;",
                "M\tcom.openggf.nativefixture.Base\tcallback\t(Ljava/lang/String;)Ljava/lang/String;",
                "M\tcom.openggf.nativefixture.Dormant\tfuture\t()V",
                "C\tcom.openggf.nativefixture.Signature"}) {
            if (!contract.contains(expected)) throw new AssertionError("Not retained: " + expected);
        }
        try (var jar = new JarFile(output.resolve("preserved-engine.jar").toFile())) {
            for (var entry : input.entrySet().stream().filter(e -> !e.getKey().equals("Unreferenced")).toList()) {
                try (var stream = jar.getInputStream(jar.getJarEntry(PREFIX + entry.getKey() + ".class"))) {
                    if (!Arrays.equals(entry.getValue(), stream.readAllBytes())) throw new AssertionError("Rewritten class bytes");
                }
            }
        }
        Path implementations = root.resolve("implementations");
        NativeModMemberContract.main(new String[] {engine.toString(), mods.toString(), implementations.toString(),
                "--references-only", "--implementation-root=com.openggf.nativefixture.Base"});
        if (!Files.readAllLines(implementations.resolve("members.tsv")).contains(
                "M\tcom.openggf.nativefixture.Unreferenced\tprobe\t()V"))
            throw new AssertionError("Unreferenced implementation method omitted");
        writeMod(mods, "jdk-fields");
        Path jdk = root.resolve("jdk-fields");
        NativeModMemberContract.main(new String[] {engine.toString(), mods.toString(), jdk.toString(),
                "--references-only", "--jdk-static-fields"});
        var jdkContract = Files.readAllLines(jdk.resolve("members.tsv"));
        for (String expected : new String[] {
                "F\tjava.nio.file.StandardCopyOption\tREPLACE_EXISTING\tLjava/nio/file/StandardCopyOption;",
                "F\tjava.nio.file.LinkOption\tNOFOLLOW_LINKS\tLjava/nio/file/LinkOption;"}) {
            if (!jdkContract.contains(expected)) throw new AssertionError("Dormant JDK static field omitted: " + expected);
        }
        if (!Files.readAllLines(jdk.resolve("jdk-preserve-packages.txt")).equals(java.util.List.of("java.nio.file")))
            throw new AssertionError("JDK field preservation package omitted");
        Path ignoredJdk = root.resolve("ignored-jdk-fields");
        generate(engine, mods, ignoredJdk);
        if (Files.readAllLines(ignoredJdk.resolve("members.tsv")).stream().anyMatch(line -> line.contains("java.nio.file")))
            throw new AssertionError("Opt-in JDK audit changed default contract");
        writeMod(mods, "jdk-missing");
        try {
            NativeModMemberContract.main(new String[] {engine.toString(), mods.toString(), root.resolve("jdk-missing").toString(),
                    "--references-only", "--jdk-static-fields"});
            throw new AssertionError("Accepted missing JDK static field");
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("MISSING input JDK static field")) throw expected;
        }
        writeMod(mods, "jdk-methods");
        Path jdkMethods = root.resolve("jdk-methods");
        NativeModMemberContract.main(new String[] {engine.toString(), mods.toString(), jdkMethods.toString(),
                "--references-only", "--jdk-members"});
        var methodContract = Files.readAllLines(jdkMethods.resolve("members.tsv"));
        for (String expected : new String[] {
                "M\tjava.io.StringWriter\t<init>\t()V",
                "M\tjava.lang.Object\twait\t(J)V"}) {
            if (!methodContract.contains(expected)) throw new AssertionError("Dormant/inherited JDK method omitted: " + expected);
        }
        writeMod(mods, "jdk-constructor");
        try {
            NativeModMemberContract.main(new String[] {engine.toString(), mods.toString(), root.resolve("jdk-constructor").toString(),
                    "--references-only", "--jdk-members"});
            throw new AssertionError("Inherited JDK constructor accepted");
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("MISSING input JDK method")) throw expected;
        }
        // The superclass has (I)V, but the child has only ()V. Constructors must not resolve through inheritance.
        for (String kind : new String[] {"field", "method", "constructor", "type"}) {
            writeMod(mods, kind);
            try {
                generate(engine, mods, root.resolve(kind));
                throw new AssertionError("Accepted missing " + kind);
            } catch (IllegalStateException expected) {
                if (!expected.getMessage().contains("MISSING input engine")) throw expected;
            }
        }
        System.out.println("PASS contract regression controls: inherited members, dormant handles/JDK members, arrays/dynamic constants, exact bytes, missing members and non-inherited constructors");
    }

    private static void generate(Path engine, Path mods, Path output) throws Exception {
        NativeModMemberContract.main(new String[] {engine.toString(), mods.toString(), output.toString(), "--references-only"});
    }

    private static byte[] host(String name, String parent) {
        var writer = new ClassWriter(0);
        writer.visit(V21, ACC_PUBLIC, PREFIX + name, null, parent, null);
        if (name.equals("Base")) {
            writer.visitField(ACC_PUBLIC | ACC_STATIC, "TOKEN", "Ljava/lang/Object;", null, null).visitEnd();
            writer.visitMethod(ACC_PUBLIC | ACC_NATIVE, "callback", "(Ljava/lang/String;)Ljava/lang/String;", null, null).visitEnd();
            writer.visitMethod(ACC_PUBLIC | ACC_NATIVE, "callback", "(I)I", null, null).visitEnd();
            constructor(writer, parent, "(I)V");
        }
        if (name.equals("Dormant")) writer.visitMethod(ACC_PRIVATE | ACC_STATIC | ACC_NATIVE, "future", "()V", null, null).visitEnd();
        if (name.equals("Unreferenced")) writer.visitMethod(ACC_PUBLIC | ACC_NATIVE, "probe", "()V", null, null).visitEnd();
        constructor(writer, parent, "()V");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void constructor(ClassWriter writer, String parent, String descriptor) {
        var method = writer.visitMethod(ACC_PUBLIC, "<init>", descriptor, null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitMethodInsn(INVOKESPECIAL, parent, "<init>", "()V", false);
        method.visitInsn(RETURN);
        method.visitMaxs(1, descriptor.equals("()V") ? 1 : 2);
        method.visitEnd();
    }

    private static void writeMod(Path mods, String mode) throws Exception {
        var writer = new ClassWriter(0);
        writer.visit(V21, ACC_PUBLIC, "fixture/Mod", null, "java/lang/Object", null);
        var method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "laterCallback", "()V", null, null);
        method.visitCode();
        switch (mode) {
            case "jdk-methods" -> {
                method.visitMethodInsn(INVOKESPECIAL, "java/io/StringWriter", "<init>", "()V", false);
                method.visitMethodInsn(INVOKEVIRTUAL, "java/io/StringWriter", "wait", "(J)V", false);
            }
            case "jdk-constructor" -> method.visitMethodInsn(INVOKESPECIAL, "java/io/StringWriter", "<init>", "(Ljava/lang/Object;)V", false);
            case "jdk-fields" -> {
                method.visitFieldInsn(GETSTATIC, "java/nio/file/StandardCopyOption", "REPLACE_EXISTING", "Ljava/nio/file/StandardCopyOption;");
                method.visitLdcInsn(new Handle(H_GETSTATIC, "java/nio/file/LinkOption", "NOFOLLOW_LINKS", "Ljava/nio/file/LinkOption;", false));
            }
            case "jdk-missing" -> method.visitFieldInsn(GETSTATIC, "java/nio/file/StandardCopyOption", "missing", "Ljava/nio/file/StandardCopyOption;");
            case "field" -> method.visitFieldInsn(GETSTATIC, PREFIX + "Child", "missing", "Ljava/lang/Object;");
            case "method" -> method.visitMethodInsn(INVOKEVIRTUAL, PREFIX + "Child", "callback", "(J)I", false);
            case "constructor" -> method.visitMethodInsn(INVOKESPECIAL, PREFIX + "Child", "<init>", "(I)V", false);
            case "type" -> method.visitLdcInsn(Type.getObjectType(PREFIX + "Missing"));
            case "valid" -> {
                method.visitFieldInsn(GETSTATIC, PREFIX + "Child", "TOKEN", "Ljava/lang/Object;");
                method.visitMethodInsn(INVOKEVIRTUAL, PREFIX + "Child", "callback", "(Ljava/lang/String;)Ljava/lang/String;", false);
                method.visitLdcInsn(Type.getType("[[L" + PREFIX + "Signature;"));
                Handle bootstrap = new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "()V", false);
                method.visitInvokeDynamicInsn("run", "()Ljava/lang/Runnable;", bootstrap,
                        Type.getMethodType("()V"), new Handle(H_INVOKESTATIC, PREFIX + "Dormant", "future", "()V", false));
                method.visitLdcInsn(new ConstantDynamic("outer", "Ljava/lang/Object;", bootstrap,
                        new ConstantDynamic("inner", "L" + PREFIX + "Signature;", bootstrap, Type.getObjectType(PREFIX + "Signature"))));
            }
            default -> throw new IllegalArgumentException(mode);
        }
        method.visitInsn(RETURN);
        method.visitMaxs(8, 0);
        method.visitEnd();
        writer.visitEnd();
        try (var jar = new JarOutputStream(Files.newOutputStream(mods.resolve("mod.jar")))) {
            jar.putNextEntry(new JarEntry("fixture/Mod.class"));
            jar.write(writer.toByteArray());
            jar.closeEntry();
        }
    }
}
