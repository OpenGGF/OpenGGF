package com.openggf.tools.modtestkit;

import com.openggf.architecture.CompositionRoot;
import com.openggf.game.GameModule;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.tests.TestSessionOutputPaths;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Guards the exact support-only composition root, rather than exempting a runtime package. */
class TestModTestKitDistributionGuard {
    @TempDir Path work;
    private static final String SUPPORT = "com/openggf/mods/testing/";
    private static final String ROOT_CLASS = SUPPORT + "ModTestKit.class";

    @Test
    void effectiveEngineInputsExcludeTheActualSupportClassifier() throws Exception {
        Node pom = xml(Path.of("pom.xml"));
        // The execution below is effective without inheritance/profile merging. If that
        // topology changes, this guard must resolve the new model before granting isolation.
        assertEquals(0, nodes(pom, "/project/parent").size());
        assertEquals(0, nodes(pom, "/project/profiles/profile/build/plugins/plugin["
                + "artifactId='maven-jar-plugin' or artifactId='maven-assembly-plugin']").size());
        assertEquals(0, nodes(pom, "/project/profiles/profile/build/plugins/plugin["
                + "artifactId='exec-maven-plugin']/executions/execution[id='prepare-mod-testkit']").size());
        Node jarPlugin = one(pom, "/project/build/plugins/plugin[artifactId='maven-jar-plugin']");
        assertEquals(0, nodes(jarPlugin, "configuration").size());
        Node engine = one(jarPlugin, "executions/execution[id='default-jar']");
        List<String> engineExcludes = texts(engine, "configuration/excludes/exclude");
        Node attachment = one(jarPlugin, "executions/execution[id='attach-mod-testkit']");
        assertEquals("package", text(attachment, "phase"));
        assertEquals(List.of("jar"), texts(attachment, "goals/goal"));
        assertEquals("mod-testkit", text(attachment, "configuration/classifier"));

        Node prepare = one(pom, "/project/build/plugins/plugin[artifactId='exec-maven-plugin']"
                + "/executions/execution[id='prepare-mod-testkit']");
        assertEquals("prepare-package", text(prepare, "phase"));
        assertEquals(List.of("exec"), texts(prepare, "goals/goal"));
        assertEquals("java", text(prepare, "configuration/executable"));
        List<String> arguments = texts(prepare, "configuration/arguments/argument");
        assertEquals(List.of("-cp", ModTestKitPackager.class.getName(),
                "${project.build.outputDirectory}", "${project.build.directory}/mod-testkit/classes"), arguments);

        Node assemblyPlugin = one(pom, "/project/build/plugins/plugin[artifactId='maven-assembly-plugin']");
        Path assemblyPath = Path.of(text(assemblyPlugin, "configuration/descriptors/descriptor"));
        Node assembly = xml(assemblyPath);
        Node engineFiles = one(assembly, "/assembly/fileSets/fileSet["
                + "directory='${project.build.outputDirectory}']");
        assertEquals(".", text(engineFiles, "outputDirectory"));
        assertEquals("false", text(assembly, "/assembly/dependencySets/dependencySet/useProjectArtifact"));
        List<String> fatExcludes = texts(engineFiles, "excludes/exclude");

        Path classes = copyCompiledSupport();
        String registry = "com/openggf/game/rewind/RewindRegistry.class";
        copyCompiled(registry, classes.resolve(registry));
        copyCompiled("com/openggf/tools/modtestkit/ModTestKitPackager.class",
                classes.resolve("com/openggf/tools/modtestkit/ModTestKitPackager.class"));
        Path output = buildPath(text(attachment, "configuration/classesDirectory"), classes);
        assertEquals(output, buildPath(arguments.get(3), classes));
        ModTestKitPackager.prepare(classes, output);
        try (var files = Files.walk(output)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String relative = output.relativize(file).toString().replace('\\', '/');
                if (!relative.endsWith(".class")) continue;
                assertTrue(relative.startsWith(SUPPORT), relative);
                assertTrue(excluded(relative, engineExcludes), "thin engine admits " + relative);
                assertTrue(excluded(relative, fatExcludes), "fat engine admits " + relative);
                assertArrayEquals(Files.readAllBytes(classes.resolve(relative)), Files.readAllBytes(file));
            }
        }
        assertTrue(Files.isRegularFile(output.resolve(ROOT_CLASS)));
        assertFalse(Files.exists(output.resolve(registry)));
        assertFalse(Files.exists(output.resolve("com/openggf/tools")));
        assertFalse(excluded(registry, engineExcludes));
        assertFalse(excluded(registry, fatExcludes));
        assertArrayEquals(Files.readAllBytes(classes.resolve("version.properties")),
                Files.readAllBytes(output.resolve("META-INF/openggf-build.properties")));
    }

    @Test
    void isolatedCompiledClassifierComposesAndRestoresOnlyModuleAdapters() throws Exception {
        Path classes = copyCompiledSupport();
        Path output = work.resolve("target/mod-testkit/classes");
        ModTestKitPackager.prepare(classes, output);
        Path jar = work.resolve("mod-testkit.jar");
        try (var archive = new JarOutputStream(Files.newOutputStream(jar)); var files = Files.walk(output)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                archive.putNextEntry(new JarEntry(output.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, archive);
                archive.closeEntry();
            }
        }
        // Engine dependencies remain available, but support classes must come from this jar.
        ClassLoader engineOnly = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.openggf.mods.testing.")
                        || name.startsWith("com.openggf.tools.modtestkit.")) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        };
        try (var loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, engineOnly)) {
            Class<?> kit = Class.forName("com.openggf.mods.testing.ModTestKit", true, loader);
            assertSame(loader, kit.getClassLoader());
            assertTrue(kit.isAnnotationPresent(CompositionRoot.class));
            assertThrows(ClassNotFoundException.class,
                    () -> loader.loadClass(ModTestKitPackager.class.getName()));
            int[] value = {7};
            RewindSnapshottable<Integer> adapter = new RewindSnapshottable<>() {
                public String key() { return "module:state"; }
                public Integer capture() { return value[0]; }
                public void restore(Integer snapshot) { value[0] = snapshot; }
            };
            GameModule module = mock(GameModule.class);
            when(module.rewindAdapters()).thenReturn(List.of(adapter));
            var registry = (RewindRegistry) kit.getMethod("moduleState", GameModule.class).invoke(null, module);
            var snapshot = (CompositeSnapshot) kit.getMethod("capture", RewindRegistry.class).invoke(null, registry);
            assertEquals(java.util.Map.of("module:state", 7), snapshot.entries());
            value[0] = 19;
            kit.getMethod("restore", RewindRegistry.class, CompositeSnapshot.class).invoke(null, registry, snapshot);
            assertEquals(7, value[0]);
            assertEquals(snapshot.entries(), registry.capture().entries());
        }
    }

    private Path copyCompiledSupport() throws Exception {
        Path compiled = TestSessionOutputPaths.compiledClasses();
        Path classes = Files.createDirectories(work.resolve("target/classes"));
        try (var files = Files.walk(compiled.resolve(SUPPORT))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                copyCompiled(compiled.relativize(file).toString(), classes.resolve(compiled.relativize(file)));
            }
        }
        copyCompiled("version.properties", classes.resolve("version.properties"));
        return classes;
    }

    private static void copyCompiled(String relative, Path destination) throws Exception {
        Files.createDirectories(destination.getParent());
        Files.copy(TestSessionOutputPaths.compiledClasses().resolve(relative), destination);
    }

    private static Path buildPath(String expression, Path classes) {
        return Path.of(expression.replace("${project.build.directory}", classes.getParent().toString()));
    }

    private static boolean excluded(String entry, List<String> excludes) {
        return excludes.stream().anyMatch(pattern -> java.nio.file.FileSystems.getDefault()
                .getPathMatcher("glob:" + pattern).matches(Path.of(entry)));
    }

    private static Node xml(Path path) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var input = Files.newInputStream(path)) { return factory.newDocumentBuilder().parse(input); }
    }

    private static List<Node> nodes(Node parent, String expression) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        var result = (NodeList) xpath.evaluate(expression, parent, XPathConstants.NODESET);
        var nodes = new java.util.ArrayList<Node>();
        for (int i = 0; i < result.getLength(); i++) nodes.add(result.item(i));
        return nodes;
    }

    private static Node one(Node parent, String expression) throws Exception {
        List<Node> result = nodes(parent, expression);
        assertEquals(1, result.size(), expression);
        return result.getFirst();
    }

    private static String text(Node parent, String expression) throws Exception {
        return one(parent, expression).getTextContent().trim();
    }

    private static List<String> texts(Node parent, String expression) throws Exception {
        return nodes(parent, expression).stream().map(node -> node.getTextContent().trim()).toList();
    }
}
