package com.openggf.game.sonic3k.dataselect;

import com.openggf.game.ZoneKey;
import com.openggf.game.GameStateManager;
import com.openggf.game.NativeGameStateOps;
import com.openggf.game.save.RuntimeSaveCapture;
import com.openggf.game.save.RuntimeSaveContext;
import com.openggf.game.save.SaveReason;
import com.openggf.game.save.SaveSnapshotProvider;
import com.openggf.game.sonic3k.S3kEmeraldProgression;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures S3K game state into a map suitable for save file serialization.
 * When the gameplay mode is available, reads live game state (lives, emeralds);
 * when null (e.g., fresh slot start), uses defaults (3 lives, 0 emeralds).
 */
public final class S3kSaveSnapshotProvider implements SaveSnapshotProvider {
    /** Captured at the request boundary: the request came from the ROM's full SaveGame. */
    private static final String SAVE_GAME_FIELD = "s3k:save-game";

    @Override
    public boolean restoreProgress(
            GameStateManager gameState, int lives, int continues, Map<String, Object> payload) {
        if (!hasValidSpecialStageProgress(payload)) return false;
        // S3K slot load: replenish exhausted lives and spend a continue
        // (sonic3k.asm:17033-17048), before restoring either payload format.
        if (lives == 0 || (lives < 3 && continues == 0)) {
            lives = 3;
            continues = Math.max(0, continues - 1);
        }
        List<Integer> states = readEmeraldStates(payload.get("emeraldStates"));
        if (states == null) {
            if (payload.containsKey("emeraldStates")) return false;
            gameState.restoreSaveProgress(lives, continues,
                    readIntList(payload.get("chaosEmeralds")),
                    readIntList(payload.get("superEmeralds")),
                    payload.get("emeraldsConverted") instanceof Boolean value ? value : null);
            restoreSpecialStageProgress(gameState, payload);
            return true;
        }
        Boolean converted = payload.get("emeraldsConverted") instanceof Boolean value ? value : null;
        gameState.restoreSaveProgress(lives, continues, List.of(), List.of(), converted);
        S3kEmeraldProgression.restore(gameState, states, Boolean.TRUE.equals(converted));
        restoreSpecialStageProgress(gameState, payload);
        return true;
    }

    private static void restoreSpecialStageProgress(GameStateManager gameState, Map<String, Object> payload) {
        // loc_D540 restores the saved word; completed-file selection at loc_D540
        // then clears the collection long (sonic3k.asm:17006-17013).
        int rings = Boolean.TRUE.equals(payload.get("clear")) ? 0
                : readOptionalProgress(payload, "collectedSpecialRings", 0xFFFF);
        NativeGameStateOps.restoreSpecialStageSaveProgress(gameState, rings,
                readOptionalProgress(payload, "currentSpecialStageIndex", 6));
    }

    static boolean hasValidSpecialStageProgress(Map<String, Object> payload) {
        return readOptionalProgress(payload, "collectedSpecialRings", 0xFFFF) >= 0
                && readOptionalProgress(payload, "currentSpecialStageIndex", 6) >= 0;
    }

    private static int readOptionalProgress(Map<String, Object> payload, String key, int maximum) {
        if (!payload.containsKey(key)) return 0;
        if (!(payload.get(key) instanceof Number number)) return -1;
        double value = number.doubleValue();
        return Double.isFinite(value) && value == Math.rint(value) && value >= 0 && value <= maximum
                ? (int) value : -1;
    }

    private static List<Integer> readEmeraldStates(Object raw) {
        if (!(raw instanceof List<?> list) || list.size() != 7) {
            return null;
        }
        java.util.ArrayList<Integer> states = new java.util.ArrayList<>(7);
        for (Object value : list) {
            if (!(value instanceof Number number)) {
                return null;
            }
            double numeric = number.doubleValue();
            if (!Double.isFinite(numeric) || numeric != Math.rint(numeric)
                    || numeric < 0 || numeric > 3) {
                return null;
            }
            states.add((int) numeric);
        }
        return List.copyOf(states);
    }

    @Override
    public Map<String,Object> captureSaveFields(com.openggf.game.zone.ZoneRuntimeState zoneState) {
        // loc_7BCB0 publishes the SSZ2 ending flag before SaveGame. Rewinding restores
        // this runtime flag; capture it without latching SaveSessionContext.clear.
        boolean clear=zoneState instanceof com.openggf.game.sonic3k.runtime.SszZoneRuntimeState state
                && state.actIndex() == 1 && state.playerCharacter() == com.openggf.game.PlayerCharacter.KNUCKLES
                && state.act2EndingActive();
        return Map.of("s3k:ending-clear",clear,
                SAVE_GAME_FIELD,com.openggf.game.sonic3k.S3kFullSaveGame.isCapturingSaveGame());
    }

