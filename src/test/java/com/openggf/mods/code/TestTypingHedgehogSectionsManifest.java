package com.openggf.mods.code;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Catalogue completeness is separate from the ROM-backed playable-section gate. */
class TestTypingHedgehogSectionsManifest {
    static final Path SOURCE = Path.of("examples/typing-of-the-hedgehog/src/main");
    @TempDir static Path temp;
    static URLClassLoader loader;

    @BeforeAll static void compile() throws Exception {
        Path output = Files.createDirectory(temp.resolve("classes"));
        Path empty = Files.createDirectory(temp.resolve("empty"));
        var errors = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, errors, errors,
                "--release", "25", "-classpath", empty.toString(), "-d", output.toString(),
                SOURCE.resolve("java/typinghog/rails/SectionPlan.java").toString(),
                SOURCE.resolve("java/typinghog/rails/Rail.java").toString()), errors.toString(StandardCharsets.UTF_8));
        loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }

    @AfterAll static void close() throws Exception { if (loader != null) loader.close(); }

    static Map<?, ?> parse(String text) throws Exception {
        try { return (Map<?, ?>) loader.loadClass("typinghog.rails.SectionPlan").getMethod("parse", String.class).invoke(null, text); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception cause) throw cause;
            throw error;
        }
    }

    @Test void everyStoryStopHasExactlyOneCourse() throws Exception {
        Set<String> expected = new HashSet<>();
        for (String raw : Files.readAllLines(SOURCE.resolve("resources/hub/story.txt"))) {
            String line = raw.strip();
            if (!line.isEmpty() && !line.startsWith("#")) {
                String[] fields = line.split("\\s+");
                assertTrue(expected.add(fields[0] + ":" + fields[1] + ":" + fields[2]), "Duplicate story stop");
            }
        }
        Map<?, ?> sections = parse(Files.readString(SOURCE.resolve("resources/sections.txt")));
        Set<String> actual = new HashSet<>();
        sections.keySet().forEach(key -> actual.add((String) key));
        for (String file : Files.readAllLines(SOURCE.resolve("resources/rails/index.txt"))) {
            if (file.isBlank() || file.startsWith("#")) continue;
            Object rail = loader.loadClass("typinghog.rails.Rail").getMethod("parse", String.class)
                    .invoke(null, Files.readString(SOURCE.resolve("resources/rails/" + file)));
            Class<?> type = rail.getClass();
            String key = type.getMethod("game").invoke(rail) + ":" + type.getMethod("zone").invoke(rail)
                    + ":" + type.getMethod("act").invoke(rail);
            assertTrue(actual.add(key), "A rail and section must not silently compete: " + key);
        }
        assertEquals(31, expected.size());
        assertEquals(27, sections.size());
        assertEquals(expected, actual);
        for (var entry : sections.entrySet()) {
            String mode = entry.getValue().getClass().getMethod("mode").invoke(entry.getValue()).toString();
            assertEquals(entry.getKey().equals("s2:8:0") ? "ESCORT"
                    : entry.getKey().equals("s3k:12:0") ? "FINALE" : "GROUND", mode);
        }
    }

    @Test void parserRejectsAmbiguousOrInvalidAuthoredSections() throws Exception {
        for (String invalid : new String[]{"s1 1 2", "s1 1 2 GROUND extra", "s4 0 0 GROUND",
                "s1 -1 0 GROUND", "s1 1 -1 GROUND", "s1 1 2 UNKNOWN",
                "s1 1 2 GROUND\ns1 1 2 ESCORT"})
            assertThrows(IllegalArgumentException.class, () -> parse(invalid), invalid);
        var parsed = parse("# comment\ns1 1 2 GROUND # Marble\n\n");
        assertEquals(Set.of("s1:1:2"), parsed.keySet());
        assertThrows(UnsupportedOperationException.class, parsed::clear);
    }
}
