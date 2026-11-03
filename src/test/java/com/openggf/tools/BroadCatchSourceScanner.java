package com.openggf.tools;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Parse only: no engine classes, annotation processors, type resolution or ROMs are loaded. */
final class BroadCatchSourceScanner {
    private static final Pattern BROAD = Pattern.compile("(?:^|[.|\\s])(Exception|RuntimeException|Throwable)(?:$|[\\s|])");

    record Site(String key, String source, long line, String body, String attempted) {}

    static List<Site> scan(Path sourceRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) throw new IOException("Missing main sources: " + sourceRoot);
        try (var paths = Files.walk(sourceRoot)) {
            var files = paths.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            if (files.isEmpty()) throw new IOException("No main sources: " + sourceRoot);
            var sources = new ArrayList<JavaFileObject>();
            for (Path file : files) {
                sources.add(source(sourceRoot.relativize(file).toString().replace('\\', '/'), Files.readString(file)));
            }
            return scan(sources);
        }
    }

    static List<Site> snippet(String text) throws IOException {
        return scan(List.of(source("Control.java", text)));
    }

    private static JavaFileObject source(String name, String text) {
        return new SimpleJavaFileObject(URI.create("string:///" + name), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return text; }
        };
    }

    private static List<Site> scan(List<JavaFileObject> sources) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IOException("A JDK is required for the broad-catch source guard");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var sites = new ArrayList<Site>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "--release", "25"), null, sources);
            for (CompilationUnitTree unit : task.parse()) {
                var positions = com.sun.source.util.Trees.instance(task).getSourcePositions();
                new TreePathScanner<Void, Void>() {
                    @Override public Void visitCatch(CatchTree caught, Void unused) {
                        String type = caught.getParameter().getType().toString();
                        if (BROAD.matcher(type).find()
                                && (!alwaysThrows(caught.getBlock()) || hasEarlyExit(caught.getBlock()))) {
                            var owners = new ArrayList<String>();
                            for (TreePath p = getCurrentPath(); p != null; p = p.getParentPath()) {
                                if (p.getLeaf() instanceof MethodTree method) {
                                    owners.add(method.getName() + "(" + String.join(",", method.getParameters().stream()
                                            .map(parameter -> parameter.getType().toString()).toList()) + ")");
                                } else if (p.getLeaf() instanceof ClassTree clazz) {
                                    owners.add(clazz.getSimpleName().toString());
                                }
                            }
                            java.util.Collections.reverse(owners);
                            String path = unit.getSourceFile().toUri().getPath().substring(1);
                            TryTree attempted = (TryTree) getCurrentPath().getParentPath().getLeaf();
                            String body = caught.getBlock().toString();
                            // Pin both attempted work and handler: moving/rewriting a legacy catch
                            // cannot spend an old caller's allowance on a different engine call.
                            String fingerprint = digest(attempted.getBlock() + "\n" + caught);
                            String key = path + "#" + String.join("#", owners) + "~" + type.replace(" ", "")
                                    + "~" + fingerprint;
                            sites.add(new Site(key, path, unit.getLineMap().getLineNumber(
                                    positions.getStartPosition(unit, caught)), body, attempted.getBlock().toString()));
                        }
                        return super.visitCatch(caught, unused);
                    }
                }.scan(unit, null);
            }
        }
        var errors = diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR).toList();
        if (!errors.isEmpty()) throw new IOException("Unable to parse guarded sources: " + errors);
        return sites;
    }

    // Deliberately conservative. A throw inside a lambda/local class or only one
    // conditional arm does not propagate the exception out of this catch.
    private static boolean alwaysThrows(Tree tree) {
        if (tree instanceof ThrowTree) return true;
        if (tree instanceof BlockTree block) {
            return !block.getStatements().isEmpty() && alwaysThrows(block.getStatements().getLast());
        }
        if (tree instanceof IfTree branch) {
            return branch.getElseStatement() != null && alwaysThrows(branch.getThenStatement())
                    && alwaysThrows(branch.getElseStatement());
        }
        return false;
    }

    private static boolean hasEarlyExit(Tree tree) {
        return Boolean.TRUE.equals(new TreeScanner<Boolean, Void>() {
            @Override public Boolean reduce(Boolean left, Boolean right) {
                return Boolean.TRUE.equals(left) || Boolean.TRUE.equals(right);
            }
            @Override public Boolean visitReturn(ReturnTree node, Void unused) { return true; }
            @Override public Boolean visitBreak(BreakTree node, Void unused) { return true; }
            @Override public Boolean visitContinue(ContinueTree node, Void unused) { return true; }
            @Override public Boolean visitLambdaExpression(LambdaExpressionTree node, Void unused) { return false; }
            @Override public Boolean visitClass(ClassTree node, Void unused) { return false; }
        }.scan(tree, null));
    }

    static Map<String, Integer> inventory(List<Site> sites) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Site site : sites) counts.merge(site.key(), 1, Integer::sum);
        return counts;
    }

    private static String digest(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
