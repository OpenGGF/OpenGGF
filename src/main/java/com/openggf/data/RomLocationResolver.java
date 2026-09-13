package com.openggf.data;

import com.openggf.configuration.SonicConfigurationService;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the file that serves a stock game, for tools that open a ROM by
 * path.
 *
 * <p>Resolution goes through the {@link RomImageCatalogue}: the per-game key
 * is an explicit override, otherwise the catalogue picks an image by size,
 * header and hash. A location is only returned when the logical ROM is one
 * whole file; a logical ROM served as a window of a lock-on dump or as a
 * composite of several images has no single path, and callers that need
 * bytes rather than a path use {@link RomManager} or the catalogue directly.
 * When nothing resolves, the literal configured value is returned unchanged
 * so the caller's "file does not exist" error names what the user typed.
 */
public final class RomLocationResolver {
    private final SonicConfigurationService configuration;
    private final Path workingDirectory;

    public RomLocationResolver(SonicConfigurationService configuration, Path workingDirectory) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory")
                .toAbsolutePath()
                .normalize();
    }

    public static RomLocationResolver forCurrentWorkingDirectory(
            SonicConfigurationService configuration) {
        return new RomLocationResolver(configuration, currentWorkingDirectory());
    }

    /** The process working directory, from {@code user.dir} when it is set. */
    static Path currentWorkingDirectory() {
        String userDirectory = System.getProperty("user.dir");
        return userDirectory == null || userDirectory.isBlank()
                ? Path.of("").toAbsolutePath()
                : Path.of(userDirectory);
    }

    public Optional<RomLocation> resolve(RomGame game) {
        Objects.requireNonNull(game, "game");
        String configuredValue = configuration.getString(game.configurationKey());
        RomImageCatalogue catalogue = RomImageCatalogue.build(configuration, workingDirectory);
        Optional<RomImageCatalogue.Resolution> resolution = catalogue.resolve(game.identity());
        if (resolution.isPresent()) {
            Optional<Path> wholeImage = resolution.get().wholeImagePath();
            if (wholeImage.isPresent()) {
                boolean explicit = resolution.get().source() == RomImageCatalogue.Source.EXPLICIT_KEY;
                return Optional.of(new RomLocation(
                        game,
                        explicit && configuredValue != null ? configuredValue : wholeImage.get().toString(),
                        wholeImage.get(),
                        explicit ? RomLocationSource.CONFIGURATION : RomLocationSource.CATALOGUE,
                        RomFingerprintPolicy.IDENTITY));
            }
            return Optional.empty();
        }
        if (configuredValue == null || configuredValue.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new RomLocation(
                game,
                configuredValue,
                resolvePath(Path.of(configuredValue)),
                RomLocationSource.CONFIGURATION,
                RomFingerprintPolicy.NONE));
    }

    public RomLocation explicit(RomGame game, Path path) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(path, "path");
        return new RomLocation(
                game,
                path.toString(),
                resolvePath(path),
                RomLocationSource.EXPLICIT_OVERRIDE,
                RomFingerprintPolicy.NONE);
    }

    private Path resolvePath(Path path) {
        return (path.isAbsolute() ? path : workingDirectory.resolve(path)).normalize();
    }
}
