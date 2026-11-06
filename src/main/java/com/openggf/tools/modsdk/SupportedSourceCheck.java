package com.openggf.tools.modsdk;

import com.openggf.mods.validation.SupportedModContract;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import javax.tools.Diagnostic;
import java.util.*;

/** Attributed checking precedes javac lowering, preserving constant provenance. */
public final class SupportedSourceCheck implements Plugin {
    @Override public String getName() { return "OpenGGFSupported"; }
    @Override public void init(JavacTask task, String... args) {
        var contract = new SupportedModContract();
        Trees trees = Trees.instance(task);
        Elements elements = task.getElements();
        Types types = task.getTypes();
        Set<Tree> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        task.addTaskListener(new TaskListener() {
            @Override public void finished(TaskEvent event) {
                if (event.getKind() != TaskEvent.Kind.ANALYZE) return;
                var unit = event.getCompilationUnit();
                var scanner = new TreePathScanner<Void, Void>() {
                    final Set<String> reported = new HashSet<>();
                    String binary(TypeElement t) { return elements.getBinaryName(t).toString().replace('.', '/'); }
                    void finding(Tree tree, String symbol) {
                        long pos = trees.getSourcePositions().getStartPosition(unit, tree);
                        if (reported.add(pos + ":" + symbol)) trees.printMessage(Arrays.asList(args).contains("audit") ? Diagnostic.Kind.WARNING : Diagnostic.Kind.ERROR,
                                "Unsupported engine symbol " + symbol.replace('/', '.') + "; " + SupportedModContract.HINT, tree, unit);
                    }
                    String descriptor(TypeMirror t) {
                        t = types.erasure(t);
                        return switch (t.getKind()) {
                            case BOOLEAN -> "Z"; case BYTE -> "B"; case SHORT -> "S"; case INT -> "I";
                            case LONG -> "J"; case CHAR -> "C"; case FLOAT -> "F"; case DOUBLE -> "D"; case VOID -> "V";
                            case ARRAY -> "[" + descriptor(((ArrayType)t).getComponentType());
                            case DECLARED -> "L" + binary((TypeElement)((DeclaredType)t).asElement()) + ";";
                            default -> "Ljava/lang/Object;";
                        };
                    }
                    String descriptor(ExecutableElement m) {
                        StringBuilder b = new StringBuilder("(");
                        m.getParameters().forEach(p -> b.append(descriptor(p.asType())));
                        return b.append(")").append(descriptor(m.getReturnType())).toString();
                    }
                    void symbol(Tree tree, Element e) {
                        if (e instanceof TypeElement t) {
                            if (!contract.supportedType(binary(t))) finding(tree, binary(t));
                        } else if (e != null && e.getEnclosingElement() instanceof TypeElement owner) {
                            String name = e.getSimpleName().toString();
                            if (name.equals("class")) return; // javac pseudo-field; the literal type is checked separately.
                            String d = e instanceof ExecutableElement m ? descriptor(m) : descriptor(e.asType());
                            if ((SupportedModContract.engine(binary(owner)) || SupportedModContract.forbiddenOperation(binary(owner),name)) && !contract.consumableMember(binary(owner), name, d)) finding(tree, binary(owner) + "#" + name);
                        }
                    }
                    void type(Tree tree, TypeMirror t, Set<String> seen) {
                        if (t == null || !seen.add(t.toString())) return;
                        switch (t.getKind()) {
                            case DECLARED -> {
                                DeclaredType d = (DeclaredType)t;
                                String n = binary((TypeElement)d.asElement());
                                if (!contract.supportedType(n)) finding(tree, n);
                                for (TypeMirror a : d.getTypeArguments()) type(tree,a,seen);
                            }
                            case ARRAY -> type(tree,((ArrayType)t).getComponentType(),seen);
                            case TYPEVAR -> type(tree,((TypeVariable)t).getUpperBound(),seen);
                            case WILDCARD -> { var w=(WildcardType)t; type(tree,w.getExtendsBound(),seen); type(tree,w.getSuperBound(),seen); }
                            case INTERSECTION -> { for (var b:((IntersectionType)t).getBounds()) type(tree,b,seen); }
                            default -> { }
                        }
                    }
                    @Override public Void scan(Tree tree, Void unused) {
                        if (tree == null) return null;
                        TreePath path = getCurrentPath() == null ? new TreePath(unit) : new TreePath(getCurrentPath(), tree);
                        if (tree instanceof ExpressionTree || tree instanceof VariableTree) type(tree, trees.getTypeMirror(path), new HashSet<>());
                        return super.scan(tree,unused);
                    }
                    @Override public Void visitIdentifier(IdentifierTree t, Void v) { symbol(t,trees.getElement(getCurrentPath())); return super.visitIdentifier(t,v); }
                    @Override public Void visitMemberSelect(MemberSelectTree t, Void v) { symbol(t,trees.getElement(getCurrentPath())); return super.visitMemberSelect(t,v); }
                    @Override public Void visitMemberReference(MemberReferenceTree t, Void v) { symbol(t,trees.getElement(getCurrentPath())); return super.visitMemberReference(t,v); }
                    @Override public Void visitNewClass(NewClassTree t, Void v) { symbol(t,trees.getElement(getCurrentPath())); return super.visitNewClass(t,v); }
                    @Override public Void visitMethod(MethodTree t, Void v) {
                        var e=trees.getElement(getCurrentPath());
                        if (e instanceof ExecutableElement method && method.getEnclosingElement() instanceof TypeElement owner) {
                            for (Element inherited:elements.getAllMembers(owner)) {
                                if (inherited instanceof ExecutableElement base && base != method
                                        && elements.overrides(method,base,owner)) symbol(t,base);
                            }
                            // getAllMembers hides overridden declarations; inspect all supertypes explicitly.
                            checkOverrides(t,method,owner,owner.asType(),new HashSet<>());
                        }
                        return super.visitMethod(t,v);
                    }
                    void checkOverrides(Tree tree, ExecutableElement method, TypeElement owner, TypeMirror current, Set<String> seen) {
                        for (TypeMirror parent:types.directSupertypes(current)) {
                            if (!seen.add(parent.toString())) continue;
                            var p=(TypeElement)types.asElement(parent);
                            for(Element e:p.getEnclosedElements()) if(e instanceof ExecutableElement m && elements.overrides(method,m,owner)) symbol(tree,m);
                            checkOverrides(tree,method,owner,parent,seen);
                        }
                    }
                };
                if (visited.add(unit)) {
                    for (ImportTree imp : unit.getImports()) scanner.scan(new TreePath(new TreePath(unit), imp), null);
                    for (AnnotationTree annotation : unit.getPackageAnnotations())
                        scanner.scan(new TreePath(new TreePath(unit),annotation),null);
                }
                var path = trees.getPath(event.getTypeElement());
                if (path != null && visited.add(path.getLeaf())) scanner.scan(path,null);
            }
        });
    }
}
