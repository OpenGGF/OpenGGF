package com.openggf.game.sonic2.dataselect;

import com.openggf.game.save.RuntimeSaveContext;
import com.openggf.game.save.SaveReason;
import com.openggf.game.save.SaveSnapshotProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.openggf.game.ZoneKey;

public final class S2SaveSnapshotProvider implements SaveSnapshotProvider {
    @Override
    public Map<String, Object> capture(SaveReason reason, RuntimeSaveContext context) {
        boolean hasLiveState = context.hasLiveGameplayState();
        if (reason != SaveReason.NEW_SLOT_START && !hasLiveState) {
            throw new IllegalStateException("Save reason " + reason + " requires a live runtime/gameplay mode");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        int zone = context.currentZone();
        int act = context.currentAct();
        int lives = context.lives();
        List<Integer> chaosEmeralds = context.chaosEmeralds();
        boolean clear = context.isClear();
        ZoneKey zoneKey = context.zoneKey();
        if (zoneKey instanceof ZoneKey.Stock) {
            // Preserve the historical stock payload shape and serialized hashes exactly.
            payload.put("zone", zone);
        } else {
            S2SavedZone.write(payload, zoneKey);
        }
        payload.put("act", act);
        payload.put("mainCharacter", context.selectedTeam().mainCharacter());
        payload.put("sidekicks", context.selectedTeam().sidekicks());
        payload.put("lives", lives);
        payload.put("chaosEmeralds", chaosEmeralds);
        payload.put("clear", clear);
        payload.put("progressCode", zoneKey instanceof ZoneKey.Stock ? zone + 1 : 1);
        payload.put("clearState", clear ? 1 : 0);
        return payload;
    }
}
