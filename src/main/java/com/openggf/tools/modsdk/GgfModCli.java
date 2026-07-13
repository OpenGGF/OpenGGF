package com.openggf.tools.modsdk;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Command-line entrypoint for the OpenGGF mod SDK. */
public final class GgfModCli {
    private GgfModCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out));
    }

    public static int run(String[] args, PrintStream output) {
        return run(args, output, path -> new ModJarValidator().validate(path));
    }

    static int run(String[] args, PrintStream output,
                   Function<Path, ModJarValidator.Report> validator) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(validator, "validator");
        if (args.length == 0) return usage(output);
        try {
            return switch (args[0]) {
                case "validate" -> validate(args, output, validator);
                case "convert" -> convert(args, output);
                case "init" -> init(args, output);
                case "package" -> packageMod(args, output);
                case "run" -> runEngine(args, output);
                default -> usage(output);
            };
        } catch (InvalidPathException error) {
            output.println("ERROR CLI_INPUT_INVALID Invalid path");
            return 1;
        } catch (Exception error) {
            String message = error.getMessage();
            output.println("ERROR COMMAND_FAILED "
                    + (message == null || message.isBlank() ? error.getClass().getSimpleName() : message));
            return 1;
        }
    }

    private static int validate(String[] args, PrintStream output,
                                Function<Path, ModJarValidator.Report> validator) {
        if (args.length != 2) return usage(output);
        ModJarValidator.Report report;
        try {
            report = Objects.requireNonNull(validator.apply(Path.of(args[1])), "validator report");
        } catch (InvalidPathException error) {
            output.println("1. ERROR CLI_INPUT_INVALID Invalid mod jar path");
            return 1;
        } catch (RuntimeException error) {
            String message = error.getMessage();
            output.println("1. ERROR VALIDATION_FAILED "
                    + (message == null || message.isBlank() ? error.getClass().getSimpleName() : message));
            return 1;
        }
        report.numberedLines().forEach(output::println);
        if (report.findings().isEmpty()) {
            output.println("Validation passed: 0 findings");
        }
        return report.valid() ? 0 : 1;
    }

    private static int convert(String[] args, PrintStream output) throws IOException {
        if (args.length < 2) return usage(output);
        int playableCount = 0;
        int playableIndex = -1;
        for (int i = 2; i < args.length; i++) {
            if ("--playable".equals(args[i])) {
                playableCount++;
                playableIndex = i;
            }
        }
        boolean playable = "art".equals(args[1]) && playableCount == 1 && playableIndex == 2;
        if (playableCount > 0 && !playable) {
            throw new IllegalArgumentException(
                    "Invalid --playable placement; use it once immediately after 'convert art'");
        }
        String[] normalized = playable ? removeArgument(args, 2) : args;
        Map<String,String> flags = flags(normalized, 2);
        switch (args[1]) {
            case "art" -> {
                if (playable) {
                    var result = new PlayableArtConverter().convert(path(flags, "--image"),
                            path(flags, "--sheet"), path(flags, "--out"));
                    output.println("WARNING generated trivial full-frame DPLC runs; bank cost="
                            + result.bankSize() + " patterns");
                } else {
                    new ArtConverter().convert(path(flags, "--image"), path(flags, "--sheet"),
                            path(flags, "--out"));
                }
            }
            case "level" -> convertLevel(flags, output);
            case "audio" -> new AudioConverter().convert(required(flags, "--owner"),
                    path(flags, "--manifest"), path(flags, "--root"), path(flags, "--out"));
            default -> { return usage(output); }
        }
        output.println("Conversion completed");
        return 0;
    }

    private static void convertLevel(Map<String, String> flags, PrintStream output) throws IOException {
        boolean fromExport = flags.containsKey("--from-export");
        boolean fromTmx = flags.containsKey("--from-tmx");
        if (fromExport == fromTmx) {
            throw new IllegalArgumentException(
                    "Select exactly one level source: --from-export or --from-tmx");
        }
        if (fromExport) {
            requireExactFlags(flags, Set.of("--from-export", "--out"));
            new LevelConverter().convert(path(flags, "--from-export"), path(flags, "--out"));
            return;
        }

        requireExactFlags(flags, Set.of("--from-tmx", "--palette", "--solid-tiles", "--out"));
        Path solidTiles = flags.containsKey("--solid-tiles") ? path(flags, "--solid-tiles") : null;
        var result = new TmxLevelImporter().importLevel(path(flags, "--from-tmx"),
                path(flags, "--palette"), solidTiles, path(flags, "--out"));
        result.warnings().forEach(warning -> output.println("WARNING " + warning));
    }

    private static void requireExactFlags(Map<String, String> flags, Set<String> allowed) {
        for (String flag : flags.keySet()) {
            if (!allowed.contains(flag)) {
                throw new IllegalArgumentException("Invalid flag for selected conversion mode: " + flag);
            }
        }
    }

    private static String[] removeArgument(String[] args, int index) {
        String[] result = new String[args.length - 1];
        System.arraycopy(args, 0, result, 0, index);
        System.arraycopy(args, index + 1, result, index, args.length - index - 1);
        return result;
    }

    private static int init(String[] args, PrintStream output) throws IOException {
        if (args.length < 2) return usage(output);
        Map<String,String> flags = flags(args, 2);
        Path result = new ProjectScaffolder().scaffold(Path.of(args[1]),
                required(flags, "--id"), required(flags, "--package"));
        output.println("Created " + result);
        return 0;
    }

    private static int packageMod(String[] args, PrintStream output) throws IOException {
        Map<String,String> flags = flags(args, 1);
        Path out = path(flags, "--out");
        JarPackager.packageDirectory(path(flags, "--input"), out);
        output.println("Packaged " + out.toAbsolutePath().normalize());
        return 0;
    }

    private static int runEngine(String[] args, PrintStream output) throws IOException, InterruptedException {
        if (args.length != 2) return usage(output);
        Process process = new ProcessBuilder(engineCommand(Path.of(args[1])))
                .inheritIO().start();
        int exit = process.waitFor();
        if (exit != 0) output.println("Engine exited with status " + exit);
        return normalizeProcessExit(exit);
    }

    static int normalizeProcessExit(int exit){return exit==0?0:1;}

    static List<String> engineCommand(Path buildOutput) {
        Path root = Objects.requireNonNull(buildOutput, "buildOutput").toAbsolutePath().normalize();
        if (!java.nio.file.Files.isDirectory(root))
            throw new IllegalArgumentException("Run input must be an exploded build directory: " + root);
        String executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java").toString();
        return List.of(executable, "-Dggfmod.dev.modDir=" + root,
                "-cp", System.getProperty("java.class.path"), "com.openggf.Engine");
    }

    private static Map<String,String> flags(String[] args, int start) {
        if ((args.length - start) % 2 != 0) throw new IllegalArgumentException("Flags require values");
        java.util.LinkedHashMap<String,String> result = new java.util.LinkedHashMap<>();
        for (int i=start;i<args.length;i+=2) {
            if (!args[i].startsWith("--") || result.putIfAbsent(args[i], args[i+1]) != null)
                throw new IllegalArgumentException("Invalid or duplicate flag: " + args[i]);
        }
        return Map.copyOf(result);
    }

    private static String required(Map<String,String> flags, String name) {
        String value = flags.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing required flag " + name);
        return value;
    }

    private static Path path(Map<String,String> flags, String name) { return Path.of(required(flags, name)); }

    private static int usage(PrintStream output) {
        output.println("Usage: ggfmod validate <mod.jar> | init <dir> --id <id> --package <java.pkg>");
        output.println("       ggfmod convert art [--playable] --image <png> --sheet <yaml> --out <ggfs|ggfp>");
        output.println("       ggfmod convert level --from-export <dir> --out <dir>");
        output.println("       ggfmod convert level --from-tmx <map.tmx> --palette <GPAL>"
                + " [--solid-tiles <profile-dir>] --out <dir>");
        output.println("       ggfmod convert audio --owner <id> --manifest <yaml> --root <dir> --out <dir>");
        output.println("       ggfmod package --input <classes/resources> --out <jar> | run <build-output>");
        return 1;
    }
}
