package com.openggf.level.objects;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Syntax-level lifecycle debt inventory, scoped to the object-package guard.
 * Uses the JDK parser without type attribution or loading gameplay classes.
 * Keys retain the file, lexical type/member chain (including overload types),
 * and operation. Lambdas belong to their enclosing member; anonymous types
 * retain their constructed type and enclosing member. Counts within a caller
 * are explicit, so deleting one call cannot buy an unrelated new caller.
 */
final class ObjectLifecycleCallAudit {
    private static final Set<String> DYNAMIC_ADDS = Set.of(
            "addDynamicObject", "addDynamicObjectAfterCurrent", "addDynamicObjectAfterCurrentNextFrame",
            "addDynamicObjectAfterSlot", "addDynamicObjectNextFrame");

    private ObjectLifecycleCallAudit() {}

    static Map<String, Integer> scan(Map<String, String> sources) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("Lifecycle audit requires a JDK compiler");
        }
        Map<String, Integer> counts = new TreeMap<>();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<JavaFileObject> inputs = new ArrayList<>();
            Map<java.net.URI, String> paths = new TreeMap<>();
            for (var source : new TreeMap<>(sources).entrySet()) {
                var uri = Path.of(source.getKey()).toUri();
                paths.put(uri, source.getKey());
                inputs.add(new SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return source.getValue();
                    }
                });
            }
            JavacTask task = (JavacTask) compiler.getTask(
                    null, fileManager, diagnostics, List.of("-proc:none", "--release", "25"), null, inputs);
            for (CompilationUnitTree unit : task.parse()) {
                String path = paths.get(unit.getSourceFile().toUri());
                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
                        String operation = operation(call);
                        if (operation != null) {
                            counts.merge(path + "#" + caller(getCurrentPath()) + "->" + operation, 1, Integer::sum);
                        }
                        return super.visitMethodInvocation(call, unused);
                    }
                }.scan(unit, null);
            }
        }
        List<String> errors = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(Object::toString).toList();
        if (!errors.isEmpty()) {
            throw new IOException("Lifecycle audit could not parse sources:\n" + String.join("\n", errors));
        }
        return counts;
    }

    static List<String> differences(Map<String, Integer> actual, Map<String, Integer> baseline) {
        Set<String> keys = new TreeSet<>(baseline.keySet());
        keys.addAll(actual.keySet());
        List<String> differences = new ArrayList<>();
        for (String key : keys) {
            int observed = actual.getOrDefault(key, 0);
            int accepted = baseline.getOrDefault(key, 0);
            if (observed != accepted) {
                differences.add(key + " observed=" + observed + " baseline=" + accepted
                        + (observed < accepted ? " (resolved debt: tighten baseline)" : " (new/increased debt)"));
            }
        }
        return differences;
    }

    /** Baseline rows are key TAB positive count TAB a reviewable migration/bridge reason. */
    static Map<String, Integer> readBaseline(List<String> lines) {
        Map<String, Integer> baseline = new TreeMap<>();
        for (String line : lines) {
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length != 3 || parts[0].isBlank() || parts[2].isBlank()) {
                throw new IllegalArgumentException("Lifecycle baseline requires key, count and reason: " + line);
            }
            int count = Integer.parseInt(parts[1]);
            if (count <= 0 || baseline.putIfAbsent(parts[0], count) != null) {
                throw new IllegalArgumentException("Lifecycle baseline has nonpositive count or duplicate key: " + line);
            }
        }
        return baseline;
    }

    private static String operation(MethodInvocationTree call) {
        ExpressionTree select = call.getMethodSelect();
        String name = select instanceof MemberSelectTree member ? member.getIdentifier().toString()
                : select instanceof IdentifierTree identifier ? identifier.getName().toString() : "";
        if (DYNAMIC_ADDS.contains(name)) {
            return name;
        }
        if (name.equals("setDestroyed") && call.getArguments().size() == 1) {
            ExpressionTree argument = call.getArguments().getFirst();
            while (argument instanceof ParenthesizedTree parens) {
                argument = parens.getExpression();
            }
            if (argument instanceof LiteralTree literal && Boolean.TRUE.equals(literal.getValue())) {
                return "setDestroyed(true)";
            }
        }
        return null;
    }

    private static String caller(TreePath path) {
        List<String> scopes = new ArrayList<>();
        for (TreePath current = path; current != null; current = current.getParentPath()) {
            var leaf = current.getLeaf();
            var parent = current.getParentPath() == null ? null : current.getParentPath().getLeaf();
            if (leaf instanceof MethodTree method) {
                String typeParameters = method.getTypeParameters().isEmpty() ? "" : "<"
                        + method.getTypeParameters().stream()
                        .map(parameter -> parameter.toString().replaceAll("\\s+", ""))
                        .collect(Collectors.joining(",")) + ">";
                scopes.add(typeParameters + method.getName() + "(" + method.getParameters().stream()
                        .map(parameter -> parameter.getType().toString().replaceAll("\\s+", ""))
                        .collect(Collectors.joining(",")) + ")");
            } else if (leaf instanceof ClassTree type) {
                String name = type.getSimpleName().toString();
                if (name.isEmpty()) {
                    name = parent instanceof NewClassTree creation
                            ? "<anonymous:" + creation.getIdentifier().toString().replaceAll("\\s+", "") + ">"
                            : "<anonymous>";
                }
                scopes.add(name);
            } else if (leaf instanceof VariableTree field && parent instanceof ClassTree) {
                scopes.add("field:" + field.getName());
            } else if (leaf instanceof BlockTree block && parent instanceof ClassTree) {
                scopes.add(block.isStatic() ? "<clinit>" : "<init-block>");
            }
        }
        Collections.reverse(scopes);
        return String.join("#", scopes);
    }
}
