package com.openggf.game.presentation;

import org.objectweb.asm.*;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Counts actual row-loop increments in a renamed copy of production bytecode.
 * No bounds logic is reproduced and the production class is never modified.
 */
final class CompositorRowProbe {
    private static final String ORIGINAL = "com/openggf/game/presentation/SceneCompositor";
    private static final String COPY = ORIGINAL + "RowProbe";
    private static final Class<?> MEASURED = instrument();
    record Result(SceneImage image, long rows) { }

    static synchronized Result compose(ScenePresentationFrame frame, RomSceneArtCatalog art) throws Exception {
        var counter = MEASURED.getField("rows"); counter.setLong(null, 0);
        var image = (SceneImage)MEASURED.getMethod("compose", ScenePresentationFrame.class,
                RomSceneArtCatalog.class, int.class, int.class).invoke(null, frame, art, 0, 0);
        return new Result(image, counter.getLong(null));
    }

    private static Class<?> instrument() {
        try (var input = SceneCompositor.class.getResourceAsStream("SceneCompositor.class")) {
            if (input == null) throw new IllegalStateException("Missing production compositor bytecode");
            var reader = new ClassReader(input);
            Map<String, Set<Integer>> rowLocals = new HashMap<>();
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitLocalVariable(String variable, String type, String signature, Label start, Label end, int index) {
                            if (variable.equals("y") && (name.equals("compose") || name.equals("drawPrimitive")))
                                rowLocals.computeIfAbsent(name, ignored -> new HashSet<>()).add(index);
                        }
                    };
                }
            }, 0);
            if (!rowLocals.keySet().equals(Set.of("compose", "drawPrimitive")))
                throw new IllegalStateException("Expected production row-loop debug locals in both raster passes");
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public void visit(int version, int access, String name, String signature, String parent, String[] interfaces) {
                    super.visit(version, access, COPY, signature, parent, interfaces);
                }
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                        @Override public void visitIincInsn(int variable, int increment) {
                            if (rowLocals.getOrDefault(name, Set.of()).contains(variable)) {
                                super.visitFieldInsn(Opcodes.GETSTATIC, COPY, "rows", "J");
                                super.visitInsn(Opcodes.LCONST_1); super.visitInsn(Opcodes.LADD);
                                super.visitFieldInsn(Opcodes.PUTSTATIC, COPY, "rows", "J");
                            }
                            super.visitIincInsn(variable, increment);
                        }
                        @Override public void visitMethodInsn(int opcode, String owner, String method, String type, boolean isInterface) {
                            super.visitMethodInsn(opcode, owner.equals(ORIGINAL) ? COPY : owner, method, type, isInterface);
                        }
                    };
                }
                @Override public void visitEnd() {
                    super.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "rows", "J", null, null).visitEnd();
                    super.visitEnd();
                }
            }, 0);
            return MethodHandles.lookup().defineClass(writer.toByteArray());
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
}
