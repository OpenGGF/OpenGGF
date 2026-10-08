package com.openggf.tools;

import com.openggf.mods.code.ModApiSurfaceInventory;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Experimental retention contract; origin: 2026-10-08 native mod follow-up.
 * Inputs: the unmodified engine JAR and trusted mod JARs. Outputs: exact engine
 * class bytes for -H:Preserve=path, and a native member-audit manifest. Includes
 * the canonical API, engine hierarchies, and static engine references in ALL mod
 * methods, including callbacks not executed during registration. It does not
 * infer string-based reflection, generated classes or arbitrary JDK linkage.
 */
public final class NativeModMemberContract {
    private record Member(String name, String descriptor, int access) { }
    private record Info(String parent, List<String> interfaces,
                        List<Member> fields, List<Member> methods) { }

    private final Map<String, byte[]> bytes = new HashMap<>();
    private final Map<String, Info> infos = new HashMap<>();
    private final Set<String> retained = new TreeSet<>();
    private final Set<String> contract = new TreeSet<>();

    public static void main(String[] args) throws Exception {
        if (args.length != 3 && args.length != 4) {
            throw new IllegalArgumentException("Expected: <engine.jar> <mods-directory> <output-directory> [--references-only]");
        }
        boolean referencesOnly = args.length == 4 && args[3].equals("--references-only");
        if (args.length == 4 && !referencesOnly) {
            throw new IllegalArgumentException("Unknown mode: " + args[3]);
        }
        new NativeModMemberContract().generate(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), referencesOnly);
    }

    private void generate(Path engine, Path mods, Path output, boolean referencesOnly) throws Exception {
        try (var jar = new JarFile(engine.toFile())) {
            for (var entry : jar.stream().filter(e -> e.getName().startsWith("com/openggf/")
                    && e.getName().endsWith(".class")).toList()) {
                try (var input = jar.getInputStream(entry)) {
                    bytes.put(entry.getName().substring(0, entry.getName().length() - 6), input.readAllBytes());
                }
            }
        }
        int apiTypes = 0;
        if (!referencesOnly) {
            for (Class<?> api : ModApiSurfaceInventory.annotatedTypes()) {
                // Resolve from the supplied artifact, never from the checkout's target/classes.
                retain(api.getName().replace('.', '/'));
                apiTypes++;
            }
        }
        List<Path> jars;
        try (var paths = Files.list(mods)) {
            jars = paths.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList();
        }
        if (jars.isEmpty()) {
            throw new IllegalArgumentException("No mod JARs in " + mods);
        }
        for (Path path : jars) {
            try (var jar = new JarFile(path.toFile())) {
                for (var entry : jar.stream().filter(e -> e.getName().endsWith(".class")).toList()) {
                    try (var input = jar.getInputStream(entry)) {
                        new ClassReader(input).accept(referenceVisitor(), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    } catch (RuntimeException failure) {
                        throw new IllegalStateException(path.getFileName() + "!" + entry.getName() + ": " + failure.getMessage(), failure);
                    }
                }
            }
        }
        Files.createDirectories(output);
        try (var jar = new JarOutputStream(Files.newOutputStream(output.resolve("preserved-engine.jar")))) {
            for (String name : retained) {
                var entry = new JarEntry(name + ".class");
                entry.setTime(0); // Stable bytes/order: this is a classpath slice, not rebuilt engine code.
                jar.putNextEntry(entry);
                jar.write(bytes.get(name));
                jar.closeEntry();
            }
        }
        Files.write(output.resolve("members.tsv"), contract, StandardCharsets.UTF_8);
        System.out.println("PASS generated contract: api-types=" + apiTypes + "; retained-types="
                + retained.size() + "; member-entries=" + contract.size() + "; mod-jars=" + jars.size());
    }

    private static boolean engine(String name) {
        return name != null && name.startsWith("com/openggf/");
    }

    private Info info(String name) {
        return infos.computeIfAbsent(name, key -> {
            byte[] data = bytes.get(key);
            if (data == null) {
                throw new IllegalStateException("MISSING input engine type: " + key);
            }
            var fields = new ArrayList<Member>();
            var methods = new ArrayList<Member>();
            var reader = new ClassReader(data);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public FieldVisitor visitField(int access, String field, String desc, String signature, Object value) {
                    fields.add(new Member(field, desc, access));
                    return null;
                }
                @Override public MethodVisitor visitMethod(int access, String method, String desc, String signature, String[] exceptions) {
                    methods.add(new Member(method, desc, access));
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return new Info(reader.getSuperName(), List.of(reader.getInterfaces()), fields, methods);
        });
    }

    private void retain(String name) {
        if (!engine(name) || !retained.add(name)) {
            return;
        }
        Info type = info(name);
        contract.add("C\t" + name.replace('/', '.'));
        retain(type.parent());
        type.interfaces().forEach(this::retain);
        for (Member field : type.fields()) {
            if ((field.access() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0) {
                memberLine("F", name, field);
                descriptor(field.descriptor());
            }
        }
        for (Member method : type.methods()) {
            if ((method.access() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0) {
                memberLine("M", name, method);
                descriptor(method.descriptor());
            }
        }
    }

    private void memberLine(String kind, String name, Member member) {
        contract.add(kind + "\t" + name.replace('/', '.') + "\t" + member.name() + "\t" + member.descriptor());
    }

    private void descriptor(String descriptor) {
        type(Type.getType(descriptor));
    }

    private void type(Type type) {
        switch (type.getSort()) {
            case Type.OBJECT -> retain(type.getInternalName());
            case Type.ARRAY -> type(type.getElementType());
            case Type.METHOD -> {
                type(type.getReturnType());
                for (Type argument : type.getArgumentTypes()) type(argument);
            }
            default -> { }
        }
    }

    private String declaring(String owner, String name, String desc, boolean field, Set<String> seen) {
        if (!engine(owner) || !seen.add(owner)) return null;
        Info type = info(owner);
        if ((field ? type.fields() : type.methods()).stream()
                .anyMatch(m -> m.name().equals(name) && m.descriptor().equals(desc))) return owner;
        if (name.equals("<init>")) return null; // Constructors are never inherited.
        // Class methods search superclasses first; fields search interfaces first.
        if (!field) {
            String found = declaring(type.parent(), name, desc, false, seen);
            if (found != null) return found;
        }
        for (String iface : type.interfaces()) {
            String found = declaring(iface, name, desc, field, seen);
            if (found != null) return found;
        }
        return field ? declaring(type.parent(), name, desc, true, seen) : null;
    }

    private void reference(String owner, String name, String desc, boolean field) {
        descriptor(desc);
        if (!engine(owner)) return;
        retain(owner);
        String declared = declaring(owner, name, desc, field, new HashSet<>());
        if (declared == null) {
            // Object's inherited members are compiled in the JDK; not an engine-member omission.
            if (!name.equals("<init>") && inheritedJdkMember(owner, name, desc, field, new HashSet<>())) return;
            throw new IllegalStateException("MISSING input engine " + (field ? "field: " : "method: ")
                    + owner + "." + name + desc);
        }
        retain(declared);
        memberLine(field ? "F" : "M", declared, new Member(name, desc, 0));
    }

    private boolean inheritedJdkMember(String owner, String name, String desc, boolean field, Set<String> seen) {
        if (owner == null || !seen.add(owner)) return false;
        if (engine(owner)) {
            Info type = info(owner);
            if (inheritedJdkMember(type.parent(), name, desc, field, seen)) return true;
            for (String iface : type.interfaces()) if (inheritedJdkMember(iface, name, desc, field, seen)) return true;
            return false;
        }
        try {
            Class<?> type = Class.forName(owner.replace('/', '.'), false, getClass().getClassLoader());
            if (field) {
                for (var candidate : type.getDeclaredFields()) {
                    if (candidate.getName().equals(name) && Type.getDescriptor(candidate.getType()).equals(desc)) return true;
                }
            } else {
                for (var candidate : type.getDeclaredMethods()) {
                    if (candidate.getName().equals(name) && Type.getMethodDescriptor(candidate).equals(desc)) return true;
                }
            }
            if (type.getSuperclass() != null && inheritedJdkMember(Type.getInternalName(type.getSuperclass()), name, desc, field, seen)) return true;
            for (Class<?> iface : type.getInterfaces()) if (inheritedJdkMember(Type.getInternalName(iface), name, desc, field, seen)) return true;
            return false;
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Missing inherited non-engine type: " + owner, exception);
        }
    }

    private void constant(Object value) {
        if (value instanceof Type type) type(type);
        if (value instanceof Handle handle) {
            reference(handle.getOwner(), handle.getName(), handle.getDesc(), handle.getTag() <= Opcodes.H_PUTSTATIC);
        }
        if (value instanceof ConstantDynamic dynamic) {
            descriptor(dynamic.getDescriptor());
            constant(dynamic.getBootstrapMethod());
            for (int i = 0; i < dynamic.getBootstrapMethodArgumentCount(); i++) constant(dynamic.getBootstrapMethodArgument(i));
        }
    }

    private AnnotationVisitor annotation(String desc) {
        descriptor(desc);
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override public void visit(String name, Object value) { constant(value); }
            @Override public void visitEnum(String name, String descriptor, String value) {
                descriptor(descriptor);
                Type enumType = Type.getType(descriptor);
                reference(enumType.getInternalName(), value, descriptor, true);
            }
            @Override public AnnotationVisitor visitAnnotation(String name, String descriptor) { return annotation(descriptor); }
            @Override public AnnotationVisitor visitArray(String name) { return this; }
        };
    }

    private ClassVisitor referenceVisitor() {
        return new ClassVisitor(Opcodes.ASM9) {
            @Override public void visit(int version, int access, String name, String signature, String parent, String[] interfaces) {
                retain(parent);
                for (String iface : interfaces) retain(iface);
            }
            @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) { return annotation(desc); }
            @Override public AnnotationVisitor visitTypeAnnotation(int ref, TypePath path, String desc, boolean visible) { return annotation(desc); }
            @Override public RecordComponentVisitor visitRecordComponent(String name, String desc, String signature) {
                descriptor(desc);
                return new RecordComponentVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) { return annotation(desc); }
                    @Override public AnnotationVisitor visitTypeAnnotation(int ref, TypePath path, String desc, boolean visible) { return annotation(desc); }
                };
            }
            @Override public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                descriptor(desc);
                constant(value);
                return new FieldVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) { return annotation(desc); }
                    @Override public AnnotationVisitor visitTypeAnnotation(int ref, TypePath path, String desc, boolean visible) { return annotation(desc); }
                };
            }
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                descriptor(desc);
                if (exceptions != null) for (String exception : exceptions) retain(exception);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) { return annotation(desc); }
                    @Override public AnnotationVisitor visitAnnotationDefault() { return annotation("Ljava/lang/Object;"); }
                    @Override public AnnotationVisitor visitTypeAnnotation(int ref, TypePath path, String desc, boolean visible) { return annotation(desc); }
                    @Override public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) { return annotation(desc); }
                    @Override public AnnotationVisitor visitInsnAnnotation(int ref, TypePath path, String desc, boolean visible) { return annotation(desc); }
                    @Override public AnnotationVisitor visitTryCatchAnnotation(int ref, TypePath path, String desc, boolean visible) { return annotation(desc); }
                    @Override public void visitTypeInsn(int opcode, String type) {
                        if (type.startsWith("[")) descriptor(type); else retain(type);
                    }
                    @Override public void visitFieldInsn(int opcode, String owner, String name, String desc) { reference(owner, name, desc, true); }
                    @Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) { reference(owner, name, desc, false); }
                    @Override public void visitInvokeDynamicInsn(String name, String desc, Handle bootstrap, Object... arguments) {
                        descriptor(desc);
                        constant(bootstrap);
                        for (Object argument : arguments) constant(argument);
                    }
                    @Override public void visitLdcInsn(Object value) { constant(value); }
                    @Override public void visitMultiANewArrayInsn(String desc, int dimensions) { descriptor(desc); }
                    @Override public void visitTryCatchBlock(org.objectweb.asm.Label start, org.objectweb.asm.Label end,
                                                            org.objectweb.asm.Label handler, String exception) { retain(exception); }
                };
            }
        };
    }
}
