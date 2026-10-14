package com.openggf.mods.code;

import com.openggf.level.objects.RewindClassResolver;
import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Each caller owns an explicit source selection, output and exact loader; no compiled fixture cache. */
public final class SecondStrikeSourceFixture {
    public static final Path MAIN = Path.of("examples/second-strike/src/main/java");
    public static final Path ENEMIES = MAIN.resolve("secondstrike/enemies");
    private SecondStrikeSourceFixture() { }

    public static void compileClasses(Path output, Path... sources) throws Exception {
        Files.createDirectories(output);
        var args = new ArrayList<>(List.of("--release", "25", "-cp", System.getProperty("java.class.path"), "-d", output.toString()));
        for (Path source : sources) {
            if (Files.isDirectory(source)) {
                try (var files = Files.walk(source)) {
                    files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
                }
            } else args.add(source.toString());
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
    }

    public static URLClassLoader compile(Path output, Path... sources) throws Exception {
        compileClasses(output, sources);
        return new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, SecondStrikeSourceFixture.class.getClassLoader());
    }

    public static RewindClassResolver ownedResolver(URLClassLoader creator) {
        return new RewindClassResolver() {
            @Override public Optional<Class<?>> resolve(String owner, String name) {
                if (owner == null) return ENGINE_ONLY.resolve(null, name);
                if (!owner.equals("second-strike") || !name.startsWith("secondstrike.")) return Optional.empty();
                try {
                    var type = Class.forName(name, false, creator);
                    return type.getClassLoader() == creator ? Optional.of(type) : Optional.empty();
                } catch (ClassNotFoundException missing) { return Optional.empty(); }
            }
            @Override public Optional<String> ownerOf(Class<?> type) {
                return type.getClassLoader() == creator && type.getName().startsWith("secondstrike.")
                        ? Optional.of("second-strike") : Optional.empty();
            }
        };
    }
}
