package com.openggf.game.sonic2.dataselect;

import com.openggf.game.dataselect.DataSelectDestination;
import com.openggf.game.dataselect.DataSelectGameProfile;
import com.openggf.game.dataselect.HostSlotPreview;
import com.openggf.game.save.SaveSlotSummary;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.openggf.game.ZoneKey;
import com.openggf.game.ZoneRegistry;

public final class S2DataSelectProfile implements DataSelectGameProfile {
    private static final int STOCK_ZONE_COUNT = 11;
    private final Supplier<ZoneRegistry> zones;
    private final Consumer<S2SaveFinding> findings;

    public S2DataSelectProfile() {
        this(com.openggf.game.sonic2.Sonic2ZoneRegistry::new);
    }

    public S2DataSelectProfile(Supplier<ZoneRegistry> zones) {
        this(zones, finding -> java.util.logging.Logger.getLogger(S2DataSelectProfile.class.getName())
                .warning(finding.code() + ": " + finding.detail()));
    }

    public S2DataSelectProfile(Supplier<ZoneRegistry> zones, Consumer<S2SaveFinding> findings) {
        this.zones = Objects.requireNonNull(zones, "zones");
        this.findings = Objects.requireNonNull(findings, "findings");
    }
    private static final List<DataSelectDestination> CLEAR_RESTARTS = List.of(
            new DataSelectDestination(Sonic2ZoneConstants.ZONE_EHZ, 0),
            new DataSelectDestination(Sonic2ZoneConstants.ZONE_CPZ, 0),
            new DataSelectDestination(Sonic2ZoneConstants.ZONE_ARZ, 0),
            new DataSelectDestination(Sonic2ZoneConstants.ZONE_CNZ, 0),
            new DataSelectDestination(Sonic2ZoneConstants.ZONE_HTZ, 0),
            new DataSelectDestination(Sonic2ZoneConstants.ZONE_MCZ, 0),
            new DataSelectDestination(Sonic2ZoneConstants.ZONE_OOZ, 0),
            new DataSelectDestination(Sonic2ZoneConstants.ZONE_MTZ, 0),
            new DataSelectDestination(Sonic2ZoneConstants.ZONE_SCZ, 0),
            new DataSelectDestination(Sonic2ZoneConstants.ZONE_WFZ, 0),
            new DataSelectDestination(Sonic2ZoneConstants.ZONE_DEZ, 0)
    );

    @Override public String gameCode() { return "s2"; }
    @Override public int slotCount() { return 8; }
    @Override public List<SelectedTeam> builtInTeams() {
        return List.of(
                new SelectedTeam("sonic", List.of()),
                new SelectedTeam("sonic", List.of("tails")),
                new SelectedTeam("knuckles", List.of())
        );
    }
    @Override public SaveSlotSummary summarizeFreshSlot(int slot) { return SaveSlotSummary.empty(slot); }

    @Override
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
    public List<DataSelectDestination> clearRestartDestinations(Map<String, Object> payload) {
        return Boolean.TRUE.equals(payload.get("clear")) ? CLEAR_RESTARTS : List.of();
    }

    @Override
    public boolean isPayloadValid(Map<String, Object> payload) {
        if (payload == null) return false;
        try {
            S2SavedZone saved = S2SavedZone.read(payload);
            Map<String, Object> normalized = payload;
            if (!(payload.get("zone") instanceof Number)) {
                normalized = new java.util.LinkedHashMap<>(payload);
                normalized.put("zone", 0);
            }
            return com.openggf.game.sonic1.dataselect.DataSelectPayloadValidators
                    .validateCommonPayload(normalized, Integer.MAX_VALUE, 7);
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    @Override
    public HostSlotPreview resolveSlotPreview(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        S2SavedZone saved;
        try { saved = S2SavedZone.read(payload); } catch (RuntimeException invalid) { return null; }
        if (saved.zoneKey() instanceof ZoneKey.Mod) return HostSlotPreview.textOnly("MOD");
        int zoneId = ((ZoneKey.Stock) saved.zoneKey()).zoneIndex();
        return zoneId >= 0 && zoneId < CLEAR_RESTARTS.size()
                ? HostSlotPreview.numberedZone(zoneId + 1)
                : null;
    }

    @Override
    public int resolveSelectedSlotIconIndex(Map<String, Object> payload, DataSelectDestination clearDestination) {
        if (clearDestination != null) {
            return zoneToIconIndex(clearDestination.zone());
        }
        if (payload == null) {
            return -1;
        }
        try {
            ZoneKey key = S2SavedZone.read(payload).zoneKey();
            return key instanceof ZoneKey.Stock stock ? zoneToIconIndex(stock.zoneIndex()) : -1;
        } catch (RuntimeException invalid) {
            return -1;
        }
    }

    @Override
    public DataSelectDestination resolveLoadDestination(Map<String, Object> payload) {
        int act = payload != null && payload.get("act") instanceof Number number
                ? Math.max(0, number.intValue()) : 0;
        S2SavedZone saved;
        try {
            saved = S2SavedZone.read(payload);
        } catch (RuntimeException invalid) {
            findings.accept(new S2SaveFinding("S2_SAVED_ZONE_INVALID", invalid.getMessage()));
            return new DataSelectDestination(0, 0);
        }
        if (saved.zoneKey() instanceof ZoneKey.Stock stock) {
            if (stock.zoneIndex() < STOCK_ZONE_COUNT) {
                return new DataSelectDestination(stock.zoneIndex(), act);
            }
            findings.accept(new S2SaveFinding("S2_LEGACY_ZONE_OUT_OF_RANGE",
                    "Legacy numeric zone " + stock.zoneIndex() + " cannot identify a mod zone"));
            return new DataSelectDestination(0, 0);
        }
        ZoneKey.Mod mod = (ZoneKey.Mod) saved.zoneKey();
        java.util.OptionalInt resolved = zones.get().resolveZoneKey(mod);
        if (resolved.isPresent()) return new DataSelectDestination(resolved.getAsInt(), act);
        findings.accept(new S2SaveFinding(mod.ownerModId(), "S2_MOD_ZONE_MISSING",
                "Saved mod zone is unavailable: " + mod.ownerModId() + ":" + mod.localName()));
        return new DataSelectDestination(0, 0);
    }

    private static int zoneToIconIndex(int zoneId) {
        return zoneId >= 0 && zoneId < CLEAR_RESTARTS.size() ? zoneId : -1;
    }
}
