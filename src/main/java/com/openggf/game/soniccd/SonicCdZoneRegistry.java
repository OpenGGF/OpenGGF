package com.openggf.game.soniccd;

import com.openggf.game.AbstractZoneRegistry;
import com.openggf.level.LevelDescriptor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Projects the 70 stage payloads onto shared zone/act identity.
 *
 * <p>Zone index is the {@link CdZone} ordinal (seven rounds; the native round numbers keep
 * their R2 gap). Act index enumerates that round's payloads in (act, period) order:
 * 0-3 act 1 present/past/good/bad future, 4-7 act 2 likewise, 8-9 act 3 good/bad future.
 * The level index is the payload ordinal. Shared callers already address a level by
 * zone and act, so the time period rides in the act index without a new hook.
 */
public final class SonicCdZoneRegistry extends AbstractZoneRegistry {
    private static final String[] ZONE_NAMES = {
            "PALMTREE PANIC", "COLLISION CHAOS", "TIDAL TEMPEST", "QUARTZ QUADRANT",
            "WACKY WORKBENCH", "STARDUST SPEEDWAY", "METALLIC MADNESS"
    };

    /** A stage payload as a shared level descriptor; the start position is read from the disc. */
    public record StageDescriptor(CdStagePayload payload) implements LevelDescriptor {
        @Override public int levelIndex() { return payload.ordinal(); }
        @Override public int startX() { return 0; }
        @Override public int startY() { return 0; }
    }

    public SonicCdZoneRegistry() {
        super(zones(), ZONE_NAMES);
    }

    private static List<List<LevelDescriptor>> zones() {
        List<List<LevelDescriptor>> zones = new ArrayList<>();
        for (CdZone zone : CdZone.values()) {
            List<LevelDescriptor> acts = new ArrayList<>();
            CdStagePayloadOrder.inZone(zone).forEach(p -> acts.add(new StageDescriptor(p)));
            zones.add(List.copyOf(acts));
        }
        return List.copyOf(zones);
    }

    public static CdStagePayload payload(int zoneIndex, int actIndex) {
        return CdStagePayloadOrder.inZone(CdZone.values()[zoneIndex]).get(actIndex);
    }

    public static int zoneIndex(CdStagePayload payload) {
        return payload.stage().zone().ordinal();
    }

    public static int actIndex(CdStagePayload payload) {
        return CdStagePayloadOrder.inZone(payload.stage().zone()).indexOf(payload);
    }

    @Override
    public int[] getStartPosition(int zoneIndex, int actIndex) {
        // Disc-backed: SonicCdGame provides the LevelStartLoc words.
        return new int[] {0, 0};
    }

    @Override
    public int getMusicId(int zoneIndex, int actIndex) {
        return -1; // CD music is a disc audio track or PCM program; not hosted yet.
    }

    /** Payload ordering within a round. */
    static final class CdStagePayloadOrder {
        private CdStagePayloadOrder() { }

        static List<CdStagePayload> inZone(CdZone zone) {
            return java.util.Arrays.stream(CdStagePayload.values())
                    .filter(p -> p.stage().zone() == zone)
                    .sorted(Comparator.<CdStagePayload>comparingInt(p -> p.stage().actNumber())
                            .thenComparing(p -> p.stage().period()))
                    .toList();
        }
    }
}
