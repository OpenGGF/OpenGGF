package com.openggf.mods.code;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Classfile annotation serializer, including non-runtime and type-use attributes. */
final class ModApiClassfileAnnotationSurface {
    private ModApiClassfileAnnotationSurface() { }

    static TreeSet<String> lines(Class<?> type) {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing classfile " + resource);
            TreeSet<String> lines = new TreeSet<>();
            new ClassReader(input).accept(visitor(type.getName(), lines),
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return lines;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read annotations for " + type.getName(), exception);
        }
    }

    static TreeSet<String> referencedTypeNames(Class<?> type) {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing classfile " + resource);
            TreeSet<String> references = new TreeSet<>();
            new ClassReader(input).accept(referenceVisitor(references),
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return references;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot audit annotations for " + type.getName(), exception);
        }
    }

    private static ClassVisitor referenceVisitor(TreeSet<String> references) {
        return new ClassVisitor(Opcodes.ASM9) {
            @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                return referenceAnnotation(desc, references);
            }
            @Override public AnnotationVisitor visitTypeAnnotation(
                    int ref, TypePath path, String desc, boolean visible) {
                return referenceAnnotation(desc, references);
            }
            @Override public RecordComponentVisitor visitRecordComponent(
                    String name, String descriptor, String signature) {
                return new RecordComponentVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        return referenceAnnotation(desc, references);
                    }
                    @Override public AnnotationVisitor visitTypeAnnotation(
                            int ref, TypePath path, String desc, boolean visible) {
                        return referenceAnnotation(desc, references);
                    }
                };
            }
            @Override public FieldVisitor visitField(
                    int access, String name, String descriptor, String signature, Object value) {
                if (!supported(access)) return null;
                return new FieldVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        return referenceAnnotation(desc, references);
                    }
                    @Override public AnnotationVisitor visitTypeAnnotation(
                            int ref, TypePath path, String desc, boolean visible) {
                        return referenceAnnotation(desc, references);
                    }
                };
            }
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                if (!supported(access)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        return referenceAnnotation(desc, references);
                    }
                    @Override public AnnotationVisitor visitTypeAnnotation(
                            int ref, TypePath path, String desc, boolean visible) {
                        return referenceAnnotation(desc, references);
                    }
                    @Override public AnnotationVisitor visitParameterAnnotation(
                            int parameter, String desc, boolean visible) {
                        return referenceAnnotation(desc, references);
                    }
                    @Override public AnnotationVisitor visitAnnotationDefault() {
                        return referenceValue(references);
                    }
                };
            }
        };
    }

    private static AnnotationVisitor referenceAnnotation(
            String descriptor, TreeSet<String> references) {
        addType(Type.getType(descriptor), references);
        return referenceValue(references);
    }

    private static AnnotationVisitor referenceValue(TreeSet<String> references) {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override public void visit(String name, Object value) {
                if (value instanceof Type type) addType(type, references);
            }
            @Override public void visitEnum(String name, String descriptor, String value) {
                addType(Type.getType(descriptor), references);
            }
            @Override public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                return referenceAnnotation(descriptor, references);
            }
            @Override public AnnotationVisitor visitArray(String name) {
                return referenceValue(references);
            }
        };
    }

    private static void addType(Type type, TreeSet<String> references) {
        while (type.getSort() == Type.ARRAY) type = type.getElementType();
        if (type.getSort() == Type.OBJECT) references.add(type.getClassName());
    }

    private static ClassVisitor visitor(String owner, TreeSet<String> lines) {
        return new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                return annotation(lines, "CF-ANNOTATION TYPE " + owner, descriptor, visible);
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath,
                    String descriptor, boolean visible) {
                return annotation(lines, typeUse("TYPE " + owner, typeRef, typePath),
                        descriptor, visible);
            }

            @Override
            public RecordComponentVisitor visitRecordComponent(
                    String name, String descriptor, String signature) {
                String target = "RECORD " + owner + " " + name + " " + descriptor;
                return new RecordComponentVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        return annotation(lines, "CF-ANNOTATION " + target, desc, visible);
                    }
                    @Override public AnnotationVisitor visitTypeAnnotation(
                            int typeRef, TypePath path, String desc, boolean visible) {
                        return annotation(lines, typeUse(target, typeRef, path), desc, visible);
                    }
                };
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                    String signature, Object value) {
                if (!supported(access)) return null;
                String target = "FIELD " + owner + " " + name + " " + descriptor;
                return new FieldVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        return annotation(lines, "CF-ANNOTATION " + target, desc, visible);
                    }
                    @Override public AnnotationVisitor visitTypeAnnotation(
                            int typeRef, TypePath path, String desc, boolean visible) {
                        return annotation(lines, typeUse(target, typeRef, path), desc, visible);
                    }
                };
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                if (!supported(access)) return null;
                String target = "METHOD " + owner + " " + name + descriptor;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        return annotation(lines, "CF-ANNOTATION " + target, desc, visible);
                    }
                    @Override public AnnotationVisitor visitTypeAnnotation(
                            int typeRef, TypePath path, String desc, boolean visible) {
                        return annotation(lines, typeUse(target, typeRef, path), desc, visible);
                    }
                    @Override public AnnotationVisitor visitParameterAnnotation(
                            int parameter, String desc, boolean visible) {
                        return annotation(lines, "CF-ANNOTATION PARAMETER " + target + " "
                                + parameter, desc, visible);
                    }
                    @Override public AnnotationVisitor visitAnnotationDefault() {
                        ValueBox box = new ValueBox();
                        return valueVisitor(box, () -> lines.add(
                                "CF-ANNOTATION-DEFAULT " + target + " " + canonical(box.value)));
                    }
                };
            }
        };
    }

    private static String typeUse(String target, int typeRef, TypePath path) {
        return "CF-TYPE-ANNOTATION " + target + " TARGET " + typeRef
                + " PATH " + (path == null ? "-" : path.toString());
    }

    private static boolean supported(int access) {
        return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0;
    }

    private static AnnotationVisitor annotation(TreeSet<String> lines, String target,
            String descriptor, boolean visible) {
        AnnotationNode node = new AnnotationNode(Type.getType(descriptor).getClassName());
        return annotationVisitor(node, () -> lines.add(target + " "
                + (visible ? "visible " : "invisible ") + node.canonical()));
    }

    private static AnnotationVisitor annotationVisitor(AnnotationNode node, Runnable finish) {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override public void visit(String name, Object value) {
                node.values.put(name, normalize(value));
            }
            @Override public void visitEnum(String name, String descriptor, String value) {
                node.values.put(name, new EnumValue(
                        Type.getType(descriptor).getClassName(), value));
            }
            @Override public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                AnnotationNode nested = new AnnotationNode(Type.getType(descriptor).getClassName());
                node.values.put(name, nested);
                return annotationVisitor(nested, () -> { });
            }
            @Override public AnnotationVisitor visitArray(String name) {
                List<Object> values = new ArrayList<>();
                node.values.put(name, values);
                return arrayVisitor(values);
            }
            @Override public void visitEnd() {
                finish.run();
            }
        };
    }

    private static AnnotationVisitor arrayVisitor(List<Object> values) {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override public void visit(String name, Object value) {
                values.add(normalize(value));
            }
            @Override public void visitEnum(String name, String descriptor, String value) {
                values.add(new EnumValue(Type.getType(descriptor).getClassName(), value));
            }
            @Override public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                AnnotationNode nested = new AnnotationNode(Type.getType(descriptor).getClassName());
                values.add(nested);
                return annotationVisitor(nested, () -> { });
            }
            @Override public AnnotationVisitor visitArray(String name) {
                List<Object> nested = new ArrayList<>();
                values.add(nested);
                return arrayVisitor(nested);
            }
        };
    }

    private static AnnotationVisitor valueVisitor(ValueBox box, Runnable finish) {
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override public void visit(String name, Object value) {
                box.value = normalize(value);
            }
            @Override public void visitEnum(String name, String descriptor, String value) {
                box.value = new EnumValue(Type.getType(descriptor).getClassName(), value);
            }
            @Override public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                AnnotationNode nested = new AnnotationNode(Type.getType(descriptor).getClassName());
                box.value = nested;
                return annotationVisitor(nested, () -> { });
            }
            @Override public AnnotationVisitor visitArray(String name) {
                List<Object> values = new ArrayList<>();
                box.value = values;
                return arrayVisitor(values);
            }
            @Override public void visitEnd() {
                finish.run();
            }
        };
    }

    private static Object normalize(Object value) {
        if (value instanceof Type type) return new ClassValue(type.getClassName());
        if (value != null && value.getClass().isArray()) {
            List<Object> values = new ArrayList<>();
            for (int index = 0; index < Array.getLength(value); index++)
                values.add(normalize(Array.get(value, index)));
            return values;
        }
        return value;
    }

    private static String canonical(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return "\"" + escape(text) + "\"";
        if (value instanceof Character character) return "'" + escape(character.toString()) + "'";
        if (value instanceof ClassValue classValue) return classValue.name + ".class";
        if (value instanceof EnumValue enumValue) return enumValue.type + "." + enumValue.value;
        if (value instanceof AnnotationNode annotation) return annotation.canonical();
        if (value instanceof List<?> list)
            return list.stream().map(ModApiClassfileAnnotationSurface::canonical)
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        return String.valueOf(value);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static final class AnnotationNode {
        private final String type;
        private final Map<String, Object> values = new TreeMap<>();
        private AnnotationNode(String type) { this.type = type; }
        private String canonical() {
            if (values.isEmpty()) return "@" + type;
            return values.entrySet().stream()
                    .map(entry -> entry.getKey() + "="
                            + ModApiClassfileAnnotationSurface.canonical(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(",", "@" + type + "(", ")"));
        }
    }

    private record EnumValue(String type, String value) { }
    private record ClassValue(String name) { }
    private static final class ValueBox { private Object value; }
}
