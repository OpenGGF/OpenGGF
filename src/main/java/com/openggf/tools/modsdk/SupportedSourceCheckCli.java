package com.openggf.tools.modsdk;

import com.openggf.mods.validation.ModValidator;
import java.nio.file.*;
import java.util.regex.Pattern;

/** Strict bytecode complement to the portable builder's javac plugin. */
public final class SupportedSourceCheckCli {
    private SupportedSourceCheckCli() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected classes directory and manifest");
        var matcher=Pattern.compile("(?m)^entrypoint:\\s*[\"']?([\\w.]+)").matcher(Files.readString(Path.of(args[1])));
        if (!matcher.find()) return; // Content-only projects have no Java entrypoint.
        var report=new ModValidator(ModValidator.ValidationMode.SUPPORTED_ONLY).validateDirectory(Path.of(args[0]),matcher.group(1));
        report.findings().forEach(System.err::println);
        if (!report.eligible()) throw new IllegalArgumentException("Supported mod validation failed");
    }
}
