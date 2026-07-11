package com.openggf.mods.validation;

import com.openggf.io.ModInputLimits;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/** ASM-only validator for untrusted compiled mods. Never defines an author class. */
public final class ModValidator {
    private static final String GGF_MOD = "com/openggf/mods/code/GgfMod";
    private static final String OBJECT_BASE = "com/openggf/level/objects/AbstractObjectInstance";
    private static final String RECREATABLE = "com/openggf/level/objects/RewindRecreatable";
    private static final String OBJECT_INSTANCE = "com/openggf/level/objects/ObjectInstance";
    private static final String RECREATE_CONTEXT = "com/openggf/level/objects/RewindRecreateContext";
    private static final String OBJECT_REF_ID = "Lcom/openggf/game/rewind/identity/ObjectRefId;";
    private static final Set<String> BUILTIN_API = Set.of(GGF_MOD, OBJECT_BASE, RECREATABLE,
            OBJECT_INSTANCE, RECREATE_CONTEXT, "com/openggf/level/objects/ObjectServices");
    private static final String MOD_API_DESCRIPTOR = "Lcom/openggf/game/ModApi;";
    private static final Map<String, EngineClassInfo> ENGINE_CLASSES = new ConcurrentHashMap<>();
    private static final EngineClassInfo MISSING_ENGINE_CLASS = new EngineClassInfo(null, List.of(), false, 0);

    private final Set<String> trustedApi;
    private final ModInputLimits limits;

    public ModValidator() {
        this(Set.of());
    }

    ModValidator(Set<String> trustedApiInternalNames) {
        this(trustedApiInternalNames, ModInputLimits.production());
    }

    ModValidator(Set<String> trustedApiInternalNames, ModInputLimits limits) {
        this.trustedApi = new HashSet<>(BUILTIN_API);
        Objects.requireNonNull(trustedApiInternalNames, "trustedApiInternalNames")
                .forEach(name -> this.trustedApi.add(normalizeInternalName(name)));
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public ModValidationReport validate(Path jarPath, String entrypointBinaryName) {
        Objects.requireNonNull(jarPath, "jarPath");
        try (InputStream input = Files.newInputStream(jarPath)) {
            if (Files.size(jarPath) > limits.maxJarBytes()) {
                return report(error("JAR_SIZE_LIMIT", "", "", "Jar exceeds size limit"));
            }
            byte[] bounded = input.readNBytes((int) Math.min(Integer.MAX_VALUE, limits.maxJarBytes() + 1));
            if (bounded.length > limits.maxJarBytes()) {
                return report(error("JAR_SIZE_LIMIT", "", "", "Jar exceeds size limit"));
            }
            return validate(bounded, entrypointBinaryName);
        } catch (IOException error) {
            return report(error("JAR_READ_FAILED", "", "", safeMessage(error)));
        }
    }

    public ModValidationReport validate(byte[] jarBytes, String entrypointBinaryName) {
        Objects.requireNonNull(jarBytes, "jarBytes");
        if (jarBytes.length > limits.maxJarBytes()) {
            return report(error("JAR_SIZE_LIMIT", "", "", "Jar exceeds size limit"));
        }
        String entrypoint = normalizeInternalName(Objects.requireNonNull(entrypointBinaryName, "entrypointBinaryName"));
        List<ModValidationFinding> findings = new ArrayList<>();
        Map<String, ClassInfo> classes = readClasses(jarBytes, findings);
        ClassInfo entry = classes.get(entrypoint);
        if (entry == null) {
            findings.add(error("ENTRYPOINT_MISSING", entrypoint, "", "Entrypoint class is absent"));
        } else {
            if (!isAssignable(entry.name, GGF_MOD, classes)
                    || (entry.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT)) != Opcodes.ACC_PUBLIC) {
                findings.add(error("ENTRYPOINT_CONTRACT", entry.name, "", "Entrypoint must be a public concrete GgfMod"));
            }
            if (!entry.publicNoArgConstructor) {
                findings.add(error("ENTRYPOINT_CONSTRUCTOR", entry.name, "<init>", "Entrypoint requires a public no-arg constructor"));
            }
        }

        for (ClassInfo info : classes.values()) {
            validateStatics(info, findings);
            if (isAssignable(info.name, OBJECT_BASE, classes)) validateObject(info, classes, findings);
            for (String reference : info.engineReferences) {
                if (!isTrustedApi(reference) && (!classes.containsKey(reference)
                        || reference.startsWith("com/openggf/"))) {
                    findings.add(warning("NON_API_ENGINE_REFERENCE", info.name, "", reference
                            + " is reachable engine internals without a compatibility promise"));
                }
            }
        }
        findings.sort(java.util.Comparator.comparing(ModValidationFinding::code)
                .thenComparing(ModValidationFinding::className)
                .thenComparing(ModValidationFinding::member)
                .thenComparing(ModValidationFinding::message));
        return new ModValidationReport(findings);
    }

