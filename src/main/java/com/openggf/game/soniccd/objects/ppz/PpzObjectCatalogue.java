package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.art.CdObjectTables;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Palmtree Panic objects' catalogue ({@code object-catalogue-ppz.tsv}, anchors in
 * {@code tools/soniccd/object_catalogue_ppz.py}); -1 where the stage has no such table.
 */
public final class PpzObjectCatalogue {
    private static final CdObjectTables TABLES = CdObjectTables.load("ppz");
    private static final Map<CdStagePayload, Entry> ENTRIES = load();

    public record Entry(CdStagePayload payload,
                        int plantMappings, int rampMappings, int rampAnimations, int sceneryMappings,
                        int logShadowMappings, int waterfallMappings, int waterfallAnimations,
                        int mosquiMappings1, int mosquiMappings2, int mosquiAnimations1, int mosquiAnimations2,
                        int antonMappings, int antonAnimations, int pataBataMappings1, int pataBataMappings2,
                        int pataBataAnimations, int tamabbohMappings1, int tamabbohMappings2,
                        int tamabbohAnimations1, int tamabbohAnimations2, int tamabbohMissileMappings,
                        int tamabbohMissileAnimations, int tagaTagaMappings1, int tagaTagaMappings2,
                        int tagaTagaAnimations1, int tagaTagaAnimations2, int sectionRanges,
                        int collapseMappings1, int collapseSizes1, int collapseMappings2, int collapseSizes2,
                        int collapseMappings3, int collapseMappings4, int spinningDiscMappings,
                        int spinningDiscAnimations, int springBoardMappings, int springBoardAnimations,
                        int sonicHoleMappings, int breakableWallMappings, int breakableWallFrames,
                        int breakableWallDeltas, int breakableWallSpeeds,
                        int platformMappings, int platformWidths, int platformRanges, int tunnelPathPaths,
                        int tunnelPathSpeeds, int swingMappings, int swingMotion) { }

    private PpzObjectCatalogue() { }

    public static Entry entry(CdStagePayload payload) {
        return Objects.requireNonNull(ENTRIES.get(Objects.requireNonNull(payload, "payload")));
    }

    private static Map<CdStagePayload, Entry> load() {
        Map<CdStagePayload, Entry> out = new EnumMap<>(CdStagePayload.class);
        for (CdStagePayload p : CdStagePayload.values()) {
            out.put(p, new Entry(p, o(p, "plantMappings"), o(p, "rampMappings"), o(p, "rampAnimations"),
                    o(p, "sceneryMappings"), o(p, "logShadowMappings"), o(p, "waterfallMappings"),
                    o(p, "waterfallAnimations"), o(p, "mosquiMappings1"), o(p, "mosquiMappings2"),
                    o(p, "mosquiAnimations1"), o(p, "mosquiAnimations2"), o(p, "antonMappings"),
                    o(p, "antonAnimations"), o(p, "pataBataMappings1"), o(p, "pataBataMappings2"),
                    o(p, "pataBataAnimations"), o(p, "tamabbohMappings1"), o(p, "tamabbohMappings2"),
                    o(p, "tamabbohAnimations1"), o(p, "tamabbohAnimations2"), o(p, "tamabbohMissileMappings"),
                    o(p, "tamabbohMissileAnimations"), o(p, "tagaTagaMappings1"), o(p, "tagaTagaMappings2"),
                    o(p, "tagaTagaAnimations1"), o(p, "tagaTagaAnimations2"), o(p, "sectionRanges"),
                    o(p, "collapseMappings1"), o(p, "collapseSizes1"), o(p, "collapseMappings2"),
                    o(p, "collapseSizes2"), o(p, "collapseMappings3"), o(p, "collapseMappings4"),
                    o(p, "spinningDiscMappings"), o(p, "spinningDiscAnimations"), o(p, "springBoardMappings"),
                    o(p, "springBoardAnimations"), o(p, "sonicHoleMappings"), o(p, "breakableWallMappings"), o(p, "breakableWallFrames"),
                    o(p, "breakableWallDeltas"), o(p, "breakableWallSpeeds"),
                    o(p, "platformMappings"), o(p, "platformWidths"), o(p, "platformRanges"), o(p, "tunnelPathPaths"),
                    o(p, "tunnelPathSpeeds"), o(p, "swingMappings"), o(p, "swingMotion")));
        }
        return Map.copyOf(out);
    }

    private static int o(CdStagePayload payload, String column) {
        return TABLES.offset(payload, column);
    }
}
