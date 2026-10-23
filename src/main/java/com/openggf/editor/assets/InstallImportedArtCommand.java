package com.openggf.editor.assets;

import com.openggf.editor.EditorCommand;
import com.openggf.level.Level;
import com.openggf.level.MutableLevel;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.objects.BakedSheetReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.IntConsumer;

/** Installs imported tiles and an optional host-safe palette as one atomic editor undo step. */
public final class InstallImportedArtCommand implements EditorCommand {
    private final MutableLevel level;
    private final List<Integer> indices;
    private final Pattern[] before;
    private final Pattern[] after;
    private final int paletteLine;
    private final Palette beforePalette;
    private final Palette afterPalette;
    private final IntConsumer refreshPalette;

    InstallImportedArtCommand(MutableLevel level, BakedSheetReader.BakedSheet sheet, BitSet reservedSlots,
            boolean importPalette, BiPredicate<Integer, Integer> editableCell, IntConsumer refreshPalette) {
        this.level = Objects.requireNonNull(level);
        this.refreshPalette = Objects.requireNonNull(refreshPalette);
        BitSet unavailable = referencedPatterns(level);
        unavailable.or(reservedSlots);
        ArrayList<Integer> free = new ArrayList<>();
        for (int slot = 1; slot < Math.min(level.getPatternCount(), 0x800) && free.size() < sheet.patterns().length; slot++) {
            // Even an unreferenced stock tile is library content. Claim only empty slots.
            if (!unavailable.get(slot) && isBlank(level.getPattern(slot))) free.add(slot);
        }
        if (free.size() != sheet.patterns().length)
            throw new IllegalArgumentException("Insufficient unused blank 8x8 tile slots: need "
                    + sheet.patterns().length + ", available " + free.size());
        indices = List.copyOf(free);
        before = new Pattern[indices.size()];
        after = new Pattern[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            before[i] = copy(level.getPattern(indices.get(i)));
            after[i] = copy(sheet.patterns()[i]);
        }
        if (importPalette) {
            BakedSheetReader.Palette palette = sheet.palette().orElseThrow(
                    () -> new IllegalArgumentException("Art sheet has no palette to import"));
            paletteLine = palette.line();
            if (paletteLine >= level.getPaletteCount() || level.getPalette(paletteLine) == null)
                throw new IllegalArgumentException("Art palette line is unavailable in this level");
            beforePalette = level.getPalette(paletteLine).deepCopy();
            afterPalette = new Palette();
            for (int color = 0; color < 16; color++) {
                var rgb = new Palette.Color();
                rgb.fromSegaFormat(palette.colors()[color]);
                afterPalette.setColor(color, rgb);
                if (!editableCell.test(paletteLine, color) && !sameColor(beforePalette.getColor(color), rgb))
                    throw new IllegalArgumentException("Imported palette changes a host-owned color: line "
                            + paletteLine + ", color " + color);
            }
        } else {
            paletteLine = -1;
            beforePalette = afterPalette = null;
        }
    }

    public List<Integer> patternIndices() { return indices; }

    @Override public void apply() { replace(before, after, beforePalette, afterPalette); }
    @Override public void undo() { replace(after, before, afterPalette, beforePalette); }

    private void replace(Pattern[] expected, Pattern[] replacement, Palette expectedPalette, Palette replacementPalette) {
        for (int i = 0; i < indices.size(); i++) {
            if (!samePattern(level.getPattern(indices.get(i)), expected[i]))
                throw new IllegalStateException("Imported tile slot changed since this command was prepared: " + indices.get(i));
        }
        if (expectedPalette != null && !expectedPalette.dataEquals(level.getPalette(paletteLine)))
            throw new IllegalStateException("Import palette line changed since this command was prepared");
        // Both initial apply and redo must still have their allocation available. During undo,
        // later terrain edits should already have been undone by the ordinary history stack.
        BitSet references = referencedPatterns(level);
        for (int slot : indices) if (references.get(slot))
            throw new IllegalStateException("Imported tile slot is referenced by terrain: " + slot);
        install(replacement, replacementPalette);
        try {
            if (replacementPalette != null) refreshPalette.accept(paletteLine);
        } catch (RuntimeException failure) {
            install(expected, expectedPalette);
            try { if (expectedPalette != null) refreshPalette.accept(paletteLine); }
            catch (RuntimeException restoreFailure) { if (restoreFailure != failure) failure.addSuppressed(restoreFailure); }
            throw failure;
        }
    }

    private void install(Pattern[] patterns, Palette palette) {
        for (int i = 0; i < indices.size(); i++) level.setPattern(indices.get(i), copy(patterns[i]));
        if (palette != null) level.setPalette(paletteLine, palette.deepCopy());
    }

    private static BitSet referencedPatterns(Level level) {
        BitSet references = new BitSet();
        for (int chunk = 0; chunk < level.getChunkCount(); chunk++) {
            for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++)
                references.set(level.getChunk(chunk).getPatternDesc(x, y).getPatternIndex());
        }
        return references;
    }

    private static boolean isBlank(Pattern pattern) {
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) if (pattern.getPixel(x, y) != 0) return false;
        return true;
    }

    private static Pattern copy(Pattern source) { Pattern result = new Pattern(); result.copyFrom(source); return result; }
    private static boolean sameColor(Palette.Color a, Palette.Color b) { return a.r == b.r && a.g == b.g && a.b == b.b; }
    private static boolean samePattern(Pattern a, Pattern b) {
        byte[] left = new byte[64], right = new byte[64];
        a.copyInto(left, 0); b.copyInto(right, 0); return Arrays.equals(left, right);
    }
}
