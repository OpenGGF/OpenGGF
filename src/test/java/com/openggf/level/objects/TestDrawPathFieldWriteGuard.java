package com.openggf.level.objects;

import com.openggf.tests.TestSessionOutputPaths;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/** Draws may populate reviewed render caches, but must not advance object state. */
class TestDrawPathFieldWriteGuard {
    private static final String DRAW_DESCRIPTOR = "(Ljava/util/List;)V";

    @Test void productionDrawPathsMatchReviewedCachesAndFrozenLogicalDebt() throws Exception {
        Map<String, Body> methods = read(TestSessionOutputPaths.compiledClasses());
        Map<String, Integer> writes = scan(methods);
        assertTrue(methods.keySet().stream().filter(key -> key.contains("#appendRenderCommands")).count() > 500,
                "The complete compiled production tree is required");
        var allowed = new TreeMap<>(baseline("reviewed-caches.txt"));
        baseline("logical-debt.txt").forEach((field, count) ->
                assertNull(allowed.put(field, count), "Debt must not be labelled a render cache: " + field));
        assertInventory(writes, allowed);
    }

    @Test void rejectsDirectHelperStaticAndOuterWrites() throws Exception {
        Map<String, Body> methods = read(TestSessionOutputPaths.compiledTestClasses());
        for (Class<?> fixture : List.of(DirectWrite.class, HelperWrite.class, StaticWrite.class, OuterWrite.Draw.class)) {
            var writes = scan(only(methods, fixture));
            assertFalse(writes.isEmpty(), fixture.getName());
            assertThrows(AssertionError.class, () -> assertInventory(writes, Map.of()));
        }
    }

    @Test void acceptsOnlyTheExactReviewedCacheAndRequiresRetiredEntriesToBeRemoved() throws Exception {
        var writes = scan(only(read(TestSessionOutputPaths.compiledTestClasses()), ReviewedCache.class));
        var allowed = Map.of(ReviewedCache.class.getName() + "#decodedFrames", 1);
        assertInventory(writes, allowed);
        assertThrows(AssertionError.class, () -> assertInventory(Map.of(), allowed));
        assertThrows(AssertionError.class, () -> assertInventory(
                Map.of(ReviewedCache.class.getName() + "#decodedFrames", 2), allowed));
    }

    private static Map<String, Body> only(Map<String, Body> methods, Class<?> fixture) {
        var selected = new TreeMap<String, Body>();
        String owner = fixture.getName().replace('.', '/');
        methods.forEach((key, body) -> { if (body.owner.equals(owner)) selected.put(key, body); });
        return selected;
    }

    private static Map<String, Integer> baseline(String resource) throws IOException {
        var counts = new TreeMap<String, Integer>();
        try (var input = TestDrawPathFieldWriteGuard.class.getResourceAsStream("/architecture/draw-purity/" + resource)) {
            assertNotNull(input, "Required draw-purity inventory: " + resource);
            for (String line : new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList()) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] columns = line.split(" ", 4);
                assertEquals(4, columns.length, "Every entry needs a one-line review reason: " + line);
                assertEquals("#", columns[2]);
                int count = Integer.parseInt(columns[0]);
                assertTrue(count > 0);
                assertNull(counts.put(columns[1], count), "Duplicate draw field: " + line);
            }
        }
        return counts;
    }

    private static void assertInventory(Map<String, Integer> actual, Map<String, Integer> approved) {
        var fields = new TreeSet<>(actual.keySet());
        fields.addAll(approved.keySet());
        var differences = fields.stream()
                .filter(field -> !actual.getOrDefault(field, 0).equals(approved.getOrDefault(field, 0)))
                .map(field -> field + " observed=" + actual.getOrDefault(field, 0)
                        + " baseline=" + approved.getOrDefault(field, 0)).toList();
        assertEquals(List.of(), differences,
                "Move logical writes to update. Review render caches explicitly; shrink logical debt and remove retired entries.");
    }

    private record Write(String owner, String field) { }
    private record Call(String owner, String name, String descriptor) {
        String key() { return owner + "#" + name + descriptor; }
    }
    private static final class Body {
        final String owner;
        final String name;
        final String descriptor;
        final boolean privateMethod;
        final List<Write> writes = new ArrayList<>();
        final List<Call> calls = new ArrayList<>();
        Body(String owner, String name, String descriptor, int access) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            privateMethod = (access & Opcodes.ACC_PRIVATE) != 0;
        }
    }

    private static Map<String, Body> read(Path classes) throws IOException {
        assertTrue(Files.isDirectory(classes), "Missing compiled classes: " + classes);
        var methods = new TreeMap<String, Body>();
        try (var files = Files.walk(classes)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                new ClassReader(Files.readAllBytes(file)).accept(new ClassVisitor(Opcodes.ASM9) {
                    private String owner;
                    @Override public void visit(int version, int access, String name, String signature,
                                                String superName, String[] interfaces) { owner = name; }
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                               String signature, String[] exceptions) {
                        var body = new Body(owner, name, descriptor, access);
                        methods.put(new Call(owner, name, descriptor).key(), body);
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitFieldInsn(int opcode, String fieldOwner, String field, String desc) {
                                if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC)
                                    body.writes.add(new Write(fieldOwner, field));
                            }
                            @Override public void visitMethodInsn(int opcode, String callOwner, String method,
                                                                  String desc, boolean isInterface) {
                                body.calls.add(new Call(callOwner, method, desc));
                            }
                            @Override public void visitInvokeDynamicInsn(String name, String descriptor,
                                                                         Handle bootstrap, Object... arguments) {
                                for (Object argument : arguments) if (argument instanceof Handle handle)
                                    body.calls.add(new Call(handle.getOwner(), handle.getName(), handle.getDesc()));
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        return methods;
    }

    private static Map<String, Integer> scan(Map<String, Body> methods) {
        var counts = new TreeMap<String, Integer>();
        var visited = new HashSet<String>();
        for (var entry : methods.entrySet()) {
            Body root = entry.getValue();
            if (!root.name.equals("appendRenderCommands") || !root.descriptor.equals(DRAW_DESCRIPTOR)) continue;
            var pending = new ArrayDeque<String>();
            pending.add(entry.getKey());
            while (!pending.isEmpty()) {
                String key = pending.removeFirst();
                if (!visited.add(key)) continue;
                Body body = methods.get(key);
                for (Write write : body.writes) {
                    if (root.owner.equals(write.owner) || root.owner.startsWith(write.owner + "$"))
                        counts.merge(write.owner.replace('/', '.') + "#" + write.field, 1, Integer::sum);
                }
                for (Call call : body.calls) {
                    Body target = methods.get(call.key());
                    if (target != null && target.owner.equals(root.owner) && target.privateMethod)
                        pending.add(call.key());
                }
            }
        }
        return counts;
    }

    private static class DirectWrite {
        int phase;
        public void appendRenderCommands(List<?> commands) { phase++; }
    }
    private static class HelperWrite {
        int phase;
        public void appendRenderCommands(List<?> commands) { first(); }
        private void first() { second(); }
        private void second() { phase++; firstIfNever(); }
        private void firstIfNever() { if (phase < 0) first(); }
    }
    private static class StaticWrite {
        static int phase;
        public void appendRenderCommands(List<?> commands) { phase++; }
    }
    private static class OuterWrite {
        int phase;
        private class Draw {
            public void appendRenderCommands(List<?> commands) { phase++; }
        }
    }
    private static class ReviewedCache {
        Object decodedFrames;
        public void appendRenderCommands(List<?> commands) {
            if (decodedFrames == null) decodedFrames = new Object();
        }
    }
}
