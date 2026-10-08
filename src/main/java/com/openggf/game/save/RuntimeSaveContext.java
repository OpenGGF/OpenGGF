package com.openggf.game.save;

import com.openggf.game.ZoneKey;
import com.openggf.game.session.GameplayModeContext;
import java.util.*;

/** Immutable input captured once at a save boundary. Custom payload schemas remain provider-owned. */
@com.openggf.game.ModApi
public final class RuntimeSaveContext {
    private record Progress(boolean live, int zone, int act, ZoneKey zoneKey, int lives, int continues,
                            List<Integer> chaosEmeralds, List<Integer> superEmeralds,
                            boolean emeraldsConverted, List<Integer> emeraldStates) { }
    private record SaveDetails(String gameCode, OptionalInt activeSlot, SelectedTeam team, boolean clear) {
        static SaveDetails capture(SaveSessionContext save) {
            return new SaveDetails(save.gameCode(),save.activeSlot(),save.selectedTeam(),save.isClear());
        }
    }
    private final Progress progress;
    private final String gameCode;
    private final OptionalInt activeSlot;
    private final SelectedTeam selectedTeam;
    private final boolean clear;
    private final Map<String, Object> capturedFields;

    private RuntimeSaveContext(Progress progress, SaveDetails save, Map<String, Object> fields) {
        this.progress=progress; gameCode=save.gameCode(); activeSlot=save.activeSlot();
        selectedTeam=save.team(); clear=save.clear(); capturedFields=fields;
    }

    /** Captures a fresh slot without attaching gameplay/session managers. */
    public static RuntimeSaveContext forNewGame(SaveSessionContext save) { return forGameplayMode(null,save); }

    static RuntimeSaveContext forGameplayMode(GameplayModeContext mode, SaveSessionContext save) {
        Objects.requireNonNull(save,"save");
        SaveDetails details=SaveDetails.capture(save);
        if (mode == null) {
            return new RuntimeSaveContext(new Progress(false,save.startZone(),save.startAct(),
                    ZoneKey.stock(save.startZone()),3,0,List.of(),List.of(),false,List.of(0,0,0,0,0,0,0)),details,Map.of());
        }
        var level=mode.getLevelManager(); var state=mode.getGameStateManager();
        int zone=level.getCurrentZone(), act=level.getCurrentAct();
        int lives=state.getLives(), continues=state.getContinues();
        var chaos=List.copyOf(state.getCollectedChaosEmeraldIndices());
        var supers=List.copyOf(state.getCollectedSuperEmeraldIndices());
        boolean converted=state.isEmeraldsConverted();
        var emeralds=List.copyOf(state.getS3kEmeraldStates());
        var module=level.getGameModule();
        var zones=module == null ? null : module.getZoneRegistry();
        // LevelManager advances credits to exactly zoneCount/act zero. Preserve
        // that numeric progress for provider-owned completion policies without
        // asking the registry to identify a zone which was never loaded.
        boolean creditsSentinel=zones != null && zone == zones.getZoneCount() && act == 0;
        ZoneKey key=zones == null || creditsSentinel ? ZoneKey.stock(zone) : zones.zoneKey(zone);
        Progress progress=new Progress(true,zone,act,key,lives,continues,chaos,supers,converted,emeralds);
        var provider=module == null ? null : module.getSaveSnapshotProvider();
        var runtime=mode.getZoneRuntimeRegistry();
        Map<String,Object> fields=provider == null ? Map.of() : provider.captureSaveFields(runtime == null ? null : runtime.current());
        return new RuntimeSaveContext(progress,details,RuntimeSaveFields.freeze(fields));
    }

    RuntimeSaveContext withSaveSession(SaveSessionContext save) {
        return new RuntimeSaveContext(progress,SaveDetails.capture(save),capturedFields);
    }
    public boolean hasLiveGameplayState() { return progress.live(); }
    public String gameCode() { return gameCode; }
    public OptionalInt activeSlot() { return activeSlot; }
    public SelectedTeam selectedTeam() { return selectedTeam; }
    public boolean isClear() { return clear; }
    public int currentZone() { return progress.zone(); }
    public int currentAct() { return progress.act(); }
    public ZoneKey zoneKey() { return progress.zoneKey(); }
    public int lives() { return progress.lives(); }
    public int continues() { return progress.continues(); }
    public List<Integer> chaosEmeralds() { return progress.chaosEmeralds(); }
    public List<Integer> superEmeralds() { return progress.superEmeralds(); }
    public boolean emeraldsConverted() { return progress.emeraldsConverted(); }
    public List<Integer> emeraldStates() { return progress.emeraldStates(); }
    /** Deeply frozen JSON values captured by the provider's optional input callback. */
    public Map<String,Object> capturedFields() { return capturedFields; }

}
