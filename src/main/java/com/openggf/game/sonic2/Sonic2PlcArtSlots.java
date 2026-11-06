package com.openggf.game.sonic2;

import com.openggf.data.Rom;
import com.openggf.data.RomFingerprint;
import com.openggf.game.rewind.snapshot.PlcProgressSnapshot.ArtSlotIdentity;
import com.openggf.game.rewind.snapshot.PlcProgressSnapshot.ArtSlotRange;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.util.PatternDecompressor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ordered logical tile ownership for explicitly bound ROM-static S2 object sheets.
 * This is resource reuse: PLC recipes supply art; mappings and renderer allocations stay put.
 * Composite, animated and externally mutated sheets do not participate in this owner.
 */
final class Sonic2PlcArtSlots {
    private record SourcePattern(int address, int index) { }
    private record Binding(int base, ObjectSpriteSheet sheet, PatternSpriteRenderer renderer,
                           Pattern[] baseline) { }

    private final Rom rom;
    private final String romSha1;
    private final Map<Integer, Pattern[]> sources = new HashMap<>();
    private final List<Binding> bindings = new ArrayList<>();
    private final TreeMap<Integer, SourcePattern> image = new TreeMap<>();
    private ArtSlotIdentity identity;
    private List<ArtSlotRange> capturedImage = List.of();

    Sonic2PlcArtSlots(Rom rom) throws IOException {
        this.rom = rom;
        romSha1 = RomFingerprint.of(rom.readAllBytes()).sha1Hex();
    }

    void beginZone(int zone) {
        identity = new ArtSlotIdentity(romSha1, zone);
        bindings.clear();
        image.clear();
        capturedImage = List.of();
    }

    ArtSlotIdentity identity() { return identity; }

    void bind(int base, ObjectSpriteSheet sheet, PatternSpriteRenderer renderer) {
        if (sheet != null && renderer != null) {
            bindings.add(new Binding(base, sheet, renderer, copyPatterns(sheet.getPatterns())));
        }
    }

    /** A private mutable view keeps the art loader's cached ROM recipes pristine across loads. */
    static ObjectSpriteSheet detached(ObjectSpriteSheet source) {
        if (source == null) return null;
        var frames = new ArrayList<com.openggf.level.render.SpriteMappingFrame>();
        for (int i = 0; i < source.getFrameCount(); i++) frames.add(source.getFrame(i));
        return new ObjectSpriteSheet(copyPatterns(source.getPatterns()), List.copyOf(frames),
                source.getPaletteIndex(), source.getFrameDelay());
    }

    private static Pattern[] copyPatterns(Pattern[] source) {
        Pattern[] result = new Pattern[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = new Pattern();
            result[i].copyFrom(source[i]);
        }
        return result;
    }

    List<ArtSlotRange> prepare(int source, int destination) throws IOException {
        Pattern[] patterns = source(source);
        List<ArtSlotRange> ranges = new ArrayList<>();
        for (int i = 0; i < patterns.length;) {
            if (!bound(destination + i)) { i++; continue; }
            int first = i++;
            while (i < patterns.length && bound(destination + i)) i++;
            ranges.add(new ArtSlotRange(destination + first, source, first, i - first));
        }
        return List.copyOf(ranges);
    }

    void validate(ArtSlotIdentity provenance, List<ArtSlotRange> ranges) throws IOException {
        if (provenance != null && !identity.equals(provenance)) {
            throw new IllegalArgumentException("Art-slot ROM/world differs from the loaded target");
        }
        if (provenance == null && !ranges.isEmpty()) {
            throw new IllegalArgumentException("Art-slot recipes have no ROM/world provenance");
        }
        for (ArtSlotRange range : ranges) {
            Pattern[] patterns = source(range.sourceAddress());
            if ((long) range.sourcePatternOffset() + range.patternCount() > patterns.length) {
                throw new IllegalArgumentException("Art-slot recipe exceeds its ROM source");
            }
            for (int i = 0; i < range.patternCount(); i++) {
                if (!bound(range.destinationTile() + i)) {
                    throw new IllegalArgumentException("Art-slot recipe has no static sheet binding");
                }
            }
        }
    }

    boolean publish(List<ArtSlotRange> writes, GraphicsManager graphics) {
        for (ArtSlotRange range : writes) put(image, range);
        if (!writes.isEmpty()) capturedImage = null;
        return reconcile(graphics);
    }

    List<ArtSlotRange> capture() {
        if (capturedImage != null) return capturedImage;
        List<ArtSlotRange> ranges = new ArrayList<>();
        for (var entry : image.entrySet()) {
            SourcePattern source = entry.getValue();
            if (!ranges.isEmpty()) {
                ArtSlotRange previous = ranges.getLast();
                if (previous.destinationTile() + previous.patternCount() == entry.getKey()
                        && previous.sourceAddress() == source.address()
                        && previous.sourcePatternOffset() + previous.patternCount() == source.index()) {
                    ranges.set(ranges.size() - 1, new ArtSlotRange(previous.destinationTile(),
                            previous.sourceAddress(), previous.sourcePatternOffset(), previous.patternCount() + 1));
                    continue;
                }
            }
            ranges.add(new ArtSlotRange(entry.getKey(), source.address(), source.index(), 1));
        }
        return capturedImage = List.copyOf(ranges);
    }

    void restore(ArtSlotIdentity provenance, List<ArtSlotRange> ranges, GraphicsManager graphics)
            throws IOException {
        // Validate provenance, ROM sources and every destination before changing bytes or pixels.
        validate(provenance, ranges);
        TreeMap<Integer, SourcePattern> restored = new TreeMap<>();
        for (ArtSlotRange range : ranges) put(restored, range);
        image.clear();
        image.putAll(restored);
        capturedImage = List.copyOf(ranges);
        reconcile(graphics);
    }

    private static void put(Map<Integer, SourcePattern> target, ArtSlotRange range) {
        for (int i = 0; i < range.patternCount(); i++) {
            target.put(range.destinationTile() + i,
                    new SourcePattern(range.sourceAddress(), range.sourcePatternOffset() + i));
        }
    }

    private Pattern[] source(int address) throws IOException {
        Pattern[] patterns = sources.get(address);
        if (patterns == null) {
            patterns = PatternDecompressor.nemesis(rom, address);
            sources.put(address, patterns);
        }
        return patterns;
    }

    private boolean bound(int tile) {
        return bindings.stream().anyMatch(binding -> tile >= binding.base()
                && tile < binding.base() + binding.baseline().length);
    }

    private boolean reconcile(GraphicsManager graphics) {
        boolean changed = false;
        for (Binding binding : bindings) {
            Pattern[] target = binding.sheet().getPatterns();
            int first = target.length, last = -1;
            for (int i = 0; i < target.length; i++) {
                SourcePattern owner = image.get(binding.base() + i);
                Pattern expected = owner == null ? binding.baseline()[i] : sources.get(owner.address())[owner.index()];
                if (!samePixels(target[i], expected)) {
                    target[i].copyFrom(expected);
                    first = Math.min(first, i);
                    last = i;
                }
            }
            if (last >= first) {
                // Existing ranges also clear the renderer's pixel-bounds cache, without reallocating it.
                binding.renderer().updatePatternRange(graphics, first, last - first + 1);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean samePixels(Pattern first, Pattern second) {
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            if (first.getPixel(x, y) != second.getPixel(x, y)) return false;
        }
        return true;
    }
}
