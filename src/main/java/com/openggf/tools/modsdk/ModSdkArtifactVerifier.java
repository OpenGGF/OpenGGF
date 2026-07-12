package com.openggf.tools.modsdk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

/** Post-package verification for the two-artifact SDK distribution contract. */
public final class ModSdkArtifactVerifier {
    private ModSdkArtifactVerifier() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("engine, fat engine, and SDK jars required");
        Path engine=Path.of(args[0]), fat=Path.of(args[1]), sdk=Path.of(args[2]);
        rejectTooling(engine); rejectTooling(fat); verifySdk(sdk);
        Result combined = launch(fat, sdk);
        if (combined.exitCode != 1 || !combined.output.contains("MOD_JAR_INVALID"))
            throw new IllegalStateException("CLI did not launch with both artifacts: " + combined.output);
        Result sdkOnly = launch(null, sdk);
        if (sdkOnly.exitCode == 0 || !sdkOnly.output.contains("NoClassDefFoundError"))
            throw new IllegalStateException("SDK unexpectedly ran standalone: " + sdkOnly.output);
    }

    private static void rejectTooling(Path jar) throws IOException {
        try (JarFile archive=new JarFile(jar.toFile())) {
            if (archive.stream().anyMatch(entry -> entry.getName().startsWith("com/openggf/tools/modsdk/")
                    || entry.getName().startsWith("META-INF/openggf-mod-sdk/")))
                throw new IllegalStateException("Engine artifact leaks SDK tooling: " + jar);
        }
    }

    private static void verifySdk(Path jar) throws IOException {
        try (JarFile archive=new JarFile(jar.toFile())) {
            List<String> files=archive.stream().filter(entry -> !entry.isDirectory()).map(e -> e.getName()).toList();
            if (!files.contains("com/openggf/tools/modsdk/GgfModCli.class")
                    || !files.contains("META-INF/openggf-mod-sdk/templates/pom.xml.template"))
                throw new IllegalStateException("SDK artifact is incomplete");
            if (files.stream().anyMatch(name -> name.startsWith("com/openggf/")
                    && !name.startsWith("com/openggf/tools/modsdk/")))
                throw new IllegalStateException("SDK artifact leaks engine internals");
        }
    }

    private static Result launch(Path engine, Path sdk) throws IOException, InterruptedException {
        String javaExecutable=Path.of(System.getProperty("java.home"),"bin",
                System.getProperty("os.name","").startsWith("Windows")?"java.exe":"java").toString();
        String cp=engine == null ? sdk.toString()
                : engine + java.io.File.pathSeparator + sdk;
        Process process=new ProcessBuilder(javaExecutable,"-cp",cp,"com.openggf.tools.modsdk.GgfModCli",
                "validate",sdk.resolveSibling("artifact-verifier-missing.jar").toString())
                .redirectErrorStream(true).start();
        String output=new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(process.waitFor(),output);
    }

    private record Result(int exitCode,String output) { }
}
