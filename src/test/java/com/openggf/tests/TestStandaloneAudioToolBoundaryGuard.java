package com.openggf.tests;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestStandaloneAudioToolBoundaryGuard {
    private static Path compiledFixtures;

    @BeforeAll
    static void compileRepresentativeDependencies(@TempDir Path directory) throws IOException {
        Map<String, String> sources = Map.of(
                "com/openggf/tools/audio/SoundTestApp.java", """
                        package com.openggf.tools.audio;
                        public class SoundTestApp {
                            public static void main(String[] args) { }
                            public static class Options { }
                        }
                        """,
                "com/openggf/tools/audio/SoundTestApplication.java", """
                        package com.openggf.tools.audio;
                        public class SoundTestApplication { }
                        """,
                "com/openggf/tools/audio/AudioHelper.java", """
                        package com.openggf.tools.audio;
                        public class AudioHelper { }
                        """,
                "com/openggf/audio/DirectAppField.java", """
                        package com.openggf.audio;
                        public class DirectAppField {
                            com.openggf.tools.audio.SoundTestApp app;
                        }
                        """,
                "com/openggf/game/RuntimeMethodReference.java", """
                        package com.openggf.game;
                        public class RuntimeMethodReference {
                            java.util.function.Consumer<String[]> command =
                                    com.openggf.tools.audio.SoundTestApp::main;
                        }
                        """,
                "com/openggf/game/NestedAppField.java", """
                        package com.openggf.game;
                        public class NestedAppField {
                            com.openggf.tools.audio.SoundTestApp.Options options;
                        }
                        """,
                "com/openggf/audio/AllowedRuntime.java", """
                        package com.openggf.audio;
                        public class AllowedRuntime {
                            com.openggf.tools.audio.SoundTestApplication differentName;
                            com.openggf.tools.audio.AudioHelper siblingTool;
                        }
                        """,
                "com/openggf/tools/AllowedLauncher.java", """
                        package com.openggf.tools;
                        public class AllowedLauncher {
                            com.openggf.tools.audio.SoundTestApp app;
                            com.openggf.tools.audio.SoundTestApp.Options options;
                        }
                        """);
        compiledFixtures = directory.resolve("classes");
        Files.createDirectories(compiledFixtures);
        ArrayList<String> arguments = new ArrayList<>();
        arguments.addAll(java.util.List.of("-proc:none", "-d", compiledFixtures.toString()));
        for (var source : sources.entrySet()) {
            Path file = directory.resolve("sources").resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            arguments.add(file.toString());
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "The bytecode controls require the build JDK compiler");
        assertEquals(0, compiler.run(null, null, null, arguments.toArray(String[]::new)));
    }

    @ParameterizedTest
    @CsvSource({
            "com.openggf.audio.DirectAppField, com.openggf.tools.audio.SoundTestApp",
            "com.openggf.game.RuntimeMethodReference, com.openggf.tools.audio.SoundTestApp.main",
            "com.openggf.game.NestedAppField, com.openggf.tools.audio.SoundTestApp$Options"
    })
    void rejectsDirectAudioAndRuntimeDependencies(String origin, String target) {
        var classes = new ClassFileImporter().importPath(classFile(origin));
        var result = TestArchUnitRules.runtime_code_does_not_depend_on_sound_test_app.evaluate(classes);

        assertTrue(result.hasViolation());
        String report = result.getFailureReport().toString();
        assertTrue(report.contains(origin), report);
        assertTrue(report.contains(target), report);
    }

    @Test
    void permitsToolCallersAndTargetsOutsideTheExactAppIdentity() {
        var classes = new ClassFileImporter().importPaths(
                classFile("com.openggf.audio.AllowedRuntime"),
                classFile("com.openggf.tools.AllowedLauncher"));
        var result = TestArchUnitRules.runtime_code_does_not_depend_on_sound_test_app.evaluate(classes);

        assertFalse(result.hasViolation(), result.getFailureReport().toString());
    }

    private static Path classFile(String name) {
        return compiledFixtures.resolve(name.replace('.', '/') + ".class");
    }
}
