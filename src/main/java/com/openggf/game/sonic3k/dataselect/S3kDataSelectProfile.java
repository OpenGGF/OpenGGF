package com.openggf.game.sonic3k.dataselect;

import com.openggf.game.ZoneKey;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.dataselect.DataSelectDestination;
import com.openggf.game.dataselect.DataSelectGameProfile;
import com.openggf.game.save.SaveSlotSummary;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.sonic3k.Sonic3kZoneRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * S3K-specific data select game profile.
 * Provides 8 save slots, 3 built-in team selections (Sonic, Sonic+Tails, Knuckles),
 * and custom team parsing from semicolon-separated strings.
 */
public final class S3kDataSelectProfile implements DataSelectGameProfile {
    private static final int STOCK_ZONE_COUNT = 22;
    private final Supplier<ZoneRegistry> zones;

    public S3kDataSelectProfile() {
        this(Sonic3kZoneRegistry::new);
    }

    public S3kDataSelectProfile(Supplier<ZoneRegistry> zones) {
        this.zones = Objects.requireNonNull(zones, "zones");
    }

    @Override
    public String gameCode() {
        return "s3k";
    }

    @Override
    public int slotCount() {
        return 8;
    }

    @Override
    public List<SelectedTeam> builtInTeams() {
        return List.of(
                new SelectedTeam("sonic", List.of("tails")),
                new SelectedTeam("sonic", List.of()),
                new SelectedTeam("tails", List.of()),
                new SelectedTeam("knuckles", List.of())
        );
    }

    @Override
    public SaveSlotSummary summarizeFreshSlot(int slot) {
        return SaveSlotSummary.empty(slot);
    }

    @Override
    public List<DataSelectDestination> clearRestartDestinations(Map<String, Object> payload) {
        return S3kSaveProgressions.clearRestartDestinations(payload);
    }

    @Override
    public int clearRestartSelectionCount(Map<String, Object> payload) {
        return S3kSaveProgressions.clearRestartSelectionCount(payload);
    }

    @Override
    public int defaultClearRestartIndex(Map<String, Object> payload) {
        return Math.max(0, clearRestartSelectionCount(payload) - 1);
    }

    /**
     * Parses extra team combinations from a semicolon-separated string.
     * Each team is a comma-separated list where the first element is the main character
     * and the rest are sidekicks.
     * <p>
     * Example: {@code "sonic,knuckles;knuckles,tails"} produces two teams:
     * Sonic+Knuckles and Knuckles+Tails.
     *
     * @param raw the raw team string, or null/blank for no extra teams
     * @return the parsed list of extra teams
     */
    public List<SelectedTeam> parseExtraTeams(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<SelectedTeam> result = new ArrayList<>();
        for (String teamRaw : raw.split(";")) {
            List<String> parts = Arrays.stream(teamRaw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            if (!parts.isEmpty()) {
                result.add(new SelectedTeam(parts.get(0), parts.subList(1, parts.size())));
            }
        }
        return result;
    }

    @Override
    public boolean isPayloadValid(Map<String, Object> payload) {
        if (payload == null) {
            return false;
        }
        try {
            S3kSavedZone.read(payload);
            Map<String, Object> normalized = payload;
            if (!(payload.get("zone") instanceof Number)) {
                normalized = new java.util.LinkedHashMap<>(payload);
                normalized.put("zone", Sonic3kZoneIds.ZONE_AIZ);
            }
            return com.openggf.game.sonic1.dataselect.DataSelectPayloadValidators
                    .validateCommonPayload(normalized, 21, 7);
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    @Override
    public DataSelectDestination resolveLoadDestination(Map<String, Object> payload) {
        int act = payload != null && payload.get("act") instanceof Number number
                ? Math.max(0, number.intValue()) : 0;
        S3kSavedZone saved;
        try {
            saved = S3kSavedZone.read(payload);
        } catch (RuntimeException invalid) {
            return new DataSelectDestination(Sonic3kZoneIds.ZONE_AIZ, 0);
        }
        if (saved.zoneKey() instanceof ZoneKey.Stock stock) {
            return stock.zoneIndex() >= 0 && stock.zoneIndex() < STOCK_ZONE_COUNT
                    ? new DataSelectDestination(stock.zoneIndex(), act)
                    : new DataSelectDestination(Sonic3kZoneIds.ZONE_AIZ, 0);
        }
        return zones.get().resolveZoneKey(saved.zoneKey()).stream()
                .mapToObj(zone -> new DataSelectDestination(zone, act))
                .findFirst()
                .orElseGet(() -> new DataSelectDestination(Sonic3kZoneIds.ZONE_AIZ, 0));
    }
}
