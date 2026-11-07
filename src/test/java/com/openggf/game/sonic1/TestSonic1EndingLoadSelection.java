package com.openggf.game.sonic1;

import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_1)
class TestSonic1EndingLoadSelection {
    @Test
    void endingIdentityComesFromSelectedRomSources() throws Exception {
        var game = new Sonic1(TestEnvironment.currentRom());
        assertNull(game.selectEndingLoad(0x80));
        var good = game.selectEndingLoad(0x95);
        var bad = game.selectEndingLoad(0x96);
        assertNotNull(good);
        assertNotNull(bad);
        assertEquals("sha256:5c7daa68eb01774b384e9b28e932da4e52914b5dfa1c3ca2edc71c4be0e04464",
                good.inputs().fingerprint("level_setup"));
        assertEquals(good.inputs().fingerprint("level_setup"),
                game.selectEndingLoad(0x95).inputs().fingerprint("level_setup"));
        assertNotEquals(good.inputs().fingerprint("level_setup"),
                bad.inputs().fingerprint("level_setup"));
    }
}
