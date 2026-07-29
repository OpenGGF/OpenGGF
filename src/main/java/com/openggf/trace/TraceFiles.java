package com.openggf.trace;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/** Shared resolution and UTF-8 reader support for plain or gzip trace files. */
public final class TraceFiles {

    private TraceFiles() {
    }

    public static Path resolve(Path directory, String fileName) {
        Path plain = directory.resolve(fileName);
        if (Files.isRegularFile(plain)) {
            return plain;
        }
        Path gzip = directory.resolve(fileName + ".gz");
        return Files.isRegularFile(gzip) ? gzip : null;
    }

    public static BufferedReader openReader(Path path) throws IOException {
        if (!path.getFileName().toString().endsWith(".gz")) {
            return Files.newBufferedReader(path, StandardCharsets.UTF_8);
        }
        InputStream input = Files.newInputStream(path);
        try {
            return new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(input), StandardCharsets.UTF_8));
        } catch (IOException e) {
            input.close();
            throw e;
        }
    }
}
