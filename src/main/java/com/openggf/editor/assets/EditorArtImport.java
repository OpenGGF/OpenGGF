package com.openggf.editor.assets;

import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.MutableLevel;
import com.openggf.level.objects.BakedSheetReader;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.resources.ResourceLoader;
import com.openggf.tools.modsdk.ArtConverter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.IntConsumer;

/**
 * Editor facade over {@code ggfmod convert art}. Inputs live under the chosen project folder;
 * the converted sheet is loaded through the same bounded typed asset path as mod art. Source
 * images are never runtime dependencies: the project's existing pattern/palette snapshot owns
 * the installed bytes. All temporary converter output is removed before returning.
 */
public final class EditorArtImport {
    private EditorArtImport() { }

    /**
     * Prepares, but does not apply, one history command. Relative input paths resolve against
     * {@code projectRoot}; absolute paths must remain inside it. No existing source/output is
     * overwritten. Reserved slots include any host animation banks or editor-owned references
     * which are not represented by the level's chunk descriptors.
     */
    public static PreparedImport prepare(Path projectRoot, Path png, Path sheet, MutableLevel level,
            BitSet reservedPatternSlots, boolean importPalette,
            BiPredicate<Integer, Integer> editablePaletteCell, IntConsumer refreshPalette) throws IOException {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(reservedPatternSlots, "reservedPatternSlots");
        Objects.requireNonNull(editablePaletteCell, "editablePaletteCell");
        Objects.requireNonNull(refreshPalette, "refreshPalette");
        Path root = Objects.requireNonNull(projectRoot, "projectRoot").toRealPath();
        if (!Files.isDirectory(root)) throw new IOException("Art project root must be a directory");
        Path image = containedInput(root, png);
        Path manifest = containedInput(root, sheet);
        ModInputLimits limits = ModInputLimits.production();
        Path temporary = Files.createTempDirectory("openggf-editor-art-");
        try {
            // Freeze just the two selected inputs, rather than snapshotting unrelated project
            // files or letting the converter follow later changes to its source paths.
            Path imageCopy = temporary.resolve("input.png");
            Path sheetCopy = temporary.resolve("sheet.yaml");
            Files.write(imageCopy, readBounded(image, limits.maxAssetBytes(), "PNG"));
            Files.write(sheetCopy, readBounded(manifest, limits.maxMetadataBytes(), "Art sheet"));
            Path output = temporary.resolve("converted");
            Files.createDirectory(output);
            ArtConverter.Result converted = new ArtConverter().convert(imageCopy, sheetCopy,
                    output.resolve("sheet.ggfs"));
            BakedSheetReader.BakedSheet decoded;
            try (ModAssetRoot assets = ModAssetRoot.directory(temporary, output, limits, DirectoryAccess.DEVELOPMENT)) {
                byte[] baked = ResourceLoader.forModAssetsOnly().loadSingle(LoadOp.modAssetBase(assets, "sheet.ggfs"));
                decoded = BakedSheetReader.read(baked, limits);
            }
            if (decoded.patterns().length == 0) throw new IllegalArgumentException("Art sheet contains no 8x8 tiles");
            var command = new InstallImportedArtCommand(level, decoded, reservedPatternSlots,
                    importPalette, editablePaletteCell, refreshPalette);
            return new PreparedImport(command, command.patternIndices(), converted.frameCount(),
                    decoded.palette().map(BakedSheetReader.Palette::line).orElse(-1), importPalette);
        } finally {
            // This directory is exclusively owned by this invocation, never project content.
            try (var files = Files.walk(temporary)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static Path containedInput(Path root, Path selected) throws IOException {
        Objects.requireNonNull(selected, "art input path");
        Path candidate = (selected.isAbsolute() ? selected : root.resolve(selected)).toAbsolutePath().normalize();
        if (!candidate.startsWith(root) || candidate.equals(root))
            throw new IOException("Art input must be inside the selected project folder: " + selected);
        Path relative = root.relativize(candidate);
        Path current = root;
        for (Path component : relative) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) throw new IOException("Art input cannot contain symbolic links: " + selected);
        }
        if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Art input must be a regular file: " + selected);
        if (!candidate.toRealPath().startsWith(root)) throw new IOException("Art input escapes project folder");
        return candidate;
    }

    private static byte[] readBounded(Path path, long maximum, String label) throws IOException {
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(Math.toIntExact(Math.min(Integer.MAX_VALUE, maximum + 1)));
            if (bytes.length > maximum) throw new IOException(label + " exceeds byte limit " + maximum);
            return bytes;
        }
    }

    public record PreparedImport(InstallImportedArtCommand command, List<Integer> patternIndices,
                                 int frameCount, int paletteLine, boolean importsPalette) {
        public PreparedImport { patternIndices = List.copyOf(patternIndices); }
    }
}
