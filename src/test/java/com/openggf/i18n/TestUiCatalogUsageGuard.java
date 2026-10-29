package com.openggf.i18n;

import com.openggf.configuration.ConfigCatalog;
import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import org.junit.jupiter.api.Test;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Checks real parsed callers; catalog edits cannot silently rename parameters used by Java. */
class TestUiCatalogUsageGuard {
    private static final Set<String> DYNAMIC_ADAPTERS = Set.of(
            "EngineSettingLabels.java", "EditorLabels.java", "MasterTitleScreen.java",
            "StageplayWorkspace.java", "EngineSettingsScreen.java", "HubSetupChips.java", "LaunchConfigPanel.java",
            "EditorLevelTeams.java");

    @Test
    void everyLiteralMessageAndNamedArgumentMatchesTheCanonicalCatalog() throws Exception {
        CatalogRepository repository = CatalogRepository.directory(Path.of("src/main/resources/i18n"));
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Run catalog guards with the required JDK 25");
        List<Path> sources;
        try (var files = Files.walk(Path.of("src/main/java"))) {
            sources = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/i18n/"))
                    .filter(p -> {
                        try { return Files.readString(p).contains("UiMessages.text("); }
                        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                    }).toList();
        }
        List<String> errors = new ArrayList<>();
        try (var manager = compiler.getStandardFileManager(null, Locale.ROOT, null)) {
            var task = (JavacTask) compiler.getTask(null, manager, null, List.of("-proc:none"), null,
                    manager.getJavaFileObjectsFromPaths(sources));
            for (CompilationUnitTree unit : task.parse()) {
                String owner = Path.of(unit.getSourceFile().toUri()).getFileName().toString();
                new TreeScanner<Void, Void>() {
                    @Override public Void visitLiteral(LiteralTree literal, Void unused) {
                        if (DYNAMIC_ADAPTERS.contains(owner) && literal.getValue() instanceof String id
                                && id.matches("(editor|settings|menu|hub|launch|legal|language|mutator|mod)\\.[a-z0-9_]+(?:\\.[a-z0-9_]+)*")) {
                            if (!repository.ids().contains(id)) errors.add(owner + ": unknown adapter ID " + id);
                        }
                        return super.visitLiteral(literal, unused);
                    }

                    @Override public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
                        if (owner.equals("HubSetupChips.java") && call.getMethodSelect().toString().equals("addToggle")) {
                            var labels = literalIds(call.getArguments().get(1));
                            assertNotNull(labels, "Toggle labels must declare their message IDs");
                            for (String label : labels) if (!repository.ids().contains(label + "_summary"))
                                errors.add(owner + ": missing toggle summary for " + label);
                        }
                        if (!call.getMethodSelect().toString().endsWith("UiMessages.text")) return super.visitMethodInvocation(call, unused);
                        Set<String> ids = literalIds(call.getArguments().getFirst());
                        if (ids == null) {
                            if (!DYNAMIC_ADAPTERS.contains(owner)) errors.add(owner + ": undeclared dynamic message ID " + call);
                            return super.visitMethodInvocation(call, unused);
                        }
                        Set<String> arguments = call.getArguments().size() == 1 ? Set.of() : mapNames(call.getArguments().get(1));
                        for (String id : ids) {
                            if (!repository.ids().contains(id)) errors.add(owner + ": unknown ID " + id);
                            else if (arguments != null && !arguments.equals(repository.contract(id).keySet()))
                                errors.add(owner + ": " + id + " expects " + repository.contract(id).keySet() + ", got " + arguments);
                        }
                        return super.visitMethodInvocation(call, unused);
                    }
                }.scan(unit, null);
            }
        }
        assertTrue(errors.isEmpty(), () -> String.join("\n", errors));
    }

    @Test
    void dynamicSettingAdaptersCoverEveryPersistedKeyAndEnumValue() throws Exception {
        var repository = CatalogRepository.directory(Path.of("src/main/resources/i18n"));
        List<String> missing = new ArrayList<>();
        for (var key : ConfigCatalog.emitOrder()) {
            String prefix = "settings.key." + key.name().toLowerCase(Locale.ROOT);
            if (!repository.ids().contains(prefix + ".label")) missing.add(prefix + ".label");
            if (!repository.ids().contains(prefix + ".help")) missing.add(prefix + ".help");
            for (String value : ConfigCatalog.meta(key).allowedValues()) {
                if (key == com.openggf.configuration.SonicConfiguration.DISPLAY_HUD_ANCHOR) continue;
                if (!repository.ids().contains("settings.value." + value)) missing.add("settings.value." + value);
            }
        }
        assertTrue(missing.isEmpty(), () -> "Missing dynamic adapter contracts: " + missing);
        for (var choice : com.openggf.editor.project.EditorLevelTeams.choices())
            assertFalse(choice.label().isBlank(), "Each team preset resolves its declared catalog ID");
    }

    private static Set<String> literalIds(ExpressionTree expression) {
        if (expression instanceof LiteralTree literal && literal.getValue() instanceof String text) return Set.of(text);
        if (expression instanceof ParenthesizedTree paren) return literalIds(paren.getExpression());
        if (expression instanceof ConditionalExpressionTree choice) {
            var yes = literalIds(choice.getTrueExpression()); var no = literalIds(choice.getFalseExpression());
            if (yes == null || no == null) return null;
            var both = new HashSet<>(yes); both.addAll(no); return both;
        }
        return null;
    }

    private static Set<String> mapNames(ExpressionTree expression) {
        if (!(expression instanceof MethodInvocationTree map) || !map.getMethodSelect().toString().endsWith("Map.of")) return null;
        Set<String> names = new HashSet<>();
        for (int i = 0; i < map.getArguments().size(); i += 2) {
            var key = literalIds(map.getArguments().get(i));
            if (key == null || key.size() != 1) return null;
            if (!names.add(key.iterator().next())) throw new AssertionError("Duplicate message argument: " + expression);
        }
        return names;
    }
}
