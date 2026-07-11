package com.openggf.tools.modsdk;

import com.sun.source.doctree.DocCommentTree;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Renders deterministic HTML for exactly the configured supported mod API types. */
public final class ExactModApiDoclet implements Doclet {
    private static final ThreadLocal<Configuration> CONFIGURATION = new ThreadLocal<>();
    private static final Set<ElementKind> API_MEMBER_KINDS = EnumSet.of(
            ElementKind.CONSTRUCTOR, ElementKind.METHOD, ElementKind.FIELD,
            ElementKind.ENUM_CONSTANT, ElementKind.RECORD_COMPONENT);

    private record Configuration(Set<String> canonicalTypeNames, Path outputDirectory) { }

    static void configure(Set<String> canonicalTypeNames, Path outputDirectory) {
        if (CONFIGURATION.get() != null) {
            throw new IllegalStateException("Exact mod API doclet is already configured");
        }
        CONFIGURATION.set(new Configuration(Set.copyOf(canonicalTypeNames), outputDirectory));
    }

    static void clearConfiguration() {
        CONFIGURATION.remove();
    }

    @Override public void init(Locale locale, Reporter reporter) { }

    @Override public String getName() {
        return "ExactModApiDoclet";
    }

    @Override public Set<? extends Option> getSupportedOptions() {
        return Set.of();
    }

    @Override public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override public boolean run(DocletEnvironment environment) {
        Configuration configuration = CONFIGURATION.get();
        if (configuration == null || configuration.canonicalTypeNames().isEmpty()) {
            throw new IllegalStateException("Exact mod API doclet has no configured types");
        }
        try {
            List<TypeElement> types = resolveExactTypes(environment, configuration);
            render(environment, configuration.outputDirectory(), types);
            return true;
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot write exact mod API Javadoc", failure);
        }
    }

    private static List<TypeElement> resolveExactTypes(DocletEnvironment environment,
            Configuration configuration) {
        List<TypeElement> types = new ArrayList<>();
        for (String name : configuration.canonicalTypeNames().stream().sorted().toList()) {
            TypeElement type = environment.getElementUtils().getTypeElement(name);
            if (type == null) {
                throw new IllegalStateException("Javadoc environment cannot resolve exact type: " + name);
            }
            types.add(type);
        }
        return List.copyOf(types);
    }

    private static void render(DocletEnvironment environment, Path output,
            List<TypeElement> types) throws IOException {
        Files.createDirectories(output);
        String links = types.stream()
                .map(type -> "<li><a href=\"" + pagePath(type) + "\">"
                        + escape(type.getQualifiedName().toString()) + "</a></li>")
                .collect(Collectors.joining("\n"));
        String index = page("OpenGGF Mod API", "<h1>OpenGGF Mod API</h1>\n<ul>\n"
                + links + "\n</ul>");
        write(output.resolve("index.html"), index);
        write(output.resolve("allclasses-index.html"), index);

        for (TypeElement type : types) {
            Path target = output.resolve(pagePath(type));
            Files.createDirectories(target.getParent());
            write(target, renderType(environment, type));
        }
    }

    private static String renderType(DocletEnvironment environment, TypeElement type) {
        StringBuilder body = new StringBuilder();
        body.append("<nav><a href=\"").append(rootPrefix(type))
                .append("allclasses-index.html\">All Classes</a></nav>\n")
                .append("<h1>").append(escape(type.getQualifiedName().toString()))
                .append("</h1>\n<pre>").append(escape(typeSignature(type))).append("</pre>\n");
        appendComment(body, environment, type);

        List<? extends Element> members = type.getEnclosedElements().stream()
                .filter(element -> API_MEMBER_KINDS.contains(element.getKind()))
                .filter(ExactModApiDoclet::isSupportedMember)
                .sorted(Comparator.comparing((Element element) -> element.getKind().name())
                        .thenComparing(element -> element.getSimpleName().toString())
                        .thenComparing(Element::toString))
                .toList();
        if (!members.isEmpty()) {
            body.append("<h2>API Members</h2>\n");
            for (Element member : members) {
                body.append("<section><h3>").append(escape(member.getSimpleName().toString()))
                        .append("</h3><pre>").append(escape(memberSignature(member)))
                        .append("</pre>");
                appendComment(body, environment, member);
                body.append("</section>\n");
            }
        }
        return page(type.getSimpleName().toString(), body.toString());
    }

