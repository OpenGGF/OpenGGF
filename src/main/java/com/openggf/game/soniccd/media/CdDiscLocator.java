package com.openggf.game.soniccd.media;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.cd.DiscImage;
import com.openggf.data.cd.DiscTrack;
import com.openggf.game.soniccd.CdReleaseVariant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Finds the user's Sonic CD disc image: a {@code .cue} sheet with its track files, or a lossless CHD
 * ({@code .chd}, MAME chdman v5) of the same disc. The disc is
 * identified by content, as the ROM catalogue identifies cartridge images: the Sega CD header of its
 * data track, never the file name.
 *
 * <p>Where it looks, first match wins: the {@value CdHostedMedia#MEDIA_ROOT_PROPERTY} system property (tests
 * and tools: {@code <root>/USA/<disc>.cue} or {@code .chd}); the {@code roms.soniccd} key ({@code SONIC_CD_DISC}:
 * the sheet's or CHD's path, relative to the working directory); when that is blank, the ROM folder
 * ({@code roms.directory}, default the working directory) and its sub-folders two levels down, CUE sheets before
 * CHDs. A named image that
 * is not the USA disc is refused, not replaced by the folder search. Probing parses each image's layout and reads
 * 512 bytes of its data track; nothing is hashed here. Whole-disc verification happens when the game opens it ({@link CdHostedMedia}).
 */
public final class CdDiscLocator {
    /** Sub-folder depth searched under the ROM folder. */
    private static final int SCAN_DEPTH = 2;
    /** Sonic CD's track count: one data track and 34 audio tracks. */
    static final int TRACKS = 35;

    /** What was found: the USA disc's sheet or CHD, or why there is none. */
    public record Result(Optional<Path> disc, String detail) {
        public boolean found() { return disc.isPresent(); }
    }

    private CdDiscLocator() { }

    /** Locates the USA disc under the configured places; never throws for a missing or foreign disc. */
    public static Result locate(SonicConfigurationService config) {
        String property = System.getProperty(CdHostedMedia.MEDIA_ROOT_PROPERTY);
        if (property != null && !property.isBlank()) {
            try {
                return identify(List.of(CdHostedMedia.propertyDisc(property)), "-D" + CdHostedMedia.MEDIA_ROOT_PROPERTY);
            } catch (IOException missing) {
                return new Result(Optional.empty(), missing.getMessage());
            }
        }
        String configured = config == null ? null : config.getString(SonicConfiguration.SONIC_CD_DISC);
        if (configured != null && !configured.isBlank()) {
            // roms.soniccd names the sheet or CHD itself, as roms.sonic1/2/3k name an image: resolved against the
            // working directory, and refused (never replaced by the folder search) when it is not the USA disc.
            Path disc = workingDirectory().resolve(configured.trim()).normalize();
            if (!Files.isRegularFile(disc)) {
                return new Result(Optional.empty(), "roms.soniccd names " + disc + ", which is not a file. Set it to "
                        + "the Sonic CD (USA) disc's .cue sheet or .chd, or leave it blank to search the ROM folder.");
            }
            return identify(List.of(disc), "roms.soniccd");
        }
        Path folder = romFolder(config);
        if (!Files.isDirectory(folder)) {
            return new Result(Optional.empty(), "The ROM folder " + folder + " does not exist. " + placementHint());
        }
        return identify(discsUnder(folder), folder.toString());
    }

    /** The folder the cartridge ROM scan uses: {@code roms.directory}, resolved against the working directory. */
    static Path romFolder(SonicConfigurationService config) {
        Path base = workingDirectory();
        String directory = config == null ? null : config.getString(SonicConfiguration.ROMS_DIRECTORY);
        Path path = Path.of(directory == null || directory.isBlank() ? "." : directory);
        return (path.isAbsolute() ? path : base.resolve(path)).normalize();
    }

    /** The working directory as RomLocationResolver reads it: user.dir when it is set. */
    private static Path workingDirectory() {
        String userDirectory = System.getProperty("user.dir");
        return (userDirectory == null || userDirectory.isBlank() ? Path.of("") : Path.of(userDirectory))
                .toAbsolutePath().normalize();
    }

    static String placementHint() {
        return "Put the Sonic CD (USA) disc image, either its .cue sheet with all 35 track files or a lossless "
                + ".chd made from them, in a folder inside your ROM folder (roms.directory in config.yaml).";
    }

    /** True for the image files the scan probes: CUE sheets and CHDs. */
    static boolean isDiscImageName(Path path) {
        if (path.getFileName() == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".cue") || name.endsWith(".chd");
    }

    private static List<Path> discsUnder(Path folder) {
        List<Path> cues = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(folder, SCAN_DEPTH + 1)) {
            walk.filter(CdDiscLocator::isDiscImageName)
                    .filter(Files::isRegularFile)
                    .filter(p -> !hidden(folder, p))
                    // CUE sheets before CHDs, so a folder holding both forms of the disc keeps using the sheet.
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString().toLowerCase(Locale.ROOT)
                                    .endsWith(".chd"))
                            .thenComparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
                    .forEach(cues::add);
        } catch (IOException | java.io.UncheckedIOException unreadable) {
            // An unreadable sub-folder ends the walk; what was listed so far is still probed.
        }
        return cues;
    }

    private static boolean hidden(Path root, Path cue) {
        for (Path part : root.relativize(cue)) if (part.toString().startsWith(".")) return true;
        return false;
    }

    /** The first image whose data track carries the USA header, else the reason none qualifies. */
    private static Result identify(List<Path> cues, String where) {
        List<String> seen = new ArrayList<>();
        for (Path cue : cues) {
            Probe probe = probe(cue);
            if (probe.variant() == CdReleaseVariant.USA && probe.problem() == null) {
                return new Result(Optional.of(cue), cue.toString());
            }
            if (probe.variant() != null || probe.problem() != null) seen.add(probe.describe(cue));
        }
        if (seen.isEmpty()) {
            return new Result(Optional.empty(), "No Sonic CD disc image was found in " + where + ". " + placementHint());
        }
        return new Result(Optional.empty(), String.join(" ", seen) + " OpenGGF plays the USA release of Sonic CD.");
    }

    /** One sheet's header agreement and track shape; {@code variant} is null for a disc that is not Sonic CD. */
    record Probe(CdReleaseVariant variant, String problem) {
        String describe(Path cue) {
            String name = cue.getFileName().toString();
            if (problem != null) return name + ": " + problem + ".";
            return name + " is the " + variant.name().charAt(0) + variant.name().substring(1).toLowerCase(Locale.ROOT)
                    + " release.";
        }
    }

    static Probe probe(Path cue) {
        try (DiscImage disc = DiscImage.open(cue)) {
            List<DiscTrack> data = disc.tracks().stream().filter(t -> t.mode().isMode1()).toList();
            if (data.size() != 1) return new Probe(null, null);
            CdDiscHeader header = CdDiscHeader.parse(disc.readMode1Bytes(data.getFirst().number(), 0, 0x200));
            CdReleaseVariant variant = header.observedVariant().orElse(null);
            if (variant == null) return new Probe(null, null);
            if (disc.tracks().size() != TRACKS) {
                return new Probe(variant, "a Sonic CD disc with " + disc.tracks().size() + " of its " + TRACKS
                        + " tracks");
            }
            return new Probe(variant, null);
        } catch (IOException unreadable) {
            // An image whose track files are missing, truncated or malformed is reported only when no USA
            // disc is found, so a damaged Sonic CD image is named rather than silently ignored.
            return new Probe(null, "the disc image could not be read (" + unreadable.getMessage() + ")");
        }
    }
}
