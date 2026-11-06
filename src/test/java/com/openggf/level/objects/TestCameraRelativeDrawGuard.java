package com.openggf.level.objects;

import com.openggf.tests.TestSessionOutputPaths;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Derive camera-reader inventory from compiled producers and their object helpers in both directions. */
class TestCameraRelativeDrawGuard {
    private static final String ANNOTATION = Type.getDescriptor(CameraRelativeDraw.class);
    private record Call(String owner, String name, String descriptor) { }
    private record Body(List<Call> calls) { }
    private static final class Definition {
        String name, parent;
        boolean annotated;
        final Map<Call, Body> methods = new HashMap<>();
    }
    @Test void productionCameraReadersDeclareTheirProjectionPolicy() throws Exception {
        var definitions = read(TestSessionOutputPaths.compiledClasses());
        assertTrue(definitions.size() > 1000, "Complete compiled tree required");
        assertEquals(List.of(), discrepancies(definitions));
    }
    @Test void rejectsMissingAndStaleMetadataIncludingHelperHiddenReads() throws Exception {
        var classes = read(TestSessionOutputPaths.compiledTestClasses());
        for (var fixture : List.of(Direct.class, Hidden.class, Stale.class, Inherited.class)) {
            var chosen = new HashMap<String, Definition>();
            var def = classes.get(Type.getInternalName(fixture));
            chosen.put(def.name, def);
            var base = classes.get(def.parent);
            if (base != null) chosen.put(base.name, base);
            assertFalse(discrepancies(chosen).isEmpty(), fixture.getName());
        }
        var valid = classes.get(Type.getInternalName(Declared.class));
        assertEquals(List.of(), discrepancies(Map.of(valid.name, valid)));
        assertFalse(CameraRelativeDraw.class.isAnnotationPresent(java.lang.annotation.Inherited.class));
    }
    private static List<String> discrepancies(Map<String, Definition> definitions) {
        var differences = new ArrayList<String>();
        for (var definition : definitions.values()) {
            var roots = roots(definition, definitions);
            if (roots.isEmpty()) continue;
            boolean reads = roots.stream().anyMatch(root -> readsCamera(root, definition.name, definitions, new HashSet<>()));
            if (reads != definition.annotated)
                differences.add(definition.name.replace('/', '.') + (reads ? " requires @CameraRelativeDraw" : " has stale @CameraRelativeDraw"));
        }
        return differences.stream().sorted().toList();
    }
    private static List<Call> roots(Definition definition, Map<String, Definition> definitions) {
        for (var current = definition; current != null; current = definitions.get(current.parent)) {
            var roots = current.methods.keySet().stream().filter(call -> call.name.equals("appendRenderCommands")).toList();
            if (!roots.isEmpty()) return roots;
        }
        return List.of();
    }
    private static boolean readsCamera(Call call, String root, Map<String, Definition> definitions, Set<Call> visited) {
        if (call.owner.equals("com/openggf/camera/Camera") && Set.of("getX", "getY", "getXCopy", "getYCopy",
                "getXWithShake", "getYWithShake").contains(call.name)) return true;
        if (!visited.add(call)) return false;
        var owner = definitions.get(call.owner);
        if (owner == null) return false;
        var body = owner.methods.get(call);
        if (body == null && owner.parent != null)
            return readsCamera(new Call(owner.parent, call.name, call.descriptor), root, definitions, visited);
        if (body == null) return false;
        for (var next : body.calls) {
            if (next.owner.equals("com/openggf/camera/Camera")) {
                if (readsCamera(next, root, definitions, visited)) return true;
            } else if (inHierarchy(next.owner, root, definitions)
                    && readsCamera(next, root, definitions, visited)) return true;
        }
        return false;
    }
    private static boolean inHierarchy(String owner, String root, Map<String, Definition> definitions) {
        for (String current = root; current != null;) {
            if (current.equals(owner)) return true;
            var definition = definitions.get(current);
            current = definition == null ? null : definition.parent;
        }
        return false;
    }
    private static Map<String, Definition> read(Path path) throws Exception {
        var definitions = new HashMap<String, Definition>();
        try (var files = Files.walk(path)) {
            for (var file : files.filter(value -> value.toString().endsWith(".class")).toList()) {
                var definition = new Definition();
                new ClassReader(Files.readAllBytes(file)).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public void visit(int version, int access, String name, String signature, String parent, String[] interfaces) {
                        definition.name = name; definition.parent = parent;
                    }
                    @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if (descriptor.equals(ANNOTATION)) definition.annotated = true;
                        return null;
                    }
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        var body = new Body(new ArrayList<>());
                        definition.methods.put(new Call(definition.name, name, descriptor), body);
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                                body.calls.add(new Call(owner, name, descriptor));
                            }
                            @Override public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) {
                                for (var argument : arguments) if (argument instanceof Handle handle)
                                    body.calls.add(new Call(handle.getOwner(), handle.getName(), handle.getDesc()));
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                definitions.put(definition.name, definition);
            }
        }
        return definitions;
    }
    private static class Direct {
        com.openggf.camera.Camera camera;
        public void appendRenderCommands(List<?> commands) { camera.getX(); }
    }
    private static class Hidden {
        com.openggf.camera.Camera camera;
        public void appendRenderCommands(List<?> commands) { first(); }
        private void first() { second(); }
        private void second() { camera.getYWithShake(); }
    }
    @CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
    private static class Stale { public void appendRenderCommands(List<?> commands) { } }
    @CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
    private static class Declared {
        com.openggf.camera.Camera camera;
        public void appendRenderCommands(List<?> commands) { camera.getX(); }
    }
    private static class Inherited extends Declared {
        @Override public void appendRenderCommands(List<?> commands) { super.appendRenderCommands(commands); }
    }
}
