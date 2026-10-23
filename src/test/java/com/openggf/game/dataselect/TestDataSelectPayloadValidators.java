package com.openggf.game.dataselect;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestDataSelectPayloadValidators {
    @Test
    void commonPayload_acceptsInclusiveLimitsAndOptionalDefaults() {
        var payload = payload();
        payload.put("zone", 6);
        payload.put("act", 2);
        payload.put("lives", 0);
        payload.put("chaosEmeralds", List.of(0, 5));
        payload.put("superEmeralds", List.of(5));
        payload.put("progressCode", 14);
        payload.put("clearState", 3);
        assertTrue(valid(payload));
        assertTrue(valid(payload()));
        assertThrows(NullPointerException.class,
                () -> DataSelectPayloadValidators.validateCommonPayload(null, 6, 6));
    }

    @Test
    void commonPayload_rejectsMissingOrWrongRequiredFieldsAndOutOfRangeNumbers() {
        for (String field : List.of("zone", "act", "mainCharacter", "sidekicks")) {
            var missing = payload();
            missing.remove(field);
            assertFalse(valid(missing), field);
        }
        for (var invalid : List.of(
                Map.entry("zone", -1), Map.entry("zone", 7), Map.entry("zone", "0"),
                Map.entry("act", -1), Map.entry("act", 3), Map.entry("act", "0"),
                Map.entry("mainCharacter", List.of("sonic")), Map.entry("sidekicks", "tails"),
                Map.entry("lives", -1), Map.entry("clear", "true"),
                Map.entry("progressCode", -1), Map.entry("progressCode", 15),
                Map.entry("clearState", -1), Map.entry("clearState", 4))) {
            var payload = payload();
            payload.put(invalid.getKey(), invalid.getValue());
            assertFalse(valid(payload), invalid.toString());
        }
    }

    @Test
    void commonPayload_preservesPermissiveOptionalAndNumberHandling() {
        var payload = payload();
        payload.put("zone", 6.9);
        payload.put("act", 2L);
        payload.put("lives", "unknown");
        payload.put("progressCode", "unknown");
        payload.put("clearState", "unknown");
        payload.put("sidekicks", List.of(12));
        assertTrue(valid(payload));
    }

    @Test
    void commonPayload_rejectsDuplicateOutOfRangeOrNonSubsetEmeralds() {
        for (String field : List.of("chaosEmeralds", "superEmeralds")) {
            for (Object invalid : List.of("0", List.of("0"), List.of(-1), List.of(6), List.of(0, 0))) {
                var payload = payload();
                payload.put("chaosEmeralds", List.of(0, 1, 2, 3, 4, 5));
                payload.put(field, invalid);
                assertFalse(valid(payload), field + "=" + invalid);
            }
        }
        var payload = payload();
        payload.put("chaosEmeralds", List.of(0));
        payload.put("superEmeralds", List.of(1));
        assertFalse(valid(payload));
        payload.put("superEmeralds", List.of(0));
        assertTrue(valid(payload));
        payload.put("chaosEmeralds", List.of(6));
        payload.put("superEmeralds", List.of(6));
        assertTrue(DataSelectPayloadValidators.validateCommonPayload(payload, 6, 7));
    }

    private static boolean valid(Map<String, Object> payload) {
        return DataSelectPayloadValidators.validateCommonPayload(payload, 6, 6);
    }

    private static Map<String, Object> payload() {
        return new LinkedHashMap<>(Map.of("zone", 0, "act", 0,
                "mainCharacter", "sonic", "sidekicks", List.of()));
    }
}
