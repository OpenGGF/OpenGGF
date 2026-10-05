package com.openggf.mods.code;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Compiles the actual external mod sources: no engine dependency is available to them. */
class TestGolfProtocol {
    @TempDir Path temp;

    @Test void boundedTypedProtocolAndReconnectReceipts() throws Exception {
        runProbe(temp, "ProtocolProbe", "run");
    }

    static void runProbe(Path temp, String probe, String method) throws Exception {
        Path project = Path.of("examples/putt-putt-paradise");
        Path sources = project.resolve("src/main/java/paradise/net");
        assertTrue(Files.isDirectory(sources), "the external golf transport has not been implemented");
        Path classes = Files.createDirectories(temp.resolve("classes"));
        var args = new ArrayList<>(List.of("--release", "21", "-classpath", classes.toString(), "-d", classes.toString()));
        for (Path directory : List.of(sources, project.resolve("src/test/java/paradise/net"))) {
            try (var files = Files.walk(directory)) {
                files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
            }
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)),
                "external engine-free networking sources must compile on Java 21");
        try (var loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, null)) {
            try {
                loader.loadClass("paradise.net." + probe).getMethod(method).invoke(null);
            } catch (InvocationTargetException failed) {
                if (failed.getCause() instanceof Exception cause) throw cause;
                if (failed.getCause() instanceof Error cause) throw cause;
                throw failed;
            }
        }
    }
}
