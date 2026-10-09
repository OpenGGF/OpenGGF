package com.openggf.mods.code;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

/** Sonic 1 rail acts can draw every frame Typing of the Hedgehog's tables name: {@link TypingHedgehogArtTables}. */
@RequiresRom(SonicGame.SONIC_1)
class TestTypingHedgehogArtSonic1 {
    @Test void everyRailActDrawsItsCastPropsAndBoss() throws Exception {
        TypingHedgehogArtTables.check(SonicGame.SONIC_1, "s1");
    }
}
