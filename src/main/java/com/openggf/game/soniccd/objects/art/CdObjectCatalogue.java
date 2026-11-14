package com.openggf.game.soniccd.objects.art;

import com.openggf.game.soniccd.CdStagePayload;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * The common objects' catalogue ({@code object-catalogue-common.tsv}, anchors in
 * {@code tools/soniccd/object_catalogue_common.py}). The level-start set has named accessors;
 * the later common columns are read by name with {@link Entry#offset(String)}.
 */
public final class CdObjectCatalogue {
    private static final CdObjectTables TABLES = CdObjectTables.load("common");
    private static final Map<CdStagePayload, Entry> ENTRIES = load();

    /** Offsets into one stage file; {@code explosionTile} is the explosion's base tile word. */
    public record Entry(CdStagePayload payload, int ringMappings, int ringAnimations, int monitorMappings,
                        int monitorAnimations, int springMappings1, int springMappings2, int springMappings3,
                        int springAnimations, int spikesMappings, int explosionMappings, int explosionAnimations,
                        int pointsMappings, int hudMappings, int hudNumbersArt, int lifeIconArt,
                        int titleCardMappings, int titleCardData, int objectTileIds, int ringDeltas,
                        int explosionTile) {
        /** Any common column by name ({@code -1} when the stage has no such routine). */
        public int offset(String column) {
            return TABLES.offset(payload, column);
        }
        public boolean hasSpikes() { return spikesMappings >= 0; }
        public boolean hasObjectTileIds() { return objectTileIds >= 0; }
    }

    private CdObjectCatalogue() { }

    public static Entry entry(CdStagePayload payload) {
        return Objects.requireNonNull(ENTRIES.get(Objects.requireNonNull(payload, "payload")),
                () -> "No object catalogue entry for " + payload);
    }

    private static Map<CdStagePayload, Entry> load() {
        Map<CdStagePayload, Entry> entries = new EnumMap<>(CdStagePayload.class);
        for (CdStagePayload p : CdStagePayload.values()) {
            entries.put(p, new Entry(p, o(p, "ringMappings"), o(p, "ringAnimations"), o(p, "monitorMappings"),
                    o(p, "monitorAnimations"), o(p, "springMappings1"), o(p, "springMappings2"),
                    o(p, "springMappings3"), o(p, "springAnimations"), o(p, "spikesMappings"),
                    o(p, "explosionMappings"), o(p, "explosionAnimations"), o(p, "pointsMappings"),
                    o(p, "hudMappings"), o(p, "hudNumbersArt"), o(p, "lifeIconArt"), o(p, "titleCardMappings"),
                    o(p, "titleCardData"), o(p, "objectTileIds"), o(p, "ringDeltas"), o(p, "explosionTile")));
        }
        return Map.copyOf(entries);
    }

    private static int o(CdStagePayload payload, String column) {
        return TABLES.offset(payload, column);
    }
}
