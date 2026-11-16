package com.openggf.game.soniccd;

import com.openggf.game.soniccd.media.CdHostedMedia;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The one resolver for the Sonic CD tests' optional user media; nothing here is bundled and absent inputs skip.
 *
 * <ul>
 *   <li>The USA disc ({@link #usaCue}): {@code -Dsoniccd.media.root=<dir with USA/*.cue or .chd>} when it is set,
 *   else {@code -Dsoniccd.disc.path=<the .cue or .chd>}, which the queued test wrapper sets from a verified CHD in
 *   the ROM folder ({@link CdHostedMedia#propertyDisc()}, the locator's property path).</li>
 *   <li>Another release, or a check of the sheet layout itself ({@link #regionCue}): only the media root's
 *   {@code USA}, {@code Europe} and {@code Japan} directories, one CUE sheet each.</li>
 *   <li>Native fixtures ({@link #nativeRoot}): {@code -Dsoniccd.native.root}, else {@code <media.root>/../native}
 *   when the media root is set, else absent; never a path beside the disc file.</li>
 * </ul>
 */
public final class CdTestMedia {
    public static final String NATIVE_ROOT_PROPERTY = "soniccd.native.root";
    private static final List<String> DISC_PROPERTIES =
            List.of(CdHostedMedia.MEDIA_ROOT_PROPERTY, CdHostedMedia.DISC_PATH_PROPERTY);

    private CdTestMedia() { }

    /**
     * The USA disc image: its CUE sheet, or a CHD ({@code soniccd.disc.path} may name either; the name predates CHD
     * support). Skips the test when neither disc property is set.
     */
    public static Path usaCue() throws IOException {
        Optional<Path> disc = CdHostedMedia.propertyDisc();
        Assumptions.assumeTrue(disc.isPresent(),
                "Set soniccd.disc.path (or soniccd.media.root) for Sonic CD disc checks");
        return disc.get();
    }

    /** The configured USA disc, or empty (no skip). */
    public static Optional<Path> optionalUsaDisc() throws IOException { return CdHostedMedia.propertyDisc(); }

    /** {@code -Dsoniccd.media.root}, for checks that need the regional layout; skips when it is not set. */
    public static Path mediaRoot() {
        String root = System.getProperty(CdHostedMedia.MEDIA_ROOT_PROPERTY);
        Assumptions.assumeTrue(root != null && !root.isBlank(),
                "Set soniccd.media.root (a folder of USA, Europe and Japan sheets) for the regional disc checks");
        return Path.of(root);
    }

    /** The single CUE sheet under {@code <media.root>/<region>}; skips without a media root. */
    public static Path regionCue(String region) throws IOException {
        Path directory = mediaRoot().resolve(region);
        Assumptions.assumeTrue(Files.isDirectory(directory), "No " + region + " disc folder under the media root");
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue"))
                    .toList();
            if (cues.size() != 1) {
                throw new IOException("Expected one " + region + " CUE sheet under " + directory + ", found " + cues);
            }
            return cues.getFirst();
        }
    }

    /** The release's disc: USA from {@link #usaCue}, another release from {@link #regionCue}. */
    public static Path disc(CdReleaseVariant variant) throws IOException {
        return switch (variant) {
            case USA -> usaCue();
            case EUROPE -> regionCue("Europe");
            case JAPAN -> regionCue("Japan");
        };
    }

    /** The native fixture root, or empty when neither the native nor the media root is set. */
    public static Optional<Path> nativeRoot() {
        String configured = System.getProperty(NATIVE_ROOT_PROPERTY);
        if (configured != null && !configured.isBlank()) return Optional.of(Path.of(configured));
        String media = System.getProperty(CdHostedMedia.MEDIA_ROOT_PROPERTY);
        if (media != null && !media.isBlank()) return Optional.of(Path.of(media, "..", "native").normalize());
        return Optional.empty();
    }

    /** A path under the native root, or empty when there is no native root (no existence check). */
    public static Optional<Path> nativePath(String relative) {
        return nativeRoot().map(root -> root.resolve(relative).normalize());
    }

    /** The native fixture file if it exists, else null (no skip). */
    public static Path optionalNativeFile(String relative) {
        return nativePath(relative).filter(Files::isRegularFile).orElse(null);
    }

    /** A native fixture file; skips the test when it is absent. */
    public static Path nativeFile(String relative) {
        Path file = nativePath(relative).orElse(null);
        Assumptions.assumeTrue(file != null, "Set soniccd.native.root (or soniccd.media.root) for native fixture "
                + relative);
        Assumptions.assumeTrue(Files.isRegularFile(file), "Native fixture absent: " + file);
        return file;
    }

    /** A native fixture directory; skips the test when it is absent. */
    public static Path nativeDir(String relative) {
        Path dir = nativePath(relative).orElse(null);
        Assumptions.assumeTrue(dir != null, "Set soniccd.native.root (or soniccd.media.root) for native capture "
                + relative);
        Assumptions.assumeTrue(Files.isDirectory(dir), "Native capture absent: " + dir);
        return dir;
    }

    /**
     * Clears both disc properties for a check of the locator's own search, and restores them on close. Without it the
     * wrapper's {@code soniccd.disc.path} would answer every locate before the configuration is consulted.
     */
    public static Restore withoutDiscProperties() {
        String[] saved = DISC_PROPERTIES.stream().map(System::getProperty).toArray(String[]::new);
        DISC_PROPERTIES.forEach(System::clearProperty);
        return () -> {
            for (int i = 0; i < saved.length; i++) {
                if (saved[i] == null) System.clearProperty(DISC_PROPERTIES.get(i));
                else System.setProperty(DISC_PROPERTIES.get(i), saved[i]);
            }
        };
    }

    /** Restores what {@link #withoutDiscProperties} cleared. */
    @FunctionalInterface
    public interface Restore extends AutoCloseable {
        @Override void close();
    }
}
