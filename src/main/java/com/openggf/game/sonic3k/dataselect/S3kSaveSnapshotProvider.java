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
        List<Integer> states = readEmeraldStates(payload.get("emeraldStates"));
        if (states == null) {
            return false;
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
    public Map<String, Object> capture(SaveReason reason, RuntimeSaveContext context) {
        boolean hasLiveState = context.hasLiveGameplayState();
        Map<String, Object> payload = new LinkedHashMap<>();
        var save = context.saveSessionContext();
        boolean requiresRuntime = switch (reason) {
            case EXISTING_SLOT_LOAD, CLEAR_RESTART_COMMIT, SPECIAL_STAGE_SAVE,
                 PROGRESSION_SAVE, LIVES_CONTINUES_SAVE -> true;
            case NEW_SLOT_START -> false;
        };
        if (requiresRuntime && !hasLiveState) {
            throw new IllegalStateException("Save reason " + reason + " requires a live runtime/gameplay mode");
        }
        int zone = !hasLiveState ? save.startZone()
                : context.levelManager().getCurrentZone();
        int act = !hasLiveState ? save.startAct()
                : context.levelManager().getCurrentAct();
        ZoneKey zoneKey = !hasLiveState ? ZoneKey.stock(zone)
                : context.levelManager().getGameModule().getZoneRegistry().zoneKey(zone);
        S3kSavedZone.write(payload, zoneKey);
        payload.put("act", act);
        payload.put("mainCharacter", save.selectedTeam().mainCharacter());
        payload.put("sidekicks", save.selectedTeam().sidekicks());
        int lives = !hasLiveState ? 3
                : context.gameState().getLives();
        int continues = !hasLiveState ? 0
                : context.gameState().getContinues();
        List<Integer> chaosEmeralds = !hasLiveState ? List.of()
                : context.gameState().getCollectedChaosEmeraldIndices();
        List<Integer> superEmeralds = !hasLiveState ? List.of()
                : context.gameState().getCollectedSuperEmeraldIndices();
        boolean emeraldsConverted = hasLiveState && context.gameState().isEmeraldsConverted();
        List<Integer> emeraldStates = !hasLiveState ? List.of(0, 0, 0, 0, 0, 0, 0)
                : context.gameState().getS3kEmeraldStates();
        boolean clear = save.isClear();
        payload.put("lives", lives);
        payload.put("continues", continues);
        payload.put("chaosEmeralds", chaosEmeralds);
        payload.put("superEmeralds", superEmeralds);
        payload.put("emeraldsConverted", emeraldsConverted);
        payload.put("emeraldStates", emeraldStates);
        payload.put("clear", clear);
        payload.put("progressCode", zoneKey instanceof ZoneKey.Stock
                ? S3kSaveProgressions.progressCodeForState(
                        zone, act, save.selectedTeam(), clear, superEmeralds)
                : 1);
        payload.put("clearState", clear ? (S3kSaveProgressions.hasAllSuperEmeralds(superEmeralds) ? 2 : 1) : 0);
        return payload;
    }
}
