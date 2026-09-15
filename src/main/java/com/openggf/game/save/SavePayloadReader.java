package com.openggf.game.save;

import java.util.List;
import java.util.Map;

/** Internal readers preserving permissive data-select save compatibility. */
public final class SavePayloadReader {
    private SavePayloadReader() {}

    public static SelectedTeam readTeam(Map<String, Object> payload) {
        String main = String.valueOf(payload.getOrDefault("mainCharacter", "sonic"));
        Object sidekicksRaw = payload.get("sidekicks");
        List<String> sidekicks = sidekicksRaw instanceof List<?>
                ? ((List<?>) sidekicksRaw).stream().map(String::valueOf).toList()
                : List.of();
        return new SelectedTeam(main, sidekicks);
    }

    public static int readInt(Map<String, Object> payload, String key, int fallback) {
        Object value = payload.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
