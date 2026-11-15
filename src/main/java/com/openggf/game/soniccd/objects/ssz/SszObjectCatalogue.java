package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.art.CdObjectTables;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stardust Speedway objects' catalogue ({@code object-catalogue-ssz.tsv}, anchors in
 * {@code tools/soniccd/object_catalogue_ssz.py}); -1 where the stage has no such table. Owned by the
 * Stardust Speedway object lane.
 */
public final class SszObjectCatalogue {
    private static final CdObjectTables TABLES = CdObjectTables.load("ssz");
    private static final Map<CdStagePayload, Entry> ENTRIES = load();

    public record Entry(CdStagePayload payload,
                        int meshMappings, int sparksMappings, int sparksAnimations, int boosterMappings,
                        int launchAnimations, int pathSwapMappings, int pathSwapAnimations, int lampMappings,
                        int searchlightMappings, int lightEdgeMappings, int lightEdgeAnimations,
                        int spikeLaunchMappings, int jumpSpikeBallMappings, int kanabunMappings1,
                        int kanabunAnimations1, int kanabunMappings2, int kanabunAnimations2,
                        int kabasiraMappings, int kabasiraAnimations, int hotaruMappings1, int hotaruAnimations1,
                        int hotaruMappings2, int hotaruAnimations2, int sectionRanges) { }

    private SszObjectCatalogue() { }

    /** The stage's offset for {@code column}, or -1 where the stage has no such table. */
    public static int offset(CdStagePayload payload, String column) {
        return TABLES.offset(payload, column);
    }

    public static Entry entry(CdStagePayload payload) {
        return Objects.requireNonNull(ENTRIES.get(Objects.requireNonNull(payload, "payload")));
    }

    private static Map<CdStagePayload, Entry> load() {
        Map<CdStagePayload, Entry> out = new EnumMap<>(CdStagePayload.class);
        for (CdStagePayload p : CdStagePayload.values()) {
            out.put(p, new Entry(p, o(p, "meshMappings"), o(p, "sparksMappings"), o(p, "sparksAnimations"),
                    o(p, "boosterMappings"), o(p, "launchAnimations"), o(p, "pathSwapMappings"),
                    o(p, "pathSwapAnimations"), o(p, "lampMappings"), o(p, "searchlightMappings"),
                    o(p, "lightEdgeMappings"), o(p, "lightEdgeAnimations"), o(p, "spikeLaunchMappings"),
                    o(p, "jumpSpikeBallMappings"), o(p, "kanabunMappings1"), o(p, "kanabunAnimations1"),
                    o(p, "kanabunMappings2"), o(p, "kanabunAnimations2"), o(p, "kabasiraMappings"),
                    o(p, "kabasiraAnimations"), o(p, "hotaruMappings1"), o(p, "hotaruAnimations1"),
                    o(p, "hotaruMappings2"), o(p, "hotaruAnimations2"), o(p, "sectionRanges")));
        }
        return Map.copyOf(out);
    }

    private static int o(CdStagePayload payload, String column) {
        return TABLES.offset(payload, column);
    }
}
