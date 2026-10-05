package com.openggf.mods.validation;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Non-executing recognizer for javac's immutable enum constants and enum switch maps.
 * Flags/names alone never grant an exception: every instruction that can run during
 * initialization, including called enum constructors/array factories, must match.
 */
final class CompilerConstantStatics {
    private static final int MAX_CAPTURED_INSTRUCTIONS = 16_384;
    private static final String STRING = "Ljava/lang/String;";

    private CompilerConstantStatics() { }

    static final class Shape {
        String name;
        String parent;
        int access;
        boolean hasInterfaces;
        boolean complete = true;
        boolean writesOutsideClinit;
        boolean writesOutsideConstructor;
        boolean exposesValuesArray;
        int captured;
        final List<Field> fields = new ArrayList<>();
        final Map<String, Method> methods = new LinkedHashMap<>();
    }

    private record Field(int access, String name, String descriptor, Object constant) { }
    private record Instruction(int opcode, String owner, String name, String descriptor, int operand, Object value) { }
    private record Guard(Label start, Label end, Label handler, String type) { }
    private static final class Method {
        final int access;
        final String name;
        final String descriptor;
        final List<Instruction> instructions = new ArrayList<>();
        final Map<Label, Integer> labels = new HashMap<>();
        final List<Guard> guards = new ArrayList<>();
        Method(int access, String name, String descriptor) {
            this.access = access; this.name = name; this.descriptor = descriptor;
        }
    }

