package com.openggf.game.save;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TestSavePayloadReader {
    @Test
    void preservesPermissiveTeamDecoding() {
        assertEquals(new SelectedTeam("sonic", List.of()), SavePayloadReader.readTeam(Map.of()));
        Map<String, Object> payload = new HashMap<>();
        payload.put("mainCharacter", null);
        payload.put("sidekicks", Arrays.asList("tails", null, 7));
        assertEquals(new SelectedTeam("null", List.of("tails", "null", "7")),
                SavePayloadReader.readTeam(payload));
        payload.put("mainCharacter", 42);
        payload.put("sidekicks", "tails");
        assertEquals(new SelectedTeam("42", List.of()), SavePayloadReader.readTeam(payload));
    }

    @Test
    void preservesNumberConversionAndFallback() {
        assertEquals(9, SavePayloadReader.readInt(Map.of(), "zone", 9));
        assertEquals(9, SavePayloadReader.readInt(Map.of("zone", "3"), "zone", 9));
        assertEquals(-3, SavePayloadReader.readInt(Map.of("zone", -3.9), "zone", 9));
        assertEquals(1, SavePayloadReader.readInt(Map.of("zone", 0x100000001L), "zone", 9));
        Map<String, Object> payload = new HashMap<>();
        payload.put("zone", null);
        assertEquals(9, SavePayloadReader.readInt(payload, "zone", 9));
    }
}
