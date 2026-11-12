package com.openggf.tests;

import java.nio.file.Files;
import java.nio.file.Path;

/** Builds the example's authored sheets in an owned test directory, without source outputs. */
public final class ExampleCharacterArt {
    private ExampleCharacterArt() { }

    public static Path assemble(Path output) throws Exception {
        Files.createDirectories(output);
        for (String name : new String[]{"mica", "talon", "mica_powered_s2", "mica_powered_s3k", "talon_powered_s2", "talon_powered_s3k"}) {
            String source = name.contains("_powered_")
                    ? name.substring(0, name.indexOf("_powered_")) + "/powered/"
                            + name.substring(name.indexOf("_powered_") + "_powered_".length()) : name + "/sheet";
            Process process = new ProcessBuilder("python3", "tools/modding/playable_art/assemble.py",
                    "examples/mica-and-talon/art/" + source, output.toString()).inheritIO().start();
            if (process.waitFor() != 0) throw new IllegalStateException("Example sheet assembly failed: " + name);
        }
        return output;
    }
}
