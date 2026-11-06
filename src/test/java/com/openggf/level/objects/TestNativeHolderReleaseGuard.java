package com.openggf.level.objects;

import com.openggf.tests.SourceScanExecutor;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import org.junit.jupiter.api.Test;

import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

/** Source acquisition audit, including nested/P2 owners; inheritance is resolved on compiled classes. */
class TestNativeHolderReleaseGuard {
    private record Holder(String name, Set<String> writes) { }

    @Test void everyNativeObjectAcquiringPlayerControlHasAReleaseHook() throws Exception {
        var missing = new ArrayList<String>();
        var audited = new ArrayList<String>();
        List<Callable<List<Holder>>> scans;
        try (var paths = Files.walk(Path.of("src/main/java/com/openggf/game"))) {
            scans = paths.filter(p -> p.toString().endsWith(".java")).sorted()
                    .<Callable<List<Holder>>>map(path -> () -> holders(Files.readString(path)))
                    .toList();
        }
        for (List<Holder> owners : SourceScanExecutor.invokeOrdered(scans, 2)) {
            for (Holder holder : owners) {
                Class<?> type = Class.forName(holder.name(), false, getClass().getClassLoader());
                if (!ObjectInstance.class.isAssignableFrom(type)) continue;
                audited.add(holder.name());
                if (type.getMethod("releasePlayersForVisualDirection").getDeclaringClass() == ObjectInstance.class)
                    missing.add(holder.name() + " " + holder.writes());
            }
        }
        System.out.println("Native holder audit: " + String.join(", ", audited));
        assertTrue(audited.size() > 100, "The audit must see the native object population, including nested owners");
        assertEquals(List.of(), missing, "Native acquisition writers need an owned player-only release hook");
    }

    @Test void scannerSeparatesNestedWritersFromOuterHooksAndIgnoresCommentsAndClears() throws Exception {
        String source = """
                package sample;
                class Outer {
                    void releasePlayersForVisualDirection() { }
                    // player.setControlLocked(true);
                    class P2 {
                        void tick() { player.setControlLocked(true); player.setForcedInputMask(RIGHT); }
                    }
                    void clear() { player.setControlLocked(false); player.setForcedInputMask(0); }
                }
                """;
        var expected = List.of(new Holder("sample.Outer$P2", Set.of("setControlLocked", "setForcedInputMask")));
        assertEquals(expected, holders(source));
        assertEquals(List.of(expected, List.of(new Holder("other.Outer", Set.of("setJumpInputPressed")))),
                SourceScanExecutor.invokeOrdered(List.<Callable<List<Holder>>>of(
                        () -> holders(source),
                        () -> holders("package other; class Outer { void tick() { player.setJumpInputPressed(true); } }")), 2),
                "parallel parsers must retain their own lexical owner and write inventory");
    }

    private static List<Holder> holders(String source) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Holder audit requires the repository JDK");
        var input = new SimpleJavaFileObject(URI.create("string:///Holder.java"), javax.tools.JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
        };
        try (var files = compiler.getStandardFileManager(null, null, null)) {
            var task = (JavacTask) compiler.getTask(null, files, null, List.of("-proc:none"), null, List.of(input));
            var result = new ArrayList<Holder>();
            for (var unit : task.parse()) new TreeScanner<Void, Void>() {
                String owner = unit.getPackageName().toString();
                Set<String> writes;
                @Override public Void visitClass(ClassTree tree, Void ignored) {
                    String previous = owner;
                    var previousWrites = writes;
                    // Anonymous callbacks execute on their lexical object's ownership surface.
                    if (tree.getSimpleName().isEmpty()) return super.visitClass(tree, ignored);
                    owner = previous + (writes == null ? "." : "$") + tree.getSimpleName();
                    writes = new LinkedHashSet<>();
                    super.visitClass(tree, ignored);
                    if (!writes.isEmpty()) result.add(new Holder(owner, Set.copyOf(writes)));
                    owner = previous;
                    writes = previousWrites;
                    return null;
                }
                @Override public Void visitMethodInvocation(MethodInvocationTree call, Void ignored) {
                    if (writes != null && acquires(call))
                        writes.add(((MemberSelectTree) call.getMethodSelect()).getIdentifier().toString());
                    return super.visitMethodInvocation(call, ignored);
                }
            }.scan(unit, null);
            return List.copyOf(result);
        }
    }

    private static boolean acquires(MethodInvocationTree call) {
        if (!(call.getMethodSelect() instanceof MemberSelectTree select)) return false;
        String method = select.getIdentifier().toString();
        var args = call.getArguments();
        if (method.equals("applyTo")) {
            String state = select.getExpression().toString();
            return state.contains("ObjectControlState.") && !state.endsWith(".none()") && !state.endsWith(".NONE");
        }
        if (Set.of("setControlLocked", "setObjectControlled", "setObjectControlSuppressesMovement",
                "setForceInputRight", "setForcedJumpPress", "setController2SignedLocked").contains(method))
            return !args.isEmpty() && !args.getFirst().toString().equals("false");
        if (method.equals("setForcedInputMask")) return !args.isEmpty() && !args.getFirst().toString().equals("0");
        if (method.equals("setMovementSuppressionPreservingOwnership"))
            return args.size() == 2 && !args.get(1).toString().equals("false");
        if (Set.of("setController2Input", "setLogicalInputState", "setDirectionalInputPressed", "setJumpInputPressed").contains(method))
            return args.stream().anyMatch(arg -> !Set.of("0", "false").contains(arg.toString()));
        if (method.equals("queueNativeEndingPoseForNextPlayerSlot")) return true;
        return false;
    }
}
