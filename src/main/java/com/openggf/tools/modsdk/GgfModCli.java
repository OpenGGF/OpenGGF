package com.openggf.tools.modsdk;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.util.Objects;
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
        if (args.length != 2 || !"validate".equals(args[0])) {
            output.println("Usage: ggfmod validate <mod.jar>");
            return 1;
        }
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
}
