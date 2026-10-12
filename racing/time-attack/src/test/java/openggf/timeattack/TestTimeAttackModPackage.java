package openggf.timeattack;

import com.openggf.tools.modsdk.GgfModCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundled Time Attack mod must package through {@code ggfmod} with warnings as errors, exactly
 * as the release build packages it: only public Mod API engine types, no static state the mod
 * validator rejects, no classes in reserved engine packages.
 */
class TestTimeAttackModPackage {
    @TempDir
    Path temp;

    @Test
    void modPackagesThroughGgfmodWithWarningsAsErrors() throws Exception {
        Path project = Path.of("racing/time-attack");
        Path classes = Files.createDirectory(temp.resolve("classes"));
        List<String> args = new ArrayList<>(List.of("--release", "21", "-nowarn",
                "-cp", System.getProperty("java.class.path"), "-d", classes.toString()));
        try (Stream<Path> files = Files.walk(project.resolve("src/main/java"))) {
            files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
        Path manifest = classes.resolve("META-INF/openggf-mod.yaml");
        Files.createDirectories(manifest.getParent());
        Files.copy(project.resolve("src/main/resources/META-INF/openggf-mod.yaml"), manifest);

        ByteArrayOutputStream report = new ByteArrayOutputStream();
        int status = GgfModCli.run(new String[] {"package", "--input", classes.toString(),
                "--out", temp.resolve("time-attack.jar").toString(), "--warnings", "error"},
                new PrintStream(report, true, StandardCharsets.UTF_8));
        String text = report.toString(StandardCharsets.UTF_8);
        assertEquals(0, status, text);
        assertTrue(text.contains("Validation passed: 0 findings"), text);
    }

    @Test
    void releaseBuildBundlesTheMod() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(pom.contains("<openggf.bundled.mods>racing/time-attack</openggf.bundled.mods>"),
                "Time Attack ships as the bundled first-party mod");
    }
}