    @Override
    public Map<String, Object> capture(SaveReason reason, RuntimeSaveContext context) {
        boolean hasLiveState = context.hasLiveGameplayState();
        Map<String, Object> payload = new LinkedHashMap<>();
        boolean requiresRuntime = switch (reason) {
            case EXISTING_SLOT_LOAD, CLEAR_RESTART_COMMIT, SPECIAL_STAGE_SAVE,
                 PROGRESSION_SAVE, LIVES_CONTINUES_SAVE -> true;
            case NEW_SLOT_START -> false;
        };
        if (requiresRuntime && !hasLiveState) {
            throw new IllegalStateException("Save reason " + reason + " requires a live runtime/gameplay mode");
        }
        int zone = context.currentZone();
        int act = context.currentAct();
        ZoneKey zoneKey = context.zoneKey();
        if (Boolean.TRUE.equals(context.capturedFields().get(SAVE_GAME_FIELD)) && zoneKey instanceof ZoneKey.Stock) {
            // SaveGame writes SaveGame_NextLevel[Current_zone_and_act] as the slot's level
            // (sonic3k.asm:15896-15908), so the file resumes where the exit leads, not where it
            // was saved. Persistence-only requests keep the current location.
            var next = S3kSaveProgressions.saveGameNextLevel(zone, act, context.selectedTeam());
            if (next.isPresent()) {
                zone = next.get().zone();
                act = next.get().act();
                zoneKey = ZoneKey.stock(zone);
            }
        } else if (hasLiveState && zoneKey instanceof ZoneKey.Stock) {
            // SaveGame_SpecialStage leaves the saved zone intact while the
            // sanctuary runs in $1701. Its Saved2 origin owns the reload destination.
            var origin = RuntimeSaveCapture.specialStageReturn(context);
            if (zone == com.openggf.game.sonic3k.constants.Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA
                    && act == 1 && origin != null && origin.originZone() >= 0) {
                zone = origin.originZone();
                act = origin.originAct();
            }
            // sub_DA4E selects zone starts, not temporary boss segments
            // (sonic3k.asm:17516-17549).
            var start = S3kSaveProgressions.saveStart(zone, act, context.selectedTeam());
            zone = start.zone();
            act = start.act();
            zoneKey = ZoneKey.stock(zone);
        }
        S3kSavedZone.write(payload, zoneKey);
        payload.put("act", act);
        payload.put("mainCharacter", context.selectedTeam().mainCharacter());
        payload.put("sidekicks", context.selectedTeam().sidekicks());
        int lives = context.lives();
        int continues = context.continues();
        List<Integer> chaosEmeralds = context.chaosEmeralds();
        List<Integer> superEmeralds = context.superEmeralds();
        boolean emeraldsConverted = context.emeraldsConverted();
        List<Integer> emeraldStates = context.emeraldStates();
        boolean clear = context.isClear();
        // Ending state is captured at the same save boundary as location and progress.
        clear |= Boolean.TRUE.equals(context.capturedFields().get("s3k:ending-clear"));
        payload.put("lives", lives);
        payload.put("continues", continues);
        payload.put("chaosEmeralds", chaosEmeralds);
        payload.put("superEmeralds", superEmeralds);
        payload.put("emeraldsConverted", emeraldsConverted);
        payload.put("emeraldStates", emeraldStates);
        // SaveGame_SpecialStage stores the collection low word and chaos-stage
        // cursor (sonic3k.asm:15974-15991). Forced sanctuary stages leave that
        // cursor unchanged, so saving it cannot advance the chaos rotation.
        payload.put("collectedSpecialRings", RuntimeSaveCapture.collectedSpecialRings(context) & 0xFFFF);
        payload.put("currentSpecialStageIndex", RuntimeSaveCapture.currentSpecialStageIndex(context));
        payload.put("clear", clear);
        payload.put("progressCode", zoneKey instanceof ZoneKey.Stock
                ? S3kSaveProgressions.progressCodeForState(
                        zone, act, context.selectedTeam(), clear, superEmeralds)
                : 1);
        payload.put("clearState", clear ? (S3kSaveProgressions.hasAllSuperEmeralds(superEmeralds) ? 2 : 1) : 0);
        return payload;
    }
	private static List<Integer> readIntList(Object raw) {
		if (!(raw instanceof List<?> list)) {
			return List.of();
		}
		List<Integer> values = new java.util.ArrayList<>();
		for (Object value : list) {
			if (value instanceof Number number) {
				values.add(number.intValue());
			}
		}
		return List.copyOf(values);
	}
}