    static Shape inspect(byte[] bytes) {
        Shape shape = new Shape();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public void visit(int version, int access, String name, String signature, String parent, String[] interfaces) {
                shape.name = name; shape.parent = parent; shape.access = access;
                shape.hasInterfaces = interfaces != null && interfaces.length != 0;
            }
            @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                shape.fields.add(new Field(access, name, descriptor, value)); return null;
            }
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                Method method = new Method(access, name, descriptor);
                if (shape.methods.putIfAbsent(name + descriptor, method) != null) shape.complete = false;
                boolean capture = name.equals("<clinit>") || name.equals("<init>") || name.equals("values") || name.equals("$values");
                return new MethodVisitor(Opcodes.ASM9) {
                    private void add(int opcode, String owner, String member, String desc, int operand, Object value) {
                        if (!capture) return;
                        if (++shape.captured > MAX_CAPTURED_INSTRUCTIONS) { shape.complete = false; return; }
                        method.instructions.add(new Instruction(opcode, owner, member, desc, operand, value));
                    }
                    @Override public void visitInsn(int opcode) { add(opcode, null, null, null, 0, null); }
                    @Override public void visitIntInsn(int opcode, int operand) { add(opcode, null, null, null, operand, null); }
                    @Override public void visitVarInsn(int opcode, int variable) { add(opcode, null, null, null, variable, null); }
                    @Override public void visitTypeInsn(int opcode, String type) { add(opcode, type, null, null, 0, null); }
                    @Override public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                        if (owner.equals(shape.name)) {
                            if (opcode == Opcodes.PUTSTATIC && !name.equals("<clinit>")) shape.writesOutsideClinit = true;
                            if (opcode == Opcodes.PUTFIELD && !name.equals("<init>")) shape.writesOutsideConstructor = true;
                            if (opcode == Opcodes.GETSTATIC && field.equals("$VALUES")
                                    && !(name.equals("values") && descriptor.equals("()" + desc))) shape.exposesValuesArray = true;
                        }
                        add(opcode, owner, field, desc, 0, null);
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String member, String desc, boolean isInterface) {
                        add(opcode, owner, member, desc, 0, isInterface);
                    }
                    @Override public void visitLdcInsn(Object value) { add(Opcodes.LDC, null, null, null, 0, value); }
                    @Override public void visitJumpInsn(int opcode, Label label) { add(opcode, null, null, null, 0, label); }
                    @Override public void visitLabel(Label label) { if (capture) method.labels.put(label, method.instructions.size()); }
                    @Override public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                        if (capture) method.guards.add(new Guard(start, end, handler, type));
                    }
                    @Override public void visitIincInsn(int variable, int increment) { add(Opcodes.IINC, null, null, null, variable, increment); }
                    @Override public void visitInvokeDynamicInsn(String member, String desc, org.objectweb.asm.Handle bootstrap, Object... arguments) {
                        add(Opcodes.INVOKEDYNAMIC, null, member, desc, 0, bootstrap);
                    }
                    @Override public void visitTableSwitchInsn(int min, int max, Label fallback, Label... labels) {
                        add(Opcodes.TABLESWITCH, null, null, null, 0, null);
                    }
                    @Override public void visitLookupSwitchInsn(Label fallback, int[] keys, Label[] labels) {
                        add(Opcodes.LOOKUPSWITCH, null, null, null, 0, null);
                    }
                    @Override public void visitMultiANewArrayInsn(String desc, int dimensions) {
                        add(Opcodes.MULTIANEWARRAY, null, null, desc, dimensions, null);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return shape;
    }

    static Set<String> immutableEnumFields(Shape shape) {
        if (!shape.complete || shape.hasInterfaces || !"java/lang/Enum".equals(shape.parent)
                || (shape.access & (Opcodes.ACC_ENUM | Opcodes.ACC_FINAL)) != (Opcodes.ACC_ENUM | Opcodes.ACC_FINAL)
                || shape.writesOutsideClinit || shape.writesOutsideConstructor || shape.exposesValuesArray) return Set.of();
        String descriptor = "L" + shape.name + ";", array = "[" + descriptor;
        List<Field> constants = new ArrayList<>();
        Map<String, Field> instance = new LinkedHashMap<>();
        boolean arrayField = false;
        Set<String> fieldNames = new HashSet<>();
        for (Field field : shape.fields) {
            if (!fieldNames.add(field.name)) return Set.of();
            if ((field.access & Opcodes.ACC_STATIC) == 0) {
                if ((field.access & Opcodes.ACC_FINAL) == 0 || !scalar(field.descriptor)) return Set.of();
                if (instance.putIfAbsent(field.name, field) != null) return Set.of();
            } else if ((field.access & Opcodes.ACC_ENUM) != 0) {
                int expected = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM;
                if (field.access != expected || !field.descriptor.equals(descriptor) || field.constant != null) return Set.of();
                constants.add(field);
            } else if (field.name.equals("$VALUES")) {
                int expected = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC;
                if (arrayField || field.access != expected || !field.descriptor.equals(array) || field.constant != null) return Set.of();
                arrayField = true;
            } else if (!literalField(field)) return Set.of();
        }
        if (!arrayField || shape.methods.containsKey("ordinal()I")) return Set.of();
        boolean constructor = false;
        for (Method method : shape.methods.values()) if (method.name.equals("<init>")) {
            constructor = true;
            if (!enumConstructor(shape, method, instance)) return Set.of();
        }
        if (!constructor || !enumValues(shape, array) || !enumArray(shape, array, constants)
                || !enumInitializer(shape, descriptor, array, constants)) return Set.of();
        Set<String> result = new HashSet<>();
        constants.forEach(field -> result.add(field.name)); result.add("$VALUES");
        return Set.copyOf(result);
    }

    private static boolean enumConstructor(Shape shape, Method method, Map<String, Field> fields) {
        if (method.access != Opcodes.ACC_PRIVATE || !method.guards.isEmpty()) return false;
        Type[] args = Type.getArgumentTypes(method.descriptor);
        if (args.length < 2 || !args[0].getDescriptor().equals(STRING) || args[1].getSort() != Type.INT
                || Type.getReturnType(method.descriptor).getSort() != Type.VOID) return false;
        Map<Integer, Type> variables = new HashMap<>();
        int variable = 1;
        for (Type arg : args) {
            if (!scalar(arg.getDescriptor())) return false;
            variables.put(variable, arg); variable += arg.getSize();
        }
        Cursor code = new Cursor(method);
        if (!code.variable(Opcodes.ALOAD, 0) || !code.variable(Opcodes.ALOAD, 1) || !code.variable(Opcodes.ILOAD, 2)
                || !code.call(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V")) return false;
        Set<String> assigned = new HashSet<>();
        while (code.has() && code.peek().opcode != Opcodes.RETURN) {
            if (!code.variable(Opcodes.ALOAD, 0)) return false;
            Instruction source = code.take();
            Instruction write = code.take();
            if (source == null || write == null || write.opcode != Opcodes.PUTFIELD || !shape.name.equals(write.owner)) return false;
            Field field = fields.get(write.name);
            if (field == null || !write.descriptor.equals(field.descriptor) || !assigned.add(field.name)) return false;
            Type type = Type.getType(field.descriptor);
            Type parameter = variables.get(source.operand);
            boolean parameterLoad = parameter != null && parameter.getDescriptor().equals(type.getDescriptor())
                    && source.opcode == type.getOpcode(Opcodes.ILOAD);
            if (!parameterLoad && !literal(source, type)) return false;
        }
        return assigned.equals(fields.keySet()) && code.op(Opcodes.RETURN) && code.end();
    }

    private static boolean enumValues(Shape shape, String array) {
        Method method = shape.methods.get("values()" + array);
        if (method == null || method.access != (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC) || !method.guards.isEmpty()) return false;
        Cursor code = new Cursor(method);
        return code.field(Opcodes.GETSTATIC, shape.name, "$VALUES", array)
                && code.call(Opcodes.INVOKEVIRTUAL, array, "clone", "()Ljava/lang/Object;")
                && code.type(Opcodes.CHECKCAST, array) && code.op(Opcodes.ARETURN) && code.end();
    }

    private static boolean enumArray(Shape shape, String array, List<Field> constants) {
        Method method = shape.methods.get("$values()" + array);
        if (method == null || method.access != (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC)
                || !method.guards.isEmpty()) return false;
        Cursor code = new Cursor(method);
        if (!code.integer(constants.size()) || !code.type(Opcodes.ANEWARRAY, shape.name)) return false;
        for (int i = 0; i < constants.size(); i++) {
            if (!code.op(Opcodes.DUP) || !code.integer(i)
                    || !code.field(Opcodes.GETSTATIC, shape.name, constants.get(i).name, constants.get(i).descriptor)
                    || !code.op(Opcodes.AASTORE)) return false;
        }
        return code.op(Opcodes.ARETURN) && code.end();
    }

    private static boolean enumInitializer(Shape shape, String descriptor, String array, List<Field> constants) {
        Method method = shape.methods.get("<clinit>()V");
        if (method == null || method.access != Opcodes.ACC_STATIC || !method.guards.isEmpty()) return false;
        Cursor code = new Cursor(method);
        for (int i = 0; i < constants.size(); i++) {
            Field field = constants.get(i);
            if (!code.type(Opcodes.NEW, shape.name) || !code.op(Opcodes.DUP) || !code.string(field.name) || !code.integer(i)) return false;
            // Parameters must all be literal pushes followed by a constructor already validated above.
            List<Instruction> parameters = new ArrayList<>();
            while (code.has() && code.peek().opcode != Opcodes.INVOKESPECIAL) parameters.add(code.take());
            Instruction call = code.take();
            if (call == null || !shape.name.equals(call.owner) || !"<init>".equals(call.name)
                    || !shape.methods.containsKey("<init>" + call.descriptor) || Boolean.TRUE.equals(call.value)) return false;
            Type[] args = Type.getArgumentTypes(call.descriptor);
            if (args.length != parameters.size() + 2) return false;
            for (int parameter = 0; parameter < parameters.size(); parameter++) {
                if (!literal(parameters.get(parameter), args[parameter + 2])) return false;
            }
            if (!code.field(Opcodes.PUTSTATIC, shape.name, field.name, descriptor)) return false;
        }
        return code.call(Opcodes.INVOKESTATIC, shape.name, "$values", "()" + array)
                && code.field(Opcodes.PUTSTATIC, shape.name, "$VALUES", array) && code.op(Opcodes.RETURN) && code.end();
    }

    static Set<String> enumSwitchFields(Shape shape, Map<String, Shape> enums) {
        if (!shape.complete || shape.hasInterfaces || (shape.access & Opcodes.ACC_SYNTHETIC) == 0 || !"java/lang/Object".equals(shape.parent)
                || shape.fields.isEmpty() || shape.writesOutsideClinit || shape.methods.size() != 1) return Set.of();
        Set<String> fields = new HashSet<>();
        for (Field field : shape.fields) {
            int expected = Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC;
            if (field.access != expected || !field.descriptor.equals("[I") || field.constant != null
                    || !field.name.startsWith("$SwitchMap$") || !fields.add(field.name)) return Set.of();
        }
        Method method = shape.methods.get("<clinit>()V");
        if (method == null || method.access != Opcodes.ACC_STATIC) return Set.of();
        Cursor code = new Cursor(method);
        Set<String> initialized = new HashSet<>();
        Set<Guard> usedGuards = new HashSet<>();
        while (code.has() && code.peek().opcode != Opcodes.RETURN) {
            Instruction allocation = code.take();
            if (allocation == null || allocation.opcode != Opcodes.INVOKESTATIC || !"values".equals(allocation.name)
                    || Boolean.TRUE.equals(allocation.value)) return Set.of();
            Shape enumeration = enums.get(allocation.owner);
            String enumArray = "[L" + allocation.owner + ";";
            if (enumeration == null || !allocation.descriptor.equals("()" + enumArray)) return Set.of();
            String field = "$SwitchMap$" + allocation.owner.replace('/', '$');
            if (!fields.contains(field) || !initialized.add(field) || !code.op(Opcodes.ARRAYLENGTH)
                    || !code.intInsn(Opcodes.NEWARRAY, Opcodes.T_INT)
                    || !code.field(Opcodes.PUTSTATIC, shape.name, field, "[I")) return Set.of();
            Set<String> cases = new HashSet<>();
            Set<Integer> caseValues = new HashSet<>();
            while (code.has() && code.peek().opcode == Opcodes.GETSTATIC) {
                int start = code.index;
                if (!code.field(Opcodes.GETSTATIC, shape.name, field, "[I")) return Set.of();
                Instruction constant = code.take();
                if (constant == null || constant.opcode != Opcodes.GETSTATIC || !allocation.owner.equals(constant.owner)
                        || !constant.descriptor.equals("L" + allocation.owner + ";") || !cases.add(constant.name)
                        || enumeration.fields.stream().noneMatch(f -> f.name.equals(constant.name) && (f.access & Opcodes.ACC_ENUM) != 0)
                        || !code.call(Opcodes.INVOKEVIRTUAL, allocation.owner, "ordinal", "()I")) return Set.of();
                Integer caseValue = integerValue(code.take());
                long constantCount = enumeration.fields.stream().filter(f -> (f.access & Opcodes.ACC_ENUM) != 0).count();
                if (caseValue == null || caseValue < 1 || caseValue > constantCount || !caseValues.add(caseValue)
                        || !code.op(Opcodes.IASTORE)) return Set.of();
                int end = code.index;
                Instruction jump = code.take();
                int handler = code.index;
                if (jump == null || jump.opcode != Opcodes.GOTO || !code.variable(Opcodes.ASTORE, 0)
                        || !Integer.valueOf(code.index).equals(method.labels.get(jump.value))) return Set.of();
                Guard guard = method.guards.stream().filter(g -> "java/lang/NoSuchFieldError".equals(g.type)
                        && Integer.valueOf(start).equals(method.labels.get(g.start))
                        && Integer.valueOf(end).equals(method.labels.get(g.end))
                        && Integer.valueOf(handler).equals(method.labels.get(g.handler))).findFirst().orElse(null);
                if (guard == null || !usedGuards.add(guard)) return Set.of();
            }
        }
        return initialized.equals(fields) && usedGuards.size() == method.guards.size()
                && code.op(Opcodes.RETURN) && code.end() ? Set.copyOf(fields) : Set.of();
    }

    private static boolean scalar(String descriptor) {
        return (descriptor.length() == 1 && "ZBCSIJFD".contains(descriptor)) || descriptor.equals(STRING);
    }
    private static boolean literalField(Field field) {
        return (field.access & Opcodes.ACC_FINAL) != 0 && scalar(field.descriptor) && field.constant != null;
    }
    private static Integer integerValue(Instruction instruction) {
        if (instruction == null) return null;
        if (instruction.opcode >= Opcodes.ICONST_M1 && instruction.opcode <= Opcodes.ICONST_5) return instruction.opcode - Opcodes.ICONST_0;
        if (instruction.opcode == Opcodes.BIPUSH || instruction.opcode == Opcodes.SIPUSH) return instruction.operand;
        return instruction.opcode == Opcodes.LDC && instruction.value instanceof Integer value ? value : null;
    }
    private static boolean literal(Instruction instruction, Type type) {
        if (instruction == null) return false;
        return switch (type.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> integerValue(instruction) != null;
            case Type.LONG -> instruction.opcode == Opcodes.LCONST_0 || instruction.opcode == Opcodes.LCONST_1
                    || instruction.opcode == Opcodes.LDC && instruction.value instanceof Long;
            case Type.FLOAT -> instruction.opcode >= Opcodes.FCONST_0 && instruction.opcode <= Opcodes.FCONST_2
                    || instruction.opcode == Opcodes.LDC && instruction.value instanceof Float;
            case Type.DOUBLE -> instruction.opcode == Opcodes.DCONST_0 || instruction.opcode == Opcodes.DCONST_1
                    || instruction.opcode == Opcodes.LDC && instruction.value instanceof Double;
            case Type.OBJECT -> type.getDescriptor().equals(STRING) && (instruction.opcode == Opcodes.ACONST_NULL
                    || instruction.opcode == Opcodes.LDC && instruction.value instanceof String);
            default -> false;
        };
    }

    private static final class Cursor {
        final List<Instruction> code;
        int index;
        Cursor(Method method) { code = method.instructions; }
        boolean has() { return index < code.size(); }
        Instruction peek() { return code.get(index); }
        Instruction take() { return has() ? code.get(index++) : null; }
        boolean end() { return index == code.size(); }
        boolean op(int opcode) { Instruction value = take(); return value != null && value.opcode == opcode; }
        boolean variable(int opcode, int variable) { Instruction value = take(); return value != null && value.opcode == opcode && value.operand == variable; }
        boolean intInsn(int opcode, int operand) { return variable(opcode, operand); }
        boolean type(int opcode, String type) { Instruction value = take(); return value != null && value.opcode == opcode && type.equals(value.owner); }
        boolean field(int opcode, String owner, String name, String descriptor) {
            Instruction value = take(); return value != null && value.opcode == opcode && owner.equals(value.owner)
                    && name.equals(value.name) && descriptor.equals(value.descriptor);
        }
        boolean call(int opcode, String owner, String name, String descriptor) {
            Instruction value = take(); return value != null && value.opcode == opcode && owner.equals(value.owner)
                    && name.equals(value.name) && descriptor.equals(value.descriptor) && !Boolean.TRUE.equals(value.value);
        }
        boolean integer(int expected) { return Integer.valueOf(expected).equals(integerValue(take())); }
        boolean string(String expected) { Instruction value = take(); return value != null && value.opcode == Opcodes.LDC && expected.equals(value.value); }
    }
}