    private boolean isTrustedApi(String internalName) {
        if (trustedApi.contains(internalName)) return true;
        return engineClass(internalName).modApi;
    }

    private Map<String, ClassInfo> readClasses(byte[] jarBytes, List<ModValidationFinding> findings) {
        Map<String, ClassInfo> classes = new LinkedHashMap<>();
        long total = 0;
        int entries = 0;
        try (JarInputStream jar = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
            if (jarBytes.length < 4 || jarBytes[0] != 'P' || jarBytes[1] != 'K') {
                throw new IOException("Jar has no ZIP header");
            }
            JarEntry entry;
            long aggregateNameBytes = 0;
            Set<String> caseFoldedNames = new HashSet<>();
            while ((entry = jar.getNextJarEntry()) != null) {
                if (++entries > limits.maxJarEntries()) throw new IOException("Jar entry count exceeds limit");
                byte[] nameBytes = entry.getName().getBytes(StandardCharsets.UTF_8);
                aggregateNameBytes += nameBytes.length;
                if (nameBytes.length > limits.maxEntryNameBytes()
                        || aggregateNameBytes > limits.maxAggregateEntryNameBytes()) {
                    throw new IOException("Jar entry-name limit exceeded");
                }
                String normalizedEntryName = entry.isDirectory() && entry.getName().endsWith("/")
                        ? entry.getName().substring(0, entry.getName().length() - 1) : entry.getName();
                try { com.openggf.io.ModAssetRoot.requireNormalizedEntry(normalizedEntryName); }
                catch (IllegalArgumentException badPath) { throw new IOException("Unsafe jar entry path", badPath); }
                if (!caseFoldedNames.add(normalizedEntryName.toLowerCase(java.util.Locale.ROOT))) {
                    throw new IOException("Case-colliding jar entries");
                }
                if (entry.isDirectory()) continue;
                byte[] bytes = readBounded(jar, limits.maxAssetBytes());
                total += bytes.length;
                if (total > limits.maxModValidationBytes()) throw new IOException("Validation bytes exceed limit");
                if (!entry.getName().endsWith(".class")) continue;
                try {
                    ClassInfo info = parse(bytes);
                    if (info.name.startsWith("com/openggf/")) {
                        findings.add(error("RESERVED_ENGINE_PACKAGE", info.name, "",
                                "Mods may not define classes in com.openggf"));
                        continue;
                    }
                    String declaredName = entry.getName().substring(0, entry.getName().length() - 6);
                    if (!declaredName.equals(info.name)) {
                        findings.add(error("CLASS_ENTRY_NAME_MISMATCH", info.name, "",
                                "Class internal name does not match jar entry " + entry.getName()));
                        continue;
                    }
                    if (classes.putIfAbsent(info.name, info) != null) {
                        findings.add(error("DUPLICATE_CLASS", info.name, "", "Duplicate class entry"));
                    }
                } catch (RuntimeException malformed) {
                    findings.add(error("MALFORMED_CLASSFILE", entry.getName(), "", safeMessage(malformed)));
                }
            }
        } catch (IOException error) {
            findings.add(error("MALFORMED_JAR", "", "", safeMessage(error)));
        }
        return classes;
    }

    private static byte[] readBounded(InputStream input, long limit) throws IOException {
        byte[] result = input.readNBytes((int) Math.min(Integer.MAX_VALUE, limit + 1));
        if (result.length > limit) throw new IOException("Class entry exceeds limit");
        return result;
    }

