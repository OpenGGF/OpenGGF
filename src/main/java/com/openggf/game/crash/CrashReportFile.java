package com.openggf.game.crash;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Saves each report beside the other per-install folders ({@code mods/}, {@code projects/}). */
public final class CrashReportFile {
    private static final Logger LOGGER = Logger.getLogger(CrashReportFile.class.getName());
    public static final Path DEFAULT_DIRECTORY = Path.of("crash-reports");
    private static final DateTimeFormatter NAME =
            DateTimeFormatter.ofPattern("'crash-'yyyyMMdd-HHmmss-SSS'.txt'").withZone(ZoneOffset.UTC);

    private CrashReportFile() { }

    /**
     * Writes the full report and returns where it went, or empty when the folder cannot be written.
     * The fault is logged in full either way, so a failed save loses nothing the log keeps.
     */
    public static Optional<Path> write(CrashReport report, Path directory) {
        String text = report.fullText();
        LOGGER.log(Level.SEVERE, "Engine fault ended the session\n" + text);
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve(NAME.format(report.time()));
            Files.writeString(file, text, StandardCharsets.UTF_8);
            return Optional.of(file.toAbsolutePath().normalize());
        } catch (IOException | SecurityException unwritable) {
            LOGGER.warning("Could not save the crash report in " + directory.toAbsolutePath() + ": " + unwritable);
            return Optional.empty();
        }
    }
}
