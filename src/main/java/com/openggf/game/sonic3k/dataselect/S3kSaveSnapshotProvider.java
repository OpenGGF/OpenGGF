package com.openggf.game.sonic3k.dataselect;

import com.openggf.game.ZoneKey;
import com.openggf.game.GameStateManager;
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

    @Override
    public boolean restoreProgress(
            GameStateManager gameState, int lives, int continues, Map<String, Object> payload) {
        // S3K slot load: replenish exhausted lives and spend a continue
        // (sonic3k.asm:16997-17012), before restoring either payload format.
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
            return true;
        }
        Boolean converted = payload.get("emeraldsConverted") instanceof Boolean value ? value : null;
        gameState.restoreSaveProgress(lives, continues, List.of(), List.of(), converted);
        S3kEmeraldProgression.restore(gameState, states, Boolean.TRUE.equals(converted));
        return true;
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
        return Map.of("s3k:ending-clear",clear);
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