    private static ClassInfo parse(byte[] bytes) {
        ClassInfo info = new ClassInfo();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public void visit(int version, int access, String name, String signature,
                                        String superName, String[] interfaces) {
                info.name = name; info.access = access; info.superName = superName;
                if (interfaces != null) info.interfaces.addAll(List.of(interfaces));
                info.reference(superName);
                if (interfaces != null) for (String value : interfaces) info.reference(value);
                info.referenceSignature(signature);
            }

            @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                info.referenceDescriptor(descriptor);
                return info.annotationValues();
            }
            @Override public AnnotationVisitor visitTypeAnnotation(int typeRef, org.objectweb.asm.TypePath path,
                                                                   String descriptor, boolean visible) {
                info.referenceDescriptor(descriptor); return info.annotationValues();
            }

            @Override public FieldVisitor visitField(int access, String name, String descriptor,
                                                     String signature, Object value) {
                FieldInfo field = new FieldInfo(access, name, descriptor, value);
                info.fields.add(field); info.referenceDescriptor(descriptor);
                info.referenceSignature(signature);
                return new FieldVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        field.annotations.add(Type.getType(descriptor).getInternalName());
                        info.referenceDescriptor(descriptor);
                        return info.annotationValues();
                    }
                    @Override public AnnotationVisitor visitTypeAnnotation(int typeRef, org.objectweb.asm.TypePath path,
                                                                           String descriptor, boolean visible) {
                        info.referenceDescriptor(descriptor); return info.annotationValues();
                    }
                };
            }

            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                       String signature, String[] exceptions) {
                info.referenceMethodDescriptor(descriptor);
                info.referenceSignature(signature);
                if (exceptions != null) for (String value : exceptions) info.reference(value);
                if (name.equals("<init>") && descriptor.equals("()V") && (access & Opcodes.ACC_PUBLIC) != 0) {
                    info.publicNoArgConstructor = true;
                }
                if (name.equals("recreateForRewind")
                        && descriptor.equals("(L" + RECREATE_CONTEXT + ";)L" + OBJECT_BASE + ";")
                        && (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT)) == Opcodes.ACC_PUBLIC) {
                    info.validRecreateMethod = true;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    final String methodKey = name + descriptor;
                    @Override public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        info.referenceDescriptor(annotation); return info.annotationValues();
                    }
                    @Override public AnnotationVisitor visitParameterAnnotation(int parameter, String annotation, boolean visible) {
                        info.referenceDescriptor(annotation); return info.annotationValues();
                    }
                    @Override public AnnotationVisitor visitTypeAnnotation(int typeRef, org.objectweb.asm.TypePath path,
                                                                           String annotation, boolean visible) {
                        info.referenceDescriptor(annotation); return info.annotationValues();
                    }
                    @Override public AnnotationVisitor visitInsnAnnotation(int typeRef, org.objectweb.asm.TypePath path,
                                                                           String annotation, boolean visible) {
                        info.referenceDescriptor(annotation); return info.annotationValues();
                    }
                    @Override public AnnotationVisitor visitTryCatchAnnotation(int typeRef, org.objectweb.asm.TypePath path,
                                                                               String annotation, boolean visible) {
                        info.referenceDescriptor(annotation); return info.annotationValues();
                    }
                    @Override public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, org.objectweb.asm.TypePath path,
                            org.objectweb.asm.Label[] start, org.objectweb.asm.Label[] end, int[] index,
                            String annotation, boolean visible) {
                        info.referenceDescriptor(annotation); return info.annotationValues();
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                                                          String desc, boolean isInterface) {
                        info.reference(owner); info.referenceMethodDescriptor(desc);
                        if (method.equals("services")
                                && desc.endsWith("Lcom/openggf/level/objects/ObjectServices;")) {
                            info.servicesMethods.add(methodKey);
                        }
                        if (owner.equals(info.name)) {
                            info.methodCalls.computeIfAbsent(methodKey, ignored -> new HashSet<>()).add(method + desc);
                        }
                    }
                    @Override public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                        info.reference(owner); info.referenceDescriptor(desc);
                        if (opcode == Opcodes.PUTSTATIC && owner.equals(info.name)) info.staticWrites.add(field);
                        if (owner.equals(info.name)) {
                            info.fieldUses.computeIfAbsent(methodKey, ignored -> new HashSet<>()).add(field);
                        }
                    }
                    @Override public void visitTypeInsn(int opcode, String type) { info.reference(type); }
                    @Override public void visitLdcInsn(Object value) {
                        info.referenceValue(value);
                    }
                    @Override public void visitInvokeDynamicInsn(String method, String desc, Handle bootstrap, Object... args) {
                        info.referenceMethodDescriptor(desc); info.referenceHandle(bootstrap);
                        for (Object arg : args) info.referenceValue(arg);
                    }
                    @Override public void visitMultiANewArrayInsn(String desc, int dimensions) { info.referenceDescriptor(desc); }
                    @Override public void visitTryCatchBlock(org.objectweb.asm.Label start, org.objectweb.asm.Label end,
                                                             org.objectweb.asm.Label handler, String type) { info.reference(type); }
                };
            }
        }, ClassReader.SKIP_FRAMES);
        return info;
    }

    private static void validateStatics(ClassInfo info, List<ModValidationFinding> findings) {
        for (FieldInfo field : info.fields) {
            if ((field.access & Opcodes.ACC_STATIC) == 0) continue;
            boolean finalField = (field.access & Opcodes.ACC_FINAL) != 0;
            boolean constantType = field.descriptor.length() == 1 || field.descriptor.equals("Ljava/lang/String;");
            if (!finalField || !constantType || field.value == null || info.staticWrites.contains(field.name)) {
                findings.add(error("STATIC_STATE_UNSUPPORTED", info.name, field.name,
                        "Only literal compile-time primitive/String constants may be static"));
            }
        }
    }

    private static void validateObject(ClassInfo info, Map<String, ClassInfo> classes,
                                       List<ModValidationFinding> findings) {
        if ((info.access & Opcodes.ACC_ABSTRACT) == 0
                && (!isAssignable(info.name, RECREATABLE, classes) || !hasRecreateMethod(info, classes))) {
            findings.add(error("OBJECT_RECREATE_PATH_MISSING", info.name, "",
                    "Mod object must implement RewindRecreatable"));
        }
        if (constructorReachesServices(info)) {
            findings.add(error("CONSTRUCTOR_SERVICES_ACCESS", info.name, "<init>",
                    "Object constructors must not call services()"));
        }
        Set<String> idNames = new HashSet<>();
        for (FieldInfo field : info.fields) if (field.descriptor.equals(OBJECT_REF_ID)) idNames.add(field.name);
        for (FieldInfo field : info.fields) {
            if ((field.access & Opcodes.ACC_STATIC) != 0 || ignoredForRewind(field)) continue;
            if ((field.access & Opcodes.ACC_FINAL) != 0 && isScalar(field.descriptor, classes)) {
                findings.add(error("FINAL_SCALAR_REWIND_GAP", info.name, field.name,
                        "Final scalar instance state cannot be restored generically"));
            }
            if (isObjectReference(field.descriptor, classes)
                    ) {
                String idName = idNames.contains(field.name + "Id") ? field.name + "Id"
                        : idNames.contains(field.name + "RewindId") ? field.name + "RewindId" : null;
                boolean captured = idName != null && info.fieldUses.entrySet().stream()
                        .filter(entry -> isCaptureMethod(entry.getKey()))
                        .anyMatch(entry -> entry.getValue().contains(idName));
                boolean restored = idName != null && info.fieldUses.entrySet().stream()
                        .filter(entry -> isRestoreMethod(entry.getKey()))
                        .anyMatch(entry -> entry.getValue().contains(idName));
                if (!captured || !restored) findings.add(error("OBJECT_REFERENCE_REWIND_ID_MISSING", info.name, field.name,
                        "Object references require a paired rewind id used by capture/restore"));
            }
        }
    }

    private static boolean isCaptureMethod(String key) {
        return key.equals("captureRewindState()Lcom/openggf/game/rewind/PerObjectRewindSnapshot;")
                || key.equals("captureRewindState(Lcom/openggf/game/rewind/schema/RewindCaptureContext;)Lcom/openggf/game/rewind/PerObjectRewindSnapshot;");
    }

    private static boolean isRestoreMethod(String key) {
        return key.equals("restoreRewindState(Lcom/openggf/game/rewind/PerObjectRewindSnapshot;)V")
                || key.equals("restoreRewindState(Lcom/openggf/game/rewind/PerObjectRewindSnapshot;Lcom/openggf/game/rewind/schema/RewindCaptureContext;)V");
    }

    private static boolean constructorReachesServices(ClassInfo info) {
        Set<String> visited = new HashSet<>();
        List<String> pending = new ArrayList<>();
        info.methodCalls.keySet().stream().filter(key -> key.startsWith("<init>"))
                .forEach(pending::add);
        if (info.publicNoArgConstructor) pending.add("<init>()V");
        while (!pending.isEmpty()) {
            String method = pending.remove(pending.size() - 1);
            if (!visited.add(method)) continue;
            if (info.servicesMethods.contains(method)) return true;
            pending.addAll(info.methodCalls.getOrDefault(method, Set.of()));
        }
        return false;
    }

    private static boolean hasRecreateMethod(ClassInfo info, Map<String, ClassInfo> classes) {
        if (info.validRecreateMethod) return true;
        ClassInfo parent = classes.get(info.superName);
        return parent != null && hasRecreateMethod(parent, classes);
    }

    private static boolean ignoredForRewind(FieldInfo field) {
        return field.annotations.contains("com/openggf/game/rewind/RewindTransient")
                || field.annotations.contains("com/openggf/game/rewind/RewindDeferred");
    }

    private static boolean isScalar(String descriptor, Map<String, ClassInfo> classes) {
        if (descriptor.length() == 1) return true;
        if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) return false;
        String name = descriptor.substring(1, descriptor.length() - 1);
        ClassInfo authored = classes.get(name);
        return authored != null ? (authored.access & Opcodes.ACC_ENUM) != 0
                : (engineClass(name).access & Opcodes.ACC_ENUM) != 0;
    }

    private static boolean isObjectReference(String descriptor, Map<String, ClassInfo> classes) {
        if (descriptor.equals("L" + OBJECT_INSTANCE + ";")
                || descriptor.equals("L" + OBJECT_BASE + ";")
                || descriptor.startsWith("Lcom/openggf/sprites/playable/")) return true;
        if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) return false;
        String internalName = descriptor.substring(1, descriptor.length() - 1);
        return isAssignable(internalName, OBJECT_BASE, classes)
                || isAssignable(internalName, OBJECT_INSTANCE, classes);
    }

    private static boolean isAssignable(String name, String target, Map<String, ClassInfo> classes) {
        return isAssignable(name, target, classes, new HashSet<>());
    }

    private static boolean isAssignable(String name, String target, Map<String, ClassInfo> classes,
                                        Set<String> visited) {
        if (name == null || !visited.add(name)) return false;
        if (name.equals(target)) return true;
        ClassInfo info = classes.get(name);
        if (info == null) {
            EngineClassInfo engine = engineClass(name);
            if (engine == MISSING_ENGINE_CLASS) return false;
            if (isAssignable(engine.superName, target, classes, visited)) return true;
            for (String value : engine.interfaces) if (isAssignable(value, target, classes, visited)) return true;
            return false;
        }
        if (isAssignable(info.superName, target, classes, visited)) return true;
        for (String value : info.interfaces) if (isAssignable(value, target, classes, visited)) return true;
        return false;
    }

    private static String normalizeInternalName(String value) {
        return value.replace('.', '/');
    }

    private static EngineClassInfo engineClass(String internalName) {
        if (internalName == null || !internalName.startsWith("com/openggf/")) return MISSING_ENGINE_CLASS;
        return ENGINE_CLASSES.computeIfAbsent(internalName, name -> {
            try (InputStream input = ModValidator.class.getClassLoader().getResourceAsStream(name + ".class")) {
                if (input == null) return MISSING_ENGINE_CLASS;
                final String[] parent = new String[1];
                List<String> interfaces = new ArrayList<>();
                final boolean[] modApi = new boolean[1];
                final int[] access = new int[1];
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public void visit(int version, int flags, String className, String signature,
                                                String superName, String[] values) {
                        access[0] = flags; parent[0] = superName;
                        if (values != null) interfaces.addAll(List.of(values));
                    }
                    @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if (descriptor.equals(MOD_API_DESCRIPTOR)) modApi[0] = true;
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return new EngineClassInfo(parent[0], List.copyOf(interfaces), modApi[0], access[0]);
            } catch (IOException | RuntimeException error) {
                return MISSING_ENGINE_CLASS;
            }
        });
    }

    private static ModValidationFinding error(String code, String owner, String member, String message) {
        return new ModValidationFinding(ModValidationFinding.Severity.ERROR, code, owner, member, message);
    }
    private static ModValidationFinding warning(String code, String owner, String member, String message) {
        return new ModValidationFinding(ModValidationFinding.Severity.WARNING, code, owner, member, message);
    }
    private static ModValidationReport report(ModValidationFinding finding) {
        return new ModValidationReport(List.of(finding));
    }
    private static String safeMessage(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static final class ClassInfo {
        String name; String superName; int access; boolean publicNoArgConstructor;
        boolean constructorServices; boolean validRecreateMethod;
        final List<String> interfaces = new ArrayList<>();
        final List<FieldInfo> fields = new ArrayList<>();
        final Set<String> engineReferences = new HashSet<>();
        final Set<String> staticWrites = new HashSet<>();
        final Set<String> servicesMethods = new HashSet<>();
        final Map<String, Set<String>> methodCalls = new HashMap<>();
        final Map<String, Set<String>> fieldUses = new HashMap<>();
        void reference(String internalName) { if (internalName != null && internalName.startsWith("com/openggf/")) engineReferences.add(internalName); }
        void referenceDescriptor(String descriptor) { referenceType(Type.getType(descriptor)); }
        void referenceMethodDescriptor(String descriptor) {
            Type method = Type.getMethodType(descriptor); referenceType(method.getReturnType());
            for (Type argument : method.getArgumentTypes()) referenceType(argument);
        }
        void referenceType(Type type) {
            if (type.getSort() == Type.OBJECT) reference(type.getInternalName());
            else if (type.getSort() == Type.ARRAY) referenceType(type.getElementType());
        }
        void referenceSignature(String signature) {
            if (signature == null) return;
            SignatureVisitor visitor = new SignatureVisitor(Opcodes.ASM9) {
                @Override public void visitClassType(String name) { reference(name); }
            };
            SignatureReader reader = new SignatureReader(signature);
            try { reader.accept(visitor); }
            catch (IllegalArgumentException error) { reader.acceptType(visitor); }
        }
        AnnotationVisitor annotationValues() {
            return new AnnotationVisitor(Opcodes.ASM9) {
                @Override public void visit(String name, Object value) { referenceValue(value); }
                @Override public void visitEnum(String name, String descriptor, String value) { referenceDescriptor(descriptor); }
                @Override public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                    referenceDescriptor(descriptor); return this;
                }
                @Override public AnnotationVisitor visitArray(String name) { return this; }
            };
        }
        void referenceValue(Object value) {
            if (value instanceof Type type) referenceType(type);
            else if (value instanceof Handle handle) referenceHandle(handle);
            else if (value instanceof ConstantDynamic dynamic) {
                referenceDescriptor(dynamic.getDescriptor()); referenceHandle(dynamic.getBootstrapMethod());
                for (int i = 0; i < dynamic.getBootstrapMethodArgumentCount(); i++) {
                    referenceValue(dynamic.getBootstrapMethodArgument(i));
                }
            }
        }
        void referenceHandle(Handle handle) {
            reference(handle.getOwner());
            int tag = handle.getTag();
            if (tag == Opcodes.H_GETFIELD || tag == Opcodes.H_GETSTATIC
                    || tag == Opcodes.H_PUTFIELD || tag == Opcodes.H_PUTSTATIC) {
                referenceDescriptor(handle.getDesc());
            } else {
                referenceMethodDescriptor(handle.getDesc());
            }
        }
    }
    private record EngineClassInfo(String superName, List<String> interfaces, boolean modApi, int access) { }
    private record FieldInfo(int access, String name, String descriptor, Object value,
                             Set<String> annotations) {
        FieldInfo(int access, String name, String descriptor, Object value) {
            this(access, name, descriptor, value, new HashSet<>());
        }
    }
}
