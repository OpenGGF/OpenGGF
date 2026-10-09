package com.openggf.game.sonic2;

import com.openggf.game.GameServices;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Metropolis Zone's boss (Obj54) fights in act 3, which loads as ROM zone $05. Its sheet must be
 * registered for that act, as every other Sonic 2 boss's is for the zone holding its fight.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2MtzBossArtRegistration {
    private SharedLevel level;

    @AfterEach void dispose() {
        if (level != null) level.dispose();
    }

    @Test void metropolisAct3RegistersTheBossSheet() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_2, 7, 2);
        // The act's loaded provider (the level's render manager), as objects draw through it.
        var art = GameServices.level().getObjectRenderManager().getArtProvider();
        var sheet = art.getSheet(Sonic2ObjectArtKeys.MTZ_BOSS);
        assertNotNull(sheet, "MTZ act 3 registers " + Sonic2ObjectArtKeys.MTZ_BOSS);
        assertTrue(sheet.getFrameCount() > 0x11, "the sheet holds Obj54's frames through the defeat face $11");
    }
}
