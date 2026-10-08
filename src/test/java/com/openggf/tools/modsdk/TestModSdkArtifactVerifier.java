package com.openggf.tools.modsdk;

import com.openggf.tests.TestSessionOutputPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class TestModSdkArtifactVerifier {
    @TempDir Path work;

    @Test
    void rejectsARealSupportClassLeakedIntoEitherEngineArtifact() throws Exception {
        Path archive = jarWithCompiled("leaked.jar", "com/openggf/mods/testing/ModTestKit.class");
        var failure = assertThrows(IllegalStateException.class, () -> ModSdkArtifactVerifier.rejectTooling(archive));
        assertTrue(failure.getMessage().contains("Engine artifact leaks creator tooling"));
    }

    @Test
    void acceptsGameplayRegistryInAnEngineArtifact() throws Exception {
        Path archive = jarWithCompiled("engine.jar", "com/openggf/game/rewind/RewindRegistry.class");
        assertDoesNotThrow(() -> ModSdkArtifactVerifier.rejectTooling(archive));
    }

    private Path jarWithCompiled(String name, String relative) throws Exception {
        Path archive = work.resolve(name);
        try (var output = new JarOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new JarEntry(relative));
            Files.copy(TestSessionOutputPaths.compiledClasses().resolve(relative), output);
            output.closeEntry();
        }
        return archive;
    }
}
