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
                case "art-keys" -> artKeys(args, output);
                case "sprites" -> SpriteSheetDump.run(java.util.Arrays.copyOfRange(args, 1, args.length), output);
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
        if (args.length < 2) return usage(output);
        Map<String, String> options = flags(args, 2);
        requireExactFlags(options, Set.of("--format", "--warnings"));
        boolean strictWarnings = warningPolicy(options);
        String format = reportFormat(options);
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
        printReport(report, format, strictWarnings, output);
        return report.valid() && (!strictWarnings || report.findings().isEmpty()) ? 0 : 1;
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

        requireExactFlags(flags, Set.of("--from-tmx", "--palette", "--solid-tiles", "--music", "--out"));
        Path solidTiles = flags.containsKey("--solid-tiles") ? path(flags, "--solid-tiles") : null;
        com.openggf.mods.TrackKey music = flags.containsKey("--music") ? parseTrackKey(flags.get("--music")) : null;
        var result = new TmxLevelImporter().importLevel(path(flags, "--from-tmx"),
                path(flags, "--palette"), solidTiles, path(flags, "--out"), music);
        result.warnings().forEach(warning -> output.println("WARNING " + warning));
    }

    /** Parses a {@code owner:localName} CLI value into a namespaced streamed-music track key. */
    private static com.openggf.mods.TrackKey parseTrackKey(String value) {
        String canonical = com.openggf.game.ModKeySyntax.requireDisplayKey(value);
        int separator = canonical.indexOf(':');
        return new com.openggf.mods.TrackKey(canonical.substring(0, separator), canonical.substring(separator + 1));
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
        requireExactFlags(flags, Set.of("--id", "--package", "--kind"));
        String pkg = flags.getOrDefault("--package", "example.mod");
        Path result = flags.containsKey("--kind")
                ? new ProjectScaffolder().scaffold(Path.of(args[1]), required(flags, "--id"), pkg, flags.get("--kind"))
                : new ProjectScaffolder().scaffold(Path.of(args[1]), required(flags, "--id"), pkg);
        output.println("Created " + result);
        return 0;
    }

    private static int packageMod(String[] args, PrintStream output) throws IOException {
        Map<String,String> flags = flags(args, 1);
        Path out = path(flags, "--out");
        requireExactFlags(flags, Set.of("--input", "--out", "--format", "--warnings"));
        boolean strictWarnings = warningPolicy(flags);
        String format = reportFormat(flags);
        ModJarValidator.Report report;
        try {
            report = JarPackager.packageDirectoryWithReport(path(flags, "--input"), out, strictWarnings);
        } catch (JarPackager.ValidationFailure failure) {
            printReport(failure.report(), format, strictWarnings, output);
            return 1;
        }
        printReport(report, format, strictWarnings, output);
        if ("text".equals(format)) output.println("Packaged " + out.toAbsolutePath().normalize());
        return 0;
    }

    private static boolean warningPolicy(Map<String, String> flags) {
        String policy = flags.getOrDefault("--warnings", "allow");
        if (!Set.of("allow", "error").contains(policy))
            throw new IllegalArgumentException("--warnings must be allow or error");
        return policy.equals("error");
    }

    private static String reportFormat(Map<String, String> flags) {
        String format = flags.getOrDefault("--format", "text");
        if (!Set.of("text", "json").contains(format))
            throw new IllegalArgumentException("--format must be text or json");
        return format;
    }

    private static void printReport(ModJarValidator.Report report, String format, boolean strict,
                                    PrintStream output) {
        long warnings = report.findings().stream()
                .filter(f -> f.severity() == ModJarValidator.Severity.WARNING).count();
        if (format.equals("json")) {
            try {
                output.println(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                        "formatVersion", 1, "valid", report.valid(), "warnings", warnings,
                        "warningPolicy", strict ? "error" : "allow", "findings", report.findings())));
            } catch (IOException failure) { throw new IllegalStateException(failure); }
        } else {
            report.numberedLines().forEach(output::println);
            output.println("Validation " + (report.valid() ? "passed" : "failed") + ": "
                    + report.findings().size() + " findings" + (warnings == 0 ? "" : " (" + warnings + " warnings)"));
        }
    }

    private static int artKeys(String[] args, PrintStream output) {
        String game = "any";
        if (args.length == 3 && args[1].equals("--game") && java.util.Set.of("s1", "s2", "s3k", "any").contains(args[2])) game = args[2];
        else if (args.length != 1) return usage(output);
        com.openggf.mods.StockArtOverrideCatalog.keys(game).forEach(output::println);
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
        return engineCommand(buildOutput, System.getProperty("os.name", ""));
    }

    static List<String> engineCommand(Path buildOutput, String operatingSystem) {
        Path root = Objects.requireNonNull(buildOutput, "buildOutput").toAbsolutePath().normalize();
        if (!java.nio.file.Files.isDirectory(root))
            throw new IllegalArgumentException("Run input must be an exploded build directory: " + root);
        String executable = Path.of(System.getProperty("java.home"), "bin",
                operatingSystem.startsWith("Windows") ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<>();
        command.add(executable);
        if (operatingSystem.startsWith("Mac")) command.add("-XstartOnFirstThread");
        command.addAll(List.of("-Dggfmod.dev.modDir=" + root,
                "-cp", System.getProperty("java.class.path"), "com.openggf.Engine"));
        return List.copyOf(command);
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
        output.println("Usage: ggfmod validate <mod.jar> [--format text|json] [--warnings allow|error] | init <dir> --id <id> --package <java.pkg> [--kind music|reskin|object|character|zone|scene|standalone]");
        output.println("       ggfmod convert art [--playable] --image <png> --sheet <yaml> --out <ggfs|ggfp>");
        output.println("       ggfmod convert level --from-export <dir> --out <dir>");
        output.println("       ggfmod convert level --from-tmx <map.tmx> --palette <GPAL>"
                + " [--solid-tiles <profile-dir>] [--music <owner:localName>] --out <dir>");
        output.println("       ggfmod convert audio --owner <id> --manifest <yaml> --root <dir> --out <dir>");
        output.println("       ggfmod package --input <classes/resources> --out <jar> [--format text|json] [--warnings allow|error] | run <build-output>");
        output.println("       ggfmod sprites <rom> <s1|s2|s3k> <out.png> art=<addr> map=<addr> [comp= dplc= layout="
                + " size= line= offset= pal=<addr>:<colours>:<line>] | char=<sonic|tails|knuckles>");
        output.println("       ggfmod art-keys [--game s1|s2|s3k|any]");
        return 1;
    }
}
