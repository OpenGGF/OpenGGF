package com.openggf.mods.validation;

import com.openggf.mods.code.ModApiSignatureSurface;
import org.objectweb.asm.*;
import org.objectweb.asm.signature.*;
import java.io.*;
import java.util.*;

/** Engine-owned classfile contract. Creator annotations never establish authority. */
public final class SupportedModContract {
    public static final String HINT = "use or add a supported facade; see the design doc";
    private record Member(boolean consumable) { }
    private record Shape(String parent, List<String> interfaces, boolean api, Map<String, Member> members) { }
    private final Set<String> reachable = new HashSet<>();
    private final Map<String, Shape> shapes = new HashMap<>();

    public SupportedModContract() {
        for (Class<?> type : ModApiSignatureSurface.recursiveTypes()) if (type.isAnnotationPresent(com.openggf.game.ModApi.class)) reachable.add(type.getName().replace('.', '/'));
    }
    public boolean supportedType(String name) {
        name = name.replace('.', '/');
        return !engine(name) || reachable.contains(name);
    }
    public static boolean engine(String name) { return name.startsWith("com/openggf/"); }

    /** Resolve symbolic owners, including inherited bytecode references, to their declaration. */
    public boolean consumableMember(String owner, String name, String descriptor) {
        String declaration = declaringOwner(owner,name,descriptor);
        return declaration != null && !forbiddenOperation(declaration,name)
                && supportedType(declaration) && shape(declaration).members.get(name+descriptor).consumable;
    }
    public String declaringOwner(String owner, String name, String descriptor) {
        return declaringOwner(owner,name,descriptor,new HashSet<>());
    }
    private String declaringOwner(String owner, String name, String descriptor, Set<String> seen) {
        if (owner == null || !seen.add(owner)) return null;
        Shape shape = shape(owner);
        if (shape.members.containsKey(name+descriptor)) return owner;
        if (name.equals("<init>")) return null;
        String inherited = declaringOwner(shape.parent,name,descriptor,seen);
        if (inherited != null) return inherited;
        for (String parent : shape.interfaces) {
            inherited = declaringOwner(parent,name,descriptor,seen);
            if (inherited != null) return inherited;
        }
        return null;
    }
    public static boolean forbiddenOperation(String owner, String name) {
        return owner.startsWith("java/lang/reflect/") || owner.startsWith("java/lang/invoke/MethodHandle")
                || owner.equals("java/lang/Class") && (name.equals("forName") || name.startsWith("getDeclared")
                || name.equals("getMethod") || name.equals("getField") || name.equals("getConstructor") || name.equals("newInstance") || name.equals("getClassLoader"))
                || owner.endsWith("ClassLoader") || owner.equals("java/util/ServiceLoader") && name.startsWith("load") || owner.endsWith("/Unsafe")
                || (owner.equals("java/lang/System") || owner.equals("java/lang/Runtime")) && (name.equals("load") || name.equals("loadLibrary"));
    }
    public static Set<String> signatureTypes(String descriptor, String signature, String[] exceptions) {
        Set<String> names = new HashSet<>();
        if (descriptor != null) {
            if (descriptor.startsWith("(")) {
                for (Type t : Type.getArgumentTypes(descriptor)) add(t, names);
                add(Type.getReturnType(descriptor), names);
            } else add(Type.getType(descriptor), names);
        }
        if (exceptions != null) names.addAll(List.of(exceptions));
        if (signature != null) {
            SignatureVisitor visitor = signatureVisitor(names);
            SignatureReader reader = new SignatureReader(signature);
            if (signature.startsWith("T") || signature.startsWith("[")) reader.acceptType(visitor);
            else reader.accept(visitor);
        }
        return names;
    }
    private static SignatureVisitor signatureVisitor(Set<String> names) {
        return new SignatureVisitor(Opcodes.ASM9) {
            String outer;
            @Override public void visitClassType(String name) { outer=name; names.add(name); }
            @Override public void visitInnerClassType(String name) { outer += "$"+name; names.add(outer); }
            @Override public SignatureVisitor visitTypeArgument(char wildcard) { return signatureVisitor(names); }
            @Override public SignatureVisitor visitArrayType() { return signatureVisitor(names); }
        };
    }
    private static SignatureVisitor boundVisitor(Set<String> names) {
        return new SignatureVisitor(Opcodes.ASM9) {
            String outer;
            @Override public void visitClassType(String n) { outer=n; names.add(n); }
            @Override public void visitInnerClassType(String n) { outer += "$"+n; names.add(outer); }
            @Override public void visitTypeVariable(String n) { names.add("@"+n); }
            @Override public SignatureVisitor visitTypeArgument(char w) { return boundVisitor(names); }
            @Override public SignatureVisitor visitArrayType() { return boundVisitor(names); }
        };
    }
    private static void add(Type t, Set<String> names) {
        if (t.getSort() == Type.ARRAY) add(t.getElementType(), names);
        else if (t.getSort() == Type.OBJECT) names.add(t.getInternalName());
    }
    private Shape shape(String name) { return shapes.computeIfAbsent(name, this::read); }
    private Shape read(String name) {
        try (InputStream input = SupportedModContract.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) return new Shape(null, List.of(), false, Map.of());
            String[] parent = new String[1];
            List<String> interfaces = new ArrayList<>();
            boolean[] marked = {false};
            Map<String, Member> members = new HashMap<>();
            Map<String,Set<String>> classBounds = new HashMap<>();
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public void visit(int v, int a, String n, String s, String p, String[] i) {
                    parent[0] = p; interfaces.addAll(List.of(i));
                    if (s != null) new SignatureReader(s).accept(new SignatureVisitor(Opcodes.ASM9) {
                        String parameter;
                        @Override public void visitFormalTypeParameter(String n) { parameter=n; classBounds.put(n,new HashSet<>()); }
                        @Override public SignatureVisitor visitClassBound() { return boundVisitor(classBounds.get(parameter)); }
                        @Override public SignatureVisitor visitInterfaceBound() { return boundVisitor(classBounds.get(parameter)); }
                        @Override public SignatureVisitor visitSuperclass() { return new SignatureVisitor(Opcodes.ASM9) { }; }
                        @Override public SignatureVisitor visitInterface() { return new SignatureVisitor(Opcodes.ASM9) { }; }
                    });
                }
                @Override public AnnotationVisitor visitAnnotation(String d, boolean v) {
                    if (d.equals("Lcom/openggf/game/ModApi;")) marked[0] = true;
                    return null;
                }
                private boolean allowed(int a, String d, String s, String[] e) {
                    Set<String> names = signatureTypes(d,s,e);
                    if (s != null) {
                        Set<String> variables = new HashSet<>();
                        Set<String> local = new HashSet<>();
                        SignatureVisitor vars = new SignatureVisitor(Opcodes.ASM9) {
                            @Override public void visitFormalTypeParameter(String n) { local.add(n); }
                            @Override public void visitTypeVariable(String n) { variables.add(n); }
                        };
                        if (s.startsWith("T") || s.startsWith("[")) new SignatureReader(s).acceptType(vars);
                        else new SignatureReader(s).accept(vars);
                        variables.removeAll(local);
                        Set<String> seen = new HashSet<>();
                        ArrayDeque<String> pending = new ArrayDeque<>(variables);
                        while (!pending.isEmpty()) {
                            String variable = pending.remove();
                            if (!seen.add(variable)) continue;
                            for (String bound : classBounds.getOrDefault(variable,Set.of())) {
                                if (bound.startsWith("@")) pending.add(bound.substring(1));
                                else names.add(bound);
                            }
                        }
                    }
                    return (a & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0
                            && names.stream().allMatch(SupportedModContract.this::supportedType);
                }
                @Override public FieldVisitor visitField(int a, String n, String d, String s, Object v) {
                    members.put(n + d, new Member(allowed(a,d,s,null)));
                    return new FieldVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                            if (desc.equals("Lcom/openggf/game/EngineOnly;")) members.put(n+d, new Member(false));
                            return null;
                        }
                    };
                }
                @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                    members.put(n+d, new Member(allowed(a,d,s,e)));
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                            if (desc.equals("Lcom/openggf/game/EngineOnly;")) members.put(n+d, new Member(false));
                            return null;
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return new Shape(parent[0], List.copyOf(interfaces), marked[0], members);
        } catch (IOException e) { throw new IllegalStateException("Cannot inspect engine " + name, e); }
    }
}
