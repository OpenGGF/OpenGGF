package com.openggf.level;

import com.openggf.tests.TestSessionOutputPaths;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestLevelMotionCallerGuard {
    @Test void onlyEngineMayRequestLiveObservationOrPresentationInProduction() throws Exception {
        var violations = new ArrayList<String>();
        try (var files = Files.walk(TestSessionOutputPaths.compiledClasses())) {
            var classes = files.filter(path -> path.toString().endsWith(".class")).toList();
            assertTrue(classes.size() > 1000);
            for (var path : classes) scan(Files.readAllBytes(path), violations);
        }
        assertEquals(List.of(), violations, "Tools and offline consumers must remain exact");
    }
    @Test void rejectsOtherProductionCallersAndMethodReferencesButAllowsZeroCallersAndEngine() {
        for (String caller : List.of("com/openggf/tools/Capture", "com/openggf/Engine$Owner")) {
            var violations = new ArrayList<String>();
            scan(fixture(caller, true), violations);
            assertEquals(2, violations.size());
        }
        var allowed = new ArrayList<String>();
        scan(fixture("com/openggf/Engine", true), allowed);
        scan(fixture("com/openggf/tools/Capture", false), allowed);
        assertEquals(List.of(), allowed);
    }
    private static byte[] fixture(String caller, boolean calls) {
        var writer = new ClassWriter(0); writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, caller, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "draw", "()V", null, null);
        method.visitCode();
        if (calls) {
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "com/openggf/level/LevelMotionAccess", "present", "()V", false);
            method.visitLdcInsn(new Handle(Opcodes.H_INVOKESTATIC, "com/openggf/level/LevelMotionAccess", "observeService", "()V", false));
            method.visitInsn(Opcodes.POP);
        }
        method.visitInsn(Opcodes.RETURN); method.visitMaxs(1, 0); method.visitEnd(); writer.visitEnd(); return writer.toByteArray();
    }
    private static void scan(byte[] bytes, List<String> violations) {
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            String owner;
            @Override public void visit(int version, int access, String name, String signature, String parent, String[] interfaces) { owner = name; }
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    private void call(String target, String method) {
                        if (target.equals("com/openggf/level/LevelMotionAccess")
                                && (method.equals("present") || method.equals("observeService")) && !owner.equals("com/openggf/Engine"))
                            violations.add(owner + "#" + name + " -> " + method);
                    }
                    @Override public void visitMethodInsn(int opcode, String target, String method, String desc, boolean isInterface) { call(target, method); }
                    @Override public void visitLdcInsn(Object value) { if (value instanceof Handle handle) call(handle.getOwner(), handle.getName()); }
                    @Override public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) {
                        for (var value : arguments) if (value instanceof Handle handle) call(handle.getOwner(), handle.getName());
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
}
