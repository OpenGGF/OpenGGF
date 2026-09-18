package com.openggf.data;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomImageClassifier.LogicalWindow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Every user-supplied ROM image the engine can see, and how each logical ROM
 * is served from them.
 *
 * <p>Built from the three per-game configuration keys plus a scan of
 * {@code roms.directory}. Identity comes from size and header, never from the
 * filename. A per-game key is an explicit override: when it names an existing
 * file that file is served (as the window that contains the game, or whole
 * when the layout is not recognised), and when it names a missing file the
 * game fails closed with the configured value rather than silently using
 * another image. The built-in default names ({@code s1.gen} and so on) are
 * only hints: when the hinted file is absent, or the key is blank, the
 * directory scan applies. Precedence for a logical ROM that several scanned
 * images contain: a hash-verified image, then the first image in directory
 * (name) order. A composite ({@code SK} + {@code S3} for
 * {@code S3K}) is used when no single image contains the logical ROM, or
 * first when {@code roms.preferComposite} is set.
 *
 * <p>Nothing here writes, joins or patches a file. Served bytes are views over
 * the loaded images ({@link RomByteReader#window} and
 * {@link RomByteReader#concat}).
 */
public final class RomImageCatalogue {
    private static final Logger LOGGER = Logger.getLogger(RomImageCatalogue.class.getName());
    private static final List<String> IMAGE_EXTENSIONS = List.of(".gen", ".bin", ".md");

    /** How the winning image was chosen. */
    public enum Source {
        EXPLICIT_KEY,
        VERIFIED_IMAGE,
        DIRECTORY_ORDER,
        COMPOSITE
    }

    /** One window of one image that contributes to a logical ROM. */
    public record Part(RomIdentity rom, PhysicalImage image, int offset, int length) {
        public boolean wholeImage() {
            return offset == 0 && length == image.size();
        }

        @Override
        public String toString() {
            return rom + " from " + image.path().getFileName()
                    + (wholeImage() ? "" : String.format(" [0x%X..0x%X)", offset, offset + length));
        }
    }

    /** The chosen way to serve a logical ROM. */
    public record Resolution(RomIdentity rom, List<Part> parts, Source source) {
        public Resolution {
            parts = List.copyOf(parts);
        }

        public boolean isComposite() {
            return parts.size() > 1;
        }

        /** The image path when the logical ROM is exactly one whole file. */
        public Optional<Path> wholeImagePath() {
            return parts.size() == 1 && parts.get(0).wholeImage()
                    ? Optional.of(parts.get(0).image().path()) : Optional.empty();
        }

        /** A key that changes when any contributing file changes on disk. */
        public String changeToken() {
            StringBuilder token = new StringBuilder();
            for (Part part : parts) {
                if (token.length() > 0) {
                    token.append('+');
                }
                token.append(part.image().changeToken())
                        .append('[').append(part.offset()).append('+').append(part.length()).append(']');
            }
            return token.toString();
        }

        public String describe() {
            StringBuilder text = new StringBuilder(rom.toString()).append(" (").append(source.name().toLowerCase(Locale.ROOT)).append("): ");
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) {
                    text.append(" + ");
                }
                text.append(parts.get(i));
            }
            return text.toString();
        }
    }

    private final List<PhysicalImage> images;
    private final Map<RomIdentity, PhysicalImage> explicit;
    private final Map<RomIdentity, String> explicitValues;
    /** Non-default configured values that name a file which does not exist: fail closed. */
    private final Map<RomIdentity, String> explicitMissing;
    private final boolean preferComposite;
    private final Map<RomIdentity, Optional<Resolution>> resolutions = new EnumMap<>(RomIdentity.class);
    private final Map<RomIdentity, RomByteReader> readers = new EnumMap<>(RomIdentity.class);

    /**
     * @param images          candidate images in precedence order (explicit
     *                        files first, then directory order)
     * @param explicit        images named by the per-game keys, by the logical
     *                        ROM the key stands for
     * @param preferComposite whether a composite beats a single image that
     *                        contains the whole logical ROM
     */
    public RomImageCatalogue(List<PhysicalImage> images, Map<RomIdentity, PhysicalImage> explicit,
            boolean preferComposite) {
        this(images, explicit, Map.of(), Map.of(), preferComposite);
    }

    private RomImageCatalogue(List<PhysicalImage> images, Map<RomIdentity, PhysicalImage> explicit,
            Map<RomIdentity, String> explicitValues, Map<RomIdentity, String> explicitMissing,
            boolean preferComposite) {
        this.images = List.copyOf(images);
        this.explicit = Map.copyOf(explicit);
        this.explicitValues = Map.copyOf(explicitValues);
        this.explicitMissing = Map.copyOf(explicitMissing);
        this.preferComposite = preferComposite;
    }

    /** Builds the catalogue from the configuration and the process working directory. */
    public static RomImageCatalogue forCurrentWorkingDirectory(SonicConfigurationService configuration) {
        return build(configuration, RomLocationResolver.currentWorkingDirectory());
    }

    /**
     * Builds the catalogue: the three per-game keys (resolved against
     * {@code workingDirectory}) plus every {@code .gen}, {@code .bin} or
     * {@code .md} file in {@code roms.directory}. Missing or unreadable files
     * are skipped with a log line; header probing reads a few bytes per file
     * and no file is hashed here.
     */
    public static RomImageCatalogue build(SonicConfigurationService configuration, Path workingDirectory) {
        Objects.requireNonNull(configuration, "configuration");
        Path base = Objects.requireNonNull(workingDirectory, "workingDirectory").toAbsolutePath().normalize();
        Map<Path, PhysicalImage> byPath = new LinkedHashMap<>();
        Map<RomIdentity, PhysicalImage> explicit = new EnumMap<>(RomIdentity.class);
        Map<RomIdentity, String> explicitValues = new EnumMap<>(RomIdentity.class);
        Map<RomIdentity, String> explicitMissing = new EnumMap<>(RomIdentity.class);
        for (RomGame game : RomGame.values()) {
            String configured = configuration.getString(game.configurationKey());
            if (configured == null || configured.isBlank()) {
                continue;
            }
            explicitValues.put(game.identity(), configured);
            Path path = resolve(base, configured);
            if (!Files.isRegularFile(path)) {
                Object hint = configuration.getDefaultValue(game.configurationKey());
                if (hint != null && configured.equals(hint.toString())) {
                    LOGGER.fine("Default " + game.configurationKey() + " hint " + path
                            + " is absent; the catalogue scan applies");
                } else {
                    LOGGER.fine("Configured " + game.configurationKey() + " image does not exist: " + path);
                    explicitMissing.put(game.identity(), configured);
                }
                continue;
            }
            PhysicalImage image = probe(byPath, path);
            if (image != null) {
                explicit.put(game.identity(), image);
            } else {
                explicitMissing.put(game.identity(), configured);
            }
        }
        String directory = configuration.getString(SonicConfiguration.ROMS_DIRECTORY);
        Path scan = resolve(base, directory == null || directory.isBlank() ? "." : directory);
        if (Files.isDirectory(scan)) {
            try (Stream<Path> entries = Files.list(scan)) {
                entries.filter(RomImageCatalogue::looksLikeImage)
                        .sorted((a, b) -> a.getFileName().toString()
                                .compareToIgnoreCase(b.getFileName().toString()))
                        .forEach(path -> probe(byPath, path));
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not scan ROM directory " + scan, e);
            }
        } else {
            LOGGER.fine("ROM directory is not a directory: " + scan);
        }
        boolean preferComposite = configuration.getBoolean(SonicConfiguration.ROMS_PREFER_COMPOSITE);
        return new RomImageCatalogue(new ArrayList<>(byPath.values()), explicit, explicitValues,
                explicitMissing, preferComposite);
    }

    /** Every probed image, explicit files first and then in directory order. */
    public List<PhysicalImage> images() {
        return images;
    }

    /** The literal configured value of the per-game key for this logical ROM, if any. */
    public Optional<String> configuredValue(RomIdentity rom) {
        return Optional.ofNullable(explicitValues.get(rom));
    }

    /**
     * The configured value when the per-game key names a file that does not
     * exist (or cannot be probed) and is not the built-in default hint. Such a
     * logical ROM never resolves: the user asked for that file, so the engine
     * reports it missing instead of picking another image.
     */
    public Optional<String> explicitMissingValue(RomIdentity rom) {
        return Optional.ofNullable(explicitMissing.get(rom));
    }

    public boolean isAvailable(RomIdentity rom) {
        return resolve(rom).isPresent();
    }

    /** Decides how {@code rom} is served; memoised, and never reads file bodies unless a tie needs hashing. */
    public synchronized Optional<Resolution> resolve(RomIdentity rom) {
        Objects.requireNonNull(rom, "rom");
        Optional<Resolution> cached = resolutions.get(rom);
        if (cached != null) {
            return cached;
        }
        Optional<Resolution> resolved = resolveOnce(rom);
        resolutions.put(rom, resolved);
        return resolved;
    }

    /**
     * Opens a reader over the logical ROM. Readers are views over the loaded
     * images and are cached per logical ROM; the choice and its verification
     * status are logged once.
     */
    public synchronized RomByteReader open(RomIdentity rom) throws IOException {
        RomByteReader cached = readers.get(rom);
        if (cached != null) {
            return cached;
        }
        Resolution resolution = resolve(rom).orElseThrow(() ->
                new IOException("No user-supplied image contains logical ROM " + rom));
        RomByteReader[] views = new RomByteReader[resolution.parts().size()];
        for (int i = 0; i < views.length; i++) {
            Part part = resolution.parts().get(i);
            views[i] = part.image().reader().window(part.offset(), part.length());
        }
        RomByteReader reader = RomByteReader.concat(views);
        LOGGER.info("Logical ROM " + resolution.describe() + "; " + verificationLabel(resolution));
        readers.put(rom, reader);
        return reader;
    }

    /**
     * Reports whether the resolution's bytes match a known dump. This hashes
     * the contributing windows, so call it only when the bytes are needed
     * anyway (opening) or when the user asks.
     */
    public String verificationLabel(Resolution resolution) throws IOException {
        List<String> labels = new ArrayList<>();
        for (Part part : resolution.parts()) {
            RomFingerprint fingerprint = part.image().fingerprint(part.offset(), part.length());
            labels.add(RomIdentityTable.verify(part.rom(), fingerprint)
                    .map(known -> part.rom() + " verified as " + known.label())
                    .orElse(part.rom() + " unverified (" + fingerprint + ")"));
        }
        return String.join("; ", labels);
    }

    private Optional<Resolution> resolveOnce(RomIdentity rom) {
        if (explicitMissing.containsKey(rom)) {
            return Optional.empty();
        }
        PhysicalImage explicitImage = explicit.get(rom);
        if (explicitImage != null) {
            Optional<LogicalWindow> window = RomImageClassifier.windowOf(explicitImage, rom);
            if (window.isPresent()) {
                return Optional.of(single(rom, explicitImage, window.get(), Source.EXPLICIT_KEY));
            }
            // The user named this file explicitly: serve it whole and let the
            // game detectors judge it, exactly as a configured path always was.
            LOGGER.info("Configured image for " + rom + " has no recognised window for it (" + explicitImage
                    + "); serving the whole file as configured");
            return Optional.of(new Resolution(rom,
                    List.of(new Part(rom, explicitImage, 0, Math.toIntExact(explicitImage.size()))),
                    Source.EXPLICIT_KEY));
        }
        if (preferComposite) {
            Optional<Resolution> composite = composite(rom);
            if (composite.isPresent()) {
                return composite;
            }
        }
        List<Part> candidates = new ArrayList<>();
        for (PhysicalImage image : images) {
            RomImageClassifier.windowOf(image, rom)
                    .ifPresent(window -> candidates.add(new Part(rom, image, window.offset(), window.length())));
        }
        if (candidates.size() == 1) {
            return Optional.of(new Resolution(rom, candidates, Source.DIRECTORY_ORDER));
        }
        if (candidates.size() > 1) {
            for (Part candidate : candidates) {
                if (isVerified(candidate)) {
                    LOGGER.info("Several images contain " + rom + "; choosing the verified " + candidate);
                    return Optional.of(new Resolution(rom, List.of(candidate), Source.VERIFIED_IMAGE));
                }
            }
            LOGGER.info("Several images contain " + rom + ", none verified; choosing the first: "
                    + candidates.get(0));
            return Optional.of(new Resolution(rom, List.of(candidates.get(0)), Source.DIRECTORY_ORDER));
        }
        return composite(rom);
    }

    private Optional<Resolution> composite(RomIdentity rom) {
        List<RomIdentity> recipe = RomImageClassifier.COMPOSITES.get(rom);
        if (recipe == null) {
            return Optional.empty();
        }
        List<Part> parts = new ArrayList<>();
        for (RomIdentity ingredient : recipe) {
            Optional<Resolution> resolved = resolve(ingredient);
            if (resolved.isEmpty()) {
                return Optional.empty();
            }
            parts.addAll(resolved.get().parts());
        }
        return Optional.of(new Resolution(rom, parts, Source.COMPOSITE));
    }

    private static Resolution single(RomIdentity rom, PhysicalImage image, LogicalWindow window, Source source) {
        return new Resolution(rom, List.of(new Part(rom, image, window.offset(), window.length())), source);
    }

    private static boolean isVerified(Part part) {
        try {
            return RomIdentityTable.verify(part.rom(),
                    part.image().fingerprint(part.offset(), part.length())).isPresent();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not hash " + part.image().path(), e);
            return false;
        }
    }

    private static PhysicalImage probe(Map<Path, PhysicalImage> byPath, Path path) {
        Path key = path.toAbsolutePath().normalize();
        PhysicalImage existing = byPath.get(key);
        if (existing != null) {
            return existing;
        }
        try {
            PhysicalImage image = PhysicalImage.probe(key);
            byPath.put(key, image);
            LOGGER.fine("Probed ROM image " + image + " -> "
                    + RomImageClassifier.layoutOf(image).map(RomImageClassifier.Layout::label).orElse("unknown layout"));
            return image;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not probe ROM image " + key + ": " + e.getMessage());
            return null;
        }
    }

    private static boolean looksLikeImage(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static Path resolve(Path base, String value) {
        Path path = Path.of(value);
        return (path.isAbsolute() ? path : base.resolve(path)).normalize();
    }
}
