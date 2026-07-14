package com.openggf.tools.modsdk;

import com.openggf.mods.TrackKey;
import com.openggf.mods.code.ModLevelExportStagingValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;

/** No-clobber private-staging publication for a validated TMX compilation. */
final class TmxLevelExportPublisher {
    Path publish(Path requested, TmxLevelImporter.Parsed source, TmxLevelImporter.Palette palette,
                 TmxLevelImporter.Profiles profiles, TmxLevelImporter.Compiled compiled,
                 TrackKey music) throws IOException {
        Path output = requested.toAbsolutePath().normalize();
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) throw new IOException("TMX output already exists: " + output);
        Path parent = Objects.requireNonNull(output.getParent(), "TMX output parent");
        Files.createDirectories(parent);
        Path staging = Files.createTempDirectory(parent, output.getFileName() + ".tmp-");
        try {
            new TmxLevelExportWriter().write(staging, source, palette, profiles, compiled, music);
            new ModLevelExportStagingValidator().validate(staging);
            Files.move(staging, output);
            return output;
        } catch (IOException | RuntimeException failure) {
            cleanup(staging, failure);
            throw failure;
        }
    }

    private static void cleanup(Path root, Throwable failure) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
    }
}
