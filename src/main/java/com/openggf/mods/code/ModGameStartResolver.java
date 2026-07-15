package com.openggf.mods.code;

import com.openggf.game.ZoneKey;
import com.openggf.game.dataselect.DataSelectDestination;
import com.openggf.game.dataselect.DataSelectHostProfile;
import com.openggf.game.dataselect.HostSlotPreview;
import com.openggf.game.dataselect.ModZoneSaveFinding;
import com.openggf.game.save.SaveSlotSummary;
import com.openggf.game.save.SelectedTeam;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** Resolves the exclusive fresh-game destination from effective mod-zone order. */
final class ModGameStartResolver implements DataSelectHostProfile {
    private static final String SHADOWED = "MOD_GAME_START_SHADOWED";

    private final DataSelectHostProfile delegate;
    private final DataSelectDestination destination;
    private final Set<ZoneKey.Mod> reportedShadowed;

    private ModGameStartResolver(DataSelectHostProfile delegate,
                                 DataSelectDestination destination,
                                 Set<ZoneKey.Mod> reportedShadowed) {
        this.delegate = delegate;
        this.destination = destination;
        this.reportedShadowed = Set.copyOf(reportedShadowed);
    }

    static DataSelectHostProfile decorate(DataSelectHostProfile delegate,
                                          DataSelectHostProfile inherited,
                                          ModZoneRegistry zones,
                                          BiConsumer<String, ModZoneSaveFinding> findingSink) {
        List<PreparedModZone> starts = zones.gameStartContributions();
        if (starts.isEmpty()) return delegate;

        LinkedHashSet<ZoneKey.Mod> reported = new LinkedHashSet<>();
        if (inherited instanceof ModGameStartResolver prior) {
            reported.addAll(prior.reportedShadowed);
        }
        PreparedModZone winner = starts.getLast();
        ZoneKey.Mod winnerKey = new ZoneKey.Mod(winner.ownerModId(), winner.localKey());
        for (PreparedModZone shadowed : starts.subList(0, starts.size() - 1)) {
            ZoneKey.Mod shadowedKey = new ZoneKey.Mod(shadowed.ownerModId(), shadowed.localKey());
            if (reported.add(shadowedKey)) {
                findingSink.accept(shadowed.ownerModId(), new ModZoneSaveFinding(
                        shadowed.ownerModId(), SHADOWED,
                        keyText(shadowedKey) + " is shadowed by " + keyText(winnerKey)));
            }
        }
        int runtimeZone = zones.resolveZoneKey(winnerKey).orElseThrow();
        return new ModGameStartResolver(delegate, new DataSelectDestination(runtimeZone, 0), reported);
    }

    private static String keyText(ZoneKey.Mod key) {
        return key.ownerModId() + ":" + key.localName();
    }

    @Override public String gameCode() { return delegate.gameCode(); }
    @Override public int slotCount() { return delegate.slotCount(); }
    @Override public List<SelectedTeam> builtInTeams() { return delegate.builtInTeams(); }
    @Override public DataSelectDestination newGameDestination() { return destination; }
    @Override public List<SelectedTeam> parseExtraTeams(String raw) { return delegate.parseExtraTeams(raw); }
    @Override public SaveSlotSummary summarizeFreshSlot(int slot) { return delegate.summarizeFreshSlot(slot); }
    @Override public boolean isPayloadValid(Map<String, Object> payload) { return delegate.isPayloadValid(payload); }
    @Override public boolean isSummaryValid(SaveSlotSummary summary) { return delegate.isSummaryValid(summary); }
    @Override public List<DataSelectDestination> clearRestartDestinations(Map<String, Object> payload) {
        return delegate.clearRestartDestinations(payload);
    }
    @Override public int clearRestartSelectionCount(Map<String, Object> payload) {
        return delegate.clearRestartSelectionCount(payload);
    }
    @Override public int defaultClearRestartIndex(Map<String, Object> payload) {
        return delegate.defaultClearRestartIndex(payload);
    }
    @Override public HostSlotPreview resolveSlotPreview(Map<String, Object> payload) {
        return delegate.resolveSlotPreview(payload);
    }
    @Override public int resolveSelectedSlotIconIndex(Map<String, Object> payload,
                                                      DataSelectDestination clearDestination) {
        return delegate.resolveSelectedSlotIconIndex(payload, clearDestination);
    }
    @Override public DataSelectDestination resolveLoadDestination(Map<String, Object> payload) {
        return delegate.resolveLoadDestination(payload);
    }
}