    private static boolean isSupportedMember(Element member) {
        return member.getKind() == ElementKind.RECORD_COMPONENT
                || member.getKind() == ElementKind.ENUM_CONSTANT
                || member.getModifiers().contains(Modifier.PUBLIC)
                || member.getModifiers().contains(Modifier.PROTECTED);
    }

    private static String typeSignature(TypeElement type) {
        String annotations = annotations(type);
        String modifiers = modifiers(type.getModifiers());
        String kind = switch (type.getKind()) {
            case ANNOTATION_TYPE -> "@interface";
            case INTERFACE -> "interface";
            case ENUM -> "enum";
            case RECORD -> "record";
            default -> "class";
        };
        StringBuilder signature = new StringBuilder(annotations).append(modifiers)
                .append(kind).append(' ').append(type.getQualifiedName())
                .append(typeParameters(type.getTypeParameters()));
        if (type.getKind() == ElementKind.RECORD) {
            signature.append(type.getRecordComponents().stream()
                    .map(ExactModApiDoclet::recordComponentSignature)
                    .collect(Collectors.joining(", ", "(", ")")));
        }
        TypeMirror superclass = type.getSuperclass();
        if (superclass.getKind() != TypeKind.NONE
                && !superclass.toString().equals(Object.class.getCanonicalName())
                && type.getKind() != ElementKind.RECORD
                && type.getKind() != ElementKind.ENUM) {
            signature.append(" extends ").append(superclass);
        }
        if (!type.getInterfaces().isEmpty()) {
            signature.append(type.getKind() == ElementKind.INTERFACE ? " extends " : " implements ")
                    .append(type.getInterfaces().stream().map(TypeMirror::toString)
                            .collect(Collectors.joining(", ")));
        }
        if (!type.getPermittedSubclasses().isEmpty()) {
            signature.append(" permits ").append(type.getPermittedSubclasses().stream()
                    .map(TypeMirror::toString).collect(Collectors.joining(", ")));
        }
        return signature.toString();
    }

    private static String memberSignature(Element member) {
        String prefix = annotations(member) + modifiers(member.getModifiers());
        return switch (member) {
            case RecordComponentElement component -> prefix + component.asType() + " "
                    + component.getSimpleName();
            case VariableElement variable -> prefix + variableSignature(variable);
            case ExecutableElement executable -> prefix + executableSignature(executable);
            default -> prefix + member;
        };
    }

    private static String variableSignature(VariableElement variable) {
        if (variable.getKind() == ElementKind.ENUM_CONSTANT) {
            return variable.getSimpleName().toString();
        }
        StringBuilder signature = new StringBuilder().append(variable.asType()).append(' ')
                .append(variable.getSimpleName());
        if (variable.getConstantValue() != null) {
            signature.append(" = ").append(constantLiteral(variable.getConstantValue()));
        }
        return signature.toString();
    }

    private static String recordComponentSignature(RecordComponentElement component) {
        return annotations(component) + component.asType() + " " + component.getSimpleName();
    }

    private static String executableSignature(ExecutableElement executable) {
        StringBuilder signature = new StringBuilder()
                .append(typeParameters(executable.getTypeParameters()));
        if (!executable.getTypeParameters().isEmpty()) {
            signature.append(' ');
        }
        if (executable.getKind() == ElementKind.CONSTRUCTOR) {
            signature.append(executable.getEnclosingElement().getSimpleName());
        } else {
            signature.append(executable.getReturnType()).append(' ')
                    .append(executable.getSimpleName());
        }
        signature.append(parameters(executable));
        if (!executable.getThrownTypes().isEmpty()) {
            signature.append(" throws ").append(executable.getThrownTypes().stream()
                    .map(TypeMirror::toString).collect(Collectors.joining(", ")));
        }
        if (executable.getDefaultValue() != null) {
            signature.append(" default ").append(executable.getDefaultValue());
        }
        return signature.toString();
    }

