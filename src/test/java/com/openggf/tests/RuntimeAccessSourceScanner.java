package com.openggf.tests;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/** Syntax-only runtime-access inventory, using the lexical caller identities from ObjectLifecycleCallAudit. */
public final class RuntimeAccessSourceScanner {
    private RuntimeAccessSourceScanner() {}

    public record Access(String path, String caller, String operation) implements Comparable<Access> {
        @Override
        public int compareTo(Access other) {
            return toString().compareTo(other.toString());
        }

        @Override
        public String toString() {
            return path + "#" + caller + "->" + operation;
        }
    }

    public static Map<String, String> readRequiredSources(Path sourceRoot, String[] packages) throws IOException {
        Map<String, String> sources = new TreeMap<>();
        for (String pkg : packages) {
            Path directory = sourceRoot.resolve(pkg);
            if (!Files.isDirectory(directory) || !Files.isReadable(directory)) {
                throw new IOException("Required source package is missing or unreadable: " + directory);
            }
            List<Path> paths;
            try (var files = Files.walk(directory)) {
                paths = files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
            }
            if (paths.isEmpty()) {
                throw new IOException("Required source package contains no Java inputs: " + directory);
            }
            for (Path path : paths) {
                sources.put(sourceRoot.relativize(path).toString().replace('\\', '/'), Files.readString(path));
            }
        }
        return sources;
    }

    public static Map<Access, Integer> scan(Map<String, String> sources, Collection<String> patterns)
            throws IOException {
        if (sources.isEmpty()) return Map.of();
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IOException("Runtime access audit requires a JDK compiler");
        Map<Access, Integer> counts = new TreeMap<>();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<JavaFileObject> inputs = new ArrayList<>();
            Map<java.net.URI, String> paths = new HashMap<>();
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
            JavacTask task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("-proc:none", "--release", "25"), null, inputs);
            for (CompilationUnitTree unit : task.parse()) {
                String path = paths.get(unit.getSourceFile().toUri());
                new TreePathScanner<Void, Void>() {
                    private final Map<ClassTree, String> typeNames = new IdentityHashMap<>();
                    private final Map<String, Integer> declarationCounts = new HashMap<>();

                    @Override
                    public Void visitClass(ClassTree type, Void unused) {
                        TreePath parentPath = getCurrentPath().getParentPath();
                        var parent = parentPath == null ? null : parentPath.getLeaf();
                        String name = type.getSimpleName().toString();
                        if (!(parent instanceof CompilationUnitTree) && !(parent instanceof ClassTree)) {
                            String base = name.isEmpty()
                                    ? "anonymous:" + (parent instanceof NewClassTree creation
                                        ? compact(creation.getIdentifier().toString()) : "unknown")
                                    : "local:" + name;
                            int ordinal = declarationCounts.merge(caller(parentPath, typeNames) + "#" + base,
                                    1, Integer::sum);
                            name = "<" + base + ":" + ordinal + ">";
                        }
                        typeNames.put(type, name);
                        return super.visitClass(type, unused);
                    }

                    @Override
                    public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
                        record(compact(call.getMethodSelect().toString())
                                + (call.getArguments().isEmpty() ? "()" : "(" + call.getArguments().size() + " args)"), false);
                        return super.visitMethodInvocation(call, unused);
                    }

                    @Override
                    public Void visitMemberSelect(MemberSelectTree member, Void unused) {
                        var parent = getCurrentPath().getParentPath().getLeaf();
                        if (!(parent instanceof MethodInvocationTree call && call.getMethodSelect() == member)) {
                            record(compact(member.toString()), false);
                        }
                        return super.visitMemberSelect(member, unused);
                    }

                    @Override
                    public Void visitMemberReference(MemberReferenceTree reference, Void unused) {
                        record(compact(reference.getQualifierExpression().toString()) + "."
                                + reference.getName() + "::reference", true);
                        return super.visitMemberReference(reference, unused);
                    }

                    private void record(String expression, boolean methodReference) {
                        // A fully qualified owner has the same policy identity as its imported short name.
                        for (String pattern : patterns) {
                            boolean invocationPattern = methodReference && pattern.endsWith("(");
                            String matchedPattern = invocationPattern
                                    ? pattern.substring(0, pattern.length() - 1) : pattern;
                            int offset = expression.indexOf(matchedPattern);
                            if (offset >= 0 && (offset == 0 || expression.charAt(offset - 1) == '.')
                                    && (invocationPattern
                                        ? expression.substring(offset + matchedPattern.length()).equals("::reference")
                                        : !expression.substring(offset + matchedPattern.length()).contains("."))) {
                                counts.merge(new Access(path, caller(getCurrentPath(), typeNames),
                                        expression.substring(offset)), 1, Integer::sum);
                                break;
                            }
                        }
                    }
                }.scan(unit, null);
            }
        }
        var errors = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR).map(Object::toString).toList();
        if (!errors.isEmpty()) throw new IOException("Runtime access audit could not parse sources:\n"
                + String.join("\n", errors));
        return counts;
    }

    public static List<String> differences(Map<Access, Integer> actual, Map<Access, Integer> expected) {
        Set<Access> keys = new TreeSet<>(expected.keySet());
        keys.addAll(actual.keySet());
        List<String> differences = new ArrayList<>();
        for (Access key : keys) {
            int observed = actual.getOrDefault(key, 0);
            int approved = expected.getOrDefault(key, 0);
            if (observed != approved) {
                differences.add(key + " observed=" + observed + " approved=" + approved);
            }
        }
        return differences;
    }

    private static String compact(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static String caller(TreePath path, Map<ClassTree, String> typeNames) {
        List<String> scopes = new ArrayList<>();
        for (TreePath current = path; current != null; current = current.getParentPath()) {
            var leaf = current.getLeaf();
            var parent = current.getParentPath() == null ? null : current.getParentPath().getLeaf();
            if (leaf instanceof MethodTree method) {
                String typeParameters = method.getTypeParameters().isEmpty() ? "" : "<"
                        + method.getTypeParameters().stream().map(parameter -> compact(parameter.toString()))
                        .collect(Collectors.joining(",")) + ">";
                scopes.add(typeParameters + method.getName() + "(" + method.getParameters().stream()
                        .map(parameter -> compact(parameter.getType().toString())).collect(Collectors.joining(",")) + ")");
            } else if (leaf instanceof ClassTree type) {
                scopes.add(typeNames.get(type));
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
