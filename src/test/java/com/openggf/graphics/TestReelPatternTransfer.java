package com.openggf.graphics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Test-reel presentation bridge: retain already loaded ROM pixels across graphics.init().
 * No asset owner, animation, PLC or gameplay update is invoked. Origin: TAS reel, 2026-10-26.
 */
public final class TestReelPatternTransfer {
    private record Tile(int id, SpritePresentation.PatternVersion pixels) { }
    private record Alias(int id, int target, SpritePresentation.PatternVersion pixels) { }
    private final List<Tile> tiles;
    private final List<Alias> aliases;
    private final List<PatternAtlas.PatternRange> ranges;

    private TestReelPatternTransfer(List<Tile> tiles, List<Alias> aliases,
                                    List<PatternAtlas.PatternRange> ranges) {
        this.tiles = tiles;
        this.aliases = aliases;
        this.ranges = ranges;
    }

    public static TestReelPatternTransfer capture(GraphicsManager graphics) {
        var atlas = graphics.getPatternAtlas();
        if (atlas == null) return new TestReelPatternTransfer(List.of(), List.of(), List.of());
        var ranges = atlas.registeredRangesForTesting();
        int end = 0;
        for (var range : PatternAtlasRange.values()) end = Math.max(end, range.endExclusive());
        for (var range : ranges) end = Math.max(end, Math.addExact(range.base(), range.size()));
        // Include governance gaps: native tile aliases can live outside a registered range.
        var bySlot = new LinkedHashMap<Long, List<Integer>>();
        var samples = new LinkedHashMap<Integer, SpritePresentation.PatternVersion>();
        for (int id = 0; id < end; id++) {
            var entry = atlas.getEntry(id);
            if (entry == null) continue;
            long slot = ((long) entry.atlasIndex() << 32) | (entry.slot() & 0xffffffffL);
            bySlot.computeIfAbsent(slot, ignored -> new ArrayList<>()).add(id);
            var sample = graphics.scenePatternSample(id);
            if (sample != null) samples.put(id, sample);
        }
        var tiles = new ArrayList<Tile>();
        var aliases = new ArrayList<Alias>();
        for (var ids : bySlot.values()) {
            SpritePresentation.PatternVersion pixels = null;
            for (int id : ids) {
                var sample = samples.get(id);
                if (sample == null) continue;
                if (pixels != null && !pixels.equals(sample))
                    throw new IllegalStateException("Cannot reconstruct conflicting headless alias pixels: " + ids);
                pixels = sample;
            }
            int target = ids.stream().filter(samples::containsKey).findFirst().orElseGet(() ->
                    ids.stream().filter(id -> governed(id, ranges)).findFirst().orElseThrow(() ->
                            new IllegalStateException("Cached slot has no allocatable source ID: " + ids)));
            tiles.add(new Tile(target, pixels));
            for (int id : ids) if (id != target) aliases.add(new Alias(id, target, samples.get(id)));
        }
        return new TestReelPatternTransfer(tiles, aliases, ranges);
    }

    public void upload(GraphicsManager graphics) {
        var atlas = graphics.getPatternAtlas();
        for (var range : ranges) {
            var reserved = PatternAtlasRange.forPatternId(range.base());
            if (reserved != null && reserved.base() == range.base() && reserved.size() == range.size())
                atlas.registerRange(reserved);
            else atlas.registerRange(range.base(), range.size(), range.category());
        }
        graphics.beginPatternAtlasBatch();
        try {
            for (var tile : tiles) {
                if (tile.pixels() == null) graphics.cachePatternTexture(null, tile.id());
                else graphics.restoreFrozenPattern(tile.pixels(), tile.id());
            }
            for (var alias : aliases) {
                if (!atlas.aliasEntry(alias.id(), alias.target()))
                    throw new IllegalStateException("Cannot restore headless tile alias " + alias.id());
                if (alias.pixels() != null) graphics.restoreFrozenPattern(alias.pixels(), alias.id());
            }
        } finally { graphics.endPatternAtlasBatch(); }
    }

    private static boolean governed(int id, List<PatternAtlas.PatternRange> ranges) {
        if (id <= 0x7ff || PatternAtlasRange.forPatternId(id) != null) return true;
        return ranges.stream().anyMatch(r -> id >= r.base() && id < r.base() + r.size());
    }
}
