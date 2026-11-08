package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.TrackKey;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestCatalogTrackRules {
    @Test void exportedCataloguePreservesModeAndMonitorBoundaries() {
        var rules = TrackRules.shipped();
        assertTrue(rules.legal(new TrackKey("s1", "ACT", 0, 0), "TIME_ATTACK"));
        assertFalse(rules.legal(new TrackKey("s1", "ACT", 0, 0), "COMPETITION"));
        assertFalse(rules.legal(new TrackKey("s2", "ARENA", 0, 0), "COMPETITION"));
        assertFalse(rules.legal(new TrackKey("s3k", "ARENA", 5, 0), "COMPETITION"));
        for (int arena = 0; arena < 5; arena++) {
            var key = new TrackKey("s3k", "ARENA", arena, 0);
            assertTrue(rules.legal(key, "COMPETITION"));
            assertFalse(rules.legal(key, "TIME_ATTACK"));
            assertEquals(240, rules.clockStartFrame(key), "native countdown catalogue comparison");
        }
        for (String monitors : new String[]{"NORMAL", "OFF", "RANDOM", "ITEMS"}) {
            assertEquals(monitors.equals("NORMAL"), rules.monitorsLegal(config("s2", "ACT", "TIME_ATTACK", monitors, 0, 0)));
            assertEquals(monitors.equals("ITEMS") || monitors.equals("OFF"),
                    rules.monitorsLegal(config("s3k", "ARENA", "COMPETITION", monitors, 0, 0)));
        }
        assertTrue(rules.monitorsLegal(config("s2", "ACT", "COMPETITION", "ITEMS", 7, 1)));
        assertFalse(rules.monitorsLegal(config("s2", "ACT", "COMPETITION", "ITEMS", 8, 0)));
        assertFalse(rules.monitorsLegal(config("s3k", "ACT", "COMPETITION", "RANDOM", 0, 0)));
    }
    private ControlMessage.RoundConfig config(String game, String kind, String mode, String monitors, int zone, int act) {
        return new ControlMessage.RoundConfig(game, zone, act, 60, "OPEN", null, kind, mode, monitors, 60, 15);
    }
}