    private static String parameters(ExecutableElement executable) {
        List<? extends VariableElement> parameters = executable.getParameters();
        List<String> rendered = new ArrayList<>(parameters.size());
        for (int index = 0; index < parameters.size(); index++) {
            VariableElement parameter = parameters.get(index);
            String type = parameter.asType().toString();
            if (executable.isVarArgs() && index == parameters.size() - 1 && type.endsWith("[]")) {
                type = type.substring(0, type.length() - 2) + "...";
            }
            rendered.add(annotations(parameter) + type + " " + parameter.getSimpleName());
        }
        return rendered.stream().collect(Collectors.joining(", ", "(", ")"));
    }

    private static String typeParameters(List<? extends TypeParameterElement> parameters) {
        if (parameters.isEmpty()) {
            return "";
        }
        return parameters.stream().map(parameter -> {
            StringBuilder value = new StringBuilder(annotations(parameter))
                    .append(parameter.getSimpleName());
            List<? extends TypeMirror> bounds = parameter.getBounds();
            if (!(bounds.size() == 1
                    && bounds.getFirst().toString().equals(Object.class.getCanonicalName()))) {
                value.append(" extends ").append(bounds.stream().map(TypeMirror::toString)
                        .collect(Collectors.joining(" & ")));
            }
            return value.toString();
        }).collect(Collectors.joining(", ", "<", ">"));
    }

    private static String annotations(Element element) {
        if (element.getAnnotationMirrors().isEmpty()) {
            return "";
        }
        return element.getAnnotationMirrors().stream().map(Object::toString)
                .collect(Collectors.joining(" ", "", " "));
    }

    private static String constantLiteral(Object value) {
        if (value instanceof String text) {
            return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        }
        if (value instanceof Character character) {
            return "'" + character.toString().replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        return String.valueOf(value);
    }

    private static String modifiers(Set<Modifier> modifiers) {
        if (modifiers.isEmpty()) {
            return "";
        }
        return modifiers.stream().map(Modifier::toString).sorted()
                .collect(Collectors.joining(" ")) + " ";
    }

    private static void appendComment(StringBuilder body, DocletEnvironment environment,
            Element element) {
        DocCommentTree comment = environment.getDocTrees().getDocCommentTree(element);
        if (comment != null && !comment.toString().isBlank()) {
            body.append("<div class=\"doc\"><pre>")
                    .append(escape(comment.toString().strip()))
                    .append("</pre></div>\n");
        }
    }

    private static String pagePath(TypeElement type) {
        String packageName = type.getEnclosingElement() instanceof TypeElement
                ? packageName(type) : environmentPackageName(type);
        String localName = type.getQualifiedName().toString().substring(packageName.length() + 1);
        return packageName.replace('.', '/') + "/" + localName + ".html";
    }

    private static String environmentPackageName(TypeElement type) {
        Element element = type;
        while (element.getEnclosingElement() instanceof TypeElement) {
            element = element.getEnclosingElement();
        }
        return element.getEnclosingElement().toString();
    }

    private static String packageName(TypeElement type) {
        return environmentPackageName(type);
    }

    private static String rootPrefix(TypeElement type) {
        String packageName = environmentPackageName(type);
        return "../".repeat(packageName.isEmpty() ? 0 : packageName.split("\\.").length);
    }

    private static void write(Path target, String content) throws IOException {
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    private static String page(String title, String body) {
        return "<!doctype html>\n<html lang=\"en\"><head><meta charset=\"UTF-8\">"
                + "<title>" + escape(title) + "</title>"
                + "<style>body{font-family:sans-serif;max-width:80rem;margin:2rem auto;padding:0 1rem}"
                + "pre{white-space:pre-wrap;background:#f6f8fa;padding:.75rem}section{border-top:1px solid #ddd}"
                + "</style></head><body>\n" + body + "\n</body></html>\n";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
